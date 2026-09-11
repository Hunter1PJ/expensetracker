package com.example.domain.usecase.backup

import com.example.domain.model.ExpenseTrackerBackup
import com.example.domain.repository.BackupRepository

class CreateFullBackupUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(): ExpenseTrackerBackup {
        return backupRepository.createSnapshot()
    }
}
