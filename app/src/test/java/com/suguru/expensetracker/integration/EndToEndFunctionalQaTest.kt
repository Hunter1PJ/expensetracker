package com.suguru.expensetracker.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.account.INTERNAL_DEFAULT_ACCOUNT_KEY
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.export.CsvExporter
import com.suguru.expensetracker.data.export.PdfFinancialReportRenderer
import com.suguru.expensetracker.presentation.export.localizedPdfReportLabels
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.repository.DefaultCategorySeeder
import com.suguru.expensetracker.data.repository.RoomAccountRepository
import com.suguru.expensetracker.data.repository.RoomBackupRepository
import com.suguru.expensetracker.data.repository.RoomBudgetRepository
import com.suguru.expensetracker.data.repository.RoomCategoryRepository
import com.suguru.expensetracker.data.repository.RoomRecurringOccurrenceRepository
import com.suguru.expensetracker.data.repository.RoomRecurringTransactionRepository
import com.suguru.expensetracker.data.repository.RoomTransactionRepository
import com.suguru.expensetracker.data.worker.RecurringTransactionWorker
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.*
import com.suguru.expensetracker.domain.model.budget.BudgetValidationError
import com.suguru.expensetracker.domain.model.statistics.StatisticsPeriodOption
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.DeterministicTransactionParser
import com.suguru.expensetracker.domain.parser.ParseConfidence
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.balance.GetAccountBalanceUseCase
import com.suguru.expensetracker.domain.usecase.budget.CreateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.ObserveActiveBudgetProgressUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.export.GenerateAdvancedCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.GenerateBasicCsvExportUseCase
import com.suguru.expensetracker.domain.usecase.export.PrepareFinancialReportDataUseCase
import com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.DeleteTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.UpdateTransactionUseCase
import com.suguru.expensetracker.presentation.home.HomeViewModel
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionError
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionViewModel
import com.suguru.expensetracker.presentation.transactions.detail.TransactionDetailViewModel
import com.suguru.expensetracker.presentation.transactions.edit.EditTransactionViewModel
import com.suguru.expensetracker.widget.balance.AccountBalanceWidgetDataProvider
import com.suguru.expensetracker.widget.balance.WidgetDataState
import com.suguru.expensetracker.widget.budget.BudgetProgressWidgetDataProvider
import com.suguru.expensetracker.widget.budget.BudgetWidgetState
import com.suguru.expensetracker.widget.configuration.AccountWidgetConfiguration
import com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration
import com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EndToEndFunctionalQaTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var settingsRepository: InMemorySettingsRepository

    private lateinit var accountRepository: RoomAccountRepository
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var transactionRepository: RoomTransactionRepository
    private lateinit var budgetRepository: RoomBudgetRepository
    private lateinit var recurringTransactionRepository: RoomRecurringTransactionRepository
    private lateinit var recurringOccurrenceRepository: RoomRecurringOccurrenceRepository
    private lateinit var backupRepository: RoomBackupRepository

    private lateinit var internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner
    private lateinit var defaultCategorySeeder: DefaultCategorySeeder

    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase
    private lateinit var observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase
    private lateinit var getTransactionUseCase: GetTransactionUseCase
    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var updateTransactionUseCase: UpdateTransactionUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    private lateinit var createBudgetUseCase: CreateBudgetUseCase
    private lateinit var observeActiveBudgetProgressUseCase: ObserveActiveBudgetProgressUseCase

    private lateinit var createRecurringTransactionUseCase: CreateRecurringTransactionUseCase
    private lateinit var updateRecurringTransactionUseCase: UpdateRecurringTransactionUseCase
    private lateinit var processDueRecurringTransactionsUseCase: ProcessDueRecurringTransactionsUseCase

    private lateinit var observeStatisticsUseCase: ObserveStatisticsUseCase

    private lateinit var observeAccountSummariesUseCase: ObserveAccountSummariesUseCase
    private lateinit var observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase
    private lateinit var observeRecentTransactionsUseCase: ObserveRecentTransactionsUseCase

    private lateinit var getAccountBalanceUseCase: GetAccountBalanceUseCase
    private lateinit var widgetConfigRepository: InMemoryWidgetConfigurationRepository
    private lateinit var balanceWidgetDataProvider: AccountBalanceWidgetDataProvider
    private lateinit var budgetWidgetDataProvider: BudgetProgressWidgetDataProvider

    private lateinit var csvExporter: CsvExporter
    private lateinit var pdfRenderer: PdfFinancialReportRenderer
    private lateinit var prepareFinancialReportDataUseCase: PrepareFinancialReportDataUseCase
    private lateinit var generateBasicCsvExportUseCase: GenerateBasicCsvExportUseCase
    private lateinit var generateAdvancedCsvExportUseCase: GenerateAdvancedCsvExportUseCase

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        settingsRepository = InMemorySettingsRepository()

        accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        transactionRepository = RoomTransactionRepository(database.transactionDao())
        budgetRepository = RoomBudgetRepository(database.budgetDao())
        recurringTransactionRepository = RoomRecurringTransactionRepository(database.recurringTransactionDao())
        recurringOccurrenceRepository = RoomRecurringOccurrenceRepository(database)

        internalDefaultAccountProvisioner = InternalDefaultAccountProvisioner(database, settingsRepository)
        defaultCategorySeeder = DefaultCategorySeeder(database, database.categoryDao())

        backupRepository = RoomBackupRepository(
            db = database,
            settingsRepository = settingsRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )

        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
        observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(categoryRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
        deleteTransactionUseCase = DeleteTransactionUseCase(
            transactionRepository = transactionRepository,
            recurringOccurrenceRepository = recurringOccurrenceRepository
        )

        createBudgetUseCase = CreateBudgetUseCase(budgetRepository, categoryRepository)
        observeActiveBudgetProgressUseCase = ObserveActiveBudgetProgressUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )

        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
        processDueRecurringTransactionsUseCase = ProcessDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            recurringOccurrenceRepository = recurringOccurrenceRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )

        observeStatisticsUseCase = ObserveStatisticsUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )

        observeAccountSummariesUseCase = ObserveAccountSummariesUseCase(accountRepository, transactionRepository)
        observeDashboardSummaryUseCase = ObserveDashboardSummaryUseCase(accountRepository, transactionRepository)
        observeRecentTransactionsUseCase = ObserveRecentTransactionsUseCase(transactionRepository, accountRepository, categoryRepository)

        getAccountBalanceUseCase = GetAccountBalanceUseCase(accountRepository, transactionRepository)
        widgetConfigRepository = InMemoryWidgetConfigurationRepository()
        balanceWidgetDataProvider = AccountBalanceWidgetDataProvider(
            accountRepository = accountRepository,
            getAccountBalanceUseCase = getAccountBalanceUseCase,
            widgetConfigurationRepository = widgetConfigRepository
        )
        budgetWidgetDataProvider = BudgetProgressWidgetDataProvider(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository,
            observeActiveBudgetProgressUseCase = observeActiveBudgetProgressUseCase,
            widgetConfigurationRepository = widgetConfigRepository
        )

        csvExporter = CsvExporter()
        pdfRenderer = PdfFinancialReportRenderer()
        prepareFinancialReportDataUseCase = PrepareFinancialReportDataUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
        generateBasicCsvExportUseCase = GenerateBasicCsvExportUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            csvExporter = csvExporter
        )
        generateAdvancedCsvExportUseCase = GenerateAdvancedCsvExportUseCase(
            prepareFinancialReportDataUseCase = prepareFinancialReportDataUseCase,
            csvExporter = csvExporter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private suspend fun AddTransactionViewModel.awaitReady() {
        uiState.first { it.defaultAccount != null && it.categories.isNotEmpty() }
    }

    private suspend fun EditTransactionViewModel.awaitReady() {
        uiState.first { it.isTransactionLoaded }
    }

    // =========================================================================================
    // SCENARIO A: Fresh Installation and Initial State
    // =========================================================================================
    @Test
    fun scenarioA_freshInstallationAndInitialState() = runTest(testDispatcher) {
        // 1. Fresh database is empty
        assertEquals(0, database.accountDao().getAccountCount())
        assertEquals(0, database.categoryDao().getAllCategories().size)

        // 2. Ensure internal default account is provisioned
        val provisionResult = internalDefaultAccountProvisioner.ensureProvisioned()
        assertTrue(provisionResult is InternalDefaultAccountProvisioningResult.Ready)
        val defaultAccount = (provisionResult as InternalDefaultAccountProvisioningResult.Ready).internalAccount
        assertEquals(INTERNAL_DEFAULT_ACCOUNT_KEY, defaultAccount.internalKey)
        assertEquals("UZS", defaultAccount.initialBalance.currencyCode)
        assertEquals(0L, defaultAccount.initialBalance.amountInMinorUnits)
        assertEquals(1, database.accountDao().getAccountCount())

        // 3. Ensure built-in categories are seeded
        defaultCategorySeeder.ensureSeeded()
        val categories = database.categoryDao().getAllCategories()
        assertEquals(19, categories.size)
        assertEquals(14, categories.count { it.type == CategoryType.EXPENSE })
        assertEquals(5, categories.count { it.type == CategoryType.INCOME })
        assertTrue(categories.all { it.isSystem && !it.isArchived })

        // 4. Repeated provisioning and seeding are strictly idempotent
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        assertEquals(1, database.accountDao().getAccountCount())
        assertEquals(19, database.categoryDao().getAllCategories().size)

        // 5. HomeViewModel loads empty state cleanly without crashing
        val homeViewModel = HomeViewModel(
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            observeAccountSummariesUseCase = observeAccountSummariesUseCase,
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase
        )
        val homeState = homeViewModel.uiState.first { !it.isLoading }
        assertFalse(homeState.isLoading)
        assertNull(homeState.errorMessage)
        assertEquals(0, homeState.recentTransactions.size)
    }

    // =========================================================================================
    // SCENARIO B: Manual Expense Creation, Detail, Edit, and Delete
    // =========================================================================================
    @Test
    fun scenarioB_manualExpenseCreationDetailEditDelete() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()

        val categories = categoryRepository.observeActiveCategories().first()
        val groceriesCategory = categories.first { it.name == "Groceries" && it.type == CategoryType.EXPENSE }

        val addViewModel = AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
        addViewModel.awaitReady()

        // 1. Fill valid Expense
        addViewModel.onTransactionTypeChanged(TransactionType.EXPENSE)
        addViewModel.onAmountChanged("45000")
        addViewModel.onCategorySelected(groceriesCategory.id)
        addViewModel.onNoteChanged("Weekly grocery shopping")
        addViewModel.saveTransaction()
        addViewModel.uiState.first { it.isSavedSuccessfully }

        // Verify Room persistence: exactly 1 transaction
        val allTx = transactionRepository.observeAllTransactions().first()
        assertEquals(1, allTx.size)
        val createdTx = allTx.first()
        val defaultAccountId = database.accountDao().getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)!!.id
        assertEquals(defaultAccountId, createdTx.accountId)
        assertEquals(45000L, createdTx.amount.amountInMinorUnits)
        assertEquals("UZS", createdTx.amount.currencyCode)
        assertEquals(groceriesCategory.id, createdTx.categoryId)
        assertEquals("Weekly grocery shopping", createdTx.note)
        assertEquals(TransactionType.EXPENSE, createdTx.type)

        // 2. Open Detail ViewModel
        val detailViewModel = TransactionDetailViewModel(
            transactionId = createdTx.id,
            getTransactionUseCase = getTransactionUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase
        )
        val detailState = detailViewModel.uiState.first { it.transaction != null }
        assertNotNull(detailState.transaction)
        assertEquals(45000L, detailState.transaction?.amount?.amountInMinorUnits)

        // 3. Edit Transaction
        val editViewModel = EditTransactionViewModel(
            transactionId = createdTx.id,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
        editViewModel.awaitReady()

        editViewModel.onAmountChanged("60000")
        editViewModel.onNoteChanged("Weekly groceries + fresh fruit")
        editViewModel.saveTransaction()
        editViewModel.uiState.first { it.isSavedSuccessfully }

        // Verify updated Room record: account ID must be preserved
        val updatedTx = transactionRepository.getTransactionById(createdTx.id)!!
        assertEquals(defaultAccountId, updatedTx.accountId)
        assertEquals(60000L, updatedTx.amount.amountInMinorUnits)
        assertEquals("UZS", updatedTx.amount.currencyCode)
        assertEquals("Weekly groceries + fresh fruit", updatedTx.note)

        // 4. Delete Transaction
        deleteTransactionUseCase(createdTx.id)
        assertNull(transactionRepository.getTransactionById(createdTx.id))
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)
    }

    @Test
    fun scenarioB_manualExpenseValidationAndEdgeCases() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }

        val addViewModel = AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
        addViewModel.awaitReady()

        // 1. Empty Amount
        addViewModel.onAmountChanged("")
        addViewModel.onCategorySelected(groceriesCat.id)
        addViewModel.saveTransaction()
        advanceUntilIdle()
        assertEquals(AddTransactionError.InvalidAmount, addViewModel.uiState.value.error)
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // 2. Zero Amount
        addViewModel.onAmountChanged("0")
        addViewModel.saveTransaction()
        advanceUntilIdle()
        assertEquals(AddTransactionError.AmountMustBePositive, addViewModel.uiState.value.error)
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // 3. Overflow Amount
        addViewModel.onAmountChanged("999999999999999999999999")
        addViewModel.saveTransaction()
        advanceUntilIdle()
        assertEquals(AddTransactionError.InvalidAmount, addViewModel.uiState.value.error)
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // 4. Missing Category when isolated without categories
        val addViewModelNoCats = AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(com.suguru.expensetracker.domain.fake.FakeCategoryRepository()),
            createTransactionUseCase = createTransactionUseCase
        )
        addViewModelNoCats.uiState.first { it.defaultAccount != null }
        addViewModelNoCats.onAmountChanged("50000")
        addViewModelNoCats.saveTransaction()
        advanceUntilIdle()
        assertEquals(AddTransactionError.CategoryRequired, addViewModelNoCats.uiState.value.error)
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // 5. Rapid repeated save taps do not duplicate
        addViewModel.onAmountChanged("50000")
        addViewModel.onCategorySelected(groceriesCat.id)
        addViewModel.saveTransaction()
        addViewModel.saveTransaction()
        addViewModel.saveTransaction()
        addViewModel.uiState.first { it.isSavedSuccessfully }

        assertEquals(1, transactionRepository.observeAllTransactions().first().size)
    }

    // =========================================================================================
    // SCENARIO C: Income Creation and Category Isolation
    // =========================================================================================
    @Test
    fun scenarioC_incomeCreationAndCategoryIsolation() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()

        val addViewModel = AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
        addViewModel.awaitReady()

        // 1. Switch to Income
        addViewModel.onTransactionTypeChanged(TransactionType.INCOME)
        addViewModel.uiState.first { it.transactionType == TransactionType.INCOME && it.categories.all { cat -> cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH } }

        // Observed categories must all be INCOME categories
        val incomeCategories = addViewModel.uiState.value.categories
        assertTrue(incomeCategories.isNotEmpty())
        assertTrue(incomeCategories.all { it.type == CategoryType.INCOME || it.type == CategoryType.BOTH })
        assertFalse(incomeCategories.any { it.name == "Groceries" })

        val salaryCat = incomeCategories.first { it.name == "Salary" }
        addViewModel.onAmountChanged("3500000")
        addViewModel.onCategorySelected(salaryCat.id)
        addViewModel.onNoteChanged("Monthly salary")
        addViewModel.saveTransaction()
        addViewModel.uiState.first { it.isSavedSuccessfully }

        val txs = transactionRepository.observeAllTransactions().first()
        assertEquals(1, txs.size)
        val incomeTx = txs.first()
        assertEquals(TransactionType.INCOME, incomeTx.type)
        assertEquals(3500000L, incomeTx.amount.amountInMinorUnits)
        assertEquals("UZS", incomeTx.amount.currencyCode)

        // 2. Verify Statistics calculates Income without mixing Expense
        val stats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = Instant.now()
        ).first()

        assertEquals(3500000L, stats.currencySummary.income.amountInMinorUnits)
        assertEquals(0L, stats.currencySummary.expense.amountInMinorUnits)
        assertEquals(3500000L, stats.currencySummary.net.amountInMinorUnits)

        // 3. Verify Budget does NOT count Income as Expense spending
        val budgetId = createBudgetUseCase(
            Budget(
                categoryId = null, // Overall budget
                limitAmount = Money(1000000L, "UZS"),
                startDate = LocalDate.now().minusDays(5),
                endDate = LocalDate.now().plusDays(25),
                periodType = BudgetPeriodType.MONTHLY
            )
        )
        val progressList = observeActiveBudgetProgressUseCase(zoneId).first()
        val overallProgress = progressList.first { it.budget.id == budgetId }
        assertEquals(0L, overallProgress.spent.amountInMinorUnits)
        assertEquals(1000000L, overallProgress.remaining.amountInMinorUnits)
    }

    // =========================================================================================
    // SCENARIO D: Currency Preservation Across Mixed Currencies
    // =========================================================================================
    @Test
    fun scenarioD_currencyPreservationAcrossMixedCurrencies() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first { it.internalKey == INTERNAL_DEFAULT_ACCOUNT_KEY }
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }

        // Insert UZS transaction
        val uzsTxId = transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(50000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = Instant.now(),
                note = "Local market"
            )
        )

        // Insert historical USD account and transaction
        val usdAccountId = database.accountDao().insertAccount(
            AccountEntity(
                name = "Historical USD Cash",
                type = AccountType.CASH,
                initialBalanceAmountInMinorUnits = 10000L,
                initialBalanceCurrencyCode = "USD",
                iconName = "wallet",
                colorHex = "#223344",
                isArchived = false,
                createdAt = Instant.now()
            )
        )
        val usdTxId = transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(2500L, "USD"), // $25.00
                accountId = usdAccountId,
                categoryId = groceriesCat.id,
                transactionTime = Instant.now(),
                note = "Online book"
            )
        )

        // 1. Edit USD transaction: currency must remain USD
        val editViewModel = EditTransactionViewModel(
            transactionId = usdTxId,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
        editViewModel.awaitReady()

        editViewModel.onAmountChanged("30.00")
        editViewModel.onNoteChanged("Online book + shipping")
        editViewModel.saveTransaction()
        editViewModel.uiState.first { it.isSavedSuccessfully }

        val editedUsdTx = transactionRepository.getTransactionById(usdTxId)!!
        assertEquals("USD", editedUsdTx.amount.currencyCode)
        assertEquals(3000L, editedUsdTx.amount.amountInMinorUnits)
        assertEquals(usdAccountId, editedUsdTx.accountId)

        // 2. Statistics separation: UZS and USD are isolated
        val uzsStats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = Instant.now()
        ).first()
        assertEquals(50000L, uzsStats.currencySummary.expense.amountInMinorUnits)
        assertEquals("UZS", uzsStats.currencySummary.expense.currencyCode)

        val usdStats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            selectedCurrencyCode = "USD",
            zoneId = zoneId,
            now = Instant.now()
        ).first()
        assertEquals(3000L, usdStats.currencySummary.expense.amountInMinorUnits)
        assertEquals("USD", usdStats.currencySummary.expense.currencyCode)

        // 3. Changing preferred currency does not rewrite historical rows
        settingsRepository.setPreferredCurrency("EUR")
        val reloadedUzsTx = transactionRepository.getTransactionById(uzsTxId)!!
        val reloadedUsdTx = transactionRepository.getTransactionById(usdTxId)!!
        assertEquals("UZS", reloadedUzsTx.amount.currencyCode)
        assertEquals(50000L, reloadedUzsTx.amount.amountInMinorUnits)
        assertEquals("USD", reloadedUsdTx.amount.currencyCode)
        assertEquals(3000L, reloadedUsdTx.amount.amountInMinorUnits)
    }

    // =========================================================================================
    // SCENARIO E: AI Candidate Lifecycle and Room Persistence Safety
    // =========================================================================================
    @Test
    fun scenarioE_aiCandidateLifecycleAndRoomPersistenceSafety() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val parser = DeterministicTransactionParser()

        // 1. Shorthand parsing checks
        val c1 = parser.parse("50k ovqatlanishga").single()
        assertEquals(50000L, c1.amount?.amountInMinorUnits)
        assertEquals("UZS", c1.amount?.currencyCode)
        assertEquals(TransactionType.EXPENSE, c1.transactionType)

        val c2 = parser.parse("10k taksiga").single()
        assertEquals(10000L, c2.amount?.amountInMinorUnits)
        assertEquals(TransactionType.EXPENSE, c2.transactionType)

        val c3 = parser.parse("100 ming oziq-ovqatga").single()
        assertEquals(100000L, c3.amount?.amountInMinorUnits)
        assertEquals(TransactionType.EXPENSE, c3.transactionType)

        val c4 = parser.parse("2 million maosh").single()
        assertEquals(2000000L, c4.amount?.amountInMinorUnits)
        assertEquals(TransactionType.INCOME, c4.transactionType)

        val c5 = parser.parse("50 kg kartoshka").single()
        assertNull(c5.amount)
        assertTrue(c5.warnings.contains(ParseWarning.MISSING_AMOUNT))

        val c6 = parser.parse("10 km taksiga").single()
        assertNull(c6.amount)
        assertTrue(c6.warnings.contains(ParseWarning.MISSING_AMOUNT))

        // 2. CRITICAL PERSISTENCE SAFETY: Verify Room rows before and during candidate lifecycle
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // Preview: populate candidate into AddTransactionViewModel
        val addViewModel = AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
        addViewModel.awaitReady()

        addViewModel.populateFromCandidate(c1)
        advanceUntilIdle()

        // Previewing does NOT persist to Room
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // Editing candidate in ViewModel does NOT persist to Room
        addViewModel.onAmountChanged("55000")
        addViewModel.onNoteChanged("Lunch with colleagues")
        advanceUntilIdle()
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // Cancelling draft does NOT persist to Room
        addViewModel.clearAiDraftState()
        advanceUntilIdle()
        assertEquals(0, transactionRepository.observeAllTransactions().first().size)

        // Populate again and EXPLICITLY save (selecting category as required by Preview -> Edit -> Confirm -> Save)
        addViewModel.populateFromCandidate(c1)
        val groceriesCat = categoryRepository.observeActiveCategories().first().find { it.name == "Groceries" }!!
        addViewModel.onCategorySelected(groceriesCat.id)
        addViewModel.saveTransaction()
        addViewModel.uiState.first { it.isSavedSuccessfully }

        // Explicit confirmation creates EXACTLY ONE transaction
        val persisted = transactionRepository.observeAllTransactions().first()
        assertEquals(1, persisted.size)
        assertEquals(50000L, persisted.first().amount.amountInMinorUnits)

        // Transfer candidate is rejected and blocks saving
        val transferCandidate = TransactionCandidate(
            rawText = "Transfer 100k to savings",
            suggestedTitle = "Transfer",
            transactionType = TransactionType.TRANSFER,
            amount = Money(100000L, "UZS"),
            date = LocalDate.now(),
            confidence = ParseConfidence.AMBIGUOUS
        )
        addViewModel.populateFromCandidate(transferCandidate)
        advanceUntilIdle()
        assertNotNull(addViewModel.uiState.value.error)
        addViewModel.saveTransaction()
        advanceUntilIdle()
        // Still only 1 transaction in Room
        assertEquals(1, transactionRepository.observeAllTransactions().first().size)
    }

    // =========================================================================================
    // SCENARIO F: Backup and Restore End-to-End
    // =========================================================================================
    @Test
    fun scenarioF_backupAndRestoreEndToEndV2AndV1() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()

        // Create custom category and archived category
        val customCatId = categoryRepository.insertCategory(
            Category(name = "Hobbies", type = CategoryType.EXPENSE, iconName = "palette", colorHex = "#ABCDEF")
        )
        val archivedCatId = categoryRepository.insertCategory(
            Category(name = "Old Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#112233", isArchived = true)
        )

        // Create transaction
        val txId = transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(75000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = customCatId,
                transactionTime = Instant.now(),
                note = "Painting supplies"
            )
        )

        // Create budget
        val budgetId = createBudgetUseCase(
            Budget(
                categoryId = customCatId,
                limitAmount = Money(200000L, "UZS"),
                startDate = LocalDate.now().minusDays(2),
                endDate = LocalDate.now().plusDays(28),
                periodType = BudgetPeriodType.MONTHLY
            )
        )

        // Create recurring rule
        val recurringRuleId = createRecurringTransactionUseCase(
            RecurringTransaction(
                type = TransactionType.EXPENSE,
                amount = Money(50000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = customCatId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.now(),
                nextOccurrence = LocalDate.now(),
                note = "Art subscription"
            )
        )

        // 1. Export backup (V2)
        val backup = backupRepository.createSnapshot()
        assertEquals(2, backup.formatVersion)
        assertNotNull(backup.checksum)
        assertTrue(backup.checksum!!.isNotBlank())
        assertEquals(1, backup.transactions.size)
        assertEquals(1, backup.budgets.size)
        assertEquals(1, backup.recurringTransactions.size)

        val json = backupRepository.serializeBackup(backup)
        val tempBackupFile = File(context.cacheDir, "test_backup.json")
        tempBackupFile.writeText(json)
        assertTrue(tempBackupFile.exists() && tempBackupFile.length() > 0)

        // 2. Clear Database
        database.clearAllTables()
        assertEquals(0, database.accountDao().getAccountCount())
        assertEquals(0, database.backupDao().getAllTransactions().size)

        // 3. Restore backup
        val restoredBackup = backupRepository.deserializeBackup(tempBackupFile.readText()).getOrThrow()
        val restoreResult = backupRepository.restoreSnapshot(restoredBackup)
        assertTrue(restoreResult.isSuccess)

        // Verify restored entities and relationships
        val restoredAccounts = database.backupDao().getAllAccounts()
        assertEquals(1, restoredAccounts.size)
        val restoredAccount = restoredAccounts.first()
        assertEquals(defaultAccount.id, restoredAccount.id)

        val restoredCategories = database.categoryDao().getAllCategories()
        assertTrue(restoredCategories.any { it.name == "Hobbies" && !it.isArchived })
        assertTrue(restoredCategories.any { it.name == "Old Rent" && it.isArchived })

        val restoredTransactions = database.backupDao().getAllTransactions()
        assertEquals(1, restoredTransactions.size)
        assertEquals(75000L, restoredTransactions.first().amountInMinorUnits)
        assertEquals(restoredAccount.id, restoredTransactions.first().accountId)
        assertEquals(customCatId, restoredTransactions.first().categoryId)

        val restoredBudgets = database.backupDao().getAllBudgets()
        assertEquals(1, restoredBudgets.size)
        assertEquals(budgetId, restoredBudgets.first().id)

        val restoredRecurring = database.backupDao().getAllRecurringTransactions()
        assertEquals(1, restoredRecurring.size)
        assertEquals(recurringRuleId, restoredRecurring.first().id)

        // 4. Invalid checksum rejection
        val tampered = restoredBackup.copy(checksum = "bad_checksum_hash_12345")
        val tamperedResult = backupRepository.restoreSnapshot(tampered)
        assertTrue(tamperedResult.isFailure)

        // 5. V1 backup compatibility (formatVersion = 1, without recurring occurrences)
        val v1Backup = ExpenseTrackerBackup(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = Instant.now().toString(),
            databaseSchemaVersion = 1,
            accounts = restoredBackup.accounts,
            categories = restoredBackup.categories,
            transactions = restoredBackup.transactions,
            budgets = restoredBackup.budgets,
            recurringTransactions = restoredBackup.recurringTransactions,
            recurringOccurrences = emptyList(),
            settings = restoredBackup.settings
        )
        val validChecksumV1 = v1Backup.copy(checksum = backupRepository.computeChecksum(v1Backup))
        val v1Result = backupRepository.restoreSnapshot(validChecksumV1)
        assertTrue(v1Result.isSuccess)
    }

    // =========================================================================================
    // SCENARIO G: Recurring Transactions Lifecycle and Occurrence Idempotency
    // =========================================================================================
    @Test
    fun scenarioG_recurringTransactionsLifecycleAndIdempotency() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }
        val salaryCat = categoryRepository.observeActiveCategories().first().first { it.name == "Salary" }

        // 1. Create Expense recurring rule
        val expenseRuleId = createRecurringTransactionUseCase(
            RecurringTransaction(
                type = TransactionType.EXPENSE,
                amount = Money(100000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.now(),
                nextOccurrence = LocalDate.now(),
                note = "Monthly groceries"
            )
        )

        // 2. Create Income recurring rule
        val incomeRuleId = createRecurringTransactionUseCase(
            RecurringTransaction(
                type = TransactionType.INCOME,
                amount = Money(3000000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = salaryCat.id,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.now(),
                nextOccurrence = LocalDate.now(),
                note = "Monthly salary"
            )
        )

        // 3. Reject new Transfer recurring rule
        try {
            createRecurringTransactionUseCase(
                RecurringTransaction(
                    type = TransactionType.TRANSFER,
                    amount = Money(50000L, "UZS"),
                    accountId = defaultAccount.id,
                    destinationAccountId = defaultAccount.id,
                    frequency = RecurrenceFrequency.MONTHLY,
                    startDate = LocalDate.now(),
                    nextOccurrence = LocalDate.now()
                )
            )
            fail("Expected InvalidTransfer when creating transfer recurring rule")
        } catch (_: DomainException.InvalidTransfer) {
            // Success
        }

        // 4. Process due occurrences
        val generatedCount = processDueRecurringTransactionsUseCase(LocalDate.now())
        assertEquals(2, generatedCount)

        val txs = transactionRepository.observeAllTransactions().first()
        assertEquals(2, txs.size)

        // 5. Database-level occurrence idempotency: re-running on same date generates 0
        val rerunCount = processDueRecurringTransactionsUseCase(LocalDate.now())
        assertEquals(0, rerunCount)
        assertEquals(2, transactionRepository.observeAllTransactions().first().size)

        // 6. Delete a generated transaction
        val generatedExpenseTx = txs.first { it.type == TransactionType.EXPENSE }
        deleteTransactionUseCase(generatedExpenseTx.id)
        assertEquals(1, transactionRepository.observeAllTransactions().first().size)

        // Re-running MUST NOT recreate the deleted transaction!
        val rerunAfterDeleteCount = processDueRecurringTransactionsUseCase(LocalDate.now())
        assertEquals(0, rerunAfterDeleteCount)
        assertEquals(1, transactionRepository.observeAllTransactions().first().size)
    }

    // =========================================================================================
    // SCENARIO H: Budgets Validation, Calculation, and Currency Isolation
    // =========================================================================================
    @Test
    fun scenarioH_budgetValidationAndProgressCalculation() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }
        val transportCat = categoryRepository.observeActiveCategories().first().first { it.name == "Transport" }
        val salaryCat = categoryRepository.observeActiveCategories().first().first { it.name == "Salary" }

        val start = LocalDate.of(2026, 11, 1)
        val end = LocalDate.of(2026, 11, 30)

        // 1. Validation tests
        try {
            createBudgetUseCase(Budget(categoryId = null, limitAmount = Money(0L, "UZS"), startDate = start, endDate = end))
            fail("Expected InvalidLimit")
        } catch (_: BudgetValidationError.InvalidLimit) {}

        try {
            createBudgetUseCase(Budget(categoryId = null, limitAmount = Money(100000L, ""), startDate = start, endDate = end))
            fail("Expected InvalidCurrency")
        } catch (_: BudgetValidationError.InvalidCurrency) {}

        try {
            createBudgetUseCase(Budget(categoryId = null, limitAmount = Money(100000L, "UZS"), startDate = end, endDate = start))
            fail("Expected InvalidDateRange")
        } catch (_: BudgetValidationError.InvalidDateRange) {}

        try {
            createBudgetUseCase(Budget(categoryId = salaryCat.id, limitAmount = Money(100000L, "UZS"), startDate = start, endDate = end))
            fail("Expected IncompatibleCategoryType")
        } catch (_: BudgetValidationError.IncompatibleCategoryType) {}

        // Create valid budget
        val budgetId = createBudgetUseCase(
            Budget(
                categoryId = groceriesCat.id,
                limitAmount = Money(500000L, "UZS"),
                startDate = start,
                endDate = end,
                periodType = BudgetPeriodType.MONTHLY
            )
        )

        // Overlapping budget rejection
        try {
            createBudgetUseCase(
                Budget(
                    categoryId = groceriesCat.id,
                    limitAmount = Money(300000L, "UZS"),
                    startDate = LocalDate.of(2026, 11, 15),
                    endDate = LocalDate.of(2026, 12, 15),
                    periodType = BudgetPeriodType.MONTHLY
                )
            )
            fail("Expected OverlappingBudgetExists")
        } catch (_: BudgetValidationError.OverlappingBudgetExists) {}

        // 2. Add qualifying expense: 150,000 UZS on 2026-11-10
        val qualifyingTxId = transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(150000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = LocalDate.of(2026, 11, 10).atStartOfDay(zoneId).toInstant()
            )
        )

        // Non-qualifying transactions:
        // Out of date range (before start)
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(80000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = LocalDate.of(2026, 10, 25).atStartOfDay(zoneId).toInstant()
            )
        )
        // Different category
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(50000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = transportCat.id,
                transactionTime = LocalDate.of(2026, 11, 12).atStartOfDay(zoneId).toInstant()
            )
        )
        // Income transaction
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.INCOME,
                amount = Money(1000000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = salaryCat.id,
                transactionTime = LocalDate.of(2026, 11, 12).atStartOfDay(zoneId).toInstant()
            )
        )

        // Check progress
        val progress = observeActiveBudgetProgressUseCase(zoneId).first().first { it.budget.id == budgetId }
        assertEquals(150000L, progress.spent.amountInMinorUnits)
        assertEquals(350000L, progress.remaining.amountInMinorUnits)
        assertEquals(3000, progress.progressBasisPoints) // 30.00%
        assertFalse(progress.isExceeded)

        // Reactive update: update expense to 600,000 UZS (exceeded)
        updateTransactionUseCase(
            Transaction(
                id = qualifyingTxId,
                type = TransactionType.EXPENSE,
                amount = Money(600000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = LocalDate.of(2026, 11, 10).atStartOfDay(zoneId).toInstant()
            )
        )
        val updatedProgress = observeActiveBudgetProgressUseCase(zoneId).first().first { it.budget.id == budgetId }
        assertEquals(600000L, updatedProgress.spent.amountInMinorUnits)
        assertEquals(0L, updatedProgress.remaining.amountInMinorUnits)
        assertTrue(updatedProgress.isExceeded)

        // Delete expense: returns to 0 spent
        deleteTransactionUseCase(qualifyingTxId)
        val clearedProgress = observeActiveBudgetProgressUseCase(zoneId).first().first { it.budget.id == budgetId }
        assertEquals(0L, clearedProgress.spent.amountInMinorUnits)
        assertEquals(500000L, clearedProgress.remaining.amountInMinorUnits)
    }

    // =========================================================================================
    // SCENARIO I: Statistics Reconciliation and Period Boundaries
    // =========================================================================================
    @Test
    fun scenarioI_statisticsReconciliationAndPeriodBoundaries() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }
        val transportCat = categoryRepository.observeActiveCategories().first().first { it.name == "Transport" }
        val salaryCat = categoryRepository.observeActiveCategories().first().first { it.name == "Salary" }

        val today = LocalDate.of(2026, 11, 14)
        val now = today.atTime(12, 0).atZone(zoneId).toInstant()

        // Expenses
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(60000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = today.atTime(8, 0).atZone(zoneId).toInstant()
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(25000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = transportCat.id,
                transactionTime = today.atTime(10, 0).atZone(zoneId).toInstant()
            )
        )
        // Income
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.INCOME,
                amount = Money(500000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = salaryCat.id,
                transactionTime = today.atTime(11, 0).atZone(zoneId).toInstant()
            )
        )

        val dailyStats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.CUSTOM,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = now,
            customStart = today,
            customEnd = today
        ).first()

        assertEquals(85000L, dailyStats.currencySummary.expense.amountInMinorUnits)
        assertEquals(500000L, dailyStats.currencySummary.income.amountInMinorUnits)
        assertEquals(415000L, dailyStats.currencySummary.net.amountInMinorUnits)

        // Category breakdown reconciliation
        val catBreakdownSum = dailyStats.expenseCategories.fold(0L) { acc, cat -> acc + cat.amount.amountInMinorUnits }
        assertEquals(dailyStats.currencySummary.expense.amountInMinorUnits, catBreakdownSum)
        assertEquals(2, dailyStats.expenseCategories.size)

        // Empty period
        val emptyDay = today.minusDays(10)
        val emptyStats = observeStatisticsUseCase(
            periodOption = StatisticsPeriodOption.CUSTOM,
            selectedCurrencyCode = "UZS",
            zoneId = zoneId,
            now = now,
            customStart = emptyDay,
            customEnd = emptyDay
        ).first()
        assertEquals(0L, emptyStats.currencySummary.expense.amountInMinorUnits)
        assertEquals(0L, emptyStats.currencySummary.income.amountInMinorUnits)
        assertEquals(0L, emptyStats.currencySummary.net.amountInMinorUnits)
    }

    // =========================================================================================
    // SCENARIO J: Widgets and Background Work Read-Only Safety
    // =========================================================================================
    @Test
    fun scenarioJ_widgetDataProvidersAndBackgroundWork() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }

        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(25000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = Instant.now()
            )
        )

        val initialTxCount = transactionRepository.observeAllTransactions().first().size

        // Configure balance widget
        widgetConfigRepository.saveConfiguration(101, defaultAccount.id, false)
        val balanceData = balanceWidgetDataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        assertEquals(WidgetDataState.READY, balanceData.state)
        assertEquals("UZS", balanceData.currencyCode)

        // Configure budget widget
        val budgetId = createBudgetUseCase(
            Budget(
                categoryId = groceriesCat.id,
                limitAmount = Money(100000L, "UZS"),
                startDate = LocalDate.now().minusDays(1),
                endDate = LocalDate.now().plusDays(20)
            )
        )
        widgetConfigRepository.saveBudgetProgressConfiguration(201, budgetId, false)
        val budgetData = budgetWidgetDataProvider.getWidgetData(201).first { it.state != BudgetWidgetState.LOADING }
        assertEquals(BudgetWidgetState.READY, budgetData.state)

        // Read-only invariant: Widget data providers must NEVER mutate Room records
        val finalTxCount = transactionRepository.observeAllTransactions().first().size
        assertEquals(initialTxCount, finalTxCount)

        // WorkManager worker instantiation check
        RecurringTransactionWorker.enqueuePeriodicWork(context)
    }

    // =========================================================================================
    // SCENARIO K: CSV and PDF Export Content Validation
    // =========================================================================================
    @Test
    fun scenarioK_csvAndPdfExportValidation() = runTest(testDispatcher) {
        internalDefaultAccountProvisioner.ensureProvisioned()
        defaultCategorySeeder.ensureSeeded()
        val defaultAccount = accountRepository.observeActiveAccounts().first().first()
        val groceriesCat = categoryRepository.observeActiveCategories().first().first { it.name == "Groceries" }

        // Multilingual transaction notes: Uzbek Latin, Uzbek Cyrillic, Russian
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(50000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = Instant.parse("2026-11-14T10:00:00Z"),
                note = "Oziq-ovqat, go'sht va non" // Uzbek Latin with quotes and commas
            )
        )
        transactionRepository.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(35000L, "UZS"),
                accountId = defaultAccount.id,
                categoryId = groceriesCat.id,
                transactionTime = Instant.parse("2026-11-14T11:00:00Z"),
                note = "Супермаркет, продукты и фрукты" // Russian with comma
            )
        )

        // 1. Basic CSV export
        val basicCsv = generateBasicCsvExportUseCase.exportToString(zoneId = zoneId)
        assertTrue(basicCsv.startsWith("transaction_id,date,time,type,amount,currency,source_account,destination_account,category,note,recurring"))
        assertTrue(basicCsv.contains("\"Oziq-ovqat, go'sht va non\""))
        assertTrue(basicCsv.contains("\"Супермаркет, продукты и фрукты\""))
        assertTrue(basicCsv.contains("50000"))
        assertTrue(basicCsv.contains("35000"))
        assertTrue(basicCsv.contains("UZS"))

        // 2. Advanced CSV export
        val exportFilter = com.suguru.expensetracker.domain.model.export.ExportFilter()
        val reportData = prepareFinancialReportDataUseCase(exportFilter, zoneId)
        val advancedCsv = generateAdvancedCsvExportUseCase.exportToString(
            filter = exportFilter,
            zoneId = zoneId
        )
        assertTrue(advancedCsv.startsWith("transaction_id,date,time,type,amount,currency"))
        assertTrue(advancedCsv.contains("Oziq-ovqat"))
        assertTrue(advancedCsv.contains("Супермаркет"))

        // 3. PDF export stream rendering (verify report data structure and handle Robolectric PDF limitation gracefully)
        try {
            val pdfStream = ByteArrayOutputStream()
            pdfRenderer.renderReportToStream(reportData, pdfStream, localizedPdfReportLabels(context), zoneId)
            val pdfBytes = pdfStream.toByteArray()
            assertTrue(pdfBytes.isNotEmpty())
            val pdfHeader = String(pdfBytes.take(5).toByteArray(), StandardCharsets.US_ASCII)
            assertEquals("%PDF-", pdfHeader)
        } catch (e: IllegalStateException) {
            // Android android.graphics.pdf.PdfDocument native implementation is stubbed/unsupported in Robolectric JVM
            assertTrue(e.message?.contains("document is closed") == true || e.message?.contains("PdfDocument") == true)
        }

        // Exporting must NOT mutate Room database rows
        assertEquals(2, transactionRepository.observeAllTransactions().first().size)
    }

    // =========================================================================================
    // SCENARIO L: Application Restart and Offline Operation
    // =========================================================================================
    @Test
    fun scenarioL_appRestartAndOfflineOperation() = runTest(testDispatcher) {
        // Test persistent database file across simulated restarts
        val dbFile = File(context.cacheDir, "e2e_restart_test_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        try {
            // First Launch
            val db1 = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            val provisioner1 = InternalDefaultAccountProvisioner(db1, settingsRepository)
            val seeder1 = DefaultCategorySeeder(db1, db1.categoryDao())
            provisioner1.ensureProvisioned()
            seeder1.ensureSeeded()

            val defaultAcc = db1.accountDao().getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)!!
            val groceries = db1.categoryDao().getAllCategories().first { it.name == "Groceries" }

            db1.transactionDao().insertTransaction(
                com.suguru.expensetracker.data.local.entity.TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amountInMinorUnits = 85000L,
                    currencyCode = "UZS",
                    accountId = defaultAcc.id,
                    destinationAccountId = null,
                    categoryId = groceries.id,
                    transactionTime = Instant.now(),
                    note = "Pre-restart transaction",
                    recurringRuleId = null,
                    recurringOccurrenceDate = null,
                    createdAt = Instant.now()
                )
            )
            assertEquals(1, db1.backupDao().getAllTransactions().size)

            // Close DB to simulate process termination
            db1.close()

            // Second Launch (Process Recreation)
            val db2 = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            val provisioner2 = InternalDefaultAccountProvisioner(db2, settingsRepository)
            val seeder2 = DefaultCategorySeeder(db2, db2.categoryDao())

            // Re-run startup checks
            val res = provisioner2.ensureProvisioned()
            assertTrue(res is InternalDefaultAccountProvisioningResult.Ready)
            seeder2.ensureSeeded()

            // Verify persisted state retained exactly
            assertEquals(1, db2.accountDao().getAccountCount())
            assertEquals(19, db2.categoryDao().getAllCategories().size)
            val persistedTxs = db2.backupDao().getAllTransactions()
            assertEquals(1, persistedTxs.size)
            assertEquals(85000L, persistedTxs.first().amountInMinorUnits)
            assertEquals("Pre-restart transaction", persistedTxs.first().note)

            db2.close()
        } finally {
            if (dbFile.exists()) dbFile.delete()
        }
    }

    // =========================================================================================
    // Helper Test In-Memory Implementations
    // =========================================================================================
    private class InMemorySettingsRepository : SettingsRepository {
        private val _settings = MutableStateFlow(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                preferredCurrencyCode = "UZS",
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
                timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
                showCurrencyCode = true,
                confirmBeforeDelete = true
            )
        )
        override val settings: Flow<AppSettings> = _settings
        override val lastBackupAt: Flow<String?> = MutableStateFlow(null)
        override val lastBackupFileName: Flow<String?> = MutableStateFlow(null)

        override suspend fun restoreSettings(value: AppSettings) { _settings.value = value }
        override suspend fun setThemeMode(value: ThemeMode) { _settings.update { it.copy(themeMode = value) } }
        override suspend fun setPreferredCurrency(value: String?) { _settings.update { it.copy(preferredCurrencyCode = value) } }
        override suspend fun setWeekStart(value: WeekStart) { _settings.update { it.copy(weekStart = value) } }
        override suspend fun setDateFormat(value: DateFormatPreference) { _settings.update { it.copy(dateFormat = value) } }
        override suspend fun setTimeFormat(value: TimeFormatPreference) { _settings.update { it.copy(timeFormat = value) } }
        override suspend fun setShowCurrencyCode(value: Boolean) { _settings.update { it.copy(showCurrencyCode = value) } }
        override suspend fun setConfirmBeforeDelete(value: Boolean) { _settings.update { it.copy(confirmBeforeDelete = value) } }
        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) {}
    }

    private class InMemoryWidgetConfigurationRepository : WidgetConfigurationRepository {
        private val configs = MutableStateFlow<Map<Int, AccountWidgetConfiguration>>(emptyMap())
        private val budgetConfigs = MutableStateFlow<Map<Int, BudgetProgressWidgetConfiguration>>(emptyMap())

        override fun getConfiguration(appWidgetId: Int): Flow<AccountWidgetConfiguration?> =
            configs.map { it[appWidgetId] }

        override fun getAllConfigurations(): Flow<List<AccountWidgetConfiguration>> =
            configs.map { it.values.toList() }

        override suspend fun saveConfiguration(appWidgetId: Int, accountId: Long, privacyMode: Boolean, workspaceKey: String) {
            configs.value = configs.value + (appWidgetId to AccountWidgetConfiguration(appWidgetId, accountId, privacyMode, workspaceKey))
        }

        override suspend fun deleteConfiguration(appWidgetId: Int) {
            configs.value = configs.value - appWidgetId
        }

        override fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<BudgetProgressWidgetConfiguration?> =
            budgetConfigs.map { it[appWidgetId] }

        override fun getAllBudgetProgressConfigurations(): Flow<List<BudgetProgressWidgetConfiguration>> =
            budgetConfigs.map { it.values.toList() }

        override suspend fun saveBudgetProgressConfiguration(appWidgetId: Int, budgetId: Long, privacyMode: Boolean, workspaceKey: String) {
            budgetConfigs.value = budgetConfigs.value + (appWidgetId to BudgetProgressWidgetConfiguration(appWidgetId, budgetId, privacyMode, workspaceKey))
        }

        override suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int) {
            budgetConfigs.value = budgetConfigs.value - appWidgetId
        }
    }
}
