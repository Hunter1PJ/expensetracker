package com.suguru.expensetracker.domain.analytics

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
import com.suguru.expensetracker.domain.model.export.ExportFilter
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.usecase.export.PrepareFinancialReportDataUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Parity test suite verifying that [PrepareFinancialReportDataUseCase] and [ObserveStatisticsUseCase]
 * produce identical minor-unit financial summaries across all scenarios via [CanonicalFinancialAggregator].
 */
class CanonicalFinancialAggregatorParityTest {

    private lateinit var fakeAccountRepo: FakeAccountRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeTransactionRepo: FakeTransactionRepository

    private lateinit var canonicalAggregator: CanonicalFinancialAggregator
    private lateinit var observeStatisticsUseCase: ObserveStatisticsUseCase
    private lateinit var prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase

    private val zoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-15T12:00:00Z")

    private var accountUsdId = 0L
    private var accountEurId = 0L
    private var catFoodId = 0L
    private var catSalaryId = 0L
    private var catArchivedId = 0L

    @Before
    fun setUp() = runTest {
        fakeAccountRepo = FakeAccountRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeTransactionRepo = FakeTransactionRepository()
        canonicalAggregator = CanonicalFinancialAggregator()

        observeStatisticsUseCase = ObserveStatisticsUseCase(
            transactionRepository = fakeTransactionRepo,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo,
            canonicalAggregator = canonicalAggregator
        )

        prepareFinancialReportDataUseCase = PrepareFinancialReportDataUseCase(
            transactionRepository = fakeTransactionRepo,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo,
            canonicalAggregator = canonicalAggregator
        )

        accountUsdId = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "USD Checking",
                type = AccountType.BANK,
                initialBalance = Money(500000L, "USD"),
                colorHex = "#112233",
                iconName = "wallet",
                isArchived = false
            )
        )
        accountEurId = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "EUR Wallet",
                type = AccountType.CASH,
                initialBalance = Money(200000L, "EUR"),
                colorHex = "#445566",
                iconName = "bank",
                isArchived = false
            )
        )

        catFoodId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Food", type = CategoryType.EXPENSE, colorHex = "#FF5722", iconName = "restaurant", isArchived = false)
        )
        catSalaryId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Salary", type = CategoryType.INCOME, colorHex = "#4CAF50", iconName = "payments", isArchived = false)
        )
        catArchivedId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Old Subscription", type = CategoryType.EXPENSE, colorHex = "#9E9E9E", iconName = "history", isArchived = true)
        )
    }

    @Test
    fun parity_singleCurrencyDataset() = runTest {
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(250000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-01T10:00:00Z"),
                note = "June Paycheck",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(4550L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-06-02T12:30:00Z"),
                note = "Groceries",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 6, 30)),
            zoneId = zoneId,
            now = now
        )

        val exportUsd = exportData.currencySummaries.first { it.currencyCode == "USD" }

        assertEquals(stats.currencySummary.income.amountInMinorUnits, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(stats.currencySummary.expense.amountInMinorUnits, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(stats.currencySummary.net.amountInMinorUnits, exportUsd.netAmount.amountInMinorUnits)
        assertEquals(stats.currencySummary.transactionCount, exportUsd.transactionCount)
    }

    @Test
    fun parity_multiCurrencyDataset() = runTest {
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(100000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-05T10:00:00Z"),
                note = "USD Salary",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(3000L, "EUR"),
                accountId = accountEurId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-06-06T11:00:00Z"),
                note = "EUR Dinner",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val statsUsd = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now
        ).first()

        val statsEur = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "EUR",
            zoneId = zoneId,
            now = now
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 6, 30)),
            zoneId = zoneId,
            now = now
        )

        val exportUsd = exportData.currencySummaries.first { it.currencyCode == "USD" }
        val exportEur = exportData.currencySummaries.first { it.currencyCode == "EUR" }

        assertEquals(statsUsd.currencySummary.income.amountInMinorUnits, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(statsUsd.currencySummary.expense.amountInMinorUnits, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(statsUsd.currencySummary.net.amountInMinorUnits, exportUsd.netAmount.amountInMinorUnits)

        assertEquals(statsEur.currencySummary.income.amountInMinorUnits, exportEur.totalIncome.amountInMinorUnits)
        assertEquals(statsEur.currencySummary.expense.amountInMinorUnits, exportEur.totalExpense.amountInMinorUnits)
        assertEquals(statsEur.currencySummary.net.amountInMinorUnits, exportEur.netAmount.amountInMinorUnits)
    }

    @Test
    fun parity_transfersAreNeutral() = runTest {
        // Account transfer
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(50000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = accountUsdId,
                categoryId = null,
                transactionTime = Instant.parse("2026-06-07T10:00:00Z"),
                note = "Internal Transfer",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 6, 30)),
            zoneId = zoneId,
            now = now
        )

        val exportUsd = exportData.currencySummaries.first { it.currencyCode == "USD" }

        // Both must be 0 for income, expense, and net
        assertEquals(0L, stats.currencySummary.income.amountInMinorUnits)
        assertEquals(0L, stats.currencySummary.expense.amountInMinorUnits)
        assertEquals(0L, stats.currencySummary.net.amountInMinorUnits)

        assertEquals(0L, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(0L, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(0L, exportUsd.netAmount.amountInMinorUnits)

        assertEquals(stats.currencySummary.income.amountInMinorUnits, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(stats.currencySummary.expense.amountInMinorUnits, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(stats.currencySummary.net.amountInMinorUnits, exportUsd.netAmount.amountInMinorUnits)
    }

    @Test
    fun parity_archivedCategoryResolution() = runTest {
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(999L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catArchivedId,
                transactionTime = Instant.parse("2026-06-08T10:00:00Z"),
                note = "Old Sub",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 6, 30)),
            zoneId = zoneId,
            now = now
        )

        val statsCat = stats.expenseCategories.first { it.categoryId == catArchivedId }
        val exportCat = exportData.expenseCategoriesByCurrency["USD"]!!.first { it.categoryId == catArchivedId }

        assertEquals(statsCat.categoryName, exportCat.categoryName)
        assertEquals("Old Subscription", exportCat.categoryName)
        assertEquals(statsCat.amount.amountInMinorUnits, exportCat.totalAmount.amountInMinorUnits)
        assertEquals(999L, exportCat.totalAmount.amountInMinorUnits)
        assertTrue(statsCat.isArchived)
        assertTrue(exportCat.isArchived)
    }

    @Test
    fun parity_zeroTransactions() = runTest {
        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(),
            zoneId = zoneId,
            now = now
        )

        assertTrue(exportData.transactions.isEmpty())
        assertTrue(exportData.currencySummaries.isEmpty())
        assertEquals(0, exportData.totalTransactionsCount)
    }

    @Test
    fun parity_mixedIncomeExpenseAndCategoryTotals() = runTest {
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(500000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-01T10:00:00Z"),
                note = "Salary 1",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(250000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-15T10:00:00Z"),
                note = "Salary 2",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(12000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-06-10T12:00:00Z"),
                note = "Dinner",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 6, 30)),
            zoneId = zoneId,
            now = now
        )

        val exportUsd = exportData.currencySummaries.first { it.currencyCode == "USD" }

        // Exact parity
        assertEquals(stats.currencySummary.income.amountInMinorUnits, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(750000L, exportUsd.totalIncome.amountInMinorUnits)

        assertEquals(stats.currencySummary.expense.amountInMinorUnits, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(12000L, exportUsd.totalExpense.amountInMinorUnits)

        assertEquals(stats.currencySummary.net.amountInMinorUnits, exportUsd.netAmount.amountInMinorUnits)
        assertEquals(738000L, exportUsd.netAmount.amountInMinorUnits)

        // Category breakdown parity
        val statsIncomeCat = stats.incomeCategories.first { it.categoryId == catSalaryId }
        val exportIncomeCat = exportData.incomeCategoriesByCurrency["USD"]!!.first { it.categoryId == catSalaryId }
        assertEquals(statsIncomeCat.amount.amountInMinorUnits, exportIncomeCat.totalAmount.amountInMinorUnits)
        assertEquals(750000L, exportIncomeCat.totalAmount.amountInMinorUnits)

        val statsExpenseCat = stats.expenseCategories.first { it.categoryId == catFoodId }
        val exportExpenseCat = exportData.expenseCategoriesByCurrency["USD"]!!.first { it.categoryId == catFoodId }
        assertEquals(statsExpenseCat.amount.amountInMinorUnits, exportExpenseCat.totalAmount.amountInMinorUnits)
        assertEquals(12000L, exportExpenseCat.totalAmount.amountInMinorUnits)
    }

    @Test
    fun parity_dateFilteredPeriod() = runTest {
        // Inside date range (June 2026)
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(100000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-10T10:00:00Z"),
                note = "June Salary",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        // Outside date range (May 2026)
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(200000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-05-10T10:00:00Z"),
                note = "May Salary",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        val startDate = LocalDate.of(2026, 6, 1)
        val endDate = LocalDate.of(2026, 6, 30)

        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.CUSTOM,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = now,
            customStart = startDate,
            customEnd = endDate
        ).first()

        val exportData = prepareFinancialReportDataUseCase(
            filter = ExportFilter(startDate = startDate, endDate = endDate),
            zoneId = zoneId,
            now = now
        )

        val exportUsd = exportData.currencySummaries.first { it.currencyCode == "USD" }

        assertEquals(stats.currencySummary.income.amountInMinorUnits, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(100000L, exportUsd.totalIncome.amountInMinorUnits)
        assertEquals(0L, exportUsd.totalExpense.amountInMinorUnits)
        assertEquals(100000L, exportUsd.netAmount.amountInMinorUnits)
    }

    @Test
    fun parity_filteredParity_accountAndCategoryAndType() = runTest {
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(5000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-06-10T10:00:00Z"),
                note = "Lunch",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(300000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-06-10T12:00:00Z"),
                note = "Bonus",
                recurringRuleId = null,
                recurringOccurrenceDate = null
            )
        )

        // Filter for Food category only
        val exportDataFoodOnly = prepareFinancialReportDataUseCase(
            filter = ExportFilter(categoryIds = setOf(catFoodId)),
            zoneId = zoneId,
            now = now
        )

        assertEquals(1, exportDataFoodOnly.totalTransactionsCount)
        val usdSummary = exportDataFoodOnly.currencySummaries.first { it.currencyCode == "USD" }
        assertEquals(0L, usdSummary.totalIncome.amountInMinorUnits)
        assertEquals(5000L, usdSummary.totalExpense.amountInMinorUnits)
        assertEquals(-5000L, usdSummary.netAmount.amountInMinorUnits)

        // Filter for Expense type only
        val exportDataExpenseOnly = prepareFinancialReportDataUseCase(
            filter = ExportFilter(transactionTypes = setOf(TransactionType.EXPENSE)),
            zoneId = zoneId,
            now = now
        )
        assertEquals(1, exportDataExpenseOnly.totalTransactionsCount)
        assertEquals(5000L, exportDataExpenseOnly.currencySummaries.first().totalExpense.amountInMinorUnits)
    }
}
