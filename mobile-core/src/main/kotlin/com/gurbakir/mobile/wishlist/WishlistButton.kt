@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.gurbakir.mobile.core.R

@Composable
fun WishlistButton(
    productId: String,
    state: WishlistMembershipUiState,
    onSetSaved: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val saved = state.isSaved(productId)
    WishlistProductButton(
        productId = productId,
        action =
            WishlistProductAction(
                saved = saved,
                updating = !state.storageAvailable || state.isUpdating(productId),
                onSetSaved = { desired -> onSetSaved(productId, desired) }
            ),
        modifier = modifier
    )
}

@Composable
fun WishlistProductButton(productId: String, action: WishlistProductAction, modifier: Modifier = Modifier) {
    val saved = action.saved
    val stateText =
        stringResource(if (saved) R.string.wishlist_saved_state else R.string.wishlist_not_saved_state)
    TextButton(
        onClick = { action.onSetSaved(!saved) },
        enabled = !action.updating,
        modifier =
            modifier
                .semantics { stateDescription = stateText }
                .testTag(WishlistTestTags.toggle(productId))
    ) {
        Text(stringResource(if (saved) R.string.wishlist_remove else R.string.wishlist_save))
    }
}

@Composable
fun WishlistProductIconButton(
    productId: String,
    action: WishlistProductAction?,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false
) {
    if (action == null) return
    val saved = action.saved
    val stateText =
        stringResource(if (saved) R.string.wishlist_saved_state else R.string.wishlist_not_saved_state)
    val actionDescription =
        stringResource(
            when {
                action.updating -> R.string.wishlist_updating
                saved -> R.string.wishlist_remove
                else -> R.string.wishlist_save
            }
        )
    IconButton(
        onClick = { action.onSetSaved(!saved) },
        enabled = !action.updating,
        modifier =
            modifier
                .size(MINIMUM_TOUCH_TARGET_SIZE)
                .semantics { stateDescription = stateText }
                .testTag(WishlistTestTags.toggle(productId))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (onMedia) {
                Modifier.size(MEDIA_CONTROL_SIZE)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = MEDIA_CONTROL_ALPHA), CircleShape)
            } else {
                Modifier
            }
        ) {
            Icon(
                painter =
                    painterResource(
                        if (saved) R.drawable.ic_nav_wishlist_selected else R.drawable.ic_nav_wishlist
                    ),
                contentDescription = actionDescription,
                modifier = Modifier.size(if (onMedia) MEDIA_ICON_SIZE else STANDARD_ICON_SIZE)
            )
        }
    }
}

data class WishlistProductAction(val saved: Boolean, val updating: Boolean, val onSetSaved: (Boolean) -> Unit)

internal fun WishlistMembershipUiState?.productAction(
    productId: String,
    onSetSaved: ((String, Boolean) -> Unit)?
): WishlistProductAction? {
    if (this == null || onSetSaved == null) return null
    val state = this
    return WishlistProductAction(
        saved = state.isSaved(productId),
        updating = !state.storageAvailable || state.isUpdating(productId),
        onSetSaved = { desired -> onSetSaved(productId, desired) }
    )
}

object WishlistTestTags {
    const val ROOT = "wishlist-root"
    const val CONTENT = "wishlist-content"
    const val LOADING = "wishlist-loading"
    const val EMPTY = "wishlist-empty"
    const val BROWSE = "wishlist-browse"
    const val STORAGE_ERROR = "wishlist-storage-error"
    const val PARTIAL_ERROR = "wishlist-partial-error"
    const val RETRY = "wishlist-retry"
    const val CLEAR = "wishlist-clear"
    const val CLEAR_CONFIRM = "wishlist-clear-confirm"

    fun toggle(productId: String): String = "wishlist-toggle-${productId.substringAfterLast('/')}"

    fun item(productId: String): String = "wishlist-item-${productId.substringAfterLast('/')}"
}

private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
private val MEDIA_CONTROL_SIZE = 32.dp
private val MEDIA_ICON_SIZE = 20.dp
private val STANDARD_ICON_SIZE = 24.dp
private const val MEDIA_CONTROL_ALPHA = 0.9f
