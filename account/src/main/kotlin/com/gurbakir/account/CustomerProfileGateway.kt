package com.gurbakir.account

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.gurbakir.account.graphql.CustomerProfileQuery
import com.gurbakir.account.graphql.CustomerProfileUpdateMutation
import com.gurbakir.account.graphql.type.CustomerUpdateInput
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerSessionResolution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_PROFILE_REQUEST_TIMEOUT_MILLIS = 45_000L
private const val MISSING_PROFILE_DATA = "MISSING_PROFILE_DATA"
private const val MISSING_PROFILE_UPDATE_PAYLOAD = "MISSING_PROFILE_UPDATE_PAYLOAD"

data class CustomerProfile(val firstName: String?, val lastName: String?) {
    override fun toString(): String = "CustomerProfile(<redacted>)"
}

data class CustomerProfileUpdate(val firstName: String?, val lastName: String?) {
    override fun toString(): String = "CustomerProfileUpdate(<redacted>)"
}

enum class CustomerProfileField {
    FIRST_NAME,
    LAST_NAME,
    FORM
}

sealed interface CustomerProfileUpdateResult {
    data class Success(val profile: CustomerProfile) : CustomerProfileUpdateResult

    data class Rejected(val fields: Set<CustomerProfileField>) : CustomerProfileUpdateResult

    data class Failure(val reason: CustomerAccountFailure) : CustomerProfileUpdateResult
}

interface CustomerProfileGateway {
    suspend fun loadProfile(): CustomerAccountResult<CustomerProfile>

    suspend fun updateProfile(update: CustomerProfileUpdate): CustomerProfileUpdateResult
}

class UnconfiguredCustomerProfileGateway : CustomerProfileGateway {
    override suspend fun loadProfile(): CustomerAccountResult<CustomerProfile> = CustomerAccountResult.Failure(
        CustomerAccountFailure.Authentication(CustomerTokenFailure.InvalidResponse)
    )

    override suspend fun updateProfile(update: CustomerProfileUpdate): CustomerProfileUpdateResult =
        CustomerProfileUpdateResult.Failure(
            CustomerAccountFailure.Authentication(CustomerTokenFailure.InvalidResponse)
        )
}

object CustomerProfileApolloClientFactory {
    fun createGateway(
        discoveryClient: CustomerAccountDiscoveryClient,
        sessionResolver: CustomerSessionResolver
    ): CustomerProfileGateway = DiscoveringCustomerProfileGateway(discoveryClient, sessionResolver)
}

private class DiscoveringCustomerProfileGateway(
    private val discoveryClient: CustomerAccountDiscoveryClient,
    private val sessionResolver: CustomerSessionResolver
) : CustomerProfileGateway {
    private val lock = Mutex()
    private var delegate: CustomerProfileGateway? = null

    override suspend fun loadProfile(): CustomerAccountResult<CustomerProfile> =
        when (val resolution = resolveDelegate()) {
            is ProfileGatewayResolution.Ready -> resolution.gateway.loadProfile()
            is ProfileGatewayResolution.Failed -> CustomerAccountResult.Failure(resolution.reason)
        }

    override suspend fun updateProfile(update: CustomerProfileUpdate): CustomerProfileUpdateResult =
        when (val resolution = resolveDelegate()) {
            is ProfileGatewayResolution.Ready -> resolution.gateway.updateProfile(update)
            is ProfileGatewayResolution.Failed -> CustomerProfileUpdateResult.Failure(resolution.reason)
        }

    private suspend fun resolveDelegate(): ProfileGatewayResolution = lock.withLock {
        delegate?.let { return@withLock ProfileGatewayResolution.Ready(it) }
        when (val discovery = discoveryClient.discover()) {
            is CustomerAccountDiscoveryResult.Failure ->
                ProfileGatewayResolution.Failed(CustomerAccountFailure.Discovery(discovery.reason))

            is CustomerAccountDiscoveryResult.Success ->
                ApolloCustomerProfileGateway(
                    CustomerAccountApolloClientFactory.createClient(
                        discovery.configuration.graphqlEndpoint
                    ),
                    sessionResolver
                ).also { delegate = it }.let(ProfileGatewayResolution::Ready)
        }
    }
}

private sealed interface ProfileGatewayResolution {
    data class Ready(val gateway: CustomerProfileGateway) : ProfileGatewayResolution

    data class Failed(val reason: CustomerAccountFailure) : ProfileGatewayResolution
}

class ApolloCustomerProfileGateway(
    private val client: ApolloClient,
    private val sessionResolver: CustomerSessionResolver,
    requestTimeoutMillis: Long = DEFAULT_PROFILE_REQUEST_TIMEOUT_MILLIS
) : CustomerProfileGateway {
    private val callExecutor = CustomerApolloCallExecutor(requestTimeoutMillis)

    override suspend fun loadProfile(): CustomerAccountResult<CustomerProfile> =
        when (val result = executeAuthenticated { client.query(CustomerProfileQuery()) }) {
            is CustomerAccountResult.Failure -> result

            is CustomerAccountResult.Success ->
                CustomerAccountResult.Success(
                    CustomerProfile(
                        firstName = result.value.customer.firstName,
                        lastName = result.value.customer.lastName
                    )
                )
        }

    override suspend fun updateProfile(update: CustomerProfileUpdate): CustomerProfileUpdateResult {
        val input =
            CustomerUpdateInput(
                firstName = Optional.present(update.firstName),
                lastName = Optional.present(update.lastName)
            )
        return when (
            val result = executeAuthenticated {
                client.mutation(CustomerProfileUpdateMutation(input))
            }
        ) {
            is CustomerAccountResult.Failure -> CustomerProfileUpdateResult.Failure(result.reason)
            is CustomerAccountResult.Success -> result.value.toProfileUpdateResult()
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

private fun CustomerProfileUpdateMutation.Data.toProfileUpdateResult(): CustomerProfileUpdateResult {
    val payload = customerUpdate
    val rejectedFields =
        payload?.userErrors.orEmpty().mapTo(mutableSetOf()) { error ->
            when (error.field?.lastOrNull()) {
                "firstName" -> CustomerProfileField.FIRST_NAME
                "lastName" -> CustomerProfileField.LAST_NAME
                else -> CustomerProfileField.FORM
            }
        }
    return when {
        payload == null ->
            CustomerProfileUpdateResult.Failure(
                CustomerAccountFailure.GraphQl(setOf(MISSING_PROFILE_UPDATE_PAYLOAD))
            )

        rejectedFields.isNotEmpty() -> CustomerProfileUpdateResult.Rejected(rejectedFields)

        payload.customer == null ->
            CustomerProfileUpdateResult.Failure(
                CustomerAccountFailure.GraphQl(setOf(MISSING_PROFILE_DATA))
            )

        else ->
            CustomerProfileUpdateResult.Success(
                CustomerProfile(payload.customer.firstName, payload.customer.lastName)
            )
    }
}
