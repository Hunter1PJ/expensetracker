package com.example.domain.usecase.dashboard

import com.example.domain.model.RecentTransactionDetail
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes the most recent transactions enriched with account and category data.
 */
class ObserveRecentTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(limit: Int = 5): Flow<List<RecentTransactionDetail>> {
        return combine(
            transactionRepository.observeRecentTransactions(limit),
            accountRepository.observeAllAccounts(),
            categoryRepository.observeAllCategories()
        ) { recentTransactions, accounts, categories ->
            val accountMap = accounts.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }

            recentTransactions.map { tx ->
                RecentTransactionDetail(
                    transaction = tx,
                    account = accountMap[tx.accountId],
                    destinationAccount = tx.destinationAccountId?.let { accountMap[it] },
                    category = tx.categoryId?.let { categoryMap[it] }
                )
            }
        }
    }
}
