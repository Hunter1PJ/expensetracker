package com.suguru.expensetracker.presentation.transactions.edit

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
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.category.GetCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.ObserveCategoriesByTypeUseCase
import com.suguru.expensetracker.domain.usecase.transaction.GetTransactionUseCase
import com.suguru.expensetracker.domain.usecase.transaction.UpdateTransactionUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EditTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var getTransactionUseCase: GetTransactionUseCase
    private lateinit var updateTransactionUseCase: UpdateTransactionUseCase
    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase
    private lateinit var observeCategoriesByTypeUseCase: ObserveCategoriesByTypeUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var getCategoryUseCase: GetCategoryUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()

        getTransactionUseCase = GetTransactionUseCase(transactionRepository)
        updateTransactionUseCase = UpdateTransactionUseCase(
            transactionRepository,
            accountRepository,
            categoryRepository
        )
        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
        observeCategoriesByTypeUseCase = ObserveCategoriesByTypeUseCase(categoryRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        getCategoryUseCase = GetCategoryUseCase(categoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(txId: Long): EditTransactionViewModel {
        return EditTransactionViewModel(
            transactionId = txId,
            getTransactionUseCase = getTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeCategoriesByTypeUseCase = observeCategoriesByTypeUseCase,
            getAccountUseCase = getAccountUseCase,
            getCategoryUseCase = getCategoryUseCase
        )
    }

    @Test
    fun `loads initial transaction data into edit form`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Whole Foods"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("45.00", state.amount)
        assertEquals(accId, state.selectedAccountId)
        assertEquals(catId, state.selectedCategoryId)
        assertEquals("Whole Foods", state.note)
    }

    @Test
    fun `updating transaction fields and submitting updates transaction`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Checking", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
        )
        val catId = categoryRepository.insertCategory(
            Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "cart", colorHex = "#000000")
        )
        val txId = transactionRepository.insertTransaction(
            Transaction(
                amount = Money(4500L, "USD"),
                type = TransactionType.EXPENSE,
                accountId = accId,
                categoryId = catId,
                transactionTime = Instant.parse("2026-09-01T15:00:00Z"),
                note = "Whole Foods"
            )
        )

        val viewModel = createViewModel(txId)
        advanceUntilIdle()

        viewModel.onAmountChanged("60.00")
        viewModel.onNoteChanged("Trader Joe's")

        var onSuccessCalled = false
        viewModel.saveTransaction { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)

        val updatedTx = transactionRepository.getTransactionById(txId)
        assertEquals(6000L, updatedTx?.amount?.amountInMinorUnits)
        assertEquals("Trader Joe's", updatedTx?.note)
    }
}
