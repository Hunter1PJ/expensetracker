package com.suguru.expensetracker.domain.model.statistics

import com.suguru.expensetracker.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonCalculationUtilsTest {

    @Test
    fun `calculateMetric computes exact difference with correct currency`() {
        val primary = Money(15000L, "USD")
        val comparison = Money(10000L, "USD")
        val metric = ComparisonCalculationUtils.calculateMetric(primary, comparison, MetricType.INCOME)
        assertEquals(5000L, metric.absoluteDelta.amountInMinorUnits)
        assertEquals("USD", metric.absoluteDelta.currencyCode)
        assertEquals(ComparisonSemantic.FAVORABLE, metric.semantic)
    }

    @Test
    fun `calculateMetric handles negative deltas correctly`() {
        val primary = Money(8000L, "EUR")
        val comparison = Money(12000L, "EUR")
        val metric = ComparisonCalculationUtils.calculateMetric(primary, comparison, MetricType.INCOME)
        assertEquals(-4000L, metric.absoluteDelta.amountInMinorUnits)
        assertEquals("EUR", metric.absoluteDelta.currencyCode)
        assertEquals(ComparisonSemantic.UNFAVORABLE, metric.semantic)
    }

    @Test
    fun `calculateBasisPoints returns exact basis points for positive values`() {
        // Delta 25.00 on 100.00 comparison (+25.00%, 2500 basis points)
        val rel = ComparisonCalculationUtils.calculateBasisPoints(2500L, 10000L)
        assertTrue(rel is RelativeChange.Defined)
        assertEquals(2500L, (rel as RelativeChange.Defined).basisPoints)

        // Delta -25.00 on 100.00 comparison (-25.00%, -2500 basis points)
        val relDec = ComparisonCalculationUtils.calculateBasisPoints(-2500L, 10000L)
        assertTrue(relDec is RelativeChange.Defined)
        assertEquals(-2500L, (relDec as RelativeChange.Defined).basisPoints)
    }

    @Test
    fun `calculateBasisPoints handles zero baseline correctly`() {
        // 0 baseline with delta > 0 is FromZero ("New")
        val fromZero = ComparisonCalculationUtils.calculateBasisPoints(5000L, 0L)
        assertEquals(RelativeChange.FromZero, fromZero)

        // 0 baseline with 0 delta is NoChangeFromZero ("No change")
        val zeroToZero = ComparisonCalculationUtils.calculateBasisPoints(0L, 0L)
        assertEquals(RelativeChange.NoChangeFromZero, zeroToZero)
    }

    @Test
    fun `calculateMetric suppresses percentage only when Net crosses signs`() {
        // Net crossing from negative to positive: comparison = -1000, primary = 2000 -> Suppressed
        val negToPos = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(2000L, "USD"),
            comparisonAmount = Money(-1000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Suppressed, negToPos.relativeChange)
        assertEquals(3000L, negToPos.absoluteDelta.amountInMinorUnits)

        // Net crossing from positive to negative: comparison = 1000, primary = -500 -> Suppressed
        val posToNeg = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(-500L, "USD"),
            comparisonAmount = Money(1000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Suppressed, posToNeg.relativeChange)
        assertEquals(-1500L, posToNeg.absoluteDelta.amountInMinorUnits)
    }

    @Test
    fun `delta test matrix covers all specified edge cases`() {
        // 1. 100 -> 125 expected: +2500 bp
        val m1 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(12500L, "USD"),
            comparisonAmount = Money(10000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Defined(2500L), m1.relativeChange)
        assertEquals(2500L, m1.absoluteDelta.amountInMinorUnits)

        // 2. 125 -> 100 expected: -2000 bp
        val m2 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(10000L, "USD"),
            comparisonAmount = Money(12500L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Defined(-2000L), m2.relativeChange)
        assertEquals(-2500L, m2.absoluteDelta.amountInMinorUnits)

        // 3. -100 -> -80 same sign: +2000 bp (improvement of deficit)
        val m3 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(-8000L, "USD"),
            comparisonAmount = Money(-10000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Defined(2000L), m3.relativeChange)
        assertEquals(2000L, m3.absoluteDelta.amountInMinorUnits)

        // 4. -80 -> -100 same sign: -2500 bp (worsening of deficit)
        val m4 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(-10000L, "USD"),
            comparisonAmount = Money(-8000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Defined(-2500L), m4.relativeChange)
        assertEquals(-2000L, m4.absoluteDelta.amountInMinorUnits)

        // 5. -100 -> +50 sign crossing: Suppressed
        val m5 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(5000L, "USD"),
            comparisonAmount = Money(-10000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Suppressed, m5.relativeChange)
        assertEquals(15000L, m5.absoluteDelta.amountInMinorUnits)

        // 6. +100 -> -50 sign crossing: Suppressed
        val m6 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(-5000L, "USD"),
            comparisonAmount = Money(10000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Suppressed, m6.relativeChange)
        assertEquals(-15000L, m6.absoluteDelta.amountInMinorUnits)

        // 7. 0 -> 0: NoChangeFromZero
        val m7 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(0L, "USD"),
            comparisonAmount = Money(0L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.NoChangeFromZero, m7.relativeChange)
        assertEquals(0L, m7.absoluteDelta.amountInMinorUnits)

        // 8. 0 -> positive: FromZero
        val m8 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(5000L, "USD"),
            comparisonAmount = Money(0L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.FromZero, m8.relativeChange)
        assertEquals(5000L, m8.absoluteDelta.amountInMinorUnits)

        // 9. 0 -> negative: FromZero
        val m9 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(-5000L, "USD"),
            comparisonAmount = Money(0L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.FromZero, m9.relativeChange)
        assertEquals(-5000L, m9.absoluteDelta.amountInMinorUnits)

        // 10. Large Long values: no silent overflow with BigInteger
        val m10 = ComparisonCalculationUtils.calculateMetric(
            primaryAmount = Money(5_000_000_000_000_000L, "USD"),
            comparisonAmount = Money(4_000_000_000_000_000L, "USD"),
            metricType = MetricType.NET
        )
        assertEquals(RelativeChange.Defined(2500L), m10.relativeChange)
        assertEquals(1_000_000_000_000_000L, m10.absoluteDelta.amountInMinorUnits)
    }

    @Test
    fun `classifySemantic handles INCOME correctly`() {
        assertEquals(ComparisonSemantic.FAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.INCOME, 500L))
        assertEquals(ComparisonSemantic.UNFAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.INCOME, -500L))
        assertEquals(ComparisonSemantic.NEUTRAL, ComparisonCalculationUtils.classifySemantic(MetricType.INCOME, 0L))
    }

    @Test
    fun `classifySemantic handles EXPENSE correctly with inverted semantics`() {
        // Spending more is UNFAVORABLE
        assertEquals(ComparisonSemantic.UNFAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.EXPENSE, 500L))
        // Spending less is FAVORABLE
        assertEquals(ComparisonSemantic.FAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.EXPENSE, -500L))
        // No change is NEUTRAL
        assertEquals(ComparisonSemantic.NEUTRAL, ComparisonCalculationUtils.classifySemantic(MetricType.EXPENSE, 0L))
    }

    @Test
    fun `classifySemantic handles NET correctly`() {
        assertEquals(ComparisonSemantic.FAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.NET, 1000L))
        assertEquals(ComparisonSemantic.UNFAVORABLE, ComparisonCalculationUtils.classifySemantic(MetricType.NET, -1000L))
        assertEquals(ComparisonSemantic.NEUTRAL, ComparisonCalculationUtils.classifySemantic(MetricType.NET, 0L))
    }

    @Test
    fun `calculateMetric creates complete ComparisonMetric`() {
        val primary = Money(20000L, "USD")
        val comp = Money(15000L, "USD")
        val metric = ComparisonCalculationUtils.calculateMetric(primary, comp, MetricType.INCOME)

        assertEquals(primary, metric.primaryAmount)
        assertEquals(comp, metric.comparisonAmount)
        assertEquals(5000L, metric.absoluteDelta.amountInMinorUnits)
        assertEquals(ComparisonSemantic.FAVORABLE, metric.semantic)
        assertTrue(metric.relativeChange is RelativeChange.Defined)
        assertEquals(3333L, (metric.relativeChange as RelativeChange.Defined).basisPoints)
    }
}
