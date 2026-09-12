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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CustomerOrderGatewayTest {
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
    fun `order page maps current statuses newest first and redacts private values`() = runBlocking {
        enqueue(
            """{
              "data":{"customer":{"orders":{"nodes":[{
                "id":"gid://shopify/Order/1001","name":"#1001",
                "processedAt":"2026-08-12T10:00:00Z","financialStatus":"PAID",
                "fulfillmentStatus":"PARTIALLY_FULFILLED",
                "totalPrice":{"amount":"1250.50","currencyCode":"TRY"}
              }],"pageInfo":{"hasNextPage":true,"endCursor":"private-cursor"}}}}
            }"""
        )

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadOrderPage()

        val page = (result as CustomerAccountResult.Success).value
        assertEquals("private-cursor", page.nextCursor)
        assertEquals(CustomerOrderFinancialStatus.PAID, page.orders.single().financialStatus)
        assertEquals(CustomerOrderFulfillmentStatus.PARTIALLY_FULFILLED, page.orders.single().fulfillmentStatus)
        assertEquals("1001", CustomerOrderIds.orderRouteFromGid(page.orders.single().id))
        assertTrue(!result.toString().contains("#1001"))
        assertTrue(!result.toString().contains("1250.50"))
        val recorded = request()
        assertEquals("synthetic-access-token", recorded?.getHeader("Authorization"))
        assertTrue(recorded?.body?.readUtf8().orEmpty().contains("PROCESSED_AT"))
    }

    @Test
    fun `order page rejects a continuation without a cursor`() = runBlocking {
        enqueue(
            """{"data":{"customer":{"orders":{"nodes":[],
              "pageInfo":{"hasNextPage":true,"endCursor":null}}}}}"""
        )

        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf("INVALID_ORDER_PAGE"))),
            gateway(CustomerSessionResolution.Authenticated(session())).loadOrderPage()
        )
    }

    @Test
    fun `order detail maps partial fulfillments tracking and verified address only`() = runBlocking {
        enqueue(detailResponse(fulfillmentHasNext = false))

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadOrder(
            "gid://shopify/Order/1001"
        )

        val detail = (result as CustomerAccountResult.Success).value!!
        assertEquals(2, detail.fulfillments.size)
        assertEquals(CustomerShipmentStatus.IN_TRANSIT, detail.fulfillments.first().shipmentStatus)
        assertEquals("Aras Kargo", detail.fulfillments.first().tracking.single().company)
        assertEquals(1, detail.fulfillments.first().lines.single().quantity)
        assertEquals(listOf("Synthetic Customer", "Private street", "Istanbul"), detail.shippingAddress)
        assertTrue(!result.toString().contains("Private street"))
        assertTrue(!result.toString().contains("TRACK-PRIVATE-1"))
    }

    @Test
    fun `order detail fails closed instead of presenting a truncated fulfillment`() = runBlocking {
        enqueue(detailResponse(fulfillmentHasNext = true))

        assertEquals(
            CustomerAccountResult.Failure(
                CustomerAccountFailure.GraphQl(setOf("INCOMPLETE_ORDER_DETAIL"))
            ),
            gateway(CustomerSessionResolution.Authenticated(session())).loadOrder(
                "gid://shopify/Order/1001"
            )
        )
    }

    @Test
    fun `missing or unowned order is indistinguishable as absent`() = runBlocking {
        enqueue("""{"data":{"order":null}}""")

        val result = gateway(CustomerSessionResolution.Authenticated(session())).loadOrder(
            "gid://shopify/Order/9999"
        )

        assertNull((result as CustomerAccountResult.Success).value)
    }

    @Test
    fun `signed out order access fails closed without network`() = runBlocking {
        val orderGateway = gateway(CustomerSessionResolution.SignedOut)

        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut),
            orderGateway.loadOrderPage()
        )
        assertEquals(
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut),
            orderGateway.loadOrder("gid://shopify/Order/1001")
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `order route conversion accepts only canonical numeric Shopify ids`() {
        assertEquals("gid://shopify/Order/1001", CustomerOrderIds.orderGidFromRoute("1001"))
        assertEquals("1001", CustomerOrderIds.orderRouteFromGid("gid://shopify/Order/1001"))
        assertNull(CustomerOrderIds.orderGidFromRoute("0"))
        assertNull(CustomerOrderIds.orderGidFromRoute("../1001"))
        assertNull(CustomerOrderIds.orderRouteFromGid("gid://shopify/DraftOrder/1001"))
    }

    private fun gateway(resolution: CustomerSessionResolution): CustomerOrderGateway = ApolloCustomerOrderGateway(
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

    private fun detailResponse(fulfillmentHasNext: Boolean): String = """{
          "data":{"order":{
            "id":"gid://shopify/Order/1001","name":"#1001",
            "processedAt":"2026-08-12T10:00:00Z","cancelledAt":null,
            "financialStatus":"PAID","fulfillmentStatus":"PARTIALLY_FULFILLED",
            "subtotal":{"amount":"1200.00","currencyCode":"TRY"},
            "totalPrice":{"amount":"1250.50","currencyCode":"TRY"},
            "totalRefunded":{"amount":"0.00","currencyCode":"TRY"},
            "totalShipping":{"amount":"50.50","currencyCode":"TRY"},
            "totalTax":{"amount":"200.00","currencyCode":"TRY"},
            "shippingAddress":{"formatted":["Synthetic Customer","Private street","Istanbul"]},
            "lineItems":{"nodes":[{
              "id":"gid://shopify/LineItem/1","name":"Synthetic copper item",
              "variantTitle":"Small","quantity":2,
              "totalPrice":{"amount":"1200.00","currencyCode":"TRY"}
            }],"pageInfo":{"hasNextPage":false}},
            "fulfillments":{"nodes":[{
              "id":"gid://shopify/Fulfillment/1","createdAt":"2026-08-12T11:00:00Z",
              "estimatedDeliveryAt":null,"latestShipmentStatus":"IN_TRANSIT","status":"SUCCESS",
              "trackingInformation":[{"company":"Aras Kargo","number":"TRACK-PRIVATE-1",
                "url":"https://www.araskargo.com.tr/takip?code=TRACK-PRIVATE-1"}],
              "fulfillmentLineItems":{"nodes":[{
                "id":"gid://shopify/FulfillmentLineItem/1","quantity":1,
                "lineItem":{"id":"gid://shopify/LineItem/1","name":"Synthetic copper item",
                  "variantTitle":"Small","quantity":2,
                  "totalPrice":{"amount":"1200.00","currencyCode":"TRY"}}
              }],"pageInfo":{"hasNextPage":$fulfillmentHasNext}}
            },{
              "id":"gid://shopify/Fulfillment/2","createdAt":"2026-08-12T12:00:00Z",
              "estimatedDeliveryAt":null,"latestShipmentStatus":"CONFIRMED","status":"PENDING",
              "trackingInformation":[],
              "fulfillmentLineItems":{"nodes":[{
                "id":"gid://shopify/FulfillmentLineItem/2","quantity":1,
                "lineItem":{"id":"gid://shopify/LineItem/1","name":"Synthetic copper item",
                  "variantTitle":"Small","quantity":2,
                  "totalPrice":{"amount":"1200.00","currencyCode":"TRY"}}
              }],"pageInfo":{"hasNextPage":false}}
            }],"pageInfo":{"hasNextPage":false}}
          }}
        }"""

    private fun session() = CustomerSession(
        accessToken = SensitiveToken.from("synthetic-access-token"),
        refreshToken = SensitiveToken.from("synthetic-refresh-token"),
        idToken = SensitiveToken.from("synthetic-id-token"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z")
    )
}
