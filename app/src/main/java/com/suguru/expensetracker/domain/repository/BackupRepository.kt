package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup

interface BackupRepository {
    suspend fun createSnapshot(): ExpenseTrackerBackup
    suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<Unit>
    fun serializeBackup(backup: ExpenseTrackerBackup): String
    fun deserializeBackup(json: String): Result<ExpenseTrackerBackup>
}
