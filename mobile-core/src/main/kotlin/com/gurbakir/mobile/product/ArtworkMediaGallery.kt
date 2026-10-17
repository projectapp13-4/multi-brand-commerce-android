@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.product

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.core.R
import com.gurbakir.storefront.StorefrontMedia

@Composable
internal fun ArtworkMediaGallery(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    opener: FocusRequester,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = state.displayMedia
    val index = state.mediaIndex.coerceIn(0, media.lastIndex.coerceAtLeast(0))
    val first = state.product?.media?.firstOrNull()?.image ?: media.firstOrNull()
    val openLabel = stringResource(R.string.product_media_expand)
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val insets = WindowInsets.safeDrawing
    val usableHeight = with(density) {
        (windowHeight - insets.getTop(this) - insets.getBottom(this)).coerceAtLeast(0).toDp()
    }
    BoxWithConstraints(modifier.fillMaxWidth().testTag(ProductDetailTestTags.MEDIA)) {
        val desiredHeight = artworkGalleryHeight(
            maxWidth,
            first?.width,
            first?.height,
            usableHeight
        )
        val matHeight = if (constraints.hasBoundedHeight) {
            desiredHeight.coerceAtMost((maxHeight - 48.dp).coerceAtLeast(48.dp))
        } else {
            desiredHeight
        }
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(matHeight)
            ) {
                if (media.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.product_no_media), Modifier.testTag("artwork-media-empty"))
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().focusRequester(opener).focusable()
                            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                            .testTag(ProductDetailTestTags.MEDIA_OPEN)
                    ) {
                        key(media.map { it.uri }) {
                            ArtworkPager(media, index, requireNotNull(state.product).title, actions.onSelectMedia)
                        }
                    }
                }
            }
            if (media.isNotEmpty()) {
                ArtworkGalleryControls(index, media.size, actions.onSelectMedia, onOpen)
            }
        }
    }
}

@Composable
private fun ArtworkGalleryControls(index: Int, count: Int, onSelect: (Int) -> Unit, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.product_media_expand)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkMediaArrow(false, index > 0) { onSelect(index - 1) }
        Text(
            stringResource(R.string.product_media_position, index + 1, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("artwork-media-count")
        )
        IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_artwork_expand), openLabel, Modifier.size(20.dp))
        }
        ArtworkMediaArrow(true, index < count - 1) { onSelect(index + 1) }
    }
}

@Composable
private fun ArtworkPager(media: List<StorefrontMedia>, index: Int, title: String, onSelect: (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = index, pageCount = { media.size })
    val currentIndex by rememberUpdatedState(index)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(index) {
        with(pager) { scroll(MutatePriority.PreventUserInput) { updateCurrentPage(index) } }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (it != currentIndex) currentOnSelect(it) }
    }
    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        ArtworkImage(media[page], title, Modifier.padding(LocalBrandSpacing.current.normalDp.dp))
    }
}

@Composable
internal fun ArtworkImage(
    media: StorefrontMedia,
    title: String,
    modifier: Modifier = Modifier,
    onDarkBackground: Boolean = false,
    onDimensions: (Int, Int) -> Unit = { _, _ -> }
) {
    var attempt by remember(media.uri) { mutableIntStateOf(0) }
    var loading by remember(media.uri, attempt) { mutableStateOf(true) }
    var failed by remember(media.uri, attempt) { mutableStateOf(false) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        key(media.uri, attempt) {
            // AsyncImage resolves decode size from its constraints; zoom never requests original size.
            AsyncImage(
                model = media.uri.toASCIIString(),
                contentDescription = media.altText?.takeIf { it.isNotBlank() } ?: title,
                contentScale = ContentScale.Fit,
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = {
                    loading = false
                    failed = false
                    onDimensions(it.result.image.width, it.result.image.height)
                },
                onError = {
                    loading = false
                    failed = true
                },
                modifier = Modifier.fillMaxSize().testTag("artwork-image")
            )
        }
        when {
            failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("artwork-image-error")
            ) {
                Text(stringResource(R.string.artwork_image_failed), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { attempt++ },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (onDarkBackground) Color.White else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("artwork-image-retry")
                ) {
                    Text(stringResource(R.string.retry))
                }
            }

            loading -> CircularProgressIndicator(
                Modifier.size(32.dp).testTag("artwork-image-loading"),
                color = if (onDarkBackground) Color.White else MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
internal fun ArtworkMediaArrow(
    next: Boolean,
    enabled: Boolean,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.size(
            48.dp
        ).testTag(if (next) ProductDetailTestTags.MEDIA_NEXT else ProductDetailTestTags.MEDIA_PREVIOUS)
    ) {
        Icon(
            painterResource(R.drawable.ic_arrow_back),
            stringResource(if (next) R.string.product_media_next else R.string.product_media_previous),
            Modifier.size(20.dp).rotate(if (next) 180f else 0f)
        )
    }
}
