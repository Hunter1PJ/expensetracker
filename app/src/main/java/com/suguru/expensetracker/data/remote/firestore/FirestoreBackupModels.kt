package com.suguru.expensetracker.data.remote.firestore

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupState
import java.time.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Keep
@IgnoreExtraProperties
data class FirestoreBackupManifestDto(
    @DocumentId val snapshotId: String = "",
    val userId: String = "",
    val status: String = CloudBackupManifestStatus.UPLOADING.name,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val failedAt: Timestamp? = null,
    val failureCode: String? = null,
    val formatVersion: Long = 0L,
    val databaseSchemaVersion: Long = 0L,
    val appVersion: String = "",
    val chunkCount: Long = 0L,
    val compressedBytes: Long = 0L,
    val uncompressedBytes: Long = 0L,
    val payloadSha256: String = "",
    val contentFingerprint: String = "",
    val generationId: String? = null
) {
    fun toDomainOrNull(): CloudBackupManifest? {
        if (snapshotId.isBlank() || userId.isBlank()) return null
        if (formatVersion !in 1L..Int.MAX_VALUE.toLong()) return null
        if (databaseSchemaVersion !in 1L..Int.MAX_VALUE.toLong()) return null
        if (chunkCount !in 1L..Int.MAX_VALUE.toLong()) return null

        val parsedStatus = CloudBackupManifestStatus.entries.firstOrNull { it.name == status }
            ?: return null
        val createdInstant = createdAt?.toInstantCompat() ?: return null
        val completedInstant = completedAt?.toInstantCompat()
        val failedInstant = failedAt?.toInstantCompat()

        val manifest = CloudBackupManifest(
            snapshotId = snapshotId,
            uid = userId,
            status = parsedStatus,
            createdAt = createdInstant,
            completedAt = completedInstant,
            formatVersion = formatVersion.toInt(),
            databaseSchemaVersion = databaseSchemaVersion.toInt(),
            appVersion = appVersion,
            chunkCount = chunkCount.toInt(),
            compressedBytes = compressedBytes,
            uncompressedBytes = uncompressedBytes,
            payloadSha256 = payloadSha256,
            contentFingerprint = contentFingerprint,
            failedAt = failedInstant,
            failureCode = failureCode,
            generationId = generationId
        )
        return manifest.validate().getOrNull()
    }

    fun matchesImmutableMetadata(manifest: CloudBackupManifest): Boolean {
        return userId == manifest.uid &&
            formatVersion == manifest.formatVersion.toLong() &&
            databaseSchemaVersion == manifest.databaseSchemaVersion.toLong() &&
            appVersion == manifest.appVersion &&
            chunkCount == manifest.chunkCount.toLong() &&
            compressedBytes == manifest.compressedBytes &&
            uncompressedBytes == manifest.uncompressedBytes &&
            payloadSha256 == manifest.payloadSha256 &&
            contentFingerprint == manifest.contentFingerprint
    }

    companion object {
        fun toUploadingCreatePayload(
            uid: String,
            manifest: CloudBackupManifest
        ): Map<String, Any> {
            val raw: Map<String, Any?> = mapOf(
                "userId" to uid,
                "status" to CloudBackupManifestStatus.UPLOADING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "formatVersion" to manifest.formatVersion.toLong(),
                "databaseSchemaVersion" to manifest.databaseSchemaVersion.toLong(),
                "appVersion" to manifest.appVersion,
                "chunkCount" to manifest.chunkCount.toLong(),
                "compressedBytes" to manifest.compressedBytes,
                "uncompressedBytes" to manifest.uncompressedBytes,
                "payloadSha256" to manifest.payloadSha256,
                "contentFingerprint" to manifest.contentFingerprint
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }

        fun toUploadingRetryUpdatePayload(): Map<String, Any> = mapOf(
            "status" to CloudBackupManifestStatus.UPLOADING.name,
            "updatedAt" to FieldValue.serverTimestamp(),
            "failedAt" to FieldValue.delete(),
            "failureCode" to FieldValue.delete()
        )

        fun toCompleteUpdatePayload(): Map<String, Any> = mapOf(
            "status" to CloudBackupManifestStatus.COMPLETE.name,
            "completedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun toFailedUpdatePayload(failureCode: String): Map<String, Any> {
            require(CloudBackupState.isValidFailureCode(failureCode)) {
                "Invalid failure code: $failureCode"
            }
            return mapOf(
                "status" to CloudBackupManifestStatus.FAILED.name,
                "failedAt" to FieldValue.serverTimestamp(),
                "failureCode" to failureCode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        fun toPrimaryCreatePayload(
            uid: String,
            manifest: CloudBackupManifest,
            generationId: String,
            generationCreatedAt: Timestamp,
            generationCompletedAt: Timestamp
        ): Map<String, Any> {
            val raw: Map<String, Any?> = mapOf(
                "userId" to uid,
                "status" to CloudBackupManifestStatus.COMPLETE.name,
                "createdAt" to generationCreatedAt,
                "updatedAt" to FieldValue.serverTimestamp(),
                "completedAt" to generationCompletedAt,
                "formatVersion" to manifest.formatVersion.toLong(),
                "databaseSchemaVersion" to manifest.databaseSchemaVersion.toLong(),
                "appVersion" to manifest.appVersion,
                "chunkCount" to manifest.chunkCount.toLong(),
                "compressedBytes" to manifest.compressedBytes,
                "uncompressedBytes" to manifest.uncompressedBytes,
                "payloadSha256" to manifest.payloadSha256,
                "contentFingerprint" to manifest.contentFingerprint,
                "generationId" to generationId
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }

        fun toPrimaryPromotionUpdatePayload(
            manifest: CloudBackupManifest,
            generationId: String,
            generationCompletedAt: Timestamp
        ): Map<String, Any> {
            val raw: Map<String, Any?> = mapOf(
                "status" to CloudBackupManifestStatus.COMPLETE.name,
                "updatedAt" to FieldValue.serverTimestamp(),
                "completedAt" to generationCompletedAt,
                "formatVersion" to manifest.formatVersion.toLong(),
                "databaseSchemaVersion" to manifest.databaseSchemaVersion.toLong(),
                "appVersion" to manifest.appVersion,
                "chunkCount" to manifest.chunkCount.toLong(),
                "compressedBytes" to manifest.compressedBytes,
                "uncompressedBytes" to manifest.uncompressedBytes,
                "payloadSha256" to manifest.payloadSha256,
                "contentFingerprint" to manifest.contentFingerprint,
                "generationId" to generationId
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreBackupChunkDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val snapshotId: String = "",
    val index: Long = -1L,
    val totalCount: Long = 0L,
    val sha256: String = "",
    val byteCount: Long = 0L,
    val payload: Blob? = null,
    val payloadBase64: String? = null,
    val createdAt: Timestamp? = null
) {
    @OptIn(ExperimentalEncodingApi::class)
    fun toDomainOrNull(
        expectedUid: String? = null,
        expectedSnapshotId: String? = null
    ): CloudBackupPayloadChunk? {
        if (userId.isBlank() || snapshotId.isBlank()) return null
        if (expectedUid != null && userId != expectedUid) return null
        if (expectedSnapshotId != null && snapshotId != expectedSnapshotId) return null
        if (index !in 0L..Int.MAX_VALUE.toLong()) return null
        if (totalCount !in 1L..Int.MAX_VALUE.toLong()) return null
        if (index >= totalCount) return null
        if (byteCount !in 1L..CloudBackupPayloadChunker.DEFAULT_MAX_CHUNK_SIZE_BYTES.toLong()) {
            return null
        }

        val rawBytes = when {
            payload != null -> payload.toBytes()
            !payloadBase64.isNullOrBlank() -> try {
                Base64.decode(payloadBase64)
            } catch (_: Exception) {
                return null
            }
            else -> return null
        }

        if (rawBytes.isEmpty()) return null
        if (rawBytes.size.toLong() != byteCount) return null
        if (!SHA256_HEX_REGEX.matches(sha256)) return null

        return CloudBackupPayloadChunk(
            index = index.toInt(),
            totalCount = totalCount.toInt(),
            bytes = rawBytes,
            sha256 = sha256
        )
    }

    companion object {
        private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

        fun toCreatePayload(
            uid: String,
            snapshotId: String,
            chunk: CloudBackupPayloadChunk
        ): Map<String, Any> {
            val raw: Map<String, Any?> = mapOf(
                "userId" to uid,
                "snapshotId" to snapshotId,
                "index" to chunk.index.toLong(),
                "totalCount" to chunk.totalCount.toLong(),
                "sha256" to chunk.sha256,
                "byteCount" to chunk.bytes.size.toLong(),
                "payload" to Blob.fromBytes(chunk.bytes),
                "createdAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }
    }
}

private fun Timestamp.toInstantCompat(): Instant =
    Instant.ofEpochSecond(seconds, nanoseconds.toLong())
