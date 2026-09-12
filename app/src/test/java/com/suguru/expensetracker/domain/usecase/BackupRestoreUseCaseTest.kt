package com.suguru.expensetracker.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = FakeSettingsRepository()
        backupRepository = RoomBackupRepository(database, settingsRepository)
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
        assertTrue(json.contains("\"formatVersion\":1"))

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
        assertEquals(1, accounts.size)
        assertEquals("Wallet", accounts.first().name)

        val transactions = transactionRepo.observeAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals(1250L, transactions.first().amount.amountInMinorUnits)
        assertEquals("Lunch", transactions.first().note)
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

        override suspend fun setThemeMode(value: ThemeMode) { _settings.update { it.copy(themeMode = value) } }
        override suspend fun setPreferredCurrency(value: String?) { _settings.update { it.copy(preferredCurrencyCode = value ?: "USD") } }
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
