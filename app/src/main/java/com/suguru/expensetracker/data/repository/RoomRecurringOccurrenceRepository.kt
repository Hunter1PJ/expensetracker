package com.suguru.expensetracker.data.repository

import androidx.room.withTransaction
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.RecurringOccurrenceEntity
import com.suguru.expensetracker.data.mapper.toEntity
import com.suguru.expensetracker.domain.model.RecurringOccurrenceStatus
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.RecurringOccurrenceRepository
import java.time.Instant
import java.time.LocalDate

class RoomRecurringOccurrenceRepository(
    private val database: ExpenseTrackerDatabase
) : RecurringOccurrenceRepository {

    private val occurrenceDao = database.recurringOccurrenceDao()
    private val transactionDao = database.transactionDao()
    private val recurringTransactionDao = database.recurringTransactionDao()

    override suspend fun isOccurrenceProcessed(ruleId: Long, occurrenceDate: LocalDate): Boolean {
        return occurrenceDao.getOccurrence(ruleId, occurrenceDate) != null
    }

    override suspend fun processAtomicOccurrence(
        rule: RecurringTransaction,
        occurrenceDate: LocalDate,
        transactionToInsert: Transaction,
        newNextOccurrence: LocalDate,
        isRuleNowActive: Boolean
    ): Long? {
        return database.withTransaction {
            val existing = occurrenceDao.getOccurrence(rule.id, occurrenceDate)
            if (existing != null) {
                // Already processed (either GENERATED or TRANSACTION_DELETED).
                // Ensure rule nextOccurrence advances so processing doesn't stall.
                val dbRule = recurringTransactionDao.getRecurringTransactionById(rule.id)
                if (dbRule != null && dbRule.nextOccurrence == occurrenceDate) {
                    recurringTransactionDao.updateRecurringTransaction(
                        dbRule.copy(nextOccurrence = newNextOccurrence, isActive = isRuleNowActive)
                    )
                }
                return@withTransaction null
            }

            val insertedTxId = transactionDao.insertTransaction(transactionToInsert.toEntity())

            occurrenceDao.insertOccurrence(
                RecurringOccurrenceEntity(
                    ruleId = rule.id,
                    occurrenceDate = occurrenceDate,
                    generatedTransactionId = insertedTxId,
                    status = RecurringOccurrenceStatus.GENERATED,
                    processedAt = Instant.now()
                )
            )

            val updatedRuleEntity = rule.copy(
                nextOccurrence = newNextOccurrence,
                isActive = isRuleNowActive
            ).toEntity()

            recurringTransactionDao.updateRecurringTransaction(updatedRuleEntity)

            insertedTxId
        }
    }

    override suspend fun markTransactionDeleted(ruleId: Long, occurrenceDate: LocalDate) {
        database.withTransaction {
            val existing = occurrenceDao.getOccurrence(ruleId, occurrenceDate)
            if (existing != null) {
                occurrenceDao.markTransactionDeleted(ruleId, occurrenceDate)
            } else {
                occurrenceDao.insertOccurrence(
                    RecurringOccurrenceEntity(
                        ruleId = ruleId,
                        occurrenceDate = occurrenceDate,
                        generatedTransactionId = null,
                        status = RecurringOccurrenceStatus.TRANSACTION_DELETED,
                        processedAt = Instant.now()
                    )
                )
            }
        }
    }
}
