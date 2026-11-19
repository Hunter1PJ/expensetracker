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
import com.suguru.expensetracker.domain.repository.AutoBackupPreferenceStore
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
    private val autoBackupPreferenceStore: AutoBackupPreferenceStore? = null,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null,
    private val onScheduleDebouncedBackup: (uid: String) -> Unit = {},
    private val onScheduleAutomaticRestore: (uid: String) -> Unit = {},
    private val onCancelBackupWork: (uid: String) -> Unit = {},
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val authenticatedUidProvider: () -> String? = {
        try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
    },
    private val activeWorkspaceUidProvider: () -> String? = authenticatedUidProvider
) {
    private val codec = CloudBackupPayloadCodec()
    private val chunker = CloudBackupPayloadChunker()
    private val syncMutex = Mutex()

    private val _syncStatus = MutableStateFlow<AutomaticBackupSyncStatus>(AutomaticBackupSyncStatus.Idle)
    val syncStatus: StateFlow<AutomaticBackupSyncStatus> = _syncStatus.asStateFlow()
    private val _syncStatusUid = MutableStateFlow<String?>(null)
    val syncStatusUid: StateFlow<String?> = _syncStatusUid.asStateFlow()

    fun currentAuthenticatedUid(): String? =
        try {
            authenticatedUidProvider()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun isExpectedWorkspaceActive(uid: String): Boolean =
        runCatching { activeWorkspaceUidProvider() == uid }.getOrDefault(false)

    suspend fun performBackup(
        expectedUid: String,
        forceAfterConflictResolution: Boolean = false,
        explicitManual: Boolean = false
    ): BackupExecutionResult = syncMutex.withLock {
        val authUid = currentAuthenticatedUid()
        if (authUid == null || authUid != expectedUid || !isExpectedWorkspaceActive(expectedUid)) {
            runCatching { Log.w(TAG, "Backup blocked at operation start (authenticated=${authUid == expectedUid}, workspaceMatches=${isExpectedWorkspaceActive(expectedUid)}).") }
            return@withLock BackupExecutionResult.PermanentFailure("UID_MISMATCH")
        }

        _syncStatusUid.value = expectedUid
        _syncStatus.value = AutomaticBackupSyncStatus.Idle

        // If auto backup is disabled for this UID, do not execute automatic backup
        if (!forceAfterConflictResolution && !explicitManual &&
            autoBackupPreferenceStore != null && !autoBackupPreferenceStore.isAutoBackupEnabled(expectedUid)
        ) {
            return@withLock BackupExecutionResult.SkippedUnchanged
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
                _syncStatus.value = AutomaticBackupSyncStatus.Idle
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
                                    pendingSinceEpochMillis = null,
                                    lastFailureAtEpochMillis = null,
                                    lastFailureCode = null
                                )
                            )
                            _syncStatus.value = AutomaticBackupSyncStatus.Idle
                            return@withLock BackupExecutionResult.SkippedUnchanged
                        } else {
                            _syncStatus.value = AutomaticBackupSyncStatus.Conflict(expectedUid, latestRemote)
                            return@withLock BackupExecutionResult.DeferredConflict
                        }
                    }
                } else {
                    val error = remoteResult.exceptionOrNull()
                    val failureCode = toFailureCode(error)
                    val failedAt = System.currentTimeMillis()
                    val permanent = isPermanentFailure(failureCode)
                    stateStore.setState(
                        expectedUid,
                        prevState.copy(
                            pendingSnapshotId = prevState.pendingSnapshotId ?: FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                            pendingSinceEpochMillis = prevState.pendingSinceEpochMillis ?: failedAt,
                            lastAttemptAtEpochMillis = attemptEpochMillis,
                            lastFailureAtEpochMillis = failedAt,
                            lastFailureCode = failureCode
                        )
                    )
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                        failureCode,
                        error?.localizedMessage ?: "Cloud backup could not be checked."
                    )
                    return@withLock if (permanent) BackupExecutionResult.PermanentFailure(failureCode)
                    else BackupExecutionResult.RetryableFailure(failureCode)
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

            // Recheck after snapshot creation/serialization so an account switch cannot upload
            // data from this workspace under a different authenticated account.
            if (currentAuthenticatedUid() != expectedUid || !isExpectedWorkspaceActive(expectedUid)) {
                runCatching { Log.w(TAG, "Backup blocked before upload (authenticated=${currentAuthenticatedUid() == expectedUid}, workspaceMatches=${isExpectedWorkspaceActive(expectedUid)}).") }
                _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                    "UID_MISMATCH",
                    "Backup cancelled because the active account changed."
                )
                return@withLock BackupExecutionResult.PermanentFailure("UID_MISMATCH")
            }
            val uploadResult = remoteRepository.uploadBackup(expectedUid, manifest, chunked)
            if (uploadResult.isSuccess) {
                val committed = uploadResult.getOrThrow()
                val successEpoch = committed.completedAt?.toEpochMilli() ?: System.currentTimeMillis()
                fingerprintStore.setLastUploadedFingerprint(expectedUid, contentFingerprint)
                fingerprintStore.setLastUploadedFingerprint(expectedUid, committed.contentFingerprint)
                val currentLocalFingerprint = runCatching {
                    BackupContentFingerprint.compute(createFullBackupUseCase())
                }.getOrNull()
                val currentUidStillMatches = currentAuthenticatedUid() == expectedUid
                val backupCoversCurrentLocalData = currentUidStillMatches &&
                    committed.contentFingerprint == contentFingerprint &&
                    currentLocalFingerprint == committed.contentFingerprint
                val latestState = stateStore.getState(expectedUid)
                val successState = latestState.copy(
                    lastSuccessfulBackupAtEpochMillis = successEpoch,
                    lastSuccessfulSnapshotId = committed.snapshotId,
                    pendingSnapshotId = if (backupCoversCurrentLocalData) null else FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                    pendingSinceEpochMillis = if (backupCoversCurrentLocalData) null else latestState.pendingSinceEpochMillis ?: System.currentTimeMillis(),
                    lastAttemptAtEpochMillis = attemptEpochMillis,
                    lastFailureAtEpochMillis = null,
                    lastFailureCode = null
                )
                stateStore.setState(expectedUid, successState)
                if (!backupCoversCurrentLocalData && currentUidStillMatches) {
                    onScheduleDebouncedBackup(expectedUid)
                }
                _syncStatus.value = AutomaticBackupSyncStatus.Idle
                return@withLock BackupExecutionResult.Success(committed.snapshotId)
            } else {
                val error = uploadResult.exceptionOrNull()
                val failureCode = toFailureCode(error)
                val isPerm = isPermanentFailure(failureCode)

                val updatedState = prevState.copy(
                    pendingSnapshotId = prevState.pendingSnapshotId ?: snapshotId,
                    pendingSinceEpochMillis = prevState.pendingSinceEpochMillis ?: attemptEpochMillis,
                    lastAttemptAtEpochMillis = attemptEpochMillis,
                    lastFailureAtEpochMillis = System.currentTimeMillis(),
                    lastFailureCode = failureCode
                )
                stateStore.setState(expectedUid, updatedState)
                _syncStatus.value = AutomaticBackupSyncStatus.Failure(failureCode, error?.localizedMessage ?: "Backup failed")

                return@withLock if (isPerm) {
                    BackupExecutionResult.PermanentFailure(failureCode)
                } else {
                    BackupExecutionResult.RetryableFailure(failureCode)
                }
            }
        } catch (e: CancellationException) {
            _syncStatus.value = AutomaticBackupSyncStatus.Idle
            throw e
        } catch (e: Exception) {
            val failureCode = toFailureCode(e)
            val isPerm = isPermanentFailure(failureCode)
            try {
                val prevState = stateStore.getState(expectedUid)
                stateStore.setState(
                    expectedUid,
                    prevState.copy(
                        pendingSnapshotId = prevState.pendingSnapshotId ?: FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                        pendingSinceEpochMillis = prevState.pendingSinceEpochMillis ?: attemptEpochMillis,
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
        if (authUid == null || authUid != uid || !isExpectedWorkspaceActive(uid)) return@withLock
        _syncStatusUid.value = uid

        // Check if auto mode is disabled for this UID
        if (autoBackupPreferenceStore != null && !autoBackupPreferenceStore.isAutoBackupEnabled(uid)) {
            val state = stateStore.getState(uid)
            if (state.pendingRestore) stateStore.setState(uid, state.copy(pendingRestore = false))
            _syncStatus.value = AutomaticBackupSyncStatus.Idle
            return@withLock
        }

        _syncStatus.value = AutomaticBackupSyncStatus.Checking

        try {
            internalDefaultAccountProvisioner?.ensureProvisioned()
            val localBackup = backupRepository.createSnapshot()
            val localFp = BackupContentFingerprint.compute(localBackup)
            val lastFp = fingerprintStore.getLastUploadedFingerprint(uid)
            val localState = stateStore.getState(uid)
            val localIsEffectivelyEmpty = LocalBackupStateClassifier.isEffectivelyEmpty(localBackup)

            if (localIsEffectivelyEmpty) {
                if (lastFp == null && localState.lastSuccessfulSnapshotId == null) {
                    stateStore.setState(uid, localState.copy(pendingRestore = true))
                    // Persist a connected-network opportunity so restore can run after process death.
                    onScheduleAutomaticRestore(uid)
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
                                val currentUidBeforeCommit = currentAuthenticatedUid()
                                val currentLocalBackup = backupRepository.createSnapshot()
                                val stillEligible = currentUidBeforeCommit == uid &&
                                    (autoBackupPreferenceStore == null || autoBackupPreferenceStore.isAutoBackupEnabled(uid)) &&
                                    LocalBackupStateClassifier.isEffectivelyEmpty(currentLocalBackup)
                                if (!stillEligible) {
                                    val state = stateStore.getState(uid)
                                    if (state.pendingRestore) stateStore.setState(uid, state.copy(pendingRestore = false))
                                    _syncStatus.value = AutomaticBackupSyncStatus.Idle
                                    return@withLock
                                }
                                val restoreResult = restoreBackupUseCase(restoredBackup)
                                if (restoreResult.isSuccess) {
                                    widgetRefreshCoordinator?.refreshAll()
                                    val restoredFp = BackupContentFingerprint.compute(restoredBackup)
                                    fingerprintStore.setLastUploadedFingerprint(uid, restoredFp)
                                    val restoreEpochMillis = System.currentTimeMillis()
                                    stateStore.setState(
                                        uid,
                                        stateStore.getState(uid).copy(
                                            lastSuccessfulSnapshotId = latest.snapshotId,
                                            lastSuccessfulRestoreGenerationId = latest.generationId,
                                            lastSuccessfulRestoreFingerprint = latest.contentFingerprint,
                                            pendingRestore = false,
                                            lastSuccessfulBackupAtEpochMillis = latest.completedAt?.toEpochMilli() ?: restoreEpochMillis,
                                            lastSuccessfulRestoreAtEpochMillis = restoreEpochMillis,
                                            lastRestoreFailureAtEpochMillis = null,
                                            lastRestoreFailureCode = null,
                                            lastRestoreFailureGenerationId = null,
                                            lastRestoreFailureFingerprint = null,
                                            pendingSnapshotId = null,
                                            pendingSinceEpochMillis = null,
                                            lastFailureAtEpochMillis = null,
                                            lastFailureCode = null
                                        )
                                    )
                                    _syncStatus.value = AutomaticBackupSyncStatus.RestoreCompleted(latest.snapshotId, restoreEpochMillis)
                                    return@withLock
                                } else {
                                    recordRestoreFailure(uid, "RESTORE_FAILED", latest)
                                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                                        "RESTORE_FAILED",
                                        "Cloud snapshot could not be restored."
                                    )
                                    return@withLock
                                }
                            } else {
                                val failure = downloadResult.exceptionOrNull()
                                val failureCode = toFailureCode(failure)
                                recordRestoreFailure(uid, failureCode, latest)
                                _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                                    failureCode,
                                    "Cloud snapshot could not be downloaded."
                                )
                                return@withLock
                            }
                        } else {
                            val state = stateStore.getState(uid)
                            if (state.pendingRestore) stateStore.setState(uid, state.copy(pendingRestore = false))
                        }
                    } else if (remoteResult.isFailure) {
                        val failure = remoteResult.exceptionOrNull()
                        val failureCode = toFailureCode(failure)
                        recordRestoreFailure(uid, failureCode)
                        _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                            failureCode,
                            "Cloud snapshot availability could not be checked."
                        )
                        return@withLock
                    }
                }
            }

            if (!localIsEffectivelyEmpty && lastFp != null && localFp == lastFp &&
                localState.pendingSnapshotId == null
            ) {
                val latestResult = remoteRepository.getLatestCompleteManifest()
                if (latestResult.isFailure) {
                    val failureCode = toFailureCode(latestResult.exceptionOrNull())
                    val now = System.currentTimeMillis()
                    stateStore.setState(
                        uid,
                        localState.copy(
                            pendingRestore = !isPermanentFailure(failureCode),
                            lastRestoreFailureAtEpochMillis = now,
                            lastRestoreFailureCode = failureCode
                        )
                    )
                    if (!isPermanentFailure(failureCode)) onScheduleAutomaticRestore(uid)
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                        failureCode,
                        "Cloud generation could not be checked."
                    )
                    return@withLock
                }

                val latest = latestResult.getOrNull()
                if (latest != null) {
                    val alreadyRestored = if (latest.generationId != null &&
                        localState.lastSuccessfulRestoreGenerationId != null
                    ) {
                        latest.generationId == localState.lastSuccessfulRestoreGenerationId
                    } else {
                        latest.contentFingerprint == localState.lastSuccessfulRestoreFingerprint
                    }
                    if (!alreadyRestored) {
                        val failureMatchesGeneration =
                            (localState.lastRestoreFailureGenerationId != null &&
                                localState.lastRestoreFailureGenerationId == latest.generationId) ||
                                (localState.lastRestoreFailureFingerprint != null &&
                                    localState.lastRestoreFailureFingerprint == latest.contentFingerprint)
                        val previousFailureIsPermanent = failureMatchesGeneration &&
                            localState.lastRestoreFailureCode?.let(::isPermanentFailure) == true
                        if (previousFailureIsPermanent) {
                            _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                                localState.lastRestoreFailureCode,
                                "Cloud generation could not be restored."
                            )
                            return@withLock
                        }
                        val pendingState = stateStore.getState(uid)
                        stateStore.setState(uid, pendingState.copy(pendingRestore = true))
                        onScheduleAutomaticRestore(uid)
                        restoreNewGenerationIfLocalBaselineUnchanged(uid, latest, localFp)
                        return@withLock
                    }
                }
            }

            if (localIsEffectivelyEmpty) {
                _syncStatus.value = AutomaticBackupSyncStatus.Idle
                return@withLock
            }

            // Non-empty local data with no local upload fingerprint must be reconciled against cloud.
            if (lastFp == null) {
                val remoteResult = remoteRepository.getLatestCompleteManifest()
                if (remoteResult.isFailure) {
                    val failureCode = toFailureCode(remoteResult.exceptionOrNull())
                    val now = System.currentTimeMillis()
                    stateStore.setState(
                        uid,
                        stateStore.getState(uid).copy(
                            pendingSnapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                            pendingSinceEpochMillis = stateStore.getState(uid).pendingSinceEpochMillis ?: now,
                            lastFailureAtEpochMillis = now,
                            lastFailureCode = failureCode
                        )
                    )
                    // The worker repeats the cloud check before uploading, so a transient lookup error
                    // can never cause this device to overwrite an unseen primary restore point.
                    onScheduleDebouncedBackup(uid)
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                        failureCode,
                        "Cloud snapshot availability could not be checked."
                    )
                    return@withLock
                }

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

                // This UID has no cloud snapshot yet. Preserve local data and schedule the first backup.
                val now = System.currentTimeMillis()
                stateStore.setState(
                    uid,
                    localState.copy(
                        pendingSnapshotId = localState.pendingSnapshotId ?: FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                        pendingSinceEpochMillis = localState.pendingSinceEpochMillis ?: now
                    )
                )
                onScheduleDebouncedBackup(uid)
                _syncStatus.value = AutomaticBackupSyncStatus.Idle
                return@withLock
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
            Log.w(TAG, "Startup reconciliation failed (${e::class.java.simpleName})")
            _syncStatus.value = if (_syncStatus.value is AutomaticBackupSyncStatus.RestoreInProgress) {
                val failureCode = when (e) {
                    is IllegalArgumentException -> "RESTORE_VALIDATION_FAILED"
                    else -> toFailureCode(e)
                }
                recordRestoreFailure(uid, failureCode)
                AutomaticBackupSyncStatus.Failure(
                    failureCode,
                    "Cloud restore failed (${e::class.java.simpleName})."
                )
            } else {
                AutomaticBackupSyncStatus.Idle
            }
        }
    }

    private suspend fun restoreNewGenerationIfLocalBaselineUnchanged(
        uid: String,
        manifest: CloudBackupManifest,
        expectedLocalFingerprint: String
    ) {
        if (currentAuthenticatedUid() != uid) return
        _syncStatusUid.value = uid
        _syncStatus.value = AutomaticBackupSyncStatus.RestoreInProgress(manifest.snapshotId)
        try {
            require(manifest.uid == uid && manifest.status == CloudBackupManifestStatus.COMPLETE) {
                "Cloud generation is not eligible for restore."
            }
            manifest.validate().getOrThrow()
            val chunks = remoteRepository.downloadChunks(uid, manifest.snapshotId).getOrThrow()
            val compressed = chunker.reassemble(chunks, manifest.payloadSha256).getOrThrow()
            val jsonBytes = codec.decompress(compressed).getOrThrow()
            val backup = backupRepository.deserializeBackup(jsonBytes.toString(Charsets.UTF_8)).getOrThrow()
            require(BackupContentFingerprint.compute(backup) == manifest.contentFingerprint) {
                "Cloud generation content fingerprint does not match its payload."
            }

            val currentLocalBackup = backupRepository.createSnapshot()
            val currentState = stateStore.getState(uid)
            val currentBaseline = fingerprintStore.getLastUploadedFingerprint(uid)
            val stillSafe = currentAuthenticatedUid() == uid &&
                (autoBackupPreferenceStore == null || autoBackupPreferenceStore.isAutoBackupEnabled(uid)) &&
                currentBaseline == expectedLocalFingerprint &&
                BackupContentFingerprint.compute(currentLocalBackup) == expectedLocalFingerprint &&
                currentState.pendingSnapshotId == null
            if (!stillSafe) {
                stateStore.setState(uid, currentState.copy(pendingRestore = false))
                if (currentAuthenticatedUid() == uid) _syncStatus.value = AutomaticBackupSyncStatus.Idle
                return
            }

            val restoreResult = restoreBackupUseCase(backup)
            if (restoreResult.isFailure) {
                val code = "RESTORE_FAILED"
                recordRestoreFailure(uid, code, manifest)
                _syncStatus.value = AutomaticBackupSyncStatus.Failure(code, "Cloud snapshot could not be restored.")
                return
            }

            widgetRefreshCoordinator?.refreshAll()
            val restoreTime = System.currentTimeMillis()
            fingerprintStore.setLastUploadedFingerprint(uid, manifest.contentFingerprint)
            stateStore.setState(
                uid,
                stateStore.getState(uid).copy(
                    lastSuccessfulSnapshotId = manifest.snapshotId,
                    lastSuccessfulRestoreGenerationId = manifest.generationId,
                    lastSuccessfulRestoreFingerprint = manifest.contentFingerprint,
                    lastSuccessfulRestoreAtEpochMillis = restoreTime,
                    lastSuccessfulBackupAtEpochMillis = manifest.completedAt?.toEpochMilli() ?: restoreTime,
                    lastRestoreFailureAtEpochMillis = null,
                    lastRestoreFailureCode = null,
                    lastRestoreFailureGenerationId = null,
                    lastRestoreFailureFingerprint = null,
                    pendingRestore = false,
                    pendingSnapshotId = null,
                    pendingSinceEpochMillis = null,
                    lastFailureAtEpochMillis = null,
                    lastFailureCode = null
                )
            )
            _syncStatus.value = AutomaticBackupSyncStatus.RestoreCompleted(manifest.snapshotId, restoreTime)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val code = when (error) {
                is IllegalArgumentException -> "RESTORE_VALIDATION_FAILED"
                else -> toFailureCode(error)
            }
            recordRestoreFailure(uid, code, manifest)
            _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                code,
                "Cloud restore could not be completed (" + error.javaClass.simpleName + ")."
            )
        }
    }

    suspend fun onLocalDataMutated() {
        val uid = currentAuthenticatedUid() ?: return
        val currentStatus = _syncStatus.value
        if (currentStatus is AutomaticBackupSyncStatus.Conflict) {
            return // Do not silently clobber cloud while conflict exists
        }

        // Check if auto mode is disabled for this UID
        if (autoBackupPreferenceStore != null && !autoBackupPreferenceStore.isAutoBackupEnabled(uid)) {
            return
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
            Log.w(TAG, "Mutation reconciliation failed (${e::class.java.simpleName})")
        }
    }

    suspend fun resolveConflictRestoreCloud(uid: String, remoteManifest: CloudBackupManifest): Boolean =
        syncMutex.withLock {
            if (currentAuthenticatedUid() != uid || !isExpectedWorkspaceActive(uid) || remoteManifest.uid != uid ||
                remoteManifest.status != CloudBackupManifestStatus.COMPLETE
            ) return@withLock false

            _syncStatusUid.value = uid
            _syncStatus.value = AutomaticBackupSyncStatus.RestoreInProgress(remoteManifest.snapshotId)
            try {
                val chunks = remoteRepository.downloadChunks(uid, remoteManifest.snapshotId).getOrThrow()
                val compressed = chunker.reassemble(chunks, remoteManifest.payloadSha256).getOrThrow()
                val jsonBytes = codec.decompress(compressed).getOrThrow()
                val restoredBackup = backupRepository.deserializeBackup(jsonBytes.toString(Charsets.UTF_8)).getOrThrow()

                // Authentication can change while the remote payload is being fetched and validated.
                if (currentAuthenticatedUid() != uid || !isExpectedWorkspaceActive(uid)) {
                    runCatching { Log.w(TAG, "Conflict restore blocked before Room commit (authenticated=${currentAuthenticatedUid() == uid}, workspaceMatches=${isExpectedWorkspaceActive(uid)}).") }
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                        "UID_MISMATCH",
                        "Restore cancelled because the authenticated account or active workspace changed."
                    )
                    return@withLock false
                }

                val restoreResult = restoreBackupUseCase(restoredBackup)
                if (restoreResult.isFailure) {
                    recordRestoreFailure(uid, "RESTORE_FAILED", remoteManifest)
                    _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                        "RESTORE_FAILED",
                        "Failed to restore cloud snapshot."
                    )
                    return@withLock false
                }

                widgetRefreshCoordinator?.refreshAll()
                val restoredFp = BackupContentFingerprint.compute(restoredBackup)
                fingerprintStore.setLastUploadedFingerprint(uid, restoredFp)
                val restoreEpochMillis = System.currentTimeMillis()
                stateStore.setState(
                    uid,
                    stateStore.getState(uid).copy(
                        lastSuccessfulSnapshotId = remoteManifest.snapshotId,
                        lastSuccessfulRestoreGenerationId = remoteManifest.generationId,
                        lastSuccessfulRestoreFingerprint = remoteManifest.contentFingerprint,
                        pendingRestore = false,
                        lastSuccessfulBackupAtEpochMillis = remoteManifest.completedAt?.toEpochMilli() ?: restoreEpochMillis,
                        lastSuccessfulRestoreAtEpochMillis = restoreEpochMillis,
                        lastRestoreFailureAtEpochMillis = null,
                        lastRestoreFailureCode = null,
                        lastRestoreFailureGenerationId = null,
                        lastRestoreFailureFingerprint = null,
                        pendingSnapshotId = null,
                        pendingSinceEpochMillis = null,
                        lastFailureAtEpochMillis = null,
                        lastFailureCode = null
                    )
                )
                _syncStatus.value = AutomaticBackupSyncStatus.RestoreCompleted(remoteManifest.snapshotId, restoreEpochMillis)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordRestoreFailure(uid, "RESTORE_EXCEPTION", remoteManifest)
                _syncStatus.value = AutomaticBackupSyncStatus.Failure(
                    "RESTORE_EXCEPTION",
                    "Cloud restore failed (${e::class.java.simpleName})."
                )
                false
            }
        }

    suspend fun resolveConflictKeepLocalAndBackup(uid: String): BackupExecutionResult {
        if (currentAuthenticatedUid() != uid) {
            return BackupExecutionResult.PermanentFailure("UID_MISMATCH")
        }
        _syncStatusUid.value = uid
        // performBackup owns the sync mutex. Calling it from inside withLock deadlocked.
        return performBackup(uid, forceAfterConflictResolution = true)
    }

    suspend fun onUserSignedOut(previousUid: String?) {
        if (!previousUid.isNullOrBlank()) {
            onCancelBackupWork(previousUid)
        }
        _syncStatusUid.value = null
        _syncStatus.value = AutomaticBackupSyncStatus.Idle
    }

    private suspend fun recordRestoreFailure(
        uid: String,
        failureCode: String,
        manifest: CloudBackupManifest? = null
    ) {
        val safeCode = failureCode.takeIf { CloudBackupState.isValidFailureCode(it) } ?: "RESTORE_FAILED"
        try {
            val previous = stateStore.getState(uid)
            stateStore.setState(
                uid,
                previous.copy(
                    lastRestoreFailureAtEpochMillis = System.currentTimeMillis(),
                    lastRestoreFailureCode = safeCode,
                    lastRestoreFailureGenerationId = manifest?.generationId,
                    lastRestoreFailureFingerprint = manifest?.contentFingerprint,
                    pendingRestore = !isPermanentFailure(safeCode)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist restore failure (${e::class.java.simpleName})")
        }
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
            "RESTORE_VALIDATION_FAILED",
            "SNAPSHOT_CONFLICT",
            "SNAPSHOT_NOT_FOUND",
            "SNAPSHOT_NOT_COMPLETE",
            "INVALID_SNAPSHOT",
            "CHUNK_INTEGRITY_ERROR" -> true
            else -> false
        }
    }

    fun shouldRetryAutomaticRestore(): Boolean {
        val failure = _syncStatus.value as? AutomaticBackupSyncStatus.Failure ?: return false
        return !isPermanentFailure(failure.code)
    }

    companion object {
        const val TAG = "AutoBackupOrchestrator"
    }
}
