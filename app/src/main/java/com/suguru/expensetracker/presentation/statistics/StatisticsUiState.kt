package com.suguru.expensetracker.presentation.statistics

import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption

import com.suguru.expensetracker.domain.model.ProEntitlement
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
    val isArchived: Boolean
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
    val proEntitlement: ProEntitlement = ProEntitlement.Checking,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val isCustomRangePickerVisible: Boolean = false,
    val customRangeError: String? = null,
    val proRequiredMessage: String? = null,
    val showProPaywall: Boolean = false,
    val showPendingMessage: Boolean = false
)
