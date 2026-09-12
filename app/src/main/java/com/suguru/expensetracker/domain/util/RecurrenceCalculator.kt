package com.suguru.expensetracker.domain.util

import com.suguru.expensetracker.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure, deterministic calculator for scheduled recurring transactions.
 * Decoupled from Android framework and system clocks.
 */
object RecurrenceCalculator {

    /**
     * Calculates the next occurrence date given frequency, original anchor start date,
     * and current occurrence date.
     */
    fun calculateNextOccurrence(
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        currentOccurrence: LocalDate
    ): LocalDate {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> currentOccurrence.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentOccurrence.plusDays(7)
            RecurrenceFrequency.BIWEEKLY -> currentOccurrence.plusDays(14)
            RecurrenceFrequency.MONTHLY -> {
                val nextMonth = currentOccurrence.plusMonths(1)
                val yearMonth = YearMonth.of(nextMonth.year, nextMonth.month)
                val targetDay = minOf(startDate.dayOfMonth, yearMonth.lengthOfMonth())
                LocalDate.of(nextMonth.year, nextMonth.month, targetDay)
            }
            RecurrenceFrequency.YEARLY -> {
                val targetYear = currentOccurrence.year + 1
                val targetMonth = startDate.month
                val yearMonth = YearMonth.of(targetYear, targetMonth)
                val targetDay = minOf(startDate.dayOfMonth, yearMonth.lengthOfMonth())
                LocalDate.of(targetYear, targetMonth, targetDay)
            }
        }
    }

    /**
     * Determines whether an occurrence date exceeds the rule's optional end date.
     */
    fun isExpired(endDate: LocalDate?, date: LocalDate): Boolean {
        return endDate != null && date > endDate
    }
}
