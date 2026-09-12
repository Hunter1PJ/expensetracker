package com.suguru.expensetracker.presentation.transactions.edit

import com.suguru.expensetracker.domain.model.Account
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
    val selectedAccountId: Long? = null,
    val selectedDestinationAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val transactionTime: Instant = Instant.now(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AddTransactionError? = null,
    val isSavedSuccessfully: Boolean = false
) {
    val selectedAccount: Account?
        get() = accounts.find { it.id == selectedAccountId }

    val selectedDestinationAccount: Account?
        get() = accounts.find { it.id == selectedDestinationAccountId }

    val selectedCategory: Category?
        get() = categories.find { it.id == selectedCategoryId }

    val currencyCode: String
        get() = selectedAccount?.initialBalance?.currencyCode ?: "USD"

    val currencySymbol: String
        get() = MoneyParser.getCurrencySymbol(currencyCode)

    val validDestinationAccounts: List<Account>
        get() = accounts.filter {
            it.id != selectedAccountId && it.initialBalance.currencyCode == currencyCode
        }

    val hasAccounts: Boolean
        get() = accounts.isNotEmpty()
}
