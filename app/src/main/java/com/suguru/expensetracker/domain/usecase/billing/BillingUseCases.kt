package com.suguru.expensetracker.domain.usecase.billing

import android.app.Activity
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProProductDetails
import com.suguru.expensetracker.domain.model.PurchaseLaunchResult
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveProEntitlementUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    operator fun invoke(): StateFlow<ProEntitlement> = entitlementRepository.entitlement
}

class ObserveProProductDetailsUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    operator fun invoke(): StateFlow<ProProductDetails?> = entitlementRepository.productDetails
}

class RefreshProEntitlementUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    suspend operator fun invoke() {
        entitlementRepository.refreshPurchases()
    }
}

class RestorePurchasesUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    suspend operator fun invoke(): RestorePurchasesResult {
        return entitlementRepository.restorePurchases()
    }
}

class LaunchProPurchaseUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    suspend operator fun invoke(activity: Activity): PurchaseLaunchResult {
        return entitlementRepository.launchPurchase(activity)
    }
}
