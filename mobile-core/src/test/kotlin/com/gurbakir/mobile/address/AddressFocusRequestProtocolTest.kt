package com.gurbakir.mobile.address

import com.gurbakir.account.CustomerAddressField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Future request-ID API proposal; never an old-API RED or native focus receipt. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressFocusRequestProtocolTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fixture: AddressFocusRequestProtocolFixture

    @BeforeEach
    fun setUp() {
        fixture = AddressFocusRequestProtocolFixture(dispatcher)
        fixture.viewModel.start(null)
    }

    @AfterEach
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun `repeated same field invalid save creates distinct request identity`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val first = fixture.request()
        val firstErrors = fixture.viewModel.state.value.fieldErrors

        fixture.viewModel.save()

        val second = fixture.request()
        assertEquals(CustomerAddressField.FIRST_NAME, first.field)
        assertEquals(first.field, second.field)
        assertNotEquals(first.id, second.id)
        assertEquals(firstErrors, fixture.viewModel.state.value.fieldErrors)
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `old acknowledgment cannot clear a same field replacement`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val first = fixture.request()
        fixture.viewModel.save()
        val replacement = fixture.request()
        val beforeOldAck = fixture.viewModel.state.value
        assertNotEquals(first.id, replacement.id)

        fixture.viewModel.consumeFocusRequest(first.id)

        assertEquals(beforeOldAck, fixture.viewModel.state.value)
        assertEquals(replacement, fixture.request())
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `exact current acknowledgment clears only the focus request`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val request = fixture.request()
        val before = fixture.viewModel.state.value
        assertEquals(AddressFocusRequest(request.id, CustomerAddressField.FIRST_NAME), request)

        fixture.viewModel.consumeFocusRequest(request.id)

        assertEquals(before.copy(focusRequest = null), fixture.viewModel.state.value)
        fixture.viewModel.consumeFocusRequest(request.id)
        assertEquals(before.copy(focusRequest = null), fixture.viewModel.state.value)
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `unhandled request remains pending without acknowledgment`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val pending = fixture.viewModel.state.value

        // A UI that declines focus sends no acknowledgment. This test does not simulate native focus.
        runCurrent()
        advanceUntilIdle()

        assertEquals(pending, fixture.viewModel.state.value)
        assertEquals(CustomerAddressField.FIRST_NAME, fixture.request().field)
        assertFalse(fixture.viewModel.state.value.busy)
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `draft update invalidates a pending request and late acknowledgment stays inert`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val request = fixture.request()

        fixture.viewModel.update(CustomerAddressField.FIRST_NAME, "Updated synthetic")

        val updated = fixture.viewModel.state.value
        assertNull(updated.focusRequest)
        assertEquals("Updated synthetic", updated.input.firstName)
        assertFalse(CustomerAddressField.FIRST_NAME in updated.fieldErrors)
        assertEquals(AddressFieldError.REQUIRED, updated.fieldErrors[CustomerAddressField.LAST_NAME])
        fixture.viewModel.consumeFocusRequest(request.id)
        assertEquals(updated, fixture.viewModel.state.value)
        fixture.assertMutationCounts(0)

        fixture.viewModel.save()
        val replacement = fixture.request()
        assertEquals(CustomerAddressField.LAST_NAME, replacement.field)
        assertNotEquals(request.id, replacement.id)
        val beforeOldAck = fixture.viewModel.state.value
        fixture.viewModel.consumeFocusRequest(request.id)
        assertEquals(beforeOldAck, fixture.viewModel.state.value)
        assertEquals(replacement, fixture.request())
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `reload invalidates request before fresh load and rejects late acknowledgment`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        val request = fixture.request()
        val freshLoad = fixture.controller.holdNextLoad()

        fixture.viewModel.reload()

        assertEquals(AddressFormPhase.LOADING, fixture.viewModel.state.value.phase)
        assertNull(fixture.viewModel.state.value.focusRequest)
        runCurrent()
        assertEquals(2, fixture.controller.loads)
        assertFalse(freshLoad.isCompleted)
        fixture.viewModel.consumeFocusRequest(request.id)
        assertNull(fixture.viewModel.state.value.focusRequest)
        freshLoad.complete(AddressFormLoadResult.Ready(null))
        advanceUntilIdle()
        assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
        assertTrue(fixture.viewModel.state.value.loaded)
        assertTrue(fixture.viewModel.state.value.fieldErrors.isEmpty())
        assertNull(fixture.viewModel.state.value.focusRequest)
        fixture.viewModel.consumeFocusRequest(request.id)
        assertNull(fixture.viewModel.state.value.focusRequest)
        fixture.assertMutationCounts(0)

        fixture.viewModel.save()
        val replacement = fixture.request()
        assertEquals(CustomerAddressField.FIRST_NAME, replacement.field)
        assertNotEquals(request.id, replacement.id)
        val beforeOldAck = fixture.viewModel.state.value
        fixture.viewModel.consumeFocusRequest(request.id)
        assertEquals(beforeOldAck, fixture.viewModel.state.value)
        assertEquals(replacement, fixture.request())
        fixture.assertMutationCounts(0)
    }

    @Test
    fun `first invalid field order survives local validation and unordered server fields`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.viewModel.save()
        assertEquals(CustomerAddressField.FIRST_NAME, fixture.request().field)
        assertEquals(
            listOf(
                CustomerAddressField.FIRST_NAME,
                CustomerAddressField.LAST_NAME,
                CustomerAddressField.ADDRESS1,
                CustomerAddressField.CITY
            ),
            fixture.viewModel.state.value.fieldErrors.keys.toList()
        )
        fixture.viewModel.update(CustomerAddressField.FIRST_NAME, "Synthetic")
        fixture.viewModel.save()
        assertEquals(CustomerAddressField.LAST_NAME, fixture.request().field)
        fixture.assertMutationCounts(0)
        fixture.fillValidDraft()
        val reply = fixture.controller.queueSaveReply()
        fixture.viewModel.save()
        runCurrent()
        fixture.assertMutationCounts(1)
        assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
        assertNull(fixture.viewModel.state.value.focusRequest)
        val reverseFields = linkedSetOf(
            CustomerAddressField.PHONE,
            CustomerAddressField.CITY,
            CustomerAddressField.LAST_NAME,
            CustomerAddressField.FIRST_NAME
        )

        reply.complete(AddressActionResult.Rejected(reverseFields, emptySet()))
        advanceUntilIdle()

        assertEquals(CustomerAddressField.FIRST_NAME, fixture.request().field)
        assertEquals(reverseFields, fixture.viewModel.state.value.fieldErrors.keys)
        assertTrue(fixture.viewModel.state.value.fieldErrors.values.all { it == AddressFieldError.SERVER_REJECTED })
        assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
        fixture.assertMutationCounts(1)
    }

    @Test
    fun `repeated server rejection uses distinct IDs and rejects old acknowledgment`() = runTest(dispatcher) {
        advanceUntilIdle()
        fixture.assertLoadedNew()
        fixture.fillValidDraft()
        val firstReply = fixture.controller.queueSaveReply()
        fixture.viewModel.save()
        runCurrent()
        fixture.assertMutationCounts(1)
        assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
        assertNull(fixture.viewModel.state.value.focusRequest)
        firstReply.complete(AddressActionResult.Rejected(setOf(CustomerAddressField.FIRST_NAME), emptySet()))
        advanceUntilIdle()
        val first = fixture.request()
        val secondReply = fixture.controller.queueSaveReply()

        fixture.viewModel.save()
        runCurrent()

        fixture.assertMutationCounts(2)
        assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
        assertNull(fixture.viewModel.state.value.focusRequest)
        assertEquals(listOf(VALID_FOCUS_PROTOCOL_DRAFT, VALID_FOCUS_PROTOCOL_DRAFT), fixture.controller.savedInputs)
        secondReply.complete(AddressActionResult.Rejected(setOf(CustomerAddressField.FIRST_NAME), emptySet()))
        advanceUntilIdle()
        val replacement = fixture.request()
        val beforeOldAck = fixture.viewModel.state.value
        assertEquals(first.field, replacement.field)
        assertNotEquals(first.id, replacement.id)
        fixture.viewModel.consumeFocusRequest(first.id)
        assertEquals(beforeOldAck, fixture.viewModel.state.value)
        fixture.viewModel.consumeFocusRequest(replacement.id)
        assertEquals(beforeOldAck.copy(focusRequest = null), fixture.viewModel.state.value)
        fixture.assertMutationCounts(2)
    }
}
