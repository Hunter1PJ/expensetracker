package com.suguru.expensetracker.data.mapper

import com.suguru.expensetracker.data.local.entity.RecurringTransactionEntity
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.RecurringTransaction

fun RecurringTransactionEntity.toDomain(): RecurringTransaction {
    return RecurringTransaction(
        id = id,
        type = type,
        amount = Money(
            amountInMinorUnits = amountInMinorUnits,
            currencyCode = currencyCode
        ),
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        frequency = frequency,
        startDate = startDate,
        endDate = endDate,
        nextOccurrence = nextOccurrence,
        note = note,
        isActive = isActive
    )
}

fun RecurringTransaction.toEntity(): RecurringTransactionEntity {
    return RecurringTransactionEntity(
        id = id,
        type = type,
        amountInMinorUnits = amount.amountInMinorUnits,
        currencyCode = amount.currencyCode,
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        frequency = frequency,
        startDate = startDate,
        endDate = endDate,
        nextOccurrence = nextOccurrence,
        note = note,
        isActive = isActive
    )
}
