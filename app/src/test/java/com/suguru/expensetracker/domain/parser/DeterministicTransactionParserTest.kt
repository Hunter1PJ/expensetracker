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

    // Section 5.A: Uzbek amount shorthand tests
    @Test
    fun parsesUzbekShorthand50kOvqatlanishga() = runTest {
        val candidate = parser.parse("50k ovqatlanishga").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(50000L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Food & Dining", candidate.suggestedCategoryName)
        assertEquals("ovqatlanishga", candidate.suggestedTitle)
    }

    @Test
    fun parsesUzbekShorthand10kTaksiga() = runTest {
        val candidate = parser.parse("10k taksiga").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(10000L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Transport", candidate.suggestedCategoryName)
    }

    @Test
    fun parsesUzbekDecimalShorthand1_5kOvqat() = runTest {
        val candidate = parser.parse("1.5k ovqat").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(1500L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Food & Dining", candidate.suggestedCategoryName)
    }

    @Test
    fun parsesUzbekShorthand100MingOziqOvqatga() = runTest {
        val candidate = parser.parse("100 ming oziq-ovqatga").single()

        assertEquals(TransactionType.EXPENSE, candidate.transactionType)
        assertEquals(100000L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Groceries", candidate.suggestedCategoryName)
    }

    @Test
    fun parsesUzbekShorthand2MillionMaosh() = runTest {
        val candidate = parser.parse("2 million maosh").single()

        assertEquals(TransactionType.INCOME, candidate.transactionType)
        assertEquals(2000000L, candidate.amount?.amountInMinorUnits)
        assertEquals("UZS", candidate.amount?.currencyCode)
        assertEquals("Salary", candidate.suggestedCategoryName)
    }

    // Section 5.B: Plain numeric amounts tests
    @Test
    fun parsesPlainNumericAmountsWithDifferentSeparators() = runTest {
        val inputs = listOf("50000 ovqat", "50 000 ovqat", "50,000 ovqat", "50.000 ovqat")
        for (input in inputs) {
            val candidate = parser.parse(input).single()
            assertEquals("Failed for input: $input", 50000L, candidate.amount?.amountInMinorUnits)
            assertEquals("Failed for input: $input", "UZS", candidate.amount?.currencyCode)
        }
    }

    // Section 5.C: Currency formats
    @Test
    fun parsesExplicitCurrencyFormats() = runTest {
        val c1 = parser.parse("50k so'm").single()
        assertEquals(50000L, c1.amount?.amountInMinorUnits)
        assertEquals("UZS", c1.amount?.currencyCode)

        val c2 = parser.parse("50000 рублей").single()
        assertEquals(5000000L, c2.amount?.amountInMinorUnits)
        assertEquals("RUB", c2.amount?.currencyCode)

        val c3 = parser.parse("$100 taksiga").single()
        assertEquals(10000L, c3.amount?.amountInMinorUnits)
        assertEquals("USD", c3.amount?.currencyCode)
    }

    // Section 5.D: Languages
    @Test
    fun parsesMultilingualInputs() = runTest {
        val c1 = parser.parse("50к овқатланишга").single()
        assertEquals(50000L, c1.amount?.amountInMinorUnits)

        val c2 = parser.parse("купил продукты на 100 тыс").single()
        assertEquals(100000L, c2.amount?.amountInMinorUnits)
        assertEquals("Groceries", c2.suggestedCategoryName)

        val c3 = parser.parse("spent 5k on groceries").single()
        assertEquals(5000L, c3.amount?.amountInMinorUnits)
        assertEquals("Groceries", c3.suggestedCategoryName)
    }

    // Section 5.E: Ambiguous and invalid inputs
    @Test
    fun handlesAmbiguousAndInvalidInputs() = runTest {
        val missingAmount = parser.parse("taksiga to'ladim").single()
        assertNull(missingAmount.amount)
        assertTrue(missingAmount.warnings.contains(ParseWarning.MISSING_AMOUNT))

        val multipleAmounts = parser.parse("50k taksi 20k ovqat").single()
        assertEquals(50000L, multipleAmounts.amount?.amountInMinorUnits)
        assertTrue(multipleAmounts.warnings.contains(ParseWarning.MULTIPLE_AMOUNTS_DETECTED))

        val physicalUnitKg = parser.parse("50 kg kartoshka").single()
        assertNull(physicalUnitKg.amount)
        assertTrue(physicalUnitKg.warnings.contains(ParseWarning.MISSING_AMOUNT))

        val physicalUnitKm = parser.parse("10 km taksiga").single()
        assertNull(physicalUnitKm.amount)
        assertTrue(physicalUnitKm.warnings.contains(ParseWarning.MISSING_AMOUNT))

        val overflow = parser.parse("99999999999999999999k ovqat").single()
        assertNull(overflow.amount)
        assertTrue(overflow.warnings.contains(ParseWarning.INVALID_AMOUNT))
    }
}