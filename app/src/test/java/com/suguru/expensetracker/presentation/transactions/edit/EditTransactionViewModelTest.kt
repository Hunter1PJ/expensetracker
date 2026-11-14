package com.suguru.expensetracker.presentation.transactions.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.UpdateTransactionUseCase
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionError
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EditTransactionViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var getTransactionUseCase: GetTransactionUseCase
    private lateinit var updateTransactionUseCase: UpdateTransactionUseCase
    private lateinit var observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        getTransactionUseCase = GetTransactionUseCase(transactionRepository)
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
        observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(categoryRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(txId: Long): EditTransactionViewModel {
        return EditTransactionViewModel(
            transactionId = txId,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    @Test
    fun `loads initial transaction data into edit form`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Whole Foods"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isTransactionLoaded)
        assertFalse(state.isReadOnlyTransfer)
        assertEquals("45.00", state.amount)
        assertEquals("USD", state.currencyCode)
        assertEquals(catId, state.selectedCategoryId)
        assertEquals("Whole Foods", state.note)
    }

    @Test
    fun `updating historical expense preserves non-default persisted accountId`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(
                name = "Internal Default",
                type = AccountType.CASH,
                initialBalance = Money(0L, "USD"),
                internalKey = "default"
            )
        )
        val historicalAccId = accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val cat1 = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val cat2 = categoryRepository.insertCategory(
            Category(name = "Dining", type = CategoryType.EXPENSE, iconName = "restaurant", colorHex = "#111111")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = historicalAccId,
                categoryId = cat1,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Whole Foods"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        viewModel.onAmountChanged("60.00")
        viewModel.onCategorySelected(cat2)
        viewModel.onNoteChanged("Trader Joe's")

        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)

        val updatedTx = transactionRepository.getTransactionById(txId)
        assertEquals(historicalAccId, updatedTx?.accountId)
        assertEquals(TransactionType.EXPENSE, updatedTx?.type)
        assertEquals(6000L, updatedTx?.amount?.amountInMinorUnits)
        assertEquals("USD", updatedTx?.amount?.currencyCode)
        assertEquals(cat2, updatedTx?.categoryId)
        assertEquals("Trader Joe's", updatedTx?.note)
    }

    @Test
    fun `updating historical income preserves non-default persisted accountId and currency`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(
                name = "Internal Default",
                type = AccountType.CASH,
                initialBalance = Money(0L, "USD"),
                internalKey = "default"
            )
        )
        val eurAccId = accountRepository.insertAccount(
            Account(name = "Euro Bank", type = AccountType.BANK, initialBalance = Money(50000L, "EUR"))
        )
        val salaryCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(250000L, "EUR"),
                type = TransactionType.INCOME,
                accountId = eurAccId,
                categoryId = salaryCat,
                transactionTime = Instant.parse("2026-09-05T09:00:00Z"),
                note = "September pay"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        assertEquals("EUR", viewModel.uiState.value.currencyCode)
        viewModel.onAmountChanged("2750.50")
        viewModel.onNoteChanged("Bonus included")

        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)
        val updatedTx = transactionRepository.getTransactionById(txId)
        assertEquals(eurAccId, updatedTx?.accountId)
        assertEquals(TransactionType.INCOME, updatedTx?.type)
        assertEquals(275050L, updatedTx?.amount?.amountInMinorUnits)
        assertEquals("EUR", updatedTx?.amount?.currencyCode)
        assertEquals("Bonus included", updatedTx?.note)
    }

    @Test
    fun `historical transfer loads as read-only and cannot be converted or saved`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val destId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(12500L, "USD"),
                type = TransactionType.TRANSFER,
                accountId = sourceId,
                destinationAccountId = destId,
                transactionTime = Instant.parse("2026-09-02T12:00:00Z"),
                note = "Monthly transfer"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertTrue(initialState.isReadOnlyTransfer)
        assertEquals("Checking", initialState.transferFromAccountName)
        assertEquals("Savings", initialState.transferToAccountName)
        assertEquals("$125.00", initialState.transferAmountFormatted)

        // Attempt conversion and edits
        viewModel.onTransactionTypeChanged(TransactionType.EXPENSE)
        viewModel.onAmountChanged("99.00")
        viewModel.onNoteChanged("Hacked note")
        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertFalse(onSuccessCalled)
        assertEquals(TransactionType.TRANSFER, viewModel.uiState.value.transactionType)
        assertTrue(viewModel.uiState.value.error is AddTransactionError.Unknown)

        val unchangedTx = transactionRepository.getTransactionById(txId)
        assertEquals(TransactionType.TRANSFER, unchangedTx?.type)
        assertEquals(12500L, unchangedTx?.amount?.amountInMinorUnits)
        assertEquals("Monthly transfer", unchangedTx?.note)
        assertEquals(sourceId, unchangedTx?.accountId)
        assertEquals(destId, unchangedTx?.destinationAccountId)
    }

    @Test
    fun `missing persisted account reference triggers AccountRequired domain error`() = runTest(testDispatcher) {
        val missingAccountId = 999L
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = missingAccountId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Orphaned account tx"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        viewModel.onAmountChanged("50.00")
        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertFalse(onSuccessCalled)
        assertEquals(AddTransactionError.AccountRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `archived persisted account reference triggers AccountArchived domain error`() = runTest(testDispatcher) {
        val archivedAccId = accountRepository.insertAccount(
            Account(
                name = "Old Checking",
                type = AccountType.BANK,
                initialBalance = Money(100000L, "USD"),
                isArchived = true
            )
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = archivedAccId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Archived account tx"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        viewModel.onAmountChanged("50.00")
        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertFalse(onSuccessCalled)
        assertEquals(AddTransactionError.AccountArchived, viewModel.uiState.value.error)
    }

    @Test
    fun `edit transaction screen hides account selectors and transfer tab for expense`() {
        val cat = Category(id = 1L, name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#10B981")
        val state = EditTransactionUiState(
            transactionId = 1L,
            transactionType = TransactionType.EXPENSE,
            amount = "45.00",
            currencyCode = "USD",
            selectedCategoryId = 1L,
            note = "Whole Foods",
            categories = listOf(cat),
            isLoading = false,
            isTransactionLoaded = true,
            isReadOnlyTransfer = false
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                EditTransactionScreenContent(
                    uiState = state,
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
        composeTestRule.onNodeWithTag("edit_transfer_readonly_card").assertDoesNotExist()

        composeTestRule.onNodeWithTag("type_tab_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_tab_income").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_input_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_selector").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `edit transaction screen renders read-only transfer card without save or conversion controls`() {
        val state = EditTransactionUiState(
            transactionId = 2L,
            transactionType = TransactionType.TRANSFER,
            amount = "125.00",
            currencyCode = "USD",
            note = "Monthly transfer",
            isLoading = false,
            isTransactionLoaded = true,
            isReadOnlyTransfer = true,
            transferFromAccountName = "Checking",
            transferToAccountName = "Savings",
            transferAmountFormatted = "$125.00"
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                EditTransactionScreenContent(
                    uiState = state,
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

        composeTestRule.onNodeWithTag("edit_transfer_readonly_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("$125.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_type_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("amount_input_field").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_transaction_button").assertDoesNotExist()
    }
}
