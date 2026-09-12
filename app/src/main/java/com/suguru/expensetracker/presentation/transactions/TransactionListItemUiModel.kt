package com.suguru.expensetracker.presentation.transactions

import com.suguru.expensetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate

/**
 * UI representation of a single transaction item in the transactions list.
 */
data class TransactionListItemUiModel(
    val id: Long,
    val type: TransactionType,
    val formattedAmount: String,
    val isPositive: Boolean?, // true: Income (+), false: Expense (-), null: Transfer (neutral)
    val title: String,
    val subtitle: String,
    val iconName: String?,
    val colorHex: String?,
    val timeFormatted: String,
    val rawInstant: Instant,
    val rawDate: LocalDate,
    val isRecurring: Boolean = false
)

/**
 * UI representation of a group of transactions belonging to a single calendar date.
 */
data class TransactionDateGroupUiModel(
    val date: LocalDate,
    val headerTitle: String,
    val transactions: List<TransactionListItemUiModel>
)
