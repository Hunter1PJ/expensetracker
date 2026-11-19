package com.suguru.expensetracker.presentation.recurring

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeRecurringTransactionRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.RecurringOccurrenceRepository
import com.suguru.expensetracker.domain.usecase.account.ObserveAllAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveAllCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.recurring.DeactivateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.ObserveRecurringTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RecurringTransactionsViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var recurringRepository: FakeRecurringTransactionRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var viewModel: RecurringTransactionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringRepository = FakeRecurringTransactionRepository()
        transactionRepository = FakeTransactionRepository()

        val fakeOccurrenceRepository = object : RecurringOccurrenceRepository {
            override suspend fun isOccurrenceProcessed(ruleId: Long, occurrenceDate: LocalDate) = false
            override suspend fun processAtomicOccurrence(
                rule: RecurringTransaction,
                occurrenceDate: LocalDate,
                transactionToInsert: com.suguru.expensetracker.domain.model.Transaction,
                newNextOccurrence: LocalDate,
                isRuleNowActive: Boolean
            ): Long? = null
            override suspend fun markTransactionDeleted(ruleId: Long, occurrenceDate: LocalDate) {}
        }

        viewModel = RecurringTransactionsViewModel(
            observeRecurringTransactionsUseCase = ObserveRecurringTransactionsUseCase(recurringRepository),
            observeAllAccountsUseCase = ObserveAllAccountsUseCase(accountRepository),
            observeAllCategoriesUseCase = ObserveAllCategoriesUseCase(categoryRepository),
            deactivateRecurringTransactionUseCase = DeactivateRecurringTransactionUseCase(recurringRepository),
            processDueRecurringTransactionsUseCase = ProcessDueRecurringTransactionsUseCase(
                recurringTransactionRepository = recurringRepository,
                recurringOccurrenceRepository = fakeOccurrenceRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list hides account names and labels while preserving recurring rule metadata`() = runTest(testDispatcher) {
        val defaultAccId = accountRepository.insertAccount(
            Account(
                name = "Internal Default Wallet",
                type = AccountType.CASH,
                initialBalance = Money(0L, "USD"),
                internalKey = "default"
            )
        )
        val subscriptionsId = categoryRepository.insertCategory(
            Category(name = "Subscriptions", type = CategoryType.EXPENSE, iconName = "tv", colorHex = "#6366F1")
        )
        val salaryId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val expenseId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(1599L, "USD"),
                accountId = defaultAccId,
                categoryId = subscriptionsId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 12, 1),
                note = "Streaming Plan",
                isActive = true
            )
        )
        val incomeId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(350000L, "USD"),
                accountId = defaultAccId,
                categoryId = salaryId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 12, 5),
                note = null,
                isActive = true
            )
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                RecurringTransactionsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToAddRule = {},
                    onNavigateToEditRule = {}
                )
            }
        }

        advanceUntilIdle()
        composeTestRule.waitForIdle()

        // Useful recurring metadata is displayed
        composeTestRule.onNodeWithTag("rule_card_$expenseId").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streaming Plan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subscriptions").assertIsDisplayed()
        composeTestRule.onNodeWithText("$15.99").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_menu_button_$expenseId").assertIsDisplayed()

        composeTestRule.onNodeWithTag("recurring_rules_list").performScrollToKey(incomeId)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rule_card_$incomeId").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salary").assertIsDisplayed()
        composeTestRule.onNodeWithText("$3500.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rule_menu_button_$incomeId").assertIsDisplayed()

        // Account names must NEVER appear
        composeTestRule.onAllNodesWithText("Internal Default Wallet", substring = true)
            .let { nodes -> assertEquals(0, nodes.fetchSemanticsNodes().size) }
        composeTestRule.onAllNodesWithText("Unknown Account", substring = true)
            .let { nodes -> assertEquals(0, nodes.fetchSemanticsNodes().size) }
    }

    @Test
    fun `historical transfer rule is displayed as paused read-only without edit or pause actions or account names`() = runTest(testDispatcher) {
        val checkingId = accountRepository.insertAccount(
            Account(name = "Secret Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val savingsId = accountRepository.insertAccount(
            Account(name = "Secret Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )

        val transferId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(25000L, "USD"),
                accountId = checkingId,
                destinationAccountId = savingsId,
                categoryId = null,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 12, 1),
                note = "Auto Save",
                isActive = false
            )
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                RecurringTransactionsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToAddRule = {},
                    onNavigateToEditRule = {}
                )
            }
        }

        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rule_card_$transferId").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paused • Read-only historical transfer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read-only").assertIsDisplayed()
        composeTestRule.onNodeWithText("$250.00").assertIsDisplayed()

        // No account names or arrows
        composeTestRule.onAllNodesWithText("Secret Checking", substring = true)
            .let { nodes -> assertEquals(0, nodes.fetchSemanticsNodes().size) }
        composeTestRule.onAllNodesWithText("Secret Savings", substring = true)
            .let { nodes -> assertEquals(0, nodes.fetchSemanticsNodes().size) }

        // No edit/pause/reactivate menu on historical Transfer cards
        composeTestRule.onNodeWithTag("rule_menu_button_$transferId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rule_edit_item_$transferId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rule_pause_item_$transferId").assertDoesNotExist()
    }

    @Test
    fun `missing or archived account never leaks account error wording in statusDetail`() = runTest(testDispatcher) {
        val archivedAccId = accountRepository.insertAccount(
            Account(
                name = "Archived Legacy",
                type = AccountType.BANK,
                initialBalance = Money(1000L, "USD"),
                isArchived = true
            )
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )
        recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(1000L, "USD"),
                accountId = archivedAccId,
                categoryId = catId,
                frequency = RecurrenceFrequency.WEEKLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 12, 1),
                isActive = true
            )
        )

        val state = viewModel.uiState.first { it is RecurringTransactionsUiState.Success } as RecurringTransactionsUiState.Success
        val uiModel = state.rules.single()
        assertEquals(RecurringStatus.NEEDS_ATTENTION, uiModel.status)
        assertEquals(R.string.recurring_status_setup_unavailable, uiModel.statusDetail?.resourceId)
    }

    @Test
    fun `pausing ordinary expense rule via confirmation dialog deactivates rule`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#6366F1")
        )
        val ruleId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(80000L, "USD"),
                accountId = accId,
                categoryId = catId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 12, 1),
                note = "Monthly Rent",
                isActive = true
            )
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                RecurringTransactionsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToAddRule = {},
                    onNavigateToEditRule = {}
                )
            }
        }

        advanceUntilIdle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rule_menu_button_$ruleId").performClick()
        composeTestRule.onNodeWithTag("rule_pause_item_$ruleId").performClick()
        composeTestRule.onNodeWithTag("confirm_deactivate_button").performClick()

        advanceUntilIdle()
        composeTestRule.waitForIdle()

        val updated = recurringRepository.getRecurringTransactionById(ruleId)!!
        assertFalse(updated.isActive)
        assertTrue((viewModel.uiState.value as RecurringTransactionsUiState.Success).rules.single().status == RecurringStatus.PAUSED)
        assertNull((viewModel.uiState.value as RecurringTransactionsUiState.Success).rules.single().statusDetail)
    }
}
