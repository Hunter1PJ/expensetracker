package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiModelDefaults
import com.suguru.expensetracker.domain.ai.AiProvider
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload

/**
 * Concrete implementation of [AiProvider] using the official Gemini generateContent REST API.
 */
class GeminiAiProvider(
    private val secureKeyStorage: SecureKeyStorage,
    private val apiClient: GeminiApiClient = GeminiApiClient()
) : AiProvider {

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini"

    override suspend fun isConfigured(): Boolean {
        return secureKeyStorage.hasKey() && !secureKeyStorage.getKey().isNullOrBlank()
    }

    override suspend fun validateKey(apiKey: String): AiResult<Boolean> {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }

        val request = GeminiRequestFactory.createValidationRequest()
        val result = apiClient.generateContent(
            apiKey = trimmed,
            model = AiModelDefaults.GEMINI_MODEL_ID,
            requestBody = request
        )

        return when (result) {
            is AiResult.Success -> AiResult.Success(true)
            is AiResult.Failure -> {
                when (result.error) {
                    is AiError.InvalidApiKey, is AiError.MissingApiKey -> result
                    else -> result
                }
            }
        }
    }

    override suspend fun extractFinancialTransactions(
        prompt: String,
        config: AiConfiguration
    ): AiResult<ExtractedFinancialPayload> {
        val apiKey = secureKeyStorage.getKey()
        if (apiKey.isNullOrBlank()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }

        val request = GeminiRequestFactory.createExtractionRequest(prompt, config)
        val responseResult = apiClient.generateContent(
            apiKey = apiKey,
            model = config.modelIdentifier,
            requestBody = request
        )

        return when (responseResult) {
            is AiResult.Success -> {
                GeminiResponseParser.parsePayload(responseResult.data)
            }
            is AiResult.Failure -> responseResult
        }
    }
}
