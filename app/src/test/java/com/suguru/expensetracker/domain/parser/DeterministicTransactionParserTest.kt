package com.suguru.expensetracker.domain.parser

import com.suguru.expensetracker.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeterministicTransactionParserTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val parser = DeterministicTransactionParser(clockToday = { today })

    @Test
    fun parsesEnglishExpenseAndAmount() = runTest {
        val candidate = parser.parse("I spent 12.50 USD on lunch yesterday").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(1250L, candidate.amount?.amountInMinorUnits)
        assertEquals("USD", candidate.amount?.currencyCode)
        assertEquals("Food & Dining", candidate.suggestedCategoryName)
        assertEquals(today.minusDays(1), candidate.date)
    }

    @Test
    fun parsesEnglishExpenseWithoutInventingAmount() = runTest {
        val candidate = parser.parse("I spent on lunch yesterday").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertNull(candidate.amount)
        assertEquals(today.minusDays(1), candidate.date)
        assertTrue(candidate.warnings.contains(ParseWarning.MISSING_AMOUNT))
    }

    @Test
    fun parsesUzbekLatinExpenseAndAmount() = runTest {
        val candidate = parser.parse("Bugun taksi uchun 12000 so'm to'ladim").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(12000L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Transport", candidate.suggestedCategoryName)
        assertEquals(today, candidate.date)
    }

    @Test
    fun parsesRussianExpenseAndRelativeDate() = runTest {
        val candidate = parser.parse("Вчера купил продукты за 500 рублей").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(50000L, candidate.amount?.amountInMinorUnits)
        assertEquals("RUB", candidate.amount?.currencyCode)
        assertEquals("Groceries", candidate.suggestedCategoryName)
        assertEquals(today.minusDays(1), candidate.date)
    }
}