package com.suguru.expensetracker.presentation.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.presentation.auth.CloudAuthUser
import com.suguru.expensetracker.presentation.auth.GoogleSignInButton
import com.suguru.expensetracker.presentation.auth.authStateFlow
import com.suguru.expensetracker.presentation.auth.signOutFromGoogle
import com.suguru.expensetracker.presentation.auth.toCloudAuthUser
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.DangerButton
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.PrimaryButton
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.SecondaryButton
import com.suguru.expensetracker.presentation.util.localized
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudBackupSection(
    createCloudBackupViewModel: (uid: String) -> CloudBackupViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isFirebaseInitialized = remember(context) {
        com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(context)
    }

    if (!isFirebaseInitialized) {
        CloudBackupUnavailableCard(modifier = modifier)
        return
    }

    val firebaseAuth = remember { Firebase.auth }
    val currentUser by firebaseAuth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = firebaseAuth.currentUser)

    var authError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
    ) {
        SectionHeader(
            title = stringResource(com.suguru.expensetracker.R.string.ui_cloud_backup_title),
            testTag = "section_cloud_backup"
        )

        val user = currentUser
        if (user == null) {
            CloudBackupSignInCard(
                authError = authError,
                onAuthSuccess = { authError = null },
                onAuthError = { msg -> authError = msg }
            )
        } else {
            val authUser = user.toCloudAuthUser()
            key(authUser.uid) {
                val vm: CloudBackupViewModel = viewModel(
                    key = authUser.uid,
                    factory = viewModelFactory {
                        initializer {
                            createCloudBackupViewModel(authUser.uid)
                        }
                    }
                )
                AuthenticatedCloudBackupCard(
                    user = authUser,
                    viewModel = vm,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}

@Composable
private fun CloudBackupSignInCard(
    authError: String?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    ExpenseTrackerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
        testTag = "cloud_backup_signin_card"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                IconAvatar(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_connect_google),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.ui_cloud_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.ui_google_backup_restore_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            ) {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_backup_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.financialWarning,
                    fontWeight = FontWeight.Medium
                )
            }

            if (authError != null) {
                ErrorBanner(message = authError)
            }

            GoogleSignInButton(
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError,
                modifier = Modifier.fillMaxWidth(),
                testTag = "cloud_backup_google_signin_button"
            )
        }
    }
}

@Composable
private fun AuthenticatedCloudBackupCard(
    user: CloudAuthUser,
    viewModel: CloudBackupViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val manifestsState by viewModel.manifestsState.collectAsStateWithLifecycle()
    val localCloudState by viewModel.localCloudState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsStateWithLifecycle()
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val localizedSuccessMessage = actionState.successMessage?.localized()
    val localizedErrorMessage = actionState.errorMessage?.localized()
    val localizedBusyMessage = actionState.busyMessage?.localized()

    LaunchedEffect(localizedSuccessMessage) {
        localizedSuccessMessage?.let { msg ->
            onShowMessage(msg)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(localizedErrorMessage) {
        localizedErrorMessage?.let { err ->
            onShowMessage(err)
            viewModel.clearMessages()
        }
    }

    ExpenseTrackerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
        testTag = "cloud_backup_authenticated_card"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    IconAvatar(
                        icon = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                        size = 40.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = user.displayName?.takeIf { it.isNotBlank() } ?: stringResource(com.suguru.expensetracker.R.string.cloud_google_account_connected),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = user.email ?: "UID: ${user.uid.take(10)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(
                    onClick = { showSignOutConfirmation = true },
                    enabled = !actionState.isBusy,
                    modifier = Modifier.testTag("cloud_backup_signout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(com.suguru.expensetracker.R.string.ui_sign_out),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(com.suguru.expensetracker.R.string.ui_sign_out))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_auto_backup_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_auto_backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
                Switch(
                    checked = isAutoBackupEnabled,
                    onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                    modifier = Modifier.testTag("auto_backup_switch")
                )
            }

            var isOnline by remember(context) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                mutableStateOf(
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
            DisposableEffect(context) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        isOnline = true
                    }

                    override fun onLost(network: android.net.Network) {
                        val activeNetwork = connectivityManager?.activeNetwork
                        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                        isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }
                }
                runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
                onDispose {
                    runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
                }
            }

            if (isAutoBackupEnabled) {
                val syncFailure = syncStatus as? com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Failure
                val backupStatusRes = when {
                    syncStatus is com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.BackupInProgress ||
                        (actionState.isBusy && actionState.busyMessage?.resourceId == com.suguru.expensetracker.R.string.cloud_busy_uploading_chunks) ->
                        com.suguru.expensetracker.R.string.cloud_status_backing_up
                    localCloudState.lastFailureCode != null ->
                        if (!isOnline && localCloudState.lastFailureCode == "NETWORK_UNAVAILABLE") com.suguru.expensetracker.R.string.cloud_status_waiting_network else com.suguru.expensetracker.R.string.cloud_status_failed
                    localCloudState.pendingSnapshotId != null ->
                        if (!isOnline) com.suguru.expensetracker.R.string.cloud_status_waiting_network else com.suguru.expensetracker.R.string.cloud_status_pending
                    localCloudState.lastSuccessfulBackupAtEpochMillis != null -> com.suguru.expensetracker.R.string.cloud_status_uptodate
                    else -> if (!isOnline) com.suguru.expensetracker.R.string.cloud_status_waiting_network else com.suguru.expensetracker.R.string.cloud_status_pending
                }

                val activeManifest = (manifestsState as? CloudSnapshotsUiState.Success)
                    ?.manifests
                    ?.firstOrNull { it.status == CloudBackupManifestStatus.COMPLETE }
                val restoreStatus = com.suguru.expensetracker.domain.model.CloudRestoreStatusResolver.resolve(
                    com.suguru.expensetracker.domain.model.CloudRestoreStatusInput(
                        activeManifest = activeManifest,
                        isLoading = manifestsState !is CloudSnapshotsUiState.Success,
                        restoreInProgress =
                            syncStatus is com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.RestoreInProgress ||
                                (actionState.isBusy && actionState.busyMessage?.resourceId == com.suguru.expensetracker.R.string.cloud_busy_restoring),
                        pendingRestore = localCloudState.pendingRestore,
                        isOnline = isOnline,
                        transientFailure =
                            actionState.restoreResult is CloudRestoreResult.Failure ||
                                syncFailure?.code?.contains("RESTORE") == true,
                        lastFailureCode = localCloudState.lastRestoreFailureCode,
                        lastFailureGenerationId = localCloudState.lastRestoreFailureGenerationId,
                        lastFailureFingerprint = localCloudState.lastRestoreFailureFingerprint,
                        restoredGenerationId = localCloudState.lastSuccessfulRestoreGenerationId,
                        restoredFingerprint = localCloudState.lastSuccessfulRestoreFingerprint
                    )
                )
                val restoreStatusRes = when (restoreStatus) {
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.UP_TO_DATE -> com.suguru.expensetracker.R.string.cloud_status_uptodate
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.NEW_BACKUP_AVAILABLE -> com.suguru.expensetracker.R.string.cloud_status_new_backup
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORING -> com.suguru.expensetracker.R.string.cloud_status_restoring
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.WAITING_FOR_INTERNET -> com.suguru.expensetracker.R.string.cloud_status_waiting_network
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORE_FAILED -> com.suguru.expensetracker.R.string.cloud_status_restore_failed
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.CHECKING -> com.suguru.expensetracker.R.string.cloud_status_checking
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.NO_CLOUD_BACKUP -> com.suguru.expensetracker.R.string.cloud_status_no_backup
                }
                val restoreDescriptionRes = when (restoreStatus) {
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.UP_TO_DATE -> com.suguru.expensetracker.R.string.cloud_restore_desc_uptodate
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.NEW_BACKUP_AVAILABLE -> com.suguru.expensetracker.R.string.cloud_restore_desc_new
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORING -> com.suguru.expensetracker.R.string.cloud_restore_desc_restoring
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.WAITING_FOR_INTERNET -> com.suguru.expensetracker.R.string.cloud_restore_desc_waiting
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORE_FAILED -> com.suguru.expensetracker.R.string.cloud_restore_desc_failed
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.CHECKING -> com.suguru.expensetracker.R.string.cloud_restore_desc_checking
                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.NO_CLOUD_BACKUP -> com.suguru.expensetracker.R.string.cloud_restore_desc_none
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                        .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_backup_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(backupStatusRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (backupStatusRes) {
                                    com.suguru.expensetracker.R.string.cloud_status_uptodate -> ExpenseTrackerTheme.extendedColors.financialPositive
                                    com.suguru.expensetracker.R.string.cloud_status_backing_up -> MaterialTheme.colorScheme.primary
                                    com.suguru.expensetracker.R.string.cloud_status_pending,
                                    com.suguru.expensetracker.R.string.cloud_status_waiting_network -> ExpenseTrackerTheme.extendedColors.financialWarning
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(restoreStatusRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (restoreStatus) {
                                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.UP_TO_DATE -> ExpenseTrackerTheme.extendedColors.financialPositive
                                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORING -> MaterialTheme.colorScheme.primary
                                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.RESTORE_FAILED -> MaterialTheme.colorScheme.error
                                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.WAITING_FOR_INTERNET,
                                    com.suguru.expensetracker.domain.model.CloudRestoreStatus.NEW_BACKUP_AVAILABLE -> ExpenseTrackerTheme.extendedColors.financialWarning
                                    else -> ExpenseTrackerTheme.extendedColors.textSecondary
                                }
                            )
                        }
                        Text(
                            text = stringResource(restoreDescriptionRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )

                        localCloudState.lastSuccessfulBackupAtEpochMillis?.let { lastCloudTime ->
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_last_backup, formatInstant(Instant.ofEpochMilli(lastCloudTime))),
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseTrackerTheme.extendedColors.textSecondary
                            )
                        }

                        Text(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_last_restored, localCloudState.lastSuccessfulRestoreAtEpochMillis?.let { formatInstant(Instant.ofEpochMilli(it)) } ?: stringResource(com.suguru.expensetracker.R.string.cloud_never)),
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )

                        localCloudState.lastFailureCode?.let {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_last_issue),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                val lastCloudTime = localCloudState.lastSuccessfulBackupAtEpochMillis
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight)
                        .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (lastCloudTime != null) {
                                stringResource(com.suguru.expensetracker.R.string.cloud_last_backup_info, formatInstant(Instant.ofEpochMilli(lastCloudTime)))
                            } else {
                                stringResource(com.suguru.expensetracker.R.string.cloud_no_backup_yet)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                        localCloudState.lastFailureCode?.let {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_last_issue),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (actionState.isBusy && actionState.busyMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = localizedBusyMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                        )
                    }
                }

                PrimaryButton(
                    text = if (actionState.isBusy) stringResource(com.suguru.expensetracker.R.string.cloud_working) else stringResource(com.suguru.expensetracker.R.string.cloud_backup_now),
                    onClick = { viewModel.backUpNow() },
                    enabled = !actionState.isBusy,
                    leadingIcon = Icons.Default.CloudUpload,
                    testTag = "cloud_backup_now_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = ExpenseTrackerTheme.extendedColors.borderSubtle)

            Text(
                text = stringResource(com.suguru.expensetracker.R.string.cloud_information),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            when (val state = manifestsState) {
                is CloudSnapshotsUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ExpenseTrackerSpacing.sm),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                is CloudSnapshotsUiState.Error -> {
                    ErrorBanner(message = stringResource(com.suguru.expensetracker.R.string.cloud_load_snapshots_failed))
                }
                is CloudSnapshotsUiState.Success -> {
                    if (state.manifests.isEmpty()) {
                        Text(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_empty_snapshots),
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                            state.manifests.take(1).forEach { manifest ->
                                CloudManifestRow(
                                    manifest = manifest,
                                    isBusy = actionState.isBusy,
                                    isAutoMode = isAutoBackupEnabled,
                                    onRestore = { viewModel.requestRestoreSnapshot(manifest) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (syncStatus is com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Conflict) {
        val conflict = syncStatus as com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Conflict
        AlertDialog(
            onDismissRequest = {},
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_conflict_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_conflict_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_snapshot_from, formatInstant(conflict.remoteManifest.completedAt ?: conflict.remoteManifest.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_snapshot_title),
                    onClick = { viewModel.resolveConflictRestore(conflict.remoteManifest) },
                    testTag = "conflict_restore_cloud_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_keep_local_backup),
                    onClick = { viewModel.resolveConflictKeepLocal() },
                    testTag = "conflict_keep_local_button"
                )
            }
        )
    }

    if (actionState.isBusy && actionState.busyMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            },
            title = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_restoring_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = localizedBusyMessage ?: stringResource(com.suguru.expensetracker.R.string.cloud_restore_desc_restoring),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            },
            confirmButton = {}
        )
    }

    actionState.pendingRestoreCandidate?.let { manifest ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_confirm_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(com.suguru.expensetracker.R.string.cloud_snapshot_from, formatInstant(manifest.completedAt ?: manifest.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_action),
                    onClick = { viewModel.confirmPendingRestore() },
                    leadingIcon = Icons.Default.CloudDownload,
                    testTag = "confirm_cloud_restore_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.ui_cancel),
                    onClick = { viewModel.cancelPendingRestore() },
                    testTag = "cancel_cloud_restore_button"
                )
            }
        )
    }

    actionState.restoreResult?.let { result ->
        when (result) {
            is CloudRestoreResult.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRestoreResult() },
                    shape = ExpenseTrackerRadius.dialog,
                    containerColor = ExpenseTrackerTheme.extendedColors.surface,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = ExpenseTrackerTheme.extendedColors.financialPositive,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_restored_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_restored_desc, formatInstant(result.manifest.completedAt ?: result.manifest.createdAt)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_next),
                            onClick = { viewModel.dismissRestoreResult() },
                            testTag = "restore_success_next_button"
                        )
                    }
                )
            }
            is CloudRestoreResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRestoreResult() },
                    shape = ExpenseTrackerRadius.dialog,
                    containerColor = ExpenseTrackerTheme.extendedColors.surface,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_failed_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_failed_desc),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(com.suguru.expensetracker.R.string.cloud_restore_failed_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = stringResource(com.suguru.expensetracker.R.string.cloud_next),
                            onClick = { viewModel.dismissRestoreResult() },
                            testTag = "restore_failure_next_button"
                        )
                    }
                )
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            shape = ExpenseTrackerRadius.dialog,
            containerColor = ExpenseTrackerTheme.extendedColors.surface,
            title = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_signout_confirm),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_signout_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_signout_action),
                    onClick = {
                        showSignOutConfirmation = false
                        signOutFromGoogle(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    testTag = "confirm_sign_out_button"
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(com.suguru.expensetracker.R.string.ui_cancel),
                    onClick = { showSignOutConfirmation = false },
                    testTag = "cancel_sign_out_button"
                )
            }
        )
    }
}

@Composable
private fun CloudManifestRow(
    manifest: CloudBackupManifest,
    isBusy: Boolean,
    isAutoMode: Boolean,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    val sizeText = remember(manifest.compressedBytes) {
        Formatter.formatShortFileSize(context, manifest.compressedBytes)
    }
    val statusColor = when (manifest.status) {
        CloudBackupManifestStatus.COMPLETE -> ExpenseTrackerTheme.extendedColors.financialPositive
        CloudBackupManifestStatus.UPLOADING -> MaterialTheme.colorScheme.primary
        CloudBackupManifestStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
            .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            Icon(
                imageVector = when (manifest.status) {
                    CloudBackupManifestStatus.COMPLETE -> Icons.Default.CloudDone
                    CloudBackupManifestStatus.UPLOADING -> Icons.Default.Sync
                    CloudBackupManifestStatus.FAILED -> Icons.Default.ErrorOutline
                },
                contentDescription = manifest.status.name,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatInstant(manifest.completedAt ?: manifest.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(com.suguru.expensetracker.R.string.cloud_manifest_chunks, stringResource(when(manifest.status) { CloudBackupManifestStatus.COMPLETE -> com.suguru.expensetracker.R.string.cloud_snapshot_status_complete; CloudBackupManifestStatus.UPLOADING -> com.suguru.expensetracker.R.string.cloud_snapshot_status_uploading; CloudBackupManifestStatus.FAILED -> com.suguru.expensetracker.R.string.cloud_snapshot_status_failed }), sizeText, manifest.chunkCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }
        }
        if (!isAutoMode && manifest.status == CloudBackupManifestStatus.COMPLETE) {
            TextButton(
                onClick = onRestore,
                enabled = !isBusy,
                modifier = Modifier.testTag("restore_cloud_snapshot_${manifest.snapshotId}")
            ) {
                Text(stringResource(com.suguru.expensetracker.R.string.ui_restore_now))
            }
        }
    }
}

@Composable
private fun CloudBackupUnavailableCard(modifier: Modifier = Modifier) {
    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Text(
            text = stringResource(com.suguru.expensetracker.R.string.cloud_unavailable_test),
            style = MaterialTheme.typography.bodySmall,
            color = ExpenseTrackerTheme.extendedColors.textSecondary
        )
    }
}

private fun formatInstant(instant: Instant): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault())
        instant.atZone(ZoneId.systemDefault()).format(formatter)
    } catch (_: Exception) {
        instant.toString()
    }
}
