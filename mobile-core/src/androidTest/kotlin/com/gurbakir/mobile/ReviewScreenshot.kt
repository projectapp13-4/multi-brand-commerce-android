package com.gurbakir.mobile

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun captureReviewScreenshot(name: String, rule: ComposeContentTestRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (InstrumentationRegistry.getArguments().getString("captureUi") != "true") return

    rule.waitForIdle()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
        "The controlled Android fixture did not produce a readable runtime screenshot."
    }
    val files = instrumentation.targetContext.getExternalFilesDir(null)
        ?: instrumentation.targetContext.filesDir
    val file = File(files, "phase2-$name.png")
    file.outputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    bitmap.recycle()
}
