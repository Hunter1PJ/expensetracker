package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null
) {
    private val validator = TransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(transaction: Transaction) {
        if (transaction.id <= 0L) {
            throw DomainException.InvalidTransactionId(transaction.id)
        }

        ensureInternalDefaultAccountIsReady()
        
        // CRITICAL FIX: Load persisted record FIRST to preserve original account ID
        val existingTransaction = transactionRepository.getTransactionById(transaction.id)
            ?: throw DomainException.TransactionNotFound(transaction.id)
        
        // Preserve the original persisted accountId - DO NOT accept different ID from UI
        // Only update allowed fields: amount, categoryId, note, transactionTime
        val resolvedTransaction = existingTransaction.copy(
            amount = transaction.amount,
            categoryId = transaction.categoryId,
            note = transaction.note,
            transactionTime = transaction.transactionTime
            // accountId is PRESERVED from existing record, not replaced
        )

        validator.validate(resolvedTransaction)

        transactionRepository.updateTransaction(resolvedTransaction)
    }

    private suspend fun ensureInternalDefaultAccountIsReady() {
        val result = internalDefaultAccountProvisioner?.ensureProvisioned() ?: return
        when (result) {
            is InternalDefaultAccountProvisioningResult.Ready -> Unit
            is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection -> {
                throw DomainException.InvalidAccount("Internal default account requires a preferred currency selection before transaction updates can be written.")
            }
            is InternalDefaultAccountProvisioningResult.Failure -> throw result.error
        }
    }
}
