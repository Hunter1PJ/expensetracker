package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsByCategoryUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(categoryId: Long): Flow<List<Transaction>> =
        transactionRepository.observeTransactionsByCategory(categoryId)
}
