package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for UpdateTransactionUseCase to ensure historical account IDs are preserved.
 * These tests verify the critical fix that prevents silent remapping of account references.
 */
class UpdateTransactionUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var updateTransactionUseCase: UpdateTransactionUseCase
    private lateinit var getTransactionUseCase: GetTransactionUseCase

    private var usdAccountId: Long = 0L
    private var uzsAccountId: Long = 0L
    private var expenseCategoryId: Long = 0L
    private var incomeCategoryId: Long = 0L

    @Test
    fun updateTransaction_preservesExistingAccountId_whenDifferentValidIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null  // No provisioner for tests
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        // Seed accounts and categories
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        uzsAccountId = accountRepository.insertAccount(
            Account(name = "UZS Card", type = AccountType.CARD, initialBalance = Money(50000000L, "UZS"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a transaction with USD account
        val originalTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )
        
        val transactionId = updateTransactionUseCase(originalTransaction)
        assertNotNull(transactionId)

        // Verify original transaction was saved
        val savedOriginal = getTransactionUseCase(transactionId)
        assertEquals(usdAccountId, savedOriginal?.accountId)

        // Act: Try to update with a DIFFERENT valid account ID (UZS instead of USD)
        val updatedTransaction = Transaction(
            id = transactionId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),  // Different amount
            accountId = uzsAccountId,      // DIFFERENT account - should be IGNORED
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        updateTransactionUseCase(updatedTransaction)

        // Assert: Original account ID must be preserved, NOT replaced with UZS account
        val updatedRecord = getTransactionUseCase(transactionId)
        assertNotNull(updatedRecord)
        assertEquals(usdAccountId, updatedRecord?.accountId, "Historical account ID must be preserved")
        assertEquals(3000L, updatedRecord?.amount?.amountInMinorUnits, "Amount should be updated")
    }

    @Test
    fun updateTransaction_preservesExistingAccountId_whenInvalidIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        // Seed accounts
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a transaction
        val originalTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )
        
        val transactionId = updateTransactionUseCase(originalTransaction)

        // Act: Try to update with an INVALID account ID (non-existent)
        val updatedTransaction = Transaction(
            id = transactionId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 999999L,  // INVALID - does not exist
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        try {
            updateTransactionUseCase(updatedTransaction)
            // If we get here without exception, the test fails
            org.junit.Assert.fail("Should have thrown DomainException.AccountNotFound for invalid account ID")
        } catch (e: DomainException.AccountNotFound) {
            // Expected - should throw error for invalid account reference
            assertEquals("Account with ID 999999 was not found", e.message)
        }

        // Assert: Original record must still exist unchanged
        val originalRecord = getTransactionUseCase(transactionId)
        assertNotNull(originalRecord)
        assertEquals(usdAccountId, originalRecord?.accountId, "Historical record must be unchanged")
    }

    @Test
    fun updateTransaction_preservesExistingAccountId_whenMissingIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        // Seed accounts
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a transaction
        val originalTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )
        
        val transactionId = updateTransactionUseCase(originalTransaction)

        // Act: Try to update with MISSING account ID (0L means no account specified)
        val updatedTransaction = Transaction(
            id = transactionId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 0L,  // MISSING/INVALID - should not replace historical record
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        try {
            updateTransactionUseCase(updatedTransaction)
            org.junit.Assert.fail("Should have thrown DomainException.AccountNotFound for missing account")
        } catch (e: DomainException.AccountNotFound) {
            // Expected - should throw error for missing account reference
            assertEquals("Account with ID 0 was not found", e.message)
        }

        // Assert: Original record must still exist unchanged
        val originalRecord = getTransactionUseCase(transactionId)
        assertNotNull(originalRecord)
        assertEquals(usdAccountId, originalRecord?.accountId, "Historical record must be unchanged")
    }

    @Test
    fun updateTransaction_withValidAccountUpdatesAllowedFields() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        // Seed accounts and categories
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a transaction
        val originalTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )
        
        val transactionId = updateTransactionUseCase(originalTransaction)

        // Act: Update with same valid account ID but different allowed fields
        val updatedTransaction = Transaction(
            id = transactionId,
            type = TransactionType.EXPENSE,
            amount = Money(3500L, "USD"),  // Different amount - should be updated
            accountId = usdAccountId,       // Same account - preserved
            categoryId = expenseCategoryId, // Same category - preserved
            transactionTime = java.time.Instant.now() + java.time.Duration.ofHours(1)  // Different time - should be updated
        )

        updateTransactionUseCase(updatedTransaction)

        // Assert: Record exists with updated allowed fields but same account ID
        val updatedRecord = getTransactionUseCase(transactionId)
        assertNotNull(updatedRecord)
        assertEquals(usdAccountId, updatedRecord?.accountId, "Account ID must remain the same")
        assertEquals(3500L, updatedRecord?.amount?.amountInMinorUnits, "Amount should be updated")
    }

    @Test(expected = DomainException.TransactionNotFound::class)
    fun updateTransaction_nonExistentId_throwsTransactionNotFound() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to update non-existent transaction
        val invalidTransaction = Transaction(
            id = 999999L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = 0L,
            categoryId = 0L,
            transactionTime = java.time.Instant.now()
        )

        updateTransactionUseCase(invalidTransaction)
    }

    @Test(expected = DomainException.InvalidTransactionId::class)
    fun updateTransaction_negativeId_throwsInvalidTransactionId() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to update with negative ID
        val invalidTransaction = Transaction(
            id = -1L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = 0L,
            categoryId = 0L,
            transactionTime = java.time.Instant.now()
        )

        updateTransactionUseCase(invalidTransaction)
    }
}
