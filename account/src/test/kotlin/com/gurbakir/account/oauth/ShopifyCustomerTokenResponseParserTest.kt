package com.gurbakir.account.oauth

import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShopifyCustomerTokenResponseParserTest {
    private val now = Instant.parse("2026-08-06T12:00:00Z")
    private val parser =
        ShopifyCustomerTokenResponseParser(
            expectedScopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

    @Test
    fun `official response without token type is accepted`() {
        val result = parser.parse(successBody())

        assertTrue(result is CustomerTokenResult.Success)
        result as CustomerTokenResult.Success
        assertEquals(now.plusSeconds(3600), result.payload.expiresAt)
        assertEquals("synthetic-access", result.payload.accessToken.use { it })
        assertEquals("synthetic-refresh", result.payload.refreshToken?.use { it })
        assertEquals("synthetic-id-token", result.payload.idToken?.use { it })
        assertTrue(!result.toString().contains("synthetic-access"))
    }

    @Test
    fun `explicit bearer token type is accepted case insensitively`() {
        val result = parser.parse(successBody(extra = "\"token_type\":\"bearer\","))

        assertTrue(result is CustomerTokenResult.Success)
    }

    @Test
    fun `unsupported token type fails closed`() {
        val result = parser.parse(successBody(extra = "\"token_type\":\"mac\","))

        assertEquals(
            CustomerTokenResult.Failure(CustomerTokenFailure.UnsupportedTokenType),
            result
        )
    }

    @Test
    fun `returned scope must contain every configured scope`() {
        val result = parser.parse(successBody(extra = "\"scope\":\"openid\","))

        assertEquals(CustomerTokenResult.Failure(CustomerTokenFailure.ScopeMismatch), result)
    }

    @Test
    fun `missing required access token fails closed`() {
        val result = parser.parse("""{"expires_in":3600}""")

        assertEquals(
            CustomerTokenResult.Failure(CustomerTokenFailure.MissingRequiredFields),
            result
        )
    }

    @Test
    fun `invalid lifetime and malformed JSON fail closed`() {
        assertEquals(
            CustomerTokenResult.Failure(CustomerTokenFailure.MissingRequiredFields),
            parser.parse(successBody(expiresIn = 0))
        )
        assertEquals(
            CustomerTokenResult.Failure(CustomerTokenFailure.InvalidResponse),
            parser.parse("not-json")
        )
    }

    private fun successBody(expiresIn: Long = 3600, extra: String = ""): String =
        """
            {
              $extra
              "access_token": "synthetic-access",
              "expires_in": $expiresIn,
              "refresh_token": "synthetic-refresh",
              "id_token": "synthetic-id-token"
            }
        """.trimIndent()
}
