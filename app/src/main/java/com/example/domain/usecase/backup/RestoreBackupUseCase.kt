package com.example.domain.usecase.backup

import com.example.domain.model.ExpenseTrackerBackup
import com.example.domain.repository.BackupRepository

class RestoreBackupUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(backup: ExpenseTrackerBackup): Result<Unit> {
        return backupRepository.restoreSnapshot(backup)
    }
}
