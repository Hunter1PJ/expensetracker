package com.suguru.expensetracker.presentation.settings

import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.parser.TransactionCandidate
import com.suguru.expensetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var aiRepository: FakeAiRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        aiRepository = FakeAiRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun verificationTransitionsFromVerifyingToValid() = runTest(testDispatcher) {
        val response = CompletableDeferred<AiResult<Boolean>>()
        aiRepository.validationResponse = response
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        testDispatcher.scheduler.runCurrent()
        assertEquals(KeyVerificationState.Verifying, successState(viewModel).keyVerificationState)

        response.complete(AiResult.Success(true))
        advanceUntilIdle()

        assertEquals(KeyVerificationState.Valid, successState(viewModel).keyVerificationState)
        assertEquals(listOf(SettingsEvent.ApiKeyVerified), events)
    }

    @Test
    fun failedVerificationDoesNotEmitSuccessEvent() = runTest(testDispatcher) {
        aiRepository.validationResponse = CompletableDeferred(AiResult.Failure(AiError.InvalidApiKey))
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        advanceUntilIdle()

        assertTrue(successState(viewModel).keyVerificationState is KeyVerificationState.Invalid)
        assertTrue(events.isEmpty())
    }

    @Test
    fun temporaryServerFailureDoesNotMarkTheKeyInvalid() = runTest(testDispatcher) {
        val response = CompletableDeferred<AiResult<Boolean>>()
        aiRepository.validationResponse = response
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        testDispatcher.scheduler.runCurrent()
        assertEquals(KeyVerificationState.Verifying, successState(viewModel).keyVerificationState)

        response.complete(AiResult.Failure(AiError.ServerError(404, "safe test message")))
        advanceUntilIdle()

        assertEquals(
            KeyVerificationState.TemporaryFailure(AiError.ServerError(404, "safe test message")),
            successState(viewModel).keyVerificationState
        )
    }

    @Test
    fun repeatedVerificationTapsLaunchOnlyOneRequest() = runTest(testDispatcher) {
        val response = CompletableDeferred<AiResult<Boolean>>()
        aiRepository.validationResponse = response
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        viewModel.verifySavedGeminiApiKey()
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, aiRepository.validationCallCount)
        assertEquals(KeyVerificationState.Verifying, successState(viewModel).keyVerificationState)
        response.complete(AiResult.Success(true))
        advanceUntilIdle()
        assertEquals(KeyVerificationState.Valid, successState(viewModel).keyVerificationState)
    }

    @Test
    fun cancellingVerificationReturnsToIdleAndIgnoresLateSuccess() = runTest(testDispatcher) {
        val response = CompletableDeferred<AiResult<Boolean>>()
        aiRepository.validationResponse = response
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        testDispatcher.scheduler.runCurrent()
        viewModel.cancelVerification()
        response.complete(AiResult.Success(true))
        advanceUntilIdle()

        assertEquals(KeyVerificationState.Idle, successState(viewModel).keyVerificationState)
        assertTrue(events.isEmpty())
    }

    @Test
    fun saveSuccessEmitsConfirmationEventAfterStorageSucceeds() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.saveGeminiApiKey("new-key")
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.ApiKeySaved), events)
    }

    @Test
    fun saveFailureDoesNotEmitConfirmationEvent() = runTest(testDispatcher) {
        aiRepository.saveResult = AiResult.Failure(AiError.ExecutionError("storage failure"))
        val viewModel = createViewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.saveGeminiApiKey("new-key")
        advanceUntilIdle()

        assertTrue(successState(viewModel).keyVerificationState is KeyVerificationState.Invalid)
        assertTrue(events.isEmpty())
    }

    @Test
    fun savingOrDeletingKeyResetsSuccessfulVerification() = runTest(testDispatcher) {
        aiRepository.validationResponse = CompletableDeferred(AiResult.Success(true))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()

        viewModel.verifySavedGeminiApiKey()
        advanceUntilIdle()
        assertEquals(KeyVerificationState.Valid, successState(viewModel).keyVerificationState)

        viewModel.saveGeminiApiKey("replacement-key")
        advanceUntilIdle()
        assertEquals(KeyVerificationState.Valid, successState(viewModel).keyVerificationState)
        assertEquals(2, aiRepository.validationCallCount)

        viewModel.verifySavedGeminiApiKey()
        advanceUntilIdle()
        viewModel.deleteGeminiApiKey()
        advanceUntilIdle()
        assertEquals(KeyVerificationState.Idle, successState(viewModel).keyVerificationState)
    }

    @Test
    fun serverErrorSelectsLocalizedHttpDiagnosticResource() {
        assertEquals(
            R.string.ai_settings_error_server_http,
            settingsErrorMessageResource(AiError.ServerError(404, "safe test message"))
        )
    }

    private fun createViewModel() = SettingsViewModel(FakeSettingsRepository(), aiRepository)

    private fun successState(viewModel: SettingsViewModel) =
        viewModel.uiState.value as SettingsUiState.Success

    private class FakeAiRepository : AiRepository {
        override val isAuthenticated: Flow<Boolean> = flowOf(true)
        override val authenticatedUid: Flow<String?> = flowOf("test_uid")
        override val isAiConfigured: Flow<Boolean> = flowOf(true)
        private val verified = MutableStateFlow(false)
        override val isKeyVerified: Flow<Boolean> = verified
        override val isKeyInvalid: Flow<Boolean> = flowOf(false)
        override val isAiFeatureEnabled: Flow<Boolean> = flowOf(true)
        var validationResponse = CompletableDeferred<AiResult<Boolean>>()
        var validationCallCount = 0
        var saveResult: AiResult<Unit> = AiResult.Success(Unit)

        override suspend fun getMaskedApiKey(): String? = "********"
        override suspend fun saveApiKey(apiKey: String): AiResult<Unit> {
            if (saveResult is AiResult.Success) verified.value = false
            return saveResult
        }
        override suspend fun validateApiKey(apiKey: String): AiResult<Boolean> {
            val result = validationResponse.await()
            if (result is AiResult.Success && result.data) verified.value = true
            return result
        }
        override suspend fun validateConfiguredKey(): AiResult<Boolean> {
            validationCallCount++
            val result = validationResponse.await()
            if (result is AiResult.Success && result.data) verified.value = true
            return result
        }
        override suspend fun deleteApiKey() {
            verified.value = false
        }
        override suspend fun setAiFeatureEnabled(enabled: Boolean) = Unit
        override suspend fun extractTransactions(
            input: String,
            defaultCurrency: String
        ): AiResult<List<TransactionCandidate>> = AiResult.Success(emptyList())
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(
            AppSettings(
                themeMode = ThemeMode.SYSTEM,
                preferredCurrencyCode = null,
                weekStart = WeekStart.MONDAY,
                dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
                timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
                showCurrencyCode = true,
                confirmBeforeDelete = true
            )
        )

        override val lastBackupAt: Flow<String?> = flowOf(null)
        override val lastBackupFileName: Flow<String?> = flowOf(null)
        override suspend fun restoreSettings(value: AppSettings) = Unit
        override suspend fun setThemeMode(value: ThemeMode) = Unit
        override suspend fun setPreferredCurrency(value: String?) = Unit
        override suspend fun setWeekStart(value: WeekStart) = Unit
        override suspend fun setDateFormat(value: DateFormatPreference) = Unit
        override suspend fun setTimeFormat(value: TimeFormatPreference) = Unit
        override suspend fun setShowCurrencyCode(value: Boolean) = Unit
        override suspend fun setConfirmBeforeDelete(value: Boolean) = Unit
        override suspend fun setLastBackupMetadata(at: String?, fileName: String?) = Unit
    }
}