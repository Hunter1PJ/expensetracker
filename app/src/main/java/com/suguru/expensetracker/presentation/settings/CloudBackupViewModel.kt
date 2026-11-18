package com.suguru.expensetracker.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

sealed interface CloudSnapshotsUiState {
    data object Loading : CloudSnapshotsUiState
    data class Success(val manifests: List<CloudBackupManifest>) : CloudSnapshotsUiState
    data class Error(val message: String) : CloudSnapshotsUiState
}

data class CloudRestoreCandidate(
    val manifest: CloudBackupManifest,
    val backup: ExpenseTrackerBackup
)

data class CloudBackupActionUiState(
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val pendingRestoreCandidate: CloudRestoreCandidate? = null
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
    private val orchestrator: com.suguru.expensetracker.data.backup.AutomaticCloudBackupOrchestrator? = null,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null,
    private val onPendingSettingsRecovery: suspend () -> InternalDefaultAccountProvisioningResult? = { null },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val codec = CloudBackupPayloadCodec()
    private val chunker = CloudBackupPayloadChunker()

    val syncStatus: StateFlow<com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus> =
        orchestrator?.syncStatus ?: MutableStateFlow(com.suguru.expensetracker.data.backup.AutomaticBackupSyncStatus.Idle)

    fun resolveConflictRestore(manifest: CloudBackupManifest) {
        if (_actionState.value.isBusy || orchestrator == null) return
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, busyMessage = "Restoring cloud snapshot...") }
            orchestrator.resolveConflictRestoreCloud(currentUserId, manifest)
            _actionState.update { it.copy(isBusy = false, busyMessage = null, successMessage = "Cloud snapshot restored.") }
        }
    }

    fun resolveConflictKeepLocal() {
        if (_actionState.value.isBusy || orchestrator == null) return
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, busyMessage = "Backing up local changes...") }
            orchestrator.resolveConflictKeepLocalAndBackup(currentUserId)
            _actionState.update { it.copy(isBusy = false, busyMessage = null, successMessage = "Local data backed up to cloud.") }
        }
    }

    val manifestsState: StateFlow<CloudSnapshotsUiState> = remoteRepository.observeManifests(currentUserId)
        .map<List<CloudBackupManifest>, CloudSnapshotsUiState> { CloudSnapshotsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing cloud backup manifests", rootError)
            emit(CloudSnapshotsUiState.Error(rootError.message ?: "Failed to load cloud backups"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CloudSnapshotsUiState.Loading
        )

    val localCloudState: StateFlow<CloudBackupState> = cloudBackupStateStore.observeState(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to read local cloud backup state", error)
            emit(CloudBackupState.EMPTY)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CloudBackupState.EMPTY
        )

    private val _actionState = MutableStateFlow(CloudBackupActionUiState())
    val actionState: StateFlow<CloudBackupActionUiState> = _actionState.asStateFlow()

    fun backUpNow() {
        if (_actionState.value.isBusy) return
        viewModelScope.launch {
            if (orchestrator != null) {
                _actionState.update {
                    it.copy(
                        isBusy = true,
                        busyMessage = "Syncing cloud backup...",
                        errorMessage = null,
                        successMessage = null
                    )
                }
                when (val res = orchestrator.performBackup(currentUserId)) {
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.Success -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, successMessage = "Cloud backup completed successfully.")
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.SkippedUnchanged -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, successMessage = "Cloud backup is already up to date.")
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.DeferredConflict -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = "Local and cloud data conflict detected.")
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.PermanentFailure -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = "Backup failed: ${res.failureCode}")
                        }
                    }
                    is com.suguru.expensetracker.data.backup.BackupExecutionResult.RetryableFailure -> {
                        _actionState.update {
                            it.copy(isBusy = false, busyMessage = null, errorMessage = "Backup queued (${res.failureCode}). Will upload when online.")
                        }
                    }
                }
                return@launch
            }
            _actionState.update {
                it.copy(
                    isBusy = true,
                    busyMessage = "Preparing encrypted cloud snapshot...",
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
                    it.copy(busyMessage = "Uploading ${chunked.totalChunkCount} chunk(s) to Firestore...")
                }

                val uploadResult = remoteRepository.uploadBackup(currentUserId, manifest, chunked)
                if (uploadResult.isSuccess) {
                    val committedManifest = uploadResult.getOrThrow()
                    val successEpochMillis = committedManifest.completedAt?.toEpochMilli()
                        ?: System.currentTimeMillis()
                    cloudBackupStateStore.setState(
                        currentUserId,
                        CloudBackupState(
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
                            successMessage = "Cloud backup completed (${chunked.totalChunkCount} chunk(s))."
                        )
                    }
                } else {
                    val failure = uploadResult.exceptionOrNull()
                    recordFailure(previousState, attemptEpochMillis, toFailureCode(failure))
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            errorMessage = failure?.localizedMessage ?: "Cloud backup upload failed."
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
                        errorMessage = e.localizedMessage ?: "Cloud backup failed."
                    )
                }
            }
        }
    }

    fun requestRestoreSnapshot(manifest: CloudBackupManifest) {
        if (_actionState.value.isBusy) return
        if (manifest.status != CloudBackupManifestStatus.COMPLETE) {
            _actionState.update {
                it.copy(errorMessage = "Only completed cloud snapshots can be restored.")
            }
            return
        }

        viewModelScope.launch {
            _actionState.update {
                it.copy(
                    isBusy = true,
                    busyMessage = "Downloading cloud backup chunks...",
                    errorMessage = null,
                    successMessage = null
                )
            }
            try {
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
                val currentLocalBackup = createFullBackupUseCase()
                if (LocalBackupStateClassifier.isEffectivelyEmpty(currentLocalBackup)) {
                    applyCloudRestore(backup, manifest)
                } else {
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            pendingRestoreCandidate = CloudRestoreCandidate(manifest, backup)
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = "Cloud restore failed: ${e.localizedMessage ?: "Unable to verify snapshot"}"
                    )
                }
            }
        }
    }

    fun confirmPendingRestore() {
        val candidate = _actionState.value.pendingRestoreCandidate ?: return
        if (_actionState.value.isBusy) return
        viewModelScope.launch {
            _actionState.update {
                it.copy(
                    isBusy = true,
                    busyMessage = "Restoring cloud snapshot to local database...",
                    pendingRestoreCandidate = null
                )
            }
            applyCloudRestore(candidate.backup, candidate.manifest)
        }
    }

    fun cancelPendingRestore() {
        _actionState.update { it.copy(pendingRestoreCandidate = null) }
    }

    fun deleteSnapshot(snapshotId: String) {
        if (_actionState.value.isBusy) return
        viewModelScope.launch {
            _actionState.update {
                it.copy(
                    isBusy = true,
                    busyMessage = "Removing cloud snapshot...",
                    errorMessage = null,
                    successMessage = null
                )
            }
            val result = remoteRepository.deleteSnapshot(currentUserId, snapshotId)
            _actionState.update {
                if (result.isSuccess) {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        successMessage = "Cloud snapshot removed."
                    )
                } else {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete snapshot."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _actionState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun applyCloudRestore(backup: ExpenseTrackerBackup, manifest: CloudBackupManifest) {
        val outcomeResult = restoreBackupUseCase(backup)
        if (outcomeResult.isFailure) {
            _actionState.update {
                it.copy(
                    isBusy = false,
                    busyMessage = null,
                    errorMessage = "Restore failed: ${outcomeResult.exceptionOrNull()?.localizedMessage ?: "Database error"}"
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
                lastSuccessfulBackupAtEpochMillis = manifest.completedAt?.toEpochMilli()
                    ?: previous.lastSuccessfulBackupAtEpochMillis,
                lastSuccessfulSnapshotId = manifest.snapshotId,
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
                successMessage = "Cloud backup restored successfully!"
            )
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
