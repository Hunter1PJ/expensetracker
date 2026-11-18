package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import com.suguru.expensetracker.domain.util.CategorySorter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ObserveCategoriesByTypeUseCase(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository? = null
) {
    operator fun invoke(type: CategoryType): Flow<List<Category>> {
        val categoriesFlow = categoryRepository.observeActiveCategoriesByType(type)
        if (type != CategoryType.EXPENSE) {
            return categoriesFlow
        }
        val txRepo = transactionRepository ?: return categoriesFlow.map { categories ->
            CategorySorter.sortExpenseCategories(categories, emptyMap())
        }
        return combine(
            categoriesFlow,
            txRepo.observeExpenseCategoryUsageCounts()
        ) { categories, usageCounts ->
            CategorySorter.sortExpenseCategories(categories, usageCounts)
        }
    }
}
