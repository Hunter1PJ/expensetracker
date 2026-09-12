package com.suguru.expensetracker.domain.usecase.budget

import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow

class GetBudgetUseCase(
    private val budgetRepository: BudgetRepository
) {
    suspend fun getById(id: Long): Budget? {
        return budgetRepository.getBudgetById(id)
    }

    fun observeById(id: Long): Flow<Budget?> {
        return budgetRepository.observeBudgetById(id)
    }
}
