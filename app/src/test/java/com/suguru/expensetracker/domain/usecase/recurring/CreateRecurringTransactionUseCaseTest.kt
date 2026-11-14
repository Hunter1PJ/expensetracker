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
import org.junit.Test

/**
 * Regression tests for CreateRecurringTransactionUseCase to verify internal default account enforcement.
 * These tests prove that new recurring Expense/Income rules always use the internal default account,
 * regardless of what account ID is supplied by UI state or AI parsing.
 */
class CreateRecurringTransactionUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var recurringTransactionRepository: FakeRecurringTransactionRepository
    private lateinit var createRecurringTransactionUseCase: CreateRecurringTransactionUseCase
    private lateinit var getRecurringTransactionUseCase: GetRecurringTransactionUseCase

    private var internalDefaultAccountId: Long = 0L
    private var otherUsdAccountId: Long = 0L
    private var expenseCategoryId: Long = 0L
    private var incomeCategoryId: Long = 0L

    @Test
    fun createNewRecurringExpense_enforcesInternalDefaultAccount_evenWhenDifferentValidIdSupplied() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null  // No provisioner for tests
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts - simulate having an internal default and another account
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        otherUsdAccountId = accountRepository.insertAccount(
            Account(name = "User's USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Act: Create a new recurring Expense rule with a DIFFERENT valid account ID supplied
        val ruleWithOtherAccount = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = otherUsdAccountId,  // SUPPLYING WRONG ACCOUNT - should be ignored
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        val ruleId = createRecurringTransactionUseCase(ruleWithOtherAccount)
        assertNotNull(ruleId)

        // Assert: Rule must use internal default account, NOT the supplied other account
        val savedRule = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedRule)
        assertEquals(
            "New recurring Expense must use internal default account, not supplied account ID",
            internalDefaultAccountId,
            savedRule?.accountId
        )
    }

    @Test
    fun createNewRecurringIncome_enforcesInternalDefaultAccount_evenWhenDifferentValidIdSupplied() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        otherUsdAccountId = accountRepository.insertAccount(
            Account(name = "User's USD Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
        )
        incomeCategoryId = categoryRepository.insertCategory(
            Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#4CAF50")
        )

        // Act: Create a new recurring Income rule with a DIFFERENT valid account ID supplied
        val ruleWithOtherAccount = RecurringTransaction(
            id = 0L,
            type = TransactionType.INCOME,
            amount = Money(500000L, "USD"),
            accountId = otherUsdAccountId,  // SUPPLYING WRONG ACCOUNT - should be ignored
            categoryId = incomeCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        val ruleId = createRecurringTransactionUseCase(ruleWithOtherAccount)
        assertNotNull(ruleId)

        // Assert: Rule must use internal default account, NOT the supplied other account
        val savedRule = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedRule)
        assertEquals(
            "New recurring Income must use internal default account, not supplied account ID",
            internalDefaultAccountId,
            savedRule?.accountId
        )
    }

    @Test
    fun createNewRecurringExpense_withAiSuppliedAccountId_enforcesInternalDefault() = runBlocking<Unit> {
        // Arrange - Simulate AI parsing that suggests a specific account
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts - AI might suggest this account
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        aiSuggestedAccountId = accountRepository.insertAccount(
            Account(name = "AI Suggested Account", type = AccountType.BANK, initialBalance = Money(100000L, "EUR"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Act: Simulate AI parsing result with suggested account ID
        val aiSuggestedRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(15000L, "USD"),
            accountId = aiSuggestedAccountId,  // AI SUPPLIED - should be IGNORED for new records
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        val ruleId = createRecurringTransactionUseCase(aiSuggestedRule)
        assertNotNull(ruleId)

        // Assert: Internal default account must be used, NOT AI-suggested account
        val savedRule = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedRule)
        assertEquals(
            "AI-supplied account ID cannot override internal default for new recurring rules",
            internalDefaultAccountId,
            savedRule?.accountId
        )
    }

    @Test
    fun createNewRecurringExpense_withMissingAccountId_enforcesInternalDefault() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed accounts
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Act: Create recurring rule with 0L (missing/invalid) account ID
        val ruleWithMissingAccount = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = 0L,  // MISSING - should use internal default
            categoryId = expenseCategoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        val ruleId = createRecurringTransactionUseCase(ruleWithMissingAccount)
        assertNotNull(ruleId)

        // Assert: Internal default account must be used
        val savedRule = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedRule)
        assertEquals(
            "Missing account ID should resolve to internal default",
            internalDefaultAccountId,
            savedRule?.accountId
        )
    }

    @Test(expected = DomainException.InvalidTransactionId::class)
    fun createRecurringRule_withNonZeroId_throwsInvalidTransactionId() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to create with non-zero ID (update operation, not creation)
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

        createRecurringTransactionUseCase(invalidRule)
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun createNewRecurringTransfer_notSupported_throwsInvalidTransfer() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to create a TRANSFER recurring rule (not supported)
        val transferRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.TRANSFER,
            amount = Money(10000L, "USD"),
            accountId = 0L,
            destinationAccountId = 0L,
            categoryId = null,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = java.time.LocalDate.now(),
            nextOccurrence = java.time.LocalDate.now().plusDays(7),
            isActive = true
        )

        createRecurringTransactionUseCase(transferRule)
    }

    @Test
    fun createNewRecurringExpense_preservesCurrencyBehavior() = runBlocking<Unit> {
        // Arrange - Verify currency validation still works
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        recurringTransactionRepository = FakeRecurringTransactionRepository()
        
        createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(
            recurringTransactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getRecurringTransactionUseCase = GetRecurringTransactionUseCase(recurringTransactionRepository)

        // Seed internal default account (USD) alongside legacy USD and UZS accounts
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        usdAccountId = accountRepository.insertAccount(
            Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        uzsAccountId = accountRepository.insertAccount(
            Account(name = "UZS Card", type = AccountType.CARD, initialBalance = Money(50000000L, "UZS"))
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Act: Create recurring rule with USD amount and USD account (valid)
        val validRule = RecurringTransaction(
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

        val ruleId = createRecurringTransactionUseCase(validRule)
        assertNotNull(ruleId)

        // Assert: Rule created successfully with correct currency and internal default account
        val savedRule = getRecurringTransactionUseCase(ruleId)
        assertNotNull(savedRule)
        assertEquals("USD", savedRule?.amount?.currencyCode)
        assertEquals(internalDefaultAccountId, savedRule?.accountId)
    }

    private var aiSuggestedAccountId: Long = 0L
    private var usdAccountId: Long = 0L
    private var uzsAccountId: Long = 0L
}
