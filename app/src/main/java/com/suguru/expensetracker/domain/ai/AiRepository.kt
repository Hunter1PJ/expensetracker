package com.suguru.expensetracker.domain.ai

import com.suguru.expensetracker.domain.parser.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain-facing repository managing AI configuration, key validation, and transaction extraction.
 */
interface AiRepository {
    companion object {
        const val GUEST_PROFILE_ID = "guest"
    }
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

    /** Whether a Firebase account is currently authenticated. */
    val isAuthenticated: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    val authenticatedUid: Flow<String?> get() = kotlinx.coroutines.flow.flowOf(null)
    /** Guest or signed-in profile currently used for local AI configuration. */
    val activeAiProfileId: Flow<String?> get() = authenticatedUid.map { it ?: GUEST_PROFILE_ID }
    /** Whether an AI settings profile is active. Guest is a valid local profile. */
    val isAiProfileAvailable: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)

    /** Whether a quarantined unscoped legacy key can be explicitly migrated by the signed-in user. */
    val hasUnassignedLegacyKey: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)

    /** Migrates a legacy key only after explicit user confirmation and verified UID-scoped write. */
    suspend fun migrateLegacyKeyToCurrentUid(confirmed: Boolean): AiResult<Unit> =
        AiResult.Failure(AiError.MissingApiKey)

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
