package com.suguru.expensetracker.presentation.budgets.addedit

import androidx.lifecycle.ViewModel
import com.suguru.expensetracker.R
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.budget.BudgetValidationError
import com.suguru.expensetracker.domain.usecase.account.ObserveAllAccountsUseCase
import com.suguru.expensetracker.domain.usecase.budget.CreateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.GetBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.UpdateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.presentation.util.UiMessage
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
    val validationError: UiMessage? = null,
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
                    _uiState.update { it.copy(isLoading = false, validationError = UiMessage(R.string.error_budget_not_found)) }
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
            _uiState.update { it.copy(validationError = UiMessage(R.string.error_budget_category_required)) }
            return
        }

        val parseResult = MoneyParser.parse(state.amountText, state.selectedCurrency)
        if (parseResult.isFailure) {
            _uiState.update { it.copy(validationError = UiMessage(R.string.error_invalid_amount)) }
            return
        }

        val limitMoney = parseResult.getOrThrow()
        if (limitMoney.amountInMinorUnits <= 0L) {
            _uiState.update { it.copy(validationError = UiMessage(R.string.error_budget_limit_required)) }
            return
        }

        if (state.startDate.isAfter(state.endDate)) {
            _uiState.update { it.copy(validationError = UiMessage(R.string.error_budget_date_range_invalid)) }
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
                    is BudgetValidationError.InvalidLimit -> UiMessage(R.string.error_budget_limit_required)
                    is BudgetValidationError.InvalidCurrency -> UiMessage(R.string.error_budget_currency_required)
                    is BudgetValidationError.InvalidDateRange -> UiMessage(R.string.error_budget_date_range_invalid)
                    is BudgetValidationError.CategoryNotFound -> UiMessage(R.string.error_category_not_found)
                    is BudgetValidationError.IncompatibleCategoryType -> UiMessage(R.string.error_budget_category_type_incompatible)
                    is BudgetValidationError.CategoryArchived -> UiMessage(R.string.error_category_archived)
                    is BudgetValidationError.OverlappingBudgetExists -> UiMessage(R.string.error_budget_overlapping)
                }
                _uiState.update { it.copy(isSubmitting = false, validationError = message) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSubmitting = false, validationError = UiMessage(R.string.error_save_budget_failed)) }
            }
        }
    }
}
