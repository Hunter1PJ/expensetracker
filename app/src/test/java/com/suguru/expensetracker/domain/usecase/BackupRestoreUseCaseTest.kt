package com.suguru.expensetracker.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.repository.RoomBackupRepository
import com.suguru.expensetracker.data.repository.RoomAccountRepository
import com.suguru.expensetracker.data.repository.RoomCategoryRepository
import com.suguru.expensetracker.data.repository.RoomTransactionRepository
import com.suguru.expensetracker.domain.model.*
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.suguru.expensetracker.domain.usecase.backup.CreateFullBackupUseCase
import com.suguru.expensetracker.domain.usecase.backup.RestoreBackupUseCase
import com.suguru.expensetracker.domain.usecase.transaction.ExportTransactionsToCsvUseCase
import com.suguru.expensetracker.presentation.startup.StartupProvisioningState
import com.suguru.expensetracker.presentation.startup.allowsNavigation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreUseCaseTest {

    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var backupRepository: RoomBackupRepository
    private lateinit var createFullBackupUseCase: CreateFullBackupUseCase
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var exportTransactionsToCsvUseCase: ExportTransactionsToCsvUseCase

    private lateinit var accountRepo: RoomAccountRepository
    private lateinit var categoryRepo: RoomCategoryRepository
    private lateinit var transactionRepo: RoomTransactionRepository
    private var restoredProvisioningResult: InternalDefaultAccountProvisioningResult? = null
    private val startupState = MutableStateFlow<StartupProvisioningState>(StartupProvisioningState.Loading)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = FakeSettingsRepository()
        restoredProvisioningResult = null
        startupState.value = StartupProvisioningState.Loading
        backupRepository = RoomBackupRepository(
            database,
            settingsRepository,
            InternalDefaultAccountProvisioner(database, settingsRepository),
            onProvisioningResult = {
                restoredProvisioningResult = it
                startupState.value = StartupProvisioningState.fromResult(it)
            },
            onSettingsRecoveryPending = { startupState.value = StartupProvisioningState.SettingsRecoveryPending }
        )
        createFullBackupUseCase = CreateFullBackupUseCase(backupRepository)
        restoreBackupUseCase = RestoreBackupUseCase(backupRepository)

        accountRepo = RoomAccountRepository(database.accountDao())
        categoryRepo = RoomCategoryRepository(database.categoryDao())
        transactionRepo = RoomTransactionRepository(database.transactionDao())
        
        exportTransactionsToCsvUseCase = ExportTransactionsToCsvUseCase(transactionRepo, accountRepo, categoryRepo)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBackupAndRestoreRoundTrip() = runBlocking {
        // 1. Seed database with some initial valid data using domain repos
        val accountId = accountRepo.insertAccount(
            Account(
                name = "Wallet",
                type = AccountType.CASH,
                initialBalance = Money(50000L, "USD")
            )
        )
        val categoryId = categoryRepo.insertCategory(
            Category(
                name = "Food",
                type = CategoryType.EXPENSE,
                iconName = "restaurant",
                colorHex = "#FF0000"
            )
        )
        val txId = transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(1250L, "USD"),
                accountId = accountId,
                categoryId = categoryId,
                transactionTime = Instant.now(),
                note = "Lunch"
            )
        )

        // 2. Perform backup
        val backup = createFullBackupUseCase()
        assertNotNull(backup)
        assertEquals(1, backup.accounts.size)
        assertEquals(1, backup.categories.size)
        assertEquals(1, backup.transactions.size)
        assertEquals("USD", backup.accounts.first().initialBalanceCurrencyCode)

        // Serialize to JSON string
        val json = backupRepository.serializeBackup(backup)
        assertTrue(json.contains("\"formatVersion\":2"))

        // Deserialize back
        val parsedResult = backupRepository.deserializeBackup(json)
        assertTrue(parsedResult.isSuccess)
        val parsedBackup = parsedResult.getOrThrow()
        assertEquals(backup.checksum, parsedBackup.checksum)

        // 3. Clear/restore database from backup
        val restoreResult = restoreBackupUseCase(parsedBackup)
        assertTrue(restoreResult.isSuccess)

        // 4. Verify data in DB matches backup exactly
        val accounts = accountRepo.observeAllAccounts().first()
        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.name == "Wallet" })
        assertNotNull(database.accountDao().getInternalAccount("default"))

        val transactions = transactionRepo.observeAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals(1250L, transactions.first().amount.amountInMinorUnits)
        assertEquals("Lunch", transactions.first().note)
    }

    @Test
    fun testRestoreWithMultipleAccountsAndNoPreferenceIsRecoverable() = runBlocking {
        settingsRepository.setPreferredCurrency(null)
        accountRepo.insertAccount(
            Account(
                id = 101L,
                name = "Checking",
                type = AccountType.BANK,
                initialBalance = Money(1000L, "USD")
            )
        )
        accountRepo.insertAccount(
            Account(
                id = 102L,
                name = "Savings",
                type = AccountType.SAVINGS,
                initialBalance = Money(2500L, "EUR")
            )
        )

        val backup = createFullBackupUseCase()
        val restoreResult = backupRepository.restoreSnapshot(backup)

        assertTrue(restoreResult.isSuccess)
        assertTrue(restoredProvisioningResult is InternalDefaultAccountProvisioningResult.RequiresCurrencySelection)
        assertEquals(2, database.accountDao().getActiveAccounts().size)
        assertNull(database.accountDao().getInternalAccount("default"))
        assertNull(settingsRepository.settings.first().preferredCurrencyCode)
    }

    @Test
    fun testV2BackupRoundTripPreservesInternalAccountIdentity() = runBlocking {
        val backup = ExpenseTrackerBackup(
            formatVersion = 2,
            appVersion = "1.0.0",
            createdAt = Instant.now().toString(),
            databaseSchemaVersion = 3,
            accounts = listOf(
                BackupAccount(
                    id = 10L,
                    name = "Internal Default",
                    type = "CASH",
                    initialBalanceAmountInMinorUnits = 0L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "account_balance",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = Instant.now().toString(),
                    internalKey = "default"
                ),
                BackupAccount(
                    id = 11L,
                    name = "Checking",
                    type = "BANK",
                    initialBalanceAmountInMinorUnits = 12500L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "wallet",
                    colorHex = "#111111",
                    isArchived = false,
                    createdAt = Instant.now().toString(),
                    internalKey = null
                )
            ),
            categories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
            recurringOccurrences = emptyList(),
            settings = BackupSettings(
                themeMode = "SYSTEM",
                preferredCurrencyCode = "USD",
                weekStart = "MONDAY",
                dateFormat = "SYSTEM_DEFAULT",
                timeFormat = "SYSTEM_DEFAULT",
                showCurrencyCode = true,
                confirmBeforeDelete = true
            ),
            checksum = null
        )

        val finalized = backup.copy(checksum = backupRepository.computeChecksum(backup))
        val roundTrip = backupRepository.deserializeBackup(backupRepository.serializeBackup(finalized))
        assertTrue(roundTrip.isSuccess)
        assertEquals("default", roundTrip.getOrThrow().accounts.first().internalKey)
        assertEquals(2, roundTrip.getOrThrow().accounts.size)
    }

    @Test
    fun testV1FixtureChecksumValidatesAndRestoresThroughProductionPath() = runBlocking {
        val json = this::class.java.classLoader?.getResource("backup_v1_valid.json")?.readText() ?: error("Fixture missing")
        val parsed = backupRepository.deserializeBackup(json)
        assertTrue(parsed.isSuccess)
        val backup = parsed.getOrThrow()
        assertEquals(1, backup.formatVersion)
        assertEquals("6123899671ff51b83724e049c16ad8561038b0167172213f057c554a2262f7ea", backup.checksum)
        assertEquals(backup.checksum, backupRepository.computeChecksum(backup))
        assertTrue(backup.accounts.all { it.internalKey == null })
        assertEquals("ABC", backup.accounts.first().initialBalanceCurrencyCode)
        assertEquals("Checking", backup.accounts.first().name)

        val restoreResult = restoreBackupUseCase(backup)
        assertTrue(restoreResult.isSuccess)
        assertEquals(BackupRestoreOutcome.Complete, restoreResult.getOrThrow())
        val restoredAccounts = database.backupDao().getAllAccounts()
        val checking = restoredAccounts.single { it.name == "Checking" }
        assertEquals(12_500L, checking.initialBalanceAmountInMinorUnits)
        assertNull(checking.internalKey)
        assertEquals("default", database.accountDao().getInternalAccount("default")?.internalKey)
        assertNotEquals(checking.id, database.accountDao().getInternalAccount("default")?.id)
        assertTrue(database.backupDao().getAllTransactions().isEmpty())
        assertTrue(database.backupDao().getAllCategories().isEmpty())
        assertTrue(database.backupDao().getAllBudgets().isEmpty())
        assertTrue(database.backupDao().getAllRecurringTransactions().isEmpty())
        assertTrue(database.backupDao().getAllRecurringOccurrences().isEmpty())
    }

    @Test
    fun testV1FixtureFormatRestoresFinancialRecordsAndRelationships() = runBlocking {
        val json = this::class.java.classLoader?.getResource("backup_v1_valid.json")?.readText() ?: error("Fixture missing")
        val fixture = backupRepository.deserializeBackup(json).getOrThrow()
        val category = BackupCategory(
            id = 7L,
            name = "Food",
            type = "EXPENSE",
            iconName = "restaurant",
            colorHex = "#112233",
            isSystem = false,
            isArchived = false
        )
        val transaction = BackupTransaction(
            id = 9L,
            type = "EXPENSE",
            amountInMinorUnits = 350L,
            currencyCode = "ABC",
            accountId = 1L,
            destinationAccountId = null,
            categoryId = category.id,
            transactionTime = "2024-01-02T12:00:00Z",
            note = "Lunch",
            recurringRuleId = null,
            recurringOccurrenceDate = null,
            createdAt = "2024-01-02T12:00:00Z"
        )
        val v1WithRecords = fixture.copy(categories = listOf(category), transactions = listOf(transaction), checksum = null)
        val checksummedV1 = v1WithRecords.copy(checksum = backupRepository.computeChecksum(v1WithRecords))

        val restoreResult = restoreBackupUseCase(checksummedV1)

        assertTrue(restoreResult.isSuccess)
        val restoredTransaction = database.backupDao().getAllTransactions().single()
        assertEquals(350L, restoredTransaction.amountInMinorUnits)
        assertEquals(1L, restoredTransaction.accountId)
        assertEquals(category.id, restoredTransaction.categoryId)
        assertEquals("Food", database.backupDao().getAllCategories().single().name)
        val restoredChecking = database.backupDao().getAllAccounts().single { it.id == 1L }
        assertNull(restoredChecking.internalKey)
        assertEquals("default", database.accountDao().getInternalAccount("default")?.internalKey)
        assertNotEquals(restoredChecking.id, database.accountDao().getInternalAccount("default")?.id)
    }

    @Test
    fun testModifiedV1FixturePayloadWithOriginalChecksumIsRejected() = runBlocking {
        val json = this::class.java.classLoader?.getResource("backup_v1_valid.json")?.readText() ?: error("Fixture missing")
        val backup = backupRepository.deserializeBackup(json).getOrThrow()
        val tampered = backup.copy(accounts = backup.accounts.map { it.copy(name = "Tampered Checking") })

        val restoreResult = restoreBackupUseCase(tampered)

        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("checksum") == true)
        assertEquals(0, database.accountDao().getAccountCount())
    }

    @Test
    fun testPendingSettingsRecoveryImmediatelyBlocksNavigationAndRetriesWithoutReimport() = runBlocking {
        settingsRepository.restoreSettingsFailures = 1
        var stateDuringSettingsRestore: StartupProvisioningState? = null
        settingsRepository.beforeRestoreSettings = { stateDuringSettingsRestore = startupState.value }
        val backup = createFullBackupUseCase()

        val restoreResult = backupRepository.restoreSnapshot(backup)

        assertEquals(BackupRestoreOutcome.SettingsRecoveryPending, restoreResult.getOrThrow())
        assertEquals(StartupProvisioningState.SettingsRecoveryPending, stateDuringSettingsRestore)
        assertFalse(startupState.value.allowsNavigation)
        assertTrue(backupRepository.hasPendingSettingsRecovery())

        val sentinelId = accountRepo.insertAccount(Account(name = "Keep after recovery", type = AccountType.BANK, initialBalance = Money(100L, "USD")))
        val recoveryResult = backupRepository.ensureProvisionedWithRecovery()

        assertTrue(recoveryResult is InternalDefaultAccountProvisioningResult.Ready)
        assertEquals(StartupProvisioningState.Ready, startupState.value)
        assertFalse(backupRepository.hasPendingSettingsRecovery())
        assertEquals("Keep after recovery", database.accountDao().getAccountById(sentinelId)?.name)
        assertEquals(2, settingsRepository.restoreSettingsCalls)
        assertEquals(StartupProvisioningState.Ready, backupRepository.ensureProvisionedWithRecovery()?.let { StartupProvisioningState.fromResult(it) })
        assertEquals(2, settingsRepository.restoreSettingsCalls)
    }

    @Test
    fun testFailedRecoveryKeepsGateBlockedAndJournalForRetry() = runBlocking {
        settingsRepository.restoreSettingsFailures = Int.MAX_VALUE
        val backup = createFullBackupUseCase()

        val restoreResult = backupRepository.restoreSnapshot(backup)
        val retryResult = backupRepository.ensureProvisionedWithRecovery()

        assertEquals(BackupRestoreOutcome.SettingsRecoveryPending, restoreResult.getOrThrow())
        assertTrue(retryResult is InternalDefaultAccountProvisioningResult.Failure)
        assertFalse(startupState.value.allowsNavigation)
        assertTrue(backupRepository.hasPendingSettingsRecovery())
    }

    @Test
    fun testConcurrentRecoveryDoesNotPublishReadyBeforeSettingsFinish() = runBlocking {
        settingsRepository.restoreSettingsFailures = 1
        val backup = createFullBackupUseCase()
        assertEquals(BackupRestoreOutcome.SettingsRecoveryPending, backupRepository.restoreSnapshot(backup).getOrThrow())
        settingsRepository.restoreSettingsFailures = 0
        val restoreStarted = CompletableDeferred<Unit>()
        val finishRestore = CompletableDeferred<Unit>()
        settingsRepository.beforeRestoreSettings = {
            if (settingsRepository.restoreSettingsCalls == 2) {
                restoreStarted.complete(Unit)
                finishRestore.await()
            }
        }

        val firstRecovery = async { backupRepository.ensureProvisionedWithRecovery() }
        restoreStarted.await()
        val secondRecovery = async { backupRepository.ensureProvisionedWithRecovery() }
        assertFalse(startupState.value.allowsNavigation)
        finishRestore.complete(Unit)

        assertTrue(firstRecovery.await() is InternalDefaultAccountProvisioningResult.Ready)
        assertTrue(secondRecovery.await() is InternalDefaultAccountProvisioningResult.Ready)
        assertEquals(StartupProvisioningState.Ready, startupState.value)
        assertEquals(2, settingsRepository.restoreSettingsCalls)
        assertFalse(backupRepository.hasPendingSettingsRecovery())
    }

    @Test
    fun testDuplicateDefaultInternalMarkersRejectedBeforeDatabaseMutation() = runBlocking {
        val backup = createFullBackupUseCase().copy(
            accounts = listOf(
                BackupAccount(
                    id = 100L,
                    name = "Internal Default",
                    type = "CASH",
                    initialBalanceAmountInMinorUnits = 0L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "account_balance",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = Instant.now().toString(),
                    internalKey = "default"
                ),
                BackupAccount(
                    id = 101L,
                    name = "Another Internal",
                    type = "CASH",
                    initialBalanceAmountInMinorUnits = 0L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "account_balance",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = Instant.now().toString(),
                    internalKey = "default"
                )
            ),
            checksum = null
        )

        val finalBackup = backup.copy(checksum = backupRepository.computeChecksum(backup))
        val restoreResult = restoreBackupUseCase(finalBackup)
        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("default", ignoreCase = true) == true)
        assertEquals(0, database.accountDao().getAccountCount())
    }

    @Test
    fun testCorruptChecksumValidationFails() = runBlocking {
        // Perform backup and tamper with it
        val backup = createFullBackupUseCase()
        val tampered = backup.copy(checksum = "badchecksum12345")

        val restoreResult = restoreBackupUseCase(tampered)
        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("checksum") == true)
    }

    @Test
    fun testIncompatibleDatabaseSchemaVersionFails() = runBlocking {
        val backup = createFullBackupUseCase()
        val tampered = backup.copy(databaseSchemaVersion = 99) // Way in the future

        // Generates correct checksum for tampered object so schema version is the only error
        val finalTampered = tampered.copy(checksum = backupRepository.computeChecksum(tampered))

        val restoreResult = restoreBackupUseCase(finalTampered)
        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("schema version") == true)
    }

    @Test
    fun testInvalidCurrencyCodesValidationFails() = runBlocking {
        val backup = createFullBackupUseCase()
        // Account has an invalid currency code "US" (must be exactly 3 characters)
        val badAccount = backup.accounts.firstOrNull()?.copy(initialBalanceCurrencyCode = "US") ?: return@runBlocking
        val tampered = backup.copy(accounts = listOf(badAccount))
        val finalTampered = tampered.copy(checksum = backupRepository.computeChecksum(tampered))

        val restoreResult = restoreBackupUseCase(finalTampered)
        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("currency") == true)
    }

    @Test
    fun testBrokenForeignKeysValidationFails() = runBlocking {
        // Seeding database
        accountRepo.insertAccount(
            Account(
                name = "Savings",
                type = AccountType.BANK,
                initialBalance = Money(100L, "USD")
            )
        )

        val backup = createFullBackupUseCase()
        // Create an orphan transaction referencing non-existent account ID 999
        val orphanTx = BackupTransaction(
            id = 55L, type = "INCOME", amountInMinorUnits = 1000L, currencyCode = "USD",
            accountId = 999L, destinationAccountId = null, categoryId = null, transactionTime = Instant.now().toString(),
            note = "Mystery money", recurringRuleId = null, recurringOccurrenceDate = null,
            createdAt = Instant.now().toString()
        )
        val tampered = backup.copy(transactions = listOf(orphanTx))
        val finalTampered = tampered.copy(checksum = backupRepository.computeChecksum(tampered))

        val restoreResult = restoreBackupUseCase(finalTampered)
        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull()?.message?.contains("references non-existent account") == true)
    }

    @Test
    fun testExportCsvFormatAndEscaping() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(
                name = "Debit Card",
                type = AccountType.BANK,
                initialBalance = Money(0L, "USD")
            )
        )
        val categoryId = categoryRepo.insertCategory(
            Category(
                name = "Shopping",
                type = CategoryType.EXPENSE,
                iconName = "shop",
                colorHex = "#00FF00"
            )
        )
        // Add a transaction containing commas and quotes to test CSV escaping
        transactionRepo.insertTransaction(
            Transaction(
                id = 12L,
                type = TransactionType.EXPENSE,
                amount = Money(5500L, "USD"),
                accountId = accountId,
                categoryId = categoryId,
                transactionTime = Instant.parse("2026-09-02T12:00:00Z"),
                note = "Bought \"cool, fancy\" jacket"
            )
        )

        val csv = exportTransactionsToCsvUseCase()
        assertNotNull(csv)

        // Verify Header is correct
        assertTrue(csv.startsWith("ID,Date,Type,Amount Minor Units,Currency,Account,Destination Account,Category,Note,Recurring Rule ID,Recurring Occurrence Date"))

        // Verify CSV escaping of notes with quotes and commas
        // "Bought \"cool, fancy\" jacket" -> "Bought ""cool, fancy"" jacket" (and double quoted because of commas/quotes!)
        val expectedPart = "\"Bought \"\"cool, fancy\"\" jacket\""
        assertTrue("Expected escaped note part in CSV but was:\n$csv", csv.contains(expectedPart))
        assertTrue(csv.contains("Debit Card"))
        assertTrue(csv.contains("Shopping"))
    }

    // Helper fake implementation of settings
    class FakeSettingsRepository : SettingsRepository {
        var restoreSettingsFailures = 0
        var restoreSettingsCalls = 0
        var beforeRestoreSettings: suspend () -> Unit = {}
        private val _settings = MutableStateFlow(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                preferredCurrencyCode = "USD",
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
                timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
                showCurrencyCode = true,
                confirmBeforeDelete = true
            )
        )
        override val settings: Flow<AppSettings> = _settings
        private val _lastBackupAt = MutableStateFlow<String?>(null)
        override val lastBackupAt: Flow<String?> = _lastBackupAt
        private val _lastBackupFileName = MutableStateFlow<String?>(null)
        override val lastBackupFileName: Flow<String?> = _lastBackupFileName

        override suspend fun restoreSettings(value: AppSettings) {
            restoreSettingsCalls += 1
            beforeRestoreSettings()
            if (restoreSettingsFailures > 0) {
                restoreSettingsFailures -= 1
                throw IllegalStateException("Settings restore failed")
            }
            _settings.value = value
        }

        override suspend fun setThemeMode(value: ThemeMode) { _settings.update { it.copy(themeMode = value) } }
        override suspend fun setPreferredCurrency(value: String?) { _settings.update { it.copy(preferredCurrencyCode = value) } }
        override suspend fun setWeekStart(value: WeekStart) { _settings.update { it.copy(weekStart = value) } }
        override suspend fun setDateFormat(value: DateFormatPreference) { _settings.update { it.copy(dateFormat = value) } }
        override suspend fun setTimeFormat(value: TimeFormatPreference) { _settings.update { it.copy(timeFormat = value) } }
        override suspend fun setShowCurrencyCode(value: Boolean) { _settings.update { it.copy(showCurrencyCode = value) } }
        override suspend fun setConfirmBeforeDelete(value: Boolean) { _settings.update { it.copy(confirmBeforeDelete = value) } }
        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) {
            _lastBackupAt.value = at
            _lastBackupFileName.value = fileName
        }
    }
}
