package com.gurbakir.account.oauth

import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import java.net.URLEncoder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomerAccountAuthorizationPlannerTest {
    @Test
    fun `disabled authorization never requests discovery or accepts callbacks`() = runTest {
        val coordinator = CustomerAccountAuthorizationCoordinator(
            capability = CustomerAccountCapability.Disabled,
            discoveryClient = { error("disabled discovery client was requested") }
        )
        assertEquals(
            CustomerAccountAuthorizationPreparation.Failed(CustomerAccountDiscoveryFailure.UNCONFIGURED),
            coordinator.prepare()
        )
        assertEquals(
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            coordinator.validateAndConsumeCallback(
                "shop.123456.gurbakir://oauth/callback?code=synthetic&state=synthetic"
            )
        )
        coordinator.cancel()
    }

    @Test
    fun `enabled authorization invokes supplied discovery`() = runTest {
        var discoveries = 0
        val coordinator = CustomerAccountAuthorizationCoordinator(
            capability = CustomerAccountCapability.Enabled(configuration()),
            discoveryClient = {
                CustomerAccountDiscoveryClient {
                    discoveries++
                    CustomerAccountDiscoveryResult.Success(testDiscovery())
                }
            }
        )
        assertTrue(coordinator.prepare() is CustomerAccountAuthorizationPreparation.Prepared)
        assertEquals(1, discoveries)
    }

    @Test
    fun `authorization plan contains unique state nonce and S256 material without rendering them`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val first = planner.prepare(testDiscovery())
        val second = planner.prepare(testDiscovery())

        assertNotEquals(first.transaction.state, second.transaction.state)
        assertNotEquals(first.transaction.nonce, second.transaction.nonce)
        assertEquals(43, first.transaction.state.length)
        assertEquals(43, first.transaction.nonce.length)
        assertEquals(43, first.transaction.pkce.challenge.length)
        assertFalse(first.toString().contains(first.transaction.state))
        assertFalse(first.toString().contains(first.transaction.nonce))
    }

    @Test
    fun `exact callback accepts one authorization code and consumes state`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val plan = planner.prepare(testDiscovery())
        val callback =
            "${plan.configuration.redirectUri}?code=synthetic-code&state=${encode(plan.transaction.state)}"

        val first = planner.validateAndConsumeCallback(callback)
        val replay = planner.validateAndConsumeCallback(callback)

        assertTrue(first is CustomerAccountCallbackResult.Authorized)
        val grant = (first as CustomerAccountCallbackResult.Authorized).grant
        assertEquals("synthetic-code", grant.code.use { it })
        assertEquals(plan.transaction.pkce.verifier, grant.codeVerifier.use { it })
        assertEquals(plan.transaction.nonce, grant.expectedNonce.use { it })
        assertFalse(grant.toString().contains("synthetic-code"))
        assertEquals("<redacted>", grant.code.toString())
        assertEquals(CustomerAccountCallbackResult.RejectedMissingTransaction, replay)
    }

    @Test
    fun `wrong route cannot consume the active transaction`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val plan = planner.prepare(testDiscovery())
        val wrongRoute =
            "shop.123456.attacker://oauth/callback?code=synthetic-code&state=${encode(plan.transaction.state)}"
        val correctRoute =
            "${plan.configuration.redirectUri}?code=synthetic-code&state=${encode(plan.transaction.state)}"

        assertEquals(CustomerAccountCallbackResult.RejectedRoute, planner.validateAndConsumeCallback(wrongRoute))
        assertTrue(planner.validateAndConsumeCallback(correctRoute) is CustomerAccountCallbackResult.Authorized)
    }

    @Test
    fun `duplicate state and mismatched state are rejected`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val plan = planner.prepare(testDiscovery())
        val duplicate = "${plan.configuration.redirectUri}?code=x&state=a&state=b"

        assertEquals(CustomerAccountCallbackResult.RejectedMalformed, planner.validateAndConsumeCallback(duplicate))
        assertEquals(
            CustomerAccountCallbackResult.RejectedState,
            planner.validateAndConsumeCallback("${plan.configuration.redirectUri}?code=x&state=wrong")
        )
    }

    @Test
    fun `provider cancellation consumes the transaction without exposing an authorization grant`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val plan = planner.prepare(testDiscovery())
        val callback =
            "${plan.configuration.redirectUri}?error=access_denied&state=${encode(plan.transaction.state)}"

        assertEquals(CustomerAccountCallbackResult.Cancelled, planner.validateAndConsumeCallback(callback))
        assertEquals(
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            planner.validateAndConsumeCallback(callback)
        )
    }

    @Test
    fun `local browser cancellation clears the active transaction before a later redirect`() {
        val planner = CustomerAccountAuthorizationPlanner(configuration())
        val plan = planner.prepare(testDiscovery())
        val callback =
            "${plan.configuration.redirectUri}?code=synthetic-code&state=${encode(plan.transaction.state)}"

        planner.cancel()

        assertEquals(
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            planner.validateAndConsumeCallback(callback)
        )
    }

    @Test
    fun `unconfigured authorization coordinator fails closed without constructing a request`() = runTest {
        val unconfigured =
            CustomerAccountConfiguration(
                clientId = "",
                issuer = "",
                authorizationEndpoint = "",
                tokenEndpoint = "",
                logoutEndpoint = "",
                graphqlEndpoint = "",
                redirectUri = "",
                scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
            )
        val coordinator =
            CustomerAccountAuthorizationCoordinator(
                unconfigured,
                UnconfiguredCustomerAccountDiscoveryClient()
            )

        assertEquals(
            CustomerAccountAuthorizationPreparation.Failed(
                CustomerAccountDiscoveryFailure.UNCONFIGURED
            ),
            coordinator.prepare()
        )
        assertEquals(
            CustomerAccountCallbackResult.RejectedMissingTransaction,
            coordinator.validateAndConsumeCallback("shop.unconfigured.gurbakir://oauth/callback")
        )
    }

    private fun configuration(): CustomerAccountConfiguration = CustomerAccountConfiguration(
        clientId = "public-client-id",
        issuer = "https://shopify.com/authentication/123456",
        authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
        tokenEndpoint = "https://shop.example/authentication/oauth/token",
        logoutEndpoint = "https://shop.example/authentication/logout",
        graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
        redirectUri = "shop.123456.gurbakir://oauth/callback",
        scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
    )

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
