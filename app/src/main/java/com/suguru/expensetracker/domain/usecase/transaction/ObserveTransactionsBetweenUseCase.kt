package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class ObserveTransactionsBetweenUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(startTime: Instant, endTime: Instant): Flow<List<Transaction>> =
        transactionRepository.observeTransactionsBetween(startTime, endTime)
}
