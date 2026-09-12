package com.gurbakir.account

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.gurbakir.account.graphql.CustomerIdentityQuery
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountDiscoveryFailure
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerSessionResolution
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500
private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 45_000L

data class CustomerIdentity(val id: String, val displayName: String)

sealed interface CustomerAccountFailure {
    data object SignedOut : CustomerAccountFailure

    data class Authentication(val reason: CustomerTokenFailure) : CustomerAccountFailure

    data class Discovery(val reason: CustomerAccountDiscoveryFailure) : CustomerAccountFailure

    data class GraphQl(val errorCodes: Set<String>) : CustomerAccountFailure

    data class Transport(val retryable: Boolean) : CustomerAccountFailure
}

sealed interface CustomerAccountResult<out T> {
    data class Success<T>(val value: T) : CustomerAccountResult<T>

    data class Failure(val reason: CustomerAccountFailure) : CustomerAccountResult<Nothing>
}

fun interface CustomerSessionResolver {
    suspend fun resolve(): CustomerSessionResolution
}

interface CustomerAccountGateway {
    suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity>
}

class UnconfiguredCustomerAccountGateway : CustomerAccountGateway {
    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> = CustomerAccountResult.Failure(
        CustomerAccountFailure.Authentication(CustomerTokenFailure.InvalidResponse)
    )
}

object CustomerAccountApolloClientFactory {
    fun createGateway(
        discoveryClient: CustomerAccountDiscoveryClient,
        sessionResolver: CustomerSessionResolver
    ): CustomerAccountGateway = DiscoveringCustomerAccountGateway(discoveryClient, sessionResolver)

    internal fun createClient(configuration: CustomerAccountConfiguration): ApolloClient {
        require(configuration.validationIssues().isEmpty()) {
            "Customer Account configuration must be valid before creating a network client."
        }
        return ApolloClient.Builder().serverUrl(configuration.graphqlEndpoint).build()
    }

    internal fun createClient(graphqlEndpoint: String): ApolloClient =
        ApolloClient.Builder().serverUrl(graphqlEndpoint).build()
}

private class DiscoveringCustomerAccountGateway(
    private val discoveryClient: CustomerAccountDiscoveryClient,
    private val sessionResolver: CustomerSessionResolver
) : CustomerAccountGateway {
    private val lock = Mutex()
    private var delegate: CustomerAccountGateway? = null

    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> {
        val gateway = lock.withLock {
            delegate?.let { return@withLock it }
            when (val discovery = discoveryClient.discover()) {
                is CustomerAccountDiscoveryResult.Failure ->
                    return CustomerAccountResult.Failure(
                        CustomerAccountFailure.Discovery(discovery.reason)
                    )

                is CustomerAccountDiscoveryResult.Success ->
                    ApolloCustomerAccountGateway(
                        CustomerAccountApolloClientFactory.createClient(
                            discovery.configuration.graphqlEndpoint
                        ),
                        sessionResolver
                    ).also { delegate = it }
            }
        }
        return gateway.loadIdentity()
    }
}

class ApolloCustomerAccountGateway(
    private val client: ApolloClient,
    private val sessionResolver: CustomerSessionResolver,
    requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS
) : CustomerAccountGateway {
    private val callExecutor = CustomerApolloCallExecutor(requestTimeoutMillis)

    override suspend fun loadIdentity(): CustomerAccountResult<CustomerIdentity> = when (
        val session = sessionResolver.resolve()
    ) {
        CustomerSessionResolution.SignedOut -> CustomerAccountResult.Failure(CustomerAccountFailure.SignedOut)

        is CustomerSessionResolution.Failed ->
            CustomerAccountResult.Failure(CustomerAccountFailure.Authentication(session.reason))

        is CustomerSessionResolution.Authenticated -> {
            val call = session.session.accessToken.use { token ->
                client.query(CustomerIdentityQuery()).addHttpHeader("Authorization", token)
            }
            when (val result = callExecutor.execute(call)) {
                is CustomerAccountResult.Failure -> result

                is CustomerAccountResult.Success ->
                    CustomerAccountResult.Success(
                        CustomerIdentity(
                            id = result.value.customer.id,
                            displayName = result.value.customer.displayName
                        )
                    )
            }
        }
    }
}

internal class CustomerApolloCallExecutor(private val requestTimeoutMillis: Long) {
    init {
        require(requestTimeoutMillis > 0)
    }

    suspend fun <D : Operation.Data> execute(call: ApolloCall<D>): CustomerAccountResult<D> {
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
                CustomerAccountResult.Failure((exception as? ApolloException).toCustomerAccountFailure())
            }
        )
    }

    private fun <D : Operation.Data> mapResponse(response: ApolloResponse<D>?): CustomerAccountResult<D> {
        val errors = response?.errors.orEmpty()
        return when {
            response == null -> transportFailure(retryable = true)

            response.exception != null ->
                CustomerAccountResult.Failure(response.exception.toCustomerAccountFailure())

            errors.isNotEmpty() ->
                CustomerAccountResult.Failure(
                    CustomerAccountFailure.GraphQl(
                        errors.map { error ->
                            error.extensions?.get("code") as? String ?: "UNCLASSIFIED_GRAPHQL_ERROR"
                        }.toSet()
                    )
                )

            response.data == null ->
                CustomerAccountResult.Failure(CustomerAccountFailure.GraphQl(setOf("MISSING_GRAPHQL_DATA")))

            else -> CustomerAccountResult.Success(response.data!!)
        }
    }

    private fun <D : Operation.Data> transportFailure(retryable: Boolean): CustomerAccountResult<D> =
        CustomerAccountResult.Failure(CustomerAccountFailure.Transport(retryable))
}

private fun ApolloException.isRetryableTransportFailure(): Boolean = when (this) {
    is ApolloNetworkException -> true

    is ApolloHttpException ->
        statusCode == HTTP_REQUEST_TIMEOUT ||
            statusCode == HTTP_TOO_MANY_REQUESTS ||
            statusCode >= HTTP_SERVER_ERROR_START

    else -> false
}

private fun ApolloException?.toCustomerAccountFailure(): CustomerAccountFailure =
    if (this is ApolloHttpException && statusCode in setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)) {
        CustomerAccountFailure.Authentication(CustomerTokenFailure.Rejected)
    } else {
        CustomerAccountFailure.Transport(retryable = this?.isRetryableTransportFailure() == true)
    }
