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
import com.example.domain.usecase.balance.GetAccountBalanceUseCase
import com.example.domain.usecase.balance.ObserveAccountBalanceUseCase
import com.example.domain.usecase.transaction.CreateTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BalanceUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var getAccountBalanceUseCase: GetAccountBalanceUseCase
    private lateinit var observeAccountBalanceUseCase: ObserveAccountBalanceUseCase

    private var accountAId: Long = 0L
    private var accountBId: Long = 0L
    private var salaryCategoryId: Long = 0L
    private var foodCategoryId: Long = 0L

    @Before
    fun setUp() {
        runBlocking {
            accountRepository = FakeAccountRepository()
            categoryRepository = FakeCategoryRepository()
            transactionRepository = FakeTransactionRepository()

            createTransactionUseCase = CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository)
            getAccountBalanceUseCase = GetAccountBalanceUseCase(accountRepository, transactionRepository)
            observeAccountBalanceUseCase = ObserveAccountBalanceUseCase(accountRepository, transactionRepository)

            // Account A starts with $1,000.00 (100,000 minor units)
            accountAId = accountRepository.insertAccount(
                Account(name = "Account A", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
            )

            // Account B starts with $200.00 (20,000 minor units)
            accountBId = accountRepository.insertAccount(
                Account(name = "Account B", type = AccountType.SAVINGS, initialBalance = Money(20000L, "USD"))
            )

            salaryCategoryId = categoryRepository.insertCategory(
                Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#4CAF50")
            )
            foodCategoryId = categoryRepository.insertCategory(
                Category(name = "Food", type = CategoryType.EXPENSE, iconName = "restaurant", colorHex = "#F44336")
            )
        }
    }

    @Test
    fun initialBalance_whenNoTransactions_matchesInitialAmount() {
        runBlocking {
            val balanceA = getAccountBalanceUseCase(accountAId)
            assertEquals(100000L, balanceA.amountInMinorUnits)
            assertEquals("USD", balanceA.currencyCode)

            val observedBalance = observeAccountBalanceUseCase(accountAId).first()
            assertNotNull(observedBalance)
            assertEquals(100000L, observedBalance?.amountInMinorUnits)
        }
    }

    @Test
    fun calculateBalance_withIncomeExpensesAndTransfers_isExact() {
        runBlocking {
            // 1. Add Income to Account A: +$500.00 (50000 units) -> Expected A: $1,500.00 (150000)
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.INCOME,
                    amount = Money(50000L, "USD"),
                    accountId = accountAId,
                    categoryId = salaryCategoryId,
                    transactionTime = Instant.now()
                )
            )

            // 2. Add Expense from Account A: -$120.50 (12050 units) -> Expected A: $1,379.50 (137950)
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money(12050L, "USD"),
                    accountId = accountAId,
                    categoryId = foodCategoryId,
                    transactionTime = Instant.now()
                )
            )

            // 3. Transfer from Account A to Account B: $300.00 (30000 units)
            // -> Expected A: 137950 - 30000 = 107950 ($1,079.50)
            // -> Expected B: 20000 + 30000 = 50000 ($500.00)
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.TRANSFER,
                    amount = Money(30000L, "USD"),
                    accountId = accountAId,
                    destinationAccountId = accountBId,
                    categoryId = null,
                    transactionTime = Instant.now()
                )
            )

            val balanceA = getAccountBalanceUseCase(accountAId)
            assertEquals(107950L, balanceA.amountInMinorUnits)
            assertEquals("USD", balanceA.currencyCode)

            val balanceB = getAccountBalanceUseCase(accountBId)
            assertEquals(50000L, balanceB.amountInMinorUnits)
            assertEquals("USD", balanceB.currencyCode)
        }
    }

    @Test(expected = DomainException.AccountNotFound::class)
    fun getBalance_nonExistentAccount_throwsException() {
        runBlocking {
            getAccountBalanceUseCase(9999L)
        }
    }
}
