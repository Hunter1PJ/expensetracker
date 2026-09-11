package com.example.domain.util

import com.example.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {

    @Test
    fun `calculateNextOccurrence DAILY advances by 1 day`() {
        val startDate = LocalDate.of(2026, 9, 1)
        val currentOccurrence = LocalDate.of(2026, 9, 1)
        val next = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.DAILY,
            startDate = startDate,
            currentOccurrence = currentOccurrence
        )
        assertEquals(LocalDate.of(2026, 9, 2), next)
    }

    @Test
    fun `calculateNextOccurrence WEEKLY advances by 7 days`() {
        val startDate = LocalDate.of(2026, 9, 1)
        val currentOccurrence = LocalDate.of(2026, 9, 1)
        val next = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.WEEKLY,
            startDate = startDate,
            currentOccurrence = currentOccurrence
        )
        assertEquals(LocalDate.of(2026, 9, 8), next)
    }

    @Test
    fun `calculateNextOccurrence BIWEEKLY advances by 14 days`() {
        val startDate = LocalDate.of(2026, 9, 1)
        val currentOccurrence = LocalDate.of(2026, 9, 1)
        val next = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.BIWEEKLY,
            startDate = startDate,
            currentOccurrence = currentOccurrence
        )
        assertEquals(LocalDate.of(2026, 9, 15), next)
    }

    @Test
    fun `calculateNextOccurrence MONTHLY preserves the original start day anchor`() {
        val startDate = LocalDate.of(2026, 1, 31)
        
        // 1. Jan 31 -> Feb 28 (non-leap year February length limits it to 28)
        val next1 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = startDate,
            currentOccurrence = startDate
        )
        assertEquals(LocalDate.of(2026, 2, 28), next1)

        // 2. Feb 28 -> Mar 31 (re-anchors to 31 because March has 31 days)
        val next2 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = startDate,
            currentOccurrence = next1
        )
        assertEquals(LocalDate.of(2026, 3, 31), next2)

        // 3. Mar 31 -> Apr 30 (limits to 30)
        val next3 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = startDate,
            currentOccurrence = next2
        )
        assertEquals(LocalDate.of(2026, 4, 30), next3)

        // 4. Apr 30 -> May 31 (re-anchors to 31)
        val next4 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = startDate,
            currentOccurrence = next3
        )
        assertEquals(LocalDate.of(2026, 5, 31), next4)
    }

    @Test
    fun `calculateNextOccurrence YEARLY leap year Feb 29 anchoring`() {
        val startDate = LocalDate.of(2024, 2, 29) // Leap year

        // 1. 2024-02-29 -> 2025-02-28
        val next1 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.YEARLY,
            startDate = startDate,
            currentOccurrence = startDate
        )
        assertEquals(LocalDate.of(2025, 2, 28), next1)

        // 2. 2025-02-28 -> 2026-02-28
        val next2 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.YEARLY,
            startDate = startDate,
            currentOccurrence = next1
        )
        assertEquals(LocalDate.of(2026, 2, 28), next2)

        // 3. 2026-02-28 -> 2027-02-28
        val next3 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.YEARLY,
            startDate = startDate,
            currentOccurrence = next2
        )
        assertEquals(LocalDate.of(2027, 2, 28), next3)

        // 4. 2027-02-28 -> 2028-02-29 (Leap year, re-anchors to Feb 29)
        val next4 = RecurrenceCalculator.calculateNextOccurrence(
            frequency = RecurrenceFrequency.YEARLY,
            startDate = startDate,
            currentOccurrence = next3
        )
        assertEquals(LocalDate.of(2028, 2, 29), next4)
    }

    @Test
    fun `isExpired checks if date exceeds optional end date`() {
        val endDate = LocalDate.of(2026, 12, 31)

        assertFalse(RecurrenceCalculator.isExpired(null, LocalDate.of(2027, 1, 1)))
        assertFalse(RecurrenceCalculator.isExpired(endDate, LocalDate.of(2026, 12, 31)))
        assertTrue(RecurrenceCalculator.isExpired(endDate, LocalDate.of(2027, 1, 1)))
    }
}
