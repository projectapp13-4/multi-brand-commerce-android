@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.gurbakir.storefront.StorefrontMedia

private const val HOME_MEDIA_ASPECT_RATIO = 3f / 4f

@Composable
internal fun HomeMedia(
    media: StorefrontMedia,
    fallbackDescription: String,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = RectangleShape
) {
    HomeMedia(
        media = media,
        fallbackDescription = fallbackDescription,
        modifier = Modifier.fillMaxWidth().aspectRatio(HOME_MEDIA_ASPECT_RATIO),
        contentScale = contentScale,
        shape = shape
    )
}

@Composable
internal fun HomeMedia(
    media: StorefrontMedia,
    fallbackDescription: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = RectangleShape
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = media.uri.toASCIIString(),
                contentDescription = media.altText ?: fallbackDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
