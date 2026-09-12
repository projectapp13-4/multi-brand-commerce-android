@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.storefront.StorefrontMoney
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun englishZeroPriceRemainsNeutralCurrencyAmount() {
        val locale = Locale.ENGLISH
        setPriceContent(locale, minimum = money("0.00"), maximum = money("0.00"))

        composeRule.onNodeWithTag(PRICE_TAG).assertTextEquals(format("0.00", locale))
    }

    @Test
    fun turkishRangeUsesLocalizedAmountsWithoutInventingFreeCopy() {
        val locale = Locale.forLanguageTag("tr-TR")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localizedContext = context.createConfigurationContext(configuration(locale))
        setPriceContent(locale, minimum = money("0.00"), maximum = money("150.00"))

        composeRule
            .onNodeWithTag(PRICE_TAG)
            .assertTextEquals(
                localizedContext.getString(
                    R.string.price_range,
                    format("0.00", locale),
                    format("150.00", locale)
                )
            )
    }

    private fun setPriceContent(locale: Locale, minimum: StorefrontMoney, maximum: StorefrontMoney) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = configuration(locale)
        val localizedContext = context.createConfigurationContext(configuration)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration
            ) {
                CoreTestTheme(darkTheme = false) {
                    PriceBlock(
                        minimumPrice = minimum,
                        maximumPrice = maximum,
                        availabilityText = "Available",
                        unavailable = false,
                        priceTestTag = PRICE_TAG
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun configuration(locale: Locale): Configuration = Configuration(
        InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
    ).apply { setLocale(locale) }

    private fun money(amount: String): StorefrontMoney = StorefrontMoney(BigDecimal(amount), CURRENCY_CODE)

    private fun format(amount: String, locale: Locale): String = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance(CURRENCY_CODE)
    }.format(BigDecimal(amount))

    private companion object {
        const val PRICE_TAG = "price-block-price"
        const val CURRENCY_CODE = "TRY"
    }
}
