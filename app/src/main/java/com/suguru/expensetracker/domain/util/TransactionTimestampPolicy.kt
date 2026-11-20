package com.suguru.expensetracker.domain.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class TransactionTimestampError {
    BEFORE_MINIMUM_DATE,
    IN_FUTURE
}

/**
 * Calendar-safe selection and validation for actual financial transaction timestamps.
 * Material 3 DatePicker values represent UTC calendar dates, not local instants.
 */
object TransactionTimestampPolicy {
    val EARLIEST_DATE: LocalDate = LocalDate.of(1986, 1, 1)

    fun dateFromPickerUtcMillis(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun pickerUtcMillisForDate(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerUtcMillisForInstant(instant: Instant, zoneId: ZoneId): Long =
        pickerUtcMillisForDate(instant.atZone(zoneId).toLocalDate())

    fun isSelectablePickerDate(utcMillis: Long, now: Instant, zoneId: ZoneId): Boolean {
        val date = dateFromPickerUtcMillis(utcMillis)
        val today = now.atZone(zoneId).toLocalDate()
        return date >= EARLIEST_DATE && date <= today
    }

    fun selectDate(
        current: Instant,
        pickerUtcMillis: Long,
        now: Instant,
        zoneId: ZoneId
    ): Instant {
        val selectedDate = dateFromPickerUtcMillis(pickerUtcMillis)
        val today = now.atZone(zoneId).toLocalDate()
        require(selectedDate >= EARLIEST_DATE) { "Transactions cannot be dated before January 1, 1986." }
        require(selectedDate <= today) { "Transactions cannot be dated in the future." }

        val currentDateTime = current.atZone(zoneId)
        if (selectedDate == currentDateTime.toLocalDate()) {
            return current.coerceAtMost(now)
        }

        val selectedLocalDateTime = LocalDateTime.of(selectedDate, currentDateTime.toLocalTime())
        val selectedInstant = selectedLocalDateTime.atZone(zoneId).toInstant()
        return if (selectedDate == today && selectedInstant > now) now else selectedInstant
    }

    fun selectTime(
        current: Instant,
        hour: Int,
        minute: Int,
        now: Instant,
        zoneId: ZoneId
    ): Instant {
        require(hour in 0..23 && minute in 0..59)
        val currentDate = current.atZone(zoneId).toLocalDate()
        require(currentDate >= EARLIEST_DATE) { "Transactions cannot be dated before January 1, 1986." }
        val selectedInstant = LocalDateTime.of(
            currentDate,
            current.atZone(zoneId).toLocalTime().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        ).atZone(zoneId).toInstant()
        val today = now.atZone(zoneId).toLocalDate()
        return if (currentDate == today && selectedInstant > now) now else selectedInstant
    }

    fun validationError(
        transactionTime: Instant,
        now: Instant,
        zoneId: ZoneId
    ): TransactionTimestampError? {
        if (transactionTime.atZone(zoneId).toLocalDate() < EARLIEST_DATE) {
            return TransactionTimestampError.BEFORE_MINIMUM_DATE
        }
        if (transactionTime > now) return TransactionTimestampError.IN_FUTURE
        return null
    }

    fun validationError(transactionTime: Instant, clock: Clock): TransactionTimestampError? =
        validationError(transactionTime, clock.instant(), clock.zone)
}
