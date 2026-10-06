@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.gurbakir.mobile.accessibility

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.address.AddressFormActions
import com.gurbakir.mobile.address.AddressFormPhase
import com.gurbakir.mobile.address.AddressFormScreen
import com.gurbakir.mobile.address.AddressFormTestTags
import com.gurbakir.mobile.address.AddressFormUiState
import com.gurbakir.mobile.address.PostalCodeInputMode
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import com.gurbakir.mobile.search.SearchActions
import com.gurbakir.mobile.search.SearchScreen
import com.gurbakir.mobile.search.SearchTestTags
import com.gurbakir.mobile.search.SearchUiState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

private const val MINIMUM_TARGET_DP = 48f
private const val CHECKBOX_VISUAL_REGION_DP = 24f

/** Test cases select resources/density locally; they never change emulator or production locale settings. */
data class ControlSemanticCase(val language: String, val fontScale: Float, val initiallyChecked: Boolean) {
    override fun toString(): String = "$language-${fontScale}x-${if (initiallyChecked) "on" else "off"}"
}

enum class SearchHistoryAvailability {
    AVAILABLE,
    UNAVAILABLE,
    LOADING
}

internal fun controlSemanticCases(): List<ControlSemanticCase> = listOf("tr", "en").flatMap { language ->
    listOf(1f, 2f).flatMap { scale ->
        listOf(false, true).map { checked -> ControlSemanticCase(language, scale, checked) }
    }
}

/** Real screens, controlled UI state and callback ledger. No gateway, ViewModel facade or provider is composed. */
internal class ControlSemanticsHarness(private val case: ControlSemanticCase) {
    val rule = createComposeRule()
    val checked = mutableStateOf(case.initiallyChecked)
    val changes = mutableListOf<Boolean>()
    val unrelatedActions = mutableListOf<String>()
    val resourceContext = InstrumentationRegistry.getInstrumentation().targetContext.run {
        val requested = Configuration(resources.configuration)
        requested.setLocale(Locale.forLanguageTag(case.language))
        requested.fontScale = case.fontScale
        createConfigurationContext(requested)
    }
    private var purposeResource = R.string.address_make_default
    private var tag = AddressFormTestTags.MAKE_DEFAULT
    private var enabled = true
    private var role = Role.Checkbox
    private var density = 1f
    private var visualIsLeading = true
    private lateinit var hostView: View
    private lateinit var inputModeManager: InputModeManager
    private val onChanged: (Boolean) -> Unit = {
        changes += it
        checked.value = it
    }
    val purpose: String get() = resourceContext.getString(purposeResource)
    val actionable get() = rule.onNode(isToggleable() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    fun showAddress(phase: AddressFormPhase) {
        enabled = phase == AddressFormPhase.READY
        show {
            AddressFormScreen(
                AddressFormUiState(
                    postalCodeInputMode = PostalCodeInputMode.TEXT,
                    phase = phase,
                    loaded = true,
                    makeDefault = checked.value
                ),
                AddressFormActions(
                    onBack = { unrelatedActions += "back" },
                    onFieldChanged = { _, _ -> unrelatedActions += "field" },
                    onMakeDefaultChanged = onChanged,
                    onSave = { unrelatedActions += "save" },
                    onReload = { unrelatedActions += "reload" },
                    onFocusHandled = { unrelatedActions += "focus-handled" }
                )
            )
        }
        rule.onNodeWithTag(AddressFormTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        rule.onNodeWithText(purpose, useUnmergedTree = true).assertIsDisplayed()
    }

    fun showSearch(availability: SearchHistoryAvailability) {
        tag = SearchTestTags.HISTORY_TOGGLE
        purposeResource = R.string.search_history_enabled
        enabled = availability == SearchHistoryAvailability.AVAILABLE
        role = Role.Switch
        visualIsLeading = false
        show {
            SearchScreen(
                SearchUiState(
                    historyEnabled = checked.value,
                    historyLoading = availability == SearchHistoryAvailability.LOADING,
                    historyStorageAvailable = availability != SearchHistoryAvailability.UNAVAILABLE
                ),
                SearchActions(
                    onQueryChanged = { unrelatedActions += "query" },
                    onSubmit = { unrelatedActions += "submit" },
                    onRetry = { unrelatedActions += "retry" },
                    onLoadMore = { unrelatedActions += "load-more" },
                    onSelectHistory = { unrelatedActions += "history-select" },
                    onRemoveHistory = { unrelatedActions += "history-remove" },
                    onClearHistory = { unrelatedActions += "history-clear" },
                    onHistoryEnabledChanged = onChanged,
                    onOpenProduct = { unrelatedActions += "open-product" }
                )
            )
        }
        val settings = rule.onNodeWithTag(SearchTestTags.HISTORY_SETTINGS)
        assertEquals(
            resourceContext.getString(R.string.state_collapsed),
            settings.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        )
        settings.performDeterministicClick()
        assertEquals(
            resourceContext.getString(R.string.state_expanded),
            settings.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        )
        rule.onNodeWithTag(SearchTestTags.GRID).performScrollToNode(hasTestTag(tag))
        rule.onNodeWithText(purpose, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertPurpose() {
        val node = actionable.assertIsDisplayed().fetchSemanticsNode()
        val labels = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertEquals(
            "The actionable node must carry its exact localized purpose once",
            1,
            labels.count { it == purpose }
        )
    }

    fun assertContract() {
        val node = actionable.fetchSemanticsNode()
        assertEquals(
            "The existing tag must identify the merged actionable row",
            node.id,
            rule.onNodeWithTag(tag).fetchSemanticsNode().id
        )
        assertEquals(role, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals(
            if (checked.value) ToggleableState.On else ToggleableState.Off,
            node.config.getOrNull(SemanticsProperties.ToggleableState)
        )
        assertEquals(!enabled, node.config.contains(SemanticsProperties.Disabled))
        assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)
        assertTrue(node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isEmpty())
    }

    fun assertSingleOwner() {
        val raw = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val subtree = controlSubtree(raw)
        assertEquals(
            "A compound control must have exactly one native click owner",
            1,
            subtree.count { it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
        )
        assertEquals(
            "Visual child and label must not add keyboard focus stops",
            if (enabled) 1 else 0,
            subtree.count { it.config.contains(SemanticsProperties.Focused) }
        )
    }

    fun assertActivations() {
        val original = checked.value
        assertEquals(enabled, performAccessibilityClick())
        rule.waitForIdle()
        assertEquals(if (enabled) listOf(!original) else emptyList<Boolean>(), changes)
        val label = rule.onNodeWithText(purpose, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val ownerBounds = actionable.fetchSemanticsNode().boundsInRoot
        actionable.performTouchInput { click(label.boundsInRoot.center - ownerBounds.topLeft) }
        rule.waitForIdle()
        assertEquals(if (enabled) listOf(!original, original) else emptyList<Boolean>(), changes)
        val currentBounds = actionable.fetchSemanticsNode().boundsInRoot
        val visualX = if (visualIsLeading) {
            CHECKBOX_VISUAL_REGION_DP * density / 2f
        } else {
            currentBounds.width - MINIMUM_TARGET_DP * density / 2f
        }
        actionable.performTouchInput { click(Offset(visualX, currentBounds.height / 2f)) }
        rule.waitForIdle()
        assertEquals(if (enabled) listOf(!original, original, !original) else emptyList<Boolean>(), changes)
        assertEquals(if (enabled) !original else original, checked.value)
        assertTrue("No other screen action may be invoked", unrelatedActions.isEmpty())
        assertContract()
    }

    fun performAccessibilityClick(): Boolean {
        val id = actionable.assertIsDisplayed().fetchSemanticsNode().id
        return rule.runOnIdle {
            val provider = checkNotNull(hostView.accessibilityNodeProvider) {
                "The actual Compose host must expose its accessibility node provider"
            }
            val nativeNode = checkNotNull(provider.createAccessibilityNodeInfo(id)) {
                "The visible semantic owner must map to a real native virtual node"
            }
            assertEquals(enabled, nativeNode.isEnabled)
            provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null)
        }
    }

    fun assertLayout() {
        val bounds = actionable.assertIsDisplayed().fetchSemanticsNode().fullBoundsInRoot()
        val label = rule.onNodeWithText(purpose, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().fullBoundsInRoot()
        val viewportTag = if (visualIsLeading) AddressFormTestTags.CONTENT else SearchTestTags.GRID
        val viewport = rule.onNodeWithTag(viewportTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Row must have at least a 48dp target", bounds.height >= MINIMUM_TARGET_DP * density)
        assertTrue(bounds.width >= MINIMUM_TARGET_DP * density)
        assertTrue("The label must have nonzero readable bounds", label.width > 0f && label.height > 0f)
        assertTrue("The label must stay inside the row horizontally", label.left >= bounds.left)
        assertTrue("The label must stay inside the row horizontally", label.right <= bounds.right)
        assertTrue("The label must stay inside the row vertically", label.top >= bounds.top)
        assertTrue("The label must stay inside the row vertically", label.bottom <= bounds.bottom)
        assertTrue("The row must stay inside the visible viewport horizontally", bounds.left >= viewport.left)
        assertTrue("The row must stay inside the visible viewport horizontally", bounds.right <= viewport.right)
        assertTrue("The row must stay inside the visible viewport vertically", bounds.top >= viewport.top)
        assertTrue("The row must stay inside the visible viewport vertically", bounds.bottom <= viewport.bottom)
        val labelClearsVisualRegion = if (visualIsLeading) {
            label.left >= bounds.left + CHECKBOX_VISUAL_REGION_DP * density
        } else {
            label.right <= bounds.right - MINIMUM_TARGET_DP * density
        }
        assertTrue("The label must not overlap the native visual-control region", labelClearsVisualRegion)
    }

    fun assertKeyboardTraversal() {
        if (enabled) {
            val previousMode = rule.runOnIdle { inputModeManager.inputMode }
            val previousNativeTouch = rule.runOnIdle { hostView.isInTouchMode }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var stage = "initial"
            var primaryFailure: Throwable? = null
            fun observeStage() {
                runCatching {
                    instrumentation.runOnMainSync {
                        Log.i(
                            "ControlKeyboardMode",
                            "case=$case tag=$tag stage=$stage sdk=${android.os.Build.VERSION.SDK_INT} " +
                                "previousMode=$previousMode previousNativeTouch=$previousNativeTouch " +
                                "mode=${inputModeManager.inputMode} nativeTouch=${hostView.isInTouchMode} " +
                                "attached=${hostView.isAttachedToWindow} windowFocus=${hostView.hasWindowFocus()} " +
                                "viewFocus=${hostView.hasFocus()}"
                        )
                    }
                }.onFailure {
                    Log.i("ControlKeyboardMode", "stage=$stage observationFailure=${it.javaClass.simpleName}")
                }
            }
            observeStage()
            try {
                stage = "request-keyboard"
                observeStage()
                rule.runOnIdle {
                    assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard))
                }
                stage = "await-keyboard"
                observeStage()
                rule.waitUntil(timeoutMillis = 5_000) {
                    rule.runOnIdle { inputModeManager.inputMode == InputMode.Keyboard }
                }
                stage = "assert-keyboard-native"
                observeStage()
                rule.runOnIdle {
                    assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
                    assertFalse(hostView.isInTouchMode)
                }
                stage = "request-control-focus"
                observeStage()
                actionable.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
                stage = "assert-focused-and-tab"
                observeStage()
                actionable.assertIsFocused().performKeyInput { pressKey(Key.Tab) }
                stage = "assert-control-not-focused"
                observeStage()
                actionable.assertIsNotFocused()
                stage = "assert-children-not-focused"
                observeStage()
                assertFalse(
                    rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                        .let(::controlSubtree).drop(1)
                        .any { it.config.getOrNull(SemanticsProperties.Focused) == true }
                )
                stage = "traversal-complete"
                observeStage()
            } catch (failure: Throwable) {
                primaryFailure = failure
                Log.i("ControlKeyboardMode", "stage=$stage primaryFailure=${failure.javaClass.simpleName}")
                observeStage()
                throw failure
            } finally {
                try {
                    stage = "restore-native"
                    observeStage()
                    // Android's Compose Touch request only checks state; restore the native test input mode.
                    instrumentation.setInTouchMode(previousMode == InputMode.Touch)
                    if (previousMode == InputMode.Touch) {
                        stage = "restore-touch-native-down-cancel"
                        observeStage()
                        val stateBeforeTouch = rule.runOnIdle {
                            Triple(changes.toList(), unrelatedActions.toList(), checked.value)
                        }
                        val owner = actionable.assertIsDisplayed().fetchSemanticsNode()
                        val ownerBounds = owner.fullBoundsInRoot()
                        assertEquals("The cleanup target must be fully unclipped", ownerBounds, owner.boundsInRoot)
                        assertTrue(ownerBounds.width > 0f && ownerBounds.height > 0f)
                        assertFalse(owner.config.getOrNull(SemanticsProperties.Disabled) != null)
                        val location = IntArray(2)
                        val visibleWindow = android.graphics.Rect()
                        val hostBounds = rule.runOnIdle {
                            assertTrue(hostView.isAttachedToWindow)
                            assertTrue(hostView.isShown)
                            assertTrue(hostView.hasWindowFocus())
                            assertNotNull(hostView.windowToken)
                            hostView.getLocationOnScreen(location)
                            hostView.getWindowVisibleDisplayFrame(visibleWindow)
                            Rect(
                                location[0].toFloat(),
                                location[1].toFloat(),
                                location[0] + hostView.width.toFloat(),
                                location[1] + hostView.height.toFloat()
                            )
                        }
                        val screenBounds = Rect(
                            location[0] + ownerBounds.left,
                            location[1] + ownerBounds.top,
                            location[0] + ownerBounds.right,
                            location[1] + ownerBounds.bottom
                        )
                        assertTrue(screenBounds.left >= hostBounds.left && screenBounds.right <= hostBounds.right)
                        assertTrue(screenBounds.top >= hostBounds.top && screenBounds.bottom <= hostBounds.bottom)
                        assertTrue(screenBounds.left >= visibleWindow.left && screenBounds.right <= visibleWindow.right)
                        assertTrue(screenBounds.top >= visibleWindow.top && screenBounds.bottom <= visibleWindow.bottom)
                        val point = screenBounds.center
                        val downTime = SystemClock.uptimeMillis()
                        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, point.x, point.y, 0)
                        var downFailure: Throwable? = null
                        try {
                            instrumentation.sendPointerSync(down)
                        } catch (failure: Throwable) {
                            downFailure = failure
                            throw failure
                        } finally {
                            down.recycle()
                            try {
                                val cancel = MotionEvent.obtain(
                                    downTime,
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_CANCEL,
                                    point.x,
                                    point.y,
                                    0
                                )
                                try {
                                    instrumentation.sendPointerSync(cancel)
                                } finally {
                                    cancel.recycle()
                                }
                            } catch (cancellationFailure: Throwable) {
                                val originalDownFailure = downFailure
                                if (originalDownFailure != null) {
                                    originalDownFailure.addSuppressed(cancellationFailure)
                                } else {
                                    throw cancellationFailure
                                }
                            }
                        }
                        stage = "assert-touch-cancellation-ledger"
                        observeStage()
                        rule.runOnIdle {
                            assertEquals(stateBeforeTouch.first, changes.toList())
                            assertEquals(stateBeforeTouch.second, unrelatedActions.toList())
                            assertEquals(stateBeforeTouch.third, checked.value)
                        }
                        assertEquals(owner.id, actionable.fetchSemanticsNode().id)
                    }
                    stage = "await-restoration"
                    observeStage()
                    rule.waitUntil(timeoutMillis = 5_000) {
                        rule.runOnIdle { inputModeManager.inputMode == previousMode }
                    }
                    stage = "assert-restoration"
                    observeStage()
                    rule.runOnIdle {
                        assertEquals(previousMode, inputModeManager.inputMode)
                        assertEquals(previousMode == InputMode.Touch, hostView.isInTouchMode)
                    }
                    stage = "restoration-complete"
                    observeStage()
                } catch (cleanupFailure: Throwable) {
                    Log.i("ControlKeyboardMode", "stage=$stage cleanupFailure=${cleanupFailure.javaClass.simpleName}")
                    observeStage()
                    val originalFailure = primaryFailure
                    if (originalFailure != null) {
                        originalFailure.addSuppressed(cleanupFailure)
                    } else {
                        throw cleanupFailure
                    }
                }
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            val hostDensity = LocalDensity.current
            val view = LocalView.current
            val modeManager = LocalInputModeManager.current
            SideEffect {
                hostView = view
                inputModeManager = modeManager
            }
            density = hostDensity.density
            CompositionLocalProvider(
                LocalContext provides resourceContext,
                LocalConfiguration provides resourceContext.resources.configuration,
                LocalDensity provides Density(hostDensity.density, case.fontScale)
            ) {
                CoreTestTheme(darkTheme = false, content = content)
            }
        }
        val root = if (visualIsLeading) AddressFormTestTags.ROOT else SearchTestTags.ROOT
        rule.waitUntilExactlyOneExists(hasTestTag(root), timeoutMillis = 5_000)
    }
}

private fun controlSubtree(node: SemanticsNode): List<SemanticsNode> =
    listOf(node) + node.children.flatMap(::controlSubtree)

private fun SemanticsNode.fullBoundsInRoot(): Rect {
    val position = positionInRoot
    val dimensions = size
    return Rect(position.x, position.y, position.x + dimensions.width, position.y + dimensions.height)
}
