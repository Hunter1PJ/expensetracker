package com.suguru.expensetracker.data.repository

import com.suguru.expensetracker.data.local.dao.TransactionDao
import com.suguru.expensetracker.data.mapper.toDomain
import com.suguru.expensetracker.data.mapper.toEntity
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomTransactionRepository(
    private val transactionDao: TransactionDao
) : TransactionRepository {

    override fun observeAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.observeAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> {
        return transactionDao.observeRecentTransactions(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecentTransactions(limit: Int): List<Transaction> {
        return transactionDao.getRecentTransactions(limit).map { it.toDomain() }
    }

    override fun observeTransactionsByAccount(accountId: Long): Flow<List<Transaction>> {
        return transactionDao.observeTransactionsByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionsByAccount(accountId: Long): List<Transaction> {
        return transactionDao.getTransactionsByAccount(accountId).map { it.toDomain() }
    }

    override fun observeTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> {
        return transactionDao.observeTransactionsByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTransactionsBetween(startTime: Instant, endTime: Instant): Flow<List<Transaction>> {
        return transactionDao.observeTransactionsBetween(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<Transaction> {
        return transactionDao.getTransactionsBetween(startTime, endTime).map { it.toDomain() }
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> {
        return transactionDao.observeTransactionById(id).map { it?.toDomain() }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun insertTransactions(transactions: List<Transaction>): List<Long> {
        return transactionDao.insertTransactions(transactions.map { it.toEntity() })
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction.toEntity())
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }
}
