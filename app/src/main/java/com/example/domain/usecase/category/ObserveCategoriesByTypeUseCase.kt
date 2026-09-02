package com.example.domain.usecase.category

import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesByTypeUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(type: CategoryType): Flow<List<Category>> = categoryRepository.observeActiveCategoriesByType(type)
}
