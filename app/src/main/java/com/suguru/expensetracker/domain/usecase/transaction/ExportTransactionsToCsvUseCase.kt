package com.suguru.expensetracker.domain.usecase.transaction

import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.lang.StringBuilder

class ExportTransactionsToCsvUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(): String {
        val transactions = transactionRepository.observeAllTransactions().first()
        val accountsMap = accountRepository.observeAllAccounts().first().associateBy { it.id }
        val categoriesMap = categoryRepository.observeAllCategories().first().associateBy { it.id }

        val sb = StringBuilder()
        // Headers
        sb.append("ID,Date,Type,Amount Minor Units,Currency,Account,Destination Account,Category,Note,Recurring Rule ID,Recurring Occurrence Date\n")

        for (tx in transactions) {
            val accountName = accountsMap[tx.accountId]?.name ?: "Unknown Account (${tx.accountId})"
            val destAccountName = tx.destinationAccountId?.let { id ->
                accountsMap[id]?.name ?: "Unknown Account ($id)"
            } ?: ""
            val categoryName = tx.categoryId?.let { id ->
                categoriesMap[id]?.name ?: "Unknown Category ($id)"
            } ?: ""

            val columns = listOf(
                tx.id.toString(),
                tx.transactionTime.toString(),
                tx.type.name,
                tx.amount.amountInMinorUnits.toString(),
                tx.amount.currencyCode,
                accountName,
                destAccountName,
                categoryName,
                tx.note ?: "",
                tx.recurringRuleId?.toString() ?: "",
                tx.recurringOccurrenceDate?.toString() ?: ""
            )

            val line = columns.joinToString(",") { it.escapeCsv() }
            sb.append(line).append("\n")
        }

        return sb.toString()
    }

    private fun String.escapeCsv(): String {
        val containsSpecial = this.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (containsSpecial) {
            "\"" + this.replace("\"", "\"\"") + "\""
        } else {
            this
        }
    }
}
