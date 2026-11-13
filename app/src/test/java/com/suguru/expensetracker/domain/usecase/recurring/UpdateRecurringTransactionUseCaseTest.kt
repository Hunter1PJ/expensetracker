package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeRecurringTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.recurring.GetRecurringTransactionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for UpdateRecurringTransactionUseCase to ensure historical account IDs are preserved.
 * These tests verify the critical fix that prevents silent remapping of account references in recurring rules.
 */
class UpdateRecurringTransactionUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var recurringTransactionRepository: FakeRecurringTransactionRepository
    private lateinit var updateRecurringTransactionUseCase: UpdateRecurringTransactionUseCase
    private lateinit var getRecurringTransactionUseCase: GetRecurringTransactionUseCase

    private var usdAccountId: Long = 0L
    private var uzsAccountId: Long = 0L
    private var expenseCategoryId: Long = 0L
    private var incomeCategoryId: Long = 0L

    @Test
    fun updateRecurringRule_preservesExistingAccountId_whenDifferentValidIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null  // No provisioner for tests
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

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

        // Create a recurring rule with USD account
        val originalRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )
        
        val ruleId = updateRecurringTransactionUseCase(originalRule)
        assertNotNull(ruleId)

        // Verify original rule was saved
        val savedOriginal = getRecurringTransactionUseCase(ruleId)
        assertEquals(usdAccountId, savedOriginal?.accountId)

        // Act: Try to update with a DIFFERENT valid account ID (UZS instead of USD)
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),  // Different amount
            accountId = uzsAccountId,      // DIFFERENT account - should be IGNORED
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(updatedRule)

        // Assert: Original account ID must be preserved, NOT replaced with UZS account
        val updatedRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(updatedRecord)
        assertEquals(usdAccountId, updatedRecord?.accountId, "Historical account ID must be preserved")
        assertEquals(3000L, updatedRecord?.amount?.amountInMinorUnits, "Amount should be updated")
    }

    @Test
    fun updateRecurringRule_preservesExistingAccountId_whenInvalidIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a recurring rule
        val originalRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )
        
        val ruleId = updateRecurringTransactionUseCase(originalRule)

        // Act: Try to update with an INVALID account ID (non-existent)
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 999999L,  // INVALID - does not exist
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        try {
            updateRecurringTransactionUseCase(updatedRule)
            // If we get here without exception, the test fails
            org.junit.Assert.fail("Should have thrown DomainException.AccountNotFound for invalid account ID")
        } catch (e: DomainException.AccountNotFound) {
            // Expected - should throw error for invalid account reference
            assertEquals("Account with ID 999999 was not found", e.message)
        }

        // Assert: Original record must still exist unchanged
        val originalRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(originalRecord)
        assertEquals(usdAccountId, originalRecord?.accountId, "Historical record must be unchanged")
    }

    @Test
    fun updateRecurringRule_preservesExistingAccountId_whenMissingIdSubmitted() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a recurring rule
        val originalRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )
        
        val ruleId = updateRecurringTransactionUseCase(originalRule)

        // Act: Try to update with MISSING account ID (0L means no account specified)
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 0L,  // MISSING/INVALID - should not replace historical record
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        try {
            updateRecurringTransactionUseCase(updatedRule)
            org.junit.Assert.fail("Should have thrown DomainException.AccountNotFound for missing account")
        } catch (e: DomainException.AccountNotFound) {
            // Expected - should throw error for missing account reference
            assertEquals("Account with ID 0 was not found", e.message)
        }

        // Assert: Original record must still exist unchanged
        val originalRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(originalRecord)
        assertEquals(usdAccountId, originalRecord?.accountId, "Historical record must be unchanged")
    }

    @Test
    fun updateRecurringRule_withValidAccountUpdatesAllowedFields() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts and categories
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Create a recurring rule
        val originalRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )
        
        val ruleId = updateRecurringTransactionUseCase(originalRule)

        // Act: Update with same valid account ID but different allowed fields
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3500L, "USD"),  // Different amount - should be updated
            accountId = usdAccountId,       // Same account - preserved
            categoryId = expenseCategoryId, // Same category - preserved
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(updatedRule)

        // Assert: Record exists with updated allowed fields but same account ID
        val updatedRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(updatedRecord)
        assertEquals(usdAccountId, updatedRecord?.accountId, "Account ID must remain the same")
        assertEquals(3500L, updatedRecord?.amount?.amountInMinorUnits, "Amount should be updated")
    }

    @Test(expected = DomainException.TransactionNotFound::class)
    fun updateRecurringRule_nonExistentId_throwsTransactionNotFound() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to update non-existent recurring rule
        val invalidRule = RecurringTransaction(
            id = 999999L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = 0L,
            categoryId = 0L,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(invalidRule)
    }

    @Test(expected = DomainException.InvalidTransactionId::class)
    fun updateRecurringRule_negativeId_throwsInvalidTransactionId() = runBlocking {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to update with negative ID
        val invalidRule = RecurringTransaction(
            id = -1L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = 0L,
            categoryId = 0L,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(invalidRule)
    }
}
