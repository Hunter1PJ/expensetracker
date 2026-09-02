package com.example.domain.model

/**
 * Supported types of financial transactions.
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

/**
 * Classification of categories.
 */
enum class CategoryType {
    EXPENSE,
    INCOME,
    BOTH
}

/**
 * Account classifications for financial tracking.
 */
enum class AccountType {
    CASH,
    BANK,
    CARD,
    SAVINGS,
    INVESTMENT,
    OTHER
}

/**
 * Recurrence frequencies for scheduled recurring transactions.
 */
enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY
}

/**
 * Budgeting timeframe periods.
 */
enum class BudgetPeriodType {
    MONTHLY,
    WEEKLY,
    YEARLY,
    CUSTOM
}
