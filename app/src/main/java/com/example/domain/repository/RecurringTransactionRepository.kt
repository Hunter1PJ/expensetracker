package com.example.domain.repository

import com.example.domain.model.RecurringTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Domain repository interface for managing scheduled recurring transactions.
 * Purely decoupled from persistence and framework dependencies.
 */
interface RecurringTransactionRepository {
    fun observeActiveRecurringTransactions(): Flow<List<RecurringTransaction>>
    fun observeAllRecurringTransactions(): Flow<List<RecurringTransaction>>
    fun observeRecurringTransactionById(id: Long): Flow<RecurringTransaction?>
    suspend fun getRecurringTransactionById(id: Long): RecurringTransaction?
    suspend fun getRecurringTransactionsDue(beforeOrOnDate: LocalDate): List<RecurringTransaction>
    suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long
    suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction)
    suspend fun deactivateRecurringTransaction(id: Long)
}
