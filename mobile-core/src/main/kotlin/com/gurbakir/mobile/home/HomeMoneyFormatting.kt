package com.gurbakir.mobile.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.storefront.StorefrontMoney
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
internal fun StorefrontMoney.localizedText(): String {
    val configuration = LocalConfiguration.current
    val locale = effectiveForegroundLocale(configuration)
    return remember(amount, currencyCode, locale) {
        formatHomeMoney(this, locale)
    }
}

internal fun formatHomeMoney(money: StorefrontMoney, locale: Locale): String = runCatching {
    NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance(money.currencyCode)
    }.format(money.amount)
}.getOrElse { "${money.amount.toPlainString()} ${money.currencyCode}" }
