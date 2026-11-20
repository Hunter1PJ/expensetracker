package com.suguru.expensetracker.presentation.statistics

import com.suguru.expensetracker.domain.model.statistics.ComparisonSemantic
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.presentation.util.UiMessage
import java.time.LocalDate

data class SummaryCardUiModel(
    val formattedIncome: String,
    val formattedExpense: String,
    val formattedNet: String,
    val isNetPositive: Boolean?
)

data class CategoryBreakdownUiModel(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String?,
    val colorHex: String?,
    val formattedAmount: String,
    val percentage: Float,
    val percentageText: String,
    val transactionCount: Int,
    val isArchived: Boolean,
    val isSystem: Boolean = false
)

data class AccountAnalyticsUiModel(
    val accountId: Long,
    val accountName: String,
    val formattedIncome: String,
    val formattedExpense: String,
    val isArchived: Boolean
)

data class TrendPointUiModel(
    val label: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val formattedIncome: String,
    val formattedExpense: String
)

data class ComparisonMetricUiModel(
    val primaryAmount: String,
    val comparisonAmount: String,
    val formattedDelta: String,
    val relativeChangeText: UiMessage?,
    val semantic: ComparisonSemantic,
    val contentDescription: UiMessage
)

data class CategoryComparisonUiModel(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String?,
    val colorHex: String?,
    val formattedPrimaryAmount: String,
    val formattedComparisonAmount: String,
    val formattedDelta: String,
    val relativeChangeText: UiMessage?,
    val semantic: ComparisonSemantic,
    val contentDescription: UiMessage,
    val isSystem: Boolean = false
)

data class AlignedTrendPointUiModel(
    val label: String,
    val primaryIncomeMinor: Long,
    val primaryExpenseMinor: Long,
    val comparisonIncomeMinor: Long,
    val comparisonExpenseMinor: Long,
    val formattedPrimaryIncome: String,
    val formattedPrimaryExpense: String,
    val formattedComparisonIncome: String,
    val formattedComparisonExpense: String
)

data class ComparisonUiModel(
    val mode: StatisticsComparisonMode,
    val primaryDateRangeLabel: String,
    val comparisonDateRangeLabel: String,
    val incomeMetric: ComparisonMetricUiModel,
    val expenseMetric: ComparisonMetricUiModel,
    val netMetric: ComparisonMetricUiModel,
    val categoryBreakdown: List<CategoryComparisonUiModel>,
    val alignedTrendPoints: List<AlignedTrendPointUiModel>,
    val isPrimaryEmpty: Boolean,
    val isComparisonEmpty: Boolean,
    val isBothEmpty: Boolean
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: StatisticsPeriodOption = StatisticsPeriodOption.THIS_MONTH,
    val selectedCurrencyCode: String = "USD",
    val availableCurrencies: List<String> = emptyList(),
    val summary: SummaryCardUiModel? = null,
    val expenseCategories: List<CategoryBreakdownUiModel> = emptyList(),
    val incomeCategories: List<CategoryBreakdownUiModel> = emptyList(),
    val accountBreakdown: List<AccountAnalyticsUiModel> = emptyList(),
    val trendPoints: List<TrendPointUiModel> = emptyList(),
    val totalTransactionsCount: Int = 0,
    val hasAnyTransactionsInPeriod: Boolean = false,
    val errorMessage: String? = null,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val isCustomRangePickerVisible: Boolean = false,
    val customRangeError: UiMessage? = null,
    // Advanced Statistics Compare
    val isComparisonEnabled: Boolean = false,
    val comparisonMode: StatisticsComparisonMode = StatisticsComparisonMode.PREVIOUS_PERIOD,
    val isComparisonLoading: Boolean = false,
    val comparison: ComparisonUiModel? = null,
    val comparisonError: String? = null
)
