package com.suguru.expensetracker.domain.usecase.account

import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveAccountsUseCase(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> = accountRepository.observeActiveAccounts()
}
