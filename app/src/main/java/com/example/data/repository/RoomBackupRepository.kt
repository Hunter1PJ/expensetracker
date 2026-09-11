package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.ExpenseTrackerDatabase
import com.example.domain.model.AccountType
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.CategoryType
import com.example.domain.model.ExpenseTrackerBackup
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.RecurringOccurrenceStatus
import com.example.domain.model.TransactionType
import com.example.domain.model.toBackup
import com.example.domain.model.toDomain
import com.example.domain.model.toEntity
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class RoomBackupRepository(
    private val db: ExpenseTrackerDatabase,
    private val settingsRepository: SettingsRepository
) : BackupRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ExpenseTrackerBackup::class.java)

    override fun serializeBackup(backup: ExpenseTrackerBackup): String {
        return adapter.toJson(backup)
    }

    override fun deserializeBackup(json: String): Result<ExpenseTrackerBackup> {
        return try {
            val backup = adapter.fromJson(json)
            if (backup != null) {
                Result.success(backup)
            } else {
                Result.failure(IllegalArgumentException("Backup JSON parsed to null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSnapshot(): ExpenseTrackerBackup {
        // Read everything atomically from the DB
        val backupDao = db.backupDao()
        val accounts = backupDao.getAllAccounts()
        val categories = backupDao.getAllCategories()
        val transactions = backupDao.getAllTransactions()
        val budgets = backupDao.getAllBudgets()
        val recurringTransactions = backupDao.getAllRecurringTransactions()
        val recurringOccurrences = backupDao.getAllRecurringOccurrences()

        // Read settings
        val settings = settingsRepository.settings.first()

        val backup = ExpenseTrackerBackup(
            formatVersion = 1,
            appVersion = "1.0",
            createdAt = Instant.now().toString(),
            databaseSchemaVersion = 2,
            accounts = accounts.map { it.toBackup() },
            categories = categories.map { it.toBackup() },
            transactions = transactions.map { it.toBackup() },
            budgets = budgets.map { it.toBackup() },
            recurringTransactions = recurringTransactions.map { it.toBackup() },
            recurringOccurrences = recurringOccurrences.map { it.toBackup() },
            settings = settings.toBackup(),
            checksum = null
        )

        // Generate checksum
        val checksum = computeChecksum(backup)
        return backup.copy(checksum = checksum)
    }

    override suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<Unit> {
        val validationResult = validateBackup(backup)
        if (validationResult.isFailure) {
            return validationResult
        }

        return try {
            db.withTransaction {
                val backupDao = db.backupDao()

                // 1. Delete all tables in FK-safe child-to-parent order
                db.compileStatement("DELETE FROM budgets").execute()
                db.compileStatement("DELETE FROM recurring_occurrences").execute()
                db.compileStatement("DELETE FROM transactions").execute()
                db.compileStatement("DELETE FROM recurring_transactions").execute()
                db.compileStatement("DELETE FROM categories").execute()
                db.compileStatement("DELETE FROM accounts").execute()

                // Reset sqlite_sequence for each table to avoid collision
                db.compileStatement(
                    "DELETE FROM sqlite_sequence WHERE name IN ('budgets', 'recurring_occurrences', 'transactions', 'recurring_transactions', 'categories', 'accounts')"
                ).execute()

                // 2. Insert entities in parent-to-child order
                if (backup.accounts.isNotEmpty()) {
                    backupDao.insertAccounts(backup.accounts.map { it.toEntity() })
                    val maxAccountId = backup.accounts.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('accounts', $maxAccountId)").execute()
                }

                if (backup.categories.isNotEmpty()) {
                    backupDao.insertCategories(backup.categories.map { it.toEntity() })
                    val maxCategoryId = backup.categories.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('categories', $maxCategoryId)").execute()
                }

                if (backup.recurringTransactions.isNotEmpty()) {
                    backupDao.insertRecurringTransactions(backup.recurringTransactions.map { it.toEntity() })
                    val maxRecurringId = backup.recurringTransactions.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('recurring_transactions', $maxRecurringId)").execute()
                }

                if (backup.transactions.isNotEmpty()) {
                    backupDao.insertTransactions(backup.transactions.map { it.toEntity() })
                    val maxTransactionId = backup.transactions.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('transactions', $maxTransactionId)").execute()
                }

                if (backup.recurringOccurrences.isNotEmpty()) {
                    backupDao.insertRecurringOccurrences(backup.recurringOccurrences.map { it.toEntity() })
                    val maxOccurrenceId = backup.recurringOccurrences.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('recurring_occurrences', $maxOccurrenceId)").execute()
                }

                if (backup.budgets.isNotEmpty()) {
                    backupDao.insertBudgets(backup.budgets.map { it.toEntity() })
                    val maxBudgetId = backup.budgets.maxOf { it.id }
                    db.compileStatement("INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('budgets', $maxBudgetId)").execute()
                }
            }

            // Restore settings after database transaction succeeds
            backup.settings?.let { backupSettings ->
                try {
                    val appSettings = backupSettings.toDomain()
                    settingsRepository.setThemeMode(appSettings.themeMode)
                    settingsRepository.setPreferredCurrency(appSettings.preferredCurrencyCode)
                    settingsRepository.setWeekStart(appSettings.weekStart)
                    settingsRepository.setDateFormat(appSettings.dateFormat)
                    settingsRepository.setTimeFormat(appSettings.timeFormat)
                    settingsRepository.setShowCurrencyCode(appSettings.showCurrencyCode)
                    settingsRepository.setConfirmBeforeDelete(appSettings.confirmBeforeDelete)
                } catch (e: Exception) {
                    // Report settings failure separately or let it pass since financial DB was successful
                    // As per prompt: "financial database success remains valid, while settings failure is reported separately."
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeChecksum(backup: ExpenseTrackerBackup): String {
        val withoutChecksum = backup.copy(checksum = null)
        val json = adapter.toJson(withoutChecksum)
        return json.sha256()
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun validateBackup(backup: ExpenseTrackerBackup): Result<Unit> {
        if (backup.formatVersion > 1) {
            return Result.failure(IllegalArgumentException("This backup was created by a newer version of ExpenseTracker (Format v${backup.formatVersion}). Please update the app before restoring it."))
        }
        if (backup.databaseSchemaVersion > 2) {
            return Result.failure(IllegalArgumentException("This backup has an incompatible schema version (${backup.databaseSchemaVersion}). Please update the app before restoring it."))
        }

        // 1. Check for blank or invalid currency codes
        val invalidCurrencies = (backup.accounts.map { it.initialBalanceCurrencyCode } +
                backup.transactions.map { it.currencyCode } +
                backup.budgets.map { it.currencyCode } +
                backup.recurringTransactions.map { it.currencyCode })
            .filter { it.trim().length != 3 }
        if (invalidCurrencies.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Backup contains invalid currency codes: ${invalidCurrencies.distinct()}"))
        }

        // 2. Check primary keys are valid (ID > 0)
        if (backup.accounts.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid account IDs."))
        if (backup.categories.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid category IDs."))
        if (backup.transactions.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid transaction IDs."))
        if (backup.budgets.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid budget IDs."))
        if (backup.recurringTransactions.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid recurring transaction IDs."))
        if (backup.recurringOccurrences.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid recurring occurrence IDs."))

        // 3. Check for duplicates in primary keys
        if (backup.accounts.map { it.id }.distinct().size != backup.accounts.size) return Result.failure(IllegalArgumentException("Backup contains duplicate account IDs."))
        if (backup.categories.map { it.id }.distinct().size != backup.categories.size) return Result.failure(IllegalArgumentException("Backup contains duplicate category IDs."))
        if (backup.transactions.map { it.id }.distinct().size != backup.transactions.size) return Result.failure(IllegalArgumentException("Backup contains duplicate transaction IDs."))
        if (backup.budgets.map { it.id }.distinct().size != backup.budgets.size) return Result.failure(IllegalArgumentException("Backup contains duplicate budget IDs."))
        if (backup.recurringTransactions.map { it.id }.distinct().size != backup.recurringTransactions.size) return Result.failure(IllegalArgumentException("Backup contains duplicate recurring transaction IDs."))
        if (backup.recurringOccurrences.map { it.id }.distinct().size != backup.recurringOccurrences.size) return Result.failure(IllegalArgumentException("Backup contains duplicate recurring occurrence IDs."))

        // 4. Check for duplicate recurring occurrences (same ruleId and occurrenceDate)
        val occurrenceKeys = backup.recurringOccurrences.map { it.ruleId to it.occurrenceDate }
        if (occurrenceKeys.distinct().size != occurrenceKeys.size) {
            return Result.failure(IllegalArgumentException("Backup contains duplicate recurring occurrence identities (ruleId + date)."))
        }

        // 5. Check foreign keys
        val accountIds = backup.accounts.map { it.id }.toSet()
        val categoryIds = backup.categories.map { it.id }.toSet()
        val ruleIds = backup.recurringTransactions.map { it.id }.toSet()

        // Accounts fk in transactions
        for (tx in backup.transactions) {
            if (tx.accountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent account ID ${tx.accountId}."))
            }
            if (tx.destinationAccountId != null && tx.destinationAccountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent destination account ID ${tx.destinationAccountId}."))
            }
            if (tx.categoryId != null && tx.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent category ID ${tx.categoryId}."))
            }
            if (tx.recurringRuleId != null && tx.recurringRuleId !in ruleIds) {
                return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent recurring rule ID ${tx.recurringRuleId}."))
            }
        }

        // Category fk in budgets
        for (b in backup.budgets) {
            if (b.categoryId != null && b.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Budget #${b.id} references non-existent category ID ${b.categoryId}."))
            }
        }

        // Account & category fks in recurring transactions
        for (rt in backup.recurringTransactions) {
            if (rt.accountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent account ID ${rt.accountId}."))
            }
            if (rt.destinationAccountId != null && rt.destinationAccountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent destination account ID ${rt.destinationAccountId}."))
            }
            if (rt.categoryId != null && rt.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent category ID ${rt.categoryId}."))
            }
        }

        // Recurring rule fk in recurring occurrences
        for (ro in backup.recurringOccurrences) {
            if (ro.ruleId !in ruleIds) {
                return Result.failure(IllegalArgumentException("Recurring occurrence #${ro.id} references non-existent rule ID ${ro.ruleId}."))
            }
        }

        // 6. Validate enums
        try {
            backup.accounts.forEach { AccountType.valueOf(it.type) }
            backup.categories.forEach { CategoryType.valueOf(it.type) }
            backup.transactions.forEach { TransactionType.valueOf(it.type) }
            backup.budgets.forEach { BudgetPeriodType.valueOf(it.periodType) }
            backup.recurringTransactions.forEach { TransactionType.valueOf(it.type); RecurrenceFrequency.valueOf(it.frequency) }
            backup.recurringOccurrences.forEach { RecurringOccurrenceStatus.valueOf(it.status) }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Backup contains invalid or unrecognized enum values: ${e.message}"))
        }

        // 7. Validate dates & times
        try {
            backup.accounts.forEach { Instant.parse(it.createdAt) }
            backup.transactions.forEach {
                Instant.parse(it.transactionTime)
                Instant.parse(it.createdAt)
                it.recurringOccurrenceDate?.let { dateStr -> LocalDate.parse(dateStr) }
            }
            backup.budgets.forEach {
                LocalDate.parse(it.startDate)
                LocalDate.parse(it.endDate)
            }
            backup.recurringTransactions.forEach {
                LocalDate.parse(it.startDate)
                it.endDate?.let { dateStr -> LocalDate.parse(dateStr) }
                LocalDate.parse(it.nextOccurrence)
            }
            backup.recurringOccurrences.forEach {
                LocalDate.parse(it.occurrenceDate)
                Instant.parse(it.processedAt)
            }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Backup contains malformed date/time strings: ${e.message}"))
        }

        // 8. Verify checksum if present
        if (backup.checksum != null) {
            val computed = computeChecksum(backup)
            if (computed != backup.checksum) {
                return Result.failure(IllegalArgumentException("Backup file checksum verification failed (data is corrupt or has been tampered with)."))
            }
        }

        return Result.success(Unit)
    }
}
