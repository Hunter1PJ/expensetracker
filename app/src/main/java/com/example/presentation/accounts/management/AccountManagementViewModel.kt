package com.example.presentation.accounts.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.error.DomainException
import com.example.domain.model.Account
import com.example.domain.usecase.account.ArchiveAccountUseCase
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Account Management screen.
 * Observes active accounts and derived balances from Room database via clean use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModel(
    private val observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    private val observeAccountBalanceUseCase: ObserveAccountBalanceUseCase,
    private val archiveAccountUseCase: ArchiveAccountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManagementUiState())
    val uiState: StateFlow<AccountManagementUiState> = _uiState.asStateFlow()

    init {
        observeAccountsAndBalances()
    }

    private fun observeAccountsAndBalances() {
        viewModelScope.launch {
            observeActiveAccountsUseCase()
                .flatMapLatest { accounts ->
                    if (accounts.isEmpty()) {
                        flowOf(emptyList<AccountItemUiState>())
                    } else {
                        val accountItemFlows = accounts.map { account ->
                            observeAccountBalanceUseCase(account.id).map { balance ->
                                val effectiveBalance = balance ?: account.initialBalance
                                val formatted = MoneyParser.format(effectiveBalance)
                                AccountItemUiState(
                                    account = account,
                                    balance = effectiveBalance,
                                    balanceFormatted = formatted
                                )
                            }
                        }
                        combine(accountItemFlows) { itemsArray ->
                            itemsArray.toList()
                        }
                    }
                }
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = AccountManagementError.ArchiveFailed(exception.message)
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            accounts = items,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onArchiveClicked(account: Account) {
        _uiState.update { it.copy(accountToArchive = account) }
    }

    fun onDismissArchiveDialog() {
        _uiState.update { it.copy(accountToArchive = null) }
    }

    fun onConfirmArchive() {
        val account = _uiState.value.accountToArchive ?: return
        if (_uiState.value.isArchiving) return

        _uiState.update { it.copy(isArchiving = true) }
        viewModelScope.launch {
            try {
                archiveAccountUseCase(account.id)
                _uiState.update {
                    it.copy(
                        accountToArchive = null,
                        isArchiving = false
                    )
                }
            } catch (e: DomainException.AccountNotFound) {
                _uiState.update {
                    it.copy(
                        accountToArchive = null,
                        isArchiving = false,
                        error = AccountManagementError.AccountNotFound
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        accountToArchive = null,
                        isArchiving = false,
                        error = AccountManagementError.ArchiveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
