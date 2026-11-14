package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.first

class UpdateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null
) {
    private val validator = RecurringTransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(rule: RecurringTransaction) {
        if (rule.id <= 0L) {
            throw DomainException.InvalidTransactionId(rule.id)
        }

        ensureInternalDefaultAccountIsReady()
        
        // CRITICAL FIX: Load persisted record FIRST to preserve original account ID
        val existingRule = recurringTransactionRepository.getRecurringTransactionById(rule.id)
            ?: throw DomainException.TransactionNotFound(rule.id)

        if (existingRule.type == com.suguru.expensetracker.domain.model.TransactionType.TRANSFER ||
            rule.type == com.suguru.expensetracker.domain.model.TransactionType.TRANSFER
        ) {
            throw DomainException.InvalidTransfer(
                "Historical recurring transfer rules are read-only and cannot be edited, converted, or reactivated."
            )
        }

        if (rule.type != existingRule.type) {
            throw DomainException.InvalidTransfer(
                "Recurring transaction type cannot be changed after creation."
            )
        }

        // Preserve the original persisted accountId - DO NOT accept different ID from UI
        // Only update allowed fields: amount, categoryId, startDate, endDate, nextOccurrence, note, isActive
        val resolvedRule = existingRule.copy(
            amount = rule.amount,
            categoryId = rule.categoryId,
            startDate = rule.startDate,
            endDate = rule.endDate,
            nextOccurrence = rule.nextOccurrence,
            note = rule.note,
            isActive = rule.isActive
            // accountId is PRESERVED from existing record, not replaced
        )

        validator.validate(resolvedRule)

        // If start date or frequency changed, ensure nextOccurrence is aligned
        val updatedNextOccurrence = if (resolvedRule.startDate != existingRule.startDate || resolvedRule.frequency != existingRule.frequency) {
            if (resolvedRule.nextOccurrence < resolvedRule.startDate) resolvedRule.startDate else resolvedRule.nextOccurrence
        } else {
            resolvedRule.nextOccurrence
        }

        val updatedRule = resolvedRule.copy(nextOccurrence = updatedNextOccurrence)
        recurringTransactionRepository.updateRecurringTransaction(updatedRule)
    }

    private suspend fun ensureInternalDefaultAccountIsReady() {
        val result = internalDefaultAccountProvisioner?.ensureProvisioned() ?: return
        when (result) {
            is InternalDefaultAccountProvisioningResult.Ready -> Unit
            is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                throw DomainException.InvalidAccount("Internal default account requires a preferred currency selection before recurring transactions can be updated.")
            }
            is InternalDefaultAccountProvisioningResult.Failure -> throw result.error
        }
    }
}
