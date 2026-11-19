package com.suguru.expensetracker.domain.ai

/**
 * Result representation for AI-driven financial parsing operations.
 */
sealed interface AiResult<out T> {
    data class Success<out T>(val data: T) : AiResult<T>
    data class Failure(val error: AiError) : AiResult<Nothing>
}

/**
 * Strongly typed AI error categories.
 * Distinctly classifies missing keys, invalid keys, network timeouts, rate limits, server errors, and parsing issues.
 */
sealed interface AiError {
    object MissingApiKey : AiError
    object InvalidApiKey : AiError
    object RequestTimeout : AiError
    object NoTransactionsFound : AiError
    data class NetworkError(val message: String) : AiError
    data class RateLimitExceeded(val retryAfterSeconds: Long? = null) : AiError
    data class ServerError(val statusCode: Int, val message: String) : AiError
    data class MalformedResponse(val reason: String) : AiError
    data class ExecutionError(val message: String) : AiError
}

object AiModelDefaults {
    const val GEMINI_MODEL_ID = "gemini-3.5-flash-lite"
}

/**
 * Standard configuration options for AI extraction.
 */
data class AiConfiguration(
    val modelIdentifier: String = AiModelDefaults.GEMINI_MODEL_ID,
    val temperature: Float = 0.1f,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 2048,
    val defaultCurrency: String = "UZS"
)

/**
 * Generic AI provider contract.
 * Designed for BYOK extensibility (Gemini currently, and potential future providers).
 */
interface AiProvider {
    val providerId: String
    val displayName: String

    suspend fun isConfigured(): Boolean
    suspend fun validateKey(apiKey: String): AiResult<Boolean>
    suspend fun extractFinancialTransactions(
        prompt: String,
        config: AiConfiguration
    ): AiResult<ExtractedFinancialPayload>

    /** Uses the UID-scoped key captured by the repository for this operation. */
    suspend fun extractFinancialTransactions(
        prompt: String,
        config: AiConfiguration,
        apiKey: String
    ): AiResult<ExtractedFinancialPayload> =
        extractFinancialTransactions(prompt, config)
}

/**
 * Extracted raw suggestions directly matching the financial contract.
 */
data class ExtractedFinancialPayload(
    val items: List<ExtractedFinancialItem>
)

data class ExtractedFinancialItem(
    val transactionType: String, // EXPENSE, INCOME, TRANSFER, MULTIPLE, AMBIGUOUS, UNKNOWN
    val rawAmount: String? = null,
    val currency: String? = null,
    val description: String? = null,
    val categorySuggestion: String? = null,
    val sourceAccountSuggestion: String? = null,
    val destinationAccountSuggestion: String? = null,
    val dateExpression: String? = null,
    val isAmountMissing: Boolean = false,
    val isAmbiguous: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val currencyAmbiguous: Boolean = false,
    val quantity: String? = null,
    val unit: String? = null,
    val unitPrice: String? = null
)
