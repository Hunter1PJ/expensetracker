package com.suguru.expensetracker.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

/**
 * Safely checks whether Firebase is initialized, attempting initialization from resources if available.
 */
fun isFirebaseInitialized(context: Context): Boolean {
    if (FirebaseApp.getApps(context).isNotEmpty()) {
        return true
    }
    return try {
        val options = FirebaseOptions.fromResource(context)
        if (options != null) {
            FirebaseApp.initializeApp(context, options)
            FirebaseApp.getApps(context).isNotEmpty()
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

data class CloudAuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?
)

internal fun FirebaseUser.toCloudAuthUser(): CloudAuthUser = CloudAuthUser(
    uid = uid,
    displayName = displayName,
    email = email
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return try {
        val clientId = context.getString(resId)
        clientId.ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

/**
 * Attempts silent auto sign-in using an already-authorized Google account.
 * Single credential option per request for Android API 36 responsiveness.
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val auth = try {
        Firebase.auth
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    if (auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = getDefaultWebClientId(context) ?: run {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

internal fun isUserCancellationException(e: Exception): Boolean {
    if (e !is GetCredentialCancellationException) return false

    val cause = e.cause
    if (cause != null && cause !is GetCredentialCancellationException) {
        return false
    }

    val combinedMessage = buildString {
        append(e.message ?: "")
        append(" ")
        append(cause?.message ?: "")
    }.lowercase()

    val errorKeywords = listOf(
        "[10]", "10:", "developer_error", "developer error",
        "[12500]", "12500:", "sign_in_failed", "sign in failed",
        "sha", "signature", "mismatch", "certificate",
        "api_not_connected", "provider", "null string", "parcel",
        "client_id", "unregistered", "invalid"
    )

    if (errorKeywords.any { combinedMessage.contains(it) }) {
        return false
    }

    return true
}

internal fun logAuthExceptionDiagnostics(e: Exception) {
    val className = e.javaClass.name
    val simpleName = e.javaClass.simpleName
    val message = e.message
    val causeClass = e.cause?.javaClass?.name
    val causeMessage = e.cause?.message
    val credentialType = (e as? GetCredentialException)?.type

    Log.e(
        AUTH_TAG,
        "Stage: CredentialManager - ExceptionClass: $className ($simpleName), CredentialType: $credentialType, Message: $message, CauseClass: $causeClass, CauseMessage: $causeMessage",
        e
    )
}

internal fun mapAuthExceptionToUserMessage(e: Exception): String {
    val msg = e.message?.lowercase() ?: ""
    val causeMsg = e.cause?.message?.lowercase() ?: ""
    val combinedMsg = "$msg $causeMsg"

    return when {
        e is com.google.firebase.FirebaseNetworkException ||
        e is java.net.UnknownHostException ||
        e is java.io.IOException -> {
            "Network failure. Please check your internet connection and try again."
        }
        combinedMsg.contains("[10]") ||
        combinedMsg.contains("[12500]") ||
        combinedMsg.contains("developer_error") ||
        combinedMsg.contains("developer error") ||
        combinedMsg.contains("sha") ||
        combinedMsg.contains("signature") ||
        e is androidx.credentials.exceptions.GetCredentialCustomException -> {
            "Google Sign-In configuration error: Developer error [10/12500] or SHA-1 fingerprint mismatch."
        }
        e is com.google.firebase.auth.FirebaseAuthException -> {
            "Firebase authentication failed. Please check your account configuration and try again."
        }
        else -> {
            e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Google Sign-In failed. Please try again."
        }
    }
}

/**
 * Interactive Google Sign-In flow triggered by the user tapping "Sign in with Google".
 * Uses [GetSignInWithGoogleOption] as the sole credential option in [GetCredentialRequest].
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = getDefaultWebClientId(context) ?: run {
        val errorMsg = "Google Sign-In configuration missing: default_web_client_id not found"
        Log.e(AUTH_TAG, "Stage: Config - $errorMsg")
        onAuthError(errorMsg)
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        val errorMsg = "Google Sign-In requires an active Activity context."
        Log.e(AUTH_TAG, "Stage: Activity - $errorMsg")
        onAuthError(errorMsg)
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            Log.i(AUTH_TAG, "Stage: CredentialManager - Requesting credential")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Log.i(AUTH_TAG, "Stage: FirebaseAuth - Signing in with ID token")
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                val currentUser = Firebase.auth.currentUser
                if (currentUser != null) {
                    Log.i(AUTH_TAG, "Stage: AuthSuccess - Authenticated UID=${currentUser.uid}")
                    onAuthSuccess()
                } else {
                    val errorMsg = "Firebase authentication succeeded, but user account could not be verified."
                    Log.e(AUTH_TAG, "Stage: FirebaseAuth - $errorMsg")
                    onAuthError(errorMsg)
                }
            } else {
                val errorMsg = "Unexpected credential type returned by Google Sign-In."
                Log.e(AUTH_TAG, "Stage: CredentialParsing - $errorMsg")
                onAuthError(errorMsg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAuthExceptionDiagnostics(e)
            if (isUserCancellationException(e)) {
                Log.i(AUTH_TAG, "Stage: CredentialManager - User intentionally cancelled sign-in prompt")
                onAuthCancelled()
            } else {
                Log.e(AUTH_TAG, "Stage: AuthFailure - ${e.javaClass.simpleName}: ${e.message}")
                onAuthError(mapAuthExceptionToUserMessage(e))
            }
        }
    }
}

/**
 * Signs out of Firebase Auth after clearing Credential Manager state inside [NonCancellable]
 * so listener teardown and state clearing cannot be aborted mid-flight by composition disposal.
 */
fun signOutFromGoogle(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                try {
                    Firebase.auth.signOut()
                } catch (e: Exception) {
                    Log.w(AUTH_TAG, "Failed to sign out FirebaseAuth: ${e.message}")
                }
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun GoogleSignInButton(
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "google_sign_in_button"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    onAuthError(msg)
                },
                scope = scope,
                onAuthCancelled = { isLoading = false }
            )
        },
        enabled = enabled && !isLoading,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
        ),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
            )
            Spacer(modifier = Modifier.size(ExpenseTrackerSpacing.sm))
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
