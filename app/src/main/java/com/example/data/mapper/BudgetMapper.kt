package com.example.data.mapper

import com.example.data.local.entity.BudgetEntity
import com.example.domain.model.Budget
import com.example.domain.model.Money

fun BudgetEntity.toDomain(): Budget {
    return Budget(
        id = id,
        categoryId = categoryId,
        limitAmount = Money(
            amountInMinorUnits = limitAmountInMinorUnits,
            currencyCode = currencyCode
        ),
        periodType = periodType,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive
    )
}

fun Budget.toEntity(): BudgetEntity {
    return BudgetEntity(
        id = id,
        categoryId = categoryId,
        limitAmountInMinorUnits = limitAmount.amountInMinorUnits,
        currencyCode = limitAmount.currencyCode,
        periodType = periodType,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive
    )
}
