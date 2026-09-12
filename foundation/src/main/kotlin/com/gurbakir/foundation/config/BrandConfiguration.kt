package com.gurbakir.foundation.config

import java.net.URI

private val BRAND_KEY_PATTERN = Regex("^[a-z][a-z0-9-]{1,31}$")
private val RESOURCE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
private val ANALYTICS_NAMESPACE_PATTERN = Regex("^[a-z][a-z0-9_]{1,31}$")
private const val MAXIMUM_DISPLAY_NAME_LENGTH = 64
private const val MAXIMUM_DP_TOKEN = 128
private const val MAXIMUM_SP_TOKEN = 128
private const val MAXIMUM_MOTION_DURATION_MS = 1_000
private const val MAXIMUM_ARGB_VALUE = 0xFFFF_FFFFL
private const val HTTPS_PORT = 443

@JvmInline
value class ArgbColor(val value: Long) {
    init {
        require(value in 0..MAXIMUM_ARGB_VALUE) { "ARGB colors must be unsigned 32-bit values." }
    }
}

data class BrandColorSchemeTokens(
    val primary: ArgbColor,
    val onPrimary: ArgbColor,
    val primaryContainer: ArgbColor,
    val onPrimaryContainer: ArgbColor,
    val secondary: ArgbColor,
    val onSecondary: ArgbColor,
    val secondaryContainer: ArgbColor,
    val onSecondaryContainer: ArgbColor,
    val tertiary: ArgbColor,
    val onTertiary: ArgbColor,
    val tertiaryContainer: ArgbColor,
    val onTertiaryContainer: ArgbColor,
    val background: ArgbColor,
    val onBackground: ArgbColor,
    val surface: ArgbColor,
    val onSurface: ArgbColor,
    val surfaceVariant: ArgbColor,
    val onSurfaceVariant: ArgbColor,
    val surfaceContainerLow: ArgbColor,
    val surfaceContainer: ArgbColor,
    val surfaceContainerHigh: ArgbColor,
    val outline: ArgbColor,
    val outlineVariant: ArgbColor,
    val error: ArgbColor,
    val onError: ArgbColor,
    val errorContainer: ArgbColor,
    val onErrorContainer: ArgbColor,
    val inverseSurface: ArgbColor,
    val inverseOnSurface: ArgbColor,
    val inversePrimary: ArgbColor,
    val scrim: ArgbColor
)

data class BrandColorTokens(val light: BrandColorSchemeTokens, val dark: BrandColorSchemeTokens)

data class BrandShapeTokens(val smallCornerDp: Int, val mediumCornerDp: Int, val largeCornerDp: Int) {
    init {
        require(
            smallCornerDp in 0..MAXIMUM_DP_TOKEN &&
                mediumCornerDp in smallCornerDp..MAXIMUM_DP_TOKEN &&
                largeCornerDp in mediumCornerDp..MAXIMUM_DP_TOKEN
        ) { "Brand corner tokens must be ordered, non-negative, and bounded." }
    }
}

data class BrandSpacingTokens(val compactDp: Int, val normalDp: Int, val generousDp: Int, val sectionDp: Int) {
    init {
        require(
            compactDp in 1..MAXIMUM_DP_TOKEN &&
                normalDp in compactDp..MAXIMUM_DP_TOKEN &&
                generousDp in normalDp..MAXIMUM_DP_TOKEN &&
                sectionDp in generousDp..MAXIMUM_DP_TOKEN
        ) { "Brand spacing tokens must be positive, ordered, and bounded." }
    }
}

enum class BrandFontWeight {
    REGULAR,
    MEDIUM,
    SEMIBOLD
}

data class BrandTextStyleTokens(val fontSizeSp: Int, val lineHeightSp: Int, val fontWeight: BrandFontWeight) {
    init {
        require(fontSizeSp in 1..MAXIMUM_SP_TOKEN && lineHeightSp in fontSizeSp..MAXIMUM_SP_TOKEN) {
            "Brand text sizes must be positive, bounded, and use a line height at least as large as the font."
        }
    }
}

data class BrandTypographyTokens(
    val fontFamilyResourceName: String?,
    val headlineLarge: BrandTextStyleTokens,
    val headlineMedium: BrandTextStyleTokens,
    val headlineSmall: BrandTextStyleTokens,
    val titleLarge: BrandTextStyleTokens,
    val titleMedium: BrandTextStyleTokens,
    val titleSmall: BrandTextStyleTokens,
    val bodyLarge: BrandTextStyleTokens,
    val bodyMedium: BrandTextStyleTokens,
    val bodySmall: BrandTextStyleTokens,
    val labelLarge: BrandTextStyleTokens,
    val labelMedium: BrandTextStyleTokens,
    val labelSmall: BrandTextStyleTokens
) {
    init {
        require(fontFamilyResourceName == null || RESOURCE_NAME_PATTERN.matches(fontFamilyResourceName)) {
            "A configured brand font must use an Android-safe resource name."
        }
    }
}

data class BrandMotionTokens(val microFeedbackMs: Int, val contentReplacementMs: Int, val containerTransitionMs: Int) {
    init {
        require(
            microFeedbackMs in 1..MAXIMUM_MOTION_DURATION_MS &&
                contentReplacementMs in microFeedbackMs..MAXIMUM_MOTION_DURATION_MS &&
                containerTransitionMs in contentReplacementMs..MAXIMUM_MOTION_DURATION_MS
        ) { "Brand motion durations must be positive, ordered, and bounded." }
    }
}

data class BrandDesignTokens(
    val colors: BrandColorTokens,
    val typography: BrandTypographyTokens,
    val shapes: BrandShapeTokens,
    val spacing: BrandSpacingTokens,
    val motion: BrandMotionTokens
)

data class BrandAssetReferences(
    val appIconResourceName: String?,
    val logoResourceName: String?,
    val heroImageResourceName: String?
) {
    internal fun areValid(): Boolean = listOf(appIconResourceName, logoResourceName, heroImageResourceName)
        .filterNotNull()
        .all(RESOURCE_NAME_PATTERN::matches)
}

data class BrandLegalLinks(val privacyPolicyUrl: String?, val termsUrl: String?, val supportUrl: String?) {
    internal fun areValid(): Boolean = listOf(privacyPolicyUrl, termsUrl, supportUrl)
        .filterNotNull()
        .all(String::isPlainHttpsUri)
}

data class BrandConfiguration(
    val key: String,
    val displayName: String,
    val designTokens: BrandDesignTokens,
    val assets: BrandAssetReferences,
    val legalLinks: BrandLegalLinks,
    val analyticsEventNamespace: String
) {
    fun validationIssues(): Set<ConfigurationIssue> = buildSet {
        if (!BRAND_KEY_PATTERN.matches(key)) add(ConfigurationIssue.BRAND_KEY)
        if (displayName.isBlank() || displayName.length > MAXIMUM_DISPLAY_NAME_LENGTH) {
            add(ConfigurationIssue.BRAND_DISPLAY_NAME)
        }
        if (!assets.areValid()) add(ConfigurationIssue.BRAND_ASSETS)
        if (!legalLinks.areValid()) add(ConfigurationIssue.BRAND_LEGAL_LINKS)
        if (!ANALYTICS_NAMESPACE_PATTERN.matches(analyticsEventNamespace)) {
            add(ConfigurationIssue.BRAND_ANALYTICS_NAMESPACE)
        }
    }
}

private fun String.isPlainHttpsUri(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme == "https" &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null &&
        uri.port in setOf(-1, HTTPS_PORT)
}.getOrDefault(false)
