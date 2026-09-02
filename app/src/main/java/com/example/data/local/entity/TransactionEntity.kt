package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.TransactionType
import java.time.Instant

/**
 * Room Entity representing a single ledger transaction (Expense, Income, or Transfer).
 *
 * For Transfers:
 * - [accountId] is the source account (outflow).
 * - [destinationAccountId] is the target account (inflow).
 * - Only ONE transaction row is stored per transfer.
 *
 * Foreign Keys use RESTRICT to ensure historical financial records cannot be broken by deleting accounts/categories.
 */
@Entity(
    tableName = "transactions",
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
        ),
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_rule_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["destination_account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["transaction_time"]),
        Index(value = ["recurring_rule_id"]),
        Index(value = ["account_id", "transaction_time"])
    ]
)
data class TransactionEntity(
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
    @ColumnInfo(name = "transaction_time")
    val transactionTime: Instant,
    val note: String?,
    @ColumnInfo(name = "recurring_rule_id")
    val recurringRuleId: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
