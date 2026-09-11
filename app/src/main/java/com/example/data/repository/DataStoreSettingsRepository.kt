package com.example.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.domain.model.AppSettings
import com.example.domain.model.DateFormatPreference
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeFormatPreference
import com.example.domain.model.WeekStart
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PREFERRED_CURRENCY_CODE = stringPreferencesKey("preferred_currency_code")
        val WEEK_START = stringPreferencesKey("week_start")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val SHOW_CURRENCY_CODE = booleanPreferencesKey("show_currency_code")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val LAST_BACKUP_AT = stringPreferencesKey("last_backup_at")
        val LAST_BACKUP_FILE_NAME = stringPreferencesKey("last_backup_file_name")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeMode = try {
                ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }

            val preferredCurrencyCode = preferences[PreferencesKeys.PREFERRED_CURRENCY_CODE]

            val weekStart = try {
                WeekStart.valueOf(preferences[PreferencesKeys.WEEK_START] ?: WeekStart.MONDAY.name)
            } catch (e: IllegalArgumentException) {
                WeekStart.MONDAY
            }

            val dateFormat = try {
                DateFormatPreference.valueOf(preferences[PreferencesKeys.DATE_FORMAT] ?: DateFormatPreference.SYSTEM_DEFAULT.name)
            } catch (e: IllegalArgumentException) {
                DateFormatPreference.SYSTEM_DEFAULT
            }

            val timeFormat = try {
                TimeFormatPreference.valueOf(preferences[PreferencesKeys.TIME_FORMAT] ?: TimeFormatPreference.SYSTEM_DEFAULT.name)
            } catch (e: IllegalArgumentException) {
                TimeFormatPreference.SYSTEM_DEFAULT
            }

            val showCurrencyCode = preferences[PreferencesKeys.SHOW_CURRENCY_CODE] ?: false
            val confirmBeforeDelete = preferences[PreferencesKeys.CONFIRM_BEFORE_DELETE] ?: true

            AppSettings(
                themeMode = themeMode,
                preferredCurrencyCode = preferredCurrencyCode,
                weekStart = weekStart,
                dateFormat = dateFormat,
                timeFormat = timeFormat,
                showCurrencyCode = showCurrencyCode,
                confirmBeforeDelete = confirmBeforeDelete
            )
        }

    override suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = value.name
        }
    }

    override suspend fun setPreferredCurrency(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.PREFERRED_CURRENCY_CODE)
            } else {
                preferences[PreferencesKeys.PREFERRED_CURRENCY_CODE] = value
            }
        }
    }

    override suspend fun setWeekStart(value: WeekStart) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEEK_START] = value.name
        }
    }

    override suspend fun setDateFormat(value: DateFormatPreference) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATE_FORMAT] = value.name
        }
    }

    override suspend fun setTimeFormat(value: TimeFormatPreference) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIME_FORMAT] = value.name
        }
    }

    override suspend fun setShowCurrencyCode(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_CURRENCY_CODE] = value
        }
    }

    override suspend fun setConfirmBeforeDelete(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONFIRM_BEFORE_DELETE] = value
        }
    }

    override val lastBackupAt: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_AT]
        }

    override val lastBackupFileName: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_FILE_NAME]
        }

    override suspend fun setLastBackupMetadata(at: String?, fileName: String?) {
        dataStore.edit { preferences ->
            if (at == null) {
                preferences.remove(PreferencesKeys.LAST_BACKUP_AT)
            } else {
                preferences[PreferencesKeys.LAST_BACKUP_AT] = at
            }
            if (fileName == null) {
                preferences.remove(PreferencesKeys.LAST_BACKUP_FILE_NAME)
            } else {
                preferences[PreferencesKeys.LAST_BACKUP_FILE_NAME] = fileName
            }
        }
    }
}
