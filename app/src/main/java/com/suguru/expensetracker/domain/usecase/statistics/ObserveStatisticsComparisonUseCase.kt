package com.suguru.expensetracker.domain.usecase.statistics

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.statistics.AlignedTrendPoint
import com.suguru.expensetracker.domain.model.statistics.CategoryComparisonMetric
import com.suguru.expensetracker.domain.model.statistics.ComparisonCalculationUtils
import com.suguru.expensetracker.domain.model.statistics.CurrencyComparisonSummary
import com.suguru.expensetracker.domain.model.statistics.MetricType
import com.suguru.expensetracker.domain.model.statistics.PeriodStatistics
import com.suguru.expensetracker.domain.model.statistics.ResolvedComparisonRanges
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparison
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonDateResolver
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObserveStatisticsComparisonUseCase(
    private val observeStatisticsUseCase: ObserveStatisticsUseCase
) {

    operator fun invoke(
        periodOption: StatisticsPeriodOption,
        mode: StatisticsComparisonMode,
        selectedCurrencyCode: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): Flow<StatisticsComparison> {
        val today = now.atZone(zoneId).toLocalDate()
        val ranges = StatisticsComparisonDateResolver.resolve(
            periodOption = periodOption,
            mode = mode,
            today = today,
            zoneId = zoneId,
            customStart = customStart,
            customEnd = customEnd
        )

        return combine(
            observeStatisticsUseCase.transactionRepository.observeTransactionsBetween(
                ranges.primaryRange.startInclusive, ranges.primaryRange.endExclusive
            ),
            observeStatisticsUseCase.transactionRepository.observeTransactionsBetween(
                ranges.comparisonRange.startInclusive, ranges.comparisonRange.endExclusive
            ),
            observeStatisticsUseCase.accountRepository.observeAllAccounts(),
            observeStatisticsUseCase.categoryRepository.observeAllCategories()
        ) { primaryTxs, compTxs, accounts, categories ->
            val primaryCurrencies = primaryTxs.map { it.amount.currencyCode }.toSet()
            val compCurrencies = compTxs.map { it.amount.currencyCode }.toSet()
            val activeAccountCurrencies = accounts
                .filter { !it.isArchived }
                .map { it.initialBalance.currencyCode }
                .toSet()

            val allAvailableCurrencies = (primaryCurrencies + compCurrencies + activeAccountCurrencies)
                .filter { it.isNotBlank() }
                .sorted()
                .ifEmpty { listOf("USD") }

            val effectiveCurrency = when {
                selectedCurrencyCode != null && allAvailableCurrencies.contains(selectedCurrencyCode) -> {
                    selectedCurrencyCode
                }
                else -> {
                    val primaryCounts = primaryTxs.groupingBy { it.amount.currencyCode }.eachCount()
                    val compCounts = compTxs.groupingBy { it.amount.currencyCode }.eachCount()
                    val bestPrimary = primaryCounts.maxByOrNull { it.value }?.key
                    val bestComp = compCounts.maxByOrNull { it.value }?.key

                    when {
                        bestPrimary != null && allAvailableCurrencies.contains(bestPrimary) -> bestPrimary
                        bestComp != null && allAvailableCurrencies.contains(bestComp) -> bestComp
                        else -> activeAccountCurrencies.firstOrNull() ?: allAvailableCurrencies.first()
                    }
                }
            }

            val currencySummaries = allAvailableCurrencies.associateWith { curr ->
                val primaryForCurr = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                    currency = curr,
                    periodOption = periodOption,
                    range = ranges.primaryRange,
                    transactions = primaryTxs,
                    accounts = accounts,
                    categories = categories,
                    allAvailableCurrencies = allAvailableCurrencies,
                    zoneId = zoneId
                )

                val compForCurr = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                    currency = curr,
                    periodOption = periodOption,
                    range = ranges.comparisonRange,
                    transactions = compTxs,
                    accounts = accounts,
                    categories = categories,
                    allAvailableCurrencies = allAvailableCurrencies,
                    zoneId = zoneId
                )

                buildCurrencyComparisonSummary(
                    currency = curr,
                    primary = primaryForCurr,
                    comparison = compForCurr
                )
            }

            val selectedSummary = currencySummaries[effectiveCurrency]
                ?: buildCurrencyComparisonSummary(
                    currency = effectiveCurrency,
                    primary = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                        currency = effectiveCurrency,
                        periodOption = periodOption,
                        range = ranges.primaryRange,
                        transactions = primaryTxs,
                        accounts = accounts,
                        categories = categories,
                        allAvailableCurrencies = allAvailableCurrencies,
                        zoneId = zoneId
                    ),
                    comparison = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                        currency = effectiveCurrency,
                        periodOption = periodOption,
                        range = ranges.comparisonRange,
                        transactions = compTxs,
                        accounts = accounts,
                        categories = categories,
                        allAvailableCurrencies = allAvailableCurrencies,
                        zoneId = zoneId
                    )
                )

            val primaryPeriodStats = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                currency = effectiveCurrency,
                periodOption = periodOption,
                range = ranges.primaryRange,
                transactions = primaryTxs,
                accounts = accounts,
                categories = categories,
                allAvailableCurrencies = allAvailableCurrencies,
                zoneId = zoneId
            )

            val compPeriodStats = observeStatisticsUseCase.calculatePeriodStatisticsForCurrency(
                currency = effectiveCurrency,
                periodOption = periodOption,
                range = ranges.comparisonRange,
                transactions = compTxs,
                accounts = accounts,
                categories = categories,
                allAvailableCurrencies = allAvailableCurrencies,
                zoneId = zoneId
            )

            StatisticsComparison(
                primary = primaryPeriodStats,
                comparison = compPeriodStats,
                primaryRange = ranges.primaryRange,
                comparisonRange = ranges.comparisonRange,
                primaryDateRange = ranges.primaryDateRange,
                comparisonDateRange = ranges.comparisonDateRange,
                mode = mode,
                availableCurrencies = allAvailableCurrencies,
                selectedCurrencyCode = effectiveCurrency,
                currencySummaries = currencySummaries,
                incomeMetric = selectedSummary.incomeMetric,
                expenseMetric = selectedSummary.expenseMetric,
                netMetric = selectedSummary.netMetric,
                categoryBreakdown = selectedSummary.categoryBreakdown,
                alignedTrendPoints = selectedSummary.alignedTrendPoints
            )
        }
    }

    private fun buildCurrencyComparisonSummary(
        currency: String,
        primary: PeriodStatistics,
        comparison: PeriodStatistics
    ): CurrencyComparisonSummary {
        val incomeMetric = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = primary.currencySummary.income,
            comparisonAmount = comparison.currencySummary.income,
            metricType = MetricType.INCOME
        )

        val expenseMetric = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = primary.currencySummary.expense,
            comparisonAmount = comparison.currencySummary.expense,
            metricType = MetricType.EXPENSE
        )

        val netMetric = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = primary.currencySummary.net,
            comparisonAmount = comparison.currencySummary.net,
            metricType = MetricType.NET
        )

        val primaryCats = primary.expenseCategories.associateBy { it.categoryId }
        val compCats = comparison.expenseCategories.associateBy { it.categoryId }
        val allCategoryIds = (primaryCats.keys + compCats.keys)

        val categoryMetrics = allCategoryIds.map { catId ->
            val pCat = primaryCats[catId]
            val cCat = compCats[catId]

            val catName = pCat?.categoryName ?: cCat?.categoryName ?: "Uncategorized"
            val iconName = pCat?.iconName ?: cCat?.iconName
            val colorHex = pCat?.colorHex ?: cCat?.colorHex

            val pAmount = pCat?.amount ?: Money(0L, currency)
            val cAmount = cCat?.amount ?: Money(0L, currency)

            val metric = ComparisonCalculationUtils.calculateMetric(
                primaryAmount = pAmount,
                comparisonAmount = cAmount,
                metricType = MetricType.EXPENSE
            )

            CategoryComparisonMetric(
                categoryId = catId,
                categoryName = catName,
                iconName = iconName,
                colorHex = colorHex,
                currencyCode = currency,
                primaryAmount = pAmount,
                comparisonAmount = cAmount,
                absoluteDelta = metric.absoluteDelta,
                relativeChange = metric.relativeChange,
                semantic = metric.semantic
            )
        }.sortedWith(
            Comparator { a, b ->
                val aPrimary = a.primaryAmount.amountInMinorUnits
                val bPrimary = b.primaryAmount.amountInMinorUnits
                if (aPrimary > 0L || bPrimary > 0L) {
                    bPrimary.compareTo(aPrimary)
                } else {
                    val compCompare = b.comparisonAmount.amountInMinorUnits.compareTo(a.comparisonAmount.amountInMinorUnits)
                    if (compCompare != 0) compCompare else a.categoryName.compareTo(b.categoryName)
                }
            }
        )

        val maxTrendBuckets = maxOf(primary.trendBuckets.size, comparison.trendBuckets.size)
        val alignedTrendPoints = (0 until maxTrendBuckets).map { index ->
            val pBucket = primary.trendBuckets.getOrNull(index)
            val cBucket = comparison.trendBuckets.getOrNull(index)

            val label = pBucket?.bucketLabel ?: cBucket?.bucketLabel ?: "P${index + 1}"
            val pIncome = pBucket?.income ?: Money(0L, currency)
            val pExpense = pBucket?.expense ?: Money(0L, currency)
            val cIncome = cBucket?.income ?: Money(0L, currency)
            val cExpense = cBucket?.expense ?: Money(0L, currency)

            AlignedTrendPoint(
                index = index,
                bucketLabel = label,
                primaryIncome = pIncome,
                primaryExpense = pExpense,
                comparisonIncome = cIncome,
                comparisonExpense = cExpense
            )
        }

        return CurrencyComparisonSummary(
            currencyCode = currency,
            primarySummary = primary.currencySummary,
            comparisonSummary = comparison.currencySummary,
            incomeMetric = incomeMetric,
            expenseMetric = expenseMetric,
            netMetric = netMetric,
            categoryBreakdown = categoryMetrics,
            alignedTrendPoints = alignedTrendPoints
        )
    }
}
