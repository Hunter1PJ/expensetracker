package com.suguru.expensetracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.BackupContentFingerprint
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.WeekStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCloudBackupFingerprintStoreTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreJob: Job
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreCloudBackupFingerprintStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreJob = SupervisorJob()
        dataStoreScope = CoroutineScope(dataStoreJob + Dispatchers.IO)
        dataStoreFile = File(context.filesDir, "cloud_fp_test_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        store = DataStoreCloudBackupFingerprintStore(dataStore)
    }

    @After
    fun tearDown() = runBlocking {
        dataStoreJob.cancel()
        dataStoreJob.join()
        dataStoreFile.delete()
        Unit
    }

    @Test
    fun `missing UID fingerprint returns null`() = runBlocking {
        assertNull(store.getLastUploadedFingerprint("user_missing_123"))
        assertNull(store.observeLastUploadedFingerprint("user_missing_123").first())
    }

    @Test
    fun `saving and reading a fingerprint works and updates cleanly`() = runBlocking {
        val uid = "firebase_uid_alpha"
        val fp1 = "a".repeat(64)
        val fp2 = "0123456789abcdef".repeat(4)

        store.setLastUploadedFingerprint(uid, fp1)
        assertEquals(fp1, store.getLastUploadedFingerprint(uid))
        assertEquals(fp1, store.observeLastUploadedFingerprint(uid).first())

        // Saving the same fingerprint repeatedly is safe and idempotent
        store.setLastUploadedFingerprint(uid, fp1)
        assertEquals(fp1, store.getLastUploadedFingerprint(uid))

        // Updating to a new fingerprint replaces the old one
        store.setLastUploadedFingerprint(uid, fp2)
        assertEquals(fp2, store.getLastUploadedFingerprint(uid))
        assertEquals(fp2, store.observeLastUploadedFingerprint(uid).first())
    }

    @Test
    fun `fingerprint persists after repository and DataStore recreation`() = runBlocking {
        val uid = "firebase_uid_persistent"
        val fp = "b1c2d3e4".repeat(8)

        store.setLastUploadedFingerprint(uid, fp)
        assertEquals(fp, store.getLastUploadedFingerprint(uid))

        // Shut down the first DataStore instance and wait for file lock release
        dataStoreJob.cancel()
        dataStoreJob.join()

        // Recreate a fresh DataStore + repository over the exact same file
        dataStoreJob = SupervisorJob()
        dataStoreScope = CoroutineScope(dataStoreJob + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        store = DataStoreCloudBackupFingerprintStore(dataStore)

        assertEquals(fp, store.getLastUploadedFingerprint(uid))
        assertEquals(fp, store.observeLastUploadedFingerprint(uid).first())
    }

    @Test
    fun `user A and user B fingerprints remain isolated`() = runBlocking {
        val uidA = "user_alpha_001"
        val uidB = "user_beta_002"
        val fpA = "a".repeat(64)
        val fpB = "b".repeat(64)

        store.setLastUploadedFingerprint(uidA, fpA)
        assertEquals(fpA, store.getLastUploadedFingerprint(uidA))
        assertNull(store.getLastUploadedFingerprint(uidB))

        store.setLastUploadedFingerprint(uidB, fpB)
        assertEquals(fpA, store.getLastUploadedFingerprint(uidA))
        assertEquals(fpB, store.getLastUploadedFingerprint(uidB))
        assertNotEquals(
            store.getLastUploadedFingerprint(uidA),
            store.getLastUploadedFingerprint(uidB)
        )
    }

    @Test
    fun `clearing user A does not affect user B`() = runBlocking {
        val uidA = "user_alpha_clear"
        val uidB = "user_beta_keep"
        val fpA = "c".repeat(64)
        val fpB = "d".repeat(64)

        store.setLastUploadedFingerprint(uidA, fpA)
        store.setLastUploadedFingerprint(uidB, fpB)

        store.clearLastUploadedFingerprint(uidA)

        assertNull(store.getLastUploadedFingerprint(uidA))
        assertNull(store.observeLastUploadedFingerprint(uidA).first())
        assertEquals(fpB, store.getLastUploadedFingerprint(uidB))
        assertEquals(fpB, store.observeLastUploadedFingerprint(uidB).first())
    }

    @Test
    fun `clearing an unknown UID is safe and idempotent`() = runBlocking {
        val uidKnown = "known_uid"
        val fpKnown = "e".repeat(64)
        store.setLastUploadedFingerprint(uidKnown, fpKnown)

        store.clearLastUploadedFingerprint("unknown_uid_never_saved")
        store.clearLastUploadedFingerprint("unknown_uid_never_saved")

        assertNull(store.getLastUploadedFingerprint("unknown_uid_never_saved"))
        assertEquals(fpKnown, store.getLastUploadedFingerprint(uidKnown))
    }

    @Test
    fun `blank or whitespace-padded UID is rejected across all operations`() = runBlocking {
        val validFp = "f".repeat(64)
        val invalidUids = listOf("", " ", "   ", "\t\n", " uid_leading", "uid_trailing ")

        for (invalidUid in invalidUids) {
            assertThrowsIllegalArgument("getLastUploadedFingerprint($invalidUid)") {
                store.getLastUploadedFingerprint(invalidUid)
            }
            assertThrowsIllegalArgument("setLastUploadedFingerprint($invalidUid)") {
                store.setLastUploadedFingerprint(invalidUid, validFp)
            }
            assertThrowsIllegalArgument("clearLastUploadedFingerprint($invalidUid)") {
                store.clearLastUploadedFingerprint(invalidUid)
            }
            assertThrowsIllegalArgument("observeLastUploadedFingerprint($invalidUid)") {
                store.observeLastUploadedFingerprint(invalidUid)
            }
        }
    }

    @Test
    fun `invalid fingerprint length is rejected without persisting`() = runBlocking {
        val uid = "uid_length_check"
        val invalidLengthFingerprints = listOf(
            "",
            "a",
            "a".repeat(63),
            "a".repeat(65),
            "0123456789abcdef"
        )

        for (invalidFp in invalidLengthFingerprints) {
            assertThrowsIllegalArgument("length=${invalidFp.length}") {
                store.setLastUploadedFingerprint(uid, invalidFp)
            }
            assertNull(store.getLastUploadedFingerprint(uid))
        }
    }

    @Test
    fun `invalid fingerprint characters are rejected without persisting`() = runBlocking {
        val uid = "uid_char_check"
        val invalidCharFingerprints = listOf(
            "g" + "a".repeat(63),
            "a".repeat(63) + "z",
            "a".repeat(31) + "-" + "a".repeat(32),
            "a".repeat(31) + " " + "a".repeat(32),
            "a".repeat(63) + "_"
        )

        for (invalidFp in invalidCharFingerprints) {
            assertThrowsIllegalArgument("fp=$invalidFp") {
                store.setLastUploadedFingerprint(uid, invalidFp)
            }
            assertNull(store.getLastUploadedFingerprint(uid))
        }
    }

    @Test
    fun `uppercase fingerprint is strictly rejected according to validation policy`() = runBlocking {
        val uid = "uid_uppercase_check"
        val allUppercase = "A".repeat(64)
        val mixedCase = "0123456789abcdef".repeat(3) + "0123456789abcdeF"

        assertThrowsIllegalArgument("all uppercase") {
            store.setLastUploadedFingerprint(uid, allUppercase)
        }
        assertThrowsIllegalArgument("mixed case") {
            store.setLastUploadedFingerprint(uid, mixedCase)
        }
        assertNull(store.getLastUploadedFingerprint(uid))
    }

    @Test
    fun `concurrent writes for different UIDs do not overwrite one another`() = runBlocking {
        val count = 25
        val entries = (1..count).map { index ->
            val uid = "concurrent_uid_$index"
            val fp = DataStoreCloudBackupFingerprintStore.sha256Hex("payload_$index")
            uid to fp
        }

        coroutineScope {
            entries.map { (uid, fp) ->
                async(Dispatchers.IO) {
                    store.setLastUploadedFingerprint(uid, fp)
                }
            }.awaitAll()
        }

        for ((uid, expectedFp) in entries) {
            assertEquals(expectedFp, store.getLastUploadedFingerprint(uid))
        }
    }

    @Test
    fun `preference key names never contain raw UID and use deterministic sha256 suffix`() = runBlocking {
        val rawUid = "sensitive_raw_firebase_uid_987654"
        val fp = "0123456789abcdef".repeat(4)
        val expectedKeySuffix = DataStoreCloudBackupFingerprintStore.sha256Hex(rawUid)

        store.setLastUploadedFingerprint(rawUid, fp)

        val storedKeyNames = dataStore.data.first().asMap().keys.map { it.name }
        assertTrue(storedKeyNames.any { it.endsWith(expectedKeySuffix) })
        assertFalse(storedKeyNames.any { it.contains(rawUid) })
    }

    @Test
    fun `persisted cloud fingerprints do not appear in exported ExpenseTrackerBackup and survive settings restore`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settingsRepository = DataStoreSettingsRepository(dataStore)
            val fixedTime = Instant.parse("2026-11-01T12:00:00Z")
            val accountId = database.accountDao().insertAccount(
                AccountEntity(
                    name = "Cash Wallet",
                    type = AccountType.CASH,
                    initialBalanceAmountInMinorUnits = 10_000L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "wallet",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = fixedTime,
                    internalKey = null
                )
            )
            val categoryId = database.categoryDao().insertCategory(
                CategoryEntity(
                    name = "Coffee",
                    type = CategoryType.EXPENSE,
                    iconName = "coffee",
                    colorHex = "#F59E0B",
                    isSystem = false,
                    isArchived = false
                )
            )
            database.transactionDao().insertTransaction(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amountInMinorUnits = 450L,
                    currencyCode = "USD",
                    accountId = accountId,
                    destinationAccountId = null,
                    categoryId = categoryId,
                    transactionTime = fixedTime,
                    note = "Espresso",
                    recurringRuleId = null,
                    recurringOccurrenceDate = null,
                    createdAt = fixedTime
                )
            )

            val uid = "cloud_export_isolation_uid"
            val sentinelFingerprint = "deadbeef".repeat(8)
            store.setLastUploadedFingerprint(uid, sentinelFingerprint)

            val backupRepository = RoomBackupRepository(
                db = database,
                settingsRepository = settingsRepository,
                appVersion = "1.5.0"
            )
            val snapshot = backupRepository.createSnapshot()
            val serializedBackupJson = backupRepository.serializeBackup(snapshot)
            val computedSnapshotFingerprint = BackupContentFingerprint.compute(snapshot)

            assertNotEquals(sentinelFingerprint, computedSnapshotFingerprint)
            assertFalse(serializedBackupJson.contains(sentinelFingerprint))
            assertFalse(serializedBackupJson.contains(uid))
            assertFalse(serializedBackupJson.contains("cloud_backup_last_uploaded_fingerprint"))
            assertFalse(serializedBackupJson.contains("lastUploadedFingerprint", ignoreCase = true))

            // Verify ordinary settings restore does not wipe UID-scoped cloud fingerprints
            settingsRepository.restoreSettings(
                AppSettings(
                    themeMode = ThemeMode.DARK,
                    preferredCurrencyCode = "EUR",
                    weekStart = WeekStart.SUNDAY,
                    dateFormat = DateFormatPreference.DD_MM_YYYY,
                    timeFormat = TimeFormatPreference.HOUR_24,
                    showCurrencyCode = true,
                    confirmBeforeDelete = false
                )
            )
            assertEquals(sentinelFingerprint, store.getLastUploadedFingerprint(uid))
        } finally {
            database.close()
        }
    }

    private suspend fun assertThrowsIllegalArgument(label: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException for $label")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}
