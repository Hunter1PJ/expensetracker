package com.suguru.expensetracker.presentation.transactions.edit

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionError
import java.time.Instant

/**
 * UI State for the Edit Transaction screen.
 */
data class EditTransactionUiState(
    val transactionId: Long = 0L,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val currencyCode: String = "USD",
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val transactionTime: Instant = Instant.now(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val isTransactionLoaded: Boolean = false,
    val isReadOnlyTransfer: Boolean = false,
    val transferFromAccountName: String? = null,
    val transferToAccountName: String? = null,
    val transferAmountFormatted: String = "",
    val isSaving: Boolean = false,
    val error: AddTransactionError? = null,
    val isSavedSuccessfully: Boolean = false
) {
    val selectedCategory: Category?
        get() = categories.find { it.id == selectedCategoryId }

    val currencySymbol: String
        get() = MoneyParser.getCurrencySymbol(currencyCode)
}
