package com.example.domain.repository

import com.example.domain.model.ExpenseTrackerBackup

interface BackupRepository {
    suspend fun createSnapshot(): ExpenseTrackerBackup
    suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<Unit>
    fun serializeBackup(backup: ExpenseTrackerBackup): String
    fun deserializeBackup(json: String): Result<ExpenseTrackerBackup>
}
