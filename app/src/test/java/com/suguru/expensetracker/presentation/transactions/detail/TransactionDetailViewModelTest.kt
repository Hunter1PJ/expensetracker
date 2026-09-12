package com.suguru.expensetracker.presentation.transactions.detail

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
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.transaction.DeleteTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var getTransactionUseCase: GetTransactionUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        getTransactionUseCase = GetTransactionUseCase(transactionRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
        deleteTransactionUseCase = DeleteTransactionUseCase(
            transactionRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(txId: Long): TransactionDetailViewModel {
        return TransactionDetailViewModel(
            transactionId = txId,
            getTransactionUseCase = getTransactionUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            zoneId = zoneId
        )
    }

    @Test
    fun `loads transaction details successfully`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Weekly groceries"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.transaction)
        assertEquals("Checking", state.sourceAccount?.name)
        assertEquals("Groceries", state.category?.name)
        assertEquals("Weekly groceries", state.note)
    }

    @Test
    fun `deleting transaction successfully triggers callback and deletes from repo`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(2500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Weekly groceries"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        var onSuccessCalled = false
        viewModel.deleteTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)
        assertTrue(transactionRepository.getTransactionById(txId) == null)
    }
}
