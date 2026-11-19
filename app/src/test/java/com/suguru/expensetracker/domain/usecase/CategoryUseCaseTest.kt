package com.suguru.expensetracker.domain.usecase

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import java.time.Instant
import com.suguru.expensetracker.domain.usecase.category.ArchiveCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.CreateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveActiveCategoriesUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.category.UpdateCategoryUseCase
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
    fun incomeOrderingUsesOnlyIncomeUsageAndStaysWorkspaceScoped() = runBlocking {
        val salaryId = createCategoryUseCase(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#000")
        )
        val businessId = createCategoryUseCase(
            Category(name = "Business", type = CategoryType.INCOME, iconName = "work", colorHex = "#000")
        )
        createCategoryUseCase(Category(name = "Freelance", type = CategoryType.INCOME, iconName = "work", colorHex = "#000"))
        createCategoryUseCase(Category(name = "Gift", type = CategoryType.INCOME, iconName = "gift", colorHex = "#000"))
        val otherId = createCategoryUseCase(
            Category(name = "Other", type = CategoryType.INCOME, iconName = "category", colorHex = "#000")
        )

        val workspaceA = FakeTransactionRepository()
        val workspaceB = FakeTransactionRepository()
        repeat(20) { index ->
            workspaceA.insertTransaction(transaction(index + 1L, TransactionType.EXPENSE, salaryId))
        }
        workspaceA.insertTransaction(transaction(100L, TransactionType.INCOME, businessId))
        repeat(25) { index ->
            workspaceA.insertTransaction(transaction(101L + index, TransactionType.INCOME, otherId))
        }
        workspaceB.insertTransaction(transaction(500L, TransactionType.INCOME, businessId))

        val categoriesInA = ObserveCategoriesByTypeUseCase(categoryRepository, workspaceA)
            .invoke(CategoryType.INCOME).first()
        val categoriesInB = ObserveCategoriesByTypeUseCase(categoryRepository, workspaceB)
            .invoke(CategoryType.INCOME).first()

        assertEquals(listOf("Business", "Salary", "Freelance", "Gift", "Other"), categoriesInA.map { it.name })
        assertEquals(listOf("Business", "Salary", "Freelance", "Gift", "Other"), categoriesInB.map { it.name })

        repeat(2) { index ->
            workspaceB.insertTransaction(transaction(600L + index, TransactionType.INCOME, salaryId))
        }
        val updatedB = ObserveCategoriesByTypeUseCase(categoryRepository, workspaceB)
            .invoke(CategoryType.INCOME).first()
        assertEquals(listOf("Salary", "Business", "Freelance", "Gift", "Other"), updatedB.map { it.name })
        assertEquals("Other", updatedB.last().name)
    }

    private fun transaction(id: Long, type: TransactionType, categoryId: Long) = Transaction(
        id = id,
        type = type,
        amount = Money(100L, "USD"),
        accountId = 1L,
        categoryId = categoryId,
        transactionTime = Instant.parse("2026-01-01T00:00:00Z")
    )

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
