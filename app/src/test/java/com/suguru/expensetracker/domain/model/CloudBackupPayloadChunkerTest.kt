package com.suguru.expensetracker.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class CloudBackupPayloadChunkerTest {

    private fun sequentialBytes(length: Int): ByteArray =
        ByteArray(length) { index -> ((index * 31 + 7) and 0xFF).toByte() }

    private fun referenceSha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `default maximum chunk size constant is 512 KiB`() {
        val defaultChunker = CloudBackupPayloadChunker()
        assertEquals(512 * 1024, CloudBackupPayloadChunker.DEFAULT_MAX_CHUNK_SIZE_BYTES)
        assertEquals(512 * 1024, defaultChunker.maxChunkSizeBytes)
    }

    @Test
    fun `1 - payload smaller than configured limit produces single chunk`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 16)
        val payload = sequentialBytes(10)

        val result = chunker.chunk(payload)

        assertEquals(1, result.totalChunkCount)
        assertEquals(1, result.chunks.size)
        assertEquals(10, result.totalBytes)
        val chunk = result.chunks.single()
        assertEquals(0, chunk.index)
        assertEquals(1, chunk.totalCount)
        assertArrayEquals(payload, chunk.bytes)
        assertEquals(referenceSha256Hex(payload), chunk.sha256)
        assertEquals(referenceSha256Hex(payload), result.payloadSha256)

        val reassembled = chunker.reassemble(result.chunks, result.payloadSha256).getOrThrow()
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `2 - payload exactly equal to chunk size produces single exact chunk with no trailing empty chunk`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 8)
        val payload = sequentialBytes(8)

        val result = chunker.chunk(payload)

        assertEquals(1, result.totalChunkCount)
        val chunk = result.chunks.single()
        assertEquals(0, chunk.index)
        assertEquals(1, chunk.totalCount)
        assertEquals(8, chunk.bytes.size)
        assertArrayEquals(payload, chunk.bytes)
        assertTrue(result.chunks.none { it.bytes.isEmpty() })

        val reassembled = chunker.reassemble(result.chunks, result.payloadSha256).getOrThrow()
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `3 - payload exactly divisible by chunk size produces exact chunks without trailing empty chunk`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(15)

        val result = chunker.chunk(payload)

        assertEquals(3, result.totalChunkCount)
        assertEquals(3, result.chunks.size)
        result.chunks.forEachIndexed { expectedIndex, chunk ->
            assertEquals(expectedIndex, chunk.index)
            assertEquals(3, chunk.totalCount)
            assertEquals(5, chunk.bytes.size)
        }
        assertTrue(result.chunks.none { it.bytes.isEmpty() })

        val reassembled = chunker.reassemble(result.chunks, result.payloadSha256).getOrThrow()
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `4 - payload requiring final partial chunk splits remainder accurately`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 6)
        val payload = sequentialBytes(20) // 6 + 6 + 6 + 2

        val result = chunker.chunk(payload)

        assertEquals(4, result.totalChunkCount)
        assertEquals(listOf(6, 6, 6, 2), result.chunks.map { it.bytes.size })
        assertArrayEquals(payload.copyOfRange(0, 6), result.chunks[0].bytes)
        assertArrayEquals(payload.copyOfRange(6, 12), result.chunks[1].bytes)
        assertArrayEquals(payload.copyOfRange(12, 18), result.chunks[2].bytes)
        assertArrayEquals(payload.copyOfRange(18, 20), result.chunks[3].bytes)

        val reassembled = chunker.reassemble(result.chunks, result.payloadSha256).getOrThrow()
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `5 - multiple chunks preserve exact byte order including arbitrary binary values`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 4)
        val payload = byteArrayOf(
            0x00, 0x7F, -0x80, -0x01,
            0x10, 0x20, 0x30, 0x40,
            -0x55, 0x55, 0x0A, 0x0D,
            0x1F, -0x75
        )

        val chunked = chunker.chunk(payload)
        val concatenated = chunked.chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk.bytes }
        assertArrayEquals(payload, concatenated)

        val reassembled = chunker.reassemble(chunked.chunks, chunked.payloadSha256).getOrThrow()
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `6 - every chunk respects configured maximum chunk size`() {
        for (limit in listOf(1, 2, 3, 7, 13)) {
            val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = limit)
            val payload = sequentialBytes(29)

            val chunked = chunker.chunk(payload)

            assertTrue(chunked.chunks.isNotEmpty())
            assertTrue(chunked.chunks.all { it.bytes.size in 1..limit })
            assertEquals(payload.size, chunked.chunks.sumOf { it.bytes.size })
        }
    }

    @Test
    fun `7 - chunk and payload SHA-256 values are deterministic 64-char lowercase hex`() {
        val sha256Pattern = Regex("^[0-9a-f]{64}$")
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 3)
        // Known NIST SHA-256 test vector for "abc":
        // ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val payload = "abcdef".toByteArray(Charsets.UTF_8)

        val firstRun = chunker.chunk(payload)
        val secondRun = chunker.chunk(payload)

        assertEquals(firstRun, secondRun)
        assertTrue(sha256Pattern.matches(firstRun.payloadSha256))
        assertEquals(referenceSha256Hex(payload), firstRun.payloadSha256)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            firstRun.chunks[0].sha256
        )
        firstRun.chunks.forEach { chunk ->
            assertTrue(sha256Pattern.matches(chunk.sha256))
            assertEquals(referenceSha256Hex(chunk.bytes), chunk.sha256)
        }
        assertNotEquals(firstRun.chunks[0].sha256, firstRun.chunks[1].sha256)
    }

    @Test
    fun `8 - reassembly succeeds when chunks arrive in shuffled order`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 4)
        val payload = sequentialBytes(23)
        val chunked = chunker.chunk(payload)
        val shuffledChunks = listOf(
            chunked.chunks[4],
            chunked.chunks[1],
            chunked.chunks[5],
            chunked.chunks[0],
            chunked.chunks[3],
            chunked.chunks[2]
        )

        val reassembled = chunker.reassemble(shuffledChunks, chunked.payloadSha256).getOrThrow()

        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `9 - empty payload is rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 16)

        assertThrows(IllegalArgumentException::class.java) {
            chunker.chunk(ByteArray(0))
        }
    }

    @Test
    fun `10 - invalid chunk sizes are rejected`() {
        for (invalidSize in listOf(0, -1, -512, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                CloudBackupPayloadChunker(maxChunkSizeBytes = invalidSize)
            }
        }
    }

    @Test
    fun `11 - missing chunk and empty chunk collection are rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(15)
        val chunked = chunker.chunk(payload)

        val emptyFailure = chunker.reassemble(emptyList(), chunked.payloadSha256)
        assertTrue(emptyFailure.isFailure)

        val missingMiddle = listOf(chunked.chunks[0], chunked.chunks[2])
        val missingFailure = chunker.reassemble(missingMiddle, chunked.payloadSha256)
        assertTrue(missingFailure.isFailure)
        assertTrue(missingFailure.exceptionOrNull() is IllegalArgumentException)

        // Maliciously huge totalCount with small chunk collection must fail cleanly without OOM
        val hugeTotalCountChunks = listOf(
            chunked.chunks[0].copy(totalCount = Int.MAX_VALUE),
            chunked.chunks[1].copy(totalCount = Int.MAX_VALUE)
        )
        val hugeFailure = chunker.reassemble(hugeTotalCountChunks, chunked.payloadSha256)
        assertTrue(hugeFailure.isFailure)
    }

    @Test
    fun `12 - duplicate chunk index is rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(15)
        val chunked = chunker.chunk(payload)
        val duplicated = listOf(
            chunked.chunks[0],
            chunked.chunks[1],
            chunked.chunks[1]
        )

        val result = chunker.reassemble(duplicated, chunked.payloadSha256)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Duplicate", ignoreCase = true) == true)
    }

    @Test
    fun `13 - negative and out-of-range chunk indices are rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(10)
        val chunked = chunker.chunk(payload)

        val negativeIndex = listOf(
            chunked.chunks[0].copy(index = -1),
            chunked.chunks[1]
        )
        val negativeResult = chunker.reassemble(negativeIndex, chunked.payloadSha256)
        assertTrue(negativeResult.isFailure)
        assertTrue(negativeResult.exceptionOrNull() is IllegalArgumentException)

        val outOfRangeIndex = listOf(
            chunked.chunks[0],
            chunked.chunks[1].copy(index = 2)
        )
        val outOfRangeResult = chunker.reassemble(outOfRangeIndex, chunked.payloadSha256)
        assertTrue(outOfRangeResult.isFailure)
        assertTrue(outOfRangeResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `14 - inconsistent or non-positive total chunk counts are rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(10)
        val chunked = chunker.chunk(payload)

        val inconsistentTotals = listOf(
            chunked.chunks[0],
            chunked.chunks[1].copy(totalCount = 3)
        )
        val inconsistentResult = chunker.reassemble(inconsistentTotals, chunked.payloadSha256)
        assertTrue(inconsistentResult.isFailure)
        assertTrue(
            inconsistentResult.exceptionOrNull()?.message?.contains("total chunk count", ignoreCase = true) == true
        )

        val zeroTotals = listOf(chunked.chunks[0].copy(totalCount = 0))
        val zeroResult = chunker.reassemble(zeroTotals, chunked.payloadSha256)
        assertTrue(zeroResult.isFailure)
    }

    @Test
    fun `15 - corrupted chunk bytes or mismatched chunk checksum are rejected`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(12)
        val chunked = chunker.chunk(payload)

        // Mutate chunk bytes while keeping original chunk sha256
        val tamperedBytes = chunked.chunks[1].bytes.copyOf().apply {
            this[0] = (this[0].toInt() xor 0xFF).toByte()
        }
        val corruptedBytesChunks = listOf(
            chunked.chunks[0],
            chunked.chunks[1].copy(bytes = tamperedBytes),
            chunked.chunks[2]
        )
        val bytesFailure = chunker.reassemble(corruptedBytesChunks, chunked.payloadSha256)
        assertTrue(bytesFailure.isFailure)
        assertTrue(bytesFailure.exceptionOrNull()?.message?.contains("checksum", ignoreCase = true) == true)

        // Malformed uppercase chunk sha256
        val uppercaseShaChunks = listOf(
            chunked.chunks[0].copy(sha256 = chunked.chunks[0].sha256.uppercase()),
            chunked.chunks[1],
            chunked.chunks[2]
        )
        assertTrue(chunker.reassemble(uppercaseShaChunks, chunked.payloadSha256).isFailure)

        // Empty chunk bytes with matching SHA-256 for empty array
        val emptyChunkBytes = ByteArray(0)
        val emptyBytesChunks = listOf(
            chunked.chunks[0],
            chunked.chunks[1].copy(bytes = emptyChunkBytes, sha256 = referenceSha256Hex(emptyChunkBytes)),
            chunked.chunks[2]
        )
        assertTrue(chunker.reassemble(emptyBytesChunks, chunked.payloadSha256).isFailure)
    }

    @Test
    fun `16 - incorrect overall payload checksum is rejected even when per-chunk checksums match`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 5)
        val payload = sequentialBytes(12)
        val chunked = chunker.chunk(payload)

        val wrongValidSha256 = "0".repeat(64)
        val mismatchResult = chunker.reassemble(chunked.chunks, wrongValidSha256)
        assertTrue(mismatchResult.isFailure)
        assertTrue(mismatchResult.exceptionOrNull()?.message?.contains("checksum", ignoreCase = true) == true)

        // Replace chunk 1 with valid bytes + matching chunk checksum so per-chunk checks pass,
        // while overall payload checksum still corresponds to original payload.
        val alteredBytes = byteArrayOf(9, 9, 9, 9, 9)
        val alteredChunks = listOf(
            chunked.chunks[0],
            chunked.chunks[1].copy(bytes = alteredBytes, sha256 = referenceSha256Hex(alteredBytes)),
            chunked.chunks[2]
        )
        val alteredPayloadFailure = chunker.reassemble(alteredChunks, chunked.payloadSha256)
        assertTrue(alteredPayloadFailure.isFailure)

        // Malformed overall SHA-256 (uppercase / wrong length)
        assertTrue(chunker.reassemble(chunked.chunks, chunked.payloadSha256.uppercase()).isFailure)
        assertTrue(chunker.reassemble(chunked.chunks, "not-a-sha256").isFailure)
    }

    @Test
    fun `17 - caller-owned input arrays and collections are not mutated`() {
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 4)
        val callerPayload = sequentialBytes(11)
        val payloadSnapshot = callerPayload.copyOf()

        val chunked = chunker.chunk(callerPayload)
        assertArrayEquals(payloadSnapshot, callerPayload)

        // Mutating callerPayload after chunking must not alter produced chunks
        callerPayload.fill(0)
        assertArrayEquals(payloadSnapshot.copyOfRange(0, 4), chunked.chunks[0].bytes)

        val mutableChunkList = mutableListOf(
            chunked.chunks[2],
            chunked.chunks[0],
            chunked.chunks[1]
        )
        val listSnapshot = mutableChunkList.toList()
        val chunkBytesSnapshots = mutableChunkList.map { it.bytes.copyOf() }

        val reassembled = chunker.reassemble(mutableChunkList, chunked.payloadSha256).getOrThrow()

        // Collection order/contents and chunk byte arrays remain untouched
        assertEquals(listSnapshot, mutableChunkList)
        mutableChunkList.forEachIndexed { index, chunk ->
            assertArrayEquals(chunkBytesSnapshots[index], chunk.bytes)
        }
        assertArrayEquals(payloadSnapshot, reassembled)

        // Mutating reassembled output does not mutate source chunk byte arrays
        reassembled.fill(-1)
        mutableChunkList.forEachIndexed { index, chunk ->
            assertArrayEquals(chunkBytesSnapshots[index], chunk.bytes)
        }
    }
}
