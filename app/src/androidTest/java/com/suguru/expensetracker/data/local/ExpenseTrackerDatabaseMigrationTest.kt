package com.suguru.expensetracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseTrackerDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ExpenseTrackerDatabase::class.java,
        emptyList()
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(V2_DATABASE)
        context.deleteDatabase(V1_DATABASE)
    }

    @Test
    fun migrateV2ToV3PreservesRowsAndEnforcesNullableUniqueInternalKey() {
        val versionTwo = migrationHelper.createDatabase(V2_DATABASE, 2)
        insertExistingRows(versionTwo)
        versionTwo.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            V2_DATABASE,
            3,
            true,
            ExpenseTrackerDatabase.MIGRATION_2_3
        )
        try {
            assertEquals(
                listOf(
                    listOf("10", "Cash", "CASH", "2500", "USD", "wallet", "#112233", "0", "123456"),
                    listOf("11", "Card", "CREDIT", "0", "EUR", "card", "#445566", "1", "123457")
                ),
                rows(migrated, "SELECT id, name, type, initial_balance_minor_units, initial_balance_currency_code, icon_name, color_hex, is_archived, created_at FROM accounts ORDER BY id")
            )
            assertEquals(
                listOf(listOf("4", "Food", "EXPENSE", "food", "#223344", "1", "0")),
                rows(migrated, "SELECT id, name, type, icon_name, color_hex, is_system, is_archived FROM categories")
            )
            assertEquals(
                listOf(listOf("30", "EXPENSE", "875", "USD", "11", null, "4", "200001", "Lunch", "20", "2025-03-04", "300001")),
                rows(migrated, "SELECT id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, transaction_time, note, recurring_rule_id, recurring_occurrence_date, created_at FROM transactions")
            )
            assertEquals(
                listOf(listOf("20", "TRANSFER", "1200", "EUR", "10", "11", null, "MONTHLY", "2025-01-01", null, "2025-04-01", "Rent", "1")),
                rows(migrated, "SELECT id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, frequency, start_date, end_date, next_occurrence, note, is_active FROM recurring_transactions")
            )
            assertEquals(
                listOf(listOf("40", "4", "5000", "USD", "MONTHLY", "2025-01-01", "2025-01-31", "1")),
                rows(migrated, "SELECT id, category_id, limit_amount_minor_units, currency_code, period_type, start_date, end_date, is_active FROM budgets")
            )
            assertEquals(
                listOf(listOf("50", "20", "2025-03-01", "30", "COMPLETED", "400001")),
                rows(migrated, "SELECT id, rule_id, occurrence_date, generated_transaction_id, status, processed_at FROM recurring_occurrences")
            )
            assertEquals(2L, scalarLong(migrated, "SELECT COUNT(*) FROM accounts WHERE internal_key IS NULL"))
            assertTrue(hasUniqueIndex(migrated, "index_accounts_internal_key"))
            assertEquals(0, migrated.query("PRAGMA foreign_key_check").count)

            migrated.execSQL("UPDATE accounts SET internal_key = 'default' WHERE id = 10")
            var duplicateRejected = false
            try {
                migrated.execSQL("UPDATE accounts SET internal_key = 'default' WHERE id = 11")
            } catch (_: SQLiteConstraintException) {
                duplicateRejected = true
            }
            assertTrue("Duplicate non-null internal keys must be rejected", duplicateRejected)
            assertEquals(1L, scalarLong(migrated, "SELECT COUNT(*) FROM accounts WHERE internal_key IS NULL"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationChainFromV1ToV4PreservesRowsAndRetainsMigrationChanges() {
        val versionTwo = migrationHelper.createDatabase(V1_DATABASE, 2)
        insertExistingRows(versionTwo)
        convertToVersionOneShape(versionTwo)
        versionTwo.close()

        val database = Room.databaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
            V1_DATABASE
        ).addMigrations(
            ExpenseTrackerDatabase.MIGRATION_1_2,
            ExpenseTrackerDatabase.MIGRATION_2_3,
            ExpenseTrackerDatabase.MIGRATION_3_4
        ).allowMainThreadQueries().build()
        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(1L, scalarLong(migrated, "SELECT COUNT(*) FROM accounts WHERE id = 10 AND name = 'Cash' AND internal_key IS NULL"))
            assertEquals(1L, scalarLong(migrated, "SELECT COUNT(*) FROM transactions WHERE id = 30 AND account_id = 11 AND category_id = 4 AND recurring_rule_id = 20 AND recurring_occurrence_date IS NULL"))
            assertEquals(1L, scalarLong(migrated, "SELECT COUNT(*) FROM recurring_transactions WHERE id = 20 AND account_id = 10 AND destination_account_id = 11"))
            assertTrue(hasColumn(migrated, "transactions", "recurring_occurrence_date"))
            assertTrue(hasTable(migrated, "recurring_occurrences"))
            assertTrue(hasUniqueIndex(migrated, "index_recurring_occurrences_rule_id_occurrence_date", "recurring_occurrences"))
            assertTrue(hasUniqueIndex(migrated, "index_accounts_internal_key"))
            assertTrue(hasTable(migrated, "pending_settings_restore"))
            assertEquals(0, migrated.query("PRAGMA foreign_key_check").count)
        } finally {
            database.close()
        }
    }

    @Test
    fun freshV3DatabaseContainsNullableInternalKeyAndUniqueIndex() {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertTrue(hasColumn(sqlite, "accounts", "internal_key"))
            assertTrue(hasUniqueIndex(sqlite, "index_accounts_internal_key"))
            assertFalse(columnIsNotNull(sqlite, "accounts", "internal_key"))
        } finally {
            database.close()
        }
    }

    private fun insertExistingRows(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO accounts (id, name, type, initial_balance_minor_units, initial_balance_currency_code, icon_name, color_hex, is_archived, created_at) VALUES (10, 'Cash', 'CASH', 2500, 'USD', 'wallet', '#112233', 0, 123456)")
        database.execSQL("INSERT INTO accounts (id, name, type, initial_balance_minor_units, initial_balance_currency_code, icon_name, color_hex, is_archived, created_at) VALUES (11, 'Card', 'CREDIT', 0, 'EUR', 'card', '#445566', 1, 123457)")
        database.execSQL("INSERT INTO categories (id, name, type, icon_name, color_hex, is_system, is_archived) VALUES (4, 'Food', 'EXPENSE', 'food', '#223344', 1, 0)")
        database.execSQL("INSERT INTO recurring_transactions (id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, frequency, start_date, end_date, next_occurrence, note, is_active) VALUES (20, 'TRANSFER', 1200, 'EUR', 10, 11, NULL, 'MONTHLY', '2025-01-01', NULL, '2025-04-01', 'Rent', 1)")
        database.execSQL("INSERT INTO transactions (id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, transaction_time, note, recurring_rule_id, recurring_occurrence_date, created_at) VALUES (30, 'EXPENSE', 875, 'USD', 11, NULL, 4, 200001, 'Lunch', 20, '2025-03-04', 300001)")
        database.execSQL("INSERT INTO budgets (id, category_id, limit_amount_minor_units, currency_code, period_type, start_date, end_date, is_active) VALUES (40, 4, 5000, 'USD', 'MONTHLY', '2025-01-01', '2025-01-31', 1)")
        database.execSQL("INSERT INTO recurring_occurrences (id, rule_id, occurrence_date, generated_transaction_id, status, processed_at) VALUES (50, 20, '2025-03-01', 30, 'COMPLETED', 400001)")
    }

    private fun convertToVersionOneShape(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `recurring_occurrences`")
        database.execSQL("ALTER TABLE `transactions` RENAME TO `transactions_version_2`")
        database.execSQL("""
            CREATE TABLE `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `amount_minor_units` INTEGER NOT NULL,
                `currency_code` TEXT NOT NULL,
                `account_id` INTEGER NOT NULL,
                `destination_account_id` INTEGER,
                `category_id` INTEGER,
                `transaction_time` INTEGER NOT NULL,
                `note` TEXT,
                `recurring_rule_id` INTEGER,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`destination_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`recurring_rule_id`) REFERENCES `recurring_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        database.execSQL("INSERT INTO transactions (id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, transaction_time, note, recurring_rule_id, created_at) SELECT id, type, amount_minor_units, currency_code, account_id, destination_account_id, category_id, transaction_time, note, recurring_rule_id, created_at FROM transactions_version_2")
        database.execSQL("DROP TABLE `transactions_version_2`")
        database.execSQL("CREATE INDEX `index_transactions_account_id` ON `transactions` (`account_id`)")
        database.execSQL("CREATE INDEX `index_transactions_destination_account_id` ON `transactions` (`destination_account_id`)")
        database.execSQL("CREATE INDEX `index_transactions_category_id` ON `transactions` (`category_id`)")
        database.execSQL("CREATE INDEX `index_transactions_transaction_time` ON `transactions` (`transaction_time`)")
        database.execSQL("CREATE INDEX `index_transactions_recurring_rule_id` ON `transactions` (`recurring_rule_id`)")
        database.execSQL("CREATE INDEX `index_transactions_account_id_transaction_time` ON `transactions` (`account_id`, `transaction_time`)")
        database.execSQL("PRAGMA user_version = 1")
    }

    private fun rows(database: SupportSQLiteDatabase, sql: String): List<List<String?>> {
        return database.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map(cursor::getString))
                }
            }
        }
    }

    private fun scalarLong(database: SupportSQLiteDatabase, sql: String): Long {
        return database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun hasColumn(database: SupportSQLiteDatabase, table: String, column: String): Boolean {
        return database.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return@use true
            }
            false
        }
    }

    private fun columnIsNotNull(database: SupportSQLiteDatabase, table: String, column: String): Boolean {
        return database.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return@use cursor.getInt(3) != 0
            }
            false
        }
    }

    private fun hasTable(database: SupportSQLiteDatabase, table: String): Boolean =
        scalarLong(database, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'") > 0

    private fun hasUniqueIndex(database: SupportSQLiteDatabase, index: String, table: String = "accounts"): Boolean {
        return database.query("PRAGMA index_list('$table')").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == index) return@use cursor.getInt(2) == 1
            }
            false
        }
    }

    private companion object {
        const val V2_DATABASE = "migration_v2_to_v3.db"
        const val V1_DATABASE = "migration_v1_to_v3.db"
    }
}
