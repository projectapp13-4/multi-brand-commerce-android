package com.gurbakir.account.session

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SessionPayloadCodecTest {
    @Test
    fun `round trips the encrypted payload boundary without exposing token strings`() {
        val original = CustomerSession(
            accessToken = SensitiveToken.from("access-value"),
            refreshToken = SensitiveToken.from("refresh-value"),
            idToken = SensitiveToken.from("id-value"),
            expiresAt = Instant.parse("2026-07-19T12:00:00Z")
        )

        val encoded = SessionPayloadCodec.encode(original)
        val decoded = SessionPayloadCodec.decode(encoded)

        assertEquals("access-value", decoded.accessToken.use { it })
        assertEquals("refresh-value", decoded.refreshToken?.use { it })
        assertEquals("id-value", decoded.idToken?.use { it })
        assertEquals(original.expiresAt, decoded.expiresAt)
        assertFalse(decoded.toString().contains("access-value"))
        assertFalse(decoded.toString().contains("refresh-value"))
        assertFalse(decoded.toString().contains("id-value"))
    }

    @Test
    fun `customer session v2 plaintext wire vector remains byte exact`() {
        val encoded =
            SessionPayloadCodec.encode(
                CustomerSession(
                    accessToken = SensitiveToken.from("access"),
                    refreshToken = SensitiveToken.from("refresh"),
                    idToken = null,
                    expiresAt = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L)
                )
            )

        assertEquals(
            "000000020000000661636365737301000000077265667265736800000000006553f100075bcd15",
            encoded.toHexString()
        )
    }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
