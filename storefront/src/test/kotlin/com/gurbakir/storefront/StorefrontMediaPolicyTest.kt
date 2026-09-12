package com.gurbakir.storefront

import java.net.URI
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorefrontMediaPolicyTest {
    private val policy = StorefrontMediaPolicy("gurbakir.com")

    @Test
    fun `accepts only the exact merchant path boundary and exact provider host`() {
        listOf(
            "https://gurbakir.com/cdn/shop/",
            "https://gurbakir.com/cdn/shop/files/product.jpg?v=1&width=640",
            "https://gurbakir.com:443/cdn/shop/products/product.png",
            "https://cdn.shopify.com/",
            "https://cdn.shopify.com/s/files/1/product.jpg?width=640"
        ).forEach { rawUrl -> assertTrue(policy.accepts(rawUrl), rawUrl) }

        listOf(
            "https://gurbakir.com/cdn/shop",
            "https://gurbakir.com/cdn/shopper/file.jpg",
            "https://gurbakir.com/products/file.jpg",
            "https://gurbakir.com.evil.test/cdn/shop/file.jpg",
            "https://evil-gurbakir.com/cdn/shop/file.jpg",
            "https://cdn.shopify.com.evil.test/file.jpg",
            "https://shopify.com/file.jpg"
        ).forEach { rawUrl -> assertFalse(policy.accepts(rawUrl), rawUrl) }
    }

    @Test
    fun `rejects unsafe URL structure and canonicalization ambiguity`() {
        listOf(
            "http://gurbakir.com/cdn/shop/file.jpg",
            "HTTPS://gurbakir.com/cdn/shop/file.jpg",
            "https://GURBAKIR.com/cdn/shop/file.jpg",
            "https://gurbakir.com.:443/cdn/shop/file.jpg",
            "https://user@gurbakir.com/cdn/shop/file.jpg",
            "https://gurbakir.com:444/cdn/shop/file.jpg",
            "https://127.0.0.1/cdn/shop/file.jpg",
            "https://[::1]/cdn/shop/file.jpg",
            "https://*.gurbakir.com/cdn/shop/file.jpg",
            "https://gurbakir.com/cdn/shop/file.jpg#fragment",
            "https://gurbakir.com/cdn/shop/../private/file.jpg",
            "https://gurbakir.com/cdn/shop/%2e%2e/private/file.jpg",
            "https://gurbakir.com/cdn/shop/%252e%252e/private/file.jpg",
            "https://gurbakir.com/cdn/shop/files%2fprivate.jpg",
            "https://gurbakir.com/cdn/shop/files%252fprivate.jpg",
            "https://gurbakir.com/cdn/shop/files%5cprivate.jpg",
            "https://gurbakir.com/cdn/shop//files/private.jpg",
            "https://gurbakir.com\\evil.test/cdn/shop/file.jpg",
            "not a URL"
        ).forEach { rawUrl -> assertFalse(policy.accepts(rawUrl), rawUrl) }
    }

    @Test
    fun `URI overload retains exact query while applying the same decision`() {
        val uri = URI("https://gurbakir.com/cdn/shop/files/product.jpg?width=640%2F2")

        assertTrue(policy.accepts(uri))
        assertTrue(uri.rawQuery.contains("%2F"))
    }

    @Test
    fun `missing or invalid merchant domain creates a deny-all policy`() {
        listOf("", "GURBAKIR.com", "gurbakir.com.", "https://gurbakir.com", "127.0.0.1")
            .map(::StorefrontMediaPolicy)
            .forEach { denyAll ->
                assertFalse(denyAll.accepts("https://gurbakir.com/cdn/shop/file.jpg"))
                assertFalse(denyAll.accepts("https://cdn.shopify.com/s/files/1/file.jpg"))
            }
    }
}
