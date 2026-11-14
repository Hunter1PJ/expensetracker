package com.suguru.expensetracker.presentation.recurring.addedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeRecurringTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.GetRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase
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
import org.junit.Assert.assertNotNull
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
class AddEditRecurringTransactionViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var recurringRepository: FakeRecurringTransactionRepository

    private lateinit var createUseCase: CreateRecurringTransactionUseCase
    private lateinit var updateUseCase: UpdateRecurringTransactionUseCase
    private lateinit var getUseCase: GetRecurringTransactionUseCase
    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase
    private lateinit var observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringRepository = FakeRecurringTransactionRepository()

        createUseCase = CreateRecurringTransactionUseCase(
            recurringRepository,
            accountRepository,
            categoryRepository,
            null
        )
        updateUseCase = UpdateRecurringTransactionUseCase(
            recurringRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getUseCase = GetRecurringTransactionUseCase(recurringRepository)
        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
        observeActiveCategoriesUseCase = ObserveActiveCategoriesUseCase(categoryRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(ruleId: Long = 0L): AddEditRecurringTransactionViewModel {
        return AddEditRecurringTransactionViewModel(
            ruleId = ruleId,
            createRecurringTransactionUseCase = createUseCase,
            updateRecurringTransactionUseCase = updateUseCase,
            getRecurringTransactionUseCase = getUseCase,
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    @Test
    fun `create expense and income rules enforce internal default account even when legacy account exists first`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Legacy Card", type = AccountType.CARD, initialBalance = Money(10000L, "USD"))
        )
        val defaultAccId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "EUR"), internalKey = "default")
        )
        val groceriesId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#EF4444")
        )
        val salaryId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        // Expense defaults properly
        assertEquals("EUR", vm.uiState.value.currencyCode)
        assertTrue(vm.uiState.value.hasDefaultAccount)
        assertEquals(listOf(groceriesId), vm.uiState.value.availableCategories.map { it.id })

        // Switch EXPENSE -> INCOME -> EXPENSE preserves full category pool without losing categories
        vm.onTypeSelected(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, vm.uiState.value.type)
        assertEquals(listOf(salaryId), vm.uiState.value.availableCategories.map { it.id })
        assertEquals(salaryId, vm.uiState.value.selectedCategoryId)

        vm.onAmountChanged("2500.00")
        vm.onFrequencySelected(RecurrenceFrequency.MONTHLY)
        vm.onNoteChanged("Monthly Salary")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSuccess)
        val savedRules = recurringRepository.observeAllRecurringTransactions().first()
        assertEquals(1, savedRules.size)
        val incomeRule = savedRules.single()
        assertEquals(TransactionType.INCOME, incomeRule.type)
        assertEquals(defaultAccId, incomeRule.accountId)
        assertEquals("EUR", incomeRule.amount.currencyCode)
        assertEquals(250000L, incomeRule.amount.amountInMinorUnits)
        assertNull(incomeRule.destinationAccountId)

        // Second VM creates Expense rule on internal default account
        val vmExpense = createViewModel()
        advanceUntilIdle()
        vmExpense.onAmountChanged("45.25")
        vmExpense.onNoteChanged("Weekly Market")
        vmExpense.saveRule()
        advanceUntilIdle()

        assertTrue(vmExpense.uiState.value.isSuccess)
        val allRules = recurringRepository.observeAllRecurringTransactions().first()
        assertEquals(2, allRules.size)
        val expenseRule = allRules.first { it.type == TransactionType.EXPENSE }
        assertEquals(defaultAccId, expenseRule.accountId)
        assertEquals("EUR", expenseRule.amount.currencyCode)
        assertEquals(4525L, expenseRule.amount.amountInMinorUnits)
    }

    @Test
    fun `create rejects transfer selection and blocks creation when internal default account is missing`() = runTest(testDispatcher) {
        // Seed only non-default account
        accountRepository.insertAccount(
            Account(name = "Legacy Checking", type = AccountType.BANK, initialBalance = Money(10000L, "USD"))
        )
        categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#EF4444")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasDefaultAccount)

        vm.onTypeSelected(TransactionType.TRANSFER)
        assertEquals(TransactionType.EXPENSE, vm.uiState.value.type)
        assertNotNull(vm.uiState.value.errorMessage)

        vm.onAmountChanged("25.00")
        vm.saveRule()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSuccess)
        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals(0, recurringRepository.observeAllRecurringTransactions().first().size)
    }

    @Test
    fun `edit mode preserves historical accountId currency frequency nextOccurrence and active state`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        val historicalEurId = accountRepository.insertAccount(
            Account(name = "Legacy Euro Bank", type = AccountType.BANK, initialBalance = Money(500000L, "EUR"))
        )
        val rentCatId = categoryRepository.insertCategory(
            Category(name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#6366F1")
        )
        val utilitiesCatId = categoryRepository.insertCategory(
            Category(name = "Utilities", type = CategoryType.EXPENSE, iconName = "bolt", colorHex = "#F59E0B")
        )

        val startDate = LocalDate.of(2026, 1, 1)
        val scheduledNext = LocalDate.of(2026, 5, 1)
        val ruleId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(125050L, "EUR"),
                accountId = historicalEurId,
                categoryId = rentCatId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = startDate,
                nextOccurrence = scheduledNext,
                note = "Old Rent",
                isActive = false
            )
        )

        val vm = createViewModel(ruleId)
        advanceUntilIdle()

        assertEquals("1250.50", vm.uiState.value.amountInput)
        assertEquals("EUR", vm.uiState.value.currencyCode)
        assertFalse(vm.uiState.value.isReadOnlyTransfer)

        // Attempting to change type or frequency in edit mode is ignored
        vm.onTypeSelected(TransactionType.INCOME)
        vm.onFrequencySelected(RecurrenceFrequency.WEEKLY)
        assertEquals(TransactionType.EXPENSE, vm.uiState.value.type)
        assertEquals(RecurrenceFrequency.MONTHLY, vm.uiState.value.frequency)

        vm.onAmountChanged("1300.00")
        vm.onCategorySelected(utilitiesCatId)
        vm.onNoteChanged("Updated Rent")
        vm.saveRule()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSuccess)
        val updated = recurringRepository.getRecurringTransactionById(ruleId)!!
        assertEquals(historicalEurId, updated.accountId)
        assertEquals("EUR", updated.amount.currencyCode)
        assertEquals(130000L, updated.amount.amountInMinorUnits)
        assertEquals(utilitiesCatId, updated.categoryId)
        assertEquals(TransactionType.EXPENSE, updated.type)
        assertEquals(RecurrenceFrequency.MONTHLY, updated.frequency)
        assertEquals(scheduledNext, updated.nextOccurrence)
        assertFalse(updated.isActive)
        assertEquals("Updated Rent", updated.note)
    }

    @Test
    fun `edit mode keeps archived historical category selectable`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        val archivedCatId = categoryRepository.insertCategory(
            Category(
                name = "Old Gym",
                type = CategoryType.EXPENSE,
                iconName = "fitness",
                colorHex = "#EF4444",
                isArchived = true
            )
        )
        val ruleId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(4000L, "USD"),
                accountId = accId,
                categoryId = archivedCatId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 2, 1),
                isActive = true
            )
        )

        val vm = createViewModel(ruleId)
        advanceUntilIdle()

        assertEquals(archivedCatId, vm.uiState.value.selectedCategoryId)
        assertTrue(vm.uiState.value.availableCategories.any { it.id == archivedCatId })
    }

    @Test
    fun `historical transfer opens read-only in edit route and rejects edits conversion reactivation and saving`() = runTest(testDispatcher) {
        val srcId = accountRepository.insertAccount(
            Account(name = "Hidden Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val dstId = accountRepository.insertAccount(
            Account(name = "Hidden Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        val transferId = recurringRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(30000L, "USD"),
                accountId = srcId,
                destinationAccountId = dstId,
                categoryId = null,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                nextOccurrence = LocalDate.of(2026, 6, 1),
                note = "Legacy Auto Transfer",
                isActive = false
            )
        )

        val vm = createViewModel(transferId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isReadOnlyTransfer)
        assertEquals("$300.00", vm.uiState.value.readOnlyAmountFormatted)

        vm.onTypeSelected(TransactionType.EXPENSE)
        vm.onAmountChanged("999.00")
        vm.onNoteChanged("Mutated")
        vm.saveRule()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSuccess)
        assertNotNull(vm.uiState.value.errorMessage)
        val persisted = recurringRepository.getRecurringTransactionById(transferId)!!
        assertEquals(TransactionType.TRANSFER, persisted.type)
        assertEquals(30000L, persisted.amount.amountInMinorUnits)
        assertEquals("Legacy Auto Transfer", persisted.note)
        assertFalse(persisted.isActive)

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                AddEditRecurringTransactionScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recurring_transfer_readonly_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("$300.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_segmented_expense").assertDoesNotExist()
        composeTestRule.onNodeWithTag("type_segmented_income").assertDoesNotExist()
        composeTestRule.onNodeWithTag("type_segmented_transfer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("amount_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_recurring_button").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Hidden Checking", substring = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
        composeTestRule.onAllNodesWithText("Hidden Savings", substring = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
    }

    @Test
    fun `add recurring screen hides transfer option and account selectors`() {
        val state = AddEditRecurringUiState(
            isRuleLoaded = true,
            hasDefaultAccount = true,
            type = TransactionType.EXPENSE,
            currencyCode = "USD",
            availableCategories = listOf(
                Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#EF4444")
            ),
            selectedCategoryId = 1L
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                AddEditRecurringTransactionScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onTypeSelected = {},
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onFrequencySelected = {},
                    onStartDateSelected = {},
                    onHasEndDateToggled = {},
                    onEndDateSelected = {},
                    onNoteChanged = {},
                    onSaveRule = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("type_segmented_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_segmented_income").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_segmented_transfer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("destination_account_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("recurring_transfer_readonly_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("amount_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_selector").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("frequency_selector").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_recurring_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `edit recurring screen locks type and frequency controls`() {
        val state = AddEditRecurringUiState(
            ruleId = 5L,
            isEditMode = true,
            isRuleLoaded = true,
            hasDefaultAccount = true,
            type = TransactionType.EXPENSE,
            amountInput = "120.00",
            currencyCode = "EUR",
            frequency = RecurrenceFrequency.MONTHLY,
            availableCategories = listOf(
                Category(id = 1L, name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#6366F1")
            ),
            selectedCategoryId = 1L
        )

        composeTestRule.setContent {
            ExpenseTrackerTheme {
                AddEditRecurringTransactionScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onTypeSelected = {},
                    onAmountChanged = {},
                    onCategorySelected = {},
                    onFrequencySelected = {},
                    onStartDateSelected = {},
                    onHasEndDateToggled = {},
                    onEndDateSelected = {},
                    onNoteChanged = {},
                    onSaveRule = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("type_segmented_income").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("frequency_selector").performScrollTo().assertIsNotEnabled()
    }
}
