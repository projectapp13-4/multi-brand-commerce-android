package com.gurbakir.mobile

import com.gurbakir.firebase.RemoteConfigResult
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
class FirebaseProofViewModelTest {
    @Test
    fun `remote config differentiates fetch from safe local fallback`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fetched = FirebaseProofViewModel(FakeFirebaseProofController())
            fetched.refreshRemoteConfig()
            advanceUntilIdle()
            assertEquals(FirebaseProofPhase.REMOTE_CONFIG_FETCHED, fetched.state.value.phase)

            val fallback = FirebaseProofViewModel(
                FakeFirebaseProofController(remoteResult = RemoteConfigResult.LocalDefaults)
            )
            fallback.refreshRemoteConfig()
            advanceUntilIdle()
            assertEquals(FirebaseProofPhase.REMOTE_CONFIG_LOCAL_DEFAULTS, fallback.state.value.phase)
        }
    }

    @Test
    fun `push registration starts only after permission and unregisters explicitly`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller = FakeFirebaseProofController()
            val viewModel = FirebaseProofViewModel(controller)

            viewModel.requestPushPermission()
            assertEquals(FirebaseProofPhase.AWAITING_NOTIFICATION_PERMISSION, viewModel.state.value.phase)
            assertEquals(0, controller.registrationCount)

            viewModel.notificationPermissionResult(true)
            advanceUntilIdle()
            assertEquals(1, controller.registrationCount)
            assertTrue(viewModel.state.value.pushRegistered)

            viewModel.unregisterPush()
            advanceUntilIdle()
            assertEquals(1, controller.unregisterCount)
            assertFalse(viewModel.state.value.pushRegistered)
            assertEquals(FirebaseProofPhase.PUSH_UNREGISTERED, viewModel.state.value.phase)
        }
    }

    @Test
    fun `notification denial and allowlisted open remain explicit`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = FirebaseProofViewModel(FakeFirebaseProofController())
            viewModel.notificationPermissionResult(false)
            assertEquals(FirebaseProofPhase.NOTIFICATION_PERMISSION_DENIED, viewModel.state.value.phase)

            viewModel.notificationOpened()
            assertEquals(FirebaseProofPhase.PUSH_NOTIFICATION_OPENED, viewModel.state.value.phase)
            assertTrue(viewModel.state.value.pushRegistered)
        }
    }

    @Test
    fun `stored explicit consent restores the unregister action without exposing a target`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel =
                FirebaseProofViewModel(
                    FakeFirebaseProofController(storedConsent = true)
                )

            assertTrue(viewModel.state.value.pushRegistered)
            assertEquals(FirebaseProofPhase.READY, viewModel.state.value.phase)
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

    private class FakeFirebaseProofController(
        private val remoteResult: RemoteConfigResult = RemoteConfigResult.Fetched(false, 1L),
        private val registrationResult: Boolean = true,
        private val unregisterResult: Boolean = true,
        private val storedConsent: Boolean = false
    ) : FirebaseProofController {
        var registrationCount = 0
        var unregisterCount = 0

        override fun hasStoredPushConsent(): Boolean = storedConsent

        override suspend fun refreshRemoteConfig(): RemoteConfigResult = remoteResult

        override suspend fun registerPushAfterConsent(): Boolean {
            registrationCount += 1
            return registrationResult
        }

        override suspend fun unregisterPush(): Boolean {
            unregisterCount += 1
            return unregisterResult
        }
    }
}
