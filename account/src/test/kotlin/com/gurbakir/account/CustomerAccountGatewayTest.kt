package com.gurbakir.account

import com.apollographql.apollo.ApolloClient
import com.gurbakir.account.session.CustomerSession
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.account.session.SensitiveToken
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CustomerAccountGatewayTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authenticated identity query uses Shopify raw-token authorization and typed mapping`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"data":{"customer":{"id":"gid://shopify/Customer/1","displayName":"Synthetic Customer"}}}"""
            )
        )
        val gateway = gateway(CustomerSessionResolution.Authenticated(session()))

        val result = gateway.loadIdentity()

        assertEquals(
            CustomerAccountResult.Success(CustomerIdentity("gid://shopify/Customer/1", "Synthetic Customer")),
            result
        )
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("synthetic-access-token", request?.getHeader("Authorization"))
    }

    @Test
    fun `signed out state fails closed without contacting the Customer Account endpoint`() = runBlocking {
        val gateway = gateway(CustomerSessionResolution.SignedOut)

        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut),
            gateway.loadIdentity()
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `GraphQL errors are classified without returning raw messages`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"errors":[{"message":"private remote detail","extensions":{"code":"UNAUTHENTICATED"}}]}"""
            )
        )

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadIdentity()

        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf("UNAUTHENTICATED"))),
            result
        )
        assertTrue(result.toString().contains("UNAUTHENTICATED"))
        assertTrue(!result.toString().contains("private remote detail"))
    }

    @Test
    fun `HTTP authentication rejection is terminal rather than a generic transport failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("private response body"))

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadIdentity()

        assertEquals(
            CustomerAccountResult.Failure(
                CustomerAccountFailure.Authentication(
                    com.gurbakir.account.oauth.CustomerTokenFailure.Rejected
                )
            ),
            result
        )
        assertTrue(!result.toString().contains("private response body"))
    }

    @Test
    fun `profile query maps only approved name fields and redacts its string form`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"data":{"customer":{"firstName":"Synthetic","lastName":"Customer"}}}"""
            )
        )
        val gateway = profileGateway(CustomerSessionResolution.Authenticated(session()))

        val result = gateway.loadProfile()

        assertEquals(
            CustomerAccountResult.Success(CustomerProfile("Synthetic", "Customer")),
            result
        )
        assertTrue(!result.toString().contains("Synthetic"))
        assertEquals("synthetic-access-token", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Authorization"))
    }

    @Test
    fun `profile update returns only server confirmed values`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"data":{"customerUpdate":{"customer":{"firstName":"Updated","lastName":"Name"},"userErrors":[]}}}"""
            )
        )
        val gateway = profileGateway(CustomerSessionResolution.Authenticated(session()))

        val result = gateway.updateProfile(CustomerProfileUpdate("Updated", "Name"))

        assertEquals(
            CustomerProfileUpdateResult.Success(CustomerProfile("Updated", "Name")),
            result
        )
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("synthetic-access-token", request?.getHeader("Authorization"))
        assertTrue(request?.body?.readUtf8().orEmpty().contains("CustomerProfileUpdate"))
    }

    @Test
    fun `profile update maps user error field without returning remote message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{
                    "data":{"customerUpdate":{"customer":null,"userErrors":[
                        {"field":["input","firstName"],"message":"private remote detail"}
                    ]}}
                }"""
            )
        )
        val gateway = profileGateway(CustomerSessionResolution.Authenticated(session()))

        val result = gateway.updateProfile(CustomerProfileUpdate("Invalid", "Name"))

        assertEquals(
            CustomerProfileUpdateResult.Rejected(setOf(CustomerProfileField.FIRST_NAME)),
            result
        )
        assertTrue(!result.toString().contains("private remote detail"))
    }

    private fun gateway(resolution: CustomerSessionResolution): CustomerAccountGateway = ApolloCustomerAccountGateway(
        ApolloClient.Builder().serverUrl(server.url("/customer/api/2026-07/graphql").toString()).build(),
        CustomerSessionResolver { resolution }
    )

    private fun profileGateway(resolution: CustomerSessionResolution): CustomerProfileGateway =
        ApolloCustomerProfileGateway(
            ApolloClient.Builder()
                .serverUrl(server.url("/customer/api/2026-07/graphql").toString())
                .build(),
            CustomerSessionResolver { resolution }
        )

    private fun session() = CustomerSession(
        accessToken = SensitiveToken.from("synthetic-access-token"),
        refreshToken = SensitiveToken.from("synthetic-refresh-token"),
        idToken = SensitiveToken.from("synthetic-id-token"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z")
    )
}
