package com.suguru.expensetracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistence abstraction for UID-scoped cloud backup deduplication metadata.
 *
 * Stores the SHA-256 content fingerprint of the last snapshot confirmed as uploaded and finalized
 * remotely for each authenticated user ID, isolated from ordinary financial settings and local
 * backup exports.
 */
interface CloudBackupFingerprintStore {

    /**
     * Returns the last successfully uploaded content fingerprint for [uid], or `null` if none
     * has been persisted yet.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun getLastUploadedFingerprint(uid: String): String?

    /**
     * Observes the last successfully uploaded content fingerprint for [uid], emitting `null` when
     * absent.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    fun observeLastUploadedFingerprint(uid: String): Flow<String?>

    /**
     * Persists [fingerprint] as the last successfully uploaded content fingerprint for [uid].
     *
     * @throws IllegalArgumentException if [uid] is invalid or [fingerprint] is not a 64-character
     * lowercase hexadecimal SHA-256 string.
     */
    suspend fun setLastUploadedFingerprint(uid: String, fingerprint: String)

    /**
     * Clears the persisted content fingerprint for [uid] only, leaving all other users'
     * fingerprints intact. Safe and idempotent if [uid] has no stored fingerprint.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun clearLastUploadedFingerprint(uid: String)
}
