package com.suguru.expensetracker.domain.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.security.MessageDigest

/**
 * Canonical representation of backup content used exclusively to compute a deterministic
 * content fingerprint.
 *
 * Included content groups (7):
 * - [accounts]
 * - [categories]
 * - [transactions]
 * - [budgets]
 * - [recurringTransactions]
 * - [recurringOccurrences]
 * - [settings]
 *
 * Excluded envelope, security, and device metadata:
 * - `ExpenseTrackerBackup.formatVersion`
 * - `ExpenseTrackerBackup.appVersion`
 * - `ExpenseTrackerBackup.createdAt`
 * - `ExpenseTrackerBackup.databaseSchemaVersion`
 * - `ExpenseTrackerBackup.checksum`
 * - `SettingsRepository.lastBackupAt` / `lastBackupFileName`
 * - Gemini BYOK API keys (stored separately in Android Keystore)
 * - Any future cloud or device metadata
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupContent(
    val accounts: List<CanonicalBackupAccount>,
    val categories: List<CanonicalBackupCategory>,
    val transactions: List<CanonicalBackupTransaction>,
    val budgets: List<CanonicalBackupBudget>,
    val recurringTransactions: List<CanonicalBackupRecurringTransaction>,
    val recurringOccurrences: List<CanonicalBackupRecurringOccurrence>,
    val settings: CanonicalBackupSettings?
)

/**
 * Canonical account record; every column persisted in Room's `accounts` table is included
 * so any meaningful account change alters the content fingerprint.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupAccount(
    val id: Long,
    val name: String,
    val type: String,
    val initialBalanceAmountInMinorUnits: Long,
    val initialBalanceCurrencyCode: String,
    val iconName: String,
    val colorHex: String,
    val isArchived: Boolean,
    val createdAt: String,
    val internalKey: String?
)

/**
 * Canonical category record; every column persisted in Room's `categories` table is included.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupCategory(
    val id: Long,
    val name: String,
    val type: String,
    val iconName: String,
    val colorHex: String,
    val isSystem: Boolean,
    val isArchived: Boolean
)

/**
 * Canonical transaction record; every column persisted in Room's `transactions` table is included.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupTransaction(
    val id: Long,
    val type: String,
    val amountInMinorUnits: Long,
    val currencyCode: String,
    val accountId: Long,
    val destinationAccountId: Long?,
    val categoryId: Long?,
    val transactionTime: String,
    val note: String?,
    val recurringRuleId: Long?,
    val recurringOccurrenceDate: String?,
    val createdAt: String
)

/**
 * Canonical budget record; every column persisted in Room's `budgets` table is included.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupBudget(
    val id: Long,
    val categoryId: Long?,
    val limitAmountInMinorUnits: Long,
    val currencyCode: String,
    val periodType: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean
)

/**
 * Canonical recurring transaction rule; every column persisted in Room's
 * `recurring_transactions` table is included.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupRecurringTransaction(
    val id: Long,
    val type: String,
    val amountInMinorUnits: Long,
    val currencyCode: String,
    val accountId: Long,
    val destinationAccountId: Long?,
    val categoryId: Long?,
    val frequency: String,
    val startDate: String,
    val endDate: String?,
    val nextOccurrence: String,
    val note: String?,
    val isActive: Boolean
)

/**
 * Canonical recurring occurrence record; every column persisted in Room's
 * `recurring_occurrences` table is included.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupRecurringOccurrence(
    val id: Long,
    val ruleId: Long,
    val occurrenceDate: String,
    val generatedTransactionId: Long?,
    val status: String,
    val processedAt: String
)

/**
 * Canonical user settings record; includes only portable user preferences from `BackupSettings`
 * and excludes secrets (e.g., BYOK keys) and device-local backup history metadata.
 */
@JsonClass(generateAdapter = true)
data class CanonicalBackupSettings(
    val themeMode: String,
    val preferredCurrencyCode: String?,
    val weekStart: String,
    val dateFormat: String,
    val timeFormat: String,
    val showCurrencyCode: Boolean,
    val confirmBeforeDelete: Boolean
)

/**
 * Computes a deterministic SHA-256 hex fingerprint over the canonical financial content of an
 * [ExpenseTrackerBackup], independent of backup envelope metadata and input list ordering.
 *
 * This is intentionally separate from [com.suguru.expensetracker.data.repository.RoomBackupRepository.computeChecksum],
 * which hashes the full backup payload including envelope metadata for file integrity validation.
 */
object BackupContentFingerprint {

    private val moshi: Moshi = Moshi.Builder().build()

    private val canonicalAdapter = moshi
        .adapter(CanonicalBackupContent::class.java)
        .serializeNulls()

    fun compute(backup: ExpenseTrackerBackup): String {
        return compute(toCanonicalContent(backup))
    }

    fun compute(content: CanonicalBackupContent): String {
        val json = canonicalJson(content)
        return sha256Hex(json)
    }

    fun toCanonicalContent(backup: ExpenseTrackerBackup): CanonicalBackupContent {
        return CanonicalBackupContent(
            accounts = backup.accounts.sortedBy { it.id }.map { it.toCanonical() },
            categories = backup.categories.sortedBy { it.id }.map { it.toCanonical() },
            transactions = backup.transactions.sortedBy { it.id }.map { it.toCanonical() },
            budgets = backup.budgets.sortedBy { it.id }.map { it.toCanonical() },
            recurringTransactions = backup.recurringTransactions.sortedBy { it.id }.map { it.toCanonical() },
            recurringOccurrences = backup.recurringOccurrences.sortedBy { it.id }.map { it.toCanonical() },
            settings = backup.settings?.toCanonical()
        )
    }

    internal fun canonicalJson(backup: ExpenseTrackerBackup): String {
        return canonicalJson(toCanonicalContent(backup))
    }

    internal fun canonicalJson(content: CanonicalBackupContent): String {
        val sortedContent = CanonicalBackupContent(
            accounts = content.accounts.sortedBy { it.id },
            categories = content.categories.sortedBy { it.id },
            transactions = content.transactions.sortedBy { it.id },
            budgets = content.budgets.sortedBy { it.id },
            recurringTransactions = content.recurringTransactions.sortedBy { it.id },
            recurringOccurrences = content.recurringOccurrences.sortedBy { it.id },
            settings = content.settings
        )
        return canonicalAdapter.toJson(sortedContent)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (index in digest.indices) {
            val value = digest[index].toInt() and 0xFF
            hexChars[index * 2] = digits[value ushr 4]
            hexChars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(hexChars)
    }
}

private fun BackupAccount.toCanonical() = CanonicalBackupAccount(
    id = id,
    name = name,
    type = type,
    initialBalanceAmountInMinorUnits = initialBalanceAmountInMinorUnits,
    initialBalanceCurrencyCode = initialBalanceCurrencyCode,
    iconName = iconName,
    colorHex = colorHex,
    isArchived = isArchived,
    createdAt = createdAt,
    internalKey = internalKey
)

private fun BackupCategory.toCanonical() = CanonicalBackupCategory(
    id = id,
    name = name,
    type = type,
    iconName = iconName,
    colorHex = colorHex,
    isSystem = isSystem,
    isArchived = isArchived
)

private fun BackupTransaction.toCanonical() = CanonicalBackupTransaction(
    id = id,
    type = type,
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    transactionTime = transactionTime,
    note = note,
    recurringRuleId = recurringRuleId,
    recurringOccurrenceDate = recurringOccurrenceDate,
    createdAt = createdAt
)

private fun BackupBudget.toCanonical() = CanonicalBackupBudget(
    id = id,
    categoryId = categoryId,
    limitAmountInMinorUnits = limitAmountInMinorUnits,
    currencyCode = currencyCode,
    periodType = periodType,
    startDate = startDate,
    endDate = endDate,
    isActive = isActive
)

private fun BackupRecurringTransaction.toCanonical() = CanonicalBackupRecurringTransaction(
    id = id,
    type = type,
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    frequency = frequency,
    startDate = startDate,
    endDate = endDate,
    nextOccurrence = nextOccurrence,
    note = note,
    isActive = isActive
)

private fun BackupRecurringOccurrence.toCanonical() = CanonicalBackupRecurringOccurrence(
    id = id,
    ruleId = ruleId,
    occurrenceDate = occurrenceDate,
    generatedTransactionId = generatedTransactionId,
    status = status,
    processedAt = processedAt
)

private fun BackupSettings.toCanonical() = CanonicalBackupSettings(
    themeMode = themeMode,
    preferredCurrencyCode = preferredCurrencyCode,
    weekStart = weekStart,
    dateFormat = dateFormat,
    timeFormat = timeFormat,
    showCurrencyCode = showCurrencyCode,
    confirmBeforeDelete = confirmBeforeDelete
)
