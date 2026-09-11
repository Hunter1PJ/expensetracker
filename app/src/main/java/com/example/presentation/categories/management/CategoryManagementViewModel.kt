package com.example.presentation.categories.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.error.DomainException
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.usecase.category.ArchiveCategoryUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Category Management screen.
 * Observes active categories, applies category type filters, and handles safe archiving.
 */
class CategoryManagementViewModel(
    private val observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase,
    private val archiveCategoryUseCase: ArchiveCategoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    init {
        observeCategories()
    }

    private fun observeCategories() {
        viewModelScope.launch {
            observeActiveCategoriesUseCase()
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = CategoryManagementError.ArchiveFailed(exception.message)
                        )
                    }
                }
                .collect { categories ->
                    _uiState.update { state ->
                        val filtered = filterCategories(categories, state.selectedTab)
                        state.copy(
                            allCategories = categories,
                            filteredCategories = filtered,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onTabSelected(tab: CategoryFilterTab) {
        _uiState.update { state ->
            val filtered = filterCategories(state.allCategories, tab)
            state.copy(
                selectedTab = tab,
                filteredCategories = filtered
            )
        }
    }

    fun onArchiveCategoryClicked(category: Category) {
        if (category.isSystem) {
            _uiState.update {
                it.copy(error = CategoryManagementError.SystemCategoryCannotBeArchived)
            }
            return
        }
        _uiState.update { it.copy(categoryToArchive = category) }
    }

    fun onDismissArchiveDialog() {
        _uiState.update { it.copy(categoryToArchive = null) }
    }

    fun onConfirmArchive() {
        val category = _uiState.value.categoryToArchive ?: return
        if (_uiState.value.isArchiving) return

        if (category.isSystem) {
            _uiState.update {
                it.copy(
                    categoryToArchive = null,
                    error = CategoryManagementError.SystemCategoryCannotBeArchived
                )
            }
            return
        }

        _uiState.update { it.copy(isArchiving = true) }

        viewModelScope.launch {
            try {
                archiveCategoryUseCase(category.id)
                _uiState.update {
                    it.copy(
                        categoryToArchive = null,
                        isArchiving = false
                    )
                }
            } catch (e: DomainException.SystemCategoryCannotBeArchived) {
                _uiState.update {
                    it.copy(
                        categoryToArchive = null,
                        isArchiving = false,
                        error = CategoryManagementError.SystemCategoryCannotBeArchived
                    )
                }
            } catch (e: DomainException.CategoryNotFound) {
                _uiState.update {
                    it.copy(
                        categoryToArchive = null,
                        isArchiving = false,
                        error = CategoryManagementError.CategoryNotFound
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        categoryToArchive = null,
                        isArchiving = false,
                        error = CategoryManagementError.ArchiveFailed(e.message)
                    )
                }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun filterCategories(
        categories: List<Category>,
        tab: CategoryFilterTab
    ): List<Category> {
        return when (tab) {
            CategoryFilterTab.ALL -> categories
            CategoryFilterTab.EXPENSE -> categories.filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH }
            CategoryFilterTab.INCOME -> categories.filter { it.type == CategoryType.INCOME || it.type == CategoryType.BOTH }
            CategoryFilterTab.BOTH -> categories.filter { it.type == CategoryType.BOTH }
        }
    }
}
