package com.suguru.expensetracker.presentation.accounts.management

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Money

sealed interface AccountManagementError {
    data object AccountNotFound : AccountManagementError
    data class ArchiveFailed(val message: String?) : AccountManagementError
}

data class AccountItemUiState(
    val account: Account,
    val balance: Money?,
    val balanceFormatted: String
)

data class AccountManagementUiState(
    val accounts: List<AccountItemUiState> = emptyList(),
    val isLoading: Boolean = true,
    val accountToArchive: Account? = null,
    val isArchiving: Boolean = false,
    val error: AccountManagementError? = null
)
