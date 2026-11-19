package com.suguru.expensetracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.BuildConfig
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiRepository
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    object Loading : SettingsUiState
    data class Success(
        val settings: AppSettings,
        val appVersionName: String,
        val appVersionCode: Int,
        val isAiFeatureEnabled: Boolean = true,
        val isAiConfigured: Boolean = false,
        val maskedApiKey: String? = null,
        val keyVerificationState: KeyVerificationState = KeyVerificationState.Idle,
        val isSignedIn: Boolean = false,
        val isAiProfileAvailable: Boolean = true,
        val hasLegacyApiKey: Boolean = false
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

sealed interface KeyVerificationState {
    object Idle : KeyVerificationState
    object Verifying : KeyVerificationState
    object Valid : KeyVerificationState
    data class Invalid(val error: AiError) : KeyVerificationState
    data class TemporaryFailure(val error: AiError) : KeyVerificationState
}

sealed interface SettingsEvent {
    object ApiKeySaved : SettingsEvent
    object ApiKeyVerified : SettingsEvent
    object LegacyKeyMigrated : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository? = null,
    initialInvalidApiKey: Boolean = false
) : ViewModel() {

    private val _keyVerificationState = MutableStateFlow<KeyVerificationState>(KeyVerificationState.Idle)
    private val _maskedKeyFlow = MutableStateFlow<String?>(null)
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    private var verificationJob: Job? = null

    val events = _events.receiveAsFlow()
    private val isSignedInFlow = aiRepository?.isAuthenticated ?: kotlinx.coroutines.flow.flowOf(false)
    private val isAiProfileAvailableFlow = aiRepository?.isAiProfileAvailable ?: kotlinx.coroutines.flow.flowOf(false)
    private val hasLegacyKeyFlow = aiRepository?.hasUnassignedLegacyKey ?: kotlinx.coroutines.flow.flowOf(false)

    init {
        if (initialInvalidApiKey) {
            _keyVerificationState.value = KeyVerificationState.Invalid(AiError.InvalidApiKey)
        }
        viewModelScope.launch {
            var lastProfile: String? = null
            var firstEmission = true
            (aiRepository?.activeAiProfileId ?: kotlinx.coroutines.flow.flowOf(null))
                .collect { profile ->
                    if (firstEmission || profile != lastProfile) {
                        firstEmission = false
                        lastProfile = profile
                        cancelVerification()
                        _maskedKeyFlow.value = null
                        _keyVerificationState.value = KeyVerificationState.Idle
                        if (!profile.isNullOrBlank()) {
                            _maskedKeyFlow.value = aiRepository?.getMaskedApiKey()
                        }
                    }
                }
        }
    }

    private val aiVerificationFlow: Flow<KeyVerificationState> = combine(
        aiRepository?.isKeyVerified ?: kotlinx.coroutines.flow.flowOf(false),
        _keyVerificationState
    ) { isVerified, verifyState ->
        when {
            verifyState is KeyVerificationState.Verifying -> KeyVerificationState.Verifying
            verifyState is KeyVerificationState.Invalid -> verifyState
            isVerified -> KeyVerificationState.Valid
            verifyState is KeyVerificationState.TemporaryFailure -> verifyState
            verifyState is KeyVerificationState.Valid -> KeyVerificationState.Valid
            else -> KeyVerificationState.Idle
        }
    }

    private val baseUiState = combine(
        settingsRepository.settings,
        aiRepository?.isAiFeatureEnabled ?: kotlinx.coroutines.flow.flowOf(false),
        aiRepository?.isAiConfigured ?: kotlinx.coroutines.flow.flowOf(false),
        _maskedKeyFlow,
        aiVerificationFlow
    ) { settings, aiEnabled, aiConfigured, maskedKey, verifyState ->
        SettingsUiState.Success(
            settings = settings,
            appVersionName = try { BuildConfig.VERSION_NAME } catch (e: Exception) { "1.5.0" },
            appVersionCode = try { BuildConfig.VERSION_CODE } catch (e: Exception) { 6 },
            isAiFeatureEnabled = aiEnabled,
            isAiConfigured = aiConfigured,
            maskedApiKey = maskedKey,
            keyVerificationState = verifyState
        )
    }
    val uiState: StateFlow<SettingsUiState> = combine(
        baseUiState,
        isSignedInFlow,
        isAiProfileAvailableFlow,
        hasLegacyKeyFlow
    ) { base, signedIn, profileAvailable, legacyKey ->
        if (base is SettingsUiState.Success) {
            base.copy(
                isSignedIn = signedIn,
                isAiProfileAvailable = profileAvailable,
                hasLegacyApiKey = signedIn && legacyKey
            )
        } else base
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState.Loading
    )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemeMode(themeMode)
            } catch (_: Exception) {}
        }
    }

    fun setPreferredCurrency(currencyCode: String?) {
        viewModelScope.launch {
            try {
                settingsRepository.setPreferredCurrency(currencyCode)
            } catch (_: Exception) {}
        }
    }

    fun setWeekStart(weekStart: WeekStart) {
        viewModelScope.launch {
            try {
                settingsRepository.setWeekStart(weekStart)
            } catch (_: Exception) {}
        }
    }

    fun setDateFormat(dateFormat: DateFormatPreference) {
        viewModelScope.launch {
            try {
                settingsRepository.setDateFormat(dateFormat)
            } catch (_: Exception) {}
        }
    }

    fun setTimeFormat(timeFormat: TimeFormatPreference) {
        viewModelScope.launch {
            try {
                settingsRepository.setTimeFormat(timeFormat)
            } catch (_: Exception) {}
        }
    }

    fun setShowCurrencyCode(showCurrencyCode: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setShowCurrencyCode(showCurrencyCode)
            } catch (_: Exception) {}
        }
    }

    fun setConfirmBeforeDelete(confirmBeforeDelete: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setConfirmBeforeDelete(confirmBeforeDelete)
            } catch (_: Exception) {}
        }
    }

    // AI BYOK Methods
    fun setAiFeatureEnabled(enabled: Boolean) {
        val profileAvailable = (uiState.value as? SettingsUiState.Success)?.isAiProfileAvailable == true
        if (enabled && !profileAvailable) return
        if (!enabled) cancelVerification()
        viewModelScope.launch {
            aiRepository?.setAiFeatureEnabled(enabled)
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        if ((uiState.value as? SettingsUiState.Success)?.isAiProfileAvailable != true) return
        if (apiKey.trim().isBlank()) return
        cancelVerification()
        viewModelScope.launch {
            when (val result = aiRepository?.saveApiKey(apiKey.trim())) {
                is AiResult.Success -> {
                    _maskedKeyFlow.value = aiRepository.getMaskedApiKey()
                    _keyVerificationState.value = KeyVerificationState.Idle
                    _events.send(SettingsEvent.ApiKeySaved)
                    verifySavedGeminiApiKey()
                }
                is AiResult.Failure -> _keyVerificationState.value = KeyVerificationState.Invalid(result.error)
                null -> Unit
            }
        }
    }

    fun verifySavedGeminiApiKey() {
        if ((uiState.value as? SettingsUiState.Success)?.isAiProfileAvailable != true) return
        val aiRepo = aiRepository ?: return
        if (_keyVerificationState.value is KeyVerificationState.Verifying || verificationJob?.isActive == true) return

        _keyVerificationState.value = KeyVerificationState.Verifying
        verificationJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                val result = aiRepo.validateConfiguredKey()
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AiResult.Success -> {
                        _keyVerificationState.value = KeyVerificationState.Valid
                        _events.send(SettingsEvent.ApiKeyVerified)
                    }
                    is AiResult.Failure -> {
                        _keyVerificationState.value = if (result.error is AiError.InvalidApiKey) {
                            KeyVerificationState.Invalid(result.error)
                        } else {
                            KeyVerificationState.TemporaryFailure(result.error)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (verificationJob === currentJob) {
                    _keyVerificationState.value = KeyVerificationState.Idle
                }
                throw cancelled
            } finally {
                if (verificationJob === currentJob) {
                    verificationJob = null
                    if (_keyVerificationState.value is KeyVerificationState.Verifying) {
                        _keyVerificationState.value = KeyVerificationState.Idle
                    }
                }
            }
        }
    }

    fun cancelVerification() {
        val job = verificationJob
        verificationJob = null
        if (_keyVerificationState.value is KeyVerificationState.Verifying) {
            _keyVerificationState.value = KeyVerificationState.Idle
        }
        job?.cancel()
    }

    fun migrateLegacyGeminiApiKey() {
        if ((uiState.value as? SettingsUiState.Success)?.isSignedIn != true) return
        viewModelScope.launch {
            when (aiRepository?.migrateLegacyKeyToCurrentUid(confirmed = true)) {
                is AiResult.Success -> {
                    _maskedKeyFlow.value = aiRepository.getMaskedApiKey()
                    _keyVerificationState.value = KeyVerificationState.Idle
                    _events.send(SettingsEvent.LegacyKeyMigrated)
                    if ((uiState.value as? SettingsUiState.Success)?.isAiFeatureEnabled == true) {
                        verifySavedGeminiApiKey()
                    }
                }
                is AiResult.Failure, null -> Unit
            }
        }
    }

    fun deleteGeminiApiKey() {
        if ((uiState.value as? SettingsUiState.Success)?.isAiProfileAvailable != true) return
        cancelVerification()
        viewModelScope.launch {
            aiRepository?.deleteApiKey()
            _maskedKeyFlow.value = null
            _keyVerificationState.value = KeyVerificationState.Idle
        }
    }
}
