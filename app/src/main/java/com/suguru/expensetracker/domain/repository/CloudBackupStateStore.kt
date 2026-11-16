package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.CloudBackupState
import kotlinx.coroutines.flow.Flow

/**
 * Persistence abstraction for UID-scoped local cloud backup execution state.
 *
 * Tracks last successful backup metadata, pending retry state, and latest attempt/failure
 * metadata independently per authenticated user ID, isolated from ordinary financial settings,
 * local backup exports, and [CloudBackupFingerprintStore].
 */
interface CloudBackupStateStore {

    /**
     * Returns the current persisted [CloudBackupState] for [uid], or [CloudBackupState.EMPTY]
     * if no state has been stored yet.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun getState(uid: String): CloudBackupState

    /**
     * Observes the [CloudBackupState] for [uid], emitting [CloudBackupState.EMPTY] when absent.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    fun observeState(uid: String): Flow<CloudBackupState>

    /**
     * Atomically persists [state] for [uid], replacing any previous state stored for [uid].
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun setState(uid: String, state: CloudBackupState)

    /**
     * Clears the persisted cloud backup state for [uid] only, leaving all other users' state
     * and [CloudBackupFingerprintStore] entries intact. Safe and idempotent if [uid] has no
     * stored state.
     *
     * @throws IllegalArgumentException if [uid] is blank or contains leading/trailing whitespace.
     */
    suspend fun clearState(uid: String)
}
