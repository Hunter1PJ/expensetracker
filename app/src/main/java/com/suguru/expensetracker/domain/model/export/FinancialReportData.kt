package com.suguru.expensetracker.domain.model.export

import com.suguru.expensetracker.domain.model.Money

/**
 * Summary totals for a single currency in an exported financial report.
 */
data class ExportCurrencySummary(
    val currencyCode: String,
    val totalIncome: Money,
    val totalExpense: Money,
    val netAmount: Money,
    val transactionCount: Int
)

/**
 * Category breakdown item for a single currency in an exported financial report.
 */
data class ExportCategoryItem(
    val categoryId: Long?,
    val categoryName: String,
    val totalAmount: Money,
    val transactionCount: Int,
    val isArchived: Boolean
)

/**
 * Complete immutable snapshot of data prepared for rendering a financial report.
 */
data class FinancialReportData(
    val filter: ExportFilter,
    val generatedAt: java.time.Instant,
    val transactions: List<ExportTransactionRow>,
    val currencySummaries: List<ExportCurrencySummary>,
    val expenseCategoriesByCurrency: Map<String, List<ExportCategoryItem>>,
    val incomeCategoriesByCurrency: Map<String, List<ExportCategoryItem>>,
    val totalTransactionsCount: Int
)
