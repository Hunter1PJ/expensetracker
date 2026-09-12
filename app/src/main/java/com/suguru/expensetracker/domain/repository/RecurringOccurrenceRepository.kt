package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.Transaction
import java.time.LocalDate

interface RecurringOccurrenceRepository {
    suspend fun isOccurrenceProcessed(ruleId: Long, occurrenceDate: LocalDate): Boolean

    suspend fun processAtomicOccurrence(
        rule: RecurringTransaction,
        occurrenceDate: LocalDate,
        transactionToInsert: Transaction,
        newNextOccurrence: LocalDate,
        isRuleNowActive: Boolean
    ): Long?

    suspend fun markTransactionDeleted(ruleId: Long, occurrenceDate: LocalDate)
}
