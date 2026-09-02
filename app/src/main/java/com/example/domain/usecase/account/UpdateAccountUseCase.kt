package com.example.domain.usecase.account

import com.example.domain.error.DomainException
import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

class UpdateAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account) {
        if (account.id <= 0L) {
            throw DomainException.InvalidAccount("Account ID must be greater than 0 for update")
        }
        if (account.name.isBlank()) {
            throw DomainException.InvalidAccount("Account name cannot be blank")
        }
        accountRepository.getAccountById(account.id)
            ?: throw DomainException.AccountNotFound(account.id)

        accountRepository.updateAccount(account)
    }
}
