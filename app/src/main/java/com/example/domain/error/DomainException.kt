package com.example.domain.error

/**
 * Domain-level exceptions representing financial rule violations or entity lookup failures.
 */
sealed class DomainException(message: String) : Exception(message) {

    // --- Account Errors ---
    data class InvalidAccount(override val message: String = "Account data is invalid") : DomainException(message)
    data class AccountNotFound(val accountId: Long) : DomainException("Account with ID $accountId was not found")
    data class AccountArchived(val accountId: Long) : DomainException("Account with ID $accountId is archived")

    // --- Category Errors ---
    data class InvalidCategory(override val message: String = "Category data is invalid") : DomainException(message)
    data class CategoryNotFound(val categoryId: Long) : DomainException("Category with ID $categoryId was not found")
    data class CategoryArchived(val categoryId: Long) : DomainException("Category with ID $categoryId is archived")
    data class SystemCategoryCannotBeArchived(val categoryId: Long) : DomainException("System category with ID $categoryId cannot be archived")
    data class IncompatibleCategoryType(val expected: String, val actual: String) : DomainException("Category type '$actual' is incompatible with transaction type '$expected'")

    // --- Transaction & Financial Errors ---
    data class InvalidAmount(override val message: String = "Transaction amount must be strictly positive") : DomainException(message)
    data class InvalidCurrency(override val message: String = "Currency code is invalid") : DomainException(message)
    data class CurrencyMismatch(val accountCurrency: String, val transactionCurrency: String) : DomainException("Transaction currency '$transactionCurrency' does not match account currency '$accountCurrency'")
    data class InvalidTransfer(override val message: String) : DomainException(message)
    data class TransactionNotFound(val transactionId: Long) : DomainException("Transaction with ID $transactionId was not found")
    data class InvalidTransactionId(val transactionId: Long) : DomainException("Invalid transaction ID: $transactionId")
}
