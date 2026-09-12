package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<List<Transaction>> = transactionRepository.observeAllTransactions()
}
