package com.suguru.expensetracker.presentation.budgets.addedit

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeBudgetRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.usecase.account.ObserveAllAccountsUseCase
import com.suguru.expensetracker.domain.usecase.budget.CreateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.GetBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.UpdateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditBudgetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var budgetRepo: FakeBudgetRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var getBudgetUseCase: GetBudgetUseCase
    private lateinit var createBudgetUseCase: CreateBudgetUseCase
    private lateinit var updateBudgetUseCase: UpdateBudgetUseCase
    private lateinit var observeCategoriesUseCase: ObserveActiveCategoriesUseCase
    private lateinit var observeAccountsUseCase: ObserveAllAccountsUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepo = FakeBudgetRepository()
        categoryRepo = FakeCategoryRepository()
        accountRepo = FakeAccountRepository()

        getBudgetUseCase = GetBudgetUseCase(budgetRepo)
        createBudgetUseCase = CreateBudgetUseCase(budgetRepo, categoryRepo)
        updateBudgetUseCase = UpdateBudgetUseCase(budgetRepo, categoryRepo)
        observeCategoriesUseCase = ObserveActiveCategoriesUseCase(categoryRepo)
        observeAccountsUseCase = ObserveAllAccountsUseCase(accountRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveBudget succeeds with valid overall budget`() = runTest {
        val viewModel = AddEditBudgetViewModel(
            budgetId = 0L,
            getBudgetUseCase = getBudgetUseCase,
            createBudgetUseCase = createBudgetUseCase,
            updateBudgetUseCase = updateBudgetUseCase,
            observeActiveCategoriesUseCase = observeCategoriesUseCase,
            observeAllAccountsUseCase = observeAccountsUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAmountChanged("500")
        viewModel.onCurrencySelected("USD")
        viewModel.onPeriodTypeChanged(BudgetPeriodType.MONTHLY)

        viewModel.saveBudget()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)

        val created = budgetRepo.observeActiveBudgets().first()
        assertEquals(1, created.size)
        assertEquals(50000L, created.first().limitAmount.amountInMinorUnits)
    }

    @Test
    fun `saveBudget fails when limit is zero or blank`() = runTest {
        val viewModel = AddEditBudgetViewModel(
            budgetId = 0L,
            getBudgetUseCase = getBudgetUseCase,
            createBudgetUseCase = createBudgetUseCase,
            updateBudgetUseCase = updateBudgetUseCase,
            observeActiveCategoriesUseCase = observeCategoriesUseCase,
            observeAllAccountsUseCase = observeAccountsUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAmountChanged("0")
        viewModel.saveBudget()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.validationError)
        assertEquals(0, budgetRepo.observeActiveBudgets().first().size)
    }

    @Test
    fun `saveBudget fails when overlapping budget exists`() = runTest {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        val end = today.withDayOfMonth(today.lengthOfMonth())

        // Insert existing active budget
        budgetRepo.insertBudget(
            Budget(
                id = 1L,
                categoryId = null,
                limitAmount = Money(100000L, "USD"),
                periodType = BudgetPeriodType.MONTHLY,
                startDate = start,
                endDate = end,
                isActive = true
            )
        )

        val viewModel = AddEditBudgetViewModel(
            budgetId = 0L,
            getBudgetUseCase = getBudgetUseCase,
            createBudgetUseCase = createBudgetUseCase,
            updateBudgetUseCase = updateBudgetUseCase,
            observeActiveCategoriesUseCase = observeCategoriesUseCase,
            observeAllAccountsUseCase = observeAccountsUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAmountChanged("300")
        viewModel.onCurrencySelected("USD")
        viewModel.onPeriodTypeChanged(BudgetPeriodType.MONTHLY)

        viewModel.saveBudget()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("already exists"))
    }
}
