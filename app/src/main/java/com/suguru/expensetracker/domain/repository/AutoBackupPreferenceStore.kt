package com.suguru.expensetracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistence abstraction for UID-scoped automatic cloud backup/restore preference.
 *
 * Each authenticated user ID has an independent boolean preference.
 * A new UID without a previously saved preference defaults to `true` (ON).
 * Preferences are isolated: switching accounts never leaks one user's choice to another.
 */
interface AutoBackupPreferenceStore {

    /**
     * Returns the current automatic backup/restore preference for [uid].
     * Returns `true` (default ON) if no explicit preference has been saved for this UID.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun isAutoBackupEnabled(uid: String): Boolean

    /**
     * Observes the automatic backup/restore preference for [uid].
     * Emits `true` (default ON) when no explicit preference has been saved.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    fun observeAutoBackupEnabled(uid: String): Flow<Boolean>

    /**
     * Persists the automatic backup/restore preference for [uid].
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun setAutoBackupEnabled(uid: String, enabled: Boolean)
}
