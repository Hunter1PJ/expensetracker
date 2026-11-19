package com.suguru.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CategoryUsageCount(
    val categoryId: Long,
    val count: Int
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY transaction_time DESC, created_at DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transaction_time DESC, created_at DESC, id DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transaction_time DESC, created_at DESC, id DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId OR destination_account_id = :accountId ORDER BY transaction_time DESC, id DESC")
    fun observeTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId OR destination_account_id = :accountId ORDER BY transaction_time DESC, id DESC")
    suspend fun getTransactionsByAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY transaction_time DESC, id DESC")
    fun observeTransactionsByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT category_id AS categoryId, COUNT(*) AS count FROM transactions WHERE type = 'EXPENSE' AND category_id IS NOT NULL GROUP BY category_id")
    fun observeExpenseCategoryUsageCounts(): Flow<List<CategoryUsageCount>>

    @Query("SELECT category_id AS categoryId, COUNT(*) AS count FROM transactions WHERE type = 'EXPENSE' AND category_id IS NOT NULL GROUP BY category_id")
    suspend fun getExpenseCategoryUsageCounts(): List<CategoryUsageCount>

    @Query("SELECT category_id AS categoryId, COUNT(*) AS count FROM transactions WHERE type = 'INCOME' AND category_id IS NOT NULL GROUP BY category_id")
    fun observeIncomeCategoryUsageCounts(): Flow<List<CategoryUsageCount>>

    @Query("SELECT * FROM transactions WHERE transaction_time >= :startTime AND transaction_time < :endTime ORDER BY transaction_time DESC, id DESC")
    fun observeTransactionsBetween(startTime: Instant, endTime: Instant): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transaction_time >= :startTime AND transaction_time < :endTime ORDER BY transaction_time DESC, id DESC")
    suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}
