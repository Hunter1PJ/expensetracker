package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountType
import java.time.Instant

/**
 * Room Entity representing a financial account.
 *
 * Notice: Current balance is not stored directly here to maintain mathematical integrity;
 * balance is derived dynamically from initialBalance + ledger transactions.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["is_archived"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    @ColumnInfo(name = "initial_balance_minor_units")
    val initialBalanceAmountInMinorUnits: Long,
    @ColumnInfo(name = "initial_balance_currency_code")
    val initialBalanceCurrencyCode: String,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
