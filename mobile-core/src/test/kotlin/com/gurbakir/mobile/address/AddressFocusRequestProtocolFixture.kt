package com.gurbakir.mobile.address

import androidx.lifecycle.ViewModelStore
import com.gurbakir.account.CustomerAddressField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Jupiter test setup follows existing setMain/resetMain conventions and owns its one ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AddressFocusRequestProtocolFixture(dispatcher: TestDispatcher) : AutoCloseable {
    init {
        Dispatchers.setMain(dispatcher)
    }

    val controller = AddressFocusProtocolController()
    val viewModel = AddressFormViewModel(controller, AddressTerritoryPolicy("ZZ", PostalCodeInputMode.TEXT))
    private val store = ViewModelStore().apply { put("address-focus-protocol", viewModel) }

    fun assertLoadedNew() {
        assertEquals(1, controller.loads)
        assertEquals(AddressFormPhase.READY, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.loaded)
        assertTrue(viewModel.state.value.isCreate)
        assertTrue(viewModel.state.value.canSave)
        assertMutationCounts(0)
    }

    fun fillValidDraft() {
        listOf(
            CustomerAddressField.FIRST_NAME to VALID_FOCUS_PROTOCOL_DRAFT.firstName,
            CustomerAddressField.LAST_NAME to VALID_FOCUS_PROTOCOL_DRAFT.lastName,
            CustomerAddressField.COMPANY to VALID_FOCUS_PROTOCOL_DRAFT.company,
            CustomerAddressField.ADDRESS1 to VALID_FOCUS_PROTOCOL_DRAFT.address1,
            CustomerAddressField.ADDRESS2 to VALID_FOCUS_PROTOCOL_DRAFT.address2,
            CustomerAddressField.CITY to VALID_FOCUS_PROTOCOL_DRAFT.city,
            CustomerAddressField.ZIP to VALID_FOCUS_PROTOCOL_DRAFT.zip,
            CustomerAddressField.PHONE to VALID_FOCUS_PROTOCOL_DRAFT.phoneNumber
        ).forEach { (field, value) -> viewModel.update(field, value) }
    }

    fun request(): AddressFocusRequest = checkNotNull(viewModel.state.value.focusRequest)

    fun assertMutationCounts(expectedSaves: Int) {
        assertEquals(expectedSaves, controller.saves)
        assertEquals(0, controller.defaults)
        assertEquals(0, controller.deletes)
    }

    override fun close() {
        try {
            store.clear()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

internal class AddressFocusProtocolController : AddressController {
    var loads = 0
        private set
    var saves = 0
        private set
    var defaults = 0
        private set
    var deletes = 0
        private set
    val savedInputs = mutableListOf<AddressInput>()
    private val saveReplies = mutableListOf<CompletableDeferred<AddressActionResult>>()
    private var loadReply: CompletableDeferred<AddressFormLoadResult>? = null

    fun holdNextLoad(): CompletableDeferred<AddressFormLoadResult> {
        check(loadReply == null) { "Only one synthetic fresh load may be held" }
        return CompletableDeferred<AddressFormLoadResult>().also { loadReply = it }
    }

    fun queueSaveReply(): CompletableDeferred<AddressActionResult> =
        CompletableDeferred<AddressActionResult>().also(saveReplies::add)

    override suspend fun loadForm(addressId: String?): AddressFormLoadResult {
        check(addressId == null) { "This fixture owns only a synthetic new-address route" }
        loads += 1
        val reply = loadReply ?: return AddressFormLoadResult.Ready(null)
        loadReply = null
        return reply.await()
    }

    override suspend fun loadAddresses(): AddressLoadResult = error("No provider list read belongs to focus protocol")

    override suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult {
        saves += 1
        check(addressId == null && expected == null && !makeDefault) { "Unexpected synthetic save identity" }
        savedInputs += input
        check(saveReplies.isNotEmpty()) { "Local validation must not reach even an inert save" }
        return saveReplies.removeAt(0).await()
    }

    override suspend fun setDefault(addressId: String): AddressActionResult {
        defaults += 1
        error("No default mutation belongs to focus protocol")
    }

    override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult {
        deletes += 1
        error("No delete mutation belongs to focus protocol")
    }
}

internal val VALID_FOCUS_PROTOCOL_DRAFT = AddressInput(
    firstName = "Synthetic",
    lastName = "Customer",
    company = "Public fixture",
    address1 = "Synthetic street",
    address2 = "",
    city = "Example City",
    zip = "ZZ-1234",
    phoneNumber = "+905550000000"
)
