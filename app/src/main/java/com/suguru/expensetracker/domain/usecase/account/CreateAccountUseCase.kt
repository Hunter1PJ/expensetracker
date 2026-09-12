package com.suguru.expensetracker.domain.usecase.account

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.repository.AccountRepository

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
