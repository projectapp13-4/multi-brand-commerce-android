package com.gurbakir.mobile.accountdeletion

import app.cash.turbine.test
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionViewModelTest {
    @Test
    fun `missing descriptors stay absent while session is ready`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val subject = AccountDeletionViewModel(FakeController(), DeletionPageSource { emptyList() })
            advanceUntilIdle()
            assertEquals(AccountDeletionPhase.READY, subject.state.value.phase)
            assertEquals(null, subject.state.value.privacyPage)
            assertEquals(null, subject.state.value.requestPage)
        }
    }

    @Test
    fun `launch outcomes retain opening return no browser and rejected feedback`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val subject = viewModel(FakeController())
            advanceUntilIdle()
            subject.onLaunchResult(DeletionPageId.PRIVACY, DeletionPageLaunchResult.OPENED)
            assertEquals(DeletionPageFeedbackType.OPENING, subject.state.value.externalFeedback?.type)
            assertEquals(DeletionPageId.PRIVACY, subject.state.value.pendingReturnPageId)
            subject.onLaunchResult(DeletionPageId.SUPPORT, DeletionPageLaunchResult.NO_BROWSER)
            assertEquals(DeletionPageFeedbackType.NO_BROWSER, subject.state.value.externalFeedback?.type)
            assertEquals(null, subject.state.value.pendingReturnPageId)
            subject.onActivityResumed()
            assertEquals(DeletionPageFeedbackType.NO_BROWSER, subject.state.value.externalFeedback?.type)
            subject.onLaunchResult(DeletionPageId.SUPPORT, DeletionPageLaunchResult.REJECTED)
            assertEquals(DeletionPageFeedbackType.REJECTED, subject.state.value.externalFeedback?.type)
            assertEquals(null, subject.state.value.pendingReturnPageId)
        }
    }

    @Test
    fun `authenticated session exposes only owned request resources with conservative defaults`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = viewModel(FakeController())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(AccountDeletionPhase.READY, state.phase)
            assertEquals(DeletionPageId.PRIVACY, state.privacyPage?.id)
            assertEquals(DeletionPageId.SUPPORT, state.requestPage?.id)
            assertTrue(state.clearSearchHistory)
            assertFalse(state.clearWishlist)
            assertTrue(state.discardCart)
        }
    }

    @Test
    fun `signed out session returns to account without exposing the request screen`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = viewModel(FakeController(check = AccountDeletionSessionCheck.SIGNED_OUT))

            viewModel.effects.test {
                advanceUntilIdle()
                assertEquals(AccountDeletionEffect.ReturnToAccount, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `confirmed cleanup preserves the exact selected plan and outcomes`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val controller = FakeController()
            val viewModel = viewModel(controller)
            advanceUntilIdle()

            viewModel.setClearSearchHistory(false)
            viewModel.setClearWishlist(true)
            viewModel.requestLocalClearConfirmation()
            viewModel.confirmLocalClearAndSignOut()
            advanceUntilIdle()

            assertEquals(
                AccountDeletionClearPlan(
                    clearSearchHistory = false,
                    clearWishlist = true,
                    discardCart = true
                ),
                controller.receivedPlan
            )
            assertEquals(AccountDeletionPhase.COMPLETED, viewModel.state.value.phase)
            assertNotNull(viewModel.state.value.result)
        }
    }

    @Test
    fun `browser return records only an unverified return outcome`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel = viewModel(FakeController())
            advanceUntilIdle()

            viewModel.onLaunchResult(DeletionPageId.SUPPORT, DeletionPageLaunchResult.OPENED)
            viewModel.onActivityResumed()

            assertEquals(
                DeletionPageFeedbackType.RETURNED,
                viewModel.state.value.externalFeedback?.type
            )
            assertEquals(null, viewModel.state.value.pendingReturnPageId)
        }
    }

    private fun viewModel(controller: FakeController) = AccountDeletionViewModel(
        controller,
        DeletionPageSource {
            listOf(DeletionPageDescriptor(DeletionPageId.PRIVACY, 1), DeletionPageDescriptor(DeletionPageId.SUPPORT, 2))
        }
    )

    private suspend fun withMainDispatcher(dispatcher: TestDispatcher, block: suspend () -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeController(
        private val check: AccountDeletionSessionCheck = AccountDeletionSessionCheck.READY,
        private val result: AccountDeletionLocalResult =
            AccountDeletionLocalResult(
                session = AccountDeletionClearOutcome.CLEARED,
                searchHistory = AccountDeletionClearOutcome.NOT_SELECTED,
                wishlist = AccountDeletionClearOutcome.CLEARED,
                cart = AccountDeletionClearOutcome.CLEARED,
                remoteLogoutUnverified = false
            )
    ) : AccountDeletionController {
        var receivedPlan: AccountDeletionClearPlan? = null

        override suspend fun checkSession(): AccountDeletionSessionCheck = check

        override suspend fun clearLocalDataAndSignOut(plan: AccountDeletionClearPlan): AccountDeletionLocalResult {
            receivedPlan = plan
            return result
        }
    }
}
