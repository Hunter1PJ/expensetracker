package com.example.presentation.budgets.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.budget.BudgetValidationError
import com.example.domain.usecase.account.ObserveAllAccountsUseCase
import com.example.domain.usecase.budget.CreateBudgetUseCase
import com.example.domain.usecase.budget.GetBudgetUseCase
import com.example.domain.usecase.budget.UpdateBudgetUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class BudgetScope {
    OVERALL,
    CATEGORY
}

data class AddEditBudgetUiState(
    val budgetId: Long = 0L,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val scope: BudgetScope = BudgetScope.OVERALL,
    val selectedCategoryId: Long? = null,
    val availableCategories: List<Category> = emptyList(),
    val amountText: String = "",
    val selectedCurrency: String = "USD",
    val availableCurrencies: List<String> = listOf("USD", "EUR", "UZS", "GBP"),
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val startDate: LocalDate = LocalDate.now().withDayOfMonth(1),
    val endDate: LocalDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
    val validationError: String? = null,
    val isSubmitting: Boolean = false,
    val isSaved: Boolean = false
)

class AddEditBudgetViewModel(
    private val budgetId: Long = 0L,
    private val getBudgetUseCase: GetBudgetUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
    private val observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase,
    private val observeAllAccountsUseCase: ObserveAllAccountsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditBudgetUiState(budgetId = budgetId, isEditMode = budgetId > 0L))
    val uiState: StateFlow<AddEditBudgetUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load categories compatible with spending budget (EXPENSE or BOTH)
            val categories = observeActiveCategoriesUseCase().first()
                .filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH }

            // Load currencies from accounts
            val accounts = observeAllAccountsUseCase().first()
            val currencies = (accounts.map { it.initialBalance.currencyCode } + listOf("USD", "EUR", "UZS", "GBP"))
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val defaultCurrency = currencies.firstOrNull() ?: "USD"

            _uiState.update { state ->
                state.copy(
                    availableCategories = categories,
                    availableCurrencies = currencies,
                    selectedCurrency = defaultCurrency
                )
            }

            if (budgetId > 0L) {
                val budget = getBudgetUseCase.getById(budgetId)
                if (budget != null) {
                    val amountStr = MoneyParser.format(budget.limitAmount, includeSymbol = false, useGrouping = false)
                    _uiState.update { state ->
                        state.copy(
                            scope = if (budget.categoryId == null) BudgetScope.OVERALL else BudgetScope.CATEGORY,
                            selectedCategoryId = budget.categoryId,
                            amountText = amountStr,
                            selectedCurrency = budget.limitAmount.currencyCode,
                            periodType = budget.periodType,
                            startDate = budget.startDate,
                            endDate = budget.endDate,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, validationError = "Budget not found") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onScopeChanged(scope: BudgetScope) {
        _uiState.update { state ->
            val defaultCatId = if (scope == BudgetScope.CATEGORY) {
                state.selectedCategoryId ?: state.availableCategories.firstOrNull()?.id
            } else null
            state.copy(
                scope = scope,
                selectedCategoryId = defaultCatId,
                validationError = null
            )
        }
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, validationError = null) }
    }

    fun onAmountChanged(amount: String) {
        val sanitized = amount.replace(',', '.')
        if (sanitized.isEmpty() || sanitized.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
            _uiState.update { it.copy(amountText = sanitized, validationError = null) }
        }
    }

    fun onCurrencySelected(currency: String) {
        _uiState.update { it.copy(selectedCurrency = currency, validationError = null) }
    }

    fun onPeriodTypeChanged(periodType: BudgetPeriodType) {
        val today = LocalDate.now()
        val (start, end) = when (periodType) {
            BudgetPeriodType.MONTHLY -> {
                today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            }
            BudgetPeriodType.WEEKLY -> {
                val monday = today.with(DayOfWeek.MONDAY)
                monday to monday.plusDays(6)
            }
            BudgetPeriodType.YEARLY -> {
                today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
            }
            BudgetPeriodType.CUSTOM -> {
                _uiState.value.startDate to _uiState.value.endDate
            }
        }

        _uiState.update {
            it.copy(
                periodType = periodType,
                startDate = start,
                endDate = end,
                validationError = null
            )
        }
    }

    fun onStartDateChanged(date: LocalDate) {
        _uiState.update { it.copy(startDate = date, validationError = null) }
    }

    fun onEndDateChanged(date: LocalDate) {
        _uiState.update { it.copy(endDate = date, validationError = null) }
    }

    fun saveBudget() {
        val state = _uiState.value
        if (state.isSubmitting) return

        if (state.scope == BudgetScope.CATEGORY && state.selectedCategoryId == null) {
            _uiState.update { it.copy(validationError = "Please select a category") }
            return
        }

        val parseResult = MoneyParser.parse(state.amountText, state.selectedCurrency)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(validationError = "Please enter a valid amount") }
            return
        }

        val limitMoney = parseResult.getOrThrow()
        if (limitMoney.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(validationError = "Limit must be greater than zero") }
            return
        }

        if (state.startDate.isAfter(state.endDate)) {
            _uiState.update { it.copy(validationError = "Start date must be before or equal to end date") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, validationError = null) }
            val budget = Budget(
                id = state.budgetId,
                categoryId = if (state.scope == BudgetScope.OVERALL) null else state.selectedCategoryId,
                limitAmount = limitMoney,
                periodType = state.periodType,
                startDate = state.startDate,
                endDate = state.endDate,
                isActive = true
            )

            try {
                if (state.isEditMode) {
                    updateBudgetUseCase(budget)
                } else {
                    createBudgetUseCase(budget)
                }
                _uiState.update { it.copy(isSubmitting = false, isSaved = true) }
            } catch (e: BudgetValidationError) {
                val message = when (e) {
                    is BudgetValidationError.InvalidLimit -> "Limit must be greater than zero"
                    is BudgetValidationError.InvalidCurrency -> "Please select a valid currency"
                    is BudgetValidationError.InvalidDateRange -> "Start date must be before or equal to end date"
                    is BudgetValidationError.CategoryNotFound -> "Selected category not found"
                    is BudgetValidationError.IncompatibleCategoryType -> "Budget category must be Expense or Both"
                    is BudgetValidationError.CategoryArchived -> "Selected category is archived"
                    is BudgetValidationError.OverlappingBudgetExists -> "An active budget already exists for this scope, currency, and period"
                }
                _uiState.update { it.copy(isSubmitting = false, validationError = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, validationError = e.message ?: "Failed to save budget") }
            }
        }
    }
}
