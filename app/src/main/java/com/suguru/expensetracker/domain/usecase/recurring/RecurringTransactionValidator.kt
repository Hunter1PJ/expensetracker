package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository

internal class RecurringTransactionValidator(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend fun validate(rule: RecurringTransaction) {
        if (rule.amount.amountInMinorUnits <= 0L) {
            throw DomainException.InvalidAmount("Recurring transaction amount must be strictly positive")
        }

        if (rule.amount.currencyCode.isBlank()) {
            throw DomainException.InvalidCurrency("Recurring transaction currency code cannot be blank")
        }

        if (rule.endDate != null && rule.endDate < rule.startDate) {
            throw DomainException.InvalidAmount("End date cannot be before start date")
        }

        when (rule.type) {
            TransactionType.EXPENSE -> validateExpense(rule)
            TransactionType.INCOME -> validateIncome(rule)
            TransactionType.TRANSFER -> validateTransfer(rule)
        }
    }

    private suspend fun validateExpense(rule: RecurringTransaction) {
        if (rule.destinationAccountId != null) {
            throw DomainException.InvalidTransfer("Expense recurring transaction cannot have a destination account")
        }

        val categoryId = rule.categoryId
            ?: throw DomainException.InvalidCategory("Expense recurring transaction must have a category")

        val account = accountRepository.getAccountById(rule.accountId)
            ?: throw DomainException.AccountNotFound(rule.accountId)

        if (account.isArchived) {
            throw DomainException.AccountArchived(rule.accountId)
        }

        if (account.initialBalance.currencyCode != rule.amount.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = account.initialBalance.currencyCode,
                transactionCurrency = rule.amount.currencyCode
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

    private suspend fun validateIncome(rule: RecurringTransaction) {
        if (rule.destinationAccountId != null) {
            throw DomainException.InvalidTransfer("Income recurring transaction cannot have a destination account")
        }

        val categoryId = rule.categoryId
            ?: throw DomainException.InvalidCategory("Income recurring transaction must have a category")

        val account = accountRepository.getAccountById(rule.accountId)
            ?: throw DomainException.AccountNotFound(rule.accountId)

        if (account.isArchived) {
            throw DomainException.AccountArchived(rule.accountId)
        }

        if (account.initialBalance.currencyCode != rule.amount.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = account.initialBalance.currencyCode,
                transactionCurrency = rule.amount.currencyCode
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

    private suspend fun validateTransfer(rule: RecurringTransaction) {
        val destinationId = rule.destinationAccountId
            ?: throw DomainException.InvalidTransfer("Transfer recurring transaction must specify a destination account")

        if (rule.accountId == destinationId) {
            throw DomainException.InvalidTransfer("Source and destination accounts must be different")
        }

        if (rule.categoryId != null) {
            throw DomainException.InvalidTransfer("Transfer recurring transaction must not have a category assigned")
        }

        val sourceAccount = accountRepository.getAccountById(rule.accountId)
            ?: throw DomainException.AccountNotFound(rule.accountId)

        if (sourceAccount.isArchived) {
            throw DomainException.AccountArchived(rule.accountId)
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

        if (rule.amount.currencyCode != sourceAccount.initialBalance.currencyCode) {
            throw DomainException.CurrencyMismatch(
                accountCurrency = sourceAccount.initialBalance.currencyCode,
                transactionCurrency = rule.amount.currencyCode
            )
        }
    }
}
