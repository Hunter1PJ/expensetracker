package com.example.domain.usecase

import com.example.domain.error.DomainException
import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.usecase.category.ArchiveCategoryUseCase
import com.example.domain.usecase.category.CreateCategoryUseCase
import com.example.domain.usecase.category.GetCategoryUseCase
import com.example.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.example.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.example.domain.usecase.category.UpdateCategoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryUseCaseTest {

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var archiveCategoryUseCase: ArchiveCategoryUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase
    private lateinit var observeActiveCategoriesUseCase: ObserveActiveCategoriesUseCase
    private lateinit var observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase

    @Before
    fun setUp() {
        categoryRepository = FakeCategoryRepository()
        createCategoryUseCase = CreateCategoryUseCase(categoryRepository)
        updateCategoryUseCase = UpdateCategoryUseCase(categoryRepository)
        archiveCategoryUseCase = ArchiveCategoryUseCase(categoryRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
        observeActiveCategoriesUseCase = ObserveActiveCategoriesUseCase(categoryRepository)
        observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(categoryRepository)
    }

    @Test
    fun createCategory_withValidData_succeeds() {
        runBlocking {
            val category = Category(
                name = "Groceries",
                type = CategoryType.EXPENSE,
                iconName = "shopping_cart",
                colorHex = "#FF5722"
            )
            val id = createCategoryUseCase(category)
            assertEquals(1L, id)

            val retrieved = getCategoryUseCase(id)
            assertNotNull(retrieved)
            assertEquals("Groceries", retrieved?.name)
        }
    }

    @Test(expected = DomainException.InvalidCategory::class)
    fun createCategory_withBlankName_throwsException() {
        runBlocking {
            val category = Category(
                name = "  ",
                type = CategoryType.EXPENSE,
                iconName = "shopping_cart",
                colorHex = "#FF5722"
            )
            createCategoryUseCase(category)
        }
    }

    @Test(expected = DomainException.SystemCategoryCannotBeArchived::class)
    fun archiveCategory_onSystemCategory_throwsException() {
        runBlocking {
            val systemCategory = Category(
                name = "Salary",
                type = CategoryType.INCOME,
                iconName = "attach_money",
                colorHex = "#4CAF50",
                isSystem = true
            )
            val id = createCategoryUseCase(systemCategory)
            archiveCategoryUseCase(id)
        }
    }

    @Test
    fun archiveCategory_onUserCategory_succeeds() {
        runBlocking {
            val userCategory = Category(
                name = "Hobbies",
                type = CategoryType.EXPENSE,
                iconName = "palette",
                colorHex = "#E91E63",
                isSystem = false
            )
            val id = createCategoryUseCase(userCategory)
            assertEquals(1, observeActiveCategoriesUseCase().first().size)

            archiveCategoryUseCase(id)
            assertTrue(observeActiveCategoriesUseCase().first().isEmpty())

            val archived = getCategoryUseCase(id)
            assertTrue(archived?.isArchived == true)
        }
    }

    @Test
    fun observeCategoriesByType_filtersAppropriately() {
        runBlocking {
            createCategoryUseCase(Category(name = "Salary", type = CategoryType.INCOME, iconName = "money", colorHex = "#000"))
            createCategoryUseCase(Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#000"))
            createCategoryUseCase(Category(name = "General", type = CategoryType.BOTH, iconName = "gen", colorHex = "#000"))

            val expenses = observeCategoriesByTypeUseCase(CategoryType.EXPENSE).first()
            assertEquals(2, expenses.size) // Food and General

            val incomes = observeCategoriesByTypeUseCase(CategoryType.INCOME).first()
            assertEquals(2, incomes.size) // Salary and General
        }
    }
}
