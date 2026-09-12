package com.gurbakir.mobile.profile

import app.cash.turbine.test
import com.gurbakir.account.CustomerProfileField
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
class ProfileViewModelTest {
    @Test
    fun `save trims input and shows only server confirmed values`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeProfileController(
                    loadResults = mutableListOf(ProfileResult.Content(ProfileContent("Ada", "Lovelace"))),
                    saveResult = ProfileResult.Content(ProfileContent("Grace", "Hopper"))
                )
            val viewModel = ProfileViewModel(fake)
            advanceUntilIdle()

            viewModel.onFirstNameChanged("  Grace  ")
            viewModel.onLastNameChanged(" Hopper ")
            viewModel.save()
            advanceUntilIdle()

            assertEquals("Grace", fake.savedFirstName)
            assertEquals("Hopper", fake.savedLastName)
            assertEquals("Grace", viewModel.state.value.firstName)
            assertEquals("Hopper", viewModel.state.value.lastName)
            assertEquals(ProfileNotice.SAVED, viewModel.state.value.notice)
            assertFalse(viewModel.state.value.dirty)
            assertTrue(!viewModel.state.value.toString().contains("Grace"))
        }
    }

    @Test
    fun `local and server field failures focus the first rejected field`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeProfileController(
                    loadResults = mutableListOf(ProfileResult.Content(ProfileContent("Ada", "Lovelace"))),
                    saveResult = ProfileResult.Rejected(setOf(CustomerProfileField.FIRST_NAME))
                )
            val viewModel = ProfileViewModel(fake)
            advanceUntilIdle()

            viewModel.onLastNameChanged("Invalid\nName")
            viewModel.save()

            assertEquals(
                ProfileFieldError.INVALID_CHARACTERS,
                viewModel.state.value.fieldErrors[CustomerProfileField.LAST_NAME]
            )
            assertEquals(CustomerProfileField.LAST_NAME, viewModel.state.value.focusRequest)

            viewModel.onLastNameChanged("Hopper")
            viewModel.onFirstNameChanged("Rejected")
            viewModel.save()
            advanceUntilIdle()

            assertEquals(
                ProfileFieldError.SERVER_REJECTED,
                viewModel.state.value.fieldErrors[CustomerProfileField.FIRST_NAME]
            )
            assertEquals(CustomerProfileField.FIRST_NAME, viewModel.state.value.focusRequest)
        }
    }

    @Test
    fun `unconfirmed save keeps draft until explicit reload discards it`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeProfileController(
                    loadResults =
                        mutableListOf(
                            ProfileResult.Content(ProfileContent("Original", "Name")),
                            ProfileResult.Content(ProfileContent("Server", "Value"))
                        ),
                    saveResult = ProfileResult.Failed(ProfileFailure.SAVE_UNCONFIRMED)
                )
            val viewModel = ProfileViewModel(fake)
            advanceUntilIdle()

            viewModel.onFirstNameChanged("Draft")
            viewModel.save()
            advanceUntilIdle()

            assertEquals("Draft", viewModel.state.value.firstName)
            assertEquals(ProfileFailure.SAVE_UNCONFIRMED, viewModel.state.value.failure)
            assertFalse(viewModel.state.value.canSave)

            viewModel.onFirstNameChanged("Second attempt")
            viewModel.save()
            advanceUntilIdle()

            assertEquals("Draft", viewModel.state.value.firstName)
            assertEquals(1, fake.saveCount)

            viewModel.reload()
            advanceUntilIdle()

            assertEquals("Server", viewModel.state.value.firstName)
            assertFalse(viewModel.state.value.dirty)
        }
    }

    @Test
    fun `new ViewModel after process recreation reloads and does not persist draft`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeProfileController(
                    loadResults =
                        mutableListOf(
                            ProfileResult.Content(ProfileContent("Server", "Value")),
                            ProfileResult.Content(ProfileContent("Server", "Value"))
                        )
                )
            val first = ProfileViewModel(fake)
            advanceUntilIdle()
            first.onFirstNameChanged("Unsaved draft")

            val recreated = ProfileViewModel(fake)
            advanceUntilIdle()

            assertEquals("Server", recreated.state.value.firstName)
            assertFalse(recreated.state.value.dirty)
        }
    }

    @Test
    fun `terminal session result clears form and returns to Account`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val viewModel =
                ProfileViewModel(
                    FakeProfileController(loadResults = mutableListOf(ProfileResult.SignedOut))
                )

            viewModel.effects.test {
                advanceUntilIdle()
                assertEquals(ProfileEffect.ReturnToAccount, awaitItem())
                assertFalse(viewModel.state.value.loaded)
                assertEquals("", viewModel.state.value.firstName)
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

    private class FakeProfileController(
        private val loadResults: MutableList<ProfileResult>,
        private val saveResult: ProfileResult = ProfileResult.Failed(ProfileFailure.SERVICE)
    ) : ProfileController {
        var savedFirstName: String? = null
        var savedLastName: String? = null
        var saveCount = 0

        override suspend fun load(): ProfileResult = loadResults.removeAt(0)

        override suspend fun save(
            firstName: String?,
            lastName: String?,
            expectedFirstName: String?,
            expectedLastName: String?
        ): ProfileResult {
            saveCount += 1
            savedFirstName = firstName
            savedLastName = lastName
            return saveResult
        }
    }
}
