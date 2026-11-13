package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.first

class CreateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null
) {
    private val validator = RecurringTransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(rule: RecurringTransaction): Long {
        if (rule.id != 0L) {
            throw DomainException.InvalidTransactionId(rule.id)
        }

        ensureInternalDefaultAccountIsReady()
        
        // CRITICAL: For ALL new recurring Expense/Income rules, enforce internal default account
        // Never accept account ID from UI state, AI parsing, or incoming model for new records
        val resolvedAccountId = resolveInternalDefaultAccountIdForNewRecurringRule(rule.type)
        
        val resolvedRule = rule.copy(
            accountId = resolvedAccountId,
            destinationAccountId = if (rule.destinationAccountId != null && rule.destinationAccountId > 0L) rule.destinationAccountId else null
        )
        validator.validate(resolvedRule)

        val preparedRule = resolvedRule.copy(
            nextOccurrence = resolvedRule.startDate,
            isActive = true
        )

        return recurringTransactionRepository.insertRecurringTransaction(preparedRule)
    }

    /**
     * Resolves the internal default account for ALL new recurring Expense/Income rules.
     * TRANSFER recurring rules are not supported for creation (per product decision).
     */
    private suspend fun resolveInternalDefaultAccountIdForNewRecurringRule(transactionType: TransactionType): Long {
        // Only allow EXPENSE and INCOME types for new recurring transactions
        when (transactionType) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                val accounts = accountRepository.observeActiveAccounts().first()
                return accounts.firstOrNull { it.internalKey == "default" }?.id
                    ?: throw DomainException.AccountNotFound(0L) // Should never happen if provisioning works
            }
            TransactionType.TRANSFER -> {
                // TRANSFER recurring creation is not supported - will be handled by UI removal
                // For now, reject with appropriate error
                throw DomainException.InvalidTransfer("New recurring transfer rules are not supported. Use internal default account for Expense/Income only.")
            }
        }
    }

    private suspend fun ensureInternalDefaultAccountIsReady() {
        val result = internalDefaultAccountProvisioner?.ensureProvisioned() ?: return
        when (result) {
            is InternalDefaultAccountProvisioningResult.Ready -> Unit
            is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                throw DomainException.InvalidAccount("Internal default account requires a preferred currency selection before recurring transactions can be created.")
            }
            is InternalDefaultAccountProvisioningResult.Failure -> throw result.error
        }
    }
}
