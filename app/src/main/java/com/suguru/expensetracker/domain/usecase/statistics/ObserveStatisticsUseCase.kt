package com.suguru.expensetracker.domain.usecase.statistics

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.statistics.AccountAnalytics
import com.suguru.expensetracker.domain.model.statistics.CategoryAnalytics
import com.suguru.expensetracker.domain.model.statistics.CurrencyAnalyticsSummary
import com.suguru.expensetracker.domain.model.statistics.PeriodStatistics
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.model.statistics.StatisticsRange
import com.suguru.expensetracker.domain.model.statistics.TimeBucketAnalytics
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
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
        now: Instant = Instant.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): Flow<PeriodStatistics> {
        val range = periodOption.calculateRange(now, zoneId, customStart, customEnd)

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
        currencyTxs: List<com.suguru.expensetracker.domain.model.Transaction>,
        effectiveCurrency: String,
        zoneId: ZoneId
    ): List<TimeBucketAnalytics> {        return when (periodOption) {
            StatisticsPeriodOption.CUSTOM -> {
                val days = ChronoUnit.DAYS.between(range.startLocalDate, range.endLocalDateExclusive)
                val buckets = mutableListOf<TimeBucketAnalytics>()
                when {
                    days <= 31 -> {
                        // Daily buckets
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
                    }
                    days <= 90 -> {
                        // Weekly buckets
                        var currentWeekStart = range.startLocalDate
                        val weekFormatter = DateTimeFormatter.ofPattern("d MMM")
                        while (currentWeekStart.isBefore(range.endLocalDateExclusive)) {
                            var nextWeekStart = currentWeekStart.plusWeeks(1)
                            if (nextWeekStart.isAfter(range.endLocalDateExclusive)) {
                                nextWeekStart = range.endLocalDateExclusive
                            }
                            val startInst = currentWeekStart.atStartOfDay(zoneId).toInstant()
                            val endInst = nextWeekStart.atStartOfDay(zoneId).toInstant()
                            val weekTxs = currencyTxs.filter { tx ->
                                !tx.transactionTime.isBefore(startInst) && tx.transactionTime.isBefore(endInst)
                            }
                            var weekIncome = 0L
                            var weekExpense = 0L
                            for (tx in weekTxs) {
                                when (tx.type) {
                                    TransactionType.INCOME -> weekIncome = Math.addExact(weekIncome, tx.amount.amountInMinorUnits)
                                    TransactionType.EXPENSE -> weekExpense = Math.addExact(weekExpense, tx.amount.amountInMinorUnits)
                                    TransactionType.TRANSFER -> {}
                                }
                            }
                            buckets.add(
                                TimeBucketAnalytics(
                                    bucketLabel = "${currentWeekStart.format(weekFormatter)} - ${nextWeekStart.minusDays(1).format(weekFormatter)}",
                                    startDate = currentWeekStart,
                                    endDateExclusive = nextWeekStart,
                                    income = Money(weekIncome, effectiveCurrency),
                                    expense = Money(weekExpense, effectiveCurrency)
                                )
                            )
                            currentWeekStart = nextWeekStart
                        }
                    }
                    days <= 366 * 2 -> {
                        // Monthly buckets
                        var currentMonthStart = range.startLocalDate.withDayOfMonth(1)
                        if (currentMonthStart.isBefore(range.startLocalDate)) {
                            currentMonthStart = range.startLocalDate
                        }
                        val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
                        while (currentMonthStart.isBefore(range.endLocalDateExclusive)) {
                            var nextMonthStart = currentMonthStart.plusMonths(1).withDayOfMonth(1)
                            if (nextMonthStart.isAfter(range.endLocalDateExclusive)) {
                                nextMonthStart = range.endLocalDateExclusive
                            }
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
                    }
                    else -> {
                        // Yearly buckets
                        var currentYearStart = range.startLocalDate.withDayOfYear(1)
                        if (currentYearStart.isBefore(range.startLocalDate)) {
                            currentYearStart = range.startLocalDate
                        }
                        val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
                        while (currentYearStart.isBefore(range.endLocalDateExclusive)) {
                            var nextYearStart = currentYearStart.plusYears(1).withDayOfYear(1)
                            if (nextYearStart.isAfter(range.endLocalDateExclusive)) {
                                nextYearStart = range.endLocalDateExclusive
                            }
                            val startInst = currentYearStart.atStartOfDay(zoneId).toInstant()
                            val endInst = nextYearStart.atStartOfDay(zoneId).toInstant()
                            val yearTxs = currencyTxs.filter { tx ->
                                !tx.transactionTime.isBefore(startInst) && tx.transactionTime.isBefore(endInst)
                            }
                            var yearIncome = 0L
                            var yearExpense = 0L
                            for (tx in yearTxs) {
                                when (tx.type) {
                                    TransactionType.INCOME -> yearIncome = Math.addExact(yearIncome, tx.amount.amountInMinorUnits)
                                    TransactionType.EXPENSE -> yearExpense = Math.addExact(yearExpense, tx.amount.amountInMinorUnits)
                                    TransactionType.TRANSFER -> {}
                                }
                            }
                            buckets.add(
                                TimeBucketAnalytics(
                                    bucketLabel = currentYearStart.format(yearFormatter),
                                    startDate = currentYearStart,
                                    endDateExclusive = nextYearStart,
                                    income = Money(yearIncome, effectiveCurrency),
                                    expense = Money(yearExpense, effectiveCurrency)
                                )
                            )
                            currentYearStart = nextYearStart
                        }
                    }
                }
                buckets
            }
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
