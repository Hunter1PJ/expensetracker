package com.suguru.expensetracker.presentation.transactions.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.UpdateTransactionUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.transactions.add.AddTransactionError
import com.suguru.expensetracker.widget.common.WidgetRefreshCoordinator
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
 * ViewModel managing state and operations for editing an existing Expense or Income transaction.
 * Preserves the persisted account reference and keeps historical Transfer records read-only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTransactionViewModel(
    private val transactionId: Long,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase,
    private val getAccountUseCase: GetAccountUseCase,
    private val getCategoryUseCase: GetCategoryUseCase,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator? = null
) : ViewModel() {

    private companion object {
        const val TRANSFER_READ_ONLY_MESSAGE =
            "Historical transfer transactions are read-only and cannot be edited or converted."
        const val TRANSFER_NOT_SUPPORTED_MESSAGE =
            "Transfers are not supported."
    }

    private val _uiState = MutableStateFlow(EditTransactionUiState(transactionId = transactionId, isLoading = true))
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    private var persistedTransaction: Transaction? = null
    private var historicalCategory: Category? = null

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
                            isTransactionLoaded = false,
                            error = AddTransactionError.Unknown("Transaction not found")
                        )
                    }
                    return@launch
                }

                persistedTransaction = tx

                // Format amount into plain decimal string for input field
                val decimalAmount = MoneyParser.format(
                    money = tx.amount,
                    includeSymbol = false,
                    useGrouping = false,
                    showExplicitSign = false
                )

                if (tx.type == TransactionType.TRANSFER) {
                    val fromAccount = getAccountUseCase(tx.accountId)
                    val toAccount = tx.destinationAccountId?.let { getAccountUseCase(it) }
                    val formattedAmount = MoneyParser.format(
                        money = tx.amount,
                        includeSymbol = true,
                        useGrouping = true,
                        showExplicitSign = false
                    )

                    _uiState.update {
                        it.copy(
                            transactionId = tx.id,
                            transactionType = tx.type,
                            amount = decimalAmount,
                            currencyCode = tx.amount.currencyCode,
                            selectedCategoryId = null,
                            note = tx.note ?: "",
                            transactionTime = tx.transactionTime,
                            isLoading = false,
                            isTransactionLoaded = true,
                            isReadOnlyTransfer = true,
                            transferFromAccountName = fromAccount?.name,
                            transferToAccountName = toAccount?.name,
                            transferAmountFormatted = formattedAmount
                        )
                    }
                    return@launch
                }

                historicalCategory = tx.categoryId?.let { getCategoryUseCase(it) }

                _uiState.update {
                    it.copy(
                        transactionId = tx.id,
                        transactionType = tx.type,
                        amount = decimalAmount,
                        currencyCode = tx.amount.currencyCode,
                        selectedCategoryId = tx.categoryId,
                        note = tx.note ?: "",
                        transactionTime = tx.transactionTime,
                        isLoading = false,
                        isTransactionLoaded = true,
                        isReadOnlyTransfer = false
                    )
                }

                observeCategories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isTransactionLoaded = false,
                        error = AddTransactionError.Unknown(e.localizedMessage ?: "Failed to load transaction")
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
                        val updatedCategoryId = if (state.isReadOnlyTransfer || state.transactionType == TransactionType.TRANSFER) {
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
        val state = _uiState.value
        val persistedType = persistedTransaction?.type
        if (state.isReadOnlyTransfer || persistedType == TransactionType.TRANSFER) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown(TRANSFER_READ_ONLY_MESSAGE)) }
            return
        }
        if (type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown(TRANSFER_NOT_SUPPORTED_MESSAGE)) }
            return
        }
        // Persisted transaction type (Expense vs Income) is immutable on edit.
        _uiState.update { it.copy(error = null) }
    }

    fun onAmountChanged(amount: String) {
        if (_uiState.value.isReadOnlyTransfer) return
        val sanitized = amount.replace(',', '.')
        if (sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
            _uiState.update { it.copy(amount = sanitized, error = null) }
        }
    }

    fun onCategorySelected(categoryId: Long) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(selectedCategoryId = categoryId, error = null) }
    }

    fun onNoteChanged(note: String) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(note = note) }
    }

    fun onTransactionTimeChanged(time: Instant) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(transactionTime = time) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveTransaction(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return

        val existing = persistedTransaction
        if (existing == null || !state.isTransactionLoaded) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown("Transaction not found")) }
            return
        }

        if (state.isReadOnlyTransfer ||
            existing.type == TransactionType.TRANSFER ||
            state.transactionType == TransactionType.TRANSFER
        ) {
            _uiState.update { it.copy(error = AddTransactionError.Unknown(TRANSFER_READ_ONLY_MESSAGE)) }
            return
        }

        // 1. Validate Amount using the persisted transaction currency
        val parseResult = MoneyParser.parse(state.amount, existing.amount.currencyCode)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(error = AddTransactionError.InvalidAmount) }
            return
        }

        val money = parseResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(error = AddTransactionError.AmountMustBePositive) }
            return
        }

        // 2. Validate Category
        if (state.selectedCategoryId == null) {
            _uiState.update { it.copy(error = AddTransactionError.CategoryRequired) }
            return
        }

        // 3. Construct updated domain transaction preserving original persisted accountId & type
        val updatedTransaction = existing.copy(
            amount = money,
            categoryId = state.selectedCategoryId,
            note = state.note.trim().ifEmpty { null },
            transactionTime = state.transactionTime
        )

        // 4. Execute use case
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                updateTransactionUseCase(updatedTransaction)
                widgetRefreshCoordinator?.refreshAccountBalanceWidgets(existing.accountId)
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
