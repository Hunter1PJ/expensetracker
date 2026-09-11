package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.ExpenseTrackerConverters
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.BackupDao
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.RecurringOccurrenceDao
import com.example.data.local.dao.RecurringTransactionDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.RecurringOccurrenceEntity
import com.example.data.local.entity.RecurringTransactionEntity
import com.example.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        RecurringOccurrenceEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(ExpenseTrackerConverters::class)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun recurringOccurrenceDao(): RecurringOccurrenceDao
    abstract fun backupDao(): BackupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurring_occurrence_date` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurring_rule_id_recurring_occurrence_date` ON `transactions` (`recurring_rule_id`, `recurring_occurrence_date`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recurring_occurrences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rule_id` INTEGER NOT NULL,
                        `occurrence_date` TEXT NOT NULL,
                        `generated_transaction_id` INTEGER,
                        `status` TEXT NOT NULL,
                        `processed_at` INTEGER NOT NULL,
                        FOREIGN KEY(`rule_id`) REFERENCES `recurring_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_rule_id_occurrence_date` ON `recurring_occurrences` (`rule_id`, `occurrence_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_generated_transaction_id` ON `recurring_occurrences` (`generated_transaction_id`)")
            }
        }
    }
}
