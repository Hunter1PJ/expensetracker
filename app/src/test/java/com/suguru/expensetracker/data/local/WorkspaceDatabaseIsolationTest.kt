package com.suguru.expensetracker.data.local

import android.app.Application
import android.content.Context
import java.io.File
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.local.entity.BudgetEntity
import com.suguru.expensetracker.data.local.entity.CategoryEntity
import com.suguru.expensetracker.data.local.entity.RecurringOccurrenceEntity
import com.suguru.expensetracker.data.local.entity.RecurringTransactionEntity
import com.suguru.expensetracker.data.local.entity.TransactionEntity
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringOccurrenceStatus
import com.suguru.expensetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class WorkspaceIsolationTestApplication : Application() {
    private val testDatabaseDirectory by lazy {
        File(System.getProperty("java.io.tmpdir"), "expense-db-${System.identityHashCode(this)}")
            .apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File = File(testDatabaseDirectory, name)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = WorkspaceIsolationTestApplication::class)
class WorkspaceDatabaseIsolationTest {
    private lateinit var context: Context
    private val guest = WorkspaceId.Guest
    private val accountA = WorkspaceId.FirebaseAccount("workspace-test-account-A")
    private val accountB = WorkspaceId.FirebaseAccount("workspace-test-account-B")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearDatabases()
        context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        clearDatabases()
        context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun guestAndFirebaseWorkspacesHaveIndependentDatabaseFiles() = runBlocking {
        assertNotEquals(accountA.databaseName, accountB.databaseName)
        assertFalse(accountA.databaseName.contains("workspace-test-account-A"))

        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        val aDb = ExpenseTrackerDatabaseProvider.getDatabase(context, accountA)
        val bDb = ExpenseTrackerDatabaseProvider.getDatabase(context, accountB)
        guestDb.accountDao().insertAccount(account("Guest", 1))
        aDb.accountDao().insertAccount(account("A", 1))
        bDb.accountDao().insertAccount(account("B", 1))

        assertEquals("Guest", guestDb.accountDao().getAccountById(1)?.name)
        assertEquals("A", aDb.accountDao().getAccountById(1)?.name)
        assertEquals("B", bDb.accountDao().getAccountById(1)?.name)
    }

    @Test
    fun legacyDatabaseIsCopiedToGuestWithoutChangingIdsOrRelationships() = runBlocking {
        val legacy = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, "expense_tracker.db")
            .addMigrations(
                ExpenseTrackerDatabase.MIGRATION_1_2,
                ExpenseTrackerDatabase.MIGRATION_2_3,
                ExpenseTrackerDatabase.MIGRATION_3_4
            ).build()
        insertRelationalFixture(legacy)
        legacy.close()

        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.MIGRATED_TO_GUEST,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        val migrated = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        assertEquals(41L, migrated.transactionDao().getTransactionById(99)?.accountId)
        assertEquals(7L, migrated.transactionDao().getTransactionById(99)?.categoryId)
        assertEquals(5L, migrated.transactionDao().getTransactionById(99)?.recurringRuleId)
        assertEquals(Instant.parse("2022-03-04T05:06:07Z"), migrated.transactionDao().getTransactionById(99)?.transactionTime)
        assertEquals(1, migrated.backupDao().getAllAccounts().size)
        assertEquals(1, migrated.backupDao().getAllCategories().size)
        assertEquals(1, migrated.backupDao().getAllBudgets().size)
        assertEquals(1, migrated.backupDao().getAllRecurringTransactions().size)
        assertEquals(1, migrated.backupDao().getAllRecurringOccurrences().size)
        assertTrue(LegacyWorkspaceDatabaseMigrator.isWorkspaceDatabaseReadable(context, guest))
        assertTrue(context.getDatabasePath("expense_tracker.db").exists())
        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.ALREADY_MIGRATED,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
    }

    @Test
    fun interruptedLegacyMigrationRecoversAfterGuestRename() = runBlocking {
        val legacy = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, "expense_tracker.db").build()
        legacy.accountDao().insertAccount(account("unassigned legacy", 2))
        legacy.close()
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        guestDb.accountDao().insertAccount(account("migrated guest", 1))
        ExpenseTrackerDatabaseProvider.closeDatabase(guest)
        context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .edit().putBoolean("legacy_guest_copy_in_progress", true).commit()

        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.ALREADY_MIGRATED,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        assertTrue(context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .getBoolean("legacy_guest_copy_complete", false))
        assertEquals("migrated guest", ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
            .accountDao().getAccountById(1)?.name)
        assertTrue(context.getDatabasePath("expense_tracker.db").exists())
        assertFalse(ExpenseTrackerDatabaseProvider.databaseExists(context, accountA))
    }

    @Test
    fun guestImportClonesFullRelationalDatabaseIdempotently() = runBlocking {
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        insertRelationalFixture(guestDb)
        ExpenseTrackerDatabaseProvider.closeDatabase(guest)

        assertTrue(LegacyWorkspaceDatabaseMigrator.cloneWorkspace(context, guest, accountA))
        assertFalse(LegacyWorkspaceDatabaseMigrator.cloneWorkspace(context, guest, accountA))

        val imported = ExpenseTrackerDatabaseProvider.getDatabase(context, accountA)
        assertEquals(41L, imported.transactionDao().getTransactionById(99)?.accountId)
        assertEquals(7L, imported.transactionDao().getTransactionById(99)?.categoryId)
        assertEquals(5L, imported.transactionDao().getTransactionById(99)?.recurringRuleId)
        assertEquals(1, imported.backupDao().getAllBudgets().size)
        assertEquals(1, imported.backupDao().getAllRecurringOccurrences().size)
        assertEquals("legacy", ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
            .accountDao().getAccountById(41)?.name)
    }

    @Test
    fun guestCollisionPreservesExistingGuestAndLegacyDatabases() = runBlocking {
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        guestDb.accountDao().insertAccount(account("existing guest", 1))
        val legacy = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, "expense_tracker.db").build()
        legacy.accountDao().insertAccount(account("unassigned legacy", 2))
        legacy.close()

        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.GUEST_COLLISION,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        assertEquals("existing guest", guestDb.accountDao().getAccountById(1)?.name)
        assertTrue(context.getDatabasePath("expense_tracker.db").exists())
        assertTrue(LegacyWorkspaceDatabaseMigrator.hasRecoverableCollision(context))
    }

    private suspend fun insertRelationalFixture(database: ExpenseTrackerDatabase) {
        database.accountDao().insertAccount(account("legacy", 41))
        database.categoryDao().insertCategory(
            CategoryEntity(7, "Food", CategoryType.EXPENSE, "food", "#123456", false, false)
        )
        database.recurringTransactionDao().insertRecurringTransaction(
            RecurringTransactionEntity(
                id = 5,
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 1234,
                currencyCode = "USD",
                accountId = 41,
                destinationAccountId = null,
                categoryId = 7,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.parse("2022-01-01"),
                endDate = null,
                nextOccurrence = LocalDate.parse("2022-04-01"),
                note = "monthly",
                isActive = true
            )
        )
        database.budgetDao().insertBudget(
            BudgetEntity(
                id = 6,
                categoryId = 7,
                limitAmountInMinorUnits = 50000,
                currencyCode = "USD",
                periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse("2022-03-01"),
                endDate = LocalDate.parse("2022-03-31"),
                isActive = true
            )
        )
        database.transactionDao().insertTransaction(
            TransactionEntity(
                id = 99,
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 1234,
                currencyCode = "USD",
                accountId = 41,
                destinationAccountId = null,
                categoryId = 7,
                transactionTime = Instant.parse("2022-03-04T05:06:07Z"),
                note = "legacy",
                recurringRuleId = 5,
                createdAt = Instant.parse("2022-03-04T05:06:07Z")
            )
        )
        database.recurringOccurrenceDao().insertOccurrence(
            RecurringOccurrenceEntity(
                id = 8,
                ruleId = 5,
                occurrenceDate = LocalDate.parse("2022-03-04"),
                generatedTransactionId = 99,
                status = RecurringOccurrenceStatus.GENERATED,
                processedAt = Instant.parse("2022-03-04T05:06:07Z")
            )
        )
    }

    private fun account(name: String, id: Long) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        initialBalanceAmountInMinorUnits = 0,
        initialBalanceCurrencyCode = "USD",
        iconName = "wallet",
        colorHex = "#123456",
        isArchived = false,
        createdAt = Instant.parse("2020-01-01T00:00:00Z"),
        internalKey = "internal_default"
    )

    private fun clearDatabases() {
        listOf(guest, accountA, accountB).forEach { workspace ->
            ExpenseTrackerDatabaseProvider.closeDatabase(workspace)
            context.deleteDatabase(workspace.databaseName)
        }
        context.deleteDatabase("expense_tracker.db")
        context.deleteDatabase("expense_tracker_guest_candidate.db")
        context.deleteDatabase("expense_tracker_workspace_candidate.db")
    }
}
