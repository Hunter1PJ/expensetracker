package com.example.domain.usecase

import com.example.domain.error.DomainException
import com.example.domain.fake.FakeAccountRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Money
import com.example.domain.usecase.account.ArchiveAccountUseCase
import com.example.domain.usecase.account.CreateAccountUseCase
import com.example.domain.usecase.account.GetAccountUseCase
import com.example.domain.usecase.account.ObserveActiveAccountsUseCase
import com.example.domain.usecase.account.UpdateAccountUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var createAccountUseCase: CreateAccountUseCase
    private lateinit var updateAccountUseCase: UpdateAccountUseCase
    private lateinit var archiveAccountUseCase: ArchiveAccountUseCase
    private lateinit var getAccountUseCase: GetAccountUseCase
    private lateinit var observeActiveAccountsUseCase: ObserveActiveAccountsUseCase

    @Before
    fun setUp() {
        accountRepository = FakeAccountRepository()
        createAccountUseCase = CreateAccountUseCase(accountRepository)
        updateAccountUseCase = UpdateAccountUseCase(accountRepository)
        archiveAccountUseCase = ArchiveAccountUseCase(accountRepository)
        getAccountUseCase = GetAccountUseCase(accountRepository)
        observeActiveAccountsUseCase = ObserveActiveAccountsUseCase(accountRepository)
    }

    @Test
    fun createAccount_withValidData_succeeds() {
        runBlocking {
            val account = Account(
                name = "Main Checking",
                type = AccountType.BANK,
                initialBalance = Money(100000L, "USD")
            )
            val id = createAccountUseCase(account)
            assertEquals(1L, id)

            val retrieved = getAccountUseCase(id)
            assertNotNull(retrieved)
            assertEquals("Main Checking", retrieved?.name)
            assertEquals(100000L, retrieved?.initialBalance?.amountInMinorUnits)
            assertEquals("USD", retrieved?.initialBalance?.currencyCode)
        }
    }

    @Test(expected = DomainException.InvalidAccount::class)
    fun createAccount_withBlankName_throwsException() {
        runBlocking {
            val account = Account(
                name = "   ",
                type = AccountType.CASH,
                initialBalance = Money(5000L, "USD")
            )
            createAccountUseCase(account)
        }
    }

    @Test(expected = DomainException.InvalidCurrency::class)
    fun createAccount_withBlankCurrency_throwsException() {
        runBlocking {
            val account = Account(
                name = "Wallet",
                type = AccountType.CASH,
                initialBalance = Money(5000L, "")
            )
            createAccountUseCase(account)
        }
    }

    @Test(expected = DomainException.InvalidAccount::class)
    fun createAccount_withNonZeroId_throwsException() {
        runBlocking {
            val account = Account(
                id = 5L,
                name = "Wallet",
                type = AccountType.CASH,
                initialBalance = Money(5000L, "USD")
            )
            createAccountUseCase(account)
        }
    }

    @Test
    fun updateAccount_withValidData_updatesSuccessfully() {
        runBlocking {
            val id = createAccountUseCase(
                Account(name = "Original", type = AccountType.CASH, initialBalance = Money(1000L, "USD"))
            )
            val original = getAccountUseCase(id)!!
            updateAccountUseCase(original.copy(name = "Updated"))

            val updated = getAccountUseCase(id)
            assertEquals("Updated", updated?.name)
        }
    }

    @Test(expected = DomainException.AccountNotFound::class)
    fun updateAccount_withNonExistentId_throwsException() {
        runBlocking {
            updateAccountUseCase(
                Account(id = 999L, name = "Ghost", type = AccountType.CASH, initialBalance = Money(1000L, "USD"))
            )
        }
    }

    @Test
    fun archiveAccount_hidesFromActiveAccounts() {
        runBlocking {
            val id = createAccountUseCase(
                Account(name = "Old Account", type = AccountType.CASH, initialBalance = Money(1000L, "USD"))
            )
            assertEquals(1, observeActiveAccountsUseCase().first().size)

            archiveAccountUseCase(id)
            val active = observeActiveAccountsUseCase().first()
            assertTrue(active.isEmpty())

            val archived = getAccountUseCase(id)
            assertTrue(archived?.isArchived == true)
        }
    }
}
