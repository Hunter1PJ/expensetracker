package com.suguru.expensetracker.domain.model

/**
 * Domain summary model representing aggregated financial totals for a single currency.
 *
 * All balances, incomes, expenses, and net amounts are strictly calculated in minor units
 * without floating-point arithmetic.
 */
data class CurrencySummary(
    val currencyCode: String,
    val totalBalance: Money,
    val monthlyIncome: Money,
    val monthlyExpense: Money,
    val monthlyNet: Money
)
