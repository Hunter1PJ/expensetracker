package com.suguru.expensetracker.data.mapper

import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.domain.model.Category

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        type = type,
        iconName = iconName,
        colorHex = colorHex,
        isSystem = isSystem,
        isArchived = isArchived
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        type = type,
        iconName = iconName,
        colorHex = colorHex,
        isSystem = isSystem,
        isArchived = isArchived
    )
}
