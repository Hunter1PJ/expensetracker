package com.suguru.expensetracker.presentation.accounts.add

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.CurrencyInfo
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.usecase.account.CreateAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.GetAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.UpdateAccountUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class AddAccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var createAccountUseCase: CreateAccountUseCase
    private lateinit var updateAccountUseCase: UpdateAccountUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        accountRepository = FakeAccountRepository()
        createAccountUseCase = CreateAccountUseCase(accountRepository)
        updateAccountUseCase = UpdateAccountUseCase(accountRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(accountId: Long = 0L): AddAccountViewModel {
        return AddAccountViewModel(
            accountId = accountId,
            createAccountUseCase = createAccountUseCase,
            updateAccountUseCase = updateAccountUseCase,
            getAccountUseCase = getAccountUseCase
        )
    }

    @Test
    fun `create mode initializes with editable currency and balance`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEditMode)
        assertTrue(state.isCurrencyEditable)
        assertTrue(state.isInitialBalanceEditable)
        assertEquals("USD", state.selectedCurrency.currencyCode)
    }

    @Test
    fun `blank name fails validation`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("   ")
        viewModel.saveAccount()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AddAccountError.NameRequired, state.error)
        assertFalse(state.isSavedSuccessfully)
    }

    @Test
    fun `invalid initial balance fails validation`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("Checking")
        viewModel.onInitialBalanceChanged("12.34.56")
        viewModel.saveAccount()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is AddAccountError.InvalidInitialBalance)
        assertFalse(state.isSavedSuccessfully)
    }

    @Test
    fun `successfully create new account with correct minor units`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged("Main Savings")
        viewModel.onTypeChanged(AccountType.SAVINGS)
        viewModel.onCurrencyChanged(CurrencyInfo.findByCode("USD"))
        viewModel.onInitialBalanceChanged("250.50")
        viewModel.onIconChanged("savings")
        viewModel.onColorChanged("#10B981")

        viewModel.saveAccount()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccessfully)
        assertNull(state.error)

        val accounts = accountRepository.observeActiveAccounts().first()
        assertEquals(1, accounts.size)
        val created = accounts.first()
        assertEquals("Main Savings", created.name)
        assertEquals(AccountType.SAVINGS, created.type)
        assertEquals(25050L, created.initialBalance.amountInMinorUnits)
        assertEquals("USD", created.initialBalance.currencyCode)
    }

    @Test
    fun `edit mode locks currency and initial balance`() = runTest(testDispatcher) {
        val accId = accountRepository.insertAccount(
            Account(
                name = "Original Name",
                type = AccountType.BANK,
                initialBalance = Money(150000L, "EUR"),
                iconName = "account_balance",
                colorHex = "#3B82F6"
            )
        )

        val viewModel = createViewModel(accId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertFalse(state.isCurrencyEditable)
        assertFalse(state.isInitialBalanceEditable)
        assertEquals("Original Name", state.name)
        assertEquals("EUR", state.selectedCurrency.currencyCode)
        assertEquals("1500.00", state.initialBalanceInput)

        // Update name, type, and visual styling
        viewModel.onNameChanged("Updated Name")
        viewModel.onTypeChanged(AccountType.INVESTMENT)
        viewModel.onColorChanged("#8B5CF6")
        viewModel.saveAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSavedSuccessfully)

        val updated = accountRepository.getAccountById(accId)
        assertNotNull(updated)
        assertEquals("Updated Name", updated!!.name)
        assertEquals(AccountType.INVESTMENT, updated.type)
        assertEquals("#8B5CF6", updated.colorHex)
        // Currency and initial balance MUST remain intact
        assertEquals(150000L, updated.initialBalance.amountInMinorUnits)
        assertEquals("EUR", updated.initialBalance.currencyCode)
    }
}
