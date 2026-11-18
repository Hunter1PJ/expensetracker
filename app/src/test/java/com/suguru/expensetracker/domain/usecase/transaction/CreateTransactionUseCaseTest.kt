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
 * Regression tests for CreateTransactionUseCase to verify internal default account enforcement.
 * These tests prove that new Expense/Income transactions always use the internal default account,
 * regardless of what account ID is supplied by UI state or AI parsing.
 */
class CreateTransactionUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var getTransactionUseCase: GetTransactionUseCase

    private var internalDefaultAccountId: Long = 0L
    private var otherUsdAccountId: Long = 0L
    private var expenseCategoryId: Long = 0L
    private var incomeCategoryId: Long = 0L

    @Test
    fun createNewExpense_enforcesInternalDefaultAccount_evenWhenDifferentValidIdSupplied() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null  // No provisioner for tests - will use internal default directly
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

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

        // Act: Create a new Expense transaction with a DIFFERENT valid account ID supplied
        val transactionWithOtherAccount = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = otherUsdAccountId,  // SUPPLYING WRONG ACCOUNT - should be ignored
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        val transactionId = createTransactionUseCase(transactionWithOtherAccount)
        assertNotNull(transactionId)

        // Assert: Transaction must use internal default account, NOT the supplied other account
        val savedTransaction = getTransactionUseCase(transactionId)
        assertNotNull(savedTransaction)
        assertEquals(
            "New Expense must use internal default account, not supplied account ID",
            internalDefaultAccountId,
            savedTransaction?.accountId
        )
    }

    @Test
    fun createNewIncome_enforcesInternalDefaultAccount_evenWhenDifferentValidIdSupplied() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

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

        // Act: Create a new Income transaction with a DIFFERENT valid account ID supplied
        val transactionWithOtherAccount = Transaction(
            id = 0L,
            type = TransactionType.INCOME,
            amount = Money(500000L, "USD"),
            accountId = otherUsdAccountId,  // SUPPLYING WRONG ACCOUNT - should be ignored
            categoryId = incomeCategoryId,
            transactionTime = java.time.Instant.now()
        )

        val transactionId = createTransactionUseCase(transactionWithOtherAccount)
        assertNotNull(transactionId)

        // Assert: Transaction must use internal default account, NOT the supplied other account
        val savedTransaction = getTransactionUseCase(transactionId)
        assertNotNull(savedTransaction)
        assertEquals(
            "New Income must use internal default account, not supplied account ID",
            internalDefaultAccountId,
            savedTransaction?.accountId
        )
    }

    @Test
    fun createNewExpense_withAiSuppliedAccountId_enforcesInternalDefault() = runBlocking<Unit> {
        // Arrange - Simulate AI parsing that suggests a specific account
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

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
        val aiSuggestedTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(15000L, "USD"),
            accountId = aiSuggestedAccountId,  // AI SUPPLIED - should be IGNORED for new records
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        val transactionId = createTransactionUseCase(aiSuggestedTransaction)
        assertNotNull(transactionId)

        // Assert: Internal default account must be used, NOT AI-suggested account
        val savedTransaction = getTransactionUseCase(transactionId)
        assertNotNull(savedTransaction)
        assertEquals(
            "AI-supplied account ID cannot override internal default for new transactions",
            internalDefaultAccountId,
            savedTransaction?.accountId
        )
    }

    @Test
    fun createNewExpense_withMissingAccountId_enforcesInternalDefault() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        // Seed accounts
        internalDefaultAccountId = accountRepository.insertAccount(
            Account(name = "Internal Default", type = AccountType.CASH, initialBalance = Money(0L, "USD"), internalKey = "default")
        )
        expenseCategoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
        )

        // Act: Create transaction with 0L (missing/invalid) account ID
        val transactionWithMissingAccount = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = 0L,  // MISSING - should use internal default
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        val transactionId = createTransactionUseCase(transactionWithMissingAccount)
        assertNotNull(transactionId)

        // Assert: Internal default account must be used
        val savedTransaction = getTransactionUseCase(transactionId)
        assertNotNull(savedTransaction)
        assertEquals(
            "Missing account ID should resolve to internal default",
            internalDefaultAccountId,
            savedTransaction?.accountId
        )
    }

    @Test(expected = DomainException.InvalidTransactionId::class)
    fun createTransaction_withNonZeroId_throwsInvalidTransactionId() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        // Act & Assert: Try to create with non-zero ID (update operation, not creation)
        val invalidTransaction = Transaction(
            id = 999999L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = 0L,
            categoryId = 0L,
            transactionTime = java.time.Instant.now()
        )

        createTransactionUseCase(invalidTransaction)
    }

    @Test
    fun createNewTransfer_withValidAccounts_preservesSourceAndDestination() = runBlocking<Unit> {
        // Arrange
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )

        val sourceId = accountRepository.insertAccount(
            Account(name = "Source", type = AccountType.CASH, initialBalance = Money(0L, "USD"))
        )
        val destinationId = accountRepository.insertAccount(
            Account(name = "Destination", type = AccountType.BANK, initialBalance = Money(0L, "USD"))
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

        val transferTransaction = Transaction(
            id = 0L,
            type = TransactionType.TRANSFER,
            amount = Money(10000L, "USD"),
            accountId = sourceId,
            destinationAccountId = destinationId,
            categoryId = null,
            transactionTime = java.time.Instant.now()
        )

        val transferId = createTransactionUseCase(transferTransaction)
        val savedTransfer = getTransactionUseCase(transferId)
        assertNotNull(savedTransfer)
        assertEquals(TransactionType.TRANSFER, savedTransfer?.type)
        assertEquals(sourceId, savedTransfer?.accountId)
        assertEquals(destinationId, savedTransfer?.destinationAccountId)
        assertNull(savedTransfer?.categoryId)
    }

    @Test
    fun createNewExpense_preservesCurrencyBehavior() = runBlocking<Unit> {
        // Arrange - Verify currency validation still works
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        
        createTransactionUseCase = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository,
            null
        )
        getTransactionUseCase = GetTransactionUseCase(transactionRepository)

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

        // Act: Create transaction with USD amount and USD account (valid)
        val validTransaction = Transaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2500L, "USD"),
            accountId = usdAccountId,
            categoryId = expenseCategoryId,
            transactionTime = java.time.Instant.now()
        )

        val transactionId = createTransactionUseCase(validTransaction)
        assertNotNull(transactionId)

        // Assert: Transaction created successfully with correct currency and internal default account
        val savedTransaction = getTransactionUseCase(transactionId)
        assertNotNull(savedTransaction)
        assertEquals("USD", savedTransaction?.amount?.currencyCode)
        assertEquals(internalDefaultAccountId, savedTransaction?.accountId)
    }

    private var aiSuggestedAccountId: Long = 0L
    private var usdAccountId: Long = 0L
    private var uzsAccountId: Long = 0L
}
