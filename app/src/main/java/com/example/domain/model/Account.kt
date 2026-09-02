package com.example.domain.model

import java.time.Instant

/**
 * Domain model representing a financial account (e.g., Wallet, Bank Account, Credit Card).
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val initialBalance: Money,
    val iconName: String = "account_balance",
    val colorHex: String = "#10B981",
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.now()
)
