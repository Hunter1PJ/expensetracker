package com.suguru.expensetracker.presentation.transactions.add

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.util.UiMessage
import java.time.Instant

/**
 * UI State for the Add Transaction screen.
 */
data class AddTransactionUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val defaultAccount: Account? = null,
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val transactionTime: Instant = Instant.now(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val error: AddTransactionError? = null,
    val isSavedSuccessfully: Boolean = false,
    val isAiAssisted: Boolean = false,
    val candidateSource: CandidateSource? = null,
    val aiAmountWarning: UiMessage? = null,
    val aiCategorySuggestion: String? = null,
    val aiCategoryMatchAmbiguous: Boolean = false,
    val aiWarnings: List<UiMessage> = emptyList(),
    val aiSourceWarnings: List<ParseWarning> = emptyList()
) {
    val selectedCategory: Category?
        get() = categories.find { it.id == selectedCategoryId }

    val currencyCode: String
        get() = defaultAccount?.initialBalance?.currencyCode ?: "USD"

    val canSave: Boolean
        get() = hasDefaultAccount && transactionType != TransactionType.TRANSFER

    val currencySymbol: String
        get() = MoneyParser.getCurrencySymbol(currencyCode)

    val hasDefaultAccount: Boolean
        get() = defaultAccount != null
}
