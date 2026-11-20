package com.suguru.expensetracker.domain.ai

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiCategoryNormalizerTest {

    private val availableCategories = listOf(
        Category(1L, "Food", CategoryType.EXPENSE, "fastfood", "#F59E0B", isSystem = true),
        Category(2L, "Groceries", CategoryType.EXPENSE, "fastfood", "#10B981", isSystem = true),
        Category(3L, "Transport", CategoryType.EXPENSE, "directions_car", "#3B82F6", isSystem = true),
        Category(4L, "Bills", CategoryType.EXPENSE, "receipt", "#64748B", isSystem = true),
        Category(5L, "Housing", CategoryType.EXPENSE, "home", "#F97316", isSystem = true),
        Category(6L, "Shopping", CategoryType.EXPENSE, "shopping_bag", "#EC4899", isSystem = true),
        Category(7L, "Health", CategoryType.EXPENSE, "local_hospital", "#EF4444", isSystem = true),
        Category(8L, "Entertainment", CategoryType.EXPENSE, "movie", "#8B5CF6", isSystem = true),
        Category(9L, "Other", CategoryType.EXPENSE, "category", "#14B8A6", isSystem = true)
    )

    @Test
    fun `exact match resolves to existing category`() {
        val result = AiCategoryNormalizer.resolve("Food", availableCategories)
        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Food", result.name)
    }

    @Test
    fun `Food & Dining maps to existing Food category`() {
        val result = AiCategoryNormalizer.resolve("Food & Dining", availableCategories)
        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Food", result.name)
    }

    @Test
    fun `pizza maps to existing Food category`() {
        val result = AiCategoryNormalizer.resolve("pizza", availableCategories)
        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Food", result.name)
    }

    @Test
    fun `groceries and supermarket map to Groceries category`() {
        val r1 = AiCategoryNormalizer.resolve("supermarket", availableCategories)
        assertNotNull(r1)
        assertEquals(2L, r1!!.id)
        assertEquals("Groceries", r1.name)

        val r2 = AiCategoryNormalizer.resolve("grocery", availableCategories)
        assertNotNull(r2)
        assertEquals(2L, r2!!.id)
        assertEquals("Groceries", r2.name)
    }

    @Test
    fun `taxi and transportation map to Transport category`() {
        val r1 = AiCategoryNormalizer.resolve("taxi", availableCategories)
        assertNotNull(r1)
        assertEquals(3L, r1!!.id)
        assertEquals("Transport", r1.name)

        val r2 = AiCategoryNormalizer.resolve("Transportation", availableCategories)
        assertNotNull(r2)
        assertEquals(3L, r2!!.id)
        assertEquals("Transport", r2.name)
    }

    @Test
    fun `unknown or unmappable category returns null and never invents category`() {
        val result = AiCategoryNormalizer.resolve("cryptocurrency staking", availableCategories)
        assertNull(result)
    }

    @Test
    fun `empty or null input returns null`() {
        assertNull(AiCategoryNormalizer.resolve(null, availableCategories))
        assertNull(AiCategoryNormalizer.resolve("", availableCategories))
        assertNull(AiCategoryNormalizer.resolve("   ", availableCategories))
    }
}
