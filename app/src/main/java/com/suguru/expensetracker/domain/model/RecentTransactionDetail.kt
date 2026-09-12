package com.suguru.expensetracker.domain.model

/**
 * Domain model representing a transaction enriched with resolved account and category details.
 */
data class RecentTransactionDetail(
    val transaction: Transaction,
    val account: Account?,
    val destinationAccount: Account? = null,
    val category: Category? = null
)
