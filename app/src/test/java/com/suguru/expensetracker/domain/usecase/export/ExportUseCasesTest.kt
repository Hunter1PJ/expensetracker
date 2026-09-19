package com.suguru.expensetracker.domain.usecase.export

import com.suguru.expensetracker.data.export.CsvExporter
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

class ExportUseCasesTest {

    private lateinit var fakeAccountRepo: FakeAccountRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeTransactionRepo: FakeTransactionRepository
    private lateinit var csvExporter: CsvExporter

    private lateinit var prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase
    private lateinit var generateBasicCsvExportUseCase: GenerateBasicCsvExportUseCase
    private lateinit var generateAdvancedCsvExportUseCase: GenerateAdvancedCsvExportUseCase

    private var accountUsdId = 0L
    private var accountEurId = 0L
    private var catFoodId = 0L
    private var catSalaryId = 0L

    @Before
    fun setUp() = runTest {
        fakeAccountRepo = FakeAccountRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeTransactionRepo = FakeTransactionRepository()
        csvExporter = CsvExporter()

        prepareFinancialReportDataUseCase = PrepareFinancialReportDataUseCase(
            transactionRepository = fakeTransactionRepo,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo
        )
        generateBasicCsvExportUseCase = GenerateBasicCsvExportUseCase(
            transactionRepository = fakeTransactionRepo,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo,
            csvExporter = csvExporter
        )
        generateAdvancedCsvExportUseCase = GenerateAdvancedCsvExportUseCase(
            prepareFinancialReportDataUseCase = prepareFinancialReportDataUseCase,
            csvExporter = csvExporter
        )

        accountUsdId = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "USD Checking",
                type = AccountType.BANK,
                initialBalance = Money(100000L, "USD"),
                colorHex = "#112233",
                iconName = "wallet",
                isArchived = false
            )
        )
        accountEurId = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "EUR Savings",
                type = AccountType.SAVINGS,
                initialBalance = Money(50000L, "EUR"),
                colorHex = "#223344",
                iconName = "bank",
                isArchived = false
            )
        )
        catFoodId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Food & Dining", type = CategoryType.EXPENSE, colorHex = "#FF5722", iconName = "restaurant", isArchived = false)
        )
        catSalaryId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Salary", type = CategoryType.INCOME, colorHex = "#4CAF50", iconName = "payments", isArchived = false)
        )

        // Add Transactions
        // 1. USD Income
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.INCOME,
                amount = Money(300000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catSalaryId,
                transactionTime = Instant.parse("2026-09-01T09:00:00Z"),
                note = "Monthly Paycheck",
                recurringRuleId = null
            )
        )

        // 2. USD Expense
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(12000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-09-05T12:30:00Z"),
                note = "Dinner with team",
                recurringRuleId = null
            )
        )

        // 3. EUR Expense
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(5000L, "EUR"),
                accountId = accountEurId,
                destinationAccountId = null,
                categoryId = catFoodId,
                transactionTime = Instant.parse("2026-09-08T18:00:00Z"),
                note = "Bistro lunch",
                recurringRuleId = null
            )
        )

        // 4. USD Transfer (transfer neutral)
        val accountUsd2Id = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "USD Cash",
                type = AccountType.CASH,
                initialBalance = Money.zero("USD"),
                colorHex = "#334455",
                iconName = "money",
                isArchived = false
            )
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(20000L, "USD"),
                accountId = accountUsdId,
                destinationAccountId = accountUsd2Id,
                categoryId = null,
                transactionTime = Instant.parse("2026-09-10T15:00:00Z"),
                note = "ATM Withdrawal",
                recurringRuleId = null
            )
        )
    }

    @Test
    fun `PrepareFinancialReportData isolates multi-currency summaries and enforces transfer neutrality`() = runTest {
        val report = prepareFinancialReportDataUseCase(ExportFilter())

        assertEquals(4, report.totalTransactionsCount)
        assertEquals(2, report.currencySummaries.size)

        val usdSummary = report.currencySummaries.first { it.currencyCode == "USD" }
        assertEquals(300000L, usdSummary.totalIncome.amountInMinorUnits)
        assertEquals(12000L, usdSummary.totalExpense.amountInMinorUnits)
        assertEquals(288000L, usdSummary.netAmount.amountInMinorUnits)
        assertEquals(2, usdSummary.transactionCount) // 1 income + 1 expense (transfer excluded from summary counts)

        val eurSummary = report.currencySummaries.first { it.currencyCode == "EUR" }
        assertEquals(0L, eurSummary.totalIncome.amountInMinorUnits)
        assertEquals(5000L, eurSummary.totalExpense.amountInMinorUnits)
        assertEquals(-5000L, eurSummary.netAmount.amountInMinorUnits)
        assertEquals(1, eurSummary.transactionCount)
    }

    @Test
    fun `PrepareFinancialReportData respects date range filter`() = runTest {
        val filter = ExportFilter(
            startDate = LocalDate.of(2026, 9, 2),
            endDate = LocalDate.of(2026, 9, 6)
        )
        val report = prepareFinancialReportDataUseCase(filter)

        assertEquals(1, report.totalTransactionsCount)
        assertEquals("Dinner with team", report.transactions.first().note)
    }

    @Test
    fun `GenerateBasicCsvExport writes all transactions correctly formatted`() = runTest {
        val outputStream = ByteArrayOutputStream()
        generateBasicCsvExportUseCase.exportToStream(outputStream)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(csv.contains("Monthly Paycheck"))
        assertTrue(csv.contains("Dinner with team"))
        assertTrue(csv.contains("Bistro lunch"))
        assertTrue(csv.contains("ATM Withdrawal"))
        assertTrue(csv.contains("USD Checking"))
        assertTrue(csv.contains("USD Cash"))
    }

    @Test
    fun `GenerateAdvancedCsvExport writes filtered transactions`() = runTest {
        val filter = ExportFilter(currencyCodes = setOf("EUR"))
        val outputStream = ByteArrayOutputStream()
        generateAdvancedCsvExportUseCase.exportToStream(filter, outputStream)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(csv.contains("Bistro lunch"))
        assertTrue(!csv.contains("Monthly Paycheck"))
    }
}
