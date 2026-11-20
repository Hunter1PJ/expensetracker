package com.suguru.expensetracker.presentation.export

import android.content.Context
import com.suguru.expensetracker.R
import com.suguru.expensetracker.data.export.PdfFinancialReportLabels
import java.util.Locale

fun localizedPdfReportLabels(context: Context): PdfFinancialReportLabels {
    val resources = context.resources
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        resources.configuration.locales[0] ?: Locale.ENGLISH
    } else {
        @Suppress("DEPRECATION")
        resources.configuration.locale ?: Locale.ENGLISH
    }
    val values = mapOf(
        "title" to context.getString(R.string.pdf_title),
        "generatedOn" to context.getString(R.string.pdf_generated_on),
        "periodRange" to context.getString(R.string.pdf_period_range),
        "periodFrom" to context.getString(R.string.pdf_period_from),
        "periodAll" to context.getString(R.string.pdf_period_all),
        "filterTypes" to context.getString(R.string.pdf_filter_types),
        "filterCurrencies" to context.getString(R.string.pdf_filter_currencies),
        "filters" to context.getString(R.string.pdf_filter_custom),
        "filterCustom" to context.getString(R.string.pdf_filter_selection),
        "footer" to context.getString(R.string.pdf_footer),
        "page" to context.getString(R.string.pdf_page),
        "summaryHeading" to context.getString(R.string.pdf_summary_heading),
        "summaryContinued" to context.getString(R.string.pdf_summary_continued),
        "currencyTotals" to context.getString(R.string.pdf_transactions_count),
        "income" to context.getString(R.string.pdf_income),
        "expense" to context.getString(R.string.pdf_expense),
        "transfer" to context.getString(R.string.pdf_type_transfer),
        "net" to context.getString(R.string.pdf_net),
        "categoryBreakdown" to context.getString(R.string.pdf_category_breakdown),
        "topExpenses" to context.getString(R.string.pdf_top_expenses),
        "topIncomes" to context.getString(R.string.pdf_top_incomes),
        "categoryCount" to context.getString(R.string.pdf_category_count),
        "ledgerTitle" to context.getString(R.string.pdf_ledger_title),
        "columnDate" to context.getString(R.string.pdf_column_date),
        "columnType" to context.getString(R.string.pdf_column_type),
        "columnCategory" to context.getString(R.string.pdf_column_category),
        "columnAccount" to context.getString(R.string.pdf_column_account),
        "columnAmount" to context.getString(R.string.pdf_column_amount),
        "columnNote" to context.getString(R.string.pdf_column_note),
        "uncategorized" to context.getString(R.string.pdf_empty_category),
        "emptyTransactions" to context.getString(R.string.pdf_empty_transactions)
    )
    return PdfFinancialReportLabels(locale, values)
}
