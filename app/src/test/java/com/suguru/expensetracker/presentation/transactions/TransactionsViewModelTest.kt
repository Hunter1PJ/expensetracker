package com.suguru.expensetracker.presentation.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.suguru.expensetracker.domain.usecase.account.ObserveAllAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveAllCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ObserveTransactionsUseCase
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
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
class TransactionsViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `initial state loads transactions grouped by date without account names on expense subtitle`() = runTest(testDispatcher) {
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
        val tx2 = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T12:00:00Z"),
                note = null
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasAnyTransactionsInDb)
        assertEquals(1, state.groupedTransactions.size)
        val items = state.groupedTransactions[0].transactions
        assertEquals(2, items.size)
        assertEquals(tx2, items[0].id)
        assertEquals("", items[0].subtitle)
        assertEquals(tx1, items[1].id)
        assertEquals("Lunch", items[1].subtitle)
        assertFalse(items.any { it.subtitle.contains("Checking") })

        job.cancel()
    }

    @Test
    fun `search query filters by note, category, and type while ignoring account names`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "SecretVaultChecking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val groceriesCat = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val salaryCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#00FF00")
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = groceriesCat,
                transactionTime = Instant.parse("2026-09-01T10:00:00Z"),
                note = "Supermarket groceries"
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = groceriesCat,
                transactionTime = Instant.parse("2026-09-01T11:00:00Z"),
                note = "Taxi fare"
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(300000L, "USD"),
                type = TransactionType.INCOME,
                accountId = accId,
                categoryId = salaryCat,
                transactionTime = Instant.parse("2026-09-01T12:00:00Z"),
                note = null
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // Search by note
        viewModel.onSearchQueryChanged("Taxi")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })

        // Search by category
        viewModel.onSearchQueryChanged("Salary")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })

        // Search by type
        viewModel.onSearchQueryChanged("INCOME")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })

        // Search by account name should NOT match ordinary transactions
        viewModel.onSearchQueryChanged("SecretVaultChecking")
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })

        job.cancel()
    }

    @Test
    fun `type and category filters preserve income and historical transfer visibility`() = runTest(testDispatcher) {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val destId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(200000L, "USD"))
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
                accountId = sourceId,
                categoryId = expCat,
                transactionTime = Instant.parse("2026-09-01T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(500000L, "USD"),
                type = TransactionType.INCOME,
                accountId = sourceId,
                categoryId = incCat,
                transactionTime = Instant.parse("2026-09-01T12:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(25000L, "USD"),
                type = TransactionType.TRANSFER,
                accountId = sourceId,
                destinationAccountId = destId,
                transactionTime = Instant.parse("2026-09-01T14:00:00Z"),
                note = "Monthly savings move"
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // All types visible initially including historical transfer
        assertEquals(3, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })

        // Category filter works
        viewModel.onCategoryFilterChanged(expCat)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.groupedTransactions.sumOf { it.transactions.size })
        assertEquals("Food", viewModel.uiState.value.groupedTransactions[0].transactions[0].title)

        // Clear and filter to TRANSFER: historical Transfer remains visible
        viewModel.clearFilters()
        viewModel.onTypeFilterChanged(TransactionTypeFilter.TRANSFER)
        advanceUntilIdle()
        val transferState = viewModel.uiState.value
        assertEquals(1, transferState.groupedTransactions.sumOf { it.transactions.size })
        val transferRow = transferState.groupedTransactions[0].transactions[0]
        assertEquals(TransactionType.TRANSFER, transferRow.type)
        assertEquals("Checking → Savings", transferRow.title)
        assertEquals("Monthly savings move", transferRow.subtitle)

        // Income filter
        viewModel.onTypeFilterChanged(TransactionTypeFilter.INCOME)
        advanceUntilIdle()
        val incomeState = viewModel.uiState.value
        assertEquals(TransactionTypeFilter.INCOME, incomeState.selectedType)
        assertEquals(1, incomeState.groupedTransactions.sumOf { it.transactions.size })
        assertEquals("Salary", incomeState.groupedTransactions[0].transactions[0].title)

        job.cancel()
    }

    @Test
    fun `transactions screen renders without account filter chips and hides ordinary account names`() {
        val state = TransactionsUiState(
            isLoading = false,
            hasAnyTransactionsInDb = true,
            categories = listOf(
                Category(id = 1L, name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#10B981")
            ),
            groupedTransactions = listOf(
                TransactionDateGroupUiModel(
                    date = LocalDate.of(2026, 9, 1),
                    headerTitle = "Sep 1, 2026",
                    transactions = listOf(
                        TransactionListItemUiModel(
                            id = 10L,
                            type = TransactionType.EXPENSE,
                            title = "Groceries",
                            subtitle = "Weekly run",
                            formattedAmount = "- $25.00",
                            isPositive = false,
                            timeFormatted = "10:00",
                            iconName = "cart",
                            colorHex = "#10B981",
                            rawInstant = Instant.parse("2026-09-01T10:00:00Z"),
                            rawDate = LocalDate.of(2026, 9, 1)
                        ),
                        TransactionListItemUiModel(
                            id = 11L,
                            type = TransactionType.TRANSFER,
                            title = "Checking → Savings",
                            subtitle = "Historical move",
                            formattedAmount = "$100.00",
                            isPositive = null,
                            timeFormatted = "11:00",
                            iconName = null,
                            colorHex = null,
                            rawInstant = Instant.parse("2026-09-01T11:00:00Z"),
                            rawDate = LocalDate.of(2026, 9, 1)
                        )
                    )
                )
            )
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                TransactionsScreenContent(
                    uiState = state,
                    onSearchQueryChanged = {},
                    onTypeFilterChanged = {},
                    onCategoryFilterChanged = {},
                    onClearFilters = {},
                    onNavigateToDetail = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("account_filter_all").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_filter_1").assertDoesNotExist()
        composeTestRule.onNodeWithText("All Accounts").assertDoesNotExist()

        composeTestRule.onAllNodesWithTag("add_transaction_fab").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("empty_add_transaction_button").assertCountEquals(0)
        composeTestRule.onNodeWithTag("category_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_filter_transfer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_row_10").assertIsDisplayed()
        composeTestRule.onNodeWithText("- $25.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weekly run").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_row_11").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Checking → Savings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyTransactionsScreenShowsInformativeStateWithoutCreationAction() {
        composeTestRule.setContent {
            ExpenseTrackerTheme {
                TransactionsScreenContent(
                    uiState = TransactionsUiState(
                        isLoading = false,
                        hasAnyTransactionsInDb = false
                    ),
                    onSearchQueryChanged = {},
                    onTypeFilterChanged = {},
                    onCategoryFilterChanged = {},
                    onClearFilters = {},
                    onNavigateToDetail = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("empty_transactions_state").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("add_transaction_fab").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("empty_add_transaction_button").assertCountEquals(0)
        composeTestRule.onNodeWithText("Add Transaction").assertDoesNotExist()
    }
}
