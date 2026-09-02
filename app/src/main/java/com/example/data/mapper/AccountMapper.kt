package com.example.data.mapper

import com.example.data.local.entity.AccountEntity
import com.example.domain.model.Account
import com.example.domain.model.Money

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
