package com.suguru.expensetracker.presentation.transactions.add

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.parser.CandidateSource
import com.suguru.expensetracker.domain.parser.ParseWarning
import com.suguru.expensetracker.domain.util.MoneyParser
import java.time.Instant

/**
 * UI State for the Add Transaction screen.
 */
data class AddTransactionUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val defaultAccount: Account? = null,
    val activeAccounts: List<Account> = emptyList(),
    val selectedTransferSourceAccountId: Long? = null,
    val selectedTransferDestinationAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val transactionTime: Instant = Instant.now(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val error: AddTransactionError? = null,
    val isSavedSuccessfully: Boolean = false,
    val isAiAssisted: Boolean = false,
    val candidateSource: CandidateSource? = null,
    val aiAmountWarning: String? = null,
    val aiCategorySuggestion: String? = null,
    val aiCategoryMatchAmbiguous: Boolean = false,
    val aiWarnings: List<String> = emptyList(),
    val aiSourceWarnings: List<ParseWarning> = emptyList()
) {
    val selectedCategory: Category?
        get() = categories.find { it.id == selectedCategoryId }

    val transferSourceAccount: Account?
        get() = activeAccounts.find { it.id == selectedTransferSourceAccountId }

    val transferDestinationAccount: Account?
        get() = activeAccounts.find { it.id == selectedTransferDestinationAccountId }

    val currencyCode: String
        get() = if (transactionType == TransactionType.TRANSFER) {
            transferSourceAccount?.initialBalance?.currencyCode ?: defaultAccount?.initialBalance?.currencyCode ?: "USD"
        } else {
            defaultAccount?.initialBalance?.currencyCode ?: "USD"
        }

    val canSave: Boolean
        get() = if (transactionType == TransactionType.TRANSFER) {
            val source = transferSourceAccount
            val destination = transferDestinationAccount
            source != null && destination != null && source.id != destination.id
        } else {
            hasDefaultAccount
        }

    val currencySymbol: String
        get() = MoneyParser.getCurrencySymbol(currencyCode)

    val hasDefaultAccount: Boolean
        get() = defaultAccount != null
}
