package com.suguru.expensetracker.domain.usecase.billing

import com.suguru.expensetracker.domain.fake.FakeEntitlementRepository
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BillingUseCasesTest {

    private lateinit var fakeRepository: FakeEntitlementRepository
    private lateinit var observeEntitlement: ObserveProEntitlementUseCase
    private lateinit var observeProductDetails: ObserveProProductDetailsUseCase
    private lateinit var restorePurchases: RestorePurchasesUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeEntitlementRepository()
        observeEntitlement = ObserveProEntitlementUseCase(fakeRepository)
        observeProductDetails = ObserveProProductDetailsUseCase(fakeRepository)
        restorePurchases = RestorePurchasesUseCase(fakeRepository)
    }

    @Test
    fun `observeProEntitlement returns initial free state`() = runTest {
        val entitlement = observeEntitlement().first()
        assertEquals(ProEntitlement.Free, entitlement)
    }

    @Test
    fun `observeProEntitlement emits Pro when updated`() = runTest {
        fakeRepository.setEntitlement(ProEntitlement.Pro)
        val entitlement = observeEntitlement().first()
        assertEquals(ProEntitlement.Pro, entitlement)
    }

    @Test
    fun `restorePurchases returns restored result`() = runTest {
        fakeRepository.restoreResult = RestorePurchasesResult.Restored
        val result = restorePurchases()
        assertEquals(RestorePurchasesResult.Restored, result)
        assertEquals(ProEntitlement.Pro, observeEntitlement().first())
    }
}
