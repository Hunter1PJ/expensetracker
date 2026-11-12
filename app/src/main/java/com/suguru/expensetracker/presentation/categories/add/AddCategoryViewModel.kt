package com.suguru.expensetracker.presentation.categories.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.usecase.category.CreateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.UpdateCategoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Creating and Editing Categories.
 * Supports custom categories and enforces validation invariants.
 */
class AddCategoryViewModel(
    private val categoryId: Long = 0L,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val getCategoryUseCase: GetCategoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddCategoryUiState(
            categoryId = categoryId,
            isEditMode = categoryId > 0L
        )
    )
    val uiState: StateFlow<AddCategoryUiState> = _uiState.asStateFlow()

    private var existingCategory: Category? = null

    init {
        if (categoryId > 0L) {
            loadExistingCategory(categoryId)
        }
    }

    private fun loadExistingCategory(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val category = getCategoryUseCase(id)
                if (category != null) {
                    existingCategory = category
                    _uiState.update {
                        it.copy(
                            categoryId = category.id,
                            isEditMode = true,
                            name = category.name,
                            type = category.type,
                            selectedIconName = category.iconName,
                            selectedColorHex = category.colorHex,
                            isSystemCategory = category.isSystem,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = AddCategoryError.CategoryNotFound
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AddCategoryError.SaveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onTypeChanged(type: CategoryType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onIconChanged(iconName: String) {
        _uiState.update { it.copy(selectedIconName = iconName) }
    }

    fun onColorChanged(colorHex: String) {
        _uiState.update { it.copy(selectedColorHex = colorHex) }
    }

    fun saveCategory() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return

        val trimmedName = state.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = AddCategoryError.NameRequired) }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                if (state.isEditMode) {
                    val current = existingCategory ?: getCategoryUseCase(state.categoryId)
                    if (current == null) {
                        _uiState.update {
                            it.copy(isSaving = false, error = AddCategoryError.CategoryNotFound)
                        }
                        return@launch
                    }

                    val updated = current.copy(
                        name = trimmedName,
                        type = state.type,
                        iconName = state.selectedIconName,
                        colorHex = state.selectedColorHex
                    )
                    updateCategoryUseCase(updated)
                } else {
                    val newCategory = Category(
                        id = 0L,
                        name = trimmedName,
                        type = state.type,
                        colorHex = state.selectedColorHex,
                        iconName = state.selectedIconName,
                        isSystem = false,
                        isArchived = false
                    )
                    createCategoryUseCase(newCategory)
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSavedSuccessfully = true
                    )
                }
            } catch (e: DomainException.InvalidCategory) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AddCategoryError.NameRequired
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AddCategoryError.SaveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
