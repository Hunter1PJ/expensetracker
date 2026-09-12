package com.suguru.expensetracker.domain.usecase.balance

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveAccountBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(accountId: Long): Flow<Money?> {
        return combine(
            accountRepository.observeAccountById(accountId),
            transactionRepository.observeTransactionsByAccount(accountId)
        ) { account, transactions ->
            if (account == null) return@combine null

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

            Money(
                amountInMinorUnits = balanceMinorUnits,
                currencyCode = currency
            )
        }
    }
}
