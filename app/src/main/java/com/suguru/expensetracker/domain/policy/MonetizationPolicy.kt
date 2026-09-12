package com.suguru.expensetracker.domain.policy

import com.suguru.expensetracker.domain.model.FeatureGateResult
import com.suguru.expensetracker.domain.model.FreeTierLimits
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProFeature

/**
 * Pure domain policy responsible for checking feature limits based on entitlement and active entity counts.
 */
class MonetizationPolicy(
    val limits: FreeTierLimits = FreeTierLimits()
) {

    fun checkAccountCreation(
        entitlement: ProEntitlement,
        currentActiveCount: Int
    ): FeatureGateResult {
        if (entitlement is ProEntitlement.Pro) {
            return FeatureGateResult.Allowed
        }
        return if (currentActiveCount >= limits.maxActiveAccounts) {
            FeatureGateResult.LimitReached(
                feature = ProFeature.ACCOUNTS,
                currentCount = currentActiveCount,
                freeLimit = limits.maxActiveAccounts
            )
        } else {
            FeatureGateResult.Allowed
        }
    }

    fun checkCustomCategoryCreation(
        entitlement: ProEntitlement,
        currentCustomCount: Int
    ): FeatureGateResult {
        if (entitlement is ProEntitlement.Pro) {
            return FeatureGateResult.Allowed
        }
        return if (currentCustomCount >= limits.maxCustomCategories) {
            FeatureGateResult.LimitReached(
                feature = ProFeature.CUSTOM_CATEGORIES,
                currentCount = currentCustomCount,
                freeLimit = limits.maxCustomCategories
            )
        } else {
            FeatureGateResult.Allowed
        }
    }

    fun checkBudgetCreation(
        entitlement: ProEntitlement,
        currentActiveBudgetCount: Int
    ): FeatureGateResult {
        if (entitlement is ProEntitlement.Pro) {
            return FeatureGateResult.Allowed
        }
        return if (currentActiveBudgetCount >= limits.maxActiveBudgets) {
            FeatureGateResult.LimitReached(
                feature = ProFeature.BUDGETS,
                currentCount = currentActiveBudgetCount,
                freeLimit = limits.maxActiveBudgets
            )
        } else {
            FeatureGateResult.Allowed
        }
    }

    fun checkRecurringRuleCreation(
        entitlement: ProEntitlement,
        currentActiveRuleCount: Int
    ): FeatureGateResult {
        if (entitlement is ProEntitlement.Pro) {
            return FeatureGateResult.Allowed
        }
        return if (currentActiveRuleCount >= limits.maxActiveRecurringRules) {
            FeatureGateResult.LimitReached(
                feature = ProFeature.RECURRING_RULES,
                currentCount = currentActiveRuleCount,
                freeLimit = limits.maxActiveRecurringRules
            )
        } else {
            FeatureGateResult.Allowed
        }
    }
}
