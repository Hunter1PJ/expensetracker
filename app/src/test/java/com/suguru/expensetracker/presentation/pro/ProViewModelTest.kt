package com.suguru.expensetracker.presentation.pro

import com.suguru.expensetracker.domain.fake.FakeEntitlementRepository
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.RestorePurchasesResult
import com.suguru.expensetracker.domain.usecase.billing.LaunchProPurchaseUseCase
import com.suguru.expensetracker.domain.usecase.billing.ObserveProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.billing.ObserveProProductDetailsUseCase
import com.suguru.expensetracker.domain.usecase.billing.RefreshProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.billing.RestorePurchasesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeEntitlementRepository
    private lateinit var viewModel: ProViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeEntitlementRepository()
        viewModel = ProViewModel(
            observeProEntitlementUseCase = ObserveProEntitlementUseCase(fakeRepository),
            observeProProductDetailsUseCase = ObserveProProductDetailsUseCase(fakeRepository),
            refreshProEntitlementUseCase = RefreshProEntitlementUseCase(fakeRepository),
            restorePurchasesUseCase = RestorePurchasesUseCase(fakeRepository),
            launchProPurchaseUseCase = LaunchProPurchaseUseCase(fakeRepository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects free entitlement and product details`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProEntitlement.Free, state.entitlement)
        assertNotNull(state.product)
        assertEquals("$9.99", state.product?.formattedPrice)
    }

    @Test
    fun `restorePurchases success updates state message`() = runTest {
        fakeRepository.restoreResult = RestorePurchasesResult.Restored
        viewModel.restorePurchases()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ExpenseTracker Pro restored successfully!", state.userMessage)
        assertEquals(ProEntitlement.Pro, state.entitlement)
    }

    @Test
    fun `restorePurchases nothing to restore updates message`() = runTest {
        fakeRepository.restoreResult = RestorePurchasesResult.NothingToRestore
        viewModel.restorePurchases()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No existing Pro purchase found for this account.", state.userMessage)
    }
}
