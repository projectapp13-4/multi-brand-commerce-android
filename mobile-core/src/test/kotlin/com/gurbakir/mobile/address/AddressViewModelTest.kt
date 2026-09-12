package com.gurbakir.mobile.address

import app.cash.turbine.test
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.account.CustomerAddressIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressViewModelTest {
    @Test
    fun `create validates configured postal and generic phone fields and focuses first invalid field`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = formViewModel(FakeAddressController())
            viewModel.start(null)
            advanceUntilIdle()

            viewModel.save()

            assertEquals(AddressFieldError.REQUIRED, viewModel.state.value.fieldErrors[CustomerAddressField.FIRST_NAME])
            assertEquals(CustomerAddressField.FIRST_NAME, viewModel.state.value.focusRequest)

            validInput().forEach(viewModel::update)
            viewModel.update(CustomerAddressField.ZIP, "34")
            viewModel.update(CustomerAddressField.PHONE, "05551112233")
            viewModel.save()

            assertEquals(
                AddressFieldError.INVALID_POSTAL_CODE,
                viewModel.state.value.fieldErrors[CustomerAddressField.ZIP]
            )
            assertEquals(AddressFieldError.INVALID_PHONE, viewModel.state.value.fieldErrors[CustomerAddressField.PHONE])
            assertEquals(CustomerAddressField.ZIP, viewModel.state.value.focusRequest)
        }
    }

    @Test
    fun `synthetic postal policy accepts a materially different valid format`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake = FakeAddressController()
            val viewModel = formViewModel(fake, SYNTHETIC_POSTAL_POLICY)
            viewModel.start(null)
            advanceUntilIdle()
            assertEquals(PostalCodeInputMode.TEXT, viewModel.state.value.postalCodeInputMode)
            validInput().forEach(viewModel::update)
            viewModel.update(CustomerAddressField.ZIP, "AB-1234")

            viewModel.save()
            advanceUntilIdle()

            assertEquals("AB-1234", fake.lastInput?.zip)
            assertFalse(CustomerAddressField.ZIP in viewModel.state.value.fieldErrors)
        }
    }

    @Test
    fun `synthetic postal policy rejects a value valid only under another policy`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake = FakeAddressController()
            val viewModel = formViewModel(fake, SYNTHETIC_POSTAL_POLICY)
            viewModel.start(null)
            advanceUntilIdle()
            validInput().forEach(viewModel::update)

            viewModel.save()

            assertEquals(
                AddressFieldError.INVALID_POSTAL_CODE,
                viewModel.state.value.fieldErrors[CustomerAddressField.ZIP]
            )
            assertEquals(null, fake.lastInput)
        }
    }

    @Test
    fun `edit trims input and emits saved only after controller confirmation`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAddressController(
                    formLoads = mutableListOf(AddressFormLoadResult.Ready(address(city = "Original"))),
                    actionResult = AddressActionResult.Confirmed("1")
                )
            val viewModel = formViewModel(fake)
            viewModel.start("1")
            advanceUntilIdle()
            viewModel.update(CustomerAddressField.CITY, "  Updated  ")

            viewModel.effects.test {
                viewModel.save()
                advanceUntilIdle()
                assertEquals(AddressFormEffect.Saved, awaitItem())
                assertEquals("Updated", fake.lastInput?.city)
                assertEquals("Original", fake.lastExpected?.city)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `unconfirmed form keeps memory draft disabled until explicit reload`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAddressController(
                    formLoads =
                        mutableListOf(
                            AddressFormLoadResult.Ready(address(city = "Original")),
                            AddressFormLoadResult.Ready(address(city = "Server"))
                        ),
                    actionResult = AddressActionResult.Failed(AddressFailure.SAVE_UNCONFIRMED)
                )
            val viewModel = formViewModel(fake)
            viewModel.start("1")
            advanceUntilIdle()
            viewModel.update(CustomerAddressField.CITY, "Draft")
            viewModel.save()
            advanceUntilIdle()

            assertEquals("Draft", viewModel.state.value.input.city)
            assertEquals(AddressFormFailure.SAVE_UNCONFIRMED, viewModel.state.value.failure)
            assertFalse(viewModel.state.value.canSave)

            viewModel.reload()
            advanceUntilIdle()
            assertEquals("Server", viewModel.state.value.input.city)
            assertFalse(viewModel.state.value.dirty)
        }
    }

    @Test
    fun `new edit ViewModel reloads server and never inherits unsaved address PII`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAddressController(
                    formLoads =
                        mutableListOf(
                            AddressFormLoadResult.Ready(address(city = "Server")),
                            AddressFormLoadResult.Ready(address(city = "Server"))
                        )
                )
            val first = formViewModel(fake)
            first.start("1")
            advanceUntilIdle()
            first.update(CustomerAddressField.CITY, "Unsaved private draft")

            val recreated = formViewModel(fake)
            recreated.start("1")
            advanceUntilIdle()

            assertEquals("Server", recreated.state.value.input.city)
            assertFalse(recreated.state.value.dirty)
            assertTrue(!recreated.state.value.toString().contains("Server"))
        }
    }

    @Test
    fun `list set default requires confirmation and reloads authoritative state`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAddressController(
                    listLoads =
                        mutableListOf(
                            AddressLoadResult.Content(listOf(address(isDefault = false))),
                            AddressLoadResult.Content(listOf(address(isDefault = true)))
                        ),
                    actionResult = AddressActionResult.Confirmed("1")
                )
            val viewModel = AddressListViewModel(fake)
            advanceUntilIdle()

            viewModel.requestConfirmation("1", AddressConfirmationType.SET_DEFAULT)
            assertEquals(AddressConfirmationType.SET_DEFAULT, viewModel.state.value.confirmation?.type)
            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(1, fake.setDefaultCount)
            assertTrue(viewModel.state.value.addresses.single().isDefault)
            assertEquals(AddressListNotice.DEFAULT_UPDATED, viewModel.state.value.notice)
        }
    }

    @Test
    fun `list surfaces protected default deletion without losing private in memory content`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAddressController(
                    listLoads = mutableListOf(AddressLoadResult.Content(listOf(address()))),
                    actionResult =
                        AddressActionResult.Rejected(
                            fields = setOf(CustomerAddressField.FORM),
                            issues = setOf(CustomerAddressIssue.DEFAULT_ADDRESS_PROTECTED)
                        )
                )
            val viewModel = AddressListViewModel(fake)
            advanceUntilIdle()
            viewModel.requestConfirmation("1", AddressConfirmationType.DELETE)
            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(AddressListFailure.DEFAULT_ADDRESS_PROTECTED, viewModel.state.value.failure)
            assertEquals(1, viewModel.state.value.addresses.size)
        }
    }

    @Test
    fun `terminal form session clears PII and returns to Account`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel =
                formViewModel(
                    FakeAddressController(
                        formLoads = mutableListOf(AddressFormLoadResult.SignedOut)
                    )
                )

            viewModel.effects.test {
                viewModel.start("1")
                advanceUntilIdle()
                assertEquals(AddressFormEffect.ReturnToAccount, awaitItem())
                assertFalse(viewModel.state.value.loaded)
                assertEquals("", viewModel.state.value.input.address1)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private suspend fun withMainDispatcher(dispatcher: TestDispatcher, block: suspend () -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun formViewModel(
        controller: AddressController,
        territoryPolicy: AddressTerritoryPolicy = FIVE_DIGIT_POSTAL_POLICY
    ) = AddressFormViewModel(controller, territoryPolicy)

    private class FakeAddressController(
        private val listLoads: MutableList<AddressLoadResult> =
            mutableListOf(AddressLoadResult.Content(emptyList())),
        private val formLoads: MutableList<AddressFormLoadResult> =
            mutableListOf(AddressFormLoadResult.Ready(null)),
        private val actionResult: AddressActionResult = AddressActionResult.Failed(AddressFailure.SERVICE)
    ) : AddressController {
        var lastInput: AddressInput? = null
        var lastExpected: AddressInput? = null
        var setDefaultCount = 0

        override suspend fun loadAddresses(): AddressLoadResult = listLoads.removeAt(0)

        override suspend fun loadForm(addressId: String?): AddressFormLoadResult = formLoads.removeAt(0)

        override suspend fun saveAddress(
            addressId: String?,
            input: AddressInput,
            expected: AddressInput?,
            makeDefault: Boolean
        ): AddressActionResult {
            lastInput = input
            lastExpected = expected
            return actionResult
        }

        override suspend fun setDefault(addressId: String): AddressActionResult {
            setDefaultCount += 1
            return actionResult
        }

        override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult =
            actionResult
    }

    private companion object {
        val FIVE_DIGIT_POSTAL_POLICY = AddressTerritoryPolicy(
            supportedTerritoryCode = "ZZ",
            postalCodeInputMode = PostalCodeInputMode.NUMERIC,
            postalCodePolicy = AddressPostalCodePolicy { postalCode -> Regex("^[0-9]{5}$").matches(postalCode) }
        )
        val SYNTHETIC_POSTAL_POLICY = AddressTerritoryPolicy(
            supportedTerritoryCode = "ZZ",
            postalCodeInputMode = PostalCodeInputMode.TEXT,
            postalCodePolicy = AddressPostalCodePolicy { postalCode ->
                Regex("^[A-Z]{2}-[0-9]{4}$").matches(postalCode)
            }
        )

        fun address(city: String = "Example City", isDefault: Boolean = false) = AddressContent(
            id = "1",
            firstName = "Synthetic",
            lastName = "Customer",
            company = "",
            address1 = "Private street",
            address2 = "",
            city = city,
            zip = "12345",
            phoneNumber = "+15551234567",
            territoryCode = "ZZ",
            formatted = listOf("<redacted>"),
            isDefault = isDefault,
            isSupported = true
        )

        fun validInput(): Map<CustomerAddressField, String> = mapOf(
            CustomerAddressField.FIRST_NAME to "Synthetic",
            CustomerAddressField.LAST_NAME to "Customer",
            CustomerAddressField.ADDRESS1 to "Private street",
            CustomerAddressField.CITY to "Example City",
            CustomerAddressField.ZIP to "12345",
            CustomerAddressField.PHONE to "+15551234567"
        )
    }
}
