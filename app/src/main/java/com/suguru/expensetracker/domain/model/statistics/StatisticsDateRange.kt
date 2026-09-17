package com.suguru.expensetracker.domain.model.statistics

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class StatisticsDateRange(
    val startInclusive: LocalDate,
    val endInclusive: LocalDate
) {
    init {
        require(!startInclusive.isAfter(endInclusive)) {
            "startInclusive ($startInclusive) cannot be after endInclusive ($endInclusive)"
        }
    }

    val dayCount: Long
        get() = ChronoUnit.DAYS.between(startInclusive, endInclusive) + 1

    fun toStatisticsRange(zoneId: ZoneId): StatisticsRange {
        val startInstant = startInclusive.atStartOfDay(zoneId).toInstant()
        val endExclusiveDate = endInclusive.plusDays(1)
        val endInstant = endExclusiveDate.atStartOfDay(zoneId).toInstant()
        return StatisticsRange(
            startInclusive = startInstant,
            endExclusive = endInstant,
            startLocalDate = startInclusive,
            endLocalDateExclusive = endExclusiveDate
        )
    }
}
