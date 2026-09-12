package com.gurbakir.mobile.legal

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegalDateFormattingTest {
    @Test
    fun `legal dates use the activity effective locale rather than process default`() {
        val date = LocalDate.of(2026, 9, 9)
        for (locale in listOf("tr-TR", "en-US", "en-GB").map(Locale::forLanguageTag)) {
            val expected = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
            assertEquals(expected, formatLegalDate(date, locale))
        }
    }
}
