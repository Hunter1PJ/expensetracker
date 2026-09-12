package com.suguru.expensetracker.domain.usecase.budget

import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeBudgetRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.Transaction
import com.suguru.expensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ObserveActiveBudgetProgressUseCaseTest {

    private lateinit var budgetRepo: FakeBudgetRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var useCase: ObserveActiveBudgetProgressUseCase

    @Before
    fun setUp() {
        budgetRepo = FakeBudgetRepository()
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        categoryRepo = FakeCategoryRepository()
        useCase = ObserveActiveBudgetProgressUseCase(
            budgetRepository = budgetRepo,
            transactionRepository = transactionRepo,
            accountRepository = accountRepo,
            categoryRepository = categoryRepo
        )
    }

    @Test
    fun `overall budget progress correctly calculates expense total`() = runBlocking {
        // Setup Account
        val accountId = accountRepo.insertAccount(
            Account(id = 1L, name = "Checking", type = AccountType.BANK, initialBalance = Money(0L, "USD"))
        )

        // Setup Categories
        val foodCatId = categoryRepo.insertCategory(
            Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )
        val salaryCatId = categoryRepo.insertCategory(
            Category(id = 2L, name = "Salary", type = CategoryType.INCOME, iconName = "work", colorHex = "#3B82F6")
        )

        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 30)

        // Insert Budget
        budgetRepo.insertBudget(
            Budget(
                id = 100L,
                categoryId = null,
                limitAmount = Money(50000L, "USD"), // $500.00
                periodType = BudgetPeriodType.MONTHLY,
                startDate = startDate,
                endDate = endDate,
                isActive = true
            )
        )

        // Insert Expenses ($100.00 and $200.00)
        val sep15 = startDate.plusDays(14).atStartOfDay(ZoneOffset.UTC).toInstant()
        transactionRepo.insertTransaction(
            Transaction(
                id = 1L,
                type = TransactionType.EXPENSE,
                amount = Money(10000L, "USD"),
                accountId = accountId,
                categoryId = foodCatId,
                transactionTime = sep15
            )
        )
        transactionRepo.insertTransaction(
            Transaction(
                id = 2L,
                type = TransactionType.EXPENSE,
                amount = Money(20000L, "USD"),
                accountId = accountId,
                categoryId = foodCatId,
                transactionTime = sep15
            )
        )

        // Insert Income ($1,000.00) - Should be excluded
        transactionRepo.insertTransaction(
            Transaction(
                id = 3L,
                type = TransactionType.INCOME,
                amount = Money(100000L, "USD"),
                accountId = accountId,
                categoryId = salaryCatId,
                transactionTime = sep15
            )
        )

        val progressList = useCase(ZoneOffset.UTC).first()
        System.err.println("DEBUG progressList test1: size=${progressList.size}, items=$progressList")

        assertEquals(1, progressList.size)
        val progress = progressList.first()

        assertEquals(30000L, progress.spent.amountInMinorUnits) // $300.00
        assertEquals(20000L, progress.remaining.amountInMinorUnits) // $200.00
        assertEquals(6000, progress.progressBasisPoints) // 60.00%
        assertFalse(progress.isExceeded)
    }

    @Test
    fun `category budget filters transactions by category`() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(id = 1L, name = "Checking", type = AccountType.BANK, initialBalance = Money(0L, "USD"))
        )

        val foodCatId = categoryRepo.insertCategory(
            Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )
        val techCatId = categoryRepo.insertCategory(
            Category(id = 2L, name = "Tech", type = CategoryType.EXPENSE, iconName = "computer", colorHex = "#3B82F6")
        )

        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 30)
        val sep10 = startDate.plusDays(9).atStartOfDay(ZoneOffset.UTC).toInstant()

        // Budget for Food only ($100.00 limit)
        budgetRepo.insertBudget(
            Budget(
                id = 100L,
                categoryId = foodCatId,
                limitAmount = Money(10000L, "USD"),
                periodType = BudgetPeriodType.MONTHLY,
                startDate = startDate,
                endDate = endDate,
                isActive = true
            )
        )

        // Expense on Food ($50.00)
        transactionRepo.insertTransaction(
            Transaction(
                id = 1L,
                type = TransactionType.EXPENSE,
                amount = Money(5000L, "USD"),
                accountId = accountId,
                categoryId = foodCatId,
                transactionTime = sep10
            )
        )

        // Expense on Tech ($150.00) -> Should NOT count towards Food budget
        transactionRepo.insertTransaction(
            Transaction(
                id = 2L,
                type = TransactionType.EXPENSE,
                amount = Money(15000L, "USD"),
                accountId = accountId,
                categoryId = techCatId,
                transactionTime = sep10
            )
        )

        val progressList = useCase(ZoneOffset.UTC).first()
        val progress = progressList.first()

        assertEquals(5000L, progress.spent.amountInMinorUnits)
        assertEquals(5000L, progress.remaining.amountInMinorUnits)
        assertEquals(5000, progress.progressBasisPoints) // 50.00%
        assertFalse(progress.isExceeded)
    }

    @Test
    fun `budget detects when limit is exceeded`() = runBlocking {
        val accountId = accountRepo.insertAccount(
            Account(id = 1L, name = "Checking", type = AccountType.BANK, initialBalance = Money(0L, "USD"))
        )
        val foodCatId = categoryRepo.insertCategory(
            Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, iconName = "fastfood", colorHex = "#10B981")
        )

        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 30)
        val sep10 = startDate.plusDays(9).atStartOfDay(ZoneOffset.UTC).toInstant()

        // Budget $100.00
        budgetRepo.insertBudget(
            Budget(
                id = 100L,
                categoryId = foodCatId,
                limitAmount = Money(10000L, "USD"),
                periodType = BudgetPeriodType.MONTHLY,
                startDate = startDate,
                endDate = endDate,
                isActive = true
            )
        )

        // Expense $150.00
        transactionRepo.insertTransaction(
            Transaction(
                id = 1L,
                type = TransactionType.EXPENSE,
                amount = Money(15000L, "USD"),
                accountId = accountId,
                categoryId = foodCatId,
                transactionTime = sep10
            )
        )

        val progressList = useCase(ZoneOffset.UTC).first()
        val progress = progressList.first()

        assertEquals(15000L, progress.spent.amountInMinorUnits)
        assertEquals(0L, progress.remaining.amountInMinorUnits)
        assertEquals(15000, progress.progressBasisPoints) // 150.00%
        assertTrue(progress.isExceeded)
    }
}
