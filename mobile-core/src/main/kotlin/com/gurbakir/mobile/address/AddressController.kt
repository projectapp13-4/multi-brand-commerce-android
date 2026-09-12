package com.gurbakir.mobile.address

import com.gurbakir.account.CustomerAccountFailure
import com.gurbakir.account.CustomerAccountResult
import com.gurbakir.account.CustomerAddress
import com.gurbakir.account.CustomerAddressDraft
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.account.CustomerAddressGateway
import com.gurbakir.account.CustomerAddressIssue
import com.gurbakir.account.CustomerAddressMutationResult
import com.gurbakir.account.oauth.CustomerAccountDiscoveryFailure
import com.gurbakir.account.oauth.CustomerTokenFailure
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ADDRESS_PAGES = 5
private const val ADDRESS_PAGE_LIMIT_EXCEEDED = "ADDRESS_PAGE_LIMIT_EXCEEDED"

data class AddressContent(
    val id: String,
    val firstName: String,
    val lastName: String,
    val company: String,
    val address1: String,
    val address2: String,
    val city: String,
    val zip: String,
    val phoneNumber: String,
    val territoryCode: String?,
    val formatted: List<String>,
    val isDefault: Boolean,
    val isSupported: Boolean
) {
    override fun toString(): String =
        "AddressContent(id=<redacted>, fields=<redacted>, isDefault=$isDefault, isSupported=$isSupported)"
}

data class AddressInput(
    val firstName: String,
    val lastName: String,
    val company: String,
    val address1: String,
    val address2: String,
    val city: String,
    val zip: String,
    val phoneNumber: String
) {
    override fun toString(): String = "AddressInput(<redacted>)"
}

enum class AddressFailure {
    CONNECTION,
    SERVICE,
    NOT_FOUND,
    UNSUPPORTED_COUNTRY,
    SAVE_UNCONFIRMED
}

sealed interface AddressLoadResult {
    data class Content(val addresses: List<AddressContent>) : AddressLoadResult

    data object SignedOut : AddressLoadResult

    data class Failed(val reason: AddressFailure) : AddressLoadResult
}

sealed interface AddressFormLoadResult {
    data class Ready(val address: AddressContent?) : AddressFormLoadResult

    data object SignedOut : AddressFormLoadResult

    data class Failed(val reason: AddressFailure) : AddressFormLoadResult
}

sealed interface AddressActionResult {
    data class Confirmed(val addressId: String?) : AddressActionResult

    data class Rejected(val fields: Set<CustomerAddressField>, val issues: Set<CustomerAddressIssue>) :
        AddressActionResult

    data object Conflict : AddressActionResult

    data object SignedOut : AddressActionResult

    data class Failed(val reason: AddressFailure) : AddressActionResult
}

interface AddressController {
    suspend fun loadAddresses(): AddressLoadResult

    suspend fun loadForm(addressId: String?): AddressFormLoadResult

    suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult

    suspend fun setDefault(addressId: String): AddressActionResult

    suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult
}

@Singleton
class DefaultAddressController
@Inject
constructor(
    gateway: CustomerAddressGateway,
    private val sessionCoordinator: CustomerAccountSessionCoordinator,
    private val territoryPolicy: AddressTerritoryPolicy
) : AddressController {
    private val reader = AddressReader(gateway, territoryPolicy)
    private val mutations = AddressMutationCoordinator(gateway, reader, sessionCoordinator, territoryPolicy)

    override suspend fun loadAddresses(): AddressLoadResult = when (val result = reader.loadAll()) {
        is CustomerAccountResult.Success ->
            AddressLoadResult.Content(result.value.map { it.toAddressContent(territoryPolicy) })

        is CustomerAccountResult.Failure -> resolveLoadFailure(result.reason)
    }

    override suspend fun loadForm(addressId: String?): AddressFormLoadResult = if (addressId == null) {
        AddressFormLoadResult.Ready(null)
    } else {
        when (val result = reader.loadAll()) {
            is CustomerAccountResult.Failure -> resolveFormFailure(result.reason)

            is CustomerAccountResult.Success -> {
                val address = result.value.firstOrNull { it.id == addressId }
                when {
                    address == null -> AddressFormLoadResult.Failed(AddressFailure.NOT_FOUND)

                    !territoryPolicy.supports(address.territoryCode) ->
                        AddressFormLoadResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY)

                    else -> AddressFormLoadResult.Ready(address.toAddressContent(territoryPolicy))
                }
            }
        }
    }

    override suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult = mutations.saveAddress(addressId, input, expected, makeDefault)

    override suspend fun setDefault(addressId: String): AddressActionResult = mutations.setDefault(addressId)

    override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult =
        mutations.deleteAddress(addressId, expected)

    private suspend fun resolveLoadFailure(failure: CustomerAccountFailure): AddressLoadResult = when {
        failure.isTerminalAddressFailure() -> {
            sessionCoordinator.clearForLogout()
            AddressLoadResult.SignedOut
        }

        failure.isAddressConnectionFailure() -> AddressLoadResult.Failed(AddressFailure.CONNECTION)

        else -> AddressLoadResult.Failed(AddressFailure.SERVICE)
    }

    private suspend fun resolveFormFailure(failure: CustomerAccountFailure): AddressFormLoadResult = when {
        failure.isTerminalAddressFailure() -> {
            sessionCoordinator.clearForLogout()
            AddressFormLoadResult.SignedOut
        }

        failure.isAddressConnectionFailure() -> AddressFormLoadResult.Failed(AddressFailure.CONNECTION)

        else -> AddressFormLoadResult.Failed(AddressFailure.SERVICE)
    }
}

private class AddressReader(
    private val gateway: CustomerAddressGateway,
    private val territoryPolicy: AddressTerritoryPolicy
) {
    suspend fun loadAll(): CustomerAccountResult<List<CustomerAddress>> {
        val addresses = mutableListOf<CustomerAddress>()
        var cursor: String? = null
        var pageCount = 0
        var outcome: CustomerAccountResult<List<CustomerAddress>>? = null
        while (outcome == null && pageCount < MAX_ADDRESS_PAGES) {
            when (val page = gateway.loadAddresses(cursor)) {
                is CustomerAccountResult.Failure -> outcome = page

                is CustomerAccountResult.Success -> {
                    addresses += page.value.addresses
                    cursor = page.value.nextCursor
                    pageCount += 1
                    if (cursor == null) outcome = CustomerAccountResult.Success(addresses)
                }
            }
        }
        return outcome ?: CustomerAccountResult.Failure(
            CustomerAccountFailure.GraphQl(setOf(ADDRESS_PAGE_LIMIT_EXCEEDED))
        )
    }

    suspend fun current(addressId: String): CustomerAccountResult<CustomerAddress?> = when (val result = loadAll()) {
        is CustomerAccountResult.Failure -> result

        is CustomerAccountResult.Success ->
            CustomerAccountResult.Success(result.value.firstOrNull { it.id == addressId })
    }

    suspend fun matches(addressId: String, input: AddressInput): CustomerAccountResult<Boolean> =
        when (val result = current(addressId)) {
            is CustomerAccountResult.Failure -> result

            is CustomerAccountResult.Success -> CustomerAccountResult.Success(
                result.value?.toAddressContent(territoryPolicy)?.toAddressInput() == input.normalized()
            )
        }

    suspend fun isDefault(addressId: String): CustomerAccountResult<Boolean> = when (val result = current(addressId)) {
        is CustomerAccountResult.Failure -> result
        is CustomerAccountResult.Success -> CustomerAccountResult.Success(result.value?.isDefault == true)
    }

    suspend fun isAbsent(addressId: String): CustomerAccountResult<Boolean> = when (val result = current(addressId)) {
        is CustomerAccountResult.Failure -> result
        is CustomerAccountResult.Success -> CustomerAccountResult.Success(result.value == null)
    }
}

private class AddressMutationCoordinator(
    private val gateway: CustomerAddressGateway,
    private val reader: AddressReader,
    sessionCoordinator: CustomerAccountSessionCoordinator,
    private val territoryPolicy: AddressTerritoryPolicy
) {
    private val failureResolver = AddressActionFailureResolver(sessionCoordinator)
    private val verifier = AddressMutationVerifier(reader, failureResolver)

    suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult = if (addressId == null) {
        create(input, makeDefault)
    } else {
        when (val current = resolveCurrent(addressId)) {
            is CurrentAddress.Failed -> current.result

            is CurrentAddress.Ready -> when {
                !current.address.isSupported ->
                    AddressActionResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY)

                expected == null || current.address.toAddressInput() != expected.normalized() ->
                    AddressActionResult.Conflict

                else -> update(addressId, input)
            }
        }
    }

    suspend fun setDefault(addressId: String): AddressActionResult = when (val current = resolveCurrent(addressId)) {
        is CurrentAddress.Failed -> current.result

        is CurrentAddress.Ready -> when {
            !current.address.isSupported ->
                AddressActionResult.Failed(AddressFailure.UNSUPPORTED_COUNTRY)

            current.address.isDefault -> AddressActionResult.Confirmed(addressId)

            else -> verifier.confirmDefault(addressId, gateway.setDefaultAddress(addressId))
        }
    }

    suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult =
        when (val current = resolveCurrent(addressId)) {
            is CurrentAddress.Failed -> current.result

            is CurrentAddress.Ready -> when {
                current.address.toAddressInput() != expected.normalized() -> AddressActionResult.Conflict

                current.address.isDefault ->
                    AddressActionResult.Rejected(
                        fields = setOf(CustomerAddressField.FORM),
                        issues = setOf(CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED)
                    )

                else -> verifier.confirmDelete(addressId, gateway.deleteAddress(addressId))
            }
        }

    private suspend fun create(input: AddressInput, makeDefault: Boolean): AddressActionResult =
        when (val result = gateway.createAddress(input.toDraft(territoryPolicy), makeDefault)) {
            is CustomerAddressMutationResult.Success -> AddressActionResult.Confirmed(result.addressId)

            is CustomerAddressMutationResult.Rejected -> result.toActionResult()

            is CustomerAddressMutationResult.Failure ->
                failureResolver.resolve(result.reason, result.reason.mayHaveReachedServer())
        }

    private suspend fun update(addressId: String, input: AddressInput): AddressActionResult =
        when (val result = gateway.updateAddress(addressId, input.toDraft(territoryPolicy))) {
            is CustomerAddressMutationResult.Success -> AddressActionResult.Confirmed(result.addressId)
            is CustomerAddressMutationResult.Rejected -> result.toActionResult()
            is CustomerAddressMutationResult.Failure -> verifier.confirmUpdate(addressId, input, result.reason)
        }

    private suspend fun resolveCurrent(addressId: String): CurrentAddress =
        when (val result = reader.current(addressId)) {
            is CustomerAccountResult.Failure ->
                CurrentAddress.Failed(failureResolver.resolve(result.reason, outcomeMayBeUnknown = false))

            is CustomerAccountResult.Success -> result.value?.let {
                CurrentAddress.Ready(it.toAddressContent(territoryPolicy))
            } ?: CurrentAddress.Failed(AddressActionResult.Failed(AddressFailure.NOT_FOUND))
        }
}

private class AddressMutationVerifier(
    private val reader: AddressReader,
    private val failureResolver: AddressActionFailureResolver
) {
    suspend fun confirmUpdate(
        addressId: String,
        input: AddressInput,
        failure: CustomerAccountFailure
    ): AddressActionResult = if (failure.mayHaveReachedServer()) {
        confirmReread(addressId, reader.matches(addressId, input))
    } else {
        failureResolver.resolve(failure, outcomeMayBeUnknown = false)
    }

    suspend fun confirmDefault(addressId: String, mutation: CustomerAddressMutationResult): AddressActionResult =
        when (mutation) {
            is CustomerAddressMutationResult.Success -> confirmReread(addressId, reader.isDefault(addressId))

            is CustomerAddressMutationResult.Rejected -> mutation.toActionResult()

            is CustomerAddressMutationResult.Failure ->
                confirmFailure(addressId, mutation.reason) { reader.isDefault(addressId) }
        }

    suspend fun confirmDelete(addressId: String, mutation: CustomerAddressMutationResult): AddressActionResult =
        when (mutation) {
            is CustomerAddressMutationResult.Success -> AddressActionResult.Confirmed(mutation.addressId)

            is CustomerAddressMutationResult.Rejected -> mutation.toActionResult()

            is CustomerAddressMutationResult.Failure ->
                confirmFailure(addressId, mutation.reason) { reader.isAbsent(addressId) }
        }

    private suspend fun confirmFailure(
        addressId: String,
        failure: CustomerAccountFailure,
        reread: suspend () -> CustomerAccountResult<Boolean>
    ): AddressActionResult = if (failure.mayHaveReachedServer()) {
        confirmReread(addressId, reread())
    } else {
        failureResolver.resolve(failure, outcomeMayBeUnknown = false)
    }

    private suspend fun confirmReread(addressId: String, reread: CustomerAccountResult<Boolean>): AddressActionResult =
        when (reread) {
            is CustomerAccountResult.Success ->
                if (reread.value) AddressActionResult.Confirmed(addressId) else unconfirmed()

            is CustomerAccountResult.Failure ->
                if (reread.reason.isTerminalAddressFailure()) {
                    failureResolver.resolve(reread.reason, outcomeMayBeUnknown = false)
                } else {
                    unconfirmed()
                }
        }

    private fun unconfirmed(): AddressActionResult = AddressActionResult.Failed(AddressFailure.SAVE_UNCONFIRMED)
}

private class AddressActionFailureResolver(private val sessionCoordinator: CustomerAccountSessionCoordinator) {
    suspend fun resolve(failure: CustomerAccountFailure, outcomeMayBeUnknown: Boolean): AddressActionResult = when {
        failure.isTerminalAddressFailure() -> {
            sessionCoordinator.clearForLogout()
            AddressActionResult.SignedOut
        }

        outcomeMayBeUnknown -> AddressActionResult.Failed(AddressFailure.SAVE_UNCONFIRMED)

        failure.isAddressConnectionFailure() -> AddressActionResult.Failed(AddressFailure.CONNECTION)

        else -> AddressActionResult.Failed(AddressFailure.SERVICE)
    }
}

private sealed interface CurrentAddress {
    data class Ready(val address: AddressContent) : CurrentAddress

    data class Failed(val result: AddressActionResult) : CurrentAddress
}

private fun CustomerAddress.toAddressContent(territoryPolicy: AddressTerritoryPolicy): AddressContent = AddressContent(
    id = id,
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    company = company.orEmpty(),
    address1 = address1.orEmpty(),
    address2 = address2.orEmpty(),
    city = city.orEmpty(),
    zip = zip.orEmpty(),
    phoneNumber = phoneNumber.orEmpty(),
    territoryCode = territoryCode,
    formatted = formatted,
    isDefault = isDefault,
    isSupported = territoryPolicy.supports(territoryCode)
)

private fun AddressContent.toAddressInput(): AddressInput = AddressInput(
    firstName = firstName,
    lastName = lastName,
    company = company,
    address1 = address1,
    address2 = address2,
    city = city,
    zip = zip,
    phoneNumber = phoneNumber
).normalized()

private fun AddressInput.toDraft(territoryPolicy: AddressTerritoryPolicy): CustomerAddressDraft {
    val normalized = normalized()
    return CustomerAddressDraft(
        firstName = normalized.firstName.nullIfBlank(),
        lastName = normalized.lastName.nullIfBlank(),
        company = normalized.company.nullIfBlank(),
        address1 = normalized.address1.nullIfBlank(),
        address2 = normalized.address2.nullIfBlank(),
        city = normalized.city.nullIfBlank(),
        zip = normalized.zip.nullIfBlank(),
        phoneNumber = normalized.phoneNumber.nullIfBlank(),
        territoryCode = territoryPolicy.supportedTerritoryCode
    )
}

fun AddressInput.normalized(): AddressInput = AddressInput(
    firstName = firstName.trim(),
    lastName = lastName.trim(),
    company = company.trim(),
    address1 = address1.trim(),
    address2 = address2.trim(),
    city = city.trim(),
    zip = zip.trim(),
    phoneNumber = phoneNumber.trim()
)

private fun String.nullIfBlank(): String? = ifBlank { null }

private fun CustomerAddressMutationResult.Rejected.toActionResult(): AddressActionResult =
    AddressActionResult.Rejected(fields, issues)

private val TERMINAL_ADDRESS_ERROR_CODES =
    setOf("UNAUTHENTICATED", "UNAUTHORIZED", "ACCESS_DENIED", "TOKEN_INVALID")

private fun CustomerAccountFailure.isTerminalAddressFailure(): Boolean = when (this) {
    CustomerAccountFailure.SignedOut -> true

    is CustomerAccountFailure.Authentication -> reason != CustomerTokenFailure.Transient

    is CustomerAccountFailure.GraphQl -> errorCodes.any(TERMINAL_ADDRESS_ERROR_CODES::contains)

    is CustomerAccountFailure.Discovery,
    is CustomerAccountFailure.Transport -> false
}

private fun CustomerAccountFailure.isAddressConnectionFailure(): Boolean = when (this) {
    is CustomerAccountFailure.Authentication -> reason == CustomerTokenFailure.Transient

    is CustomerAccountFailure.Discovery -> reason == CustomerAccountDiscoveryFailure.NETWORK

    is CustomerAccountFailure.Transport -> retryable

    is CustomerAccountFailure.GraphQl,
    CustomerAccountFailure.SignedOut -> false
}

private fun CustomerAccountFailure.mayHaveReachedServer(): Boolean =
    this is CustomerAccountFailure.GraphQl || this is CustomerAccountFailure.Transport
