package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.BackupContentFingerprint
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup

interface BackupRepository {
    suspend fun createSnapshot(): ExpenseTrackerBackup
    suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome>

    /**
     * Restore only while the caller's identity/workspace checks remain valid and, when supplied,
     * the current local content still matches the snapshot used to establish restore eligibility.
     * Room-backed implementations should perform these checks inside their restore transaction.
     */
    suspend fun restoreSnapshotGuarded(
        backup: ExpenseTrackerBackup,
        expectedCurrentFingerprint: String? = null,
        preCommitCheck: suspend () -> Boolean
    ): Result<BackupRestoreOutcome> {
        if (!preCommitCheck()) return Result.failure(BackupRestorePreconditionFailedException())
        if (
            expectedCurrentFingerprint != null &&
            BackupContentFingerprint.compute(createSnapshot()) != expectedCurrentFingerprint
        ) {
            return Result.failure(BackupRestorePreconditionFailedException())
        }
        if (!preCommitCheck()) return Result.failure(BackupRestorePreconditionFailedException())
        return restoreSnapshot(backup)
    }

    fun serializeBackup(backup: ExpenseTrackerBackup): String
    fun deserializeBackup(json: String): Result<ExpenseTrackerBackup>
}

class BackupRestorePreconditionFailedException : IllegalStateException(
    "Local restore preconditions changed before the database commit."
)
