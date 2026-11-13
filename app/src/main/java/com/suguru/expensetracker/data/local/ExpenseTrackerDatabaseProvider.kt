package com.suguru.expensetracker.data.local

import android.content.Context
import androidx.room.Room

/**
 * Thread-safe singleton provider for the ExpenseTracker Room Database.
 */
object ExpenseTrackerDatabaseProvider {

    @Volatile
    private var instance: ExpenseTrackerDatabase? = null

    fun getDatabase(context: Context): ExpenseTrackerDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ExpenseTrackerDatabase::class.java,
                "expense_tracker.db"
            ).addMigrations(
                ExpenseTrackerDatabase.MIGRATION_1_2,
                ExpenseTrackerDatabase.MIGRATION_2_3,
                ExpenseTrackerDatabase.MIGRATION_3_4
            )
            .build().also { instance = it }
        }
    }
}
