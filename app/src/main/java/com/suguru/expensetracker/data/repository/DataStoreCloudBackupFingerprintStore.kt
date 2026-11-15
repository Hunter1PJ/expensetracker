package com.suguru.expensetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.suguru.expensetracker.domain.repository.CloudBackupFingerprintStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest

/**
 * [DataStore]-backed implementation of [CloudBackupFingerprintStore].
 *
 * Shares the existing application [DataStore] instance without creating a separate DataStore file.
 * User isolation is enforced by deriving each preference key suffix from the lowercase SHA-256
 * digest of the authenticated UID, never storing the raw UID in preference key names.
 *
 * Validation policy:
 * - UIDs must be non-blank and must not contain leading or trailing whitespace.
 * - Fingerprints must be strictly 64 lowercase hexadecimal characters (`^[0-9a-f]{64}$`);
 *   uppercase hex strings, invalid lengths, and non-hex characters are rejected with
 *   [IllegalArgumentException].
 */
class DataStoreCloudBackupFingerprintStore(
    private val dataStore: DataStore<Preferences>
) : CloudBackupFingerprintStore {

    override suspend fun getLastUploadedFingerprint(uid: String): String? {
        val key = preferenceKeyForUid(uid)
        val stored = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()[key]
        return stored?.takeIf { FINGERPRINT_REGEX.matches(it) }
    }

    override fun observeLastUploadedFingerprint(uid: String): Flow<String?> {
        val key = preferenceKeyForUid(uid)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key]?.takeIf { FINGERPRINT_REGEX.matches(it) }
            }
            .distinctUntilChanged()
    }

    override suspend fun setLastUploadedFingerprint(uid: String, fingerprint: String) {
        val key = preferenceKeyForUid(uid)
        requireValidFingerprint(fingerprint)
        dataStore.edit { preferences ->
            preferences[key] = fingerprint
        }
    }

    override suspend fun clearLastUploadedFingerprint(uid: String) {
        val key = preferenceKeyForUid(uid)
        dataStore.edit { preferences ->
            if (preferences.contains(key)) {
                preferences.remove(key)
            }
        }
    }

    internal companion object {
        private const val KEY_PREFIX = "cloud_backup_last_uploaded_fingerprint_"
        private val FINGERPRINT_REGEX = Regex("^[0-9a-f]{64}$")

        fun preferenceKeyForUid(uid: String): Preferences.Key<String> {
            requireValidUid(uid)
            return stringPreferencesKey("$KEY_PREFIX${sha256Hex(uid)}")
        }

        fun requireValidUid(uid: String) {
            require(uid.isNotBlank()) { "UID must not be blank." }
            require(uid == uid.trim()) { "UID must not contain leading or trailing whitespace." }
        }

        fun requireValidFingerprint(fingerprint: String) {
            require(FINGERPRINT_REGEX.matches(fingerprint)) {
                "Fingerprint must be exactly 64 lowercase hexadecimal characters."
            }
        }

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            val hexChars = CharArray(digest.size * 2)
            val digits = "0123456789abcdef"
            for (index in digest.indices) {
                val value = digest[index].toInt() and 0xFF
                hexChars[index * 2] = digits[value ushr 4]
                hexChars[index * 2 + 1] = digits[value and 0x0F]
            }
            return String(hexChars)
        }
    }
}
