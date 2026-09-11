package com.example.presentation.transactions.detail

import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType

/**
 * UI State for the Transaction Detail screen.
 */
data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val transaction: Transaction? = null,
    val type: TransactionType? = null,
    val amountFormatted: String = "",
    val currencyCode: String = "",
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null,
    val category: Category? = null,
    val note: String? = null,
    val dateTimeFormatted: String = "",
    val isDeleting: Boolean = false,
    val isDeletedSuccessfully: Boolean = false,
    val errorMessage: String? = null
) {
    val isExpense: Boolean get() = type == TransactionType.EXPENSE
    val isIncome: Boolean get() = type == TransactionType.INCOME
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    val isSourceAccountArchived: Boolean get() = sourceAccount?.isArchived == true
    val isDestinationAccountArchived: Boolean get() = destinationAccount?.isArchived == true
    val isCategoryArchived: Boolean get() = category?.isArchived == true
}
