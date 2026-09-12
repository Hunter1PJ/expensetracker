package com.suguru.expensetracker.widget.budget

import com.suguru.expensetracker.domain.model.BudgetPeriodType

enum class BudgetWidgetState {
    LOADING,
    CONFIGURATION_REQUIRED,
    READY,
    INACTIVE,
    BUDGET_UNAVAILABLE,
    ERROR
}

data class BudgetProgressWidgetData(
    val budgetId: Long = 0L,
    val title: String = "",
    val spentFormatted: String? = null,
    val limitFormatted: String? = null,
    val remainingFormatted: String? = null,
    val progressBasisPoints: Int = 0, // 5000 = 50%, 10000 = 100%
    val progressLabel: String = "0%",
    val isExceeded: Boolean = false,
    val periodLabel: String? = null,
    val privacyMode: Boolean = false,
    val state: BudgetWidgetState = BudgetWidgetState.LOADING,
    val errorMessage: String? = null
) {
    companion object {
        fun loading() = BudgetProgressWidgetData(state = BudgetWidgetState.LOADING)
        fun configurationRequired() = BudgetProgressWidgetData(state = BudgetWidgetState.CONFIGURATION_REQUIRED)
        fun inactive(title: String) = BudgetProgressWidgetData(title = title, state = BudgetWidgetState.INACTIVE)
        fun unavailable() = BudgetProgressWidgetData(state = BudgetWidgetState.BUDGET_UNAVAILABLE)
        fun error(message: String) = BudgetProgressWidgetData(state = BudgetWidgetState.ERROR, errorMessage = message)
    }
}
