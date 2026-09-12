package com.suguru.expensetracker.di

import android.content.Context
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabaseProvider
import com.suguru.expensetracker.data.repository.RoomAccountRepository
import com.suguru.expensetracker.data.repository.RoomBudgetRepository
import com.suguru.expensetracker.data.repository.RoomCategoryRepository
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

private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(name = "expense_tracker_settings")

/**
 * Application-level dependency container providing singleton database and repository instances.
 * Repositories are exposed via their clean domain interfaces.
 */
class AppContainer(private val context: Context) {

    val database: ExpenseTrackerDatabase = ExpenseTrackerDatabaseProvider.getDatabase(context)

    val settingsRepository: com.suguru.expensetracker.domain.repository.SettingsRepository by lazy {
        com.suguru.expensetracker.data.repository.DataStoreSettingsRepository(context.dataStore)
    }

    val googlePlayBillingManager: com.suguru.expensetracker.data.billing.GooglePlayBillingManager by lazy {
        com.suguru.expensetracker.data.billing.GooglePlayBillingManager(
            context = context.applicationContext,
            onEntitlementUpdated = { entitlement ->
                (entitlementRepository as? com.suguru.expensetracker.data.repository.BillingEntitlementRepositoryImpl)?.onBillingEntitlementUpdated(entitlement)
            }
        )
    }

    val entitlementRepository: com.suguru.expensetracker.domain.repository.EntitlementRepository by lazy {
        com.suguru.expensetracker.data.repository.BillingEntitlementRepositoryImpl(
            context = context.applicationContext,
            dataStore = context.dataStore,
            billingManager = googlePlayBillingManager
        )
    }

    val observeProEntitlementUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.billing.ObserveProEntitlementUseCase(entitlementRepository)
    }
    val observeProProductDetailsUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.billing.ObserveProProductDetailsUseCase(entitlementRepository)
    }
    val refreshProEntitlementUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.billing.RefreshProEntitlementUseCase(entitlementRepository)
    }
    val restorePurchasesUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.billing.RestorePurchasesUseCase(entitlementRepository)
    }
    val launchProPurchaseUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.billing.LaunchProPurchaseUseCase(entitlementRepository)
    }

    val accountRepository: AccountRepository by lazy {
        RoomAccountRepository(database.accountDao())
    }

    val categoryRepository: CategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao())
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

    val monetizationPolicy by lazy { com.suguru.expensetracker.domain.policy.MonetizationPolicy() }

    // Account Use Cases
    val observeActiveAccountsUseCase by lazy { ObserveActiveAccountsUseCase(accountRepository) }
    val observeAllAccountsUseCase by lazy { ObserveAllAccountsUseCase(accountRepository) }
    val observeAccountUseCase by lazy { ObserveAccountUseCase(accountRepository) }
    val getAccountUseCase by lazy { GetAccountUseCase(accountRepository) }
    val createAccountUseCase by lazy { CreateAccountUseCase(accountRepository, entitlementRepository, monetizationPolicy) }
    val updateAccountUseCase by lazy { UpdateAccountUseCase(accountRepository, entitlementRepository, monetizationPolicy) }
    val archiveAccountUseCase by lazy { ArchiveAccountUseCase(accountRepository) }

    // Category Use Cases
    val observeActiveCategoriesUseCase by lazy { ObserveActiveCategoriesUseCase(categoryRepository) }
    val observeAllCategoriesUseCase by lazy { ObserveAllCategoriesUseCase(categoryRepository) }
    val observeCategoriesByTypeUseCase by lazy { ObserveCategoriesByTypeUseCase(categoryRepository) }
    val getCategoryUseCase by lazy { GetCategoryUseCase(categoryRepository) }
    val createCategoryUseCase by lazy { CreateCategoryUseCase(categoryRepository, entitlementRepository, monetizationPolicy) }
    val updateCategoryUseCase by lazy { UpdateCategoryUseCase(categoryRepository, entitlementRepository, monetizationPolicy) }
    val archiveCategoryUseCase by lazy { ArchiveCategoryUseCase(categoryRepository) }

    // Transaction Use Cases
    val observeTransactionsUseCase by lazy { ObserveTransactionsUseCase(transactionRepository) }
    val observeTransactionsByAccountUseCase by lazy { ObserveTransactionsByAccountUseCase(transactionRepository) }
    val observeTransactionsByCategoryUseCase by lazy { ObserveTransactionsByCategoryUseCase(transactionRepository) }
    val observeTransactionsBetweenUseCase by lazy { ObserveTransactionsBetweenUseCase(transactionRepository) }
    val getTransactionUseCase by lazy { GetTransactionUseCase(transactionRepository) }
    val createTransactionUseCase by lazy { CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository) }
    val updateTransactionUseCase by lazy { UpdateTransactionUseCase(transactionRepository, accountRepository, categoryRepository) }
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository, recurringOccurrenceRepository) }

    // Recurring Transaction Use Cases
    val processDueRecurringTransactionsUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            recurringOccurrenceRepository = recurringOccurrenceRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            widgetRefreshCoordinator = { widgetRefreshCoordinator }
        )
    }
    val createRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            entitlementRepository = entitlementRepository,
            monetizationPolicy = monetizationPolicy
        )
    }
    val updateRecurringTransactionUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            entitlementRepository = entitlementRepository,
            monetizationPolicy = monetizationPolicy
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
            categoryRepository = categoryRepository,
            entitlementRepository = entitlementRepository,
            monetizationPolicy = monetizationPolicy
        )
    }
    val updateBudgetUseCase by lazy {
        com.suguru.expensetracker.domain.usecase.budget.UpdateBudgetUseCase(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository,
            entitlementRepository = entitlementRepository,
            monetizationPolicy = monetizationPolicy
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
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase
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
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createStatisticsViewModel(): com.suguru.expensetracker.presentation.statistics.StatisticsViewModel {
        return com.suguru.expensetracker.presentation.statistics.StatisticsViewModel(
            observeStatisticsUseCase = observeStatisticsUseCase,
            observeProEntitlementUseCase = observeProEntitlementUseCase
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
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase
        )
    }

    fun createSettingsViewModel(): com.suguru.expensetracker.presentation.settings.SettingsViewModel {
        return com.suguru.expensetracker.presentation.settings.SettingsViewModel(
            settingsRepository = settingsRepository,
            observeProEntitlementUseCase = observeProEntitlementUseCase
        )
    }

    val backupRepository: com.suguru.expensetracker.domain.repository.BackupRepository by lazy {
        com.suguru.expensetracker.data.repository.RoomBackupRepository(database, settingsRepository)
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

    fun createDataStorageViewModel(): com.suguru.expensetracker.presentation.settings.DataStorageViewModel {
        return com.suguru.expensetracker.presentation.settings.DataStorageViewModel(
            createFullBackupUseCase = createFullBackupUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            exportTransactionsToCsvUseCase = exportTransactionsToCsvUseCase,
            backupRepository = backupRepository,
            settingsRepository = settingsRepository,
            widgetRefreshCoordinator = widgetRefreshCoordinator
        )
    }

    fun createProViewModel(): com.suguru.expensetracker.presentation.pro.ProViewModel {
        return com.suguru.expensetracker.presentation.pro.ProViewModel(
            observeProEntitlementUseCase = observeProEntitlementUseCase,
            observeProProductDetailsUseCase = observeProProductDetailsUseCase,
            refreshProEntitlementUseCase = refreshProEntitlementUseCase,
            restorePurchasesUseCase = restorePurchasesUseCase,
            launchProPurchaseUseCase = launchProPurchaseUseCase
        )
    }

    // Widget Foundation
    val widgetConfigurationRepository: com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository by lazy {
        com.suguru.expensetracker.widget.configuration.DataStoreWidgetConfigurationRepository(context.applicationContext)
    }

    val widgetRefreshCoordinator: com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator by lazy {
        com.suguru.expensetracker.widget.common.GlanceWidgetRefreshCoordinator(context.applicationContext, widgetConfigurationRepository)
    }

    val widgetEntitlementPolicy: com.suguru.expensetracker.widget.common.WidgetEntitlementPolicy by lazy {
        com.suguru.expensetracker.widget.common.WidgetEntitlementPolicy()
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

