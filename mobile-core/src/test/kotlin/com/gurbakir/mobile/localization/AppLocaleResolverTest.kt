package com.gurbakir.mobile.localization

import com.gurbakir.foundation.config.LocalizationPolicy
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppLocaleResolverTest {
    private val gurbakir = LocalizationPolicy(defaultLocaleTag = "tr", supportedLocaleTags = listOf("tr", "en"))

    @Test
    fun `gurbakir preserves requested regions for supported languages`() {
        assertEquals(listOf("tr-TR", "tr"), resolveTags(gurbakir, "tr-TR"))
        assertEquals(listOf("en-US", "tr"), resolveTags(gurbakir, "en-US"))
        assertEquals(listOf("en-GB", "tr"), resolveTags(gurbakir, "en-GB"))
    }

    @Test
    fun `unsupported requests continue through device order then fall back to default`() {
        assertEquals(listOf("en-GB", "tr"), resolveTags(gurbakir, "fr-FR", "en-GB"))
        assertEquals(listOf("tr"), resolveTags(gurbakir, "fr-FR"))
    }

    @Test
    fun `debug preserves pseudo locales while release skips them`() {
        assertEquals(listOf("en-XA", "tr"), resolveTags(gurbakir, "en-XA", allowPseudoLocales = true))
        assertEquals(listOf("ar-XB", "tr"), resolveTags(gurbakir, "ar-XB", allowPseudoLocales = true))
        assertEquals(listOf("tr"), resolveTags(gurbakir, "en-XA"))
        assertEquals(listOf("en-US", "tr"), resolveTags(gurbakir, "ar-XB", "en-US"))
    }

    @Test
    fun `synthetic keeps supported requested English region and appends Canadian fallback once`() {
        val synthetic = LocalizationPolicy(defaultLocaleTag = "en-CA", supportedLocaleTags = listOf("en-CA"))

        assertEquals(listOf("en-US", "en-CA"), resolveTags(synthetic, "en-US", "en-US"))
        assertEquals(listOf("en-CA"), resolveTags(synthetic, "tr-TR"))
    }

    @Test
    fun `resolver rejects an invalid policy instead of inventing a locale`() {
        assertThrows<IllegalArgumentException> {
            resolveAppLocales(LocalizationPolicy("fr", listOf("tr", "en")), listOf(Locale.ENGLISH), false)
        }
    }

    private fun resolveTags(
        policy: LocalizationPolicy,
        vararg requestedTags: String,
        allowPseudoLocales: Boolean = false
    ): List<String> = resolveAppLocales(
        policy = policy,
        requestedLocales = requestedTags.map(Locale::forLanguageTag),
        allowPseudoLocales = allowPseudoLocales
    ).map(Locale::toLanguageTag)
}
