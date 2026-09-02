package com.example.domain.usecase.transaction

import com.example.domain.error.DomainException
import com.example.domain.model.Transaction
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository

class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    private val validator = TransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(transaction: Transaction) {
        if (transaction.id <= 0L) {
            throw DomainException.InvalidTransactionId(transaction.id)
        }

        transactionRepository.getTransactionById(transaction.id)
            ?: throw DomainException.TransactionNotFound(transaction.id)

        validator.validate(transaction)

        transactionRepository.updateTransaction(transaction)
    }
}
