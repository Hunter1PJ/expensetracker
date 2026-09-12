package com.suguru.expensetracker.presentation.categories.add

import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.usecase.category.CreateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.UpdateCategoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddCategoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        categoryRepository = FakeCategoryRepository()
        createCategoryUseCase = CreateCategoryUseCase(categoryRepository)
        updateCategoryUseCase = UpdateCategoryUseCase(categoryRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(categoryId: Long = 0L): AddCategoryViewModel {
        return AddCategoryViewModel(
            categoryId = categoryId,
            createCategoryUseCase = createCategoryUseCase,
            updateCategoryUseCase = updateCategoryUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    @Test
    fun `blank name fails validation`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("   ")
        viewModel.saveCategory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddCategoryError.NameRequired, state.error)
        assertFalse(state.isSavedSuccessfully)
    }

    @Test
    fun `successfully create new custom category`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("Gaming")
        viewModel.onTypeChanged(CategoryType.EXPENSE)
        viewModel.onIconChanged("sports_esports")
        viewModel.onColorChanged("#8B5CF6")

        viewModel.saveCategory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val categories = categoryRepository.observeActiveCategories().first()
        assertEquals(1, categories.size)
        val created = categories.first()
        assertEquals("Gaming", created.name)
        assertEquals(CategoryType.EXPENSE, created.type)
        assertEquals("sports_esports", created.iconName)
        assertEquals("#8B5CF6", created.colorHex)
        assertFalse(created.isSystem)
    }

    @Test
    fun `edit mode loads and updates custom category`() = runTest(testDispatcher) {
        val catId = categoryRepository.insertCategory(
            Category(
                name = "Old Category",
                type = CategoryType.EXPENSE,
                iconName = "fastfood",
                colorHex = "#EF4444",
                isSystem = false
            )
        )

        val viewModel = createViewModel(catId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals("Old Category", state.name)
        assertEquals(CategoryType.EXPENSE, state.type)

        viewModel.onNameChanged("Dining Out")
        viewModel.onColorChanged("#F59E0B")
        viewModel.saveCategory()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)

        val updated = categoryRepository.getCategoryById(catId)
        assertNotNull(updated)
        assertEquals("Dining Out", updated!!.name)
        assertEquals("#F59E0B", updated.colorHex)
    }
}
