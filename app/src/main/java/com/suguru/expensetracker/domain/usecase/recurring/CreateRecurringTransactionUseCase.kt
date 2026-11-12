package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository

class CreateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    private val validator = RecurringTransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(rule: RecurringTransaction): Long {
        if (rule.id != 0L) {
            throw DomainException.InvalidTransactionId(rule.id)
        }

        validator.validate(rule)

        val preparedRule = rule.copy(
            nextOccurrence = rule.startDate,
            isActive = true
        )

        return recurringTransactionRepository.insertRecurringTransaction(preparedRule)
    }
}
