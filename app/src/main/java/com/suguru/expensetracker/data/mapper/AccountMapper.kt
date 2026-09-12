package com.suguru.expensetracker.data.mapper

import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.Money

fun AccountEntity.toDomain(): Account {
    return Account(
        id = id,
        name = name,
        type = type,
        initialBalance = Money(
            amountInMinorUnits = initialBalanceAmountInMinorUnits,
            currencyCode = initialBalanceCurrencyCode
        ),
        iconName = iconName,
        colorHex = colorHex,
        isArchived = isArchived,
        createdAt = createdAt
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        name = name,
        type = type,
        initialBalanceAmountInMinorUnits = initialBalance.amountInMinorUnits,
        initialBalanceCurrencyCode = initialBalance.currencyCode,
        iconName = iconName,
        colorHex = colorHex,
        isArchived = isArchived,
        createdAt = createdAt
    )
}
