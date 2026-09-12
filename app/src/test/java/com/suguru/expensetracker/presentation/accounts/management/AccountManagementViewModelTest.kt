package com.suguru.expensetracker.presentation.accounts.management

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.usecase.account.ArchiveAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.ObserveActiveAccountsUseCase
import com.suguru.expensetracker.domain.usecase.balance.ObserveAccountBalanceUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase
    private lateinit var observeAccountBalanceUseCase: ObserveAccountBalanceUseCase
    private lateinit var archiveAccountUseCase: ArchiveAccountUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        transactionRepository = FakeTransactionRepository()

        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
        observeAccountBalanceUseCase = ObserveAccountBalanceUseCase(accountRepository, transactionRepository)
        archiveAccountUseCase = ArchiveAccountUseCase(accountRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AccountManagementViewModel {
        return AccountManagementViewModel(
            observeActiveAccountsUseCase = observeActiveAccountsUseCase,
            observeAccountBalanceUseCase = observeAccountBalanceUseCase,
            archiveAccountUseCase = archiveAccountUseCase
        )
    }

    @Test
    fun `initial state loads active accounts with balances`() = runTest(testDispatcher) {
        val acc1 = Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(50000L, "USD"))
        val acc2 = Account(name = "Cash", type = AccountType.CASH, initialBalance = Money(2000L, "USD"))
        accountRepository.insertAccount(acc1)
        accountRepository.insertAccount(acc2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.accounts.size)
        assertEquals(50000L, state.accounts[0].balance?.amountInMinorUnits)
        assertEquals(2000L, state.accounts[1].balance?.amountInMinorUnits)
    }

    @Test
    fun `archive confirmation dialog flow and execution`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(50000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val account = viewModel.uiState.value.accounts.first().account
        viewModel.onArchiveClicked(account)

        assertEquals(account, viewModel.uiState.value.accountToArchive)

        viewModel.onConfirmArchive()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.accountToArchive)
        assertFalse(viewModel.uiState.value.isArchiving)
        assertTrue(viewModel.uiState.value.accounts.isEmpty())

        val dbAccount = accountRepository.getAccountById(accId)
        assertNotNull(dbAccount)
        assertTrue(dbAccount!!.isArchived)
    }

    @Test
    fun `dismiss archive dialog clears selection`() = runTest(testDispatcher) {
        accountRepository.insertAccount(
            Account(name = "Bank", type = AccountType.BANK, initialBalance = Money(50000L, "USD"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val account = viewModel.uiState.value.accounts.first().account
        viewModel.onArchiveClicked(account)
        assertNotNull(viewModel.uiState.value.accountToArchive)

        viewModel.onDismissArchiveDialog()
        assertNull(viewModel.uiState.value.accountToArchive)
    }
}
