package com.gurbakir.storefront

import com.gurbakir.foundation.config.ControlledPublicToken
import com.gurbakir.foundation.config.StorefrontConfiguration
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.TreeSet

internal data class OwnedOnboardingConfiguration(
    val storefront: StorefrontConfiguration,
    val menuHandle: String,
    val homeType: String,
    val homeHandle: String
)

private val projectionKeys =
    setOf(
        "onboarding.schemaVersion",
        "onboarding.sourceRegistrySha256",
        "onboarding.application",
        "onboarding.profile",
        "app.brandKey",
        "app.brandDisplayName",
        "app.profileDisplayName",
        "app.analyticsNamespace",
        "app.environmentId",
        "app.defaultLocale",
        "app.supportedLocales",
        "app.marketId",
        "app.marketCountryCode",
        "app.marketCurrencyCode",
        "app.supportedTerritory",
        "app.searchNormalizationLocale",
        "app.databaseName",
        "app.customerAccountMode",
        "app.customerAccountUserAgent",
        "app.customerAccountCallbackSchemeSuffix",
        "app.customerAccountCallbackHost",
        "app.customerAccountCallbackPath",
        "app.customerAccountScopes",
        "app.cartPreferences",
        "app.cartKeyAlias",
        "app.customerPreferences",
        "app.customerKeyAlias",
        "android.applicationId.debug",
        "android.applicationId.release",
        "web.collectionAppLinkOrigin",
        "web.collectionAppLinkPathPrefix",
        "web.productAppLinkOrigin",
        "web.productAppLinkPathPrefix",
        "web.orderAppLinkOrigin",
        "web.orderAppLinkPathPrefix",
        "web.legalSupportOrigin",
        "web.legalSupportPath.support",
        "web.legalSupportPath.privacy",
        "web.legalSupportPath.terms",
        "web.legalSupportPath.shipping",
        "web.legalSupportPath.returns",
        "web.legalSupportPath.legalNotice",
        "web.checkoutHostPolicy",
        "web.assetLinksMode",
        "web.manifestAutoVerify",
        "shopify.storefrontMode",
        "shopify.storefrontDomain",
        "shopify.storefrontApiVersion",
        "shopify.storefrontMediaOrigins",
        "shopify.catalogMenuHandle",
        "shopify.homeRootType",
        "shopify.homeRootHandle",
        "shopify.homeContentSchemaVersion",
        "firebase.mode",
        "firebase.ownershipKey",
        "firebase.configPath.debug",
        "firebase.configPath.release"
    )

private val localConfigurationKeys =
    setOf(
        "shopify.storefrontPublicToken",
        "shopify.customerAccountClientId",
        "shopify.customerAccountIssuer",
        "shopify.customerAccountAuthorizationEndpoint",
        "shopify.customerAccountTokenEndpoint",
        "shopify.customerAccountLogoutEndpoint",
        "shopify.customerAccountGraphqlEndpoint",
        "shopify.customerAccountRedirectUri"
    )

private fun loadStrictProperties(file: File, allowedKeys: Set<String>): Map<String, String> {
    require(file.isFile) { "Required onboarding configuration is missing." }
    require(file.length() <= 262_144) { "Onboarding configuration is too large." }
    val bytes = file.readBytes()
    require(bytes.size <= 262_144) { "Onboarding configuration is too large." }
    require(!(bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte())) {
        "UTF-8 BOM is forbidden."
    }
    val text =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    require('\r' !in text) { "CRLF is forbidden." }
    val values = linkedMapOf<String, String>()
    val seen = TreeSet(String.CASE_INSENSITIVE_ORDER)
    text.split('\n').forEachIndexed { index, line ->
        if (index == text.count { it == '\n' } && line.isEmpty()) return@forEachIndexed
        require(line.isNotEmpty() && !line.startsWith('#')) { "Invalid onboarding property line." }
        require('\\' !in line) { "Property escapes and continuations are forbidden." }
        val separator = line.indexOf('=')
        require(separator > 0) { "Invalid onboarding property line." }
        val key = line.substring(0, separator)
        val value = line.substring(separator + 1)
        require(key == key.trim() && value == value.trim()) { "Property whitespace is forbidden." }
        require(key in allowedKeys) { "Unknown onboarding property." }
        require(seen.add(key)) { "Duplicate onboarding property." }
        require(value.length <= 4_096 && isSafeOnboardingText(value)) {
            "Unsafe onboarding property value."
        }
        values[key] = value
    }
    return values
}

private val unsafeOnboardingCodePointRanges =
    listOf(
        0x00..0x1f,
        0x7f..0x9f,
        0x202a..0x202e,
        0x2066..0x2069,
        0xd800..0xdfff,
        0xfdd0..0xfdef
    )

private fun isSafeOnboardingText(value: String): Boolean = value.codePoints().allMatch { codePoint ->
    unsafeOnboardingCodePointRanges.none { codePoint in it } &&
        (codePoint and 0xffff) !in 0xfffe..0xffff
}

internal fun loadOwnedOnboardingConfiguration(): OwnedOnboardingConfiguration {
    val application = requireNotNull(System.getProperty("onboarding.application")) {
        "An explicit enrolled application is required."
    }
    val profile = requireNotNull(System.getProperty("onboarding.profile")) {
        "An explicit enrolled profile is required."
    }
    val root = File(requireNotNull(System.getProperty("onboarding.repoRoot"))).canonicalFile
    fun resolve(property: String): File {
        val relative = requireNotNull(System.getProperty(property)) { "$property is required." }
        require(relative.isNotBlank() && '\\' !in relative) { "$property must be a repository-relative path." }
        val resolved = File(root, relative).canonicalFile
        require(resolved.toPath().startsWith(root.toPath())) { "$property escapes the repository root." }
        return resolved
    }
    val projection = loadStrictProperties(resolve("onboarding.projectionPath"), projectionKeys)
    val local = loadStrictProperties(resolve("onboarding.localConfigurationPath"), localConfigurationKeys)
    require(projection["onboarding.schemaVersion"] == "1")
    require(
        projection["onboarding.sourceRegistrySha256"] ==
            requireNotNull(System.getProperty("onboarding.registrySha256"))
    )
    require(projection["onboarding.application"] == application)
    require(projection["onboarding.profile"] == profile)
    return OwnedOnboardingConfiguration(
        storefront = StorefrontConfiguration(
            domain = projection.getValue("shopify.storefrontDomain"),
            apiVersion = projection.getValue("shopify.storefrontApiVersion"),
            publicToken = ControlledPublicToken.from(local["shopify.storefrontPublicToken"].orEmpty())
        ),
        menuHandle = projection.getValue("shopify.catalogMenuHandle"),
        homeType = projection.getValue("shopify.homeRootType"),
        homeHandle = projection.getValue("shopify.homeRootHandle")
    )
}
