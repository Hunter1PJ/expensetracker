package com.suguru.expensetracker.presentation.transactions.detail

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
import com.suguru.expensetracker.domain.usecase.transaction.DeleteTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TransactionDetailViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var getTransactionUseCase: GetTransactionUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        getTransactionUseCase = GetTransactionUseCase(transactionRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
        deleteTransactionUseCase = DeleteTransactionUseCase(transactionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(txId: Long): TransactionDetailViewModel {
        return TransactionDetailViewModel(
            transactionId = txId,
            getTransactionUseCase = getTransactionUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            zoneId = zoneId
        )
    }

    @Test
    fun `loads expense details without exposing account information`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Weekly groceries"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.transaction)
        assertNull(state.sourceAccount)
        assertNull(state.destinationAccount)
        assertTrue(state.canEdit)
        assertEquals("Groceries", state.category?.name)
        assertEquals("Weekly groceries", state.note)
        assertEquals("- $25.00", state.amountFormatted)
    }

    @Test
    fun `loads historical transfer as read-only with source and destination context`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val destId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(200000L, "USD"))
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(15000L, "USD"),
                type = TransactionType.TRANSFER,
                accountId = sourceId,
                destinationAccountId = destId,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z"),
                note = "Emergency fund"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isTransfer)
        assertFalse(state.canEdit)
        assertEquals("Checking", state.sourceAccount?.name)
        assertEquals("Savings", state.destinationAccount?.name)
        assertEquals("$150.00", state.amountFormatted)
    }

    @Test
    fun `expense detail UI hides account row and preserves category, currency, edit, and delete`() {
        val tx = Transaction(
            id = 1L,
            amount = Money(2500L, "USD"),
            type = TransactionType.EXPENSE,
            accountId = 10L,
            categoryId = 20L,
            transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
            note = "Weekly groceries"
        )
        val state = TransactionDetailUiState(
            isLoading = false,
            transaction = tx,
            type = TransactionType.EXPENSE,
            amountFormatted = "- $25.00",
            currencyCode = "USD",
            sourceAccount = null,
            destinationAccount = null,
            category = Category(id = 20L, name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#10B981"),
            note = "Weekly groceries",
            dateTimeFormatted = "Sep 01, 2026, 15:00"
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                TransactionDetailScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onNavigateToEdit = {},
                    onDeleteTransaction = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("detail_source_account").assertDoesNotExist()
        composeTestRule.onNodeWithTag("detail_dest_account").assertDoesNotExist()
        composeTestRule.onNodeWithTag("detail_transfer_readonly_banner").assertDoesNotExist()

        composeTestRule.onNodeWithTag("edit_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_amount_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("- $25.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_category").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_date_time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_note").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_edit_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `historical transfer detail UI shows read-only accounts and hides edit actions`() {
        val tx = Transaction(
            id = 2L,
            amount = Money(15000L, "USD"),
            type = TransactionType.TRANSFER,
            accountId = 10L,
            destinationAccountId = 11L,
            transactionTime = Instant.parse("2026-09-02T10:00:00Z"),
            note = "Emergency fund"
        )
        val state = TransactionDetailUiState(
            isLoading = false,
            transaction = tx,
            type = TransactionType.TRANSFER,
            amountFormatted = "$150.00",
            currencyCode = "USD",
            sourceAccount = Account(id = 10L, name = "Checking", type = AccountType.BANK, initialBalance = Money(0L, "USD")),
            destinationAccount = Account(id = 11L, name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(0L, "USD")),
            category = null,
            note = "Emergency fund",
            dateTimeFormatted = "Sep 02, 2026, 10:00"
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                TransactionDetailScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onNavigateToEdit = {},
                    onDeleteTransaction = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("edit_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("detail_edit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_source_account").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_dest_account").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Checking").assertIsDisplayed()
        composeTestRule.onNodeWithText("Savings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_transfer_readonly_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `deleting transaction successfully triggers callback and deletes from repo`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Weekly groceries"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        var onSuccessCalled = false
        viewModel.deleteTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)
        assertTrue(transactionRepository.getTransactionById(txId) == null)
    }
}
