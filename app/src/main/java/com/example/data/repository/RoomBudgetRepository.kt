package com.example.data.repository

import com.example.data.local.dao.BudgetDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Budget
import com.example.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBudgetRepository(
    private val budgetDao: BudgetDao
) : BudgetRepository {

    override fun observeActiveBudgets(): Flow<List<Budget>> {
        return budgetDao.observeActiveBudgets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllBudgets(): Flow<List<Budget>> {
        return budgetDao.observeAllBudgets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeBudgetsByCategory(categoryId: Long): Flow<List<Budget>> {
        return budgetDao.observeBudgetsByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeBudgetById(id: Long): Flow<Budget?> {
        return budgetDao.observeBudgetById(id).map { it?.toDomain() }
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        return budgetDao.getBudgetById(id)?.toDomain()
    }

    override suspend fun insertBudget(budget: Budget): Long {
        return budgetDao.insertBudget(budget.toEntity())
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget.toEntity())
    }

    override suspend fun deactivateBudget(id: Long) {
        budgetDao.deactivateBudget(id)
    }
}
