package com.suguru.expensetracker.widget.common

import com.suguru.expensetracker.domain.model.ProEntitlement

enum class WidgetFeature {
    ACCOUNT_BALANCE,
    QUICK_ADD
}

sealed interface WidgetGateResult {
    data object Allowed : WidgetGateResult
    data class LimitReached(val feature: WidgetFeature, val limit: Int) : WidgetGateResult
    data class ProRequired(val feature: WidgetFeature) : WidgetGateResult
}

class WidgetEntitlementPolicy {
    fun checkEntitlement(
        feature: WidgetFeature,
        currentProEntitlement: ProEntitlement,
        currentConfiguredCount: Int
    ): WidgetGateResult {
        if (currentProEntitlement is ProEntitlement.Pro) {
            return WidgetGateResult.Allowed
        }

        return when (feature) {
            WidgetFeature.ACCOUNT_BALANCE -> {
                if (currentConfiguredCount >= 1) {
                    WidgetGateResult.LimitReached(WidgetFeature.ACCOUNT_BALANCE, 1)
                } else {
                    WidgetGateResult.Allowed
                }
            }
            WidgetFeature.QUICK_ADD -> {
                WidgetGateResult.ProRequired(WidgetFeature.QUICK_ADD)
            }
        }
    }
}
