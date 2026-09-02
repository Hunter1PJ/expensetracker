package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY start_date DESC")
    fun observeActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY is_active DESC, start_date DESC")
    fun observeAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND is_active = 1 ORDER BY start_date DESC")
    fun observeBudgetsByCategory(categoryId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    fun observeBudgetById(id: Long): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET is_active = 0 WHERE id = :id")
    suspend fun deactivateBudget(id: Long)
}
