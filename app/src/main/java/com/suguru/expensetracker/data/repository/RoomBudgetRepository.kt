package com.suguru.expensetracker.data.repository

import com.suguru.expensetracker.data.local.dao.BudgetDao
import com.suguru.expensetracker.data.mapper.toDomain
import com.suguru.expensetracker.data.mapper.toEntity
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBudgetRepository(
    private val budgetDao: BudgetDao,
    private val onDataMutated: () -> Unit = {}
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
        val id = budgetDao.insertBudget(budget.toEntity())
        onDataMutated()
        return id
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget.toEntity())
        onDataMutated()
    }

    override suspend fun deactivateBudget(id: Long) {
        budgetDao.deactivateBudget(id)
        onDataMutated()
    }
}
