package com.suguru.expensetracker.domain.model

import java.security.MessageDigest

/**
 * A single bounded slice of a compressed cloud backup payload along with its integrity metadata.
 *
 * @property index Zero-based sequential position of this chunk within the chunked payload.
 * @property totalCount Total number of chunks that comprise the complete payload.
 * @property bytes Raw chunk bytes (at most the configured maximum chunk size, never empty).
 * @property sha256 Deterministic 64-character lowercase hexadecimal SHA-256 digest of [bytes].
 */
data class CloudBackupPayloadChunk(
    val index: Int,
    val totalCount: Int,
    val bytes: ByteArray,
    val sha256: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudBackupPayloadChunk) return false
        return index == other.index &&
            totalCount == other.totalCount &&
            bytes.contentEquals(other.bytes) &&
            sha256 == other.sha256
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + totalCount
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + sha256.hashCode()
        return result
    }

    override fun toString(): String {
        return "CloudBackupPayloadChunk(index=$index, totalCount=$totalCount, byteCount=${bytes.size}, sha256=$sha256)"
    }
}

/**
 * Result of splitting a compressed cloud backup payload into bounded, checksummed chunks.
 *
 * @property payloadSha256 Deterministic 64-character lowercase hexadecimal SHA-256 digest of the
 * complete original payload.
 * @property totalBytes Total byte length of the original payload.
 * @property maxChunkSizeBytes Maximum raw byte size configured for each chunk.
 * @property chunks Ordered list of sequential [CloudBackupPayloadChunk] items (`0 until totalChunkCount`).
 */
data class CloudBackupChunkedPayload(
    val payloadSha256: String,
    val totalBytes: Int,
    val maxChunkSizeBytes: Int,
    val chunks: List<CloudBackupPayloadChunk>
) {
    val totalChunkCount: Int
        get() = chunks.size
}

/**
 * Pure, Firebase-independent domain component that splits an already serialized and compressed
 * cloud backup `ByteArray` into bounded chunks with SHA-256 integrity metadata, and safely
 * reassembles and verifies chunks back into the original payload.
 */
class CloudBackupPayloadChunker(
    val maxChunkSizeBytes: Int = DEFAULT_MAX_CHUNK_SIZE_BYTES
) {
    init {
        require(maxChunkSizeBytes > 0) {
            "Maximum chunk size must be strictly positive, was $maxChunkSizeBytes."
        }
    }

    /**
     * Splits a non-empty [payload] into sequential [CloudBackupPayloadChunk]s of at most
     * [maxChunkSizeBytes] bytes each, preserving exact byte order and computing deterministic
     * lowercase SHA-256 checksums for every chunk and the overall payload.
     *
     * Does not mutate [payload].
     *
     * @throws IllegalArgumentException if [payload] is empty.
     */
    fun chunk(payload: ByteArray): CloudBackupChunkedPayload {
        require(payload.isNotEmpty()) {
            "Backup payload must not be empty."
        }

        val payloadSha256 = sha256Hex(payload)
        val fullChunkCount = payload.size / maxChunkSizeBytes
        val hasTrailingPartial = payload.size % maxChunkSizeBytes != 0
        val totalChunkCount = if (hasTrailingPartial) fullChunkCount + 1 else fullChunkCount

        val chunks = ArrayList<CloudBackupPayloadChunk>(totalChunkCount)
        for (chunkIndex in 0 until totalChunkCount) {
            val startOffset = chunkIndex * maxChunkSizeBytes
            val endOffset = minOf(
                startOffset.toLong() + maxChunkSizeBytes.toLong(),
                payload.size.toLong()
            ).toInt()
            val chunkBytes = payload.copyOfRange(startOffset, endOffset)
            chunks.add(
                CloudBackupPayloadChunk(
                    index = chunkIndex,
                    totalCount = totalChunkCount,
                    bytes = chunkBytes,
                    sha256 = sha256Hex(chunkBytes)
                )
            )
        }

        return CloudBackupChunkedPayload(
            payloadSha256 = payloadSha256,
            totalBytes = payload.size,
            maxChunkSizeBytes = maxChunkSizeBytes,
            chunks = chunks
        )
    }

    /**
     * Validates and reassembles [chunks] into the original compressed payload `ByteArray`, verifying:
     * - [chunks] is non-empty;
     * - [expectedPayloadSha256] is a valid 64-character lowercase hexadecimal SHA-256 string;
     * - all chunks agree on a strictly positive `totalCount`;
     * - indices are unique and cover the complete range `0 until totalCount`;
     * - every chunk's bytes are non-empty and match its `sha256` checksum before reassembly;
     * - the reconstructed payload's SHA-256 checksum matches [expectedPayloadSha256].
     *
     * Reassembles strictly by [CloudBackupPayloadChunk.index] regardless of [chunks] iteration
     * order, and never mutates caller-owned collections or byte arrays.
     */
    fun reassemble(
        chunks: Collection<CloudBackupPayloadChunk>,
        expectedPayloadSha256: String
    ): Result<ByteArray> {
        val snapshot = chunks.toList()
        if (snapshot.isEmpty()) {
            return Result.failure(IllegalArgumentException("Chunk collection must not be empty."))
        }

        if (!SHA256_HEX_REGEX.matches(expectedPayloadSha256)) {
            return Result.failure(
                IllegalArgumentException(
                    "Expected payload SHA-256 checksum must be 64 lowercase hexadecimal characters."
                )
            )
        }

        val expectedTotalCount = snapshot.first().totalCount
        if (expectedTotalCount <= 0) {
            return Result.failure(
                IllegalArgumentException(
                    "Total chunk count must be strictly positive, was $expectedTotalCount."
                )
            )
        }

        if (snapshot.any { it.totalCount != expectedTotalCount }) {
            return Result.failure(
                IllegalArgumentException("Inconsistent total chunk counts across supplied chunks.")
            )
        }

        val seenIndices = HashSet<Int>(snapshot.size)
        for (chunk in snapshot) {
            if (chunk.index < 0 || chunk.index >= expectedTotalCount) {
                return Result.failure(
                    IllegalArgumentException(
                        "Chunk index ${chunk.index} is out of valid range 0 until $expectedTotalCount."
                    )
                )
            }
            if (!seenIndices.add(chunk.index)) {
                return Result.failure(
                    IllegalArgumentException("Duplicate chunk index detected: ${chunk.index}.")
                )
            }
        }

        if (seenIndices.size != expectedTotalCount) {
            val firstMissingIndex = (0 until expectedTotalCount).first { it !in seenIndices }
            return Result.failure(
                IllegalArgumentException(
                    "Missing chunk(s): expected $expectedTotalCount chunks, received ${seenIndices.size} (first missing index: $firstMissingIndex)."
                )
            )
        }

        val orderedChunks = arrayOfNulls<CloudBackupPayloadChunk>(expectedTotalCount)
        for (chunk in snapshot) {
            if (chunk.bytes.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("Chunk ${chunk.index} must not have empty bytes.")
                )
            }
            if (!SHA256_HEX_REGEX.matches(chunk.sha256)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Chunk ${chunk.index} has a malformed SHA-256 checksum."
                    )
                )
            }
            val actualChunkSha256 = sha256Hex(chunk.bytes)
            if (actualChunkSha256 != chunk.sha256) {
                return Result.failure(
                    IllegalArgumentException(
                        "Chunk ${chunk.index} SHA-256 checksum verification failed."
                    )
                )
            }
            orderedChunks[chunk.index] = chunk
        }

        var totalBytesLong = 0L
        for (chunk in orderedChunks) {
            val verifiedChunk = chunk
                ?: return Result.failure(IllegalArgumentException("Missing chunk during assembly."))
            totalBytesLong += verifiedChunk.bytes.size.toLong()
            if (totalBytesLong > Int.MAX_VALUE.toLong()) {
                return Result.failure(
                    IllegalArgumentException("Reassembled payload exceeds maximum supported size.")
                )
            }
        }

        val reconstructed = ByteArray(totalBytesLong.toInt())
        var offset = 0
        for (chunk in orderedChunks) {
            val verifiedChunk = chunk!!
            System.arraycopy(verifiedChunk.bytes, 0, reconstructed, offset, verifiedChunk.bytes.size)
            offset += verifiedChunk.bytes.size
        }

        val actualPayloadSha256 = sha256Hex(reconstructed)
        if (actualPayloadSha256 != expectedPayloadSha256) {
            return Result.failure(
                IllegalArgumentException(
                    "Reassembled payload SHA-256 checksum verification failed."
                )
            )
        }

        return Result.success(reconstructed)
    }

    companion object {
        const val DEFAULT_MAX_CHUNK_SIZE_BYTES = 512 * 1024

        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
        private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hexChars = CharArray(digest.size * 2)
            for (index in digest.indices) {
                val value = digest[index].toInt() and 0xFF
                hexChars[index * 2] = HEX_DIGITS[value ushr 4]
                hexChars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
            }
            return String(hexChars)
        }
    }
}
