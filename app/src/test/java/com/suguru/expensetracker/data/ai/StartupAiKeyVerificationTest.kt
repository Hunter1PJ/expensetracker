package com.suguru.expensetracker.data.ai

import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupAiKeyVerificationTest {
    @Test
    fun guestProfileVerifiesWithoutGoogleSignIn() = runTest {
        val repository = FakeAiRepository(enabled = true, configured = true, signedIn = false)
        assertEquals(
            StartupAiKeyVerification.Outcome.VERIFIED,
            StartupAiKeyVerification(repository).verify()
        )
        assertEquals(1, repository.validationCalls)
    }

    @Test
    fun unavailableAiProfileSkipsVerification() = runTest {
        val repository = FakeAiRepository(
            enabled = true,
            configured = true,
            profileAvailable = false
        )
        assertEquals(
            StartupAiKeyVerification.Outcome.SKIPPED_SIGNED_OUT,
            StartupAiKeyVerification(repository).verify()
        )
        assertEquals(0, repository.validationCalls)
    }

    @Test
    fun disabledAssistantSkipsVerification() = runTest {
        val repository = FakeAiRepository(enabled = false, configured = true)
        assertEquals(StartupAiKeyVerification.Outcome.SKIPPED_DISABLED, StartupAiKeyVerification(repository).verify())
        assertEquals(0, repository.validationCalls)
    }

    @Test
    fun enabledAssistantWithoutKeySkipsVerification() = runTest {
        val repository = FakeAiRepository(enabled = true, configured = false)
        assertEquals(StartupAiKeyVerification.Outcome.SKIPPED_NO_KEY, StartupAiKeyVerification(repository).verify())
        assertEquals(0, repository.validationCalls)
    }

    @Test
    fun enabledAssistantWithKeyVerifiesOnce() = runTest {
        val repository = FakeAiRepository(enabled = true, configured = true)
        assertEquals(StartupAiKeyVerification.Outcome.VERIFIED, StartupAiKeyVerification(repository).verify())
        assertEquals(1, repository.validationCalls)
    }

    @Test
    fun invalidKeyRoutesToSettingsButTemporaryFailuresRemainRetryable() = runTest {
        val invalid = FakeAiRepository(
            enabled = true,
            configured = true,
            result = AiResult.Failure(AiError.InvalidApiKey)
        )
        assertEquals(StartupAiKeyVerification.Outcome.INVALID_KEY, StartupAiKeyVerification(invalid).verify())

        val network = FakeAiRepository(
            enabled = true,
            configured = true,
            result = AiResult.Failure(AiError.NetworkError("temporary"))
        )
        assertEquals(
            StartupAiKeyVerification.Outcome.RETRY_WHEN_CONNECTED,
            StartupAiKeyVerification(network).verify()
        )

        val rateLimited = FakeAiRepository(
            enabled = true,
            configured = true,
            result = AiResult.Failure(AiError.RateLimitExceeded())
        )
        assertEquals(
            StartupAiKeyVerification.Outcome.RETRY_WHEN_CONNECTED,
            StartupAiKeyVerification(rateLimited).verify()
        )
    }

    @Test
    fun previouslyRejectedKeyIsNotRetriedAtStartup() = runTest {
        val repository = FakeAiRepository(enabled = true, configured = true, invalid = true)
        assertEquals(
            StartupAiKeyVerification.Outcome.SKIPPED_ALREADY_INVALID,
            StartupAiKeyVerification(repository).verify()
        )
        assertEquals(0, repository.validationCalls)
    }

    @Test
    fun offlineStartupDefersRequestUntilConnectivity() = runTest {
        val repository = FakeAiRepository(enabled = true, configured = true)
        val result = StartupAiKeyVerification(repository, hasValidatedInternet = { false }).verify()
        assertEquals(StartupAiKeyVerification.Outcome.RETRY_WHEN_CONNECTED, result)
        assertEquals(0, repository.validationCalls)
    }

    private class FakeAiRepository(
        enabled: Boolean,
        private val configured: Boolean,
        private val result: AiResult<Boolean> = AiResult.Success(true),
        invalid: Boolean = false,
        signedIn: Boolean = true,
        profileAvailable: Boolean = true
    ) : AiRepository {
        override val isAuthenticated: Flow<Boolean> = flowOf(signedIn)
        override val isAiProfileAvailable: Flow<Boolean> = flowOf(profileAvailable)
        override val isAiConfigured: Flow<Boolean> = flowOf(configured)
        override val isKeyVerified: Flow<Boolean> = flowOf(false)
        override val isKeyInvalid: Flow<Boolean> = flowOf(invalid)
        override val isAiFeatureEnabled: Flow<Boolean> = flowOf(enabled)
        var validationCalls = 0

        override suspend fun getMaskedApiKey(): String? = if (configured) "••••1234" else null
        override suspend fun saveApiKey(apiKey: String): AiResult<Unit> = AiResult.Success(Unit)
        override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> = result
        override suspend fun validateConfiguredKey(): AiResult<Boolean> {
            validationCalls++
            return result
        }
        override suspend fun deleteApiKey() = Unit
        override suspend fun setAiFeatureEnabled(enabled: Boolean) = Unit
        override suspend fun extractTransactions(input: String, defaultCurrency: String): AiResult<List<TransactionCandidate>> =
            AiResult.Success(emptyList())
    }
}
