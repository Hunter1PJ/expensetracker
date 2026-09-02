package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveAccountsUseCase(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> = accountRepository.observeActiveAccounts()
}
