package com.suguru.expensetracker.domain.repository

import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
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
