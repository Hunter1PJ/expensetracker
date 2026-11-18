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
import java.time.Clock

class CreateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val internalDefaultAccountProvisioner: InternalDefaultAccountProvisioner? = null,
    clock: Clock = Clock.systemDefaultZone()
) {
    private val validator = TransactionValidator(accountRepository, categoryRepository, clock)

    suspend operator fun invoke(transaction: Transaction): Long {
        if (transaction.id != 0L) {
            throw DomainException.InvalidTransactionId(transaction.id)
        }

        ensureInternalDefaultAccountIsReady()
        
        // Expense and income use the internal ledger account. Transfers retain both explicitly
        // selected accounts and are validated below.
        val resolvedAccountId = resolveAccountIdForNewTransaction(transaction)
        
        val resolvedTransaction = transaction.copy(
            accountId = resolvedAccountId
        )
        validator.validate(resolvedTransaction)

        return transactionRepository.insertTransaction(resolvedTransaction)
    }

    /**
     * Resolves the ledger account for new transactions. Expenses and income always use the
     * provisioned internal account; transfers keep their selected source account.
     */
    private suspend fun resolveAccountIdForNewTransaction(transaction: Transaction): Long =
        when (transaction.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                val accounts = accountRepository.observeActiveAccounts().first()
                accounts.firstOrNull { it.internalKey == "default" }?.id
                    ?: throw DomainException.AccountNotFound(0L)
            }
            TransactionType.TRANSFER -> transaction.accountId
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
