package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.TransactionType
import java.time.LocalDate

/**
 * Room Entity representing a scheduled recurring transaction rule.
 *
 * Foreign Keys use RESTRICT to prevent accidental deletion of referenced accounts/categories.
 */
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["destination_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["destination_account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["next_occurrence"]),
        Index(value = ["is_active"])
    ]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor_units")
    val amountInMinorUnits: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "destination_account_id")
    val destinationAccountId: Long?,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    val frequency: RecurrenceFrequency,
    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,
    @ColumnInfo(name = "end_date")
    val endDate: LocalDate?,
    @ColumnInfo(name = "next_occurrence")
    val nextOccurrence: LocalDate,
    val note: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
