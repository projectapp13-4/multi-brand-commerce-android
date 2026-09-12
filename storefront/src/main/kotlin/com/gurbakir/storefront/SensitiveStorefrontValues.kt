package com.gurbakir.storefront

import java.net.URI

private const val MAXIMUM_OPAQUE_VALUE_LENGTH = 64 * 1024

class SensitiveCartId private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted-cart-id>"

    override fun equals(other: Any?): Boolean = other is SensitiveCartId && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    companion object {
        internal fun from(rawValue: String): SensitiveCartId {
            require(rawValue.isNotBlank() && rawValue.length <= MAXIMUM_OPAQUE_VALUE_LENGTH)
            return SensitiveCartId(rawValue)
        }
    }
}

class SensitiveCartLineId private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted-cart-line-id>"

    override fun equals(other: Any?): Boolean = other is SensitiveCartLineId && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    companion object {
        internal fun from(rawValue: String): SensitiveCartLineId {
            require(rawValue.isNotBlank() && rawValue.length <= MAXIMUM_OPAQUE_VALUE_LENGTH)
            return SensitiveCartLineId(rawValue)
        }
    }
}

class SensitiveCheckoutUrl private constructor(private val rawValue: URI) {
    fun <T> use(block: (URI) -> T): T = block(rawValue)

    suspend fun <T> useSuspending(block: suspend (URI) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted-checkout-url>"

    override fun equals(other: Any?): Boolean = other is SensitiveCheckoutUrl && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    companion object {
        internal fun from(rawValue: URI): SensitiveCheckoutUrl = SensitiveCheckoutUrl(rawValue)
    }
}

class SensitiveBuyerAccessToken private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "<redacted-buyer-access-token>"

    companion object {
        fun from(rawValue: String): SensitiveBuyerAccessToken {
            require(rawValue.isNotBlank() && rawValue.length <= MAXIMUM_OPAQUE_VALUE_LENGTH)
            return SensitiveBuyerAccessToken(rawValue)
        }
    }
}
