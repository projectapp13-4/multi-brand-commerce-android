package com.gurbakir.mobile

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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
class CommerceProofViewModelTest {
    @Test
    fun `restore exposes create only after an available variant is proven`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val unavailable =
                CommerceProofViewModel(
                    FakeCommerceProofController(restoreResult = CommerceProofResult.Empty(false))
                )
            advanceUntilIdle()
            assertFalse(unavailable.state.value.canCreateCart)

            val available =
                CommerceProofViewModel(
                    FakeCommerceProofController(restoreResult = CommerceProofResult.Empty(true))
                )
            advanceUntilIdle()
            assertTrue(available.state.value.canCreateCart)
            assertEquals(CommerceProofPhase.EMPTY, available.state.value.phase)
        }
    }

    @Test
    fun `synthetic cart result exposes only generic quantity state`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeCommerceProofController(
                    restoreResult = CommerceProofResult.Empty(true),
                    createResult = CommerceProofResult.Active(snapshot())
                )
            val viewModel = CommerceProofViewModel(fake)
            advanceUntilIdle()

            viewModel.createCart()
            advanceUntilIdle()

            assertEquals(CommerceProofPhase.ACTIVE, viewModel.state.value.phase)
            assertEquals(2, viewModel.state.value.snapshot?.totalQuantity)
            assertTrue(viewModel.state.value.canCheckout)
        }
    }

    @Test
    fun `completion clears cart state while cancellation retains it`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeCommerceProofController(
                    restoreResult = CommerceProofResult.Active(snapshot()),
                    checkoutOnCreate = true
                )
            val viewModel = CommerceProofViewModel(fake)
            advanceUntilIdle()

            viewModel.createCart()
            advanceUntilIdle()

            fake.emit(CommerceProofEvent.CHECKOUT_CANCELLED)
            advanceUntilIdle()
            assertEquals(CommerceProofPhase.CHECKOUT_CANCELLED, viewModel.state.value.phase)
            assertTrue(viewModel.state.value.snapshot != null)

            fake.emit(CommerceProofEvent.CHECKOUT_COMPLETED)
            advanceUntilIdle()
            assertEquals(CommerceProofPhase.CHECKOUT_COMPLETED, viewModel.state.value.phase)
            assertEquals(null, viewModel.state.value.snapshot)
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

    private fun snapshot(): CommerceProofSnapshot = CommerceProofSnapshot(
        totalQuantity = 2,
        lineCount = 1,
        hasMoreLines = false,
        warningCount = 0
    )

    private class FakeCommerceProofController(
        private val restoreResult: CommerceProofResult,
        private val createResult: CommerceProofResult = restoreResult,
        private val checkoutOnCreate: Boolean = false
    ) : CommerceProofController {
        private val mutableEvents = MutableSharedFlow<CommerceProofEvent>(extraBufferCapacity = 1)

        fun emit(event: CommerceProofEvent) {
            check(mutableEvents.tryEmit(event))
        }

        override suspend fun restore(): CommerceProofResult = restoreResult

        override suspend fun createCart(): CommerceProofResult = if (checkoutOnCreate) {
            CommerceProofResult.CheckoutStarted(mutableEvents)
        } else {
            createResult
        }

        override suspend fun addLine(): CommerceProofResult = createResult

        override suspend fun incrementFirstLine(): CommerceProofResult = createResult

        override suspend fun removeAllLines(): CommerceProofResult = CommerceProofResult.Empty(true)

        override suspend fun preloadCheckout(activity: Activity): CommerceProofResult =
            CommerceProofResult.CheckoutStarted(emptyFlow())

        override suspend fun presentCheckout(activity: Activity): CommerceProofResult =
            CommerceProofResult.CheckoutStarted(emptyFlow())
    }
}
