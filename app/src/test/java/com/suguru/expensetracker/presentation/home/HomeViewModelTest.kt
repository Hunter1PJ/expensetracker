package com.suguru.expensetracker.presentation.home

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase
    private lateinit var observeAccountSummariesUseCase: ObserveAccountSummariesUseCase
    private lateinit var observeRecentTransactionsUseCase: ObserveRecentTransactionsUseCase

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        createTransactionUseCase = CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository)
        observeDashboardSummaryUseCase = ObserveDashboardSummaryUseCase(accountRepository, transactionRepository)
        observeAccountSummariesUseCase = ObserveAccountSummariesUseCase(accountRepository, transactionRepository)
        observeRecentTransactionsUseCase = ObserveRecentTransactionsUseCase(transactionRepository, accountRepository, categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_whenNoAccounts_hasAccountsIsFalse() = runTest {
        viewModel = HomeViewModel(
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            observeAccountSummariesUseCase = observeAccountSummariesUseCase,
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasAccounts)
        assertEquals(0, state.currencySummaries.size)
        assertEquals(0, state.accounts.size)
        assertEquals(0, state.recentTransactions.size)

        collectJob.cancel()
    }

    @Test
    fun populatedState_formatsBalancesAndCalculatesMonthlyFlows() = runTest {
        // Create 2 accounts: USD and UZS
        val usdId = accountRepository.insertAccount(
            Account(name = "Main Bank", type = AccountType.BANK, initialBalance = Money(150000L, "USD"))
        )
        val uzsId = accountRepository.insertAccount(
            Account(name = "Cash Wallet", type = AccountType.CASH, initialBalance = Money(3000000L, "UZS"))
        )

        val foodCat = categoryRepository.insertCategory(
            Category(name = "Dining", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#EF4444")
        )
        val salaryCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        // Add USD expense: $50.00
        createTransactionUseCase(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(5000L, "USD"),
                accountId = usdId,
                categoryId = foodCat,
                transactionTime = Instant.now()
            )
        )

        // Add USD income: $500.00
        createTransactionUseCase(
            Transaction(
                type = TransactionType.INCOME,
                amount = Money(50000L, "USD"),
                accountId = usdId,
                categoryId = salaryCat,
                transactionTime = Instant.now()
            )
        )

        viewModel = HomeViewModel(
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            observeAccountSummariesUseCase = observeAccountSummariesUseCase,
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase
        )

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasAccounts)
        assertTrue(state.isMultiCurrency)
        assertEquals(2, state.currencySummaries.size)
        assertEquals(2, state.accounts.size)
        assertEquals(2, state.recentTransactions.size)

        val usdSummary = state.currencySummaries.find { it.currencyCode == "USD" }!!
        // Balance: 1500.00 - 50.00 + 500.00 = $1,950.00
        assertEquals("$1,950.00", usdSummary.balanceFormatted)
        assertEquals("+ $500.00", usdSummary.incomeFormatted)
        assertEquals("- $50.00", usdSummary.expenseFormatted)
        assertEquals("+ $450.00", usdSummary.netFormatted)
        assertTrue(usdSummary.isNetPositive)
        assertFalse(usdSummary.isNetNegative)

        val uzsSummary = state.currencySummaries.find { it.currencyCode == "UZS" }!!
        assertEquals("3,000,000 so'm", uzsSummary.balanceFormatted)

        collectJob.cancel()
    }
}
