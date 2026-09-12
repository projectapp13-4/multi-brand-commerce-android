package com.gurbakir.mobile.order

import app.cash.turbine.test
import com.gurbakir.account.CustomerOrderDetail
import com.gurbakir.account.CustomerOrderFinancialStatus
import com.gurbakir.account.CustomerOrderFulfillmentStatus
import com.gurbakir.account.CustomerOrderMoney
import com.gurbakir.account.CustomerOrderSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModelTest {
    @Test
    fun `list pagination deduplicates summaries and announces only additions`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller =
                FakeOrderController(
                    pages =
                        mutableListOf(
                            page(listOf(summary("1")), "next"),
                            page(listOf(summary("1"), summary("2")), null)
                        )
                )
            val viewModel = OrderListViewModel(controller)
            advanceUntilIdle()

            viewModel.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("1", "2"), viewModel.state.value.orders.map { it.routeId })
            assertEquals(1, viewModel.state.value.addedOrderCount)
            assertNull(viewModel.state.value.nextCursor)
        }
    }

    @Test
    fun `page failure retains verified list and retry resumes the same cursor`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller =
                FakeOrderController(
                    pages =
                        mutableListOf(
                            page(listOf(summary("1")), "next"),
                            OrderPageResult.Failed(OrderFailure.CONNECTION),
                            page(listOf(summary("2")), null)
                        )
                )
            val viewModel = OrderListViewModel(controller)
            advanceUntilIdle()
            viewModel.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("1"), viewModel.state.value.orders.map { it.routeId })
            assertEquals(OrderFailure.CONNECTION, viewModel.state.value.failure)

            viewModel.retry()
            advanceUntilIdle()
            assertEquals(listOf("1", "2"), viewModel.state.value.orders.map { it.routeId })
            assertEquals(listOf(null, "next", "next"), controller.requestedCursors)
        }
    }

    @Test
    fun `private list is cleared off screen and authoritatively reloaded on resume`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller =
                FakeOrderController(
                    pages = mutableListOf(page(listOf(summary("1"))), page(listOf(summary("2"))))
                )
            val viewModel = OrderListViewModel(controller)
            advanceUntilIdle()

            viewModel.clearPrivateContent()
            assertTrue(viewModel.state.value.orders.isEmpty())
            assertTrue(!viewModel.state.value.loaded)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(listOf("2"), viewModel.state.value.orders.map { it.routeId })
        }
    }

    @Test
    fun `terminal list session clears content and returns to account`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = OrderListViewModel(FakeOrderController(pages = mutableListOf(OrderPageResult.SignedOut)))

            viewModel.effects.test {
                advanceUntilIdle()
                assertEquals(OrderListEffect.ReturnToAccount, awaitItem())
                assertTrue(viewModel.state.value.orders.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `detail keeps no private copy while backgrounded and reloads on resume`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller =
                FakeOrderController(
                    details =
                        mutableListOf(
                            OrderDetailResult.Content(detail("1")),
                            OrderDetailResult.Content(detail("1"))
                        )
                )
            val viewModel = OrderDetailViewModel(controller)
            viewModel.start("gid://shopify/Order/1")
            advanceUntilIdle()
            assertEquals(OrderDetailPhase.READY, viewModel.state.value.phase)

            viewModel.clearPrivateContent()
            assertNull(viewModel.state.value.order)
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(OrderDetailPhase.READY, viewModel.state.value.phase)
            assertEquals(2, controller.requestedOrderIds.size)
        }
    }

    @Test
    fun `detail tracking feedback never stores the tracking URL`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = OrderDetailViewModel(FakeOrderController())
            viewModel.onTrackingLaunchResult(TrackingLaunchResult.REJECTED)

            assertEquals(TrackingFeedback.REJECTED, viewModel.state.value.trackingFeedback)
            assertTrue(!viewModel.state.value.toString().contains("http"))
        }
    }

    private suspend fun withMainDispatcher(dispatcher: TestDispatcher, block: suspend () -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeOrderController(
        private val pages: MutableList<OrderPageResult> = mutableListOf(page(emptyList())),
        private val details: MutableList<OrderDetailResult> = mutableListOf(OrderDetailResult.Unavailable)
    ) : OrderController {
        val requestedCursors = mutableListOf<String?>()
        val requestedOrderIds = mutableListOf<String>()

        override suspend fun loadPage(after: String?): OrderPageResult {
            requestedCursors += after
            return if (pages.size == 1) pages.single() else pages.removeAt(0)
        }

        override suspend fun loadDetail(orderId: String): OrderDetailResult {
            requestedOrderIds += orderId
            return if (details.size == 1) details.single() else details.removeAt(0)
        }
    }

    private companion object {
        fun page(orders: List<CustomerOrderSummary>, nextCursor: String? = null) = OrderPageResult.Content(
            OrderPageContent(
                orders.map { summary ->
                    OrderSummaryContent(summary.id.substringAfterLast('/'), summary)
                },
                nextCursor
            )
        )

        fun summary(id: String) = CustomerOrderSummary(
            id = "gid://shopify/Order/$id",
            name = "#$id",
            processedAt = "2026-08-12T10:00:00Z",
            financialStatus = CustomerOrderFinancialStatus.PAID,
            fulfillmentStatus = CustomerOrderFulfillmentStatus.UNFULFILLED,
            totalPrice = CustomerOrderMoney("100.00", "TRY")
        )

        fun detail(id: String) = CustomerOrderDetail(
            id = "gid://shopify/Order/$id",
            name = "#$id",
            processedAt = "2026-08-12T10:00:00Z",
            cancelledAt = null,
            financialStatus = CustomerOrderFinancialStatus.PAID,
            fulfillmentStatus = CustomerOrderFulfillmentStatus.UNFULFILLED,
            subtotal = null,
            totalPrice = CustomerOrderMoney("100.00", "TRY"),
            totalRefunded = CustomerOrderMoney("0.00", "TRY"),
            totalShipping = CustomerOrderMoney("0.00", "TRY"),
            totalTax = null,
            shippingAddress = null,
            lines = emptyList(),
            fulfillments = emptyList()
        )
    }
}
