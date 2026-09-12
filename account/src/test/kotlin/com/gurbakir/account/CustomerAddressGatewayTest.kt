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

class CustomerAddressGatewayTest {
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
    fun `address query maps default Turkey address and redacts PII`() = runBlocking {
        enqueue(
            """{
              "data":{"customer":{
                "defaultAddress":{"id":"gid://shopify/CustomerAddress/1"},
                "addresses":{"nodes":[{
                  "id":"gid://shopify/CustomerAddress/1","firstName":"Synthetic",
                  "lastName":"Customer","company":null,"address1":"Private street",
                  "address2":null,"city":"Istanbul","zip":"34000","phoneNumber":null,
                  "territoryCode":"TR","zoneCode":null,
                  "formatted":["Synthetic Customer","Private street","Istanbul 34000","Turkey"]
                }],"pageInfo":{"hasNextPage":false,"endCursor":null}}
              }}
            }"""
        )

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadAddresses()

        val page = (result as CustomerAccountResult.Success).value
        assertEquals(1, page.addresses.size)
        assertTrue(page.addresses.single().isDefault)
        assertEquals("TR", page.addresses.single().territoryCode)
        assertTrue(!result.toString().contains("Private street"))
        assertEquals("synthetic-access-token", request()?.getHeader("Authorization"))
    }

    @Test
    fun `address query rejects a pagination page without its required cursor`() = runBlocking {
        enqueue(
            """{
              "data":{"customer":{"defaultAddress":null,"addresses":{
                "nodes":[],"pageInfo":{"hasNextPage":true,"endCursor":null}
              }}}
            }"""
        )

        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf("INVALID_ADDRESS_PAGE"))),
            gateway(CustomerSessionResolution.Authenticated(session())).loadAddresses()
        )
    }

    @Test
    fun `address create sends fixed Turkey input and returns server id`() = runBlocking {
        enqueue(
            """{
              "data":{"customerAddressCreate":{
                "customerAddress":{"id":"gid://shopify/CustomerAddress/2"},"userErrors":[]
              }}
            }"""
        )
        val result = gateway(CustomerSessionResolution.Authenticated(session())).createAddress(draft(), true)

        assertEquals(
            CustomerAddressMutationResult.Success("gid://shopify/CustomerAddress/2"),
            result
        )
        val recorded = request()
        val body = recorded?.body?.readUtf8().orEmpty()
        assertTrue(body.contains("CustomerAddressCreate"))
        assertTrue(body.contains("\"territoryCode\":\"TR\""))
        assertTrue(body.contains("\"defaultAddress\":true"))
        assertEquals("synthetic-access-token", recorded?.getHeader("Authorization"))
    }

    @Test
    fun `address field errors map from code without exposing remote message`() = runBlocking {
        enqueue(
            """{
              "data":{"customerAddressUpdate":{"customerAddress":null,"userErrors":[{
                "code":"PHONE_NUMBER_NOT_VALID","field":null,"message":"private remote detail"
              }]}}
            }"""
        )

        val result = gateway(CustomerSessionResolution.Authenticated(session())).updateAddress(
            "gid://shopify/CustomerAddress/1",
            draft()
        )

        assertEquals(
            CustomerAddressMutationResult.Rejected(
                fields = setOf(CustomerAddressField.PHONE),
                issues = setOf(CustomerAddressIssue.UNKNOWN)
            ),
            result
        )
        assertTrue(!result.toString().contains("private remote detail"))
    }

    @Test
    fun `default delete rejection remains a structured non PII issue`() = runBlocking {
        enqueue(
            """{
              "data":{"customerAddressDelete":{"deletedAddressId":null,"userErrors":[{
                "code":"DELETING_CUSTOMER_DEFAULT_ADDRESS_NOT_ALLOWED","field":["addressId"],
                "message":"private remote detail"
              }]}}
            }"""
        )

        assertEquals(
            CustomerAddressMutationResult.Rejected(
                fields = setOf(CustomerAddressField.FORM),
                issues = setOf(CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED)
            ),
            gateway(CustomerSessionResolution.Authenticated(session())).deleteAddress(
                "gid://shopify/CustomerAddress/1"
            )
        )
    }

    @Test
    fun `signed out address query fails closed without network`() = runBlocking {
        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut),
            gateway(CustomerSessionResolution.SignedOut).loadAddresses()
        )
        assertEquals(0, server.requestCount)
    }

    private fun gateway(resolution: CustomerSessionResolution): CustomerAddressGateway = ApolloCustomerAddressGateway(
        ApolloClient.Builder()
            .serverUrl(server.url("/customer/api/2026-07/graphql").toString())
            .build(),
        CustomerSessionResolver { resolution }
    )

    private fun enqueue(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    private fun request() = server.takeRequest(2, TimeUnit.SECONDS)

    private fun draft() = CustomerAddressDraft(
        firstName = "Synthetic",
        lastName = "Customer",
        company = null,
        address1 = "Private street",
        address2 = null,
        city = "Istanbul",
        zip = "34000",
        phoneNumber = "+905551112233",
        territoryCode = "TR"
    )

    private fun session() = CustomerSession(
        accessToken = SensitiveToken.from("synthetic-access-token"),
        refreshToken = SensitiveToken.from("synthetic-refresh-token"),
        idToken = SensitiveToken.from("synthetic-id-token"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z")
    )
}
