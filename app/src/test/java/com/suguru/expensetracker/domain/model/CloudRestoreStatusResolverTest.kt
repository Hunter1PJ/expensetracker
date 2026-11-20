package com.suguru.expensetracker.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudRestoreStatusResolverTest {
    private val fingerprintA = "a".repeat(64)
    private val fingerprintB = "b".repeat(64)

    private fun manifest(generation: String?, fingerprint: String = fingerprintA) = CloudBackupManifest(
        snapshotId = "primary_restore_point",
        uid = "uid_a",
        status = CloudBackupManifestStatus.COMPLETE,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        completedAt = Instant.parse("2026-01-01T00:01:00Z"),
        formatVersion = 1,
        databaseSchemaVersion = 1,
        appVersion = "1.0",
        chunkCount = 1,
        compressedBytes = 1,
        uncompressedBytes = 1,
        payloadSha256 = fingerprint,
        contentFingerprint = fingerprint,
        generationId = generation
    )

    @Test
    fun noCompletedManifestShowsNoCloudBackup() {
        assertEquals(
            CloudRestoreStatus.NO_CLOUD_BACKUP,
            CloudRestoreStatusResolver.resolve(CloudRestoreStatusInput(activeManifest = null))
        )
    }

    @Test
    fun loadingWithoutManifestShowsChecking() {
        assertEquals(
            CloudRestoreStatus.CHECKING,
            CloudRestoreStatusResolver.resolve(CloudRestoreStatusInput(activeManifest = null, isLoading = true))
        )
    }

    @Test
    fun firstSnapshotAndDifferentGenerationShowNewBackupAvailable() {
        assertEquals(
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(activeManifest = manifest("gen_new"))
            )
        )
        assertEquals(
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_new"),
                    restoredGenerationId = "gen_old",
                    restoredFingerprint = fingerprintA
                )
            )
        )
    }

    @Test
    fun matchingGenerationShowsUpToDateEvenWhenPrimaryIdIsStable() {
        assertEquals(
            CloudRestoreStatus.UP_TO_DATE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_same"),
                    restoredGenerationId = "gen_same"
                )
            )
        )
    }

    @Test
    fun legacyManifestFallsBackToContentFingerprint() {
        assertEquals(
            CloudRestoreStatus.UP_TO_DATE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest(null, fingerprintA),
                    restoredFingerprint = fingerprintA
                )
            )
        )
        assertEquals(
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest(null, fingerprintB),
                    restoredFingerprint = fingerprintA
                )
            )
        )
    }

    @Test
    fun activeAndWaitingStatesHavePriority() {
        val manifest = manifest("gen_waiting")
        assertEquals(
            CloudRestoreStatus.RESTORING,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest,
                    restoreInProgress = true,
                    pendingRestore = true,
                    isOnline = false
                )
            )
        )
        assertEquals(
            CloudRestoreStatus.WAITING_FOR_INTERNET,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest,
                    pendingRestore = true,
                    isOnline = false
                )
            )
        )
    }

    @Test
    fun failureIsShownOnlyForTheRelevantGeneration() {
        assertEquals(
            CloudRestoreStatus.RESTORE_FAILED,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_failed"),
                    lastFailureCode = "RESTORE_FAILED",
                    lastFailureGenerationId = "gen_failed"
                )
            )
        )
        assertEquals(
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_current"),
                    lastFailureCode = "RESTORE_FAILED",
                    lastFailureGenerationId = "gen_old"
                )
            )
        )
    }

    @Test
    fun temporaryManifestFailureIsNotClassifiedAsDefinitiveRestoreFailure() {
        assertEquals(
            CloudRestoreStatus.NEW_BACKUP_AVAILABLE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_existing"),
                    lastFailureCode = "NETWORK_UNAVAILABLE",
                    pendingRestore = true
                )
            )
        )
        assertEquals(
            CloudRestoreStatus.WAITING_FOR_INTERNET,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_existing"),
                    lastFailureCode = "NETWORK_UNAVAILABLE",
                    pendingRestore = true,
                    isOnline = false
                )
            )
        )
    }

    @Test
    fun transientFailureIsVisibleAndDoesNotChangeSuccessfulIdentity() {
        assertEquals(
            CloudRestoreStatus.RESTORE_FAILED,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest("gen_new"),
                    transientFailure = true,
                    restoredGenerationId = "gen_old"
                )
            )
        )
    }
}
