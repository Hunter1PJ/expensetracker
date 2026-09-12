package com.suguru.expensetracker.domain.model

object BillingProducts {
    const val PRO_LIFETIME = "expensetracker_pro_lifetime"
}

sealed interface ProEntitlement {
    data object Checking : ProEntitlement
    data object Free : ProEntitlement
    data object Pending : ProEntitlement
    data object Pro : ProEntitlement
    data class Unavailable(val reason: String? = null) : ProEntitlement
}

data class ProProductDetails(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String
)

sealed interface PurchaseLaunchResult {
    data object Launched : PurchaseLaunchResult
    data object AlreadyOwned : PurchaseLaunchResult
    data object ProductUnavailable : PurchaseLaunchResult
    data object BillingUnavailable : PurchaseLaunchResult
    data class Error(val message: String?) : PurchaseLaunchResult
}

sealed interface RestorePurchasesResult {
    data object Restored : RestorePurchasesResult
    data object NothingToRestore : RestorePurchasesResult
    data object BillingUnavailable : RestorePurchasesResult
    data class Error(val message: String?) : RestorePurchasesResult
}
