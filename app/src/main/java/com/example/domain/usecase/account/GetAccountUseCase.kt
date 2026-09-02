package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

class GetAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(accountId: Long): Account? = accountRepository.getAccountById(accountId)
}
