package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CategoryType

/**
 * Room Entity representing a transaction category.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["is_archived"]),
        Index(value = ["type"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean
)
