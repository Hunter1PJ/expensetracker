package com.suguru.expensetracker.presentation.recurring.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.GetRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddEditRecurringUiState(
    val ruleId: Long = 0L,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isRuleLoaded: Boolean = false,
    val isReadOnlyTransfer: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val currencyCode: String = "USD",
    val hasDefaultAccount: Boolean = false,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val hasEndDate: Boolean = false,
    val endDate: LocalDate? = null,
    val nextOccurrence: LocalDate? = null,
    val noteInput: String = "",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val availableCategories: List<Category> = emptyList(),
    val readOnlyAmountFormatted: String = ""
) {
    val currencySymbol: String
        get() = MoneyParser.getCurrencySymbol(currencyCode)
}

class AddEditRecurringTransactionViewModel(
    private val ruleId: Long = 0L,
    private val createRecurringTransactionUseCase: CreateRecurringTransactionUseCase,
    private val updateRecurringTransactionUseCase: UpdateRecurringTransactionUseCase,
    private val getRecurringTransactionUseCase: GetRecurringTransactionUseCase,
    observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase,
    private val getCategoryUseCase: GetCategoryUseCase? = null
) : ViewModel() {

    private companion object {
        const val INTERNAL_DEFAULT_ACCOUNT_KEY = "default"
        const val TRANSFER_NOT_SUPPORTED_MESSAGE =
            "Recurring transfers are not supported."
        const val TRANSFER_READ_ONLY_MESSAGE =
            "Historical recurring transfer rules are read-only and cannot be edited, converted, or reactivated."
    }

    private val _uiState = MutableStateFlow(
        AddEditRecurringUiState(
            ruleId = ruleId,
            isEditMode = ruleId > 0L,
            isLoading = ruleId > 0L,
            isRuleLoaded = ruleId <= 0L
        )
    )
    val uiState: StateFlow<AddEditRecurringUiState> = _uiState.asStateFlow()

    private var defaultAccount: Account? = null
    private var allActiveCategories: List<Category> = emptyList()
    private var persistedRule: RecurringTransaction? = null
    private var historicalCategory: Category? = null

    init {
        viewModelScope.launch {
            combine(
                observeActiveAccountsUseCase(),
                observeActiveCategoriesUseCase()
            ) { accounts, categories ->
                accounts to categories
            }.collect { (accounts, categories) ->
                defaultAccount = accounts.firstOrNull { it.internalKey == INTERNAL_DEFAULT_ACCOUNT_KEY }
                allActiveCategories = categories

                _uiState.update { current ->
                    val effectiveType = persistedRule?.type ?: current.type
                    val mergedCategories = mergeCategories(
                        activeCategories = categories,
                        type = effectiveType,
                        historicalCategory = historicalCategory
                    )
                    val selectedCategory = when {
                        current.isReadOnlyTransfer || effectiveType == TransactionType.TRANSFER -> null
                        current.selectedCategoryId != null && mergedCategories.any { it.id == current.selectedCategoryId } -> current.selectedCategoryId
                        else -> mergedCategories.firstOrNull()?.id
                    }

                    val resolvedCurrency = if (current.isEditMode) {
                        persistedRule?.amount?.currencyCode ?: current.currencyCode
                    } else {
                        defaultAccount?.initialBalance?.currencyCode ?: current.currencyCode
                    }

                    current.copy(
                        currencyCode = resolvedCurrency,
                        hasDefaultAccount = if (current.isEditMode) true else (defaultAccount != null),
                        availableCategories = mergedCategories,
                        selectedCategoryId = selectedCategory
                    )
                }
            }
        }

        if (ruleId > 0L) {
            loadExistingRule(ruleId)
        }
    }

    private fun loadExistingRule(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val existing = getRecurringTransactionUseCase(id)
                if (existing == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRuleLoaded = false,
                            errorMessage = "Recurring rule not found"
                        )
                    }
                    return@launch
                }

                persistedRule = existing

                if (existing.type == TransactionType.TRANSFER) {
                    val formattedDisplayAmount = MoneyParser.format(
                        money = existing.amount,
                        includeSymbol = true,
                        useGrouping = true,
                        showExplicitSign = false
                    )
                    val plainAmount = MoneyParser.format(
                        money = existing.amount,
                        includeSymbol = false,
                        useGrouping = false
                    )
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRuleLoaded = true,
                            isReadOnlyTransfer = true,
                            type = existing.type,
                            amountInput = plainAmount,
                            readOnlyAmountFormatted = formattedDisplayAmount,
                            currencyCode = existing.amount.currencyCode,
                            hasDefaultAccount = true,
                            selectedCategoryId = null,
                            frequency = existing.frequency,
                            startDate = existing.startDate,
                            hasEndDate = existing.endDate != null,
                            endDate = existing.endDate,
                            nextOccurrence = existing.nextOccurrence,
                            noteInput = existing.note ?: "",
                            isActive = existing.isActive,
                            availableCategories = emptyList(),
                            errorMessage = null
                        )
                    }
                    return@launch
                }

                historicalCategory = existing.categoryId?.let { getCategoryUseCase?.invoke(it) }
                val plainAmount = MoneyParser.format(
                    money = existing.amount,
                    includeSymbol = false,
                    useGrouping = false
                )

                _uiState.update { current ->
                    val mergedCategories = mergeCategories(
                        activeCategories = allActiveCategories,
                        type = existing.type,
                        historicalCategory = historicalCategory
                    )
                    current.copy(
                        isLoading = false,
                        isRuleLoaded = true,
                        isReadOnlyTransfer = false,
                        type = existing.type,
                        amountInput = plainAmount,
                        currencyCode = existing.amount.currencyCode,
                        hasDefaultAccount = true,
                        selectedCategoryId = existing.categoryId,
                        frequency = existing.frequency,
                        startDate = existing.startDate,
                        hasEndDate = existing.endDate != null,
                        endDate = existing.endDate,
                        nextOccurrence = existing.nextOccurrence,
                        noteInput = existing.note ?: "",
                        isActive = existing.isActive,
                        availableCategories = mergedCategories,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRuleLoaded = false,
                        errorMessage = e.localizedMessage ?: "Failed to load recurring rule"
                    )
                }
            }
        }
    }

    fun onTypeSelected(type: TransactionType) {
        val currentState = _uiState.value
        if (currentState.isReadOnlyTransfer || persistedRule?.type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(errorMessage = TRANSFER_READ_ONLY_MESSAGE) }
            return
        }
        if (type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(errorMessage = TRANSFER_NOT_SUPPORTED_MESSAGE) }
            return
        }
        if (currentState.isEditMode) {
            // Persisted rule type (Expense vs Income) is immutable on edit.
            _uiState.update { it.copy(errorMessage = null) }
            return
        }

        _uiState.update { current ->
            val filteredCats = filterCategories(allActiveCategories, type)
            val keptCategory = current.selectedCategoryId?.takeIf { id -> filteredCats.any { it.id == id } }
            current.copy(
                type = type,
                availableCategories = filteredCats,
                selectedCategoryId = keptCategory ?: filteredCats.firstOrNull()?.id,
                errorMessage = null
            )
        }
    }

    fun onAmountChanged(amount: String) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(amountInput = amount, errorMessage = null) }
    }

    fun onCategorySelected(categoryId: Long) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(selectedCategoryId = categoryId, errorMessage = null) }
    }

    fun onFrequencySelected(frequency: RecurrenceFrequency) {
        val current = _uiState.value
        if (current.isReadOnlyTransfer || current.isEditMode) return
        _uiState.update { it.copy(frequency = frequency, errorMessage = null) }
    }

    fun onStartDateSelected(date: LocalDate) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(startDate = date, errorMessage = null) }
    }

    fun onHasEndDateToggled(hasEndDate: Boolean) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { current ->
            current.copy(
                hasEndDate = hasEndDate,
                endDate = if (hasEndDate) (current.endDate ?: current.startDate.plusMonths(6)) else null,
                errorMessage = null
            )
        }
    }

    fun onEndDateSelected(date: LocalDate) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(endDate = date, errorMessage = null) }
    }

    fun onNoteChanged(note: String) {
        if (_uiState.value.isReadOnlyTransfer) return
        _uiState.update { it.copy(noteInput = note, errorMessage = null) }
    }

    fun saveRule() {
        val state = _uiState.value
        if (state.isSaving) return

        if (state.isReadOnlyTransfer || persistedRule?.type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(errorMessage = TRANSFER_READ_ONLY_MESSAGE) }
            return
        }
        if (state.type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(errorMessage = TRANSFER_NOT_SUPPORTED_MESSAGE) }
            return
        }

        val existing = persistedRule
        if (state.isEditMode && (existing == null || !state.isRuleLoaded)) {
            _uiState.update { it.copy(errorMessage = "Recurring rule not found") }
            return
        }

        val resolvedAccountId: Long
        val resolvedCurrencyCode: String

        if (state.isEditMode && existing != null) {
            resolvedAccountId = existing.accountId
            resolvedCurrencyCode = existing.amount.currencyCode
        } else {
            val activeDefault = defaultAccount
            if (activeDefault == null) {
                _uiState.update { it.copy(errorMessage = "Default setup is still initializing. Please try again.") }
                return
            }
            resolvedAccountId = activeDefault.id
            resolvedCurrencyCode = activeDefault.initialBalance.currencyCode
        }

        val parseResult = MoneyParser.parse(state.amountInput, resolvedCurrencyCode)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(errorMessage = parseResult.exceptionOrNull()?.message ?: "Invalid monetary amount") }
            return
        }
        val money = parseResult.getOrThrow()
        if (money.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(errorMessage = "Amount must be greater than zero") }
            return
        }

        val finalEndDate = if (state.hasEndDate) state.endDate else null
        if (finalEndDate != null && finalEndDate < state.startDate) {
            _uiState.update { it.copy(errorMessage = "End date cannot be before start date") }
            return
        }

        val ruleToSave = if (state.isEditMode && existing != null) {
            existing.copy(
                amount = money,
                categoryId = state.selectedCategoryId,
                startDate = state.startDate,
                endDate = finalEndDate,
                note = state.noteInput.ifBlank { null }
                // Preserves existing.accountId, existing.type, existing.frequency, existing.nextOccurrence, existing.isActive
            )
        } else {
            RecurringTransaction(
                id = 0L,
                type = state.type,
                amount = money,
                accountId = resolvedAccountId,
                destinationAccountId = null,
                categoryId = state.selectedCategoryId,
                frequency = state.frequency,
                startDate = state.startDate,
                endDate = finalEndDate,
                nextOccurrence = state.startDate,
                note = state.noteInput.ifBlank { null },
                isActive = true
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                if (state.isEditMode) {
                    updateRecurringTransactionUseCase(ruleToSave)
                } else {
                    createRecurringTransactionUseCase(ruleToSave)
                }
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Failed to save rule") }
            }
        }
    }

    private fun mergeCategories(
        activeCategories: List<Category>,
        type: TransactionType,
        historicalCategory: Category?
    ): List<Category> {
        val filtered = filterCategories(activeCategories, type).toMutableList()
        if (historicalCategory != null &&
            historicalCategory.isArchived &&
            type != TransactionType.TRANSFER &&
            filtered.none { it.id == historicalCategory.id }
        ) {
            filtered.add(historicalCategory)
        }
        return filtered
    }

    private fun filterCategories(categories: List<Category>, type: TransactionType): List<Category> {
        return when (type) {
            TransactionType.EXPENSE -> categories.filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH }
            TransactionType.INCOME -> categories.filter { it.type == CategoryType.INCOME || it.type == CategoryType.BOTH }
            TransactionType.TRANSFER -> emptyList()
        }
    }
}
