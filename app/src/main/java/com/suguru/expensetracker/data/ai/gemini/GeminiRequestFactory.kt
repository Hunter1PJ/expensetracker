package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.domain.ai.AiConfiguration

/**
 * Constructs production-grade Gemini generateContent payloads with strict system instructions
 * for multi-lingual financial transaction extraction (Uzbek Latin, Uzbek Cyrillic, Russian, English).
 */
object GeminiRequestFactory {

    private const val SYSTEM_PROMPT = """You are a specialized, deterministic financial extraction engine for a personal finance tracker.
Your job is to extract financial transaction records from natural language user input in Uzbek (Latin and Cyrillic), Russian, and English.

CRITICAL FINANCIAL SAFETY INSTRUCTIONS:
1. NEVER invent transactions, accounts, categories, or amounts. Do not invent fake merchant names, locations, or dates.
2. Recognize amount shorthand: "50k" -> "50000", "1.5k" -> "1500", "100 ming" -> "100000", "2 million" -> "2000000", "50к" -> "50000", "100 тыс" -> "100000". Output expanded plain numeric strings in "rawAmount".
3. Distinguish physical measurements or quantities (e.g. "50 kg", "10 km", "3 pcs", "5 dona") from monetary amounts. A measurement unit without a monetary price is NOT a financial amount (set "rawAmount": null, "isAmountMissing": true, "unit": "kg", "quantity": "50").
4. Remove thousand grouping separators (spaces, commas, dots used for grouping) in "rawAmount" (e.g. "50 000" -> "50000").
5. If an amount is missing or not explicitly stated, set "rawAmount": null, "isAmountMissing": true, and add "amount" to "missingFields".
6. Return amount as a raw numeric string (e.g. "25000", "1500", "2000000"). Never calculate or approximate with floating point decimals.
7. Transaction types must be one of: "EXPENSE", "INCOME", "TRANSFER", "AMBIGUOUS", "UNKNOWN". Do not infer "INCOME" merely because the input contains a positive number.
8. For TRANSFERS, both a source account ("sourceAccountSuggestion") and a destination account ("destinationAccountSuggestion") must be identified if possible. If unclear, mark "isAmbiguous": true.
9. Respect explicit currency codes/symbols ("UZS", "USD", "EUR", "RUB", "$", "€", "₽", "so'm", "som", "сум"). If currency is ambiguous or absent, flag "currencyAmbiguous": true and suggest the likely or default currency if evident.
10. Categories must be standard categories compatible with the transaction type (e.g. "Groceries", "Transport", "Food & Dining", "Salary"). Do not invent fabricated category names.
11. Description/title must preserve user's meaningful note text without including amount, currency, or date expressions.
12. Support multi-transaction inputs: if user enters multiple transactions (either line-by-line or combined), output each as an individual object in the "items" array.
13. If unit price or quantity is mentioned (e.g. "3 kg apples at 15000 each"), extract "quantity", "unit", and "unitPrice".
14. For relative dates (e.g. "today", "yesterday", "kecha", "bugun", "вчера", "сегодня"), extract into "dateExpression".
15. Return strictly valid JSON conforming exactly to the requested schema. No markdown formatting, no code blocks, no explanation text outside the JSON.
"""

    private val RESPONSE_JSON_SCHEMA: Map<String, Any> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "items" to mapOf(
                "type" to "ARRAY",
                "items" to mapOf(
                    "type" to "OBJECT",
                    "properties" to mapOf(
                        "transactionType" to mapOf(
                            "type" to "STRING",
                            "enum" to listOf("EXPENSE", "INCOME", "TRANSFER", "AMBIGUOUS", "UNKNOWN")
                        ),
                        "rawAmount" to mapOf("type" to "STRING", "nullable" to true),
                        "currency" to mapOf("type" to "STRING", "nullable" to true),
                        "description" to mapOf("type" to "STRING", "nullable" to true),
                        "categorySuggestion" to mapOf("type" to "STRING", "nullable" to true),
                        "sourceAccountSuggestion" to mapOf("type" to "STRING", "nullable" to true),
                        "destinationAccountSuggestion" to mapOf("type" to "STRING", "nullable" to true),
                        "dateExpression" to mapOf("type" to "STRING", "nullable" to true),
                        "isAmountMissing" to mapOf("type" to "BOOLEAN"),
                        "isAmbiguous" to mapOf("type" to "BOOLEAN"),
                        "missingFields" to mapOf(
                            "type" to "ARRAY",
                            "items" to mapOf("type" to "STRING")
                        ),
                        "currencyAmbiguous" to mapOf("type" to "BOOLEAN"),
                        "quantity" to mapOf("type" to "STRING", "nullable" to true),
                        "unit" to mapOf("type" to "STRING", "nullable" to true),
                        "unitPrice" to mapOf("type" to "STRING", "nullable" to true)
                    ),
                    "required" to listOf(
                        "transactionType",
                        "isAmountMissing",
                        "isAmbiguous",
                        "missingFields",
                        "currencyAmbiguous"
                    )
                )
            )
        ),
        "required" to listOf("items")
    )

    fun createExtractionRequest(
        userInput: String,
        config: AiConfiguration
    ): GeminiGenerateContentRequest {
        val userContent = GeminiContent(
            role = "user",
            parts = listOf(
                GeminiPart(
                    text = "Default Currency: ${config.defaultCurrency}\nExtract transactions from the following text:\n$userInput"
                )
            )
        )

        val systemContent = GeminiContent(
            parts = listOf(GeminiPart(text = SYSTEM_PROMPT))
        )

        val genConfig = GeminiGenerationConfig(
            temperature = config.temperature,
            topP = config.topP,
            maxOutputTokens = config.maxOutputTokens,
            responseMimeType = "application/json",
            responseSchema = RESPONSE_JSON_SCHEMA
        )

        return GeminiGenerateContentRequest(
            contents = listOf(userContent),
            systemInstruction = systemContent,
            generationConfig = genConfig
        )
    }

    fun createValidationRequest(): GeminiGenerateContentRequest {
        val userContent = GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = "Respond with status ok in JSON: {\"status\":\"ok\"}"))
        )
        val genConfig = GeminiGenerationConfig(
            temperature = 0.0f,
            maxOutputTokens = 32,
            responseMimeType = "application/json"
        )
        return GeminiGenerateContentRequest(
            contents = listOf(userContent),
            generationConfig = genConfig
        )
    }
}
