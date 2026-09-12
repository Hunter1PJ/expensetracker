package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository

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
