package com.suguru.expensetracker.presentation.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.data.export.PdfFinancialReportRenderer
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.export.ExportFilter
import com.suguru.expensetracker.domain.model.export.FinancialReportData
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import com.suguru.expensetracker.domain.usecase.export.GenerateAdvancedCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.GenerateBasicCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.PrepareFinancialReportDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ExportViewModel(
    private val generateBasicCsvExportUseCase: GenerateBasicCsvExportUseCase,
    private val generateAdvancedCsvExportUseCase: GenerateAdvancedCsvExportUseCase,
    private val prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase,
    private val pdfFinancialReportRenderer: PdfFinancialReportRenderer,
    private val entitlementRepository: EntitlementRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                entitlementRepository.entitlement,
                accountRepository.observeAllAccounts(),
                categoryRepository.observeAllCategories()
            ) { entitlement, accounts, categories ->
                val currencies = accounts.map { it.initialBalance.currencyCode }.distinct().sorted()
                _uiState.update { current ->
                    current.copy(
                        proEntitlement = entitlement,
                        availableAccounts = accounts,
                        availableCategories = categories,
                        availableCurrencies = currencies
                    )
                }
            }.collect {}
        }
    }

    fun selectFormat(format: ExportFormat) {
        val currentState = _uiState.value
        val isPro = currentState.proEntitlement is ProEntitlement.Pro
        if ((format == ExportFormat.ADVANCED_CSV || format == ExportFormat.PDF_REPORT) && !isPro) {
            _uiState.update { it.copy(showProPaywallDialog = true) }
            return
        }
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun setDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            _uiState.update { it.copy(errorMessage = "Start date cannot be after end date") }
            return
        }
        _uiState.update {
            it.copy(
                filter = it.filter.copy(startDate = startDate, endDate = endDate),
                errorMessage = null
            )
        }
    }

    fun toggleAccountFilter(accountId: Long) {
        _uiState.update { state ->
            val current = state.filter.accountIds
            val updated = if (current.contains(accountId)) current - accountId else current + accountId
            state.copy(filter = state.filter.copy(accountIds = updated))
        }
    }

    fun toggleCategoryFilter(categoryId: Long) {
        _uiState.update { state ->
            val current = state.filter.categoryIds
            val updated = if (current.contains(categoryId)) current - categoryId else current + categoryId
            state.copy(filter = state.filter.copy(categoryIds = updated))
        }
    }

    fun toggleTransactionTypeFilter(type: TransactionType) {
        _uiState.update { state ->
            val current = state.filter.transactionTypes
            val updated = if (current.contains(type)) current - type else current + type
            state.copy(filter = state.filter.copy(transactionTypes = updated))
        }
    }

    fun toggleCurrencyFilter(currencyCode: String) {
        _uiState.update { state ->
            val current = state.filter.currencyCodes
            val updated = if (current.contains(currencyCode)) current - currencyCode else current + currencyCode
            state.copy(filter = state.filter.copy(currencyCodes = updated))
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(filter = ExportFilter()) }
    }

    fun showFilterSheet(show: Boolean) {
        _uiState.update { it.copy(showFilterSheet = show) }
    }

    fun dismissProPaywall() {
        _uiState.update { it.copy(showProPaywallDialog = false) }
    }

    fun dismissPrivacyWarning() {
        _uiState.update { it.copy(showPrivacyWarningDialog = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    /**
     * Initiates the export flow for the currently selected format.
     * Checks Pro entitlement for Pro-exclusive formats.
     */
    fun onExportRequested() {
        val currentState = _uiState.value
        val format = currentState.selectedFormat
        val isPro = currentState.proEntitlement is ProEntitlement.Pro

        if ((format == ExportFormat.ADVANCED_CSV || format == ExportFormat.PDF_REPORT) && !isPro) {
            _uiState.update { it.copy(showProPaywallDialog = true) }
            return
        }

        // Show privacy reminder and prepare filename
        val fileName = generateDefaultFileName(format, currentState.filter)
        _uiState.update {
            it.copy(
                targetFileName = fileName,
                pendingExportAction = format,
                showPrivacyWarningDialog = true
            )
        }
    }

    fun proceedWithSafExport() {
        _uiState.update {
            it.copy(showPrivacyWarningDialog = false)
        }
    }

    fun onExportCancelled() {
        _uiState.update {
            it.copy(
                isPreparingExport = false,
                isWritingFile = false,
                targetFileName = null,
                pendingExportAction = null,
                exportProgressMessage = null
            )
        }
    }

    suspend fun executeExportToStream(
        outputStream: OutputStream,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val currentState = _uiState.value
        val format = currentState.pendingExportAction ?: currentState.selectedFormat
        val filter = currentState.filter

        _uiState.update {
            it.copy(
                isWritingFile = true,
                exportProgressMessage = "Writing export file..."
            )
        }

        try {
            withContext(Dispatchers.IO) {
                when (format) {
                    ExportFormat.BASIC_CSV -> {
                        generateBasicCsvExportUseCase.exportToStream(outputStream, zoneId)
                    }
                    ExportFormat.ADVANCED_CSV -> {
                        generateAdvancedCsvExportUseCase.exportToStream(filter, outputStream, zoneId)
                    }
                    ExportFormat.PDF_REPORT -> {
                        val reportData = prepareFinancialReportDataUseCase(filter, zoneId)
                        pdfFinancialReportRenderer.renderReportToStream(reportData, outputStream, zoneId)
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isWritingFile = false,
                    isPreparingExport = false,
                    targetFileName = null,
                    pendingExportAction = null,
                    exportProgressMessage = null,
                    successMessage = "Export completed successfully!"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isWritingFile = false,
                    isPreparingExport = false,
                    targetFileName = null,
                    pendingExportAction = null,
                    exportProgressMessage = null,
                    errorMessage = "Export failed. Please retry: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun generateDefaultFileName(format: ExportFormat, filter: ExportFilter): String {
        val today = LocalDate.now().toString()
        return when (format) {
            ExportFormat.BASIC_CSV -> "ExpenseTracker_All_Transactions_$today.csv"
            ExportFormat.ADVANCED_CSV -> {
                if (filter.startDate != null && filter.endDate != null) {
                    "ExpenseTracker_Export_${filter.startDate}_to_${filter.endDate}.csv"
                } else {
                    "ExpenseTracker_Export_$today.csv"
                }
            }
            ExportFormat.PDF_REPORT -> {
                if (filter.startDate != null && filter.endDate != null) {
                    "ExpenseTracker_Financial_Report_${filter.startDate}_to_${filter.endDate}.pdf"
                } else {
                    "ExpenseTracker_Financial_Report_$today.pdf"
                }
            }
        }
    }
}
