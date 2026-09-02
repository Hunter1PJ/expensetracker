package com.example.domain.usecase.category

import com.example.domain.model.Category
import com.example.domain.repository.CategoryRepository

class GetCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long): Category? = categoryRepository.getCategoryById(categoryId)
}
