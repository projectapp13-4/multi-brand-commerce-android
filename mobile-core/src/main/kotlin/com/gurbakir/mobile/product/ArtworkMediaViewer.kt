@file:Suppress("FunctionNaming", "MagicNumber")

package com.gurbakir.mobile.product

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gurbakir.mobile.core.R
import com.gurbakir.storefront.StorefrontMedia
import kotlin.math.roundToInt

@Composable
internal fun ArtworkMediaViewer(state: ProductDetailUiState, actions: ProductDetailActions) {
    val media = state.displayMedia
    if (media.isEmpty()) return
    val index = state.mediaIndex.coerceIn(0, media.lastIndex)
    val close = remember { FocusRequester() }
    Dialog(
        onDismissRequest = actions.onCloseMediaViewer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = actions.onCloseMediaViewer)
        DialogSystemBarAppearance()
        RequestDialogFocus(close)
        Column(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag(ProductDetailTestTags.MEDIA_VIEWER)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.product_media_position, index + 1, media.size), color = Color.White)
                TextButton(
                    onClick = actions.onCloseMediaViewer,
                    modifier = Modifier.focusRequester(
                        close
                    ).focusable().heightIn(min = 48.dp).testTag(ProductDetailTestTags.MEDIA_CLOSE)
                ) {
                    Text(stringResource(R.string.close), color = Color.White)
                }
            }
            key(media[index].uri) {
                ArtworkZoomViewport(media[index], requireNotNull(state.product).title, index, media.size, actions)
            }
        }
    }
}

@Composable
@Suppress("LongMethod") // Zoom viewport and its accessible alternatives share bounded state.
private fun androidx.compose.foundation.layout.ColumnScope.ArtworkZoomViewport(
    media: StorefrontMedia,
    title: String,
    index: Int,
    count: Int,
    actions: ProductDetailActions
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var imageWidth by remember { mutableStateOf(media.width) }
    var imageHeight by remember { mutableStateOf(media.height) }
    fun applyZoom(value: Float) {
        zoom = value.coerceIn(1f, 4f)
        pan = clampArtworkPan(pan, zoom, viewport, imageWidth, imageHeight)
    }
    fun move(delta: Offset): Boolean {
        pan = clampArtworkPan(pan + delta, zoom, viewport, imageWidth, imageHeight)
        return true
    }
    val transform = rememberTransformableState { scale, offset, _ ->
        applyZoom(zoom * scale)
        move(offset)
    }
    val zoomLabel = stringResource(R.string.artwork_zoom_level, (zoom * 100).roundToInt())
    val left = stringResource(R.string.artwork_pan_left)
    val right = stringResource(R.string.artwork_pan_right)
    val up = stringResource(R.string.artwork_pan_up)
    val down = stringResource(R.string.artwork_pan_down)
    Box(
        Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .onSizeChanged {
                viewport = Size(it.width.toFloat(), it.height.toFloat())
                pan = clampArtworkPan(pan, zoom, viewport, imageWidth, imageHeight)
            }
            .transformable(transform)
            .semantics {
                stateDescription = zoomLabel
                customActions = listOf(
                    CustomAccessibilityAction(left) { move(Offset(viewport.width / 4, 0f)) },
                    CustomAccessibilityAction(right) { move(Offset(-viewport.width / 4, 0f)) },
                    CustomAccessibilityAction(up) { move(Offset(0f, viewport.height / 4)) },
                    CustomAccessibilityAction(down) { move(Offset(0f, -viewport.height / 4)) }
                )
            }.testTag("artwork-zoom-viewport")
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
            }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White
            ) {
                ArtworkImage(media, title, onDarkBackground = true) { width, height ->
                    imageWidth = width
                    imageHeight = height
                    pan = clampArtworkPan(pan, zoom, viewport, width, height)
                }
            }
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ViewerArrow(false, index > 0) { actions.onSelectMedia(index - 1) }
        ZoomButton("−", R.string.artwork_zoom_out, "artwork-zoom-out", zoom > 1f) { applyZoom(zoom - 0.5f) }
        TextButton(
            onClick = { applyZoom(1f) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("artwork-zoom-fit")
        ) {
            Text(stringResource(R.string.artwork_fit), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        ZoomButton("+", R.string.artwork_zoom_in, "artwork-zoom-in", zoom < 4f) { applyZoom(zoom + 0.5f) }
        ViewerArrow(true, index < count - 1) { actions.onSelectMedia(index + 1) }
    }
}

@Composable
private fun ZoomButton(text: String, label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(onClick, Modifier.size(48.dp).testTag(tag).semantics { contentDescription = description }, enabled) {
        Text(text, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ViewerArrow(next: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ArtworkMediaArrow(
        next,
        enabled,
        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        onClick = onClick
    )
}
