package com.suguru.expensetracker.domain.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pure, Firebase-independent domain codec that GZIP-compresses and safely decompresses
 * already-serialized cloud backup payload bytes (for example, UTF-8 JSON bytes produced by
 * `BackupRepository.serializeBackup`).
 *
 * This codec operates strictly on raw `ByteArray` payloads and does not serialize or deserialize
 * [ExpenseTrackerBackup] domain models directly.
 *
 * @property maxUncompressedBytes Maximum allowed uncompressed payload size in bytes during
 * decompression, enforced incrementally while streaming to protect against decompression bombs
 * and unbounded memory allocation. Defaults to [DEFAULT_MAX_UNCOMPRESSED_BYTES] (16 MiB).
 */
class CloudBackupPayloadCodec(
    val maxUncompressedBytes: Int = DEFAULT_MAX_UNCOMPRESSED_BYTES
) {
    init {
        require(maxUncompressedBytes > 0) {
            "Maximum uncompressed payload size must be strictly positive, was $maxUncompressedBytes."
        }
    }

    /**
     * Compresses serialized backup [uncompressedBytes] using standard, deterministic GZIP.
     *
     * Accepts empty `ByteArray` inputs (producing a valid GZIP stream representing zero bytes)
     * and never mutates [uncompressedBytes].
     */
    fun compress(uncompressedBytes: ByteArray): Result<ByteArray> {
        return try {
            val initialCapacity = minOf(
                maxOf(uncompressedBytes.size.toLong() / 4L, DEFAULT_MIN_BUFFER_CAPACITY.toLong()),
                MAX_INITIAL_OUTPUT_CAPACITY.toLong()
            ).toInt()
            val outputStream = ByteArrayOutputStream(initialCapacity)
            GZIPOutputStream(outputStream, STREAM_BUFFER_SIZE_BYTES).use { gzipOut ->
                gzipOut.write(uncompressedBytes)
            }
            Result.success(outputStream.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decompresses GZIP-encoded [compressedBytes] back into the original serialized backup bytes.
     *
     * Rejects empty input, malformed/truncated/corrupted GZIP streams, and any payload whose
     * decompressed size exceeds [maxUncompressedBytes] (enforced while streaming before
     * buffering beyond the configured limit). Never mutates [compressedBytes] and never returns
     * partially decompressed data on failure.
     */
    fun decompress(compressedBytes: ByteArray): Result<ByteArray> {
        if (compressedBytes.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Compressed backup payload must not be empty.")
            )
        }

        return try {
            val initialCapacity = minOf(
                maxOf(compressedBytes.size.toLong() * 2L, DEFAULT_MIN_BUFFER_CAPACITY.toLong()),
                maxUncompressedBytes.toLong(),
                MAX_INITIAL_OUTPUT_CAPACITY.toLong()
            ).toInt()
            val outputStream = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(STREAM_BUFFER_SIZE_BYTES)
            var totalUncompressedBytes = 0L

            GZIPInputStream(ByteArrayInputStream(compressedBytes), STREAM_BUFFER_SIZE_BYTES).use { gzipIn ->
                while (true) {
                    val remainingAllowed = maxUncompressedBytes.toLong() - totalUncompressedBytes
                    val maxReadLength = minOf(buffer.size.toLong(), remainingAllowed + 1L).toInt()
                    val bytesRead = gzipIn.read(buffer, 0, maxReadLength)
                    if (bytesRead == -1) {
                        break
                    }
                    if (bytesRead == 0) {
                        continue
                    }

                    val bytesReadLong = bytesRead.toLong()
                    if (totalUncompressedBytes > Long.MAX_VALUE - bytesReadLong) {
                        return Result.failure(
                            IllegalArgumentException("Decompressed backup payload size overflow.")
                        )
                    }
                    val nextTotal = totalUncompressedBytes + bytesReadLong
                    if (nextTotal > Int.MAX_VALUE.toLong() || nextTotal > maxUncompressedBytes.toLong()) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Decompressed backup payload exceeds maximum allowed size of $maxUncompressedBytes bytes."
                            )
                        )
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalUncompressedBytes = nextTotal
                }
            }

            Result.success(outputStream.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Conservative 16 MiB upper bound for decompressed cloud backup JSON payloads.
         *
         * Accommodates tens of thousands of personal expense records (~300–350 bytes of JSON per
         * transaction ≈ 45,000+ transactions, typically compressing to ~1.5–3.5 MiB across 3–7
         * chunks of 512 KiB) while keeping peak in-memory restore allocation (compressed bytes +
         * decompressed bytes + UTF-8 JSON string + Moshi object graph) safe on `minSdk = 24`
         * devices and bounding malicious GZIP bomb amplification.
         */
        const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024

        private const val STREAM_BUFFER_SIZE_BYTES = 8 * 1024
        private const val DEFAULT_MIN_BUFFER_CAPACITY = 64
        private const val MAX_INITIAL_OUTPUT_CAPACITY = 1024 * 1024
    }
}
