package com.suguru.expensetracker.domain.model.export

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate

/**
 * Resolved, presentation-ready immutable transaction row for CSV exporting and PDF reporting.
 *
 * For TRANSFER:
 * - [sourceAccountName] is the source outflow account
 * - [destinationAccountName] is the destination inflow account
 * - [categoryName] is null
 */
data class ExportTransactionRow(
    val transactionId: Long,
    val transactionTime: Instant,
    val type: TransactionType,
    val amount: Money,
    val currencyCode: String,
    val sourceAccountName: String,
    val destinationAccountName: String? = null,
    val categoryName: String? = null,
    val note: String? = null,
    val recurringRuleId: Long? = null,
    val recurringOccurrenceDate: LocalDate? = null,
    val categoryIsSystem: Boolean = false
)
