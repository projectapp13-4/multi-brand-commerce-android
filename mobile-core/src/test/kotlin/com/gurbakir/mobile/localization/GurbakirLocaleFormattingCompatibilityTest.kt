package com.gurbakir.mobile.localization

import com.gurbakir.account.CustomerOrderMoney
import com.gurbakir.foundation.config.LocalizationPolicy
import com.gurbakir.mobile.home.formatHomeMoney
import com.gurbakir.mobile.order.formatOrderDate
import com.gurbakir.mobile.order.formatOrderMoney
import com.gurbakir.mobile.search.SearchHistoryNormalizationPolicy
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GurbakirLocaleFormattingCompatibilityTest {
    private val policy = LocalizationPolicy(defaultLocaleTag = "tr", supportedLocaleTags = listOf("tr", "en"))
    private val homeMoney = StorefrontMoney(BigDecimal("1234.50"), "TRY")
    private val orderMoney = CustomerOrderMoney("1234.50", "TRY")
    private val orderDate = "2026-09-09T10:15:30Z"

    @Test
    fun `every gurbakir compatibility row uses one effective locale while search keys remain fixed`() {
        val rows =
            listOf(
                CompatibilityRow("tr-TR", "tr-TR"),
                CompatibilityRow("en-US", "en-US"),
                CompatibilityRow("en-GB", "en-GB"),
                CompatibilityRow("fr-FR", "tr"),
                CompatibilityRow("en-XA", "en-XA", allowPseudoLocales = true),
                CompatibilityRow("ar-XB", "ar-XB", allowPseudoLocales = true)
            )

        val originalDefault = Locale.getDefault()
        try {
            rows.forEach { row ->
                val requested = Locale.forLanguageTag(row.requestedTag)
                Locale.setDefault(requested)
                val effective =
                    resolveAppLocales(
                        policy = policy,
                        requestedLocales = listOf(requested),
                        allowPseudoLocales = row.allowPseudoLocales
                    ).first()

                assertEquals(row.effectiveTag, effective.toLanguageTag(), row.requestedTag)
                assertEquals(platformMoney(effective), formatHomeMoney(homeMoney, effective), row.requestedTag)
                assertEquals(platformMoney(effective), formatOrderMoney(orderMoney, effective), row.requestedTag)
                assertEquals(platformDate(effective), formatOrderDate(orderDate, effective), row.requestedTag)
                assertEquals(
                    "ı bakır",
                    SearchHistoryNormalizationPolicy("tr-TR").normalize("I BAKIR"),
                    row.requestedTag
                )
            }
        } finally {
            Locale.setDefault(originalDefault)
        }
    }

    private fun platformMoney(locale: Locale): String = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("TRY")
    }.format(homeMoney.amount)

    private fun platformDate(locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(OffsetDateTime.parse(orderDate))

    private data class CompatibilityRow(
        val requestedTag: String,
        val effectiveTag: String,
        val allowPseudoLocales: Boolean = false
    )
}
