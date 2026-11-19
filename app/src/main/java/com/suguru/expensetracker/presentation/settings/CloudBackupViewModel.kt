package com.suguru.expensetracker.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.R
import com.suguru.expensetracker.presentation.util.UiMessage
import com.suguru.expensetracker.domain.error.CloudBackupRemoteException
import com.suguru.expensetracker.domain.model.BackupContentFingerprint
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupPayloadCodec
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.model.LocalBackupStateClassifier
import com.suguru.expensetracker.domain.repository.BackupRepository
import com.suguru.expensetracker.domain.repository.CloudBackupFingerprintStore
import com.suguru.expensetracker.domain.repository.CloudBackupRemoteRepository
import com.suguru.expensetracker.domain.repository.CloudBackupStateStore
import com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase
import com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase
import com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID

sealed interface CloudSnapshotsUiState {
    data object Loading : CloudSnapshotsUiState
    data class Success(val manifests: List<CloudBackupManifest>) : CloudSnapshotsUiState
    data class Error(val message: String) : CloudSnapshotsUiState
}

sealed interface CloudRestoreResult {
    data class Success(val manifest: CloudBackupManifest) : CloudRestoreResult
    data class Failure(val reason: String) : CloudRestoreResult
}

data class CloudBackupActionUiState(
    val isBusy: Boolean = false,
    val busyMessage: UiMessage? = null,
    val errorMessage: UiMessage? = null,
    val successMessage: UiMessage? = null,
    val pendingRestoreCandidate: CloudBackupManifest? = null,
    val restoreResult: CloudRestoreResult? = null
)

/**
 * Auth-scoped ViewModel managing Firestore cloud backups for a single authenticated user [currentUserId].
 * Must only be instantiated while [currentUserId] matches a signed-in Firebase user and keyed by [currentUserId].
 */
class CloudBackupViewModel(
    private val currentUserId: String,
    private val remoteRepository: CloudBackupRemoteRepository,
    private val createFullBackupUseCase: CreateFullBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val backupRepository: BackupRepository,
    private val cloudBackupStateStore: CloudBackupStateStore,
    private val cloudBackupFingerprintStore: CloudBackupFingerprintStore,
    private val autoBackupPreferenceStore: com.suguru.expensetracker.domain.repository.AutoBackupPreferenceStore? = null,
    private val orchestrator: com.suguru.expensetracker.data.backup.AutomaticCloudBackupOrchestrator? = null,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null,
    private val onPendingSettingsRecovery: suspend () -> InternalDefaultAccountProvisioningResult? = { null },
    private val isWorkspaceIdentityCurrent: (uid: String) -> Boolean = { true },
    private val authenticatedUidProvider: () -> String? = { currentUserId },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val codec = CloudBackupPayloadCodec()
    private val chunker = CloudBackupPayloadChunker()

    val syncStatus: StateFlow<com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus> =
        if (orchestrator == null) {
            MutableStateFlow(com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Idle)
        } else {
            combine(orchestrator.syncStatus, orchestrator.syncStatusUid) { status, uid ->
                if (uid == currentUserId) status
                else com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Idle
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Idle
            )
        }

    val isAutoBackupEnabled: StateFlow<Boolean> = (autoBackupPreferenceStore?.observeAutoBackupEnabled(currentUserId)
        ?: kotlinx.coroutines.flow.flowOf(true))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true
        )

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            autoBackupPreferenceStore?.setAutoBackupEnabled(currentUserId, enabled)
            if (enabled && orchestrator != null) {
                // Reconcile both local changes and any cloud generation that arrived while disabled.
                orchestrator.evaluateOnLaunchOrSignIn(currentUserId)
            }
        }
    }

    fun resolveConflictRestore(manifest: CloudBackupManifest) {
        if (_actionState.value.isBusy || orchestrator == null) return
        if (!isCloudOperationIdentityCurrent("conflict restore start")) {
            _actionState.update { it.copy(errorMessage = UiMessage(R.string.cloud_workspace_changing)) }
            return
        }
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, busyMessage = UiMessage(R.string.cloud_busy_restoring)) }
            val restored = orchestrator.resolveConflictRestoreCloud(currentUserId, manifest)
            _actionState.update {
                if (restored) {
                    it.copy(isBusy = false, busyMessage = null, successMessage = UiMessage(R.string.cloud_snapshot_restored))
                } else {
                    it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_snapshot_restore_failed))
                }
            }
        }
    }

    fun resolveConflictKeepLocal() {
        if (_actionState.value.isBusy || orchestrator == null) return
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, busyMessage = UiMessage(R.string.cloud_busy_backing_up_local)) }
            when (val result = orchestrator.resolveConflictKeepLocalAndBackup(currentUserId)) {
                is com.suguru.expensetracker.data.backup.BackupExecutionResult.Success,
                com.suguru.expensetracker.data.backup.BackupExecutionResult.SkippedUnchanged -> {
                    _actionState.update {
                        it.copy(isBusy = false, busyMessage = null, successMessage = UiMessage(R.string.cloud_backup_success))
                    }
                }
                is com.suguru.expensetracker.data.backup.BackupExecutionResult.PermanentFailure -> {
                    _actionState.update {
                        it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_backup_failed))
                    }
                }
                is com.suguru.expensetracker.data.backup.BackupExecutionResult.RetryableFailure -> {
                    _actionState.update {
                        it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_backup_pending))
                    }
                }
                com.suguru.expensetracker.data.backup.BackupExecutionResult.DeferredConflict -> {
                    _actionState.update {
                        it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_conflict_decision_required))
                    }
                }
            }
        }
    }

    val manifestsState: StateFlow<CloudSnapshotsUiState> = remoteRepository.observeManifests(currentUserId)
        .map<List<CloudBackupManifest>, CloudSnapshotsUiState> { CloudSnapshotsUiState.Success(it) }
        .retryWhen { error, attempt ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            val retryable = error is java.io.IOException ||
                error is CloudBackupRemoteException.Unauthenticated ||
                error is CloudBackupRemoteException.UidMismatch ||
                error is FirebaseNetworkException ||
                firestoreError?.code in setOf(
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    FirebaseFirestoreException.Code.ABORTED
                )
            if (retryable) delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
            retryable
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing cloud backup manifests", rootError)
            emit(CloudSnapshotsUiState.Error(rootError.message ?: ""))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CloudSnapshotsUiState.Loading
        )

    val localCloudState: StateFlow<CloudBackupState> = cloudBackupStateStore.observeState(currentUserId)
        .retryWhen { error, attempt ->
            if (error is java.io.IOException) {
                Log.w(TAG, "Retrying transient cloud backup state read failure.")
                delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
                true
            } else {
                if (error !is CancellationException) {
                    Log.w(TAG, "Cloud backup state read failed (${error::class.java.simpleName}).")
                }
                false
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CloudBackupState.EMPTY
        )

    init {
        viewModelScope.launch {
            manifestsState.collect { snapshots ->
                val latest = (snapshots as? CloudSnapshotsUiState.Success)
                    ?.manifests
                    ?.firstOrNull { it.status == CloudBackupManifestStatus.COMPLETE }
                    ?: return@collect
                val preferenceEnabled = autoBackupPreferenceStore?.isAutoBackupEnabled(currentUserId) ?: true
                if (!preferenceEnabled || orchestrator == null) return@collect

                val localState = cloudBackupStateStore.getState(currentUserId)
                val generationAlreadyApplied = if (
                    latest.generationId != null && localState.lastSuccessfulRestoreGenerationId != null
                ) {
                    latest.generationId == localState.lastSuccessfulRestoreGenerationId
                } else {
                    latest.contentFingerprint == localState.lastSuccessfulRestoreFingerprint
                }
                if (!generationAlreadyApplied) {
                    orchestrator.scheduleAutomaticRestoreIfEligible(currentUserId)
                }
            }
        }
    }

    private val _actionState = MutableStateFlow(CloudBackupActionUiState())
    val actionState: StateFlow<CloudBackupActionUiState> = _actionState.asStateFlow()

    fun backUpNow() {
        if (_actionState.value.isBusy) return
        if (!isCloudOperationIdentityCurrent("manual backup start")) {
            _actionState.update { it.copy(errorMessage = UiMessage(R.string.cloud_workspace_changing)) }
            return
        }
        viewModelScope.launch {
            if (!isCloudOperationIdentityCurrent("manual backup execution")) {
                _actionState.update { it.copy(errorMessage = UiMessage(R.string.cloud_workspace_changing)) }
                return@launch
            }
            if (orchestrator != null) {
                _actionState.update {
                    it.copy(
                        isBusy = true,
                        busyMessage = UiMessage(R.string.cloud_busy_syncing),
                        errorMessage = null,
                        successMessage = null
                    )
                }
                when (val res = orchestrator.performBackup(currentUserId, explicitManual = true)) {
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.Success -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, successMessage = UiMessage(R.string.cloud_backup_success))
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.SkippedUnchanged -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, successMessage = UiMessage(R.string.cloud_backup_already_current))
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.DeferredConflict -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_local_cloud_conflict))
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.PermanentFailure -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_backup_failed))
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.RetryableFailure -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_backup_queued))
                        }
                    }
                }
                return@launch
            }
            _actionState.update {
                it.copy(
                    isBusy = true,
                    busyMessage = UiMessage(R.string.cloud_busy_preparing),
                    errorMessage = null,
                    successMessage = null
                )
            }
            val attemptEpochMillis = System.currentTimeMillis()
            val snapshotId = "snap_${attemptEpochMillis}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

            try {
                val backup = createFullBackupUseCase()
                val contentFingerprint = withContext(workDispatcher) {
                    BackupContentFingerprint.compute(backup)
                }
                val jsonBytes = withContext(workDispatcher) {
                    backupRepository.serializeBackup(backup).toByteArray(Charsets.UTF_8)
                }
                val compressedBytes = withContext(workDispatcher) {
                    codec.compress(jsonBytes).getOrThrow()
                }
                val chunked = withContext(workDispatcher) {
                    chunker.chunk(compressedBytes)
                }

                val previousState = cloudBackupStateStore.getState(currentUserId)
                cloudBackupStateStore.setState(
                    currentUserId,
                    previousState.copy(
                        pendingSnapshotId = snapshotId,
                        pendingSinceEpochMillis = attemptEpochMillis,
                        lastAttemptAtEpochMillis = attemptEpochMillis
                    )
                )

                val manifest = CloudBackupManifest(
                    snapshotId = snapshotId,
                    uid = currentUserId,
                    status = CloudBackupManifestStatus.UPLOADING,
                    createdAt = Instant.ofEpochMilli(attemptEpochMillis),
                    completedAt = null,
                    formatVersion = backup.formatVersion,
                    databaseSchemaVersion = backup.databaseSchemaVersion,
                    appVersion = backup.appVersion.ifBlank { "1.0.0" }.take(64),
                    chunkCount = chunked.totalChunkCount,
                    compressedBytes = compressedBytes.size.toLong(),
                    uncompressedBytes = jsonBytes.size.toLong(),
                    payloadSha256 = chunked.payloadSha256,
                    contentFingerprint = contentFingerprint
                )

                _actionState.update {
                    it.copy(busyMessage = UiMessage(R.string.cloud_busy_uploading_chunks, listOf(chunked.totalChunkCount)) )
                }

                if (!isCloudOperationIdentityCurrent("manual backup upload")) {
                    _actionState.update {
                        it.copy(isBusy = false, busyMessage = null, errorMessage = UiMessage(R.string.cloud_workspace_changed))
                    }
                    return@launch
                }
                val uploadResult = remoteRepository.uploadBackup(currentUserId, manifest, chunked)
                if (uploadResult.isSuccess) {
                    val committedManifest = uploadResult.getOrThrow()
                    val successEpochMillis = committedManifest.completedAt?.toEpochMilli()
                        ?: System.currentTimeMillis()
                    cloudBackupStateStore.setState(
                        currentUserId,
                        previousState.copy(
                            lastSuccessfulBackupAtEpochMillis = successEpochMillis,
                            lastSuccessfulSnapshotId = committedManifest.snapshotId,
                            pendingSnapshotId = null,
                            pendingSinceEpochMillis = null,
                            lastAttemptAtEpochMillis = attemptEpochMillis,
                            lastFailureAtEpochMillis = null,
                            lastFailureCode = null
                        )
                    )
                    cloudBackupFingerprintStore.setLastUploadedFingerprint(currentUserId, contentFingerprint)
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            successMessage = UiMessage(R.string.cloud_backup_success)
                        )
                    }
                } else {
                    val failure = uploadResult.exceptionOrNull()
                    recordFailure(previousState, attemptEpochMillis, toFailureCode(failure))
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            errorMessage = UiMessage(R.string.cloud_upload_failed)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val previousState = try {
                    cloudBackupStateStore.getState(currentUserId)
                } catch (_: Exception) {
                    CloudBackupState.EMPTY
                }
                recordFailure(previousState, attemptEpochMillis, toFailureCode(e))
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = UiMessage(R.string.cloud_backup_failed)
                    )
                }
            }
        }
    }

    fun requestRestoreSnapshot(manifest: CloudBackupManifest) {
        if (_actionState.value.isBusy) return
        if (!isCloudOperationIdentityCurrent("manual restore selection")) {
            _actionState.update { it.copy(errorMessage = UiMessage(R.string.cloud_workspace_changing)) }
            return
        }
        if (autoBackupPreferenceStore != null && isAutoBackupEnabled.value) {
            _actionState.update { it.copy(errorMessage = UiMessage(R.string.cloud_manual_restore_disabled)) }
            return
        }
        if (manifest.status != CloudBackupManifestStatus.COMPLETE || manifest.uid != currentUserId) {
            _actionState.update {
                it.copy(errorMessage = UiMessage(R.string.cloud_completed_snapshot_required))
            }
            return
        }

        // Show confirmation dialog first without starting download or restoration
        _actionState.update {
            it.copy(
                pendingRestoreCandidate = manifest,
                restoreResult = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun confirmPendingRestore() {
        val manifest = _actionState.value.pendingRestoreCandidate ?: return
        if (_actionState.value.isBusy) return
        if (!isCloudOperationIdentityCurrent("manual restore start")) {
            _actionState.update {
                it.copy(
                    pendingRestoreCandidate = null,
                    restoreResult = CloudRestoreResult.Failure("Your account workspace is still changing. Please wait and try again.")
                )
            }
            return
        }
        _actionState.update {
            it.copy(
                isBusy = true,
                busyMessage = UiMessage(R.string.cloud_busy_restoring),
                pendingRestoreCandidate = null,
                restoreResult = null,
                errorMessage = null,
                successMessage = null
            )
        }
        viewModelScope.launch {
            try {
                if (autoBackupPreferenceStore?.isAutoBackupEnabled(currentUserId) == true) {
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            restoreResult = CloudRestoreResult.Failure(
                                "Manual restore is unavailable while automatic backup is enabled."
                            )
                        )
                    }
                    return@launch
                }
                val chunks = remoteRepository.downloadChunks(currentUserId, manifest.snapshotId)
                    .getOrThrow()
                val compressedBytes = withContext(workDispatcher) {
                    chunker.reassemble(chunks, manifest.payloadSha256).getOrThrow()
                }
                val jsonBytes = withContext(workDispatcher) {
                    codec.decompress(compressedBytes).getOrThrow()
                }
                val backup = withContext(workDispatcher) {
                    backupRepository.deserializeBackup(jsonBytes.toString(Charsets.UTF_8)).getOrThrow()
                }
                applyCloudRestore(backup, manifest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordRestoreFailure(toFailureCode(e), manifest)
                val failureReason = "Cloud snapshot restore failed (${e::class.java.simpleName})."
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        restoreResult = CloudRestoreResult.Failure(failureReason)
                    )
                }
            }
        }
    }

    fun cancelPendingRestore() {
        _actionState.update { it.copy(pendingRestoreCandidate = null) }
    }

    fun dismissRestoreResult() {
        _actionState.update { it.copy(restoreResult = null) }
    }

    fun clearMessages() {
        _actionState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun applyCloudRestore(backup: ExpenseTrackerBackup, manifest: CloudBackupManifest) {
        val authenticatedUidMatches = runCatching { authenticatedUidProvider() == currentUserId }.getOrDefault(false)
        val workspaceMatches = runCatching { isWorkspaceIdentityCurrent(currentUserId) }.getOrDefault(false)
        val orchestratorUidMatches = orchestrator == null || orchestrator.currentAuthenticatedUid() == currentUserId
        if (manifest.uid != currentUserId ||
            manifest.status != CloudBackupManifestStatus.COMPLETE ||
            !authenticatedUidMatches ||
            !workspaceMatches ||
            !orchestratorUidMatches
        ) {
            Log.w(TAG, "Manual cloud operation blocked at manual restore pre-commit (authenticated=${authenticatedUidMatches}, workspaceMatches=${workspaceMatches}, accountSwitchDetected=${!authenticatedUidMatches}).")
            val reason = when {
                manifest.uid != currentUserId || manifest.status != CloudBackupManifestStatus.COMPLETE ->
                    "The selected cloud snapshot is no longer valid for this account. Nothing was restored."
                !authenticatedUidMatches ->
                    "The signed-in account changed. Nothing was restored."
                else ->
                    "The active account workspace changed before restore. Nothing was restored."
            }
            _actionState.update {
                it.copy(
                    isBusy = false,
                    busyMessage = null,
                    restoreResult = CloudRestoreResult.Failure(reason)
                )
            }
            return
        }

        val outcomeResult = restoreBackupUseCase(backup) {
            val stillAuthenticated = runCatching { authenticatedUidProvider() == currentUserId }.getOrDefault(false)
            val stillInWorkspace = runCatching { isWorkspaceIdentityCurrent(currentUserId) }.getOrDefault(false)
            val orchestratorStillScoped = orchestrator == null ||
                orchestrator.currentAuthenticatedUid() == currentUserId
            val automaticModeStillOff =
                autoBackupPreferenceStore?.isAutoBackupEnabled(currentUserId) != true
            manifest.uid == currentUserId &&
                manifest.status == CloudBackupManifestStatus.COMPLETE &&
                stillAuthenticated &&
                stillInWorkspace &&
                orchestratorStillScoped &&
                automaticModeStillOff
        }
        if (outcomeResult.isFailure) {
            val failure = outcomeResult.exceptionOrNull()
            recordRestoreFailure(toFailureCode(failure), manifest)
            val failureReason = "Database restore failed (${failure?.javaClass?.simpleName ?: "unknown"})."
            _actionState.update {
                it.copy(
                    isBusy = false,
                    busyMessage = null,
                    restoreResult = CloudRestoreResult.Failure(failureReason)
                )
            }
            return
        }

        if (!isCloudOperationIdentityCurrent("manual restore post-commit")) {
            _actionState.update {
                it.copy(
                    isBusy = false,
                    busyMessage = null,
                    errorMessage = UiMessage(R.string.cloud_workspace_changing)
                )
            }
            return
        }

        widgetRefreshCoordinator?.refreshAll()
        val fingerprint = withContext(workDispatcher) {
            BackupContentFingerprint.compute(backup)
        }
        cloudBackupFingerprintStore.setLastUploadedFingerprint(currentUserId, fingerprint)

        val previous = cloudBackupStateStore.getState(currentUserId)
        cloudBackupStateStore.setState(
            currentUserId,
            previous.copy(
                lastSuccessfulBackupAtEpochMillis = manifest.completedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                lastSuccessfulRestoreAtEpochMillis = System.currentTimeMillis(),
                lastRestoreFailureAtEpochMillis = null,
                lastRestoreFailureCode = null,
                lastRestoreFailureGenerationId = null,
                lastRestoreFailureFingerprint = null,
                lastSuccessfulSnapshotId = manifest.snapshotId,
                lastSuccessfulRestoreGenerationId = manifest.generationId,
                lastSuccessfulRestoreFingerprint = manifest.contentFingerprint,
                pendingRestore = false,
                pendingSnapshotId = null,
                pendingSinceEpochMillis = null,
                lastFailureAtEpochMillis = null,
                lastFailureCode = null
            )
        )

        val outcome = outcomeResult.getOrNull()
        if (outcome == BackupRestoreOutcome.SettingsRecoveryPending) {
            onPendingSettingsRecovery()
        }
        _actionState.update {
            it.copy(
                isBusy = false,
                busyMessage = null,
                pendingRestoreCandidate = null,
                restoreResult = CloudRestoreResult.Success(manifest)
            )
        }
    }

    private fun isCloudOperationIdentityCurrent(stage: String): Boolean {
        val authenticatedUidMatches = runCatching { authenticatedUidProvider() == currentUserId }.getOrDefault(false)
        val workspaceMatches = runCatching { isWorkspaceIdentityCurrent(currentUserId) }.getOrDefault(false)
        val orchestratorUidMatches = orchestrator == null || orchestrator.currentAuthenticatedUid() == currentUserId
        val matches = authenticatedUidMatches && workspaceMatches && orchestratorUidMatches
        if (!matches) {
            Log.w(TAG, "Manual cloud operation blocked at $stage (authenticated=${authenticatedUidMatches}, workspaceMatches=${workspaceMatches}, accountSwitchDetected=${!authenticatedUidMatches}).")
        }
        return matches
    }

    private suspend fun recordRestoreFailure(
        failureCode: String,
        manifest: CloudBackupManifest? = null
    ) {
        val safeCode = failureCode.takeIf(CloudBackupState::isValidFailureCode) ?: "RESTORE_FAILED"
        try {
            val previous = cloudBackupStateStore.getState(currentUserId)
            cloudBackupStateStore.setState(
                currentUserId,
                previous.copy(
                    lastRestoreFailureAtEpochMillis = System.currentTimeMillis(),
                    lastRestoreFailureCode = safeCode,
                    lastRestoreFailureGenerationId = manifest?.generationId,
                    lastRestoreFailureFingerprint = manifest?.contentFingerprint,
                    pendingRestore = false
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Unable to persist restore failure state (${e::class.java.simpleName})")
        }
    }

    private suspend fun recordFailure(
        previousState: CloudBackupState,
        attemptEpochMillis: Long,
        failureCode: String
    ) {
        try {
            cloudBackupStateStore.setState(
                currentUserId,
                previousState.copy(
                    pendingSnapshotId = null,
                    pendingSinceEpochMillis = null,
                    lastAttemptAtEpochMillis = attemptEpochMillis,
                    lastFailureAtEpochMillis = System.currentTimeMillis(),
                    lastFailureCode = failureCode
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Unable to persist cloud failure state", e)
        }
    }

    private fun toFailureCode(error: Throwable?): String {
        return when (error) {
            is CloudBackupRemoteException -> error.failureCode
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "PERMISSION_DENIED"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "NETWORK_UNAVAILABLE"
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "QUOTA_EXCEEDED"
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "UNAUTHENTICATED"
                else -> "FIRESTORE_ERROR"
            }
            is FirebaseNetworkException -> "NETWORK_UNAVAILABLE"
            is IllegalArgumentException -> "VALIDATION_ERROR"
            else -> "UPLOAD_FAILED"
        }
    }

    private companion object {
        const val TAG = "CloudBackupVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
