package com.example.domain.repository

import com.example.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain repository interface for managing financial transactions.
 * Purely decoupled from persistence and framework dependencies.
 */
interface TransactionRepository {
    fun observeAllTransactions(): Flow<List<Transaction>>
    fun observeTransactionsByAccount(accountId: Long): Flow<List<Transaction>>
    suspend fun getTransactionsByAccount(accountId: Long): List<Transaction>
    fun observeTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    fun observeTransactionsBetween(startTime: Instant, endTime: Instant): Flow<List<Transaction>>
    suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<Transaction>
    fun observeTransactionById(id: Long): Flow<Transaction?>
    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun insertTransactions(transactions: List<Transaction>): List<Long>
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransactionById(id: Long)
}
