package com.example.presentation.categories.management

import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.usecase.category.ArchiveCategoryUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase
    private lateinit var archiveCategoryUseCase: ArchiveCategoryUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        categoryRepository = FakeCategoryRepository()
        observeActiveCategoriesUseCase = ObserveActiveCategoriesUseCase(categoryRepository)
        archiveCategoryUseCase = ArchiveCategoryUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CategoryManagementViewModel {
        return CategoryManagementViewModel(
            observeActiveCategoriesUseCase = observeActiveCategoriesUseCase,
            archiveCategoryUseCase = archiveCategoryUseCase
        )
    }

    @Test
    fun `initial state loads active categories and filters by tab`() = runTest(testDispatcher) {
        val expCat = Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981", isSystem = true)
        val incCat = Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981", isSystem = true)
        val bothCat = Category(name = "Adjustments", type = CategoryType.BOTH, iconName = "swap_horiz", colorHex = "#6366F1", isSystem = false)

        categoryRepository.insertCategory(expCat)
        categoryRepository.insertCategory(incCat)
        categoryRepository.insertCategory(bothCat)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.allCategories.size)
        assertEquals(3, state.filteredCategories.size) // ALL tab

        // Switch to Expense tab
        viewModel.onTabSelected(CategoryFilterTab.EXPENSE)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredCategories.size) // Groceries + Adjustments

        // Switch to Income tab
        viewModel.onTabSelected(CategoryFilterTab.INCOME)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredCategories.size) // Salary + Adjustments

        // Switch to Both tab
        viewModel.onTabSelected(CategoryFilterTab.BOTH)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredCategories.size) // Adjustments
    }

    @Test
    fun `attempting to archive system category is blocked`() = runTest(testDispatcher) {
        val sysCat = Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981", isSystem = true)
        categoryRepository.insertCategory(sysCat)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val category = viewModel.uiState.value.allCategories.first()
        viewModel.onArchiveCategoryClicked(category)

        assertEquals(CategoryManagementError.SystemCategoryCannotBeArchived, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.categoryToArchive)
    }

    @Test
    fun `archive custom category succeeds`() = runTest(testDispatcher) {
        val customCat = Category(name = "Hobbies", type = CategoryType.EXPENSE, iconName = "palette", colorHex = "#EC4899", isSystem = false)
        val id = categoryRepository.insertCategory(customCat)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val category = viewModel.uiState.value.allCategories.first()
        viewModel.onArchiveCategoryClicked(category)

        assertEquals(category, viewModel.uiState.value.categoryToArchive)

        viewModel.onConfirmArchive()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.categoryToArchive)
        assertTrue(viewModel.uiState.value.filteredCategories.isEmpty())

        val dbCat = categoryRepository.getCategoryById(id)
        assertNotNull(dbCat)
        assertTrue(dbCat!!.isArchived)
    }
}
