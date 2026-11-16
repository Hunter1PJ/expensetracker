package com.suguru.expensetracker.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class CloudBackupPayloadCodecTest {

    private val codec = CloudBackupPayloadCodec()

    private fun deterministicBytes(length: Int): ByteArray =
        ByteArray(length) { index -> ((index * 37 + 13) and 0xFF).toByte() }

    private fun standardGzipCompress(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun standardGzipDecompress(bytes: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }

    @Test
    fun `default maximum uncompressed payload size is 16 MiB`() {
        assertEquals(16 * 1024 * 1024, CloudBackupPayloadCodec.DEFAULT_MAX_UNCOMPRESSED_BYTES)
        assertEquals(16 * 1024 * 1024, codec.maxUncompressedBytes)
    }

    @Test
    fun `invalid maximum uncompressed payload sizes are rejected`() {
        for (invalidSize in listOf(0, -1, -1024, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                CloudBackupPayloadCodec(maxUncompressedBytes = invalidSize)
            }
        }
        // Int.MAX_VALUE is valid without overflow
        assertEquals(Int.MAX_VALUE, CloudBackupPayloadCodec(maxUncompressedBytes = Int.MAX_VALUE).maxUncompressedBytes)
    }

    @Test
    fun `1 - small byte array round-trip`() {
        val original = byteArrayOf(0x00, 0x01, 0x7F, -0x80, -0x01, 0x42, 0x0A)

        val compressed = codec.compress(original).getOrThrow()
        assertTrue(compressed.size >= 18) // GZIP 10-byte header + deflate + 8-byte trailer
        val decompressed = codec.decompress(compressed).getOrThrow()

        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `2 - empty byte array round-trip`() {
        val empty = ByteArray(0)

        val compressed = codec.compress(empty).getOrThrow()
        assertTrue(compressed.isNotEmpty())
        val decompressed = codec.decompress(compressed).getOrThrow()

        assertEquals(0, decompressed.size)
        assertArrayEquals(empty, decompressed)
    }

    @Test
    fun `3 - large byte array round-trip`() {
        // ~2 MiB realistic JSON-like repetitive + varied payload
        val largeJson = buildString(2 * 1024 * 1024) {
            append("{\"transactions\":[")
            repeat(12_000) { index ->
                if (index > 0) append(',')
                append("{\"id\":")
                append(index + 1)
                append(",\"type\":\"EXPENSE\",\"amountInMinorUnits\":")
                append(1_000L + index * 17L)
                append(",\"currencyCode\":\"USD\",\"note\":\"Merchant #")
                append(index)
                append("\"}")
            }
            append("]}")
        }
        val original = largeJson.toByteArray(Charsets.UTF_8)
        assertTrue(original.size > 1_000_000)

        val compressed = codec.compress(original).getOrThrow()
        assertTrue(compressed.size < original.size / 3)
        val decompressed = codec.decompress(compressed).getOrThrow()

        assertArrayEquals(original, decompressed)
        assertEquals(largeJson, decompressed.toString(Charsets.UTF_8))
    }

    @Test
    fun `4 - UTF-8 JSON text round-trip with Uzbek Russian and Unicode characters`() {
        val json = """
            {
              "formatVersion": 2,
              "accounts": [
                {"id": 1, "name": "O‘zbekiston soʻmi 🇺🇿", "currency": "UZS"},
                {"id": 2, "name": "Основной счёт ₽", "currency": "RUB"}
              ],
              "transactions": [
                {"id": 10, "note": "G‘allaorol bozori — non, choy va mevalar 🍞🍵"},
                {"id": 11, "note": "Продукты в супермаркете и кофе ☕"},
                {"id": 12, "note": "日本語食費 • العربية مصروف • Café €12.50 £9.99 ¥500"}
              ]
            }
        """.trimIndent()
        val originalBytes = json.toByteArray(Charsets.UTF_8)

        val compressed = codec.compress(originalBytes).getOrThrow()
        val decompressedBytes = codec.decompress(compressed).getOrThrow()

        assertArrayEquals(originalBytes, decompressedBytes)
        assertEquals(json, decompressedBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `5 - compressed output is interoperable with standard GZIP implementation`() {
        val original = "ExpenseTracker-GZIP-Interop-Test-1234567890".repeat(25).toByteArray(Charsets.UTF_8)

        // Codec compress -> standard GZIPInputStream decompress
        val codecCompressed = codec.compress(original).getOrThrow()
        val standardDecompressed = standardGzipDecompress(codecCompressed)
        assertArrayEquals(original, standardDecompressed)

        // Standard GZIPOutputStream compress -> codec decompress
        val standardCompressed = standardGzipCompress(original)
        val codecDecompressed = codec.decompress(standardCompressed).getOrThrow()
        assertArrayEquals(original, codecDecompressed)
    }

    @Test
    fun `6 - empty and invalid non-GZIP input are rejected`() {
        val emptyFailure = codec.decompress(ByteArray(0))
        assertTrue(emptyFailure.isFailure)
        assertTrue(emptyFailure.exceptionOrNull() is IllegalArgumentException)

        val invalidInputs = listOf(
            "{\"plain\":\"json\"}".toByteArray(Charsets.UTF_8),
            byteArrayOf(0x1F), // incomplete magic
            byteArrayOf(0x1F, 0x8A.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), // wrong magic
            byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP signature, not GZIP
            deterministicBytes(64)
        )

        for (invalid in invalidInputs) {
            val result = codec.decompress(invalid)
            assertTrue("Expected failure for non-GZIP input of size ${invalid.size}", result.isFailure)
        }
    }

    @Test
    fun `7 - truncated GZIP input is rejected`() {
        val original = deterministicBytes(512)
        val compressed = codec.compress(original).getOrThrow()
        assertTrue(compressed.size > 20)

        // Every prefix shorter than the full compressed GZIP stream must fail
        for (truncatedLength in 1 until compressed.size) {
            val truncated = compressed.copyOf(truncatedLength)
            val result = codec.decompress(truncated)
            assertTrue(
                "Expected failure for truncated GZIP length $truncatedLength/${compressed.size}",
                result.isFailure
            )
        }
    }

    @Test
    fun `8 - corrupted GZIP input is rejected`() {
        val original = "Account=Wallet,Amount=12500,Currency=USD;".repeat(20).toByteArray(Charsets.UTF_8)
        val compressed = codec.compress(original).getOrThrow()

        // Flip bytes across header, deflate payload, and 8-byte CRC32/ISIZE trailer
        val positionsToCorrupt = listOf(
            0, // ID1 magic
            1, // ID2 magic
            2, // CM compression method
            10, // start of deflate stream
            compressed.size / 2, // middle of deflate stream
            compressed.size - 8, // CRC32 byte
            compressed.size - 5, // CRC32 byte
            compressed.size - 4, // ISIZE byte
            compressed.size - 1 // ISIZE last byte
        ).distinct()

        for (pos in positionsToCorrupt) {
            val corrupted = compressed.copyOf()
            corrupted[pos] = (corrupted[pos].toInt() xor 0xFF).toByte()
            val result = codec.decompress(corrupted)
            assertTrue("Expected failure when byte at index $pos is corrupted", result.isFailure)
        }
    }

    @Test
    fun `9 - decompressed output exceeding configured maximum is rejected without full allocation`() {
        val smallLimit = 4 * 1024 // 4 KiB
        val boundedCodec = CloudBackupPayloadCodec(maxUncompressedBytes = smallLimit)

        // 1 MiB of zeros compresses to ~1 KiB (a small GZIP bomb)
        val hugeZeroPayload = ByteArray(1024 * 1024)
        val compressedBomb = standardGzipCompress(hugeZeroPayload)
        assertTrue(compressedBomb.size < smallLimit)

        val result = boundedCodec.decompress(compressedBomb)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("exceeds maximum", ignoreCase = true) == true)
    }

    @Test
    fun `10 - input arrays are not mutated during compression or decompression`() {
        val callerInput = deterministicBytes(256)
        val inputSnapshot = callerInput.copyOf()

        val compressed = codec.compress(callerInput).getOrThrow()
        assertArrayEquals(inputSnapshot, callerInput)

        // Mutating callerInput after compress must not affect compressed bytes
        callerInput.fill(0)
        assertArrayEquals(inputSnapshot, codec.decompress(compressed).getOrThrow())

        val compressedSnapshot = compressed.copyOf()
        val decompressed = codec.decompress(compressed).getOrThrow()
        assertArrayEquals(compressedSnapshot, compressed)
        assertArrayEquals(inputSnapshot, decompressed)

        // Mutating decompressed output must not affect source compressed array
        decompressed.fill(-1)
        assertArrayEquals(compressedSnapshot, compressed)
    }

    @Test
    fun `11 - repeated compression and decompression remain deterministic and correct`() {
        val original = "{\"accounts\":[{\"id\":1,\"name\":\"Main\"}],\"amount\":4200}".toByteArray(Charsets.UTF_8)

        val compressedRuns = (1..5).map { codec.compress(original).getOrThrow() }
        for (index in 1 until compressedRuns.size) {
            assertArrayEquals(compressedRuns[0], compressedRuns[index])
        }

        val decompressedRuns = compressedRuns.map { codec.decompress(it).getOrThrow() }
        for (decompressed in decompressedRuns) {
            assertArrayEquals(original, decompressed)
        }

        // Distinct payload produces distinct compressed output
        val altered = "{\"accounts\":[{\"id\":1,\"name\":\"Cash\"}],\"amount\":4200}".toByteArray(Charsets.UTF_8)
        assertNotEquals(compressedRuns[0].toList(), codec.compress(altered).getOrThrow().toList())
    }

    @Test
    fun `12 - boundary behavior at exact configured maximum size`() {
        // Test both sub-buffer (64 bytes), exact 1 byte, and multi-buffer (> 8 KiB internal buffer) boundaries
        val boundaries = listOf(
            1,
            64,
            8 * 1024,
            8 * 1024 * 2 + 13
        )

        for (limit in boundaries) {
            val boundedCodec = CloudBackupPayloadCodec(maxUncompressedBytes = limit)

            val exactPayload = deterministicBytes(limit)
            val compressedExact = standardGzipCompress(exactPayload)
            val exactResult = boundedCodec.decompress(compressedExact)
            assertTrue("Expected exact boundary ($limit bytes) to succeed", exactResult.isSuccess)
            assertArrayEquals(exactPayload, exactResult.getOrThrow())

            val overLimitPayload = deterministicBytes(limit + 1)
            val compressedOver = standardGzipCompress(overLimitPayload)
            val overResult = boundedCodec.decompress(compressedOver)
            assertTrue("Expected limit + 1 (${limit + 1} bytes) to fail", overResult.isFailure)
            assertTrue(overResult.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `13 - integrates cleanly with CloudBackupPayloadChunker round-trip`() {
        val utf8Json = "{\"backup\":\"ExpenseTracker\",\"note\":\"O‘zbekcha • Русский\",\"items\":[1,2,3]}"
            .repeat(100)
            .toByteArray(Charsets.UTF_8)
        val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 256)

        val compressed = codec.compress(utf8Json).getOrThrow()
        val chunked = chunker.chunk(compressed)
        val reassembled = chunker.reassemble(chunked.chunks.reversed(), chunked.payloadSha256).getOrThrow()
        val restoredBytes = codec.decompress(reassembled).getOrThrow()

        assertArrayEquals(utf8Json, restoredBytes)
    }
}
