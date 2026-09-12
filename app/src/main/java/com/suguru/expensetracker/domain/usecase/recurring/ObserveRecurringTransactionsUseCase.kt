package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
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
