package com.suguru.expensetracker.domain.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.data.repository.DataStoreSettingsRepository
import com.suguru.expensetracker.data.repository.DefaultCategorySeeder
import com.suguru.expensetracker.data.repository.RoomBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupStateClassifierProvisioningTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsRepository: DataStoreSettingsRepository
    private lateinit var backupRepository: RoomBackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = File(context.filesDir, "classifier_test_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        settingsRepository = DataStoreSettingsRepository(dataStore)
        backupRepository = RoomBackupRepository(
            db = database,
            settingsRepository = settingsRepository,
            internalDefaultAccountProvisioner = InternalDefaultAccountProvisioner(database, settingsRepository),
            appVersion = "1.0.0"
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `actual provisioned account seeded categories and default settings classify as empty`() = runBlocking {
        val accountResult = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()
        assertTrue(accountResult is InternalDefaultAccountProvisioningResult.Ready)
        DefaultCategorySeeder(database, database.categoryDao()).ensureSeeded()

        val freshSnapshot = backupRepository.createSnapshot()
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(freshSnapshot))

        // Explicit UZS preferred currency from onboarding/settings stays empty
        settingsRepository.setPreferredCurrency("UZS")
        val uzsSnapshot = backupRepository.createSnapshot()
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(uzsSnapshot))
    }

    @Test
    fun `unprovisioned database and user mutations classify as non-empty`() = runBlocking {
        // Before provisioning/seeding runs, tables are empty and must NOT vacuously classify as empty
        val unprovisionedSnapshot = backupRepository.createSnapshot()
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(unprovisionedSnapshot))

        // Provision baseline
        val accountResult = InternalDefaultAccountProvisioner(database, settingsRepository).ensureProvisioned()
            as InternalDefaultAccountProvisioningResult.Ready
        DefaultCategorySeeder(database, database.categoryDao()).ensureSeeded()
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))

        // Changing preferred currency to USD marks state non-empty
        settingsRepository.setPreferredCurrency("USD")
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))

        // Reset preferred currency to null, then insert a user transaction
        settingsRepository.setPreferredCurrency(null)
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))

        val categoryId = database.categoryDao().getAllCategories().first().id
        database.transactionDao().insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 5_000L,
                currencyCode = "UZS",
                accountId = accountResult.internalAccount.id,
                destinationAccountId = null,
                categoryId = categoryId,
                transactionTime = Instant.parse("2026-11-15T09:00:00Z"),
                note = "Metro",
                recurringRuleId = null,
                recurringOccurrenceDate = null,
                createdAt = Instant.parse("2026-11-15T09:00:00Z")
            )
        )
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))

        // Delete transaction, then add a user-created account
        database.compileStatement("DELETE FROM transactions").execute()
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))

        database.accountDao().insertAccount(
            AccountEntity(
                name = "Cash Wallet",
                type = AccountType.CASH,
                initialBalanceAmountInMinorUnits = 0L,
                initialBalanceCurrencyCode = "UZS",
                iconName = "wallet",
                colorHex = "#2563EB",
                isArchived = false,
                createdAt = Instant.parse("2026-11-15T09:05:00Z"),
                internalKey = null
            )
        )
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(backupRepository.createSnapshot()))
    }
}
