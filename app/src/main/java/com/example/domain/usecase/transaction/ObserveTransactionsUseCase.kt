package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<List<Transaction>> = transactionRepository.observeAllTransactions()
}
