package com.example.domain.usecase.budget

import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.domain.model.budget.BudgetProgress
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

class ObserveActiveBudgetProgressUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<BudgetProgress>> {
        return combine(
            budgetRepository.observeActiveBudgets(),
            transactionRepository.observeAllTransactions(),
            accountRepository.observeAllAccounts(),
            categoryRepository.observeAllCategories()
        ) { budgets, transactions, accounts, categories ->
            val accountMap = accounts.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }

            budgets.map { budget ->
                val currencyCode = budget.limitAmount.currencyCode
                val startInstant = budget.startDate.atStartOfDay(zoneId).toInstant()
                val endInstant = budget.endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                // Filter matching EXPENSE transactions only
                val matchingExpenses = transactions.filter { tx ->
                    if (tx.type != TransactionType.EXPENSE) return@filter false
                    if (tx.transactionTime.isBefore(startInstant) || !tx.transactionTime.isBefore(endInstant)) return@filter false

                    if (tx.amount.currencyCode != currencyCode) return@filter false

                    if (budget.categoryId != null) {
                        tx.categoryId == budget.categoryId
                    } else {
                        true
                    }
                }

                // Calculate spent sum using Math.addExact
                var spentMinor = 0L
                for (tx in matchingExpenses) {
                    spentMinor = Math.addExact(spentMinor, tx.amount.amountInMinorUnits)
                }

                val limitMinor = budget.limitAmount.amountInMinorUnits
                val remainingMinor = if (spentMinor >= limitMinor) 0L else Math.subtractExact(limitMinor, spentMinor)

                val spentMoney = Money(spentMinor, currencyCode)
                val remainingMoney = Money(remainingMinor, currencyCode)

                val isExceeded = spentMinor > limitMinor

                val basisPoints = if (limitMinor <= 0L) 0 else {
                    try {
                        val num = Math.multiplyExact(spentMinor, 10000L)
                        (num / limitMinor).toInt()
                    } catch (e: ArithmeticException) {
                        val ratio = spentMinor.toDouble() / limitMinor.toDouble()
                        (ratio * 10000).toInt()
                    }
                }

                val category = budget.categoryId?.let { categoryMap[it] }

                BudgetProgress(
                    budget = budget,
                    categoryName = category?.name,
                    categoryIconName = category?.iconName,
                    categoryColorHex = category?.colorHex,
                    spent = spentMoney,
                    remaining = remainingMoney,
                    progressBasisPoints = basisPoints,
                    isExceeded = isExceeded
                )
            }.sortedWith(
                compareBy<BudgetProgress> { it.budget.limitAmount.currencyCode }
                    .thenBy { it.budget.categoryId != null } // Overall budgets first
                    .thenBy { it.categoryName ?: "" }
                    .thenByDescending { it.budget.startDate }
            )
        }
    }
}
