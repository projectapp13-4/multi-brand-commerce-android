package com.gurbakir.account

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.gurbakir.account.graphql.CustomerOrderDetailQuery
import com.gurbakir.account.graphql.CustomerOrdersQuery
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerSessionResolution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_ORDER_REQUEST_TIMEOUT_MILLIS = 45_000L
private const val ORDER_PAGE_SIZE = 20
private const val ORDER_DETAIL_CONNECTION_LIMIT = 250
private const val INVALID_ORDER_ID = "INVALID_ORDER_ID"
private const val INVALID_ORDER_PAGE = "INVALID_ORDER_PAGE"
private const val INCOMPLETE_ORDER_DETAIL = "INCOMPLETE_ORDER_DETAIL"
private val ORDER_GID = Regex("^gid://shopify/Order/([1-9][0-9]*)$")
private val ORDER_ROUTE_ID = Regex("^[1-9][0-9]*$")

data class CustomerOrderMoney(val amount: String, val currencyCode: String) {
    override fun toString(): String = "CustomerOrderMoney(<redacted>)"
}

enum class CustomerOrderFinancialStatus {
    AUTHORIZED,
    EXPIRED,
    PAID,
    PARTIALLY_PAID,
    PARTIALLY_REFUNDED,
    PENDING,
    REFUNDED,
    VOIDED,
    UNKNOWN
}

enum class CustomerOrderFulfillmentStatus {
    FULFILLED,
    IN_PROGRESS,
    ON_HOLD,
    OPEN,
    PARTIALLY_FULFILLED,
    PENDING_FULFILLMENT,
    RESTOCKED,
    SCHEDULED,
    UNFULFILLED,
    UNKNOWN
}

enum class CustomerFulfillmentStatus {
    CANCELLED,
    ERROR,
    FAILURE,
    SUCCESS,
    OPEN,
    PENDING,
    UNKNOWN
}

enum class CustomerShipmentStatus {
    ATTEMPTED_DELIVERY,
    CARRIER_PICKED_UP,
    CONFIRMED,
    DELAYED,
    DELIVERED,
    FAILURE,
    IN_TRANSIT,
    LABEL_PRINTED,
    LABEL_PURCHASED,
    OUT_FOR_DELIVERY,
    PICKED_UP,
    READY_FOR_PICKUP,
    UNKNOWN
}

data class CustomerOrderSummary(
    val id: String,
    val name: String,
    val processedAt: String,
    val financialStatus: CustomerOrderFinancialStatus?,
    val fulfillmentStatus: CustomerOrderFulfillmentStatus,
    val totalPrice: CustomerOrderMoney
) {
    override fun toString(): String = "CustomerOrderSummary(<redacted>)"
}

data class CustomerOrderPage(val orders: List<CustomerOrderSummary>, val nextCursor: String?) {
    override fun toString(): String = "CustomerOrderPage(count=${orders.size}, hasNext=${nextCursor != null})"
}

data class CustomerOrderLine(
    val id: String,
    val name: String,
    val variantTitle: String?,
    val quantity: Int,
    val totalPrice: CustomerOrderMoney?
) {
    override fun toString(): String = "CustomerOrderLine(<redacted>)"
}

data class CustomerFulfillmentLine(
    val id: String,
    val orderLineId: String,
    val name: String,
    val variantTitle: String?,
    val quantity: Int?
) {
    override fun toString(): String = "CustomerFulfillmentLine(<redacted>)"
}

data class CustomerTrackingInformation(val company: String?, val number: String?, val url: String?) {
    override fun toString(): String = "CustomerTrackingInformation(<redacted>)"
}

data class CustomerOrderFulfillment(
    val id: String,
    val createdAt: String,
    val estimatedDeliveryAt: String?,
    val shipmentStatus: CustomerShipmentStatus?,
    val status: CustomerFulfillmentStatus?,
    val lines: List<CustomerFulfillmentLine>,
    val tracking: List<CustomerTrackingInformation>
) {
    override fun toString(): String =
        "CustomerOrderFulfillment(<redacted>, lineCount=${lines.size}, trackingCount=${tracking.size})"
}

data class CustomerOrderDetail(
    val id: String,
    val name: String,
    val processedAt: String,
    val cancelledAt: String?,
    val financialStatus: CustomerOrderFinancialStatus?,
    val fulfillmentStatus: CustomerOrderFulfillmentStatus,
    val subtotal: CustomerOrderMoney?,
    val totalPrice: CustomerOrderMoney,
    val totalRefunded: CustomerOrderMoney,
    val totalShipping: CustomerOrderMoney,
    val totalTax: CustomerOrderMoney?,
    val shippingAddress: List<String>?,
    val lines: List<CustomerOrderLine>,
    val fulfillments: List<CustomerOrderFulfillment>
) {
    override fun toString(): String =
        "CustomerOrderDetail(<redacted>, lineCount=${lines.size}, fulfillmentCount=${fulfillments.size})"
}

object CustomerOrderIds {
    fun orderGidFromRoute(routeValue: String): String? =
        routeValue.takeIf(ORDER_ROUTE_ID::matches)?.let { "gid://shopify/Order/$it" }

    fun orderRouteFromGid(gid: String): String? = ORDER_GID.matchEntire(gid)?.groupValues?.get(1)

    fun isOrderGid(value: String): Boolean = ORDER_GID.matches(value)
}

interface CustomerOrderGateway {
    suspend fun loadOrderPage(after: String? = null): CustomerAccountResult<CustomerOrderPage>

    suspend fun loadOrder(orderId: String): CustomerAccountResult<CustomerOrderDetail?>
}

class UnconfiguredCustomerOrderGateway : CustomerOrderGateway {
    private val failure = CustomerAccountFailure.Authentication(CustomerTokenFailure.InvalidResponse)

    override suspend fun loadOrderPage(after: String?): CustomerAccountResult<CustomerOrderPage> =
        CustomerAccountResult.Failure(failure)

    override suspend fun loadOrder(orderId: String): CustomerAccountResult<CustomerOrderDetail?> =
        CustomerAccountResult.Failure(failure)
}

object CustomerOrderApolloClientFactory {
    fun createGateway(
        discoveryClient: CustomerAccountDiscoveryClient,
        sessionResolver: CustomerSessionResolver
    ): CustomerOrderGateway = DiscoveringCustomerOrderGateway(discoveryClient, sessionResolver)
}

private class DiscoveringCustomerOrderGateway(
    private val discoveryClient: CustomerAccountDiscoveryClient,
    private val sessionResolver: CustomerSessionResolver
) : CustomerOrderGateway {
    private val lock = Mutex()
    private var delegate: CustomerOrderGateway? = null

    override suspend fun loadOrderPage(after: String?): CustomerAccountResult<CustomerOrderPage> =
        when (val resolution = resolveDelegate()) {
            is OrderGatewayResolution.Ready -> resolution.gateway.loadOrderPage(after)
            is OrderGatewayResolution.Failed -> CustomerAccountResult.Failure(resolution.reason)
        }

    override suspend fun loadOrder(orderId: String): CustomerAccountResult<CustomerOrderDetail?> =
        when (val resolution = resolveDelegate()) {
            is OrderGatewayResolution.Ready -> resolution.gateway.loadOrder(orderId)
            is OrderGatewayResolution.Failed -> CustomerAccountResult.Failure(resolution.reason)
        }

    private suspend fun resolveDelegate(): OrderGatewayResolution = lock.withLock {
        delegate?.let { return@withLock OrderGatewayResolution.Ready(it) }
        when (val discovery = discoveryClient.discover()) {
            is CustomerAccountDiscoveryResult.Failure ->
                OrderGatewayResolution.Failed(CustomerAccountFailure.Discovery(discovery.reason))

            is CustomerAccountDiscoveryResult.Success ->
                ApolloCustomerOrderGateway(
                    CustomerAccountApolloClientFactory.createClient(
                        discovery.configuration.graphqlEndpoint
                    ),
                    sessionResolver
                ).also { delegate = it }.let(OrderGatewayResolution::Ready)
        }
    }
}

private sealed interface OrderGatewayResolution {
    data class Ready(val gateway: CustomerOrderGateway) : OrderGatewayResolution

    data class Failed(val reason: CustomerAccountFailure) : OrderGatewayResolution
}

class ApolloCustomerOrderGateway(
    private val client: ApolloClient,
    private val sessionResolver: CustomerSessionResolver,
    requestTimeoutMillis: Long = DEFAULT_ORDER_REQUEST_TIMEOUT_MILLIS
) : CustomerOrderGateway {
    private val callExecutor = CustomerApolloCallExecutor(requestTimeoutMillis)

    override suspend fun loadOrderPage(after: String?): CustomerAccountResult<CustomerOrderPage> = when (
        val result = executeAuthenticated {
            client.query(
                CustomerOrdersQuery(
                    first = ORDER_PAGE_SIZE,
                    after = after?.let { Optional.present(it) } ?: Optional.Absent
                )
            )
        }
    ) {
        is CustomerAccountResult.Failure -> result
        is CustomerAccountResult.Success -> result.value.toOrderPage()
    }

    override suspend fun loadOrder(orderId: String): CustomerAccountResult<CustomerOrderDetail?> {
        if (!CustomerOrderIds.isOrderGid(orderId)) {
            return CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf(INVALID_ORDER_ID)))
        }
        return when (
            val result = executeAuthenticated {
                client.query(CustomerOrderDetailQuery(orderId, ORDER_DETAIL_CONNECTION_LIMIT))
            }
        ) {
            is CustomerAccountResult.Failure -> result
            is CustomerAccountResult.Success -> result.value.toOrderDetail()
        }
    }

    private suspend fun <D : Operation.Data> executeAuthenticated(
        createCall: () -> ApolloCall<D>
    ): CustomerAccountResult<D> = when (val resolution = sessionResolver.resolve()) {
        CustomerSessionResolution.SignedOut ->
            CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut)

        is CustomerSessionResolution.Failed ->
            CustomerAccountResult.Failure(CustomerAccountFailure.Authentication(resolution.reason))

        is CustomerSessionResolution.Authenticated -> {
            val call = resolution.session.accessToken.use { token ->
                createCall().addHttpHeader("Authorization", token)
            }
            callExecutor.execute(call)
        }
    }
}

private fun CustomerOrdersQuery.Data.toOrderPage(): CustomerAccountResult<CustomerOrderPage> {
    val connection = customer.orders
    val invalidCode = when {
        connection.pageInfo.hasNextPage && connection.pageInfo.endCursor == null -> INVALID_ORDER_PAGE
        connection.nodes.any { !CustomerOrderIds.isOrderGid(it.id) } -> INVALID_ORDER_ID
        else -> null
    }
    return if (invalidCode != null) {
        CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf(invalidCode)))
    } else {
        CustomerAccountResult.Success(
            CustomerOrderPage(
                orders = connection.nodes.map { node ->
                    CustomerOrderSummary(
                        id = node.id,
                        name = node.name,
                        processedAt = node.processedAt,
                        financialStatus = node.financialStatus?.rawValue.toFinancialStatus(),
                        fulfillmentStatus = node.fulfillmentStatus.rawValue.toFulfillmentStatus(),
                        totalPrice = CustomerOrderMoney(node.totalPrice.amount, node.totalPrice.currencyCode)
                    )
                },
                nextCursor = connection.pageInfo.endCursor.takeIf { connection.pageInfo.hasNextPage }
            )
        )
    }
}

private fun CustomerOrderDetailQuery.Data.toOrderDetail(): CustomerAccountResult<CustomerOrderDetail?> {
    val value = order
    val incomplete =
        value?.let { detail ->
            detail.lineItems.pageInfo.hasNextPage ||
                detail.fulfillments.pageInfo.hasNextPage ||
                detail.fulfillments.nodes.any { it.fulfillmentLineItems.pageInfo.hasNextPage }
        } == true
    return when {
        value == null -> CustomerAccountResult.Success(null)

        incomplete -> CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf(INCOMPLETE_ORDER_DETAIL)))

        !CustomerOrderIds.isOrderGid(value.id) ->
            CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf(INVALID_ORDER_ID)))

        else -> CustomerAccountResult.Success(value.toCustomerOrderDetail())
    }
}

private fun CustomerOrderDetailQuery.Order.toCustomerOrderDetail(): CustomerOrderDetail = CustomerOrderDetail(
    id = id,
    name = name,
    processedAt = processedAt,
    cancelledAt = cancelledAt,
    financialStatus = financialStatus?.rawValue.toFinancialStatus(),
    fulfillmentStatus = fulfillmentStatus.rawValue.toFulfillmentStatus(),
    subtotal = subtotal?.let { CustomerOrderMoney(it.amount, it.currencyCode) },
    totalPrice = CustomerOrderMoney(totalPrice.amount, totalPrice.currencyCode),
    totalRefunded = CustomerOrderMoney(totalRefunded.amount, totalRefunded.currencyCode),
    totalShipping = CustomerOrderMoney(totalShipping.amount, totalShipping.currencyCode),
    totalTax = totalTax?.let { CustomerOrderMoney(it.amount, it.currencyCode) },
    shippingAddress = shippingAddress?.formatted,
    lines = lineItems.nodes.map { line ->
        CustomerOrderLine(
            id = line.id,
            name = line.name,
            variantTitle = line.variantTitle,
            quantity = line.quantity,
            totalPrice = line.totalPrice?.let { CustomerOrderMoney(it.amount, it.currencyCode) }
        )
    },
    fulfillments = fulfillments.nodes.map { fulfillment ->
        CustomerOrderFulfillment(
            id = fulfillment.id,
            createdAt = fulfillment.createdAt,
            estimatedDeliveryAt = fulfillment.estimatedDeliveryAt,
            shipmentStatus = fulfillment.latestShipmentStatus?.rawValue.toShipmentStatus(),
            status = fulfillment.status?.rawValue.toCustomerFulfillmentStatus(),
            lines = fulfillment.fulfillmentLineItems.nodes.map { line ->
                CustomerFulfillmentLine(
                    id = line.id,
                    orderLineId = line.lineItem.id,
                    name = line.lineItem.name,
                    variantTitle = line.lineItem.variantTitle,
                    quantity = line.quantity
                )
            },
            tracking = fulfillment.trackingInformation.map { tracking ->
                CustomerTrackingInformation(tracking.company, tracking.number, tracking.url)
            }
        )
    }
)

private fun String?.toFinancialStatus(): CustomerOrderFinancialStatus = when (this) {
    "AUTHORIZED" -> CustomerOrderFinancialStatus.AUTHORIZED
    "EXPIRED" -> CustomerOrderFinancialStatus.EXPIRED
    "PAID" -> CustomerOrderFinancialStatus.PAID
    "PARTIALLY_PAID" -> CustomerOrderFinancialStatus.PARTIALLY_PAID
    "PARTIALLY_REFUNDED" -> CustomerOrderFinancialStatus.PARTIALLY_REFUNDED
    "PENDING" -> CustomerOrderFinancialStatus.PENDING
    "REFUNDED" -> CustomerOrderFinancialStatus.REFUNDED
    "VOIDED" -> CustomerOrderFinancialStatus.VOIDED
    else -> CustomerOrderFinancialStatus.UNKNOWN
}

private fun String?.toFulfillmentStatus(): CustomerOrderFulfillmentStatus = when (this) {
    "FULFILLED" -> CustomerOrderFulfillmentStatus.FULFILLED
    "IN_PROGRESS" -> CustomerOrderFulfillmentStatus.IN_PROGRESS
    "ON_HOLD" -> CustomerOrderFulfillmentStatus.ON_HOLD
    "OPEN" -> CustomerOrderFulfillmentStatus.OPEN
    "PARTIALLY_FULFILLED" -> CustomerOrderFulfillmentStatus.PARTIALLY_FULFILLED
    "PENDING_FULFILLMENT" -> CustomerOrderFulfillmentStatus.PENDING_FULFILLMENT
    "RESTOCKED" -> CustomerOrderFulfillmentStatus.RESTOCKED
    "SCHEDULED" -> CustomerOrderFulfillmentStatus.SCHEDULED
    "UNFULFILLED" -> CustomerOrderFulfillmentStatus.UNFULFILLED
    else -> CustomerOrderFulfillmentStatus.UNKNOWN
}

private fun String?.toCustomerFulfillmentStatus(): CustomerFulfillmentStatus = when (this) {
    "CANCELLED" -> CustomerFulfillmentStatus.CANCELLED
    "ERROR" -> CustomerFulfillmentStatus.ERROR
    "FAILURE" -> CustomerFulfillmentStatus.FAILURE
    "SUCCESS" -> CustomerFulfillmentStatus.SUCCESS
    "OPEN" -> CustomerFulfillmentStatus.OPEN
    "PENDING" -> CustomerFulfillmentStatus.PENDING
    else -> CustomerFulfillmentStatus.UNKNOWN
}

private fun String?.toShipmentStatus(): CustomerShipmentStatus = when (this) {
    "ATTEMPTED_DELIVERY" -> CustomerShipmentStatus.ATTEMPTED_DELIVERY
    "CARRIER_PICKED_UP" -> CustomerShipmentStatus.CARRIER_PICKED_UP
    "CONFIRMED" -> CustomerShipmentStatus.CONFIRMED
    "DELAYED" -> CustomerShipmentStatus.DELAYED
    "DELIVERED" -> CustomerShipmentStatus.DELIVERED
    "FAILURE" -> CustomerShipmentStatus.FAILURE
    "IN_TRANSIT" -> CustomerShipmentStatus.IN_TRANSIT
    "LABEL_PRINTED" -> CustomerShipmentStatus.LABEL_PRINTED
    "LABEL_PURCHASED" -> CustomerShipmentStatus.LABEL_PURCHASED
    "OUT_FOR_DELIVERY" -> CustomerShipmentStatus.OUT_FOR_DELIVERY
    "PICKED_UP" -> CustomerShipmentStatus.PICKED_UP
    "READY_FOR_PICKUP" -> CustomerShipmentStatus.READY_FOR_PICKUP
    else -> CustomerShipmentStatus.UNKNOWN
}
