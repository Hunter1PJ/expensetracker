package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.repository.RecurringOccurrenceRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProcessDueRecurringTransactionsUseCaseTest {

    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var recurringTxRepo: FakeRecurringTransactionRepository
    private lateinit var recurringOccurrenceRepo: FakeRecurringOccurrenceRepository
    private lateinit var useCase: ProcessDueRecurringTransactionsUseCase

    private var accountId: Long = 0L
    private var categoryId: Long = 0L
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() = runBlocking {
        accountRepo = FakeAccountRepository()
        categoryRepo = FakeCategoryRepository()
        transactionRepo = FakeTransactionRepository()
        recurringTxRepo = FakeRecurringTransactionRepository()
        recurringOccurrenceRepo = FakeRecurringOccurrenceRepository(recurringTxRepo, transactionRepo)

        useCase = ProcessDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTxRepo,
            recurringOccurrenceRepository = recurringOccurrenceRepo,
            accountRepository = accountRepo,
            categoryRepository = categoryRepo
        )

        accountId = accountRepo.insertAccount(
            Account(
                name = "Test Account",
                type = AccountType.BANK,
                initialBalance = Money(100000L, "USD")
            )
        )

        categoryId = categoryRepo.insertCategory(
            Category(
                name = "Test Category",
                type = CategoryType.EXPENSE,
                iconName = "shopping_bag",
                colorHex = "#FF0000"
            )
        )
    }

    @Test
    fun `process one due occurrence generates exactly one transaction`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 9, 1),
            zoneId = zoneId
        )

        assertEquals(1, processedCount)

        // Verify transaction was generated
        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(1, txs.size)
        val tx = txs.first()
        assertEquals(1000L, tx.amount.amountInMinorUnits)
        assertEquals(ruleId, tx.recurringRuleId)
        assertEquals(LocalDate.of(2026, 9, 1), tx.recurringOccurrenceDate)

        // Verify nextOccurrence advanced
        val updatedRule = recurringTxRepo.getRecurringTransactionById(ruleId)
        assertEquals(LocalDate.of(2026, 9, 2), updatedRule?.nextOccurrence)
        assertTrue(updatedRule?.isActive == true)
    }

    @Test
    fun `process several missed occurrences handles catch-up`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1500L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = LocalDate.of(2026, 8, 1),
            nextOccurrence = LocalDate.of(2026, 8, 1),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        // System date is 2026-08-20, meaning 2026-08-01, 2026-08-08, and 2026-08-15 are due
        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 8, 20),
            zoneId = zoneId
        )

        assertEquals(3, processedCount)

        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(3, txs.size)

        // Verify the occurrence dates generated
        val dates = txs.mapNotNull { it.recurringOccurrenceDate }.sorted()
        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 15)), dates)

        // Verify next occurrence is advanced past the current as-of-date
        val updatedRule = recurringTxRepo.getRecurringTransactionById(ruleId)
        assertEquals(LocalDate.of(2026, 8, 22), updatedRule?.nextOccurrence)
    }

    @Test
    fun `future occurrence is ignored`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 10),
            nextOccurrence = LocalDate.of(2026, 9, 10),
            isActive = true
        )
        recurringTxRepo.insertRecurringTransaction(rule)

        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 9, 5),
            zoneId = zoneId
        )

        assertEquals(0, processedCount)
        assertTrue(transactionRepo.getTransactionsByAccount(accountId).isEmpty())
    }

    @Test
    fun `inactive rule is ignored`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = false
        )
        recurringTxRepo.insertRecurringTransaction(rule)

        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 9, 1),
            zoneId = zoneId
        )

        assertEquals(0, processedCount)
        assertTrue(transactionRepo.getTransactionsByAccount(accountId).isEmpty())
    }

    @Test
    fun `invalid rule skipped and valid rules continue`() = runBlocking {
        // Invalidate rule by using an archived category
        val archivedCategoryId = categoryRepo.insertCategory(
            Category(
                name = "Archived Cat",
                type = CategoryType.EXPENSE,
                iconName = "archive",
                colorHex = "#999999",
                isArchived = true
            )
        )

        val invalidRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = archivedCategoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val invalidRuleId = recurringTxRepo.insertRecurringTransaction(invalidRule)

        val validRule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(2000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val validRuleId = recurringTxRepo.insertRecurringTransaction(validRule)

        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 9, 1),
            zoneId = zoneId
        )

        // Only 1 rule should be processed
        assertEquals(1, processedCount)

        // Verify invalid rule is untouched and nextOccurrence didn't advance
        val dbInvalidRule = recurringTxRepo.getRecurringTransactionById(invalidRuleId)
        assertEquals(LocalDate.of(2026, 9, 1), dbInvalidRule?.nextOccurrence)

        // Verify valid rule advanced
        val dbValidRule = recurringTxRepo.getRecurringTransactionById(validRuleId)
        assertEquals(LocalDate.of(2026, 9, 2), dbValidRule?.nextOccurrence)

        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(1, txs.size)
        assertEquals(2000L, txs.first().amount.amountInMinorUnits)
    }

    @Test
    fun `endDate is respected`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        // Run processor up to 2026-09-10
        val processedCount = useCase(
            asOfDate = LocalDate.of(2026, 9, 10),
            zoneId = zoneId
        )

        // Daily occurrences should be: Sep 1, Sep 2, Sep 3 (3 total). Sep 4 is expired.
        assertEquals(3, processedCount)

        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(3, txs.size)

        // Rule should now be deactivated
        val dbRule = recurringTxRepo.getRecurringTransactionById(ruleId)
        assertEquals(false, dbRule?.isActive)
    }

    @Test
    fun `processor is completely idempotent`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        // Run processor once
        val processed1 = useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)
        assertEquals(1, processed1)

        // Roll back the rule's nextOccurrence to simulate repeated triggering (e.g. concurrent/redundant calls)
        val dbRule = recurringTxRepo.getRecurringTransactionById(ruleId)!!
        recurringTxRepo.updateRecurringTransaction(dbRule.copy(nextOccurrence = LocalDate.of(2026, 9, 1)))

        // Run processor again for the same date
        val processed2 = useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)
        assertEquals(0, processed2) // 0 more transactions generated!

        // Assert exactly 1 transaction remains
        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(1, txs.size)

        // Assert exactly 1 ledger record
        assertEquals(1, recurringOccurrenceRepo.occurrences.size)
    }

    @Test
    fun `deleted transaction is not regenerated`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        // 1. Generate occurrence
        useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)
        
        val generatedTx = transactionRepo.getTransactionsByAccount(accountId).first()
        assertNotNull(generatedTx)

        // 2. Delete transaction (simulating DeleteTransactionUseCase flow)
        transactionRepo.deleteTransactionById(generatedTx.id)
        recurringOccurrenceRepo.markTransactionDeleted(ruleId, LocalDate.of(2026, 9, 1))

        assertTrue(transactionRepo.getTransactionsByAccount(accountId).isEmpty())

        // Roll back rule nextOccurrence for Sep 1
        val dbRule = recurringTxRepo.getRecurringTransactionById(ruleId)!!
        recurringTxRepo.updateRecurringTransaction(dbRule.copy(nextOccurrence = LocalDate.of(2026, 9, 1)))

        // 3. Process again
        val processedAgain = useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)
        assertEquals(0, processedAgain)

        // Verify transaction remains deleted
        assertTrue(transactionRepo.getTransactionsByAccount(accountId).isEmpty())
    }

    @Test
    fun `edited transaction does not cause duplicate regeneration`() = runBlocking {
        val rule = RecurringTransaction(
            id = 0L,
            type = TransactionType.EXPENSE,
            amount = Money(1000L, "USD"),
            accountId = accountId,
            categoryId = categoryId,
            frequency = RecurrenceFrequency.DAILY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 9, 1),
            isActive = true
        )
        val ruleId = recurringTxRepo.insertRecurringTransaction(rule)

        // 1. Generate occurrence
        useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)

        val generatedTx = transactionRepo.getTransactionsByAccount(accountId).first()

        // 2. Edit transaction details while preserving recurring metadata
        val editedTx = generatedTx.copy(
            amount = Money(1200L, "USD"),
            note = "Edited"
        )
        transactionRepo.updateTransaction(editedTx)

        // Roll back rule nextOccurrence
        val dbRule = recurringTxRepo.getRecurringTransactionById(ruleId)!!
        recurringTxRepo.updateRecurringTransaction(dbRule.copy(nextOccurrence = LocalDate.of(2026, 9, 1)))

        // 3. Run processor again
        val processedAgain = useCase(asOfDate = LocalDate.of(2026, 9, 1), zoneId = zoneId)
        assertEquals(0, processedAgain)

        // Verify there is still only 1 transaction, and its edited values are preserved
        val txs = transactionRepo.getTransactionsByAccount(accountId)
        assertEquals(1, txs.size)
        assertEquals(1200L, txs.first().amount.amountInMinorUnits)
        assertEquals("Edited", txs.first().note)
    }
}

// Inline fake implementation to keep the test environment decoupled and isolated.
class FakeRecurringTransactionRepository : RecurringTransactionRepository {
    val rules = mutableMapOf<Long, RecurringTransaction>()
    private var nextId = 1L

    override fun observeActiveRecurringTransactions(): Flow<List<RecurringTransaction>> = flowOf(rules.values.filter { it.isActive })
    override fun observeAllRecurringTransactions(): Flow<List<RecurringTransaction>> = flowOf(rules.values.toList())
    override fun observeRecurringTransactionById(id: Long): Flow<RecurringTransaction?> = flowOf(rules[id])
    override suspend fun getRecurringTransactionById(id: Long): RecurringTransaction? = rules[id]
    
    override suspend fun getRecurringTransactionsDue(beforeOrOnDate: LocalDate): List<RecurringTransaction> {
        return rules.values.filter { it.isActive && it.nextOccurrence <= beforeOrOnDate }
    }
    
    override suspend fun insertRecurringTransaction(recurringTransaction: RecurringTransaction): Long {
        val id = if (recurringTransaction.id == 0L) nextId++ else recurringTransaction.id
        val updated = recurringTransaction.copy(id = id)
        rules[id] = updated
        return id
    }
    
    override suspend fun updateRecurringTransaction(recurringTransaction: RecurringTransaction) {
        rules[recurringTransaction.id] = recurringTransaction
    }
    
    override suspend fun deactivateRecurringTransaction(id: Long) {
        val rule = rules[id] ?: return
        rules[id] = rule.copy(isActive = false)
    }
}

class FakeRecurringOccurrenceRepository(
    private val recurringTransactionRepository: FakeRecurringTransactionRepository,
    private val transactionRepository: FakeTransactionRepository
) : RecurringOccurrenceRepository {

    data class OccurrenceRecord(
        val ruleId: Long,
        val occurrenceDate: LocalDate,
        val generatedTransactionId: Long?,
        val status: String // "GENERATED" or "TRANSACTION_DELETED"
    )

    val occurrences = mutableListOf<OccurrenceRecord>()

    override suspend fun isOccurrenceProcessed(ruleId: Long, occurrenceDate: LocalDate): Boolean {
        return occurrences.any { it.ruleId == ruleId && it.occurrenceDate == occurrenceDate }
    }

    override suspend fun processAtomicOccurrence(
        rule: RecurringTransaction,
        occurrenceDate: LocalDate,
        transactionToInsert: Transaction,
        newNextOccurrence: LocalDate,
        isRuleNowActive: Boolean
    ): Long? {
        val existing = occurrences.find { it.ruleId == rule.id && it.occurrenceDate == occurrenceDate }
        if (existing != null) {
            // Already processed. Ensure rule nextOccurrence is synchronized
            val dbRule = recurringTransactionRepository.getRecurringTransactionById(rule.id)
            if (dbRule != null && dbRule.nextOccurrence == occurrenceDate) {
                recurringTransactionRepository.updateRecurringTransaction(
                    dbRule.copy(nextOccurrence = newNextOccurrence, isActive = isRuleNowActive)
                )
            }
            return null
        }

        val txId = transactionRepository.insertTransaction(transactionToInsert)

        occurrences.add(
            OccurrenceRecord(
                ruleId = rule.id,
                occurrenceDate = occurrenceDate,
                generatedTransactionId = txId,
                status = "GENERATED"
            )
        )

        recurringTransactionRepository.updateRecurringTransaction(
            rule.copy(nextOccurrence = newNextOccurrence, isActive = isRuleNowActive)
        )

        return txId
    }

    override suspend fun markTransactionDeleted(ruleId: Long, occurrenceDate: LocalDate) {
        val index = occurrences.indexOfFirst { it.ruleId == ruleId && it.occurrenceDate == occurrenceDate }
        if (index != -1) {
            val existing = occurrences[index]
            occurrences[index] = existing.copy(status = "TRANSACTION_DELETED", generatedTransactionId = null)
        } else {
            occurrences.add(
                OccurrenceRecord(
                    ruleId = ruleId,
                    occurrenceDate = occurrenceDate,
                    generatedTransactionId = null,
                    status = "TRANSACTION_DELETED"
                )
            )
        }
    }
}
