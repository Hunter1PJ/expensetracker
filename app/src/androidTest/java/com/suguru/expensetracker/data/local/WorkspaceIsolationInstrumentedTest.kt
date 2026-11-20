package com.suguru.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

@RunWith(AndroidJUnit4::class)
class WorkspaceIsolationInstrumentedTest {
    // Use the instrumentation APK's private data directory. These tests never open or delete the
    // installed app's financial databases.
    private lateinit var context: Context
    private val guest = WorkspaceId.Guest
    private val accountA = WorkspaceId.FirebaseAccount("workspace-test-account-A")
    private val accountB = WorkspaceId.FirebaseAccount("workspace-test-account-B")

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().context
        clearTestDatabases()
        context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        clearTestDatabases()
        context.getSharedPreferences("workspace_database_migration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun guestAndFirebaseUsersUseIndependentRoomFiles() = runBlocking {
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
        assertEquals(1, guestDb.accountDao().getAccountCount())
        assertEquals(1, aDb.accountDao().getAccountCount())
        assertEquals(1, bDb.accountDao().getAccountCount())
    }

    @Test
    fun unknownLegacyDatabaseIsCopiedToGuestWithIdsAndForeignKeysPreserved() = runBlocking {
        val legacy = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, "expense_tracker.db")
            .addMigrations(
                ExpenseTrackerDatabase.MIGRATION_1_2,
                ExpenseTrackerDatabase.MIGRATION_2_3,
                ExpenseTrackerDatabase.MIGRATION_3_4
            ).build()
        legacy.accountDao().insertAccount(account("legacy", 41))
        legacy.categoryDao().insertCategory(
            CategoryEntity(7, "Food", CategoryType.EXPENSE, "food", "#123456", false, false)
        )
        legacy.recurringTransactionDao().insertRecurringTransaction(
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
        legacy.budgetDao().insertBudget(
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
        legacy.transactionDao().insertTransaction(
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
        legacy.recurringOccurrenceDao().insertOccurrence(
            RecurringOccurrenceEntity(
                id = 8,
                ruleId = 5,
                occurrenceDate = LocalDate.parse("2022-03-04"),
                generatedTransactionId = 99,
                status = RecurringOccurrenceStatus.GENERATED,
                processedAt = Instant.parse("2022-03-04T05:06:07Z")
            )
        )
        legacy.close()

        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.MIGRATED_TO_GUEST,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        val migratedTransaction = guestDb.transactionDao().getTransactionById(99)
        assertEquals(41L, migratedTransaction?.accountId)
        assertEquals(7L, migratedTransaction?.categoryId)
        assertEquals(Instant.parse("2022-03-04T05:06:07Z"), migratedTransaction?.transactionTime)
        assertEquals(1, guestDb.backupDao().getAllAccounts().size)
        assertEquals(1, guestDb.backupDao().getAllCategories().size)
        assertEquals(1, guestDb.backupDao().getAllBudgets().size)
        assertEquals(1, guestDb.backupDao().getAllRecurringTransactions().size)
        assertEquals(1, guestDb.backupDao().getAllRecurringOccurrences().size)
        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.ALREADY_MIGRATED,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        assertTrue(context.getDatabasePath("expense_tracker.db").exists())
        assertTrue(LegacyWorkspaceDatabaseMigrator.isWorkspaceDatabaseReadable(context, guest))
    }

    @Test
    fun guestImportClonesRelationalDataOnceAndLeavesGuestUnchanged() = runBlocking {
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        guestDb.accountDao().insertAccount(account("guest", 41))
        guestDb.categoryDao().insertCategory(
            CategoryEntity(7, "Food", CategoryType.EXPENSE, "food", "#123456", false, false)
        )
        guestDb.transactionDao().insertTransaction(
            TransactionEntity(
                id = 99,
                type = TransactionType.EXPENSE,
                amountInMinorUnits = 1234,
                currencyCode = "USD",
                accountId = 41,
                destinationAccountId = null,
                categoryId = 7,
                transactionTime = Instant.parse("2022-03-04T05:06:07Z"),
                note = "guest",
                recurringRuleId = null,
                createdAt = Instant.parse("2022-03-04T05:06:07Z")
            )
        )
        ExpenseTrackerDatabaseProvider.closeDatabase(guest)

        assertTrue(LegacyWorkspaceDatabaseMigrator.cloneWorkspace(context, guest, accountA))
        assertFalse(LegacyWorkspaceDatabaseMigrator.cloneWorkspace(context, guest, accountA))

        val imported = ExpenseTrackerDatabaseProvider.getDatabase(context, accountA)
        assertEquals("guest", imported.accountDao().getAccountById(41)?.name)
        assertEquals(7L, imported.transactionDao().getTransactionById(99)?.categoryId)
        assertEquals("guest", ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
            .transactionDao().getTransactionById(99)?.note)
    }

    @Test
    fun guestDatabaseCollisionPreservesBothFiles() = runBlocking {
        val guestDb = ExpenseTrackerDatabaseProvider.getDatabase(context, guest)
        guestDb.accountDao().insertAccount(account("existing guest", 1))
        val legacy = Room.databaseBuilder(context, ExpenseTrackerDatabase::class.java, "expense_tracker.db").build()
        legacy.accountDao().insertAccount(account("legacy", 2))
        legacy.close()

        assertEquals(
            LegacyWorkspaceDatabaseMigrator.Result.GUEST_COLLISION,
            LegacyWorkspaceDatabaseMigrator.migrate(context)
        )
        assertEquals("existing guest", guestDb.accountDao().getAccountById(1)?.name)
        assertTrue(context.getDatabasePath("expense_tracker.db").exists())
        assertTrue(LegacyWorkspaceDatabaseMigrator.hasRecoverableCollision(context))
    }

    private fun clearTestDatabases() {
        listOf(guest, accountA, accountB).forEach { workspace ->
            ExpenseTrackerDatabaseProvider.closeDatabase(workspace)
            context.deleteDatabase(workspace.databaseName)
        }
        context.deleteDatabase("expense_tracker.db")
        context.deleteDatabase("expense_tracker_guest_candidate.db")
        context.deleteDatabase("expense_tracker_workspace_candidate.db")
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
}
