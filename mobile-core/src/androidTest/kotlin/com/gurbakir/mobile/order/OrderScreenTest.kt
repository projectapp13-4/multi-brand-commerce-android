@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.order

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.account.CustomerFulfillmentLine
import com.gurbakir.account.CustomerFulfillmentStatus
import com.gurbakir.account.CustomerOrderDetail
import com.gurbakir.account.CustomerOrderFinancialStatus
import com.gurbakir.account.CustomerOrderFulfillment
import com.gurbakir.account.CustomerOrderFulfillmentStatus
import com.gurbakir.account.CustomerOrderLine
import com.gurbakir.account.CustomerOrderMoney
import com.gurbakir.account.CustomerOrderSummary
import com.gurbakir.account.CustomerShipmentStatus
import com.gurbakir.account.CustomerTrackingInformation
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyOrderListOffersOwnedSupportRecovery() {
        var support = 0
        setListContent(
            OrderListUiState(phase = OrderListPhase.READY, loaded = true),
            listActions(onSupport = { support += 1 })
        )

        composeRule.onNodeWithTag(OrderListTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(OrderListTestTags.EMPTY_SUPPORT).assertHasClickAction().performDeterministicClick()
        assertEquals(1, support)
    }

    @Test
    fun verifiedOrderSummaryOpensOnlyItsBoundedRouteId() {
        var opened: String? = null
        val content = OrderSummaryContent("1001", summary())
        setListContent(
            OrderListUiState(
                phase = OrderListPhase.READY,
                orders = listOf(content),
                loaded = true
            ),
            listActions(onOpen = { opened = it })
        )

        composeRule.onNodeWithTag(OrderListTestTags.card("1001")).assertIsDisplayed()
        composeRule.onNodeWithTag(OrderListTestTags.open("1001")).performDeterministicClick()
        assertEquals("1001", opened)
    }

    @Test
    fun partialFulfillmentsExposeOnlyAllowlistedTrackingAndRemainUsableAtLargeText() {
        var trackingOpened: String? = null
        var support = 0
        val state = OrderDetailUiState(OrderDetailPhase.READY, order = detail())
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CoreTestTheme(darkTheme = false) {
                    OrderDetailScreen(
                        state = state,
                        actions =
                            detailActions(
                                onTracking = { trackingOpened = it },
                                onSupport = { support += 1 }
                            ),
                        isTrackingAllowed = TrackingUrlPolicy(setOf("carrier.example"))::isAllowed
                    )
                }
            }
        }

        composeRule.onNodeWithTag(OrderDetailTestTags.fulfillment(1)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(OrderDetailTestTags.tracking(0)).performScrollTo().performDeterministicClick()
        assertEquals("https://www.carrier.example/takip?code=SYNTHETIC", trackingOpened)
        composeRule.onAllNodesWithTag(OrderDetailTestTags.tracking(1)).assertCountEquals(0)
        composeRule.onNodeWithTag(OrderDetailTestTags.TRACKING_SUPPORT).performScrollTo().performDeterministicClick()
        assertEquals(1, support)
    }

    @Test
    fun unavailableDetailHasNoExistenceOracleAndOffersSupport() {
        var support = 0
        setDetailContent(
            OrderDetailUiState(OrderDetailPhase.UNAVAILABLE),
            detailActions(onSupport = { support += 1 })
        )

        composeRule.onNodeWithTag(OrderDetailTestTags.UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithTag(OrderDetailTestTags.RECOVERY_SUPPORT).performDeterministicClick()
        assertEquals(1, support)
    }

    private fun setListContent(state: OrderListUiState, actions: OrderListActions = listActions()) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                OrderListScreen(state, actions)
            }
        }
    }

    private fun setDetailContent(state: OrderDetailUiState, actions: OrderDetailActions = detailActions()) {
        composeRule.setContent {
            CoreTestTheme(darkTheme = false) {
                OrderDetailScreen(state, actions, TrackingUrlPolicy(setOf("carrier.example"))::isAllowed)
            }
        }
    }

    private fun listActions(onOpen: (String) -> Unit = {}, onSupport: () -> Unit = {}) = OrderListActions(
        onBack = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpenOrder = onOpen,
        onSupport = onSupport
    )

    private fun detailActions(onTracking: (String) -> Unit = {}, onSupport: () -> Unit = {}) = OrderDetailActions(
        onBack = {},
        onRetry = {},
        onOpenTracking = onTracking,
        onSupport = onSupport
    )

    private companion object {
        fun summary() = CustomerOrderSummary(
            id = "gid://shopify/Order/1001",
            name = "#1001",
            processedAt = "2026-08-12T10:00:00Z",
            financialStatus = CustomerOrderFinancialStatus.PAID,
            fulfillmentStatus = CustomerOrderFulfillmentStatus.PARTIALLY_FULFILLED,
            totalPrice = CustomerOrderMoney("1250.50", "TRY")
        )

        fun detail() = CustomerOrderDetail(
            id = "gid://shopify/Order/1001",
            name = "#1001",
            processedAt = "2026-08-12T10:00:00Z",
            cancelledAt = null,
            financialStatus = CustomerOrderFinancialStatus.PAID,
            fulfillmentStatus = CustomerOrderFulfillmentStatus.PARTIALLY_FULFILLED,
            subtotal = CustomerOrderMoney("1200.00", "TRY"),
            totalPrice = CustomerOrderMoney("1250.50", "TRY"),
            totalRefunded = CustomerOrderMoney("0.00", "TRY"),
            totalShipping = CustomerOrderMoney("50.50", "TRY"),
            totalTax = CustomerOrderMoney("200.00", "TRY"),
            shippingAddress = listOf("Synthetic Customer", "Private street", "Istanbul"),
            lines =
                listOf(
                    CustomerOrderLine(
                        id = "line-1",
                        name = "Synthetic copper item",
                        variantTitle = "Small",
                        quantity = 2,
                        totalPrice = CustomerOrderMoney("1200.00", "TRY")
                    )
                ),
            fulfillments =
                listOf(
                    fulfillment(
                        id = "1",
                        tracking =
                            CustomerTrackingInformation(
                                company = "Aras Kargo",
                                number = "SYNTHETIC",
                                url = "https://www.carrier.example/takip?code=SYNTHETIC"
                            )
                    ),
                    fulfillment(
                        id = "2",
                        tracking =
                            CustomerTrackingInformation(
                                company = "Unknown",
                                number = "SYNTHETIC-2",
                                url = "https://tracking.example/SYNTHETIC-2"
                            )
                    )
                )
        )

        fun fulfillment(id: String, tracking: CustomerTrackingInformation) = CustomerOrderFulfillment(
            id = id,
            createdAt = "2026-08-12T11:00:00Z",
            estimatedDeliveryAt = null,
            shipmentStatus = CustomerShipmentStatus.IN_TRANSIT,
            status = CustomerFulfillmentStatus.SUCCESS,
            lines =
                listOf(
                    CustomerFulfillmentLine(
                        id = "fulfillment-line-$id",
                        orderLineId = "line-1",
                        name = "Synthetic copper item",
                        variantTitle = "Small",
                        quantity = 1
                    )
                ),
            tracking = listOf(tracking)
        )
    }
}
