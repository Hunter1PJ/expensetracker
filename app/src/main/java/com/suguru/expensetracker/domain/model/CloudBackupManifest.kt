package com.suguru.expensetracker.domain.model

import java.time.Instant

/**
 * Explicit lifecycle states for a cloud backup snapshot manifest.
 *
 * A snapshot begins in [UPLOADING] while chunks are being uploaded and transitions to [COMPLETE]
 * only after all chunks have been uploaded and validated.
 */
enum class CloudBackupManifestStatus {
    UPLOADING,
    COMPLETE
}

/**
 * Pure domain metadata contract describing a single cloud backup snapshot.
 *
 * Contains structural, versioning, sizing, and integrity metadata only; excludes financial
 * amounts, transaction notes, category details, and Gemini BYOK secrets.
 */
data class CloudBackupManifest(
    val snapshotId: String,
    val uid: String,
    val status: CloudBackupManifestStatus,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val formatVersion: Int,
    val databaseSchemaVersion: Int,
    val appVersion: String,
    val chunkCount: Int,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val payloadSha256: String,
    val contentFingerprint: String
) {
    /**
     * Validates this manifest against all domain integrity rules without mutating or normalizing
     * any fields.
     */
    fun validate(): Result<CloudBackupManifest> = validate(this)

    companion object {
        private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

        /**
         * Validates [manifest] against all domain integrity rules and returns [Result.success]
         * with [manifest] if valid, or [Result.failure] with an [IllegalArgumentException]
         * describing the first violated rule.
         *
         * Does not mutate or normalize [manifest].
         */
        fun validate(manifest: CloudBackupManifest): Result<CloudBackupManifest> {
            if (manifest.snapshotId.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Snapshot ID must not be blank.")
                )
            }

            if (manifest.uid.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("UID must not be blank.")
                )
            }

            if (manifest.uid != manifest.uid.trim()) {
                return Result.failure(
                    IllegalArgumentException("UID must not contain leading or trailing whitespace.")
                )
            }

            when (manifest.status) {
                CloudBackupManifestStatus.UPLOADING -> {
                    if (manifest.completedAt != null) {
                        return Result.failure(
                            IllegalArgumentException(
                                "UPLOADING manifest must not have a completion timestamp."
                            )
                        )
                    }
                }
                CloudBackupManifestStatus.COMPLETE -> {
                    val completedAt = manifest.completedAt
                        ?: return Result.failure(
                            IllegalArgumentException(
                                "COMPLETE manifest must have a completion timestamp."
                            )
                        )
                    if (completedAt.isBefore(manifest.createdAt)) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Completion timestamp ($completedAt) must not be earlier than creation timestamp (${manifest.createdAt})."
                            )
                        )
                    }
                }
            }

            if (manifest.formatVersion <= 0) {
                return Result.failure(
                    IllegalArgumentException(
                        "Backup format version must be strictly positive, was ${manifest.formatVersion}."
                    )
                )
            }

            if (manifest.databaseSchemaVersion <= 0) {
                return Result.failure(
                    IllegalArgumentException(
                        "Database schema version must be strictly positive, was ${manifest.databaseSchemaVersion}."
                    )
                )
            }

            if (manifest.appVersion.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("App version must not be blank.")
                )
            }

            if (manifest.chunkCount <= 0) {
                return Result.failure(
                    IllegalArgumentException(
                        "Chunk count must be strictly positive, was ${manifest.chunkCount}."
                    )
                )
            }

            if (manifest.compressedBytes <= 0L) {
                return Result.failure(
                    IllegalArgumentException(
                        "Compressed byte count must be strictly positive, was ${manifest.compressedBytes}."
                    )
                )
            }

            if (manifest.uncompressedBytes <= 0L) {
                return Result.failure(
                    IllegalArgumentException(
                        "Uncompressed byte count must be strictly positive, was ${manifest.uncompressedBytes}."
                    )
                )
            }

            if (!SHA256_HEX_REGEX.matches(manifest.payloadSha256)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Payload SHA-256 checksum must be exactly 64 lowercase hexadecimal characters."
                    )
                )
            }

            if (!SHA256_HEX_REGEX.matches(manifest.contentFingerprint)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Content fingerprint must be exactly 64 lowercase hexadecimal characters."
                    )
                )
            }

            return Result.success(manifest)
        }
    }
}
