package com.example.domain.usecase

import com.example.domain.error.DomainException
import com.example.domain.fake.FakeAccountRepository
import com.example.domain.fake.FakeCategoryRepository
import com.example.domain.fake.FakeTransactionRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.domain.usecase.transaction.GetTransactionUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TransactionUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var updateTransactionUseCase: UpdateTransactionUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase
    private lateinit var getTransactionUseCase: GetTransactionUseCase

    private var usdAccountId: Long = 0L
    private var uzsAccountId: Long = 0L
    private var archivedAccountId: Long = 0L
    private var secondUsdAccountId: Long = 0L

    private var expenseCategoryId: Long = 0L
    private var incomeCategoryId: Long = 0L
    private var archivedCategoryId: Long = 0L

    @Before
    fun setUp() {
        runBlocking {
            accountRepository = FakeAccountRepository()
            categoryRepository = FakeCategoryRepository()
            transactionRepository = FakeTransactionRepository()

            createTransactionUseCase = CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository)
            updateTransactionUseCase = UpdateTransactionUseCase(transactionRepository, accountRepository, categoryRepository)
            deleteTransactionUseCase = DeleteTransactionUseCase(transactionRepository)
            getTransactionUseCase = GetTransactionUseCase(transactionRepository)

            // Seed accounts
            usdAccountId = accountRepository.insertAccount(
                Account(name = "USD Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
            )
            secondUsdAccountId = accountRepository.insertAccount(
                Account(name = "USD Savings", type = AccountType.SAVINGS, initialBalance = Money(50000L, "USD"))
            )
            uzsAccountId = accountRepository.insertAccount(
                Account(name = "UZS Card", type = AccountType.CARD, initialBalance = Money(50000000L, "UZS"))
            )
            archivedAccountId = accountRepository.insertAccount(
                Account(name = "Old Card", type = AccountType.CARD, initialBalance = Money(1000L, "USD"), isArchived = true)
            )

            // Seed categories
            expenseCategoryId = categoryRepository.insertCategory(
                Category(name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#F44336")
            )
            incomeCategoryId = categoryRepository.insertCategory(
                Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#4CAF50")
            )
            archivedCategoryId = categoryRepository.insertCategory(
                Category(name = "Old Category", type = CategoryType.EXPENSE, iconName = "block", colorHex = "#9E9E9E", isArchived = true)
            )
        }
    }

    // --- EXPENSE TESTS ---

    @Test
    fun createExpense_valid_succeeds() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(2500L, "USD"),
                accountId = usdAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            val id = createTransactionUseCase(tx)
            assertEquals(1L, id)

            val saved = getTransactionUseCase(id)
            assertNotNull(saved)
            assertEquals(2500L, saved?.amount?.amountInMinorUnits)
            assertEquals(usdAccountId, saved?.accountId)
        }
    }

    @Test(expected = DomainException.InvalidAmount::class)
    fun createExpense_zeroAmount_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(0L, "USD"),
                accountId = usdAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.InvalidAmount::class)
    fun createExpense_negativeAmount_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(-500L, "USD"),
                accountId = usdAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.IncompatibleCategoryType::class)
    fun createExpense_withIncomeCategory_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1500L, "USD"),
                accountId = usdAccountId,
                categoryId = incomeCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.CurrencyMismatch::class)
    fun createExpense_withCurrencyMismatch_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1500L, "UZS"),
                accountId = usdAccountId, // USD account vs UZS transaction
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.AccountArchived::class)
    fun createExpense_withArchivedAccount_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1500L, "USD"),
                accountId = archivedAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.CategoryArchived::class)
    fun createExpense_withArchivedCategory_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1500L, "USD"),
                accountId = usdAccountId,
                categoryId = archivedCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    // --- INCOME TESTS ---

    @Test
    fun createIncome_valid_succeeds() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.INCOME,
                amount = Money(500000L, "USD"),
                accountId = usdAccountId,
                categoryId = incomeCategoryId,
                transactionTime = Instant.now()
            )
            val id = createTransactionUseCase(tx)
            assertEquals(1L, id)
        }
    }

    @Test(expected = DomainException.IncompatibleCategoryType::class)
    fun createIncome_withExpenseCategory_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.INCOME,
                amount = Money(500000L, "USD"),
                accountId = usdAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    // --- TRANSFER TESTS ---

    @Test
    fun createTransfer_validSameCurrency_succeeds() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.TRANSFER,
                amount = Money(10000L, "USD"),
                accountId = usdAccountId,
                destinationAccountId = secondUsdAccountId,
                categoryId = null,
                transactionTime = Instant.now()
            )
            val id = createTransactionUseCase(tx)
            assertEquals(1L, id)
        }
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun createTransfer_toSameAccount_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.TRANSFER,
                amount = Money(10000L, "USD"),
                accountId = usdAccountId,
                destinationAccountId = usdAccountId,
                categoryId = null,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun createTransfer_withDifferentCurrencies_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.TRANSFER,
                amount = Money(10000L, "USD"),
                accountId = usdAccountId,
                destinationAccountId = uzsAccountId, // USD vs UZS
                categoryId = null,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.InvalidTransfer::class)
    fun createTransfer_withCategory_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.TRANSFER,
                amount = Money(10000L, "USD"),
                accountId = usdAccountId,
                destinationAccountId = secondUsdAccountId,
                categoryId = expenseCategoryId,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test(expected = DomainException.AccountArchived::class)
    fun createTransfer_withArchivedDestination_throwsException() {
        runBlocking {
            val tx = Transaction(
                type = TransactionType.TRANSFER,
                amount = Money(10000L, "USD"),
                accountId = usdAccountId,
                destinationAccountId = archivedAccountId,
                categoryId = null,
                transactionTime = Instant.now()
            )
            createTransactionUseCase(tx)
        }
    }

    @Test
    fun deleteTransaction_removesTransaction() {
        runBlocking {
            val id = createTransactionUseCase(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money(2500L, "USD"),
                    accountId = usdAccountId,
                    categoryId = expenseCategoryId,
                    transactionTime = Instant.now()
                )
            )
            assertNotNull(getTransactionUseCase(id))

            deleteTransactionUseCase(id)
            assertNull(getTransactionUseCase(id))
        }
    }
}
