package com.example.domain.usecase.dashboard

import com.example.domain.error.DomainException
import com.example.domain.model.AccountBalanceSummary
import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes all active accounts along with their current derived balances.
 */
class ObserveAccountSummariesUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<List<AccountBalanceSummary>> {
        return combine(
            accountRepository.observeActiveAccounts(),
            transactionRepository.observeAllTransactions()
        ) { accounts, allTransactions ->
            accounts.map { account ->
                val currency = account.initialBalance.currencyCode
                var balanceMinorUnits = account.initialBalance.amountInMinorUnits

                val accountTransactions = allTransactions.filter {
                    it.accountId == account.id || it.destinationAccountId == account.id
                }

                for (tx in accountTransactions) {
                    if (tx.amount.currencyCode != currency) {
                        throw DomainException.CurrencyMismatch(
                            accountCurrency = currency,
                            transactionCurrency = tx.amount.currencyCode
                        )
                    }

                    when (tx.type) {
                        TransactionType.INCOME -> {
                            if (tx.accountId == account.id) {
                                balanceMinorUnits = Math.addExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                            }
                        }
                        TransactionType.EXPENSE -> {
                            if (tx.accountId == account.id) {
                                balanceMinorUnits = Math.subtractExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                            }
                        }
                        TransactionType.TRANSFER -> {
                            if (tx.accountId == account.id) {
                                balanceMinorUnits = Math.subtractExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                            }
                            if (tx.destinationAccountId == account.id) {
                                balanceMinorUnits = Math.addExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                            }
                        }
                    }
                }

                AccountBalanceSummary(
                    account = account,
                    balance = Money(
                        amountInMinorUnits = balanceMinorUnits,
                        currencyCode = currency
                    )
                )
            }
        }
    }
}
