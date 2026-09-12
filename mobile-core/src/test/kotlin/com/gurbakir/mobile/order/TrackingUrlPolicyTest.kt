package com.gurbakir.mobile.order

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackingUrlPolicyTest {
    private val policy =
        TrackingUrlPolicy(setOf("carrier.example", "parcel.example", "shipping.example", "delivery.example"))

    @Test
    fun `explicit carrier HTTPS hosts and subdomains are allowed`() {
        assertTrue(policy.isAllowed("https://track.carrier.example/Track/Verify?q=AA123"))
        assertTrue(policy.isAllowed("https://www.parcel.example/tr/online-servisler/gonderi-sorgula?code=1"))
        assertTrue(policy.isAllowed("https://www.shipping.example/takip?code=1"))
        assertTrue(policy.isAllowed("https://www.delivery.example/KargoTakip/"))
    }

    @Test
    fun `lookalikes userinfo insecure schemes ports fragments and traversal fail closed`() {
        assertFalse(policy.isAllowed("https://shipping.example.evil.example/takip"))
        assertFalse(policy.isAllowed("https://shipping.example@evil.example/takip"))
        assertFalse(policy.isAllowed("http://www.shipping.example/takip"))
        assertFalse(policy.isAllowed("https://www.shipping.example:8443/takip"))
        assertFalse(policy.isAllowed("https://www.shipping.example/takip#private"))
        assertFalse(policy.isAllowed("https://www.shipping.example/a/../takip"))
    }

    @Test
    fun `unknown carriers and oversized links fall back to support`() {
        assertFalse(policy.isAllowed("https://tracking.example/TRACK-1"))
        assertFalse(policy.isAllowed("https://www.shipping.example/takip?code=${"x".repeat(2100)}"))
    }
}
