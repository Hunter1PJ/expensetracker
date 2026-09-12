package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository

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
