package com.suguru.expensetracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.suguru.expensetracker.data.ai.AiRepositoryImpl
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.repository.RoomAccountRepository
import com.suguru.expensetracker.data.repository.RoomCategoryRepository
import com.suguru.expensetracker.data.repository.RoomTransactionRepository
import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiProvider
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload
import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionPersistenceIntegrationTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var accountRepository: RoomAccountRepository
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var transactionRepository: RoomTransactionRepository
    private lateinit var createTransaction: CreateTransactionUseCase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        transactionRepository = RoomTransactionRepository(database.transactionDao())
        createTransaction = CreateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validExpenseIsPersistedOnceWithMinorUnitsAndValidReferences() = runBlocking {
        val accountId = accountRepository.insertAccount(
            Account(name = "Cash", type = AccountType.CASH, initialBalance = Money(0, "USD"))
        )
        val categoryId = categoryRepository.insertCategory(
            Category(name = "Food", type = CategoryType.EXPENSE, iconName = "food", colorHex = "#000000")
        )
        val expense = transaction(
            type = TransactionType.EXPENSE,
            amount = Money(2599, "USD"),
            accountId = accountId,
            categoryId = categoryId
        )

        val transactionId = createTransaction(expense)
        val stored = transactionRepository.getTransactionById(transactionId)

        assertNotNull(stored)
        assertEquals(1, transactionRepository.getRecentTransactions(10).size)
        assertEquals(2599L, stored!!.amount.amountInMinorUnits)
        assertEquals("USD", stored.amount.currencyCode)
        assertEquals(TransactionType.EXPENSE, stored.type)
        assertEquals(accountId, stored.accountId)
        assertEquals(categoryId, stored.categoryId)
        assertNotNull(accountRepository.getAccountById(stored.accountId))
        assertNotNull(categoryRepository.getCategoryById(stored.categoryId!!))
    }

    @Test
    fun transferPreservesAccountsAndInvalidTransactionIsNotWritten() = runBlocking {
        val sourceId = accountRepository.insertAccount(
            Account(name = "Cash", type = AccountType.CASH, initialBalance = Money(0, "USD"))
        )
        val destinationId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(0, "USD"))
        )
        val transferId = createTransaction(
            transaction(
                type = TransactionType.TRANSFER,
                amount = Money(5000, "USD"),
                accountId = sourceId,
                destinationAccountId = destinationId
            )
        )
        val storedTransfer = transactionRepository.getTransactionById(transferId)!!

        assertEquals(TransactionType.TRANSFER, storedTransfer.type)
        assertEquals(sourceId, storedTransfer.accountId)
        assertEquals(destinationId, storedTransfer.destinationAccountId)

        try {
            createTransaction(
                transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money(0, "USD"),
                    accountId = sourceId,
                    categoryId = null
                )
            )
            throw AssertionError("Invalid transaction should fail validation")
        } catch (_: DomainException.InvalidAmount) {
            assertEquals(1, transactionRepository.getRecentTransactions(10).size)
        }
    }

    @Test
    fun offlineCandidateExtractionDoesNotWriteTransactionsToRoom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
                File(context.cacheDir, "ai-room-test-${System.nanoTime()}.preferences_pb")
            }
            val repository = AiRepositoryImpl(
                aiProvider = UnusedAiProvider,
                secureKeyStorage = EmptySecureKeyStorage,
                dataStore = dataStore
            )

            val result = repository.extractTransactions("paid 50 dollars", "USD")

            assertTrue(result is AiResult.Success)
            assertEquals(0, transactionRepository.getRecentTransactions(10).size)
        } finally {
            scope.cancel()
        }
    }

    private fun transaction(
        type: TransactionType,
        amount: Money,
        accountId: Long,
        categoryId: Long? = null,
        destinationAccountId: Long? = null
    ) = Transaction(
        type = type,
        amount = amount,
        accountId = accountId,
        categoryId = categoryId,
        destinationAccountId = destinationAccountId,
        transactionTime = Instant.parse("2026-11-12T12:00:00Z")
    )

    private object EmptySecureKeyStorage : SecureKeyStorage {
        override fun saveKey(key: String) = Unit
        override fun getKey(): String? = null
        override fun deleteKey() = Unit
        override fun hasKey() = false
    }

    private object UnusedAiProvider : AiProvider {
        override val providerId = "test"
        override val displayName = "Test"
        override suspend fun isConfigured() = false
        override suspend fun validateKey(apiKey: String) = AiResult.Failure(AiError.MissingApiKey)
        override suspend fun extractFinancialTransactions(
            prompt: String,
            config: AiConfiguration
        ) = AiResult.Failure(AiError.MissingApiKey)
    }
}