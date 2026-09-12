package com.suguru.expensetracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suguru.expensetracker.BuildConfig
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    object Loading : SettingsUiState
    data class Success(
        val settings: AppSettings,
        val appVersionName: String,
        val appVersionCode: Int
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { settings ->
            SettingsUiState.Success(
                settings = settings,
                appVersionName = try { BuildConfig.VERSION_NAME } catch (e: Exception) { "1.0" },
                appVersionCode = try { BuildConfig.VERSION_CODE } catch (e: Exception) { 1 }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState.Loading
        )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemeMode(themeMode)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setPreferredCurrency(currencyCode: String?) {
        viewModelScope.launch {
            try {
                settingsRepository.setPreferredCurrency(currencyCode)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setWeekStart(weekStart: WeekStart) {
        viewModelScope.launch {
            try {
                settingsRepository.setWeekStart(weekStart)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setDateFormat(dateFormat: DateFormatPreference) {
        viewModelScope.launch {
            try {
                settingsRepository.setDateFormat(dateFormat)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setTimeFormat(timeFormat: TimeFormatPreference) {
        viewModelScope.launch {
            try {
                settingsRepository.setTimeFormat(timeFormat)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setShowCurrencyCode(showCurrencyCode: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setShowCurrencyCode(showCurrencyCode)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun setConfirmBeforeDelete(confirmBeforeDelete: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setConfirmBeforeDelete(confirmBeforeDelete)
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }
}
