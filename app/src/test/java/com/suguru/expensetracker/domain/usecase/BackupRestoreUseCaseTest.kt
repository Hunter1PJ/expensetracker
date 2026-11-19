package com.suguru.expensetracker.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.BuildConfig
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioner
import com.suguru.expensetracker.data.account.InternalDefaultAccountProvisioningResult
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.BudgetEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.RecurringOccurrenceEntity
import com.suguru.expensetracker.data.local.entity.RecurringTransactionEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
        assertEquals(2, backup.formatVersion)
        assertEquals(4, backup.databaseSchemaVersion)
        assertEquals(backup.checksum, backupRepository.computeChecksum(backup))
        assertEquals(1, backup.accounts.size)
        assertEquals(1, backup.categories.size)
        assertEquals(1, backup.transactions.size)
        assertEquals("USD", backup.accounts.first().initialBalanceCurrencyCode)

        // Serialize to JSON string
        val json = backupRepository.serializeBackup(backup)
        assertTrue(json.contains("\"formatVersion\":2"))
        assertTrue(json.contains("\"databaseSchemaVersion\":4"))

        // Deserialize back
        val parsedResult = backupRepository.deserializeBackup(json)
        assertTrue(parsedResult.isSuccess)
        val parsedBackup = parsedResult.getOrThrow()
        assertEquals(backup.checksum, parsedBackup.checksum)
        assertEquals(4, parsedBackup.databaseSchemaVersion)

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
    fun guardedCloudRestoreRejectsUnbackedLocalFinancialChanges() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(name = "Wallet", type = AccountType.CASH, initialBalance = Money(0L, "USD"))
        )
        transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(500L, "USD"),
                accountId = accountId,
                categoryId = null,
                transactionTime = Instant.parse("2026-10-01T10:00:00Z"),
                note = "Existing local expense"
            )
        )
        val cloudSnapshot = createFullBackupUseCase()
        val expectedFingerprint = BackupContentFingerprint.compute(cloudSnapshot)

        transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.INCOME,
                amount = Money(900L, "USD"),
                accountId = accountId,
                categoryId = null,
                transactionTime = Instant.parse("2026-10-02T10:00:00Z"),
                note = "Newer local income"
            )
        )

        val result = restoreBackupUseCase(cloudSnapshot, expectedFingerprint) { true }

        assertTrue(result.isFailure)
        val localTransactions = transactionRepo.observeAllTransactions().first()
        assertEquals(2, localTransactions.size)
        assertTrue(localTransactions.any { it.note == "Newer local income" })
    }

    @Test
    fun guardedCloudRestoreRollsBackWhenIdentityChangesBeforeRoomCommit() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(name = "Wallet", type = AccountType.CASH, initialBalance = Money(0L, "USD"))
        )
        transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(500L, "USD"),
                accountId = accountId,
                categoryId = null,
                transactionTime = Instant.parse("2026-10-01T10:00:00Z"),
                note = "Keep this expense"
            )
        )
        val cloudSnapshot = createFullBackupUseCase()
        val expectedFingerprint = BackupContentFingerprint.compute(cloudSnapshot)
        var identityChecks = 0

        val result = restoreBackupUseCase(cloudSnapshot, expectedFingerprint) {
            identityChecks++
            identityChecks == 1
        }

        assertTrue(result.isFailure)
        assertTrue(identityChecks >= 2)
        val localTransactions = transactionRepo.observeAllTransactions().first()
        assertEquals(1, localTransactions.size)
        assertEquals("Keep this expense", localTransactions.single().note)
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

    @Test
    fun testExportAndRestoreRoomV4WithRecurringRulesAndOccurrences() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(
                name = "Primary Checking",
                type = AccountType.BANK,
                initialBalance = Money(100_000L, "USD")
            )
        )
        val categoryId = categoryRepo.insertCategory(
            Category(
                name = "Utilities",
                type = CategoryType.EXPENSE,
                iconName = "bolt",
                colorHex = "#2563EB"
            )
        )
        val ruleId = database.recurringTransactionDao().insertRecurringTransaction(
            RecurringTransactionEntity(
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 4500L,
                currencyCode = "USD",
                accountId = accountId,
                destinationAccountId = null,
                categoryId = categoryId,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                nextOccurrence = LocalDate.of(2026, 2, 1),
                note = "Internet",
                isActive = true
            )
        )
        val txId = transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(4500L, "USD"),
                accountId = accountId,
                categoryId = categoryId,
                transactionTime = Instant.parse("2026-01-01T10:00:00Z"),
                note = "Internet",
                recurringRuleId = ruleId,
                recurringOccurrenceDate = LocalDate.of(2026, 1, 1)
            )
        )
        database.recurringOccurrenceDao().insertOccurrence(
            RecurringOccurrenceEntity(
                ruleId = ruleId,
                occurrenceDate = LocalDate.of(2026, 1, 1),
                generatedTransactionId = txId,
                status = RecurringOccurrenceStatus.GENERATED,
                processedAt = Instant.parse("2026-01-01T10:00:00Z")
            )
        )
        database.budgetDao().insertBudget(
            BudgetEntity(
                categoryId = categoryId,
                limitAmountInMinorUnits = 20_000L,
                currencyCode = "USD",
                periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 31),
                isActive = true
            )
        )

        val backup = createFullBackupUseCase()
        assertEquals(2, backup.formatVersion)
        assertEquals(4, backup.databaseSchemaVersion)
        assertEquals(backup.checksum, backupRepository.computeChecksum(backup))

        val json = backupRepository.serializeBackup(backup)
        assertTrue(json.contains("\"formatVersion\":2"))
        assertTrue(json.contains("\"databaseSchemaVersion\":4"))

        database.clearAllTables()
        assertEquals(0, database.backupDao().getAllAccounts().size)

        val parsed = backupRepository.deserializeBackup(json).getOrThrow()
        val restoreResult = restoreBackupUseCase(parsed)
        assertTrue(restoreResult.isSuccess)
        assertEquals(BackupRestoreOutcome.Complete, restoreResult.getOrThrow())

        assertEquals(1, database.backupDao().getAllCategories().size)
        assertEquals(1, database.backupDao().getAllTransactions().size)
        assertEquals(1, database.backupDao().getAllBudgets().size)
        assertEquals(1, database.backupDao().getAllRecurringTransactions().size)
        assertEquals(1, database.backupDao().getAllRecurringOccurrences().size)
        assertEquals("Internet", database.backupDao().getAllRecurringTransactions().single().note)
        assertEquals(txId, database.backupDao().getAllRecurringOccurrences().single().generatedTransactionId)
    }

    @Test
    fun testImportSupportsHistoricalSchemaVersions1Through4AndV1V2Formats() = runBlocking {
        val supportedCases = listOf(
            1 to 1,
            1 to 2,
            2 to 2,
            2 to 3,
            2 to 4
        )

        for ((formatVer, schemaVer) in supportedCases) {
            database.clearAllTables()
            val candidate = ExpenseTrackerBackup(
                formatVersion = formatVer,
                appVersion = "1.0.0",
                createdAt = "2026-01-15T00:00:00Z",
                databaseSchemaVersion = schemaVer,
                accounts = listOf(
                    BackupAccount(
                        id = 10L,
                        name = "Cash-$formatVer-$schemaVer",
                        type = "CASH",
                        initialBalanceAmountInMinorUnits = 5_000L,
                        initialBalanceCurrencyCode = "USD",
                        iconName = "wallet",
                        colorHex = "#10B981",
                        isArchived = false,
                        createdAt = "2026-01-01T00:00:00Z",
                        internalKey = if (formatVer == 2) "default" else null
                    )
                ),
                categories = listOf(
                    BackupCategory(
                        id = 20L,
                        name = "Groceries",
                        type = "EXPENSE",
                        iconName = "shopping_cart",
                        colorHex = "#22C55E",
                        isSystem = false,
                        isArchived = false
                    )
                ),
                transactions = listOf(
                    BackupTransaction(
                        id = 30L,
                        type = "EXPENSE",
                        amountInMinorUnits = 1_200L,
                        currencyCode = "USD",
                        accountId = 10L,
                        destinationAccountId = null,
                        categoryId = 20L,
                        transactionTime = "2026-01-10T12:00:00Z",
                        note = "Milk",
                        recurringRuleId = null,
                        recurringOccurrenceDate = null,
                        createdAt = "2026-01-10T12:00:00Z"
                    )
                ),
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
            val signed = candidate.copy(checksum = backupRepository.computeChecksum(candidate))
            val result = restoreBackupUseCase(signed)

            assertTrue("Expected restore success for formatVersion=$formatVer, schemaVersion=$schemaVer", result.isSuccess)
            val accounts = database.backupDao().getAllAccounts()
            assertTrue(accounts.any { it.name == "Cash-$formatVer-$schemaVer" })
            assertNotNull(database.accountDao().getInternalAccount("default"))
            val tx = database.backupDao().getAllTransactions().single()
            assertEquals("Milk", tx.note)
            assertEquals(1_200L, tx.amountInMinorUnits)
        }
    }

    @Test
    fun testUnsupportedVersionsAndInvalidChecksumDoNotMutateExistingRoomData() = runBlocking {
        val existingAccountId = accountRepo.insertAccount(
            Account(
                name = "Existing Safe Wallet",
                type = AccountType.CASH,
                initialBalance = Money(75_000L, "EUR")
            )
        )
        val existingCategoryId = categoryRepo.insertCategory(
            Category(
                name = "Existing Rent",
                type = CategoryType.EXPENSE,
                iconName = "home",
                colorHex = "#EF4444"
            )
        )
        transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(25_000L, "EUR"),
                accountId = existingAccountId,
                categoryId = existingCategoryId,
                transactionTime = Instant.parse("2026-02-01T09:00:00Z"),
                note = "Existing local tx"
            )
        )
        settingsRepository.setPreferredCurrency("EUR")

        val baseCandidate = ExpenseTrackerBackup(
            formatVersion = 2,
            appVersion = "1.0.0",
            createdAt = "2026-03-01T00:00:00Z",
            databaseSchemaVersion = 4,
            accounts = listOf(
                BackupAccount(
                    id = 99L,
                    name = "Incoming Replacement",
                    type = "BANK",
                    initialBalanceAmountInMinorUnits = 100L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "bank",
                    colorHex = "#000000",
                    isArchived = false,
                    createdAt = "2026-03-01T00:00:00Z",
                    internalKey = "default"
                )
            ),
            categories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
            recurringOccurrences = emptyList(),
            settings = BackupSettings(
                themeMode = "DARK",
                preferredCurrencyCode = "USD",
                weekStart = "SUNDAY",
                dateFormat = "SYSTEM_DEFAULT",
                timeFormat = "SYSTEM_DEFAULT",
                showCurrencyCode = false,
                confirmBeforeDelete = false
            ),
            checksum = null
        )

        val futureSchema5 = baseCandidate.copy(databaseSchemaVersion = 5).let {
            it.copy(checksum = backupRepository.computeChecksum(it))
        }
        val futureSchema99 = baseCandidate.copy(databaseSchemaVersion = 99).let {
            it.copy(checksum = backupRepository.computeChecksum(it))
        }
        val invalidSchema0 = baseCandidate.copy(databaseSchemaVersion = 0).let {
            it.copy(checksum = backupRepository.computeChecksum(it))
        }
        val unsupportedFormat3 = baseCandidate.copy(formatVersion = 3).let {
            it.copy(checksum = backupRepository.computeChecksum(it))
        }
        val invalidChecksum = baseCandidate.copy(checksum = "deadbeef_invalid_checksum")

        val invalidBackups = listOf(
            futureSchema5 to "schema version",
            futureSchema99 to "schema version",
            invalidSchema0 to "schema version",
            unsupportedFormat3 to "format version",
            invalidChecksum to "checksum"
        )

        for ((badBackup, expectedMessagePart) in invalidBackups) {
            val result = restoreBackupUseCase(badBackup)
            assertTrue("Expected failure for $expectedMessagePart", result.isFailure)
            assertTrue(
                "Expected error message containing '$expectedMessagePart' but got '${result.exceptionOrNull()?.message}'",
                result.exceptionOrNull()?.message?.contains(expectedMessagePart, ignoreCase = true) == true
            )

            // Verify existing Room data and settings were NOT mutated or cleared
            val accountsAfter = database.backupDao().getAllAccounts()
            assertEquals(1, accountsAfter.size)
            assertEquals("Existing Safe Wallet", accountsAfter.single().name)
            val txsAfter = database.backupDao().getAllTransactions()
            assertEquals(1, txsAfter.size)
            assertEquals("Existing local tx", txsAfter.single().note)
            val categoriesAfter = database.backupDao().getAllCategories()
            assertEquals(1, categoriesAfter.size)
            assertEquals("Existing Rent", categoriesAfter.single().name)
            assertFalse(backupRepository.hasPendingSettingsRecovery())
            assertEquals("EUR", settingsRepository.settings.first().preferredCurrencyCode)
        }
    }

    @Test
    fun testInvalidForeignKeysDoNotPartiallyRestoreOrMutateExistingRoomData() = runBlocking {
        val existingAccountId = accountRepo.insertAccount(
            Account(
                name = "Preserved Account",
                type = AccountType.BANK,
                initialBalance = Money(10_000L, "USD")
            )
        )
        val existingCategoryId = categoryRepo.insertCategory(
            Category(
                name = "Preserved Category",
                type = CategoryType.EXPENSE,
                iconName = "star",
                colorHex = "#F59E0B"
            )
        )
        transactionRepo.insertTransaction(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = Money(500L, "USD"),
                accountId = existingAccountId,
                categoryId = existingCategoryId,
                transactionTime = Instant.parse("2026-02-10T10:00:00Z"),
                note = "Preserved Transaction"
            )
        )

        val validAccount = BackupAccount(
            id = 50L,
            name = "Valid Incoming",
            type = "CASH",
            initialBalanceAmountInMinorUnits = 1_000L,
            initialBalanceCurrencyCode = "USD",
            iconName = "wallet",
            colorHex = "#10B981",
            isArchived = false,
            createdAt = "2026-02-15T00:00:00Z",
            internalKey = "default"
        )
        val validCategory = BackupCategory(
            id = 60L,
            name = "Valid Incoming Cat",
            type = "EXPENSE",
            iconName = "tag",
            colorHex = "#10B981",
            isSystem = false,
            isArchived = false
        )
        val validRule = BackupRecurringTransaction(
            id = 70L,
            type = "EXPENSE",
            amountInMinorUnits = 1_000L,
            currencyCode = "USD",
            accountId = 50L,
            destinationAccountId = null,
            categoryId = 60L,
            frequency = "MONTHLY",
            startDate = "2026-02-01",
            endDate = null,
            nextOccurrence = "2026-03-01",
            note = "Rule",
            isActive = true
        )

        val base = ExpenseTrackerBackup(
            formatVersion = 2,
            appVersion = "1.0.0",
            createdAt = "2026-02-15T00:00:00Z",
            databaseSchemaVersion = 4,
            accounts = listOf(validAccount),
            categories = listOf(validCategory),
            transactions = emptyList(),
            budgets = emptyList(),
            recurringTransactions = listOf(validRule),
            recurringOccurrences = emptyList(),
            settings = null,
            checksum = null
        )

        val brokenTxCategory = base.copy(
            transactions = listOf(
                BackupTransaction(
                    id = 80L,
                    type = "EXPENSE",
                    amountInMinorUnits = 200L,
                    currencyCode = "USD",
                    accountId = 50L,
                    destinationAccountId = null,
                    categoryId = 9999L,
                    transactionTime = "2026-02-15T00:00:00Z",
                    note = "Bad category FK",
                    recurringRuleId = null,
                    recurringOccurrenceDate = null,
                    createdAt = "2026-02-15T00:00:00Z"
                )
            )
        )
        val brokenOccurrenceRule = base.copy(
            recurringOccurrences = listOf(
                BackupRecurringOccurrence(
                    id = 90L,
                    ruleId = 8888L,
                    occurrenceDate = "2026-02-01",
                    generatedTransactionId = null,
                    status = "GENERATED",
                    processedAt = "2026-02-01T00:00:00Z"
                )
            )
        )
        val brokenBudgetCategory = base.copy(
            budgets = listOf(
                BackupBudget(
                    id = 95L,
                    categoryId = 7777L,
                    limitAmountInMinorUnits = 5_000L,
                    currencyCode = "USD",
                    periodType = "MONTHLY",
                    startDate = "2026-02-01",
                    endDate = "2026-02-28",
                    isActive = true
                )
            )
        )

        for (candidate in listOf(brokenTxCategory, brokenOccurrenceRule, brokenBudgetCategory)) {
            val signed = candidate.copy(checksum = backupRepository.computeChecksum(candidate))
            val result = restoreBackupUseCase(signed)
            assertTrue(result.isFailure)

            // Confirm NO partial restore occurred and original data remains intact
            val accountsAfter = database.backupDao().getAllAccounts()
            assertEquals(1, accountsAfter.size)
            assertEquals("Preserved Account", accountsAfter.single().name)
            val categoriesAfter = database.backupDao().getAllCategories()
            assertEquals(1, categoriesAfter.size)
            assertEquals("Preserved Category", categoriesAfter.single().name)
            val txsAfter = database.backupDao().getAllTransactions()
            assertEquals(1, txsAfter.size)
            assertEquals("Preserved Transaction", txsAfter.single().note)
            assertTrue(database.backupDao().getAllRecurringTransactions().isEmpty())
            assertTrue(database.backupDao().getAllRecurringOccurrences().isEmpty())
            assertTrue(database.backupDao().getAllBudgets().isEmpty())
        }
    }

    @Test
    fun testActualRoomDatabaseVersionMatchesBackupSchemaVersion() = runBlocking {
        val openedDatabaseVersion = database.openHelper.readableDatabase.version
        val snapshot = createFullBackupUseCase()

        assertEquals(RoomBackupRepository.CURRENT_DATABASE_SCHEMA_VERSION, openedDatabaseVersion)
        assertEquals(openedDatabaseVersion, snapshot.databaseSchemaVersion)
    }

    @Test
    fun testCreateSnapshotUsesBuildConfigAppVersionByDefault() = runBlocking {
        val snapshot = createFullBackupUseCase()

        assertEquals(BuildConfig.VERSION_NAME, snapshot.appVersion)
        assertNotEquals("1.0.0", snapshot.appVersion)
        assertEquals(snapshot.checksum, backupRepository.computeChecksum(snapshot))
        assertTrue(restoreBackupUseCase(snapshot).isSuccess)
    }

    @Test
    fun testCreateSnapshotUsesInjectedAppVersionAndPreservesChecksumValidation() = runBlocking {
        val injectedVersion = "2.4.0-test"
        val customVersionRepository = RoomBackupRepository(
            db = database,
            settingsRepository = settingsRepository,
            internalDefaultAccountProvisioner = InternalDefaultAccountProvisioner(database, settingsRepository),
            appVersion = injectedVersion
        )

        val snapshot = customVersionRepository.createSnapshot()

        assertEquals(injectedVersion, snapshot.appVersion)
        assertEquals(snapshot.checksum, customVersionRepository.computeChecksum(snapshot))

        val tamperedVersion = snapshot.copy(appVersion = "1.0.0")
        val tamperedRestore = customVersionRepository.restoreSnapshot(tamperedVersion)
        assertTrue(tamperedRestore.isFailure)
        assertTrue(tamperedRestore.exceptionOrNull()?.message?.contains("checksum") == true)

        assertTrue(customVersionRepository.restoreSnapshot(snapshot).isSuccess)
    }

    @Test
    fun testBackupDaoAndSnapshotReturnAllSixCollectionsInDeterministicIdOrder() = runBlocking {
        val now = Instant.parse("2026-03-01T12:00:00Z")
        val accountIds = listOf(30L, 10L, 20L)
        database.backupDao().insertAccounts(
            accountIds.map { id ->
                AccountEntity(
                    id = id,
                    name = "Account-$id",
                    type = AccountType.BANK,
                    initialBalanceAmountInMinorUnits = id * 100L,
                    initialBalanceCurrencyCode = "USD",
                    iconName = "bank",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = now,
                    internalKey = if (id == 10L) "default" else null
                )
            }
        )

        val categoryIds = listOf(33L, 11L, 22L)
        database.backupDao().insertCategories(
            categoryIds.map { id ->
                CategoryEntity(
                    id = id,
                    name = "Category-$id",
                    type = CategoryType.EXPENSE,
                    iconName = "tag",
                    colorHex = "#EF4444",
                    isSystem = false,
                    isArchived = false
                )
            }
        )

        val ruleIds = listOf(300L, 100L, 200L)
        database.backupDao().insertRecurringTransactions(
            ruleIds.map { id ->
                RecurringTransactionEntity(
                    id = id,
                    type = TransactionType.EXPENSE,
                    amountInMinorUnits = id * 10L,
                    currencyCode = "USD",
                    accountId = 10L,
                    destinationAccountId = null,
                    categoryId = 11L,
                    frequency = RecurrenceFrequency.MONTHLY,
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = null,
                    nextOccurrence = LocalDate.of(2026, 4, 1),
                    note = "Rule-$id",
                    isActive = true
                )
            }
        )

        val txIds = listOf(3000L, 1000L, 2000L)
        database.backupDao().insertTransactions(
            txIds.map { id ->
                TransactionEntity(
                    id = id,
                    type = TransactionType.EXPENSE,
                    amountInMinorUnits = id,
                    currencyCode = "USD",
                    accountId = 20L,
                    destinationAccountId = null,
                    categoryId = 22L,
                    transactionTime = now,
                    note = "Tx-$id",
                    recurringRuleId = 100L,
                    recurringOccurrenceDate = LocalDate.of(2026, 3, 1),
                    createdAt = now
                )
            }
        )

        val occurrenceIds = listOf(30000L, 10000L, 20000L)
        database.backupDao().insertRecurringOccurrences(
            occurrenceIds.mapIndexed { index, id ->
                RecurringOccurrenceEntity(
                    id = id,
                    ruleId = 100L,
                    occurrenceDate = LocalDate.of(2026, 3, index + 1),
                    generatedTransactionId = 1000L,
                    status = RecurringOccurrenceStatus.GENERATED,
                    processedAt = now
                )
            }
        )

        val budgetIds = listOf(300000L, 100000L, 200000L)
        database.backupDao().insertBudgets(
            budgetIds.map { id ->
                BudgetEntity(
                    id = id,
                    categoryId = 33L,
                    limitAmountInMinorUnits = id,
                    currencyCode = "USD",
                    periodType = BudgetPeriodType.MONTHLY,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 3, 31),
                    isActive = true
                )
            }
        )

        val daoAccounts = database.backupDao().getAllAccounts()
        val daoCategories = database.backupDao().getAllCategories()
        val daoTransactions = database.backupDao().getAllTransactions()
        val daoBudgets = database.backupDao().getAllBudgets()
        val daoRules = database.backupDao().getAllRecurringTransactions()
        val daoOccurrences = database.backupDao().getAllRecurringOccurrences()

        assertEquals(listOf(10L, 20L, 30L), daoAccounts.map { it.id })
        assertEquals(listOf(11L, 22L, 33L), daoCategories.map { it.id })
        assertEquals(listOf(1000L, 2000L, 3000L), daoTransactions.map { it.id })
        assertEquals(listOf(100000L, 200000L, 300000L), daoBudgets.map { it.id })
        assertEquals(listOf(100L, 200L, 300L), daoRules.map { it.id })
        assertEquals(listOf(10000L, 20000L, 30000L), daoOccurrences.map { it.id })

        val snapshot = createFullBackupUseCase()
        assertEquals(listOf(10L, 20L, 30L), snapshot.accounts.map { it.id })
        assertEquals(listOf(11L, 22L, 33L), snapshot.categories.map { it.id })
        assertEquals(listOf(1000L, 2000L, 3000L), snapshot.transactions.map { it.id })
        assertEquals(listOf(100000L, 200000L, 300000L), snapshot.budgets.map { it.id })
        assertEquals(listOf(100L, 200L, 300L), snapshot.recurringTransactions.map { it.id })
        assertEquals(listOf(10000L, 20000L, 30000L), snapshot.recurringOccurrences.map { it.id })
        assertEquals("Account-10", snapshot.accounts.first().name)
        assertEquals("Tx-3000", snapshot.transactions.last().note)
        assertEquals(snapshot.checksum, backupRepository.computeChecksum(snapshot))
    }

    @Test
    fun testCreateSnapshotExecutesSixRoomReadsInOneTransactionAndReadsSettingsOutside() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recordedQueries = mutableListOf<Pair<String, Boolean>>()
        lateinit var trackedDb: ExpenseTrackerDatabase
        trackedDb = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                RoomDatabase.QueryCallback { sqlQuery, _ ->
                    if (sqlQuery.contains("ORDER BY id ASC")) {
                        recordedQueries += sqlQuery to trackedDb.inTransaction()
                    }
                },
                { runnable -> runnable.run() }
            )
            .build()

        try {
            var settingsReadInsideTransaction: Boolean? = null
            val transactionCheckingSettingsRepo = object : SettingsRepository by settingsRepository {
                override val settings: Flow<AppSettings> = flow {
                    settingsReadInsideTransaction = trackedDb.inTransaction()
                    emit(settingsRepository.settings.first())
                }
            }
            val trackedRepo = RoomBackupRepository(
                db = trackedDb,
                settingsRepository = transactionCheckingSettingsRepo,
                appVersion = "1.5.0-test"
            )

            val snapshot = trackedRepo.createSnapshot()

            assertEquals("1.5.0-test", snapshot.appVersion)
            assertEquals(6, recordedQueries.size)
            assertTrue(
                "Expected all 6 backup SELECT queries to run inside a Room transaction, got: $recordedQueries",
                recordedQueries.all { (_, inTx) -> inTx }
            )
            assertEquals(false, settingsReadInsideTransaction)
        } finally {
            trackedDb.close()
        }
    }

    @Test
    fun testConcurrentTransactionalMutationNeverProducesHalfWrittenSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diskDbFile = context.getDatabasePath("backup_concurrency_test_${System.nanoTime()}.db")
        diskDbFile.parentFile?.mkdirs()
        val concurrentDb = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, diskDbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        try {
            val concurrentRepo = RoomBackupRepository(
                db = concurrentDb,
                settingsRepository = settingsRepository,
                appVersion = BuildConfig.VERSION_NAME
            )
            val now = Instant.parse("2026-04-01T00:00:00Z")
            val writerJob = async(Dispatchers.IO) {
                for (i in 1L..25L) {
                    concurrentDb.withTransaction {
                        concurrentDb.backupDao().insertAccounts(
                            listOf(
                                AccountEntity(
                                    id = i,
                                    name = "Concurrent-$i",
                                    type = AccountType.CASH,
                                    initialBalanceAmountInMinorUnits = 100L * i,
                                    initialBalanceCurrencyCode = "USD",
                                    iconName = "wallet",
                                    colorHex = "#10B981",
                                    isArchived = false,
                                    createdAt = now,
                                    internalKey = if (i == 1L) "default" else null
                                )
                            )
                        )
                        concurrentDb.backupDao().insertTransactions(
                            listOf(
                                TransactionEntity(
                                    id = i,
                                    type = TransactionType.EXPENSE,
                                    amountInMinorUnits = 50L,
                                    currencyCode = "USD",
                                    accountId = i,
                                    destinationAccountId = null,
                                    categoryId = null,
                                    transactionTime = now,
                                    note = "Tx-$i",
                                    recurringRuleId = null,
                                    recurringOccurrenceDate = null,
                                    createdAt = now
                                )
                            )
                        )
                    }
                }
            }

            repeat(15) {
                val snapshot = concurrentRepo.createSnapshot()
                assertEquals(snapshot.accounts.size, snapshot.transactions.size)
                assertEquals(snapshot.accounts.map { it.id }, snapshot.transactions.map { it.accountId })
            }
            writerJob.await()
        } finally {
            concurrentDb.close()
            diskDbFile.delete()
            java.io.File("${diskDbFile.absolutePath}-wal").delete()
            java.io.File("${diskDbFile.absolutePath}-shm").delete()
        }
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
