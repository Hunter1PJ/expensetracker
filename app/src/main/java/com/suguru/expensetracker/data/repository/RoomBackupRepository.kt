package com.suguru.expensetracker.data.repository

import androidx.room.withTransaction
import com.suguru.expensetracker.BuildConfig
import com.suguru.expensetracker.data.account.INTERNAL_DEFAULT_ACCOUNT_KEY
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.BudgetEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.PendingSettingsRecoveryEntity
import com.suguru.expensetracker.data.local.entity.RecurringOccurrenceEntity
import com.suguru.expensetracker.data.local.entity.RecurringTransactionEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.BackupSettings
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringOccurrenceStatus
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.toBackup
import com.suguru.expensetracker.domain.model.toDomain
import com.suguru.expensetracker.domain.model.toEntity
import com.suguru.expensetracker.domain.repository.BackupRepository
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class RoomBackupRepository(
    private val db: ExpenseTrackerDatabase,
    private val settingsRepository: SettingsRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null,
    private val onProvisioningResult: (InternalDefaultAccountProvisioningResult) -> Unit = {},
    private val onSettingsRecoveryPending: () -> Unit = {},
    private val appVersion: String = BuildConfig.VERSION_NAME
) : BackupRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ExpenseTrackerBackup::class.java)
    private val backupSettingsAdapter = moshi.adapter(BackupSettings::class.java)
    private val recoveryMutex = Mutex()

    override fun serializeBackup(backup: ExpenseTrackerBackup): String = adapter.toJson(backup)

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

    suspend fun hasPendingSettingsRecovery(): Boolean = db.pendingSettingsRecoveryDao().getPending() != null

    suspend fun retryPendingSettingsRecovery(): Result<Unit> {
        return try {
            recoveryMutex.withLock {
                restorePendingSettingsLocked()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureProvisionedWithRecovery(): InternalDefaultAccountProvisioningResult? {
        return try {
            recoveryMutex.withLock {
                val hadPendingSettings = db.pendingSettingsRecoveryDao().getPending() != null
                if (hadPendingSettings) {
                    onSettingsRecoveryPending()
                    val recovery = restorePendingSettingsLocked()
                    if (recovery.isFailure) {
                        return@withLock InternalDefaultAccountProvisioningResult.Failure(
                            recovery.exceptionOrNull() ?: IllegalStateException("Settings recovery failed.")
                        )
                    }
                }

                val provisioningResult = internalDefaultAccountProvisioner?.ensureProvisioned()
                if (provisioningResult is InternalDefaultAccountProvisioningResult.Failure) {
                    return@withLock provisioningResult
                }

                if (hadPendingSettings) {
                    db.pendingSettingsRecoveryDao().clear()
                }
                provisioningResult?.let(onProvisioningResult)
                provisioningResult
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InternalDefaultAccountProvisioningResult.Failure(e)
        }
    }

    private suspend fun restorePendingSettingsLocked(): Result<Unit> {
        val pending = db.pendingSettingsRecoveryDao().getPending() ?: return Result.success(Unit)
        val settings = try {
            backupSettingsAdapter.fromJson(pending.settingsJson)?.toDomain()
                ?: return Result.failure(IllegalStateException("Pending settings recovery payload is missing."))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            settingsRepository.restoreSettings(settings)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class RoomSnapshotData(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity>,
        val recurringTransactions: List<RecurringTransactionEntity>,
        val recurringOccurrences: List<RecurringOccurrenceEntity>
    )

    override suspend fun createSnapshot(): ExpenseTrackerBackup {
        val snapshotData = db.withTransaction {
            val backupDao = db.backupDao()
            RoomSnapshotData(
                accounts = backupDao.getAllAccounts(),
                categories = backupDao.getAllCategories(),
                transactions = backupDao.getAllTransactions(),
                budgets = backupDao.getAllBudgets(),
                recurringTransactions = backupDao.getAllRecurringTransactions(),
                recurringOccurrences = backupDao.getAllRecurringOccurrences()
            )
        }
        val settings = settingsRepository.settings.first()

        val backup = ExpenseTrackerBackup(
            formatVersion = CURRENT_FORMAT_VERSION,
            appVersion = appVersion,
            createdAt = Instant.now().toString(),
            databaseSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
            accounts = snapshotData.accounts.map { it.toBackup() },
            categories = snapshotData.categories.map { it.toBackup() },
            transactions = snapshotData.transactions.map { it.toBackup() },
            budgets = snapshotData.budgets.map { it.toBackup() },
            recurringTransactions = snapshotData.recurringTransactions.map { it.toBackup() },
            recurringOccurrences = snapshotData.recurringOccurrences.map { it.toBackup() },
            settings = settings.toBackup(),
            checksum = null
        )

        return backup.copy(checksum = computeChecksum(backup))
    }

    override suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome> {
        val validationResult = validateBackup(backup)
        if (validationResult.isFailure) {
            return validationResult.map { BackupRestoreOutcome.Complete }
        }

        return try {
            recoveryMutex.withLock {
                db.withTransaction {
                    val backupDao = db.backupDao()

                    db.compileStatement("DELETE FROM budgets").execute()
                    db.compileStatement("DELETE FROM recurring_occurrences").execute()
                    db.compileStatement("DELETE FROM transactions").execute()
                    db.compileStatement("DELETE FROM recurring_transactions").execute()
                    db.compileStatement("DELETE FROM categories").execute()
                    db.compileStatement("DELETE FROM accounts").execute()
                    db.compileStatement(
                        "DELETE FROM sqlite_sequence WHERE name IN ('budgets', 'recurring_occurrences', 'transactions', 'recurring_transactions', 'categories', 'accounts')"
                    ).execute()

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

                    backup.settings?.let { backupSettings ->
                        val payload = backupSettingsAdapter.toJson(backupSettings)
                        db.pendingSettingsRecoveryDao().insert(PendingSettingsRecoveryEntity(settingsJson = payload))
                    } ?: db.pendingSettingsRecoveryDao().clear()
                }

                val backupSettings = backup.settings?.toDomain() ?: return@withLock Result.success(BackupRestoreOutcome.Complete)
                onSettingsRecoveryPending()

                return@withLock try {
                    settingsRepository.restoreSettings(backupSettings)
                    val provisioningResult = internalDefaultAccountProvisioner?.ensureProvisioned()
                    when (provisioningResult) {
                        is InternalDefaultAccountProvisioningResult.Failure -> Result.success(BackupRestoreOutcome.SettingsRecoveryPending)
                        else -> {
                            db.pendingSettingsRecoveryDao().clear()
                            provisioningResult?.let(onProvisioningResult)
                            Result.success(BackupRestoreOutcome.Complete)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.success(BackupRestoreOutcome.SettingsRecoveryPending)
                }
            }
        } catch (e: CancellationException) {
            throw e
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
        if (backup.formatVersion !in MIN_SUPPORTED_FORMAT_VERSION..CURRENT_FORMAT_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported backup format version ${backup.formatVersion}. This app supports format versions $MIN_SUPPORTED_FORMAT_VERSION and $CURRENT_FORMAT_VERSION."
                )
            )
        }
        if (backup.databaseSchemaVersion < MIN_SUPPORTED_DATABASE_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported database schema version (${backup.databaseSchemaVersion})."
                )
            )
        }
        if (backup.databaseSchemaVersion > CURRENT_DATABASE_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "This backup has an incompatible database schema version (${backup.databaseSchemaVersion}). Please update the app before restoring it."
                )
            )
        }

        val duplicateInternalKeys = backup.accounts
            .mapNotNull { it.internalKey }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateInternalKeys.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Backup contains duplicate internal account markers: ${duplicateInternalKeys.joinToString()}."))
        }

        val invalidCurrencies = (
            backup.accounts.map { it.initialBalanceCurrencyCode } +
                backup.transactions.map { it.currencyCode } +
                backup.budgets.map { it.currencyCode } +
                backup.recurringTransactions.map { it.currencyCode }
        ).filter { it.trim().length != 3 }
        if (invalidCurrencies.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Backup contains invalid currency codes: ${invalidCurrencies.distinct()}"))
        }

        if (backup.accounts.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid account IDs."))
        if (backup.categories.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid category IDs."))
        if (backup.transactions.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid transaction IDs."))
        if (backup.budgets.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid budget IDs."))
        if (backup.recurringTransactions.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid recurring transaction IDs."))
        if (backup.recurringOccurrences.any { it.id <= 0 }) return Result.failure(IllegalArgumentException("Backup contains invalid recurring occurrence IDs."))

        if (backup.accounts.map { it.id }.distinct().size != backup.accounts.size) return Result.failure(IllegalArgumentException("Backup contains duplicate account IDs."))
        if (backup.categories.map { it.id }.distinct().size != backup.categories.size) return Result.failure(IllegalArgumentException("Backup contains duplicate category IDs."))
        if (backup.transactions.map { it.id }.distinct().size != backup.transactions.size) return Result.failure(IllegalArgumentException("Backup contains duplicate transaction IDs."))
        if (backup.budgets.map { it.id }.distinct().size != backup.budgets.size) return Result.failure(IllegalArgumentException("Backup contains duplicate budget IDs."))
        if (backup.recurringTransactions.map { it.id }.distinct().size != backup.recurringTransactions.size) return Result.failure(IllegalArgumentException("Backup contains duplicate recurring transaction IDs."))
        if (backup.recurringOccurrences.map { it.id }.distinct().size != backup.recurringOccurrences.size) return Result.failure(IllegalArgumentException("Backup contains duplicate recurring occurrence IDs."))

        val occurrenceKeys = backup.recurringOccurrences.map { it.ruleId to it.occurrenceDate }
        if (occurrenceKeys.distinct().size != occurrenceKeys.size) {
            return Result.failure(IllegalArgumentException("Backup contains duplicate recurring occurrence identities (ruleId + date)."))
        }

        val accountIds = backup.accounts.map { it.id }.toSet()
        val categoryIds = backup.categories.map { it.id }.toSet()
        val ruleIds = backup.recurringTransactions.map { it.id }.toSet()

        for (tx in backup.transactions) {
            if (tx.accountId !in accountIds) return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent account ID ${tx.accountId}."))
            if (tx.destinationAccountId != null && tx.destinationAccountId !in accountIds) return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent destination account ID ${tx.destinationAccountId}."))
            if (tx.categoryId != null && tx.categoryId !in categoryIds) return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent category ID ${tx.categoryId}."))
            if (tx.recurringRuleId != null && tx.recurringRuleId !in ruleIds) return Result.failure(IllegalArgumentException("Transaction #${tx.id} references non-existent recurring rule ID ${tx.recurringRuleId}."))
        }

        for (b in backup.budgets) {
            if (b.categoryId != null && b.categoryId !in categoryIds) return Result.failure(IllegalArgumentException("Budget #${b.id} references non-existent category ID ${b.categoryId}."))
        }

        for (rt in backup.recurringTransactions) {
            if (rt.accountId !in accountIds) return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent account ID ${rt.accountId}."))
            if (rt.destinationAccountId != null && rt.destinationAccountId !in accountIds) return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent destination account ID ${rt.destinationAccountId}."))
            if (rt.categoryId != null && rt.categoryId !in categoryIds) return Result.failure(IllegalArgumentException("Recurring transaction #${rt.id} references non-existent category ID ${rt.categoryId}."))
        }

        for (ro in backup.recurringOccurrences) {
            if (ro.ruleId !in ruleIds) return Result.failure(IllegalArgumentException("Recurring occurrence #${ro.id} references non-existent rule ID ${ro.ruleId}."))
        }

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

        if (backup.checksum != null) {
            val computed = computeChecksum(backup)
            if (computed != backup.checksum) {
                return Result.failure(IllegalArgumentException("Backup file checksum verification failed (data is corrupt or has been tampered with)."))
            }
        }

        return Result.success(Unit)
    }

    companion object {
        const val MIN_SUPPORTED_FORMAT_VERSION = 1
        const val CURRENT_FORMAT_VERSION = 2
        const val MIN_SUPPORTED_DATABASE_SCHEMA_VERSION = 1
        const val CURRENT_DATABASE_SCHEMA_VERSION = ExpenseTrackerDatabase.SCHEMA_VERSION
    }
}
