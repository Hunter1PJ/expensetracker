package com.suguru.expensetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAutoBackupPreferenceStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreAutoBackupPreferenceStore

    private val UID_ALICE = "alice_uid_123"
    private val UID_BOB = "bob_uid_456"

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val file = tmpFolder.root.resolve("test_auto_backup.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = DataStoreAutoBackupPreferenceStore(dataStore)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
    }

    @Test
    fun `new UID without saved preference defaults to ON`() = runBlocking {
        assertTrue(store.isAutoBackupEnabled(UID_ALICE))
        assertTrue(store.observeAutoBackupEnabled(UID_ALICE).first())
    }

    @Test
    fun `setting OFF persists and remains OFF for that UID`() = runBlocking {
        store.setAutoBackupEnabled(UID_ALICE, false)
        assertFalse(store.isAutoBackupEnabled(UID_ALICE))
        assertFalse(store.observeAutoBackupEnabled(UID_ALICE).first())
    }

    @Test
    fun `setting ON persists and remains ON for that UID`() = runBlocking {
        store.setAutoBackupEnabled(UID_ALICE, true)

        assertTrue(store.isAutoBackupEnabled(UID_ALICE))
        assertTrue(store.observeAutoBackupEnabled(UID_ALICE).first())
    }

    @Test
    fun `switching accounts does not leak preferences between UIDs`() = runBlocking {
        // A single persisted change for Alice must not alter Bob's independent default.
        store.setAutoBackupEnabled(UID_ALICE, false)

        assertFalse(store.isAutoBackupEnabled(UID_ALICE))
        assertTrue(store.isAutoBackupEnabled(UID_BOB))
        assertTrue(store.observeAutoBackupEnabled(UID_BOB).first())
        assertFalse(
            DataStoreAutoBackupPreferenceStore.keyForUid(UID_ALICE) ==
                DataStoreAutoBackupPreferenceStore.keyForUid(UID_BOB)
        )
    }
}
