package com.suguru.expensetracker.presentation.accounts.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.CurrencyInfo
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.usecase.account.CreateAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.UpdateAccountUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Creating and Editing Accounts.
 * Handles validation, currency decimal safety, and Room persistence.
 */
class AddAccountViewModel(
    private val accountId: Long = 0L,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val getAccountUseCase: GetAccountUseCase,
    private val settingsRepository: com.suguru.expensetracker.domain.repository.SettingsRepository? = null,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddAccountUiState(
            accountId = accountId,
            isEditMode = accountId > 0L,
            isCurrencyEditable = accountId == 0L,
            isInitialBalanceEditable = accountId == 0L
        )
    )
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    private var existingAccount: Account? = null

    init {
        if (accountId > 0L) {
            loadExistingAccount(accountId)
        } else if (settingsRepository != null) {
            viewModelScope.launch {
                try {
                    val settings = settingsRepository.settings.first()
                    settings.preferredCurrencyCode?.let { code ->
                        val info = CurrencyInfo.findByCode(code)
                        _uiState.update { it.copy(selectedCurrency = info) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun loadExistingAccount(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val account = getAccountUseCase(id)
                if (account != null) {
                    existingAccount = account
                    val currencyInfo = CurrencyInfo.findByCode(account.initialBalance.currencyCode)
                    val formattedInitial = MoneyParser.format(account.initialBalance, includeSymbol = false)
                    _uiState.update {
                        it.copy(
                            accountId = account.id,
                            isEditMode = true,
                            name = account.name,
                            type = account.type,
                            selectedCurrency = currencyInfo,
                            initialBalanceInput = formattedInitial,
                            selectedIconName = account.iconName,
                            selectedColorHex = account.colorHex,
                            isCurrencyEditable = false,
                            isInitialBalanceEditable = false,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = AddAccountError.AccountNotFound
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AddAccountError.SaveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onTypeChanged(type: AccountType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onCurrencyChanged(currency: CurrencyInfo) {
        if (!_uiState.value.isCurrencyEditable) return
        _uiState.update { it.copy(selectedCurrency = currency, error = null) }
    }

    fun onInitialBalanceChanged(input: String) {
        if (!_uiState.value.isInitialBalanceEditable) return
        // Allow numeric inputs and decimal separator
        val sanitized = input.filter { it.isDigit() || it == '.' || it == ',' }
        _uiState.update { it.copy(initialBalanceInput = sanitized, error = null) }
    }

    fun onIconChanged(iconName: String) {
        _uiState.update { it.copy(selectedIconName = iconName) }
    }

    fun onColorChanged(colorHex: String) {
        _uiState.update { it.copy(selectedColorHex = colorHex) }
    }

    fun saveAccount() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return

        val trimmedName = state.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = AddAccountError.NameRequired) }
            return
        }

        val initialBalance: Money
        if (state.isEditMode && existingAccount != null) {
            initialBalance = existingAccount!!.initialBalance
        } else {
            val parseResult = MoneyParser.parse(
                input = state.initialBalanceInput.ifBlank { "0" },
                currencyCode = state.selectedCurrency.currencyCode
            )
            if (parseResult.isFailure) {
                _uiState.update {
                    it.copy(
                        error = AddAccountError.InvalidInitialBalance(
                            parseResult.exceptionOrNull()?.message
                        )
                    )
                }
                return
            }
            initialBalance = parseResult.getOrThrow()
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                if (state.isEditMode) {
                    val current = existingAccount ?: getAccountUseCase(state.accountId)
                    if (current == null) {
                        _uiState.update {
                            it.copy(isSaving = false, error = AddAccountError.AccountNotFound)
                        }
                        return@launch
                    }

                    val updated = current.copy(
                        name = trimmedName,
                        type = state.type,
                        iconName = state.selectedIconName,
                        colorHex = state.selectedColorHex
                    )
                    updateAccountUseCase(updated)
                    widgetRefreshCoordinator?.refreshAccountBalanceWidgets(updated.id)
                } else {
                    val newAccount = Account(
                        id = 0L,
                        name = trimmedName,
                        type = state.type,
                        initialBalance = initialBalance,
                        colorHex = state.selectedColorHex,
                        iconName = state.selectedIconName,
                        isArchived = false
                    )
                    createAccountUseCase(newAccount)
                    widgetRefreshCoordinator?.refreshAll()
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSavedSuccessfully = true
                    )
                }
            } catch (e: DomainException.FeatureLimitReached) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        limitReachedState = com.suguru.expensetracker.domain.model.FeatureGateResult.LimitReached(
                            feature = e.feature,
                            currentCount = e.currentCount,
                            freeLimit = e.freeLimit
                        )
                    )
                }
            } catch (e: DomainException.InvalidAccount) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AddAccountError.NameRequired
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AddAccountError.SaveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onDismissLimitSheet() {
        _uiState.update { it.copy(limitReachedState = null) }
    }
}
