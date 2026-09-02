package com.example.presentation.transactions.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.error.DomainException
import com.example.domain.model.CategoryType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel managing the state and business operations for creating a new financial transaction.
 * Communicates strictly with domain use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModel(
    private val observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    private val observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase,
    private val createTransactionUseCase: CreateTransactionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        observeAccounts()
        observeCategories()
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            observeActiveAccountsUseCase().collect { accounts ->
                _uiState.update { state ->
                    val isCurrentSelectionValid = accounts.any { it.id == state.selectedAccountId }
                    val updatedAccountId = if (isCurrentSelectionValid) {
                        state.selectedAccountId
                    } else {
                        accounts.firstOrNull()?.id
                    }

                    // Also check destination account validity if transfer
                    val isDestValid = accounts.any { it.id == state.selectedDestinationAccountId }
                    val updatedDestId = if (isDestValid) state.selectedDestinationAccountId else null

                    state.copy(
                        accounts = accounts,
                        selectedAccountId = updatedAccountId,
                        selectedDestinationAccountId = updatedDestId
                    )
                }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            _uiState
                .map { it.transactionType }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    when (type) {
                        TransactionType.EXPENSE -> observeCategoriesByTypeUseCase(CategoryType.EXPENSE)
                        TransactionType.INCOME -> observeCategoriesByTypeUseCase(CategoryType.INCOME)
                        TransactionType.TRANSFER -> flowOf(emptyList())
                    }
                }
                .collect { categories ->
                    _uiState.update { state ->
                        val isSelectedCategoryValid = categories.any { it.id == state.selectedCategoryId }
                        val updatedCategoryId = if (state.transactionType == TransactionType.TRANSFER) {
                            null
                        } else if (isSelectedCategoryValid) {
                            state.selectedCategoryId
                        } else {
                            categories.firstOrNull()?.id
                        }

                        state.copy(
                            categories = categories,
                            selectedCategoryId = updatedCategoryId
                        )
                    }
                }
        }
    }

    fun onTransactionTypeChanged(type: TransactionType) {
        _uiState.update { state ->
            state.copy(
                transactionType = type,
                selectedCategoryId = if (type == TransactionType.TRANSFER) null else state.selectedCategoryId,
                selectedDestinationAccountId = if (type == TransactionType.TRANSFER) state.selectedDestinationAccountId else null,
                error = null
            )
        }
    }

    fun onAmountChanged(amount: String) {
        val sanitized = amount.replace(',', '.')
        // Allow empty or valid decimal input patterns (e.g., "12", "12.5", "12.50")
        if (sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
            _uiState.update { it.copy(amount = sanitized, error = null) }
        }
    }

    fun onAccountSelected(accountId: Long) {
        _uiState.update { state ->
            val newSelectedAccount = state.accounts.find { it.id == accountId }
            val newCurrency = newSelectedAccount?.initialBalance?.currencyCode

            // If in transfer mode and destination account now has mismatched currency or same account, reset destination
            val destAccount = state.accounts.find { it.id == state.selectedDestinationAccountId }
            val isDestStillValid = destAccount != null &&
                    destAccount.id != accountId &&
                    destAccount.initialBalance.currencyCode == newCurrency

            state.copy(
                selectedAccountId = accountId,
                selectedDestinationAccountId = if (isDestStillValid) state.selectedDestinationAccountId else null,
                error = null
            )
        }
    }

    fun onDestinationAccountSelected(accountId: Long) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, error = null) }
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, error = null) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onTransactionTimeChanged(time: Instant) {
        _uiState.update { it.copy(transactionTime = time) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveTransaction(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return

        // 1. Validate Account existence
        if (!state.hasAccounts || state.selectedAccountId == null) {
            _uiState.update { it.copy(error = AddTransactionError.AccountRequired) }
            return
        }

        val sourceAccount = state.selectedAccount
        if (sourceAccount == null) {
            _uiState.update { it.copy(error = AddTransactionError.AccountRequired) }
            return
        }

        // 2. Validate Amount
        val parseResult = MoneyParser.parse(state.amount, sourceAccount.initialBalance.currencyCode)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(error = AddTransactionError.InvalidAmount) }
            return
        }

        val money = parseResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(error = AddTransactionError.AmountMustBePositive) }
            return
        }

        // 3. Validate Category / Destination based on type
        when (state.transactionType) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (state.selectedCategoryId == null) {
                    _uiState.update { it.copy(error = AddTransactionError.CategoryRequired) }
                    return
                }
            }
            TransactionType.TRANSFER -> {
                if (state.selectedDestinationAccountId == null) {
                    _uiState.update { it.copy(error = AddTransactionError.DestinationRequired) }
                    return
                }
                if (state.selectedAccountId == state.selectedDestinationAccountId) {
                    _uiState.update { it.copy(error = AddTransactionError.SameSourceAndDestination) }
                    return
                }
                val destAccount = state.selectedDestinationAccount
                if (destAccount == null || destAccount.initialBalance.currencyCode != sourceAccount.initialBalance.currencyCode) {
                    _uiState.update { it.copy(error = AddTransactionError.CurrencyMismatch) }
                    return
                }
            }
        }

        // 4. Construct domain transaction
        val transaction = Transaction(
            type = state.transactionType,
            amount = money,
            accountId = sourceAccount.id,
            destinationAccountId = if (state.transactionType == TransactionType.TRANSFER) state.selectedDestinationAccountId else null,
            categoryId = if (state.transactionType != TransactionType.TRANSFER) state.selectedCategoryId else null,
            note = state.note.trim().ifEmpty { null },
            transactionTime = state.transactionTime
        )

        // 5. Execute use case
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                createTransactionUseCase(transaction)
                _uiState.update { it.copy(isSaving = false, isSavedSuccessfully = true, error = null) }
                onSuccess()
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.fromDomainException(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = AddTransactionError.Unknown(e.localizedMessage ?: e.message)) }
            }
        }
    }
}
