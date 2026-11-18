package com.suguru.expensetracker.presentation.settings

import com.google.firebase.firestore.FirebaseFirestoreException
import com.squareup.moshi.Moshi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
        assertTrue(vm.actionState.value.successMessage?.contains("completed") == true)
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
    fun `requestRestoreSnapshot sets pendingRestoreCandidate when local database is non-empty`() = runTest(testDispatcher) {
        val backupRepo = FakeBackupRepo(nonEmptyBackup())
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

        assertNotNull(vm.actionState.value.pendingRestoreCandidate)
        assertEquals(0, backupRepo.restoreCalls)

        vm.confirmPendingRestore()
        advanceUntilIdle()
        assertEquals(1, backupRepo.restoreCalls)
        assertNull(vm.actionState.value.pendingRestoreCandidate)
    }

    @Test
    fun `requestRestoreSnapshot restores immediately without confirmation when local database is effectively empty`() = runTest(testDispatcher) {
        val emptyLocal = effectivelyEmptyBackup()
        val cloudData = nonEmptyBackup()
        val backupRepo = FakeBackupRepo(emptyLocal)
        val remote = FakeRemoteRepo()
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

        assertNull(vm.actionState.value.pendingRestoreCandidate)
        assertEquals(1, backupRepo.restoreCalls)
        assertEquals(cloudData.transactions.size, backupRepo.currentBackup.transactions.size)
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
            contentFingerprint = BackupContentFingerprint.compute(backup)
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
        private val observeFlowOverride: Flow<List<CloudBackupManifest>>? = null
    ) : CloudBackupRemoteRepository {
        val uploadedManifests = mutableListOf<CloudBackupManifest>()
        val chunksBySnapshot = mutableMapOf<String, List<CloudBackupPayloadChunk>>()
        private val flow = MutableStateFlow<List<CloudBackupManifest>>(emptyList())

        override fun observeManifests(userId: String): Flow<List<CloudBackupManifest>> =
            observeFlowOverride ?: flow

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

    private class FakeBackupRepo(var currentBackup: ExpenseTrackerBackup) : BackupRepository {
        private val moshi = Moshi.Builder().build()
        private val adapter = moshi.adapter(ExpenseTrackerBackup::class.java)
        var restoreCalls = 0

        override suspend fun createSnapshot(): ExpenseTrackerBackup = currentBackup

        override suspend fun restoreSnapshot(backup: ExpenseTrackerBackup): Result<BackupRestoreOutcome> {
            restoreCalls++
            currentBackup = backup
            return Result.success(BackupRestoreOutcome.Complete)
        }

        override fun serializeBackup(backup: ExpenseTrackerBackup): String = adapter.toJson(backup)

        override fun deserializeBackup(json: String): Result<ExpenseTrackerBackup> = runCatching {
            adapter.fromJson(json) ?: error("Null backup JSON")
        }
    }
}
