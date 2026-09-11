package com.example.domain.repository

import com.example.domain.model.AppSettings
import com.example.domain.model.DateFormatPreference
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeFormatPreference
import com.example.domain.model.WeekStart
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setPreferredCurrency(value: String?)
    suspend fun setWeekStart(value: WeekStart)
    suspend fun setDateFormat(value: DateFormatPreference)
    suspend fun setTimeFormat(value: TimeFormatPreference)
    suspend fun setShowCurrencyCode(value: Boolean)
    suspend fun setConfirmBeforeDelete(value: Boolean)

    val lastBackupAt: Flow<String?>
    val lastBackupFileName: Flow<String?>
    suspend fun setLastBackupMetadata(at: String?, fileName: String?)
}
