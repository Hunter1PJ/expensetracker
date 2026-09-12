package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveCategoriesUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = categoryRepository.observeActiveCategories()
}
