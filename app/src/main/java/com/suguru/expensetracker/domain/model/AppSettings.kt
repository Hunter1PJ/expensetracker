package com.suguru.expensetracker.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class WeekStart {
    MONDAY,
    SUNDAY
}

enum class DateFormatPreference {
    SYSTEM_DEFAULT,
    DD_MM_YYYY,
    MM_DD_YYYY,
    YYYY_MM_DD
}

enum class TimeFormatPreference {
    SYSTEM_DEFAULT,
    HOUR_24,
    HOUR_12
}

data class AppSettings(
    val themeMode: ThemeMode,
    val preferredCurrencyCode: String?,
    val weekStart: WeekStart,
    val dateFormat: DateFormatPreference,
    val timeFormat: TimeFormatPreference,
    val showCurrencyCode: Boolean,
    val confirmBeforeDelete: Boolean
)
