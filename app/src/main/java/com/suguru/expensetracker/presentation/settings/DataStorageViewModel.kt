package com.suguru.expensetracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.repository.BackupRepository
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase
import com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ExportTransactionsToCsvUseCase
import com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator
import com.suguru.expensetracker.R
import com.suguru.expensetracker.presentation.util.UiMessage
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
    val message: UiMessage? = null,
    val error: UiMessage? = null,
    val successMessage: UiMessage? = null,
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
    private val settingsRepository: SettingsRepository,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null,
    private val onPendingSettingsRecovery: suspend () -> InternalDefaultAccountProvisioningResult? = { null }
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
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = UiMessage(R.string.data_progress_backup)) }
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
                        error = UiMessage(R.string.data_backup_failed)
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
                    successMessage = UiMessage(R.string.data_backup_success)
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
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = UiMessage(R.string.data_progress_csv)) }
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
                        error = UiMessage(R.string.data_csv_failed)
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
                successMessage = UiMessage(R.string.data_csv_success)
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
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = UiMessage(R.string.data_progress_inspect)) }
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
                            error = UiMessage(R.string.data_backup_invalid)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        error = UiMessage(R.string.data_backup_invalid)
                    )
                }
            }
        }
    }

    fun confirmAndRestore() {
        val backup = _uiState.value.pendingRestoreBackup ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null, message = UiMessage(R.string.data_progress_restore)) }
            val result = restoreBackupUseCase(backup)
            when {
                result.isFailure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            pendingRestoreBackup = null,
                            error = UiMessage(R.string.data_restore_failed)
                        )
                    }
                }
                result.getOrNull() == BackupRestoreOutcome.SettingsRecoveryPending -> {
                    widgetRefreshCoordinator?.refreshAll()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            pendingRestoreBackup = null,
                            successMessage = UiMessage(R.string.data_restore_pending_success)
                        )
                    }
                    onPendingSettingsRecovery()
                }
                else -> {
                    widgetRefreshCoordinator?.refreshAll()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            pendingRestoreBackup = null,
                            successMessage = UiMessage(R.string.data_restore_success)
                        )
                    }
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
