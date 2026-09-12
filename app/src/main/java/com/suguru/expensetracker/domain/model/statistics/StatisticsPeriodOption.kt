package com.suguru.expensetracker.domain.model.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class StatisticsPeriodOption(val displayName: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    THIS_YEAR("This Year");

    fun calculateRange(now: Instant, zoneId: ZoneId): StatisticsRange {
        val zonedDateTime = now.atZone(zoneId)
        val today = zonedDateTime.toLocalDate()

        return when (this) {
            THIS_MONTH -> {
                val startLocal = today.withDayOfMonth(1)
                val endLocal = startLocal.plusMonths(1)
                StatisticsRange(
                    startInclusive = startLocal.atStartOfDay(zoneId).toInstant(),
                    endExclusive = endLocal.atStartOfDay(zoneId).toInstant(),
                    startLocalDate = startLocal,
                    endLocalDateExclusive = endLocal
                )
            }
            LAST_MONTH -> {
                val startLocal = today.withDayOfMonth(1).minusMonths(1)
                val endLocal = today.withDayOfMonth(1)
                StatisticsRange(
                    startInclusive = startLocal.atStartOfDay(zoneId).toInstant(),
                    endExclusive = endLocal.atStartOfDay(zoneId).toInstant(),
                    startLocalDate = startLocal,
                    endLocalDateExclusive = endLocal
                )
            }
            LAST_3_MONTHS -> {
                val startLocal = today.withDayOfMonth(1).minusMonths(2)
                val endLocal = today.withDayOfMonth(1).plusMonths(1)
                StatisticsRange(
                    startInclusive = startLocal.atStartOfDay(zoneId).toInstant(),
                    endExclusive = endLocal.atStartOfDay(zoneId).toInstant(),
                    startLocalDate = startLocal,
                    endLocalDateExclusive = endLocal
                )
            }
            THIS_YEAR -> {
                val startLocal = today.withDayOfYear(1)
                val endLocal = startLocal.plusYears(1)
                StatisticsRange(
                    startInclusive = startLocal.atStartOfDay(zoneId).toInstant(),
                    endExclusive = endLocal.atStartOfDay(zoneId).toInstant(),
                    startLocalDate = startLocal,
                    endLocalDateExclusive = endLocal
                )
            }
        }
    }
}

data class StatisticsRange(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val startLocalDate: LocalDate,
    val endLocalDateExclusive: LocalDate
)
