package com.gurbakir.mobile.account

import app.cash.turbine.test
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlan
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlanner
import com.gurbakir.account.oauth.CustomerAccountDiscoveryParser
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Test
    fun `hosted launch cancellation returns to signed out state and clears the transaction`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake =
                FakeAccountController(
                    preparation = AccountPreparation.Ready(authorizationPlan())
                )
            val viewModel = AccountViewModel(fake)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.startAuthorization()
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is AccountEffect.LaunchAuthorization)
                assertEquals("LaunchAuthorization(<redacted>)", effect.toString())
                assertEquals(AccountPhase.AWAITING_BROWSER, viewModel.state.value.phase)

                viewModel.consumeAuthorizationResult(null)

                assertEquals(AccountPhase.SIGNED_OUT, viewModel.state.value.phase)
                assertEquals(setOf(AccountNotice.AUTHORIZATION_CANCELLED), viewModel.state.value.notices)
                assertEquals(1, fake.cancelCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `awaiting browser callback is consumed once`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val summary = AccountSummary("Authorized Customer")
            val fake =
                FakeAccountController(
                    preparation = AccountPreparation.Ready(authorizationPlan()),
                    callbackResult = AccountResult.Authenticated(summary)
                )
            val viewModel = AccountViewModel(fake)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.startAuthorization()
                advanceUntilIdle()
                awaitItem()

                viewModel.consumeAuthorizationResult(CALLBACK_URI)
                advanceUntilIdle()

                assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
                assertEquals(summary, viewModel.state.value.summary)
                assertEquals(1, fake.callbackCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `transient refresh keeps the in memory summary while logout removes it`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val summary = AccountSummary("Test Customer")
            val fake =
                FakeAccountController(
                    restoreResult = AccountResult.Authenticated(summary),
                    refreshResult =
                        AccountResult.Failed(
                            AccountFailure.IDENTITY_TRANSPORT,
                            retryable = true,
                            sessionRetained = true
                        ),
                    logoutResult =
                        AccountResult.SignedOut(setOf(AccountNotice.REMOTE_LOGOUT_UNVERIFIED))
                )
            val viewModel = AccountViewModel(fake)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
            assertEquals(summary, viewModel.state.value.summary)
            assertEquals(AccountFailure.IDENTITY_TRANSPORT, viewModel.state.value.failure)

            viewModel.logout()
            advanceUntilIdle()

            assertEquals(AccountPhase.SIGNED_OUT, viewModel.state.value.phase)
            assertEquals(null, viewModel.state.value.summary)
            assertEquals(setOf(AccountNotice.REMOTE_LOGOUT_UNVERIFIED), viewModel.state.value.notices)
        }
    }

    @Test
    fun `validated callback after recreation wins over the stale initial restore`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val restoreGate = CompletableDeferred<Unit>()
            val summary = AccountSummary("Restored Customer")
            val fake =
                FakeAccountController(
                    restoreResult = AccountResult.SignedOut(),
                    callbackResult = AccountResult.Authenticated(summary),
                    restoreGate = restoreGate
                )
            val viewModel = AccountViewModel(fake)
            runCurrent()

            viewModel.consumeAuthorizationResult(CALLBACK_URI)
            advanceUntilIdle()

            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
            assertEquals(summary, viewModel.state.value.summary)
            assertEquals(1, fake.callbackCount)

            restoreGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
            assertEquals(summary, viewModel.state.value.summary)
        }
    }

    @Test
    fun `authenticated callback replay is ignored without replacing the summary`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val authenticatedSummary = AccountSummary("Authenticated Customer")
            val fake =
                FakeAccountController(
                    restoreResult = AccountResult.Authenticated(authenticatedSummary),
                    callbackResult = AccountResult.Authenticated(AccountSummary("Replayed Customer"))
                )
            val viewModel = AccountViewModel(fake)
            advanceUntilIdle()

            repeat(2) { viewModel.consumeAuthorizationResult(CALLBACK_URI) }
            advanceUntilIdle()

            assertEquals(AccountPhase.AUTHENTICATED, viewModel.state.value.phase)
            assertEquals(authenticatedSummary, viewModel.state.value.summary)
            assertEquals(0, fake.callbackCount)
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

    private fun authorizationPlan(): CustomerAccountAuthorizationPlan {
        val configuration =
            CustomerAccountConfiguration(
                clientId = "public-client-id",
                issuer = ISSUER,
                authorizationEndpoint = "https://shop.example/authentication/oauth/authorize",
                tokenEndpoint = "https://shop.example/authentication/oauth/token",
                logoutEndpoint = "https://shop.example/authentication/logout",
                graphqlEndpoint = "https://shop.example/customer/api/2026-07/graphql",
                redirectUri = "shop.123456.example://oauth/callback",
                scopes = REQUIRED_CUSTOMER_ACCOUNT_SCOPES
            )
        val discovery =
            CustomerAccountDiscoveryParser().parse(
                expectedIssuer = ISSUER,
                openIdDocument = VALID_OPEN_ID_DOCUMENT,
                customerApiDocument = VALID_CUSTOMER_API_DOCUMENT
            ) as CustomerAccountDiscoveryResult.Success
        return CustomerAccountAuthorizationPlanner(configuration).prepare(discovery.configuration)
    }

    private class FakeAccountController(
        private val restoreResult: AccountResult = AccountResult.SignedOut(),
        private val preparation: AccountPreparation = AccountPreparation.Failed(AccountFailure.DISCOVERY),
        private val callbackResult: AccountResult =
            AccountResult.Failed(AccountFailure.CALLBACK, retryable = true, sessionRetained = false),
        private val refreshResult: AccountResult = AccountResult.SignedOut(),
        private val logoutResult: AccountResult = AccountResult.SignedOut(),
        private val restoreGate: CompletableDeferred<Unit>? = null
    ) : AccountController {
        var cancelCount = 0
        var callbackCount = 0

        override suspend fun restore(): AccountResult {
            restoreGate?.await()
            return restoreResult
        }

        override suspend fun prepareAuthorization(): AccountPreparation = preparation

        override suspend fun consumeCallback(rawRedirectUri: String): AccountResult {
            callbackCount += 1
            return callbackResult
        }

        override fun cancelAuthorization() {
            cancelCount += 1
        }

        override suspend fun refresh(): AccountResult = refreshResult

        override suspend fun logout(): AccountResult = logoutResult
    }

    private companion object {
        const val ISSUER = "https://shopify.com/authentication/123456"
        const val CALLBACK_URI =
            "shop.123456.example://oauth/callback?code=synthetic-code&state=synthetic-state"
        const val VALID_OPEN_ID_DOCUMENT =
            """{
                "issuer":"https://shopify.com/authentication/123456",
                "authorization_endpoint":"https://shop.example/authentication/oauth/authorize",
                "token_endpoint":"https://shop.example/authentication/oauth/token",
                "end_session_endpoint":"https://shop.example/authentication/logout",
                "jwks_uri":"https://shop.example/authentication/.well-known/jwks.json",
                "grant_types_supported":["authorization_code","refresh_token"],
                "code_challenge_methods_supported":["S256"],
                "id_token_signing_alg_values_supported":["RS256"]
            }"""
        const val VALID_CUSTOMER_API_DOCUMENT =
            """{"graphql_api":"https://shop.example/customer/api/2026-07/graphql"}"""
    }
}
