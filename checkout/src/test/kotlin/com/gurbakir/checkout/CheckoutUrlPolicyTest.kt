package com.gurbakir.checkout

import java.net.URI
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutUrlPolicyTest {
    private val policy = CheckoutUrlPolicy(setOf("gurbakir.com"))

    @Test
    fun `allows only HTTPS checkout targets on the configured owned host`() {
        assertTrue(policy.validate(URI("https://gurbakir.com/cart/c/synthetic")))
        assertTrue(policy.validate(URI("https://GURBAKIR.COM/cart/c/synthetic?fresh=1")))
        assertFalse(policy.validate(URI("http://gurbakir.com/cart/c/synthetic")))
        assertFalse(policy.validate(URI("https://attacker.example/cart/c/synthetic")))
        assertFalse(policy.validate(URI("https://user@gurbakir.com/cart/c/synthetic")))
        assertFalse(policy.validate(URI("https://gurbakir.com:8443/cart/c/synthetic")))
        assertFalse(policy.validate(URI("https://gurbakir.com/cart/c/synthetic#fragment")))
    }
}
