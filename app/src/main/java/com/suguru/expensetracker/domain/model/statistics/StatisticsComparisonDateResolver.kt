package com.suguru.expensetracker.domain.model.statistics

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ResolvedComparisonRanges(
    val primaryDateRange: StatisticsDateRange,
    val comparisonDateRange: StatisticsDateRange,
    val primaryRange: StatisticsRange,
    val comparisonRange: StatisticsRange
)

object StatisticsComparisonDateResolver {

    fun resolve(
        periodOption: StatisticsPeriodOption,
        mode: StatisticsComparisonMode,
        today: LocalDate,
        zoneId: ZoneId,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): ResolvedComparisonRanges {
        val (primaryDateRange, comparisonDateRange) = when (periodOption) {
            StatisticsPeriodOption.THIS_MONTH -> {
                val startOfMonth = today.withDayOfMonth(1)
                val primary = StatisticsDateRange(startOfMonth, today)
                val elapsedDays = primary.dayCount

                val comparison = when (mode) {
                    StatisticsComparisonMode.PREVIOUS_PERIOD -> {
                        val prevMonthStart = startOfMonth.minusMonths(1)
                        val prevMonthMaxDays = prevMonthStart.lengthOfMonth().toLong()
                        val daysToTake = elapsedDays.coerceAtMost(prevMonthMaxDays)
                        StatisticsDateRange(prevMonthStart, prevMonthStart.plusDays(daysToTake - 1))
                    }
                    StatisticsComparisonMode.PREVIOUS_YEAR -> {
                        val prevYearSameMonthStart = startOfMonth.minusYears(1)
                        val prevYearMonthMaxDays = prevYearSameMonthStart.lengthOfMonth().toLong()
                        val daysToTake = elapsedDays.coerceAtMost(prevYearMonthMaxDays)
                        StatisticsDateRange(prevYearSameMonthStart, prevYearSameMonthStart.plusDays(daysToTake - 1))
                    }
                }
                primary to comparison
            }

            StatisticsPeriodOption.LAST_MONTH -> {
                val lastMonthStart = today.withDayOfMonth(1).minusMonths(1)
                val lastMonthEnd = lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth())
                val primary = StatisticsDateRange(lastMonthStart, lastMonthEnd)

                val comparison = when (mode) {
                    StatisticsComparisonMode.PREVIOUS_PERIOD -> {
                        val compStart = lastMonthStart.minusMonths(1)
                        val compEnd = compStart.withDayOfMonth(compStart.lengthOfMonth())
                        StatisticsDateRange(compStart, compEnd)
                    }
                    StatisticsComparisonMode.PREVIOUS_YEAR -> {
                        val compStart = lastMonthStart.minusYears(1)
                        val compEnd = compStart.withDayOfMonth(compStart.lengthOfMonth())
                        StatisticsDateRange(compStart, compEnd)
                    }
                }
                primary to comparison
            }

            StatisticsPeriodOption.LAST_3_MONTHS -> {
                val startOfBlock = today.withDayOfMonth(1).minusMonths(2)
                val primary = StatisticsDateRange(startOfBlock, today)
                val elapsedDays = primary.dayCount

                val comparison = when (mode) {
                    StatisticsComparisonMode.PREVIOUS_PERIOD -> {
                        val compStart = startOfBlock.minusMonths(3)
                        val maxPrecedingBlockDays = ChronoUnit.DAYS.between(compStart, startOfBlock)
                        val daysToTake = elapsedDays.coerceAtMost(maxPrecedingBlockDays)
                        StatisticsDateRange(compStart, compStart.plusDays(daysToTake - 1))
                    }
                    StatisticsComparisonMode.PREVIOUS_YEAR -> {
                        val compStart = startOfBlock.minusYears(1)
                        val targetMonthStart = today.withDayOfMonth(1).minusYears(1)
                        val targetMaxDays = targetMonthStart.lengthOfMonth()
                        val targetDay = today.dayOfMonth.coerceAtMost(targetMaxDays)
                        val compEnd = targetMonthStart.withDayOfMonth(targetDay)
                        StatisticsDateRange(compStart, compEnd)
                    }
                }
                primary to comparison
            }

            StatisticsPeriodOption.THIS_YEAR -> {
                val startOfYear = today.withDayOfYear(1)
                val primary = StatisticsDateRange(startOfYear, today)

                val compStart = startOfYear.minusYears(1)
                val targetMonthStart = today.withDayOfMonth(1).minusYears(1)
                val targetMaxDays = targetMonthStart.lengthOfMonth()
                val targetDay = today.dayOfMonth.coerceAtMost(targetMaxDays)
                val compEnd = targetMonthStart.withDayOfMonth(targetDay)
                val comparison = StatisticsDateRange(compStart, compEnd)

                primary to comparison
            }

            StatisticsPeriodOption.CUSTOM -> {
                val start = customStart ?: today
                val end = customEnd ?: today
                val effectiveStart = if (start.isAfter(end)) end else start
                val effectiveEnd = if (start.isAfter(end)) start else end

                val isOngoing = !today.isBefore(effectiveStart) && !today.isAfter(effectiveEnd)
                val primaryEnd = if (isOngoing) today else effectiveEnd
                val primary = StatisticsDateRange(effectiveStart, primaryEnd)
                val elapsedDays = primary.dayCount

                val comparison = when (mode) {
                    StatisticsComparisonMode.PREVIOUS_PERIOD -> {
                        val compStart = effectiveStart.minusDays(elapsedDays)
                        val compEnd = effectiveStart.minusDays(1)
                        StatisticsDateRange(compStart, compEnd)
                    }
                    StatisticsComparisonMode.PREVIOUS_YEAR -> {
                        val compStart = safeShiftOneYearEarlier(effectiveStart)
                        val compEnd = safeShiftOneYearEarlier(primaryEnd)
                        if (compStart.isAfter(compEnd)) {
                            StatisticsDateRange(compEnd, compEnd)
                        } else {
                            StatisticsDateRange(compStart, compEnd)
                        }
                    }
                }
                primary to comparison
            }
        }

        return ResolvedComparisonRanges(
            primaryDateRange = primaryDateRange,
            comparisonDateRange = comparisonDateRange,
            primaryRange = primaryDateRange.toStatisticsRange(zoneId),
            comparisonRange = comparisonDateRange.toStatisticsRange(zoneId)
        )
    }

    private fun safeShiftOneYearEarlier(date: LocalDate): LocalDate {
        return date.minusYears(1)
    }
}
