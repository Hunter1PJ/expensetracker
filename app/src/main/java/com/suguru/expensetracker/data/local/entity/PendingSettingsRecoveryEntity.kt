package com.suguru.expensetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_settings_restore")
data class PendingSettingsRecoveryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = 1L,
    @ColumnInfo(name = "settings_json")
    val settingsJson: String
)
