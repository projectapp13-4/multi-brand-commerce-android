@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.gurbakir.mobile.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gurbakir.account.CustomerFulfillmentStatus
import com.gurbakir.account.CustomerOrderDetail
import com.gurbakir.account.CustomerOrderFinancialStatus
import com.gurbakir.account.CustomerOrderFulfillment
import com.gurbakir.account.CustomerOrderFulfillmentStatus
import com.gurbakir.account.CustomerOrderLine
import com.gurbakir.account.CustomerOrderMoney
import com.gurbakir.account.CustomerShipmentStatus
import com.gurbakir.account.CustomerTrackingInformation
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.localization.effectiveForegroundLocale
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.centeredDestinationContent
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

data class OrderListActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenOrder: (String) -> Unit,
    val onSupport: () -> Unit
)

@Composable
fun OrderListScreen(state: OrderListUiState, actions: OrderListActions) {
    val firstOrderFocus = remember { FocusRequester() }
    LaunchedEffect(state.addedOrderCount) {
        if (state.addedOrderCount > 0) {
            withFrameNanos { }
            firstOrderFocus.requestFocus()
        }
    }
    DestinationScaffold(
        title = stringResource(R.string.order_list_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(OrderListTestTags.ROOT),
        onNavigateUp = actions.onBack,
        navigateUpTestTag = OrderListTestTags.BACK
    ) { padding ->
        OrderListContent(state, actions, firstOrderFocus, padding)
    }
}

@Composable
private fun OrderListContent(
    state: OrderListUiState,
    actions: OrderListActions,
    firstOrderFocus: FocusRequester,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(720.dp)
                .consumeDestinationInsets(padding)
                .testTag(OrderListTestTags.CONTENT),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.sectionDp.dp,
                vertical = spacing.sectionDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.order_list_heading),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        }
        item { Text(stringResource(R.string.order_privacy_explanation)) }
        if (state.busy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag(OrderListTestTags.PROGRESS)) }
        }
        if (state.loaded && state.orders.isEmpty()) {
            item { OrderEmpty(actions.onSupport) }
        }
        if (state.orders.isNotEmpty()) {
            itemsIndexed(state.orders, key = { _, item -> item.routeId }) { index, item ->
                OrderSummaryCard(
                    content = item,
                    onOpen = { actions.onOpenOrder(item.routeId) },
                    modifier =
                        Modifier.then(if (index == 0) Modifier.focusRequester(firstOrderFocus) else Modifier)
                )
            }
        }
        orderListFooter(state, actions)
    }
}

private fun LazyListScope.orderListFooter(state: OrderListUiState, actions: OrderListActions) {
    if (state.addedOrderCount > 0) {
        item {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.order_list_added,
                        state.addedOrderCount,
                        state.addedOrderCount
                    ),
                modifier =
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(OrderListTestTags.PAGE_ANNOUNCEMENT)
            )
        }
    }
    if (state.failure != null) {
        item { OrderFailureFeedback(state.failure) }
        item {
            OutlinedButton(
                onClick = actions.onRetry,
                modifier = Modifier.fillMaxWidth().testTag(OrderListTestTags.RETRY)
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    } else if (state.canLoadMore) {
        item {
            OutlinedButton(
                onClick = actions.onLoadMore,
                modifier = Modifier.fillMaxWidth().testTag(OrderListTestTags.LOAD_MORE)
            ) {
                Text(stringResource(R.string.order_load_more))
            }
        }
    }
    if (state.loaded || state.failure != null) {
        item {
            TextButton(onClick = actions.onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.order_refresh))
            }
        }
    }
}

@Composable
private fun OrderEmpty(onSupport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(OrderListTestTags.EMPTY)) {
        Column(
            modifier = Modifier.padding(LocalBrandSpacing.current.generousDp.dp),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            Text(stringResource(R.string.order_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.order_empty_message))
            TextButton(
                onClick = onSupport,
                modifier = Modifier.testTag(OrderListTestTags.EMPTY_SUPPORT)
            ) {
                Text(stringResource(R.string.order_support_action))
            }
        }
    }
}

@Composable
private fun OrderSummaryCard(content: OrderSummaryContent, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val order = content.order
    val locale = currentLocale()
    Card(modifier = modifier.fillMaxWidth().testTag(OrderListTestTags.card(content.routeId))) {
        Column(
            modifier = Modifier.padding(LocalBrandSpacing.current.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            Text(order.name, style = MaterialTheme.typography.titleLarge)
            Text(formattedDateOrUnavailable(order.processedAt, locale))
            StatusText(
                label = stringResource(R.string.order_fulfillment_label),
                value = stringResource(order.fulfillmentStatus.labelResource())
            )
            order.financialStatus?.let {
                StatusText(
                    label = stringResource(R.string.order_financial_label),
                    value = stringResource(it.labelResource())
                )
            }
            Text(
                stringResource(
                    R.string.order_total_value,
                    formattedMoneyOrUnavailable(order.totalPrice, locale)
                )
            )
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().testTag(OrderListTestTags.open(content.routeId))
            ) {
                Text(stringResource(R.string.order_open_detail))
            }
        }
    }
}

data class OrderDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenTracking: (String) -> Unit,
    val onSupport: () -> Unit
)

@Composable
fun OrderDetailScreen(state: OrderDetailUiState, actions: OrderDetailActions, isTrackingAllowed: (String) -> Boolean) {
    DestinationScaffold(
        title = stringResource(R.string.order_detail_title),
        level = DestinationLevel.SECONDARY,
        modifier = Modifier.fillMaxSize().testTag(OrderDetailTestTags.ROOT),
        onNavigateUp = actions.onBack,
        navigateUpTestTag = OrderDetailTestTags.BACK
    ) { padding ->
        OrderDetailContent(state, actions, isTrackingAllowed, padding)
    }
}

@Suppress("LongMethod") // Shared scaffold inset consumption keeps this state renderer linear.
@Composable
private fun OrderDetailContent(
    state: OrderDetailUiState,
    actions: OrderDetailActions,
    isTrackingAllowed: (String) -> Boolean,
    padding: PaddingValues
) {
    val spacing = LocalBrandSpacing.current
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .centeredDestinationContent(720.dp)
                .consumeDestinationInsets(padding)
                .testTag(OrderDetailTestTags.CONTENT),
        contentPadding =
            padding.withDestinationSpacing(
                horizontal = spacing.sectionDp.dp,
                vertical = spacing.sectionDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        if (state.phase == OrderDetailPhase.LOADING) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag(OrderDetailTestTags.PROGRESS)) }
        }
        state.order?.let { order ->
            item { OrderHeading(order) }
            item { OrderLines(order.lines) }
            item { OrderTotals(order) }
            order.shippingAddress?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.let { address ->
                item { OrderShippingAddress(address) }
            }
            item { OrderFulfillments(order.fulfillments, actions, isTrackingAllowed) }
        }
        if (state.phase == OrderDetailPhase.UNAVAILABLE) {
            item {
                RecoveryCard(
                    title = stringResource(R.string.order_unavailable_title),
                    message = stringResource(R.string.order_unavailable_message),
                    actions = actions,
                    canRetry = false,
                    testTag = OrderDetailTestTags.UNAVAILABLE
                )
            }
        }
        state.failure?.let { failure ->
            item {
                RecoveryCard(
                    title = stringResource(R.string.order_failure_title),
                    message = stringResource(failure.messageResource()),
                    actions = actions,
                    canRetry = true,
                    testTag = OrderDetailTestTags.FAILURE
                )
            }
        }
        state.trackingFeedback?.let { feedback ->
            item {
                Text(
                    text = stringResource(feedback.messageResource()),
                    modifier =
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(OrderDetailTestTags.TRACKING_FEEDBACK)
                )
            }
        }
    }
}

@Composable
private fun OrderHeading(order: CustomerOrderDetail) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)) {
        Text(
            text = order.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(formattedDateOrUnavailable(order.processedAt, locale))
        StatusText(
            label = stringResource(R.string.order_fulfillment_label),
            value = stringResource(order.fulfillmentStatus.labelResource())
        )
        order.financialStatus?.let {
            StatusText(
                label = stringResource(R.string.order_financial_label),
                value = stringResource(it.labelResource())
            )
        }
        order.cancelledAt?.let {
            Text(
                stringResource(
                    R.string.order_cancelled_value,
                    formattedDateOrUnavailable(it, locale)
                )
            )
        }
    }
}

@Composable
private fun OrderLines(lines: List<CustomerOrderLine>) {
    val locale = currentLocale()
    SectionCard(title = stringResource(R.string.order_items_heading), testTag = OrderDetailTestTags.LINES) {
        lines.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider()
            Text(line.name, style = MaterialTheme.typography.titleMedium)
            line.variantTitle?.takeIf(String::isNotBlank)?.let { Text(it) }
            Text(stringResource(R.string.order_quantity_value, line.quantity))
            line.totalPrice?.let {
                Text(stringResource(R.string.order_line_total_value, formattedMoneyOrUnavailable(it, locale)))
            }
        }
    }
}

@Composable
private fun OrderTotals(order: CustomerOrderDetail) {
    val locale = currentLocale()
    SectionCard(title = stringResource(R.string.order_totals_heading), testTag = OrderDetailTestTags.TOTALS) {
        order.subtotal?.let {
            TotalRow(stringResource(R.string.order_subtotal), formattedMoneyOrUnavailable(it, locale))
        }
        TotalRow(stringResource(R.string.order_shipping), formattedMoneyOrUnavailable(order.totalShipping, locale))
        order.totalTax?.let {
            TotalRow(stringResource(R.string.order_tax), formattedMoneyOrUnavailable(it, locale))
        }
        TotalRow(stringResource(R.string.order_refunded), formattedMoneyOrUnavailable(order.totalRefunded, locale))
        HorizontalDivider()
        TotalRow(stringResource(R.string.order_total), formattedMoneyOrUnavailable(order.totalPrice, locale))
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun OrderShippingAddress(address: List<String>) {
    SectionCard(
        title = stringResource(R.string.order_shipping_address_heading),
        testTag = OrderDetailTestTags.ADDRESS
    ) {
        address.forEach { Text(it) }
    }
}

@Composable
private fun OrderFulfillments(
    fulfillments: List<CustomerOrderFulfillment>,
    actions: OrderDetailActions,
    isTrackingAllowed: (String) -> Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.FULFILLMENTS),
        verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
    ) {
        Text(
            text = stringResource(R.string.order_fulfillments_heading),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        if (fulfillments.isEmpty()) {
            Text(stringResource(R.string.order_fulfillment_unavailable))
            OutlinedButton(onClick = actions.onSupport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.order_support_action))
            }
        } else {
            fulfillments.forEachIndexed { index, fulfillment ->
                FulfillmentCard(index + 1, fulfillment, actions, isTrackingAllowed)
            }
        }
    }
}

@Composable
private fun FulfillmentCard(
    position: Int,
    fulfillment: CustomerOrderFulfillment,
    actions: OrderDetailActions,
    isTrackingAllowed: (String) -> Boolean
) {
    val locale = currentLocale()
    Card(modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.fulfillment(position))) {
        Column(
            modifier = Modifier.padding(LocalBrandSpacing.current.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            Text(
                stringResource(R.string.order_fulfillment_number, position),
                style = MaterialTheme.typography.titleMedium
            )
            fulfillment.shipmentStatus?.let {
                StatusText(
                    label = stringResource(R.string.order_shipment_label),
                    value = stringResource(it.labelResource())
                )
            } ?: fulfillment.status?.let {
                StatusText(
                    label = stringResource(R.string.order_fulfillment_label),
                    value = stringResource(it.labelResource())
                )
            }
            Text(
                stringResource(
                    R.string.order_fulfillment_created,
                    formattedDateOrUnavailable(fulfillment.createdAt, locale)
                )
            )
            fulfillment.estimatedDeliveryAt?.let {
                Text(
                    stringResource(
                        R.string.order_estimated_delivery,
                        formattedDateOrUnavailable(it, locale)
                    )
                )
            }
            fulfillment.lines.forEach { line ->
                Text(
                    stringResource(
                        R.string.order_fulfilled_line,
                        line.name,
                        line.quantity?.toString() ?: stringResource(R.string.order_quantity_unavailable)
                    )
                )
            }
            TrackingSection(fulfillment.tracking, actions, isTrackingAllowed)
        }
    }
}

@Composable
private fun TrackingSection(
    tracking: List<CustomerTrackingInformation>,
    actions: OrderDetailActions,
    isTrackingAllowed: (String) -> Boolean
) {
    Text(
        text = stringResource(R.string.order_tracking_heading),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() }
    )
    if (tracking.isEmpty()) {
        Text(stringResource(R.string.order_tracking_unavailable))
        OutlinedButton(
            onClick = actions.onSupport,
            modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.TRACKING_SUPPORT)
        ) {
            Text(stringResource(R.string.order_support_action))
        }
    } else {
        tracking.forEachIndexed { index, item ->
            item.company?.takeIf(String::isNotBlank)?.let {
                Text(stringResource(R.string.order_carrier_value, it))
            }
            item.number?.takeIf(String::isNotBlank)?.let {
                Text(stringResource(R.string.order_tracking_number_value, it))
            }
            val url = item.url
            if (url != null && isTrackingAllowed(url)) {
                Button(
                    onClick = { actions.onOpenTracking(url) },
                    modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.tracking(index))
                ) {
                    Text(stringResource(R.string.order_tracking_action))
                }
            } else {
                Text(
                    stringResource(
                        if (url == null) {
                            R.string.order_tracking_link_missing
                        } else {
                            R.string.order_tracking_link_rejected
                        }
                    )
                )
                OutlinedButton(
                    onClick = actions.onSupport,
                    modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.TRACKING_SUPPORT)
                ) {
                    Text(stringResource(R.string.order_support_action))
                }
            }
        }
    }
}

@Composable
private fun RecoveryCard(
    title: String,
    message: String,
    actions: OrderDetailActions,
    canRetry: Boolean,
    testTag: String
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Column(
            modifier =
                Modifier.padding(LocalBrandSpacing.current.generousDp.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message)
            if (canRetry) {
                Button(onClick = actions.onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
            }
            OutlinedButton(
                onClick = actions.onSupport,
                modifier = Modifier.fillMaxWidth().testTag(OrderDetailTestTags.RECOVERY_SUPPORT)
            ) {
                Text(stringResource(R.string.order_support_action))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, testTag: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Column(
            modifier = Modifier.padding(LocalBrandSpacing.current.normalDp.dp),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun StatusText(label: String, value: String) {
    Text(stringResource(R.string.order_status_value, label, value))
}

@Composable
private fun OrderFailureFeedback(failure: OrderFailure) {
    Text(
        text = stringResource(failure.messageResource()),
        modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(OrderListTestTags.FAILURE)
    )
}

@Composable
private fun currentLocale(): Locale = effectiveForegroundLocale(LocalConfiguration.current)

@Composable
private fun formattedDateOrUnavailable(value: String, locale: Locale): String =
    formatOrderDate(value, locale) ?: stringResource(R.string.order_date_unavailable)

@Composable
private fun formattedMoneyOrUnavailable(value: CustomerOrderMoney, locale: Locale): String =
    formatOrderMoney(value, locale) ?: stringResource(R.string.order_money_unavailable)

internal fun formatOrderDate(value: String, locale: Locale): String? = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(OffsetDateTime.parse(value))
}.getOrNull()

internal fun formatOrderMoney(value: CustomerOrderMoney, locale: Locale): String? = runCatching {
    val amount = BigDecimal(value.amount)
    val currency = Currency.getInstance(value.currencyCode.uppercase(Locale.ROOT))
    NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }.format(amount)
}.getOrNull()

private fun OrderFailure.messageResource(): Int = when (this) {
    OrderFailure.CONNECTION -> R.string.order_failure_connection
    OrderFailure.SERVICE -> R.string.order_failure_service
}

private fun TrackingFeedback.messageResource(): Int = when (this) {
    TrackingFeedback.OPENED -> R.string.order_tracking_opened
    TrackingFeedback.NO_BROWSER -> R.string.order_tracking_no_browser
    TrackingFeedback.REJECTED -> R.string.order_tracking_rejected
}

private fun CustomerOrderFinancialStatus.labelResource(): Int = when (this) {
    CustomerOrderFinancialStatus.AUTHORIZED -> R.string.order_financial_authorized
    CustomerOrderFinancialStatus.EXPIRED -> R.string.order_financial_expired
    CustomerOrderFinancialStatus.PAID -> R.string.order_financial_paid
    CustomerOrderFinancialStatus.PARTIALLY_PAID -> R.string.order_financial_partially_paid
    CustomerOrderFinancialStatus.PARTIALLY_REFUNDED -> R.string.order_financial_partially_refunded
    CustomerOrderFinancialStatus.PENDING -> R.string.order_financial_pending
    CustomerOrderFinancialStatus.REFUNDED -> R.string.order_financial_refunded
    CustomerOrderFinancialStatus.VOIDED -> R.string.order_financial_voided
    CustomerOrderFinancialStatus.UNKNOWN -> R.string.order_financial_other
}

private fun CustomerOrderFulfillmentStatus.labelResource(): Int = when (this) {
    CustomerOrderFulfillmentStatus.FULFILLED -> R.string.order_fulfillment_fulfilled
    CustomerOrderFulfillmentStatus.IN_PROGRESS -> R.string.order_fulfillment_in_progress
    CustomerOrderFulfillmentStatus.ON_HOLD -> R.string.order_fulfillment_on_hold
    CustomerOrderFulfillmentStatus.OPEN -> R.string.order_fulfillment_open
    CustomerOrderFulfillmentStatus.PARTIALLY_FULFILLED -> R.string.order_fulfillment_partial
    CustomerOrderFulfillmentStatus.PENDING_FULFILLMENT -> R.string.order_fulfillment_pending
    CustomerOrderFulfillmentStatus.RESTOCKED -> R.string.order_fulfillment_restocked
    CustomerOrderFulfillmentStatus.SCHEDULED -> R.string.order_fulfillment_scheduled
    CustomerOrderFulfillmentStatus.UNFULFILLED -> R.string.order_fulfillment_unfulfilled
    CustomerOrderFulfillmentStatus.UNKNOWN -> R.string.order_fulfillment_other
}

private fun CustomerFulfillmentStatus.labelResource(): Int = when (this) {
    CustomerFulfillmentStatus.CANCELLED -> R.string.fulfillment_cancelled
    CustomerFulfillmentStatus.ERROR -> R.string.fulfillment_error
    CustomerFulfillmentStatus.FAILURE -> R.string.fulfillment_failure
    CustomerFulfillmentStatus.SUCCESS -> R.string.fulfillment_success
    CustomerFulfillmentStatus.OPEN -> R.string.fulfillment_open
    CustomerFulfillmentStatus.PENDING -> R.string.fulfillment_pending
    CustomerFulfillmentStatus.UNKNOWN -> R.string.fulfillment_other
}

private fun CustomerShipmentStatus.labelResource(): Int = when (this) {
    CustomerShipmentStatus.ATTEMPTED_DELIVERY -> R.string.shipment_attempted_delivery
    CustomerShipmentStatus.CARRIER_PICKED_UP -> R.string.shipment_carrier_picked_up
    CustomerShipmentStatus.CONFIRMED -> R.string.shipment_confirmed
    CustomerShipmentStatus.DELAYED -> R.string.shipment_delayed
    CustomerShipmentStatus.DELIVERED -> R.string.shipment_delivered
    CustomerShipmentStatus.FAILURE -> R.string.shipment_failure
    CustomerShipmentStatus.IN_TRANSIT -> R.string.shipment_in_transit
    CustomerShipmentStatus.LABEL_PRINTED -> R.string.shipment_label_printed
    CustomerShipmentStatus.LABEL_PURCHASED -> R.string.shipment_label_purchased
    CustomerShipmentStatus.OUT_FOR_DELIVERY -> R.string.shipment_out_for_delivery
    CustomerShipmentStatus.PICKED_UP -> R.string.shipment_picked_up
    CustomerShipmentStatus.READY_FOR_PICKUP -> R.string.shipment_ready_for_pickup
    CustomerShipmentStatus.UNKNOWN -> R.string.shipment_other
}

object OrderListTestTags {
    const val ROOT = "order-list-root"
    const val CONTENT = "order-list-content"
    const val PROGRESS = "order-list-progress"
    const val EMPTY = "order-list-empty"
    const val EMPTY_SUPPORT = "order-list-empty-support"
    const val FAILURE = "order-list-failure"
    const val RETRY = "order-list-retry"
    const val LOAD_MORE = "order-list-load-more"
    const val PAGE_ANNOUNCEMENT = "order-list-page-announcement"
    const val BACK = "order-list-back"

    fun card(routeId: String) = "order-card-${routeId.hashCode()}"

    fun open(routeId: String) = "order-open-${routeId.hashCode()}"
}

object OrderDetailTestTags {
    const val ROOT = "order-detail-root"
    const val CONTENT = "order-detail-content"
    const val PROGRESS = "order-detail-progress"
    const val LINES = "order-detail-lines"
    const val TOTALS = "order-detail-totals"
    const val ADDRESS = "order-detail-address"
    const val FULFILLMENTS = "order-detail-fulfillments"
    const val UNAVAILABLE = "order-detail-unavailable"
    const val FAILURE = "order-detail-failure"
    const val TRACKING_FEEDBACK = "order-detail-tracking-feedback"
    const val TRACKING_SUPPORT = "order-detail-tracking-support"
    const val RECOVERY_SUPPORT = "order-detail-recovery-support"
    const val BACK = "order-detail-back"

    fun fulfillment(position: Int) = "order-detail-fulfillment-$position"

    fun tracking(position: Int) = "order-detail-tracking-$position"
}
