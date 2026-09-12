package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsByAccountUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(accountId: Long): Flow<List<Transaction>> =
        transactionRepository.observeTransactionsByAccount(accountId)
}
