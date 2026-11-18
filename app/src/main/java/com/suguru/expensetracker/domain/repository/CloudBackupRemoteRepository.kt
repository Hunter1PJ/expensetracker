package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.CloudBackupChunkedPayload
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import kotlinx.coroutines.flow.Flow

/**
 * Remote storage abstraction for UID-isolated cloud backup snapshot manifests and chunks.
 */
interface CloudBackupRemoteRepository {

    fun observeManifests(userId: String): Flow<List<CloudBackupManifest>>

    /**
     * Retrieves the newest valid [CloudBackupManifestStatus.COMPLETE] snapshot for the
     * currently authenticated user, or `null` when no completed snapshot exists.
     */
    suspend fun getLatestCompleteManifest(): Result<CloudBackupManifest?>

    suspend fun uploadBackup(
        userId: String,
        manifest: CloudBackupManifest,
        chunkedPayload: CloudBackupChunkedPayload
    ): Result<CloudBackupManifest>

    suspend fun downloadChunks(
        userId: String,
        snapshotId: String
    ): Result<List<CloudBackupPayloadChunk>>

    suspend fun deleteSnapshot(
        userId: String,
        snapshotId: String
    ): Result<Unit>
}
