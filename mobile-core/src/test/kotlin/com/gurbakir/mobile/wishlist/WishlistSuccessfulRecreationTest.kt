package com.gurbakir.mobile.wishlist

import com.gurbakir.storefront.StorefrontResult
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Old-API P-A4 characterization; final visible-entry proof lives in the actual destination Android test. */
@OptIn(ExperimentalCoroutinesApi::class)
class WishlistSuccessfulRecreationTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `new actual model discovers removal after prior successful snapshot`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture(1)
        try {
            fixture.gateway.holdReads = false
            val first = fixture.wishlist()
            runCurrent()
            assertEquals("Initial synthetic product", first.state.value.entries.single().product?.title)
            assertEquals(1, fixture.gateway.started)
            fixture.clearModel(first)
            fixture.gateway.result(fixture.initial.single().productId, StorefrontResult.Success(null))
            val next = fixture.wishlist()
            runCurrent()
            assertEquals(2, fixture.gateway.started, "Ordinary model recreation must not reuse old successful truth")
            assertEquals(WishlistItemIssue.REMOVED, next.state.value.entries.single().issue)
            assertEquals(null, next.state.value.entries.single().product)
            assertFalse(next.state.value.hasRetryableItems)
        } finally {
            fixture.close()
            runCurrent()
        }
    }

    @Test
    fun `new actual model reads changed title availability and price without refresh`() = runTest(dispatcher) {
        val fixture = WishlistHydrationFixture(1)
        try {
            fixture.gateway.holdReads = false
            val first = fixture.wishlist()
            runCurrent()
            assertEquals(1, fixture.gateway.started)
            assertFalse(first.state.value.hasRetryableItems)
            fixture.clearModel(first)
            fixture.gateway.title = "Changed synthetic product"
            fixture.gateway.available = false
            fixture.gateway.price = "125.00"
            val next = fixture.wishlist()
            runCurrent()
            assertEquals(2, fixture.gateway.started, "No refresh method or elapsed time is used")
            val product = checkNotNull(next.state.value.entries.single().product)
            assertEquals("Changed synthetic product", product.title)
            assertFalse(product.availableForSale)
            assertEquals(BigDecimal("125.00"), product.variants.first().price.amount)
        } finally {
            fixture.close()
            runCurrent()
        }
    }
}
