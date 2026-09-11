package com.example.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.budget.BudgetProgress
import com.example.domain.usecase.budget.DeactivateBudgetUseCase
import com.example.domain.usecase.budget.ObserveActiveBudgetProgressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList(),
    val availableCurrencies: List<String> = emptyList(),
    val selectedCurrencyFilter: String? = null,
    val deactivatingBudgetId: Long? = null,
    val errorMessage: String? = null
)

class BudgetsViewModel(
    observeActiveBudgetProgressUseCase: ObserveActiveBudgetProgressUseCase,
    private val deactivateBudgetUseCase: DeactivateBudgetUseCase
) : ViewModel() {

    private val _selectedCurrency = MutableStateFlow<String?>(null)
    private val _deactivatingBudgetId = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BudgetsUiState> = combine(
        observeActiveBudgetProgressUseCase(),
        _selectedCurrency,
        _deactivatingBudgetId,
        _errorMessage
    ) { progressList, selectedCurrency, deactivatingId, errorMsg ->
        val currencies = progressList.map { it.budget.limitAmount.currencyCode }.distinct().sorted()
        val filtered = if (selectedCurrency == null || !currencies.contains(selectedCurrency)) {
            progressList
        } else {
            progressList.filter { it.budget.limitAmount.currencyCode == selectedCurrency }
        }

        BudgetsUiState(
            isLoading = false,
            budgets = filtered,
            availableCurrencies = currencies,
            selectedCurrencyFilter = if (currencies.contains(selectedCurrency)) selectedCurrency else null,
            deactivatingBudgetId = deactivatingId,
            errorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState(isLoading = true)
    )

    fun selectCurrencyFilter(currency: String?) {
        _selectedCurrency.value = currency
    }

    fun promptDeactivate(id: Long) {
        _deactivatingBudgetId.value = id
    }

    fun cancelDeactivate() {
        _deactivatingBudgetId.value = null
    }

    fun confirmDeactivate() {
        val id = _deactivatingBudgetId.value ?: return
        viewModelScope.launch {
            try {
                deactivateBudgetUseCase(id)
                _deactivatingBudgetId.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to deactivate budget"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
