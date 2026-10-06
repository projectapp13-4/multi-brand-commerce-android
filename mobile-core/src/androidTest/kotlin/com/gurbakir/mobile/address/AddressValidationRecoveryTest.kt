@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.address

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real address validation recovery across offscreen, fixed-header and IME boundaries. */
@RunWith(AndroidJUnit4::class)
class AddressValidationRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var fixture: AddressValidationRecoveryFixture

    @After
    fun clearOwnedViewModelAndKeyboard() {
        if (::fixture.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { fixture.close() }
        }
    }

    @Test
    fun requiredOffscreenFieldRecoversAtNormalFont() = verifyRequiredRecovery(1f, keyboard = false)

    @Test
    fun requiredOffscreenFieldRecoversAtDoubleFont() = verifyRequiredRecovery(2f, keyboard = false)

    @Test
    fun requiredOffscreenFieldRecoversAboveVisibleImeAtNormalFont() = verifyRequiredRecovery(1f, keyboard = true)

    @Test
    fun requiredOffscreenFieldRecoversAboveVisibleImeAtDoubleFont() = verifyRequiredRecovery(2f, keyboard = true)

    @Test
    fun deferredServerRejectionRecoversOffscreenFieldAtNormalFont() = verifyServerRecovery(1f, keyboard = false)

    @Test
    fun deferredServerRejectionRecoversOffscreenFieldAtDoubleFont() = verifyServerRecovery(2f, keyboard = false)

    @Test
    fun deferredServerRejectionRecoversAboveVisibleImeAtNormalFont() = verifyServerRecovery(1f, keyboard = true)

    @Test
    fun deferredServerRejectionRecoversAboveVisibleImeAtDoubleFont() = verifyServerRecovery(2f, keyboard = true)

    @Test
    fun deferredFormAndFieldRejectionRecoversWithFeedbackHeaderAtDoubleFont() =
        verifyServerRecovery(2f, keyboard = false, formFeedback = true)

    @Test
    fun initiallyVisibleRequiredFieldStillFocusesWithoutControllerSave() {
        showScreen(fontScale = 1f)
        composeRule.onNodeWithTag(FIRST_NAME).assertIsDisplayed()
        composeRule.runOnIdle { fixture.viewModel.save() }
        awaitFieldError(AddressFieldError.REQUIRED)
        assertRecovery(AddressFieldError.REQUIRED, keyboard = false)
        assertEquals(0, fixture.controller.saves.get())
    }

    @Test
    fun normalImeNextStillMovesFromFirstNameToLastName() {
        showScreen(fontScale = 1f)
        composeRule.onNodeWithTag(FIRST_NAME).performClick().performImeAction()
        composeRule.onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.LAST_NAME))
            .assertIsFocused()
        assertEquals(0, fixture.controller.saves.get())
    }

    @Test
    fun bottomOfLargeTextFormKeepsBackReachableWithoutSaving() {
        showScreen(fontScale = 2f)
        scrollTo(PHONE)
        composeRule.onNodeWithTag(AddressFormTestTags.BACK).assertIsDisplayed().performDeterministicClick()
        assertEquals(1, fixture.backCalls.get())
        assertEquals(0, fixture.controller.saves.get())
    }

    @Test
    fun turkishDoubleFontServerRejectionRecoversWholeErrorAboveVisibleImeInFullHeightForm() {
        showTurkishFullHeightScreen()
        composeRule.runOnIdle { fixture.fillValidDraft() }
        arrangeSave(keyboard = true)
        assertFirstNameDisposed()
        composeRule.onNodeWithTag(AddressFormTestTags.SAVE).performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.controller.saveEntered.isCompleted }
        assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
        assertEquals(VALID_ADDRESS_RECOVERY_DRAFT, fixture.controller.savedInput)
        assertEquals(1, fixture.controller.saves.get())
        composeRule.runOnIdle {
            fixture.controller.saveReply.complete(
                AddressActionResult.Rejected(setOf(CustomerAddressField.FIRST_NAME), emptySet())
            )
        }
        awaitFieldError(AddressFieldError.SERVER_REJECTED)
        assertEquals(VALID_ADDRESS_RECOVERY_DRAFT, fixture.viewModel.state.value.input)
        assertEquals("Synthetic rejection must not replay even an inert save", 1, fixture.controller.saves.get())
        assertNoOtherMutation()
        assertTurkishFullRecoveryUnion()
        assertRecovery(AddressFieldError.SERVER_REJECTED, keyboard = true)
    }

    private fun verifyRequiredRecovery(fontScale: Float, keyboard: Boolean) {
        showScreen(fontScale)
        arrangeSave(keyboard)
        val draft = fixture.viewModel.state.value.input
        assertFirstNameDisposed()
        composeRule.onNodeWithTag(AddressFormTestTags.SAVE).performDeterministicClick()
        awaitFieldError(AddressFieldError.REQUIRED)
        assertEquals(draft, fixture.viewModel.state.value.input)
        assertEquals(0, fixture.controller.saves.get())
        assertNoOtherMutation()
        assertRecovery(AddressFieldError.REQUIRED, keyboard)
    }

    private fun verifyServerRecovery(fontScale: Float, keyboard: Boolean, formFeedback: Boolean = false) {
        showScreen(fontScale, serverRejection = true)
        composeRule.runOnIdle { fixture.fillValidDraft() }
        arrangeSave(keyboard)
        assertFirstNameDisposed()
        composeRule.onNodeWithTag(AddressFormTestTags.SAVE).performDeterministicClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.controller.saveEntered.isCompleted }
        assertEquals(AddressFormPhase.SAVING, fixture.viewModel.state.value.phase)
        assertEquals(VALID_ADDRESS_RECOVERY_DRAFT, fixture.controller.savedInput)
        assertEquals(1, fixture.controller.saves.get())
        val rejected = if (formFeedback) {
            setOf(CustomerAddressField.FIRST_NAME, CustomerAddressField.FORM)
        } else {
            setOf(CustomerAddressField.FIRST_NAME)
        }
        composeRule.runOnIdle {
            fixture.controller.saveReply.complete(AddressActionResult.Rejected(rejected, emptySet()))
        }
        awaitFieldError(AddressFieldError.SERVER_REJECTED)
        assertEquals(VALID_ADDRESS_RECOVERY_DRAFT, fixture.viewModel.state.value.input)
        assertEquals("Synthetic rejection must not replay even an inert save", 1, fixture.controller.saves.get())
        assertNoOtherMutation()
        assertRecovery(AddressFieldError.SERVER_REJECTED, keyboard)
    }

    private fun showScreen(fontScale: Float, serverRejection: Boolean = false) {
        fixture = AddressValidationRecoveryFixture(serverRejection)
        composeRule.setContent {
            val state by fixture.viewModel.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            val view = LocalView.current
            SideEffect { fixture.hostView = view }
            CoreTestTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().imePadding()) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(max = 320.dp).clipToBounds().testTag(BOUNDED_HOST)
                        ) {
                            AddressFormScreen(state, fixture.actions())
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { fixture.viewModel.start(null) }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.viewModel.state.value.loaded }
        assertEquals(1, fixture.controller.loads.get())
        assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
        assertTrue(fixture.viewModel.state.value.canSave)
    }

    private fun showTurkishFullHeightScreen() {
        fixture = AddressValidationRecoveryFixture(serverRejection = true)
        val localizedContext = turkishDoubleFontRecoveryContext()
        fixture.recoveryResourceContext = localizedContext
        assertEquals("Ad", localizedContext.getString(R.string.address_first_name))
        assertEquals(
            "Shopify bu alanı kabul etmedi. Değeri kontrol edin.",
            localizedContext.getString(R.string.address_error_server_rejected)
        )
        assertEquals(2f, localizedContext.resources.configuration.fontScale, 0f)
        composeRule.setContent {
            val state by fixture.viewModel.state.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            val view = LocalView.current
            SideEffect { fixture.hostView = view }
            CoreTestTheme {
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalResources provides localizedContext.resources,
                    LocalDensity provides Density(density.density, 2f)
                ) {
                    Box(Modifier.fillMaxSize().imePadding()) {
                        Box(Modifier.fillMaxSize().clipToBounds().testTag(BOUNDED_HOST)) {
                            AddressFormScreen(state, fixture.actions())
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { fixture.viewModel.start(null) }
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.viewModel.state.value.loaded }
        assertEquals(1, fixture.controller.loads.get())
        assertEquals(AddressFormPhase.READY, fixture.viewModel.state.value.phase)
        assertTrue(fixture.viewModel.state.value.canSave)
    }

    private fun assertTurkishFullRecoveryUnion() {
        // Only observations after Save/rejection: no scroll, focus, keyboard action or additional Save.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FIRST_NAME).fetchSemanticsNodes().any { node ->
                node.config.contains(SemanticsProperties.Focused) && node.config[SemanticsProperties.Focused]
            }
        }
        val nativeImeAtFocus = composeRule.runOnIdle { fixture.imeVisible() }
        android.util.Log.i("W4_TR2_IME", "native_ime_at_focus=$nativeImeAtFocus")
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.runOnIdle { fixture.imeVisible() } }
        val nativeImeAfterWait = composeRule.runOnIdle { fixture.imeVisible() }
        android.util.Log.i("W4_TR2_IME", "native_ime_after_wait=$nativeImeAfterWait")
        val context = checkNotNull(fixture.recoveryResourceContext)
        val field = composeRule.onNodeWithTag(FIRST_NAME).assertIsFocused()
            .fetchSemanticsNode().fullBoundsInRoot()
        val label = composeRule.onNode(
            hasText(context.getString(R.string.address_first_name)) and hasAnyAncestor(hasTestTag(FIRST_NAME)),
            useUnmergedTree = true
        ).fetchSemanticsNode().fullBoundsInRoot()
        val errorText = context.getString(R.string.address_error_server_rejected)
        val error = composeRule.onNode(
            hasText(errorText) and hasAnyAncestor(hasTestTag(FIRST_NAME)),
            useUnmergedTree = true
        ).fetchSemanticsNode().fullBoundsInRoot()
        val host = composeRule.onNodeWithTag(BOUNDED_HOST).fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag(AddressFormTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
        val back = composeRule.onNodeWithTag(AddressFormTestTags.BACK, useUnmergedTree = true).fetchSemanticsNode()
        val header = pinnedTopAppBarBounds(back, field, host)
        val visible = Rect()
        val location = IntArray(2)
        composeRule.runOnIdle {
            assertTrue("SETUP: real native IME remains visible", fixture.imeVisible())
            fixture.hostView.getWindowVisibleDisplayFrame(visible)
            fixture.hostView.getLocationOnScreen(location)
        }
        val effective = ComposeRect(
            maxOf(host.left, viewport.left, (visible.left - location[0]).toFloat()),
            maxOf(host.top, viewport.top, header.bottom, (visible.top - location[1]).toFloat()),
            minOf(host.right, viewport.right, (visible.right - location[0]).toFloat()),
            minOf(host.bottom, viewport.bottom, (visible.bottom - location[1]).toFloat())
        )
        val union = ComposeRect(
            minOf(field.left, label.left, error.left),
            minOf(field.top, label.top, error.top),
            maxOf(field.right, label.right, error.right),
            maxOf(field.bottom, label.bottom, error.bottom)
        )
        assertTrue("SETUP: nonzero native/header/IME intersection", effective.width > 0 && effective.height > 0)
        assertTrue(
            "SETUP: actual full label/input/error union can fit in available width",
            union.width <= effective.width
        )
        assertTrue(
            "SETUP: actual full label/input/error union can fit in available height",
            union.height <= effective.height
        )
        assertFullyInside("Automatic full Turkish label/input/error union recovery", union, effective)
    }

    private fun arrangeSave(keyboard: Boolean) {
        if (keyboard) {
            scrollTo(PHONE)
            composeRule.onNodeWithTag(PHONE).performClick().performTextReplacement("+905550000000")
            composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.runOnIdle { fixture.imeVisible() } }
        }
        scrollTo(AddressFormTestTags.SAVE)
        composeRule.onNodeWithTag(AddressFormTestTags.SAVE).assertIsDisplayed()
        assertEquals(keyboard, composeRule.runOnIdle { fixture.imeVisible() })
        assertTrue(fixture.viewModel.state.value.canSave)
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(AddressFormTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    }

    private fun assertFirstNameDisposed() {
        composeRule.onNodeWithTag(FIRST_NAME).assertDoesNotExist()
        composeRule.onNodeWithTag(FIRST_NAME, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, fixture.focusAcknowledgments.get())
    }

    private fun awaitFieldError(error: AddressFieldError) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fixture.viewModel.state.value.fieldErrors[CustomerAddressField.FIRST_NAME] == error
        }
    }

    private fun assertRecovery(error: AddressFieldError, keyboard: Boolean) {
        // No test scroll, focus or keyboard action occurs after Save/rejection.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FIRST_NAME).fetchSemanticsNodes().any { node ->
                node.config.contains(SemanticsProperties.Focused) && node.config[SemanticsProperties.Focused]
            }
        }
        val field = composeRule.onNodeWithTag(FIRST_NAME).assertIsFocused().assertIsDisplayed()
        val errorText = composeRule.runOnIdle {
            (fixture.recoveryResourceContext ?: fixture.hostView.context).getString(error.messageResource())
        }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, errorText))
        val supportingError = composeRule.onNode(
            hasText(errorText) and hasAnyAncestor(hasTestTag(FIRST_NAME)),
            useUnmergedTree = true
        ).assertIsDisplayed()
        val fieldBounds = field.fetchSemanticsNode().fullBoundsInRoot()
        val errorBounds = supportingError.fetchSemanticsNode().fullBoundsInRoot()
        val viewportBounds = composeRule.onNodeWithTag(AddressFormTestTags.CONTENT)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hostBounds = composeRule.onNodeWithTag(BOUNDED_HOST).fetchSemanticsNode().boundsInRoot
        assertFullyInside("Recovered field inside visible form viewport", fieldBounds, viewportBounds)
        assertFullyInside("Supporting error inside visible form viewport", errorBounds, viewportBounds)
        assertFullyInside("Recovered field inside bounded host", fieldBounds, hostBounds)
        assertFullyInside("Supporting error inside bounded host", errorBounds, hostBounds)
        assertEquals(1, fixture.focusAcknowledgments.get())
        assertNull(fixture.viewModel.state.value.focusRequest)
        assertFalse(fixture.viewModel.state.value.busy)
        if (keyboard) assertAboveActuallyVisibleIme(fieldBounds, errorBounds)
        composeRule.onNodeWithTag(AddressFormTestTags.BACK).assertIsDisplayed()
        val back = composeRule.onNodeWithTag(AddressFormTestTags.BACK, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode()
        val headerBounds = pinnedTopAppBarBounds(back, fieldBounds, hostBounds)
        val labelText = composeRule.runOnIdle {
            (fixture.recoveryResourceContext ?: fixture.hostView.context).getString(R.string.address_first_name)
        }
        val labelBounds = composeRule.onNode(
            hasText(labelText) and hasAnyAncestor(hasTestTag(FIRST_NAME)),
            useUnmergedTree = true
        ).assertIsDisplayed().fetchSemanticsNode().fullBoundsInRoot()
        assertTrue("Form viewport excludes the complete fixed header", viewportBounds.top >= headerBounds.bottom)
        assertTrue("Recovered field below complete fixed header", fieldBounds.top >= headerBounds.bottom)
        assertTrue("Recovered floating label below complete fixed header", labelBounds.top >= headerBounds.bottom)
        assertTrue("Supporting error below complete fixed header", errorBounds.top >= headerBounds.bottom)
        assertFullyInside("Recovered floating label inside visible form viewport", labelBounds, viewportBounds)
        assertFullyInside("Recovered floating label inside bounded host", labelBounds, hostBounds)
        if (keyboard) assertAboveActuallyVisibleIme(labelBounds, errorBounds)
    }

    private fun assertAboveActuallyVisibleIme(fieldBounds: ComposeRect, errorBounds: ComposeRect) {
        val visible = Rect()
        val location = IntArray(2)
        composeRule.runOnIdle {
            assertTrue("IME must remain actually visible for this matrix row", fixture.imeVisible())
            fixture.hostView.getWindowVisibleDisplayFrame(visible)
            fixture.hostView.getLocationOnScreen(location)
        }
        val visibleInRoot = ComposeRect(
            (visible.left - location[0]).toFloat(),
            (visible.top - location[1]).toFloat(),
            (visible.right - location[0]).toFloat(),
            (visible.bottom - location[1]).toFloat()
        )
        assertFullyInside("Recovered field inside native visible frame above IME", fieldBounds, visibleInRoot)
        assertFullyInside("Supporting error inside native visible frame above IME", errorBounds, visibleInRoot)
    }

    private fun assertNoOtherMutation() {
        assertEquals(0, fixture.controller.defaults.get())
        assertEquals(0, fixture.controller.deletes.get())
    }

    private companion object {
        const val BOUNDED_HOST = "address-validation-bounded-host"
        val FIRST_NAME = AddressFormTestTags.fields.getValue(CustomerAddressField.FIRST_NAME)
        val PHONE = AddressFormTestTags.fields.getValue(CustomerAddressField.PHONE)
    }
}

private fun SemanticsNode.fullBoundsInRoot(): ComposeRect {
    val position = positionInRoot
    val dimensions = size
    return ComposeRect(position.x, position.y, position.x + dimensions.width, position.y + dimensions.height)
}

private fun pinnedTopAppBarBounds(back: SemanticsNode, field: ComposeRect, host: ComposeRect): ComposeRect {
    val header = checkNotNull(
        generateSequence(back.parent) { it.parent }.firstOrNull { node ->
            node.config.contains(SemanticsProperties.IsTraversalGroup) &&
                node.config[SemanticsProperties.IsTraversalGroup]
        }
    ) { "SETUP: pinned Material3 full TopAppBar traversal ancestor is absent" }
    val bounds = header.fullBoundsInRoot()
    assertTrue(
        "SETUP: complete header has positive height below host height",
        bounds.height > 0 && bounds.height < host.height
    )
    assertTrue("SETUP: complete header width contains recovered field width", bounds.width >= field.width)
    assertFullyInside("SETUP: complete header inside bounded host", bounds, host)
    assertFullyInside("SETUP: Back action inside complete header", back.fullBoundsInRoot(), bounds)
    return bounds
}

private fun assertFullyInside(message: String, bounds: ComposeRect, viewport: ComposeRect) {
    assertTrue("$message: nonzero full bounds", bounds.width > 0f && bounds.height > 0f)
    assertTrue("$message: nonzero visible viewport", viewport.width > 0f && viewport.height > 0f)
    assertTrue("$message: left edge", bounds.left >= viewport.left)
    assertTrue("$message: top edge", bounds.top >= viewport.top)
    assertTrue("$message: right edge", bounds.right <= viewport.right)
    assertTrue("$message: bottom edge", bounds.bottom <= viewport.bottom)
}

private fun turkishDoubleFontRecoveryContext(): Context {
    val initial = InstrumentationRegistry.getInstrumentation().targetContext
    val configuration = Configuration(initial.resources.configuration)
    val locale = Locale.forLanguageTag("tr")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.setLocales(LocaleList(locale))
    } else {
        configuration.setLocale(locale)
    }
    configuration.fontScale = 2f
    return initial.createConfigurationContext(configuration)
}
