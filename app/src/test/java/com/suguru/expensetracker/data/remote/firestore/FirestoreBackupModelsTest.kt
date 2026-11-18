package com.suguru.expensetracker.data.remote.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FirestoreBackupModelsTest {

    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    @Test
    fun `toUploadingCreatePayload excludes snapshotId and optional fields and uses serverTimestamp`() {
        val manifest = CloudBackupManifest(
            snapshotId = "snap_001",
            uid = "uid_123",
            status = CloudBackupManifestStatus.UPLOADING,
            createdAt = Instant.ofEpochSecond(1_763_200_000L),
            formatVersion = 1,
            databaseSchemaVersion = 4,
            appVersion = "1.5.0",
            chunkCount = 2,
            compressedBytes = 1024L,
            uncompressedBytes = 4096L,
            payloadSha256 = shaA,
            contentFingerprint = shaB
        )

        val map = FirestoreBackupManifestDto.toUploadingCreatePayload("uid_123", manifest)
        assertFalse(map.containsKey("snapshotId"))
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("completedAt"))
        assertFalse(map.containsKey("failedAt"))
        assertFalse(map.containsKey("failureCode"))
        assertFalse(map.containsKey("email"))
        assertEquals("uid_123", map["userId"])
        assertEquals("UPLOADING", map["status"])
        assertTrue(map["createdAt"] is FieldValue)
        assertTrue(map["updatedAt"] is FieldValue)
    }

    @Test
    fun `toUploadingRetryUpdatePayload clears failedAt and failureCode and resets status`() {
        val retryMap = FirestoreBackupManifestDto.toUploadingRetryUpdatePayload()
        assertEquals(setOf("status", "updatedAt", "failedAt", "failureCode"), retryMap.keys)
        assertEquals("UPLOADING", retryMap["status"])
        assertTrue(retryMap["updatedAt"] is FieldValue)
        assertTrue(retryMap["failedAt"] is FieldValue)
        assertTrue(retryMap["failureCode"] is FieldValue)
    }

    @Test
    fun `toCompleteUpdatePayload and toFailedUpdatePayload include only action-specific fields`() {
        val completeMap = FirestoreBackupManifestDto.toCompleteUpdatePayload()
        assertEquals(setOf("status", "completedAt", "updatedAt"), completeMap.keys)
        assertEquals("COMPLETE", completeMap["status"])

        val failedMap = FirestoreBackupManifestDto.toFailedUpdatePayload("NETWORK_UNAVAILABLE")
        assertEquals(setOf("status", "failedAt", "failureCode", "updatedAt"), failedMap.keys)
        assertEquals("FAILED", failedMap["status"])
        assertEquals("NETWORK_UNAVAILABLE", failedMap["failureCode"])
    }

    @Test
    fun `toPrimaryCreatePayload includes all 14 required fields with explicit generation timestamps`() {
        val manifest = CloudBackupManifest(
            snapshotId = "primary_restore_point",
            uid = "uid_123",
            status = CloudBackupManifestStatus.COMPLETE,
            createdAt = Instant.ofEpochSecond(1_763_200_000L),
            completedAt = Instant.ofEpochSecond(1_763_200_010L),
            formatVersion = 1,
            databaseSchemaVersion = 4,
            appVersion = "1.5.0",
            chunkCount = 2,
            compressedBytes = 1024L,
            uncompressedBytes = 4096L,
            payloadSha256 = shaA,
            contentFingerprint = shaB
        )
        val genCreated = Timestamp(1_763_200_000L, 0)
        val genCompleted = Timestamp(1_763_200_010L, 0)

        val map = FirestoreBackupManifestDto.toPrimaryCreatePayload(
            uid = "uid_123",
            manifest = manifest,
            generationId = "gen_001",
            generationCreatedAt = genCreated,
            generationCompletedAt = genCompleted
        )

        val expectedKeys = setOf(
            "userId", "status", "createdAt", "updatedAt", "completedAt",
            "formatVersion", "databaseSchemaVersion", "appVersion",
            "chunkCount", "compressedBytes", "uncompressedBytes",
            "payloadSha256", "contentFingerprint", "generationId"
        )
        assertEquals(expectedKeys, map.keys)
        assertEquals("uid_123", map["userId"])
        assertEquals("COMPLETE", map["status"])
        assertEquals(genCreated, map["createdAt"])
        assertEquals(genCompleted, map["completedAt"])
        assertEquals("gen_001", map["generationId"])
        assertTrue(map["updatedAt"] is FieldValue)
    }

    @Test
    fun `toPrimaryPromotionUpdatePayload excludes createdAt and userId to preserve immutability`() {
        val manifest = CloudBackupManifest(
            snapshotId = "primary_restore_point",
            uid = "uid_123",
            status = CloudBackupManifestStatus.COMPLETE,
            createdAt = Instant.ofEpochSecond(1_763_200_000L),
            completedAt = Instant.ofEpochSecond(1_763_200_020L),
            formatVersion = 1,
            databaseSchemaVersion = 4,
            appVersion = "1.5.0",
            chunkCount = 3,
            compressedBytes = 2048L,
            uncompressedBytes = 8192L,
            payloadSha256 = shaA,
            contentFingerprint = shaB
        )
        val genCompleted = Timestamp(1_763_200_020L, 0)

        val map = FirestoreBackupManifestDto.toPrimaryPromotionUpdatePayload(
            manifest = manifest,
            generationId = "gen_002",
            generationCompletedAt = genCompleted
        )

        assertFalse(map.containsKey("createdAt"))
        assertFalse(map.containsKey("userId"))
        val expectedKeys = setOf(
            "status", "updatedAt", "completedAt",
            "formatVersion", "databaseSchemaVersion", "appVersion",
            "chunkCount", "compressedBytes", "uncompressedBytes",
            "payloadSha256", "contentFingerprint", "generationId"
        )
        assertEquals(expectedKeys, map.keys)
        assertEquals("COMPLETE", map["status"])
        assertEquals(genCompleted, map["completedAt"])
        assertEquals("gen_002", map["generationId"])
        assertTrue(map["updatedAt"] is FieldValue)
    }

    @Test
    fun `manifest DTO maps valid COMPLETE and FAILED documents and rejects malformed values`() {
        val dto = FirestoreBackupManifestDto(
            snapshotId = "snap_001",
            userId = "uid_123",
            status = "COMPLETE",
            createdAt = Timestamp(1_763_200_000L, 0),
            updatedAt = Timestamp(1_763_200_010L, 0),
            completedAt = Timestamp(1_763_200_010L, 0),
            formatVersion = 1L,
            databaseSchemaVersion = 4L,
            appVersion = "1.5.0",
            chunkCount = 1L,
            compressedBytes = 512L,
            uncompressedBytes = 2048L,
            payloadSha256 = shaA,
            contentFingerprint = shaB
        )
        val domain = dto.toDomainOrNull()
        assertNotNull(domain)
        assertEquals(CloudBackupManifestStatus.COMPLETE, domain!!.status)
        assertEquals("snap_001", domain.snapshotId)

        val failedDto = dto.copy(
            status = "FAILED",
            completedAt = null,
            failedAt = Timestamp(1_763_200_012L, 0),
            failureCode = "NETWORK_UNAVAILABLE"
        )
        val failedDomain = failedDto.toDomainOrNull()
        assertNotNull(failedDomain)
        assertEquals(CloudBackupManifestStatus.FAILED, failedDomain!!.status)
        assertEquals("NETWORK_UNAVAILABLE", failedDomain.failureCode)

        assertNull(dto.copy(status = "UNKNOWN_FUTURE_STATUS").toDomainOrNull())
        assertNull(dto.copy(chunkCount = Long.MAX_VALUE).toDomainOrNull())
        assertNull(dto.copy(formatVersion = 0L).toDomainOrNull())
    }

    @Test
    fun `chunk DTO preserves arbitrary binary bytes via Firestore Blob without Base64 expansion`() {
        val rawBytes = byteArrayOf(
            0x00, 0x1F, -0x75, 0x08, 0x00, -0x01, 0x7F, -0x80,
            0x0A, 0x0D, 0x55, -0x56, 0x42
        )
        val chunk = CloudBackupPayloadChunk(
            index = 0,
            totalCount = 1,
            bytes = rawBytes,
            sha256 = CloudBackupPayloadChunker.sha256Hex(rawBytes)
        )
        val map = FirestoreBackupChunkDto.toCreatePayload("uid_123", "snap_001", chunk)
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("payloadBase64"))
        assertEquals("uid_123", map["userId"])
        assertEquals("snap_001", map["snapshotId"])
        assertEquals(0L, map["index"])
        assertEquals(1L, map["totalCount"])
        assertEquals(rawBytes.size.toLong(), map["byteCount"])
        assertTrue(map["payload"] is Blob)

        val dto = FirestoreBackupChunkDto(
            id = "chunk_0",
            userId = "uid_123",
            snapshotId = "snap_001",
            index = 0L,
            totalCount = 1L,
            sha256 = chunk.sha256,
            byteCount = rawBytes.size.toLong(),
            payload = map["payload"] as Blob,
            createdAt = Timestamp(1_763_200_000L, 0)
        )
        val decoded = dto.toDomainOrNull(expectedUid = "uid_123", expectedSnapshotId = "snap_001")
        assertNotNull(decoded)
        assertArrayEquals(rawBytes, decoded!!.bytes)
        assertEquals(chunk.sha256, decoded.sha256)

        // Rejects mismatched byteCount, wrong UID, or wrong snapshotId
        assertNull(dto.copy(byteCount = rawBytes.size.toLong() + 1).toDomainOrNull())
        assertNull(dto.toDomainOrNull(expectedUid = "other_uid", expectedSnapshotId = "snap_001"))
        assertNull(dto.toDomainOrNull(expectedUid = "uid_123", expectedSnapshotId = "other_snap"))
    }
}
