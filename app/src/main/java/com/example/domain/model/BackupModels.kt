package com.example.domain.model

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.RecurringOccurrenceEntity
import com.example.data.local.entity.RecurringTransactionEntity
import com.example.data.local.entity.TransactionEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupAccount(
    val id: Long,
    val name: String,
    val type: String,
    val initialBalanceAmountInMinorUnits: Long,
    val initialBalanceCurrencyCode: String,
    val iconName: String,
    val colorHex: String,
    val isArchived: Boolean,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class BackupCategory(
    val id: Long,
    val name: String,
    val type: String,
    val iconName: String,
    val colorHex: String,
    val isSystem: Boolean,
    val isArchived: Boolean
)

@JsonClass(generateAdapter = true)
data class BackupTransaction(
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

@JsonClass(generateAdapter = true)
data class BackupBudget(
    val id: Long,
    val categoryId: Long?,
    val limitAmountInMinorUnits: Long,
    val currencyCode: String,
    val periodType: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean
)

@JsonClass(generateAdapter = true)
data class BackupRecurringTransaction(
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

@JsonClass(generateAdapter = true)
data class BackupRecurringOccurrence(
    val id: Long,
    val ruleId: Long,
    val occurrenceDate: String,
    val generatedTransactionId: Long?,
    val status: String,
    val processedAt: String
)

@JsonClass(generateAdapter = true)
data class BackupSettings(
    val themeMode: String,
    val preferredCurrencyCode: String?,
    val weekStart: String,
    val dateFormat: String,
    val timeFormat: String,
    val showCurrencyCode: Boolean,
    val confirmBeforeDelete: Boolean
)

@JsonClass(generateAdapter = true)
data class ExpenseTrackerBackup(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val databaseSchemaVersion: Int,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val budgets: List<BackupBudget>,
    val recurringTransactions: List<BackupRecurringTransaction>,
    val recurringOccurrences: List<BackupRecurringOccurrence>,
    val settings: BackupSettings?,
    val checksum: String? = null
)

// Extension functions for mapping
fun AccountEntity.toBackup() = BackupAccount(
    id = id,
    name = name,
    type = type.name,
    initialBalanceAmountInMinorUnits = initialBalanceAmountInMinorUnits,
    initialBalanceCurrencyCode = initialBalanceCurrencyCode,
    iconName = iconName,
    colorHex = colorHex,
    isArchived = isArchived,
    createdAt = createdAt.toString()
)

fun BackupAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    initialBalanceAmountInMinorUnits = initialBalanceAmountInMinorUnits,
    initialBalanceCurrencyCode = initialBalanceCurrencyCode,
    iconName = iconName,
    colorHex = colorHex,
    isArchived = isArchived,
    createdAt = java.time.Instant.parse(createdAt)
)

fun CategoryEntity.toBackup() = BackupCategory(
    id = id,
    name = name,
    type = type.name,
    iconName = iconName,
    colorHex = colorHex,
    isSystem = isSystem,
    isArchived = isArchived
)

fun BackupCategory.toEntity() = CategoryEntity(
    id = id,
    name = name,
    type = CategoryType.valueOf(type),
    iconName = iconName,
    colorHex = colorHex,
    isSystem = isSystem,
    isArchived = isArchived
)

fun TransactionEntity.toBackup() = BackupTransaction(
    id = id,
    type = type.name,
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    transactionTime = transactionTime.toString(),
    note = note,
    recurringRuleId = recurringRuleId,
    recurringOccurrenceDate = recurringOccurrenceDate?.toString(),
    createdAt = createdAt.toString()
)

fun BackupTransaction.toEntity() = TransactionEntity(
    id = id,
    type = TransactionType.valueOf(type),
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    transactionTime = java.time.Instant.parse(transactionTime),
    note = note,
    recurringRuleId = recurringRuleId,
    recurringOccurrenceDate = recurringOccurrenceDate?.let { java.time.LocalDate.parse(it) },
    createdAt = java.time.Instant.parse(createdAt)
)

fun BudgetEntity.toBackup() = BackupBudget(
    id = id,
    categoryId = categoryId,
    limitAmountInMinorUnits = limitAmountInMinorUnits,
    currencyCode = currencyCode,
    periodType = periodType.name,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    isActive = isActive
)

fun BackupBudget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    limitAmountInMinorUnits = limitAmountInMinorUnits,
    currencyCode = currencyCode,
    periodType = BudgetPeriodType.valueOf(periodType),
    startDate = java.time.LocalDate.parse(startDate),
    endDate = java.time.LocalDate.parse(endDate),
    isActive = isActive
)

fun RecurringTransactionEntity.toBackup() = BackupRecurringTransaction(
    id = id,
    type = type.name,
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    frequency = frequency.name,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    nextOccurrence = nextOccurrence.toString(),
    note = note,
    isActive = isActive
)

fun BackupRecurringTransaction.toEntity() = RecurringTransactionEntity(
    id = id,
    type = TransactionType.valueOf(type),
    amountInMinorUnits = amountInMinorUnits,
    currencyCode = currencyCode,
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    frequency = RecurrenceFrequency.valueOf(frequency),
    startDate = java.time.LocalDate.parse(startDate),
    endDate = endDate?.let { java.time.LocalDate.parse(it) },
    nextOccurrence = java.time.LocalDate.parse(nextOccurrence),
    note = note,
    isActive = isActive
)

fun RecurringOccurrenceEntity.toBackup() = BackupRecurringOccurrence(
    id = id,
    ruleId = ruleId,
    occurrenceDate = occurrenceDate.toString(),
    generatedTransactionId = generatedTransactionId,
    status = status.name,
    processedAt = processedAt.toString()
)

fun RecurringOccurrenceEntity.toEntity() = RecurringOccurrenceEntity(
    id = id,
    ruleId = ruleId,
    occurrenceDate = java.time.LocalDate.parse(occurrenceDate.toString()),
    generatedTransactionId = generatedTransactionId,
    status = status,
    processedAt = processedAt
)

fun BackupRecurringOccurrence.toEntity() = RecurringOccurrenceEntity(
    id = id,
    ruleId = ruleId,
    occurrenceDate = java.time.LocalDate.parse(occurrenceDate),
    generatedTransactionId = generatedTransactionId,
    status = RecurringOccurrenceStatus.valueOf(status),
    processedAt = java.time.Instant.parse(processedAt)
)

fun AppSettings.toBackup() = BackupSettings(
    themeMode = themeMode.name,
    preferredCurrencyCode = preferredCurrencyCode,
    weekStart = weekStart.name,
    dateFormat = dateFormat.name,
    timeFormat = timeFormat.name,
    showCurrencyCode = showCurrencyCode,
    confirmBeforeDelete = confirmBeforeDelete
)

fun BackupSettings.toDomain() = AppSettings(
    themeMode = try { ThemeMode.valueOf(themeMode) } catch (e: Exception) { ThemeMode.SYSTEM },
    preferredCurrencyCode = preferredCurrencyCode,
    weekStart = try { WeekStart.valueOf(weekStart) } catch (e: Exception) { WeekStart.MONDAY },
    dateFormat = try { DateFormatPreference.valueOf(dateFormat) } catch (e: Exception) { DateFormatPreference.SYSTEM_DEFAULT },
    timeFormat = try { TimeFormatPreference.valueOf(timeFormat) } catch (e: Exception) { TimeFormatPreference.SYSTEM_DEFAULT },
    showCurrencyCode = showCurrencyCode,
    confirmBeforeDelete = confirmBeforeDelete
)
