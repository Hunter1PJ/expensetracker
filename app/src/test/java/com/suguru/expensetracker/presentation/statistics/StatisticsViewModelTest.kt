package com.suguru.expensetracker.presentation.statistics

import com.suguru.expensetracker.R
import com.suguru.expensetracker.presentation.util.UiMessage
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
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import java.time.LocalDate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    private val fixedNow = Instant.parse("2026-09-15T12:00:00Z")

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var observeStatisticsUseCase: ObserveStatisticsUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        observeStatisticsUseCase = ObserveStatisticsUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StatisticsViewModel {
        return StatisticsViewModel(
            observeStatisticsUseCase = observeStatisticsUseCase,
            zoneId = zoneId,
            clockNow = { fixedNow }
        )
    }

    @Test
    fun `initial state loads period statistics correctly`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#FF0000")
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(StatisticsPeriodOption.THIS_MONTH, state.selectedPeriod)
        assertEquals("USD", state.selectedCurrencyCode)
        assertNotNull(state.summary)
        assertEquals("$25.00", state.summary?.formattedExpense)
        assertEquals(1, state.expenseCategories.size)
        assertEquals("Food", state.expenseCategories[0].categoryName)
        assertEquals("100%", state.expenseCategories[0].percentageText)

        job.cancel()
    }

    @Test
    fun `switching period updates UI state`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        // Expense in August (Last Month relative to Sept 2026)
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                transactionTime = Instant.parse("2026-08-15T10:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // In Sept (This Month), expense should be 0
        assertEquals("$0.00", viewModel.uiState.value.summary?.formattedExpense)

        // Switch to Last Month
        viewModel.onPeriodSelected(StatisticsPeriodOption.LAST_MONTH)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StatisticsPeriodOption.LAST_MONTH, state.selectedPeriod)
        assertEquals("$40.00", state.summary?.formattedExpense)

        job.cancel()
    }

    @Test
    fun `switching currency updates UI state`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "USD Card", type = AccountType.CARD, initialBalance = Money(0, "USD"))
        )
        val uzsAcc = accountRepository.insertAccount(
            Account(name = "UZS Cash", type = AccountType.CASH, initialBalance = Money(0, "UZS"))
        )

        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000000L, "UZS"),
                type = TransactionType.INCOME,
                accountId = uzsAcc,
                transactionTime = Instant.parse("2026-09-03T10:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onCurrencySelected("UZS")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("UZS", state.selectedCurrencyCode)
        assertEquals("5,000,000 so'm", state.summary?.formattedIncome)

        job.cancel()
    }

    @Test
    fun `selecting custom range shows custom range picker for all users`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onPeriodSelected(StatisticsPeriodOption.CUSTOM)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCustomRangePickerVisible)

        job.cancel()
    }

    @Test
    fun `submitting a valid custom range updates dates and queries statistics correctly`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(0, "USD"))
        )
        // Transaction on September 12, 2026
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                transactionTime = Instant.parse("2026-09-12T10:00:00Z")
            )
        )
        // Transaction outside selected custom range
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                transactionTime = Instant.parse("2026-09-05T10:00:00Z")
            )
        )

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // Submit custom dates from Sept 10 to Sept 14
        viewModel.onCustomDatesSelected(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StatisticsPeriodOption.CUSTOM, state.selectedPeriod)
        assertEquals(LocalDate.of(2026, 9, 10), state.customStartDate)
        assertEquals(LocalDate.of(2026, 9, 14), state.customEndDate)
        assertEquals("$15.00", state.summary?.formattedExpense)

        job.cancel()
    }

    @Test
    fun `submitting an invalid custom range shows validation error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // Start date is after end date
        viewModel.onCustomDatesSelected(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 10))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.customRangeError)
        assertEquals(UiMessage(R.string.error_budget_date_range_invalid), state.customRangeError)
        assertEquals(StatisticsPeriodOption.THIS_MONTH, state.selectedPeriod)

        job.cancel()
    }
}
