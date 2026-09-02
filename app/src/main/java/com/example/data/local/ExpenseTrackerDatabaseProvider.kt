package com.example.data.local

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
            ).build().also { instance = it }
        }
    }
}
