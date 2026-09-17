package com.suguru.expensetracker.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsComparisonDateResolverTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `resolve handles THIS_MONTH previous period correctly`() {
        val today = LocalDate.of(2026, 9, 16)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            today = today,
            zoneId = zoneId
        )
        // Primary: Sep 1 to Sep 16
        assertEquals(LocalDate.of(2026, 9, 1), resolved.primaryDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 16), resolved.primaryDateRange.endInclusive)
        // Previous period (August): Aug 1 to Aug 16
        assertEquals(LocalDate.of(2026, 8, 1), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 8, 16), resolved.comparisonDateRange.endInclusive)
    }

    @Test
    fun `resolve handles THIS_MONTH previous year correctly`() {
        val today = LocalDate.of(2026, 9, 16)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.THIS_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_YEAR,
            today = today,
            zoneId = zoneId
        )
        assertEquals(LocalDate.of(2026, 9, 1), resolved.primaryDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 16), resolved.primaryDateRange.endInclusive)
        // Previous year: Sep 1 2025 to Sep 16 2025
        assertEquals(LocalDate.of(2025, 9, 1), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2025, 9, 16), resolved.comparisonDateRange.endInclusive)
    }

    @Test
    fun `resolve handles LAST_MONTH previous period correctly`() {
        val today = LocalDate.of(2026, 9, 16)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.LAST_MONTH,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            today = today,
            zoneId = zoneId
        )
        // Primary: Aug 1 to Aug 31
        assertEquals(LocalDate.of(2026, 8, 1), resolved.primaryDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 8, 31), resolved.primaryDateRange.endInclusive)
        // Comparison: Jul 1 to Jul 31
        assertEquals(LocalDate.of(2026, 7, 1), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 31), resolved.comparisonDateRange.endInclusive)
    }

    @Test
    fun `resolve handles LAST_3_MONTHS previous period correctly`() {
        val today = LocalDate.of(2026, 9, 16)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.LAST_3_MONTHS,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            today = today,
            zoneId = zoneId
        )
        // Primary: Jul 1 to Sep 16
        assertEquals(LocalDate.of(2026, 7, 1), resolved.primaryDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 16), resolved.primaryDateRange.endInclusive)
        // Comparison starts 3 months before Jul 1 -> Apr 1
        assertEquals(LocalDate.of(2026, 4, 1), resolved.comparisonDateRange.startInclusive)
    }

    @Test
    fun `resolve handles THIS_YEAR previous year correctly`() {
        val today = LocalDate.of(2026, 9, 16)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.THIS_YEAR,
            mode = StatisticsComparisonMode.PREVIOUS_YEAR,
            today = today,
            zoneId = zoneId
        )
        // Primary: Jan 1 2026 to Sep 16 2026
        assertEquals(LocalDate.of(2026, 1, 1), resolved.primaryDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 9, 16), resolved.primaryDateRange.endInclusive)
        // Comparison: Jan 1 2025 to Sep 16 2025
        assertEquals(LocalDate.of(2025, 1, 1), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2025, 9, 16), resolved.comparisonDateRange.endInclusive)
    }

    @Test
    fun `resolve handles CUSTOM range previous period with exact day length`() {
        val today = LocalDate.of(2026, 9, 20)
        // Completed past 10 days: Sep 1 to Sep 10 (10 days)
        val customStart = LocalDate.of(2026, 9, 1)
        val customEnd = LocalDate.of(2026, 9, 10)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.CUSTOM,
            mode = StatisticsComparisonMode.PREVIOUS_PERIOD,
            today = today,
            zoneId = zoneId,
            customStart = customStart,
            customEnd = customEnd
        )
        assertEquals(customStart, resolved.primaryDateRange.startInclusive)
        assertEquals(customEnd, resolved.primaryDateRange.endInclusive)
        // Preceding 10 days: Aug 22 to Aug 31
        assertEquals(LocalDate.of(2026, 8, 22), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2026, 8, 31), resolved.comparisonDateRange.endInclusive)
        assertEquals(resolved.primaryDateRange.dayCount, resolved.comparisonDateRange.dayCount)
    }

    @Test
    fun `resolve handles CUSTOM range previous year correctly`() {
        val today = LocalDate.of(2026, 9, 20)
        val customStart = LocalDate.of(2026, 9, 1)
        val customEnd = LocalDate.of(2026, 9, 10)
        val resolved = StatisticsComparisonDateResolver.resolve(
            periodOption = StatisticsPeriodOption.CUSTOM,
            mode = StatisticsComparisonMode.PREVIOUS_YEAR,
            today = today,
            zoneId = zoneId,
            customStart = customStart,
            customEnd = customEnd
        )
        assertEquals(LocalDate.of(2025, 9, 1), resolved.comparisonDateRange.startInclusive)
        assertEquals(LocalDate.of(2025, 9, 10), resolved.comparisonDateRange.endInclusive)
    }
}
