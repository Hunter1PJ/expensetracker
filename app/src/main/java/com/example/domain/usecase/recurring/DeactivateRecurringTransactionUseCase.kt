package com.example.domain.usecase.recurring

import com.example.domain.error.DomainException
import com.example.domain.repository.RecurringTransactionRepository

class DeactivateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository
) {
    suspend operator fun invoke(id: Long) {
        if (id <= 0L) {
            throw DomainException.InvalidTransactionId(id)
        }

        recurringTransactionRepository.getRecurringTransactionById(id)
            ?: throw DomainException.TransactionNotFound(id)

        recurringTransactionRepository.deactivateRecurringTransaction(id)
    }
}
