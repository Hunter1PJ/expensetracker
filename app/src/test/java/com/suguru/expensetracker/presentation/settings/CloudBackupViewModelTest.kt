package com.suguru.expensetracker.presentation.settings

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreException
import com.squareup.moshi.Moshi
import com.suguru.expensetracker.R
import com.suguru.expensetracker.data.backup.AutomaticCloudBackupOrchestrator
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.BackupAccount
import com.suguru.expensetracker.domain.model.BackupCategory
import com.suguru.expensetracker.domain.model.BackupContentFingerprint
import com.suguru.expensetracker.domain.model.BackupRestoreOutcome
import com.suguru.expensetracker.domain.model.BackupTransaction
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.CloudBackupChunkedPayload
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunk
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupPayloadCodec
import com.suguru.expensetracker.domain.model.CloudBackupState
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ExpenseTrackerBackup
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.model.toBackup
import com.suguru.expensetracker.domain.repository.AutoBackupPreferenceStore
import com.suguru.expensetracker.domain.repository.BackupRepository
import com.suguru.expensetracker.domain.repository.CloudBackupFingerprintStore
import com.suguru.expensetracker.domain.repository.CloudBackupRemoteRepository
import com.suguru.expensetracker.domain.repository.CloudBackupStateStore
import com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase
import com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

private class CloudBackupTestApplication : Application()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = CloudBackupTestApplication::class)
class CloudBackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new cloud manifest observed while settings is open schedules guarded automatic restore`() =
        runTest(testDispatcher) {
            val remote = FakeRemoteRepo()
            val stateStore = FakeStateStore()
            val fingerprintStore = FakeFingerprintStore()
            val backupRepo = FakeBackupRepo(nonEmptyBackup())
            val preferenceStore = FakeAutoBackupPreferenceStore(enabled = true)
            val scheduledRestores = mutableListOf<String>()
            val orchestrator = AutomaticCloudBackupOrchestrator(
                remoteRepository = remote,
                backupRepository = backupRepo,
                createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
                restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
                stateStore = stateStore,
                fingerprintStore = fingerprintStore,
                autoBackupPreferenceStore = preferenceStore,
                onScheduleAutomaticRestore = scheduledRestores::add,
                authenticatedUidProvider = { "uid_1" },
                activeWorkspaceUidProvider = { "uid_1" }
            )
            CloudBackupViewModel(
                currentUserId = "uid_1",
                remoteRepository = remote,
                createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
                restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
                backupRepository = backupRepo,
                cloudBackupStateStore = stateStore,
                cloudBackupFingerprintStore = fingerprintStore,
                autoBackupPreferenceStore = preferenceStore,
                orchestrator = orchestrator,
                isWorkspaceIdentityCurrent = { true },
                authenticatedUidProvider = { "uid_1" },
                workDispatcher = testDispatcher
            )
            advanceUntilIdle()
            val (manifest, chunks) = createCloudSnapshot(backupRepo, backupRepo.currentBackup)
            remote.publish(manifest, chunks)

            advanceUntilIdle()

            assertEquals(listOf("uid_1"), scheduledRestores)
        }

    @Test
    fun `backUpNow uploads chunks and updates CloudBackupStateStore and FingerprintStore`() = runTest(testDispatcher) {
        val remote = FakeRemoteRepo()
        val stateStore = FakeStateStore()
        val fpStore = FakeFingerprintStore()
        val backupRepo = FakeBackupRepo(nonEmptyBackup())

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = stateStore,
            cloudBackupFingerprintStore = fpStore,
            workDispatcher = testDispatcher
        )

        vm.backUpNow()
        advanceUntilIdle()

        assertEquals(1, remote.uploadedManifests.size)
        val savedState = stateStore.getState("uid_1")
        assertNotNull(savedState.lastSuccessfulSnapshotId)
        assertNull(savedState.lastFailureCode)
        assertEquals(
            BackupContentFingerprint.compute(backupRepo.currentBackup),
            fpStore.getLastUploadedFingerprint("uid_1")
        )
        assertEquals(R.string.cloud_backup_success, vm.actionState.value.successMessage?.resourceId)
    }

    @Test
    fun `manual backup is blocked while auth and active workspace disagree`() = runTest(testDispatcher) {
        val remote = FakeRemoteRepo()
        val backupRepo = FakeBackupRepo(nonEmptyBackup())
        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            isWorkspaceIdentityCurrent = { false },
            authenticatedUidProvider = { "uid_1" },
            workDispatcher = testDispatcher
        )

        vm.backUpNow()
        advanceUntilIdle()

        assertTrue(remote.uploadedManifests.isEmpty())
        assertEquals(R.string.cloud_workspace_changing, vm.actionState.value.errorMessage?.resourceId)
    }

    @Test
    fun `backUpNow failure records machine-readable failure code in CloudBackupStateStore`() = runTest(testDispatcher) {
        val remote = FakeRemoteRepo(
            uploadResultOverride = Result.failure(
                FirebaseFirestoreException("Permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
        )
        val stateStore = FakeStateStore()
        val fpStore = FakeFingerprintStore()
        val backupRepo = FakeBackupRepo(nonEmptyBackup())

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = stateStore,
            cloudBackupFingerprintStore = fpStore,
            workDispatcher = testDispatcher
        )

        vm.backUpNow()
        advanceUntilIdle()

        val savedState = stateStore.getState("uid_1")
        assertEquals("PERMISSION_DENIED", savedState.lastFailureCode)
        assertNotNull(savedState.lastFailureAtEpochMillis)
        assertNull(savedState.pendingSnapshotId)
        assertNotNull(vm.actionState.value.errorMessage)
    }

    @Test
    fun `manifestsState unwraps FirebaseFirestoreException wrapped in CancellationException`() = runTest(testDispatcher) {
        val remote = FakeRemoteRepo(
            observeFlowOverride = flow {
                throw CancellationException(
                    "Error getting Query snapshot",
                    FirebaseFirestoreException("Permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
                )
            }
        )
        val backupRepo = FakeBackupRepo(nonEmptyBackup())
        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        val job = vm.manifestsState.launchIn(backgroundScope)
        advanceUntilIdle()

        val state = vm.manifestsState.value
        assertTrue(state is CloudSnapshotsUiState.Error)
        assertEquals("Permission denied", (state as CloudSnapshotsUiState.Error).message)
        job.cancel()
    }

    @Test
    fun `requestRestoreSnapshot shows confirmation dialog and does not start restoration`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val snapshot = createCloudSnapshot(backupRepo, nonEmptyBackup())
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()

        // Confirmation candidate set, but no download or restoration performed
        assertNotNull(vm.actionState.value.pendingRestoreCandidate)
        assertEquals(snapshot.first.snapshotId, vm.actionState.value.pendingRestoreCandidate?.snapshotId)
        assertEquals(0, backupRepo.restoreCalls)
        assertFalse(vm.actionState.value.isBusy)
        assertNull(vm.actionState.value.restoreResult)
    }

    @Test
    fun `cancelPendingRestore closes confirmation dialog without restoration`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(nonEmptyBackup())
        val remote = FakeRemoteRepo()
        val snapshot = createCloudSnapshot(backupRepo, nonEmptyBackup())

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        assertNotNull(vm.actionState.value.pendingRestoreCandidate)

        vm.cancelPendingRestore()
        advanceUntilIdle()

        assertNull(vm.actionState.value.pendingRestoreCandidate)
        assertEquals(0, backupRepo.restoreCalls)
        assertNull(vm.actionState.value.restoreResult)
    }

    @Test
    fun `confirmPendingRestore executes restoration and displays success result`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val cloudData = nonEmptyBackup()
        val snapshot = createCloudSnapshot(backupRepo, cloudData)
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second
        val stateStore = FakeStateStore()
        stateStore.setState(
            "uid_1",
            CloudBackupState(
                lastRestoreFailureAtEpochMillis = 1L,
                lastRestoreFailureCode = "RESTORE_FAILED"
            )
        )

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = stateStore,
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        assertEquals(0, backupRepo.restoreCalls)

        vm.confirmPendingRestore()
        advanceUntilIdle()

        assertEquals(1, backupRepo.restoreCalls)
        assertNull(vm.actionState.value.pendingRestoreCandidate)
        assertFalse(vm.actionState.value.isBusy)
        val result = vm.actionState.value.restoreResult
        assertTrue(result is CloudRestoreResult.Success)
        assertEquals(snapshot.first.snapshotId, (result as CloudRestoreResult.Success).manifest.snapshotId)
        assertNull(stateStore.getState("uid_1").lastRestoreFailureCode)
        assertNotNull(stateStore.getState("uid_1").lastSuccessfulRestoreAtEpochMillis)
        assertEquals(snapshot.first.generationId, stateStore.getState("uid_1").lastSuccessfulRestoreGenerationId)
        assertEquals(snapshot.first.contentFingerprint, stateStore.getState("uid_1").lastSuccessfulRestoreFingerprint)

        vm.dismissRestoreResult()
        assertNull(vm.actionState.value.restoreResult)
    }

    @Test
    fun `manual restore aborts before Room commit when workspace changes during download`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        var workspaceCurrent = true
        val remote = FakeRemoteRepo(onDownload = { workspaceCurrent = false })
        val snapshot = createCloudSnapshot(backupRepo, nonEmptyBackup())
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second
        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            isWorkspaceIdentityCurrent = { workspaceCurrent },
            authenticatedUidProvider = { "uid_1" },
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        vm.confirmPendingRestore()
        advanceUntilIdle()

        assertEquals(0, backupRepo.restoreCalls)
        assertTrue(vm.actionState.value.restoreResult is CloudRestoreResult.Failure)
        assertTrue((vm.actionState.value.restoreResult as CloudRestoreResult.Failure).reason.contains("workspace changed"))
    }

    @Test
    fun `restore failure displays error result with failure reason`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val snapshot = createCloudSnapshot(backupRepo, nonEmptyBackup())
        val stateStore = FakeStateStore()
        // Chunks missing -> download failure
        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = stateStore,
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()

        vm.confirmPendingRestore()
        advanceUntilIdle()

        assertEquals(0, backupRepo.restoreCalls)
        assertFalse(vm.actionState.value.isBusy)
        val result = vm.actionState.value.restoreResult
        assertTrue(result is CloudRestoreResult.Failure)
        assertTrue((result as CloudRestoreResult.Failure).reason.isNotBlank())
        assertEquals("UPLOAD_FAILED", stateStore.getState("uid_1").lastRestoreFailureCode)
        assertNotNull(stateStore.getState("uid_1").lastRestoreFailureAtEpochMillis)

        vm.dismissRestoreResult()
        assertNull(vm.actionState.value.restoreResult)
    }

    @Test
    fun `confirmPendingRestore prevents duplicate restore while already busy`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val cloudData = nonEmptyBackup()
        val snapshot = createCloudSnapshot(backupRepo, cloudData)
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()

        vm.confirmPendingRestore()
        // Try calling again immediately while running
        vm.confirmPendingRestore()
        advanceUntilIdle()

        assertEquals(1, backupRepo.restoreCalls)
    }

    @Test
    fun `successful manual restore sets restoreResult immediately without navigation side effects`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val cloudData = nonEmptyBackup()
        val snapshot = createCloudSnapshot(backupRepo, cloudData)
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second
        val fpStore = FakeFingerprintStore()
        val stateStore = FakeStateStore()

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = stateStore,
            cloudBackupFingerprintStore = fpStore,
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        vm.confirmPendingRestore()
        advanceUntilIdle()

        // restoreResult must be Success immediately (dialog available without reopening Settings)
        val finalState = vm.actionState.value
        assertFalse("isBusy must be false after restore completes", finalState.isBusy)
        assertTrue("restoreResult must be Success", finalState.restoreResult is CloudRestoreResult.Success)
        assertNull("pendingRestoreCandidate must be cleared", finalState.pendingRestoreCandidate)
        // The restore flow should NOT set successMessage (which could be confused with backup success).
        // Only restoreResult should communicate the outcome to the UI.
        assertNull("No successMessage should be set during restore flow", finalState.successMessage)
    }

    @Test
    fun `restore with SettingsRecoveryPending outcome still sets success result and calls onPendingSettingsRecovery`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(
            effectivelyEmptyBackup(),
            restoreOutcomeOverride = BackupRestoreOutcome.SettingsRecoveryPending
        )
        val remote = FakeRemoteRepo()
        val cloudData = nonEmptyBackup()
        val snapshot = createCloudSnapshot(backupRepo, cloudData)
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second

        var settingsRecoveryCalled = false
        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            onPendingSettingsRecovery = {
                settingsRecoveryCalled = true
                null
            },
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        vm.confirmPendingRestore()
        advanceUntilIdle()

        // onPendingSettingsRecovery must have been invoked
        assertTrue("onPendingSettingsRecovery should be called", settingsRecoveryCalled)
        // restoreResult must still be Success (dialog displays over Settings)
        val result = vm.actionState.value.restoreResult
        assertTrue("restoreResult must be Success even with SettingsRecoveryPending", result is CloudRestoreResult.Success)
        assertFalse(vm.actionState.value.isBusy)
    }

    @Test
    fun `dismissRestoreResult clears result preserving Settings destination`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(effectivelyEmptyBackup())
        val remote = FakeRemoteRepo()
        val cloudData = nonEmptyBackup()
        val snapshot = createCloudSnapshot(backupRepo, cloudData)
        remote.chunksBySnapshot[snapshot.first.snapshotId] = snapshot.second

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        vm.confirmPendingRestore()
        advanceUntilIdle()

        assertTrue(vm.actionState.value.restoreResult is CloudRestoreResult.Success)

        // Pressing Next -> dismissRestoreResult
        vm.dismissRestoreResult()

        // After dismiss, all action state is clean — no navigation triggers
        val state = vm.actionState.value
        assertNull("restoreResult must be null after dismiss", state.restoreResult)
        assertNull("pendingRestoreCandidate must remain null", state.pendingRestoreCandidate)
        assertFalse("isBusy must be false", state.isBusy)
        assertNull("errorMessage must be null", state.errorMessage)
        // No successMessage that could trigger navigation
        assertNull("successMessage must be null", state.successMessage)
    }

    @Test
    fun `restore failure also preserves clean state for Settings`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(
            effectivelyEmptyBackup(),
            restoreOutcomeOverride = null // will fail due to missing chunks
        )
        val remote = FakeRemoteRepo()
        val snapshot = createCloudSnapshot(backupRepo, nonEmptyBackup())
        // Intentionally NOT adding chunks -> download failure

        val vm = CloudBackupViewModel(
            currentUserId = "uid_1",
            remoteRepository = remote,
            createFullBackupUseCase = CreateFullBackupUseCase(backupRepo),
            restoreBackupUseCase = RestoreBackupUseCase(backupRepo),
            backupRepository = backupRepo,
            cloudBackupStateStore = FakeStateStore(),
            cloudBackupFingerprintStore = FakeFingerprintStore(),
            workDispatcher = testDispatcher
        )

        vm.requestRestoreSnapshot(snapshot.first)
        advanceUntilIdle()
        vm.confirmPendingRestore()
        advanceUntilIdle()

        // Result must be Failure
        val result = vm.actionState.value.restoreResult
        assertTrue("restoreResult must be Failure", result is CloudRestoreResult.Failure)
        assertFalse(vm.actionState.value.isBusy)

        // Dismiss failure
        vm.dismissRestoreResult()
        assertNull(vm.actionState.value.restoreResult)
        assertNull(vm.actionState.value.successMessage)
    }

    private fun createCloudSnapshot(
        backupRepo: FakeBackupRepo,
        backup: ExpenseTrackerBackup
    ): Pair<CloudBackupManifest, List<CloudBackupPayloadChunk>> {
        val json = backupRepo.serializeBackup(backup).toByteArray(Charsets.UTF_8)
        val compressed = CloudBackupPayloadCodec().compress(json).getOrThrow()
        val chunked = CloudBackupPayloadChunker().chunk(compressed)
        val manifest = CloudBackupManifest(
            snapshotId = "snap_restore_1",
            uid = "uid_1",
            status = CloudBackupManifestStatus.COMPLETE,
            createdAt = Instant.ofEpochSecond(1_763_200_000L),
            completedAt = Instant.ofEpochSecond(1_763_200_005L),
            formatVersion = backup.formatVersion,
            databaseSchemaVersion = backup.databaseSchemaVersion,
            appVersion = backup.appVersion,
            chunkCount = chunked.totalChunkCount,
            compressedBytes = compressed.size.toLong(),
            uncompressedBytes = json.size.toLong(),
            payloadSha256 = chunked.payloadSha256,
            contentFingerprint = BackupContentFingerprint.compute(backup),
            generationId = "gen_restore_1"
        )
        return manifest to chunked.chunks
    }

    private fun nonEmptyBackup(): ExpenseTrackerBackup = effectivelyEmptyBackup().copy(
        transactions = listOf(
            BackupTransaction(
                id = 1L,
                type = TransactionType.EXPENSE.name,
                amountInMinorUnits = 5000L,
                currencyCode = "USD",
                accountId = 1L,
                destinationAccountId = null,
                categoryId = 1L,
                transactionTime = "2026-11-17T10:00:00Z",
                note = "Lunch",
                recurringRuleId = null,
                recurringOccurrenceDate = null,
                createdAt = "2026-11-17T10:00:00Z"
            )
        )
    )

    private fun effectivelyEmptyBackup(): ExpenseTrackerBackup = ExpenseTrackerBackup(
        formatVersion = 1,
        appVersion = "1.5.0",
        createdAt = "2026-11-17T00:00:00Z",
        databaseSchemaVersion = 4,
        accounts = listOf(
            BackupAccount(
                id = 1L,
                name = "Internal Default",
                type = AccountType.CASH.name,
                initialBalanceAmountInMinorUnits = 0L,
                initialBalanceCurrencyCode = "UZS",
                iconName = "account_balance",
                colorHex = "#10B981",
                isArchived = false,
                createdAt = "2026-11-17T00:00:00Z",
                internalKey = "default"
            )
        ),
        categories = listOf(
            BackupCategory(1L, "Groceries", CategoryType.EXPENSE.name, "fastfood", "#10B981", true, false),
            BackupCategory(2L, "Food", CategoryType.EXPENSE.name, "fastfood", "#F59E0B", true, false),
            BackupCategory(3L, "Transport", CategoryType.EXPENSE.name, "directions_car", "#3B82F6", true, false),
            BackupCategory(4L, "Study", CategoryType.EXPENSE.name, "school", "#6366F1", true, false),
            BackupCategory(5L, "Shopping", CategoryType.EXPENSE.name, "shopping_bag", "#EC4899", true, false),
            BackupCategory(6L, "Health", CategoryType.EXPENSE.name, "local_hospital", "#EF4444", true, false),
            BackupCategory(7L, "Entertainment", CategoryType.EXPENSE.name, "movie", "#8B5CF6", true, false),
            BackupCategory(8L, "Bills", CategoryType.EXPENSE.name, "receipt", "#64748B", true, false),
            BackupCategory(9L, "Other", CategoryType.EXPENSE.name, "category", "#14B8A6", true, false),
            BackupCategory(10L, "Salary", CategoryType.INCOME.name, "work", "#10B981", true, false),
            BackupCategory(11L, "Freelance", CategoryType.INCOME.name, "work", "#3B82F6", true, false),
            BackupCategory(12L, "Business", CategoryType.INCOME.name, "work", "#6366F1", true, false),
            BackupCategory(13L, "Gift", CategoryType.INCOME.name, "card_giftcard", "#F59E0B", true, false),
            BackupCategory(14L, "Other", CategoryType.INCOME.name, "category", "#14B8A6", true, false)
        ),
        transactions = emptyList(),
        budgets = emptyList(),
        recurringTransactions = emptyList(),
        recurringOccurrences = emptyList(),
        settings = AppSettings(
            themeMode = ThemeMode.SYSTEM,
            preferredCurrencyCode = null,
            weekStart = WeekStart.MONDAY,
            dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
            timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
            showCurrencyCode = false,
            confirmBeforeDelete = true
        ).toBackup()
    )

    private class FakeRemoteRepo(
        private val uploadResultOverride: Result<CloudBackupManifest>? = null,
        private val observeFlowOverride: Flow<List<CloudBackupManifest>>? = null,
        private val onDownload: () -> Unit = {}
    ) : CloudBackupRemoteRepository {
        val uploadedManifests = mutableListOf<CloudBackupManifest>()
        val chunksBySnapshot = mutableMapOf<String, List<CloudBackupPayloadChunk>>()
        private val flow = MutableStateFlow<List<CloudBackupManifest>>(emptyList())

        override fun observeManifests(userId: String): Flow<List<CloudBackupManifest>> =
            observeFlowOverride ?: flow

        fun publish(manifest: CloudBackupManifest, chunks: List<CloudBackupPayloadChunk>) {
            uploadedManifests.add(manifest)
            chunksBySnapshot[manifest.snapshotId] = chunks
            flow.value = uploadedManifests.toList()
        }

        override suspend fun getLatestCompleteManifest(): Result<CloudBackupManifest?> {
            val latest = uploadedManifests
                .filter { it.status == CloudBackupManifestStatus.COMPLETE }
                .maxByOrNull { it.completedAt ?: it.createdAt }
            return Result.success(latest)
        }

        override suspend fun uploadBackup(
            userId: String,
            manifest: CloudBackupManifest,
            chunkedPayload: CloudBackupChunkedPayload
        ): Result<CloudBackupManifest> {
            uploadResultOverride?.let { return it }
            val completed = manifest.copy(
                status = CloudBackupManifestStatus.COMPLETE,
                completedAt = Instant.now()
            )
            uploadedManifests.add(completed)
            chunksBySnapshot[manifest.snapshotId] = chunkedPayload.chunks
            flow.value = uploadedManifests.toList()
            return Result.success(completed)
        }

        override suspend fun downloadChunks(
            userId: String,
            snapshotId: String
        ): Result<List<CloudBackupPayloadChunk>> {
            onDownload()
            val chunks = chunksBySnapshot[snapshotId]
                ?: return Result.failure(IllegalStateException("Missing snapshot"))
            return Result.success(chunks)
        }

        override suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit> {
            chunksBySnapshot.remove(snapshotId)
            uploadedManifests.removeAll { it.snapshotId == snapshotId }
            flow.value = uploadedManifests.toList()
            return Result.success(Unit)
        }
    }

    private class FakeStateStore : CloudBackupStateStore {
        private val stateFlow = MutableStateFlow<Map<String, CloudBackupState>>(emptyMap())

        override suspend fun getState(uid: String): CloudBackupState =
            stateFlow.value[uid] ?: CloudBackupState.EMPTY

        override fun observeState(uid: String): Flow<CloudBackupState> =
            stateFlow.map { it[uid] ?: CloudBackupState.EMPTY }

        override suspend fun setState(uid: String, state: CloudBackupState) {
            stateFlow.value = stateFlow.value + (uid to state)
        }

        override suspend fun clearState(uid: String) {
            stateFlow.value = stateFlow.value - uid
        }
    }

    private class FakeFingerprintStore : CloudBackupFingerprintStore {
        private val fpFlow = MutableStateFlow<Map<String, String>>(emptyMap())

        override suspend fun getLastUploadedFingerprint(uid: String): String? = fpFlow.value[uid]

        override fun observeLastUploadedFingerprint(uid: String): Flow<String?> = fpFlow.map { it[uid] }

        override suspend fun setLastUploadedFingerprint(uid: String, fingerprint: String) {
            fpFlow.value = fpFlow.value + (uid to fingerprint)
        }

        override suspend fun clearLastUploadedFingerprint(uid: String) {
            fpFlow.value = fpFlow.value - uid
        }
    }

    private class FakeAutoBackupPreferenceStore(
        private var enabled: Boolean
    ) : AutoBackupPreferenceStore {
        override suspend fun isAutoBackupEnabled(uid: String): Boolean = enabled

        override fun observeAutoBackupEnabled(uid: String): Flow<Boolean> = MutableStateFlow(enabled)

        override suspend fun setAutoBackupEnabled(uid: String, enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakeBackupRepo(
        var currentBackup: ExpenseTrackerBackup,
        private val restoreOutcomeOverride: BackupRestoreOutcome? = null
    ) : BackupRepository {
        private val moshi = Moshi.Builder().build()
        private val adapter = moshi.adapter(ExpenseTrackerBackup::class.java)
        var restoreCalls = 0

        override suspend fun createSnapshot(): ExpenseTrackerBackup = currentBackup

        override suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome> {
            restoreCalls++
            currentBackup = backup
            return Result.success(restoreOutcomeOverride ?: BackupRestoreOutcome.Complete)
        }

        override fun serializeBackup(backup: ExpenseTrackerBackup): String = adapter.toJson(backup)

        override fun deserializeBackup(json: String): Result<ExpenseTrackerBackup> = runCatching {
            adapter.fromJson(json) ?: error("Null backup JSON")
        }
    }
}
