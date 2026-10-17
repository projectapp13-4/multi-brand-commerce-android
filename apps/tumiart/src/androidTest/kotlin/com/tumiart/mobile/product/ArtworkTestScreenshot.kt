package com.tumiart.mobile.product

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

internal fun captureArtworkSystemFrame(): Bitmap {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.takeScreenshot()?.let { return it }
    // Legacy emulator SurfaceControl capture can return null; screencap reads the real compositor.
    return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p")).use {
        checkNotNull(BitmapFactory.decodeStream(it)) { "System screenshot is unavailable" }
    }
}
