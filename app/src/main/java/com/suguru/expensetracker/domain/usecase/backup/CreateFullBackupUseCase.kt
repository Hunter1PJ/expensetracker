package com.suguru.expensetracker.domain.usecase.backup

import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.repository.BackupRepository

class CreateFullBackupUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(): ExpenseTrackerBackup {
        return backupRepository.createSnapshot()
    }
}
