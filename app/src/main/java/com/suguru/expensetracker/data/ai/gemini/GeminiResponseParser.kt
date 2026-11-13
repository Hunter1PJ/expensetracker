package com.suguru.expensetracker.data.ai.gemini

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialItem
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload


/**
 * Validates and parses raw Gemini JSON response text into strongly-typed [ExtractedFinancialPayload].
 * Handles schema violations, malformed JSON, and unknown types defensively.
 */
object GeminiResponseParser {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val payloadAdapter by lazy {
        moshi.adapter(ExtractedFinancialPayload::class.java)
    }

    fun parsePayload(jsonText: String): AiResult<ExtractedFinancialPayload> {
        val cleanJson = cleanJsonOutput(jsonText)
        if (cleanJson.isBlank()) {
            return AiResult.Failure(AiError.MalformedResponse("Empty JSON output from Gemini"))
        }

        return try {
            if (!cleanJson.contains("\"items\"")) {
                return AiResult.Failure(AiError.MalformedResponse("Missing 'items' array in response"))
            }

            val payload = payloadAdapter.fromJson(cleanJson)
                ?: return AiResult.Failure(AiError.MalformedResponse("Missing 'items' array in response"))

            val validatedItems = payload.items.map { item ->
                val rawType = item.transactionType.uppercase()
                val safeType = when (rawType) {
                    "EXPENSE", "INCOME", "TRANSFER", "AMBIGUOUS", "UNKNOWN" -> rawType
                    else -> "UNKNOWN"
                }

                item.copy(
                    transactionType = safeType,
                    rawAmount = item.rawAmount?.takeIf { it.isNotBlank() },
                    currency = item.currency?.takeIf { it.isNotBlank() },
                    description = item.description?.takeIf { it.isNotBlank() },
                    categorySuggestion = item.categorySuggestion?.takeIf { it.isNotBlank() },
                    sourceAccountSuggestion = item.sourceAccountSuggestion?.takeIf { it.isNotBlank() },
                    destinationAccountSuggestion = item.destinationAccountSuggestion?.takeIf { it.isNotBlank() },
                    dateExpression = item.dateExpression?.takeIf { it.isNotBlank() },
                    isAmountMissing = item.isAmountMissing || item.rawAmount.isNullOrBlank(),
                    quantity = item.quantity?.takeIf { it.isNotBlank() },
                    unit = item.unit?.takeIf { it.isNotBlank() },
                    unitPrice = item.unitPrice?.takeIf { it.isNotBlank() }
                )
            }

            AiResult.Success(ExtractedFinancialPayload(items = validatedItems))
        } catch (_: Exception) {
            AiResult.Failure(AiError.MalformedResponse("Gemini response did not match the expected format."))
        }
    }

    private fun cleanJsonOutput(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json")
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```")
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```")
        }
        return trimmed.trim()
    }
}
