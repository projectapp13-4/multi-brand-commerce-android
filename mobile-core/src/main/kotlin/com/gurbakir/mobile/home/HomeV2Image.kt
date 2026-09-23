@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMediaClientFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.gurbakir.storefront.StorefrontMediaRequestGuard
import java.security.MessageDigest

internal const val HOME_V2_IMAGE_MAX_DIMENSION = 1600
private const val HOME_V2_IMAGE_MAX_RESPONSE_BYTES = 8L * 1024L * 1024L

internal class HomeV2ImageRequestGuard : StorefrontMediaRequestGuard {
    private var bytesRead = 0L
    private var exhausted = false

    override fun onRequestStarted(url: String): Boolean = !exhausted

    override fun onRedirect(targetUrl: String): Boolean = !exhausted

    @Synchronized
    override fun maximumResponseBytesForRead(requestedByteCount: Long): Long {
        val remaining = (HOME_V2_IMAGE_MAX_RESPONSE_BYTES - bytesRead).coerceAtLeast(0)
        if (requestedByteCount > 0 && remaining == 0L) exhausted = true
        return minOf(requestedByteCount, remaining)
    }

    @Synchronized
    override fun onResponseBytes(byteCount: Long): Boolean {
        require(byteCount >= 0)
        val remaining = HOME_V2_IMAGE_MAX_RESPONSE_BYTES - bytesRead
        if (exhausted || byteCount > remaining) {
            bytesRead = HOME_V2_IMAGE_MAX_RESPONSE_BYTES
            exhausted = true
            return false
        }
        bytesRead += byteCount
        return true
    }
}

internal fun homeV2ImageCacheKey(url: String, revisionKey: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(revisionKey.toByteArray(Charsets.UTF_8))
    digest.update(0)
    digest.update(url.toByteArray(Charsets.UTF_8))
    return "home-v2:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun createHomeV2ImageLoader(context: Context, mediaPolicy: StorefrontMediaPolicy): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = {
                        StorefrontMediaClientFactory.createWithRequestGuardFactory(
                            mediaPolicy,
                            ::HomeV2ImageRequestGuard
                        )
                    }
                )
            )
        }
        .build()

internal data class HomeV2ImageContent(
    val media: StorefrontMedia,
    val fallbackDescription: String,
    val revisionKey: String
)

@Composable
internal fun HomeV2Image(
    content: HomeV2ImageContent,
    coordinator: HomePlaybackCoordinator,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = RectangleShape
) {
    val context = LocalContext.current
    val url = content.media.uri.toASCIIString()
    val cacheKey = remember(url, content.revisionKey) { homeV2ImageCacheKey(url, content.revisionKey) }
    val imageLoader = remember(coordinator, context) { coordinator.imageLoader(context) }
    val request = remember(url, content.revisionKey) {
        ImageRequest.Builder(context)
            .data(url)
            .size(HOME_V2_IMAGE_MAX_DIMENSION, HOME_V2_IMAGE_MAX_DIMENSION)
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = shape, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = content.media.altText ?: content.fallbackDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
