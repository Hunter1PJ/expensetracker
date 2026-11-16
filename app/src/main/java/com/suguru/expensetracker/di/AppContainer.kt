package com.suguru.expensetracker.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.presentation.startup.StartupProvisioningState
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabaseProvider
import com.suguru.expensetracker.domain.parser.DeterministicTransactionParser
import com.suguru.expensetracker.data.repository.RoomAccountRepository
import com.suguru.expensetracker.data.repository.RoomBudgetRepository
import com.suguru.expensetracker.data.repository.RoomCategoryRepository
import com.suguru.expensetracker.data.repository.DefaultCategorySeeder
import com.suguru.expensetracker.data.repository.RoomRecurringTransactionRepository
import com.suguru.expensetracker.data.repository.RoomTransactionRepository
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.BudgetRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import com.suguru.expensetracker.domain.usecase.account.ArchiveAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.CreateAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.ObserveAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.account.ObserveAllAccountsUseCase
import com.suguru.expensetracker.domain.usecase.account.UpdateAccountUseCase
import com.suguru.expensetracker.domain.usecase.balance.GetAccountBalanceUseCase
import com.suguru.expensetracker.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.suguru.expensetracker.domain.usecase.category.ArchiveCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.CreateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveAllCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.category.UpdateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.DeleteTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ObserveTransactionsBetweenUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ObserveTransactionsByAccountUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ObserveTransactionsByCategoryUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ObserveTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(name = "expense_tracker_settings")

/**
 * Application-level dependency container providing singleton database and repository instances.
 * Repositories are exposed via their clean domain interfaces.
 */
class AppContainer(private val context: Context) {

    private val _provisioningState = kotlinx.coroutines.flow.MutableStateFlow<StartupProvisioningState>(StartupProvisioningState.Loading)
    val provisioningState: kotlinx.coroutines.flow.StateFlow<StartupProvisioningState> = _provisioningState
    private val provisioningMutex = Mutex()
    private val categoryInitializationMutex = Mutex()
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ExpenseTrackerDatabase = ExpenseTrackerDatabaseProvider.getDatabase(context)

    val settingsRepository: com.suguru.expensetracker.domain.repository.SettingsRepository by lazy {
        com.suguru.expensetracker.data.repository.DataStoreSettingsRepository(context.dataStore)
    }

    val cloudBackupFingerprintStore: com.suguru.expensetracker.domain.repository.CloudBackupFingerprintStore by lazy {
        com.suguru.expensetracker.data.repository.DataStoreCloudBackupFingerprintStore(context.dataStore)
    }

    val cloudBackupStateStore: com.suguru.expensetracker.domain.repository.CloudBackupStateStore by lazy {
        com.suguru.expensetracker.data.repository.DataStoreCloudBackupStateStore(context.dataStore)
    }

    // AI BYOK Foundation
    val secureKeyStorage: com.suguru.expensetracker.data.security.SecureKeyStorage by lazy {
        com.suguru.expensetracker.data.security.AndroidKeystoreSecureStorage(context.applicationContext)
    }

    val geminiApiClient: com.suguru.expensetracker.data.ai.gemini.GeminiApiClient by lazy {
        com.suguru.expensetracker.data.ai.gemini.GeminiApiClient()
    }

    val geminiAiProvider: com.suguru.expensetracker.domain.ai.AiProvider by lazy {
        com.suguru.expensetracker.data.ai.gemini.GeminiAiProvider(secureKeyStorage, geminiApiClient)
    }

    val aiRepository: com.suguru.expensetracker.domain.ai.AiRepository by lazy {
        com.suguru.expensetracker.data.ai.AiRepositoryImpl(
            aiProvider = geminiAiProvider,
            secureKeyStorage = secureKeyStorage,
            dataStore = context.dataStore,
            offlineParser = DeterministicTransactionParser(),
            isOnline = ::hasValidatedInternetConnection
        )
    }

    private fun hasValidatedInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner by lazy {
        InternalDefaultAccountProvisioner(database, settingsRepository)
    }

    suspend fun ensureInternalDefaultAccountProvisioning(): InternalDefaultAccountProvisioningResult {
        return provisioningMutex.withLock {
            val roomBackupRepository = backupRepository as? com.suguru.expensetracker.data.repository.RoomBackupRepository
            val result = roomBackupRepository?.ensureProvisionedWithRecovery()
                ?: internalDefaultAccountProvisioner.ensureProvisioned()
            try {
                ensureDefaultCategories()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _provisioningState.value = StartupProvisioningState.RecoverableError(
                    error.localizedMessage ?: "Unable to initialize default categories."
                )
                return@withLock InternalDefaultAccountProvisioningResult.Failure(error)
            }
            if (result is InternalDefaultAccountProvisioningResult.Failure &&
                _provisioningState.value == StartupProvisioningState.SettingsRecoveryPending
            ) {
                _provisioningState.value = StartupProvisioningState.RecoverableError(
                    result.error.localizedMessage ?: "Settings recovery is still pending."
                )
            } else {
                _provisioningState.value = StartupProvisioningState.fromResult(result)
            }
            result
        }
    }

    val accountRepository: AccountRepository by lazy {
        RoomAccountRepository(database.accountDao())
    }

    val categoryRepository: CategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao())
    }

    val defaultCategorySeeder: DefaultCategorySeeder by lazy {
        DefaultCategorySeeder(database, database.categoryDao())
    }

    private suspend fun ensureDefaultCategories() {
        categoryInitializationMutex.withLock {
            defaultCategorySeeder.ensureSeeded()
        }
    }

    val transactionRepository: TransactionRepository by lazy {
        RoomTransactionRepository(database.transactionDao())
    }

    val budgetRepository: BudgetRepository by lazy {
        RoomBudgetRepository(database.budgetDao())
    }

    val recurringTransactionRepository: RecurringTransactionRepository by lazy {
        RoomRecurringTransactionRepository(database.recurringTransactionDao())
    }

    val recurringOccurrenceRepository: com.suguru.expensetracker.domain.repository.RecurringOccurrenceRepository by lazy {
        com.suguru.expensetracker.data.repository.RoomRecurringOccurrenceRepository(database)
    }

    // Account Use Cases
    val observeActiveAccountsUseCase by lazy { ObserveActiveAccountsUseCase(accountRepository) }
    val observeAllAccountsUseCase by lazy { ObserveAllAccountsUseCase(accountRepository) }
    val observeAccountUseCase by lazy { ObserveAccountUseCase(accountRepository) }
    val getAccountUseCase by lazy { GetAccountUseCase(accountRepository) }
    val createAccountUseCase by lazy { CreateAccountUseCase(accountRepository) }
    val updateAccountUseCase by lazy { UpdateAccountUseCase(accountRepository) }
    val archiveAccountUseCase by lazy { ArchiveAccountUseCase(accountRepository) }

    // Category Use Cases
    val observeActiveCategoriesUseCase by lazy { ObserveActiveCategoriesUseCase(categoryRepository) }
    val observeAllCategoriesUseCase by lazy { ObserveAllCategoriesUseCase(categoryRepository) }
    val observeCategoriesByTypeUseCase by lazy { ObserveCategoriesByTypeUseCase(categoryRepository) }
    val getCategoryUseCase by lazy { GetCategoryUseCase(categoryRepository) }
    val createCategoryUseCase by lazy { CreateCategoryUseCase(categoryRepository) }
    val updateCategoryUseCase by lazy { UpdateCategoryUseCase(categoryRepository) }
    val archiveCategoryUseCase by lazy { ArchiveCategoryUseCase(categoryRepository) }

    // Transaction Use Cases
    val observeTransactionsUseCase by lazy { ObserveTransactionsUseCase(transactionRepository) }
    val observeTransactionsByAccountUseCase by lazy { ObserveTransactionsByAccountUseCase(transactionRepository) }
    val observeTransactionsByCategoryUseCase by lazy { ObserveTransactionsByCategoryUseCase(transactionRepository) }
    val observeTransactionsBetweenUseCase by lazy { ObserveTransactionsBetweenUseCase(transactionRepository) }
    val getTransactionUseCase by lazy { GetTransactionUseCase(transactionRepository) }
    val createTransactionUseCase by lazy {
        CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository, internalDefaultAccountProvisioner)
    }
    val updateTransactionUseCase by lazy {
        UpdateTransactionUseCase(transactionRepository, accountRepository, categoryRepository, internalDefaultAccountProvisioner)
    }
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository, recurringOccurrenceRepository) }

    // Recurring Transaction Use Cases
    val processDueRecurringTransactionsUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            recurringOccurrenceRepository = recurringOccurrenceRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            widgetRefreshCoordinator = { widgetRefreshCoordinator },
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
    }
    val createRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
    }
    val updateRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner
        )
    }
    val deactivateRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.DeactivateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository
        )
    }
    val getRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.GetRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository
        )
    }
    val observeRecurringTransactionsUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.ObserveRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository
        )
    }

    // Balance Use Cases
    val getAccountBalanceUseCase by lazy { GetAccountBalanceUseCase(accountRepository, transactionRepository) }
    val observeAccountBalanceUseCase by lazy { ObserveAccountBalanceUseCase(accountRepository, transactionRepository) }

    // Dashboard Use Cases
    val observeAccountSummariesUseCase by lazy { ObserveAccountSummariesUseCase(accountRepository, transactionRepository) }
    val observeDashboardSummaryUseCase by lazy { ObserveDashboardSummaryUseCase(accountRepository, transactionRepository) }
    val observeRecentTransactionsUseCase by lazy { ObserveRecentTransactionsUseCase(transactionRepository, accountRepository, categoryRepository) }

    // Statistics Use Cases
    val observeStatisticsUseCase by lazy { com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsUseCase(transactionRepository, accountRepository, categoryRepository) }
    val observeStatisticsComparisonUseCase by lazy { com.suguru.expensetracker.domain.usecase.statistics.ObserveStatisticsComparisonUseCase(observeStatisticsUseCase) }

    // Budget Use Cases
    val observeActiveBudgetProgressUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.ObserveActiveBudgetProgressUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
    val createBudgetUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.CreateBudgetUseCase(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository
        )
    }
    val updateBudgetUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.UpdateBudgetUseCase(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository
        )
    }
    val deactivateBudgetUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.DeactivateBudgetUseCase(
            budgetRepository = budgetRepository
        )
    }
    val getBudgetUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.GetBudgetUseCase(
            budgetRepository = budgetRepository
        )
    }

    // ViewModel Creation
    fun createAddTransactionViewModel(): com.suguru.expensetracker.presentation.transactions.add.AddTransactionViewModel {
        return com.suguru.expensetracker.presentation.transactions.add.AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createHomeViewModel(): com.suguru.expensetracker.presentation.home.HomeViewModel {
        return com.suguru.expensetracker.presentation.home.HomeViewModel(
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            observeAccountSummariesUseCase = observeAccountSummariesUseCase,
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase,
            aiRepository = aiRepository
        )
    }

    fun createAccountManagementViewModel(): com.suguru.expensetracker.presentation.accounts.management.AccountManagementViewModel {
        return com.suguru.expensetracker.presentation.accounts.management.AccountManagementViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeAccountBalanceUseCase = observeAccountBalanceUseCase,
            archiveAccountUseCase = archiveAccountUseCase,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createAddAccountViewModel(accountId: Long = 0L): com.suguru.expensetracker.presentation.accounts.add.AddAccountViewModel {
        return com.suguru.expensetracker.presentation.accounts.add.AddAccountViewModel(
            accountId = accountId,
            createAccountUseCase = createAccountUseCase,
            updateAccountUseCase = updateAccountUseCase,
            getAccountUseCase = getAccountUseCase,
            settingsRepository = settingsRepository,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createCategoryManagementViewModel(): com.suguru.expensetracker.presentation.categories.management.CategoryManagementViewModel {
        return com.suguru.expensetracker.presentation.categories.management.CategoryManagementViewModel(
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            archiveCategoryUseCase = archiveCategoryUseCase
        )
    }

    fun createAddCategoryViewModel(categoryId: Long = 0L): com.suguru.expensetracker.presentation.categories.add.AddCategoryViewModel {
        return com.suguru.expensetracker.presentation.categories.add.AddCategoryViewModel(
            categoryId = categoryId,
            createCategoryUseCase = createCategoryUseCase,
            updateCategoryUseCase = updateCategoryUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    fun createTransactionsViewModel(): com.suguru.expensetracker.presentation.transactions.TransactionsViewModel {
        return com.suguru.expensetracker.presentation.transactions.TransactionsViewModel(
            observeTransactionsUseCase = observeTransactionsUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase,
            observeAllCategoriesUseCase = observeAllCategoriesUseCase
        )
    }

    fun createTransactionDetailViewModel(transactionId: Long): com.suguru.expensetracker.presentation.transactions.detail.TransactionDetailViewModel {
        return com.suguru.expensetracker.presentation.transactions.detail.TransactionDetailViewModel(
            transactionId = transactionId,
            getTransactionUseCase = getTransactionUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            settingsRepository = settingsRepository,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createEditTransactionViewModel(transactionId: Long): com.suguru.expensetracker.presentation.transactions.edit.EditTransactionViewModel {
        return com.suguru.expensetracker.presentation.transactions.edit.EditTransactionViewModel(
            transactionId = transactionId,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createStatisticsViewModel(savedStateHandle: androidx.lifecycle.SavedStateHandle? = null): com.suguru.expensetracker.presentation.statistics.StatisticsViewModel {
        return com.suguru.expensetracker.presentation.statistics.StatisticsViewModel(
            observeStatisticsUseCase = observeStatisticsUseCase,
            observeStatisticsComparisonUseCase = observeStatisticsComparisonUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    fun createBudgetsViewModel(): com.suguru.expensetracker.presentation.budgets.BudgetsViewModel {
        return com.suguru.expensetracker.presentation.budgets.BudgetsViewModel(
            observeActiveBudgetProgressUseCase = observeActiveBudgetProgressUseCase,
            deactivateBudgetUseCase = deactivateBudgetUseCase
        )
    }

    fun createAddEditBudgetViewModel(budgetId: Long = 0L): com.suguru.expensetracker.presentation.budgets.addedit.AddEditBudgetViewModel {
        return com.suguru.expensetracker.presentation.budgets.addedit.AddEditBudgetViewModel(
            budgetId = budgetId,
            getBudgetUseCase = getBudgetUseCase,
            createBudgetUseCase = createBudgetUseCase,
            updateBudgetUseCase = updateBudgetUseCase,
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase
        )
    }

    fun createRecurringTransactionsViewModel(): com.suguru.expensetracker.presentation.recurring.RecurringTransactionsViewModel {
        return com.suguru.expensetracker.presentation.recurring.RecurringTransactionsViewModel(
            observeRecurringTransactionsUseCase = observeRecurringTransactionsUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase,
            observeAllCategoriesUseCase = observeAllCategoriesUseCase,
            deactivateRecurringTransactionUseCase = deactivateRecurringTransactionUseCase,
            processDueRecurringTransactionsUseCase = processDueRecurringTransactionsUseCase
        )
    }

    fun createAddEditRecurringTransactionViewModel(ruleId: Long = 0L): com.suguru.expensetracker.presentation.recurring.addedit.AddEditRecurringTransactionViewModel {
        return com.suguru.expensetracker.presentation.recurring.addedit.AddEditRecurringTransactionViewModel(
            ruleId = ruleId,
            createRecurringTransactionUseCase = createRecurringTransactionUseCase,
            updateRecurringTransactionUseCase = updateRecurringTransactionUseCase,
            getRecurringTransactionUseCase = getRecurringTransactionUseCase,
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    fun createSettingsViewModel(): com.suguru.expensetracker.presentation.settings.SettingsViewModel {
        return com.suguru.expensetracker.presentation.settings.SettingsViewModel(
            settingsRepository = settingsRepository,
            aiRepository = aiRepository
        )
    }

    val backupRepository: com.suguru.expensetracker.domain.repository.BackupRepository by lazy {
        com.suguru.expensetracker.data.repository.RoomBackupRepository(
            db = database,
            settingsRepository = settingsRepository,
            internalDefaultAccountProvisioner = internalDefaultAccountProvisioner,
            onProvisioningResult = { result ->
                _provisioningState.value = StartupProvisioningState.Loading
                startupScope.launch {
                    try {
                        ensureDefaultCategories()
                        _provisioningState.value = StartupProvisioningState.fromResult(result)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _provisioningState.value = StartupProvisioningState.RecoverableError(
                            error.localizedMessage ?: "Unable to initialize default categories."
                        )
                    }
                }
            },
            onSettingsRecoveryPending = {
                _provisioningState.value = StartupProvisioningState.SettingsRecoveryPending
            },
            appVersion = com.suguru.expensetracker.BuildConfig.VERSION_NAME
        )
    }

    val createFullBackupUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase(backupRepository)
    }

    val restoreBackupUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase(backupRepository)
    }

    val exportTransactionsToCsvUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.transaction.ExportTransactionsToCsvUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }

    val csvExporter: com.suguru.expensetracker.data.export.CsvExporter by lazy {
        com.suguru.expensetracker.data.export.CsvExporter()
    }

    val pdfFinancialReportRenderer: com.suguru.expensetracker.data.export.PdfFinancialReportRenderer by lazy {
        com.suguru.expensetracker.data.export.PdfFinancialReportRenderer()
    }

    val generateBasicCsvExportUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.export.GenerateBasicCsvExportUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            csvExporter = csvExporter
        )
    }

    val prepareFinancialReportDataUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.export.PrepareFinancialReportDataUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }

    val generateAdvancedCsvExportUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.export.GenerateAdvancedCsvExportUseCase(
            prepareFinancialReportDataUseCase = prepareFinancialReportDataUseCase,
            csvExporter = csvExporter
        )
    }

    fun createExportViewModel(): com.suguru.expensetracker.presentation.export.ExportViewModel {
        return com.suguru.expensetracker.presentation.export.ExportViewModel(
            generateBasicCsvExportUseCase = generateBasicCsvExportUseCase,
            generateAdvancedCsvExportUseCase = generateAdvancedCsvExportUseCase,
            prepareFinancialReportDataUseCase = prepareFinancialReportDataUseCase,
            pdfFinancialReportRenderer = pdfFinancialReportRenderer,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }

    fun createDataStorageViewModel(): com.suguru.expensetracker.presentation.settings.DataStorageViewModel {
        return com.suguru.expensetracker.presentation.settings.DataStorageViewModel(
            createFullBackupUseCase = createFullBackupUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            exportTransactionsToCsvUseCase = exportTransactionsToCsvUseCase,
            backupRepository = backupRepository,
            settingsRepository = settingsRepository,
            widgetRefreshCoordinator = widgetRefreshCoordinator,
            onPendingSettingsRecovery = { ensureInternalDefaultAccountProvisioning() }
        )
    }

    // Widget Foundation
    val widgetConfigurationRepository: com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository by lazy {
        com.suguru.expensetracker.widget.configuration.DataStoreWidgetConfigurationRepository(context.applicationContext)
    }

    val widgetRefreshCoordinator: com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator by lazy {
        com.suguru.expensetracker.widget.common.GlanceWidgetRefreshCoordinator(context.applicationContext, widgetConfigurationRepository)
    }

    val accountBalanceWidgetDataProvider: com.suguru.expensetracker.widget.balance.AccountBalanceWidgetDataProvider by lazy {
        com.suguru.expensetracker.widget.balance.AccountBalanceWidgetDataProvider(
            accountRepository = accountRepository,
            getAccountBalanceUseCase = getAccountBalanceUseCase,
            widgetConfigurationRepository = widgetConfigurationRepository
        )
    }

    val budgetProgressWidgetDataProvider: com.suguru.expensetracker.widget.budget.BudgetProgressWidgetDataProvider by lazy {
        com.suguru.expensetracker.widget.budget.BudgetProgressWidgetDataProvider(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository,
            observeActiveBudgetProgressUseCase = observeActiveBudgetProgressUseCase,
            widgetConfigurationRepository = widgetConfigurationRepository
        )
    }
}
