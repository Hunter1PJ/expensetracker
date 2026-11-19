package com.suguru.expensetracker.domain.model

/**
 * Immutable local domain state describing UID-scoped cloud backup progress, last completed
 * snapshot metadata, pending retry state, and the most recent attempt/failure metadata.
 *
 * Intentionally excludes financial amounts, transaction notes, category/account details,
 * raw UIDs, device identifiers, API keys, raw exception messages, and content fingerprints
 * (which are persisted separately by `CloudBackupFingerprintStore`).
 */
data class CloudBackupState(
    val lastSuccessfulBackupAtEpochMillis: Long? = null,
    val lastSuccessfulRestoreAtEpochMillis: Long? = null,
    val lastSuccessfulSnapshotId: String? = null,
    val lastSuccessfulRestoreGenerationId: String? = null,
    val lastSuccessfulRestoreFingerprint: String? = null,
    val pendingRestore: Boolean = false,
    val pendingSnapshotId: String? = null,
    val pendingSinceEpochMillis: Long? = null,
    val lastAttemptAtEpochMillis: Long? = null,
    val lastFailureAtEpochMillis: Long? = null,
    val lastFailureCode: String? = null,
    val lastRestoreFailureAtEpochMillis: Long? = null,
    val lastRestoreFailureCode: String? = null,
    val lastRestoreFailureGenerationId: String? = null,
    val lastRestoreFailureFingerprint: String? = null
) {
    init {
        if (lastSuccessfulBackupAtEpochMillis != null) {
            require(lastSuccessfulBackupAtEpochMillis >= 0L) {
                "lastSuccessfulBackupAtEpochMillis must be non-negative, was $lastSuccessfulBackupAtEpochMillis."
            }
        }
        if (lastSuccessfulRestoreAtEpochMillis != null) {
            require(lastSuccessfulRestoreAtEpochMillis >= 0L) {
                "lastSuccessfulRestoreAtEpochMillis must be non-negative, was $lastSuccessfulRestoreAtEpochMillis."
            }
        }
        if (lastSuccessfulSnapshotId != null) {
            require(isValidSnapshotId(lastSuccessfulSnapshotId)) {
                "lastSuccessfulSnapshotId is invalid."
            }
        }
        if (lastSuccessfulRestoreGenerationId != null) {
            require(isValidSnapshotId(lastSuccessfulRestoreGenerationId)) {
                "lastSuccessfulRestoreGenerationId is invalid."
            }
        }
        if (lastSuccessfulRestoreFingerprint != null) {
            require(Regex("^[0-9a-f]{64}$").matches(lastSuccessfulRestoreFingerprint)) {
                "lastSuccessfulRestoreFingerprint must be a SHA-256 fingerprint."
            }
        }
        if (pendingSnapshotId != null) {
            require(isValidSnapshotId(pendingSnapshotId)) {
                "pendingSnapshotId is invalid."
            }
        }
        if (pendingSinceEpochMillis != null) {
            require(pendingSinceEpochMillis >= 0L) {
                "pendingSinceEpochMillis must be non-negative, was $pendingSinceEpochMillis."
            }
        }
        require((pendingSnapshotId == null) == (pendingSinceEpochMillis == null)) {
            "pendingSnapshotId and pendingSinceEpochMillis must either both be set or both be null."
        }
        if (lastAttemptAtEpochMillis != null) {
            require(lastAttemptAtEpochMillis >= 0L) {
                "lastAttemptAtEpochMillis must be non-negative, was $lastAttemptAtEpochMillis."
            }
        }
        if (lastFailureAtEpochMillis != null) {
            require(lastFailureAtEpochMillis >= 0L) {
                "lastFailureAtEpochMillis must be non-negative, was $lastFailureAtEpochMillis."
            }
        }
        if (lastFailureCode != null) {
            require(isValidFailureCode(lastFailureCode)) {
                "lastFailureCode must be a bounded machine-readable identifier."
            }
        }
        require((lastFailureAtEpochMillis == null) == (lastFailureCode == null)) {
            "lastFailureAtEpochMillis and lastFailureCode must either both be set or both be null."
        }
        if (lastRestoreFailureAtEpochMillis != null) {
            require(lastRestoreFailureAtEpochMillis >= 0L) {
                "lastRestoreFailureAtEpochMillis must be non-negative."
            }
        }
        if (lastRestoreFailureCode != null) {
            require(isValidFailureCode(lastRestoreFailureCode)) {
                "lastRestoreFailureCode must be a bounded machine-readable identifier."
            }
        }
        require((lastRestoreFailureAtEpochMillis == null) == (lastRestoreFailureCode == null)) {
            "Restore failure timestamp and code must either both be set or both be null."
        }
        if (lastRestoreFailureGenerationId != null) {
            require(isValidSnapshotId(lastRestoreFailureGenerationId)) {
                "lastRestoreFailureGenerationId is invalid."
            }
        }
        if (lastRestoreFailureFingerprint != null) {
            require(Regex("^[0-9a-f]{64}$").matches(lastRestoreFailureFingerprint)) {
                "lastRestoreFailureFingerprint must be a SHA-256 fingerprint."
            }
        }
    }

    val isEmpty: Boolean
        get() = this == EMPTY

    companion object {
        val EMPTY = CloudBackupState()

        const val MAX_SNAPSHOT_ID_LENGTH = 128
        const val MAX_FAILURE_CODE_LENGTH = 64

        private val FAILURE_CODE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_-]{0,63}$")

        /**
         * Validates snapshot IDs conservatively in alignment with [CloudBackupManifest]:
         * non-blank, no leading/trailing/internal whitespace or control characters,
         * no path separators (`/`), not `.` or `..`, and bounded to [MAX_SNAPSHOT_ID_LENGTH].
         */
        fun isValidSnapshotId(snapshotId: String): Boolean {
            if (snapshotId.isBlank()) return false
            if (snapshotId != snapshotId.trim()) return false
            if (snapshotId.length > MAX_SNAPSHOT_ID_LENGTH) return false
            if (snapshotId == "." || snapshotId == "..") return false
            return snapshotId.none { ch -> ch.isWhitespace() || ch.isISOControl() || ch == '/' }
        }

        /**
         * Validates failure codes as bounded machine-readable identifiers
         * (`^[A-Za-z][A-Za-z0-9_-]{0,63}$`), rejecting prose, URLs, stack traces,
         * whitespace, and unbounded strings.
         */
        fun isValidFailureCode(failureCode: String): Boolean {
            return failureCode.length <= MAX_FAILURE_CODE_LENGTH &&
                FAILURE_CODE_REGEX.matches(failureCode)
        }
    }
}
