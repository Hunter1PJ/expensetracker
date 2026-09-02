package com.example.domain.usecase.account

import com.example.domain.error.DomainException
import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

class CreateAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account): Long {
        if (account.name.isBlank()) {
            throw DomainException.InvalidAccount("Account name cannot be blank")
        }
        if (account.initialBalance.currencyCode.isBlank()) {
            throw DomainException.InvalidCurrency("Initial balance currency code cannot be blank")
        }
        if (account.id != 0L) {
            throw DomainException.InvalidAccount("New account must have an ID of 0L")
        }
        return accountRepository.insertAccount(account)
    }
}
