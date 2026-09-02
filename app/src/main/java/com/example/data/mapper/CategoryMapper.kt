package com.example.data.mapper

import com.example.data.local.entity.CategoryEntity
import com.example.domain.model.Category

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
