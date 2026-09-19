package com.suguru.expensetracker.domain.usecase.export

import com.suguru.expensetracker.domain.analytics.CanonicalFinancialAggregator
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.export.ExportCategoryItem
import com.suguru.expensetracker.domain.model.export.ExportCurrencySummary
import com.suguru.expensetracker.domain.model.export.ExportFilter
import com.suguru.expensetracker.domain.model.export.ExportTransactionRow
import com.suguru.expensetracker.domain.model.export.FinancialReportData
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Prepares an immutable snapshot of financial data and canonical statistics aggregations
 * for filtered CSV export and PDF report rendering.
 *
 * Reuses the single-source-of-truth [CanonicalFinancialAggregator] for:
 * - Exact Long minor-unit arithmetic
 * - Multi-currency isolation (no cross-currency merging)
 * - Transfer neutrality (transfers never count toward income/expense/net totals)
 * - Canonical category breakdowns matching the Statistics engine
 * - Safe resolution of archived and missing entities
 */
class PrepareFinancialReportDataUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val canonicalAggregator: CanonicalFinancialAggregator = CanonicalFinancialAggregator()
) {

    suspend operator fun invoke(
        filter: ExportFilter,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): FinancialReportData {
        // Validate date bounds
        if (filter.startDate != null && filter.endDate != null) {
            require(!filter.startDate.isAfter(filter.endDate)) {
                "Start date cannot be after end date"
            }
        }

        val allTransactions = if (filter.startDate != null || filter.endDate != null) {
            val startInclusive = (filter.startDate ?: java.time.LocalDate.of(1970, 1, 1)).atStartOfDay(zoneId).toInstant()
            val endExclusive = (filter.endDate ?: java.time.LocalDate.of(2100, 1, 1)).plusDays(1).atStartOfDay(zoneId).toInstant()
            transactionRepository.observeTransactionsBetween(startInclusive, endExclusive).first()
        } else {
            transactionRepository.observeAllTransactions().first()
        }

        val accounts = accountRepository.observeAllAccounts().first()
        val categories = categoryRepository.observeAllCategories().first()
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        // Filter transactions logically
        val filteredTxs = allTransactions.filter { tx ->
            matchesFilter(tx, filter)
        }

        // Build resolved transaction rows
        val exportRows = filteredTxs.map { tx ->
            val sourceAccountName = accountMap[tx.accountId]?.name ?: "Unknown Account (${tx.accountId})"
            val destAccountName = if (tx.type == TransactionType.TRANSFER) {
                tx.destinationAccountId?.let { id ->
                    accountMap[id]?.name ?: "Unknown Account ($id)"
                }
            } else null

            val categoryName = if (tx.type != TransactionType.TRANSFER) {
                tx.categoryId?.let { id ->
                    categoryMap[id]?.name ?: "Unknown Category ($id)"
                }
            } else null

            ExportTransactionRow(
                transactionId = tx.id,
                transactionTime = tx.transactionTime,
                type = tx.type,
                amount = tx.amount,
                currencyCode = tx.amount.currencyCode,
                sourceAccountName = sourceAccountName,
                destinationAccountName = destAccountName,
                categoryName = categoryName,
                note = tx.note,
                recurringRuleId = tx.recurringRuleId,
                recurringOccurrenceDate = tx.recurringOccurrenceDate
            )
        }

        // Group filtered transactions by currency to compute isolated summaries via canonical aggregator
        val currencies = filteredTxs.map { it.amount.currencyCode }.distinct().sorted()

        val currencySummaries = mutableListOf<ExportCurrencySummary>()
        val expenseCategoriesByCurrency = mutableMapOf<String, List<ExportCategoryItem>>()
        val incomeCategoriesByCurrency = mutableMapOf<String, List<ExportCategoryItem>>()

        for (curr in currencies) {
            // Canonical Currency Summary
            val canonicalSummary = canonicalAggregator.aggregateCurrencySummary(
                currency = curr,
                transactions = filteredTxs
            )
            currencySummaries.add(
                ExportCurrencySummary(
                    currencyCode = canonicalSummary.currencyCode,
                    totalIncome = canonicalSummary.income,
                    totalExpense = canonicalSummary.expense,
                    netAmount = canonicalSummary.net,
                    transactionCount = canonicalSummary.transactionCount
                )
            )

            // Canonical Expense Category Breakdown
            val canonicalExpenseCategories = canonicalAggregator.aggregateCategoryBreakdown(
                currency = curr,
                transactions = filteredTxs,
                categories = categories,
                type = TransactionType.EXPENSE
            )
            expenseCategoriesByCurrency[curr] = canonicalExpenseCategories.map {
                ExportCategoryItem(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    totalAmount = it.amount,
                    transactionCount = it.transactionCount,
                    isArchived = it.isArchived
                )
            }

            // Canonical Income Category Breakdown
            val canonicalIncomeCategories = canonicalAggregator.aggregateCategoryBreakdown(
                currency = curr,
                transactions = filteredTxs,
                categories = categories,
                type = TransactionType.INCOME
            )
            incomeCategoriesByCurrency[curr] = canonicalIncomeCategories.map {
                ExportCategoryItem(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    totalAmount = it.amount,
                    transactionCount = it.transactionCount,
                    isArchived = it.isArchived
                )
            }
        }

        return FinancialReportData(
            filter = filter,
            generatedAt = now,
            transactions = exportRows,
            currencySummaries = currencySummaries,
            expenseCategoriesByCurrency = expenseCategoriesByCurrency,
            incomeCategoriesByCurrency = incomeCategoriesByCurrency,
            totalTransactionsCount = exportRows.size
        )
    }

    private fun matchesFilter(tx: Transaction, filter: ExportFilter): Boolean {
        // Transaction Type filter
        if (filter.transactionTypes.isNotEmpty() && !filter.transactionTypes.contains(tx.type)) {
            return false
        }

        // Currency filter
        if (filter.currencyCodes.isNotEmpty() && !filter.currencyCodes.contains(tx.amount.currencyCode)) {
            return false
        }

        // Account filter
        if (filter.accountIds.isNotEmpty()) {
            val accountMatches = when (tx.type) {
                TransactionType.TRANSFER -> {
                    filter.accountIds.contains(tx.accountId) ||
                            (tx.destinationAccountId != null && filter.accountIds.contains(tx.destinationAccountId))
                }
                else -> {
                    filter.accountIds.contains(tx.accountId)
                }
            }
            if (!accountMatches) {
                return false
            }
        }

        // Category filter
        if (filter.categoryIds.isNotEmpty()) {
            if (tx.type == TransactionType.TRANSFER) {
                // Transfers have no category; excluded when category filter is active
                return false
            }
            if (tx.categoryId == null || !filter.categoryIds.contains(tx.categoryId)) {
                return false
            }
        }

        return true
    }
}
