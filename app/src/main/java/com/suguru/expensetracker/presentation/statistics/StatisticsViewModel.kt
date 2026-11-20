package com.suguru.expensetracker.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.suguru.expensetracker.R
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.statistics.AlignedTrendPoint
import com.suguru.expensetracker.domain.model.statistics.CategoryComparisonMetric
import com.suguru.expensetracker.domain.model.statistics.ComparisonMetric
import com.suguru.expensetracker.domain.model.statistics.MetricType
import com.suguru.expensetracker.domain.model.statistics.PeriodStatistics
import com.suguru.expensetracker.domain.model.statistics.RelativeChange
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparison
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsComparisonUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.util.UiMessage
import com.suguru.expensetracker.presentation.util.uiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val observeStatisticsUseCase: ObserveStatisticsUseCase,
    private val observeStatisticsComparisonUseCase: ObserveStatisticsComparisonUseCase = ObserveStatisticsComparisonUseCase(observeStatisticsUseCase),
    private val savedStateHandle: SavedStateHandle? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clockNow: () -> Instant = { Instant.now() }
) : ViewModel() {

    companion object {
        const val KEY_COMPARISON_ENABLED = "key_comparison_enabled"
        const val KEY_COMPARISON_MODE = "key_comparison_mode"

    }

    private val initialComparisonEnabled: Boolean = savedStateHandle?.get<Boolean>(KEY_COMPARISON_ENABLED) ?: false
    private val initialComparisonMode: StatisticsComparisonMode = savedStateHandle?.get<String>(KEY_COMPARISON_MODE)?.let {
        runCatching { StatisticsComparisonMode.valueOf(it) }.getOrNull()
    } ?: StatisticsComparisonMode.PREVIOUS_PERIOD

    private val _selectedPeriod = MutableStateFlow(StatisticsPeriodOption.THIS_MONTH)
    private val _selectedCurrency = MutableStateFlow<String?>(null)

    private val _customStartDate = MutableStateFlow<LocalDate?>(null)
    private val _customEndDate = MutableStateFlow<LocalDate?>(null)
    private val _isCustomRangePickerVisible = MutableStateFlow(false)
    private val _customRangeError = MutableStateFlow<UiMessage?>(null)

    // Advanced Compare state
    private val _isComparisonEnabled = MutableStateFlow(initialComparisonEnabled)
    private val _comparisonMode = MutableStateFlow(initialComparisonMode)

    private data class SearchParams(
        val period: StatisticsPeriodOption,
        val currency: String?,
        val customStart: LocalDate?,
        val customEnd: LocalDate?,
        val comparisonEnabled: Boolean,
        val comparisonMode: StatisticsComparisonMode
    )

    val uiState: StateFlow<StatisticsUiState> = combine(
        combine(_selectedPeriod, _selectedCurrency, _customStartDate) { period, currency, start ->
            Triple(period, currency, start)
        },
        combine(_customEndDate, _isComparisonEnabled, _comparisonMode) { end, compEnabled, compMode ->
            Triple(end, compEnabled, compMode)
        }
    ) { (period, currency, customStart), (customEnd, compEnabled, compMode) ->
        SearchParams(period, currency, customStart, customEnd, compEnabled, compMode)
    }.flatMapLatest { params ->
        if (!params.comparisonEnabled) {
            observeStatisticsUseCase(
                periodOption = params.period,
                selectedCurrencyCode = params.currency,
                zoneId = zoneId,
                now = clockNow(),
                customStart = params.customStart,
                customEnd = params.customEnd
            ).map { periodStats ->
                mapToUiState(
                    stats = periodStats,
                    comparisonEnabled = false,
                    comparisonMode = params.comparisonMode,
                    comparisonUiModel = null,
                    comparisonError = null
                )
            }
        } else {
            observeStatisticsComparisonUseCase(
                periodOption = params.period,
                mode = params.comparisonMode,
                selectedCurrencyCode = params.currency,
                zoneId = zoneId,
                now = clockNow(),
                customStart = params.customStart,
                customEnd = params.customEnd
            ).map { comp ->
                val compUiModel = mapToComparisonUiModel(comp)
                mapToUiState(
                    stats = comp.primary,
                    comparisonEnabled = true,
                    comparisonMode = params.comparisonMode,
                    comparisonUiModel = compUiModel,
                    comparisonError = null,
                    extraAvailableCurrencies = comp.availableCurrencies
                )
            }.catch { _ ->
                emitAll(
                    observeStatisticsUseCase(
                        periodOption = params.period,
                        selectedCurrencyCode = params.currency,
                        zoneId = zoneId,
                        now = clockNow(),
                        customStart = params.customStart,
                        customEnd = params.customEnd
                    ).map { periodStats ->
                        mapToUiState(
                            stats = periodStats,
                            comparisonEnabled = true,
                            comparisonMode = params.comparisonMode,
                            comparisonUiModel = null,
                            comparisonError = "Couldn't load comparison"
                        )
                    }
                )
            }
        }
    }.combine(
        combine(
            _customStartDate,
            _customEndDate,
            _isCustomRangePickerVisible,
            _customRangeError
        ) { start, end, visible, error ->
            Quad(start, end, visible, error)
        }
    ) { state, quad ->
        state.copy(
            customStartDate = quad.first,
            customEndDate = quad.second,
            isCustomRangePickerVisible = quad.third,
            customRangeError = quad.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun onPeriodSelected(period: StatisticsPeriodOption) {
        if (period == StatisticsPeriodOption.CUSTOM) {
            _isCustomRangePickerVisible.value = true
        } else {
            _selectedPeriod.value = period
        }
    }

    fun onCurrencySelected(currencyCode: String) {
        _selectedCurrency.value = currencyCode
    }

    fun onToggleComparison() {
        val next = !_isComparisonEnabled.value
        _isComparisonEnabled.value = next
        savedStateHandle?.set(KEY_COMPARISON_ENABLED, next)
    }

    fun onComparisonModeSelected(mode: StatisticsComparisonMode) {
        _comparisonMode.value = mode
        savedStateHandle?.set(KEY_COMPARISON_MODE, mode.name)
    }

    fun onCustomDatesSelected(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) {
            _customRangeError.value = UiMessage(R.string.error_budget_date_range_invalid)
            return
        }
        _customRangeError.value = null
        _customStartDate.value = start
        _customEndDate.value = end
        _isCustomRangePickerVisible.value = false
        _selectedPeriod.value = StatisticsPeriodOption.CUSTOM
    }

    fun dismissCustomRangePicker() {
        _isCustomRangePickerVisible.value = false
        _customRangeError.value = null
    }

    private fun mapToUiState(
        stats: PeriodStatistics,
        comparisonEnabled: Boolean,
        comparisonMode: StatisticsComparisonMode,
        comparisonUiModel: ComparisonUiModel?,
        comparisonError: String?,
        extraAvailableCurrencies: List<String> = emptyList()
    ): StatisticsUiState {
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
                isArchived = cat.isArchived,
                isSystem = cat.isSystem
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
                isArchived = cat.isArchived,
                isSystem = cat.isSystem
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

        val allCurrencies = (stats.availableCurrencies + extraAvailableCurrencies).distinct().sorted()

        return StatisticsUiState(
            isLoading = false,
            selectedPeriod = stats.periodOption,
            selectedCurrencyCode = stats.selectedCurrencyCode,
            availableCurrencies = allCurrencies,
            summary = summaryModel,
            expenseCategories = expenseCategoryUiList,
            incomeCategories = incomeCategoryUiList,
            accountBreakdown = accountUiList,
            trendPoints = trendUiList,
            totalTransactionsCount = stats.totalTransactionsInPeriod,
            hasAnyTransactionsInPeriod = stats.totalTransactionsInPeriod > 0,
            errorMessage = null,
            isComparisonEnabled = comparisonEnabled,
            comparisonMode = comparisonMode,
            isComparisonLoading = false,
            comparison = comparisonUiModel,
            comparisonError = comparisonError
        )
    }

    private fun mapToComparisonUiModel(comp: StatisticsComparison): ComparisonUiModel {
        val primaryLabel = formatDateRange(comp.primaryDateRange.startInclusive, comp.primaryDateRange.endInclusive)
        val comparisonLabel = formatDateRange(comp.comparisonDateRange.startInclusive, comp.comparisonDateRange.endInclusive)

        val incomeMetricUi = formatComparisonMetric(comp.incomeMetric, MetricType.INCOME)
        val expenseMetricUi = formatComparisonMetric(comp.expenseMetric, MetricType.EXPENSE)
        val netMetricUi = formatComparisonMetric(comp.netMetric, MetricType.NET)

        val categoryListUi = comp.categoryBreakdown.map { formatCategoryComparison(it) }

        val trendListUi = comp.alignedTrendPoints.map { pt ->
            AlignedTrendPointUiModel(
                label = pt.bucketLabel,
                primaryIncomeMinor = pt.primaryIncome.amountInMinorUnits,
                primaryExpenseMinor = pt.primaryExpense.amountInMinorUnits,
                comparisonIncomeMinor = pt.comparisonIncome.amountInMinorUnits,
                comparisonExpenseMinor = pt.comparisonExpense.amountInMinorUnits,
                formattedPrimaryIncome = MoneyParser.format(pt.primaryIncome, includeSymbol = true),
                formattedPrimaryExpense = MoneyParser.format(pt.primaryExpense, includeSymbol = true),
                formattedComparisonIncome = MoneyParser.format(pt.comparisonIncome, includeSymbol = true),
                formattedComparisonExpense = MoneyParser.format(pt.comparisonExpense, includeSymbol = true)
            )
        }

        val isPrimaryEmpty = comp.primary.totalTransactionsInPeriod == 0
        val isComparisonEmpty = comp.comparison.totalTransactionsInPeriod == 0
        val isBothEmpty = isPrimaryEmpty && isComparisonEmpty

        return ComparisonUiModel(
            mode = comp.mode,
            primaryDateRangeLabel = primaryLabel,
            comparisonDateRangeLabel = comparisonLabel,
            incomeMetric = incomeMetricUi,
            expenseMetric = expenseMetricUi,
            netMetric = netMetricUi,
            categoryBreakdown = categoryListUi,
            alignedTrendPoints = trendListUi,
            isPrimaryEmpty = isPrimaryEmpty,
            isComparisonEmpty = isComparisonEmpty,
            isBothEmpty = isBothEmpty
        )
    }

    private fun formatDateRange(start: LocalDate, end: LocalDate): String {
        val locale = java.util.Locale.getDefault()
        val fullDateFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
        val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        return if (start.year == end.year) {
            "${start.format(shortDateFormatter)} – ${end.format(shortDateFormatter)}, ${start.year}"
        } else {
            "${start.format(fullDateFormatter)} – ${end.format(fullDateFormatter)}"
        }
    }

    private fun relativeChangeMessage(change: RelativeChange): UiMessage? = when (change) {
        is RelativeChange.Defined -> {
            val bp = change.basisPoints
            val sign = if (bp > 0L) "+" else ""
            val whole = bp / 100L
            val remainder = abs(bp % 100L) / 10L
            val value = if (remainder == 0L) "$sign$whole" else "$sign$whole.$remainder"
            uiMessage(R.string.statistics_relative_percentage, value)
        }
        is RelativeChange.FromZero -> UiMessage(R.string.statistics_relative_new)
        is RelativeChange.NoChangeFromZero -> UiMessage(R.string.statistics_relative_no_change)
        is RelativeChange.Suppressed -> null
    }

    private fun comparisonChangeMessage(
        deltaMinorUnits: Long,
        currencyCode: String,
        formattedDelta: String
    ): UiMessage = when {
        deltaMinorUnits == 0L -> UiMessage(R.string.statistics_change_no_change)
        deltaMinorUnits > 0L -> uiMessage(R.string.statistics_change_increased, formattedDelta)
        else -> uiMessage(
            R.string.statistics_change_decreased,
            MoneyParser.format(Money(abs(deltaMinorUnits), currencyCode), includeSymbol = true)
        )
    }

    private fun formatComparisonMetric(
        metric: ComparisonMetric,
        metricType: MetricType
    ): ComparisonMetricUiModel {
        val formattedPrimary = MoneyParser.format(metric.primaryAmount, includeSymbol = true, useGrouping = true)
        val formattedComp = MoneyParser.format(metric.comparisonAmount, includeSymbol = true, useGrouping = true)
        val formattedDelta = MoneyParser.format(metric.absoluteDelta, includeSymbol = true, useGrouping = true, showExplicitSign = true)

        val relativeText = relativeChangeMessage(metric.relativeChange)
        val metricLabel = when (metricType) {
            MetricType.INCOME -> UiMessage(R.string.type_income)
            MetricType.EXPENSE -> UiMessage(R.string.type_expense)
            MetricType.NET -> UiMessage(R.string.home_net)
        }
        val changeDescription = comparisonChangeMessage(
            deltaMinorUnits = metric.absoluteDelta.amountInMinorUnits,
            currencyCode = metric.absoluteDelta.currencyCode,
            formattedDelta = formattedDelta
        )
        val contentDescription = UiMessage(
            resourceId = if (relativeText == null) {
                R.string.statistics_metric_accessibility_no_relative
            } else {
                R.string.statistics_metric_accessibility
            },
            arguments = buildList {
                add(metricLabel)
                add(formattedPrimary)
                add(formattedComp)
                add(changeDescription)
                relativeText?.let(::add)
            }
        )

        return ComparisonMetricUiModel(
            primaryAmount = formattedPrimary,
            comparisonAmount = formattedComp,
            formattedDelta = formattedDelta,
            relativeChangeText = relativeText,
            semantic = metric.semantic,
            contentDescription = contentDescription
        )
    }

    private fun formatCategoryComparison(
        cat: CategoryComparisonMetric
    ): CategoryComparisonUiModel {
        val formattedPrimary = MoneyParser.format(cat.primaryAmount, includeSymbol = true, useGrouping = true)
        val formattedComp = MoneyParser.format(cat.comparisonAmount, includeSymbol = true, useGrouping = true)
        val formattedDelta = MoneyParser.format(cat.absoluteDelta, includeSymbol = true, useGrouping = true, showExplicitSign = true)

        val relativeText = relativeChangeMessage(cat.relativeChange)
        val changeDescription = comparisonChangeMessage(
            deltaMinorUnits = cat.absoluteDelta.amountInMinorUnits,
            currencyCode = cat.absoluteDelta.currencyCode,
            formattedDelta = formattedDelta
        )
        val contentDesc = UiMessage(
            R.string.statistics_category_accessibility,
            listOf(
                if (cat.categoryId == null) {
                    UiMessage(R.string.pdf_empty_category)
                } else {
                    com.suguru.expensetracker.presentation.util.builtInCategoryLabelResource(cat.categoryName, cat.isSystem)
                        ?.let { UiMessage(it) } ?: cat.categoryName
                },
                formattedPrimary,
                formattedComp,
                changeDescription
            )
        )

        return CategoryComparisonUiModel(
            categoryId = cat.categoryId,
            categoryName = cat.categoryName,
            iconName = cat.iconName,
            colorHex = cat.colorHex,
            formattedPrimaryAmount = formattedPrimary,
            formattedComparisonAmount = formattedComp,
            formattedDelta = formattedDelta,
            relativeChangeText = relativeText,
            semantic = cat.semantic,
            contentDescription = contentDesc,
            isSystem = cat.isSystem
        )
    }
}
