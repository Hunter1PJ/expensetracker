package com.example.presentation.util

import com.example.domain.model.DateFormatPreference
import com.example.domain.model.TimeFormatPreference
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

    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun getLocalDateFormatter(
        pref: DateFormatPreference
    ): DateTimeFormatter {
        val pattern = when (pref) {
            DateFormatPreference.SYSTEM_DEFAULT -> "MMM d, yyyy"
            DateFormatPreference.DD_MM_YYYY -> "dd/MM/yyyy"
            DateFormatPreference.MM_DD_YYYY -> "MM/dd/yyyy"
            DateFormatPreference.YYYY_MM_DD -> "yyyy/MM/dd"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }

    fun getTimeFormatter(
        pref: TimeFormatPreference
    ): DateTimeFormatter {
        val pattern = when (pref) {
            TimeFormatPreference.SYSTEM_DEFAULT,
            TimeFormatPreference.HOUR_24 -> "HH:mm"
            TimeFormatPreference.HOUR_12 -> "hh:mm a"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }

    fun formatTransactionTime(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT,
        timePref: TimeFormatPreference = TimeFormatPreference.SYSTEM_DEFAULT
    ): String {
        val zonedDateTime = instant.atZone(zoneId)
        val txDate = zonedDateTime.toLocalDate()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val timeFormatter = getTimeFormatter(timePref)
        val timePart = zonedDateTime.format(timeFormatter)

        return when (txDate) {
            today -> "Today, $timePart"
            yesterday -> "Yesterday, $timePart"
            else -> {
                val dateFormatter = getLocalDateFormatter(datePref)
                val datePart = zonedDateTime.format(dateFormatter)
                "$datePart, $timePart"
            }
        }
    }

    fun formatTimeOnly(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        timePref: TimeFormatPreference = TimeFormatPreference.SYSTEM_DEFAULT
    ): String {
        return instant.atZone(zoneId).format(getTimeFormatter(timePref))
    }

    fun formatGroupDateHeader(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId),
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT
    ): String {
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                date.format(getLocalDateFormatter(datePref))
            }
        }
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
        return yearMonth.format(monthYearFormatter)
    }

    fun formatLocalDate(
        date: LocalDate,
        datePref: DateFormatPreference = DateFormatPreference.SYSTEM_DEFAULT
    ): String {
        return date.format(getLocalDateFormatter(datePref))
    }
}
