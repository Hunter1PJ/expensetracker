package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsByAccountUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(accountId: Long): Flow<List<Transaction>> =
        transactionRepository.observeTransactionsByAccount(accountId)
}
