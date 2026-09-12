package com.suguru.expensetracker.presentation.home

import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.TransactionType

data class CurrencySummaryUiModel(
    val currencyCode: String,
    val balanceFormatted: String,
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val isNetPositive: Boolean,
    val isNetNegative: Boolean
)

data class AccountSummaryUiModel(
    val id: Long,
    val name: String,
    val type: AccountType,
    val typeName: String,
    val currencyCode: String,
    val iconName: String,
    val colorHex: String,
    val balanceFormatted: String
)

data class RecentTransactionUiModel(
    val id: Long,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val accountName: String,
    val amountFormatted: String,
    val formattedDate: String,
    val iconName: String,
    val colorHex: String,
    val isPositive: Boolean?,
    val isRecurring: Boolean = false
)

/**
 * UI State for the financial Home dashboard.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val currencySummaries: List<CurrencySummaryUiModel> = emptyList(),
    val accounts: List<AccountSummaryUiModel> = emptyList(),
    val recentTransactions: List<RecentTransactionUiModel> = emptyList(),
    val currentMonthName: String = "This Month",
    val errorMessage: String? = null
) {
    val hasAccounts: Boolean get() = accounts.isNotEmpty()
    val hasTransactions: Boolean get() = recentTransactions.isNotEmpty()
    val isMultiCurrency: Boolean get() = currencySummaries.size > 1
}
