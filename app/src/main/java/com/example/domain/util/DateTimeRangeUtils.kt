package com.example.domain.util

import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Utility for determining deterministic calendar month date boundaries as Instants.
 */
object DateTimeRangeUtils {

    /**
     * Returns the start Instant (inclusive, 00:00:00) and end Instant (inclusive, 23:59:59.999999999)
     * for the given [YearMonth] in the given [ZoneId].
     */
    fun getMonthRange(
        yearMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Instant, Instant> {
        val startInstant = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant()
        val endInstant = yearMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(zoneId).toInstant()
        return Pair(startInstant, endInstant)
    }

    /**
     * Returns the current month range based on [zoneId].
     */
    fun getCurrentMonthRange(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Instant, Instant> {
        val currentYearMonth = YearMonth.now(zoneId)
        return getMonthRange(currentYearMonth, zoneId)
    }
}
