package com.suguru.expensetracker.domain.fake

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<Map<Long, Account>>(emptyMap())
    private var nextId = 1L

    override fun observeActiveAccounts(): Flow<List<Account>> =
        accounts.map { map -> map.values.filter { !it.isArchived } }

    override fun observeAllAccounts(): Flow<List<Account>> =
        accounts.map { map -> map.values.toList() }

    override fun observeAccountById(id: Long): Flow<Account?> =
        accounts.map { it[id] }

    override suspend fun getAccountById(id: Long): Account? = accounts.value[id]

    override suspend fun insertAccount(account: Account): Long {
        val id = if (account.id == 0L) nextId++ else { nextId = maxOf(nextId, account.id + 1); account.id }
        val updated = account.copy(id = id)
        accounts.value = accounts.value + (id to updated)
        return id
    }

    override suspend fun updateAccount(account: Account) {
        accounts.value = accounts.value + (account.id to account)
    }

    override suspend fun archiveAccount(id: Long) {
        val account = accounts.value[id] ?: return
        accounts.value = accounts.value + (id to account.copy(isArchived = true))
    }
}

class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow<Map<Long, Category>>(emptyMap())
    private var nextId = 1L

    override fun observeActiveCategories(): Flow<List<Category>> =
        categories.map { map -> map.values.filter { !it.isArchived } }

    override fun observeAllCategories(): Flow<List<Category>> =
        categories.map { map -> map.values.toList() }

    override fun observeActiveCategoriesByType(type: CategoryType): Flow<List<Category>> =
        categories.map { map ->
            map.values.filter { !it.isArchived && (it.type == type || it.type == CategoryType.BOTH) }
        }

    override fun observeCategoryById(id: Long): Flow<Category?> =
        categories.map { it[id] }

    override suspend fun getCategoryById(id: Long): Category? = categories.value[id]

    override suspend fun insertCategory(category: Category): Long {
        val id = if (category.id == 0L) nextId++ else { nextId = maxOf(nextId, category.id + 1); category.id }
        val updated = category.copy(id = id)
        categories.value = categories.value + (id to updated)
        return id
    }

    override suspend fun insertCategories(categories: List<Category>): List<Long> {
        return categories.map { insertCategory(it) }
    }

    override suspend fun updateCategory(category: Category) {
        categories.value = categories.value + (category.id to category)
    }

    override suspend fun archiveCategory(id: Long) {
        val category = categories.value[id] ?: return
        categories.value = categories.value + (id to category.copy(isArchived = true))
    }
}

class FakeTransactionRepository : TransactionRepository {
    private val transactions = MutableStateFlow<Map<Long, Transaction>>(emptyMap())
    private var nextId = 1L

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        transactions.map { it.values.toList().sortedByDescending { tx -> tx.transactionTime } }

    override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactions.map { map ->
            map.values.sortedByDescending { it.transactionTime }.take(limit)
        }

    override suspend fun getRecentTransactions(limit: Int): List<Transaction> =
        transactions.value.values.sortedByDescending { it.transactionTime }.take(limit)

    override fun observeTransactionsByAccount(accountId: Long): Flow<List<Transaction>> =
        transactions.map { map ->
            map.values.filter { it.accountId == accountId || it.destinationAccountId == accountId }
                .sortedByDescending { it.transactionTime }
        }

    override suspend fun getTransactionsByAccount(accountId: Long): List<Transaction> {
        return transactions.value.values.filter { it.accountId == accountId || it.destinationAccountId == accountId }
            .sortedByDescending { it.transactionTime }
    }

    override fun observeTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactions.map { map ->
            map.values.filter { it.categoryId == categoryId }
                .sortedByDescending { it.transactionTime }
        }

    override fun observeTransactionsBetween(startTime: Instant, endTime: Instant): Flow<List<Transaction>> =
        transactions.map { map ->
            map.values.filter { !it.transactionTime.isBefore(startTime) && it.transactionTime.isBefore(endTime) }
                .sortedByDescending { it.transactionTime }
        }

    override suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<Transaction> =
        transactions.value.values.filter { !it.transactionTime.isBefore(startTime) && it.transactionTime.isBefore(endTime) }
            .sortedByDescending { it.transactionTime }

    override fun observeTransactionById(id: Long): Flow<Transaction?> =
        transactions.map { it[id] }

    override suspend fun getTransactionById(id: Long): Transaction? = transactions.value[id]

    override suspend fun insertTransaction(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        val updated = transaction.copy(id = id)
        transactions.value = transactions.value + (id to updated)
        return id
    }

    override suspend fun insertTransactions(transactions: List<Transaction>): List<Long> =
        transactions.map { insertTransaction(it) }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactions.value = transactions.value + (transaction.id to transaction)
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactions.value = transactions.value - id
    }
}

class FakeBudgetRepository : com.suguru.expensetracker.domain.repository.BudgetRepository {
    private val budgets = MutableStateFlow<Map<Long, com.suguru.expensetracker.domain.model.Budget>>(emptyMap())
    private var nextId = 1L

    override fun observeActiveBudgets(): Flow<List<com.suguru.expensetracker.domain.model.Budget>> =
        budgets.map { map -> map.values.filter { it.isActive } }

    override fun observeAllBudgets(): Flow<List<com.suguru.expensetracker.domain.model.Budget>> =
        budgets.map { map -> map.values.toList() }

    override fun observeBudgetsByCategory(categoryId: Long): Flow<List<com.suguru.expensetracker.domain.model.Budget>> =
        budgets.map { map -> map.values.filter { it.categoryId == categoryId } }

    override fun observeBudgetById(id: Long): Flow<com.suguru.expensetracker.domain.model.Budget?> =
        budgets.map { it[id] }

    override suspend fun getBudgetById(id: Long): com.suguru.expensetracker.domain.model.Budget? = budgets.value[id]

    override suspend fun insertBudget(budget: com.suguru.expensetracker.domain.model.Budget): Long {
        val id = if (budget.id == 0L) nextId++ else { nextId = maxOf(nextId, budget.id + 1); budget.id }
        val updated = budget.copy(id = id)
        budgets.value = budgets.value + (id to updated)
        return id
    }

    override suspend fun updateBudget(budget: com.suguru.expensetracker.domain.model.Budget) {
        budgets.value = budgets.value + (budget.id to budget)
    }

    override suspend fun deactivateBudget(id: Long) {
        val budget = budgets.value[id] ?: return
        budgets.value = budgets.value + (id to budget.copy(isActive = false))
    }
}

class FakeRecurringTransactionRepository : com.suguru.expensetracker.domain.repository.RecurringTransactionRepository {
    private val rules = MutableStateFlow<Map<Long, com.suguru.expensetracker.domain.model.RecurringTransaction>>(emptyMap())
    private var nextId = 1L

    override fun observeActiveRecurringTransactions(): Flow<List<com.suguru.expensetracker.domain.model.RecurringTransaction>> =
        rules.map { map -> map.values.filter { it.isActive } }

    override fun observeAllRecurringTransactions(): Flow<List<com.suguru.expensetracker.domain.model.RecurringTransaction>> =
        rules.map { map -> map.values.toList() }

    override fun observeRecurringTransactionById(id: Long): Flow<com.suguru.expensetracker.domain.model.RecurringTransaction?> =
        rules.map { it[id] }

    override suspend fun getRecurringTransactionById(id: Long): com.suguru.expensetracker.domain.model.RecurringTransaction? =
        rules.value[id]

    override suspend fun getRecurringTransactionsDue(beforeOrOnDate: java.time.LocalDate): List<com.suguru.expensetracker.domain.model.RecurringTransaction> =
        rules.value.values.filter { it.isActive && it.nextOccurrence <= beforeOrOnDate }

    override suspend fun insertRecurringTransaction(recurringTransaction: com.suguru.expensetracker.domain.model.RecurringTransaction): Long {
        val id = if (recurringTransaction.id == 0L) nextId++ else recurringTransaction.id
        val updated = recurringTransaction.copy(id = id)
        rules.value = rules.value + (id to updated)
        return id
    }

    override suspend fun updateRecurringTransaction(recurringTransaction: com.suguru.expensetracker.domain.model.RecurringTransaction) {
        rules.value = rules.value + (recurringTransaction.id to recurringTransaction)
    }

    override suspend fun deactivateRecurringTransaction(id: Long) {
        val rule = rules.value[id] ?: return
        rules.value = rules.value + (id to rule.copy(isActive = false))
    }
}

class FakeEntitlementRepository : com.suguru.expensetracker.domain.repository.EntitlementRepository {
    private val _entitlement = MutableStateFlow<com.suguru.expensetracker.domain.model.ProEntitlement>(com.suguru.expensetracker.domain.model.ProEntitlement.Free)
    override val entitlement: StateFlow<com.suguru.expensetracker.domain.model.ProEntitlement> = _entitlement

    private val _productDetails = MutableStateFlow<com.suguru.expensetracker.domain.model.ProProductDetails?>(
        com.suguru.expensetracker.domain.model.ProProductDetails(
            productId = com.suguru.expensetracker.domain.model.BillingProducts.PRO_LIFETIME,
            title = "ExpenseTracker Pro — Lifetime",
            description = "One-time upgrade",
            formattedPrice = "$9.99"
        )
    )
    override val productDetails: StateFlow<com.suguru.expensetracker.domain.model.ProProductDetails?> = _productDetails

    var restoreResult: com.suguru.expensetracker.domain.model.RestorePurchasesResult =
        com.suguru.expensetracker.domain.model.RestorePurchasesResult.Restored
    var launchResult: com.suguru.expensetracker.domain.model.PurchaseLaunchResult =
        com.suguru.expensetracker.domain.model.PurchaseLaunchResult.Launched

    fun setEntitlement(newEntitlement: com.suguru.expensetracker.domain.model.ProEntitlement) {
        _entitlement.value = newEntitlement
    }

    override suspend fun refreshPurchases() {
        // No-op for fake
    }

    override suspend fun restorePurchases(): com.suguru.expensetracker.domain.model.RestorePurchasesResult {
        if (restoreResult is com.suguru.expensetracker.domain.model.RestorePurchasesResult.Restored) {
            _entitlement.value = com.suguru.expensetracker.domain.model.ProEntitlement.Pro
        }
        return restoreResult
    }

    override suspend fun launchPurchase(activity: android.app.Activity): com.suguru.expensetracker.domain.model.PurchaseLaunchResult {
        if (launchResult is com.suguru.expensetracker.domain.model.PurchaseLaunchResult.AlreadyOwned) {
            _entitlement.value = com.suguru.expensetracker.domain.model.ProEntitlement.Pro
        }
        return launchResult
    }
}
