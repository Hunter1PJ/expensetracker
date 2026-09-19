package com.suguru.expensetracker.domain.usecase.export

import com.suguru.expensetracker.data.export.CsvExporter
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.export.ExportTransactionRow
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.time.ZoneId

/**
 * Generates an unfiltered, comprehensive CSV export of all user transactions.
 * Available to all FREE and PRO users to ensure complete data portability.
 */
class GenerateBasicCsvExportUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val csvExporter: CsvExporter
) {

    suspend fun exportToStream(
        outputStream: OutputStream,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val rows = getBasicExportRows()
        csvExporter.exportToStream(rows, outputStream, zoneId)
    }

    suspend fun exportToString(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val rows = getBasicExportRows()
        return csvExporter.exportToString(rows, zoneId)
    }

    suspend fun getBasicExportRows(): List<ExportTransactionRow> {
        val transactions = transactionRepository.observeAllTransactions().first()
        val accounts = accountRepository.observeAllAccounts().first().associateBy { it.id }
        val categories = categoryRepository.observeAllCategories().first().associateBy { it.id }

        return transactions.map { tx ->
            val sourceAccountName = accounts[tx.accountId]?.name ?: "Unknown Account (${tx.accountId})"
            val destAccountName = if (tx.type == TransactionType.TRANSFER) {
                tx.destinationAccountId?.let { id ->
                    accounts[id]?.name ?: "Unknown Account ($id)"
                }
            } else null

            val categoryName = if (tx.type != TransactionType.TRANSFER) {
                tx.categoryId?.let { id ->
                    categories[id]?.name ?: "Unknown Category ($id)"
                }
            } else null

            ExportTransactionRow(
                transactionId = tx.id,
                transactionTime = tx.transactionTime,
                type = tx.type,
                amount = tx.amount,
                currencyCode = tx.amount.currencyCode,
                sourceAccountName = sourceAccountName,
                destinationAccountName = destAccountName,
                categoryName = categoryName,
                note = tx.note,
                recurringRuleId = tx.recurringRuleId,
                recurringOccurrenceDate = tx.recurringOccurrenceDate
            )
        }
    }
}
