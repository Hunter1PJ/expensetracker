package com.suguru.expensetracker.domain.model

/** Cloud restore status derived from the current cloud generation and this device's persisted restore state. */
enum class CloudRestoreStatus(val label: String) {
    CHECKING("Checking"),
    UP_TO_DATE("Up to date"),
    NEW_BACKUP_AVAILABLE("New backup available"),
    RESTORING("Restoring"),
    WAITING_FOR_INTERNET("Waiting for internet"),
    RESTORE_FAILED("Restore failed"),
    NO_CLOUD_BACKUP("No cloud backup")
}

data class CloudRestoreStatusInput(
    val activeManifest: CloudBackupManifest?,
    val isLoading: Boolean = false,
    val restoreInProgress: Boolean = false,
    val pendingRestore: Boolean = false,
    val isOnline: Boolean = true,
    val transientFailure: Boolean = false,
    val lastFailureCode: String? = null,
    val lastFailureGenerationId: String? = null,
    val lastFailureFingerprint: String? = null,
    val restoredGenerationId: String? = null,
    val restoredFingerprint: String? = null
)

object CloudRestoreStatusResolver {
    private val TRANSIENT_FAILURE_CODES = setOf(
        "NETWORK_UNAVAILABLE",
        "DEADLINE_EXCEEDED",
        "ABORTED",
        "FIRESTORE_ERROR",
        "UNAUTHENTICATED"
    )

    fun resolve(input: CloudRestoreStatusInput): CloudRestoreStatus {
        if (input.restoreInProgress) return CloudRestoreStatus.RESTORING
        if (input.pendingRestore && !input.isOnline) return CloudRestoreStatus.WAITING_FOR_INTERNET
        if (input.transientFailure || hasRelevantFailure(input)) return CloudRestoreStatus.RESTORE_FAILED

        val manifest = input.activeManifest
        if (manifest == null) {
            return if (input.isLoading) CloudRestoreStatus.CHECKING else CloudRestoreStatus.NO_CLOUD_BACKUP
        }
        return if (matchesRestoredGeneration(
                manifest,
                input.restoredGenerationId,
                input.restoredFingerprint
            )
        ) {
            CloudRestoreStatus.UP_TO_DATE
        } else {
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE
        }
    }

    private fun hasRelevantFailure(input: CloudRestoreStatusInput): Boolean {
        val failureCode = input.lastFailureCode ?: return false
        if (failureCode in TRANSIENT_FAILURE_CODES) return false
        val manifest = input.activeManifest ?: return false
        val hasFailureIdentity = input.lastFailureGenerationId != null ||
            input.lastFailureFingerprint != null
        if (!hasFailureIdentity) return true
        return (input.lastFailureGenerationId != null &&
            input.lastFailureGenerationId == manifest.generationId) ||
            (input.lastFailureFingerprint != null &&
                input.lastFailureFingerprint == manifest.contentFingerprint)
    }

    private fun matchesRestoredGeneration(
        manifest: CloudBackupManifest,
        restoredGenerationId: String?,
        restoredFingerprint: String?
    ): Boolean {
        if (manifest.generationId != null && restoredGenerationId != null) {
            return manifest.generationId == restoredGenerationId
        }
        return restoredFingerprint != null && manifest.contentFingerprint == restoredFingerprint
    }
}
