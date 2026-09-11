package com.example.domain.usecase.recurring

import com.example.domain.model.RecurringTransaction
import com.example.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveRecurringTransactionsUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository
) {
    operator fun invoke(onlyActive: Boolean = false): Flow<List<RecurringTransaction>> {
        return if (onlyActive) {
            recurringTransactionRepository.observeActiveRecurringTransactions()
        } else {
            recurringTransactionRepository.observeAllRecurringTransactions()
        }
    }
}
