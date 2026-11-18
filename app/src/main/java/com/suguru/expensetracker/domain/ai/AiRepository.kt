package com.suguru.expensetracker.domain.ai

import com.suguru.expensetracker.domain.parser.TransactionCandidate
import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing repository managing AI configuration, key validation, and transaction extraction.
 */
interface AiRepository {
    /**
     * Observes whether a valid BYOK API key is currently saved and available.
     */
    val isAiConfigured: Flow<Boolean>

    /**
     * Observes whether the currently saved API key has been verified.
     */
    val isKeyVerified: Flow<Boolean>

    /** Whether the currently saved API key has received a definitive provider rejection. */
    val isKeyInvalid: Flow<Boolean>

    /**
     * Observes whether the user has chosen to show the AI features in the UI.
     */
    val isAiFeatureEnabled: Flow<Boolean>

    /**
     * Retrieves the masked representation of the active key (e.g. "••••••••1234"), or null.
     */
    suspend fun getMaskedApiKey(): String?

    /**
     * Saves a new Gemini API key after validating its non-blankness.
     */
    suspend fun saveApiKey(apiKey: String): AiResult<Unit>

    /**
     * Tests and verifies an API key against the Gemini service.
     */
    suspend fun validateApiKey(apiKey: String): AiResult<Boolean>

    /**
     * Tests and verifies the currently saved API key against the Gemini service.
     */
    suspend fun validateConfiguredKey(): AiResult<Boolean>

    /**
     * Deletes the stored API key securely.
     */
    suspend fun deleteApiKey()

    /**
     * Sets user preference for whether AI assistant UI entry points are shown.
     */
    suspend fun setAiFeatureEnabled(enabled: Boolean)

    /**
     * Extracts structured transaction candidates from natural language text.
     * Returned candidates conform strictly to the financial safety contract.
     */
    suspend fun extractTransactions(
        input: String,
        defaultCurrency: String = "UZS"
    ): AiResult<List<TransactionCandidate>>
}
