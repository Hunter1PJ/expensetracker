package com.example.presentation.transactions.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.error.DomainException
import com.example.domain.model.CategoryType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.GetAccountUseCase
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.category.GetCategoryUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.transaction.GetTransactionUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.domain.util.MoneyParser
import com.example.presentation.transactions.add.AddTransactionError
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
 * ViewModel managing state and operations for editing an existing financial transaction.
 * Preserves historical archived accounts and categories while strictly enforcing domain rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTransactionViewModel(
    private val transactionId: Long,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    private val observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase,
    private val getAccountUseCase: GetAccountUseCase,
    private val getCategoryUseCase: GetCategoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditTransactionUiState(transactionId = transactionId, isLoading = true))
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    private var historicalSourceAccount: com.example.domain.model.Account? = null
    private var historicalDestAccount: com.example.domain.model.Account? = null
    private var historicalCategory: com.example.domain.model.Category? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val tx = getTransactionUseCase(transactionId)
                if (tx == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = AddTransactionError.Unknown("Transaction not found")
                        )
                    }
                    return@launch
                }

                // Check historical archived references
                historicalSourceAccount = getAccountUseCase(tx.accountId)
                historicalDestAccount = tx.destinationAccountId?.let { getAccountUseCase(it) }
                historicalCategory = tx.categoryId?.let { getCategoryUseCase(it) }

                // Format amount into plain decimal string for input field
                val decimalAmount = MoneyParser.format(
                    money = tx.amount,
                    includeSymbol = false,
                    useGrouping = false,
                    showExplicitSign = false
                )

                _uiState.update {
                    it.copy(
                        transactionId = tx.id,
                        transactionType = tx.type,
                        amount = decimalAmount,
                        selectedAccountId = tx.accountId,
                        selectedDestinationAccountId = tx.destinationAccountId,
                        selectedCategoryId = tx.categoryId,
                        note = tx.note ?: "",
                        transactionTime = tx.transactionTime,
                        isLoading = false
                    )
                }

                observeAccounts()
                observeCategories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AddTransactionError.Unknown(e.localizedMessage ?: "Failed to load transaction")
                    )
                }
            }
        }
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            observeActiveAccountsUseCase().collect { activeAccounts ->
                _uiState.update { state ->
                    // Merge active accounts with historical archived account if it exists and was selected
                    val allAccounts = activeAccounts.toMutableList()
                    val histSource = historicalSourceAccount
                    if (histSource != null && histSource.isArchived && allAccounts.none { it.id == histSource.id }) {
                        allAccounts.add(histSource)
                    }
                    val histDest = historicalDestAccount
                    if (histDest != null && histDest.isArchived && allAccounts.none { it.id == histDest.id }) {
                        allAccounts.add(histDest)
                    }

                    val isCurrentSelectionValid = allAccounts.any { it.id == state.selectedAccountId }
                    val updatedAccountId = if (isCurrentSelectionValid) {
                        state.selectedAccountId
                    } else {
                        allAccounts.firstOrNull()?.id
                    }

                    val isDestValid = allAccounts.any { it.id == state.selectedDestinationAccountId }
                    val updatedDestId = if (isDestValid) state.selectedDestinationAccountId else null

                    state.copy(
                        accounts = allAccounts,
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
                .collect { activeCategories ->
                    _uiState.update { state ->
                        val allCategories = activeCategories.toMutableList()
                        val histCat = historicalCategory
                        if (histCat != null && histCat.isArchived && allCategories.none { it.id == histCat.id }) {
                            allCategories.add(histCat)
                        }

                        val isSelectedCategoryValid = allCategories.any { it.id == state.selectedCategoryId }
                        val updatedCategoryId = if (state.transactionType == TransactionType.TRANSFER) {
                            null
                        } else if (isSelectedCategoryValid) {
                            state.selectedCategoryId
                        } else {
                            allCategories.firstOrNull()?.id
                        }

                        state.copy(
                            categories = allCategories,
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
        if (sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
            _uiState.update { it.copy(amount = sanitized, error = null) }
        }
    }

    fun onAccountSelected(accountId: Long) {
        _uiState.update { state ->
            val newSelectedAccount = state.accounts.find { it.id == accountId }
            val newCurrency = newSelectedAccount?.initialBalance?.currencyCode

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

        // 4. Construct updated domain transaction
        val updatedTransaction = Transaction(
            id = transactionId,
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
                updateTransactionUseCase(updatedTransaction)
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
