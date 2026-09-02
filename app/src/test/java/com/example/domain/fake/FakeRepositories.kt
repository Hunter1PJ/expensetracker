package com.example.domain.fake

import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Transaction
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val id = if (account.id == 0L) nextId++ else account.id
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
        val id = if (category.id == 0L) nextId++ else category.id
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
            map.values.filter { !it.transactionTime.isBefore(startTime) && !it.transactionTime.isAfter(endTime) }
                .sortedByDescending { it.transactionTime }
        }

    override suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<Transaction> =
        transactions.value.values.filter { !it.transactionTime.isBefore(startTime) && !it.transactionTime.isAfter(endTime) }
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
