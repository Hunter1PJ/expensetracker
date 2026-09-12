package com.suguru.expensetracker.domain.usecase

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveAccountSummariesUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveDashboardSummaryUseCase
import com.suguru.expensetracker.domain.usecase.dashboard.ObserveRecentTransactionsUseCase
import com.suguru.expensetracker.domain.usecase.transaction.CreateTransactionUseCase
import com.suguru.expensetracker.domain.util.DateTimeRangeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class DashboardUseCaseTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository

    private lateinit var createTransactionUseCase: CreateTransactionUseCase
    private lateinit var observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase
    private lateinit var observeAccountSummariesUseCase: ObserveAccountSummariesUseCase
    private lateinit var observeRecentTransactionsUseCase: ObserveRecentTransactionsUseCase

    private var usdBankAccountId: Long = 0L
    private var usdCashAccountId: Long = 0L
    private var uzsBankAccountId: Long = 0L

    private var salaryCategoryId: Long = 0L
    private var foodCategoryId: Long = 0L
    private var rentCategoryId: Long = 0L

    private val testZoneId = ZoneId.of("UTC")
    private val testYearMonth = YearMonth.of(2026, 9)
    private val monthRange = DateTimeRangeUtils.getMonthRange(testYearMonth, testZoneId)
    private val monthStart: Instant get() = monthRange.first
    private val monthEnd: Instant get() = monthRange.second

    @Before
    fun setUp() {
        runBlocking {
            accountRepository = FakeAccountRepository()
            categoryRepository = FakeCategoryRepository()
            transactionRepository = FakeTransactionRepository()

            createTransactionUseCase = CreateTransactionUseCase(transactionRepository, accountRepository, categoryRepository)
            observeDashboardSummaryUseCase = ObserveDashboardSummaryUseCase(accountRepository, transactionRepository)
            observeAccountSummariesUseCase = ObserveAccountSummariesUseCase(accountRepository, transactionRepository)
            observeRecentTransactionsUseCase = ObserveRecentTransactionsUseCase(transactionRepository, accountRepository, categoryRepository)

            // USD Bank Account: initial $1,000.00 (100,000 minor units)
            usdBankAccountId = accountRepository.insertAccount(
                Account(name = "Chase Bank", type = AccountType.BANK, initialBalance = Money(100000L, "USD"))
            )

            // USD Cash Account: initial $200.00 (20,000 minor units)
            usdCashAccountId = accountRepository.insertAccount(
                Account(name = "Wallet", type = AccountType.CASH, initialBalance = Money(20000L, "USD"))
            )

            // UZS Bank Account: initial 5,000,000 so'm (5,000,000 minor units, 0 decimals)
            uzsBankAccountId = accountRepository.insertAccount(
                Account(name = "Ipak Yuli Bank", type = AccountType.BANK, initialBalance = Money(5000000L, "UZS"))
            )

            salaryCategoryId = categoryRepository.insertCategory(
                Category(name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#10B981")
            )
            foodCategoryId = categoryRepository.insertCategory(
                Category(name = "Groceries", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#EF4444")
            )
            rentCategoryId = categoryRepository.insertCategory(
                Category(name = "Rent", type = CategoryType.EXPENSE, iconName = "home", colorHex = "#8B5CF6")
            )
        }
    }

    @Test
    fun initialDashboardSummary_groupsByCurrencyWithNoTransactions() {
        runBlocking {
            val summaries = observeDashboardSummaryUseCase(
                startTime = monthStart,
                endTime = monthEnd,
                zoneId = testZoneId
            ).first()

            assertEquals(2, summaries.size)

            val usdSummary = summaries.find { it.currencyCode == "USD" }
            assertNotNull(usdSummary)
            // USD total balance: 100000 + 20000 = 120000 ($1,200.00)
            assertEquals(120000L, usdSummary!!.totalBalance.amountInMinorUnits)
            assertEquals(0L, usdSummary.monthlyIncome.amountInMinorUnits)
            assertEquals(0L, usdSummary.monthlyExpense.amountInMinorUnits)
            assertEquals(0L, usdSummary.monthlyNet.amountInMinorUnits)

            val uzsSummary = summaries.find { it.currencyCode == "UZS" }
            assertNotNull(uzsSummary)
            assertEquals(5000000L, uzsSummary!!.totalBalance.amountInMinorUnits)
            assertEquals(0L, uzsSummary.monthlyIncome.amountInMinorUnits)
            assertEquals(0L, uzsSummary.monthlyExpense.amountInMinorUnits)
            assertEquals(0L, uzsSummary.monthlyNet.amountInMinorUnits)
        }
    }

    @Test
    fun dashboardSummary_calculatesMonthlyIncomeExpenseNet_andExcludesTransfers() {
        runBlocking {
            val midMonthTime = monthStart.plusSeconds(3600 * 24 * 5) // Sep 6, 2026

            // 1. USD Income: +$3,000.00 (300,000 units)
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.INCOME,
                    amount = Money(300000L, "USD"),
                    accountId = usdBankAccountId,
                    categoryId = salaryCategoryId,
                    transactionTime = midMonthTime
                )
            )

            // 2. USD Expense: -$800.00 (80,000 units)
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money(80000L, "USD"),
                    accountId = usdBankAccountId,
                    categoryId = rentCategoryId,
                    transactionTime = midMonthTime
                )
            )

            // 3. USD Transfer: $150.00 (15,000 units) from Chase Bank to Wallet
            // Transfer must NOT increase income or expense
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.TRANSFER,
                    amount = Money(15000L, "USD"),
                    accountId = usdBankAccountId,
                    destinationAccountId = usdCashAccountId,
                    categoryId = null,
                    transactionTime = midMonthTime
                )
            )

            // 4. UZS Expense: -1,200,000 so'm
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = Money(1200000L, "UZS"),
                    accountId = uzsBankAccountId,
                    categoryId = foodCategoryId,
                    transactionTime = midMonthTime
                )
            )

            val summaries = observeDashboardSummaryUseCase(
                startTime = monthStart,
                endTime = monthEnd,
                zoneId = testZoneId
            ).first()

            val usdSummary = summaries.find { it.currencyCode == "USD" }!!
            // Total balance: Initial (120,000) + 300,000 - 80,000 - 15,000 + 15,000 = 340,000 ($3,400.00)
            assertEquals(340000L, usdSummary.totalBalance.amountInMinorUnits)
            // Income: 300,000 ($3,000.00)
            assertEquals(300000L, usdSummary.monthlyIncome.amountInMinorUnits)
            // Expense: 80,000 ($800.00) (Transfer excluded!)
            assertEquals(80000L, usdSummary.monthlyExpense.amountInMinorUnits)
            // Net: 300,000 - 80,000 = 220,000 ($2,200.00)
            assertEquals(220000L, usdSummary.monthlyNet.amountInMinorUnits)

            val uzsSummary = summaries.find { it.currencyCode == "UZS" }!!
            // Total balance: 5,000,000 - 1,200,000 = 3,800,000
            assertEquals(3800000L, uzsSummary.totalBalance.amountInMinorUnits)
            assertEquals(0L, uzsSummary.monthlyIncome.amountInMinorUnits)
            assertEquals(1200000L, uzsSummary.monthlyExpense.amountInMinorUnits)
            assertEquals(-1200000L, uzsSummary.monthlyNet.amountInMinorUnits)
        }
    }

    @Test
    fun observeAccountSummaries_returnsDerivedBalancesAccurately() {
        runBlocking {
            val midMonthTime = monthStart.plusSeconds(3600 * 24 * 3)

            // Transfer $100 from Chase to Wallet
            createTransactionUseCase(
                Transaction(
                    type = TransactionType.TRANSFER,
                    amount = Money(10000L, "USD"),
                    accountId = usdBankAccountId,
                    destinationAccountId = usdCashAccountId,
                    categoryId = null,
                    transactionTime = midMonthTime
                )
            )

            val accountSummaries = observeAccountSummariesUseCase().first()
            assertEquals(3, accountSummaries.size)

            val chase = accountSummaries.find { it.account.id == usdBankAccountId }!!
            assertEquals(90000L, chase.balance.amountInMinorUnits) // 100000 - 10000

            val wallet = accountSummaries.find { it.account.id == usdCashAccountId }!!
            assertEquals(30000L, wallet.balance.amountInMinorUnits) // 20000 + 10000

            val ipak = accountSummaries.find { it.account.id == uzsBankAccountId }!!
            assertEquals(5000000L, ipak.balance.amountInMinorUnits)
        }
    }

    @Test
    fun observeRecentTransactions_ordersByTimestampAndLimitsToCount() {
        runBlocking {
            val t1 = monthStart.plusSeconds(100)
            val t2 = monthStart.plusSeconds(200)
            val t3 = monthStart.plusSeconds(300)
            val t4 = monthStart.plusSeconds(400)
            val t5 = monthStart.plusSeconds(500)
            val t6 = monthStart.plusSeconds(600)

            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(100L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t1))
            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(200L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t2))
            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(300L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t3))
            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(400L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t4))
            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(500L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t5))
            createTransactionUseCase(Transaction(type = TransactionType.INCOME, amount = Money(600L, "USD"), accountId = usdBankAccountId, categoryId = salaryCategoryId, transactionTime = t6))

            val recents = observeRecentTransactionsUseCase(limit = 5).first()
            assertEquals(5, recents.size)
            // Most recent first: t6 (600), t5 (500), t4 (400), t3 (300), t2 (200)
            assertEquals(600L, recents[0].transaction.amount.amountInMinorUnits)
            assertEquals(500L, recents[1].transaction.amount.amountInMinorUnits)
            assertEquals(400L, recents[2].transaction.amount.amountInMinorUnits)
            assertEquals(300L, recents[3].transaction.amount.amountInMinorUnits)
            assertEquals(200L, recents[4].transaction.amount.amountInMinorUnits)

            // Account and Category should be resolved
            assertEquals("Chase Bank", recents[0].account?.name)
            assertEquals("Salary", recents[0].category?.name)
        }
    }
}
