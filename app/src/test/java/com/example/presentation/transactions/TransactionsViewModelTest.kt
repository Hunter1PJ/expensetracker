package com.example.presentation.transactions

import com.example.domain.fake.FakeAccountRepository
import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.fake.FakeTransactionRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveAllAccountsUseCase
import com.example.domain.usecase.category.ObserveAllCategoriesUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var observeTransactionsUseCase: ObserveTransactionsUseCase
    private lateinit var observeAllAccountsUseCase: ObserveAllAccountsUseCase
    private lateinit var observeAllCategoriesUseCase: ObserveAllCategoriesUseCase

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        observeTransactionsUseCase = ObserveTransactionsUseCase(transactionRepository)
        observeAllAccountsUseCase = ObserveAllAccountsUseCase(accountRepository)
        observeAllCategoriesUseCase = ObserveAllCategoriesUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TransactionsViewModel {
        return TransactionsViewModel(
            observeTransactionsUseCase = observeTransactionsUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase,
            observeAllCategoriesUseCase = observeAllCategoriesUseCase,
            zoneId = zoneId
        )
    }

    @Test
    fun `initial state loads transactions grouped by date`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val tx1 = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T10:00:00Z"),
                note = "Lunch"
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasAnyTransactionsInDb)
        assertEquals(1, state.groupedTransactions.size)
        assertEquals(1, state.groupedTransactions[0].transactions.size)
        assertEquals(tx1, state.groupedTransactions[0].transactions[0].id)

        job.cancel()
    }

    @Test
    fun `search query filters transactions by note`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T10:00:00Z"),
                note = "Supermarket groceries"
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T11:00:00Z"),
                note = "Taxi fare"
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Taxi")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Taxi", state.searchQuery)
        val totalFilteredCount = state.groupedTransactions.sumOf { it.transactions.size }
        assertEquals(1, totalFilteredCount)

        job.cancel()
    }

    @Test
    fun `type filter restricts to income only`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val expCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#000")
        )
        val incCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#FFF")
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = expCat,
                transactionTime = Instant.parse("2026-09-01T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(500000L, "USD"),
                type = TransactionType.INCOME,
                accountId = accId,
                categoryId = incCat,
                transactionTime = Instant.parse("2026-09-01T12:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onTypeFilterChanged(TransactionTypeFilter.INCOME)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionTypeFilter.INCOME, state.selectedType)
        val totalFilteredCount = state.groupedTransactions.sumOf { it.transactions.size }
        assertEquals(1, totalFilteredCount)
        assertEquals("Salary", state.groupedTransactions[0].transactions[0].title)

        job.cancel()
    }
}
