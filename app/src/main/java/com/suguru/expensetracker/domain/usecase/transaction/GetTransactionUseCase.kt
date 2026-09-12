package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.TransactionRepository

class GetTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long): Transaction? =
        transactionRepository.getTransactionById(transactionId)
}
