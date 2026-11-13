package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup

interface BackupRepository {
    suspend fun createSnapshot(): ExpenseTrackerBackup
    suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome>
    fun serializeBackup(backup: ExpenseTrackerBackup): String
    fun deserializeBackup(json: String): Result<ExpenseTrackerBackup>
}
