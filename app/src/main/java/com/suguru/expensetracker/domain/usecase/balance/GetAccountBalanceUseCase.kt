package com.suguru.expensetracker.domain.usecase.balance

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository

class GetAccountBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(accountId: Long): Money {
        val account = accountRepository.getAccountById(accountId)
            ?: throw DomainException.AccountNotFound(accountId)

        val transactions = transactionRepository.getTransactionsByAccount(accountId)
        val currency = account.initialBalance.currencyCode
        var balanceMinorUnits = account.initialBalance.amountInMinorUnits

        for (tx in transactions) {
            if (tx.amount.currencyCode != currency) {
                throw DomainException.CurrencyMismatch(
                    accountCurrency = currency,
                    transactionCurrency = tx.amount.currencyCode
                )
            }

            when (tx.type) {
                TransactionType.INCOME -> {
                    if (tx.accountId == accountId) {
                        balanceMinorUnits = Math.addExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.accountId == accountId) {
                        balanceMinorUnits = Math.subtractExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                    }
                }
                TransactionType.TRANSFER -> {
                    if (tx.accountId == accountId) {
                        balanceMinorUnits = Math.subtractExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                    }
                    if (tx.destinationAccountId == accountId) {
                        balanceMinorUnits = Math.addExact(balanceMinorUnits, tx.amount.amountInMinorUnits)
                    }
                }
            }
        }

        return Money(
            amountInMinorUnits = balanceMinorUnits,
            currencyCode = currency
        )
    }
}
