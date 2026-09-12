package com.suguru.expensetracker.presentation.categories.management

import com.suguru.expensetracker.domain.model.Category

enum class CategoryFilterTab {
    ALL,
    EXPENSE,
    INCOME,
    BOTH
}

sealed interface CategoryManagementError {
    data object CategoryNotFound : CategoryManagementError
    data object SystemCategoryCannotBeArchived : CategoryManagementError
    data class ArchiveFailed(val message: String?) : CategoryManagementError
}

data class CategoryManagementUiState(
    val allCategories: List<Category> = emptyList(),
    val filteredCategories: List<Category> = emptyList(),
    val selectedTab: CategoryFilterTab = CategoryFilterTab.ALL,
    val isLoading: Boolean = true,
    val categoryToArchive: Category? = null,
    val isArchiving: Boolean = false,
    val error: CategoryManagementError? = null
)
