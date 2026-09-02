package com.example.domain.repository

import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing categories.
 * Purely decoupled from persistence and framework dependencies.
 */
interface CategoryRepository {
    fun observeActiveCategories(): Flow<List<Category>>
    fun observeAllCategories(): Flow<List<Category>>
    fun observeActiveCategoriesByType(type: CategoryType): Flow<List<Category>>
    fun observeCategoryById(id: Long): Flow<Category?>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun insertCategories(categories: List<Category>): List<Long>
    suspend fun updateCategory(category: Category)
    suspend fun archiveCategory(id: Long)
}
