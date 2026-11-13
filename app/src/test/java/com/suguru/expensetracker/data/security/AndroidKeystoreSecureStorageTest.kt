package com.suguru.expensetracker.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private val prefName = "test_byok_prefs"

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        storage = AndroidKeystoreSecureStorage(
            context = context,
            prefName = prefName,
            keyAlias = "test_master_key"
        )
        storage.deleteKey()
    }

    @Test
    fun saveKey_usesAuthenticatedEncryptionOrFailsClosed() {
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())

        val testKey = "AIzaSyTestApiKeyForGemini12345"
        try {
            storage.saveKey(testKey)
        } catch (_: IllegalStateException) {
            assertFalse(storage.hasKey())
            assertNull(storage.getKey())
            return
        }

        assertTrue(storage.hasKey())
        assertEquals(testKey, storage.getKey())
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(prefName, 0)
        assertNotEquals(testKey, preferences.getString("encrypted_key_ciphertext", null))
        assertNotEquals(
            android.util.Base64.encodeToString(testKey.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP),
            preferences.getString("encrypted_key_ciphertext", null)
        )
        assertNotEquals("FALLBACK", preferences.getString("encrypted_key_iv", null))
    }

    @Test
    fun saveKey_withSurroundingWhitespace_trimsKey() {
        val testKey = "  AIzaSyWithWhitespace778899  "
        try {
            storage.saveKey(testKey)
        } catch (_: IllegalStateException) {
            assertFalse(storage.hasKey())
            return
        }

        assertEquals("AIzaSyWithWhitespace778899", storage.getKey())
    }

    @Test
    fun deleteKey_removesStoredKey() {
        try {
            storage.saveKey("AIzaSyDeleteMeNow999")
        } catch (_: IllegalStateException) {
            assertFalse(storage.hasKey())
            return
        }
        assertTrue(storage.hasKey())

        storage.deleteKey()
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())
    }

    @Test
    fun saveKey_emptyString_actsAsDelete() {
        try {
            storage.saveKey("AIzaSyOldKey")
        } catch (_: IllegalStateException) {
            assertFalse(storage.hasKey())
        }

        storage.saveKey("   ")
        assertFalse(storage.hasKey())
        assertNull(storage.getKey())
    }

    @Test
    fun legacyBase64Fallback_isDeletedAndNeverReadAsAnApiKey() {
        val testKey = "AIzaSyLegacyPlaintextFallback"
        val encoded = android.util.Base64.encodeToString(
            testKey.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        RuntimeEnvironment.getApplication().getSharedPreferences(prefName, 0)
            .edit()
            .putString("encrypted_key_iv", "FALLBACK")
            .putString("encrypted_key_ciphertext", encoded)
            .commit()

        assertFalse(storage.hasKey())
        assertNull(storage.getKey())
        assertFalse(RuntimeEnvironment.getApplication().getSharedPreferences(prefName, 0).contains("encrypted_key_ciphertext"))
    }
}
