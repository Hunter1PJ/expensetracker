package com.example.domain.repository

import com.example.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing budgets.
 * Purely decoupled from persistence and framework dependencies.
 */
interface BudgetRepository {
    fun observeActiveBudgets(): Flow<List<Budget>>
    fun observeAllBudgets(): Flow<List<Budget>>
    fun observeBudgetsByCategory(categoryId: Long): Flow<List<Budget>>
    fun observeBudgetById(id: Long): Flow<Budget?>
    suspend fun getBudgetById(id: Long): Budget?
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deactivateBudget(id: Long)
}
