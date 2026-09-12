package com.gurbakir.mobile.update

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatePolicyViewModelTest {
    @Test
    fun `update deferral hides the same version and survives saved state recreation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val savedState = SavedStateHandle()
            val controller =
                FakeController(
                    UpdatePolicyPresentation(
                        source = UpdatePolicySource.REMOTE_CONFIG,
                        optionalUpdateVersionCode = 2
                    )
                )
            val first = UpdatePolicyViewModel(controller, savedState)
            advanceUntilIdle()
            assertEquals(UpdatePolicyNotice.OptionalUpdate(2), first.state.value.notice)

            first.deferUpdate()

            assertNull(first.state.value.notice)
            assertEquals(2, controller.deferredVersion)

            val restored = UpdatePolicyViewModel(controller, savedState)
            advanceUntilIdle()

            assertNull(restored.state.value.notice)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `maintenance dismissal is revision scoped`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val controller =
                FakeController(
                    UpdatePolicyPresentation(
                        source = UpdatePolicySource.REMOTE_CONFIG,
                        maintenanceMessageEnabled = true,
                        policyRevision = 5L
                    )
                )
            val viewModel = UpdatePolicyViewModel(controller, SavedStateHandle())
            advanceUntilIdle()
            viewModel.dismissMaintenance()
            assertNull(viewModel.state.value.notice)

            controller.presentation = controller.presentation.copy(policyRevision = 6L)
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(UpdatePolicyNotice.Maintenance(6L), viewModel.state.value.notice)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeController(var presentation: UpdatePolicyPresentation) : UpdatePolicyController {
        var deferredVersion: Int? = null

        override fun loadCachedPolicy(): UpdatePolicyPresentation = presentation

        override suspend fun refreshPolicy(): UpdatePolicyPresentation = presentation

        override fun deferUpdate(recommendedVersionCode: Int): Boolean {
            deferredVersion = recommendedVersionCode
            return true
        }
    }
}
