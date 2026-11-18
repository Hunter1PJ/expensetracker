package com.suguru.expensetracker.domain.error

import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupState

/**
 * Typed domain failure hierarchy for remote cloud backup operations.
 *
 * Every failure carries a bounded machine-readable [failureCode] compatible with
 * [CloudBackupState.isValidFailureCode] and never embeds raw stack traces, URLs,
 * or sensitive user account information.
 */
sealed class CloudBackupRemoteException(
    val failureCode: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    init {
        require(CloudBackupState.isValidFailureCode(failureCode)) {
            "Invalid failure code: $failureCode"
        }
    }

    class Unauthenticated(
        message: String = "User must be signed in before accessing cloud backups."
    ) : CloudBackupRemoteException("UNAUTHENTICATED", message)

    class UidMismatch(
        message: String = "Authenticated user does not match requested backup owner."
    ) : CloudBackupRemoteException("UID_MISMATCH", message)

    class InvalidSnapshot(
        message: String,
        cause: Throwable? = null
    ) : CloudBackupRemoteException("VALIDATION_ERROR", message, cause)

    class SnapshotConflict(
        val snapshotId: String,
        message: String = "Existing snapshot '$snapshotId' cannot be silently replaced."
    ) : CloudBackupRemoteException("SNAPSHOT_CONFLICT", message)

    class SnapshotNotFound(
        val snapshotId: String
    ) : CloudBackupRemoteException(
        failureCode = "SNAPSHOT_NOT_FOUND",
        message = "Cloud backup snapshot '$snapshotId' was not found."
    )

    class SnapshotNotComplete(
        val snapshotId: String,
        val status: CloudBackupManifestStatus
    ) : CloudBackupRemoteException(
        failureCode = "SNAPSHOT_NOT_COMPLETE",
        message = "Cloud backup snapshot '$snapshotId' is in status $status and cannot be downloaded."
    )

    class ChunkIntegrityError(
        val snapshotId: String,
        message: String,
        cause: Throwable? = null
    ) : CloudBackupRemoteException("CHUNK_INTEGRITY_ERROR", message, cause)

    class RemoteOperationFailed(
        failureCode: String,
        message: String,
        cause: Throwable? = null
    ) : CloudBackupRemoteException(failureCode, message, cause)
}
