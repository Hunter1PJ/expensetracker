package com.example.domain.usecase.category

import com.example.domain.model.Category
import com.example.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveCategoriesUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = categoryRepository.observeActiveCategories()
}
