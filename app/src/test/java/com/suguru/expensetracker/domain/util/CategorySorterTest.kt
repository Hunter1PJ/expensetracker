package com.suguru.expensetracker.domain.util

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySorterTest {

    private fun category(
        id: Long,
        name: String,
        isSystem: Boolean = true,
        type: CategoryType = CategoryType.EXPENSE
    ): Category {
        return Category(
            id = id,
            name = name,
            type = type,
            iconName = "icon",
            colorHex = "#000000",
            isSystem = isSystem,
            isArchived = false
        )
    }

    @Test
    fun `unused categories follow curated initial tie-breaker order`() {
        val categories = listOf(
            category(1L, "Bills"),
            category(2L, "Food"),
            category(3L, "Other"),
            category(4L, "Groceries"),
            category(5L, "Housing")
        )

        val sorted = CategorySorter.sortExpenseCategories(categories, emptyMap())

        val expectedNames = listOf("Food", "Groceries", "Bills", "Housing", "Other")
        assertEquals(expectedNames, sorted.map { it.name })
    }

    @Test
    fun `most frequently used expense category appears first`() {
        val categories = listOf(
            category(1L, "Food"),
            category(2L, "Groceries"),
            category(3L, "Transport"),
            category(4L, "Other")
        )
        // Transport has 10 expenses, Groceries has 5, Food has 1
        val usageCounts = mapOf(3L to 10, 2L to 5, 1L to 1)

        val sorted = CategorySorter.sortExpenseCategories(categories, usageCounts)

        val expectedNames = listOf("Transport", "Groceries", "Food", "Other")
        assertEquals(expectedNames, sorted.map { it.name })
    }

    @Test
    fun `ties use stable curated default order as tie-breaker`() {
        val categories = listOf(
            category(1L, "Transport"),
            category(2L, "Groceries"),
            category(3L, "Food"),
            category(4L, "Bills")
        )
        // Transport and Food both have 5 expenses
        val usageCounts = mapOf(1L to 5, 3L to 5, 2L to 1, 4L to 1)

        val sorted = CategorySorter.sortExpenseCategories(categories, usageCounts)

        // Food tie-breaks before Transport; Groceries tie-breaks before Bills
        val expectedNames = listOf("Food", "Transport", "Groceries", "Bills")
        assertEquals(expectedNames, sorted.map { it.name })
    }

    @Test
    fun `Other is always last even if it has the highest transaction count`() {
        val categories = listOf(
            category(1L, "Other"),
            category(2L, "Food"),
            category(3L, "Groceries")
        )
        // Other has 100 transactions, Food has 10, Groceries has 5
        val usageCounts = mapOf(1L to 100, 2L to 10, 3L to 5)

        val sorted = CategorySorter.sortExpenseCategories(categories, usageCounts)

        val expectedNames = listOf("Food", "Groceries", "Other")
        assertEquals(expectedNames, sorted.map { it.name })
        assertEquals("Other", sorted.last().name)
    }

    @Test
    fun `income categories use income usage and keep Other last`() {
        val categories = listOf(
            category(1L, "Other", type = CategoryType.INCOME),
            category(2L, "Salary", type = CategoryType.INCOME),
            category(3L, "Business", type = CategoryType.INCOME),
            category(4L, "Freelance", type = CategoryType.INCOME),
            category(5L, "Gift", type = CategoryType.INCOME)
        )
        val incomeUsage = mapOf(3L to 4, 2L to 2, 1L to 100)

        val sorted = CategorySorter.sortIncomeCategories(categories, incomeUsage)

        assertEquals(listOf("Business", "Salary", "Freelance", "Gift", "Other"), sorted.map { it.name })
    }

    @Test
    fun `income categories use deterministic Salary first fallback for equal and zero usage`() {
        val categories = listOf(
            category(1L, "Other", type = CategoryType.INCOME),
            category(2L, "Gift", type = CategoryType.INCOME),
            category(3L, "Freelance", type = CategoryType.INCOME),
            category(4L, "Business", type = CategoryType.INCOME),
            category(5L, "Salary", type = CategoryType.INCOME)
        )

        assertEquals(
            listOf("Salary", "Business", "Freelance", "Gift", "Other"),
            CategorySorter.sortIncomeCategories(categories).map { it.name }
        )
        assertEquals(
            listOf("Salary", "Business", "Freelance", "Gift", "Other"),
            CategorySorter.sortIncomeCategories(categories, categories.associate { it.id to 3 }).map { it.name }
        )
    }

    @Test
    fun `custom categories are placed appropriately with usage count`() {
        val customCat = category(100L, "Custom Hobby", isSystem = false)
        val food = category(1L, "Food", isSystem = true)
        val other = category(2L, "Other", isSystem = true)

        val categories = listOf(customCat, other, food)
        val usageCounts = mapOf(100L to 20, 1L to 5)

        val sorted = CategorySorter.sortExpenseCategories(categories, usageCounts)

        // Custom Hobby (20) > Food (5) > Other (0)
        assertEquals(listOf("Custom Hobby", "Food", "Other"), sorted.map { it.name })
    }
}
