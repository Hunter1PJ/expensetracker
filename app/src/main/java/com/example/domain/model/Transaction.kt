package com.example.domain.model

import java.time.Instant

/**
 * Domain model representing a financial ledger transaction.
 *
 * For EXPENSE and INCOME:
 * - [accountId] represents the active account.
 * - [destinationAccountId] is null.
 * - [categoryId] references the assigned category.
 *
 * For TRANSFER:
 * - [accountId] represents the SOURCE account (outflow).
 * - [destinationAccountId] represents the DESTINATION account (inflow).
 * - [categoryId] is optional/null or system transfer category.
 */
data class Transaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amount: Money,
    val accountId: Long,
    val destinationAccountId: Long? = null,
    val categoryId: Long? = null,
    val transactionTime: Instant,
    val note: String? = null,
    val recurringRuleId: Long? = null,
    val createdAt: Instant = Instant.now()
)
