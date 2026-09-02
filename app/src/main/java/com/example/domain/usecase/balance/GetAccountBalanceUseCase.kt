package com.example.domain.usecase.balance

import com.example.domain.error.DomainException
import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.TransactionRepository

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
                        balanceMinorUnits += tx.amount.amountInMinorUnits
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.accountId == accountId) {
                        balanceMinorUnits -= tx.amount.amountInMinorUnits
                    }
                }
                TransactionType.TRANSFER -> {
                    if (tx.accountId == accountId) {
                        balanceMinorUnits -= tx.amount.amountInMinorUnits
                    }
                    if (tx.destinationAccountId == accountId) {
                        balanceMinorUnits += tx.amount.amountInMinorUnits
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
