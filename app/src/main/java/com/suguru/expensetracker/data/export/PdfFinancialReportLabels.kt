package com.suguru.expensetracker.data.export

import java.util.Locale

/**
 * Localized static copy for a generated financial report. Values are resolved from app resources
 * before rendering so the PDF renderer stays independent of Android Context and resources.
 */
data class PdfFinancialReportLabels(
    val locale: Locale,
    private val values: Map<String, String>
) {
    fun text(key: String, vararg arguments: Any): String =
        String.format(locale, values.getValue(key), *arguments)
}
