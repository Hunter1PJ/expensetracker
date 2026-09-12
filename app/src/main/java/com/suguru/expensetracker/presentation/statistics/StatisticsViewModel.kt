package com.suguru.expensetracker.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.statistics.PeriodStatistics
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val observeStatisticsUseCase: ObserveStatisticsUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clockNow: () -> Instant = { Instant.now() }
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(StatisticsPeriodOption.THIS_MONTH)
    private val _selectedCurrency = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StatisticsUiState> = combine(
        _selectedPeriod,
        _selectedCurrency
    ) { period, currency ->
        period to currency
    }.flatMapLatest { (period, currency) ->
        observeStatisticsUseCase(
            periodOption = period,
            selectedCurrencyCode = currency,
            zoneId = zoneId,
            now = clockNow()
        ).map { periodStats ->
            mapToUiState(periodStats)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun onPeriodSelected(period: StatisticsPeriodOption) {
        _selectedPeriod.value = period
    }

    fun onCurrencySelected(currencyCode: String) {
        _selectedCurrency.value = currencyCode
    }

    private fun mapToUiState(stats: PeriodStatistics): StatisticsUiState {
        val summaryModel = SummaryCardUiModel(
            formattedIncome = MoneyParser.format(stats.currencySummary.income, includeSymbol = true, useGrouping = true),
            formattedExpense = MoneyParser.format(stats.currencySummary.expense, includeSymbol = true, useGrouping = true),
            formattedNet = MoneyParser.format(stats.currencySummary.net, includeSymbol = true, useGrouping = true, showExplicitSign = true),
            isNetPositive = when {
                stats.currencySummary.net.amountInMinorUnits > 0L -> true
                stats.currencySummary.net.amountInMinorUnits < 0L -> false
                else -> null
            }
        )

        val totalExpenseMinor = stats.currencySummary.expense.amountInMinorUnits
        val expenseCategoryUiList = stats.expenseCategories.map { cat ->
            val ratio = if (totalExpenseMinor > 0L) {
                cat.amount.amountInMinorUnits.toFloat() / totalExpenseMinor.toFloat()
            } else {
                0.0f
            }
            val percentageInt = (ratio * 100).toInt()
            CategoryBreakdownUiModel(
                categoryId = cat.categoryId,
                categoryName = cat.categoryName,
                iconName = cat.iconName,
                colorHex = cat.colorHex,
                formattedAmount = MoneyParser.format(cat.amount, includeSymbol = true, useGrouping = true),
                percentage = ratio,
                percentageText = "$percentageInt%",
                transactionCount = cat.transactionCount,
                isArchived = cat.isArchived
            )
        }

        val totalIncomeMinor = stats.currencySummary.income.amountInMinorUnits
        val incomeCategoryUiList = stats.incomeCategories.map { cat ->
            val ratio = if (totalIncomeMinor > 0L) {
                cat.amount.amountInMinorUnits.toFloat() / totalIncomeMinor.toFloat()
            } else {
                0.0f
            }
            val percentageInt = (ratio * 100).toInt()
            CategoryBreakdownUiModel(
                categoryId = cat.categoryId,
                categoryName = cat.categoryName,
                iconName = cat.iconName,
                colorHex = cat.colorHex,
                formattedAmount = MoneyParser.format(cat.amount, includeSymbol = true, useGrouping = true),
                percentage = ratio,
                percentageText = "$percentageInt%",
                transactionCount = cat.transactionCount,
                isArchived = cat.isArchived
            )
        }

        val accountUiList = stats.accountBreakdown.map { acc ->
            AccountAnalyticsUiModel(
                accountId = acc.accountId,
                accountName = acc.accountName,
                formattedIncome = MoneyParser.format(acc.income, includeSymbol = true, useGrouping = true),
                formattedExpense = MoneyParser.format(acc.expense, includeSymbol = true, useGrouping = true),
                isArchived = acc.isArchived
            )
        }

        val trendUiList = stats.trendBuckets.map { bucket ->
            TrendPointUiModel(
                label = bucket.bucketLabel,
                incomeMinor = bucket.income.amountInMinorUnits,
                expenseMinor = bucket.expense.amountInMinorUnits,
                formattedIncome = MoneyParser.format(bucket.income, includeSymbol = true, useGrouping = true),
                formattedExpense = MoneyParser.format(bucket.expense, includeSymbol = true, useGrouping = true)
            )
        }

        return StatisticsUiState(
            isLoading = false,
            selectedPeriod = stats.periodOption,
            selectedCurrencyCode = stats.selectedCurrencyCode,
            availableCurrencies = stats.availableCurrencies,
            summary = summaryModel,
            expenseCategories = expenseCategoryUiList,
            incomeCategories = incomeCategoryUiList,
            accountBreakdown = accountUiList,
            trendPoints = trendUiList,
            totalTransactionsCount = stats.totalTransactionsInPeriod,
            hasAnyTransactionsInPeriod = stats.totalTransactionsInPeriod > 0,
            errorMessage = null
        )
    }
}
