package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ExpenseTrackerBackup
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.backup.CreateFullBackupUseCase
import com.example.domain.usecase.backup.RestoreBackupUseCase
import com.example.domain.usecase.transaction.ExportTransactionsToCsvUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class DataStorageUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val lastBackupAt: String? = null,
    val lastBackupFileName: String? = null,
    val backupJsonToWrite: String? = null,
    val csvStringToWrite: String? = null,
    val backupFileName: String? = null,
    val csvFileName: String? = null,
    val pendingRestoreBackup: ExpenseTrackerBackup? = null
)

class DataStorageViewModel(
    private val createFullBackupUseCase: CreateFullBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val exportTransactionsToCsvUseCase: ExportTransactionsToCsvUseCase,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataStorageUiState())

    val uiState: StateFlow<DataStorageUiState> = combine(
        _uiState,
        settingsRepository.lastBackupAt,
        settingsRepository.lastBackupFileName
    ) { state, lastAt, lastFileName ->
        state.copy(
            lastBackupAt = lastAt,
            lastBackupFileName = lastFileName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DataStorageUiState()
    )

    fun createBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = "Preparing backup...") }
            try {
                val backup = createFullBackupUseCase()
                val json = backupRepository.serializeBackup(backup)
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                val timestampStr = LocalDateTime.now().format(formatter)
                val fileName = "ExpenseTracker_Backup_$timestampStr.json"

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        backupJsonToWrite = json,
                        backupFileName = fileName
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = "Failed to create backup: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun onBackupWritten(fileName: String) {
        val nowStr = Instant.now().toString()
        viewModelScope.launch {
            settingsRepository.setLastBackupMetadata(nowStr, fileName)
            _uiState.update {
                it.copy(
                    backupJsonToWrite = null,
                    backupFileName = null,
                    successMessage = "Backup created successfully and saved to file!"
                )
            }
        }
    }

    fun cancelBackupWrite() {
        _uiState.update {
            it.copy(
                backupJsonToWrite = null,
                backupFileName = null
            )
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = "Exporting transactions to CSV...") }
            try {
                val csv = exportTransactionsToCsvUseCase()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val dateStr = LocalDateTime.now().format(formatter)
                val fileName = "ExpenseTracker_Transactions_$dateStr.csv"

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        csvStringToWrite = csv,
                        csvFileName = fileName
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = "CSV export failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun onCsvWritten() {
        _uiState.update {
            it.copy(
                csvStringToWrite = null,
                csvFileName = null,
                successMessage = "Transactions exported successfully as CSV!"
            )
        }
    }

    fun cancelCsvWrite() {
        _uiState.update {
            it.copy(
                csvStringToWrite = null,
                csvFileName = null
            )
        }
    }

    fun inspectBackupForRestore(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = "Inspecting backup...") }
            try {
                val result = backupRepository.deserializeBackup(json)
                if (result.isSuccess) {
                    val backup = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            pendingRestoreBackup = backup
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            error = "Invalid backup file: ${result.exceptionOrNull()?.localizedMessage ?: "Format mismatch"}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = "Failed to parse backup file: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun confirmAndRestore() {
        val backup = _uiState.value.pendingRestoreBackup ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = "Restoring from backup...") }
            val result = restoreBackupUseCase(backup)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        pendingRestoreBackup = null,
                        successMessage = "Database restored successfully! All data has been updated."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        pendingRestoreBackup = null,
                        error = "Restore failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown database error"}"
                    )
                }
            }
        }
    }

    fun cancelRestore() {
        _uiState.update {
            it.copy(
                pendingRestoreBackup = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                error = null,
                successMessage = null
            )
        }
    }
}
