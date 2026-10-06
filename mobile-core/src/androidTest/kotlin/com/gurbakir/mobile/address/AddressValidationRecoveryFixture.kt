package com.gurbakir.mobile.address

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import com.gurbakir.account.CustomerAddressField
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/** Test-only proposal. No provider, authorization, protected store or real customer is composed. */
internal class AddressValidationRecoveryFixture(serverRejection: Boolean = false) : AutoCloseable {
    val controller = AddressValidationRecoveryController(serverRejection)
    val viewModel = AddressFormViewModel(controller, AddressTerritoryPolicy("ZZ", PostalCodeInputMode.TEXT))
    private val store = ViewModelStore().apply { put("address-recovery", viewModel) }
    val focusAcknowledgments = AtomicInteger()
    val backCalls = AtomicInteger()
    lateinit var hostView: View
    var recoveryResourceContext: Context? = null

    fun actions() = AddressFormActions(
        onBack = { backCalls.incrementAndGet() },
        onFieldChanged = viewModel::update,
        onMakeDefaultChanged = viewModel::setMakeDefault,
        onSave = viewModel::save,
        onReload = viewModel::reload,
        onFocusHandled = { requestId ->
            focusAcknowledgments.incrementAndGet()
            viewModel.consumeFocusRequest(requestId)
        }
    )

    fun fillValidDraft() {
        listOf(
            CustomerAddressField.FIRST_NAME to VALID_ADDRESS_RECOVERY_DRAFT.firstName,
            CustomerAddressField.LAST_NAME to VALID_ADDRESS_RECOVERY_DRAFT.lastName,
            CustomerAddressField.COMPANY to VALID_ADDRESS_RECOVERY_DRAFT.company,
            CustomerAddressField.ADDRESS1 to VALID_ADDRESS_RECOVERY_DRAFT.address1,
            CustomerAddressField.ADDRESS2 to VALID_ADDRESS_RECOVERY_DRAFT.address2,
            CustomerAddressField.CITY to VALID_ADDRESS_RECOVERY_DRAFT.city,
            CustomerAddressField.ZIP to VALID_ADDRESS_RECOVERY_DRAFT.zip,
            CustomerAddressField.PHONE to VALID_ADDRESS_RECOVERY_DRAFT.phoneNumber
        ).forEach { (field, value) -> viewModel.update(field, value) }
    }

    fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(hostView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun hideOwnedIme() {
        val input = hostView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.hideSoftInputFromWindow(hostView.windowToken, 0)
    }

    override fun close() {
        if (::hostView.isInitialized) hideOwnedIme()
        store.clear()
    }
}

internal class AddressValidationRecoveryController(private val serverRejection: Boolean) : AddressController {
    val loads = AtomicInteger()
    val saves = AtomicInteger()
    val defaults = AtomicInteger()
    val deletes = AtomicInteger()
    val saveEntered = CompletableDeferred<Unit>()
    val saveReply = CompletableDeferred<AddressActionResult>()

    @Volatile
    var savedInput: AddressInput? = null
        private set

    override suspend fun loadForm(addressId: String?): AddressFormLoadResult {
        check(addressId == null) { "Only a synthetic new-address form belongs to this fixture" }
        loads.incrementAndGet()
        return AddressFormLoadResult.Ready(null)
    }

    override suspend fun loadAddresses(): AddressLoadResult = error("No provider address read belongs to recovery")

    override suspend fun saveAddress(
        addressId: String?,
        input: AddressInput,
        expected: AddressInput?,
        makeDefault: Boolean
    ): AddressActionResult {
        saves.incrementAndGet()
        check(serverRejection) { "Local required validation must not reach even an inert save" }
        check(addressId == null && expected == null && !makeDefault) { "Unexpected synthetic save identity" }
        savedInput = input
        saveEntered.complete(Unit)
        return saveReply.await()
    }

    override suspend fun setDefault(addressId: String): AddressActionResult {
        defaults.incrementAndGet()
        error("No default mutation belongs to validation recovery")
    }

    override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult {
        deletes.incrementAndGet()
        error("No delete mutation belongs to validation recovery")
    }
}

internal val VALID_ADDRESS_RECOVERY_DRAFT = AddressInput(
    firstName = "Synthetic",
    lastName = "Customer",
    company = "Public fixture",
    address1 = "Synthetic street",
    address2 = "",
    city = "Example City",
    zip = "ZZ-1234",
    phoneNumber = "+905550000000"
)
