package com.suguru.expensetracker.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.statistics.AlignedTrendPoint
import com.suguru.expensetracker.domain.model.statistics.CategoryComparisonMetric
import com.suguru.expensetracker.domain.model.statistics.ComparisonMetric
import com.suguru.expensetracker.domain.model.statistics.MetricType
import com.suguru.expensetracker.domain.model.statistics.PeriodStatistics
import com.suguru.expensetracker.domain.model.statistics.RelativeChange
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparison
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.usecase.billing.ObserveProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsComparisonUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val observeStatisticsUseCase: ObserveStatisticsUseCase,
    private val observeStatisticsComparisonUseCase: ObserveStatisticsComparisonUseCase = ObserveStatisticsComparisonUseCase(observeStatisticsUseCase),
    private val observeProEntitlementUseCase: ObserveProEntitlementUseCase,
    private val savedStateHandle: SavedStateHandle? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clockNow: () -> Instant = { Instant.now() }
) : ViewModel() {

    companion object {
        const val KEY_COMPARISON_ENABLED = "key_comparison_enabled"
        const val KEY_COMPARISON_MODE = "key_comparison_mode"
        private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")
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
    private val _customRangeError = MutableStateFlow<String?>(null)

    // Advanced Compare state
    private val _isComparisonEnabled = MutableStateFlow(initialComparisonEnabled)
    private val _comparisonMode = MutableStateFlow(initialComparisonMode)
    private val _showCompareProPaywall = MutableStateFlow(false)
    private val _showComparePendingMessage = MutableStateFlow(false)

    val proEntitlement: StateFlow<ProEntitlement> = observeProEntitlementUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProEntitlement.Checking
        )

    private val _proRequiredMessage = MutableStateFlow<String?>(null)
    private val _showProPaywall = MutableStateFlow(false)
    private val _showPendingMessage = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            var lastEntitlement: ProEntitlement? = null
            proEntitlement.collect { entitlement ->
                if (lastEntitlement != null && lastEntitlement is ProEntitlement.Pro && entitlement is ProEntitlement.Free) {
                    if (_selectedPeriod.value == StatisticsPeriodOption.CUSTOM) {
                        _selectedPeriod.value = StatisticsPeriodOption.THIS_MONTH
                        _proRequiredMessage.value = "Custom Date Range requires Pro."
                    }
                    if (_isComparisonEnabled.value) {
                        _isComparisonEnabled.value = false
                        savedStateHandle?.set(KEY_COMPARISON_ENABLED, false)
                        _proRequiredMessage.value = "Advanced Compare requires Pro"
                    }
                } else if (lastEntitlement != null && lastEntitlement is ProEntitlement.Free && entitlement is ProEntitlement.Pro) {
                    // Reactive unlock after purchase
                    if (_showCompareProPaywall.value) {
                        _showCompareProPaywall.value = false
                        _isComparisonEnabled.value = true
                        savedStateHandle?.set(KEY_COMPARISON_ENABLED, true)
                    }
                }
                lastEntitlement = entitlement
            }
        }
    }

    private data class SearchParams(
        val period: StatisticsPeriodOption,
        val currency: String?,
        val customStart: LocalDate?,
        val customEnd: LocalDate?,
        val comparisonEnabled: Boolean,
        val comparisonMode: StatisticsComparisonMode
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
            proEntitlement,
            _customStartDate,
            _customEndDate,
            _isCustomRangePickerVisible
        ) { pro, start, end, visible ->
            Quad(pro, start, end, visible)
        }
    ) { state, quad ->
        state.copy(
            proEntitlement = quad.first,
            customStartDate = quad.second,
            customEndDate = quad.third,
            isCustomRangePickerVisible = quad.fourth
        )
    }.combine(
        combine(
            _customRangeError,
            _proRequiredMessage,
            _showProPaywall,
            _showPendingMessage
        ) { error, msg, paywall, pending ->
            Quad(error, msg, paywall, pending)
        }
    ) { state, quad ->
        state.copy(
            customRangeError = quad.first,
            proRequiredMessage = quad.second,
            showProPaywall = quad.third,
            showPendingMessage = quad.fourth
        )
    }.combine(
        combine(
            _showCompareProPaywall,
            _showComparePendingMessage
        ) { comparePaywall, comparePending ->
            comparePaywall to comparePending
        }
    ) { state, pair ->
        state.copy(
            showCompareProPaywall = pair.first,
            showComparePendingMessage = pair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun onPeriodSelected(period: StatisticsPeriodOption) {
        if (period == StatisticsPeriodOption.CUSTOM) {
            val entitlement = proEntitlement.value
            when (entitlement) {
                is ProEntitlement.Pro -> {
                    _isCustomRangePickerVisible.value = true
                }
                is ProEntitlement.Pending -> {
                    _showPendingMessage.value = true
                }
                else -> {
                    _showProPaywall.value = true
                }
            }
        } else {
            _selectedPeriod.value = period
            _proRequiredMessage.value = null
        }
    }

    fun onCurrencySelected(currencyCode: String) {
        _selectedCurrency.value = currencyCode
    }

    fun onToggleComparison() {
        if (!_isComparisonEnabled.value) {
            when (proEntitlement.value) {
                is ProEntitlement.Pro -> {
                    _isComparisonEnabled.value = true
                    savedStateHandle?.set(KEY_COMPARISON_ENABLED, true)
                }
                is ProEntitlement.Pending -> {
                    _showComparePendingMessage.value = true
                }
                else -> {
                    _showCompareProPaywall.value = true
                }
            }
        } else {
            _isComparisonEnabled.value = false
            savedStateHandle?.set(KEY_COMPARISON_ENABLED, false)
        }
    }

    fun onComparisonModeSelected(mode: StatisticsComparisonMode) {
        _comparisonMode.value = mode
        savedStateHandle?.set(KEY_COMPARISON_MODE, mode.name)
    }

    fun dismissCompareProPaywall() {
        _showCompareProPaywall.value = false
    }

    fun dismissComparePendingMessage() {
        _showComparePendingMessage.value = false
    }

    fun onCustomDatesSelected(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) {
            _customRangeError.value = "Start date must be on or before end date."
            return
        }
        _customRangeError.value = null
        _customStartDate.value = start
        _customEndDate.value = end
        _isCustomRangePickerVisible.value = false
        _selectedPeriod.value = StatisticsPeriodOption.CUSTOM
        _proRequiredMessage.value = null
    }

    fun dismissCustomRangePicker() {
        _isCustomRangePickerVisible.value = false
        _customRangeError.value = null
    }

    fun dismissProPaywall() {
        _showProPaywall.value = false
    }

    fun dismissPendingMessage() {
        _showPendingMessage.value = false
    }

    fun dismissProRequiredMessage() {
        _proRequiredMessage.value = null
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
        val rangeSubtitle = "$primaryLabel vs $comparisonLabel"

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
            rangeHeaderSubtitle = rangeSubtitle,
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
        return if (start.year == end.year) {
            "${start.format(shortDateFormatter)} – ${end.format(shortDateFormatter)}, ${start.year}"
        } else {
            "${start.format(dateFormatter)} – ${end.format(dateFormatter)}"
        }
    }

    private fun formatComparisonMetric(
        metric: ComparisonMetric,
        metricType: MetricType
    ): ComparisonMetricUiModel {
        val formattedPrimary = MoneyParser.format(metric.primaryAmount, includeSymbol = true, useGrouping = true)
        val formattedComp = MoneyParser.format(metric.comparisonAmount, includeSymbol = true, useGrouping = true)
        val formattedDelta = MoneyParser.format(metric.absoluteDelta, includeSymbol = true, useGrouping = true, showExplicitSign = true)

        val relativeText = when (val rel = metric.relativeChange) {
            is RelativeChange.Defined -> {
                val bp = rel.basisPoints
                val sign = if (bp > 0L) "+" else ""
                val whole = bp / 100L
                val remainder = abs(bp % 100L) / 10L
                if (remainder == 0L) "$sign$whole%" else "$sign$whole.$remainder%"
            }
            is RelativeChange.FromZero -> "New"
            is RelativeChange.NoChangeFromZero -> "No change"
            is RelativeChange.Suppressed -> ""
        }

        val typeName = when (metricType) {
            MetricType.INCOME -> "Income"
            MetricType.EXPENSE -> "Expense"
            MetricType.NET -> "Net"
        }

        val changeDescription = when {
            metric.absoluteDelta.amountInMinorUnits == 0L -> "No change"
            metric.absoluteDelta.amountInMinorUnits > 0L -> "Increased by $formattedDelta"
            else -> "Decreased by ${MoneyParser.format(Money(abs(metric.absoluteDelta.amountInMinorUnits), metric.absoluteDelta.currencyCode), includeSymbol = true)}"
        }

        val contentDescription = if (relativeText.isNotBlank()) {
            "$typeName: Current $formattedPrimary, Previous $formattedComp. $changeDescription ($relativeText)."
        } else {
            "$typeName: Current $formattedPrimary, Previous $formattedComp. $changeDescription."
        }

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

        val relativeText = when (val rel = cat.relativeChange) {
            is RelativeChange.Defined -> {
                val bp = rel.basisPoints
                val sign = if (bp > 0L) "+" else ""
                val whole = bp / 100L
                val remainder = abs(bp % 100L) / 10L
                if (remainder == 0L) "$sign$whole%" else "$sign$whole.$remainder%"
            }
            is RelativeChange.FromZero -> "New"
            is RelativeChange.NoChangeFromZero -> "No change"
            is RelativeChange.Suppressed -> ""
        }

        val changeDesc = when {
            cat.absoluteDelta.amountInMinorUnits == 0L -> "No change"
            cat.absoluteDelta.amountInMinorUnits > 0L -> "Increased by $formattedDelta"
            else -> "Decreased by ${MoneyParser.format(Money(abs(cat.absoluteDelta.amountInMinorUnits), cat.absoluteDelta.currencyCode), includeSymbol = true)}"
        }

        val contentDesc = "${cat.categoryName}: Current $formattedPrimary, Previous $formattedComp. $changeDesc."

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
            contentDescription = contentDesc
        )
    }
}
