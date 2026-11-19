package com.suguru.expensetracker.presentation.export

import androidx.annotation.StringRes
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.export.ExportFilter

enum class ExportFormat {
    BASIC_CSV,
    ADVANCED_CSV,
    PDF_REPORT
}

enum class ExportUiMessage(@get:StringRes val resourceId: Int) {
    INVALID_DATE_RANGE(R.string.ui_export_invalid_range),
    WRITING_FILE(R.string.ui_export_writing),
    EXPORT_COMPLETE(R.string.ui_export_complete),
    EXPORT_FAILED(R.string.ui_export_failed),
    GENERATING_FILE(R.string.ui_export_generating)
}

data class ExportUiState(
    val selectedFormat: ExportFormat = ExportFormat.BASIC_CSV,
    val filter: ExportFilter = ExportFilter(),
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availableCurrencies: List<String> = emptyList(),
    val isPreparingExport: Boolean = false,
    val isWritingFile: Boolean = false,
    val exportProgressMessage: ExportUiMessage? = null,
    val targetFileName: String? = null,
    val pendingExportAction: ExportFormat? = null,
    val successMessage: ExportUiMessage? = null,
    val errorMessage: ExportUiMessage? = null,
    val showFilterSheet: Boolean = false,
    val showPrivacyWarningDialog: Boolean = false
)
