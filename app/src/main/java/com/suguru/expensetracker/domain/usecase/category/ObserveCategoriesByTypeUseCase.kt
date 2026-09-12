package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesByTypeUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(type: CategoryType): Flow<List<Category>> = categoryRepository.observeActiveCategoriesByType(type)
}
