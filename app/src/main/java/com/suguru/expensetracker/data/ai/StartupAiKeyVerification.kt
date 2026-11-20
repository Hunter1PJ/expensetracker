package com.suguru.expensetracker.data.ai

import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Performs one process-start verification attempt without tying it to a screen lifecycle.
 * Network-limited outcomes are reported as retryable so the application can retry on connectivity.
 */
class StartupAiKeyVerification(
    private val aiRepository: AiRepository,
    private val hasValidatedInternet: () -> Boolean = { true }
) {
    enum class Outcome {
        SKIPPED_SIGNED_OUT,
        SKIPPED_DISABLED,
        SKIPPED_NO_KEY,
        SKIPPED_ALREADY_INVALID,
        VERIFIED,
        INVALID_KEY,
        RETRY_WHEN_CONNECTED,
        OTHER_FAILURE
    }

    suspend fun verify(): Outcome {
        if (!aiRepository.isAiProfileAvailable.first()) return Outcome.SKIPPED_SIGNED_OUT
        if (!aiRepository.isAiFeatureEnabled.first()) return Outcome.SKIPPED_DISABLED
        if (!aiRepository.isAiConfigured.first()) return Outcome.SKIPPED_NO_KEY
        if (aiRepository.isKeyInvalid.first()) return Outcome.SKIPPED_ALREADY_INVALID
        if (!hasValidatedInternet()) return Outcome.RETRY_WHEN_CONNECTED

        return try {
            when (val result = aiRepository.validateConfiguredKey()) {
                is AiResult.Success -> if (result.data) Outcome.VERIFIED else Outcome.OTHER_FAILURE
                is AiResult.Failure -> when (val error = result.error) {
                    AiError.InvalidApiKey -> Outcome.INVALID_KEY
                    AiError.RequestTimeout,
                    is AiError.NetworkError,
                    is AiError.RateLimitExceeded -> Outcome.RETRY_WHEN_CONNECTED
                    is AiError.ServerError -> if (error.statusCode >= 500) {
                        Outcome.RETRY_WHEN_CONNECTED
                    } else {
                        Outcome.OTHER_FAILURE
                    }
                    else -> Outcome.OTHER_FAILURE
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Outcome.RETRY_WHEN_CONNECTED
        }
    }
}
