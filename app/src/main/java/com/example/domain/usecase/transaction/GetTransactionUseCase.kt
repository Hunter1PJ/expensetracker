package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository

class GetTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long): Transaction? =
        transactionRepository.getTransactionById(transactionId)
}
