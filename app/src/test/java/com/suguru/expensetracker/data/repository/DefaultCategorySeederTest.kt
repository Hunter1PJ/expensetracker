package com.suguru.expensetracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.BudgetEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.TransactionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultCategorySeederTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var seeder: DefaultCategorySeeder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = DefaultCategorySeeder(database, database.categoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshDatabaseSeedsAllFourteenSystemCategories() = runBlocking {
        seeder.ensureSeeded()

        val categories = database.categoryDao().getAllCategories()
        assertEquals(14, categories.size)
        assertEquals(9, categories.count { it.type == CategoryType.EXPENSE })
        assertEquals(5, categories.count { it.type == CategoryType.INCOME })
        assertTrue(categories.all { it.isSystem && !it.isArchived && it.iconName.isNotBlank() && it.colorHex.isNotBlank() })
    }

    @Test
    fun repeatedSeedingDoesNotCreateDuplicates() = runBlocking {
        seeder.ensureSeeded()
        val firstSeed = database.categoryDao().getAllCategories().sortedBy { it.id }

        seeder.ensureSeeded()

        assertEquals(firstSeed, database.categoryDao().getAllCategories().sortedBy { it.id })
    }

    @Test
    fun matchingActiveCustomCategoryIsPreservedWithoutDuplicate() = runBlocking {
        val custom = customCategory(name = "  gRoCeRiEs  ", type = CategoryType.EXPENSE)
        val customId = database.categoryDao().insertCategory(custom)

        seeder.ensureSeeded()

        assertEquals(custom.copy(id = customId), database.categoryDao().getCategoryById(customId))
        assertEquals(1, database.categoryDao().getAllCategories().count { it.name.trim().equals("groceries", true) })
    }

    @Test
    fun bothCategorySatisfiesExpenseAndIncomeDefaults() = runBlocking {
        val both = customCategory(name = "Other", type = CategoryType.BOTH)
        val bothId = database.categoryDao().insertCategory(both)

        seeder.ensureSeeded()

        val expenseCategories = database.categoryDao().observeActiveCategoriesByType(CategoryType.EXPENSE).first()
        val incomeCategories = database.categoryDao().observeActiveCategoriesByType(CategoryType.INCOME).first()
        assertEquals(1, database.categoryDao().getAllCategories().count { it.name == "Other" })
        assertTrue(expenseCategories.any { it.id == bothId })
        assertTrue(incomeCategories.any { it.id == bothId })
    }

    @Test
    fun archivedMatchingCustomCategoryIsPreservedAndActiveDefaultIsCreated() = runBlocking {
        val archived = customCategory(name = "Food", type = CategoryType.EXPENSE, isArchived = true)
        val archivedId = database.categoryDao().insertCategory(archived)

        seeder.ensureSeeded()

        assertEquals(archived.copy(id = archivedId), database.categoryDao().getCategoryById(archivedId))
        val foodRows = database.categoryDao().getAllCategories().filter { it.name == "Food" }
        assertEquals(2, foodRows.size)
        assertTrue(foodRows.any { it.id == archivedId && it.isArchived && !it.isSystem })
        assertTrue(foodRows.any { it.isSystem && !it.isArchived })
        assertFalse(
            database.categoryDao().observeActiveCategoriesByType(CategoryType.EXPENSE)
                .first().any { it.id == archivedId }
        )
    }

    @Test
    fun existingSystemDefaultIdAndDataArePreserved() = runBlocking {
        val existing = customCategory(
            name = "Food",
            type = CategoryType.EXPENSE,
            isSystem = true
        ).copy(iconName = "school", colorHex = "#123456")
        val existingId = database.categoryDao().insertCategory(existing)

        seeder.ensureSeeded()

        assertEquals(existing.copy(id = existingId), database.categoryDao().getCategoryById(existingId))
        assertEquals(1, database.categoryDao().getAllCategories().count { it.name == "Food" })
    }

    @Test
    fun seededExpenseAndIncomeCategoriesAreTypeCompatible() = runBlocking {
        seeder.ensureSeeded()

        val expenses = database.categoryDao().observeActiveCategoriesByType(CategoryType.EXPENSE).first()
        val incomes = database.categoryDao().observeActiveCategoriesByType(CategoryType.INCOME).first()
        assertEquals(EXPENSE_NAMES, expenses.map { it.name }.toSet())
        assertEquals(INCOME_NAMES, incomes.map { it.name }.toSet())
        assertFalse(expenses.any { it.type == CategoryType.INCOME })
        assertFalse(incomes.any { it.type == CategoryType.EXPENSE })
    }

    @Test
    fun historicalTransactionCategoryReferenceIsPreserved() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(customCategory("Food", CategoryType.EXPENSE))
        val accountId = insertAccount()
        val transaction = TransactionEntity(
            type = TransactionType.EXPENSE,
            amountInMinorUnits = 2599,
            currencyCode = "USD",
            accountId = accountId,
            destinationAccountId = null,
            categoryId = categoryId,
            transactionTime = Instant.parse("2026-11-12T12:00:00Z"),
            note = "Lunch",
            recurringRuleId = null,
            createdAt = Instant.parse("2026-11-12T12:00:00Z")
        )
        val transactionId = database.transactionDao().insertTransaction(transaction)

        seeder.ensureSeeded()

        val stored = database.transactionDao().getTransactionById(transactionId)
        assertNotNull(stored)
        assertEquals(categoryId, stored!!.categoryId)
        assertEquals(transaction.copy(id = transactionId), stored)
        assertNotNull(database.categoryDao().getCategoryById(categoryId))
    }

    @Test
    fun budgetCategoryReferenceIsPreserved() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(customCategory("Food", CategoryType.EXPENSE))
        val budget = BudgetEntity(
            categoryId = categoryId,
            limitAmountInMinorUnits = 5000,
            currencyCode = "USD",
            periodType = BudgetPeriodType.MONTHLY,
            startDate = LocalDate.parse("2026-11-01"),
            endDate = LocalDate.parse("2026-11-30"),
            isActive = true
        )
        val budgetId = database.budgetDao().insertBudget(budget)

        seeder.ensureSeeded()

        val stored = database.budgetDao().getBudgetById(budgetId)
        assertNotNull(stored)
        assertEquals(categoryId, stored!!.categoryId)
        assertEquals(budget.copy(id = budgetId), stored)
        assertNotNull(database.categoryDao().getCategoryById(categoryId))
    }

    @Test
    fun concurrentSeedingCreatesOneSetOfDefaults() = runBlocking {
        coroutineScope {
            (1..8).map { async { seeder.ensureSeeded() } }.awaitAll()
        }

        val categories = database.categoryDao().getAllCategories()
        assertEquals(14, categories.size)
        assertEquals(14, categories.map { it.name to it.type }.toSet().size)
    }

    @Test
    fun partiallyPopulatedDatabaseOnlyReceivesMissingDefaults() = runBlocking {
        val existingCustom = customCategory("Food", CategoryType.EXPENSE)
        val existingId = database.categoryDao().insertCategory(existingCustom)

        seeder.ensureSeeded()

        val categories = database.categoryDao().getAllCategories()
        assertEquals(14, categories.size)
        assertEquals(existingCustom.copy(id = existingId), database.categoryDao().getCategoryById(existingId))
        assertEquals(1, categories.count { it.name == "Food" && it.type == CategoryType.EXPENSE })
    }

    private suspend fun insertAccount(): Long = database.accountDao().insertAccount(
        AccountEntity(
            name = "Cash",
            type = AccountType.CASH,
            initialBalanceAmountInMinorUnits = 0,
            initialBalanceCurrencyCode = "USD",
            iconName = "wallet",
            colorHex = "#10B981",
            isArchived = false,
            createdAt = Instant.parse("2026-11-12T12:00:00Z")
        )
    )

    private fun customCategory(
        name: String,
        type: CategoryType,
        isArchived: Boolean = false,
        isSystem: Boolean = false
    ) = CategoryEntity(
        name = name,
        type = type,
        iconName = "fastfood",
        colorHex = "#112233",
        isSystem = isSystem,
        isArchived = isArchived
    )

    private companion object {
        val EXPENSE_NAMES = setOf(
            "Groceries", "Food", "Transport", "Study", "Shopping",
            "Health", "Entertainment", "Bills", "Other"
        )
        val INCOME_NAMES = setOf("Salary", "Freelance", "Business", "Gift", "Other")
    }
}