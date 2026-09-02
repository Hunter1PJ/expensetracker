package com.example.domain.model

/**
 * Domain model representing a transaction category.
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    val iconName: String,
    val colorHex: String,
    val isSystem: Boolean = false,
    val isArchived: Boolean = false
)
