package com.example.domain.model

import java.time.LocalDate

/**
 * Domain model representing a financial budget target for a category and timeframe.
 */
data class Budget(
    val id: Long = 0L,
    val categoryId: Long?, // null indicates an overall monthly budget limit
    val limitAmount: Money,
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isActive: Boolean = true
)
