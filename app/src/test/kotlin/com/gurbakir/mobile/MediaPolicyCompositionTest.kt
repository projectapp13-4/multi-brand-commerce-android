package com.gurbakir.mobile

import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.storefront.StorefrontMediaPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaPolicyCompositionTest {
    @Test
    fun `Hilt gateway and application image loader derive equivalent policy from the Storefront domain`() {
        val configuration =
            BuildConfigurationSource.current.copy(
                storefront = BuildConfigurationSource.current.storefront.copy(domain = "gurbakir.com")
            )
        val hiltGatewayPolicy = StorefrontMediaPolicy(configuration.storefront.domain)
        val applicationLoaderPolicy = mediaPolicyFor(configuration)
        val accepted =
            listOf(
                "https://gurbakir.com/cdn/shop/files/image.jpg",
                "https://cdn.shopify.com/s/files/1/image.jpg"
            )
        val rejected =
            listOf(
                "https://other.invalid/cdn/shop/files/image.jpg",
                "https://gurbakir.com/products/image.jpg"
            )

        accepted.forEach { url ->
            assertTrue(hiltGatewayPolicy.accepts(url))
            assertEquals(hiltGatewayPolicy.accepts(url), applicationLoaderPolicy.accepts(url))
        }
        rejected.forEach { url ->
            assertFalse(hiltGatewayPolicy.accepts(url))
            assertEquals(hiltGatewayPolicy.accepts(url), applicationLoaderPolicy.accepts(url))
        }
    }
}
