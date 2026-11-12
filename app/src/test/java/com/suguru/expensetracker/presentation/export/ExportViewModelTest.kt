package com.suguru.expensetracker.presentation.export

import com.suguru.expensetracker.data.export.CsvExporter
import com.suguru.expensetracker.data.export.PdfFinancialReportRenderer
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
import com.suguru.expensetracker.domain.usecase.export.GenerateAdvancedCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.GenerateBasicCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.PrepareFinancialReportDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAccountRepo: FakeAccountRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeTransactionRepo: FakeTransactionRepository

    private lateinit var csvExporter: CsvExporter
    private lateinit var pdfRenderer: PdfFinancialReportRenderer
    private lateinit var prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase
    private lateinit var generateBasicCsvExportUseCase: GenerateBasicCsvExportUseCase
    private lateinit var generateAdvancedCsvExportUseCase: GenerateAdvancedCsvExportUseCase

    private lateinit var viewModel: ExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAccountRepo = FakeAccountRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeTransactionRepo = FakeTransactionRepository()

        csvExporter = CsvExporter()
        pdfRenderer = PdfFinancialReportRenderer()
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

        viewModel = ExportViewModel(
            generateBasicCsvExportUseCase = generateBasicCsvExportUseCase,
            generateAdvancedCsvExportUseCase = generateAdvancedCsvExportUseCase,
            prepareFinancialReportDataUseCase = prepareFinancialReportDataUseCase,
            pdfFinancialReportRenderer = pdfRenderer,
            accountRepository = fakeAccountRepo,
            categoryRepository = fakeCategoryRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state defaults to Basic CSV`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExportFormat.BASIC_CSV, state.selectedFormat)
        assertFalse(state.isWritingFile)
    }

    @Test
    fun `selecting advanced CSV or PDF updates selected format directly`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectFormat(ExportFormat.ADVANCED_CSV)
        assertEquals(ExportFormat.ADVANCED_CSV, viewModel.uiState.value.selectedFormat)

        viewModel.selectFormat(ExportFormat.PDF_REPORT)
        assertEquals(ExportFormat.PDF_REPORT, viewModel.uiState.value.selectedFormat)
    }

    @Test
    fun `export requested on basic CSV opens privacy confirmation with expected filename`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExportRequested()
        val state = viewModel.uiState.value
        assertTrue(state.showPrivacyWarningDialog)
        assertNotNull(state.targetFileName)
        assertTrue(state.targetFileName!!.startsWith("ExpenseTracker_All_Transactions_"))
        assertTrue(state.targetFileName!!.endsWith(".csv"))
    }

    @Test
    fun `export requested on PDF report creates PDF filename`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectFormat(ExportFormat.PDF_REPORT)
        viewModel.onExportRequested()

        val state = viewModel.uiState.value
        assertTrue(state.showPrivacyWarningDialog)
        assertNotNull(state.targetFileName)
        assertTrue(state.targetFileName!!.startsWith("ExpenseTracker_Financial_Report_"))
        assertTrue(state.targetFileName!!.endsWith(".pdf"))
    }

    @Test
    fun `filter modifications update UI state filter`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleTransactionTypeFilter(TransactionType.EXPENSE)
        assertTrue(viewModel.uiState.value.filter.transactionTypes.contains(TransactionType.EXPENSE))
        assertTrue(viewModel.uiState.value.filter.isFiltered)

        viewModel.toggleCurrencyFilter("USD")
        assertTrue(viewModel.uiState.value.filter.currencyCodes.contains("USD"))

        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 31)
        viewModel.setDateRange(start, end)
        assertEquals(start, viewModel.uiState.value.filter.startDate)
        assertEquals(end, viewModel.uiState.value.filter.endDate)

        viewModel.clearFilters()
        assertFalse(viewModel.uiState.value.filter.isFiltered)
    }

    @Test
    fun `execute export to output stream writes valid CSV content`() = runTest {
        val accountId = fakeAccountRepo.insertAccount(
            Account(
                id = 0L,
                name = "Checking",
                type = AccountType.BANK,
                initialBalance = Money(100000L, "USD"),
                colorHex = "#123456",
                iconName = "wallet",
                isArchived = false
            )
        )
        val categoryId = fakeCategoryRepo.insertCategory(
            Category(id = 0L, name = "Groceries", type = CategoryType.EXPENSE, colorHex = "#FF0000", iconName = "cart", isArchived = false)
        )
        fakeTransactionRepo.insertTransaction(
            Transaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(4500L, "USD"),
                accountId = accountId,
                destinationAccountId = null,
                categoryId = categoryId,
                transactionTime = Instant.parse("2026-09-10T10:00:00Z"),
                note = "Whole Foods grocery trip",
                recurringRuleId = null
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val outputStream = ByteArrayOutputStream()
        viewModel.executeExportToStream(outputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val csvString = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(csvString.contains("transaction_id,date,time,type,amount,currency,source_account,destination_account,category,note,recurring"))
        assertTrue(csvString.contains("Groceries"))
        assertTrue(csvString.contains("Checking"))
        assertTrue(csvString.contains("45.00"))
        assertTrue(csvString.contains("Whole Foods grocery trip"))
    }
}
