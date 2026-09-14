package com.gurbakir.mobile

import app.cash.turbine.test
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlan
import com.gurbakir.account.oauth.CustomerAccountAuthorizationPlanner
import com.gurbakir.account.oauth.CustomerAccountDiscoveryParser
import com.gurbakir.account.oauth.CustomerAccountDiscoveryResult
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.foundation.config.REQUIRED_CUSTOMER_ACCOUNT_SCOPES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerAccountProofViewModelTest {
    @Test
    fun `restore reports only authenticated proof state or signed out`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val signedOut = CustomerAccountProofViewModel(FakeProofController())
            advanceUntilIdle()

            assertEquals(CustomerAccountProofPhase.SIGNED_OUT, signedOut.state.value.phase)

            val authenticated =
                CustomerAccountProofViewModel(
                    FakeProofController(restoreResult = CustomerAccountProofResult.Authenticated)
                )
            advanceUntilIdle()

            assertEquals(CustomerAccountProofPhase.AUTHENTICATED, authenticated.state.value.phase)
            assertTrue(authenticated.state.value.canUseSessionActions)
        }
    }

    @Test
    fun `authorization preparation emits one redacted browser launch and cancellation clears transaction`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake = FakeProofController(
                preparation = CustomerAccountProofPreparation.Ready(authorizationPlan())
            )
            val viewModel = CustomerAccountProofViewModel(fake)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.startAuthorization()
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is CustomerAccountProofEffect.LaunchAuthorization)
                assertEquals("LaunchAuthorization(<redacted>)", effect.toString())
                assertEquals(CustomerAccountProofPhase.AWAITING_BROWSER, viewModel.state.value.phase)

                viewModel.consumeAuthorizationResult(null)
                assertEquals(CustomerAccountProofPhase.CANCELLED, viewModel.state.value.phase)
                assertEquals(1, fake.cancelCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `validated callback success enables session actions without exposing identity`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val fake = FakeProofController(
                preparation = CustomerAccountProofPreparation.Ready(authorizationPlan()),
                callbackResult = CustomerAccountProofResult.Authenticated
            )
            val viewModel = CustomerAccountProofViewModel(fake)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.startAuthorization()
                advanceUntilIdle()
                awaitItem()
                viewModel.consumeAuthorizationResult(
                    "shop.123456.gurbakir://oauth/callback?code=redacted&state=redacted"
                )
                advanceUntilIdle()

                assertEquals(CustomerAccountProofPhase.AUTHENTICATED, viewModel.state.value.phase)
                assertEquals(1, fake.callbackCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `refresh and remote logout failures remain explicit and fail closed`() = runTest {
        withMainDispatcher(StandardTestDispatcher(testScheduler)) {
            val refreshFailure = FakeProofController(
                restoreResult = CustomerAccountProofResult.Authenticated,
                refreshResult =
                    CustomerAccountProofResult.Failed(CustomerAccountProofFailure.TOKEN_REJECTED)
            )
            val refreshViewModel = CustomerAccountProofViewModel(refreshFailure)
            advanceUntilIdle()
            refreshViewModel.refresh()
            advanceUntilIdle()
            assertEquals(CustomerAccountProofPhase.TOKEN_FAILED, refreshViewModel.state.value.phase)

            val logoutFailure = FakeProofController(
                restoreResult = CustomerAccountProofResult.Authenticated,
                logoutResult =
                    CustomerAccountProofResult.Failed(CustomerAccountProofFailure.REMOTE_LOGOUT)
            )
            val logoutViewModel = CustomerAccountProofViewModel(logoutFailure)
            advanceUntilIdle()
            logoutViewModel.logout()
            advanceUntilIdle()
            assertEquals(CustomerAccountProofPhase.LOGOUT_REMOTE_FAILED, logoutViewModel.state.value.phase)
            assertTrue(logoutViewModel.state.value.canSignIn)
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
                redirectUri = "shop.123456.gurbakir://oauth/callback",
                userAgent = "Test-Android",
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

    private class FakeProofController(
        private val restoreResult: CustomerAccountProofResult = CustomerAccountProofResult.SignedOut,
        private val preparation: CustomerAccountProofPreparation =
            CustomerAccountProofPreparation.Failed(CustomerAccountProofFailure.DISCOVERY),
        private val callbackResult: CustomerAccountProofResult =
            CustomerAccountProofResult.Failed(CustomerAccountProofFailure.CALLBACK),
        private val refreshResult: CustomerAccountProofResult = CustomerAccountProofResult.Authenticated,
        private val logoutResult: CustomerAccountProofResult = CustomerAccountProofResult.SignedOut
    ) : CustomerAccountProofController {
        var cancelCount = 0
        var callbackCount = 0

        override suspend fun restore(): CustomerAccountProofResult = restoreResult

        override suspend fun prepareAuthorization(): CustomerAccountProofPreparation = preparation

        override suspend fun consumeCallback(rawRedirectUri: String): CustomerAccountProofResult {
            callbackCount += 1
            return callbackResult
        }

        override fun cancelAuthorization() {
            cancelCount += 1
        }

        override suspend fun refresh(): CustomerAccountProofResult = refreshResult

        override suspend fun logout(): CustomerAccountProofResult = logoutResult
    }

    private companion object {
        const val ISSUER = "https://shopify.com/authentication/123456"
        val VALID_OPEN_ID_DOCUMENT =
            """
            {
              "issuer": "$ISSUER",
              "authorization_endpoint": "https://shop.example/authentication/oauth/authorize",
              "token_endpoint": "https://shop.example/authentication/oauth/token",
              "end_session_endpoint": "https://shop.example/authentication/logout",
              "jwks_uri": "https://shop.example/authentication/.well-known/jwks.json",
              "code_challenge_methods_supported": ["S256"],
              "grant_types_supported": ["authorization_code", "refresh_token"],
              "id_token_signing_alg_values_supported": ["RS256"]
            }
            """.trimIndent()
        const val VALID_CUSTOMER_API_DOCUMENT =
            """{"graphql_api":"https://shop.example/customer/api/2026-07/graphql"}"""
    }
}
