package com.example.presentation.categories.add

import com.example.domain.model.CategoryType

sealed interface AddCategoryError {
    data object NameRequired : AddCategoryError
    data object CategoryNotFound : AddCategoryError
    data class SaveFailed(val message: String?) : AddCategoryError
}

data class AddCategoryUiState(
    val categoryId: Long = 0L,
    val isEditMode: Boolean = false,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val selectedIconName: String = "fastfood",
    val selectedColorHex: String = "#EF4444",
    val isSystemCategory: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSavedSuccessfully: Boolean = false,
    val error: AddCategoryError? = null
)
