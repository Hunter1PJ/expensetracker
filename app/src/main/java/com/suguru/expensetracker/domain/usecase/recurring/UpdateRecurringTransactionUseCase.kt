package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository

class UpdateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    private val validator = RecurringTransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(rule: RecurringTransaction) {
        if (rule.id <= 0L) {
            throw DomainException.InvalidTransactionId(rule.id)
        }

        val existing = recurringTransactionRepository.getRecurringTransactionById(rule.id)
            ?: throw DomainException.TransactionNotFound(rule.id)

        validator.validate(rule)

        // If start date or frequency changed, ensure nextOccurrence is aligned
        val updatedNextOccurrence = if (rule.startDate != existing.startDate || rule.frequency != existing.frequency) {
            if (rule.nextOccurrence < rule.startDate) rule.startDate else rule.nextOccurrence
        } else {
            rule.nextOccurrence
        }

        val updatedRule = rule.copy(nextOccurrence = updatedNextOccurrence)
        recurringTransactionRepository.updateRecurringTransaction(updatedRule)
    }
}
