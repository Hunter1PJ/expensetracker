package com.suguru.expensetracker.domain.model.statistics

import com.suguru.expensetracker.domain.model.Money
import java.math.BigInteger
import kotlin.math.abs

enum class StatisticsComparisonMode {
    PREVIOUS_PERIOD,
    PREVIOUS_YEAR
}

sealed interface RelativeChange {
    data class Defined(val basisPoints: Long) : RelativeChange
    object FromZero : RelativeChange
    object NoChangeFromZero : RelativeChange
    object Suppressed : RelativeChange
}

enum class ComparisonSemantic {
    FAVORABLE,
    UNFAVORABLE,
    NEUTRAL
}

enum class MetricType {
    INCOME,
    EXPENSE,
    NET
}

data class ComparisonMetric(
    val primaryAmount: Money,
    val comparisonAmount: Money,
    val absoluteDelta: Money,
    val relativeChange: RelativeChange,
    val semantic: ComparisonSemantic
)

data class CategoryComparisonMetric(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String?,
    val colorHex: String?,
    val currencyCode: String = primaryAmount.currencyCode,
    val primaryAmount: Money,
    val comparisonAmount: Money,
    val absoluteDelta: Money,
    val relativeChange: RelativeChange,
    val semantic: ComparisonSemantic
)

data class AlignedTrendPoint(
    val index: Int,
    val bucketLabel: String,
    val primaryIncome: Money,
    val primaryExpense: Money,
    val comparisonIncome: Money,
    val comparisonExpense: Money
)

data class CurrencyComparisonSummary(
    val currencyCode: String,
    val primarySummary: CurrencyAnalyticsSummary,
    val comparisonSummary: CurrencyAnalyticsSummary,
    val incomeMetric: ComparisonMetric,
    val expenseMetric: ComparisonMetric,
    val netMetric: ComparisonMetric,
    val categoryBreakdown: List<CategoryComparisonMetric>,
    val alignedTrendPoints: List<AlignedTrendPoint>
)

data class StatisticsComparison(
    val primary: PeriodStatistics,
    val comparison: PeriodStatistics,
    val primaryRange: StatisticsRange,
    val comparisonRange: StatisticsRange,
    val primaryDateRange: StatisticsDateRange,
    val comparisonDateRange: StatisticsDateRange,
    val mode: StatisticsComparisonMode,
    val availableCurrencies: List<String> = primary.availableCurrencies,
    val selectedCurrencyCode: String = primary.selectedCurrencyCode,
    val currencySummaries: Map<String, CurrencyComparisonSummary> = emptyMap(),
    val incomeMetric: ComparisonMetric,
    val expenseMetric: ComparisonMetric,
    val netMetric: ComparisonMetric,
    val categoryBreakdown: List<CategoryComparisonMetric>,
    val alignedTrendPoints: List<AlignedTrendPoint>
) {
    val isBothEmpty: Boolean
        get() {
            val currSummary = currencySummaries[selectedCurrencyCode]
            return if (currSummary != null) {
                currSummary.primarySummary.transactionCount == 0 && currSummary.comparisonSummary.transactionCount == 0
            } else {
                primary.totalTransactionsInPeriod == 0 && comparison.totalTransactionsInPeriod == 0
            }
        }

    val currencyCode: String
        get() = selectedCurrencyCode
}

object ComparisonCalculationUtils {

    fun classifySemantic(metricType: MetricType, deltaMinorUnits: Long): ComparisonSemantic {
        return when {
            deltaMinorUnits == 0L -> ComparisonSemantic.NEUTRAL
            metricType == MetricType.EXPENSE -> {
                if (deltaMinorUnits < 0L) ComparisonSemantic.FAVORABLE else ComparisonSemantic.UNFAVORABLE
            }
            else -> {
                if (deltaMinorUnits > 0L) ComparisonSemantic.FAVORABLE else ComparisonSemantic.UNFAVORABLE
            }
        }
    }

    fun calculateMetric(
        primaryAmount: Money,
        comparisonAmount: Money,
        metricType: MetricType
    ): ComparisonMetric {
        require(primaryAmount.currencyCode == comparisonAmount.currencyCode) {
            "Currency mismatch: ${primaryAmount.currencyCode} vs ${comparisonAmount.currencyCode}"
        }

        val primaryMinor = primaryAmount.amountInMinorUnits
        val comparisonMinor = comparisonAmount.amountInMinorUnits
        val pBig = BigInteger.valueOf(primaryMinor)
        val cBig = BigInteger.valueOf(comparisonMinor)
        val deltaBig = pBig.subtract(cBig)
        val deltaMinor = deltaBig.coerceIn(
            BigInteger.valueOf(Long.MIN_VALUE),
            BigInteger.valueOf(Long.MAX_VALUE)
        ).toLong()

        val absoluteDelta = Money(deltaMinor, primaryAmount.currencyCode)
        val semantic = classifySemantic(metricType, deltaMinor)

        val relativeChange: RelativeChange = when (metricType) {
            MetricType.NET -> {
                when {
                    (primaryMinor > 0L && comparisonMinor < 0L) || (primaryMinor < 0L && comparisonMinor > 0L) -> {
                        // Sign-crossing: crossing zero makes percentage changes misleading
                        RelativeChange.Suppressed
                    }
                    comparisonMinor == 0L -> {
                        if (primaryMinor == 0L) RelativeChange.NoChangeFromZero else RelativeChange.FromZero
                    }
                    else -> {
                        // Same sign (both positive OR both negative): mathematically defined
                        calculateBasisPoints(deltaMinor, comparisonMinor)
                    }
                }
            }
            MetricType.INCOME, MetricType.EXPENSE -> {
                when {
                    comparisonMinor == 0L -> {
                        if (primaryMinor == 0L) RelativeChange.NoChangeFromZero else RelativeChange.FromZero
                    }
                    else -> {
                        calculateBasisPoints(deltaMinor, comparisonMinor)
                    }
                }
            }
        }

        return ComparisonMetric(
            primaryAmount = primaryAmount,
            comparisonAmount = comparisonAmount,
            absoluteDelta = absoluteDelta,
            relativeChange = relativeChange,
            semantic = semantic
        )
    }

    fun calculateBasisPoints(deltaMinorUnits: Long, comparisonMinorUnits: Long): RelativeChange {
        if (comparisonMinorUnits == 0L) {
            return if (deltaMinorUnits == 0L) RelativeChange.NoChangeFromZero else RelativeChange.FromZero
        }
        val deltaBig = BigInteger.valueOf(deltaMinorUnits)
        val compAbsBig = BigInteger.valueOf(comparisonMinorUnits).abs()
        val basisPointsBig = deltaBig.multiply(BigInteger.valueOf(10_000L)).divide(compAbsBig)
        val clamped = basisPointsBig.coerceIn(
            BigInteger.valueOf(Long.MIN_VALUE),
            BigInteger.valueOf(Long.MAX_VALUE)
        ).toLong()
        return RelativeChange.Defined(clamped)
    }
}
