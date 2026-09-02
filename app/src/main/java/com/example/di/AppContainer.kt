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
import com.example.domain.usecase.account.UpdateAccountUseCase
import com.example.domain.usecase.balance.GetAccountBalanceUseCase
import com.example.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.example.domain.usecase.category.ArchiveCategoryUseCase
import com.example.domain.usecase.category.CreateCategoryUseCase
import com.example.domain.usecase.category.GetCategoryUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.category.UpdateCategoryUseCase
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.domain.usecase.transaction.GetTransactionUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsBetweenUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsByAccountUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsByCategoryUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase

/**
 * Application-level dependency container providing singleton database and repository instances.
 * Repositories are exposed via their clean domain interfaces.
 */
class AppContainer(context: Context) {

    val database: ExpenseTrackerDatabase = ExpenseTrackerDatabaseProvider.getDatabase(context)

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

    // Account Use Cases
    val observeActiveAccountsUseCase by lazy { ObserveActiveAccountsUseCase(accountRepository) }
    val observeAccountUseCase by lazy { ObserveAccountUseCase(accountRepository) }
    val getAccountUseCase by lazy { GetAccountUseCase(accountRepository) }
    val createAccountUseCase by lazy { CreateAccountUseCase(accountRepository) }
    val updateAccountUseCase by lazy { UpdateAccountUseCase(accountRepository) }
    val archiveAccountUseCase by lazy { ArchiveAccountUseCase(accountRepository) }

    // Category Use Cases
    val observeActiveCategoriesUseCase by lazy { ObserveActiveCategoriesUseCase(categoryRepository) }
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
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository) }

    // Balance Use Cases
    val getAccountBalanceUseCase by lazy { GetAccountBalanceUseCase(accountRepository, transactionRepository) }
    val observeAccountBalanceUseCase by lazy { ObserveAccountBalanceUseCase(accountRepository, transactionRepository) }

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
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeAccountBalanceUseCase = observeAccountBalanceUseCase
        )
    }
}

