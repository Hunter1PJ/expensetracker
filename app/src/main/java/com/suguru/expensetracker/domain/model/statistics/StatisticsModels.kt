package com.suguru.expensetracker.domain.model.statistics

import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Money
import java.time.LocalDate

data class CurrencyAnalyticsSummary(
    val currencyCode: String,
    val income: Money,
    val expense: Money,
    val net: Money,
    val transactionCount: Int
)

data class CategoryAnalytics(
    val categoryId: Long?,
    val categoryName: String,
    val iconName: String?,
    val colorHex: String?,
    val amount: Money,
    val transactionCount: Int,
    val isArchived: Boolean
)

data class AccountAnalytics(
    val accountId: Long,
    val accountName: String,
    val accountType: AccountType,
    val currencyCode: String,
    val income: Money,
    val expense: Money,
    val isArchived: Boolean
)

data class TimeBucketAnalytics(
    val bucketLabel: String,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val income: Money,
    val expense: Money
)

data class PeriodStatistics(
    val periodOption: StatisticsPeriodOption,
    val range: StatisticsRange,
    val availableCurrencies: List<String>,
    val selectedCurrencyCode: String,
    val currencySummary: CurrencyAnalyticsSummary,
    val expenseCategories: List<CategoryAnalytics>,
    val incomeCategories: List<CategoryAnalytics>,
    val accountBreakdown: List<AccountAnalytics>,
    val trendBuckets: List<TimeBucketAnalytics>,
    val totalTransactionsInPeriod: Int
)
