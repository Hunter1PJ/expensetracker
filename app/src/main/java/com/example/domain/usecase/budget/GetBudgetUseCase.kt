package com.example.domain.usecase.budget

import com.example.domain.model.Budget
import com.example.domain.repository.BudgetRepository
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
