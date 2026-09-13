package com.gurbakir.mobile.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `healthy manual refresh retains content and prevents overlapping requests`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val replacement = CompletableDeferred<HomeLoadResult>()
            val repository = QueueRepository(mutableListOf(accepted("initial")), replacement)
            val viewModel = HomeViewModel(repository, HomeLoadingClock(), FixedClock())
            advanceUntilIdle()

            viewModel.refreshContent()
            viewModel.refreshContent()
            runCurrent()

            assertEquals(2, repository.calls)
            assertTrue(viewModel.state.value.requestActive)
            assertTrue(requireNotNull(viewModel.state.value.presentation).refreshing)

            replacement.complete(accepted("replacement"))
            advanceUntilIdle()
            assertFalse(viewModel.state.value.requestActive)
            assertEquals("replacement", viewModel.state.value.presentation?.renderedSections?.single()?.stableId)
        }
    }

    @Test
    fun `resume at the editorial deadline hides expired content while one refresh runs`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val clock = FixedClock(now = 100L)
            val repository = PendingAfterInitial(accepted("fresh", expiresAt = 200L))
            val viewModel = HomeViewModel(repository, HomeLoadingClock(), clock)
            runCurrent()
            assertEquals("fresh", viewModel.state.value.presentation?.renderedSections?.single()?.stableId)

            clock.now = 200L
            viewModel.onHomeResumed()
            viewModel.onHomeResumed()
            runCurrent()

            assertEquals(2, repository.calls)
            assertTrue(viewModel.state.value.expired)
            assertTrue(viewModel.state.value.requestActive)
            assertNull(viewModel.state.value.presentation)
            repository.complete(
                HomeLoadResult.Failed(HomeLoadFailure(HomeLoadFailureCategory.CONNECTION, true))
            )
            runCurrent()
        }
    }

    private fun accepted(id: String, expiresAt: Long? = null): HomeLoadResult.Accepted = HomeLoadResult.Accepted(
        HomePresentation(
            editorial = HomeEditorialState.Packaged,
            renderedSections = listOf(
                HomeRenderedSection.CollectionGrid(id, HomeText.Remote(id), emptyList())
            ),
            source = HomeContentSource.PACKAGED,
            resourceStatus = HomeResourceStatus.COMPLETE,
            editorialExpiresAtMillis = expiresAt
        ),
        HomePersistenceStatus.NOT_APPLICABLE
    )

    private suspend fun withMainDispatcher(dispatcher: TestDispatcher, block: suspend () -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class QueueRepository(
        private val immediate: MutableList<HomeLoadResult>,
        private val pending: CompletableDeferred<HomeLoadResult>
    ) : HomeContentRepository {
        var calls = 0
        override suspend fun load(trigger: HomeLoadTrigger): HomeLoadResult {
            calls += 1
            return if (immediate.isNotEmpty()) immediate.removeAt(0) else pending.await()
        }
    }

    private class PendingAfterInitial(private val initial: HomeLoadResult) : HomeContentRepository {
        private val pending = CompletableDeferred<HomeLoadResult>()
        var calls = 0
        override suspend fun load(trigger: HomeLoadTrigger): HomeLoadResult {
            calls += 1
            return if (calls == 1) initial else pending.await()
        }

        fun complete(result: HomeLoadResult) = pending.complete(result)
    }

    private class FixedClock(var now: Long = 0L) : HomeEditorialClock {
        override fun nowMillis(): Long = now
        override suspend fun awaitUntil(deadlineMillis: Long) = awaitCancellation()
    }
}
