package com.example.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Money
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen, providing real-time financial balance from Room persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val observeActiveAccountsUseCase: ObserveActiveAccountsUseCase? = null,
    private val observeAccountBalanceUseCase: ObserveAccountBalanceUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeBalances()
    }

    private fun observeBalances() {
        val observeAccounts = observeActiveAccountsUseCase ?: return
        val observeBalance = observeAccountBalanceUseCase ?: return

        viewModelScope.launch {
            observeAccounts().flatMapLatest { accounts ->
                if (accounts.isEmpty()) {
                    flowOf("$0.00")
                } else {
                    val balanceFlows = accounts.map { account ->
                        observeBalance(account.id)
                    }
                    combine(balanceFlows) { balances ->
                        val nonNullBalances = balances.filterNotNull()
                        val primaryCurrency = accounts.first().initialBalance.currencyCode
                        val sameCurrency = accounts.all { it.initialBalance.currencyCode == primaryCurrency }
                        if (sameCurrency) {
                            val totalMinorUnits = nonNullBalances.sumOf { it.amountInMinorUnits }
                            MoneyParser.format(Money(totalMinorUnits, primaryCurrency))
                        } else {
                            val primaryBalance = nonNullBalances.firstOrNull() ?: Money.zero(primaryCurrency)
                            MoneyParser.format(primaryBalance)
                        }
                    }
                }
            }.collect { formatted ->
                _uiState.update { it.copy(balanceFormatted = formatted) }
            }
        }
    }
}
