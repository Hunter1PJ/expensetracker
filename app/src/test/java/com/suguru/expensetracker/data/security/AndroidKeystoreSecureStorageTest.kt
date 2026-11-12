package com.suguru.expensetracker.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidKeystoreSecureStorageTest {

    private lateinit var storage: AndroidKeystoreSecureStorage

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        storage = AndroidKeystoreSecureStorage(
            context = context,
            prefName = "test_byok_prefs",
            keyAlias = "test_master_key"
        )
        storage.deleteKey()
    }

    @Test
    fun saveKey_storesAndRetrievesPlaintext() {
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())

        val testKey = "AIzaSyTestApiKeyForGemini12345"
        storage.saveKey(testKey)

        assertTrue(storage.hasKey())
        assertEquals(testKey, storage.getKey())
    }

    @Test
    fun saveKey_withSurroundingWhitespace_trimsKey() {
        val testKey = "  AIzaSyWithWhitespace778899  "
        storage.saveKey(testKey)

        assertEquals("AIzaSyWithWhitespace778899", storage.getKey())
    }

    @Test
    fun deleteKey_removesStoredKey() {
        storage.saveKey("AIzaSyDeleteMeNow999")
        assertTrue(storage.hasKey())

        storage.deleteKey()
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())
    }

    @Test
    fun saveKey_emptyString_actsAsDelete() {
        storage.saveKey("AIzaSyOldKey")
        assertTrue(storage.hasKey())

        storage.saveKey("   ")
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())
    }
}
