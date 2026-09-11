package com.example.domain.usecase.recurring

import com.example.domain.model.RecurringTransaction
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.RecurringOccurrenceRepository
import com.example.domain.repository.RecurringTransactionRepository
import com.example.domain.util.RecurrenceCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Idempotent financial use case for executing due recurring transaction rules up to [asOfDate].
 */
class ProcessDueRecurringTransactionsUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val recurringOccurrenceRepository: RecurringOccurrenceRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        asOfDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val dueRules = recurringTransactionRepository.getRecurringTransactionsDue(asOfDate)
        var totalGeneratedCount = 0

        for (rule in dueRules) {
            if (!rule.isActive) continue

            var currentRule = rule
            var iterationCount = 0
            val maxCatchUpLimit = 500

            while (currentRule.isActive && currentRule.nextOccurrence <= asOfDate && iterationCount < maxCatchUpLimit) {
                iterationCount++
                val occurrenceDate = currentRule.nextOccurrence

                // Check if current occurrence date exceeds end date
                if (RecurrenceCalculator.isExpired(currentRule.endDate, occurrenceDate)) {
                    val deactivatedRule = currentRule.copy(isActive = false)
                    recurringTransactionRepository.updateRecurringTransaction(deactivatedRule)
                    break
                }

                // Check account & category health. If archived/missing, do NOT generate and do NOT advance.
                if (!isRuleValidForExecution(currentRule)) {
                    break
                }

                // Calculate next occurrence date after this one
                val nextDate = RecurrenceCalculator.calculateNextOccurrence(
                    frequency = currentRule.frequency,
                    startDate = currentRule.startDate,
                    currentOccurrence = occurrenceDate
                )

                val isStillActive = !RecurrenceCalculator.isExpired(currentRule.endDate, nextDate)

                val txTime = occurrenceDate.atStartOfDay(zoneId).toInstant()
                val transactionToInsert = Transaction(
                    id = 0L,
                    type = currentRule.type,
                    amount = currentRule.amount,
                    accountId = currentRule.accountId,
                    destinationAccountId = currentRule.destinationAccountId,
                    categoryId = currentRule.categoryId,
                    transactionTime = txTime,
                    note = currentRule.note,
                    recurringRuleId = currentRule.id,
                    recurringOccurrenceDate = occurrenceDate,
                    createdAt = Instant.now()
                )

                val insertedId = recurringOccurrenceRepository.processAtomicOccurrence(
                    rule = currentRule,
                    occurrenceDate = occurrenceDate,
                    transactionToInsert = transactionToInsert,
                    newNextOccurrence = nextDate,
                    isRuleNowActive = isStillActive
                )

                if (insertedId != null) {
                    totalGeneratedCount++
                }

                currentRule = currentRule.copy(
                    nextOccurrence = nextDate,
                    isActive = isStillActive
                )
            }
        }

        return totalGeneratedCount
    }

    private suspend fun isRuleValidForExecution(rule: RecurringTransaction): Boolean {
        val sourceAccount = accountRepository.getAccountById(rule.accountId) ?: return false
        if (sourceAccount.isArchived) return false

        if (rule.type == TransactionType.TRANSFER) {
            val destId = rule.destinationAccountId ?: return false
            val destAccount = accountRepository.getAccountById(destId) ?: return false
            if (destAccount.isArchived) return false
        } else {
            val catId = rule.categoryId ?: return false
            val category = categoryRepository.getCategoryById(catId) ?: return false
            if (category.isArchived) return false
        }

        return true
    }
}
