package com.suguru.expensetracker.presentation.accounts.add

import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.CurrencyInfo

sealed interface AddAccountError {
    data object NameRequired : AddAccountError
    data class InvalidInitialBalance(val message: String?) : AddAccountError
    data object AccountNotFound : AddAccountError
    data class SaveFailed(val message: String?) : AddAccountError
}

data class AddAccountUiState(
    val accountId: Long = 0L,
    val isEditMode: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val selectedCurrency: CurrencyInfo = CurrencyInfo.DEFAULT,
    val initialBalanceInput: String = "0",
    val selectedIconName: String = "account_balance",
    val selectedColorHex: String = "#10B981",
    val isCurrencyEditable: Boolean = true,
    val isInitialBalanceEditable: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedSuccessfully: Boolean = false,
    val error: AddAccountError? = null
)
