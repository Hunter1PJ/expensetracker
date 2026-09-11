package com.example.presentation.budgets

import com.example.domain.fake.FakeAccountRepository
import com.example.domain.fake.FakeBudgetRepository
import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.fake.FakeTransactionRepository
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.Money
import com.example.domain.usecase.budget.DeactivateBudgetUseCase
import com.example.domain.usecase.budget.ObserveActiveBudgetProgressUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var budgetRepo: FakeBudgetRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var observeUseCase: ObserveActiveBudgetProgressUseCase
    private lateinit var deactivateUseCase: DeactivateBudgetUseCase
    private lateinit var viewModel: BudgetsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepo = FakeBudgetRepository()
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        categoryRepo = FakeCategoryRepository()
        observeUseCase = ObserveActiveBudgetProgressUseCase(budgetRepo, transactionRepo, accountRepo, categoryRepo)
        deactivateUseCase = DeactivateBudgetUseCase(budgetRepo)

        viewModel = BudgetsViewModel(observeUseCase, deactivateUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads active budgets`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        budgetRepo.insertBudget(
            Budget(
                id = 1L,
                categoryId = null,
                limitAmount = Money(10000L, "USD"),
                periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(30),
                isActive = true
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.budgets.size)
        assertEquals("USD", state.budgets.first().budget.limitAmount.currencyCode)
    }

    @Test
    fun `deactivating budget updates state`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val id = budgetRepo.insertBudget(
            Budget(
                id = 1L,
                categoryId = null,
                limitAmount = Money(10000L, "USD"),
                periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(30),
                isActive = true
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.promptDeactivate(id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(id, viewModel.uiState.value.deactivatingBudgetId)

        viewModel.confirmDeactivate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.deactivatingBudgetId)
        // Verify deactivated budget is no longer in active budgets flow
        val activeBudgets = budgetRepo.observeActiveBudgets().first()
        assertEquals(0, activeBudgets.size)
    }
}
