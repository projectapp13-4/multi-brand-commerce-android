package com.gurbakir.account

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.gurbakir.account.graphql.CustomerAddressCreateMutation
import com.gurbakir.account.graphql.CustomerAddressDeleteMutation
import com.gurbakir.account.graphql.CustomerAddressSetDefaultMutation
import com.gurbakir.account.graphql.CustomerAddressUpdateMutation
import com.gurbakir.account.graphql.CustomerAddressesQuery
import com.gurbakir.account.graphql.type.CustomerAddressInput
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerSessionResolution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_ADDRESS_REQUEST_TIMEOUT_MILLIS = 45_000L
private const val ADDRESS_PAGE_SIZE = 50
private const val MISSING_ADDRESS_PAYLOAD = "MISSING_ADDRESS_PAYLOAD"
private const val MISSING_ADDRESS_DATA = "MISSING_ADDRESS_DATA"
private const val INVALID_ADDRESS_PAGE = "INVALID_ADDRESS_PAGE"

data class CustomerAddress(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val zip: String?,
    val phoneNumber: String?,
    val territoryCode: String?,
    val zoneCode: String?,
    val formatted: List<String>,
    val isDefault: Boolean
) {
    override fun toString(): String = "CustomerAddress(id=<redacted>, fields=<redacted>, isDefault=$isDefault)"
}

data class CustomerAddressDraft(
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val zip: String?,
    val phoneNumber: String?,
    val territoryCode: String,
    val zoneCode: String? = null
) {
    override fun toString(): String = "CustomerAddressDraft(<redacted>)"
}

data class CustomerAddressPage(val addresses: List<CustomerAddress>, val nextCursor: String?) {
    override fun toString(): String = "CustomerAddressPage(count=${addresses.size}, hasNext=${nextCursor != null})"
}

enum class CustomerAddressField {
    FIRST_NAME,
    LAST_NAME,
    COMPANY,
    ADDRESS1,
    ADDRESS2,
    CITY,
    ZIP,
    PHONE,
    COUNTRY,
    FORM
}

enum class CustomerAddressIssue {
    DEFAULT_ADDRESS_PROTECTED,
    DUPLICATE,
    NOT_FOUND,
    INVALID_COUNTRY,
    UNKNOWN
}

sealed interface CustomerAddressMutationResult {
    data class Success(val addressId: String) : CustomerAddressMutationResult

    data class Rejected(val fields: Set<CustomerAddressField>, val issues: Set<CustomerAddressIssue>) :
        CustomerAddressMutationResult

    data class Failure(val reason: CustomerAccountFailure) : CustomerAddressMutationResult
}

interface CustomerAddressGateway {
    suspend fun loadAddresses(after: String? = null): CustomerAccountResult<CustomerAddressPage>

    suspend fun createAddress(address: CustomerAddressDraft, makeDefault: Boolean): CustomerAddressMutationResult

    suspend fun updateAddress(addressId: String, address: CustomerAddressDraft): CustomerAddressMutationResult

    suspend fun setDefaultAddress(addressId: String): CustomerAddressMutationResult

    suspend fun deleteAddress(addressId: String): CustomerAddressMutationResult
}

class UnconfiguredCustomerAddressGateway : CustomerAddressGateway {
    private val failure = CustomerAccountFailure.Authentication(CustomerTokenFailure.InvalidResponse)

    override suspend fun loadAddresses(after: String?): CustomerAccountResult<CustomerAddressPage> =
        CustomerAccountResult.Failure(failure)

    override suspend fun createAddress(
        address: CustomerAddressDraft,
        makeDefault: Boolean
    ): CustomerAddressMutationResult = CustomerAddressMutationResult.Failure(failure)

    override suspend fun updateAddress(
        addressId: String,
        address: CustomerAddressDraft
    ): CustomerAddressMutationResult = CustomerAddressMutationResult.Failure(failure)

    override suspend fun setDefaultAddress(addressId: String): CustomerAddressMutationResult =
        CustomerAddressMutationResult.Failure(failure)

    override suspend fun deleteAddress(addressId: String): CustomerAddressMutationResult =
        CustomerAddressMutationResult.Failure(failure)
}

object CustomerAddressApolloClientFactory {
    fun createGateway(
        discoveryClient: CustomerAccountDiscoveryClient,
        sessionResolver: CustomerSessionResolver
    ): CustomerAddressGateway = DiscoveringCustomerAddressGateway(discoveryClient, sessionResolver)
}

private class DiscoveringCustomerAddressGateway(
    private val discoveryClient: CustomerAccountDiscoveryClient,
    private val sessionResolver: CustomerSessionResolver
) : CustomerAddressGateway {
    private val lock = Mutex()
    private var delegate: CustomerAddressGateway? = null

    override suspend fun loadAddresses(after: String?): CustomerAccountResult<CustomerAddressPage> =
        when (val resolution = resolveDelegate()) {
            is AddressGatewayResolution.Ready -> resolution.gateway.loadAddresses(after)
            is AddressGatewayResolution.Failed -> CustomerAccountResult.Failure(resolution.reason)
        }

    override suspend fun createAddress(
        address: CustomerAddressDraft,
        makeDefault: Boolean
    ): CustomerAddressMutationResult = withGateway { it.createAddress(address, makeDefault) }

    override suspend fun updateAddress(
        addressId: String,
        address: CustomerAddressDraft
    ): CustomerAddressMutationResult = withGateway { it.updateAddress(addressId, address) }

    override suspend fun setDefaultAddress(addressId: String): CustomerAddressMutationResult =
        withGateway { it.setDefaultAddress(addressId) }

    override suspend fun deleteAddress(addressId: String): CustomerAddressMutationResult =
        withGateway { it.deleteAddress(addressId) }

    private suspend fun withGateway(
        action: suspend (CustomerAddressGateway) -> CustomerAddressMutationResult
    ): CustomerAddressMutationResult = when (val resolution = resolveDelegate()) {
        is AddressGatewayResolution.Ready -> action(resolution.gateway)
        is AddressGatewayResolution.Failed -> CustomerAddressMutationResult.Failure(resolution.reason)
    }

    private suspend fun resolveDelegate(): AddressGatewayResolution = lock.withLock {
        delegate?.let { return@withLock AddressGatewayResolution.Ready(it) }
        when (val discovery = discoveryClient.discover()) {
            is CustomerAccountDiscoveryResult.Failure ->
                AddressGatewayResolution.Failed(CustomerAccountFailure.Discovery(discovery.reason))

            is CustomerAccountDiscoveryResult.Success ->
                ApolloCustomerAddressGateway(
                    CustomerAccountApolloClientFactory.createClient(
                        discovery.configuration.graphqlEndpoint
                    ),
                    sessionResolver
                ).also { delegate = it }.let(AddressGatewayResolution::Ready)
        }
    }
}

private sealed interface AddressGatewayResolution {
    data class Ready(val gateway: CustomerAddressGateway) : AddressGatewayResolution

    data class Failed(val reason: CustomerAccountFailure) : AddressGatewayResolution
}

class ApolloCustomerAddressGateway(
    private val client: ApolloClient,
    private val sessionResolver: CustomerSessionResolver,
    requestTimeoutMillis: Long = DEFAULT_ADDRESS_REQUEST_TIMEOUT_MILLIS
) : CustomerAddressGateway {
    private val callExecutor = CustomerApolloCallExecutor(requestTimeoutMillis)

    override suspend fun loadAddresses(after: String?): CustomerAccountResult<CustomerAddressPage> = when (
        val result = executeAuthenticated {
            client.query(
                CustomerAddressesQuery(
                    first = ADDRESS_PAGE_SIZE,
                    after = after?.let { Optional.present(it) } ?: Optional.Absent
                )
            )
        }
    ) {
        is CustomerAccountResult.Failure -> result
        is CustomerAccountResult.Success -> result.value.toAddressPage()
    }

    override suspend fun createAddress(
        address: CustomerAddressDraft,
        makeDefault: Boolean
    ): CustomerAddressMutationResult = executeMutation(
        createCall = {
            client.mutation(
                CustomerAddressCreateMutation(address.toGraphQlInput(), makeDefault)
            )
        },
        map = { data -> data.customerAddressCreate.toRawMutationPayload() }
    )

    override suspend fun updateAddress(
        addressId: String,
        address: CustomerAddressDraft
    ): CustomerAddressMutationResult = executeMutation(
        createCall = {
            client.mutation(
                CustomerAddressUpdateMutation(address.toGraphQlInput(), addressId)
            )
        },
        map = { data -> data.customerAddressUpdate.toRawMutationPayload() }
    )

    override suspend fun setDefaultAddress(addressId: String): CustomerAddressMutationResult = executeMutation(
        createCall = { client.mutation(CustomerAddressSetDefaultMutation(addressId)) },
        map = { data -> data.customerAddressUpdate.toRawMutationPayload() }
    )

    override suspend fun deleteAddress(addressId: String): CustomerAddressMutationResult = executeMutation(
        createCall = { client.mutation(CustomerAddressDeleteMutation(addressId)) },
        map = { data -> data.customerAddressDelete.toRawMutationPayload() }
    )

    private suspend fun <D : Operation.Data> executeMutation(
        createCall: () -> ApolloCall<D>,
        map: (D) -> RawAddressMutationPayload?
    ): CustomerAddressMutationResult = when (val result = executeAuthenticated(createCall)) {
        is CustomerAccountResult.Failure -> CustomerAddressMutationResult.Failure(result.reason)
        is CustomerAccountResult.Success -> map(result.value).toMutationResult()
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

private fun CustomerAddressesQuery.Data.toAddressPage(): CustomerAccountResult<CustomerAddressPage> {
    val defaultAddressId = customer.defaultAddress?.id
    val pageInfo = customer.addresses.pageInfo
    val nextCursor = when {
        !pageInfo.hasNextPage -> null

        pageInfo.endCursor != null -> pageInfo.endCursor

        else -> return CustomerAccountResult.Failure(
            CustomerAccountFailure.GraphQl(setOf(INVALID_ADDRESS_PAGE))
        )
    }
    return CustomerAccountResult.Success(
        CustomerAddressPage(
            addresses = customer.addresses.nodes.map { it.toCustomerAddress(defaultAddressId) },
            nextCursor = nextCursor
        )
    )
}

private fun CustomerAddressesQuery.Node.toCustomerAddress(defaultAddressId: String?): CustomerAddress = CustomerAddress(
    id = id,
    firstName = firstName,
    lastName = lastName,
    company = company,
    address1 = address1,
    address2 = address2,
    city = city,
    zip = zip,
    phoneNumber = phoneNumber,
    territoryCode = territoryCode?.rawValue,
    zoneCode = zoneCode,
    formatted = formatted,
    isDefault = id == defaultAddressId
)

private fun CustomerAddressDraft.toGraphQlInput(): CustomerAddressInput = CustomerAddressInput(
    firstName = Optional.present(firstName),
    lastName = Optional.present(lastName),
    company = Optional.present(company),
    address1 = Optional.present(address1),
    address2 = Optional.present(address2),
    city = Optional.present(city),
    zip = Optional.present(zip),
    phoneNumber = Optional.present(phoneNumber),
    territoryCode = Optional.present(territoryCode),
    zoneCode = Optional.present(zoneCode)
)

private data class RawAddressMutationPayload(val addressId: String?, val errors: List<RawAddressMutationError>)

private data class RawAddressMutationError(val field: List<String>?, val code: String?)

private fun CustomerAddressCreateMutation.CustomerAddressCreate?.toRawMutationPayload(): RawAddressMutationPayload? =
    this?.let { payload ->
        RawAddressMutationPayload(
            addressId = payload.customerAddress?.id,
            errors = payload.userErrors.map { RawAddressMutationError(it.field, it.code?.rawValue) }
        )
    }

private fun CustomerAddressUpdateMutation.CustomerAddressUpdate?.toRawMutationPayload(): RawAddressMutationPayload? =
    this?.let { payload ->
        RawAddressMutationPayload(
            addressId = payload.customerAddress?.id,
            errors =
                payload.userErrors.map { error ->
                    RawAddressMutationError(error.field, error.code?.rawValue)
                }
        )
    }

private fun CustomerAddressSetDefaultMutation.CustomerAddressUpdate?.toRawMutationPayload():
    RawAddressMutationPayload? =
    this?.let { payload ->
        RawAddressMutationPayload(
            addressId = payload.customerAddress?.id,
            errors =
                payload.userErrors.map { error ->
                    RawAddressMutationError(error.field, error.code?.rawValue)
                }
        )
    }

private fun CustomerAddressDeleteMutation.CustomerAddressDelete?.toRawMutationPayload(): RawAddressMutationPayload? =
    this?.let { payload ->
        RawAddressMutationPayload(
            addressId = payload.deletedAddressId,
            errors = payload.userErrors.map { RawAddressMutationError(it.field, it.code?.rawValue) }
        )
    }

private fun RawAddressMutationPayload?.toMutationResult(): CustomerAddressMutationResult = when {
    this == null ->
        CustomerAddressMutationResult.Failure(
            CustomerAccountFailure.GraphQl(setOf(MISSING_ADDRESS_PAYLOAD))
        )

    errors.isNotEmpty() ->
        CustomerAddressMutationResult.Rejected(
            fields = errors.mapTo(mutableSetOf()) { it.toField() },
            issues = errors.mapTo(mutableSetOf()) { it.toIssue() }
        )

    addressId == null ->
        CustomerAddressMutationResult.Failure(
            CustomerAccountFailure.GraphQl(setOf(MISSING_ADDRESS_DATA))
        )

    else -> CustomerAddressMutationResult.Success(addressId)
}

private fun RawAddressMutationError.toField(): CustomerAddressField = when (field?.lastOrNull()) {
    "firstName" -> CustomerAddressField.FIRST_NAME
    "lastName" -> CustomerAddressField.LAST_NAME
    "company" -> CustomerAddressField.COMPANY
    "address1" -> CustomerAddressField.ADDRESS1
    "address2" -> CustomerAddressField.ADDRESS2
    "city" -> CustomerAddressField.CITY
    "zip" -> CustomerAddressField.ZIP
    "phoneNumber" -> CustomerAddressField.PHONE
    "territoryCode", "zoneCode" -> CustomerAddressField.COUNTRY
    else -> if (code == "PHONE_NUMBER_NOT_VALID") CustomerAddressField.PHONE else CustomerAddressField.FORM
}

private fun RawAddressMutationError.toIssue(): CustomerAddressIssue = when (code) {
    "DELETING_CUSTOMER_DEFAULT_ADDRESS_NOT_ALLOWED",
    "DEMOTING_CUSTOMER_DEFAULT_ADDRESS_NOT_ALLOWED" -> CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED

    "CUSTOMER_ADDRESS_ALREADY_EXISTS" -> CustomerAddressIssue.DUPLICATE

    "ADDRESS_ID_DOES_NOT_EXIST" -> CustomerAddressIssue.NOT_FOUND

    "COUNTRY_NOT_EXIST",
    "INVALID_FOR_COUNTRY",
    "INVALID_FOR_COUNTRY_AND_PROVINCE",
    "INVALID_TERRITORY_CODE",
    "TERRITORY_CODE_MISSING",
    "ZONE_CODE_MISSING" -> CustomerAddressIssue.INVALID_COUNTRY

    else -> CustomerAddressIssue.UNKNOWN
}
