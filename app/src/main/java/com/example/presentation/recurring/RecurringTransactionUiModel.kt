package com.example.presentation.recurring

import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import java.time.LocalDate

enum class RecurringStatus {
    ACTIVE,
    PAUSED,
    ENDING_SOON,
    NEEDS_ATTENTION
}

data class RecurringTransactionUiModel(
    val id: Long,
    val type: TransactionType,
    val formattedAmount: String,
    val accountName: String,
    val destinationAccountName: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val frequency: RecurrenceFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextOccurrence: LocalDate,
    val note: String?,
    val isActive: Boolean,
    val status: RecurringStatus,
    val statusDetail: String? = null
)
