package com.suguru.expensetracker.data.repository

import androidx.room.withTransaction
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.dao.CategoryDao
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.domain.model.CategoryType

class DefaultCategorySeeder(
    private val database: ExpenseTrackerDatabase,
    private val categoryDao: CategoryDao
) {
    suspend fun ensureSeeded() {
        database.withTransaction {
            val existingCategories = categoryDao.getAllCategories()
            DEFAULT_CATEGORIES.forEach { defaultCategory ->
                val hasCompatibleActiveCategory = existingCategories.any { existing ->
                    !existing.isArchived &&
                        existing.name.trim().equals(defaultCategory.name, ignoreCase = true) &&
                        (existing.type == defaultCategory.type || existing.type == CategoryType.BOTH)
                }
                if (!hasCompatibleActiveCategory) {
                    categoryDao.insertCategory(defaultCategory)
                }
            }
        }
    }

    private companion object {
        val DEFAULT_CATEGORIES = listOf(
            category("Groceries", CategoryType.EXPENSE, "fastfood", "#10B981"),
            category("Food", CategoryType.EXPENSE, "fastfood", "#F59E0B"),
            category("Transport", CategoryType.EXPENSE, "directions_car", "#3B82F6"),
            category("Study", CategoryType.EXPENSE, "school", "#6366F1"),
            category("Shopping", CategoryType.EXPENSE, "shopping_bag", "#EC4899"),
            category("Health", CategoryType.EXPENSE, "local_hospital", "#EF4444"),
            category("Entertainment", CategoryType.EXPENSE, "movie", "#8B5CF6"),
            category("Bills", CategoryType.EXPENSE, "receipt", "#64748B"),
            category("Other", CategoryType.EXPENSE, "category", "#14B8A6"),
            category("Salary", CategoryType.INCOME, "work", "#10B981"),
            category("Freelance", CategoryType.INCOME, "work", "#3B82F6"),
            category("Business", CategoryType.INCOME, "work", "#6366F1"),
            category("Gift", CategoryType.INCOME, "card_giftcard", "#F59E0B"),
            category("Other", CategoryType.INCOME, "category", "#14B8A6")
        )

        fun category(
            name: String,
            type: CategoryType,
            iconName: String,
            colorHex: String
        ) = CategoryEntity(
            name = name,
            type = type,
            iconName = iconName,
            colorHex = colorHex,
            isSystem = true,
            isArchived = false
        )
    }
}