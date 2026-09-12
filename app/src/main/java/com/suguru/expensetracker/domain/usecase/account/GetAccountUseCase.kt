package com.suguru.expensetracker.domain.usecase.account

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.repository.AccountRepository

class GetAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(accountId: Long): Account? = accountRepository.getAccountById(accountId)
}
