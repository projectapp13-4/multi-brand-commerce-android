package com.gurbakir.mobile.address

import androidx.lifecycle.ViewModelStore
import com.gurbakir.account.CustomerAddressField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Additional qualification for the proposed callback API; absent API compilation is not behavioral RED. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressSubmissionCallbackTest {
    @Test
    fun `initial local rejection skips editing callback and submission`() = runTest {
        withForm(StandardTestDispatcher(testScheduler)) { fixture ->
            var callbackCalls = 0
            assertEquals(AddressFormPhase.LOADING, fixture.viewModel.state.value.phase)
            assertFalse(fixture.viewModel.state.value.canSave)

            fixture.viewModel.save { callbackCalls += 1 }

            assertEquals(0, callbackCalls)
            assertTrue(fixture.controller.saves.isEmpty())
            advanceUntilIdle()

            fixture.viewModel.save { callbackCalls += 1 }
            advanceUntilIdle()

            assertEquals(0, callbackCalls)
            assertTrue(fixture.controller.saves.isEmpty())
            assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
            assertEquals(
                AddressFieldError.REQUIRED,
                fixture.viewModel.state.value.fieldErrors[CustomerAddressField.FIRST_NAME]
            )
            assertEquals(CustomerAddressField.FIRST_NAME, fixture.viewModel.state.value.focusRequest?.field)
        }
    }

    @Test
    fun `valid submission invokes callback exactly once before busy state and controller`() = runTest {
        withForm(StandardTestDispatcher(testScheduler)) { fixture ->
            advanceUntilIdle()
            fixture.fillValidDraft()
            val sequence = mutableListOf<String>()
            var callbackCalls = 0
            fixture.controller.onSave = {
                sequence += "controller"
                assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
            }

            fixture.viewModel.save {
                callbackCalls += 1
                sequence += "callback"
                assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
                assertTrue(fixture.viewModel.state.value.canSave)
                assertTrue(fixture.controller.saves.isEmpty())
            }

            assertEquals(1, callbackCalls)
            assertEquals(listOf("callback"), sequence)
            assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
            fixture.viewModel.save { callbackCalls += 1 }
            assertEquals(1, callbackCalls)
            runCurrent()
            assertEquals(listOf("callback", "controller"), sequence)
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT, fixture.controller.saves.single().input)
            fixture.controller.saveReply.complete(AddressActionResult.Confirmed("synthetic-created"))
            advanceUntilIdle()
            assertEquals(1, callbackCalls)
            assertEquals(1, fixture.controller.saves.size)
        }
    }

    @Test
    fun `submission uses fresh valid draft and make default choice made during callback`() = runTest {
        withForm(StandardTestDispatcher(testScheduler)) { fixture ->
            advanceUntilIdle()
            fixture.fillValidDraft()

            fixture.viewModel.save {
                fixture.viewModel.update(CustomerAddressField.CITY, "  Final synthetic city  ")
                fixture.viewModel.setMakeDefault(true)
            }
            runCurrent()

            val saved = fixture.controller.saves.single()
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT.copy(city = "Final synthetic city"), saved.input)
            assertEquals(saved.input, fixture.viewModel.state.value.input)
            assertTrue(saved.makeDefault)
            assertNull(saved.addressId)
            assertNull(saved.expected)
        }
    }

    @Test
    fun `fresh edit submission keeps loaded identity and original comparison`() = runTest {
        val original = editableAddress()
        withForm(StandardTestDispatcher(testScheduler), original) { fixture ->
            advanceUntilIdle()
            fixture.viewModel.update(CustomerAddressField.CITY, "Earlier synthetic edit")
            assertTrue(fixture.viewModel.state.value.canSave)

            fixture.viewModel.save {
                fixture.viewModel.update(CustomerAddressField.CITY, "  Final synthetic edit  ")
            }
            runCurrent()

            val saved = fixture.controller.saves.single()
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT.copy(city = "Final synthetic edit"), saved.input)
            assertEquals(original.id, saved.addressId)
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT, saved.expected)
            assertFalse(saved.makeDefault)
        }
    }

    @Test
    fun `fresh invalid text after callback remains a local rejection with no controller call`() = runTest {
        withForm(StandardTestDispatcher(testScheduler)) { fixture ->
            advanceUntilIdle()
            fixture.fillValidDraft()
            var callbackCalls = 0

            fixture.viewModel.save {
                callbackCalls += 1
                fixture.viewModel.update(CustomerAddressField.FIRST_NAME, "  ")
            }
            advanceUntilIdle()

            assertEquals(1, callbackCalls)
            assertTrue(fixture.controller.saves.isEmpty())
            assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT.copy(firstName = ""), fixture.viewModel.state.value.input)
            assertEquals(
                AddressFieldError.REQUIRED,
                fixture.viewModel.state.value.fieldErrors[CustomerAddressField.FIRST_NAME]
            )
            assertEquals(CustomerAddressField.FIRST_NAME, fixture.viewModel.state.value.focusRequest?.field)
        }
    }

    @Test
    fun `callback restoring original edit makes form non saveable and refuses submission`() = runTest {
        val original = editableAddress()
        withForm(StandardTestDispatcher(testScheduler), original) { fixture ->
            advanceUntilIdle()
            fixture.viewModel.update(CustomerAddressField.CITY, "Earlier synthetic edit")
            assertTrue(fixture.viewModel.state.value.canSave)
            var callbackCalls = 0

            fixture.viewModel.save {
                callbackCalls += 1
                fixture.viewModel.update(CustomerAddressField.CITY, original.city)
            }
            advanceUntilIdle()

            assertEquals(1, callbackCalls)
            assertTrue(fixture.controller.saves.isEmpty())
            assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
            assertEquals(VALID_FOCUS_PROTOCOL_DRAFT, fixture.viewModel.state.value.input)
            assertFalse(fixture.viewModel.state.value.dirty)
            assertFalse(fixture.viewModel.state.value.canSave)
            assertTrue(fixture.viewModel.state.value.fieldErrors.isEmpty())
            assertNull(fixture.viewModel.state.value.focusRequest)
        }
    }

    private suspend fun withForm(
        dispatcher: TestDispatcher,
        address: AddressContent? = null,
        block: suspend (SubmissionFixture) -> Unit
    ) {
        val fixture = SubmissionFixture(dispatcher, address)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private class SubmissionFixture(dispatcher: TestDispatcher, address: AddressContent?) : AutoCloseable {
        init {
            Dispatchers.setMain(dispatcher)
        }

        val controller = SubmissionController(address)
        val viewModel = AddressFormViewModel(controller, AddressTerritoryPolicy("ZZ", PostalCodeInputMode.TEXT))
        private val store = ViewModelStore().apply { put("address-submission-callback", viewModel) }

        init {
            viewModel.start(address?.id)
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

        override fun close() {
            try {
                store.clear()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class SubmissionController(private val address: AddressContent?) : AddressController {
        val saves = mutableListOf<Submission>()
        val saveReply = CompletableDeferred<AddressActionResult>()
        var onSave: () -> Unit = {}

        override suspend fun loadForm(addressId: String?): AddressFormLoadResult = AddressFormLoadResult.Ready(address)

        override suspend fun saveAddress(
            addressId: String?,
            input: AddressInput,
            expected: AddressInput?,
            makeDefault: Boolean
        ): AddressActionResult {
            saves += Submission(addressId, input, expected, makeDefault)
            onSave()
            return saveReply.await()
        }

        override suspend fun loadAddresses(): AddressLoadResult =
            error("No list read belongs to submission callback tests")

        override suspend fun setDefault(addressId: String): AddressActionResult =
            error("No separate default mutation belongs to submission callback tests")

        override suspend fun deleteAddress(addressId: String, expected: AddressInput): AddressActionResult =
            error("No delete mutation belongs to submission callback tests")
    }

    private data class Submission(
        val addressId: String?,
        val input: AddressInput,
        val expected: AddressInput?,
        val makeDefault: Boolean
    )

    private companion object {
        fun editableAddress() = AddressContent(
            id = "synthetic-submission-edit",
            firstName = VALID_FOCUS_PROTOCOL_DRAFT.firstName,
            lastName = VALID_FOCUS_PROTOCOL_DRAFT.lastName,
            company = VALID_FOCUS_PROTOCOL_DRAFT.company,
            address1 = VALID_FOCUS_PROTOCOL_DRAFT.address1,
            address2 = VALID_FOCUS_PROTOCOL_DRAFT.address2,
            city = VALID_FOCUS_PROTOCOL_DRAFT.city,
            zip = VALID_FOCUS_PROTOCOL_DRAFT.zip,
            phoneNumber = VALID_FOCUS_PROTOCOL_DRAFT.phoneNumber,
            territoryCode = "ZZ",
            formatted = listOf("<redacted>"),
            isDefault = false,
            isSupported = true
        )
    }
}
