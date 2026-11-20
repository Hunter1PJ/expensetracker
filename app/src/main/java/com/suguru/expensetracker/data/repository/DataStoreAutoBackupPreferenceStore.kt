package com.suguru.expensetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.suguru.expensetracker.domain.repository.AutoBackupPreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import java.io.IOException
import java.security.MessageDigest

/**
 * [DataStore]-backed implementation of [AutoBackupPreferenceStore].
 *
 * Shares the existing application [DataStore] instance. User isolation is enforced by deriving
 * each preference key from the lowercase SHA-256 digest of the authenticated UID.
 *
 * Default value is `true` (ON) for new UIDs without a saved preference.
 */
class DataStoreAutoBackupPreferenceStore(
    private val dataStore: DataStore<Preferences>
) : AutoBackupPreferenceStore {

    override suspend fun isAutoBackupEnabled(uid: String): Boolean {
        requireValidUid(uid)
        val key = keyForUid(uid)
        val preferences = dataStore.data
            .retryWhen { exception, attempt ->
                if (exception is IOException) {
                    delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
                    true
                } else {
                    false
                }
            }
            .first()
        // Default to true (ON) for new UIDs
        return preferences[key] ?: true
    }

    override fun observeAutoBackupEnabled(uid: String): Flow<Boolean> {
        requireValidUid(uid)
        val key = keyForUid(uid)
        return dataStore.data
            .retryWhen { exception, attempt ->
                if (exception is IOException) {
                    delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
                    true
                } else {
                    false
                }
            }
            .map { preferences ->
                // Default to true (ON) for new UIDs
                preferences[key] ?: true
            }
            .distinctUntilChanged()
    }

    override suspend fun setAutoBackupEnabled(uid: String, enabled: Boolean) {
        requireValidUid(uid)
        val key = keyForUid(uid)
        dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    internal companion object {
        private const val KEY_PREFIX = "auto_backup_enabled_"

        fun keyForUid(uid: String): Preferences.Key<Boolean> {
            return booleanPreferencesKey("$KEY_PREFIX${sha256Hex(uid)}")
        }

        fun requireValidUid(uid: String) {
            require(uid.isNotBlank()) { "UID must not be blank." }
            require(uid == uid.trim()) { "UID must not contain leading or trailing whitespace." }
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
