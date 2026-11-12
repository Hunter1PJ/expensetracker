package com.suguru.expensetracker.domain.usecase.budget

import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.budget.BudgetValidationError
import com.suguru.expensetracker.domain.repository.BudgetRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first

class UpdateBudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(budget: Budget) {
        if (budget.id <= 0L) {
            throw IllegalArgumentException("Budget to update must have id > 0L")
        }
        if (budget.limitAmount.amountInMinorUnits <= 0L) {
            throw BudgetValidationError.InvalidLimit
        }
        if (budget.limitAmount.currencyCode.isBlank()) {
            throw BudgetValidationError.InvalidCurrency
        }
        if (budget.startDate.isAfter(budget.endDate)) {
            throw BudgetValidationError.InvalidDateRange
        }

        if (budget.categoryId != null) {
            val category = categoryRepository.getCategoryById(budget.categoryId)
                ?: throw BudgetValidationError.CategoryNotFound
            if (category.type == CategoryType.INCOME) {
                throw BudgetValidationError.IncompatibleCategoryType
            }
        }

        // Check overlapping active budgets excluding self
        val activeBudgets = budgetRepository.observeActiveBudgets().first()
        val currency = budget.limitAmount.currencyCode
        val hasOverlap = activeBudgets.any { existing ->
            existing.id != budget.id &&
                    existing.categoryId == budget.categoryId &&
                    existing.limitAmount.currencyCode == currency &&
                    datesOverlap(budget.startDate, budget.endDate, existing.startDate, existing.endDate)
        }

        if (hasOverlap) {
            throw BudgetValidationError.OverlappingBudgetExists
        }

        budgetRepository.updateBudget(budget)
    }

    private fun datesOverlap(start1: java.time.LocalDate, end1: java.time.LocalDate, start2: java.time.LocalDate, end2: java.time.LocalDate): Boolean {
        return !(end1.isBefore(start2) || start1.isAfter(end2))
    }
}
