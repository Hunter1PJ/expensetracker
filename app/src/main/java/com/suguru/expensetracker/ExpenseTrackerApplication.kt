package com.suguru.expensetracker

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.worker.RecurringTransactionWorker
import com.suguru.expensetracker.data.ai.StartupAiKeyVerification
import kotlinx.coroutines.flow.first
import com.suguru.expensetracker.di.AppContainer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for ExpenseTracker, maintaining the application-scoped dependency graph.
 */
class ExpenseTrackerApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiVerificationMutex = Mutex()
    @Volatile private var aiVerificationAttemptedOnCurrentNetwork = false

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)

        RecurringTransactionWorker.enqueuePeriodicWork(this)
        applicationScope.launch { verifySavedAiKeyAtStartup() }
        registerAiVerificationConnectivityRetry()

        val firebaseReady = com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(this)

        applicationScope.launch {
            when (val result = appContainer.ensureInternalDefaultAccountProvisioning()) {
                is InternalDefaultAccountProvisioningResult.Ready -> {
                    Log.i("ExpenseTrackerApplication", "Internal default account ready: ${result.internalAccount.id}")
                }
                is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                    Log.w("ExpenseTrackerApplication", "Internal default account requires a one-time preferred currency selection.")
                }
                is InternalDefaultAccountProvisioningResult.Failure -> {
                    Log.e("ExpenseTrackerApplication", "Internal default account provisioning failed", result.error)
                }
            }

            try {
                appContainer.processDueRecurringTransactionsUseCase()
            } catch (e: Exception) {
                Log.w("ExpenseTrackerApplication", "Recurring transaction processing deferred until provisioning is ready.", e)
            }

            if (firebaseReady) {
                try {
                    val currentUid = Firebase.auth.currentUser?.uid
                    if (currentUid != null) {
                        appContainer.cloudBackupOrchestrator.evaluateOnLaunchOrSignIn(currentUid)
                    }
                } catch (e: Exception) {
                    Log.w("ExpenseTrackerApplication", "Initial launch cloud backup evaluation failed", e)
                }
            }
        }

        if (firebaseReady) {
            registerCloudRestoreConnectivityRetry()
            try {
                var previousUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
                Firebase.auth.addAuthStateListener(
                    FirebaseAuth.AuthStateListener { auth ->
                        val currentUser = auth.currentUser
                        val newUid = currentUser?.uid
                        if (newUid != previousUid) {
                            val oldUid = previousUid
                            previousUid = newUid
                            applicationScope.launch {
                                if (newUid != null) {
                                    appContainer.cloudBackupOrchestrator.evaluateOnLaunchOrSignIn(newUid)
                                } else {
                                    appContainer.cloudBackupOrchestrator.onUserSignedOut(oldUid)
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w("ExpenseTrackerApplication", "Failed to register auth state listener", e)
            }
        }
    }

    private suspend fun verifySavedAiKeyAtStartup() {
        aiVerificationMutex.withLock {
            if (aiVerificationAttemptedOnCurrentNetwork) return
            aiVerificationAttemptedOnCurrentNetwork = true
            when (StartupAiKeyVerification(
                appContainer.aiRepository,
                ::hasValidatedInternetConnection
            ).verify()) {
                StartupAiKeyVerification.Outcome.SKIPPED_DISABLED,
                StartupAiKeyVerification.Outcome.SKIPPED_NO_KEY,
                StartupAiKeyVerification.Outcome.VERIFIED,
                StartupAiKeyVerification.Outcome.OTHER_FAILURE -> Unit
                StartupAiKeyVerification.Outcome.INVALID_KEY,
                StartupAiKeyVerification.Outcome.SKIPPED_ALREADY_INVALID -> {
                    if (appContainer.aiRepository.isAiFeatureEnabled.first()) {
                        appContainer.reportStartupAiInvalidKey()
                    }
                }
                StartupAiKeyVerification.Outcome.RETRY_WHEN_CONNECTED -> {
                    if (!hasValidatedInternetConnection()) {
                        aiVerificationAttemptedOnCurrentNetwork = false
                    }
                }
            }
        }
    }

    private fun hasValidatedInternetConnection(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerAiVerificationConnectivityRetry() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: android.net.Network) {
                    aiVerificationAttemptedOnCurrentNetwork = false
                }

                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                        aiVerificationAttemptedOnCurrentNetwork
                    ) return
                    applicationScope.launch {
                        val repository = appContainer.aiRepository
                        if (!repository.isAiFeatureEnabled.first() ||
                            !repository.isAiConfigured.first() ||
                            repository.isKeyVerified.first() ||
                            repository.isKeyInvalid.first()
                        ) return@launch
                        verifySavedAiKeyAtStartup()
                    }
                }
            })
        }
    }

    private fun registerCloudRestoreConnectivityRetry() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: android.net.Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                            !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ) return
                        val uid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
                        if (!uid.isNullOrBlank()) {
                            applicationScope.launch {
                                runCatching {
                                    appContainer.cloudBackupOrchestrator.evaluateOnLaunchOrSignIn(uid)
                                }.onFailure { error ->
                                    Log.w("ExpenseTrackerApplication", "Connectivity restore eligibility check failed", error)
                                }
                            }
                        }
                    }
                }
            )
        }.onFailure { error ->
            Log.w("ExpenseTrackerApplication", "Failed to register cloud restore connectivity retry", error)
        }
    }
}
