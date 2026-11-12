package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseParserTest {

    @Test
    fun parsePayload_validSingleExpense_parsesCleanly() {
        val json = """
            {
              "items": [
                {
                  "transactionType": "EXPENSE",
                  "rawAmount": "50000",
                  "currency": "UZS",
                  "description": "Taksi xarajati",
                  "categorySuggestion": "Transport",
                  "sourceAccountSuggestion": "Cash",
                  "destinationAccountSuggestion": null,
                  "dateExpression": "bugun",
                  "isAmountMissing": false,
                  "isAmbiguous": false,
                  "missingFields": [],
                  "currencyAmbiguous": false
                }
              ]
            }
        """.trimIndent()

        val result = GeminiResponseParser.parsePayload(json)
        assertTrue(result is AiResult.Success)

        val items = (result as AiResult.Success).data.items
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("EXPENSE", item.transactionType)
        assertEquals("50000", item.rawAmount)
        assertEquals("UZS", item.currency)
        assertEquals("Taksi xarajati", item.description)
        assertEquals("Transport", item.categorySuggestion)
        assertFalse(item.isAmountMissing)
        assertFalse(item.isAmbiguous)
    }

    @Test
    fun parsePayload_missingAmount_flaggedProperly() {
        val json = """
            {
              "items": [
                {
                  "transactionType": "EXPENSE",
                  "rawAmount": null,
                  "currency": "UZS",
                  "description": "Non oldim",
                  "categorySuggestion": "Groceries",
                  "sourceAccountSuggestion": null,
                  "destinationAccountSuggestion": null,
                  "dateExpression": "bugun",
                  "isAmountMissing": true,
                  "isAmbiguous": true,
                  "missingFields": ["amount"],
                  "currencyAmbiguous": false
                }
              ]
            }
        """.trimIndent()

        val result = GeminiResponseParser.parsePayload(json)
        assertTrue(result is AiResult.Success)

        val item = (result as AiResult.Success).data.items.first()
        assertNull(item.rawAmount)
        assertTrue(item.isAmountMissing)
        assertTrue(item.missingFields.contains("amount"))
    }

    @Test
    fun parsePayload_multipleTransactions_parsesIndependently() {
        val json = """
            {
              "items": [
                {
                  "transactionType": "EXPENSE",
                  "rawAmount": "25000",
                  "currency": "UZS",
                  "description": "Kofe",
                  "categorySuggestion": "Food & Dining",
                  "sourceAccountSuggestion": null,
                  "destinationAccountSuggestion": null,
                  "dateExpression": "kecha",
                  "isAmountMissing": false,
                  "isAmbiguous": false,
                  "missingFields": [],
                  "currencyAmbiguous": false
                },
                {
                  "transactionType": "INCOME",
                  "rawAmount": "5000000",
                  "currency": "UZS",
                  "description": "Oylik maosh",
                  "categorySuggestion": "Salary",
                  "sourceAccountSuggestion": "Bank",
                  "destinationAccountSuggestion": null,
                  "dateExpression": "bugun",
                  "isAmountMissing": false,
                  "isAmbiguous": false,
                  "missingFields": [],
                  "currencyAmbiguous": false
                }
              ]
            }
        """.trimIndent()

        val result = GeminiResponseParser.parsePayload(json)
        assertTrue(result is AiResult.Success)

        val items = (result as AiResult.Success).data.items
        assertEquals(2, items.size)
        assertEquals("EXPENSE", items[0].transactionType)
        assertEquals("25000", items[0].rawAmount)
        assertEquals("INCOME", items[1].transactionType)
        assertEquals("5000000", items[1].rawAmount)
    }

    @Test
    fun parsePayload_wrappedInMarkdownTicks_cleansBeforeParsing() {
        val json = """
            ```json
            {
              "items": [
                {
                  "transactionType": "EXPENSE",
                  "rawAmount": "100",
                  "currency": "USD",
                  "description": "Groceries",
                  "isAmountMissing": false,
                  "isAmbiguous": false,
                  "missingFields": [],
                  "currencyAmbiguous": false
                }
              ]
            }
            ```
        """.trimIndent()

        val result = GeminiResponseParser.parsePayload(json)
        assertTrue(result is AiResult.Success)
        assertEquals("100", (result as AiResult.Success).data.items.first().rawAmount)
    }

    @Test
    fun parsePayload_malformedJson_returnsTypedError() {
        val badJson = "Not a json response {{"
        val result = GeminiResponseParser.parsePayload(badJson)
        assertTrue(result is AiResult.Failure)
        assertTrue((result as AiResult.Failure).error is AiError.MalformedResponse)
    }

    @Test
    fun parsePayload_missingItemsArray_returnsTypedError() {
        val json = """{"status": "ok"}"""
        val result = GeminiResponseParser.parsePayload(json)
        assertTrue(result is AiResult.Failure)
        assertTrue((result as AiResult.Failure).error is AiError.MalformedResponse)
    }
}
