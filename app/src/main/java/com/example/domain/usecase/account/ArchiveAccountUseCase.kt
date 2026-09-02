package com.example.domain.usecase.account

import com.example.domain.error.DomainException
import com.example.domain.repository.AccountRepository

class ArchiveAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(accountId: Long) {
        if (accountId <= 0L) {
            throw DomainException.InvalidAccount("Account ID must be greater than 0")
        }
        accountRepository.getAccountById(accountId)
            ?: throw DomainException.AccountNotFound(accountId)

        accountRepository.archiveAccount(accountId)
    }
}
