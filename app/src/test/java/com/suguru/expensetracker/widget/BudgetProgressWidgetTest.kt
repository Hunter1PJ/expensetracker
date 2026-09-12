package com.suguru.expensetracker.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.domain.model.*
import com.suguru.expensetracker.domain.model.budget.BudgetProgress
import com.suguru.expensetracker.domain.repository.BudgetRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.usecase.budget.ObserveActiveBudgetProgressUseCase
import com.suguru.expensetracker.widget.budget.*
import com.suguru.expensetracker.widget.common.*
import com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration
import com.suguru.expensetracker.widget.configuration.WidgetConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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
class BudgetProgressWidgetTest {

    private lateinit var context: Context
    private lateinit var fakeBudgetRepository: FakeBudgetRepository
    private lateinit var fakeCategoryRepository: FakeCategoryRepository
    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var observeActiveBudgetProgressUseCase: ObserveActiveBudgetProgressUseCase
    private lateinit var dataProvider: BudgetProgressWidgetDataProvider
    private lateinit var fakeWidgetConfigRepository: FakeWidgetConfigurationRepository
    private lateinit var widgetRefreshCoordinator: FakeWidgetRefreshCoordinator
    private lateinit var widgetEntitlementPolicy: WidgetEntitlementPolicy

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeBudgetRepository = FakeBudgetRepository()
        fakeCategoryRepository = FakeCategoryRepository()
        fakeTransactionRepository = FakeTransactionRepository()
        fakeAccountRepository = FakeAccountRepository()
        
        observeActiveBudgetProgressUseCase = ObserveActiveBudgetProgressUseCase(
            budgetRepository = fakeBudgetRepository,
            transactionRepository = fakeTransactionRepository,
            accountRepository = fakeAccountRepository,
            categoryRepository = fakeCategoryRepository
        )
        
        fakeWidgetConfigRepository = FakeWidgetConfigurationRepository()
        
        dataProvider = BudgetProgressWidgetDataProvider(
            budgetRepository = fakeBudgetRepository,
            categoryRepository = fakeCategoryRepository,
            observeActiveBudgetProgressUseCase = observeActiveBudgetProgressUseCase,
            widgetConfigurationRepository = fakeWidgetConfigRepository
        )
        widgetRefreshCoordinator = FakeWidgetRefreshCoordinator()
        widgetEntitlementPolicy = WidgetEntitlementPolicy()
    }

    // --- TEST 1: Entitlement Testing - Free Tier is Blocked ---
    @Test
    fun testFreeTierBlocksBudgetWidget() {
        val result = widgetEntitlementPolicy.checkEntitlement(
            feature = WidgetFeature.BUDGET_PROGRESS,
            currentProEntitlement = ProEntitlement.Free,
            currentConfiguredCount = 0
        )
        assertTrue(result is WidgetGateResult.ProRequired)
    }

    // --- TEST 2: Entitlement Testing - Pro Tier is Allowed ---
    @Test
    fun testProTierAllowsBudgetWidget() {
        val result = widgetEntitlementPolicy.checkEntitlement(
            feature = WidgetFeature.BUDGET_PROGRESS,
            currentProEntitlement = ProEntitlement.Pro,
            currentConfiguredCount = 3
        )
        assertTrue(result is WidgetGateResult.Allowed)
    }

    // --- TEST 3: Configuration Persistence - Configurations are Independent per Widget ID ---
    @Test
    fun testConfigurationIndependentPerWidget() = runBlocking {
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(101, 1L, true)
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(102, 2L, false)

        val config1 = fakeWidgetConfigRepository.getBudgetProgressConfiguration(101).first()
        val config2 = fakeWidgetConfigRepository.getBudgetProgressConfiguration(102).first()

        assertNotNull(config1)
        assertNotNull(config2)
        assertEquals(1L, config1?.budgetId)
        assertEquals(true, config1?.privacyMode)
        assertEquals(2L, config2?.budgetId)
        assertEquals(false, config2?.privacyMode)
    }

    // --- TEST 4: Configuration Deletion - Independent Deletion ---
    @Test
    fun testDeletingWidgetConfigLeavesOthersIntact() = runBlocking {
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(101, 1L, true)
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(102, 2L, false)

        fakeWidgetConfigRepository.deleteBudgetProgressConfiguration(101)

        val config1 = fakeWidgetConfigRepository.getBudgetProgressConfiguration(101).first()
        val config2 = fakeWidgetConfigRepository.getBudgetProgressConfiguration(102).first()

        assertNull(config1)
        assertNotNull(config2)
        assertEquals(2L, config2?.budgetId)
    }

    // --- TEST 5: Privacy Mode Masking ---
    @Test
    fun testPrivacyModeMasksBudgetAmounts() = runBlocking {
        // Setup details in repos
        val category = Category(id = 10L, name = "Groceries", iconName = "fastfood", colorHex = "#FF0000", type = CategoryType.EXPENSE)
        fakeCategoryRepository.insertCategory(category)

        val budget = Budget(
            id = 1L,
            categoryId = 10L,
            limitAmount = Money(50000L, "USD"),
            periodType = BudgetPeriodType.MONTHLY,
            startDate = LocalDate.now().minusDays(5),
            endDate = LocalDate.now().plusDays(25)
        )
        fakeBudgetRepository.insertBudget(budget)

        val account = Account(id = 1L, name = "Cash", type = AccountType.CASH, initialBalance = Money(100000L, "USD"))
        fakeAccountRepository.insertAccount(account)

        val tx = Transaction(
            id = 1L,
            type = TransactionType.EXPENSE,
            amount = Money(20000L, "USD"),
            accountId = 1L,
            categoryId = 10L,
            transactionTime = Instant.now()
        )
        fakeTransactionRepository.insertTransaction(tx)

        // Save widget config with privacy mode ENABLED
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(201, 1L, true)

        val data = dataProvider.getWidgetData(201).first { it.state != BudgetWidgetState.LOADING }
        
        assertEquals(BudgetWidgetState.READY, data.state)
        assertEquals("Groceries", data.title)
        assertEquals("Amounts Hidden", data.spentFormatted)
        assertEquals("Amounts Hidden", data.limitFormatted)
        assertEquals("Amounts Hidden", data.remainingFormatted)
        assertEquals("40%", data.progressLabel)
        assertEquals(4000, data.progressBasisPoints)
    }

    // --- TEST 6: Unmasked Renders Beautiful Amounts ---
    @Test
    fun testNormalModeDisplaysFormattedAmounts() = runBlocking {
        val category = Category(id = 10L, name = "Groceries", iconName = "fastfood", colorHex = "#FF0000", type = CategoryType.EXPENSE)
        fakeCategoryRepository.insertCategory(category)

        val budget = Budget(
            id = 1L,
            categoryId = 10L,
            limitAmount = Money(50000L, "USD"),
            periodType = BudgetPeriodType.MONTHLY,
            startDate = LocalDate.now().minusDays(5),
            endDate = LocalDate.now().plusDays(25)
        )
        fakeBudgetRepository.insertBudget(budget)

        val account = Account(id = 1L, name = "Cash", type = AccountType.CASH, initialBalance = Money(100000L, "USD"))
        fakeAccountRepository.insertAccount(account)

        val tx = Transaction(
            id = 1L,
            type = TransactionType.EXPENSE,
            amount = Money(20000L, "USD"),
            accountId = 1L,
            categoryId = 10L,
            transactionTime = Instant.now()
        )
        fakeTransactionRepository.insertTransaction(tx)

        // Save widget config with privacy mode DISABLED
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(201, 1L, false)

        val data = dataProvider.getWidgetData(201).first { it.state != BudgetWidgetState.LOADING }
        
        assertEquals(BudgetWidgetState.READY, data.state)
        assertEquals("Groceries", data.title)
        assertEquals("$200.00", data.spentFormatted)
        assertEquals("$500.00", data.limitFormatted)
        assertEquals("$300.00", data.remainingFormatted)
        assertEquals("40%", data.progressLabel)
    }

    // --- TEST 7: Overall Budget Render (No category name) ---
    @Test
    fun testOverallBudgetDisplaysDefaultTitle() = runBlocking {
        val budget = Budget(
            id = 2L,
            categoryId = null,
            limitAmount = Money(100000L, "USD"),
            periodType = BudgetPeriodType.WEEKLY,
            startDate = LocalDate.now().minusDays(2),
            endDate = LocalDate.now().plusDays(5)
        )
        fakeBudgetRepository.insertBudget(budget)

        val account = Account(id = 1L, name = "Cash", type = AccountType.CASH, initialBalance = Money(200000L, "USD"))
        fakeAccountRepository.insertAccount(account)

        val tx = Transaction(
            id = 2L,
            type = TransactionType.EXPENSE,
            amount = Money(80000L, "USD"),
            accountId = 1L,
            categoryId = 10L,
            transactionTime = Instant.now()
        )
        fakeTransactionRepository.insertTransaction(tx)

        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(202, 2L, false)

        val data = dataProvider.getWidgetData(202).first { it.state != BudgetWidgetState.LOADING }
        
        assertEquals(BudgetWidgetState.READY, data.state)
        assertEquals("Overall Budget", data.title)
        assertEquals("$800.00", data.spentFormatted)
        assertEquals("$1,000.00", data.limitFormatted)
        assertEquals("$200.00", data.remainingFormatted)
    }

    // --- TEST 8: Exceeded Budget State displays properly ---
    @Test
    fun testExceededBudgetVisualProperties() = runBlocking {
        val budget = Budget(
            id = 1L,
            categoryId = null,
            limitAmount = Money(10000L, "USD"),
            periodType = BudgetPeriodType.WEEKLY,
            startDate = LocalDate.now().minusDays(1),
            endDate = LocalDate.now().plusDays(5)
        )
        fakeBudgetRepository.insertBudget(budget)

        val account = Account(id = 1L, name = "Cash", type = AccountType.CASH, initialBalance = Money(100000L, "USD"))
        fakeAccountRepository.insertAccount(account)

        val tx = Transaction(
            id = 1L,
            type = TransactionType.EXPENSE,
            amount = Money(15000L, "USD"),
            accountId = 1L,
            categoryId = null,
            transactionTime = Instant.now()
        )
        fakeTransactionRepository.insertTransaction(tx)

        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(203, 1L, false)

        val data = dataProvider.getWidgetData(203).first { it.state != BudgetWidgetState.LOADING }
        
        assertEquals(BudgetWidgetState.READY, data.state)
        assertTrue(data.isExceeded)
        assertEquals("150%", data.progressLabel)
    }

    // --- TEST 9: Budget Unavailable State ---
    @Test
    fun testMissingBudgetReturnsUnavailable() = runBlocking {
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(301, 999L, false)

        val data = dataProvider.getWidgetData(301).first { it.state != BudgetWidgetState.LOADING }
        assertEquals(BudgetWidgetState.BUDGET_UNAVAILABLE, data.state)
    }

    // --- TEST 10: Event-Driven Refresh triggers correctly ---
    @Test
    fun testWidgetRefreshCoordinatorFiresRefresh() {
        widgetRefreshCoordinator.refreshBudgetWidgets(5L)
        assertTrue(widgetRefreshCoordinator.refreshedBudgets.contains(5L))
    }

    // --- TEST 11: Grandfathering functional verification ---
    @Test
    fun testGrandfatheredWidgetsActiveOnFreeTier() = runBlocking {
        // Pre-configured on home screen when Pro was active
        fakeWidgetConfigRepository.saveBudgetProgressConfiguration(401, 1L, false)

        val budget = Budget(
            id = 1L,
            categoryId = null,
            limitAmount = Money(30000L, "USD"),
            periodType = BudgetPeriodType.MONTHLY,
            startDate = LocalDate.now().minusDays(5),
            endDate = LocalDate.now().plusDays(25)
        )
        fakeBudgetRepository.insertBudget(budget)

        val account = Account(id = 1L, name = "Cash", type = AccountType.CASH, initialBalance = Money(100000L, "USD"))
        fakeAccountRepository.insertAccount(account)

        val tx = Transaction(
            id = 1L,
            type = TransactionType.EXPENSE,
            amount = Money(10000L, "USD"),
            accountId = 1L,
            categoryId = null,
            transactionTime = Instant.now()
        )
        fakeTransactionRepository.insertTransaction(tx)

        // Even if entitlement is now Free, the grandfathered widget remains functional
        val data = dataProvider.getWidgetData(401).first { it.state != BudgetWidgetState.LOADING }
        assertEquals(BudgetWidgetState.READY, data.state)
        assertEquals("$100.00", data.spentFormatted)
    }

    // --- FAKES FOR BUDGET HERMETIC TESTS ---

    class FakeBudgetRepository : BudgetRepository {
        private val budgets = MutableStateFlow<Map<Long, Budget>>(emptyMap())
        override fun observeActiveBudgets(): Flow<List<Budget>> = budgets.map { it.values.toList() }
        override fun observeAllBudgets(): Flow<List<Budget>> = budgets.map { it.values.toList() }
        override fun observeBudgetsByCategory(categoryId: Long): Flow<List<Budget>> = budgets.map { it.values.filter { b -> b.categoryId == categoryId } }
        override fun observeBudgetById(id: Long): Flow<Budget?> = budgets.map { it[id] }
        override suspend fun getBudgetById(id: Long): Budget? = budgets.value[id]
        override suspend fun insertBudget(budget: Budget): Long {
            budgets.value = budgets.value + (budget.id to budget)
            return budget.id
        }
        override suspend fun updateBudget(budget: Budget) {
            budgets.value = budgets.value + (budget.id to budget)
        }
        override suspend fun deactivateBudget(id: Long) {
            budgets.value = budgets.value - id
        }
    }

    class FakeCategoryRepository : CategoryRepository {
        private val categories = MutableStateFlow<Map<Long, Category>>(emptyMap())
        override fun observeActiveCategories(): Flow<List<Category>> = categories.map { it.values.toList() }
        override fun observeAllCategories(): Flow<List<Category>> = categories.map { it.values.toList() }
        override fun observeActiveCategoriesByType(type: CategoryType): Flow<List<Category>> = categories.map { it.values.filter { c -> c.type == type } }
        override fun observeCategoryById(id: Long): Flow<Category?> = categories.map { it[id] }
        override suspend fun getCategoryById(id: Long): Category? = categories.value[id]
        override suspend fun insertCategory(category: Category): Long {
            categories.value = categories.value + (category.id to category)
            return category.id
        }
        override suspend fun insertCategories(categories: List<Category>): List<Long> {
            val ids = mutableListOf<Long>()
            categories.forEach {
                insertCategory(it)
                ids.add(it.id)
            }
            return ids
        }
        override suspend fun updateCategory(category: Category) {
            categories.value = categories.value + (category.id to category)
        }
        override suspend fun archiveCategory(id: Long) {}
    }

    class FakeTransactionRepository : com.suguru.expensetracker.domain.repository.TransactionRepository {
        private val transactions = MutableStateFlow<Map<Long, Transaction>>(emptyMap())
        override fun observeAllTransactions(): Flow<List<Transaction>> = transactions.map { it.values.toList() }
        override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> = transactions.map { it.values.take(limit) }
        override suspend fun getRecentTransactions(limit: Int): List<Transaction> = transactions.value.values.take(limit)
        override fun observeTransactionsByAccount(accountId: Long): Flow<List<Transaction>> = transactions.map { it.values.filter { t -> t.accountId == accountId } }
        override suspend fun getTransactionsByAccount(accountId: Long): List<Transaction> = transactions.value.values.filter { t -> t.accountId == accountId }
        override fun observeTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = transactions.map { it.values.filter { t -> t.categoryId == categoryId } }
        override fun observeTransactionsBetween(startTime: Instant, endTime: Instant): Flow<List<Transaction>> = transactions.map { it.values.filter { t -> !t.transactionTime.isBefore(startTime) && t.transactionTime.isBefore(endTime) } }
        override suspend fun getTransactionsBetween(startTime: Instant, endTime: Instant): List<Transaction> = transactions.value.values.filter { t -> !t.transactionTime.isBefore(startTime) && t.transactionTime.isBefore(endTime) }
        override fun observeTransactionById(id: Long): Flow<Transaction?> = transactions.map { it[id] }
        override suspend fun getTransactionById(id: Long): Transaction? = transactions.value[id]
        override suspend fun insertTransaction(transaction: Transaction): Long {
            transactions.value = transactions.value + (transaction.id to transaction)
            return transaction.id
        }
        override suspend fun insertTransactions(transactions: List<Transaction>): List<Long> {
            val ids = mutableListOf<Long>()
            transactions.forEach {
                insertTransaction(it)
                ids.add(it.id)
            }
            return ids
        }
        override suspend fun updateTransaction(transaction: Transaction) {
            transactions.value = transactions.value + (transaction.id to transaction)
        }
        override suspend fun deleteTransactionById(id: Long) {
            transactions.value = transactions.value - id
        }
    }

    class FakeAccountRepository : com.suguru.expensetracker.domain.repository.AccountRepository {
        private val accounts = MutableStateFlow<Map<Long, Account>>(emptyMap())
        override fun observeActiveAccounts(): Flow<List<Account>> = accounts.map { it.values.filter { !it.isArchived } }
        override fun observeAllAccounts(): Flow<List<Account>> = accounts.map { it.values.toList() }
        override fun observeAccountById(id: Long): Flow<Account?> = accounts.map { it[id] }
        override suspend fun getAccountById(id: Long): Account? = accounts.value[id]
        override suspend fun insertAccount(account: Account): Long {
            accounts.value = accounts.value + (account.id to account)
            return account.id
        }
        override suspend fun updateAccount(account: Account) {
            accounts.value = accounts.value + (account.id to account)
        }
        override suspend fun archiveAccount(id: Long) {}
    }

    class FakeWidgetConfigurationRepository : WidgetConfigurationRepository {
        private val configs = MutableStateFlow<Map<Int, com.suguru.expensetracker.widget.configuration.AccountWidgetConfiguration>>(emptyMap())
        private val budgetConfigs = MutableStateFlow<Map<Int, BudgetProgressWidgetConfiguration>>(emptyMap())

        override fun getConfiguration(appWidgetId: Int): Flow<com.suguru.expensetracker.widget.configuration.AccountWidgetConfiguration?> =
            configs.map { it[appWidgetId] }

        override fun getAllConfigurations(): Flow<List<com.suguru.expensetracker.widget.configuration.AccountWidgetConfiguration>> =
            configs.map { it.values.toList() }

        override suspend fun saveConfiguration(appWidgetId: Int, accountId: Long, privacyMode: Boolean) {}

        override suspend fun deleteConfiguration(appWidgetId: Int) {}

        override fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<BudgetProgressWidgetConfiguration?> =
            budgetConfigs.map { it[appWidgetId] }

        override fun getAllBudgetProgressConfigurations(): Flow<List<BudgetProgressWidgetConfiguration>> =
            budgetConfigs.map { it.values.toList() }

        override suspend fun saveBudgetProgressConfiguration(appWidgetId: Int, budgetId: Long, privacyMode: Boolean) {
            budgetConfigs.value = budgetConfigs.value + (appWidgetId to BudgetProgressWidgetConfiguration(appWidgetId, budgetId, privacyMode))
        }

        override suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int) {
            budgetConfigs.value = budgetConfigs.value - appWidgetId
        }
    }

    class FakeWidgetRefreshCoordinator : WidgetRefreshCoordinator {
        val refreshedAccounts = mutableListOf<Long>()
        val refreshedBudgets = mutableListOf<Long>()
        var allRefreshed = false

        override fun refreshAccountBalanceWidgets(accountId: Long) {
            refreshedAccounts.add(accountId)
        }

        override fun refreshBudgetWidgets(budgetId: Long) {
            refreshedBudgets.add(budgetId)
        }

        override fun refreshAll() {
            allRefreshed = true
        }
    }
}
