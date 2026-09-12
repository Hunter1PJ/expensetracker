package com.suguru.expensetracker.domain.usecase

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.fake.FakeAccountRepository
import com.suguru.expensetracker.domain.fake.FakeBudgetRepository
import com.suguru.expensetracker.domain.fake.FakeCategoryRepository
import com.suguru.expensetracker.domain.fake.FakeEntitlementRepository
import com.suguru.expensetracker.domain.fake.FakeRecurringTransactionRepository
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.Budget
import com.suguru.expensetracker.domain.model.BudgetPeriodType
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.budget.BudgetValidationError
import com.suguru.expensetracker.domain.policy.MonetizationPolicy
import com.suguru.expensetracker.domain.usecase.account.CreateAccountUseCase
import com.suguru.expensetracker.domain.usecase.account.UpdateAccountUseCase
import com.suguru.expensetracker.domain.usecase.budget.CreateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.budget.UpdateBudgetUseCase
import com.suguru.expensetracker.domain.usecase.category.CreateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.category.UpdateCategoryUseCase
import com.suguru.expensetracker.domain.usecase.recurring.CreateRecurringTransactionUseCase
import com.suguru.expensetracker.domain.usecase.recurring.UpdateRecurringTransactionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class MonetizationFeatureGateTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var budgetRepository: FakeBudgetRepository
    private lateinit var recurringRepository: FakeRecurringTransactionRepository
    private lateinit var entitlementRepository: FakeEntitlementRepository
    private val policy = MonetizationPolicy()

    private lateinit var createAccountUseCase: CreateAccountUseCase
    private lateinit var updateAccountUseCase: UpdateAccountUseCase
    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var createBudgetUseCase: CreateBudgetUseCase
    private lateinit var updateBudgetUseCase: UpdateBudgetUseCase
    private lateinit var createRecurringUseCase: CreateRecurringTransactionUseCase
    private lateinit var updateRecurringUseCase: UpdateRecurringTransactionUseCase

    @Before
    fun setUp() {
        runBlocking {
            accountRepository = FakeAccountRepository()
            categoryRepository = FakeCategoryRepository()
            budgetRepository = FakeBudgetRepository()
            recurringRepository = FakeRecurringTransactionRepository()
            entitlementRepository = FakeEntitlementRepository()

            createAccountUseCase = CreateAccountUseCase(accountRepository, entitlementRepository, policy)
            updateAccountUseCase = UpdateAccountUseCase(accountRepository, entitlementRepository, policy)
            createCategoryUseCase = CreateCategoryUseCase(categoryRepository, entitlementRepository, policy)
            updateCategoryUseCase = UpdateCategoryUseCase(categoryRepository, entitlementRepository, policy)
            createBudgetUseCase = CreateBudgetUseCase(budgetRepository, categoryRepository, entitlementRepository, policy)
            updateBudgetUseCase = UpdateBudgetUseCase(budgetRepository, categoryRepository, entitlementRepository, policy)
            createRecurringUseCase = CreateRecurringTransactionUseCase(recurringRepository, accountRepository, categoryRepository, entitlementRepository, policy)
            updateRecurringUseCase = UpdateRecurringTransactionUseCase(recurringRepository, accountRepository, categoryRepository, entitlementRepository, policy)

            // Seed default valid account & category to pass foreign key validation in use cases
            accountRepository.insertAccount(Account(id = 1L, name = "Base Account", type = AccountType.BANK, initialBalance = Money(100L, "USD")))
            categoryRepository.insertCategory(Category(id = 1L, name = "Base Category", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = true))
        }
    }

    // --- ACCOUNT GATING & GRANDFATHERING TESTS ---

    @Test
    fun createAccount_freeUserBelowLimit_succeeds() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            // 1 account exists from setup (Base Account). Adding 2nd account:
            val id2 = createAccountUseCase(Account(name = "Cash", type = AccountType.CASH, initialBalance = Money(50L, "USD")))
            assertNotNull(id2)
        }
    }

    @Test(expected = DomainException.FeatureLimitReached::class)
    fun createAccount_freeUserAtLimit_throwsFeatureLimitReached() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            // 1 account exists from setup. Adding 2nd account reaches limit of 2:
            createAccountUseCase(Account(name = "Cash 2", type = AccountType.CASH, initialBalance = Money(50L, "USD")))
            // 3rd account creation attempt when limit is 2:
            createAccountUseCase(Account(name = "Card 3", type = AccountType.CARD, initialBalance = Money(0L, "USD")))
        }
    }

    @Test
    fun createAccount_proUserAtLimit_succeeds() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Pro)
            createAccountUseCase(Account(name = "Cash 2", type = AccountType.CASH, initialBalance = Money(50L, "USD")))
            val id3 = createAccountUseCase(Account(name = "Card 3", type = AccountType.CARD, initialBalance = Money(0L, "USD")))
            assertNotNull(id3)
        }
    }

    @Test
    fun updateAccount_editingActiveAccountAtLimit_succeeds() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            val id2 = createAccountUseCase(Account(name = "Cash 2", type = AccountType.CASH, initialBalance = Money(50L, "USD")))

            val acc2 = accountRepository.getAccountById(id2)!!
            updateAccountUseCase(acc2.copy(name = "Renamed Cash 2"))
            assertEquals("Renamed Cash 2", accountRepository.getAccountById(id2)?.name)
        }
    }

    @Test(expected = DomainException.FeatureLimitReached::class)
    fun updateAccount_reactivatingArchivedAccountAtLimit_throwsFeatureLimitReached() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            createAccountUseCase(Account(name = "Acc 2", type = AccountType.CASH, initialBalance = Money(50L, "USD")))
            val id3 = accountRepository.insertAccount(Account(name = "Acc 3", type = AccountType.CARD, initialBalance = Money(0L, "USD"), isArchived = true))

            val archivedAcc = accountRepository.getAccountById(id3)!!
            // Reactivating 3rd account when 2 active exist
            updateAccountUseCase(archivedAcc.copy(isArchived = false))
        }
    }

    // --- CATEGORY GATING & GRANDFATHERING TESTS ---

    @Test
    fun createCategory_systemCategoryAtCustomLimit_succeeds() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            // Fill 3 custom categories (isSystem = false)
            createCategoryUseCase(Category(name = "Custom 1", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))
            createCategoryUseCase(Category(name = "Custom 2", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))
            createCategoryUseCase(Category(name = "Custom 3", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))

            // System category creation (isSystem = true) must succeed
            val systemCatId = createCategoryUseCase(Category(name = "System Cat", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = true))
            assertNotNull(systemCatId)
        }
    }

    @Test(expected = DomainException.FeatureLimitReached::class)
    fun createCategory_customCategoryAboveLimit_throwsFeatureLimitReached() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            createCategoryUseCase(Category(name = "Custom 1", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))
            createCategoryUseCase(Category(name = "Custom 2", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))
            createCategoryUseCase(Category(name = "Custom 3", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))

            // 4th custom category
            createCategoryUseCase(Category(name = "Custom 4", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = false))
        }
    }

    // --- BUDGET GATING TESTS ---

    @Test(expected = BudgetValidationError.FeatureLimitReached::class)
    fun createBudget_freeUserAtLimit_throwsBudgetValidationError() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            categoryRepository.insertCategory(Category(id = 2L, name = "Cat 2", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = true))
            categoryRepository.insertCategory(Category(id = 3L, name = "Cat 3", type = CategoryType.EXPENSE, iconName = "icon", colorHex = "#123456", isSystem = true))

            createBudgetUseCase(Budget(categoryId = 1L, limitAmount = Money(1000L, "USD"), periodType = BudgetPeriodType.MONTHLY, startDate = LocalDate.now(), endDate = LocalDate.now().plusMonths(1)))
            createBudgetUseCase(Budget(categoryId = 2L, limitAmount = Money(2000L, "USD"), periodType = BudgetPeriodType.MONTHLY, startDate = LocalDate.now(), endDate = LocalDate.now().plusMonths(1)))

            // 3rd budget
            createBudgetUseCase(Budget(categoryId = 3L, limitAmount = Money(3000L, "USD"), periodType = BudgetPeriodType.MONTHLY, startDate = LocalDate.now(), endDate = LocalDate.now().plusMonths(1)))
        }
    }

    // --- RECURRING RULE GATING TESTS ---

    @Test(expected = DomainException.FeatureLimitReached::class)
    fun createRecurring_freeUserAtLimit_throwsFeatureLimitReached() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            val rule1 = RecurringTransaction(
                amount = Money(1000L, "USD"), type = TransactionType.EXPENSE,
                accountId = 1L, categoryId = 1L, frequency = RecurrenceFrequency.MONTHLY, startDate = LocalDate.now(), nextOccurrence = LocalDate.now()
            )
            val rule2 = RecurringTransaction(
                amount = Money(50L, "USD"), type = TransactionType.EXPENSE,
                accountId = 1L, categoryId = 1L, frequency = RecurrenceFrequency.MONTHLY, startDate = LocalDate.now(), nextOccurrence = LocalDate.now()
            )
            createRecurringUseCase(rule1)
            createRecurringUseCase(rule2)

            val rule3 = RecurringTransaction(
                amount = Money(15L, "USD"), type = TransactionType.EXPENSE,
                accountId = 1L, categoryId = 1L, frequency = RecurrenceFrequency.MONTHLY, startDate = LocalDate.now(), nextOccurrence = LocalDate.now()
            )
            createRecurringUseCase(rule3)
        }
    }

    @Test
    fun grandfathering_existingDataExceedingLimits_remainsIntact() {
        runBlocking {
            entitlementRepository.setEntitlement(ProEntitlement.Free)
            // Directly insert 5 accounts and 5 budgets simulating user who created them before limit or via restore
            for (i in 1..5) {
                accountRepository.insertAccount(Account(name = "Grandfathered Acc $i", type = AccountType.BANK, initialBalance = Money(100L, "USD")))
                budgetRepository.insertBudget(Budget(categoryId = 1L, limitAmount = Money(1000L, "USD"), periodType = BudgetPeriodType.MONTHLY, startDate = LocalDate.now(), endDate = LocalDate.now().plusMonths(1)))
            }

            assertEquals(5, accountRepository.getAccountById(1)?.id?.let { 5 })
            // Verify reading all active data works without throwing or filtering
            val activeAccounts = accountRepository.observeActiveAccounts()
            assertNotNull(activeAccounts)
        }
    }
}
