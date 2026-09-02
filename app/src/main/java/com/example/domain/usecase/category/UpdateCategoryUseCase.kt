package com.example.domain.usecase.category

import com.example.domain.error.DomainException
import com.example.domain.model.Category
import com.example.domain.repository.CategoryRepository

class UpdateCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(category: Category) {
        if (category.id <= 0L) {
            throw DomainException.InvalidCategory("Category ID must be greater than 0 for update")
        }
        if (category.name.isBlank()) {
            throw DomainException.InvalidCategory("Category name cannot be blank")
        }
        categoryRepository.getCategoryById(category.id)
            ?: throw DomainException.CategoryNotFound(category.id)

        categoryRepository.updateCategory(category)
    }
}
