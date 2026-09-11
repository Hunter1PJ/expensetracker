package com.example.domain.usecase.statistics

import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.domain.model.statistics.AccountAnalytics
import com.example.domain.model.statistics.CategoryAnalytics
import com.example.domain.model.statistics.CurrencyAnalyticsSummary
import com.example.domain.model.statistics.PeriodStatistics
import com.example.domain.model.statistics.StatisticsPeriodOption
import com.example.domain.model.statistics.StatisticsRange
import com.example.domain.model.statistics.TimeBucketAnalytics
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Domain use case that reacts to transaction, account, and category changes to compute
 * period-based financial analytics, category breakdowns, account analytics, and trend data.
 * Adheres strictly to multi-currency isolation, transfer neutrality, and exact Long minor-unit arithmetic.
 */
class ObserveStatisticsUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    operator fun invoke(
        periodOption: StatisticsPeriodOption,
        selectedCurrencyCode: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): Flow<PeriodStatistics> {        val range = periodOption.calculateRange(now, zoneId)

        return combine(
            transactionRepository.observeTransactionsBetween(range.startInclusive, range.endExclusive),
            accountRepository.observeAllAccounts(),
            categoryRepository.observeAllCategories()
        ) { transactions, accounts, categories ->
            val accountMap = accounts.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }

            // Determine currency for each transaction directly from transaction amount
            val txWithCurrency = transactions.map { tx ->
                val currency = tx.amount.currencyCode
                tx to currency
            }

            // Identify available currencies in this period + active accounts
            val periodCurrencies = txWithCurrency.map { it.second }.toSet()
            val activeAccountCurrencies = accounts.filter { !it.isArchived }.map { it.initialBalance.currencyCode }.toSet()
            val availableCurrenciesList = (periodCurrencies + activeAccountCurrencies)
                .filter { it.isNotBlank() }
                .sorted()
                .ifEmpty { listOf("USD") }

            // Determine effective currency
            val effectiveCurrency = when {
                selectedCurrencyCode != null && availableCurrenciesList.contains(selectedCurrencyCode) -> {
                    selectedCurrencyCode
                }
                else -> {
                    // Pick currency with highest transaction count in this period
                    val currencyCounts = txWithCurrency.groupingBy { it.second }.eachCount()
                    val maxCurrency = currencyCounts.maxByOrNull { it.value }?.key
                    if (maxCurrency != null && availableCurrenciesList.contains(maxCurrency)) {
                        maxCurrency
                    } else {
                        activeAccountCurrencies.firstOrNull() ?: availableCurrenciesList.first()
                    }
                }
            }

            // Filter transactions matching the effective currency
            val currencyTxs = txWithCurrency
                .filter { it.second == effectiveCurrency }
                .map { it.first }

            // Calculate Income, Expense, and Net
            var incomeMinorUnits = 0L
            var expenseMinorUnits = 0L
            var totalTxCount = 0

            for (tx in currencyTxs) {
                when (tx.type) {
                    TransactionType.INCOME -> {
                        incomeMinorUnits = Math.addExact(incomeMinorUnits, tx.amount.amountInMinorUnits)
                        totalTxCount++
                    }
                    TransactionType.EXPENSE -> {
                        expenseMinorUnits = Math.addExact(expenseMinorUnits, tx.amount.amountInMinorUnits)
                        totalTxCount++
                    }
                    TransactionType.TRANSFER -> {
                        // Transfers do NOT count towards income or expense
                    }
                }
            }

            val netMinorUnits = Math.subtractExact(incomeMinorUnits, expenseMinorUnits)

            val summary = CurrencyAnalyticsSummary(
                currencyCode = effectiveCurrency,
                income = Money(incomeMinorUnits, effectiveCurrency),
                expense = Money(expenseMinorUnits, effectiveCurrency),
                net = Money(netMinorUnits, effectiveCurrency),
                transactionCount = totalTxCount
            )

            // Expense Category Breakdown
            val expenseTxs = currencyTxs.filter { it.type == TransactionType.EXPENSE }
            val expenseCategoryGroups = expenseTxs.groupBy { it.categoryId }
            val expenseCategoriesList = expenseCategoryGroups.map { (catId, txList) ->
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
                    amount = Money(catTotal, effectiveCurrency),
                    transactionCount = txList.size,
                    isArchived = cat?.isArchived == true
                )
            }.sortedByDescending { it.amount.amountInMinorUnits }

            // Income Category Breakdown
            val incomeTxs = currencyTxs.filter { it.type == TransactionType.INCOME }
            val incomeCategoryGroups = incomeTxs.groupBy { it.categoryId }
            val incomeCategoriesList = incomeCategoryGroups.map { (catId, txList) ->
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
                    amount = Money(catTotal, effectiveCurrency),
                    transactionCount = txList.size,
                    isArchived = cat?.isArchived == true
                )
            }.sortedByDescending { it.amount.amountInMinorUnits }

            // Account Breakdown (accounts matching effectiveCurrency)
            val currencyAccounts = accounts.filter { it.initialBalance.currencyCode == effectiveCurrency }
            val accountAnalyticsList = currencyAccounts.map { acc ->
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
                    income = Money(accIncome, effectiveCurrency),
                    expense = Money(accExpense, effectiveCurrency),
                    isArchived = acc.isArchived
                )
            }.sortedWith(
                compareByDescending<AccountAnalytics> { it.expense.amountInMinorUnits }
                    .thenBy { it.accountName }
            )

            // Trend Buckets
            val trendBucketsList = generateTrendBuckets(
                periodOption = periodOption,
                range = range,
                currencyTxs = currencyTxs,
                effectiveCurrency = effectiveCurrency,
                zoneId = zoneId
            )

            PeriodStatistics(
                periodOption = periodOption,
                range = range,
                availableCurrencies = availableCurrenciesList,
                selectedCurrencyCode = effectiveCurrency,
                currencySummary = summary,
                expenseCategories = expenseCategoriesList,
                incomeCategories = incomeCategoriesList,
                accountBreakdown = accountAnalyticsList,
                trendBuckets = trendBucketsList,
                totalTransactionsInPeriod = currencyTxs.size
            )
        }
    }

    private fun generateTrendBuckets(
        periodOption: StatisticsPeriodOption,
        range: StatisticsRange,
        currencyTxs: List<com.example.domain.model.Transaction>,
        effectiveCurrency: String,
        zoneId: ZoneId
    ): List<TimeBucketAnalytics> {        return when (periodOption) {
            StatisticsPeriodOption.THIS_MONTH, StatisticsPeriodOption.LAST_MONTH -> {
                // Daily buckets
                val buckets = mutableListOf<TimeBucketAnalytics>()
                var currentDay = range.startLocalDate
                val dayFormatter = DateTimeFormatter.ofPattern("d MMM")

                while (currentDay.isBefore(range.endLocalDateExclusive)) {
                    val nextDay = currentDay.plusDays(1)
                    val startInst = currentDay.atStartOfDay(zoneId).toInstant()
                    val endInst = nextDay.atStartOfDay(zoneId).toInstant()

                    val dayTxs = currencyTxs.filter { tx ->
                        !tx.transactionTime.isBefore(startInst) && tx.transactionTime.isBefore(endInst)
                    }

                    var dayIncome = 0L
                    var dayExpense = 0L
                    for (tx in dayTxs) {
                        when (tx.type) {
                            TransactionType.INCOME -> dayIncome = Math.addExact(dayIncome, tx.amount.amountInMinorUnits)
                            TransactionType.EXPENSE -> dayExpense = Math.addExact(dayExpense, tx.amount.amountInMinorUnits)
                            TransactionType.TRANSFER -> {}
                        }
                    }

                    buckets.add(
                        TimeBucketAnalytics(
                            bucketLabel = currentDay.format(dayFormatter),
                            startDate = currentDay,
                            endDateExclusive = nextDay,
                            income = Money(dayIncome, effectiveCurrency),
                            expense = Money(dayExpense, effectiveCurrency)
                        )
                    )
                    currentDay = nextDay
                }
                buckets
            }
            StatisticsPeriodOption.LAST_3_MONTHS -> {
                // 3 Monthly buckets
                val buckets = mutableListOf<TimeBucketAnalytics>()
                var currentMonthStart = range.startLocalDate
                val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

                while (currentMonthStart.isBefore(range.endLocalDateExclusive)) {
                    val nextMonthStart = currentMonthStart.plusMonths(1)
                    val startInst = currentMonthStart.atStartOfDay(zoneId).toInstant()
                    val endInst = nextMonthStart.atStartOfDay(zoneId).toInstant()

                    val monthTxs = currencyTxs.filter { tx ->
                        !tx.transactionTime.isBefore(startInst) && tx.transactionTime.isBefore(endInst)
                    }

                    var monthIncome = 0L
                    var monthExpense = 0L
                    for (tx in monthTxs) {
                        when (tx.type) {
                            TransactionType.INCOME -> monthIncome = Math.addExact(monthIncome, tx.amount.amountInMinorUnits)
                            TransactionType.EXPENSE -> monthExpense = Math.addExact(monthExpense, tx.amount.amountInMinorUnits)
                            TransactionType.TRANSFER -> {}
                        }
                    }

                    buckets.add(
                        TimeBucketAnalytics(
                            bucketLabel = currentMonthStart.format(monthFormatter),
                            startDate = currentMonthStart,
                            endDateExclusive = nextMonthStart,
                            income = Money(monthIncome, effectiveCurrency),
                            expense = Money(monthExpense, effectiveCurrency)
                        )
                    )
                    currentMonthStart = nextMonthStart
                }
                buckets
            }
            StatisticsPeriodOption.THIS_YEAR -> {
                // 12 Monthly buckets
                val buckets = mutableListOf<TimeBucketAnalytics>()
                var currentMonthStart = range.startLocalDate
                val monthFormatter = DateTimeFormatter.ofPattern("MMM")

                while (currentMonthStart.isBefore(range.endLocalDateExclusive)) {
                    val nextMonthStart = currentMonthStart.plusMonths(1)
                    val startInst = currentMonthStart.atStartOfDay(zoneId).toInstant()
                    val endInst = nextMonthStart.atStartOfDay(zoneId).toInstant()

                    val monthTxs = currencyTxs.filter { tx ->
                        !tx.transactionTime.isBefore(startInst) && tx.transactionTime.isBefore(endInst)
                    }

                    var monthIncome = 0L
                    var monthExpense = 0L
                    for (tx in monthTxs) {
                        when (tx.type) {
                            TransactionType.INCOME -> monthIncome = Math.addExact(monthIncome, tx.amount.amountInMinorUnits)
                            TransactionType.EXPENSE -> monthExpense = Math.addExact(monthExpense, tx.amount.amountInMinorUnits)
                            TransactionType.TRANSFER -> {}
                        }
                    }

                    buckets.add(
                        TimeBucketAnalytics(
                            bucketLabel = currentMonthStart.format(monthFormatter),
                            startDate = currentMonthStart,
                            endDateExclusive = nextMonthStart,
                            income = Money(monthIncome, effectiveCurrency),
                            expense = Money(monthExpense, effectiveCurrency)
                        )
                    )
                    currentMonthStart = nextMonthStart
                }
                buckets
            }
        }
    }
}
