package com.suguru.expensetracker.domain.model.budget

import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.Money

/**
 * Derived summary model combining a budget with real expense calculation.
 */
data class BudgetProgress(
    val budget: Budget,
    val categoryName: String?, // null indicates overall budget
    val categoryIconName: String?,
    val categoryColorHex: String?,
    val spent: Money,
    val remaining: Money,
    val progressBasisPoints: Int, // 0 -> 10000+ (e.g., 5000 = 50%, 10000 = 100%)
    val isExceeded: Boolean
)

sealed class BudgetValidationError : Exception() {
    data object InvalidLimit : BudgetValidationError()
    data object InvalidCurrency : BudgetValidationError()
    data object InvalidDateRange : BudgetValidationError()
    data object CategoryNotFound : BudgetValidationError()
    data object IncompatibleCategoryType : BudgetValidationError()
    data object CategoryArchived : BudgetValidationError()
    data object OverlappingBudgetExists : BudgetValidationError()
}
