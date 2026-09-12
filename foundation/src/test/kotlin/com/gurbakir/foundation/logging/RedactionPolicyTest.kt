package com.gurbakir.foundation.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedactionPolicyTest {
    private val policy = RedactionPolicy()

    @Test
    fun `redacts authorization tokens and query credentials`() {
        val input = "Bearer abc.def.ghi access_token=sensitive&state=nonce user@example.test"
        val output = policy.redact(input)

        assertFalse(output.contains("abc.def.ghi"))
        assertFalse(output.contains("sensitive"))
        assertFalse(output.contains("nonce"))
        assertFalse(output.contains("user@example.test"))
        assertTrue(output.contains("<redacted>"))
    }
}
