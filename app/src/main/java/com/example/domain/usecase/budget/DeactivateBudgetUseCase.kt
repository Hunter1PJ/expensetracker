package com.example.domain.usecase.budget

import com.example.domain.repository.BudgetRepository

class DeactivateBudgetUseCase(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(id: Long) {
        budgetRepository.deactivateBudget(id)
    }
}
