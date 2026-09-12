package com.suguru.expensetracker.domain.repository

import android.app.Activity
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProProductDetails
import com.suguru.expensetracker.domain.model.PurchaseLaunchResult
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import kotlinx.coroutines.flow.StateFlow

interface EntitlementRepository {
    val entitlement: StateFlow<ProEntitlement>
    val productDetails: StateFlow<ProProductDetails?>

    suspend fun refreshPurchases()
    suspend fun restorePurchases(): RestorePurchasesResult
    suspend fun launchPurchase(activity: Activity): PurchaseLaunchResult
}
