package com.suguru.expensetracker

import android.app.Application
import android.content.Context
import com.suguru.expensetracker.presentation.util.AppLanguageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.ai.StartupAiKeyVerification
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabaseProvider
import com.suguru.expensetracker.data.local.LegacyWorkspaceDatabaseMigrator
import com.suguru.expensetracker.data.local.WorkspaceId
import com.suguru.expensetracker.data.worker.RecurringTransactionWorker
import com.suguru.expensetracker.di.AppContainer
import com.suguru.expensetracker.domain.model.LocalBackupStateClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GuestImportPrompt(val uid: String)

class ExpenseTrackerApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    private val containers = LinkedHashMap<String, AppContainer>()
    private val workspaceMutex = Mutex()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiVerificationMutex = Mutex()
    private val _activeAppContainer = MutableStateFlow<AppContainer?>(null)
    val activeAppContainer: StateFlow<AppContainer?> = _activeAppContainer.asStateFlow()
    private val _guestImportPrompt = MutableStateFlow<GuestImportPrompt?>(null)
    val guestImportPrompt: StateFlow<GuestImportPrompt?> = _guestImportPrompt.asStateFlow()
    private val _workspaceMigrationWarning = MutableStateFlow(false)
    val workspaceMigrationWarning: StateFlow<Boolean> = _workspaceMigrationWarning.asStateFlow()

    val appContainer: AppContainer
        get() = checkNotNull(_activeAppContainer.value) { "Workspace is not initialized yet." }

    @Volatile private var aiVerificationAttemptedOnCurrentNetwork = false
    @Volatile private var activeAiUid: String? = null
    @Volatile private var observedAuthUid: String? = null
    private var aiVerificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            AppLanguageManager.languageChanges.collect { languageTag ->
                if (languageTag != null) _activeAppContainer.value?.widgetRefreshCoordinator?.refreshAll()
            }
        }
        applicationScope.launch {
            val migrationResult = runCatching {
                withContext(Dispatchers.IO) {
                    LegacyWorkspaceDatabaseMigrator.migrate(this@ExpenseTrackerApplication)
                }
            }.getOrElse { error ->
                Log.e(TAG, "Legacy database migration could not be completed; source data was preserved.", error)
                LegacyWorkspaceDatabaseMigrator.Result.GUEST_COLLISION
            }
            _workspaceMigrationWarning.value =
                migrationResult == LegacyWorkspaceDatabaseMigrator.Result.GUEST_COLLISION ||
                    LegacyWorkspaceDatabaseMigrator.hasRecoverableCollision(this@ExpenseTrackerApplication)

            _activeAppContainer.value = containerFor(WorkspaceId.Guest)
            RecurringTransactionWorker.enqueuePeriodicWork(this@ExpenseTrackerApplication)
            registerAiAuthStateVerification()
            registerAiVerificationConnectivityRetry()

            val guest = appContainer
            logProvisioningResult(guest.ensureInternalDefaultAccountProvisioning())
            runCatching { guest.processDueRecurringTransactionsUseCase() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Guest recurring transaction processing deferred.", error)
                }

            if (!com.suguru.expensetracker.presentation.auth.isFirebaseInitialized(this@ExpenseTrackerApplication)) return@launch
            registerCloudRestoreConnectivityRetry()
            try {
                observedAuthUid = Firebase.auth.currentUser?.uid
                Firebase.auth.addAuthStateListener(
                    FirebaseAuth.AuthStateListener { auth ->
                        val newUid = auth.currentUser?.uid
                        if (newUid == observedAuthUid) return@AuthStateListener
                        val oldUid = observedAuthUid
                        observedAuthUid = newUid
                        applicationScope.launch { resolveAuthenticatedWorkspace(oldUid, newUid) }
                    }
                )
                observedAuthUid?.let { uid ->
                    resolveAuthenticatedWorkspace(null, uid)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Failed to register auth state listener", error)
            }
        }
    }

    fun containerFor(uid: String?): AppContainer = containerFor(WorkspaceId.forUid(uid))

    fun isActiveWorkspaceCurrent(): Boolean {
        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        return _activeAppContainer.value?.workspaceId == WorkspaceId.forUid(uid)
    }

    fun isWorkspaceActive(uid: String): Boolean {
        val authenticatedUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        return authenticatedUid == uid &&
            _activeAppContainer.value?.workspaceId == WorkspaceId.FirebaseAccount(uid)
    }

    fun containerFor(workspaceId: WorkspaceId): AppContainer = synchronized(containers) {
        containers.getOrPut(workspaceId.storageKey) { AppContainer(this, workspaceId) }
    }

    suspend fun resolveGuestImport(uid: String, importGuestData: Boolean) {
        workspaceMutex.withLock {
            if (Firebase.auth.currentUser?.uid != uid || _guestImportPrompt.value?.uid != uid) return
            val prefs = getSharedPreferences(GUEST_IMPORT_PREFS, MODE_PRIVATE)
            val decisionKey = guestDecisionKey(uid)

            if (importGuestData) {
                val target = WorkspaceId.FirebaseAccount(uid)
                val priorDecision = prefs.getString(decisionKey, null)
                val copied = withContext(Dispatchers.IO) {
                    if (priorDecision == IMPORT_PENDING &&
                        ExpenseTrackerDatabaseProvider.databaseExists(this@ExpenseTrackerApplication, target)
                    ) {
                        LegacyWorkspaceDatabaseMigrator.isWorkspaceDatabaseReadable(this@ExpenseTrackerApplication, target)
                    } else {
                        ExpenseTrackerDatabaseProvider.closeDatabase(WorkspaceId.Guest)
                        synchronized(containers) { containers.remove(WorkspaceId.Guest.storageKey) }
                        prefs.edit().putString(decisionKey, IMPORT_PENDING).commit()
                        LegacyWorkspaceDatabaseMigrator.cloneWorkspace(this@ExpenseTrackerApplication, WorkspaceId.Guest, target)
                    }
                }
                if (!copied) {
                    _activeAppContainer.value = containerFor(WorkspaceId.Guest)
                    _workspaceMigrationWarning.value = true
                    return
                }
                prefs.edit().putString(decisionKey, IMPORT_COMPLETE).commit()
                runCatching { containerFor(uid).cloudBackupFingerprintStore.clearLastUploadedFingerprint(uid) }
                    .onFailure { Log.w(TAG, "Unable to reset the imported workspace backup fingerprint.", it) }
            } else {
                prefs.edit().putString(decisionKey, IMPORT_DECLINED).commit()
            }

            _guestImportPrompt.value = null
            activateWorkspace(uid)
            if (importGuestData) {
                runCatching { appContainer.cloudBackupOrchestrator.onLocalDataMutated() }
                    .onFailure { Log.w(TAG, "Initial imported workspace backup could not be scheduled.", it) }
            }
        }
    }

    private suspend fun resolveAuthenticatedWorkspace(oldUid: String?, newUid: String?) {
        workspaceMutex.withLock {
            if (Firebase.auth.currentUser?.uid != newUid) return
            if (!oldUid.isNullOrBlank() && oldUid != newUid) {
                runCatching { containerFor(oldUid).cloudBackupOrchestrator.onUserSignedOut(oldUid) }
                    .onFailure { Log.w(TAG, "Previous account cloud work cancellation failed.", it) }
            }
            if (newUid == null) {
                _guestImportPrompt.value = null
                activateWorkspace(null)
                return
            }

            val newWorkspace = WorkspaceId.FirebaseAccount(newUid)
            val targetExists = ExpenseTrackerDatabaseProvider.databaseExists(this, newWorkspace)
            val wasGuestWorkspace = _activeAppContainer.value?.workspaceId == WorkspaceId.Guest

            if (wasGuestWorkspace && !targetExists && shouldAskGuestImport(newUid)) {
                _guestImportPrompt.value = GuestImportPrompt(newUid)
                return
            }
            _guestImportPrompt.value = null
            activateWorkspace(newUid)
        }
    }

    private suspend fun shouldAskGuestImport(uid: String): Boolean {
        val prefs = getSharedPreferences(GUEST_IMPORT_PREFS, MODE_PRIVATE)
        if (prefs.getString(guestDecisionKey(uid), null) != null) return false

        val guest = containerFor(WorkspaceId.Guest)
        val guestBackup = runCatching { guest.backupRepository.createSnapshot() }.getOrNull() ?: return false
        if (LocalBackupStateClassifier.isEffectivelyEmpty(guestBackup)) return false

        val cloud = guest.cloudBackupRemoteRepository.getLatestCompleteManifest()
        return cloud.isSuccess && cloud.getOrNull() == null
    }

    private suspend fun activateWorkspace(uid: String?) {
        if (uid != null && Firebase.auth.currentUser?.uid != uid) return
        val workspace = WorkspaceId.forUid(uid)
        val container = containerFor(workspace)
        _activeAppContainer.value = container
        container.widgetRefreshCoordinator.refreshAll()
        logProvisioningResult(container.ensureInternalDefaultAccountProvisioning())
        runCatching { container.processDueRecurringTransactionsUseCase() }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Recurring transaction processing deferred for " + workspace.storageKey, error)
            }
        if (uid != null && Firebase.auth.currentUser?.uid == uid) {
            runCatching { container.cloudBackupOrchestrator.evaluateOnLaunchOrSignIn(uid) }
                .onFailure { Log.w(TAG, "Initial cloud reconciliation failed.", it) }
        }
    }

    private fun logProvisioningResult(result: InternalDefaultAccountProvisioningResult) {
        when (result) {
            is InternalDefaultAccountProvisioningResult.Ready -> Log.i(TAG, "Internal default account ready.")
            is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection ->
                Log.w(TAG, "Internal default account requires preferred currency selection.")
            is InternalDefaultAccountProvisioningResult.Failure ->
                Log.e(TAG, "Internal default account provisioning failed.", result.error)
        }
    }

    private fun guestDecisionKey(uid: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "decision_$digest"
    }

    private fun registerAiAuthStateVerification() {
        applicationScope.launch {
            var previousProfile: String? = null
            containerFor(WorkspaceId.Guest).activeAiProfileFlow.collect { profile ->
                if (profile == previousProfile) return@collect
                previousProfile = profile
                activeAiUid = profile
                aiVerificationAttemptedOnCurrentNetwork = false
                aiVerificationJob?.cancel()
                aiVerificationJob = applicationScope.launch { verifySavedAiKeyAtStartup(profile) }
            }
        }
    }

    private suspend fun verifySavedAiKeyAtStartup(expectedProfile: String) {
        aiVerificationMutex.withLock {
            val container = appContainer
            if (activeAiUid != expectedProfile ||
                container.aiRepository.activeAiProfileId.first() != expectedProfile ||
                aiVerificationAttemptedOnCurrentNetwork
            ) return
            aiVerificationAttemptedOnCurrentNetwork = true
            when (StartupAiKeyVerification(container.aiRepository, ::hasValidatedInternetConnection).verify()) {
                StartupAiKeyVerification.Outcome.SKIPPED_SIGNED_OUT,
                StartupAiKeyVerification.Outcome.SKIPPED_DISABLED,
                StartupAiKeyVerification.Outcome.SKIPPED_NO_KEY,
                StartupAiKeyVerification.Outcome.VERIFIED,
                StartupAiKeyVerification.Outcome.OTHER_FAILURE -> Unit
                StartupAiKeyVerification.Outcome.INVALID_KEY,
                StartupAiKeyVerification.Outcome.SKIPPED_ALREADY_INVALID -> {
                    if (activeAiUid == expectedProfile &&
                        container.aiRepository.activeAiProfileId.first() == expectedProfile &&
                        container.aiRepository.isAiFeatureEnabled.first() &&
                        expectedProfile != com.suguru.expensetracker.domain.ai.AiRepository.GUEST_PROFILE_ID
                    ) container.reportStartupAiInvalidKey(expectedProfile)
                }
                StartupAiKeyVerification.Outcome.RETRY_WHEN_CONNECTED ->
                    if (activeAiUid == expectedProfile && !hasValidatedInternetConnection()) {
                        aiVerificationAttemptedOnCurrentNetwork = false
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
                        if (!repository.isAiFeatureEnabled.first() || !repository.isAiConfigured.first() ||
                            repository.isKeyVerified.first() || repository.isKeyInvalid.first()
                        ) return@launch
                        verifySavedAiKeyAtStartup(appContainer.activeAiProfileFlow.first())
                    }
                }
            })
        }
    }

    private fun registerCloudRestoreConnectivityRetry() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) return
                    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                    if (!uid.isNullOrBlank()) applicationScope.launch {
                        runCatching { containerFor(uid).cloudBackupOrchestrator.evaluateOnLaunchOrSignIn(uid) }
                            .onFailure { Log.w(TAG, "Connectivity restore eligibility check failed.", it) }
                    }
                }
            })
        }.onFailure { Log.w(TAG, "Cloud restore connectivity callback registration failed.", it) }
    }

    companion object {
        private const val TAG = "ExpenseTrackerApplication"
        private const val GUEST_IMPORT_PREFS = "guest_workspace_import"
        private const val IMPORT_PENDING = "pending"
        private const val IMPORT_COMPLETE = "complete"
        private const val IMPORT_DECLINED = "declined"
    }
}
