package com.suguru.expensetracker.presentation.export

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.export.ExportFilter

enum class ExportFormat {
    BASIC_CSV,
    ADVANCED_CSV,
    PDF_REPORT
}

data class ExportUiState(
    val selectedFormat: ExportFormat = ExportFormat.BASIC_CSV,
    val filter: ExportFilter = ExportFilter(),
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availableCurrencies: List<String> = emptyList(),
    val isPreparingExport: Boolean = false,
    val isWritingFile: Boolean = false,
    val exportProgressMessage: String? = null,
    val targetFileName: String? = null,
    val pendingExportAction: ExportFormat? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val showFilterSheet: Boolean = false,
    val showPrivacyWarningDialog: Boolean = false
)
