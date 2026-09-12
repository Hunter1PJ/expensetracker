package com.suguru.expensetracker.domain.usecase.budget

import com.suguru.expensetracker.domain.repository.BudgetRepository

class DeactivateBudgetUseCase(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(id: Long) {
        budgetRepository.deactivateBudget(id)
    }
}
