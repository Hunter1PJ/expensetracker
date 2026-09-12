package com.suguru.expensetracker.domain.usecase.statistics

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ObserveStatisticsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    // Fixed now instant: 2026-09-15T12:00:00Z
    private val fixedNow = Instant.parse("2026-09-15T12:00:00Z")

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var useCase: ObserveStatisticsUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        useCase = ObserveStatisticsUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty period returns zero summary and empty analytics`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )

        val stats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("USD", stats.selectedCurrencyCode)
        assertEquals(0L, stats.currencySummary.income.amountInMinorUnits)
        assertEquals(0L, stats.currencySummary.expense.amountInMinorUnits)
        assertEquals(0L, stats.currencySummary.net.amountInMinorUnits)
        assertTrue(stats.expenseCategories.isEmpty())
        assertTrue(stats.incomeCategories.isEmpty())
    }

    @Test
    fun `calculates income, expense, and net correctly while ignoring transfers`() = runTest(testDispatcher) {
        val usdAcc1 = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val usdAcc2 = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(5000, "USD"))
        )
        val foodCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#FF0000")
        )
        val salaryCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#00FF00")
        )

        // Sept 5 Income: $2,000.00
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(200000L, "USD"),
                type = TransactionType.INCOME,
                accountId = usdAcc1,
                categoryId = salaryCat,
                transactionTime = Instant.parse("2026-09-05T10:00:00Z")
            )
        )

        // Sept 10 Expense: $500.00
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(50000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc1,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-09-10T14:00:00Z")
            )
        )

        // Sept 12 Transfer: $300.00 (from usdAcc1 to usdAcc2)
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(30000L, "USD"),
                type = TransactionType.TRANSFER,
                accountId = usdAcc1,
                destinationAccountId = usdAcc2,
                transactionTime = Instant.parse("2026-09-12T09:00:00Z")
            )
        )

        val stats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("USD", stats.selectedCurrencyCode)
        assertEquals(200000L, stats.currencySummary.income.amountInMinorUnits)
        assertEquals(50000L, stats.currencySummary.expense.amountInMinorUnits)
        assertEquals(150000L, stats.currencySummary.net.amountInMinorUnits) // $2000 - $500 = $1500
        assertEquals(2, stats.currencySummary.transactionCount) // Income + Expense (transfer excluded)

        // Check Expense Category
        assertEquals(1, stats.expenseCategories.size)
        assertEquals("Food", stats.expenseCategories[0].categoryName)
        assertEquals(50000L, stats.expenseCategories[0].amount.amountInMinorUnits)

        // Check Income Category
        assertEquals(1, stats.incomeCategories.size)
        assertEquals("Salary", stats.incomeCategories[0].categoryName)
        assertEquals(200000L, stats.incomeCategories[0].amount.amountInMinorUnits)
    }

    @Test
    fun `multi-currency safety keeps currencies separated and allows selection`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "USD Card", type = AccountType.CARD, initialBalance = Money(0, "USD"))
        )
        val uzsAcc = accountRepository.insertAccount(
            Account(name = "UZS Cash", type = AccountType.CASH, initialBalance = Money(0, "UZS"))
        )

        // USD Expense: $100.00
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(10000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )

        // UZS Income: 5,000,000 so'm
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000000L, "UZS"),
                type = TransactionType.INCOME,
                accountId = uzsAcc,
                transactionTime = Instant.parse("2026-09-03T10:00:00Z")
            )
        )

        // Observe with UZS explicitly selected
        val uzsStats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("UZS", uzsStats.selectedCurrencyCode)
        assertEquals(5000000L, uzsStats.currencySummary.income.amountInMinorUnits)
        assertEquals(0L, uzsStats.currencySummary.expense.amountInMinorUnits)
        assertEquals(5000000L, uzsStats.currencySummary.net.amountInMinorUnits)

        // Observe with USD explicitly selected
        val usdStats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("USD", usdStats.selectedCurrencyCode)
        assertEquals(0L, usdStats.currencySummary.income.amountInMinorUnits)
        assertEquals(10000L, usdStats.currencySummary.expense.amountInMinorUnits)
        assertEquals(-10000L, usdStats.currencySummary.net.amountInMinorUnits)
    }

    @Test
    fun `includes archived accounts and archived categories in historical statistics`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Old Account", type = AccountType.BANK, initialBalance = Money(0, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Old Category", type = CategoryType.EXPENSE, iconName = "archive", colorHex = "#123456")
        )

        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1200L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-04T10:00:00Z")
            )
        )

        // Archive both
        accountRepository.archiveAccount(accId)
        categoryRepository.archiveCategory(catId)

        val stats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals(1200L, stats.currencySummary.expense.amountInMinorUnits)
        assertEquals(1, stats.expenseCategories.size)
        assertEquals("Old Category", stats.expenseCategories[0].categoryName)
        assertTrue(stats.expenseCategories[0].isArchived)

        assertEquals(1, stats.accountBreakdown.size)
        assertEquals("Old Account", stats.accountBreakdown[0].accountName)
        assertTrue(stats.accountBreakdown[0].isArchived)
    }

    @Test
    fun `boundary test respects startInclusive and endExclusive`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Card", type = AccountType.CARD, initialBalance = Money(0, "USD"))
        )

        // Transaction at exact start of Sept (2026-09-01T00:00:00Z) -> INCLUDED in Sept
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                transactionTime = Instant.parse("2026-09-01T00:00:00Z")
            )
        )

        // Transaction at exact end of Sept / start of Oct (2026-10-01T00:00:00Z) -> EXCLUDED from Sept
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                transactionTime = Instant.parse("2026-10-01T00:00:00Z")
            )
        )

        val septStats = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals(1000L, septStats.currencySummary.expense.amountInMinorUnits)
    }
}
