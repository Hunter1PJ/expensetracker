package com.suguru.expensetracker.domain.model

enum class ProFeature {
    ACCOUNTS,
    CUSTOM_CATEGORIES,
    BUDGETS,
    RECURRING_RULES
}

data class FreeTierLimits(
    val maxActiveAccounts: Int = 2,
    val maxCustomCategories: Int = 3,
    val maxActiveBudgets: Int = 2,
    val maxActiveRecurringRules: Int = 2
)

sealed interface FeatureGateResult {
    data object Allowed : FeatureGateResult

    data class LimitReached(
        val feature: ProFeature,
        val currentCount: Int,
        val freeLimit: Int
    ) : FeatureGateResult
}
