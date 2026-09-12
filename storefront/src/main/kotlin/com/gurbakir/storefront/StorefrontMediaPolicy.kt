package com.gurbakir.storefront

import java.net.URI
import java.util.Locale

private const val HTTPS_DEFAULT_PORT = 443
private const val SHOPIFY_MEDIA_HOST = "cdn.shopify.com"
private const val MERCHANT_MEDIA_PATH_PREFIX = "/cdn/shop/"
private const val FIRST_PRINTABLE_ASCII = 0x21
private const val LAST_PRINTABLE_ASCII = 0x7e
private val DNS_HOST_PATTERN = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")
private val ENCODED_PATH_AMBIGUITY = Regex("%2e|%2f|%5c|%25", RegexOption.IGNORE_CASE)

class StorefrontMediaPolicy(merchantDomain: String) {
    private val merchantHost: String? = merchantDomain.validatedDnsHost()

    fun accepts(rawUrl: String): Boolean = runCatching { URI(rawUrl) }
        .getOrNull()
        ?.takeIf { it.toString() == rawUrl }
        ?.let(::accepts) == true

    fun accepts(uri: URI): Boolean {
        val configuredMerchantHost = merchantHost ?: return false
        return uri.hasSafeStructure() && uri.hasAllowedOrigin(configuredMerchantHost)
    }
}

private fun String.validatedDnsHost(): String? {
    val parsed = runCatching { URI("https://$this/") }.getOrNull()
    return takeIf { isSafeDnsHostText() && parsed?.host == this && parsed.rawAuthority == this }
}

private fun String.isSafeDnsHostText(): Boolean = isNotBlank() &&
    this == lowercase(Locale.ROOT) &&
    all { character -> character.code in FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII } &&
    DNS_HOST_PATTERN.matches(this) &&
    !endsWith('.') &&
    '*' !in this &&
    ':' !in this &&
    hasSafeDnsLabels()

private fun String.hasSafeDnsLabels(): Boolean {
    val labels = split('.')
    return labels.size >= 2 &&
        labels.none { label -> label.isEmpty() || label.startsWith('-') || label.endsWith('-') } &&
        labels.any { label -> !label.all(Char::isDigit) }
}

private fun URI.hasSafeStructure(): Boolean = scheme == "https" &&
    hasSafeAuthority() &&
    rawUserInfo == null &&
    rawFragment == null &&
    (port == -1 || port == HTTPS_DEFAULT_PORT) &&
    toString().none(Char::isISOControl) &&
    '\\' !in toString() &&
    rawPath.hasSafePathStructure()

private fun URI.hasSafeAuthority(): Boolean {
    val exactHost = host
    val exactAuthority = rawAuthority
    return exactHost != null &&
        exactAuthority != null &&
        exactHost.validatedDnsHost() == exactHost &&
        (exactAuthority == exactHost || exactAuthority == "$exactHost:$HTTPS_DEFAULT_PORT")
}

private fun String?.hasSafePathStructure(): Boolean = this != null &&
    !ENCODED_PATH_AMBIGUITY.containsMatchIn(this) &&
    "//" !in this &&
    split('/').none { segment -> segment == "." || segment == ".." }

private fun URI.hasAllowedOrigin(configuredMerchantHost: String): Boolean = host == SHOPIFY_MEDIA_HOST ||
    (host == configuredMerchantHost && rawPath.startsWith(MERCHANT_MEDIA_PATH_PREFIX))
