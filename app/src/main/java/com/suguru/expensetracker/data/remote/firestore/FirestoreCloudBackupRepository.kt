package com.suguru.expensetracker.data.remote.firestore

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.FieldValue
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.error.CloudBackupRemoteException
import com.suguru.expensetracker.domain.model.CloudBackupChunkedPayload
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupPayloadCodec
import com.suguru.expensetracker.domain.repository.CloudBackupRemoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Firestore-backed implementation of [CloudBackupRemoteRepository].
 *
 * All documents are stored strictly under `/users/{uid}/backup_manifests/{snapshotId}` and
 * `/users/{uid}/backup_manifests/{snapshotId}/chunks/chunk_{index}` on the named Firestore
 * database configured via `R.string.firestore_database_id`.
 */
class FirestoreCloudBackupRepository internal constructor(
    private val gateway: RemoteBackupGateway,
    private val chunker: CloudBackupPayloadChunker = CloudBackupPayloadChunker()
) : CloudBackupRemoteRepository {

    constructor(
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth
    ) : this(FirestoreRemoteBackupGateway(db, auth))

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    )

    private fun requireAuthenticatedUid(): String {
        return gateway.currentUserUid()?.takeIf { it.isNotBlank() }
            ?: throw CloudBackupRemoteException.Unauthenticated()
    }

    private fun requireMatchingUid(authenticatedUid: String, requestedUid: String) {
        if (authenticatedUid != requestedUid) {
            throw CloudBackupRemoteException.UidMismatch(
                "Authenticated user does not match requested backup owner."
            )
        }
    }

    override fun observeManifests(userId: String): Flow<List<CloudBackupManifest>> = flow {
        val authenticatedUid = gateway.currentUserUid()
        if (userId.isBlank() || authenticatedUid == null || authenticatedUid != userId) {
            emitAll(emptyFlow())
            return@flow
        }
        emitAll(
            gateway.observeManifests(authenticatedUid, MAX_MANIFESTS_LIMIT)
                .map { manifests ->
                    manifests.filter { it.snapshotId == PRIMARY_SNAPSHOT_ID && it.status == CloudBackupManifestStatus.COMPLETE }
                }
        )
    }

    override suspend fun getLatestCompleteManifest(): Result<CloudBackupManifest?> {
        var path = "users/unauthenticated/backup_manifests"
        return try {
            val uid = requireAuthenticatedUid()
            path = "users/$uid/backup_manifests"
            val primaryDto = gateway.getManifest(uid, PRIMARY_SNAPSHOT_ID)
            val primary = primaryDto?.toDomainOrNull()
            if (primary != null && primary.status == CloudBackupManifestStatus.COMPLETE && primary.completedAt != null) {
                return Result.success(primary)
            }

            val candidates = gateway.listCompleteManifests(uid)
                .mapNotNull { dto ->
                    if (dto.userId != uid) return@mapNotNull null
                    dto.toDomainOrNull()
                }
                .filter { manifest ->
                    manifest.status == CloudBackupManifestStatus.COMPLETE && manifest.completedAt != null
                }

            val latest = candidates.maxWithOrNull(
                compareBy<CloudBackupManifest>(
                    { it.completedAt },
                    { it.createdAt },
                    { it.snapshotId }
                )
            )
            Result.success(latest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(toTypedRemoteException(e))
        }
    }

    override suspend fun uploadBackup(
        userId: String,
        manifest: CloudBackupManifest,
        chunkedPayload: CloudBackupChunkedPayload
    ): Result<CloudBackupManifest> {
        var currentPath = "users/$userId/backup_manifests/$PRIMARY_SNAPSHOT_ID"
        var currentOp = OperationType.CREATE
        var stagingUploading = false
        var authenticatedUid: String? = null
        val stagingSnapshotId = "gen_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "").take(8)}"

        return try {
            val uid = requireAuthenticatedUid()
            authenticatedUid = uid
            requireMatchingUid(uid, userId)
            requireMatchingUid(uid, manifest.uid)
            validateUploadInputs(uid, manifest, chunkedPayload)

            val existingPrimaryDto = gateway.getManifest(uid, PRIMARY_SNAPSHOT_ID)
            val existingPrimary = existingPrimaryDto?.toDomainOrNull()
            if (existingPrimary != null && existingPrimary.status == CloudBackupManifestStatus.COMPLETE &&
                existingPrimary.contentFingerprint == manifest.contentFingerprint) {
                return Result.success(existingPrimary)
            }

            currentOp = OperationType.CREATE
            val stagingManifest = manifest.copy(snapshotId = stagingSnapshotId)
            gateway.createUploadingManifest(
                uid = uid,
                snapshotId = stagingSnapshotId,
                payload = FirestoreBackupManifestDto.toUploadingCreatePayload(uid, stagingManifest)
            )
            stagingUploading = true

            for (chunk in chunkedPayload.chunks) {
                val chunkId = chunkDocumentId(chunk.index)
                currentPath = "users/$uid/backup_manifests/$stagingSnapshotId/chunks/$chunkId"
                currentOp = OperationType.CREATE
                gateway.createChunk(
                    uid = uid,
                    snapshotId = stagingSnapshotId,
                    chunkId = chunkId,
                    payload = FirestoreBackupChunkDto.toCreatePayload(uid, stagingSnapshotId, chunk)
                )
            }

            currentPath = "users/$uid/backup_manifests/$stagingSnapshotId"
            currentOp = OperationType.UPDATE
            gateway.markManifestComplete(
                uid = uid,
                snapshotId = stagingSnapshotId,
                payload = FirestoreBackupManifestDto.toCompleteUpdatePayload()
            )

            val completedStaging = gateway.getManifest(uid, stagingSnapshotId)
                ?: throw CloudBackupRemoteException.RemoteOperationFailed("PROMOTION_FAILED", "Staging manifest missing.")

            val stagingDomain = completedStaging.toDomainOrNull() ?: manifest
            val primaryPayload = FirestoreBackupManifestDto.toUploadingCreatePayload(uid, stagingDomain).toMutableMap().apply {
                this["status"] = CloudBackupManifestStatus.COMPLETE.name
                this["completedAt"] = completedStaging.completedAt ?: com.google.firebase.Timestamp.now()
                this["updatedAt"] = FieldValue.serverTimestamp()
            }

            val primaryExists = existingPrimaryDto != null
            currentPath = "users/$uid/backup_manifests/$PRIMARY_SNAPSHOT_ID"
            currentOp = if (primaryExists) OperationType.UPDATE else OperationType.CREATE
            if (primaryExists) {
                gateway.markManifestComplete(uid, PRIMARY_SNAPSHOT_ID, primaryPayload)
            } else {
                gateway.createUploadingManifest(uid, PRIMARY_SNAPSHOT_ID, primaryPayload)
            }

            val stagingChunks = gateway.listChunks(uid, stagingSnapshotId)
            for (chunkDto in stagingChunks) {
                val chunkId = chunkDto.id
                val chunkPayload = mapOf(
                    "userId" to uid,
                    "snapshotId" to PRIMARY_SNAPSHOT_ID,
                    "index" to chunkDto.index,
                    "totalCount" to chunkDto.totalCount,
                    "sha256" to chunkDto.sha256,
                    "byteCount" to chunkDto.byteCount,
                    "payload" to (chunkDto.payload ?: com.google.firebase.firestore.Blob.fromBytes(ByteArray(0)))
                )
                gateway.createChunk(uid, PRIMARY_SNAPSHOT_ID, chunkId, chunkPayload)
            }

            try {
                gateway.deleteSnapshotDocuments(uid, stagingSnapshotId)
            } catch (_: Exception) {}

            val committed = gateway.getManifest(uid, PRIMARY_SNAPSHOT_ID)
                ?.toDomainOrNull()
                ?: manifest.copy(
                    snapshotId = PRIMARY_SNAPSHOT_ID,
                    status = CloudBackupManifestStatus.COMPLETE,
                    completedAt = Instant.now()
                )
            Result.success(committed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, currentPath)
            val uid = authenticatedUid
            if (stagingUploading && uid != null && gateway.currentUserUid() == uid) {
                try {
                    gateway.deleteSnapshotDocuments(uid, stagingSnapshotId)
                } catch (_: Exception) {}
            }
            Result.failure(toTypedRemoteException(e))
        }
    }

    override suspend fun downloadChunks(
        userId: String,
        snapshotId: String
    ): Result<List<CloudBackupPayloadChunk>> {
        var path = "users/$userId/backup_manifests/$snapshotId"
        var op = OperationType.GET
        return try {
            val uid = requireAuthenticatedUid()
            requireMatchingUid(uid, userId)
            val targetId = if (gateway.getManifest(uid, snapshotId) != null) snapshotId else PRIMARY_SNAPSHOT_ID
            if (!isValidFirestoreId(targetId)) {
                throw CloudBackupRemoteException.InvalidSnapshot("Snapshot ID is invalid.")
            }

            path = "users/$uid/backup_manifests/$targetId"
            op = OperationType.GET
            val manifestDto = gateway.getManifest(uid, targetId)
                ?: throw CloudBackupRemoteException.SnapshotNotFound(targetId)

            val manifest = manifestDto.toDomainOrNull()
                ?: throw CloudBackupRemoteException.InvalidSnapshot(
                    "Manifest '$targetId' is malformed or missing required fields."
                )
            if (manifest.uid != uid) {
                throw CloudBackupRemoteException.UidMismatch()
            }
            if (manifest.status != CloudBackupManifestStatus.COMPLETE) {
                throw CloudBackupRemoteException.SnapshotNotComplete(targetId, manifest.status)
            }

            path = "users/$uid/backup_manifests/$targetId/chunks"
            op = OperationType.LIST
            val chunkDtos = gateway.listChunks(uid, targetId)
            if (chunkDtos.size != manifest.chunkCount) {
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = targetId,
                    message = "Expected ${manifest.chunkCount} chunk(s) for snapshot '$targetId', found ${chunkDtos.size}."
                )
            }

            val chunks = chunkDtos.map { dto ->
                val chunk = dto.toDomainOrNull(
                    expectedUid = uid,
                    expectedSnapshotId = targetId
                ) ?: throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = targetId,
                    message = "Malformed or corrupted backup chunk '${dto.id}'."
                )
                if (chunk.totalCount != manifest.chunkCount) {
                    throw CloudBackupRemoteException.ChunkIntegrityError(
                        snapshotId = targetId,
                        message = "Chunk ${chunk.index} totalCount (${chunk.totalCount}) does not match manifest (${manifest.chunkCount})."
                    )
                }
                chunk
            }

            val reassembled = chunker.reassemble(chunks, manifest.payloadSha256).getOrElse { error ->
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = targetId,
                    message = error.message ?: "Backup chunk checksum verification failed.",
                    cause = error
                )
            }
            if (reassembled.size.toLong() != manifest.compressedBytes) {
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = targetId,
                    message = "Reassembled byte count (${reassembled.size}) does not match manifest compressedBytes (${manifest.compressedBytes})."
                )
            }

            Result.success(chunks.sortedBy { it.index })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path)
            Result.failure(toTypedRemoteException(e))
        }
    }

    override suspend fun deleteSnapshot(
        userId: String,
        snapshotId: String
    ): Result<Unit> {
        val targetId = if (snapshotId == PRIMARY_SNAPSHOT_ID || gateway.getManifest(requireAuthenticatedUid(), snapshotId) != null) snapshotId else PRIMARY_SNAPSHOT_ID
        val path = "users/$userId/backup_manifests/$targetId"
        return try {
            val uid = requireAuthenticatedUid()
            requireMatchingUid(uid, userId)
            if (!isValidFirestoreId(targetId)) {
                throw CloudBackupRemoteException.InvalidSnapshot("Snapshot ID is invalid.")
            }

            gateway.deleteSnapshotDocuments(uid, targetId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(toTypedRemoteException(e))
        }
    }

    private fun validateUploadInputs(
        uid: String,
        manifest: CloudBackupManifest,
        chunkedPayload: CloudBackupChunkedPayload
    ) {
        if (!isValidFirestoreId(manifest.snapshotId)) {
            throw CloudBackupRemoteException.InvalidSnapshot(
                "Snapshot ID must match ^[a-zA-Z0-9_-]{1,128}$"
            )
        }
        if (manifest.uid != uid) {
            throw CloudBackupRemoteException.UidMismatch("Manifest UID must match authenticated user.")
        }
        if (manifest.status != CloudBackupManifestStatus.UPLOADING) {
            throw CloudBackupRemoteException.InvalidSnapshot(
                "New upload manifest must start in UPLOADING status."
            )
        }
        manifest.validate().getOrElse { error ->
            throw CloudBackupRemoteException.InvalidSnapshot(
                error.message ?: "Invalid cloud backup manifest.",
                error
            )
        }
        if (manifest.formatVersion !in 1..1000) {
            throw CloudBackupRemoteException.InvalidSnapshot("formatVersion out of supported range.")
        }
        if (manifest.databaseSchemaVersion !in 1..1000) {
            throw CloudBackupRemoteException.InvalidSnapshot("databaseSchemaVersion out of supported range.")
        }
        if (manifest.appVersion.length !in 1..64) {
            throw CloudBackupRemoteException.InvalidSnapshot("appVersion must be 1..64 characters.")
        }
        if (manifest.chunkCount !in 1..MAX_CHUNKS_COUNT) {
            throw CloudBackupRemoteException.InvalidSnapshot("chunkCount out of supported range.")
        }
        if (manifest.compressedBytes !in 1L..CloudBackupPayloadCodec.DEFAULT_MAX_UNCOMPRESSED_BYTES.toLong()) {
            throw CloudBackupRemoteException.InvalidSnapshot("compressedBytes exceeds 16 MiB bound.")
        }
        if (manifest.uncompressedBytes !in 1L..CloudBackupPayloadCodec.DEFAULT_MAX_UNCOMPRESSED_BYTES.toLong()) {
            throw CloudBackupRemoteException.InvalidSnapshot("uncompressedBytes exceeds 16 MiB bound.")
        }
        if (chunkedPayload.payloadSha256 != manifest.payloadSha256) {
            throw CloudBackupRemoteException.InvalidSnapshot(
                "Chunked payload SHA-256 does not match manifest payloadSha256."
            )
        }
        if (chunkedPayload.totalBytes.toLong() != manifest.compressedBytes) {
            throw CloudBackupRemoteException.InvalidSnapshot(
                "Chunked payload totalBytes (${chunkedPayload.totalBytes}) does not match manifest (${manifest.compressedBytes})."
            )
        }
        if (chunkedPayload.chunks.size != manifest.chunkCount) {
            throw CloudBackupRemoteException.InvalidSnapshot(
                "Chunk count (${chunkedPayload.chunks.size}) does not match manifest (${manifest.chunkCount})."
            )
        }
        chunkedPayload.chunks.forEach { chunk ->
            if (chunk.bytes.size !in 1..CloudBackupPayloadChunker.DEFAULT_MAX_CHUNK_SIZE_BYTES) {
                throw CloudBackupRemoteException.InvalidSnapshot(
                    "Chunk ${chunk.index} size out of bounds."
                )
            }
        }
        chunker.reassemble(chunkedPayload.chunks, manifest.payloadSha256).getOrElse { error ->
            throw CloudBackupRemoteException.InvalidSnapshot(
                error.message ?: "Chunk integrity verification failed prior to upload.",
                error
            )
        }
    }

    private fun toTypedRemoteException(error: Exception): CloudBackupRemoteException {
        return when (error) {
            is CloudBackupRemoteException -> error
            else -> CloudBackupRemoteException.RemoteOperationFailed(
                failureCode = mapFailureCode(error),
                message = error.message ?: "Remote backup operation failed.",
                cause = error
            )
        }
    }

    private fun mapFailureCode(error: Exception): String {
        return when (error) {
            is CloudBackupRemoteException -> error.failureCode
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "PERMISSION_DENIED"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "NETWORK_UNAVAILABLE"
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "DEADLINE_EXCEEDED"
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "QUOTA_EXCEEDED"
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "UNAUTHENTICATED"
                FirebaseFirestoreException.Code.ABORTED -> "ABORTED"
                else -> "FIRESTORE_ERROR"
            }
            is FirebaseNetworkException -> "NETWORK_UNAVAILABLE"
            is IllegalArgumentException -> "VALIDATION_ERROR"
            else -> "UPLOAD_FAILED"
        }
    }

    private fun maxOfInstant(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

    companion object {
        private const val MAX_MANIFESTS_LIMIT = 20L
        private const val MAX_CHUNKS_COUNT = 500
        const val PRIMARY_SNAPSHOT_ID = "primary_restore_point"
        private val FIRESTORE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        fun isValidFirestoreId(id: String): Boolean = FIRESTORE_ID_REGEX.matches(id)

        fun chunkDocumentId(index: Int): String = "chunk_$index"
    }
}
