package com.gurbakir.mobile.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMediaPolicy
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeV2ImageDecodeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cropPresentationStillCapsActualLandscapePortraitAndExtremeAspectDecodes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dimensions = listOf(3200 to 1600, 1600 to 3200, 6400 to 32, 32 to 6400)
        val files = dimensions.mapIndexed { index, (width, height) ->
            File(context.cacheDir, "home-v2-decode-$index.png").also { file ->
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val results = ConcurrentHashMap<String, Pair<Int, Int>>()
        val coordinator = HomePlaybackCoordinator(StorefrontMediaPolicy("example.com"), HomePlaybackClock { 0 })
        val loader = coordinator.imageLoader(context).newBuilder().components {
            add(
                Interceptor { chain ->
                    val url = chain.request.data.toString()
                    val index = url.substringAfterLast('/').toInt()
                    val result = chain.withRequest(chain.request.newBuilder().data(files[index]).build()).proceed()
                    if (result is SuccessResult) results[url] = result.image.width to result.image.height
                    result
                }
            )
        }.build()
        // Replace only the transport fixture; the real HomeV2Image request and Coil decoder remain under test.
        HomePlaybackCoordinator::class.java.getDeclaredField("v2ImageLoader").apply { isAccessible = true }
            .set(coordinator, loader)
        var index by mutableStateOf(0)
        composeRule.setContent {
            CoreTestTheme {
                HomeV2Image(
                    HomeV2ImageContent(
                        StorefrontMedia(URI("https://example.com/$index"), "Image", null, null),
                        "Image",
                        "decode-$index"
                    ),
                    coordinator,
                    Modifier.size(320.dp),
                    ContentScale.Crop
                )
            }
        }
        try {
            dimensions.indices.forEach { current ->
                composeRule.runOnIdle { index = current }
                val url = "https://example.com/$current"
                composeRule.waitUntil(10_000) { results.containsKey(url) }
                val (width, height) = requireNotNull(results[url])
                android.util.Log.i("HomeV2DecodeProof", "source=${dimensions[current]} decoded=${width}x$height")
                assertTrue("${dimensions[current]} decoded to ${width}x$height", width <= 1600 && height <= 1600)
                assertTrue("Pixel cap", width.toLong() * height <= 2_560_000)
            }
        } finally {
            loader.shutdown()
            files.forEach(File::delete)
        }
    }
}
