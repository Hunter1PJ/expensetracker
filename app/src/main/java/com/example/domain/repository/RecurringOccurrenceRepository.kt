package com.example.domain.repository

import com.example.domain.model.RecurringTransaction
import com.example.domain.model.Transaction
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
