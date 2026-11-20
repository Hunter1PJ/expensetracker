package com.suguru.expensetracker.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.File

/**
 * Copies the old unpartitioned database into Guest without consuming or deleting the source.
 * The source remains available for recovery. A durable in-progress marker makes the final rename
 * recoverable after process death.
 */
object LegacyWorkspaceDatabaseMigrator {
    enum class Result { NOT_NEEDED, MIGRATED_TO_GUEST, ALREADY_MIGRATED, GUEST_COLLISION }

    private const val PREFS = "workspace_database_migration"
    private const val KEY_COMPLETE = "legacy_guest_copy_complete"
    private const val KEY_IN_PROGRESS = "legacy_guest_copy_in_progress"
    private const val KEY_COLLISION = "legacy_guest_copy_collision"
    private const val LEGACY_NAME = "expense_tracker.db"
    private const val CANDIDATE_NAME = "expense_tracker_guest_candidate.db"

    @Synchronized
    fun migrate(context: Context): Result {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_COMPLETE, false)) return Result.ALREADY_MIGRATED

        val legacy = app.getDatabasePath(LEGACY_NAME)
        val guest = app.getDatabasePath(WorkspaceId.Guest.databaseName)
        if (!legacy.exists()) return Result.NOT_NEEDED

        if (prefs.getBoolean(KEY_IN_PROGRESS, false) && guest.exists()) {
            if (isRoomDatabaseReadable(app, guest.name)) {
                prefs.edit().putBoolean(KEY_COMPLETE, true).putBoolean(KEY_IN_PROGRESS, false)
                    .putBoolean(KEY_COLLISION, false).commit()
                return Result.ALREADY_MIGRATED
            }
            return Result.GUEST_COLLISION
        }

        if (guest.exists()) {
            prefs.edit().putBoolean(KEY_COLLISION, true).commit()
            return Result.GUEST_COLLISION
        }

        val candidate = app.getDatabasePath(CANDIDATE_NAME)
        removeDatabaseFiles(candidate)
        if (!checkpoint(legacy)) return Result.GUEST_COLLISION

        prefs.edit().putBoolean(KEY_IN_PROGRESS, true).commit()
        try {
            legacy.copyTo(candidate, overwrite = true)
            File(legacy.path + "-wal").takeIf(File::exists)?.copyTo(File(candidate.path + "-wal"), overwrite = true)

            if (!isRoomDatabaseReadable(app, candidate.name)) {
                removeDatabaseFiles(candidate)
                prefs.edit().putBoolean(KEY_IN_PROGRESS, false).commit()
                return Result.GUEST_COLLISION
            }

            if (guest.exists() || !candidate.renameTo(guest)) {
                return Result.GUEST_COLLISION
            }

            if (!isRoomDatabaseReadable(app, guest.name)) {
                // Keep both files for recovery; never replace a source with an unreadable copy.
                return Result.GUEST_COLLISION
            }
            prefs.edit().putBoolean(KEY_COMPLETE, true).putBoolean(KEY_IN_PROGRESS, false)
                .putBoolean(KEY_COLLISION, false).commit()
            return Result.MIGRATED_TO_GUEST
        } catch (_: Exception) {
            removeDatabaseFiles(candidate)
            prefs.edit().putBoolean(KEY_IN_PROGRESS, false).commit()
            return Result.GUEST_COLLISION
        } finally {
            removeDatabaseFiles(candidate)
        }
    }

    fun hasRecoverableCollision(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COLLISION, false)

    /**
     * Makes an idempotent database-file clone. Callers close the source Room instance first.
     * Both workspaces retain the same primary keys and relational graph.
     */
    @Synchronized
    fun cloneWorkspace(context: Context, source: WorkspaceId, target: WorkspaceId): Boolean {
        require(source != target)
        val app = context.applicationContext
        val sourceFile = app.getDatabasePath(source.databaseName)
        val targetFile = app.getDatabasePath(target.databaseName)
        if (!sourceFile.exists()) return false
        if (targetFile.exists()) return false
        if (!checkpoint(sourceFile)) return false

        val candidate = app.getDatabasePath("expense_tracker_workspace_candidate.db")
        removeDatabaseFiles(candidate)
        return try {
            sourceFile.copyTo(candidate, overwrite = true)
            File(sourceFile.path + "-wal").takeIf(File::exists)
                ?.copyTo(File(candidate.path + "-wal"), overwrite = true)
            if (!isRoomDatabaseReadable(app, candidate.name)) return false
            if (targetFile.exists() || !candidate.renameTo(targetFile)) return false
            isRoomDatabaseReadable(app, targetFile.name)
        } catch (_: Exception) {
            false
        } finally {
            removeDatabaseFiles(candidate)
        }
    }

    private fun checkpoint(file: File): Boolean {
        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use {
                it.moveToFirst() && it.getInt(0) == 0
            }
        } catch (_: Exception) {
            false
        } finally {
            database?.close()
        }
    }

    fun isWorkspaceDatabaseReadable(context: Context, workspaceId: WorkspaceId): Boolean =
        isRoomDatabaseReadable(context.applicationContext, workspaceId.databaseName)

    private fun isRoomDatabaseReadable(context: Context, name: String): Boolean {
        val database = try {
            Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, name)
                .addMigrations(
                    ExpenseTrackerDatabase.MIGRATION_1_2,
                    ExpenseTrackerDatabase.MIGRATION_2_3,
                    ExpenseTrackerDatabase.MIGRATION_3_4
                ).build()
        } catch (_: Exception) {
            return false
        }
        return try {
            val sql = database.openHelper.writableDatabase
            val integrity = sql.query("PRAGMA integrity_check").use {
                it.moveToFirst() && it.getString(0) == "ok"
            }
            val foreignKeysValid = sql.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
            integrity && foreignKeysValid
        } catch (_: Exception) {
            false
        } finally {
            database.close()
        }
    }

    private fun removeDatabaseFiles(file: File) {
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
    }
}
