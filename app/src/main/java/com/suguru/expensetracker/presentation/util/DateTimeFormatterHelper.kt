package com.suguru.expensetracker.presentation.util

import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation helper for formatting timestamps and month ranges nicely for the user.
 */
object DateTimeFormatterHelper {

    fun getLocalDateFormatter(
        pref: DateFormatPreference
    ): DateTimeFormatter {
        if (pref == DateFormatPreference.SYSTEM_DEFAULT) {
            return DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        }
        val pattern = when (pref) {
            DateFormatPreference.SYSTEM_DEFAULT -> error("Handled above")
            DateFormatPreference.DD_MM_YYYY -> "dd/MM/yyyy"
            DateFormatPreference.MM_DD_YYYY -> "MM/dd/yyyy"
            DateFormatPreference.YYYY_MM_DD -> "yyyy/MM/dd"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }

    @Suppress("UNUSED_PARAMETER")
    fun getTimeFormatter(
        pref: TimeFormatPreference
    ): DateTimeFormatter {
        // Clock presentation is intentionally independent of the Android system setting
        // and the legacy per-workspace preference.
        return DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    }

    fun formatTransactionTime(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT,
        timePref: TimeFormatPreference = TimeFormatPreference.SYSTEM_DEFAULT
    ): String {
        val zonedDateTime = instant.atZone(zoneId)
        val timeFormatter = getTimeFormatter(timePref)
        val dateFormatter = getLocalDateFormatter(datePref)
        val timePart = zonedDateTime.format(timeFormatter)
        val datePart = zonedDateTime.format(dateFormatter)
        return "$datePart, $timePart"
    }

    fun formatTimeOnly(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        timePref: TimeFormatPreference = TimeFormatPreference.SYSTEM_DEFAULT
    ): String {
        return instant.atZone(zoneId).format(getTimeFormatter(timePref))
    }

    @Suppress("UNUSED_PARAMETER")
    fun formatGroupDateHeader(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId),
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT
    ): String {
        return date.format(getLocalDateFormatter(datePref))
    }

    fun formatFullDateTime(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT,
        timePref: TimeFormatPreference = TimeFormatPreference.SYSTEM_DEFAULT
    ): String {
        val zonedDateTime = instant.atZone(zoneId)
        val dateFormatter = getLocalDateFormatter(datePref)
        val timeFormatter = getTimeFormatter(timePref)
        return "${zonedDateTime.format(dateFormatter)} ${zonedDateTime.format(timeFormatter)}"
    }

    fun formatCurrentMonthLabel(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val yearMonth = YearMonth.now(zoneId)
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        return yearMonth.format(formatter)
    }

    fun formatLocalDate(
        date: LocalDate,
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT
    ): String {
        return date.format(getLocalDateFormatter(datePref))
    }
}
