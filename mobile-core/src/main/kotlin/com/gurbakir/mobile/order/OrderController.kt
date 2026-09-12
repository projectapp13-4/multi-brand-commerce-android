package com.gurbakir.mobile.order

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerOrderDetail
import com.gurbakir.account.CustomerOrderGateway
import com.gurbakir.account.CustomerOrderIds
import com.gurbakir.account.CustomerOrderSummary
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import javax.inject.Inject
import javax.inject.Singleton

data class OrderSummaryContent(val routeId: String, val order: CustomerOrderSummary) {
    override fun toString(): String = "OrderSummaryContent(<redacted>)"
}

data class OrderPageContent(val orders: List<OrderSummaryContent>, val nextCursor: String?) {
    override fun toString(): String = "OrderPageContent(count=${orders.size}, hasNext=${nextCursor != null})"
}

enum class OrderFailure {
    CONNECTION,
    SERVICE
}

sealed interface OrderPageResult {
    data class Content(val page: OrderPageContent) : OrderPageResult

    data object SignedOut : OrderPageResult

    data class Failed(val reason: OrderFailure) : OrderPageResult
}

sealed interface OrderDetailResult {
    data class Content(val order: CustomerOrderDetail) : OrderDetailResult

    data object Unavailable : OrderDetailResult

    data object SignedOut : OrderDetailResult

    data class Failed(val reason: OrderFailure) : OrderDetailResult
}

interface OrderController {
    suspend fun loadPage(after: String? = null): OrderPageResult

    suspend fun loadDetail(orderId: String): OrderDetailResult
}

@Singleton
class DefaultOrderController
@Inject
constructor(
    private val gateway: CustomerOrderGateway,
    private val sessionCoordinator: CustomerAccountSessionCoordinator
) : OrderController {
    override suspend fun loadPage(after: String?): OrderPageResult = when (val result = gateway.loadOrderPage(after)) {
        is CustomerAccountResult.Success -> {
            val mapped = result.value.orders.mapNotNull { order ->
                CustomerOrderIds.orderRouteFromGid(order.id)?.let { OrderSummaryContent(it, order) }
            }
            if (mapped.size != result.value.orders.size) {
                OrderPageResult.Failed(OrderFailure.SERVICE)
            } else {
                OrderPageResult.Content(OrderPageContent(mapped, result.value.nextCursor))
            }
        }

        is CustomerAccountResult.Failure -> resolvePageFailure(result.reason)
    }

    override suspend fun loadDetail(orderId: String): OrderDetailResult {
        if (!CustomerOrderIds.isOrderGid(orderId)) return OrderDetailResult.Unavailable
        return when (val result = gateway.loadOrder(orderId)) {
            is CustomerAccountResult.Success ->
                result.value?.let(OrderDetailResult::Content) ?: OrderDetailResult.Unavailable

            is CustomerAccountResult.Failure -> resolveDetailFailure(result.reason)
        }
    }

    private suspend fun resolvePageFailure(failure: CustomerAccountFailure): OrderPageResult = when {
        failure.isTerminalOrderSessionFailure() -> {
            sessionCoordinator.clearForLogout()
            OrderPageResult.SignedOut
        }

        failure.isOrderConnectionFailure() -> OrderPageResult.Failed(OrderFailure.CONNECTION)

        else -> OrderPageResult.Failed(OrderFailure.SERVICE)
    }

    private suspend fun resolveDetailFailure(failure: CustomerAccountFailure): OrderDetailResult = when {
        failure.isOrderUnavailableFailure() -> OrderDetailResult.Unavailable

        failure.isTerminalOrderSessionFailure() -> {
            sessionCoordinator.clearForLogout()
            OrderDetailResult.SignedOut
        }

        failure.isOrderConnectionFailure() -> OrderDetailResult.Failed(OrderFailure.CONNECTION)

        else -> OrderDetailResult.Failed(OrderFailure.SERVICE)
    }
}

private val TERMINAL_ORDER_ERROR_CODES = setOf("UNAUTHENTICATED", "UNAUTHORIZED", "TOKEN_INVALID")
private val UNAVAILABLE_ORDER_ERROR_CODES =
    setOf("NOT_FOUND", "RESOURCE_NOT_FOUND", "ACCESS_DENIED", "FORBIDDEN", "INVALID_ORDER_ID")

private fun CustomerAccountFailure.isTerminalOrderSessionFailure(): Boolean = when (this) {
    CustomerAccountFailure.SignedOut -> true
    is CustomerAccountFailure.Authentication -> reason != CustomerTokenFailure.Transient
    is CustomerAccountFailure.GraphQl -> errorCodes.any(TERMINAL_ORDER_ERROR_CODES::contains)
    else -> false
}

private fun CustomerAccountFailure.isOrderUnavailableFailure(): Boolean =
    this is CustomerAccountFailure.GraphQl && errorCodes.any(UNAVAILABLE_ORDER_ERROR_CODES::contains)

private fun CustomerAccountFailure.isOrderConnectionFailure(): Boolean =
    (this is CustomerAccountFailure.Transport && retryable) ||
        (this is CustomerAccountFailure.Authentication && reason == CustomerTokenFailure.Transient)
