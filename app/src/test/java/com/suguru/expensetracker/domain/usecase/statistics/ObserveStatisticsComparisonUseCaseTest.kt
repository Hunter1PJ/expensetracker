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
import com.suguru.expensetracker.domain.model.statistics.ComparisonSemantic
import com.suguru.expensetracker.domain.model.statistics.RelativeChange
import com.suguru.expensetracker.domain.model.statistics.StatisticsComparisonMode
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
class ObserveStatisticsComparisonUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    // Fixed now instant: 2026-09-15T12:00:00Z
    private val fixedNow = Instant.parse("2026-09-15T12:00:00Z")

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var observeStatisticsUseCase: ObserveStatisticsUseCase
    private lateinit var useCase: ObserveStatisticsComparisonUseCase

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
        useCase = ObserveStatisticsComparisonUseCase(observeStatisticsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `both periods empty produces isBothEmpty true and zero metrics`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )

        val comparison = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertTrue(comparison.isBothEmpty)
        assertEquals(0L, comparison.netMetric.primaryAmount.amountInMinorUnits)
        assertEquals(0L, comparison.netMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(0L, comparison.netMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.NEUTRAL, comparison.netMetric.semantic)
        assertTrue(comparison.categoryBreakdown.isEmpty())
    }

    @Test
    fun `calculates comparison metrics correctly across current and previous periods`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val salaryCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )
        val foodCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#EF4444")
        )

        // Previous Period (August 2026): Income 4000, Expense 3500 -> Net 500
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4000L, "USD"),
                type = TransactionType.INCOME,
                accountId = usdAcc,
                categoryId = salaryCat,
                transactionTime = Instant.parse("2026-08-10T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(3500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-08-15T12:00:00Z")
            )
        )

        // Current Period (September 2026): Income 5000, Expense 3000 -> Net 2000
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.INCOME,
                accountId = usdAcc,
                categoryId = salaryCat,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(3000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-09-05T15:00:00Z")
            )
        )

        val comparison = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertFalse(comparison.isBothEmpty)

        // Net: 2000 vs 500 -> +1500 (FAVORABLE)
        assertEquals(2000L, comparison.netMetric.primaryAmount.amountInMinorUnits)
        assertEquals(500L, comparison.netMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(1500L, comparison.netMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, comparison.netMetric.semantic)

        // Income: 5000 vs 4000 -> +1000 (FAVORABLE, +25.00%)
        assertEquals(5000L, comparison.incomeMetric.primaryAmount.amountInMinorUnits)
        assertEquals(4000L, comparison.incomeMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(1000L, comparison.incomeMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, comparison.incomeMetric.semantic)
        assertTrue(comparison.incomeMetric.relativeChange is RelativeChange.Defined)
        assertEquals(2500L, (comparison.incomeMetric.relativeChange as RelativeChange.Defined).basisPoints)

        // Expense: 3000 vs 3500 -> -500 (FAVORABLE because spent less!)
        assertEquals(3000L, comparison.expenseMetric.primaryAmount.amountInMinorUnits)
        assertEquals(3500L, comparison.expenseMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(-500L, comparison.expenseMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, comparison.expenseMetric.semantic)
    }

    @Test
    fun `category comparison includes shared, new, and dropped categories`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val foodCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#EF4444")
        )
        val rentCat = categoryRepository.insertCategory(
            Category(name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#3B82F6")
        )
        val entertainmentCat = categoryRepository.insertCategory(
            Category(name = "Entertainment", type = CategoryType.EXPENSE, iconName = "movie", colorHex = "#F59E0B")
        )

        // Aug (Comparison): Food (1000) and Rent (2000)
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-08-10T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = rentCat,
                transactionTime = Instant.parse("2026-08-11T10:00:00Z")
            )
        )

        // Sep (Primary): Food (1500) and Entertainment (800) [Rent dropped]
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(1500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = foodCat,
                transactionTime = Instant.parse("2026-09-05T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(800L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = entertainmentCat,
                transactionTime = Instant.parse("2026-09-06T10:00:00Z")
            )
        )

        val comparison = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            zoneId = zoneId,
            now = fixedNow
        ).first()

        val foodComp = comparison.categoryBreakdown.first { it.categoryId == foodCat }
        assertEquals(1500L, foodComp.primaryAmount.amountInMinorUnits)
        assertEquals(1000L, foodComp.comparisonAmount.amountInMinorUnits)
        assertEquals(500L, foodComp.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.UNFAVORABLE, foodComp.semantic) // Expense increased

        val rentComp = comparison.categoryBreakdown.first { it.categoryId == rentCat }
        assertEquals(0L, rentComp.primaryAmount.amountInMinorUnits)
        assertEquals(2000L, rentComp.comparisonAmount.amountInMinorUnits)
        assertEquals(-2000L, rentComp.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, rentComp.semantic) // Expense decreased to zero

        val entertainmentComp = comparison.categoryBreakdown.first { it.categoryId == entertainmentCat }
        assertEquals(800L, entertainmentComp.primaryAmount.amountInMinorUnits)
        assertEquals(0L, entertainmentComp.comparisonAmount.amountInMinorUnits)
        assertEquals(800L, entertainmentComp.absoluteDelta.amountInMinorUnits)
        assertEquals(RelativeChange.FromZero, entertainmentComp.relativeChange)
    }

    @Test
    fun `currency isolation prevents cross currency contamination`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "USD Account", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val eurAcc = accountRepository.insertAccount(
            Account(name = "EUR Account", type = AccountType.BANK, initialBalance = Money(1000, "EUR"))
        )
        val cat = categoryRepository.insertCategory(
            Category(name = "General", type = CategoryType.EXPENSE, iconName = "tag", colorHex = "#10B981")
        )

        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(9999L, "EUR"),
                type = TransactionType.EXPENSE,
                accountId = eurAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-09-03T10:00:00Z")
            )
        )

        val comparison = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("USD", comparison.currencyCode)
        assertEquals(5000L, comparison.expenseMetric.primaryAmount.amountInMinorUnits)
    }

    @Test
    fun `multi currency union includes currencies from primary, comparison, and accounts`() = runTest(testDispatcher) {
        val usdAcc = accountRepository.insertAccount(
            Account(name = "USD Account", type = AccountType.BANK, initialBalance = Money(1000, "USD"))
        )
        val uzsAcc = accountRepository.insertAccount(
            Account(name = "UZS Account", type = AccountType.BANK, initialBalance = Money(1000, "UZS"))
        )
        val eurAcc = accountRepository.insertAccount(
            Account(name = "EUR Account", type = AccountType.BANK, initialBalance = Money(1000, "EUR"))
        )
        val cat = categoryRepository.insertCategory(
            Category(name = "General", type = CategoryType.EXPENSE, iconName = "tag", colorHex = "#10B981")
        )

        // Previous Period (August 2026): USD and EUR
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-08-10T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(5000L, "EUR"),
                type = TransactionType.EXPENSE,
                accountId = eurAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-08-11T10:00:00Z")
            )
        )

        // Current Period (September 2026): USD and UZS
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(3000L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = usdAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-09-02T10:00:00Z")
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                amount = Money(7000L, "UZS"),
                type = TransactionType.EXPENSE,
                accountId = uzsAcc,
                categoryId = cat,
                transactionTime = Instant.parse("2026-09-03T10:00:00Z")
            )
        )

        // TEST 1: Result availableCurrencies contains USD, EUR, UZS (full union)
        val comparisonUSD = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertTrue(comparisonUSD.availableCurrencies.contains("USD"))
        assertTrue(comparisonUSD.availableCurrencies.contains("EUR"))
        assertTrue(comparisonUSD.availableCurrencies.contains("UZS"))

        // TEST 2: Currency only in primary (UZS): comparison amount is zero
        val comparisonUZS = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("UZS", comparisonUZS.currencyCode)
        assertEquals(7000L, comparisonUZS.expenseMetric.primaryAmount.amountInMinorUnits)
        assertEquals(0L, comparisonUZS.expenseMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(7000L, comparisonUZS.expenseMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(RelativeChange.FromZero, comparisonUZS.expenseMetric.relativeChange)

        // TEST 3: Currency only in comparison (EUR): primary amount is zero
        val comparisonEUR = useCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            selectedCurrencyCode = "EUR",
            zoneId = zoneId,
            now = fixedNow
        ).first()

        assertEquals("EUR", comparisonEUR.currencyCode)
        assertEquals(0L, comparisonEUR.expenseMetric.primaryAmount.amountInMinorUnits)
        assertEquals(5000L, comparisonEUR.expenseMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(-5000L, comparisonEUR.expenseMetric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, comparisonEUR.expenseMetric.semantic) // Expense dropped to 0

        // TEST 4: No mixed-currency aggregation
        assertEquals("USD", comparisonUSD.expenseMetric.primaryAmount.currencyCode)
        assertEquals("USD", comparisonUSD.expenseMetric.comparisonAmount.currencyCode)
        assertEquals(3000L, comparisonUSD.expenseMetric.primaryAmount.amountInMinorUnits)
        assertEquals(2000L, comparisonUSD.expenseMetric.comparisonAmount.amountInMinorUnits)
        assertEquals(1000L, comparisonUSD.expenseMetric.absoluteDelta.amountInMinorUnits)

        // TEST 5: Category with same ID remains strictly currency-isolated
        val usdCatBreakdown = comparisonUSD.categoryBreakdown.first { it.categoryId == cat }
        assertEquals("USD", usdCatBreakdown.currencyCode)
        assertEquals(3000L, usdCatBreakdown.primaryAmount.amountInMinorUnits)
        assertEquals(2000L, usdCatBreakdown.comparisonAmount.amountInMinorUnits)

        val uzsCatBreakdown = comparisonUZS.categoryBreakdown.first { it.categoryId == cat }
        assertEquals("UZS", uzsCatBreakdown.currencyCode)
        assertEquals(7000L, uzsCatBreakdown.primaryAmount.amountInMinorUnits)
        assertEquals(0L, uzsCatBreakdown.comparisonAmount.amountInMinorUnits)
    }
}
