package com.example.domain.model

import java.time.LocalDate

/**
 * Domain model representing a scheduled recurring transaction rule/template.
 */
data class RecurringTransaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amount: Money,
    val accountId: Long,
    val destinationAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: RecurrenceFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextOccurrence: LocalDate,
    val note: String? = null,
    val isActive: Boolean = true
)
