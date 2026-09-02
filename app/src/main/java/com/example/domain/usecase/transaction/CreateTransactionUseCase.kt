package com.example.domain.usecase.transaction

import com.example.domain.error.DomainException
import com.example.domain.model.Transaction
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository

class CreateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    private val validator = TransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(transaction: Transaction): Long {
        if (transaction.id != 0L) {
            throw DomainException.InvalidTransactionId(transaction.id)
        }

        validator.validate(transaction)

        return transactionRepository.insertTransaction(transaction)
    }
}
