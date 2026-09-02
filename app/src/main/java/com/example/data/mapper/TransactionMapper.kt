package com.example.data.mapper

import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.Money
import com.example.domain.model.Transaction

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
        createdAt = createdAt
    )
}
