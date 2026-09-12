package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.repository.CategoryRepository

class ArchiveCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long) {
        if (categoryId <= 0L) {
            throw DomainException.InvalidCategory("Category ID must be greater than 0")
        }
        val existing = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException.CategoryNotFound(categoryId)

        if (existing.isSystem) {
            throw DomainException.SystemCategoryCannotBeArchived(categoryId)
        }

        categoryRepository.archiveCategory(categoryId)
    }
}
