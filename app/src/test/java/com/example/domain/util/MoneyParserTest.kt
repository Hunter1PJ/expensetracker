package com.example.domain.util

import com.example.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyParserTest {

    @Test
    fun `parse integer string for 2-decimal currency`() {
        val result = MoneyParser.parse("12", "USD")
        assertTrue(result.isSuccess)
        val money = result.getOrThrow()
        assertEquals(1200L, money.amountInMinorUnits)
        assertEquals("USD", money.currencyCode)
    }

    @Test
    fun `parse single decimal fraction string`() {
        val result = MoneyParser.parse("12.5", "USD")
        assertTrue(result.isSuccess)
        val money = result.getOrThrow()
        assertEquals(1250L, money.amountInMinorUnits)
    }

    @Test
    fun `parse two decimal fraction string`() {
        val result = MoneyParser.parse("12.50", "USD")
        assertTrue(result.isSuccess)
        val money = result.getOrThrow()
        assertEquals(1250L, money.amountInMinorUnits)
    }

    @Test
    fun `parse cents only with leading zero`() {
        val result = MoneyParser.parse("0.05", "USD")
        assertTrue(result.isSuccess)
        val money = result.getOrThrow()
        assertEquals(5L, money.amountInMinorUnits)
    }

    @Test
    fun `parse comma as decimal separator`() {
        val result = MoneyParser.parse("12,50", "EUR")
        assertTrue(result.isSuccess)
        val money = result.getOrThrow()
        assertEquals(1250L, money.amountInMinorUnits)
        assertEquals("EUR", money.currencyCode)
    }

    @Test
    fun `parse zero amount`() {
        val result = MoneyParser.parse("0", "USD")
        assertTrue(result.isSuccess)
        assertEquals(0L, result.getOrThrow().amountInMinorUnits)

        val result2 = MoneyParser.parse("0.00", "USD")
        assertTrue(result2.isSuccess)
        assertEquals(0L, result2.getOrThrow().amountInMinorUnits)
    }

    @Test
    fun `parse zero-decimal currency without fraction`() {
        val result = MoneyParser.parse("500", "JPY")
        assertTrue(result.isSuccess)
        assertEquals(500L, result.getOrThrow().amountInMinorUnits)
    }

    @Test
    fun `reject zero-decimal currency with fractional part`() {
        val result = MoneyParser.parse("500.50", "JPY")
        assertTrue(result.isFailure)
    }

    @Test
    fun `reject negative numbers`() {
        val result = MoneyParser.parse("-5.00", "USD")
        assertTrue(result.isFailure)
    }

    @Test
    fun `reject empty or blank input`() {
        val result1 = MoneyParser.parse("", "USD")
        assertTrue(result1.isFailure)

        val result2 = MoneyParser.parse("   ", "USD")
        assertTrue(result2.isFailure)
    }

    @Test
    fun `reject invalid non-numeric text`() {
        val result1 = MoneyParser.parse("abc", "USD")
        assertTrue(result1.isFailure)

        val result2 = MoneyParser.parse("12.3.4", "USD")
        assertTrue(result2.isFailure)

        val result3 = MoneyParser.parse("12a.50", "USD")
        assertTrue(result3.isFailure)
    }

    @Test
    fun `reject too many decimal places`() {
        val result = MoneyParser.parse("12.345", "USD")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse very large values safely without overflow`() {
        val result = MoneyParser.parse("92233720368547758", "USD")
        assertTrue(result.isSuccess)
        assertEquals(9223372036854775800L, result.getOrThrow().amountInMinorUnits)

        // Overflow values should fail safely
        val overflowResult = MoneyParser.parse("99999999999999999999", "USD")
        assertTrue(overflowResult.isFailure)
    }

    @Test
    fun `format money to standard string`() {
        val formatted1 = MoneyParser.format(Money(1250L, "USD"))
        assertEquals("$12.50", formatted1)

        val formatted2 = MoneyParser.format(Money(5L, "USD"))
        assertEquals("$0.05", formatted2)

        val formatted3 = MoneyParser.format(Money(50000L, "JPY"))
        assertEquals("¥50000", formatted3)

        val formatted4 = MoneyParser.format(Money(-1250L, "USD"))
        assertEquals("-$12.50", formatted4)
    }
}
