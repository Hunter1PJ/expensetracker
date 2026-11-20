package com.suguru.expensetracker.domain.analytics

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.statistics.AccountAnalytics
import com.suguru.expensetracker.domain.model.statistics.CategoryAnalytics
import com.suguru.expensetracker.domain.model.statistics.CurrencyAnalyticsSummary

/**
 * Shared canonical financial aggregation engine.
 *
 * Provides single-source-of-truth mathematical calculations for:
 * - Income aggregation (transfers excluded)
 * - Expense aggregation (transfers excluded)
 * - Net calculation (income minus expense in exact minor units)
 * - Category totals & breakdowns
 * - Account totals & breakdowns
 * - Multi-currency isolation
 *
 * Strictly adheres to:
 * - Exact Long minor-unit integer arithmetic (`Math.addExact`, `Math.subtractExact`)
 * - Zero Float/Double intermediate representations
 * - Transfer neutrality across all summary and category metrics
 * - Robust handling of archived and null categories
 */
class CanonicalFinancialAggregator {

    /**
     * Aggregates total income, total expense, net balance, and transaction count
     * for a given currency from a list of transactions.
     */
    fun aggregateCurrencySummary(
        currency: String,
        transactions: List<Transaction>
    ): CurrencyAnalyticsSummary {
        val currencyTxs = transactions.filter { it.amount.currencyCode == currency }

        var incomeMinorUnits = 0L
        var expenseMinorUnits = 0L
        var txCount = 0

        for (tx in currencyTxs) {
            when (tx.type) {
                TransactionType.INCOME -> {
                    incomeMinorUnits = Math.addExact(incomeMinorUnits, tx.amount.amountInMinorUnits)
                    txCount++
                }
                TransactionType.EXPENSE -> {
                    expenseMinorUnits = Math.addExact(expenseMinorUnits, tx.amount.amountInMinorUnits)
                    txCount++
                }
                TransactionType.TRANSFER -> {
                    // Transfer neutrality: transfers do NOT contribute to income or expense totals or summary transactionCount
                }
            }
        }

        val netMinorUnits = Math.subtractExact(incomeMinorUnits, expenseMinorUnits)

        return CurrencyAnalyticsSummary(
            currencyCode = currency,
            income = Money(incomeMinorUnits, currency),
            expense = Money(expenseMinorUnits, currency),
            net = Money(netMinorUnits, currency),
            transactionCount = txCount
        )
    }

    /**
     * Computes the category breakdown for a specific transaction type (EXPENSE or INCOME)
     * and currency, correctly linking category metadata and sorting by descending amount.
     */
    fun aggregateCategoryBreakdown(
        currency: String,
        transactions: List<Transaction>,
        categories: List<Category>,
        type: TransactionType
    ): List<CategoryAnalytics> {
        val categoryMap = categories.associateBy { it.id }
        val matchingTxs = transactions.filter {
            it.amount.currencyCode == currency && it.type == type
        }

        val categoryGroups = matchingTxs.groupBy { it.categoryId }

        return categoryGroups.map { (catId, txList) ->
            var catTotal = 0L
            for (tx in txList) {
                catTotal = Math.addExact(catTotal, tx.amount.amountInMinorUnits)
            }
            val cat = catId?.let { categoryMap[it] }
            CategoryAnalytics(
                categoryId = catId,
                categoryName = cat?.name ?: "Uncategorized",
                iconName = cat?.iconName,
                colorHex = cat?.colorHex,
                amount = Money(catTotal, currency),
                transactionCount = txList.size,
                isArchived = cat?.isArchived == true,
                isSystem = cat?.isSystem == true
            )
        }.sortedByDescending { it.amount.amountInMinorUnits }
    }

    /**
     * Computes account income and expense analytics for accounts matching a given currency.
     */
    fun aggregateAccountBreakdown(
        currency: String,
        transactions: List<Transaction>,
        accounts: List<Account>
    ): List<AccountAnalytics> {
        val currencyTxs = transactions.filter { it.amount.currencyCode == currency }
        val currencyAccounts = accounts.filter { it.initialBalance.currencyCode == currency }

        return currencyAccounts.map { acc ->
            val accTxs = currencyTxs.filter { it.accountId == acc.id }
            var accIncome = 0L
            var accExpense = 0L
            for (tx in accTxs) {
                when (tx.type) {
                    TransactionType.INCOME -> accIncome = Math.addExact(accIncome, tx.amount.amountInMinorUnits)
                    TransactionType.EXPENSE -> accExpense = Math.addExact(accExpense, tx.amount.amountInMinorUnits)
                    TransactionType.TRANSFER -> {}
                }
            }
            AccountAnalytics(
                accountId = acc.id,
                accountName = acc.name,
                accountType = acc.type,
                currencyCode = acc.initialBalance.currencyCode,
                income = Money(accIncome, currency),
                expense = Money(accExpense, currency),
                isArchived = acc.isArchived
            )
        }.sortedWith(
            compareByDescending<AccountAnalytics> { it.expense.amountInMinorUnits }
                .thenBy { it.accountName }
        )
    }
}
