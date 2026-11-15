package com.suguru.expensetracker.domain.model

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.data.repository.DataStoreSettingsRepository
import com.suguru.expensetracker.data.repository.RoomBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class BackupContentFingerprintCompatibilityTest {

    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsRepository: DataStoreSettingsRepository
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = File(context.filesDir, "fingerprint_test_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        settingsRepository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `v1 fixture checksum remains unchanged and fingerprint ignores envelope metadata`() {
        val repo = RoomBackupRepository(db = database, settingsRepository = settingsRepository, appVersion = "1.5.0")
        val json = this::class.java.classLoader?.getResource("backup_v1_valid.json")?.readText()
            ?: error("Fixture missing")
        val parsed = repo.deserializeBackup(json).getOrThrow()

        // Existing integrity checksum behavior is completely preserved
        assertEquals(
            "6123899671ff51b83724e049c16ad8561038b0167172213f057c554a2262f7ea",
            repo.computeChecksum(parsed)
        )
        assertEquals(parsed.checksum, repo.computeChecksum(parsed))

        // Changing envelope metadata alters computeChecksum() but keeps content fingerprint identical
        val alteredEnvelope = parsed.copy(
            formatVersion = 2,
            appVersion = "2.0.0",
            createdAt = "2026-11-15T12:00:00Z",
            databaseSchemaVersion = 4,
            checksum = "different-checksum"
        )
        assertNotEquals(repo.computeChecksum(parsed), repo.computeChecksum(alteredEnvelope))
        assertEquals(
            BackupContentFingerprint.compute(parsed),
            BackupContentFingerprint.compute(alteredEnvelope)
        )
    }

    @Test
    fun `snapshots across different timestamps appVersions and non-exported prefs share fingerprint`() = runBlocking {
        val fixedTime = Instant.parse("2026-10-01T10:00:00Z")
        val accountId = database.accountDao().insertAccount(
            AccountEntity(
                name = "Checking",
                type = AccountType.BANK,
                initialBalanceAmountInMinorUnits = 50_000L,
                initialBalanceCurrencyCode = "USD",
                iconName = "wallet",
                colorHex = "#2563EB",
                isArchived = false,
                createdAt = fixedTime,
                internalKey = null
            )
        )
        val categoryId = database.categoryDao().insertCategory(
            CategoryEntity(
                name = "Dining",
                type = CategoryType.EXPENSE,
                iconName = "restaurant",
                colorHex = "#F97316",
                isSystem = false,
                isArchived = false
            )
        )
        database.transactionDao().insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 1_850L,
                currencyCode = "USD",
                accountId = accountId,
                destinationAccountId = null,
                categoryId = categoryId,
                transactionTime = fixedTime,
                note = "Lunch",
                recurringRuleId = null,
                recurringOccurrenceDate = null,
                createdAt = fixedTime
            )
        )
        settingsRepository.setPreferredCurrency("USD")

        val repoV1 = RoomBackupRepository(db = database, settingsRepository = settingsRepository, appVersion = "1.5.0")
        val snapshot1 = repoV1.createSnapshot()

        // Mutate non-exported local metadata & AI preferences (and simulate BYOK key presence outside backup)
        val byokKeyMaterial = "AIzaSyFakeSecretByokKey1234567890"
        context.getSharedPreferences("byok_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("encrypted_key_iv", "mock_iv")
            .putString("encrypted_key_ciphertext", byokKeyMaterial)
            .apply()
        settingsRepository.setLastBackupMetadata("2026-11-15T08:00:00Z", "backup_20261115.json")
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("ai_feature_enabled")] = false
        }
        delay(5)

        val repoV2 = RoomBackupRepository(db = database, settingsRepository = settingsRepository, appVersion = "1.6.0")
        val snapshot2 = repoV2.createSnapshot()

        // Envelope differs, so integrity checksum differs
        assertNotEquals(snapshot1.createdAt, snapshot2.createdAt)
        assertNotEquals(snapshot1.appVersion, snapshot2.appVersion)
        assertNotEquals(snapshot1.checksum, snapshot2.checksum)

        // Content is identical, so content fingerprint is identical
        assertEquals(
            BackupContentFingerprint.compute(snapshot1),
            BackupContentFingerprint.compute(snapshot2)
        )

        val canonicalJson = BackupContentFingerprint.canonicalJson(snapshot2)
        assertFalse(canonicalJson.contains(byokKeyMaterial))
        assertFalse(canonicalJson.contains("backup_20261115.json"))
        assertFalse(canonicalJson.contains("ai_feature_enabled"))
        assertTrue(canonicalJson.contains("\"Checking\""))
    }
}
