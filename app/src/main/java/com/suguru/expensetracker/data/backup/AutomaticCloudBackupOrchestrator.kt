package com.suguru.expensetracker.data.backup

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.remote.firestore.FirestoreCloudBackupRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

sealed interface AutomaticBackupSyncStatus {
    data object Idle : AutomaticBackupSyncStatus
    data object Checking : AutomaticBackupSyncStatus
    data class BackupInProgress(val snapshotId: String) : AutomaticBackupSyncStatus
    data class RestoreInProgress(val snapshotId: String) : AutomaticBackupSyncStatus
    data class RestoreCompleted(val snapshotId: String, val timestampEpochMillis: Long) : AutomaticBackupSyncStatus
    data class Conflict(val uid: String, val remoteManifest: CloudBackupManifest) : AutomaticBackupSyncStatus
    data class Failure(val code: String, val message: String) : AutomaticBackupSyncStatus
}

sealed interface BackupExecutionResult {
    data class Success(val snapshotId: String) : BackupExecutionResult
    data object SkippedUnchanged : BackupExecutionResult
    data object DeferredConflict : BackupExecutionResult
    data class PermanentFailure(val failureCode: String) : BackupExecutionResult
    data class RetryableFailure(val failureCode: String) : BackupExecutionResult
}

class AutomaticCloudBackupOrchestrator(
    private val remoteRepository: CloudBackupRemoteRepository,
    private val backupRepository: BackupRepository,
    private val createFullBackupUseCase: CreateFullBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val stateStore: CloudBackupStateStore,
    private val fingerprintStore: CloudBackupFingerprintStore,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null,
    private val onScheduleDebouncedBackup: (uid: String) -> Unit = {},
    private val onCancelBackupWork: (uid: String) -> Unit = {},
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val codec = CloudBackupPayloadCodec()
    private val chunker = CloudBackupPayloadChunker()
    private val syncMutex = Mutex()

    private val _syncStatus = MutableStateFlow<AutomaticBackupSyncStatus>(AutomaticBackupSyncStatus.Idle)
    val syncStatus: StateFlow<AutomaticBackupSyncStatus> = _syncStatus.asStateFlow()

    fun currentAuthenticatedUid(): String? {
        return try {
            Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun performBackup(
        expectedUid: String,
        forceAfterConflictResolution: Boolean = false
    ): BackupExecutionResult = syncMutex.withLock {
        val authUid = currentAuthenticatedUid()
        if (authUid == null || authUid != expectedUid) {
            return@withLock BackupExecutionResult.PermanentFailure("UID_MISMATCH")
        }

        val attemptEpochMillis = System.currentTimeMillis()
        val snapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID

        try {
            val backup = createFullBackupUseCase()
            val contentFingerprint = withContext(workDispatcher) {
                BackupContentFingerprint.compute(backup)
            }
            val lastUploadedFp = fingerprintStore.getLastUploadedFingerprint(expectedUid)
            val prevState = stateStore.getState(expectedUid)

            if (lastUploadedFp == contentFingerprint && prevState.pendingSnapshotId == null && !forceAfterConflictResolution) {
                return@withLock BackupExecutionResult.SkippedUnchanged
            }

            if (!forceAfterConflictResolution && lastUploadedFp == null && !LocalBackupStateClassifier.isEffectivelyEmpty(backup)) {
                val remoteResult = remoteRepository.getLatestCompleteManifest()
                if (remoteResult.isSuccess) {
                    val latestRemote = remoteResult.getOrNull()
                    if (latestRemote != null) {
                        if (latestRemote.contentFingerprint == contentFingerprint) {
                            fingerprintStore.setLastUploadedFingerprint(expectedUid, contentFingerprint)
                            stateStore.setState(
                                expectedUid,
                                prevState.copy(
                                    lastSuccessfulBackupAtEpochMillis = latestRemote.completedAt?.toEpochMilli() ?: attemptEpochMillis,
                                    lastSuccessfulSnapshotId = latestRemote.snapshotId,
                                    pendingSnapshotId = null,
                                    pendingSinceEpochMillis = null
                                )
                            )
                            return@withLock BackupExecutionResult.SkippedUnchanged
                        } else {
                            _syncStatus.value = AutomaticBackupSyncStatus.Conflict(expectedUid, latestRemote)
                            return@withLock BackupExecutionResult.DeferredConflict
                        }
                    }
                } else {
                    val error = remoteResult.exceptionOrNull()
                    val failureCode = toFailureCode(error)
                    if (isPermanentFailure(failureCode)) {
                        return@withLock BackupExecutionResult.PermanentFailure(failureCode)
                    } else {
                        return@withLock BackupExecutionResult.RetryableFailure(failureCode)
                    }
                }
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

            stateStore.setState(
                expectedUid,
                prevState.copy(
                    pendingSnapshotId = snapshotId,
                    pendingSinceEpochMillis = attemptEpochMillis,
                    lastAttemptAtEpochMillis = attemptEpochMillis
                )
            )

            _syncStatus.value = AutomaticBackupSyncStatus.BackupInProgress(snapshotId)

            val manifest = CloudBackupManifest(
                snapshotId = snapshotId,
                uid = expectedUid,
                status = CloudBackupManifestStatus.UPLOADING,
                createdAt = Instant.ofEpochMilli(attemptEpochMillis),
                completedAt = null,
                formatVersion = backup.formatVersion,
                databaseSchemaVersion = backup.databaseSchemaVersion,
                appVersion = backup.appVersion.ifBlank { "1.5.0" }.take(64),
                chunkCount = chunked.totalChunkCount,
                compressedBytes = compressedBytes.size.toLong(),
                uncompressedBytes = jsonBytes.size.toLong(),
                payloadSha256 = chunked.payloadSha256,
                contentFingerprint = contentFingerprint
            )

            val uploadResult = remoteRepository.uploadBackup(expectedUid, manifest, chunked)
            if (uploadResult.isSuccess) {
                val committed = uploadResult.getOrThrow()
                val successEpoch = committed.completedAt?.toEpochMilli() ?: System.currentTimeMillis()
                fingerprintStore.setLastUploadedFingerprint(expectedUid, contentFingerprint)
                stateStore.setState(
                    expectedUid,
                    CloudBackupState(
                        lastSuccessfulBackupAtEpochMillis = successEpoch,
                        lastSuccessfulSnapshotId = committed.snapshotId,
                        pendingSnapshotId = null,
                        pendingSinceEpochMillis = null,
                        lastAttemptAtEpochMillis = attemptEpochMillis,
                        lastFailureAtEpochMillis = null,
                        lastFailureCode = null
                    )
                )
                if (_syncStatus.value is AutomaticBackupSyncStatus.Conflict) {
                    _syncStatus.value = AutomaticBackupSyncStatus.Idle
                }
                return@withLock BackupExecutionResult.Success(committed.snapshotId)
            } else {
                val error = uploadResult.exceptionOrNull()
                val failureCode = toFailureCode(error)
                val isPerm = isPermanentFailure(failureCode)

                val updatedState = if (isPerm) {
                    prevState.copy(
                        pendingSnapshotId = null,
                        pendingSinceEpochMillis = null,
                        lastAttemptAtEpochMillis = attemptEpochMillis,
                        lastFailureAtEpochMillis = System.currentTimeMillis(),
                        lastFailureCode = failureCode
                    )
                } else {
                    prevState.copy(
                        pendingSnapshotId = snapshotId,
                        pendingSinceEpochMillis = attemptEpochMillis,
                        lastAttemptAtEpochMillis = attemptEpochMillis,
                        lastFailureAtEpochMillis = System.currentTimeMillis(),
                        lastFailureCode = failureCode
                    )
                }
                stateStore.setState(expectedUid, updatedState)
                _syncStatus.value = AutomaticBackupSyncStatus.Failure(failureCode, error?.localizedMessage ?: "Backup failed")

                return@withLock if (isPerm) {
                    BackupExecutionResult.PermanentFailure(failureCode)
                } else {
                    BackupExecutionResult.RetryableFailure(failureCode)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failureCode = toFailureCode(e)
            val isPerm = isPermanentFailure(failureCode)
            try {
                val prevState = stateStore.getState(expectedUid)
                stateStore.setState(
                    expectedUid,
                    prevState.copy(
                        pendingSnapshotId = if (isPerm) null else prevState.pendingSnapshotId,
                        pendingSinceEpochMillis = if (isPerm) null else prevState.pendingSinceEpochMillis,
                        lastAttemptAtEpochMillis = attemptEpochMillis,
                        lastFailureAtEpochMillis = System.currentTimeMillis(),
                        lastFailureCode = failureCode
                    )
                )
            } catch (_: Exception) {}

            _syncStatus.value = AutomaticBackupSyncStatus.Failure(failureCode, e.localizedMessage ?: "Backup failed")
            return@withLock if (isPerm) {
                BackupExecutionResult.PermanentFailure(failureCode)
            } else {
                BackupExecutionResult.RetryableFailure(failureCode)
            }
        }
    }

    suspend fun evaluateOnLaunchOrSignIn(uid: String) = syncMutex.withLock {
        val authUid = currentAuthenticatedUid()
        if (authUid == null || authUid != uid) return@withLock

        _syncStatus.value = AutomaticBackupSyncStatus.Checking

        try {
            internalDefaultAccountProvisioner?.ensureProvisioned()
            val localBackup = backupRepository.createSnapshot()
            val localFp = BackupContentFingerprint.compute(localBackup)
            val lastFp = fingerprintStore.getLastUploadedFingerprint(uid)
            val localState = stateStore.getState(uid)

            if (LocalBackupStateClassifier.isEffectivelyEmpty(localBackup)) {
                if (lastFp == null && localState.lastSuccessfulSnapshotId == null) {
                    val remoteResult = remoteRepository.getLatestCompleteManifest()
                    if (remoteResult.isSuccess) {
                        val latest = remoteResult.getOrNull()
                        if (latest != null) {
                            _syncStatus.value = AutomaticBackupSyncStatus.RestoreInProgress(latest.snapshotId)
                            val downloadResult = remoteRepository.downloadChunks(uid, latest.snapshotId)
                            if (downloadResult.isSuccess) {
                                val chunks = downloadResult.getOrThrow()
                                val compressed = chunker.reassemble(chunks, latest.payloadSha256).getOrThrow()
                                val jsonBytes = codec.decompress(compressed).getOrThrow()
                                val restoredBackup = backupRepository.deserializeBackup(jsonBytes.toString(Charsets.UTF_8)).getOrThrow()
                                val restoreResult = restoreBackupUseCase(restoredBackup)
                                if (restoreResult.isSuccess) {
                                    widgetRefreshCoordinator?.refreshAll()
                                    val restoredFp = BackupContentFingerprint.compute(restoredBackup)
                                    fingerprintStore.setLastUploadedFingerprint(uid, restoredFp)
                                    stateStore.setState(
                                        uid,
                                        CloudBackupState(
                                            lastSuccessfulBackupAtEpochMillis = latest.completedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                                            lastSuccessfulSnapshotId = latest.snapshotId,
                                            pendingSnapshotId = null,
                                            pendingSinceEpochMillis = null,
                                            lastFailureAtEpochMillis = null,
                                            lastFailureCode = null
                                        )
                                    )
                                    _syncStatus.value = AutomaticBackupSyncStatus.RestoreCompleted(latest.snapshotId, System.currentTimeMillis())
                                    return@withLock
                                }
                            }
                        }
                    }
                }
            }

            // Non-empty or no remote snapshot
            if (lastFp == null) {
                val remoteResult = remoteRepository.getLatestCompleteManifest()
                if (remoteResult.isSuccess) {
                    val latest = remoteResult.getOrNull()
                    if (latest != null) {
                        if (latest.contentFingerprint == localFp) {
                            fingerprintStore.setLastUploadedFingerprint(uid, localFp)
                            stateStore.setState(
                                uid,
                                localState.copy(
                                    lastSuccessfulBackupAtEpochMillis = latest.completedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                                    lastSuccessfulSnapshotId = latest.snapshotId,
                                    pendingSnapshotId = null,
                                    pendingSinceEpochMillis = null
                                )
                            )
                            _syncStatus.value = AutomaticBackupSyncStatus.Idle
                            return@withLock
                        } else {
                            _syncStatus.value = AutomaticBackupSyncStatus.Conflict(uid, latest)
                            return@withLock
                        }
                    }
                }
            } else if (lastFp != localFp) {
                // Local changes exist since last upload -> schedule backup
                val prevState = stateStore.getState(uid)
                stateStore.setState(
                    uid,
                    prevState.copy(
                        pendingSnapshotId = prevState.pendingSnapshotId ?: "primary_backup_pending",
                        pendingSinceEpochMillis = prevState.pendingSinceEpochMillis ?: System.currentTimeMillis()
                    )
                )
                onScheduleDebouncedBackup(uid)
            }

            _syncStatus.value = AutomaticBackupSyncStatus.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "evaluateOnLaunchOrSignIn failed", e)
            _syncStatus.value = AutomaticBackupSyncStatus.Idle
        }
    }

    suspend fun onLocalDataMutated() {
        val uid = currentAuthenticatedUid() ?: return
        val currentStatus = _syncStatus.value
        if (currentStatus is AutomaticBackupSyncStatus.Conflict) {
            return // Do not silently clobber cloud while conflict exists
        }

        try {
            val prevState = stateStore.getState(uid)
            val now = System.currentTimeMillis()
            stateStore.setState(
                uid,
                prevState.copy(
                    pendingSnapshotId = prevState.pendingSnapshotId ?: "primary_backup_pending",
                    pendingSinceEpochMillis = prevState.pendingSinceEpochMillis ?: now
                )
            )
            onScheduleDebouncedBackup(uid)
        } catch (e: Exception) {
            Log.w(TAG, "onLocalDataMutated failed", e)
        }
    }

    suspend fun resolveConflictRestoreCloud(uid: String, remoteManifest: CloudBackupManifest) = syncMutex.withLock {
        val authUid = currentAuthenticatedUid()
        if (authUid == null || authUid != uid) return@withLock

        _syncStatus.value = AutomaticBackupSyncStatus.RestoreInProgress(remoteManifest.snapshotId)
        try {
            val downloadResult = remoteRepository.downloadChunks(uid, remoteManifest.snapshotId)
            if (downloadResult.isSuccess) {
                val chunks = downloadResult.getOrThrow()
                val compressed = chunker.reassemble(chunks, remoteManifest.payloadSha256).getOrThrow()
                val jsonBytes = codec.decompress(compressed).getOrThrow()
                val restoredBackup = backupRepository.deserializeBackup(jsonBytes.toString(Charsets.UTF_8)).getOrThrow()
                val restoreResult = restoreBackupUseCase(restoredBackup)
                if (restoreResult.isSuccess) {
                    widgetRefreshCoordinator?.refreshAll()
                    val restoredFp = BackupContentFingerprint.compute(restoredBackup)
                    fingerprintStore.setLastUploadedFingerprint(uid, restoredFp)
                    stateStore.setState(
                        uid,
                        CloudBackupState(
                            lastSuccessfulBackupAtEpochMillis = remoteManifest.completedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                            lastSuccessfulSnapshotId = remoteManifest.snapshotId,
                            pendingSnapshotId = null,
                            pendingSinceEpochMillis = null,
                            lastFailureAtEpochMillis = null,
                            lastFailureCode = null
                        )
                    )
                    _syncStatus.value = AutomaticBackupSyncStatus.RestoreCompleted(remoteManifest.snapshotId, System.currentTimeMillis())
                } else {
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure("RESTORE_FAILED", "Failed to restore cloud snapshot")
                }
            } else {
                _syncStatus.value = AutomaticBackupSyncStatus.Failure("DOWNLOAD_FAILED", "Failed to download cloud chunks")
            }
        } catch (e: Exception) {
            _syncStatus.value = AutomaticBackupSyncStatus.Failure("RESTORE_EXCEPTION", e.localizedMessage ?: "Restore failed")
        }
    }

    suspend fun resolveConflictKeepLocalAndBackup(uid: String) = syncMutex.withLock {
        val authUid = currentAuthenticatedUid()
        if (authUid == null || authUid != uid) return@withLock

        _syncStatus.value = AutomaticBackupSyncStatus.Idle
        // Perform backup forcing overwrite/upload of local data over cloud conflict
        performBackup(uid, forceAfterConflictResolution = true)
    }

    suspend fun onUserSignedOut(previousUid: String?) {
        if (!previousUid.isNullOrBlank()) {
            onCancelBackupWork(previousUid)
        }
        _syncStatus.value = AutomaticBackupSyncStatus.Idle
    }

    private fun toFailureCode(error: Throwable?): String {
        return when (error) {
            is CloudBackupRemoteException -> error.failureCode
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "PERMISSION_DENIED"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "NETWORK_UNAVAILABLE"
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "DEADLINE_EXCEEDED"
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "QUOTA_EXCEEDED"
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "UNAUTHENTICATED"
                FirebaseFirestoreException.Code.ABORTED -> "ABORTED"
                else -> "FIRESTORE_ERROR"
            }
            is FirebaseNetworkException -> "NETWORK_UNAVAILABLE"
            is IllegalArgumentException -> "VALIDATION_ERROR"
            else -> "UPLOAD_FAILED"
        }
    }

    private fun isPermanentFailure(failureCode: String): Boolean {
        return when (failureCode) {
            "PERMISSION_DENIED",
            "UNAUTHENTICATED",
            "UID_MISMATCH",
            "VALIDATION_ERROR",
            "SNAPSHOT_CONFLICT" -> true
            else -> false
        }
    }

    companion object {
        const val TAG = "AutoBackupOrchestrator"
    }
}
