package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class CreateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null
) {
    private val validator = TransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(transaction: Transaction): Long {
        if (transaction.id != 0L) {
            throw DomainException.InvalidTransactionId(transaction.id)
        }

        ensureInternalDefaultAccountIsReady()
        
        // CRITICAL: For ALL new Expense/Income transactions, enforce internal default account
        // Never accept account ID from UI state, AI parsing, or incoming model for new records
        val resolvedAccountId = resolveInternalDefaultAccountIdForNewTransaction(transaction.type)
        
        val resolvedTransaction = transaction.copy(
            accountId = resolvedAccountId
        )
        validator.validate(resolvedTransaction)

        return transactionRepository.insertTransaction(resolvedTransaction)
    }

    /**
     * Resolves the internal default account for ALL new Expense/Income transactions.
     * TRANSFER transactions are not supported for creation (per product decision).
     */
    private suspend fun resolveInternalDefaultAccountIdForNewTransaction(transactionType: TransactionType): Long {
        // Only allow EXPENSE and INCOME types for new transactions
        when (transactionType) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                val accounts = accountRepository.observeActiveAccounts().first()
                return accounts.firstOrNull { it.internalKey == "default" }?.id
                    ?: throw DomainException.AccountNotFound(0L) // Should never happen if provisioning works
            }
            TransactionType.TRANSFER -> {
                // TRANSFER creation is not supported - will be handled by UI removal
                // For now, reject with appropriate error
                throw DomainException.InvalidTransfer("New transfer transactions are not supported. Use internal default account for Expense/Income only.")
            }
        }
    }

    private suspend fun ensureInternalDefaultAccountIsReady() {
        val result = internalDefaultAccountProvisioner?.ensureProvisioned() ?: return
        when (result) {
            is InternalDefaultAccountProvisioningResult.Ready -> Unit
            is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                throw DomainException.InvalidAccount("Internal default account requires a preferred currency selection before new transactions can be written.")
            }
            is InternalDefaultAccountProvisioningResult.Failure -> throw result.error
        }
    }
}
