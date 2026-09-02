package com.example.presentation.transactions.add

import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.TransactionType
import com.example.domain.util.MoneyParser
import java.time.Instant

/**
 * UI State for the Add Transaction screen.
 */
data class AddTransactionUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val selectedAccountId: Long? = null,
    val selectedDestinationAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val note: String = "",
    val transactionTime: Instant = Instant.now(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
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
