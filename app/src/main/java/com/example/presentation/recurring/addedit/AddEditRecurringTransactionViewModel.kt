package com.example.presentation.recurring.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.error.DomainException
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Money
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.RecurringTransaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.example.domain.usecase.recurring.CreateRecurringTransactionUseCase
import com.example.domain.usecase.recurring.GetRecurringTransactionUseCase
import com.example.domain.usecase.recurring.UpdateRecurringTransactionUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class AddEditRecurringUiState(
    val ruleId: Long = 0L,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val selectedAccountId: Long? = null,
    val selectedDestinationAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val hasEndDate: Boolean = false,
    val endDate: LocalDate? = null,
    val noteInput: String = "",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<Category> = emptyList()
)

class AddEditRecurringTransactionViewModel(
    private val ruleId: Long = 0L,
    private val createRecurringTransactionUseCase: CreateRecurringTransactionUseCase,
    private val updateRecurringTransactionUseCase: UpdateRecurringTransactionUseCase,
    private val getRecurringTransactionUseCase: GetRecurringTransactionUseCase,
    observeActiveAccountsUseCase: ObserveActiveAccountsUseCase,
    observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditRecurringUiState(ruleId = ruleId, isEditMode = ruleId > 0L))
    val uiState: StateFlow<AddEditRecurringUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeActiveAccountsUseCase(),
                observeActiveCategoriesUseCase()
            ) { accounts, categories ->
                Pair(accounts, categories)
            }.collect { (accounts, categories) ->
                _uiState.update { current ->
                    val defaultAccount = current.selectedAccountId ?: accounts.firstOrNull()?.id
                    val filteredCategories = filterCategories(categories, current.type)
                    val defaultCategory = current.selectedCategoryId ?: filteredCategories.firstOrNull()?.id

                    current.copy(
                        availableAccounts = accounts,
                        availableCategories = filteredCategories,
                        selectedAccountId = defaultAccount,
                        selectedCategoryId = if (current.type == TransactionType.TRANSFER) null else defaultCategory
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
            val existing = getRecurringTransactionUseCase(id)
            if (existing != null) {
                val formattedAmount = MoneyParser.format(existing.amount, includeSymbol = false)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        type = existing.type,
                        amountInput = formattedAmount,
                        selectedAccountId = existing.accountId,
                        selectedDestinationAccountId = existing.destinationAccountId,
                        selectedCategoryId = existing.categoryId,
                        frequency = existing.frequency,
                        startDate = existing.startDate,
                        hasEndDate = existing.endDate != null,
                        endDate = existing.endDate,
                        noteInput = existing.note ?: "",
                        isActive = existing.isActive
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Recurring rule not found") }
            }
        }
    }

    fun onTypeSelected(type: TransactionType) {
        _uiState.update { current ->
            val allCats = current.availableCategories
            val filteredCats = filterCategories(allCats, type)
            current.copy(
                type = type,
                availableCategories = filteredCats,
                selectedCategoryId = if (type == TransactionType.TRANSFER) null else filteredCats.firstOrNull()?.id,
                selectedDestinationAccountId = if (type == TransactionType.TRANSFER) {
                    current.availableAccounts.firstOrNull { it.id != current.selectedAccountId }?.id
                } else null
            )
        }
    }

    fun onAmountChanged(amount: String) {
        _uiState.update { it.copy(amountInput = amount, errorMessage = null) }
    }

    fun onAccountSelected(accountId: Long) {
        _uiState.update { current ->
            val dest = if (current.type == TransactionType.TRANSFER && current.selectedDestinationAccountId == accountId) {
                current.availableAccounts.firstOrNull { it.id != accountId }?.id
            } else {
                current.selectedDestinationAccountId
            }
            current.copy(selectedAccountId = accountId, selectedDestinationAccountId = dest, errorMessage = null)
        }
    }

    fun onDestinationAccountSelected(accountId: Long) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, errorMessage = null) }
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, errorMessage = null) }
    }

    fun onFrequencySelected(frequency: RecurrenceFrequency) {
        _uiState.update { it.copy(frequency = frequency, errorMessage = null) }
    }

    fun onStartDateSelected(date: LocalDate) {
        _uiState.update { it.copy(startDate = date, errorMessage = null) }
    }

    fun onHasEndDateToggled(hasEndDate: Boolean) {
        _uiState.update { current ->
            current.copy(
                hasEndDate = hasEndDate,
                endDate = if (hasEndDate) current.startDate.plusMonths(6) else null,
                errorMessage = null
            )
        }
    }

    fun onEndDateSelected(date: LocalDate) {
        _uiState.update { it.copy(endDate = date, errorMessage = null) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(noteInput = note, errorMessage = null) }
    }

    fun saveRule() {
        val state = _uiState.value
        if (state.isSaving) return

        val accountId = state.selectedAccountId
        if (accountId == null) {
            _uiState.update { it.copy(errorMessage = "Please select an account") }
            return
        }

        val sourceAccount = state.availableAccounts.find { it.id == accountId }
        if (sourceAccount == null) {
            _uiState.update { it.copy(errorMessage = "Selected account invalid") }
            return
        }

        val parseResult = MoneyParser.parse(state.amountInput, sourceAccount.initialBalance.currencyCode)
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

        val ruleToSave = RecurringTransaction(
            id = state.ruleId,
            type = state.type,
            amount = money,
            accountId = accountId,
            destinationAccountId = if (state.type == TransactionType.TRANSFER) state.selectedDestinationAccountId else null,
            categoryId = if (state.type != TransactionType.TRANSFER) state.selectedCategoryId else null,
            frequency = state.frequency,
            startDate = state.startDate,
            endDate = finalEndDate,
            nextOccurrence = state.startDate,
            note = state.noteInput.ifBlank { null },
            isActive = state.isActive
        )

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

    private fun filterCategories(categories: List<Category>, type: TransactionType): List<Category> {
        return when (type) {
            TransactionType.EXPENSE -> categories.filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH }
            TransactionType.INCOME -> categories.filter { it.type == CategoryType.INCOME || it.type == CategoryType.BOTH }
            TransactionType.TRANSFER -> emptyList()
        }
    }
}
