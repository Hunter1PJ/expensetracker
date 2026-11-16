package com.suguru.expensetracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCloudBackupStateStoreTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreJob: Job
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreCloudBackupStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreJob = SupervisorJob()
        dataStoreScope = CoroutineScope(dataStoreJob + Dispatchers.IO)
        dataStoreFile = File(context.filesDir, "cloud_state_test_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        store = DataStoreCloudBackupStateStore(dataStore)
    }

    @After
    fun tearDown() = runBlocking {
        dataStoreJob.cancel()
        dataStoreJob.join()
        dataStoreFile.delete()
        Unit
    }

    @Test
    fun `11 - empty state is returned for a UID with no stored values`() = runBlocking {
        assertEquals(CloudBackupState.EMPTY, store.getState("missing_uid_001"))
        assertEquals(CloudBackupState.EMPTY, store.observeState("missing_uid_001").first())
    }

    @Test
    fun `12 - state survives read-after-write, updates, and DataStore recreation`() = runBlocking {
        val uid = "firebase_uid_alpha"
        val initialState = CloudBackupState(
            pendingSnapshotId = "snap_pending_01",
            pendingSinceEpochMillis = 1_763_200_000_000L,
            lastAttemptAtEpochMillis = 1_763_200_010_000L,
            lastFailureAtEpochMillis = 1_763_200_010_000L,
            lastFailureCode = "NETWORK_UNAVAILABLE"
        )

        store.setState(uid, initialState)
        assertEquals(initialState, store.getState(uid))

        // Update state when upload succeeds (clearing pending and failure fields)
        val completedState = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_200_050_000L,
            lastSuccessfulSnapshotId = "snap_pending_01",
            lastAttemptAtEpochMillis = 1_763_200_050_000L
        )
        store.setState(uid, completedState)
        assertEquals(completedState, store.getState(uid))

        // Recreate DataStore + store over the exact same backing file
        dataStoreJob.cancel()
        dataStoreJob.join()

        dataStoreJob = SupervisorJob()
        dataStoreScope = CoroutineScope(dataStoreJob + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        store = DataStoreCloudBackupStateStore(dataStore)

        assertEquals(completedState, store.getState(uid))
        assertEquals(completedState, store.observeState(uid).first())
    }

    @Test
    fun `13 - state survives collection through Flow`() = runBlocking {
        val uid = "firebase_uid_flow"
        val state1 = CloudBackupState(
            pendingSnapshotId = "snap_1",
            pendingSinceEpochMillis = 1_000L
        )
        val state2 = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 2_000L,
            lastSuccessfulSnapshotId = "snap_1",
            lastAttemptAtEpochMillis = 2_000L
        )

        val collected = mutableListOf<CloudBackupState>()
        val collectorJob = launch(Dispatchers.Unconfined) {
            store.observeState(uid).take(4).toList(collected)
        }

        store.setState(uid, state1)
        store.setState(uid, state1) // Duplicate should be deduplicated by distinctUntilChanged
        store.setState(uid, state2)
        store.clearState(uid)
        collectorJob.join()

        assertEquals(
            listOf(
                CloudBackupState.EMPTY,
                state1,
                state2,
                CloudBackupState.EMPTY
            ),
            collected
        )
    }

    @Test
    fun `14 - updating one UID does not affect another UID`() = runBlocking {
        val uidA = "user_alpha"
        val uidB = "user_beta"
        val stateA = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_000L,
            lastSuccessfulSnapshotId = "snap_a"
        )
        val stateB = CloudBackupState(
            pendingSnapshotId = "snap_b",
            pendingSinceEpochMillis = 2_000L,
            lastAttemptAtEpochMillis = 2_500L,
            lastFailureAtEpochMillis = 2_500L,
            lastFailureCode = "TIMEOUT"
        )

        store.setState(uidA, stateA)
        assertEquals(stateA, store.getState(uidA))
        assertEquals(CloudBackupState.EMPTY, store.getState(uidB))

        store.setState(uidB, stateB)
        assertEquals(stateA, store.getState(uidA))
        assertEquals(stateB, store.getState(uidB))
        assertNotEquals(store.getState(uidA), store.getState(uidB))

        // Concurrent writes across many UIDs remain isolated
        val count = 20
        val entries = (1..count).map { index ->
            "concurrent_uid_$index" to CloudBackupState(
                lastSuccessfulBackupAtEpochMillis = index * 1_000L,
                lastSuccessfulSnapshotId = "snap_$index",
                lastAttemptAtEpochMillis = index * 1_000L
            )
        }
        coroutineScope {
            entries.map { (uid, state) ->
                async(Dispatchers.IO) {
                    store.setState(uid, state)
                }
            }.awaitAll()
        }
        for ((uid, expectedState) in entries) {
            assertEquals(expectedState, store.getState(uid))
        }
    }

    @Test
    fun `15 and 16 - clearing one UID returns it to empty state without affecting another UID`() = runBlocking {
        val uidA = "user_clear_alpha"
        val uidB = "user_keep_beta"
        val stateA = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 5_000L,
            lastSuccessfulSnapshotId = "snap_a",
            pendingSnapshotId = "snap_a_next",
            pendingSinceEpochMillis = 6_000L
        )
        val stateB = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 9_000L,
            lastSuccessfulSnapshotId = "snap_b"
        )

        store.setState(uidA, stateA)
        store.setState(uidB, stateB)

        store.clearState(uidA)

        assertEquals(CloudBackupState.EMPTY, store.getState(uidA))
        assertEquals(CloudBackupState.EMPTY, store.observeState(uidA).first())
        assertEquals(stateB, store.getState(uidB))
        assertEquals(stateB, store.observeState(uidB).first())

        // Clearing an unknown or already-cleared UID is safe and idempotent
        store.clearState(uidA)
        store.clearState("never_stored_uid")
        assertEquals(stateB, store.getState(uidB))
    }

    @Test
    fun `17 - partial and malformed persisted fields are handled safely without fabricating success`() = runBlocking {
        val uid = "corrupted_uid_01"
        val keys = DataStoreCloudBackupStateStore.keysForUid(uid)

        // Case A: Partial pending pair, partial failure pair, negative success timestamp, blank snapshot ID
        dataStore.edit { prefs ->
            prefs[keys.lastSuccessfulBackupAtEpochMillis] = -99L
            prefs[keys.lastSuccessfulSnapshotId] = "   "
            prefs[keys.pendingSnapshotId] = "snap_orphan"
            // pendingSinceEpochMillis intentionally missing
            prefs[keys.lastAttemptAtEpochMillis] = -5L
            prefs[keys.lastFailureAtEpochMillis] = 5_000L
            prefs[keys.lastFailureCode] = "invalid error with spaces"
        }

        val decodedA = store.getState(uid)
        assertEquals(CloudBackupState.EMPTY, decodedA)
        assertNull(decodedA.lastSuccessfulBackupAtEpochMillis)
        assertNull(decodedA.lastSuccessfulSnapshotId)

        // Case B: Valid success fields alongside corrupt pending/failure fields preserves valid success fields
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[keys.lastSuccessfulBackupAtEpochMillis] = 12_345L
            prefs[keys.lastSuccessfulSnapshotId] = "snap_valid_1"
            prefs[keys.pendingSnapshotId] = "snap_pending_2"
            prefs[keys.pendingSinceEpochMillis] = -1L
            prefs[keys.lastFailureCode] = "NETWORK_UNAVAILABLE"
            // lastFailureAtEpochMillis intentionally missing
        }

        val decodedB = store.getState(uid)
        assertEquals(
            CloudBackupState(
                lastSuccessfulBackupAtEpochMillis = 12_345L,
                lastSuccessfulSnapshotId = "snap_valid_1"
            ),
            decodedB
        )

        // Case C: Wrong preference value types stored under the key names (e.g. String in Long key)
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[stringPreferencesKey(keys.lastSuccessfulBackupAtEpochMillis.name)] = "not_a_long"
            prefs[longPreferencesKey(keys.lastSuccessfulSnapshotId.name)] = 999L
            prefs[stringPreferencesKey(keys.lastAttemptAtEpochMillis.name)] = "corrupt"
        }

        val decodedC = store.getState(uid)
        assertEquals(CloudBackupState.EMPTY, decodedC)
        assertEquals(CloudBackupState.EMPTY, store.observeState(uid).first())
    }

    @Test
    fun `18 - raw UID is never persisted as a preference key or value`() = runBlocking {
        val rawUid = "sensitive_raw_firebase_uid_123456"
        val expectedHash = DataStoreCloudBackupStateStore.sha256Hex(rawUid)
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_200_000_000L,
            lastSuccessfulSnapshotId = "snap_01",
            pendingSnapshotId = "snap_02",
            pendingSinceEpochMillis = 1_763_200_010_000L,
            lastAttemptAtEpochMillis = 1_763_200_020_000L,
            lastFailureAtEpochMillis = 1_763_200_020_000L,
            lastFailureCode = "TIMEOUT"
        )

        store.setState(rawUid, state)

        val entries = dataStore.data.first().asMap()
        val keyNames = entries.keys.map { it.name }
        val stringValues = entries.values.map { it.toString() }

        assertTrue(keyNames.isNotEmpty())
        assertTrue(keyNames.all { it.contains(expectedHash) })
        assertFalse(keyNames.any { it.contains(rawUid) })
        assertFalse(stringValues.any { it.contains(rawUid) })
    }

    @Test
    fun `19 - existing CloudBackupFingerprintStore and SettingsRepository data remain independent and intact`() = runBlocking {
        val fingerprintStore = DataStoreCloudBackupFingerprintStore(dataStore)
        val settingsRepository = DataStoreSettingsRepository(dataStore)
        val uid = "shared_datastore_uid"
        val fingerprint = "0123456789abcdef".repeat(4)
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_200_000_000L,
            lastSuccessfulSnapshotId = "snap_shared_01",
            lastAttemptAtEpochMillis = 1_763_200_000_000L
        )

        fingerprintStore.setLastUploadedFingerprint(uid, fingerprint)
        store.setState(uid, state)

        // Clearing state does not clear fingerprint
        store.clearState(uid)
        assertEquals(CloudBackupState.EMPTY, store.getState(uid))
        assertEquals(fingerprint, fingerprintStore.getLastUploadedFingerprint(uid))

        // Clearing fingerprint does not clear state
        store.setState(uid, state)
        fingerprintStore.clearLastUploadedFingerprint(uid)
        assertNull(fingerprintStore.getLastUploadedFingerprint(uid))
        assertEquals(state, store.getState(uid))

        // Restoring settings does not disturb cloud backup state
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
        assertEquals(state, store.getState(uid))
    }

    @Test
    fun `20 - repeated writes of the same state behave deterministically`() = runBlocking {
        val uid = "deterministic_uid"
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 10_000L,
            lastSuccessfulSnapshotId = "snap_det_1",
            pendingSnapshotId = "snap_det_2",
            pendingSinceEpochMillis = 20_000L,
            lastAttemptAtEpochMillis = 30_000L,
            lastFailureAtEpochMillis = 30_000L,
            lastFailureCode = "NETWORK_UNAVAILABLE"
        )

        repeat(5) {
            store.setState(uid, state)
        }

        assertEquals(state, store.getState(uid))
        assertEquals(state, store.observeState(uid).first())
    }

    @Test
    fun `blank or whitespace-padded UIDs are rejected across all store operations`() = runBlocking {
        val invalidUids = listOf("", " ", "   ", "\t\n", " uid_leading", "uid_trailing ")

        for (invalidUid in invalidUids) {
            assertThrowsIllegalArgument("getState($invalidUid)") {
                store.getState(invalidUid)
            }
            assertThrowsIllegalArgument("observeState($invalidUid)") {
                store.observeState(invalidUid)
            }
            assertThrowsIllegalArgument("setState($invalidUid)") {
                store.setState(invalidUid, CloudBackupState.EMPTY)
            }
            assertThrowsIllegalArgument("clearState($invalidUid)") {
                store.clearState(invalidUid)
            }
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
