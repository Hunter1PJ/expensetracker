package com.suguru.expensetracker.presentation.util

import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeFormatterHelperTest {
    @Test
    fun `date formatter follows selected app locale for month names`() {
        val original = Locale.getDefault()
        try {
            val date = LocalDate.of(2026, 3, 5)
            val monthTexts = listOf("en", "uz", "ru").map { language ->
                Locale.setDefault(Locale.forLanguageTag(language))
                date.format(DateTimeFormatterHelper.getLocalDateFormatter(DateFormatPreference.SYSTEM_DEFAULT))
            }

            assertTrue(monthTexts[0].contains("Mar"))
            assertTrue(monthTexts[1].contains("mar", ignoreCase = true))
            assertTrue(monthTexts[2].contains("мар", ignoreCase = true))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `clock formatter stays twenty four hour`() {
        val original = Locale.getDefault()
        try {
            for (language in listOf("en", "uz", "ru")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                assertEquals(
                    "17:05",
                    LocalTime.of(17, 5).format(DateTimeFormatterHelper.getTimeFormatter(TimeFormatPreference.SYSTEM_DEFAULT))
                )
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
