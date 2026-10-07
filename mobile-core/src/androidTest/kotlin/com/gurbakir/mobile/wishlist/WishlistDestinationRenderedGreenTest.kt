package com.gurbakir.mobile.wishlist

import android.graphics.Rect as AndroidRect
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog as isNativeDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot as isNativeRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.foundation.config.PrimaryNavigationDestination
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.core.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * 4 methods x tr/en at fontScale=2 = 8 GREEN-only drafted cases.
 * R.string.wishlist_product_checking is a PROPOSED new resource; resolve its final spelling before compilation.
 * No absence/compile failure against old source is RED. Touch injection is not SemanticsActions.OnClick.
 */
@RunWith(Parameterized::class)
class WishlistDestinationRenderedGreenTest(private val locale: String) {
    @get:Rule val composeRule = createAndroidComposeRule<WishlistLifecycleProbeActivity>()

    @get:Rule val evidenceTestName = TestName()
    private val evidence = WishlistRenderedEvidence(locale) { evidenceTestName.methodName }
    private lateinit var fixture: WishlistGreenDestinationFixture

    @After
    fun closeOwnedRoomAndModel() {
        if (!::fixture.isInitialized) return
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.runOnIdle { fixture.attached.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { fixture.clearOwnedModel() }
        runBlocking(Dispatchers.IO) { fixture.room.finishPhysicalReads() }
        awaitReturnedLoads()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        fixture.room.closeDatabase()
    }

    @Test
    fun freshIntervalShowsLocalizedPendingAndNativeRemoveBeforeReply() {
        show(count = 1)
        fixture.room.gateway.release(1)
        awaitSettled(1)
        val first = fixture.currentModel
        val id = roomGreenId(1)
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.CATEGORIES) }
        composeRule.onNodeWithTag(ROOM_GREEN_CATEGORIES).assertIsDisplayed()
        fixture.room.gateway.setProduct("Fresh Room product")
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        awaitPhysical(started = 2, active = 1, completed = 1)
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.item(id)))
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_checking)).assertIsDisplayed()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_service)).assertDoesNotExist()
        composeRule.onNodeWithText("Initial Room product").assertDoesNotExist()
        composeRule.onNodeWithTag(CatalogTestTags.productPrice("offline-1")).assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(first === fixture.currentModel)
            assertEquals(null, first.state.value.entries.single().product)
            assertEquals(10L, first.state.value.entries.single().addedAtEpochMillis)
        }
        composeRule.onNode(
            hasText(fixture.context.getString(R.string.wishlist_remove)) and
                hasAnyAncestor(hasTestTag(WishlistTestTags.item(id)))
        ).assertIsDisplayed().assertIsEnabled().guardPointerControl().performTouchInput { click() }
        awaitEmptyLocal()
        assertEquals(1, fixture.room.gateway.snapshot().active)
        assertEquals(1, fixture.room.gateway.snapshot().completed)
        releaseAllAndAssertEmpty(expectedReads = 2)
    }

    @Test
    fun pendingNativeClearCancellationAndConfirmationKeepLocalContractAtFontTwo() {
        show(count = 2)
        awaitPhysical(started = 2, active = 2, completed = 0)
        openClear()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_clear_title)).assertIsDisplayed()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_clear_cancel))
            .assertIsDisplayed().assertIsEnabled().guardPointerControl(dialog = true).performTouchInput { click() }
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).assertDoesNotExist()
        assertEquals(2, localRows().size)
        assertEquals(2, fixture.room.gateway.snapshot().active)
        openClear()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM)
            .assertIsDisplayed().assertIsEnabled().guardPointerControl(dialog = true).performTouchInput { click() }
        awaitEmptyLocal()
        assertEquals(2, fixture.room.gateway.snapshot().active)
        assertEquals(0, fixture.room.gateway.snapshot().completed)
        releaseAllAndAssertEmpty(expectedReads = 2)
    }

    @Test
    fun renderedNativeRetryStartsOneCurrentRoundAndKeepsRoomIdentity() {
        show(count = 1, initialReply = RoomGreenReply.CONNECTION, immediate = true)
        awaitSettled(1)
        val original = localRows()
        composeRule.runOnIdle { assertTrue(fixture.currentModel.state.value.hasRetryableItems) }
        fixture.room.gateway.reply(roomGreenId(1), RoomGreenReply.PRODUCT)
        fixture.room.gateway.setProduct("Recovered Room product")
        fixture.room.gateway.holdNewReplies()
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT).performScrollToNode(hasTestTag(WishlistTestTags.RETRY))
        composeRule.onNodeWithTag(WishlistTestTags.RETRY).assertIsDisplayed().assertIsEnabled()
            .guardPointerControl().performTouchInput { click() }
        awaitPhysical(started = 2, active = 1, completed = 1)
        assertEquals(original, localRows())
        fixture.room.gateway.release(2)
        awaitSettled(2)
        composeRule.onNodeWithText("Recovered Room product").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(fixture.currentModel.state.value.hasRetryableItems)
            assertEquals(10L, fixture.currentModel.state.value.entries.single().addedAtEpochMillis)
            assertEquals(2, fixture.observedRepository.snapshot().started)
        }
        assertEquals(original, localRows())
    }

    @Test
    fun configurationAndRemovedRowsKeepLocalizedLocalClearWithoutRetry() {
        show(count = 2, initialReply = RoomGreenReply.CONFIGURATION, immediate = true, secondRemoved = true)
        awaitSettled(2)
        composeRule.runOnIdle { assertFalse(fixture.currentModel.state.value.hasRetryableItems) }
        composeRule.onNodeWithTag(WishlistTestTags.RETRY).assertDoesNotExist()
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.item(roomGreenId(1))))
        composeRule.onNodeWithText(
            fixture.context.getString(R.string.wishlist_product_configuration)
        ).assertIsDisplayed()
        captureVisibleState(
            "configuration-row",
            composeRule.onNodeWithText(
                fixture.context.getString(R.string.wishlist_product_configuration)
            )
        )
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WishlistTestTags.item(roomGreenId(2))))
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_product_removed)).assertIsDisplayed()
        captureVisibleState(
            "removed-row",
            composeRule.onNodeWithText(
                fixture.context.getString(R.string.wishlist_product_removed)
            )
        )
        assertEquals(2, localRows().size)
        openClear()
        composeRule.onNodeWithText(fixture.context.getString(R.string.wishlist_clear_cancel))
            .assertIsDisplayed().guardPointerControl(dialog = true).performTouchInput { click() }
        assertEquals(2, localRows().size)
        openClear()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).assertIsDisplayed()
            .guardPointerControl(dialog = true).performTouchInput { click() }
        awaitEmptyLocal()
        assertEquals(2, fixture.room.gateway.snapshot().started)
    }

    private fun show(
        count: Int,
        initialReply: RoomGreenReply = RoomGreenReply.PRODUCT,
        immediate: Boolean = false,
        secondRemoved: Boolean = false
    ) {
        val room = WishlistRoomGreenFixture(composeRule.activity.applicationContext)
        fixture = WishlistGreenDestinationFixture(room, locale = locale, font = 2f)
        runBlocking(Dispatchers.IO) {
            room.seed(*(1..count).map { StoredWishlistEntry(roomGreenId(it), 11L - it) }.toTypedArray())
        }
        (1..count).forEach { room.gateway.reply(roomGreenId(it), initialReply) }
        if (secondRemoved) room.gateway.reply(roomGreenId(2), RoomGreenReply.REMOVED)
        if (immediate) room.gateway.allowImmediateReplies()
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(ROOM_GREEN_HOME).assertIsDisplayed()
        composeRule.runOnIdle { fixture.navigate(PrimaryNavigationDestination.WISHLIST) }
        composeRule.onNodeWithTag(WishlistTestTags.ROOT).assertIsDisplayed()
        awaitPhysical(started = count, active = if (immediate) 0 else count, completed = if (immediate) count else 0)
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.currentModel.state.value.entries.size == count }
        composeRule.runOnIdle { assertEquals(1, fixture.creations) }
        // Check language independently of querying the same composed resource; a stale context must fail setup.
        assertEquals(
            if (locale ==
                "tr"
            ) {
                "Listeyi temizle"
            } else {
                "Clear list"
            },
            fixture.context.getString(R.string.wishlist_clear)
        )
        assertEquals(
            if (locale == "tr") {
                "Güncel ürün bilgileri kontrol ediliyor. Ürün bu cihazdaki listenizde kalır."
            } else {
                "Checking current product details. This product remains in your list on this device."
            },
            fixture.context.getString(R.string.wishlist_product_checking)
        )
    }

    private fun openClear() {
        composeRule.onNodeWithTag(WishlistTestTags.CONTENT).performScrollToNode(hasTestTag(WishlistTestTags.CLEAR))
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR).assertIsDisplayed().assertIsEnabled()
            .guardPointerControl().performTouchInput { click() }
        if (evidence.isFirstIssueClearOpen()) captureCurrentRoots("issue-first-clear-after-touch")
    }

    /** Bounds guard only. The next operation still injects the original pointer event. */
    private fun SemanticsNodeInteraction.guardPointerControl(dialog: Boolean = false): SemanticsNodeInteraction {
        val checkpoint = evidence.nextPointerCheckpoint()
        var observedGeometry: JSONObject? = null
        assertIsDisplayed().assertIsEnabled()
        val node = fetchSemanticsNode()
        val root = node.topSemanticsRoot()
        val viewport = if (dialog) {
            root
        } else {
            composeRule.onNodeWithTag(WishlistTestTags.CONTENT)
                .assertIsDisplayed().fetchSemanticsNode()
        }
        assertEquals(
            "Control and clipped viewport must share a Compose window root",
            root.id,
            viewport.topSemanticsRoot().id
        )
        var nativeHost: View = composeRule.activity.window.decorView
        if (dialog) {
            // Capture this AlertDialog's decor root, never the Activity viewport behind its separate window.
            onView(isNativeRoot()).inRoot(isNativeDialog()).check { view, failure ->
                if (failure != null) throw failure
                nativeHost = checkNotNull(view) { "The actual dialog must expose its native decor root" }
            }
        }
        composeRule.runOnIdle {
            assertTrue(
                "Pointer target must be the actual clickable control owner",
                node.config.getOrNull(SemanticsActions.OnClick)?.action != null
            )
            assertTrue(
                "Native control window must be attached, shown and focused",
                nativeHost.isAttachedToWindow && nativeHost.isShown && nativeHost.hasWindowFocus()
            )
            val position = node.positionInRoot
            val size = node.size
            val fullRoot = Rect(position.x, position.y, position.x + size.width, position.y + size.height)
            assertTrue(
                "Entire unclipped control must have positive bounds",
                fullRoot.width > 0f && fullRoot.height > 0f
            )
            assertContains("Entire control must fit its clipped Compose viewport", viewport.boundsInRoot, fullRoot)
            assertContains("Entire control must fit its clipped Compose root", root.boundsInRoot, fullRoot)
            assertContains("Control must have no ancestor clipping", node.boundsInRoot, fullRoot)
            val windowPosition = node.positionInWindow
            val fullWindow = Rect(
                windowPosition.x,
                windowPosition.y,
                windowPosition.x + size.width,
                windowPosition.y + size.height
            )
            assertContains(
                "Entire control must fit its own clipped Compose window root",
                root.boundsInWindow,
                fullWindow
            )
            val screenLocation = IntArray(2)
            val windowLocation = IntArray(2)
            nativeHost.getLocationOnScreen(screenLocation)
            nativeHost.getLocationInWindow(windowLocation)
            val offsetX = (screenLocation[0] - windowLocation[0]).toFloat()
            val offsetY = (screenLocation[1] - windowLocation[1]).toFloat()
            val fullScreen = Rect(
                fullWindow.left + offsetX,
                fullWindow.top + offsetY,
                fullWindow.right + offsetX,
                fullWindow.bottom + offsetY
            )
            val hostVisible = AndroidRect()
            assertTrue(
                "Native host must have a nonempty visible rectangle",
                nativeHost.getLocalVisibleRect(hostVisible) && !hostVisible.isEmpty
            )
            val hostVisibleScreen = Rect(
                hostVisible.left + screenLocation[0].toFloat(),
                hostVisible.top + screenLocation[1].toFloat(),
                hostVisible.right + screenLocation[0].toFloat(),
                hostVisible.bottom + screenLocation[1].toFloat()
            )
            assertContains("Entire control must fit the actual native host rectangle", hostVisibleScreen, fullScreen)
            val windowVisible = AndroidRect()
            nativeHost.getWindowVisibleDisplayFrame(windowVisible)
            assertTrue("Native window frame must be nonempty", !windowVisible.isEmpty)
            assertContains(
                "Entire control must fit its own native visible window",
                windowVisible.toComposeRect(),
                fullScreen
            )
            if (checkpoint != null) {
                observedGeometry = evidence.geometry(
                    node,
                    root,
                    viewport,
                    nativeHost,
                    mapOf(
                        "checkedFullControlInRoot" to fullRoot,
                        "checkedViewportInRoot" to viewport.boundsInRoot,
                        "checkedRootInRoot" to root.boundsInRoot,
                        "checkedControlClippedInRoot" to node.boundsInRoot,
                        "checkedFullControlInWindow" to fullWindow,
                        "checkedRootInWindow" to root.boundsInWindow,
                        "checkedFullControlOnScreen" to fullScreen,
                        "checkedNativeVisibleHostOnScreen" to hostVisibleScreen,
                        "checkedNativeWindowVisibleFrameOnScreen" to windowVisible.toComposeRect()
                    )
                )
            }
        }
        if (checkpoint != null) evidence.capture(checkpoint, checkNotNull(observedGeometry))
        return this
    }

    private fun captureCurrentRoots(checkpoint: String) {
        if (!evidence.enabled) return
        val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(false)
        var observedGeometry: JSONObject? = null
        composeRule.runOnIdle { observedGeometry = evidence.rootsGeometry(roots) }
        evidence.capture(checkpoint, checkNotNull(observedGeometry))
    }

    private fun captureVisibleState(checkpoint: String, interaction: SemanticsNodeInteraction) {
        if (!evidence.enabled) return
        val node = interaction.fetchSemanticsNode()
        val viewport = composeRule.onNodeWithTag(WishlistTestTags.CONTENT).fetchSemanticsNode()
        var observedGeometry: JSONObject? = null
        composeRule.runOnIdle {
            observedGeometry = evidence.geometry(
                node,
                node.topSemanticsRoot(),
                viewport,
                composeRule.activity.window.decorView
            )
        }
        evidence.capture(checkpoint, checkNotNull(observedGeometry))
    }

    private fun SemanticsNode.topSemanticsRoot(): SemanticsNode {
        var current = this
        while (true) current = current.parent ?: return current
    }

    private fun AndroidRect.toComposeRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun assertContains(message: String, outer: Rect, inner: Rect) {
        // Half a physical pixel permits integer native-rectangle rounding; it cannot hide meaningful clipping.
        val rounding = 0.5f
        assertTrue(
            message,
            inner.left >= outer.left - rounding && inner.top >= outer.top - rounding &&
                inner.right <= outer.right + rounding && inner.bottom <= outer.bottom + rounding
        )
    }

    private fun localRows() = runBlocking(Dispatchers.IO) { fixture.room.store.load(fixture.room.partition) }

    private fun awaitEmptyLocal() {
        composeRule.waitUntil(timeoutMillis = 5_000) { fixture.currentModel.state.value.entries.isEmpty() }
        assertTrue(localRows().isEmpty())
        composeRule.waitUntil(timeoutMillis = 5_000) { !fixture.currentModel.state.value.mutating }
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
    }

    private fun awaitPhysical(started: Int, active: Int, completed: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.room.gateway.snapshot()
            value.started == started && value.active == active && value.completed == completed
        }
    }

    private fun awaitReturnedLoads() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val value = fixture.observedRepository.snapshot()
            value.active == 0 && value.completed == value.started
        }
    }

    private fun awaitSettled(total: Int) {
        awaitPhysical(started = total, active = 0, completed = total)
        awaitReturnedLoads()
        composeRule.waitUntil(timeoutMillis = 5_000) { !fixture.currentModel.state.value.loading }
        composeRule.waitForIdle()
    }

    private fun releaseAllAndAssertEmpty(expectedReads: Int) {
        fixture.room.gateway.releaseAll()
        awaitSettled(expectedReads)
        assertTrue(localRows().isEmpty())
        composeRule.onNodeWithTag(WishlistTestTags.EMPTY).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(fixture.currentModel.state.value.entries.isEmpty()) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "locale={0},font=2")
        fun locales(): List<Array<Any>> = listOf(arrayOf("tr"), arrayOf("en"))
    }
}
