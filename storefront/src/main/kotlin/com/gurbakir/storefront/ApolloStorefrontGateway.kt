package com.gurbakir.storefront

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.gurbakir.foundation.config.StorefrontConfiguration
import com.gurbakir.storefront.graphql.CartBuyerIdentityUpdateMutation
import com.gurbakir.storefront.graphql.CartCreateMutation
import com.gurbakir.storefront.graphql.CartLinesAddMutation
import com.gurbakir.storefront.graphql.CartLinesRemoveMutation
import com.gurbakir.storefront.graphql.CartLinesUpdateMutation
import com.gurbakir.storefront.graphql.CatalogPageQuery
import com.gurbakir.storefront.graphql.ShopSummaryQuery
import com.gurbakir.storefront.graphql.fragment.HomeImageFields
import com.gurbakir.storefront.graphql.type.CartBuyerIdentityInput
import com.gurbakir.storefront.graphql.type.CartInput
import com.gurbakir.storefront.graphql.type.CartLineInput as ApolloCartLineInput
import com.gurbakir.storefront.graphql.type.CartLineUpdateInput as ApolloCartLineUpdateInput
import java.util.concurrent.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500
internal const val DEFAULT_PAGE_SIZE = 20
internal const val MAX_PAGE_SIZE = 50
internal const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 45_000L

object StorefrontApolloClientFactory {
    fun createGateways(
        configuration: StorefrontConfiguration,
        mediaPolicy: StorefrontMediaPolicy
    ): StorefrontGatewaySet {
        val client = createClient(configuration)
        return StorefrontGatewaySet(
            api = ApolloStorefrontGateway(client, mediaPolicy),
            catalog = ApolloStorefrontCatalogGateway(client, mediaPolicy),
            search = ApolloStorefrontSearchGateway(client, mediaPolicy),
            product = ApolloStorefrontProductGateway(client, mediaPolicy)
        )
    }

    internal fun createClient(configuration: StorefrontConfiguration): ApolloClient {
        require(configuration.validationIssues().isEmpty()) {
            "Storefront configuration must be valid before creating a network client."
        }

        val endpoint = "https://${configuration.domain}/api/${configuration.apiVersion}/graphql.json"
        return configuration.publicToken.use { token ->
            ApolloClient.Builder()
                .serverUrl(endpoint)
                .addHttpHeader("X-Shopify-Storefront-Access-Token", token)
                .build()
        }
    }
}

class ApolloStorefrontGateway(
    private val client: ApolloClient,
    private val mediaPolicy: StorefrontMediaPolicy,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : StorefrontApi,
    StorefrontHomeGateway by ApolloStorefrontHomeGateway(client, mediaPolicy, requestTimeoutMillis) {
    private val callExecutor = ApolloCallExecutor(requestTimeoutMillis)
    private val cartPager = StorefrontCartPager(client, callExecutor, mediaPolicy)

    init {
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be between 1 and $MAX_PAGE_SIZE" }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
    }

    override suspend fun loadShopSummary(): StorefrontResult<ShopSummary> =
        when (val result = callExecutor.execute(client.query(ShopSummaryQuery()))) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success ->
                StorefrontResult.Success(
                    ShopSummary(
                        name = result.value.shop.name,
                        primaryDomain = result.value.shop.primaryDomain.host
                    )
                )
        }

    override suspend fun loadCatalogPage(after: Cursor?): StorefrontResult<CatalogPage> = when (
        val result =
            callExecutor.execute(
                client.query(
                    CatalogPageQuery(
                        first = pageSize,
                        after = after?.let { Optional.present(it.value) } ?: Optional.Absent
                    )
                )
            )
    ) {
        is StorefrontResult.Failure -> result

        is StorefrontResult.Success -> {
            val products =
                result.value.products.nodes.map { product ->
                    ProductSummary(
                        id = product.id,
                        handle = product.handle,
                        title = product.title,
                        primaryImage = product.featuredImage?.url?.toApprovedMediaUri(mediaPolicy),
                        variants =
                            product.variants.nodes.map { variant ->
                                ProductVariantSummary(
                                    id = variant.id,
                                    title = variant.title,
                                    availableForSale = variant.availableForSale
                                )
                            }
                    )
                }
            StorefrontResult.Success(
                CatalogPage(
                    products = products,
                    endCursor = result.value.products.pageInfo.endCursor?.let(::Cursor),
                    hasNextPage = result.value.products.pageInfo.hasNextPage
                )
            )
        }
    }

    override suspend fun createCart(
        lines: List<CartLineInput>,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> {
        validateCreateLines(lines)?.let { return StorefrontResult.Failure(it) }
        val input =
            CartInput(
                lines =
                    Optional.present(
                        lines.map { line ->
                            ApolloCartLineInput(
                                quantity = Optional.present(line.quantity),
                                merchandiseId = line.merchandiseId
                            )
                        }
                    ),
                buyerIdentity =
                    buyerAccessToken?.use { token ->
                        Optional.present(
                            CartBuyerIdentityInput(customerAccessToken = Optional.present(token))
                        )
                    } ?: Optional.Absent
            )

        return when (val result = callExecutor.execute(client.mutation(CartCreateMutation(input)))) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val payload = result.value.cartCreate
                val userErrors = payload?.userErrors.orEmpty().toCreateProjectErrors()
                when {
                    userErrors.isNotEmpty() -> userErrors.toCartFailure()

                    payload?.cart == null -> graphQlFailure("MISSING_CART_CREATE_PAYLOAD")

                    else ->
                        cartPager.completeMutationCart(
                            payload.cart.cartSnapshotFields.toMappedCart(
                                payload.warnings.mapTo(mutableSetOf()) { it.code.rawValue },
                                mediaPolicy
                            )
                        )
                }
            }
        }
    }

    override suspend fun loadCart(cartId: SensitiveCartId): StorefrontResult<CartReference> = cartPager.loadCart(cartId)

    override suspend fun addCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineInput>
    ): StorefrontResult<CartReference> {
        validateCreateLines(lines)?.let { return StorefrontResult.Failure(it) }
        val inputs =
            lines.map { line ->
                ApolloCartLineInput(
                    quantity = Optional.present(line.quantity),
                    merchandiseId = line.merchandiseId
                )
            }
        val call = cartId.use { rawCartId -> client.mutation(CartLinesAddMutation(rawCartId, inputs)) }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val payload = result.value.cartLinesAdd
                val userErrors = payload?.userErrors.orEmpty().toAddProjectErrors()
                when {
                    userErrors.isNotEmpty() -> userErrors.toCartFailure()

                    payload?.cart == null -> graphQlFailure("MISSING_CART_ADD_PAYLOAD")

                    else ->
                        cartPager.completeMutationCart(
                            payload.cart.cartSnapshotFields.toMappedCart(
                                payload.warnings.mapTo(mutableSetOf()) { it.code.rawValue },
                                mediaPolicy
                            )
                        )
                }
            }
        }
    }

    override suspend fun updateCartLines(
        cartId: SensitiveCartId,
        lines: List<CartLineUpdate>
    ): StorefrontResult<CartReference> {
        validateUpdateLines(lines)?.let { return StorefrontResult.Failure(it) }
        val inputs =
            lines.map { line ->
                ApolloCartLineUpdateInput(
                    id = Optional.present(line.lineId.use { it }),
                    quantity = Optional.present(line.quantity)
                )
            }

        val call = cartId.use { rawCartId -> client.mutation(CartLinesUpdateMutation(rawCartId, inputs)) }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val payload = result.value.cartLinesUpdate
                val userErrors = payload?.userErrors.orEmpty().toUpdateProjectErrors()
                when {
                    userErrors.isNotEmpty() -> userErrors.toCartFailure()

                    payload?.cart == null -> graphQlFailure("MISSING_CART_UPDATE_PAYLOAD")

                    else ->
                        cartPager.completeMutationCart(
                            payload.cart.cartSnapshotFields.toMappedCart(
                                payload.warnings.mapTo(mutableSetOf()) { it.code.rawValue },
                                mediaPolicy
                            )
                        )
                }
            }
        }
    }

    override suspend fun removeCartLines(
        cartId: SensitiveCartId,
        lineIds: List<SensitiveCartLineId>
    ): StorefrontResult<CartReference> {
        validateRemoveLines(lineIds)?.let { return StorefrontResult.Failure(it) }
        val rawLineIds = lineIds.map { lineId -> lineId.use { it } }
        val call = cartId.use { rawCartId ->
            client.mutation(CartLinesRemoveMutation(rawCartId, Optional.present(rawLineIds)))
        }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val payload = result.value.cartLinesRemove
                val userErrors = payload?.userErrors.orEmpty().toRemoveProjectErrors()
                when {
                    userErrors.isNotEmpty() -> userErrors.toCartFailure()

                    payload?.cart == null -> graphQlFailure("MISSING_CART_REMOVE_PAYLOAD")

                    else ->
                        cartPager.completeMutationCart(
                            payload.cart.cartSnapshotFields.toMappedCart(
                                payload.warnings.mapTo(mutableSetOf()) { it.code.rawValue },
                                mediaPolicy
                            )
                        )
                }
            }
        }
    }

    override suspend fun updateBuyerIdentity(
        cartId: SensitiveCartId,
        buyerAccessToken: SensitiveBuyerAccessToken?
    ): StorefrontResult<CartReference> {
        val buyerIdentity =
            buyerAccessToken?.use { token ->
                CartBuyerIdentityInput(customerAccessToken = Optional.present(token))
            } ?: CartBuyerIdentityInput()
        val call = cartId.use { rawCartId ->
            client.mutation(CartBuyerIdentityUpdateMutation(rawCartId, buyerIdentity))
        }
        return when (val result = callExecutor.execute(call)) {
            is StorefrontResult.Failure -> result

            is StorefrontResult.Success -> {
                val payload = result.value.cartBuyerIdentityUpdate
                val userErrors = payload?.userErrors.orEmpty().toBuyerIdentityProjectErrors()
                when {
                    userErrors.isNotEmpty() -> userErrors.toCartFailure()

                    payload?.cart == null -> graphQlFailure("MISSING_BUYER_IDENTITY_PAYLOAD")

                    else ->
                        cartPager.completeMutationCart(
                            payload.cart.cartSnapshotFields.toMappedCart(
                                payload.warnings.mapTo(mutableSetOf()) { it.code.rawValue },
                                mediaPolicy
                            )
                        )
                }
            }
        }
    }
}

internal fun HomeImageFields.toStorefrontMedia(mediaPolicy: StorefrontMediaPolicy): StorefrontMedia? {
    val uri = runCatching { java.net.URI(url) }.getOrNull()?.takeIf(mediaPolicy::accepts) ?: return null
    return StorefrontMedia(
        uri = uri,
        altText = altText?.trim()?.takeIf(String::isNotEmpty),
        width = width?.takeIf { it > 0 },
        height = height?.takeIf { it > 0 }
    )
}

internal class ApolloCallExecutor(private val requestTimeoutMillis: Long) {
    suspend fun <D : Operation.Data> execute(call: ApolloCall<D>): StorefrontResult<D> {
        val result =
            try {
                Result.success(withTimeoutOrNull(requestTimeoutMillis) { call.execute() })
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApolloException) {
                Result.failure(exception)
            }

        return result.fold(
            onSuccess = ::mapResponse,
            onFailure = { exception ->
                StorefrontResult.Failure(
                    StorefrontFailure.Transport(
                        retryable = (exception as? ApolloException)?.isRetryableTransportFailure() == true
                    )
                )
            }
        )
    }

    private fun <D : Operation.Data> mapResponse(response: ApolloResponse<D>?): StorefrontResult<D> {
        val errors = response?.errors.orEmpty()
        return when {
            response == null -> transportFailure(retryable = true)

            response.exception != null ->
                transportFailure(retryable = response.exception!!.isRetryableTransportFailure())

            errors.isNotEmpty() ->
                StorefrontResult.Failure(
                    StorefrontFailure.GraphQl(
                        errors.map { error ->
                            error.extensions?.get("code") as? String ?: "UNCLASSIFIED_GRAPHQL_ERROR"
                        }.toSet()
                    )
                )

            response.data == null -> graphQlFailure("MISSING_GRAPHQL_DATA")

            else -> StorefrontResult.Success(response.data!!)
        }
    }

    private fun <D : Operation.Data> transportFailure(retryable: Boolean): StorefrontResult<D> =
        StorefrontResult.Failure(StorefrontFailure.Transport(retryable))
}

private fun ApolloException.isRetryableTransportFailure(): Boolean = when (this) {
    is ApolloNetworkException -> true

    is ApolloHttpException ->
        statusCode == HTTP_REQUEST_TIMEOUT ||
            statusCode == HTTP_TOO_MANY_REQUESTS ||
            statusCode >= HTTP_SERVER_ERROR_START

    else -> false
}
