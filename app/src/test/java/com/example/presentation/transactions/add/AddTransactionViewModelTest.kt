package com.example.presentation.transactions.add

import com.example.domain.fake.FakeAccountRepository
import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.fake.FakeTransactionRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase
    private lateinit var observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase
    private lateinit var createTransactionUseCase: CreateTransactionUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
        observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(categoryRepository)
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddTransactionViewModel {
        return AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
    }

    @Test
    fun `initial state loads active accounts and default categories`() = runTest(testDispatcher) {
        val accountId1 = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId1 = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.accounts.size)
        assertEquals(accountId1, state.selectedAccountId)
        assertEquals(1, state.categories.size)
        assertEquals(categoryId1, state.selectedCategoryId)
        assertEquals(TransactionType.EXPENSE, state.transactionType)
    }

    @Test
    fun `switching to income updates compatible categories`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        val incomeCatId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.INCOME)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionType.INCOME, state.transactionType)
        assertEquals(1, state.categories.size)
        assertEquals("Salary", state.categories.first().name)
        assertEquals(incomeCatId, state.selectedCategoryId)
    }

    @Test
    fun `switching to transfer clears category selection`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionType.TRANSFER, state.transactionType)
        assertTrue(state.categories.isEmpty())
        assertNull(state.selectedCategoryId)
    }

    @Test
    fun `amount input filtering allows only valid numbers`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onAmountChanged("25.50")
        assertEquals("25.50", viewModel.uiState.value.amount)

        viewModel.onAmountChanged("abc") // rejected
        assertEquals("25.50", viewModel.uiState.value.amount)

        viewModel.onAmountChanged("25,75") // converted comma
        assertEquals("25.75", viewModel.uiState.value.amount)
    }

    @Test
    fun `save with invalid or zero amount sets error`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("0")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.AmountMustBePositive, viewModel.uiState.value.error)
    }

    @Test
    fun `save with no accounts sets error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.AccountRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `save transfer without destination account sets error`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.DestinationRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `save transfer with same source and destination sets error`() = runTest(testDispatcher) {
        val accountId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        viewModel.onAccountSelected(accountId)
        viewModel.onDestinationAccountSelected(accountId)
        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.SameSourceAndDestination, viewModel.uiState.value.error)
    }

    @Test
    fun `successfully save expense transaction`() = runTest(testDispatcher) {
        val accountId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("45.50")
        viewModel.onNoteChanged("Weekly supermarket run")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val transactions = transactionRepository.getTransactionsByAccount(accountId)
        assertEquals(1, transactions.size)
        val tx = transactions.first()
        assertEquals(4550L, tx.amount.amountInMinorUnits)
        assertEquals("USD", tx.amount.currencyCode)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(categoryId, tx.categoryId)
        assertEquals("Weekly supermarket run", tx.note)
    }

    @Test
    fun `successfully save transfer transaction`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val destId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        advanceUntilIdle()

        viewModel.onAccountSelected(sourceId)
        viewModel.onDestinationAccountSelected(destId)
        viewModel.onAmountChanged("200.00")
        viewModel.onNoteChanged("Monthly savings transfer")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val transactions = transactionRepository.getTransactionsByAccount(sourceId)
        assertEquals(1, transactions.size)
        val tx = transactions.first()
        assertEquals(20000L, tx.amount.amountInMinorUnits)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(sourceId, tx.accountId)
        assertEquals(destId, tx.destinationAccountId)
        assertNull(tx.categoryId)
        assertEquals("Monthly savings transfer", tx.note)
    }

    @Test
    fun `successfully save income transaction`() = runTest(testDispatcher) {
        val accountId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.INCOME)
        advanceUntilIdle()

        viewModel.onAccountSelected(accountId)
        viewModel.onCategorySelected(categoryId)
        viewModel.onAmountChanged("3500.00")
        viewModel.onNoteChanged("Monthly salary payment")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val transactions = transactionRepository.getTransactionsByAccount(accountId)
        assertEquals(1, transactions.size)
        val tx = transactions.first()
        assertEquals(350000L, tx.amount.amountInMinorUnits)
        assertEquals("USD", tx.amount.currencyCode)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(categoryId, tx.categoryId)
        assertNull(tx.destinationAccountId)
        assertEquals("Monthly salary payment", tx.note)
    }

    @Test
    fun `save expense without category sets error`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        // No categories inserted

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.CategoryRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `cross-currency transfer is rejected`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val destId = accountRepository.insertAccount(
            Account(name = "EUR Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "EUR"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        viewModel.onAccountSelected(sourceId)
        viewModel.onDestinationAccountSelected(destId)
        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()

        assertEquals(AddTransactionError.CurrencyMismatch, viewModel.uiState.value.error)
    }

    @Test
    fun `dismiss error clears error state`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("0")
        viewModel.saveTransaction()
        assertEquals(AddTransactionError.AmountMustBePositive, viewModel.uiState.value.error)

        viewModel.onDismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `duplicate save attempts are prevented while isSaving is true`() = runTest(testDispatcher) {
        val accountId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#FF5722")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("10.00")
        viewModel.onAccountSelected(accountId)
        viewModel.onCategorySelected(categoryId)

        // Trigger first save
        viewModel.saveTransaction()
        // Immediately trigger second save before dispatcher advances
        viewModel.saveTransaction()
        advanceUntilIdle()

        // Only 1 transaction should have been inserted
        val transactions = transactionRepository.getTransactionsByAccount(accountId)
        assertEquals(1, transactions.size)
    }
}
