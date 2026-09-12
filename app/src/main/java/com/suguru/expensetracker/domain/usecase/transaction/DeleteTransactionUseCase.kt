package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.repository.RecurringOccurrenceRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository

class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val recurringOccurrenceRepository: RecurringOccurrenceRepository? = null
) {
    suspend operator fun invoke(transactionId: Long) {
        if (transactionId <= 0L) {
            throw DomainException.InvalidTransactionId(transactionId)
        }

        val transaction = transactionRepository.getTransactionById(transactionId)
            ?: throw DomainException.TransactionNotFound(transactionId)

        transactionRepository.deleteTransactionById(transactionId)

        if (transaction.recurringRuleId != null && transaction.recurringOccurrenceDate != null) {
            recurringOccurrenceRepository?.markTransactionDeleted(
                ruleId = transaction.recurringRuleId,
                occurrenceDate = transaction.recurringOccurrenceDate
            )
        }
    }
}
