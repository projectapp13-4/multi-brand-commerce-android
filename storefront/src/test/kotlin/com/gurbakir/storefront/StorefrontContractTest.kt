package com.gurbakir.storefront

import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class StorefrontContractTest {
    @Test
    fun `unconfigured gateway fails closed without creating a cart`() {
        var result: StorefrontResult<CartReference>? = null
        kotlinx.coroutines.test.runTest {
            result = UnconfiguredStorefrontGateway().createCart(
                listOf(CartLineInput(merchandiseId = "gid://fixture", quantity = 1))
            )
        }

        val failure = assertInstanceOf(StorefrontResult.Failure::class.java, requireNotNull(result))
        assertInstanceOf(StorefrontFailure.Configuration::class.java, failure.error)
    }

    @Test
    fun `invalid cart is distinct from transient transport failure`() {
        val invalid = StorefrontFailure.InvalidCart(InvalidCartReason.EXPIRED)
        val transport = StorefrontFailure.Transport(retryable = true)

        assertFalse(invalid == transport)
        assertEquals(InvalidCartReason.EXPIRED, invalid.reason)
    }

    @Test
    fun `cart reference keeps checkout url typed`() {
        val cart =
            CartReference(
                id = SensitiveCartId.from("gid://shopify/Cart/fixture?key=synthetic"),
                checkoutUrl = SensitiveCheckoutUrl.from(URI("https://checkout.example.test/cart")),
                totalQuantity = 0,
                lines = emptyList(),
                hasMoreLines = false,
                warningCodes = emptySet()
            )

        assertEquals("https", cart.checkoutUrl.use { it.scheme })
        assertEquals("<redacted-cart-id>", cart.id.toString())
    }
}
