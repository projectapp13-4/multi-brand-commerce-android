package com.gurbakir.mobile.localization

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.os.ConfigurationCompat
import com.gurbakir.foundation.config.LocalizationPolicy
import java.util.Locale

private val PSEUDO_LOCALE_TAGS = setOf("en-XA", "ar-XB")

fun resolveAppLocales(
    policy: LocalizationPolicy,
    requestedLocales: List<Locale>,
    allowPseudoLocales: Boolean
): List<Locale> {
    require(policy.validationIssues().isEmpty()) { "A valid localization policy is required." }
    val supported = policy.supportedLocaleTags.map(Locale::forLanguageTag)
    val defaultLocale = Locale.forLanguageTag(policy.defaultLocaleTag)
    val selected =
        requestedLocales.firstNotNullOfOrNull { requested ->
            val canonical = Locale.forLanguageTag(requested.toLanguageTag())
            val tag = canonical.toLanguageTag()
            when {
                tag in PSEUDO_LOCALE_TAGS -> canonical.takeIf { allowPseudoLocales }
                supported.any { it.toLanguageTag() == tag } -> canonical
                supported.any { it.language == canonical.language } -> canonical
                else -> null
            }
        } ?: defaultLocale
    return listOf(selected, defaultLocale).distinctBy(Locale::toLanguageTag)
}

@SuppressLint("AppBundleLocaleChanges") // Device-supported locales or bundled base defaults only; no runtime picker.
fun Context.withAppLocale(policy: LocalizationPolicy, allowPseudoLocales: Boolean): Context {
    val requestedLocales =
        ConfigurationCompat.getLocales(resources.configuration).let { locales ->
            buildList {
                for (index in 0 until locales.size()) {
                    locales[index]?.let(::add)
                }
            }
        }
    val resolved = resolveAppLocales(policy, requestedLocales, allowPseudoLocales)
    val localizedConfiguration = Configuration(resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val localeTags = resolved.joinToString(",", transform = Locale::toLanguageTag)
        localizedConfiguration.setLocales(LocaleList.forLanguageTags(localeTags))
    } else {
        @Suppress("DEPRECATION")
        localizedConfiguration.setLocale(resolved.first())
    }
    return createConfigurationContext(localizedConfiguration)
}

fun effectiveForegroundLocale(configuration: Configuration): Locale =
    ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT
