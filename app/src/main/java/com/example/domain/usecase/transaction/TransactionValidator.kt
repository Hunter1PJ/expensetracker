package com.example.domain.usecase.transaction

import com.example.domain.error.DomainException
import com.example.domain.model.CategoryType
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository

internal class TransactionValidator(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend fun validate(transaction: Transaction) {
        if (transaction.amount.amountInMinorUnits <= 0L) {
            throw DomainException.InvalidAmount("Transaction amount must be strictly positive")
        }

        if (transaction.amount.currencyCode.isBlank()) {
            throw DomainException.InvalidCurrency("Transaction currency code cannot be blank")
        }

        when (transaction.type) {
            TransactionType.EXPENSE -> validateExpense(transaction)
            TransactionType.INCOME -> validateIncome(transaction)
            TransactionType.TRANSFER -> validateTransfer(transaction)
        }
    }

    private suspend fun validateExpense(transaction: Transaction) {
        if (transaction.destinationAccountId != null) {
            throw DomainException.InvalidTransfer("Expense transaction cannot have a destination account")
        }

        val categoryId = transaction.categoryId
            ?: throw DomainException.InvalidCategory("Expense transaction must have a category")

        val account = accountRepository.getAccountById(transaction.accountId)
            ?: throw DomainException.AccountNotFound(transaction.accountId)

        if (account.isArchived) {
            throw DomainException.AccountArchived(transaction.accountId)
        }

        if (account.initialBalance.currencyCode != transaction.amount.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = account.initialBalance.currencyCode,
                transactionCurrency = transaction.amount.currencyCode
            )
        }

        val category = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException.CategoryNotFound(categoryId)

        if (category.isArchived) {
            throw DomainException.CategoryArchived(categoryId)
        }

        if (category.type != CategoryType.EXPENSE && category.type != CategoryType.BOTH) {
            throw DomainException.IncompatibleCategoryType(
                expected = "EXPENSE",
                actual = category.type.name
            )
        }
    }

    private suspend fun validateIncome(transaction: Transaction) {
        if (transaction.destinationAccountId != null) {
            throw DomainException.InvalidTransfer("Income transaction cannot have a destination account")
        }

        val categoryId = transaction.categoryId
            ?: throw DomainException.InvalidCategory("Income transaction must have a category")

        val account = accountRepository.getAccountById(transaction.accountId)
            ?: throw DomainException.AccountNotFound(transaction.accountId)

        if (account.isArchived) {
            throw DomainException.AccountArchived(transaction.accountId)
        }

        if (account.initialBalance.currencyCode != transaction.amount.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = account.initialBalance.currencyCode,
                transactionCurrency = transaction.amount.currencyCode
            )
        }

        val category = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException.CategoryNotFound(categoryId)

        if (category.isArchived) {
            throw DomainException.CategoryArchived(categoryId)
        }

        if (category.type != CategoryType.INCOME && category.type != CategoryType.BOTH) {
            throw DomainException.IncompatibleCategoryType(
                expected = "INCOME",
                actual = category.type.name
            )
        }
    }

    private suspend fun validateTransfer(transaction: Transaction) {
        val destinationId = transaction.destinationAccountId
            ?: throw DomainException.InvalidTransfer("Transfer transaction must specify a destination account")

        if (transaction.accountId == destinationId) {
            throw DomainException.InvalidTransfer("Source and destination accounts must be different")
        }

        if (transaction.categoryId != null) {
            throw DomainException.InvalidTransfer("Transfer transaction must not have a category assigned")
        }

        val sourceAccount = accountRepository.getAccountById(transaction.accountId)
            ?: throw DomainException.AccountNotFound(transaction.accountId)

        if (sourceAccount.isArchived) {
            throw DomainException.AccountArchived(transaction.accountId)
        }

        val destinationAccount = accountRepository.getAccountById(destinationId)
            ?: throw DomainException.AccountNotFound(destinationId)

        if (destinationAccount.isArchived) {
            throw DomainException.AccountArchived(destinationId)
        }

        if (sourceAccount.initialBalance.currencyCode != destinationAccount.initialBalance.currencyCode) {
            throw DomainException.InvalidTransfer(
                "Transfers between accounts with different currencies (${sourceAccount.initialBalance.currencyCode} vs ${destinationAccount.initialBalance.currencyCode}) are not supported"
            )
        }

        if (transaction.amount.currencyCode != sourceAccount.initialBalance.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = sourceAccount.initialBalance.currencyCode,
                transactionCurrency = transaction.amount.currencyCode
            )
        }
    }
}
