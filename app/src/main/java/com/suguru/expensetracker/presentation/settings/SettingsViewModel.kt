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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        val keyVerificationState: KeyVerificationState = KeyVerificationState.Idle
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

sealed interface KeyVerificationState {
    object Idle : KeyVerificationState
    object Verifying : KeyVerificationState
    object Valid : KeyVerificationState
    data class Invalid(val message: String) : KeyVerificationState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository? = null
) : ViewModel() {

    private val _keyVerificationState = MutableStateFlow<KeyVerificationState>(KeyVerificationState.Idle)
    private val _maskedKeyFlow = MutableStateFlow<String?>(null)

    init {
        loadMaskedKey()
    }

    private fun loadMaskedKey() {
        viewModelScope.launch {
            _maskedKeyFlow.value = aiRepository?.getMaskedApiKey()
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        aiRepository?.isAiFeatureEnabled ?: kotlinx.coroutines.flow.flowOf(true),
        aiRepository?.isAiConfigured ?: kotlinx.coroutines.flow.flowOf(false),
        _maskedKeyFlow,
        _keyVerificationState
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
        viewModelScope.launch {
            aiRepository?.setAiFeatureEnabled(enabled)
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.trim().isBlank()) return@launch
            aiRepository?.saveApiKey(apiKey.trim())
            _maskedKeyFlow.value = aiRepository?.getMaskedApiKey()
            _keyVerificationState.value = KeyVerificationState.Idle
        }
    }

    fun verifySavedGeminiApiKey() {
        val aiRepo = aiRepository ?: return
        viewModelScope.launch {
            _keyVerificationState.value = KeyVerificationState.Verifying
            val result = aiRepo.validateConfiguredKey()
            when (result) {
                is AiResult.Success -> {
                    _keyVerificationState.value = KeyVerificationState.Valid
                }
                is AiResult.Failure -> {
                    val msg = when (result.error) {
                        is AiError.InvalidApiKey -> "Invalid API key or unauthorized."
                        is AiError.RateLimitExceeded -> "Quota exceeded (Rate limit)."
                        is AiError.NetworkError -> "Network connection failed."
                        is AiError.MissingApiKey -> "No API key configured."
                        else -> "Verification failed."
                    }
                    _keyVerificationState.value = KeyVerificationState.Invalid(msg)
                }
            }
        }
    }

    fun deleteGeminiApiKey() {
        viewModelScope.launch {
            aiRepository?.deleteApiKey()
            _maskedKeyFlow.value = null
            _keyVerificationState.value = KeyVerificationState.Idle
        }
    }
}
