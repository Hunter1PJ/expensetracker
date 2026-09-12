package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository

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
