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
    fun updateRecurringRule_preservesExistingAccountId_whenDifferentValidIdSubmitted() = runBlocking<Unit> {
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

        // Seed persisted recurring rule with USD account
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
        
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(originalRule)

        // Verify original rule was saved
        val savedOriginal = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedOriginal)
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
        assertEquals("Historical account ID must be preserved", usdAccountId, updatedRecord?.accountId)
        assertEquals("Amount should be updated", 3000L, updatedRecord?.amount?.amountInMinorUnits)
    }

    @Test
    fun updateRecurringRule_preservesExistingAccountId_whenInvalidIdSubmitted() = runBlocking<Unit> {
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

        // Seed persisted recurring rule
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
        
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(originalRule)

        // Act: Submit an update with an INVALID incoming account ID (non-existent) — must be ignored
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 999999L,  // INVALID incoming ID - ignored during historical edit
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(updatedRule)

        // Assert: Persisted account ID is preserved and allowed fields update
        val updatedRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(updatedRecord)
        assertEquals("Historical account ID must be preserved", usdAccountId, updatedRecord?.accountId)
        assertEquals("Amount should be updated", 3000L, updatedRecord?.amount?.amountInMinorUnits)
    }

    @Test
    fun updateRecurringRule_preservesExistingAccountId_whenMissingIdSubmitted() = runBlocking<Unit> {
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

        // Seed persisted recurring rule
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
        
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(originalRule)

        // Act: Submit an update with MISSING incoming account ID (0L) — must be ignored
        val updatedRule = RecurringTransaction(
            id = ruleId,
            type = TransactionType.EXPENSE,
            amount = Money(3000L, "USD"),
            accountId = 0L,  // MISSING incoming ID - ignored during historical edit
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        updateRecurringTransactionUseCase(updatedRule)

        // Assert: Persisted account ID is preserved and allowed fields update
        val updatedRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(updatedRecord)
        assertEquals("Historical account ID must be preserved", usdAccountId, updatedRecord?.accountId)
        assertEquals("Amount should be updated", 3000L, updatedRecord?.amount?.amountInMinorUnits)
    }

    @Test(expected = DomainException.AccountNotFound::class)
    fun updateRecurringRule_invalidPersistedAccountReference_throwsAccountNotFound() = runBlocking<Unit> {
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

        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        val persistedWithMissingAccount = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = 999999L,
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(persistedWithMissingAccount)

        // Act & Assert: Persisted account reference does not exist, validator must reject
        updateRecurringTransactionUseCase(
            persistedWithMissingAccount.copy(
                id = ruleId,
                amount = Money(3000L, "USD")
            )
        )
    }

    @Test
    fun updateRecurringRule_withValidAccountUpdatesAllowedFields() = runBlocking<Unit> {
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

        // Seed persisted recurring rule
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
        
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(originalRule)

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
            note = "Updated weekly food",
            isActive = true
        )

        updateRecurringTransactionUseCase(updatedRule)

        // Assert: Record exists with updated allowed fields but same account ID
        val updatedRecord = getRecurringTransactionUseCase(ruleId)
        assertNotNull(updatedRecord)
        assertEquals("Account ID must remain the same", usdAccountId, updatedRecord?.accountId)
        assertEquals("Amount should be updated", 3500L, updatedRecord?.amount?.amountInMinorUnits)
        assertEquals("Note should be updated", "Updated weekly food", updatedRecord?.note)
    }

    @Test(expected = DomainException.TransactionNotFound::class)
    fun updateRecurringRule_nonExistentId_throwsTransactionNotFound() = runBlocking<Unit> {
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
    fun updateRecurringRule_negativeId_throwsInvalidTransactionId() = runBlocking<Unit> {
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

    @Test(expected = DomainException.InvalidTransfer::class)
    fun updateRecurringRule_historicalTransferReactivation_throwsInvalidTransfer() = runBlocking<Unit> {
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()

        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        val srcId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val dstId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )

        val transferRuleId = recurringTransactionRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(5000L, "USD"),
                accountId = srcId,
                destinationAccountId = dstId,
                categoryId = null,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.of(2026, 1, 1),
                nextOccurrence = java.time.LocalDate.of(2026, 2, 1),
                note = "Monthly transfer",
                isActive = false
            )
        )

        val persistedBefore = recurringTransactionRepository.getRecurringTransactionById(transferRuleId)!!
        updateRecurringTransactionUseCase(
            persistedBefore.copy(isActive = true, note = "Reactivate attempt")
        )
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun updateRecurringRule_historicalTransferConvertToExpense_throwsInvalidTransfer() = runBlocking<Unit> {
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()

        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        val srcId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val dstId = accountRepository.insertAccount(
            Account(name = "Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        val transferRuleId = recurringTransactionRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.TRANSFER,
                amount = Money(5000L, "USD"),
                accountId = srcId,
                destinationAccountId = dstId,
                categoryId = null,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.of(2026, 1, 1),
                nextOccurrence = java.time.LocalDate.of(2026, 2, 1),
                isActive = false
            )
        )

        updateRecurringTransactionUseCase(
            RecurringTransaction(
                id = transferRuleId,
                type = TransactionType.EXPENSE,
                amount = Money(5000L, "USD"),
                accountId = srcId,
                destinationAccountId = null,
                categoryId = catId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.of(2026, 1, 1),
                nextOccurrence = java.time.LocalDate.of(2026, 2, 1),
                isActive = true
            )
        )
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun updateRecurringRule_changeTypeExpenseToIncome_throwsInvalidTransfer() = runBlocking<Unit> {
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()

        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        val accId = accountRepository.insertAccount(
            Account(name = "Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        val expenseCat = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )
        val incomeCat = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
        )

        val ruleId = recurringTransactionRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(2500L, "USD"),
                accountId = accId,
                categoryId = expenseCat,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.of(2026, 1, 1),
                nextOccurrence = java.time.LocalDate.of(2026, 2, 1),
                isActive = true
            )
        )

        updateRecurringTransactionUseCase(
            RecurringTransaction(
                id = ruleId,
                type = TransactionType.INCOME,
                amount = Money(2500L, "USD"),
                accountId = accId,
                categoryId = incomeCat,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.of(2026, 1, 1),
                nextOccurrence = java.time.LocalDate.of(2026, 2, 1),
                isActive = true
            )
        )
    }

    @Test
    fun updateRecurringRule_preservesNextOccurrenceFrequencyAndActiveState() = runBlocking<Unit> {
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()

        updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        val accId = accountRepository.insertAccount(
            Account(name = "Legacy", type = AccountType.BANK, initialBalance = Money(1000L, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#6366F1")
        )

        val startDate = java.time.LocalDate.of(2026, 1, 1)
        val scheduledNext = java.time.LocalDate.of(2026, 4, 1)
        val ruleId = recurringTransactionRepository.insertRecurringTransaction(
            RecurringTransaction(
                id = 0L,
                type = TransactionType.EXPENSE,
                amount = Money(100000L, "USD"),
                accountId = accId,
                categoryId = catId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = startDate,
                nextOccurrence = scheduledNext,
                note = "Apartment rent",
                isActive = false
            )
        )

        updateRecurringTransactionUseCase(
            RecurringTransaction(
                id = ruleId,
                type = TransactionType.EXPENSE,
                amount = Money(110000L, "USD"),
                accountId = accId,
                categoryId = catId,
                frequency = RecurrenceFrequency.WEEKLY, // ignored by use case
                startDate = startDate,
                nextOccurrence = scheduledNext,
                note = "Updated rent",
                isActive = false
            )
        )

        val saved = recurringTransactionRepository.getRecurringTransactionById(ruleId)!!
        assertEquals(RecurrenceFrequency.MONTHLY, saved.frequency)
        assertEquals(scheduledNext, saved.nextOccurrence)
        assertEquals(false, saved.isActive)
        assertEquals(110000L, saved.amount.amountInMinorUnits)
        assertEquals("Updated rent", saved.note)
    }
}
