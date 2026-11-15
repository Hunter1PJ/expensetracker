package com.suguru.expensetracker.domain.model

import com.suguru.expensetracker.BuildConfig
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.repository.RoomBackupRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudBackupManifestTest {

    private val createdInstant = Instant.parse("2026-11-15T08:00:00Z")
    private val completedInstant = Instant.parse("2026-11-15T08:00:05Z")
    private val validPayloadSha256 = "0123456789abcdef".repeat(4)
    private val validContentFingerprint = "fedcba9876543210".repeat(4)

    private fun validUploadingManifest(
        snapshotId: String = "snap_20261115_080000",
        uid: String = "firebase_uid_123",
        createdAt: Instant = createdInstant,
        completedAt: Instant? = null,
        formatVersion: Int = RoomBackupRepository.CURRENT_FORMAT_VERSION,
        databaseSchemaVersion: Int = ExpenseTrackerDatabase.SCHEMA_VERSION,
        appVersion: String = BuildConfig.VERSION_NAME,
        chunkCount: Int = 3,
        compressedBytes: Long = 1_024L,
        uncompressedBytes: Long = 4_096L,
        payloadSha256: String = validPayloadSha256,
        contentFingerprint: String = validContentFingerprint
    ): CloudBackupManifest = CloudBackupManifest(
        snapshotId = snapshotId,
        uid = uid,
        status = CloudBackupManifestStatus.UPLOADING,
        createdAt = createdAt,
        completedAt = completedAt,
        formatVersion = formatVersion,
        databaseSchemaVersion = databaseSchemaVersion,
        appVersion = appVersion,
        chunkCount = chunkCount,
        compressedBytes = compressedBytes,
        uncompressedBytes = uncompressedBytes,
        payloadSha256 = payloadSha256,
        contentFingerprint = contentFingerprint
    )

    private fun validCompleteManifest(
        createdAt: Instant = createdInstant,
        completedAt: Instant? = completedInstant
    ): CloudBackupManifest = validUploadingManifest(
        createdAt = createdAt,
        completedAt = completedAt
    ).copy(status = CloudBackupManifestStatus.COMPLETE)

    private fun assertInvalid(manifest: CloudBackupManifest, expectedMessagePart: String? = null) {
        val instanceResult = manifest.validate()
        val staticResult = CloudBackupManifest.validate(manifest)

        assertTrue("Expected failure for manifest: $manifest", instanceResult.isFailure)
        assertTrue("Expected failure from static validate for manifest: $manifest", staticResult.isFailure)

        val exception = instanceResult.exceptionOrNull()
        assertTrue(
            "Expected IllegalArgumentException, got ${exception?.javaClass?.name}",
            exception is IllegalArgumentException
        )
        assertEquals(exception?.message, staticResult.exceptionOrNull()?.message)

        if (expectedMessagePart != null) {
            assertTrue(
                "Expected error message to contain '$expectedMessagePart', was '${exception?.message}'",
                exception?.message?.contains(expectedMessagePart, ignoreCase = true) == true
            )
        }
    }

    @Test
    fun `1 - valid UPLOADING manifest passes validation`() {
        val manifest = validUploadingManifest()

        val result = manifest.validate()

        assertTrue(result.isSuccess)
        assertSame(manifest, result.getOrThrow())
        assertEquals(manifest, CloudBackupManifest.validate(manifest).getOrThrow())
    }

    @Test
    fun `2 - valid COMPLETE manifest passes validation including equal timestamps and small compressed growth`() {
        val laterCompletion = validCompleteManifest()
        assertTrue(laterCompletion.validate().isSuccess)

        val sameTimestampCompletion = validCompleteManifest(
            createdAt = createdInstant,
            completedAt = createdInstant
        )
        assertTrue(sameTimestampCompletion.validate().isSuccess)

        // Small payloads can have compressed size larger than uncompressed size due to headers
        val smallPayloadComplete = laterCompletion.copy(
            compressedBytes = 32L,
            uncompressedBytes = 12L
        )
        assertTrue(smallPayloadComplete.validate().isSuccess)

        // Instant.EPOCH is valid under java.time.Instant domain conventions
        val epochManifest = validCompleteManifest(
            createdAt = Instant.EPOCH,
            completedAt = Instant.EPOCH.plusSeconds(1)
        )
        assertTrue(epochManifest.validate().isSuccess)
    }

    @Test
    fun `3 - blank snapshot ID is rejected`() {
        for (blankId in listOf("", " ", "   ", "\t\n")) {
            assertInvalid(
                validUploadingManifest(snapshotId = blankId),
                expectedMessagePart = "Snapshot ID"
            )
        }
    }

    @Test
    fun `4 - blank UID is rejected`() {
        for (blankUid in listOf("", " ", "   ", "\t\n")) {
            assertInvalid(
                validUploadingManifest(uid = blankUid),
                expectedMessagePart = "UID"
            )
        }
    }

    @Test
    fun `5 - UID with leading or trailing whitespace is rejected`() {
        for (paddedUid in listOf(" uid_123", "uid_123 ", " uid_123 ", "\tuid_123", "uid_123\n")) {
            assertInvalid(
                validUploadingManifest(uid = paddedUid),
                expectedMessagePart = "whitespace"
            )
        }
    }

    @Test
    fun `6 - UPLOADING manifest with completedAt set is rejected`() {
        assertInvalid(
            validUploadingManifest(completedAt = completedInstant),
            expectedMessagePart = "UPLOADING"
        )
        assertInvalid(
            validUploadingManifest(completedAt = createdInstant),
            expectedMessagePart = "UPLOADING"
        )
    }

    @Test
    fun `7 - COMPLETE manifest without completedAt is rejected`() {
        assertInvalid(
            validCompleteManifest(completedAt = null),
            expectedMessagePart = "COMPLETE"
        )
    }

    @Test
    fun `8 - COMPLETE manifest with completion earlier than creation is rejected`() {
        assertInvalid(
            validCompleteManifest(
                createdAt = createdInstant,
                completedAt = createdInstant.minusNanos(1)
            ),
            expectedMessagePart = "earlier"
        )
        assertInvalid(
            validCompleteManifest(
                createdAt = createdInstant,
                completedAt = createdInstant.minusSeconds(60)
            ),
            expectedMessagePart = "earlier"
        )
    }

    @Test
    fun `9 - non-positive format and database schema versions are rejected`() {
        for (invalidVersion in listOf(0, -1, Int.MIN_VALUE)) {
            assertInvalid(
                validUploadingManifest(formatVersion = invalidVersion),
                expectedMessagePart = "format version"
            )
            assertInvalid(
                validUploadingManifest(databaseSchemaVersion = invalidVersion),
                expectedMessagePart = "schema version"
            )
        }
    }

    @Test
    fun `10 - blank app version is rejected`() {
        for (blankVersion in listOf("", " ", "   ", "\t\n")) {
            assertInvalid(
                validUploadingManifest(appVersion = blankVersion),
                expectedMessagePart = "App version"
            )
        }
    }

    @Test
    fun `11 - non-positive chunk count is rejected`() {
        for (invalidChunkCount in listOf(0, -1, Int.MIN_VALUE)) {
            assertInvalid(
                validUploadingManifest(chunkCount = invalidChunkCount),
                expectedMessagePart = "Chunk count"
            )
        }
    }

    @Test
    fun `12 - non-positive compressed and uncompressed byte sizes are rejected`() {
        for (invalidSize in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertInvalid(
                validUploadingManifest(compressedBytes = invalidSize),
                expectedMessagePart = "Compressed byte count"
            )
            assertInvalid(
                validUploadingManifest(uncompressedBytes = invalidSize),
                expectedMessagePart = "Uncompressed byte count"
            )
        }
    }

    @Test
    fun `13 - invalid payload SHA-256 values are rejected`() {
        val invalidHashes = listOf(
            "",
            "a".repeat(63),
            "a".repeat(65),
            validPayloadSha256.uppercase(),
            validPayloadSha256.dropLast(1) + "A",
            "g" + "a".repeat(63),
            " ${validPayloadSha256.drop(1)}",
            "${validPayloadSha256.dropLast(1)} "
        )

        for (invalidHash in invalidHashes) {
            assertInvalid(
                validUploadingManifest(payloadSha256 = invalidHash),
                expectedMessagePart = "Payload SHA-256"
            )
        }
    }

    @Test
    fun `14 - invalid content fingerprint values are rejected`() {
        val invalidFingerprints = listOf(
            "",
            "b".repeat(63),
            "b".repeat(65),
            validContentFingerprint.uppercase(),
            validContentFingerprint.dropLast(1) + "F",
            "z" + "b".repeat(63),
            " ${validContentFingerprint.drop(1)}",
            "${validContentFingerprint.dropLast(1)}\n"
        )

        for (invalidFingerprint in invalidFingerprints) {
            assertInvalid(
                validUploadingManifest(contentFingerprint = invalidFingerprint),
                expectedMessagePart = "Content fingerprint"
            )
        }
    }

    @Test
    fun `15 - manifest validation does not mutate or normalize source data`() {
        val validManifest = validCompleteManifest()
        val validSnapshot = validManifest.copy()

        val validResult = validManifest.validate()
        assertTrue(validResult.isSuccess)
        assertEquals(validSnapshot, validManifest)
        assertEquals(validSnapshot, validResult.getOrThrow())

        // Untrusted padded/uppercase inputs must be rejected rather than silently normalized
        val untrustedUidManifest = validUploadingManifest(uid = "  uid_with_spaces  ")
        val untrustedUidSnapshot = untrustedUidManifest.copy()
        assertTrue(untrustedUidManifest.validate().isFailure)
        assertEquals(untrustedUidSnapshot, untrustedUidManifest)

        val uppercaseShaManifest = validUploadingManifest(payloadSha256 = validPayloadSha256.uppercase())
        val uppercaseShaSnapshot = uppercaseShaManifest.copy()
        assertTrue(uppercaseShaManifest.validate().isFailure)
        assertEquals(uppercaseShaSnapshot, uppercaseShaManifest)
    }

    @Test
    fun `16 - manifest integrates cleanly with chunker and fingerprint outputs`() {
        val rawBytes = "{\"sample\":\"payload\"}".toByteArray(Charsets.UTF_8)
        val chunked = CloudBackupPayloadChunker(maxChunkSizeBytes = 8).chunk(rawBytes)
        val manifest = CloudBackupManifest(
            snapshotId = "snap_integration_01",
            uid = "uid_integration_01",
            status = CloudBackupManifestStatus.COMPLETE,
            createdAt = createdInstant,
            completedAt = completedInstant,
            formatVersion = RoomBackupRepository.CURRENT_FORMAT_VERSION,
            databaseSchemaVersion = ExpenseTrackerDatabase.SCHEMA_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            chunkCount = chunked.totalChunkCount,
            compressedBytes = chunked.totalBytes.toLong(),
            uncompressedBytes = 128L,
            payloadSha256 = chunked.payloadSha256,
            contentFingerprint = validContentFingerprint
        )

        assertTrue(manifest.validate().isSuccess)
    }
}
