package com.suguru.expensetracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TransactionTimestampPolicyTest {
    private val zone = ZoneId.of("Asia/Tashkent")
    private val now = Instant.parse("2026-11-18T13:01:00Z") // 18:01 local

    @Test fun datePickerUtcDateIsNotShiftedByLocalTimezone() {
        val pickerMillis = TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 11, 18), TransactionTimestampPolicy.dateFromPickerUtcMillis(pickerMillis))
        assertEquals(LocalDate.of(2026, 11, 18), Instant.ofEpochMilli(pickerMillis).atZone(zone).toLocalDate())
    }

    @Test fun selectingSameOrDifferentDatePreservesTimeOfDay() {
        val selected = Instant.parse("2026-11-18T11:22:37Z")
        val same = TransactionTimestampPolicy.selectDate(
            selected, TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(2026, 11, 18)), now, zone
        )
        assertEquals(selected, same)
        val previous = TransactionTimestampPolicy.selectDate(
            selected, TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(2026, 11, 17)), now, zone
        )
        assertEquals(LocalTime.of(16, 22, 37), previous.atZone(zone).toLocalTime())
    }

    @Test fun selectingTimePreservesDateAndClampsFutureTimeToday() {
        val selected = Instant.parse("2026-11-17T07:30:00Z")
        val changed = TransactionTimestampPolicy.selectTime(selected, 9, 45, now, zone)
        assertEquals(LocalDate.of(2026, 11, 17), changed.atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(9, 45), changed.atZone(zone).toLocalTime())
        val today = TransactionTimestampPolicy.selectTime(now, 18, 2, now, zone)
        assertEquals(now, today)
    }

    @Test fun dateBoundsAndFutureValidationAreEnforced() {
        assertTrue(TransactionTimestampPolicy.isSelectablePickerDate(
            TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(1986, 1, 1)), now, zone
        ))
        assertFalse(TransactionTimestampPolicy.isSelectablePickerDate(
            TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(1985, 12, 31)), now, zone
        ))
        assertFalse(TransactionTimestampPolicy.isSelectablePickerDate(
            TransactionTimestampPolicy.pickerUtcMillisForDate(LocalDate.of(2026, 11, 19)), now, zone
        ))
        assertNull(TransactionTimestampPolicy.validationError(now, now, zone))
        assertEquals(TransactionTimestampError.IN_FUTURE,
            TransactionTimestampPolicy.validationError(now.plusSeconds(1), now, zone))
        assertEquals(TransactionTimestampError.BEFORE_MINIMUM_DATE,
            TransactionTimestampPolicy.validationError(
                LocalDate.of(1985, 12, 31).atStartOfDay(zone).toInstant(), now, zone
            ))
    }
}
