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
        emitAll(gateway.observeManifests(authenticatedUid, MAX_MANIFESTS_LIMIT))
    }

    override suspend fun getLatestCompleteManifest(): Result<CloudBackupManifest?> {
        var path = "users/unauthenticated/backup_manifests"
        return try {
            val uid = requireAuthenticatedUid()
            path = "users/$uid/backup_manifests"
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
        var currentPath = "users/$userId/backup_manifests/${manifest.snapshotId}"
        var currentOp = OperationType.CREATE
        var manifestInUploadingState = false
        var authenticatedUid: String? = null

        return try {
            val uid = requireAuthenticatedUid()
            authenticatedUid = uid
            requireMatchingUid(uid, userId)
            requireMatchingUid(uid, manifest.uid)
            validateUploadInputs(uid, manifest, chunkedPayload)

            currentOp = OperationType.GET
            val existingDto = gateway.getManifest(uid, manifest.snapshotId)
            val isRetryOfExistingSnapshot = if (existingDto != null) {
                if (existingDto.userId != uid) {
                    throw CloudBackupRemoteException.UidMismatch()
                }
                when (existingDto.status) {
                    CloudBackupManifestStatus.COMPLETE.name -> {
                        val existingComplete = existingDto.toDomainOrNull()
                        if (existingComplete != null && existingDto.matchesImmutableMetadata(manifest)) {
                            return Result.success(existingComplete)
                        }
                        throw CloudBackupRemoteException.SnapshotConflict(
                            snapshotId = manifest.snapshotId,
                            message = "Completed snapshot '${manifest.snapshotId}' cannot be replaced with a different payload."
                        )
                    }
                    CloudBackupManifestStatus.UPLOADING.name,
                    CloudBackupManifestStatus.FAILED.name -> {
                        if (!existingDto.matchesImmutableMetadata(manifest)) {
                            throw CloudBackupRemoteException.SnapshotConflict(
                                snapshotId = manifest.snapshotId,
                                message = "Existing snapshot '${manifest.snapshotId}' cannot be retried with different payload metadata."
                            )
                        }
                        currentOp = OperationType.UPDATE
                        gateway.resetManifestToUploading(
                            uid = uid,
                            snapshotId = manifest.snapshotId,
                            payload = FirestoreBackupManifestDto.toUploadingRetryUpdatePayload()
                        )
                        manifestInUploadingState = true
                        true
                    }
                    else -> {
                        throw CloudBackupRemoteException.SnapshotConflict(
                            snapshotId = manifest.snapshotId,
                            message = "Existing snapshot '${manifest.snapshotId}' has unrecognized status '${existingDto.status}'."
                        )
                    }
                }
            } else {
                currentOp = OperationType.CREATE
                gateway.createUploadingManifest(
                    uid = uid,
                    snapshotId = manifest.snapshotId,
                    payload = FirestoreBackupManifestDto.toUploadingCreatePayload(uid, manifest)
                )
                manifestInUploadingState = true
                false
            }

            for (chunk in chunkedPayload.chunks) {
                val chunkId = chunkDocumentId(chunk.index)
                currentPath = "users/$uid/backup_manifests/${manifest.snapshotId}/chunks/$chunkId"

                if (isRetryOfExistingSnapshot) {
                    currentOp = OperationType.GET
                    val existingChunkDto = gateway.getChunk(uid, manifest.snapshotId, chunkId)
                    if (existingChunkDto != null) {
                        val existingChunk = existingChunkDto.toDomainOrNull(
                            expectedUid = uid,
                            expectedSnapshotId = manifest.snapshotId
                        )
                        val chunkMatches = existingChunk != null &&
                            existingChunk.index == chunk.index &&
                            existingChunk.totalCount == chunk.totalCount &&
                            existingChunk.sha256 == chunk.sha256 &&
                            existingChunk.bytes.contentEquals(chunk.bytes)
                        if (chunkMatches) {
                            continue
                        }
                        currentOp = OperationType.DELETE
                        gateway.deleteChunk(uid, manifest.snapshotId, chunkId)
                    }
                }

                currentOp = OperationType.CREATE
                gateway.createChunk(
                    uid = uid,
                    snapshotId = manifest.snapshotId,
                    chunkId = chunkId,
                    payload = FirestoreBackupChunkDto.toCreatePayload(uid, manifest.snapshotId, chunk)
                )
            }

            currentPath = "users/$uid/backup_manifests/${manifest.snapshotId}"
            currentOp = OperationType.UPDATE
            gateway.markManifestComplete(
                uid = uid,
                snapshotId = manifest.snapshotId,
                payload = FirestoreBackupManifestDto.toCompleteUpdatePayload()
            )

            currentOp = OperationType.GET
            val committed = gateway.getManifest(uid, manifest.snapshotId)
                ?.toDomainOrNull()
                ?.takeIf { it.status == CloudBackupManifestStatus.COMPLETE }
                ?: manifest.copy(
                    status = CloudBackupManifestStatus.COMPLETE,
                    completedAt = maxOfInstant(manifest.createdAt, Instant.now())
                )
            Result.success(committed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, currentPath)
            val uid = authenticatedUid
            if (manifestInUploadingState && uid != null && gateway.currentUserUid() == uid) {
                try {
                    val failureCode = mapFailureCode(e)
                    gateway.markManifestFailed(
                        uid = uid,
                        snapshotId = manifest.snapshotId,
                        payload = FirestoreBackupManifestDto.toFailedUpdatePayload(failureCode)
                    )
                } catch (cleanupError: CancellationException) {
                    throw cleanupError
                } catch (cleanupError: Exception) {
                    handleFirestoreError(
                        cleanupError,
                        OperationType.UPDATE,
                        "users/$uid/backup_manifests/${manifest.snapshotId}"
                    )
                }
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
            if (!isValidFirestoreId(snapshotId)) {
                throw CloudBackupRemoteException.InvalidSnapshot("Snapshot ID is invalid.")
            }

            path = "users/$uid/backup_manifests/$snapshotId"
            op = OperationType.GET
            val manifestDto = gateway.getManifest(uid, snapshotId)
                ?: throw CloudBackupRemoteException.SnapshotNotFound(snapshotId)

            val manifest = manifestDto.toDomainOrNull()
                ?: throw CloudBackupRemoteException.InvalidSnapshot(
                    "Manifest '$snapshotId' is malformed or missing required fields."
                )
            if (manifest.uid != uid) {
                throw CloudBackupRemoteException.UidMismatch()
            }
            if (manifest.status != CloudBackupManifestStatus.COMPLETE) {
                throw CloudBackupRemoteException.SnapshotNotComplete(snapshotId, manifest.status)
            }

            path = "users/$uid/backup_manifests/$snapshotId/chunks"
            op = OperationType.LIST
            val chunkDtos = gateway.listChunks(uid, snapshotId)
            if (chunkDtos.size != manifest.chunkCount) {
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = snapshotId,
                    message = "Expected ${manifest.chunkCount} chunk(s) for snapshot '$snapshotId', found ${chunkDtos.size}."
                )
            }

            val chunks = chunkDtos.map { dto ->
                val chunk = dto.toDomainOrNull(
                    expectedUid = uid,
                    expectedSnapshotId = snapshotId
                ) ?: throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = snapshotId,
                    message = "Malformed or corrupted backup chunk '${dto.id}'."
                )
                if (chunk.totalCount != manifest.chunkCount) {
                    throw CloudBackupRemoteException.ChunkIntegrityError(
                        snapshotId = snapshotId,
                        message = "Chunk ${chunk.index} totalCount (${chunk.totalCount}) does not match manifest (${manifest.chunkCount})."
                    )
                }
                chunk
            }

            val reassembled = chunker.reassemble(chunks, manifest.payloadSha256).getOrElse { error ->
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = snapshotId,
                    message = error.message ?: "Backup chunk checksum verification failed.",
                    cause = error
                )
            }
            if (reassembled.size.toLong() != manifest.compressedBytes) {
                throw CloudBackupRemoteException.ChunkIntegrityError(
                    snapshotId = snapshotId,
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
        val path = "users/$userId/backup_manifests/$snapshotId"
        return try {
            val uid = requireAuthenticatedUid()
            requireMatchingUid(uid, userId)
            if (!isValidFirestoreId(snapshotId)) {
                throw CloudBackupRemoteException.InvalidSnapshot("Snapshot ID is invalid.")
            }

            gateway.deleteSnapshotDocuments(uid, snapshotId)
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
        private val FIRESTORE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        fun isValidFirestoreId(id: String): Boolean = FIRESTORE_ID_REGEX.matches(id)

        fun chunkDocumentId(index: Int): String = "chunk_$index"
    }
}
