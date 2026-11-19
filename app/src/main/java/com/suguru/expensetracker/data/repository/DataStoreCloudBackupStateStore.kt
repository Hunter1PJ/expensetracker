package com.suguru.expensetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.repository.CloudBackupStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
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
            .retryWhen { exception, attempt ->
                if (exception is IOException) {
                    delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
                    true
                } else {
                    false
                }
            }
            .first()
        return decodeState(preferences, keys)
    }

    override fun observeState(uid: String): Flow<CloudBackupState> {
        val keys = keysForUid(uid)
        return dataStore.data
            .retryWhen { exception, attempt ->
                if (exception is IOException) {
                    delay((1_000L * (attempt + 1)).coerceAtMost(30_000L))
                    true
                } else {
                    false
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
            setOrRemoveLong(
                preferences,
                keys.lastSuccessfulRestoreAtEpochMillis,
                state.lastSuccessfulRestoreAtEpochMillis
            )
            setOrRemoveString(
                preferences,
                keys.lastSuccessfulSnapshotId,
                state.lastSuccessfulSnapshotId
            )
            setOrRemoveString(preferences, keys.lastSuccessfulRestoreGenerationId, state.lastSuccessfulRestoreGenerationId)
            setOrRemoveString(preferences, keys.lastSuccessfulRestoreFingerprint, state.lastSuccessfulRestoreFingerprint)
            preferences[keys.pendingRestore] = state.pendingRestore
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
            setOrRemoveLong(
                preferences,
                keys.lastRestoreFailureAtEpochMillis,
                state.lastRestoreFailureAtEpochMillis
            )
            setOrRemoveString(
                preferences,
                keys.lastRestoreFailureCode,
                state.lastRestoreFailureCode
            )
            setOrRemoveString(preferences, keys.lastRestoreFailureGenerationId, state.lastRestoreFailureGenerationId)
            setOrRemoveString(preferences, keys.lastRestoreFailureFingerprint, state.lastRestoreFailureFingerprint)
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
        val lastSuccessfulRestoreAtEpochMillis = (rawMap[keys.lastSuccessfulRestoreAtEpochMillis] as? Long)
            ?.takeIf { it >= 0L }
        val lastSuccessfulSnapshotId = (rawMap[keys.lastSuccessfulSnapshotId] as? String)
            ?.takeIf { CloudBackupState.isValidSnapshotId(it) }
        val lastSuccessfulRestoreGenerationId = (rawMap[keys.lastSuccessfulRestoreGenerationId] as? String)
            ?.takeIf { CloudBackupState.isValidSnapshotId(it) }
        val lastSuccessfulRestoreFingerprint = (rawMap[keys.lastSuccessfulRestoreFingerprint] as? String)
            ?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }

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

        val rawRestoreFailureAt = (rawMap[keys.lastRestoreFailureAtEpochMillis] as? Long)
            ?.takeIf { it >= 0L }
        val rawRestoreFailureCode = (rawMap[keys.lastRestoreFailureCode] as? String)
            ?.takeIf { CloudBackupState.isValidFailureCode(it) }
        val hasValidRestoreFailurePair = rawRestoreFailureAt != null && rawRestoreFailureCode != null
        val lastRestoreFailureGenerationId = (rawMap[keys.lastRestoreFailureGenerationId] as? String)
            ?.takeIf { CloudBackupState.isValidSnapshotId(it) }
        val lastRestoreFailureFingerprint = (rawMap[keys.lastRestoreFailureFingerprint] as? String)
            ?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }

        return CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = lastSuccessfulBackupAtEpochMillis,
            lastSuccessfulRestoreAtEpochMillis = lastSuccessfulRestoreAtEpochMillis,
            lastSuccessfulSnapshotId = lastSuccessfulSnapshotId,
            lastSuccessfulRestoreGenerationId = lastSuccessfulRestoreGenerationId,
            lastSuccessfulRestoreFingerprint = lastSuccessfulRestoreFingerprint,
            pendingRestore = (rawMap[keys.pendingRestore] as? Boolean) ?: false,
            pendingSnapshotId = pendingSnapshotId,
            pendingSinceEpochMillis = pendingSinceEpochMillis,
            lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
            lastFailureAtEpochMillis = lastFailureAtEpochMillis,
            lastFailureCode = lastFailureCode,
            lastRestoreFailureAtEpochMillis = if (hasValidRestoreFailurePair) rawRestoreFailureAt else null,
            lastRestoreFailureCode = if (hasValidRestoreFailurePair) rawRestoreFailureCode else null,
            lastRestoreFailureGenerationId = if (hasValidRestoreFailurePair) lastRestoreFailureGenerationId else null,
            lastRestoreFailureFingerprint = if (hasValidRestoreFailurePair) lastRestoreFailureFingerprint else null
        )
    }

    internal data class UidPreferenceKeys(
        val lastSuccessfulBackupAtEpochMillis: Preferences.Key<Long>,
        val lastSuccessfulRestoreAtEpochMillis: Preferences.Key<Long>,
        val lastSuccessfulSnapshotId: Preferences.Key<String>,
        val lastSuccessfulRestoreGenerationId: Preferences.Key<String>,
        val lastSuccessfulRestoreFingerprint: Preferences.Key<String>,
        val pendingRestore: Preferences.Key<Boolean>,
        val pendingSnapshotId: Preferences.Key<String>,
        val pendingSinceEpochMillis: Preferences.Key<Long>,
        val lastAttemptAtEpochMillis: Preferences.Key<Long>,
        val lastFailureAtEpochMillis: Preferences.Key<Long>,
        val lastFailureCode: Preferences.Key<String>,
        val lastRestoreFailureAtEpochMillis: Preferences.Key<Long>,
        val lastRestoreFailureCode: Preferences.Key<String>,
        val lastRestoreFailureGenerationId: Preferences.Key<String>,
        val lastRestoreFailureFingerprint: Preferences.Key<String>
    ) {
        val allKeys: List<Preferences.Key<*>> = listOf(
            lastSuccessfulBackupAtEpochMillis,
            lastSuccessfulRestoreAtEpochMillis,
            lastSuccessfulSnapshotId,
            lastSuccessfulRestoreGenerationId,
            lastSuccessfulRestoreFingerprint,
            pendingRestore,
            pendingSnapshotId,
            pendingSinceEpochMillis,
            lastAttemptAtEpochMillis,
            lastFailureAtEpochMillis,
            lastFailureCode,
            lastRestoreFailureAtEpochMillis,
            lastRestoreFailureCode,
            lastRestoreFailureGenerationId,
            lastRestoreFailureFingerprint
        )
    }

    internal companion object {
        private const val KEY_PREFIX = "cloud_backup_state_"

        fun keysForUid(uid: String): UidPreferenceKeys {
            requireValidUid(uid)
            val uidPrefix = "$KEY_PREFIX${sha256Hex(uid)}_"
            return UidPreferenceKeys(
                lastSuccessfulBackupAtEpochMillis = longPreferencesKey("${uidPrefix}last_successful_backup_at"),
                lastSuccessfulRestoreAtEpochMillis = longPreferencesKey("${uidPrefix}last_successful_restore_at"),
                lastSuccessfulSnapshotId = stringPreferencesKey("${uidPrefix}last_successful_snapshot_id"),
                lastSuccessfulRestoreGenerationId = stringPreferencesKey("${uidPrefix}last_successful_restore_generation_id"),
                lastSuccessfulRestoreFingerprint = stringPreferencesKey("${uidPrefix}last_successful_restore_fingerprint"),
                pendingRestore = androidx.datastore.preferences.core.booleanPreferencesKey("${uidPrefix}pending_restore"),
                pendingSnapshotId = stringPreferencesKey("${uidPrefix}pending_snapshot_id"),
                pendingSinceEpochMillis = longPreferencesKey("${uidPrefix}pending_since"),
                lastAttemptAtEpochMillis = longPreferencesKey("${uidPrefix}last_attempt_at"),
                lastFailureAtEpochMillis = longPreferencesKey("${uidPrefix}last_failure_at"),
                lastFailureCode = stringPreferencesKey("${uidPrefix}last_failure_code"),
                lastRestoreFailureAtEpochMillis = longPreferencesKey("${uidPrefix}last_restore_failure_at"),
                lastRestoreFailureCode = stringPreferencesKey("${uidPrefix}last_restore_failure_code"),
                lastRestoreFailureGenerationId = stringPreferencesKey("${uidPrefix}last_restore_failure_generation_id"),
                lastRestoreFailureFingerprint = stringPreferencesKey("${uidPrefix}last_restore_failure_fingerprint")
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
