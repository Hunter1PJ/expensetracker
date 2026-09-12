package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.repository.CategoryRepository

class GetCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long): Category? = categoryRepository.getCategoryById(categoryId)
}
