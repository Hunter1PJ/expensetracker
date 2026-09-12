package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow

class GetRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository
) {
    suspend operator fun invoke(id: Long): RecurringTransaction? {
        if (id <= 0L) return null
        return recurringTransactionRepository.getRecurringTransactionById(id)
    }
}
