package com.example.data.repository

import com.example.data.local.dao.CategoryDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun observeActiveCategories(): Flow<List<Category>> {
        return categoryDao.observeActiveCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllCategories(): Flow<List<Category>> {
        return categoryDao.observeAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeActiveCategoriesByType(type: CategoryType): Flow<List<Category>> {
        return categoryDao.observeActiveCategoriesByType(type).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeCategoryById(id: Long): Flow<Category?> {
        return categoryDao.observeCategoryById(id).map { it?.toDomain() }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)?.toDomain()
    }

    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun insertCategories(categories: List<Category>): List<Long> {
        return categoryDao.insertCategories(categories.map { it.toEntity() })
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun archiveCategory(id: Long) {
        categoryDao.archiveCategory(id)
    }
}
