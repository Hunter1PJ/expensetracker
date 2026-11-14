package com.suguru.expensetracker.presentation.transactions

import com.suguru.expensetracker.domain.model.Category

/**
 * UI State for the Transactions Screen.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedType: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val selectedCategoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val groupedTransactions: List<TransactionDateGroupUiModel> = emptyList(),
    val hasAnyTransactionsInDb: Boolean = false,
    val errorMessage: String? = null
) {
    val isFilterActive: Boolean
        get() = searchQuery.isNotBlank() ||
                selectedType != TransactionTypeFilter.ALL ||
                selectedCategoryId != null

    val totalMatchingCount: Int
        get() = groupedTransactions.sumOf { it.transactions.size }
}
