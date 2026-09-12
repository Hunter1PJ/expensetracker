package com.suguru.expensetracker.data.repository

import com.suguru.expensetracker.data.local.dao.RecurringTransactionDao
import com.suguru.expensetracker.data.mapper.toDomain
import com.suguru.expensetracker.data.mapper.toEntity
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomRecurringTransactionRepository(
    private val recurringTransactionDao: RecurringTransactionDao
) : RecurringTransactionRepository {

    override fun observeActiveRecurringTransactions(): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.observeActiveRecurringTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllRecurringTransactions(): Flow<List<RecurringTransaction>> {
        return recurringTransactionDao.observeAllRecurringTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeRecurringTransactionById(id: Long): Flow<RecurringTransaction?> {
        return recurringTransactionDao.observeRecurringTransactionById(id).map { it?.toDomain() }
    }

    override suspend fun getRecurringTransactionById(id: Long): RecurringTransaction? {
        return recurringTransactionDao.getRecurringTransactionById(id)?.toDomain()
    }

    override suspend fun getRecurringTransactionsDue(beforeOrOnDate: LocalDate): List<RecurringTransaction> {
        return recurringTransactionDao.getRecurringTransactionsDue(beforeOrOnDate).map { it.toDomain() }
    }

    override suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long {
        return recurringTransactionDao.insertRecurringTransaction(recurringTransaction.toEntity())
    }

    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        recurringTransactionDao.updateRecurringTransaction(recurringTransaction.toEntity())
    }

    override suspend fun deactivateRecurringTransaction(id: Long) {
        recurringTransactionDao.deactivateRecurringTransaction(id)
    }
}
