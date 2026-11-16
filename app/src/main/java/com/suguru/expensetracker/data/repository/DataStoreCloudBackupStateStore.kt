package com.suguru.expensetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.repository.CloudBackupStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest

/**
 * [DataStore]-backed implementation of [CloudBackupStateStore].
 *
 * Shares the existing application [DataStore] instance without creating a separate DataStore file.
 * User isolation is enforced by deriving each preference key prefix from the lowercase SHA-256
 * digest of the authenticated UID, never storing the raw UID in preference key names or values.
 */
class DataStoreCloudBackupStateStore(
    private val dataStore: DataStore<Preferences>
) : CloudBackupStateStore {

    override suspend fun getState(uid: String): CloudBackupState {
        val keys = keysForUid(uid)
        val preferences = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()
        return decodeState(preferences, keys)
    }

    override fun observeState(uid: String): Flow<CloudBackupState> {
        val keys = keysForUid(uid)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences -> decodeState(preferences, keys) }
            .distinctUntilChanged()
    }

    override suspend fun setState(uid: String, state: CloudBackupState) {
        val keys = keysForUid(uid)
        dataStore.edit { preferences ->
            setOrRemoveLong(
                preferences,
                keys.lastSuccessfulBackupAtEpochMillis,
                state.lastSuccessfulBackupAtEpochMillis
            )
            setOrRemoveString(
                preferences,
                keys.lastSuccessfulSnapshotId,
                state.lastSuccessfulSnapshotId
            )
            setOrRemoveString(
                preferences,
                keys.pendingSnapshotId,
                state.pendingSnapshotId
            )
            setOrRemoveLong(
                preferences,
                keys.pendingSinceEpochMillis,
                state.pendingSinceEpochMillis
            )
            setOrRemoveLong(
                preferences,
                keys.lastAttemptAtEpochMillis,
                state.lastAttemptAtEpochMillis
            )
            setOrRemoveLong(
                preferences,
                keys.lastFailureAtEpochMillis,
                state.lastFailureAtEpochMillis
            )
            setOrRemoveString(
                preferences,
                keys.lastFailureCode,
                state.lastFailureCode
            )
        }
    }

    override suspend fun clearState(uid: String) {
        val keys = keysForUid(uid)
        dataStore.edit { preferences ->
            for (key in keys.allKeys) {
                if (preferences.contains(key)) {
                    preferences.remove(key)
                }
            }
        }
    }

    private fun setOrRemoveLong(
        preferences: MutablePreferences,
        key: Preferences.Key<Long>,
        value: Long?
    ) {
        if (value == null) {
            if (preferences.contains(key)) {
                preferences.remove(key)
            }
        } else {
            preferences[key] = value
        }
    }

    private fun setOrRemoveString(
        preferences: MutablePreferences,
        key: Preferences.Key<String>,
        value: String?
    ) {
        if (value == null) {
            if (preferences.contains(key)) {
                preferences.remove(key)
            }
        } else {
            preferences[key] = value
        }
    }

    private fun decodeState(preferences: Preferences, keys: UidPreferenceKeys): CloudBackupState {
        val rawMap = preferences.asMap()

        val lastSuccessfulBackupAtEpochMillis = (rawMap[keys.lastSuccessfulBackupAtEpochMillis] as? Long)
            ?.takeIf { it >= 0L }
        val lastSuccessfulSnapshotId = (rawMap[keys.lastSuccessfulSnapshotId] as? String)
            ?.takeIf { CloudBackupState.isValidSnapshotId(it) }

        val rawPendingSnapshotId = (rawMap[keys.pendingSnapshotId] as? String)
            ?.takeIf { CloudBackupState.isValidSnapshotId(it) }
        val rawPendingSinceEpochMillis = (rawMap[keys.pendingSinceEpochMillis] as? Long)
            ?.takeIf { it >= 0L }
        val hasValidPendingPair = rawPendingSnapshotId != null && rawPendingSinceEpochMillis != null
        val pendingSnapshotId = if (hasValidPendingPair) rawPendingSnapshotId else null
        val pendingSinceEpochMillis = if (hasValidPendingPair) rawPendingSinceEpochMillis else null

        val lastAttemptAtEpochMillis = (rawMap[keys.lastAttemptAtEpochMillis] as? Long)
            ?.takeIf { it >= 0L }

        val rawFailureAtEpochMillis = (rawMap[keys.lastFailureAtEpochMillis] as? Long)
            ?.takeIf { it >= 0L }
        val rawFailureCode = (rawMap[keys.lastFailureCode] as? String)
            ?.takeIf { CloudBackupState.isValidFailureCode(it) }
        val hasValidFailurePair = rawFailureAtEpochMillis != null && rawFailureCode != null
        val lastFailureAtEpochMillis = if (hasValidFailurePair) rawFailureAtEpochMillis else null
        val lastFailureCode = if (hasValidFailurePair) rawFailureCode else null

        return CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = lastSuccessfulBackupAtEpochMillis,
            lastSuccessfulSnapshotId = lastSuccessfulSnapshotId,
            pendingSnapshotId = pendingSnapshotId,
            pendingSinceEpochMillis = pendingSinceEpochMillis,
            lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
            lastFailureAtEpochMillis = lastFailureAtEpochMillis,
            lastFailureCode = lastFailureCode
        )
    }

    internal data class UidPreferenceKeys(
        val lastSuccessfulBackupAtEpochMillis: Preferences.Key<Long>,
        val lastSuccessfulSnapshotId: Preferences.Key<String>,
        val pendingSnapshotId: Preferences.Key<String>,
        val pendingSinceEpochMillis: Preferences.Key<Long>,
        val lastAttemptAtEpochMillis: Preferences.Key<Long>,
        val lastFailureAtEpochMillis: Preferences.Key<Long>,
        val lastFailureCode: Preferences.Key<String>
    ) {
        val allKeys: List<Preferences.Key<*>> = listOf(
            lastSuccessfulBackupAtEpochMillis,
            lastSuccessfulSnapshotId,
            pendingSnapshotId,
            pendingSinceEpochMillis,
            lastAttemptAtEpochMillis,
            lastFailureAtEpochMillis,
            lastFailureCode
        )
    }

    internal companion object {
        private const val KEY_PREFIX = "cloud_backup_state_"

        fun keysForUid(uid: String): UidPreferenceKeys {
            requireValidUid(uid)
            val uidPrefix = "$KEY_PREFIX${sha256Hex(uid)}_"
            return UidPreferenceKeys(
                lastSuccessfulBackupAtEpochMillis = longPreferencesKey("${uidPrefix}last_successful_backup_at"),
                lastSuccessfulSnapshotId = stringPreferencesKey("${uidPrefix}last_successful_snapshot_id"),
                pendingSnapshotId = stringPreferencesKey("${uidPrefix}pending_snapshot_id"),
                pendingSinceEpochMillis = longPreferencesKey("${uidPrefix}pending_since"),
                lastAttemptAtEpochMillis = longPreferencesKey("${uidPrefix}last_attempt_at"),
                lastFailureAtEpochMillis = longPreferencesKey("${uidPrefix}last_failure_at"),
                lastFailureCode = stringPreferencesKey("${uidPrefix}last_failure_code")
            )
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
