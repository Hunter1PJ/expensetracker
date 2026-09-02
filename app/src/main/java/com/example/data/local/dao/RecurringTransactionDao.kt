package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 ORDER BY next_occurrence ASC")
    fun observeActiveRecurringTransactions(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY is_active DESC, next_occurrence ASC")
    fun observeAllRecurringTransactions(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    fun observeRecurringTransactionById(id: Long): Flow<RecurringTransactionEntity?>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getRecurringTransactionById(id: Long): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 AND next_occurrence <= :beforeOrOnDate ORDER BY next_occurrence ASC")
    suspend fun getRecurringTransactionsDue(beforeOrOnDate: LocalDate): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransactionEntity): Long

    @Update
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransactionEntity)

    @Query("UPDATE recurring_transactions SET is_active = 0 WHERE id = :id")
    suspend fun deactivateRecurringTransaction(id: Long)
}
