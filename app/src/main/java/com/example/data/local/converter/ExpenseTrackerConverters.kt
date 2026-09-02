package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.domain.model.AccountType
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.CategoryType
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate

/**
 * Room Type Converters for ExpenseTracker persistence.
 *
 * Converts Java 8 Time types (Instant, LocalDate) and domain Enums to SQLite compatible primitives.
 */
class ExpenseTrackerConverters {

    // --- Instant (Epoch Milliseconds) ---
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? = epochMilli?.let { Instant.ofEpochMilli(it) }

    // --- LocalDate (ISO-8601 String) ---
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? = dateString?.let { LocalDate.parse(it) }

    // --- AccountType (Enum String) ---
    @TypeConverter
    fun fromAccountType(type: AccountType?): String? = type?.name

    @TypeConverter
    fun toAccountType(name: String?): AccountType? = name?.let {
        try {
            AccountType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            AccountType.OTHER
        }
    }

    // --- TransactionType (Enum String) ---
    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String? = type?.name

    @TypeConverter
    fun toTransactionType(name: String?): TransactionType? = name?.let {
        try {
            TransactionType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            TransactionType.EXPENSE
        }
    }

    // --- CategoryType (Enum String) ---
    @TypeConverter
    fun fromCategoryType(type: CategoryType?): String? = type?.name

    @TypeConverter
    fun toCategoryType(name: String?): CategoryType? = name?.let {
        try {
            CategoryType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            CategoryType.EXPENSE
        }
    }

    // --- BudgetPeriodType (Enum String) ---
    @TypeConverter
    fun fromBudgetPeriodType(type: BudgetPeriodType?): String? = type?.name

    @TypeConverter
    fun toBudgetPeriodType(name: String?): BudgetPeriodType? = name?.let {
        try {
            BudgetPeriodType.valueOf(it)
        } catch (_: IllegalArgumentException) {
            BudgetPeriodType.MONTHLY
        }
    }

    // --- RecurrenceFrequency (Enum String) ---
    @TypeConverter
    fun fromRecurrenceFrequency(frequency: RecurrenceFrequency?): String? = frequency?.name

    @TypeConverter
    fun toRecurrenceFrequency(name: String?): RecurrenceFrequency? = name?.let {
        try {
            RecurrenceFrequency.valueOf(it)
        } catch (_: IllegalArgumentException) {
            RecurrenceFrequency.MONTHLY
        }
    }
}
