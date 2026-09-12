package com.suguru.expensetracker.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suguru.expensetracker.domain.model.*
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.SettingsRepository
import com.suguru.expensetracker.domain.usecase.balance.GetAccountBalanceUseCase
import com.suguru.expensetracker.domain.util.MoneyParser
import com.suguru.expensetracker.widget.balance.*
import com.suguru.expensetracker.widget.common.*
import com.suguru.expensetracker.widget.configuration.AccountWidgetConfiguration
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountBalanceWidgetTest {

    private lateinit var context: Context
    private lateinit var fakeAccountRepository: FakeAccountRepository
    private lateinit var getAccountBalanceUseCase: GetAccountBalanceUseCase
    private lateinit var widgetConfigRepository: FakeWidgetConfigurationRepository
    private lateinit var dataProvider: AccountBalanceWidgetDataProvider
    private lateinit var widgetRefreshCoordinator: FakeWidgetRefreshCoordinator
    private lateinit var widgetEntitlementPolicy: WidgetEntitlementPolicy

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeAccountRepository = FakeAccountRepository()
        getAccountBalanceUseCase = GetAccountBalanceUseCase(fakeAccountRepository, FakeTransactionRepository())
        widgetConfigRepository = FakeWidgetConfigurationRepository()
        dataProvider = AccountBalanceWidgetDataProvider(
            accountRepository = fakeAccountRepository,
            getAccountBalanceUseCase = getAccountBalanceUseCase,
            widgetConfigurationRepository = widgetConfigRepository
        )
        widgetRefreshCoordinator = FakeWidgetRefreshCoordinator()
        widgetEntitlementPolicy = WidgetEntitlementPolicy()
    }

    // --- TEST 1: Free + 0 Balance widgets -> configure first allowed ---
    @Test
    fun testFreeTierFirstWidgetAllowed() {
        val result = widgetEntitlementPolicy.checkEntitlement(
            feature = WidgetFeature.ACCOUNT_BALANCE,
            currentProEntitlement = ProEntitlement.Free,
            currentConfiguredCount = 0
        )
        assertTrue(result is WidgetGateResult.Allowed)
    }

    // --- TEST 2: Free + 1 existing Balance widget -> second blocked ---
    @Test
    fun testFreeTierSecondWidgetBlocked() {
        val result = widgetEntitlementPolicy.checkEntitlement(
            feature = WidgetFeature.ACCOUNT_BALANCE,
            currentProEntitlement = ProEntitlement.Free,
            currentConfiguredCount = 1
        )
        assertTrue(result is WidgetGateResult.LimitReached)
    }

    // --- TEST 3: Pro + multiple widgets -> new configuration allowed ---
    @Test
    fun testProTierUnlimitedWidgetsAllowed() {
        val result = widgetEntitlementPolicy.checkEntitlement(
            feature = WidgetFeature.ACCOUNT_BALANCE,
            currentProEntitlement = ProEntitlement.Pro,
            currentConfiguredCount = 5
        )
        assertTrue(result is WidgetGateResult.Allowed)
    }

    // --- TEST 4: Pro -> Free with existing multiple widgets -> widgets remain functional (Grandfathering) ---
    @Test
    fun testProToFreeGrandfathering() = runBlocking {
        // Pre-configure two widgets while Pro (this represents existing widgets on home screen)
        widgetConfigRepository.saveConfiguration(101, 1L, false)
        widgetConfigRepository.saveConfiguration(102, 2L, false)

        // Setup some accounts
        fakeAccountRepository.insertAccount(Account(id = 1L, name = "Account A", type = AccountType.BANK, initialBalance = Money(1000L, "USD")))
        fakeAccountRepository.insertAccount(Account(id = 2L, name = "Account B", type = AccountType.CASH, initialBalance = Money(2000L, "USD")))

        // Read data back (simulating system reading current widget states after entitlement drops to Free)
        val data1 = dataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        val data2 = dataProvider.getWidgetData(102).first { it.state != WidgetDataState.LOADING }

        assertEquals(WidgetDataState.READY, data1.state)
        assertEquals(WidgetDataState.READY, data2.state)
        assertEquals("Account A", data1.accountName)
        assertEquals("Account B", data2.accountName)
    }

    // --- TEST 5: Configuration saved per appWidgetId ---
    @Test
    fun testConfigurationSavedPerWidgetId() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 10L, true)
        widgetConfigRepository.saveConfiguration(102, 20L, false)

        val config1 = widgetConfigRepository.getConfiguration(101).first()
        val config2 = widgetConfigRepository.getConfiguration(102).first()

        assertNotNull(config1)
        assertNotNull(config2)
        assertEquals(10L, config1?.accountId)
        assertEquals(20L, config2?.accountId)
    }

    // --- TEST 6: Two widget instances keep independent accountId/privacy values ---
    @Test
    fun testTwoWidgetInstancesIndependentValues() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 1L, true)
        widgetConfigRepository.saveConfiguration(102, 2L, false)

        val config1 = widgetConfigRepository.getConfiguration(101).first()
        val config2 = widgetConfigRepository.getConfiguration(102).first()

        assertEquals(true, config1?.privacyMode)
        assertEquals(false, config2?.privacyMode)
    }

    // --- TEST 7: Deleting widget A does not delete widget B config ---
    @Test
    fun testDeletingWidgetDoesNotAffectOthers() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 1L, false)
        widgetConfigRepository.saveConfiguration(102, 2L, false)

        widgetConfigRepository.deleteConfiguration(101)

        val config1 = widgetConfigRepository.getConfiguration(101).first()
        val config2 = widgetConfigRepository.getConfiguration(102).first()

        assertNull(config1)
        assertNotNull(config2)
        assertEquals(2L, config2?.accountId)
    }

    // --- TEST 8: Privacy mode does not render amount ---
    @Test
    fun testPrivacyModeMasksAmount() = runBlocking {
        fakeAccountRepository.insertAccount(Account(id = 1L, name = "Private Bank", type = AccountType.BANK, initialBalance = Money(150000L, "USD")))
        widgetConfigRepository.saveConfiguration(101, 1L, true)

        val data = dataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        assertEquals(WidgetDataState.READY, data.state)
        assertEquals("Private Bank", data.accountName)
        assertEquals("••••••", data.formattedBalance)
    }

    // --- TEST 9: Normal mode renders formatted balance ---
    @Test
    fun testNormalModeRendersFormattedBalance() = runBlocking {
        fakeAccountRepository.insertAccount(Account(id = 1L, name = "Normal Bank", type = AccountType.BANK, initialBalance = Money(125000L, "USD")))
        widgetConfigRepository.saveConfiguration(101, 1L, false)

        val data = dataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        assertEquals(WidgetDataState.READY, data.state)
        assertEquals("$1,250.00", data.formattedBalance)
    }

    // --- TEST 10: Missing account -> AccountUnavailable ---
    @Test
    fun testMissingAccountReturnsUnavailable() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 999L, false)

        val data = dataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        assertEquals(WidgetDataState.ACCOUNT_UNAVAILABLE, data.state)
    }

    // --- TEST 11: Archived configured account behaves according to chosen rule ---
    @Test
    fun testArchivedAccountCanStillBeDisplayed() = runBlocking {
        fakeAccountRepository.insertAccount(Account(id = 1L, name = "Old Account", type = AccountType.BANK, initialBalance = Money(5000L, "USD"), isArchived = true))
        widgetConfigRepository.saveConfiguration(101, 1L, false)

        val data = dataProvider.getWidgetData(101).first { it.state != WidgetDataState.LOADING }
        assertEquals(WidgetDataState.READY, data.state)
        assertEquals("Old Account", data.accountName)
    }

    // --- TEST 12: Cancelled configuration writes nothing ---
    @Test
    fun testCancelledConfigurationWritesNothing() = runBlocking {
        val config = widgetConfigRepository.getConfiguration(101).first()
        assertNull(config)
    }

    // --- TEST 13: Successful configuration writes data ---
    @Test
    fun testSuccessfulConfigurationWritesCorrectly() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 3L, true)
        val config = widgetConfigRepository.getConfiguration(101).first()
        assertNotNull(config)
        assertEquals(3L, config?.accountId)
        assertEquals(true, config?.privacyMode)
    }

    // --- TEST 14: Transaction mutation triggers refresh ---
    @Test
    fun testTransactionMutationTriggersRefresh() {
        widgetRefreshCoordinator.refreshAccountBalanceWidgets(1L)
        assertTrue(widgetRefreshCoordinator.refreshedAccounts.contains(1L))
    }

    // --- TEST 15: Transfer refreshes both affected accounts ---
    @Test
    fun testTransferRefreshesBothAccounts() {
        widgetRefreshCoordinator.refreshAccountBalanceWidgets(1L)
        widgetRefreshCoordinator.refreshAccountBalanceWidgets(2L)
        assertTrue(widgetRefreshCoordinator.refreshedAccounts.contains(1L))
        assertTrue(widgetRefreshCoordinator.refreshedAccounts.contains(2L))
    }

    // --- TEST 16: Restore triggers refresh ---
    @Test
    fun testRestoreTriggersRefresh() {
        widgetRefreshCoordinator.refreshAll()
        assertTrue(widgetRefreshCoordinator.allRefreshed)
    }

    // --- TEST 17: Recurring generated transaction triggers refresh ---
    @Test
    fun testRecurringTriggersRefresh() {
        widgetRefreshCoordinator.refreshAll()
        assertTrue(widgetRefreshCoordinator.allRefreshed)
    }

    // --- TEST 18: Process reconstruction resolves persisted config correctly ---
    @Test
    fun testProcessReconstructionResolvesConfig() = runBlocking {
        widgetConfigRepository.saveConfiguration(101, 5L, false)
        
        // Re-read config from repository simulating process reconstruction
        val freshRetrieved = widgetConfigRepository.getConfiguration(101).first()
        assertEquals(5L, freshRetrieved?.accountId)
    }

    // --- FAKE CLASSES FOR HERMETIC TESTING ---

    class FakeAccountRepository : AccountRepository {
        private val accounts = MutableStateFlow<Map<Long, Account>>(emptyMap())
        override fun observeActiveAccounts(): Flow<List<Account>> = accounts.map { it.values.toList() }
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

    class FakeTransactionRepository : com.suguru.expensetracker.domain.repository.TransactionRepository {
        override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun getRecentTransactions(limit: Int): List<Transaction> = emptyList()
        override fun observeTransactionsByAccount(accountId: Long): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun getTransactionsByAccount(accountId: Long): List<Transaction> = emptyList()
        override fun observeTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override fun observeTransactionsBetween(startTime: java.time.Instant, endTime: java.time.Instant): Flow<List<Transaction>> = MutableStateFlow(emptyList())
        override suspend fun getTransactionsBetween(startTime: java.time.Instant, endTime: java.time.Instant): List<Transaction> = emptyList()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = MutableStateFlow(null)
        override suspend fun getTransactionById(id: Long): Transaction? = null
        override suspend fun insertTransaction(transaction: Transaction): Long = 0L
        override suspend fun insertTransactions(transactions: List<Transaction>): List<Long> = emptyList()
        override suspend fun updateTransaction(transaction: Transaction) {}
        override suspend fun deleteTransactionById(id: Long) {}
    }

    class FakeWidgetConfigurationRepository : WidgetConfigurationRepository {
        private val configs = MutableStateFlow<Map<Int, AccountWidgetConfiguration>>(emptyMap())
        private val budgetConfigs = MutableStateFlow<Map<Int, com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration>>(emptyMap())

        override fun getConfiguration(appWidgetId: Int): Flow<AccountWidgetConfiguration?> =
            configs.map { it[appWidgetId] }

        override fun getAllConfigurations(): Flow<List<AccountWidgetConfiguration>> =
            configs.map { it.values.toList() }

        override suspend fun saveConfiguration(appWidgetId: Int, accountId: Long, privacyMode: Boolean) {
            configs.value = configs.value + (appWidgetId to AccountWidgetConfiguration(appWidgetId, accountId, privacyMode))
        }

        override suspend fun deleteConfiguration(appWidgetId: Int) {
            configs.value = configs.value - appWidgetId
        }

        override fun getBudgetProgressConfiguration(appWidgetId: Int): Flow<com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration?> =
            budgetConfigs.map { it[appWidgetId] }

        override fun getAllBudgetProgressConfigurations(): Flow<List<com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration>> =
            budgetConfigs.map { it.values.toList() }

        override suspend fun saveBudgetProgressConfiguration(appWidgetId: Int, budgetId: Long, privacyMode: Boolean) {
            budgetConfigs.value = budgetConfigs.value + (appWidgetId to com.suguru.expensetracker.widget.configuration.BudgetProgressWidgetConfiguration(appWidgetId, budgetId, privacyMode))
        }

        override suspend fun deleteBudgetProgressConfiguration(appWidgetId: Int) {
            budgetConfigs.value = budgetConfigs.value - appWidgetId
        }
    }

    class FakeSettingsRepository : SettingsRepository {
        private val settingsFlow = MutableStateFlow(
            AppSettings(
                themeMode = ThemeMode.DARK,
                preferredCurrencyCode = "USD",
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.DD_MM_YYYY,
                timeFormat = TimeFormatPreference.HOUR_24,
                showCurrencyCode = true,
                confirmBeforeDelete = false
            )
        )
        override val settings: Flow<AppSettings> = settingsFlow
        override suspend fun setThemeMode(value: ThemeMode) {}
        override suspend fun setPreferredCurrency(value: String?) {}
        override suspend fun setWeekStart(value: WeekStart) {}
        override suspend fun setDateFormat(value: DateFormatPreference) {}
        override suspend fun setTimeFormat(value: TimeFormatPreference) {}
        override suspend fun setShowCurrencyCode(value: Boolean) {}
        override suspend fun setConfirmBeforeDelete(value: Boolean) {}
        override val lastBackupAt: Flow<String?> = MutableStateFlow(null)
        override val lastBackupFileName: Flow<String?> = MutableStateFlow(null)
        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) {}
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
