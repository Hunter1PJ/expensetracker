package com.suguru.expensetracker.presentation.transactions.add

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `populateFromCandidate pre-populates supported fields for expense`() = runTest(testDispatcher) {
        val accountId = accountRepository.insertAccount(
            Account(name = "Main Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Taxi & Transport", type = CategoryType.EXPENSE, iconName = "local_taxi", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Paid 25 USD for taxi",
            suggestedTitle = "Taxi ride to office",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            date = java.time.LocalDate.of(2026, 11, 10),
            suggestedAccountName = "Main Bank",
            suggestedCategoryName = "Taxi & Transport",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAiAssisted)
        assertEquals(TransactionType.EXPENSE, state.transactionType)
        assertEquals("25.00", state.amount)
        assertEquals("Taxi ride to office", state.note)
        assertEquals(accountId, state.selectedAccountId)
        assertEquals(categoryId, state.selectedCategoryId)
        assertNull(state.aiAmountWarning)
        assertFalse(state.aiAccountMatchAmbiguous)
        assertFalse(state.aiCategoryMatchAmbiguous)
        assertFalse(state.isSavedSuccessfully) // Strict: must never save automatically
    }

    @Test
    fun `populateFromCandidate handles missing amount safely and sets warning`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Coffee", type = CategoryType.EXPENSE, iconName = "local_cafe", colorHex = "#EF4444")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Bought coffee",
            suggestedTitle = "Coffee at cafe",
            transactionType = TransactionType.EXPENSE,
            amount = null, // Missing amount!
            date = java.time.LocalDate.now(),
            suggestedAccountName = "Checking",
            suggestedCategoryName = "Coffee",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.LOW,
            warnings = listOf(com.suguru.expensetracker.domain.parser.ParseWarning.MISSING_AMOUNT)
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertTrue(state.isAiAssisted)
        assertEquals("", state.amount) // Never invent default amount
        assertNotNull(state.aiAmountWarning)

        // Attempting to save without providing an amount must be rejected
        viewModel.saveTransaction()
        assertEquals(AddTransactionError.InvalidAmount, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
    }

    @Test
    fun `populateFromCandidate exact category matching preselects category`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val groceriesId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        categoryRepository.insertCategory(
            Category(name = "Utilities", type = CategoryType.EXPENSE, iconName = "bolt", colorHex = "#F59E0B")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Supermarket 50",
            suggestedTitle = "Supermarket",
            transactionType = TransactionType.EXPENSE,
            amount = Money(5000L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedCategoryName = "groceries", // Case-insensitive exact match
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertEquals(groceriesId, state.selectedCategoryId)
        assertFalse(state.aiCategoryMatchAmbiguous)
    }

    @Test
    fun `populateFromCandidate ambiguous category matching requires manual selection`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        // Two categories matching "Food" prefix / words
        categoryRepository.insertCategory(
            Category(name = "Food & Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        categoryRepository.insertCategory(
            Category(name = "Food Dining", type = CategoryType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Food 30",
            suggestedTitle = "Food",
            transactionType = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedCategoryName = "Food", // Ambiguous match!
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertNull(state.selectedCategoryId) // Ambiguous match requires manual selection
        assertTrue(state.aiCategoryMatchAmbiguous)
        assertEquals("Food", state.aiCategorySuggestion)
    }

    @Test
    fun `populateFromCandidate unmatched category leaves category unselected with suggestion`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Flight ticket 300",
            suggestedTitle = "Flight ticket",
            transactionType = TransactionType.EXPENSE,
            amount = Money(30000L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedCategoryName = "Travel & Flights", // Does not exist
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertNull(state.selectedCategoryId)
        assertFalse(state.aiCategoryMatchAmbiguous)
        assertEquals("Travel & Flights", state.aiCategorySuggestion)
    }

    @Test
    fun `populateFromCandidate exact account matching preselects account`() = runTest(testDispatcher) {
        val walletId = accountRepository.insertAccount(
            Account(name = "Cash Wallet", type = AccountType.CASH, initialBalance = Money(50000L, "USD"))
        )
        accountRepository.insertAccount(
            Account(name = "Bank Card", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Snack from cash wallet",
            suggestedTitle = "Snack",
            transactionType = TransactionType.EXPENSE,
            amount = Money(500L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedAccountName = "Cash Wallet",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertEquals(walletId, state.selectedAccountId)
        assertFalse(state.aiAccountMatchAmbiguous)
    }

    @Test
    fun `populateFromCandidate ambiguous account matching requires manual selection`() = runTest(testDispatcher) {
        // Two card accounts
        accountRepository.insertAccount(
            Account(name = "Card Visa", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        accountRepository.insertAccount(
            Account(name = "Card Mastercard", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Paid 20 with card",
            suggestedTitle = "Item",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2000L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedAccountName = "Card", // Matches both!
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertNull(state.selectedAccountId) // Ambiguous -> requires manual selection
        assertTrue(state.aiAccountMatchAmbiguous)
    }

    @Test
    fun `populateFromCandidate pre-populates transfer with source and destination accounts`() = runTest(testDispatcher) {
        val checkingId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val savingsId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Transfer 200 from Checking to Savings",
            suggestedTitle = "Savings transfer",
            transactionType = TransactionType.TRANSFER,
            amount = Money(20000L, "USD"),
            date = java.time.LocalDate.now(),
            transferSourceAccountName = "Checking",
            transferDestinationAccountName = "Savings",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertEquals(TransactionType.TRANSFER, state.transactionType)
        assertEquals("200.00", state.amount)
        assertEquals(checkingId, state.selectedAccountId)
        assertEquals(savingsId, state.selectedDestinationAccountId)
        assertNull(state.selectedCategoryId)
        assertFalse(state.aiAccountMatchAmbiguous)
        assertFalse(state.aiDestinationAccountMatchAmbiguous)

        // Save successfully
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        val txs = transactionRepository.getTransactionsByAccount(checkingId)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.TRANSFER, txs.first().type)
        assertEquals(savingsId, txs.first().destinationAccountId)
    }

    @Test
    fun `populateFromCandidate transfer with ambiguous destination requires manual selection`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Main Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        accountRepository.insertAccount(
            Account(name = "Savings USD 1", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        accountRepository.insertAccount(
            Account(name = "Savings USD 2", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Transfer to savings",
            suggestedTitle = "Savings",
            transactionType = TransactionType.TRANSFER,
            amount = Money(10000L, "USD"),
            date = java.time.LocalDate.now(),
            transferSourceAccountName = "Main Bank",
            transferDestinationAccountName = "Savings", // Matches both savings accounts!
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertEquals(sourceId, state.selectedAccountId)
        assertNull(state.selectedDestinationAccountId) // Ambiguous destination!
        assertTrue(state.aiDestinationAccountMatchAmbiguous)

        // Save must fail until destination account is selected
        viewModel.saveTransaction()
        assertEquals(AddTransactionError.DestinationRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `populateFromCandidate resolves date to start of day instant`() = runTest(testDispatcher) {
        val testDate = java.time.LocalDate.of(2026, 11, 5)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Lunch on Nov 5",
            suggestedTitle = "Lunch",
            transactionType = TransactionType.EXPENSE,
            amount = Money(1500L, "USD"),
            date = testDate,
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        assertEquals(expectedInstant, viewModel.uiState.value.transactionTime)
    }

    @Test
    fun `clearAiDraftState removes AI-assisted flags and warnings`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Draft",
            suggestedTitle = "Draft",
            transactionType = TransactionType.EXPENSE,
            amount = null,
            date = java.time.LocalDate.now(),
            suggestedAccountName = "Ambiguous Account",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.LOW,
            warnings = listOf(com.suguru.expensetracker.domain.parser.ParseWarning.MISSING_AMOUNT)
        )

        viewModel.populateFromCandidate(candidate)
        assertTrue(viewModel.uiState.value.isAiAssisted)

        viewModel.clearAiDraftState()
        assertFalse(viewModel.uiState.value.isAiAssisted)
        assertNull(viewModel.uiState.value.aiAmountWarning)
        assertNull(viewModel.uiState.value.aiAccountSuggestion)
        assertFalse(viewModel.uiState.value.aiAccountMatchAmbiguous)
    }

    @Test
    fun `clearForm resets state to clean manual transaction entry`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Dinner 80",
            suggestedTitle = "Dinner",
            transactionType = TransactionType.EXPENSE,
            amount = Money(8000L, "USD"),
            date = java.time.LocalDate.of(2026, 11, 1),
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        assertEquals("80.00", viewModel.uiState.value.amount)
        assertTrue(viewModel.uiState.value.isAiAssisted)

        // Clear form for clean manual entry
        viewModel.clearForm()

        val state = viewModel.uiState.value
        assertFalse(state.isAiAssisted)
        assertEquals("", state.amount)
        assertEquals("", state.note)
    }

    @Test
    fun `user can edit pre-filled fields after AI pre-population`() = runTest(testDispatcher) {
        val account1 = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val account2 = accountRepository.insertAccount(
            Account(name = "Cash", type = AccountType.CASH, initialBalance = Money(50000L, "USD"))
        )
        val cat1 = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )
        val cat2 = categoryRepository.insertCategory(
            Category(name = "Transport", type = CategoryType.EXPENSE, iconName = "directions_bus", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = com.suguru.expensetracker.domain.parser.TransactionCandidate(
            rawText = "Food 20",
            suggestedTitle = "Food",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2000L, "USD"),
            date = java.time.LocalDate.now(),
            suggestedAccountName = "Bank",
            suggestedCategoryName = "Food",
            confidence = com.suguru.expensetracker.domain.parser.ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        assertEquals(account1, viewModel.uiState.value.selectedAccountId)
        assertEquals(cat1, viewModel.uiState.value.selectedCategoryId)
        assertEquals("20.00", viewModel.uiState.value.amount)

        // User edits every field
        viewModel.onAmountChanged("35.50")
        viewModel.onAccountSelected(account2)
        viewModel.onCategorySelected(cat2)
        viewModel.onNoteChanged("Transport for party")

        val updatedState = viewModel.uiState.value
        assertEquals("35.50", updatedState.amount)
        assertEquals(account2, updatedState.selectedAccountId)
        assertEquals(cat2, updatedState.selectedCategoryId)
        assertEquals("Transport for party", updatedState.note)

        // User confirms and saves
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        val txs = transactionRepository.getTransactionsByAccount(account2)
        assertEquals(1, txs.size)
        assertEquals(3550L, txs.first().amount.amountInMinorUnits)
        assertEquals(cat2, txs.first().categoryId)
        assertEquals("Transport for party", txs.first().note)
    }
}
