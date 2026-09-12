package com.suguru.expensetracker.domain.usecase.dashboard

import com.suguru.expensetracker.domain.model.CurrencySummary
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import com.suguru.expensetracker.domain.util.DateTimeRangeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/**
 * Observes aggregated monthly financial summaries grouped by currency.
 *
 * Rules:
 * - Active account balances are grouped and totaled by currency.
 * - Monthly income counts only TransactionType.INCOME in the given month.
 * - Monthly expense counts only TransactionType.EXPENSE in the given month.
 * - Transfers are strictly excluded from monthly income, expense, and net totals.
 * - Monthly Net = monthlyIncome - monthlyExpense.
 * - Overflow-safe Long minor units arithmetic is used for all sums.
 * - Currencies are never mixed or converted.
 */
class ObserveDashboardSummaryUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(
        startTime: Instant? = null,
        endTime: Instant? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<CurrencySummary>> {
        val (monthStart, monthEnd) = if (startTime != null && endTime != null) {
            Pair(startTime, endTime)
        } else {
            DateTimeRangeUtils.getCurrentMonthRange(zoneId)
        }

        return combine(
            accountRepository.observeActiveAccounts(),
            transactionRepository.observeAllTransactions(),
            transactionRepository.observeTransactionsBetween(monthStart, monthEnd)
        ) { accounts, allTransactions, monthTransactions ->
            if (accounts.isEmpty()) {
                return@combine emptyList()
            }

            val accountsByCurrency = accounts.groupBy { it.initialBalance.currencyCode }
            val distinctCurrencies = accountsByCurrency.keys.toList()

            distinctCurrencies.map { currencyCode ->
                val currencyAccounts = accountsByCurrency[currencyCode] ?: emptyList()
                var totalBalanceMinorUnits = 0L

                for (account in currencyAccounts) {
                    var accountBalance = account.initialBalance.amountInMinorUnits
                    val accountTxs = allTransactions.filter {
                        (it.accountId == account.id || it.destinationAccountId == account.id) &&
                                it.amount.currencyCode == currencyCode
                    }
                    for (tx in accountTxs) {
                        when (tx.type) {
                            TransactionType.INCOME -> {
                                if (tx.accountId == account.id) {
                                    accountBalance = Math.addExact(accountBalance, tx.amount.amountInMinorUnits)
                                }
                            }
                            TransactionType.EXPENSE -> {
                                if (tx.accountId == account.id) {
                                    accountBalance = Math.subtractExact(accountBalance, tx.amount.amountInMinorUnits)
                                }
                            }
                            TransactionType.TRANSFER -> {
                                if (tx.accountId == account.id) {
                                    accountBalance = Math.subtractExact(accountBalance, tx.amount.amountInMinorUnits)
                                }
                                if (tx.destinationAccountId == account.id) {
                                    accountBalance = Math.addExact(accountBalance, tx.amount.amountInMinorUnits)
                                }
                            }
                        }
                    }
                    totalBalanceMinorUnits = Math.addExact(totalBalanceMinorUnits, accountBalance)
                }

                var monthlyIncomeMinorUnits = 0L
                var monthlyExpenseMinorUnits = 0L

                val currencyMonthTxs = monthTransactions.filter { it.amount.currencyCode == currencyCode }
                for (tx in currencyMonthTxs) {
                    when (tx.type) {
                        TransactionType.INCOME -> {
                            monthlyIncomeMinorUnits = Math.addExact(monthlyIncomeMinorUnits, tx.amount.amountInMinorUnits)
                        }
                        TransactionType.EXPENSE -> {
                            monthlyExpenseMinorUnits = Math.addExact(monthlyExpenseMinorUnits, tx.amount.amountInMinorUnits)
                        }
                        TransactionType.TRANSFER -> {
                            // Transfers do not count towards monthly income or expense
                        }
                    }
                }

                val monthlyNetMinorUnits = Math.subtractExact(monthlyIncomeMinorUnits, monthlyExpenseMinorUnits)

                CurrencySummary(
                    currencyCode = currencyCode,
                    totalBalance = Money(totalBalanceMinorUnits, currencyCode),
                    monthlyIncome = Money(monthlyIncomeMinorUnits, currencyCode),
                    monthlyExpense = Money(monthlyExpenseMinorUnits, currencyCode),
                    monthlyNet = Money(monthlyNetMinorUnits, currencyCode)
                )
            }
        }
    }
}
