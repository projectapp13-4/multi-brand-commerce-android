package com.gurbakir.storefront

import com.apollographql.apollo.ApolloClient
import com.gurbakir.storefront.graphql.CartByIdQuery
import com.gurbakir.storefront.graphql.CartLinesPageQuery

private const val MAXIMUM_CART_PAGES = 2

internal class StorefrontCartPager(
    private val client: ApolloClient,
    private val callExecutor: ApolloCallExecutor,
    private val mediaPolicy: StorefrontMediaPolicy
) {
    suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> {
        val call = cartId.use { rawCartId -> client.query(CartByIdQuery(rawCartId)) }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success ->
                result.value.cart?.cartSnapshotFields?.toMappedCart(emptySet(), mediaPolicy)?.let { snapshot ->
                    when (snapshot) {
                        is StorefrontResult.Failure -> snapshot
                        is StorefrontResult.Success -> loadRemainingCartLines(cartId, snapshot.value)
                    }
                } ?: StorefrontResult.Failure(
                    StorefrontFailure.InvalidCart(InvalidCartReason.NOT_FOUND)
                )
        }
    }

    suspend fun completeMutationCart(mapped: StorefrontResult<MappedCartSnapshot>): StorefrontResult<CartReference> =
        when (mapped) {
            is StorefrontResult.Failure -> mapped

            is StorefrontResult.Success -> {
                val snapshot = mapped.value
                if (!snapshot.cart.hasMoreLines) {
                    StorefrontResult.Success(snapshot.cart)
                } else {
                    when (val complete = loadCart(snapshot.cart.id)) {
                        is StorefrontResult.Failure -> complete

                        is StorefrontResult.Success ->
                            StorefrontResult.Success(
                                complete.value.copy(
                                    warningCodes = complete.value.warningCodes + snapshot.cart.warningCodes
                                )
                            )
                    }
                }
            }
        }

    private suspend fun loadRemainingCartLines(
        cartId: SensitiveCartId,
        snapshot: MappedCartSnapshot
    ): StorefrontResult<CartReference> {
        var cart = snapshot.cart
        var cursor = snapshot.endCursor
        var pageCount = 1
        var outcome: StorefrontResult<CartReference>? = null
        while (cart.hasMoreLines && outcome == null) {
            when (val pageResult = loadNextPage(cartId, cart, cursor, pageCount)) {
                is StorefrontResult.Failure -> outcome = pageResult

                is StorefrontResult.Success -> {
                    cart = pageResult.value.cart
                    cursor = pageResult.value.endCursor
                    pageCount += 1
                }
            }
        }
        return outcome ?: cart.completePageResult()
    }

    private suspend fun loadNextPage(
        cartId: SensitiveCartId,
        cart: CartReference,
        cursor: Cursor?,
        pageCount: Int
    ): StorefrontResult<CartPageProgress> = when {
        pageCount >= MAXIMUM_CART_PAGES -> graphQlFailure("CART_LINE_PAGE_LIMIT")

        cursor == null -> graphQlFailure("MISSING_CART_LINE_CURSOR")

        else ->
            when (val pageResult = loadPage(cartId, cursor)) {
                is StorefrontResult.Failure -> pageResult
                is StorefrontResult.Success -> cart.merge(pageResult.value)
            }
    }

    private suspend fun loadPage(cartId: SensitiveCartId, cursor: Cursor): StorefrontResult<CartLinePage> {
        val call = cartId.use { rawCartId -> client.query(CartLinesPageQuery(rawCartId, cursor.value)) }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val page = result.value.cart?.lines
                val mappedLines =
                    page?.nodes.orEmpty().mapNotNull { it.cartLineFields.toDomainLine(mediaPolicy) }
                when {
                    page == null ->
                        StorefrontResult.Failure(StorefrontFailure.InvalidCart(InvalidCartReason.NOT_FOUND))

                    mappedLines.size != page.nodes.size -> graphQlFailure("UNSUPPORTED_CART_LINE_PAGE")

                    page.pageInfo.hasNextPage && page.pageInfo.endCursor == null ->
                        graphQlFailure("MISSING_CART_LINE_CURSOR")

                    else ->
                        StorefrontResult.Success(
                            CartLinePage(
                                lines = mappedLines,
                                hasNextPage = page.pageInfo.hasNextPage,
                                endCursor = page.pageInfo.endCursor?.let(::Cursor)
                            )
                        )
                }
            }
        }
    }
}

private data class CartLinePage(val lines: List<CartLineSummary>, val hasNextPage: Boolean, val endCursor: Cursor?)

private data class CartPageProgress(val cart: CartReference, val endCursor: Cursor?)

private fun CartReference.merge(page: CartLinePage): StorefrontResult<CartPageProgress> {
    val combined = lines + page.lines
    val cartCurrency = total?.currencyCode
    return when {
        cartCurrency == null -> graphQlFailure("INVALID_CART_MONEY")

        page.lines.any { !it.hasCartCurrency(cartCurrency) } -> graphQlFailure("CART_CURRENCY_MISMATCH")

        combined.map(CartLineSummary::id).distinct().size != combined.size ->
            graphQlFailure("DUPLICATE_CART_LINE_PAGE")

        else ->
            StorefrontResult.Success(
                CartPageProgress(
                    cart = copy(lines = combined, hasMoreLines = page.hasNextPage),
                    endCursor = page.endCursor
                )
            )
    }
}

private fun CartReference.completePageResult(): StorefrontResult<CartReference> =
    if (lines.sumOf(CartLineSummary::quantity) == totalQuantity) {
        StorefrontResult.Success(this)
    } else {
        graphQlFailure("CART_CHANGED_DURING_PAGING")
    }
