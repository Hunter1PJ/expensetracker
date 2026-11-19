package com.suguru.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide Room instances keyed by workspace. Each DAO graph remains bound to exactly one file.
 */
object ExpenseTrackerDatabaseProvider {
    private val instances = ConcurrentHashMap<String, ExpenseTrackerDatabase>()

    fun getDatabase(context: Context): ExpenseTrackerDatabase =
        getDatabase(context, WorkspaceId.Guest)

    @Synchronized
    fun getDatabase(context: Context, workspaceId: WorkspaceId): ExpenseTrackerDatabase {
        val appContext = context.applicationContext
        appContext.getDatabasePath(workspaceId.databaseName).parentFile?.mkdirs()
        return instances.getOrPut(workspaceId.databaseName) {
            Room.databaseBuilder(
                appContext,
                ExpenseTrackerDatabase::class.java,
                workspaceId.databaseName
            ).addMigrations(
                ExpenseTrackerDatabase.MIGRATION_1_2,
                ExpenseTrackerDatabase.MIGRATION_2_3,
                ExpenseTrackerDatabase.MIGRATION_3_4
            ).build()
        }
    }

    @Synchronized
    fun closeDatabase(workspaceId: WorkspaceId) {
        instances.remove(workspaceId.databaseName)?.close()
    }

    fun databaseExists(context: Context, workspaceId: WorkspaceId): Boolean =
        context.applicationContext.getDatabasePath(workspaceId.databaseName).exists()
}
