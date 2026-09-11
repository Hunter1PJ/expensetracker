package com.example.presentation.transactions

import com.example.domain.model.Account
import com.example.domain.model.Category

/**
 * UI State for the Transactions Screen.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedType: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val groupedTransactions: List<TransactionDateGroupUiModel> = emptyList(),
    val hasAnyTransactionsInDb: Boolean = false,
    val errorMessage: String? = null
) {
    val isFilterActive: Boolean
        get() = searchQuery.isNotBlank() ||
                selectedType != TransactionTypeFilter.ALL ||
                selectedAccountId != null ||
                selectedCategoryId != null

    val totalMatchingCount: Int
        get() = groupedTransactions.sumOf { it.transactions.size }
}
