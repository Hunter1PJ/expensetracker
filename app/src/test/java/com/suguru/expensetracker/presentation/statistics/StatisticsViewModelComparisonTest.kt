package com.suguru.expensetracker.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeEntitlementRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
import com.suguru.expensetracker.domain.usecase.billing.ObserveProEntitlementUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsComparisonUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelComparisonTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    private val fixedNow = Instant.parse("2026-09-15T12:00:00Z")

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var fakeEntitlementRepository: FakeEntitlementRepository
    private lateinit var observeStatisticsUseCase: ObserveStatisticsUseCase
    private lateinit var observeStatisticsComparisonUseCase: ObserveStatisticsComparisonUseCase
    private lateinit var observeProEntitlementUseCase: ObserveProEntitlementUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        fakeEntitlementRepository = FakeEntitlementRepository()
        observeStatisticsUseCase = ObserveStatisticsUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
        observeStatisticsComparisonUseCase = ObserveStatisticsComparisonUseCase(observeStatisticsUseCase)
        observeProEntitlementUseCase = ObserveProEntitlementUseCase(fakeEntitlementRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle? = null): StatisticsViewModel {
        return StatisticsViewModel(
            observeStatisticsUseCase = observeStatisticsUseCase,
            observeStatisticsComparisonUseCase = observeStatisticsComparisonUseCase,
            observeProEntitlementUseCase = observeProEntitlementUseCase,
            savedStateHandle = savedStateHandle,
            zoneId = zoneId,
            clockNow = { fixedNow }
        )
    }

    @Test
    fun `Free user attempting to toggle comparison triggers compare paywall`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Free)
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleComparison()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isComparisonEnabled)
        assertTrue(state.showCompareProPaywall)

        viewModel.dismissCompareProPaywall()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showCompareProPaywall)
        job.cancel()
    }

    @Test
    fun `Pending user attempting to toggle comparison triggers pending message`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Pending)
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleComparison()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isComparisonEnabled)
        assertTrue(state.showComparePendingMessage)

        viewModel.dismissComparePendingMessage()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showComparePendingMessage)
        job.cancel()
    }

    @Test
    fun `Pro user can toggle comparison and select comparison mode`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Pro)

        val usdAcc = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val foodCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#EF4444")
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-08-10T10:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComparisonEnabled)

        viewModel.onToggleComparison()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.isComparisonEnabled)
        assertEquals(StatisticsComparisonMode.PREVIOUS_PERIOD, state.comparisonMode)
        assertNotNull(state.comparison)
        assertEquals("$20.00", state.comparison!!.expenseMetric.primaryAmount)
        assertEquals("$15.00", state.comparison!!.expenseMetric.comparisonAmount)

        // Switch to Previous Year
        viewModel.onComparisonModeSelected(StatisticsComparisonMode.PREVIOUS_YEAR)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(StatisticsComparisonMode.PREVIOUS_YEAR, state.comparisonMode)

        // Toggle off
        viewModel.onToggleComparison()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isComparisonEnabled)

        job.cancel()
    }

    @Test
    fun `Reactive entitlement unlock enables comparison if paywall was showing`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Free)
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleComparison()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showCompareProPaywall)

        // User purchases Pro
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Pro)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showCompareProPaywall)
        assertTrue(state.isComparisonEnabled)

        job.cancel()
    }

    @Test
    fun `Loss of Pro entitlement automatically disables comparison`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Pro)
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onToggleComparison()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isComparisonEnabled)

        // Pro expires / revokes
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Free)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isComparisonEnabled)

        job.cancel()
    }

    @Test
    fun `SavedStateHandle restores comparison state across process death for Pro user`() = runTest(testDispatcher) {
        fakeEntitlementRepository.setEntitlement(ProEntitlement.Pro)

        val handle = SavedStateHandle(
            mapOf(
                StatisticsViewModel.KEY_COMPARISON_ENABLED to true,
                StatisticsViewModel.KEY_COMPARISON_MODE to StatisticsComparisonMode.PREVIOUS_YEAR.name
            )
        )

        val viewModel = createViewModel(handle)
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isComparisonEnabled)
        assertEquals(StatisticsComparisonMode.PREVIOUS_YEAR, state.comparisonMode)

        job.cancel()
    }
}
