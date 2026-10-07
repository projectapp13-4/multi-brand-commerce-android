package com.gurbakir.mobile.wishlist

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Gated test-artifact export only: no input, scrolling, callback release or lifecycle operation. */
internal class WishlistRenderedEvidence(private val locale: String, private val caseName: () -> String) {
    private val arguments = InstrumentationRegistry.getArguments()
    val enabled = arguments.getString("w5RenderedEvidence") == "1"
    private var pointerSequence = 0
    private var captureSequence = 0
    private val directory by lazy {
        val run = checkNotNull(arguments.getString("w5RenderedEvidenceRun"))
        check(run.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        listOf("Source", "TargetApk", "TestApk").forEach { name ->
            check(arguments.getString("w5RenderedEvidence$name")?.matches(Regex("[a-fA-F0-9]{64}")) == true)
        }
        val context = InstrumentationRegistry.getInstrumentation().context
        val parent = File(checkNotNull(context.getExternalFilesDir("w5-rendered-evidence")), run)
        check(parent.isDirectory || parent.mkdirs())
        val case = caseName().replace(Regex("[^A-Za-z0-9_.=-]"), "_")
        File(parent, "${case}_$locale").also { check(it.mkdir()) { "Refuse existing evidence case directory" } }
    }

    fun nextPointerCheckpoint(): String? {
        if (!enabled) return null
        val labels = when (caseName().substringBefore('[')) {
            "freshIntervalShowsLocalizedPendingAndNativeRemoveBeforeReply" -> listOf("pending-remove")

            "pendingNativeClearCancellationAndConfirmationKeepLocalContractAtFontTwo" ->
                listOf(null, "pending-clear-cancel", null, "pending-clear-confirm")

            "renderedNativeRetryStartsOneCurrentRoundAndKeepsRoomIdentity" -> listOf("connection-retry")

            "configurationAndRemovedRowsKeepLocalizedLocalClearWithoutRetry" ->
                listOf("issue-first-clear-opener", "issue-clear-cancel", null, "issue-clear-confirm")

            else -> error("Unknown rendered evidence case")
        }
        return labels[pointerSequence++]
    }

    fun isFirstIssueClearOpen(): Boolean = enabled && pointerSequence == 1 &&
        caseName().substringBefore('[') == "configurationAndRemovedRowsKeepLocalizedLocalClearWithoutRetry"

    /** Observe all current roots and their own native decor without requiring a dialog to exist. */
    fun rootsGeometry(roots: List<SemanticsNode>): JSONObject = JSONObject().apply {
        put("geometryStartedElapsedNanos", SystemClock.elapsedRealtimeNanos())
        put("diagnosticAfterOriginalClearTouch", true)
        put("composeRootCount", roots.size)
        put(
            "composeRoots",
            JSONArray(
                roots.map { root ->
                    val nativeHost = (root.root as? ViewRootForTest)?.view?.rootView
                    if (nativeHost != null) {
                        geometry(root, root, root, nativeHost).put("diagnosticRootOnlyNoControlAssertion", true)
                    } else {
                        JSONObject().put("rootId", root.id).put("semanticsTree", semantics(root))
                            .put("nativeHost", JSONObject.NULL)
                    }
                }
            )
        )
        put("geometryFinishedElapsedNanos", SystemClock.elapsedRealtimeNanos())
    }

    /** Read only on the main thread after the existing assertions have completed. */
    fun geometry(
        node: SemanticsNode,
        root: SemanticsNode,
        viewport: SemanticsNode,
        nativeHost: View,
        checkedRectangles: Map<String, Rect> = emptyMap()
    ): JSONObject {
        val started = SystemClock.elapsedRealtimeNanos()
        val location = IntArray(2)
        nativeHost.getLocationOnScreen(location)
        val windowLocation = IntArray(2)
        nativeHost.getLocationInWindow(windowLocation)
        val frame = AndroidRect()
        nativeHost.getWindowVisibleDisplayFrame(frame)
        val attributes = nativeHost.layoutParams as? WindowManager.LayoutParams
        return JSONObject().apply {
            put("geometryStartedElapsedNanos", started)
            put("nodeId", node.id)
            put("rootId", root.id)
            put("viewportId", viewport.id)
            put(
                "checkedRectangles",
                JSONObject().apply {
                    checkedRectangles.forEach { (name, bounds) -> put(name, rectangle(bounds)) }
                }
            )
            put("semanticsTree", semantics(root))
            put(
                "nativeHost",
                JSONObject().apply {
                    put("class", nativeHost.javaClass.name)
                    put("attached", nativeHost.isAttachedToWindow)
                    put("shown", nativeHost.isShown)
                    put("windowFocus", nativeHost.hasWindowFocus())
                    put("windowToken", nativeHost.windowToken?.toString() ?: JSONObject.NULL)
                    put("locationOnScreen", JSONArray(location.toList()))
                    put("locationInWindow", JSONArray(windowLocation.toList()))
                    put(
                        "fullDecorBoundsOnScreen",
                        JSONArray(
                            listOf(
                                location[0],
                                location[1],
                                location[0] + nativeHost.width,
                                location[1] + nativeHost.height
                            )
                        )
                    )
                    put(
                        "windowVisibleFrameOnScreen",
                        JSONArray(listOf(frame.left, frame.top, frame.right, frame.bottom))
                    )
                    put("windowType", attributes?.type ?: JSONObject.NULL)
                    put("windowFlags", attributes?.flags ?: JSONObject.NULL)
                    put("softInputMode", attributes?.softInputMode ?: JSONObject.NULL)
                    put("densityDpi", nativeHost.resources.displayMetrics.densityDpi)
                }
            )
            put("geometryFinishedElapsedNanos", SystemClock.elapsedRealtimeNanos())
        }
    }

    /** A full-display native PNG on the instrumentation thread, after the geometry snapshot. */
    fun capture(checkpoint: String, geometry: JSONObject) {
        if (!enabled) return
        check(Looper.myLooper() != Looper.getMainLooper())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ordinal = ++captureSequence
        val stem = "%02d-%s".format(ordinal, checkpoint)
        val png = File(directory, "$stem.png")
        val receipt = File(directory, "$stem.json")
        check(png.createNewFile()) { "Refuse existing PNG" }
        check(receipt.createNewFile()) { "Refuse existing receipt" }
        val startedUtcMillis = System.currentTimeMillis()
        val started = SystemClock.elapsedRealtimeNanos()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Native screenshot unavailable" }
        val finished = SystemClock.elapsedRealtimeNanos()
        val width = bitmap.width
        val height = bitmap.height
        try {
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(png.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val data = JSONObject().apply {
            put("schema", "W5_RENDERED_CHECKPOINT_EXPORT_1")
            put("checkpoint", checkpoint)
            put("caseName", caseName())
            put("locale", locale)
            put("composedFontScale", 2)
            put("ordinal", ordinal)
            put("api", Build.VERSION.SDK_INT)
            put("processId", Process.myPid())
            put("instrumentationComponent", instrumentation.componentName.flattenToString())
            put("instrumentationPackage", instrumentation.context.packageName)
            put("targetPackage", instrumentation.targetContext.packageName)
            put("declaredRun", arguments.getString("w5RenderedEvidenceRun"))
            put("declaredSourceTreeSha256", arguments.getString("w5RenderedEvidenceSource"))
            put("declaredTargetApkSha256", arguments.getString("w5RenderedEvidenceTargetApk"))
            put("declaredTestApkSha256", arguments.getString("w5RenderedEvidenceTestApk"))
            put("screenshotStartedUtcMillis", startedUtcMillis)
            put("screenshotStartedElapsedNanos", started)
            put("screenshotFinishedElapsedNanos", finished)
            put("png", png.name)
            put("pngSha256", digest)
            put("pngBytes", png.length())
            put("pngWidth", width)
            put("pngHeight", height)
            put("geometry", geometry)
            put("atomicGeometryAndPixels", false)
            put("runtimeAccepted", false)
        }
        receipt.writeText(data.toString(2) + "\n")
    }

    private fun semantics(node: SemanticsNode): JSONObject = JSONObject().apply {
        put("id", node.id)
        put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: JSONObject.NULL)
        put("isDialog", node.config.getOrNull(SemanticsProperties.IsDialog) != null)
        put("text", JSONArray(node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList<String>()))
        val position = node.positionInRoot
        put(
            "fullBoundsInRoot",
            rectangle(
                Rect(
                    position.x,
                    position.y,
                    position.x + node.size.width,
                    position.y + node.size.height
                )
            )
        )
        put("clippedBoundsInRoot", rectangle(node.boundsInRoot))
        put("clippedBoundsInWindow", rectangle(node.boundsInWindow))
        put("children", JSONArray(node.children.map { semantics(it) }))
    }

    private fun rectangle(bounds: Rect): JSONArray = JSONArray(
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).map {
            if (it.isFinite()) it.toDouble() else JSONObject.NULL
        }
    )
}
