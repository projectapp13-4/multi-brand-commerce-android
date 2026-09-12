package com.gurbakir.account.session

import com.gurbakir.account.oauth.CustomerAccountAuthorizationGrant
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.CustomerLogoutResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.oauth.CustomerTokenPayload
import com.gurbakir.account.oauth.CustomerTokenResult
import com.gurbakir.account.oauth.SensitiveAuthorizationCode
import com.gurbakir.account.oauth.SensitiveCodeVerifier
import com.gurbakir.account.oauth.SensitiveNonce
import com.gurbakir.account.oauth.testDiscovery
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomerAccountSessionCoordinatorTest {
    @Test
    fun `disabled account never constructs or touches secure storage token or logout clients`() = runTest {
        val coordinator = CustomerAccountSessionCoordinator(
            capability = CustomerAccountCapability.Disabled,
            tokenClient = { error("disabled token client was requested") },
            sessionStore = { error("disabled secure store was requested") },
            logoutClient = { error("disabled logout client was requested") }
        )
        assertEquals(CustomerSessionResolution.SignedOut, coordinator.restore())
        assertEquals(CustomerSessionResolution.SignedOut, coordinator.refresh())
        assertEquals(CustomerSessionResolution.SignedOut, coordinator.exchange(grant()))
        assertEquals(CustomerLogoutResolution.SignedOut, coordinator.logout())
        coordinator.clearForLogout()
    }

    @Test
    fun `enabled capability consumes supplied secure session and refresh client`() = runTest {
        val store = FakeSessionStore(session("old", now.minusSeconds(1)))
        val client = FakeTokenClient(refreshResult = CustomerTokenResult.Failure(CustomerTokenFailure.Transient))
        var storeRequests = 0
        var tokenRequests = 0
        val coordinator = CustomerAccountSessionCoordinator(
            capability = CustomerAccountCapability.Enabled(configuration()),
            tokenClient = {
                tokenRequests++
                client
            },
            sessionStore = {
                storeRequests++
                store
            },
            clock = clock
        )
        assertEquals(CustomerSessionResolution.Failed(CustomerTokenFailure.Transient, true), coordinator.restore())
        assertEquals(1, storeRequests)
        assertEquals(1, tokenRequests)
        assertEquals(1, client.refreshCalls)
    }

    private val now = Instant.parse("2026-07-19T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `authorization exchange validates nonce and persists a complete encrypted-session payload`() = runTest {
        val store = FakeSessionStore()
        val client = FakeTokenClient(exchangeResult = successPayload("access-new", now.plusSeconds(3600)))
        val coordinator = coordinator(client, store)

        val result = coordinator.exchange(grant())

        assertTrue(result is CustomerSessionResolution.Authenticated)
        assertEquals("access-new", store.session?.accessToken?.use { it })
        assertEquals("refresh-new", store.session?.refreshToken?.use { it })
        assertTrue(store.session?.idToken != null)
        assertFalse(result.toString().contains("access-new"))
    }

    @Test
    fun `invalid nonce fails closed without persisting tokens`() = runTest {
        val store = FakeSessionStore()
        val client = FakeTokenClient(
            exchangeResult = successPayload("access-new", now.plusSeconds(3600), nonce = "wrong-nonce")
        )

        val result = coordinator(client, store).exchange(grant())

        assertEquals(CustomerSessionResolution.Failed(CustomerTokenFailure.InvalidIdToken, false), result)
        assertNull(store.session)
    }

    @Test
    fun `failed authorization exchange clears any superseded encrypted session`() = runTest {
        val store = FakeSessionStore(session("access-old", now.plusSeconds(3600)))
        val client = FakeTokenClient(
            exchangeResult = CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
        )

        val result = coordinator(client, store).exchange(grant())

        assertEquals(CustomerSessionResolution.Failed(CustomerTokenFailure.Rejected, false), result)
        assertNull(store.session)
    }

    @Test
    fun `restore refreshes near-expiry session and retains rotated fields omitted by server`() = runTest {
        val previous = session("access-old", now.minusSeconds(1))
        val store = FakeSessionStore(previous)
        val client = FakeTokenClient(
            refreshResult =
                CustomerTokenResult.Success(
                    CustomerTokenPayload(
                        accessToken = SensitiveToken.from("access-refreshed"),
                        refreshToken = null,
                        idToken = null,
                        expiresAt = now.plusSeconds(3600)
                    )
                )
        )

        val result = coordinator(client, store).restore()

        assertTrue(result is CustomerSessionResolution.Authenticated)
        assertEquals("access-refreshed", store.session?.accessToken?.use { it })
        assertEquals("refresh-old", store.session?.refreshToken?.use { it })
        assertEquals(1, client.refreshCalls)
    }

    @Test
    fun `invalid refresh rejects the session and clears encrypted persistence`() = runTest {
        val store = FakeSessionStore(session("access-old", now.minusSeconds(1)))
        val client = FakeTokenClient(refreshResult = CustomerTokenResult.Failure(CustomerTokenFailure.Rejected))

        val result = coordinator(client, store).restore()

        assertEquals(CustomerSessionResolution.Failed(CustomerTokenFailure.Rejected, false), result)
        assertNull(store.session)
    }

    @Test
    fun `malformed refresh response clears encrypted persistence`() = runTest {
        val store = FakeSessionStore(session("access-old", now.minusSeconds(1)))
        val malformedPayload =
            CustomerTokenPayload(
                accessToken = SensitiveToken.from("access-new"),
                refreshToken = null,
                idToken = null,
                expiresAt = now.minusSeconds(1)
            )
        val client = FakeTokenClient(refreshResult = CustomerTokenResult.Success(malformedPayload))

        val result = coordinator(client, store).restore()

        assertEquals(
            CustomerSessionResolution.Failed(CustomerTokenFailure.InvalidResponse, false),
            result
        )
        assertNull(store.session)
    }

    @Test
    fun `transient refresh failure retains encrypted session for safe retry`() = runTest {
        val stored = session("access-old", now.minusSeconds(1))
        val store = FakeSessionStore(stored)
        val client = FakeTokenClient(refreshResult = CustomerTokenResult.Failure(CustomerTokenFailure.Transient))

        val result = coordinator(client, store).restore()

        assertEquals(CustomerSessionResolution.Failed(CustomerTokenFailure.Transient, true), result)
        assertEquals(stored, store.session)
    }

    @Test
    fun `expired session without refresh token fails closed and logout clears local state`() = runTest {
        val noRefresh = session("access-old", now.minusSeconds(1)).copy(refreshToken = null)
        val store = FakeSessionStore(noRefresh)
        val coordinator = coordinator(FakeTokenClient(), store)

        assertEquals(
            CustomerSessionResolution.Failed(CustomerTokenFailure.Rejected, false),
            coordinator.restore()
        )
        assertNull(store.session)

        store.session = session("access-later", now.plusSeconds(3600))
        coordinator.clearForLogout()
        assertNull(store.session)
    }

    @Test
    fun `logout ends the remote mobile session before clearing encrypted local state`() = runTest {
        val store = FakeSessionStore(session("access-old", now.plusSeconds(3600)))
        val logoutClient = FakeLogoutClient(CustomerLogoutResult.Success)
        val coordinator = coordinator(FakeTokenClient(), store, logoutClient)

        val result = coordinator.logout()

        assertEquals(CustomerLogoutResolution.Completed, result)
        assertEquals(1, logoutClient.calls)
        assertNull(store.session)
    }

    @Test
    fun `remote logout failure still clears local credentials and reports incomplete end-session`() = runTest {
        val store = FakeSessionStore(session("access-old", now.plusSeconds(3600)))
        val logoutClient =
            FakeLogoutClient(CustomerLogoutResult.Failure(CustomerTokenFailure.Transient))
        val coordinator = coordinator(FakeTokenClient(), store, logoutClient)

        val result = coordinator.logout()

        assertEquals(CustomerLogoutResolution.RemoteFailed(CustomerTokenFailure.Transient), result)
        assertNull(store.session)
    }

    private fun coordinator(
        client: CustomerAccountTokenClient,
        store: CustomerSessionStore,
        logoutClient: CustomerAccountLogoutClient = FakeLogoutClient(CustomerLogoutResult.Success)
    ) = CustomerAccountSessionCoordinator(
        configuration = configuration(),
        tokenClient = client,
        sessionStore = store,
        logoutClient = logoutClient,
        clock = clock
    )

    private fun grant() = CustomerAccountAuthorizationGrant(
        code = SensitiveAuthorizationCode.from("synthetic-code"),
        codeVerifier = SensitiveCodeVerifier.from("synthetic-verifier"),
        expectedNonce = SensitiveNonce.from("expected-nonce"),
        redirectUri = "shop.123456.gurbakir://oauth/callback",
        discovery = testDiscovery()
    )

    private fun successPayload(accessToken: String, expiresAt: Instant, nonce: String = "expected-nonce") =
        CustomerTokenResult.Success(
            CustomerTokenPayload(
                accessToken = SensitiveToken.from(accessToken),
                refreshToken = SensitiveToken.from("refresh-new"),
                idToken = SensitiveToken.from(idToken(nonce, expiresAt.plusSeconds(60))),
                expiresAt = expiresAt
            )
        )

    private fun session(accessToken: String, expiresAt: Instant) = CustomerSession(
        accessToken = SensitiveToken.from(accessToken),
        refreshToken = SensitiveToken.from("refresh-old"),
        idToken = SensitiveToken.from(idToken("expected-nonce", now.plusSeconds(7200))),
        expiresAt = expiresAt
    )

    private fun idToken(nonce: String, expiresAt: Instant): String {
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64Url(
            """
            {
              "iss":"https://shopify.com/authentication/123456",
              "sub":"synthetic-customer",
              "aud":"public-client-id",
              "exp":${expiresAt.epochSecond},
              "iat":${now.epochSecond},
              "nonce":"$nonce"
            }
            """.trimIndent()
        )
        return "$header.$payload.synthetic-signature"
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun configuration() = CustomerAccountConfiguration(
        clientId = "public-client-id",
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.gurbakir://oauth/callback",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private class FakeSessionStore(var session: CustomerSession? = null) : CustomerSessionStore {
        override suspend fun read(): CustomerSession? = session
        override suspend fun write(session: CustomerSession) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeTokenClient(
        private val exchangeResult: CustomerTokenResult = CustomerTokenResult.Failure(CustomerTokenFailure.Rejected),
        private val refreshResult: CustomerTokenResult = CustomerTokenResult.Failure(CustomerTokenFailure.Rejected)
    ) : CustomerAccountTokenClient {
        var refreshCalls = 0
        override suspend fun exchange(grant: CustomerAccountAuthorizationGrant): CustomerTokenResult = exchangeResult
        override suspend fun refresh(refreshToken: SensitiveToken): CustomerTokenResult {
            refreshCalls += 1
            return refreshResult
        }
    }

    private class FakeLogoutClient(private val result: CustomerLogoutResult) : CustomerAccountLogoutClient {
        var calls = 0

        override suspend fun logout(idToken: SensitiveToken): CustomerLogoutResult {
            calls += 1
            return result
        }
    }
}
