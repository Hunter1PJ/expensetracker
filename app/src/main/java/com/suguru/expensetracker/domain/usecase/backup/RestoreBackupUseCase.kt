package com.suguru.expensetracker.domain.usecase.backup

import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.repository.BackupRepository

class RestoreBackupUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome> {
        return backupRepository.restoreSnapshot(backup)
    }
}
