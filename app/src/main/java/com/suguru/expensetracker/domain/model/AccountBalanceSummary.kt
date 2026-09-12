package com.suguru.expensetracker.domain.model

/**
 * Domain model pairing an account with its derived balance.
 */
data class AccountBalanceSummary(
    val account: Account,
    val balance: Money
)
