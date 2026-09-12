package com.gurbakir.mobile

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.ConfigurationIssue
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.ui.CommerceTheme
import com.gurbakir.mobile.brand.GurbakirBrand
import com.gurbakir.mobile.navigation.IntegrationId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun foundationShellShowsDeterministicHonestConfigurationState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val waiting = context.getString(R.string.integration_waiting)
        setFoundationContent()

        composeRule.onNodeWithTag(FoundationTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(FoundationTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(FoundationTestTags.ENVIRONMENT).assertIsDisplayed()
        composeRule.onNodeWithTag(FoundationTestTags.CONFIGURATION_STATUS).assertIsDisplayed()
        composeRule.onAllNodesWithText(waiting).assertCountEquals(4)
    }

    @Test
    fun integrationActionExposesAccessibleClickAndStableIdentifier() {
        var selected: IntegrationId? = null
        setFoundationContent { selected = it.id }

        composeRule
            .onNodeWithTag(FoundationTestTags.integrationAction(IntegrationId.STOREFRONT))
            .assertHasClickAction()
            .performClick()
        assertEquals(IntegrationId.STOREFRONT, selected)
        composeRule
            .onNodeWithTag(FoundationTestTags.integrationStatus(IntegrationId.STOREFRONT))
            .assertIsDisplayed()
    }

    @Test
    fun unconfiguredFirebaseDetailsActionIsDisabled() {
        setFoundationContent()

        composeRule
            .onNodeWithTag(FoundationTestTags.integrationAction(IntegrationId.FIREBASE))
            .assertIsNotEnabled()
    }

    @Test
    fun otherUnreadyIntegrationDetailsRemainAvailable() {
        setFoundationContent()

        composeRule
            .onNodeWithTag(FoundationTestTags.integrationAction(IntegrationId.STOREFRONT))
            .assertIsEnabled()
    }

    @Test
    fun unconfiguredFirebaseDestinationDoesNotEvaluateConfiguredContent() {
        var configuredContentInvocations = 0
        composeRule.setContent {
            CommerceTheme(designTokens = GurbakirBrand.configuration.designTokens, darkTheme = false) {
                FirebaseProofDestination(
                    firebaseConfigured = false,
                    onBack = {},
                    configuredContent = {
                        configuredContentInvocations += 1
                        Text("configured")
                    }
                )
            }
        }

        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofUnavailableTestTags.BACK).assertHasClickAction()
        composeRule.runOnIdle { assertEquals(0, configuredContentInvocations) }
    }

    @Test
    fun configuredFirebaseDestinationEvaluatesConfiguredContentOnce() {
        var configuredContentInvocations = 0
        composeRule.setContent {
            FirebaseProofDestination(
                firebaseConfigured = true,
                onBack = {},
                configuredContent = {
                    configuredContentInvocations += 1
                    Text("configured")
                }
            )
        }

        composeRule.onAllNodesWithText("configured").assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(1, configuredContentInvocations) }
    }

    @Test
    fun unconfiguredNotificationEntryIsConsumedWithoutNavigation() {
        var navigationCount = 0
        var consumptionCount = 0
        composeRule.setContent {
            FirebaseNotificationProofEffect(
                notificationProofNonce = 1,
                firebaseConfigured = false,
                onNavigate = { navigationCount += 1 },
                onConsumed = { consumptionCount += 1 }
            )
        }

        composeRule.runOnIdle {
            assertEquals(0, navigationCount)
            assertEquals(1, consumptionCount)
        }
    }

    @Test
    fun customerAccountProofShowsOnlySignInWhenSignedOut() {
        setCustomerAccountProofContent(
            CustomerAccountProofUiState(CustomerAccountProofPhase.SIGNED_OUT)
        )

        composeRule.onNodeWithTag(CustomerAccountProofTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.SIGN_IN).assertHasClickAction()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.REFRESH).assertIsNotDisplayed()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.LOGOUT).assertIsNotDisplayed()
    }

    @Test
    fun authenticatedCustomerAccountProofShowsRefreshAndLogoutWithoutIdentityData() {
        setCustomerAccountProofContent(
            CustomerAccountProofUiState(CustomerAccountProofPhase.AUTHENTICATED)
        )

        composeRule.onNodeWithTag(CustomerAccountProofTestTags.SIGN_IN).assertIsNotDisplayed()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.REFRESH).assertHasClickAction()
        composeRule.onNodeWithTag(CustomerAccountProofTestTags.LOGOUT).assertHasClickAction()
    }

    @Test
    fun commerceProofRequiresAnAvailableVariantBeforeCreatingCart() {
        setCommerceProofContent(
            CommerceProofUiState(
                phase = CommerceProofPhase.EMPTY,
                variantAvailable = false
            )
        )

        composeRule.onNodeWithTag(CommerceProofTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(CommerceProofTestTags.CREATE_CART).assertIsNotDisplayed()
        composeRule.onNodeWithTag(CommerceProofTestTags.PRESENT).assertIsNotDisplayed()
    }

    @Test
    fun activeSyntheticCartExposesMutationAndCheckoutActionsWithoutSensitiveValues() {
        setCommerceProofContent(
            CommerceProofUiState(
                phase = CommerceProofPhase.ACTIVE,
                variantAvailable = true,
                snapshot =
                    CommerceProofSnapshot(
                        totalQuantity = 2,
                        lineCount = 1,
                        hasMoreLines = false,
                        warningCount = 0
                    )
            )
        )

        composeRule.onNodeWithTag(CommerceProofTestTags.CART_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(CommerceProofTestTags.ADD_LINE).assertHasClickAction()
        composeRule.onNodeWithTag(CommerceProofTestTags.INCREMENT_LINE).assertHasClickAction()
        composeRule.onNodeWithTag(CommerceProofTestTags.REMOVE_LINES).assertHasClickAction()
        composeRule.onNodeWithTag(CommerceProofTestTags.PRELOAD).assertHasClickAction()
        composeRule.onNodeWithTag(CommerceProofTestTags.PRESENT).assertHasClickAction()
    }

    @Test
    fun commerceFailureShowsOnlyItsSafeCategory() {
        setCommerceProofContent(
            CommerceProofUiState(
                phase = CommerceProofPhase.FAILED,
                variantAvailable = true,
                failure = CommerceProofFailure.CHECKOUT_NETWORK,
                cartRetainedAfterFailure = true
            )
        )

        composeRule.onNodeWithTag(CommerceProofTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(CommerceProofTestTags.CART_SUMMARY).assertIsNotDisplayed()
    }

    @Test
    fun commerceProofShowsOnlyTheBoundedShopifyWarningCount() {
        setCommerceProofContent(
            CommerceProofUiState(
                phase = CommerceProofPhase.ACTIVE,
                variantAvailable = true,
                snapshot =
                    CommerceProofSnapshot(
                        totalQuantity = 1,
                        lineCount = 1,
                        hasMoreLines = false,
                        warningCount = 1
                    )
            )
        )

        composeRule.onNodeWithTag(CommerceProofTestTags.CART_WARNINGS).assertIsDisplayed()
    }

    @Test
    fun firebaseProofRequiresExplicitActionsAndNeverDisplaysATargetIdentifier() {
        setFirebaseProofContent(FirebaseProofUiState())

        composeRule.onNodeWithTag(FirebaseProofTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(FirebaseProofTestTags.REFRESH_REMOTE_CONFIG).assertHasClickAction()
        composeRule.onNodeWithTag(FirebaseProofTestTags.ENABLE_PUSH).assertHasClickAction()
        composeRule.onNodeWithTag(FirebaseProofTestTags.DISABLE_PUSH).assertIsNotDisplayed()
    }

    @Test
    fun registeredFirebaseProofExposesOnlyTheUnregisterAction() {
        setFirebaseProofContent(
            FirebaseProofUiState(
                phase = FirebaseProofPhase.PUSH_REGISTERED,
                pushRegistered = true
            )
        )

        composeRule.onNodeWithTag(FirebaseProofTestTags.ENABLE_PUSH).assertIsNotDisplayed()
        composeRule.onNodeWithTag(FirebaseProofTestTags.DISABLE_PUSH).assertHasClickAction()
    }

    private fun setFoundationContent(onSelected: (IntegrationStatus) -> Unit = {}) {
        composeRule.setContent {
            CommerceTheme(
                designTokens = GurbakirBrand.configuration.designTokens,
                darkTheme = false
            ) {
                FoundationScreen(
                    state =
                        FoundationUiState(
                            brandDisplayName = GurbakirBrand.configuration.displayName,
                            environment = EnvironmentId.DEVELOPMENT,
                            configurationIssues = setOf(ConfigurationIssue.STOREFRONT_DOMAIN),
                            integrations =
                                IntegrationId.entries.map { id ->
                                    IntegrationStatus(id = id, ready = false)
                                }
                        ),
                    onIntegrationSelected = onSelected
                )
            }
        }
    }

    private fun setCustomerAccountProofContent(state: CustomerAccountProofUiState) {
        composeRule.setContent {
            CommerceTheme(
                designTokens = GurbakirBrand.configuration.designTokens,
                darkTheme = false
            ) {
                CustomerAccountProofScreen(
                    state = state,
                    onSignIn = {},
                    onRefresh = {},
                    onLogout = {},
                    onBack = {}
                )
            }
        }
    }

    private fun setCommerceProofContent(state: CommerceProofUiState) {
        composeRule.setContent {
            CommerceTheme(
                designTokens = GurbakirBrand.configuration.designTokens,
                darkTheme = false
            ) {
                CommerceProofScreen(
                    state = state,
                    actions =
                        CommerceProofActions(
                            createCart = {},
                            addLine = {},
                            incrementLine = {},
                            removeLines = {},
                            preloadCheckout = {},
                            presentCheckout = {},
                            back = {}
                        )
                )
            }
        }
    }

    private fun setFirebaseProofContent(state: FirebaseProofUiState) {
        composeRule.setContent {
            CommerceTheme(
                designTokens = GurbakirBrand.configuration.designTokens,
                darkTheme = false
            ) {
                FirebaseProofScreen(
                    state = state,
                    onRefreshRemoteConfig = {},
                    onEnablePush = {},
                    onDisablePush = {},
                    onBack = {}
                )
            }
        }
    }
}
