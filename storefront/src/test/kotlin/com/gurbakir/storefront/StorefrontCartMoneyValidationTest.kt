package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorefrontCartMoneyValidationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApolloClient
    private val mediaPolicy = StorefrontMediaPolicy("gurbakir.com")

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ApolloClient.Builder().serverUrl(server.url("graphql").toString()).build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `cart rejects a line currency different from its total`() = runBlocking {
        val result =
            createCart(MoneyFixture(lineUnit = MoneyValue(currency = "USD"), lineTotal = MoneyValue(currency = "USD")))

        assertEquals(graphQlFailure<CartReference>("CART_CURRENCY_MISMATCH"), result)
    }

    @Test
    fun `cart rejects a different currency on the second page`() = runBlocking {
        server.enqueue(MockResponse().setBody(cartReadFirstPageResponse()))
        server.enqueue(
            MockResponse().setBody(
                cartReadSecondPageResponse(
                    MoneyFixture(lineUnit = MoneyValue(currency = "USD"), lineTotal = MoneyValue(currency = "USD"))
                )
            )
        )

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadCart(SensitiveCartId.from(CART_ID))

        assertEquals(graphQlFailure<CartReference>("CART_CURRENCY_MISMATCH"), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cart rejects subtotal and total currency mismatch`() = runBlocking {
        val result = createCart(MoneyFixture(cartSubtotal = MoneyValue(currency = "USD")))

        assertEquals(graphQlFailure<CartReference>("CART_CURRENCY_MISMATCH"), result)
    }

    @Test
    fun `cart rejects line unit and total currency mismatch`() = runBlocking {
        val result = createCart(MoneyFixture(lineUnit = MoneyValue(currency = "USD")))

        assertEquals(graphQlFailure<CartReference>("UNSUPPORTED_CART_LINE"), result)
    }

    @Test
    fun `cart rejects negative total and subtotal`() = runBlocking {
        val fixtures = listOf(
            MoneyFixture(cartTotal = MoneyValue(amount = "-10.00")),
            MoneyFixture(cartSubtotal = MoneyValue(amount = "-10.00"))
        )
        for (fixture in fixtures) {
            assertEquals(graphQlFailure<CartReference>("INVALID_CART_MONEY"), createCart(fixture))
        }
    }

    @Test
    fun `cart rejects negative line unit and total`() = runBlocking {
        val fixtures = listOf(
            MoneyFixture(lineUnit = MoneyValue(amount = "-10.00")),
            MoneyFixture(lineTotal = MoneyValue(amount = "-10.00"))
        )
        for (fixture in fixtures) {
            assertEquals(graphQlFailure<CartReference>("UNSUPPORTED_CART_LINE"), createCart(fixture))
        }
    }

    @Test
    fun `cart accepts zero money without requiring a positive price`() = runBlocking {
        val result = createCart(
            MoneyFixture(
                cartSubtotal = MoneyValue(amount = "0.00"),
                cartTotal = MoneyValue(amount = "0.00"),
                lineUnit = MoneyValue(amount = "0.00"),
                lineTotal = MoneyValue(amount = "0.00")
            )
        )

        val cart = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CartReference
        assertEquals(BigDecimal("0.00"), cart.total?.amount)
        assertEquals(BigDecimal("0.00"), cart.lines.single().unitPrice?.amount)
    }

    @Test
    fun `cart accepts consistent TRY money`() = runBlocking {
        val result = createCart(MoneyFixture())

        val cart = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CartReference
        assertEquals("TRY", cart.total?.currencyCode)
        assertEquals("TRY", cart.lines.single().totalPrice?.currencyCode)
    }

    @Test
    fun `cart still completes a valid second page`() = runBlocking {
        server.enqueue(MockResponse().setBody(cartReadFirstPageResponse()))
        server.enqueue(MockResponse().setBody(cartReadSecondPageResponse()))

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadCart(SensitiveCartId.from(CART_ID))

        val cart = assertInstanceOf(StorefrontResult.Success::class.java, result).value as CartReference
        assertEquals(2, cart.lines.size)
        assertEquals(false, cart.hasMoreLines)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cart still bounds a repeated second-page cursor`() = runBlocking {
        server.enqueue(MockResponse().setBody(cartReadFirstPageResponse()))
        server.enqueue(MockResponse().setBody(cartReadSecondPageResponse(hasNextPage = true, endCursor = "cursor-1")))

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadCart(SensitiveCartId.from(CART_ID))

        assertEquals(graphQlFailure<CartReference>("CART_LINE_PAGE_LIMIT"), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cart still rejects a duplicate line across pages`() = runBlocking {
        server.enqueue(MockResponse().setBody(cartReadFirstPageResponse()))
        server.enqueue(MockResponse().setBody(cartReadSecondPageResponse(lineId = FIRST_LINE_ID)))

        val result = ApolloStorefrontGateway(client, mediaPolicy).loadCart(SensitiveCartId.from(CART_ID))

        assertEquals(graphQlFailure<CartReference>("DUPLICATE_CART_LINE_PAGE"), result)
        assertEquals(2, server.requestCount)
    }

    private suspend fun createCart(money: MoneyFixture): StorefrontResult<CartReference> {
        server.enqueue(MockResponse().setBody(cartCreateResponse(money)))
        return ApolloStorefrontGateway(client, mediaPolicy).createCart(listOf(CartLineInput(VARIANT_ID, 1)))
    }

    private fun cartCreateResponse(money: MoneyFixture): String =
        """{"data":{"cartCreate":{"cart":${cartSnapshot(money)},"userErrors":[],"warnings":[]}}}"""

    private fun cartReadFirstPageResponse(): String = """{"data":{"cart":${cartSnapshot(
        MoneyFixture(),
        totalQuantity = 2,
        hasNextPage = true,
        endCursor = "cursor-1",
        lineId = FIRST_LINE_ID
    )}}}"""

    private fun cartReadSecondPageResponse(
        money: MoneyFixture = MoneyFixture(),
        hasNextPage: Boolean = false,
        endCursor: String? = null,
        lineId: String = SECOND_LINE_ID
    ): String = """{"data":{"cart":{"lines":{"nodes":[${cartLine(
        lineId,
        money
    )}],"pageInfo":${pageInfo(hasNextPage, endCursor)}}}}}"""

    private fun cartSnapshot(
        money: MoneyFixture,
        totalQuantity: Int = 1,
        hasNextPage: Boolean = false,
        endCursor: String? = null,
        lineId: String = FIRST_LINE_ID
    ): String = """{"__typename":"Cart","id":"$CART_ID",
            "checkoutUrl":"https://gurbakir.com/cart/c/synthetic?key=synthetic-checkout-secret",
            "totalQuantity":$totalQuantity,"buyerIdentity":{"customer":null},
            "cost":{"subtotalAmount":{"amount":"${money.cartSubtotal.amount}","currencyCode":"${money.cartSubtotal.currency}"},
                    "totalAmount":{"amount":"${money.cartTotal.amount}","currencyCode":"${money.cartTotal.currency}"}},
            "lines":{"nodes":[${cartLine(lineId, money)}],"pageInfo":${pageInfo(hasNextPage, endCursor)}}}"""

    private fun cartLine(lineId: String, money: MoneyFixture): String =
        """{"__typename":"CartLine","id":"$lineId","quantity":1,
            "instructions":{"canRemove":true,"canUpdateQuantity":true},
            "cost":{"amountPerQuantity":{"amount":"${money.lineUnit.amount}","currencyCode":"${money.lineUnit.currency}"},
                    "totalAmount":{"amount":"${money.lineTotal.amount}","currencyCode":"${money.lineTotal.currency}"}},
            "merchandise":{"__typename":"ProductVariant","id":"$VARIANT_ID","title":"Synthetic variant",
                           "availableForSale":true,"currentlyNotInStock":false,
                           "quantityRule":{"minimum":1,"maximum":null,"increment":1},"image":null,
                           "product":{"id":"gid://shopify/Product/synthetic","title":"Synthetic product"}}}"""

    private fun pageInfo(hasNextPage: Boolean, endCursor: String?): String =
        """{"endCursor":${endCursor?.let { "\"$it\"" } ?: "null"},"hasNextPage":$hasNextPage}"""

    private data class MoneyFixture(
        val cartSubtotal: MoneyValue = MoneyValue(),
        val cartTotal: MoneyValue = MoneyValue(),
        val lineUnit: MoneyValue = MoneyValue(),
        val lineTotal: MoneyValue = MoneyValue()
    )

    private data class MoneyValue(val amount: String = "10.00", val currency: String = "TRY")

    private companion object {
        const val CART_ID = "gid://shopify/Cart/synthetic?key=synthetic-secret"
        const val VARIANT_ID = "gid://shopify/ProductVariant/synthetic"
        const val FIRST_LINE_ID = "gid://shopify/CartLine/first"
        const val SECOND_LINE_ID = "gid://shopify/CartLine/second"
    }
}
