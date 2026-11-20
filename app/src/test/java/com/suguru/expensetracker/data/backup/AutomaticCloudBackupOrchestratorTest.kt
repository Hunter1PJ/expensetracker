package com.suguru.expensetracker.data.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.BackupContentFingerprint
import com.suguru.expensetracker.domain.model.CloudBackupPayloadCodec
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.model.CloudRestoreStatus
import com.suguru.expensetracker.domain.model.CloudRestoreStatusInput
import com.suguru.expensetracker.domain.model.CloudRestoreStatusResolver
import com.suguru.expensetracker.domain.model.CloudBackupChunkedPayload
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.model.BackupTransaction
import com.suguru.expensetracker.data.remote.firestore.FirestoreCloudBackupRepository
import com.suguru.expensetracker.data.local.WorkspaceId
import com.suguru.expensetracker.presentation.util.AppLanguageManager
import com.suguru.expensetracker.domain.repository.AutoBackupPreferenceStore
import com.suguru.expensetracker.domain.repository.BackupRestorePreconditionFailedException
import com.suguru.expensetracker.domain.repository.BackupRepository
import com.suguru.expensetracker.domain.repository.CloudBackupFingerprintStore
import com.suguru.expensetracker.domain.repository.CloudBackupRemoteRepository
import com.suguru.expensetracker.domain.repository.CloudBackupStateStore
import com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase
import com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AutomaticCloudBackupOrchestratorTest {

    @Test
    fun `new uid with local transactions and no cloud snapshot schedules pending backup`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        val orchestrator = fixture.createOrchestrator()

        orchestrator.evaluateOnLaunchOrSignIn(Fixture.UID)

        assertEquals(listOf(Fixture.UID), fixture.scheduledBackups)
        assertEquals(
            FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
            fixture.stateStore.getState(Fixture.UID).pendingSnapshotId
        )
        assertNotNull(fixture.stateStore.getState(Fixture.UID).pendingSinceEpochMillis)
        assertEquals(0, fixture.remote.uploadCalls)
    }

    @Test
    fun `authenticated uid with its active workspace completes manual backup`() = runTest {
        val fixture = Fixture(remoteManifest = null)

        val result = fixture.createOrchestrator().performBackup(Fixture.UID, explicitManual = true)

        assertTrue(result is BackupExecutionResult.Success)
        assertEquals(1, fixture.remote.uploadCalls)
    }

    @Test
    fun `mismatched active workspace prevents manual backup`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.activeWorkspaceUid = "uid_a"

        val result = fixture.createOrchestrator().performBackup(Fixture.UID, explicitManual = true)

        assertTrue(result is BackupExecutionResult.PermanentFailure)
        assertEquals(0, fixture.remote.uploadCalls)
    }

    @Test
    fun `account switch while local snapshot is prepared prevents upload`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.backupRepository.onCreateSnapshot = { fixture.authenticatedUid = "uid_b" }

        val result = fixture.createOrchestrator().performBackup(Fixture.UID, explicitManual = true)

        assertTrue(result is BackupExecutionResult.PermanentFailure)
        assertEquals("UID_MISMATCH", (result as BackupExecutionResult.PermanentFailure).failureCode)
        assertEquals(0, fixture.remote.uploadCalls)
    }

    @Test
    fun `automatic mode off does not schedule backup work`() = runTest {
        val fixture = Fixture(remoteManifest = null, autoEnabled = false)
        val orchestrator = fixture.createOrchestrator()

        orchestrator.evaluateOnLaunchOrSignIn(Fixture.UID)

        assertTrue(fixture.scheduledBackups.isEmpty())
        assertEquals(null, fixture.stateStore.getState(Fixture.UID).pendingSnapshotId)
        assertEquals(0, fixture.remote.latestManifestChecks)
    }

    @Test
    fun `matching cloud generation is adopted without destructively restoring the same local data`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        val payload = CloudBackupPayloadCodec().compress(
            fixture.backupRepository.serializeBackup(fixture.backupRepository.snapshot).toByteArray(Charsets.UTF_8)
        ).getOrThrow()
        val chunked = CloudBackupPayloadChunker().chunk(payload)
        val fingerprint = BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
        fixture.remote.payloadChunks = chunked.chunks
        fixture.remote.latest = CloudBackupManifest(
            snapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
            uid = Fixture.UID,
            status = CloudBackupManifestStatus.COMPLETE,
            createdAt = Instant.parse("2026-01-02T00:00:00Z"),
            completedAt = Instant.parse("2026-01-02T00:01:00Z"),
            formatVersion = 1,
            databaseSchemaVersion = 1,
            appVersion = "test",
            chunkCount = chunked.totalChunkCount,
            compressedBytes = payload.size.toLong(),
            uncompressedBytes = fixture.backupRepository.serializeBackup(fixture.backupRepository.snapshot).toByteArray().size.toLong(),
            payloadSha256 = chunked.payloadSha256,
            contentFingerprint = fingerprint,
            generationId = "gen_new"
        )
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, fingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulSnapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                lastSuccessfulRestoreGenerationId = "gen_old",
                lastSuccessfulRestoreFingerprint = fingerprint
            )
        )

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertEquals("gen_new", fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreGenerationId)
        assertEquals(
            CloudRestoreStatus.UP_TO_DATE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = fixture.remote.latest,
                    restoredGenerationId = fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreGenerationId,
                    restoredFingerprint = fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreFingerprint
                )
            )
        )
        assertTrue(fixture.scheduledRestores.isEmpty())
        assertTrue(fixture.scheduledBackups.isEmpty())
    }

    @Test
    fun `matching local and cloud data adopts legacy generation without restoring on restart`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.installCurrentSnapshotAsRemote("gen_existing")
        val fingerprint = BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, fingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulBackupAtEpochMillis = 1_763_200_050_000L,
                lastSuccessfulSnapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID
                // Legacy local state has no applied-generation marker.
            )
        )
        val twoTransactionsBefore = fixture.backupRepository.snapshot.transactions.toList()

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        val state = fixture.stateStore.getState(Fixture.UID)
        assertEquals("gen_existing", state.lastSuccessfulRestoreGenerationId)
        assertEquals(fingerprint, state.lastSuccessfulRestoreFingerprint)
        assertEquals(1_763_200_050_000L, state.lastSuccessfulBackupAtEpochMillis)
        assertEquals(twoTransactionsBefore, fixture.backupRepository.snapshot.transactions)
        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertTrue(fixture.scheduledRestores.isEmpty())
        assertTrue(fixture.scheduledBackups.isEmpty())
        assertEquals(
            CloudRestoreStatus.UP_TO_DATE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = fixture.remote.latest,
                    restoredGenerationId = state.lastSuccessfulRestoreGenerationId,
                    restoredFingerprint = state.lastSuccessfulRestoreFingerprint
                )
            )
        )
    }

    @Test
    fun `language change and process recreation preserve the same account cloud generation and two transactions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = Fixture(remoteManifest = null)
        val initial = fixture.backupRepository.snapshot
        fixture.backupRepository.snapshot = initial.copy(
            transactions = initial.transactions + initial.transactions.first().copy(
                id = 2L,
                amountInMinorUnits = 200L,
                note = "second"
            )
        )
        fixture.installCurrentSnapshotAsRemote("gen_existing")
        val manifest = requireNotNull(fixture.remote.latest)
        val fingerprint = BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, fingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulBackupAtEpochMillis = 1_763_200_050_000L,
                lastSuccessfulSnapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                lastSuccessfulRestoreGenerationId = manifest.generationId,
                lastSuccessfulRestoreFingerprint = fingerprint
            )
        )
        val expectedDatabase = WorkspaceId.FirebaseAccount(Fixture.UID).databaseName
        val originalTransactions = fixture.backupRepository.snapshot.transactions.toList()
        val initiallyUpToDate = CloudRestoreStatusResolver.resolve(
            CloudRestoreStatusInput(
                activeManifest = manifest,
                restoredGenerationId = fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreGenerationId,
                restoredFingerprint = fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreFingerprint
            )
        )
        assertEquals(CloudRestoreStatus.UP_TO_DATE, initiallyUpToDate)

        listOf("en", "ru", "uz").forEach { language ->
            AppLanguageManager.setLanguage(context, language)
            assertEquals(language, AppLanguageManager.selectedLanguageTag(context))

            // A new orchestrator instance models the process-local service reconstruction after relaunch.
            fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

            val stateAfterRestart = fixture.stateStore.getState(Fixture.UID)
            assertEquals(Fixture.UID, fixture.authenticatedUid)
            assertEquals(Fixture.UID, fixture.activeWorkspaceUid)
            assertEquals(expectedDatabase, WorkspaceId.FirebaseAccount(Fixture.UID).databaseName)
            assertEquals(manifest, fixture.remote.latest)
            assertEquals(manifest.generationId, stateAfterRestart.lastSuccessfulRestoreGenerationId)
            assertEquals(fingerprint, fixture.fingerprintStore.getLastUploadedFingerprint(Fixture.UID))
            assertEquals(originalTransactions, fixture.backupRepository.snapshot.transactions)
            assertEquals(0, fixture.backupRepository.restoreCalls)
            assertEquals(0, fixture.remote.uploadCalls)
            assertEquals(
                CloudRestoreStatus.UP_TO_DATE,
                CloudRestoreStatusResolver.resolve(
                    CloudRestoreStatusInput(
                        activeManifest = fixture.remote.latest,
                        restoredGenerationId = stateAfterRestart.lastSuccessfulRestoreGenerationId,
                        restoredFingerprint = stateAfterRestart.lastSuccessfulRestoreFingerprint
                    )
                )
            )
        }
    }

    @Test
    fun `temporary manifest lookup failure preserves known backup metadata and local data`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.installCurrentSnapshotAsRemote("gen_existing")
        val fingerprint = BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, fingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulBackupAtEpochMillis = 1_763_200_050_000L,
                lastSuccessfulSnapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                lastSuccessfulRestoreGenerationId = "gen_existing",
                lastSuccessfulRestoreFingerprint = fingerprint
            )
        )
        fixture.remote.latestManifestError = RuntimeException("temporary network failure")
        val originalTransactions = fixture.backupRepository.snapshot.transactions.toList()

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        val state = fixture.stateStore.getState(Fixture.UID)
        assertEquals(1_763_200_050_000L, state.lastSuccessfulBackupAtEpochMillis)
        assertEquals("gen_existing", state.lastSuccessfulRestoreGenerationId)
        assertEquals(fingerprint, fixture.fingerprintStore.getLastUploadedFingerprint(Fixture.UID))
        assertEquals(originalTransactions, fixture.backupRepository.snapshot.transactions)
        assertEquals(0, fixture.remote.uploadCalls)
        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertTrue(state.pendingRestore)
        assertEquals(null, state.lastRestoreFailureCode)
    }

    @Test
    fun `changed local content is not overwritten by a newer cloud generation`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        val olderCloudSnapshot = fixture.backupRepository.snapshot
        val olderCloudFingerprint = BackupContentFingerprint.compute(olderCloudSnapshot)
        fixture.installCurrentSnapshotAsRemote("gen_new")
        fixture.backupRepository.snapshot = olderCloudSnapshot.copy(
            transactions = olderCloudSnapshot.transactions +
                olderCloudSnapshot.transactions.first().copy(id = 2L)
        )
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, olderCloudFingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulRestoreGenerationId = "gen_old",
                lastSuccessfulRestoreFingerprint = olderCloudFingerprint
            )
        )

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertTrue(fixture.scheduledRestores.isEmpty())
        assertEquals(listOf(Fixture.UID), fixture.scheduledBackups)
        assertEquals(2, fixture.backupRepository.snapshot.transactions.size)
    }

    @Test
    fun `originating device marks the successfully uploaded generation as locally applied`() = runTest {
        val fixture = Fixture(remoteManifest = null)

        val result = fixture.createOrchestrator().performBackup(Fixture.UID)

        assertTrue(result is BackupExecutionResult.Success)
        val state = fixture.stateStore.getState(Fixture.UID)
        val manifest = fixture.remote.latest!!
        assertEquals(manifest.generationId, state.lastSuccessfulRestoreGenerationId)
        assertEquals(manifest.contentFingerprint, state.lastSuccessfulRestoreFingerprint)
        assertFalse(state.pendingRestore)
        assertEquals(
            CloudRestoreStatus.UP_TO_DATE,
            CloudRestoreStatusResolver.resolve(
                CloudRestoreStatusInput(
                    activeManifest = manifest,
                    restoredGenerationId = state.lastSuccessfulRestoreGenerationId,
                    restoredFingerprint = state.lastSuccessfulRestoreFingerprint
                )
            )
        )
    }

    @Test
    fun `local mutation during upload does not mark the uploaded snapshot as applied`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.remote.afterUpload = {
            fixture.backupRepository.snapshot = fixture.backupRepository.snapshot.copy(
                transactions = fixture.backupRepository.snapshot.transactions +
                    fixture.backupRepository.snapshot.transactions.first().copy(id = 2L)
            )
        }

        val result = fixture.createOrchestrator().performBackup(Fixture.UID)

        assertTrue(result is BackupExecutionResult.Success)
        val state = fixture.stateStore.getState(Fixture.UID)
        assertEquals(null, state.lastSuccessfulRestoreGenerationId)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, state.pendingSnapshotId)
        assertEquals(listOf(Fixture.UID), fixture.scheduledBackups)
        assertTrue(fixture.scheduledRestores.isEmpty())
    }

    @Test
    fun `external generation promoted during upload is not marked applied by origin device`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.remote.afterUpload = {
            fixture.remote.latest = fixture.remote.latest!!.copy(
                generationId = "gen_external",
                contentFingerprint = "b".repeat(64),
                payloadSha256 = "c".repeat(64)
            )
        }

        val result = fixture.createOrchestrator().performBackup(Fixture.UID)

        assertTrue(result is BackupExecutionResult.Success)
        val state = fixture.stateStore.getState(Fixture.UID)
        assertEquals(null, state.lastSuccessfulRestoreGenerationId)
        assertEquals("gen_external", fixture.remote.latest?.generationId)
        assertTrue(state.pendingRestore)
        assertEquals(listOf(Fixture.UID), fixture.scheduledRestores)
    }

    @Test
    fun `account switch during upload prevents local sync metadata update`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.remote.afterUpload = { fixture.authenticatedUid = "uid_b" }

        val result = fixture.createOrchestrator().performBackup(Fixture.UID)

        assertTrue(result is BackupExecutionResult.Success)
        assertEquals(null, fixture.fingerprintStore.getLastUploadedFingerprint(Fixture.UID))
        assertEquals(null, fixture.stateStore.getState(Fixture.UID).lastSuccessfulBackupAtEpochMillis)
        assertEquals(null, fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreGenerationId)
    }

    @Test
    fun `local financial mutation schedules automatic backup work`() = runTest {
        val fixture = Fixture(remoteManifest = null)

        fixture.createOrchestrator().onLocalDataMutated()

        assertEquals(listOf(Fixture.UID), fixture.scheduledBackups)
        assertNotNull(fixture.stateStore.getState(Fixture.UID).pendingSnapshotId)
    }

    @Test
    fun `guest workspace never checks or uploads firebase cloud backups`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        fixture.authenticatedUid = null
        fixture.activeWorkspaceUid = null

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn("guest")

        assertEquals(0, fixture.remote.latestManifestChecks)
        assertEquals(0, fixture.remote.uploadCalls)
        assertTrue(fixture.scheduledBackups.isEmpty())
        assertTrue(fixture.scheduledRestores.isEmpty())
    }

    @Test
    fun `automatic backup off does not automatically restore a newer remote generation`() = runTest {
        val fixture = Fixture(remoteManifest = null, autoEnabled = false)
        fixture.installCurrentSnapshotAsRemote("gen_external")
        fixture.fingerprintStore.setLastUploadedFingerprint(
            Fixture.UID,
            BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
        )
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulRestoreGenerationId = "gen_old",
                lastSuccessfulRestoreFingerprint = BackupContentFingerprint.compute(fixture.backupRepository.snapshot)
            )
        )

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertTrue(fixture.scheduledRestores.isEmpty())
    }

    @Test
    fun `account switch at room restore commit guard leaves local database untouched`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        val localSnapshot = fixture.backupRepository.snapshot
        val localFingerprint = BackupContentFingerprint.compute(localSnapshot)
        val remoteSnapshot = localSnapshot.copy(
            transactions = localSnapshot.transactions + localSnapshot.transactions.first().copy(id = 2L)
        )
        fixture.backupRepository.snapshot = remoteSnapshot
        fixture.installCurrentSnapshotAsRemote("gen_new")
        fixture.backupRepository.snapshot = localSnapshot
        fixture.backupRepository.snapshotToDeserialize = remoteSnapshot
        fixture.fingerprintStore.setLastUploadedFingerprint(Fixture.UID, localFingerprint)
        fixture.stateStore.setState(
            Fixture.UID,
            CloudBackupState(
                lastSuccessfulRestoreGenerationId = "gen_old",
                lastSuccessfulRestoreFingerprint = localFingerprint
            )
        )
        fixture.backupRepository.onBeforeRestoreCommit = { fixture.authenticatedUid = "uid_b" }

        fixture.createOrchestrator().evaluateOnLaunchOrSignIn(Fixture.UID)

        assertEquals(0, fixture.backupRepository.restoreCalls)
        assertEquals(localSnapshot, fixture.backupRepository.snapshot)
        assertEquals("gen_old", fixture.stateStore.getState(Fixture.UID).lastSuccessfulRestoreGenerationId)
    }

    @Test
    fun `keep local conflict resolution completes upload without mutex deadlock`() = runTest {
        val fixture = Fixture(remoteManifest = null)
        val orchestrator = fixture.createOrchestrator()

        val result = orchestrator.resolveConflictKeepLocalAndBackup(Fixture.UID)

        assertTrue(result is BackupExecutionResult.Success)
        assertEquals(1, fixture.remote.uploadCalls)
    }

    private class Fixture(
        remoteManifest: CloudBackupManifest?,
        autoEnabled: Boolean = true
    ) {
        val backupRepository = FakeBackupRepository()
        val remote = FakeRemoteRepository(remoteManifest)
        val stateStore = FakeStateStore()
        val fingerprintStore = FakeFingerprintStore()
        private val preferences = FakePreferences(enabled = autoEnabled)
        val scheduledBackups = mutableListOf<String>()
        val scheduledRestores = mutableListOf<String>()
        var authenticatedUid: String? = UID
        var activeWorkspaceUid: String? = UID
        fun installCurrentSnapshotAsRemote(generationId: String) {
            val encoded = backupRepository.serializeBackup(backupRepository.snapshot).toByteArray(Charsets.UTF_8)
            val payload = CloudBackupPayloadCodec().compress(encoded).getOrThrow()
            val chunked = CloudBackupPayloadChunker().chunk(payload)
            val fingerprint = BackupContentFingerprint.compute(backupRepository.snapshot)
            remote.payloadChunks = chunked.chunks
            remote.latest = CloudBackupManifest(
                snapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                uid = UID,
                status = CloudBackupManifestStatus.COMPLETE,
                createdAt = Instant.parse("2026-01-02T00:00:00Z"),
                completedAt = Instant.parse("2026-01-02T00:01:00Z"),
                formatVersion = 1,
                databaseSchemaVersion = 1,
                appVersion = "test",
                chunkCount = chunked.totalChunkCount,
                compressedBytes = payload.size.toLong(),
                uncompressedBytes = encoded.size.toLong(),
                payloadSha256 = chunked.payloadSha256,
                contentFingerprint = fingerprint,
                generationId = generationId
            )
        }

        fun createOrchestrator() = AutomaticCloudBackupOrchestrator(
            remoteRepository = remote,
            backupRepository = backupRepository,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepository),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepository),
            stateStore = stateStore,
            fingerprintStore = fingerprintStore,
            autoBackupPreferenceStore = preferences,
            onScheduleDebouncedBackup = scheduledBackups::add,
            onScheduleAutomaticRestore = scheduledRestores::add,
            authenticatedUidProvider = { authenticatedUid },
            activeWorkspaceUidProvider = { activeWorkspaceUid }
        )

        companion object {
            const val UID = "test_uid"
        }
    }

    private class FakeBackupRepository : BackupRepository {
        var onCreateSnapshot: () -> Unit = {}
        var snapshot = ExpenseTrackerBackup(
            formatVersion = 1,
            appVersion = "test",
            createdAt = "2026-01-01T00:00:00Z",
            databaseSchemaVersion = 1,
            accounts = emptyList(),
            categories = emptyList(),
            transactions = listOf(
                BackupTransaction(
                    id = 1L,
                    type = "EXPENSE",
                    amountInMinorUnits = 100L,
                    currencyCode = "USD",
                    accountId = 1L,
                    destinationAccountId = null,
                    categoryId = 1L,
                    transactionTime = "2026-01-01T00:00:00Z",
                    note = "test",
                    recurringRuleId = null,
                    recurringOccurrenceDate = null,
                    createdAt = "2026-01-01T00:00:00Z"
                )
            ),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
            recurringOccurrences = emptyList(),
            settings = null
        )

        override suspend fun createSnapshot(): ExpenseTrackerBackup {
            onCreateSnapshot()
            return snapshot
        }

        var restoreCalls = 0
        var snapshotToDeserialize: ExpenseTrackerBackup? = null
        var onBeforeRestoreCommit: () -> Unit = {}
        override suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome> {
            restoreCalls++
            snapshot = backup
            return Result.success(BackupRestoreOutcome.Complete)
        }

        override suspend fun restoreSnapshotGuarded(
            backup: ExpenseTrackerBackup,
            expectedCurrentFingerprint: String?,
            preCommitCheck: suspend () -> Boolean
        ): Result<BackupRestoreOutcome> {
            if (!preCommitCheck()) return Result.failure(BackupRestorePreconditionFailedException())
            if (
                expectedCurrentFingerprint != null &&
                BackupContentFingerprint.compute(snapshot) != expectedCurrentFingerprint
            ) {
                return Result.failure(BackupRestorePreconditionFailedException())
            }
            onBeforeRestoreCommit()
            if (!preCommitCheck()) return Result.failure(BackupRestorePreconditionFailedException())
            return restoreSnapshot(backup)
        }

        override fun serializeBackup(backup: ExpenseTrackerBackup): String = "test-backup"

        override fun deserializeBackup(json: String): Result<ExpenseTrackerBackup> =
            Result.success(snapshotToDeserialize ?: snapshot)
    }

    private class FakeRemoteRepository(
        var latest: CloudBackupManifest?
    ) : CloudBackupRemoteRepository {
        var payloadChunks: List<CloudBackupPayloadChunk> = emptyList()
        var uploadCalls = 0
        var afterUpload: () -> Unit = {}
        var latestManifestChecks = 0
        var latestManifestError: Throwable? = null
        private val manifests = MutableStateFlow(emptyList<CloudBackupManifest>())

        override fun observeManifests(userId: String): Flow<List<CloudBackupManifest>> = manifests

        override suspend fun getLatestCompleteManifest(): Result<CloudBackupManifest?> {
            latestManifestChecks++
            latestManifestError?.let { return Result.failure(it) }
            return Result.success(latest)
        }

        override suspend fun uploadBackup(
            userId: String,
            manifest: CloudBackupManifest,
            chunkedPayload: CloudBackupChunkedPayload
        ): Result<CloudBackupManifest> {
            uploadCalls++
            val completed = manifest.copy(
                status = CloudBackupManifestStatus.COMPLETE,
                completedAt = Instant.now(),
                generationId = "gen_upload_$uploadCalls"
            )
            latest = completed
            manifests.value = listOf(completed)
            afterUpload()
            return Result.success(completed)
        }

        override suspend fun downloadChunks(
            userId: String,
            snapshotId: String
        ): Result<List<CloudBackupPayloadChunk>> = Result.success(payloadChunks)

        override suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeStateStore : CloudBackupStateStore {
        private val states = mutableMapOf<String, com.suguru.expensetracker.domain.model.CloudBackupState>()

        override suspend fun getState(uid: String) =
            states[uid] ?: com.suguru.expensetracker.domain.model.CloudBackupState.EMPTY

        override fun observeState(uid: String): Flow<com.suguru.expensetracker.domain.model.CloudBackupState> =
            manifestsFlow(uid)

        private fun manifestsFlow(uid: String): Flow<com.suguru.expensetracker.domain.model.CloudBackupState> =
            MutableStateFlow(getStateValue(uid)).map { it }

        private fun getStateValue(uid: String) =
            states[uid] ?: com.suguru.expensetracker.domain.model.CloudBackupState.EMPTY

        override suspend fun setState(
            uid: String,
            state: com.suguru.expensetracker.domain.model.CloudBackupState
        ) {
            states[uid] = state
        }

        override suspend fun clearState(uid: String) {
            states.remove(uid)
        }
    }

    private class FakeFingerprintStore : CloudBackupFingerprintStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getLastUploadedFingerprint(uid: String): String? = values[uid]

        override fun observeLastUploadedFingerprint(uid: String): Flow<String?> =
            MutableStateFlow(values[uid])

        override suspend fun setLastUploadedFingerprint(uid: String, fingerprint: String) {
            values[uid] = fingerprint
        }

        override suspend fun clearLastUploadedFingerprint(uid: String) {
            values.remove(uid)
        }
    }

    private class FakePreferences(private val enabled: Boolean) : AutoBackupPreferenceStore {
        override suspend fun isAutoBackupEnabled(uid: String): Boolean = enabled

        override fun observeAutoBackupEnabled(uid: String): Flow<Boolean> = MutableStateFlow(enabled)

        override suspend fun setAutoBackupEnabled(uid: String, enabled: Boolean) = Unit
    }
}
