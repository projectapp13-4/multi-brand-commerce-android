package com.gurbakir.mobile.home

import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeMoneyFormattingTest {
    @Test
    fun `home money uses the full effective locale supplied by the activity`() {
        val money = StorefrontMoney(BigDecimal("1234.50"), "TRY")
        for (locale in listOf("tr-TR", "en-US", "en-GB").map(Locale::forLanguageTag)) {
            val expected =
                NumberFormat.getCurrencyInstance(locale).apply {
                    currency = Currency.getInstance("TRY")
                }.format(money.amount)
            assertEquals(expected, formatHomeMoney(money, locale))
        }
    }
}
