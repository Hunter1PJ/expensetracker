package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class ObserveAccountUseCase(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(accountId: Long): Flow<Account?> = accountRepository.observeAccountById(accountId)
}
