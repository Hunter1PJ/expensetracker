package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.repository.CategoryRepository

class CreateCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(category: Category): Long {
        if (category.name.isBlank()) {
            throw DomainException.InvalidCategory("Category name cannot be blank")
        }
        if (category.id != 0L) {
            throw DomainException.InvalidCategory("New category must have an ID of 0L")
        }
        return categoryRepository.insertCategory(category)
    }
}
