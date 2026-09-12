package com.gurbakir.foundation.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalRoutePolicyTest {
    private val policy =
        ExternalRoutePolicy(
            allowedHttpsHosts = setOf("app.example.test"),
            allowedCustomSchemes = setOf("shop.test.mobile")
        )

    @Test
    fun `accepts only allowlisted external routes`() {
        assertTrue(policy.isAllowed("https://app.example.test/checkout-return"))
        assertTrue(policy.isAllowed("shop.test.mobile://callback?code=redacted"))
        assertFalse(policy.isAllowed("https://attacker.example/checkout-return"))
        assertFalse(policy.isAllowed("javascript:alert(1)"))
        assertFalse(policy.isAllowed("https://user@app.example.test/checkout-return"))
    }
}
