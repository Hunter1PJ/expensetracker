package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converter.ExpenseTrackerConverters
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.RecurringTransactionDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.RecurringTransactionEntity
import com.example.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(ExpenseTrackerConverters::class)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
}
