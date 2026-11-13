package com.suguru.expensetracker.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class InternalDefaultAccountProvisioningTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var settingsRepository: TestSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = TestSettingsRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshDatabase_provisionsOneUzsInternalAccount() = runBlocking {
        val provisioner = InternalDefaultAccountProvisioner(database, settingsRepository)

        val result = provisioner.ensureProvisioned()

        assertTrue(result is InternalDefaultAccountProvisioningResult.Ready)
        val internalAccount = (result as InternalDefaultAccountProvisioningResult.Ready).internalAccount
        assertEquals("default", database.accountDao().getInternalAccount("default")?.internalKey)
        assertEquals("UZS", internalAccount.initialBalance.currencyCode)
        assertEquals(0L, internalAccount.initialBalance.amountInMinorUnits)
        assertEquals(1, database.accountDao().getActiveAccounts().size)
    }

    @Test
    fun repeatedInitialization_returnsSameId() = runBlocking {
        val provisioner = InternalDefaultAccountProvisioner(database, settingsRepository)

        val first = provisioner.ensureProvisioned()
        val second = provisioner.ensureProvisioned()

        assertTrue(first is InternalDefaultAccountProvisioningResult.Ready)
        assertTrue(second is InternalDefaultAccountProvisioningResult.Ready)
        val firstId = (first as InternalDefaultAccountProvisioningResult.Ready).internalAccount.id
        val secondId = (second as InternalDefaultAccountProvisioningResult.Ready).internalAccount.id
        assertEquals(firstId, secondId)
        assertEquals(1, database.accountDao().getAccountCount())
    }

    @Test
    fun concurrentInitialization_doesNotCreateDuplicates() = runBlocking {
        val provisioner = InternalDefaultAccountProvisioner(database, settingsRepository)

        val results = (1..12).map {
            async { provisioner.ensureProvisioned() }
        }.awaitAll()

        assertEquals(1, database.accountDao().getAccountCount())
        val accountIds = results.mapNotNull { result ->
            (result as? InternalDefaultAccountProvisioningResult.Ready)?.internalAccount?.id
        }.distinct()
        assertEquals(1, accountIds.size)
    }

    @Test
    fun singleExistingActiveAccount_withoutPreference_reusesCurrency() = runBlocking {
        val existingAccountId = database.accountDao().insertAccount(
            com.suguru.expensetracker.data.local.entity.AccountEntity(
                id = 10L,
                name = "Cash",
                type = AccountType.CASH,
                initialBalanceAmountInMinorUnits = 2500L,
                initialBalanceCurrencyCode = "EUR",
                iconName = "wallet",
                colorHex = "#112233",
                isArchived = false,
                createdAt = Instant.now()
            )
        )
        settingsRepository.setPreferredCurrency(null)

        val result = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()

        assertTrue(result is InternalDefaultAccountProvisioningResult.Ready)
        val internalAccount = (result as InternalDefaultAccountProvisioningResult.Ready).internalAccount
        assertEquals("EUR", internalAccount.initialBalance.currencyCode)
        assertEquals(existingAccountId, 10L)
        assertEquals(2500L, database.accountDao().getAccountById(existingAccountId)?.initialBalanceAmountInMinorUnits)
    }

    @Test
    fun savedPreferredCurrency_takesPrecedence() = runBlocking {
        settingsRepository.setPreferredCurrency("JPY")

        val result = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()

        assertTrue(result is InternalDefaultAccountProvisioningResult.Ready)
        val internalAccount = (result as InternalDefaultAccountProvisioningResult.Ready).internalAccount
        assertEquals("JPY", internalAccount.initialBalance.currencyCode)
    }

    @Test
    fun multipleActiveAccounts_withoutPreference_requiresCurrencySelection() = runBlocking {
        database.accountDao().insertAccount(
            com.suguru.expensetracker.data.local.entity.AccountEntity(
                id = 1L,
                name = "Cash",
                type = AccountType.CASH,
                initialBalanceAmountInMinorUnits = 100L,
                initialBalanceCurrencyCode = "USD",
                iconName = "wallet",
                colorHex = "#111111",
                isArchived = false,
                createdAt = Instant.now()
            )
        )
        database.accountDao().insertAccount(
            com.suguru.expensetracker.data.local.entity.AccountEntity(
                id = 2L,
                name = "Savings",
                type = AccountType.SAVINGS,
                initialBalanceAmountInMinorUnits = 200L,
                initialBalanceCurrencyCode = "EUR",
                iconName = "account_balance",
                colorHex = "#222222",
                isArchived = false,
                createdAt = Instant.now()
            )
        )
        settingsRepository.setPreferredCurrency(null)

        val result = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()

        assertTrue(result is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection)
        assertEquals(0, database.accountDao().getInternalAccount("default")?.let { 1 } ?: 0)
    }

    @Test
    fun existingAccountIds_andTransactionReferences_remainStable() = runBlocking {
        val accountId = database.accountDao().insertAccount(
            com.suguru.expensetracker.data.local.entity.AccountEntity(
                id = 15L,
                name = "Checking",
                type = AccountType.BANK,
                initialBalanceAmountInMinorUnits = 500L,
                initialBalanceCurrencyCode = "USD",
                iconName = "bank",
                colorHex = "#333333",
                isArchived = false,
                createdAt = Instant.now()
            )
        )
        val transactionId = database.transactionDao().insertTransaction(
            com.suguru.expensetracker.data.local.entity.TransactionEntity(
                id = 21L,
                type = com.suguru.expensetracker.domain.model.TransactionType.EXPENSE,
                amountInMinorUnits = 50L,
                currencyCode = "USD",
                accountId = accountId,
                destinationAccountId = null,
                categoryId = null,
                transactionTime = Instant.now(),
                note = "coffee",
                recurringRuleId = null,
                recurringOccurrenceDate = null,
                createdAt = Instant.now()
            )
        )
        val ruleId = database.recurringTransactionDao().insertRecurringTransaction(
            com.suguru.expensetracker.data.local.entity.RecurringTransactionEntity(
                id = 30L,
                type = com.suguru.expensetracker.domain.model.TransactionType.TRANSFER,
                amountInMinorUnits = 100L,
                currencyCode = "USD",
                accountId = accountId,
                destinationAccountId = null,
                categoryId = null,
                frequency = com.suguru.expensetracker.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = java.time.LocalDate.now(),
                endDate = null,
                nextOccurrence = java.time.LocalDate.now(),
                note = "scheduled",
                isActive = true
            )
        )

        val result = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()

        assertTrue(result is InternalDefaultAccountProvisioningResult.Ready)
        assertEquals(accountId, 15L)
        assertEquals(500L, database.accountDao().getAccountById(accountId)?.initialBalanceAmountInMinorUnits)
        assertEquals(transactionId, 21L)
        assertEquals(ruleId, 30L)
        assertEquals("USD", database.transactionDao().getTransactionById(transactionId)?.currencyCode)
    }

    private class TestSettingsRepository(
        initialPreferredCurrency: String? = null
    ) : SettingsRepository {
        private val state = MutableStateFlow(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                preferredCurrencyCode = initialPreferredCurrency,
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
                timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
                showCurrencyCode = false,
                confirmBeforeDelete = true
            )
        )

        override val settings: Flow<AppSettings> = state

        override suspend fun restoreSettings(value: AppSettings) {
            state.value = value
        }

        override suspend fun setThemeMode(value: ThemeMode) = Unit

        override suspend fun setPreferredCurrency(value: String?) {
            state.value = state.value.copy(preferredCurrencyCode = value)
        }

        override suspend fun setWeekStart(value: WeekStart) = Unit

        override suspend fun setDateFormat(value: DateFormatPreference) = Unit

        override suspend fun setTimeFormat(value: TimeFormatPreference) = Unit

        override suspend fun setShowCurrencyCode(value: Boolean) = Unit

        override suspend fun setConfirmBeforeDelete(value: Boolean) = Unit

        override val lastBackupAt: Flow<String?> = MutableStateFlow(null)
        override val lastBackupFileName: Flow<String?> = MutableStateFlow(null)

        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) = Unit
    }
}
