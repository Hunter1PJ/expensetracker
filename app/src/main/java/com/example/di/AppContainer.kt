package com.example.di

import android.content.Context
import com.example.data.local.ExpenseTrackerDatabase
import com.example.data.local.ExpenseTrackerDatabaseProvider
import com.example.data.repository.RoomAccountRepository
import com.example.data.repository.RoomBudgetRepository
import com.example.data.repository.RoomCategoryRepository
import com.example.data.repository.RoomRecurringTransactionRepository
import com.example.data.repository.RoomTransactionRepository
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.RecurringTransactionRepository
import com.example.domain.repository.TransactionRepository
import com.example.domain.usecase.account.ArchiveAccountUseCase
import com.example.domain.usecase.account.CreateAccountUseCase
import com.example.domain.usecase.account.GetAccountUseCase
import com.example.domain.usecase.account.ObserveAccountUseCase
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.account.ObserveAllAccountsUseCase
import com.example.domain.usecase.account.UpdateAccountUseCase
import com.example.domain.usecase.balance.GetAccountBalanceUseCase
import com.example.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.example.domain.usecase.category.ArchiveCategoryUseCase
import com.example.domain.usecase.category.CreateCategoryUseCase
import com.example.domain.usecase.category.GetCategoryUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.example.domain.usecase.category.ObserveAllCategoriesUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.category.UpdateCategoryUseCase
import com.example.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.example.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.example.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.domain.usecase.transaction.GetTransactionUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsBetweenUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsByAccountUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsByCategoryUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase

private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(name = "expense_tracker_settings")

/**
 * Application-level dependency container providing singleton database and repository instances.
 * Repositories are exposed via their clean domain interfaces.
 */
class AppContainer(private val context: Context) {

    val database: ExpenseTrackerDatabase = ExpenseTrackerDatabaseProvider.getDatabase(context)

    val settingsRepository: com.example.domain.repository.SettingsRepository by lazy {
        com.example.data.repository.DataStoreSettingsRepository(context.dataStore)
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

    val recurringOccurrenceRepository: com.example.domain.repository.RecurringOccurrenceRepository by lazy {
        com.example.data.repository.RoomRecurringOccurrenceRepository(database)
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
    val createTransactionUseCase by lazy { CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository) }
    val updateTransactionUseCase by lazy { UpdateTransactionUseCase(transactionRepository, accountRepository, categoryRepository) }
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository, recurringOccurrenceRepository) }

    // Recurring Transaction Use Cases
    val processDueRecurringTransactionsUseCase by lazy {
        com.example.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            recurringOccurrenceRepository = recurringOccurrenceRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
    val createRecurringTransactionUseCase by lazy {
        com.example.domain.usecase.recurring.CreateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
    val updateRecurringTransactionUseCase by lazy {
        com.example.domain.usecase.recurring.UpdateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
    val deactivateRecurringTransactionUseCase by lazy {
        com.example.domain.usecase.recurring.DeactivateRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository
        )
    }
    val getRecurringTransactionUseCase by lazy {
        com.example.domain.usecase.recurring.GetRecurringTransactionUseCase(
            recurringTransactionRepository = recurringTransactionRepository
        )
    }
    val observeRecurringTransactionsUseCase by lazy {
        com.example.domain.usecase.recurring.ObserveRecurringTransactionsUseCase(
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
    val observeStatisticsUseCase by lazy { com.example.domain.usecase.statistics.ObserveStatisticsUseCase(transactionRepository, accountRepository, categoryRepository) }

    // Budget Use Cases
    val observeActiveBudgetProgressUseCase by lazy {
        com.example.domain.usecase.budget.ObserveActiveBudgetProgressUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }
    val createBudgetUseCase by lazy {
        com.example.domain.usecase.budget.CreateBudgetUseCase(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository
        )
    }
    val updateBudgetUseCase by lazy {
        com.example.domain.usecase.budget.UpdateBudgetUseCase(
            budgetRepository = budgetRepository,
            categoryRepository = categoryRepository
        )
    }
    val deactivateBudgetUseCase by lazy {
        com.example.domain.usecase.budget.DeactivateBudgetUseCase(
            budgetRepository = budgetRepository
        )
    }
    val getBudgetUseCase by lazy {
        com.example.domain.usecase.budget.GetBudgetUseCase(
            budgetRepository = budgetRepository
        )
    }

    // ViewModel Creation
    fun createAddTransactionViewModel(): com.example.presentation.transactions.add.AddTransactionViewModel {
        return com.example.presentation.transactions.add.AddTransactionViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            createTransactionUseCase = createTransactionUseCase
        )
    }

    fun createHomeViewModel(): com.example.presentation.home.HomeViewModel {
        return com.example.presentation.home.HomeViewModel(
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            observeAccountSummariesUseCase = observeAccountSummariesUseCase,
            observeRecentTransactionsUseCase = observeRecentTransactionsUseCase
        )
    }

    fun createAccountManagementViewModel(): com.example.presentation.accounts.management.AccountManagementViewModel {
        return com.example.presentation.accounts.management.AccountManagementViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeAccountBalanceUseCase = observeAccountBalanceUseCase,
            archiveAccountUseCase = archiveAccountUseCase
        )
    }

    fun createAddAccountViewModel(accountId: Long = 0L): com.example.presentation.accounts.add.AddAccountViewModel {
        return com.example.presentation.accounts.add.AddAccountViewModel(
            accountId = accountId,
            createAccountUseCase = createAccountUseCase,
            updateAccountUseCase = updateAccountUseCase,
            getAccountUseCase = getAccountUseCase,
            settingsRepository = settingsRepository
        )
    }

    fun createCategoryManagementViewModel(): com.example.presentation.categories.management.CategoryManagementViewModel {
        return com.example.presentation.categories.management.CategoryManagementViewModel(
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            archiveCategoryUseCase = archiveCategoryUseCase
        )
    }

    fun createAddCategoryViewModel(categoryId: Long = 0L): com.example.presentation.categories.add.AddCategoryViewModel {
        return com.example.presentation.categories.add.AddCategoryViewModel(
            categoryId = categoryId,
            createCategoryUseCase = createCategoryUseCase,
            updateCategoryUseCase = updateCategoryUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    fun createTransactionsViewModel(): com.example.presentation.transactions.TransactionsViewModel {
        return com.example.presentation.transactions.TransactionsViewModel(
            observeTransactionsUseCase = observeTransactionsUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase,
            observeAllCategoriesUseCase = observeAllCategoriesUseCase
        )
    }

    fun createTransactionDetailViewModel(transactionId: Long): com.example.presentation.transactions.detail.TransactionDetailViewModel {
        return com.example.presentation.transactions.detail.TransactionDetailViewModel(
            transactionId = transactionId,
            getTransactionUseCase = getTransactionUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            settingsRepository = settingsRepository
        )
    }

    fun createEditTransactionViewModel(transactionId: Long): com.example.presentation.transactions.edit.EditTransactionViewModel {
        return com.example.presentation.transactions.edit.EditTransactionViewModel(
            transactionId = transactionId,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    fun createStatisticsViewModel(): com.example.presentation.statistics.StatisticsViewModel {
        return com.example.presentation.statistics.StatisticsViewModel(
            observeStatisticsUseCase = observeStatisticsUseCase
        )
    }

    fun createBudgetsViewModel(): com.example.presentation.budgets.BudgetsViewModel {
        return com.example.presentation.budgets.BudgetsViewModel(
            observeActiveBudgetProgressUseCase = observeActiveBudgetProgressUseCase,
            deactivateBudgetUseCase = deactivateBudgetUseCase
        )
    }

    fun createAddEditBudgetViewModel(budgetId: Long = 0L): com.example.presentation.budgets.addedit.AddEditBudgetViewModel {
        return com.example.presentation.budgets.addedit.AddEditBudgetViewModel(
            budgetId = budgetId,
            getBudgetUseCase = getBudgetUseCase,
            createBudgetUseCase = createBudgetUseCase,
            updateBudgetUseCase = updateBudgetUseCase,
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase
        )
    }

    fun createRecurringTransactionsViewModel(): com.example.presentation.recurring.RecurringTransactionsViewModel {
        return com.example.presentation.recurring.RecurringTransactionsViewModel(
            observeRecurringTransactionsUseCase = observeRecurringTransactionsUseCase,
            observeAllAccountsUseCase = observeAllAccountsUseCase,
            observeAllCategoriesUseCase = observeAllCategoriesUseCase,
            deactivateRecurringTransactionUseCase = deactivateRecurringTransactionUseCase,
            processDueRecurringTransactionsUseCase = processDueRecurringTransactionsUseCase
        )
    }

    fun createAddEditRecurringTransactionViewModel(ruleId: Long = 0L): com.example.presentation.recurring.addedit.AddEditRecurringTransactionViewModel {
        return com.example.presentation.recurring.addedit.AddEditRecurringTransactionViewModel(
            ruleId = ruleId,
            createRecurringTransactionUseCase = createRecurringTransactionUseCase,
            updateRecurringTransactionUseCase = updateRecurringTransactionUseCase,
            getRecurringTransactionUseCase = getRecurringTransactionUseCase,
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase
        )
    }

    fun createSettingsViewModel(): com.example.presentation.settings.SettingsViewModel {
        return com.example.presentation.settings.SettingsViewModel(
            settingsRepository = settingsRepository
        )
    }

    val backupRepository: com.example.domain.repository.BackupRepository by lazy {
        com.example.data.repository.RoomBackupRepository(database, settingsRepository)
    }

    val createFullBackupUseCase by lazy {
        com.example.domain.usecase.backup.CreateFullBackupUseCase(backupRepository)
    }

    val restoreBackupUseCase by lazy {
        com.example.domain.usecase.backup.RestoreBackupUseCase(backupRepository)
    }

    val exportTransactionsToCsvUseCase by lazy {
        com.example.domain.usecase.transaction.ExportTransactionsToCsvUseCase(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }

    fun createDataStorageViewModel(): com.example.presentation.settings.DataStorageViewModel {
        return com.example.presentation.settings.DataStorageViewModel(
            createFullBackupUseCase = createFullBackupUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            exportTransactionsToCsvUseCase = exportTransactionsToCsvUseCase,
            backupRepository = backupRepository,
            settingsRepository = settingsRepository
        )
    }
}

