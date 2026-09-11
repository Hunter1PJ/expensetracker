package com.example.domain.usecase.recurring

import com.example.domain.model.RecurringTransaction
import com.example.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow

class GetRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository
) {
    suspend operator fun invoke(id: Long): RecurringTransaction? {
        if (id <= 0L) return null
        return recurringTransactionRepository.getRecurringTransactionById(id)
    }
}
