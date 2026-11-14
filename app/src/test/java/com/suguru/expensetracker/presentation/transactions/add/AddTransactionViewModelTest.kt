package com.suguru.expensetracker.presentation.transactions.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AddTransactionViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    private suspend fun insertDefaultAccount(
        currencyCode: String = "USD",
        initialBalanceMinor: Long = 100000L
    ): Long {
        return accountRepository.insertAccount(
            Account(
                name = "Internal Default",
                type = AccountType.CASH,
                initialBalance = Money(initialBalanceMinor, currencyCode),
                internalKey = "default"
            )
        )
    }

    @Test
    fun `initial state resolves internal default account and default categories`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        val categoryId1 = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(defaultAccountId, state.defaultAccount?.id)
        assertTrue(state.hasDefaultAccount)
        assertEquals(1, state.categories.size)
        assertEquals(categoryId1, state.selectedCategoryId)
        assertEquals(TransactionType.EXPENSE, state.transactionType)
    }

    @Test
    fun `internal default account is resolved over legacy accounts regardless of insertion order`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val internalAccountId = insertDefaultAccount()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(internalAccountId, viewModel.uiState.value.defaultAccount?.id)
    }

    @Test
    fun `add transaction screen hides transfer tab and account selectors while keeping expense and income controls`() {
        val expenseCategory = Category(
            id = 1L,
            name = "Groceries",
            type = CategoryType.EXPENSE,
            iconName = "shopping_cart",
            colorHex = "#10B981"
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                AddTransactionScreenContent(
                    uiState = AddTransactionUiState(
                        transactionType = TransactionType.EXPENSE,
                        defaultAccount = Account(
                            id = 1L,
                            name = "Internal Default",
                            type = AccountType.CASH,
                            initialBalance = Money(0L, "USD"),
                            internalKey = "default"
                        ),
                        categories = listOf(expenseCategory),
                        selectedCategoryId = expenseCategory.id
                    ),
                    onNavigateBack = {},
                    onTransactionTypeChanged = {},
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onNoteChanged = {},
                    onTransactionTimeChanged = {},
                    onDismissError = {},
                    onSaveTransaction = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("account_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("destination_account_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("type_tab_transfer").assertDoesNotExist()

        composeTestRule.onNodeWithTag("type_tab_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_tab_income").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_input_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_selector").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_input_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_picker_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `switching to income updates compatible categories`() = runTest(testDispatcher) {
        insertDefaultAccount()
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
    fun `switching to transfer is rejected and preserves current transaction type and category`() = runTest(testDispatcher) {
        insertDefaultAccount()
        val groceriesId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.TRANSFER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransactionType.EXPENSE, state.transactionType)
        assertEquals(groceriesId, state.selectedCategoryId)
        assertEquals(1, state.categories.size)
        assertNotNull(state.error)
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
    fun `save with invalid or zero amount sets error and creates no record`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("0")
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertEquals(AddTransactionError.AmountMustBePositive, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(defaultAccountId).isEmpty())
    }

    @Test
    fun `save without internal default account sets error and creates no record`() = runTest(testDispatcher) {
        val legacyAccountId = accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.defaultAccount)
        assertFalse(viewModel.uiState.value.hasDefaultAccount)

        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertEquals(AddTransactionError.AccountRequired, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(legacyAccountId).isEmpty())
    }

    @Test
    fun `successfully save expense transaction persists to internal default account`() = runTest(testDispatcher) {
        val legacyAccountId = accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(50000L, "USD"))
        )
        val defaultAccountId = insertDefaultAccount()
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

        val transactions = transactionRepository.getTransactionsByAccount(defaultAccountId)
        assertEquals(1, transactions.size)
        val tx = transactions.first()
        assertEquals(defaultAccountId, tx.accountId)
        assertEquals(4550L, tx.amount.amountInMinorUnits)
        assertEquals("USD", tx.amount.currencyCode)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(categoryId, tx.categoryId)
        assertNull(tx.destinationAccountId)
        assertEquals("Weekly supermarket run", tx.note)
        assertTrue(transactionRepository.getTransactionsByAccount(legacyAccountId).isEmpty())
    }

    @Test
    fun `successfully save income transaction persists to internal default account`() = runTest(testDispatcher) {
        val legacyAccountId = accountRepository.insertAccount(
            Account(name = "Legacy Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        val defaultAccountId = insertDefaultAccount()
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTransactionTypeChanged(TransactionType.INCOME)
        advanceUntilIdle()

        viewModel.onCategorySelected(categoryId)
        viewModel.onAmountChanged("3500.00")
        viewModel.onNoteChanged("Monthly salary payment")
        viewModel.saveTransaction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val transactions = transactionRepository.getTransactionsByAccount(defaultAccountId)
        assertEquals(1, transactions.size)
        val tx = transactions.first()
        assertEquals(defaultAccountId, tx.accountId)
        assertEquals(350000L, tx.amount.amountInMinorUnits)
        assertEquals("USD", tx.amount.currencyCode)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(categoryId, tx.categoryId)
        assertNull(tx.destinationAccountId)
        assertEquals("Monthly salary payment", tx.note)
        assertTrue(transactionRepository.getTransactionsByAccount(legacyAccountId).isEmpty())
    }

    @Test
    fun `caller-supplied account ID cannot override internal default`() = runTest(testDispatcher) {
        val callerAccountId = accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(50000L, "USD"))
        )
        val internalDefaultId = insertDefaultAccount()
        val expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )
        val incomeCategoryId = categoryRepository.insertCategory(
            Category(name = "Bonus", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        createTransactionUseCase(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1200L, "USD"),
                accountId = callerAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
        )
        createTransactionUseCase(
            Transaction(
                type = TransactionType.INCOME,
                amount = Money(5000L, "USD"),
                accountId = callerAccountId,
                categoryId = incomeCategoryId,
                transactionTime = Instant.now()
            )
        )

        assertTrue(transactionRepository.getTransactionsByAccount(callerAccountId).isEmpty())
        val defaultTransactions = transactionRepository.getTransactionsByAccount(internalDefaultId)
        assertEquals(2, defaultTransactions.size)
        assertTrue(defaultTransactions.all { it.accountId == internalDefaultId })
    }

    @Test
    fun `save expense without category sets error and creates no record`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        // No categories inserted

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("50.00")
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertEquals(AddTransactionError.CategoryRequired, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(defaultAccountId).isEmpty())
    }

    @Test
    fun `dismiss error clears error state`() = runTest(testDispatcher) {
        insertDefaultAccount()
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
        val defaultAccountId = insertDefaultAccount()
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#FF5722")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChanged("10.00")
        viewModel.onCategorySelected(categoryId)

        // Trigger first save
        viewModel.saveTransaction()
        // Immediately trigger second save before dispatcher advances
        viewModel.saveTransaction()
        advanceUntilIdle()

        // Only 1 transaction should have been inserted
        val transactions = transactionRepository.getTransactionsByAccount(defaultAccountId)
        assertEquals(1, transactions.size)
    }

    @Test
    fun `internal default account currency is used for display and persistence`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Legacy USD", type = AccountType.BANK, initialBalance = Money(10000L, "USD"))
        )
        val defaultAccountId = insertDefaultAccount(currencyCode = "UZS", initialBalanceMinor = 0L)
        categoryRepository.insertCategory(
            Category(name = "Transport", type = CategoryType.EXPENSE, iconName = "directions_bus", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("UZS", viewModel.uiState.value.currencyCode)
        viewModel.onAmountChanged("15000")
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        val saved = transactionRepository.getTransactionsByAccount(defaultAccountId).single()
        assertEquals("UZS", saved.amount.currencyCode)
        assertEquals(15000L, saved.amount.amountInMinorUnits)
    }

    @Test
    fun `populateFromCandidate pre-populates supported fields for expense without auto-saving`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        accountRepository.insertAccount(
            Account(name = "Main Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Taxi & Transport", type = CategoryType.EXPENSE, iconName = "local_taxi", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Paid 25 USD for taxi",
            suggestedTitle = "Taxi ride to office",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            date = LocalDate.of(2026, 11, 10),
            suggestedAccountName = "Main Bank",
            suggestedCategoryName = "Taxi & Transport",
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAiAssisted)
        assertEquals(TransactionType.EXPENSE, state.transactionType)
        assertEquals("25.00", state.amount)
        assertEquals("Taxi ride to office", state.note)
        assertEquals(defaultAccountId, state.defaultAccount?.id)
        assertEquals(categoryId, state.selectedCategoryId)
        assertEquals(CandidateSource.OFFLINE_PARSER, state.candidateSource)
        assertNull(state.aiAmountWarning)
        assertFalse(state.aiCategoryMatchAmbiguous)
        assertFalse(state.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(defaultAccountId).isEmpty())
    }

    @Test
    fun `populateFromCandidate preserves Gemini source and fallback warning`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val candidate = TransactionCandidate(
            rawText = "Paid 25 for taxi",
            suggestedTitle = "Taxi",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            date = LocalDate.of(2026, 11, 10),
            confidence = ParseConfidence.HIGH,
            warnings = listOf(ParseWarning.INVALID_API_KEY),
            source = CandidateSource.OFFLINE_PARSER
        )

        viewModel.populateFromCandidate(candidate)

        assertEquals(CandidateSource.OFFLINE_PARSER, viewModel.uiState.value.candidateSource)
        assertTrue(viewModel.uiState.value.aiSourceWarnings.contains(ParseWarning.INVALID_API_KEY))
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
    }

    @Test
    fun `populateFromCandidate handles missing amount safely and prevents saving`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Coffee", type = CategoryType.EXPENSE, iconName = "local_cafe", colorHex = "#EF4444")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Bought coffee",
            suggestedTitle = "Coffee at cafe",
            transactionType = TransactionType.EXPENSE,
            amount = null,
            date = LocalDate.now(),
            suggestedAccountName = "Checking",
            suggestedCategoryName = "Coffee",
            confidence = ParseConfidence.LOW,
            warnings = listOf(ParseWarning.MISSING_AMOUNT)
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertTrue(state.isAiAssisted)
        assertEquals("", state.amount)
        assertNotNull(state.aiAmountWarning)

        viewModel.saveTransaction()
        advanceUntilIdle()

        assertEquals(AddTransactionError.InvalidAmount, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(defaultAccountId).isEmpty())
    }

    @Test
    fun `populateFromCandidate exact category matching preselects category`() = runTest(testDispatcher) {
        insertDefaultAccount()
        val groceriesId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        categoryRepository.insertCategory(
            Category(name = "Utilities", type = CategoryType.EXPENSE, iconName = "bolt", colorHex = "#F59E0B")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Supermarket 50",
            suggestedTitle = "Supermarket",
            transactionType = TransactionType.EXPENSE,
            amount = Money(5000L, "USD"),
            date = LocalDate.now(),
            suggestedCategoryName = "groceries",
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertEquals(groceriesId, state.selectedCategoryId)
        assertFalse(state.aiCategoryMatchAmbiguous)
    }

    @Test
    fun `populateFromCandidate ambiguous category matching requires manual selection`() = runTest(testDispatcher) {
        insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Food & Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        categoryRepository.insertCategory(
            Category(name = "Food Dining", type = CategoryType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Food 30",
            suggestedTitle = "Food",
            transactionType = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            date = LocalDate.now(),
            suggestedCategoryName = "Food",
            confidence = ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertNull(state.selectedCategoryId)
        assertTrue(state.aiCategoryMatchAmbiguous)
        assertEquals("Food", state.aiCategorySuggestion)
    }

    @Test
    fun `populateFromCandidate unmatched category leaves category unselected with suggestion`() = runTest(testDispatcher) {
        insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Flight ticket 300",
            suggestedTitle = "Flight ticket",
            transactionType = TransactionType.EXPENSE,
            amount = Money(30000L, "USD"),
            date = LocalDate.now(),
            suggestedCategoryName = "Travel & Flights",
            confidence = ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        val state = viewModel.uiState.value
        assertNull(state.selectedCategoryId)
        assertFalse(state.aiCategoryMatchAmbiguous)
        assertEquals("Travel & Flights", state.aiCategorySuggestion)
    }

    @Test
    fun `populateFromCandidate ignores AI account suggestions and keeps internal default account`() = runTest(testDispatcher) {
        val cashWalletId = accountRepository.insertAccount(
            Account(name = "Cash Wallet", type = AccountType.CASH, initialBalance = Money(50000L, "USD"))
        )
        val defaultAccountId = insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Snacks", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Snack from cash wallet",
            suggestedTitle = "Snack",
            transactionType = TransactionType.EXPENSE,
            amount = Money(500L, "USD"),
            date = LocalDate.now(),
            suggestedAccountName = "Cash Wallet",
            suggestedCategoryName = "Snacks",
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        assertEquals(defaultAccountId, viewModel.uiState.value.defaultAccount?.id)

        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        assertEquals(1, transactionRepository.getTransactionsByAccount(defaultAccountId).size)
        assertTrue(transactionRepository.getTransactionsByAccount(cashWalletId).isEmpty())
    }

    @Test
    fun `populateFromCandidate ignores ambiguous AI account name and keeps internal default account`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Card Visa", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        accountRepository.insertAccount(
            Account(name = "Card Mastercard", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val defaultAccountId = insertDefaultAccount()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Paid 20 with card",
            suggestedTitle = "Item",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2000L, "USD"),
            date = LocalDate.now(),
            suggestedAccountName = "Card",
            confidence = ParseConfidence.MEDIUM
        )

        viewModel.populateFromCandidate(candidate)

        assertEquals(defaultAccountId, viewModel.uiState.value.defaultAccount?.id)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `populateFromCandidate rejects AI transfer candidate without converting or saving`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        val savingsId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Transfer 200 from Internal Default to Savings",
            suggestedTitle = "Savings transfer",
            transactionType = TransactionType.TRANSFER,
            amount = Money(20000L, "USD"),
            date = LocalDate.now(),
            transferSourceAccountName = "Internal Default",
            transferDestinationAccountName = "Savings",
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAiAssisted)
        assertFalse(state.isSavedSuccessfully)
        assertNotNull(state.error)
        assertEquals("", state.amount)
        assertEquals("", state.note)
        assertEquals(TransactionType.EXPENSE, state.transactionType)

        // Attempting to save after rejection must still not create any transaction
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(defaultAccountId).isEmpty())
        assertTrue(transactionRepository.getTransactionsByAccount(savingsId).isEmpty())
    }

    @Test
    fun `populateFromCandidate resolves date to start of day instant`() = runTest(testDispatcher) {
        val testDate = LocalDate.of(2026, 11, 5)
        val expectedInstant = testDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Lunch on Nov 5",
            suggestedTitle = "Lunch",
            transactionType = TransactionType.EXPENSE,
            amount = Money(1500L, "USD"),
            date = testDate,
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)

        assertEquals(expectedInstant, viewModel.uiState.value.transactionTime)
    }

    @Test
    fun `clearAiDraftState removes AI-assisted flags and warnings`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Draft",
            suggestedTitle = "Draft",
            transactionType = TransactionType.EXPENSE,
            amount = null,
            date = LocalDate.now(),
            suggestedAccountName = "Ambiguous Account",
            suggestedCategoryName = "Unknown Category",
            confidence = ParseConfidence.LOW,
            warnings = listOf(ParseWarning.MISSING_AMOUNT, ParseWarning.AI_FALLBACK)
        )

        viewModel.populateFromCandidate(candidate)
        assertTrue(viewModel.uiState.value.isAiAssisted)

        viewModel.clearAiDraftState()
        val cleared = viewModel.uiState.value
        assertFalse(cleared.isAiAssisted)
        assertNull(cleared.aiAmountWarning)
        assertNull(cleared.aiCategorySuggestion)
        assertFalse(cleared.aiCategoryMatchAmbiguous)
        assertTrue(cleared.aiWarnings.isEmpty())
        assertTrue(cleared.aiSourceWarnings.isEmpty())
        assertNull(cleared.candidateSource)
    }

    @Test
    fun `clearForm resets state to clean manual transaction entry`() = runTest(testDispatcher) {
        val defaultAccountId = insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Dinner 80",
            suggestedTitle = "Dinner",
            transactionType = TransactionType.EXPENSE,
            amount = Money(8000L, "USD"),
            date = LocalDate.of(2026, 11, 1),
            confidence = ParseConfidence.HIGH
        )

        viewModel.populateFromCandidate(candidate)
        assertEquals("80.00", viewModel.uiState.value.amount)
        assertTrue(viewModel.uiState.value.isAiAssisted)

        viewModel.clearForm()

        val state = viewModel.uiState.value
        assertFalse(state.isAiAssisted)
        assertEquals("", state.amount)
        assertEquals("", state.note)
        assertEquals(defaultAccountId, state.defaultAccount?.id)
    }

    @Test
    fun `AI expense candidate follows Preview Edit Confirm Save and persists to internal default account`() = runTest(testDispatcher) {
        val legacyBankId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val internalDefaultId = insertDefaultAccount()
        val cat1 = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )
        val cat2 = categoryRepository.insertCategory(
            Category(name = "Transport", type = CategoryType.EXPENSE, iconName = "directions_bus", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Food 20",
            suggestedTitle = "Food",
            transactionType = TransactionType.EXPENSE,
            amount = Money(2000L, "USD"),
            date = LocalDate.now(),
            suggestedAccountName = "Bank",
            suggestedCategoryName = "Food",
            confidence = ParseConfidence.HIGH,
            source = CandidateSource.GEMINI_AI
        )

        // Preview / Pre-populate: never auto-saves
        viewModel.populateFromCandidate(candidate)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSavedSuccessfully)
        assertEquals(internalDefaultId, viewModel.uiState.value.defaultAccount?.id)
        assertEquals(cat1, viewModel.uiState.value.selectedCategoryId)
        assertEquals("20.00", viewModel.uiState.value.amount)
        assertTrue(transactionRepository.getTransactionsByAccount(internalDefaultId).isEmpty())

        // Edit
        viewModel.onAmountChanged("35.50")
        viewModel.onCategorySelected(cat2)
        viewModel.onNoteChanged("Transport for party")

        val updatedState = viewModel.uiState.value
        assertEquals("35.50", updatedState.amount)
        assertEquals(cat2, updatedState.selectedCategoryId)
        assertEquals("Transport for party", updatedState.note)

        // Confirm & Save
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        val txs = transactionRepository.getTransactionsByAccount(internalDefaultId)
        assertEquals(1, txs.size)
        assertEquals(internalDefaultId, txs.first().accountId)
        assertEquals(TransactionType.EXPENSE, txs.first().type)
        assertEquals(3550L, txs.first().amount.amountInMinorUnits)
        assertEquals(cat2, txs.first().categoryId)
        assertEquals("Transport for party", txs.first().note)
        assertTrue(transactionRepository.getTransactionsByAccount(legacyBankId).isEmpty())
    }

    @Test
    fun `AI income candidate follows Preview Edit Confirm Save and persists to internal default account`() = runTest(testDispatcher) {
        val legacyAccountId = accountRepository.insertAccount(
            Account(name = "Payroll Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val internalDefaultId = insertDefaultAccount()
        categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981")
        )
        val salaryCatId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )
        val freelanceCatId = categoryRepository.insertCategory(
            Category(name = "Freelance", type = CategoryType.INCOME, iconName = "laptop", colorHex = "#3B82F6")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val candidate = TransactionCandidate(
            rawText = "Received 1200 salary to Payroll Bank",
            suggestedTitle = "Salary deposit",
            transactionType = TransactionType.INCOME,
            amount = Money(120000L, "USD"),
            date = LocalDate.of(2026, 11, 10),
            suggestedAccountName = "Payroll Bank",
            suggestedCategoryName = "Salary",
            confidence = ParseConfidence.HIGH,
            source = CandidateSource.GEMINI_AI
        )

        // Preview / Pre-populate: switches to INCOME and matches income category without saving
        viewModel.populateFromCandidate(candidate)
        advanceUntilIdle()

        val previewState = viewModel.uiState.value
        assertTrue(previewState.isAiAssisted)
        assertEquals(TransactionType.INCOME, previewState.transactionType)
        assertEquals("1200.00", previewState.amount)
        assertEquals(salaryCatId, previewState.selectedCategoryId)
        assertEquals(internalDefaultId, previewState.defaultAccount?.id)
        assertFalse(previewState.isSavedSuccessfully)
        assertTrue(transactionRepository.getTransactionsByAccount(internalDefaultId).isEmpty())

        // Edit
        viewModel.onAmountChanged("1350.00")
        viewModel.onCategorySelected(freelanceCatId)
        viewModel.onNoteChanged("Updated consulting payout")

        // Confirm & Save
        viewModel.saveTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)
        val txs = transactionRepository.getTransactionsByAccount(internalDefaultId)
        assertEquals(1, txs.size)
        val saved = txs.first()
        assertEquals(internalDefaultId, saved.accountId)
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(135000L, saved.amount.amountInMinorUnits)
        assertEquals(freelanceCatId, saved.categoryId)
        assertEquals("Updated consulting payout", saved.note)
        assertTrue(transactionRepository.getTransactionsByAccount(legacyAccountId).isEmpty())
    }
}
