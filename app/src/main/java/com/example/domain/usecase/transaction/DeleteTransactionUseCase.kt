package com.example.domain.usecase.transaction

import com.example.domain.error.DomainException
import com.example.domain.repository.TransactionRepository

class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long) {
        if (transactionId <= 0L) {
            throw DomainException.InvalidTransactionId(transactionId)
        }

        transactionRepository.getTransactionById(transactionId)
            ?: throw DomainException.TransactionNotFound(transactionId)

        transactionRepository.deleteTransactionById(transactionId)
    }
}
