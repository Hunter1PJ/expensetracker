package com.suguru.expensetracker.data.mapper

import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction

fun TransactionEntity.toDomain(): Transaction {
    return Transaction(
        id = id,
        type = type,
        amount = Money(
            amountInMinorUnits = amountInMinorUnits,
            currencyCode = currencyCode
        ),
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        transactionTime = transactionTime,
        note = note,
        recurringRuleId = recurringRuleId,
        recurringOccurrenceDate = recurringOccurrenceDate,
        createdAt = createdAt
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        type = type,
        amountInMinorUnits = amount.amountInMinorUnits,
        currencyCode = amount.currencyCode,
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        transactionTime = transactionTime,
        note = note,
        recurringRuleId = recurringRuleId,
        recurringOccurrenceDate = recurringOccurrenceDate,
        createdAt = createdAt
    )
}
