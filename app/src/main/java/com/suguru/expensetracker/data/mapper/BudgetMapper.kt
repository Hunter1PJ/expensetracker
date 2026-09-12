package com.suguru.expensetracker.data.mapper

import com.suguru.expensetracker.data.local.entity.BudgetEntity
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.Money

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
