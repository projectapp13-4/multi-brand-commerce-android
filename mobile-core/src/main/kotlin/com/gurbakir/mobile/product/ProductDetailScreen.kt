@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "MagicNumber", "TooManyFunctions")

package com.gurbakir.mobile.product

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.mobile.catalog.CatalogError
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.ui.DestinationLevel
import com.gurbakir.mobile.ui.DestinationScaffold
import com.gurbakir.mobile.ui.DestinationTitleAlignment
import com.gurbakir.mobile.ui.PriceBlock
import com.gurbakir.mobile.ui.PriceBlockEmphasis
import com.gurbakir.mobile.ui.consumeDestinationInsets
import com.gurbakir.mobile.ui.withDestinationSpacing
import com.gurbakir.mobile.wishlist.WishlistMembershipUiState
import com.gurbakir.mobile.wishlist.WishlistProductAction
import com.gurbakir.mobile.wishlist.WishlistProductIconButton
import com.gurbakir.mobile.wishlist.productAction
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontProductOption

@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    wishlist: WishlistMembershipUiState? = null
) {
    val mediaOpenerFocusRequester = remember { FocusRequester() }
    var restoreMediaFocus by remember { mutableStateOf(false) }
    LaunchedEffect(state.mediaViewerOpen) {
        if (!state.mediaViewerOpen && restoreMediaFocus) {
            withFrameNanos { }
            mediaOpenerFocusRequester.requestFocus()
            restoreMediaFocus = false
        }
    }
    BoxWithConstraints {
        val expandedLayout = isExpandedProductLayout(maxWidth)
        DestinationScaffold(
            title = stringResource(R.string.product_detail_title),
            level = DestinationLevel.SECONDARY,
            modifier = Modifier.fillMaxSize().testTag(ProductDetailTestTags.ROOT),
            titleAlignment = DestinationTitleAlignment.CENTER,
            onNavigateUp = actions.onBack,
            bottomBar = {
                if (!expandedLayout) {
                    state.product?.let { ProductPurchaseBar(state, actions) }
                }
            }
        ) { padding ->
            ProductDetailBody(
                state = state,
                actions = actions,
                wishlist = wishlist,
                padding = padding,
                expandedLayout = expandedLayout,
                mediaOpenerFocusRequester = mediaOpenerFocusRequester,
                onOpenMediaViewer = {
                    restoreMediaFocus = true
                    actions.onOpenMediaViewer()
                }
            )
        }
        if (state.mediaViewerOpen) {
            ProductMediaViewer(state, actions)
        }
    }
}

@Composable
@Suppress("LongParameterList") // Screen, inset, wishlist, and focus contracts are one destination boundary.
private fun ProductDetailBody(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    wishlist: WishlistMembershipUiState?,
    padding: PaddingValues,
    expandedLayout: Boolean,
    mediaOpenerFocusRequester: FocusRequester,
    onOpenMediaViewer: () -> Unit
) {
    val spacing = LocalBrandSpacing.current
    if (expandedLayout && state.canRenderExpandedProductContent()) {
        ExpandedProductDetailContent(
            state = state,
            actions = actions,
            wishlist = wishlist,
            padding = padding,
            mediaOpenerFocusRequester = mediaOpenerFocusRequester,
            onOpenMediaViewer = onOpenMediaViewer
        )
        return
    }
    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .consumeDestinationInsets(padding)
                .testTag(ProductDetailTestTags.CONTENT),
        contentPadding = padding.withDestinationSpacing(),
        verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp)
    ) {
        when {
            state.loading -> item {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag(ProductDetailTestTags.LOADING))
            }

            state.notFound -> item { ProductNotFound(actions.onBrowse) }

            state.failure != null -> item {
                CatalogError(state.failure, actions.onRetry, ProductDetailTestTags.ERROR)
            }

            state.product != null ->
                productContent(
                    state,
                    actions,
                    wishlist,
                    mediaOpenerFocusRequester,
                    onOpenMediaViewer
                )
        }
    }
}

private fun ProductDetailUiState.canRenderExpandedProductContent(): Boolean =
    product != null && !loading && !notFound && failure == null

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun ExpandedProductDetailContent(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    wishlist: WishlistMembershipUiState?,
    padding: PaddingValues,
    mediaOpenerFocusRequester: FocusRequester,
    onOpenMediaViewer: () -> Unit
) {
    val product = requireNotNull(state.product)
    val spacing = LocalBrandSpacing.current
    Row(
        modifier =
            Modifier.fillMaxSize()
                .consumeDestinationInsets(padding)
                .padding(padding.withDestinationSpacing())
                .testTag(ProductDetailTestTags.CONTENT),
        horizontalArrangement = Arrangement.spacedBy(spacing.sectionDp.dp)
    ) {
        ProductMediaGallery(
            state = state,
            actions = actions,
            openerFocusRequester = mediaOpenerFocusRequester,
            onOpenMediaViewer = onOpenMediaViewer,
            modifier = Modifier.weight(1f)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.generousDp.dp),
                contentPadding = PaddingValues(bottom = spacing.normalDp.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = product.title,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier =
                                Modifier.weight(1f)
                                    .semantics { heading() }
                                    .testTag(ProductDetailTestTags.TITLE)
                        )
                        WishlistProductIconButton(
                            productId = product.id,
                            action =
                                wishlist.productAction(product.id, actions.onSetWishlist)
                        )
                    }
                }
                item {
                    PriceBlock(
                        minimumPrice = state.minimumPrice,
                        maximumPrice = state.maximumPrice,
                        currentPrice = state.selectedVariant?.price,
                        compareAtPrice = state.selectedVariant?.compareAtPrice,
                        availabilityText = state.availabilityText(),
                        unavailable = state.hasUnavailableSelection,
                        emphasis = PriceBlockEmphasis.DETAIL,
                        priceTestTag = ProductDetailTestTags.PRICE,
                        availabilityTestTag = ProductDetailTestTags.AVAILABILITY
                    )
                }
                if (state.invalidRequestedVariant) {
                    item {
                        Text(
                            text = stringResource(R.string.product_variant_changed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(ProductDetailTestTags.INVALID_VARIANT)
                        )
                    }
                }
                state.displayOptions.forEach { option ->
                    item(key = "expanded-product-option-${option.id}") {
                        ProductOptionGroup(option, state, actions)
                    }
                }
                if (product.description.isNotBlank()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
                        ) {
                            Text(
                                stringResource(R.string.product_description_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                product.description,
                                modifier = Modifier.testTag(ProductDetailTestTags.DESCRIPTION)
                            )
                        }
                    }
                }
            }
            ProductPurchaseBar(state, actions, includeNavigationBarInset = false)
        }
    }
}

@Composable
private fun ProductNotFound(onBrowse: () -> Unit) {
    val spacing = LocalBrandSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(ProductDetailTestTags.NOT_FOUND),
        verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)
    ) {
        Text(stringResource(R.string.product_not_found))
        Button(onClick = onBrowse) { Text(stringResource(R.string.product_browse)) }
    }
}

@Suppress("LongMethod") // LazyListScope order is the product information hierarchy contract.
private fun LazyListScope.productContent(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    wishlist: WishlistMembershipUiState?,
    mediaOpenerFocusRequester: FocusRequester,
    onOpenMediaViewer: () -> Unit
) {
    val product = requireNotNull(state.product)
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier =
                    Modifier.weight(1f)
                        .semantics { heading() }
                        .testTag(ProductDetailTestTags.TITLE)
            )
            WishlistProductIconButton(
                productId = product.id,
                action =
                    wishlist.productAction(product.id, actions.onSetWishlist)
            )
        }
    }
    item {
        PriceBlock(
            minimumPrice = state.minimumPrice,
            maximumPrice = state.maximumPrice,
            currentPrice = state.selectedVariant?.price,
            compareAtPrice = state.selectedVariant?.compareAtPrice,
            availabilityText = state.availabilityText(),
            unavailable = state.hasUnavailableSelection,
            emphasis = PriceBlockEmphasis.DETAIL,
            priceTestTag = ProductDetailTestTags.PRICE,
            availabilityTestTag = ProductDetailTestTags.AVAILABILITY
        )
    }
    if (state.invalidRequestedVariant) {
        item {
            Text(
                text = stringResource(R.string.product_variant_changed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ProductDetailTestTags.INVALID_VARIANT)
            )
        }
    }
    item {
        ProductMediaGallery(
            state = state,
            actions = actions,
            openerFocusRequester = mediaOpenerFocusRequester,
            onOpenMediaViewer = onOpenMediaViewer
        )
    }
    state.displayOptions.forEach { option ->
        item(key = "product-option-${option.id}") { ProductOptionGroup(option, state, actions) }
    }
    if (product.description.isNotBlank()) {
        item {
            val spacing = LocalBrandSpacing.current
            Column(verticalArrangement = Arrangement.spacedBy(spacing.normalDp.dp)) {
                Text(
                    stringResource(R.string.product_description_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(product.description, modifier = Modifier.testTag(ProductDetailTestTags.DESCRIPTION))
            }
        }
    }
}

@Composable
private fun ProductOptionGroup(
    option: StorefrontProductOption,
    state: ProductDetailUiState,
    actions: ProductDetailActions
) {
    val spacing = LocalBrandSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)) {
        Text(option.name, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.compactDp.dp)
        ) {
            state.valueStates(option).forEach { valueState ->
                FilterChip(
                    selected = valueState.selected,
                    onClick = { actions.onSelectOption(option.name, valueState.name) },
                    enabled = valueState.existsForCurrentSelection,
                    label = {
                        Text(
                            if (valueState.hasAvailableMatch) {
                                valueState.name
                            } else {
                                stringResource(R.string.product_option_unavailable, valueState.name)
                            }
                        )
                    },
                    modifier = Modifier.testTag(ProductDetailTestTags.option(option.name, valueState.name))
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod") // Gallery controls share one indexed media semantics surface.
private fun ProductMediaGallery(
    state: ProductDetailUiState,
    actions: ProductDetailActions,
    openerFocusRequester: FocusRequester,
    onOpenMediaViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = state.displayMedia
    val index = state.mediaIndex.coerceIn(0, media.lastIndex.coerceAtLeast(0))
    val spacing = LocalBrandSpacing.current
    val openLabel = stringResource(R.string.product_media_expand)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            modifier.fillMaxWidth()
                .aspectRatio(1f)
                .testTag(ProductDetailTestTags.MEDIA)
    ) {
        if (media.isEmpty()) {
            Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.product_no_media)) }
        } else {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(openerFocusRequester)
                    .focusable()
                    .clickable(
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onClick = onOpenMediaViewer
                    )
                    .testTag(ProductDetailTestTags.MEDIA_OPEN)
            ) {
                ProductImage(media[index], requireNotNull(state.product).title)
                Row(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(spacing.compactDp.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaArrowButton(
                        onClick = { actions.onSelectMedia(index - 1) },
                        enabled = index > 0,
                        contentDescription = stringResource(R.string.product_media_previous),
                        testTag = ProductDetailTestTags.MEDIA_PREVIOUS
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = MEDIA_CONTROL_ALPHA)
                    ) {
                        Text(
                            stringResource(R.string.product_media_position, index + 1, media.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(
                                horizontal = spacing.normalDp.dp,
                                vertical = spacing.compactDp.dp
                            )
                        )
                    }
                    MediaArrowButton(
                        onClick = { actions.onSelectMedia(index + 1) },
                        enabled = index < media.lastIndex,
                        contentDescription = stringResource(R.string.product_media_next),
                        rotate = true,
                        testTag = ProductDetailTestTags.MEDIA_NEXT
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaArrowButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    rotate: Boolean = false,
    testTag: String? = null
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = MEDIA_CONTROL_ALPHA)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier.size(MINIMUM_TOUCH_TARGET_SIZE).then(
                    if (testTag == null) Modifier else Modifier.testTag(testTag)
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = contentDescription,
                modifier = Modifier.rotate(if (rotate) HALF_TURN_DEGREES else 0f)
            )
        }
    }
}

@Composable
private fun ProductImage(media: StorefrontMedia, fallbackDescription: String) {
    AsyncImage(
        model = media.uri.toASCIIString(),
        contentDescription = media.altText ?: fallbackDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
@Suppress("LongMethod") // Full-screen media, safe controls, Back, and focus form one dialog contract.
private fun ProductMediaViewer(state: ProductDetailUiState, actions: ProductDetailActions) {
    val media = state.displayMedia
    if (media.isEmpty()) return
    val index = state.mediaIndex.coerceIn(0, media.lastIndex)
    val closeFocusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = actions.onCloseMediaViewer,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false
            )
    ) {
        BackHandler(onBack = actions.onCloseMediaViewer)
        DialogSystemBarAppearance()
        RequestDialogFocus(closeFocusRequester)
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize().testTag(ProductDetailTestTags.MEDIA_VIEWER)
        ) {
            Box(Modifier.fillMaxSize()) {
                ProductImage(media[index], requireNotNull(state.product).title)
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(LocalBrandSpacing.current.normalDp.dp),
                    verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.normalDp.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = MEDIA_VIEWER_CONTROL_ALPHA)
                        ) {
                            Text(
                                stringResource(R.string.product_media_position, index + 1, media.size),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier =
                                    Modifier.padding(
                                        horizontal = LocalBrandSpacing.current.normalDp.dp,
                                        vertical = LocalBrandSpacing.current.compactDp.dp
                                    )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = MEDIA_VIEWER_CONTROL_ALPHA)
                        ) {
                            TextButton(
                                onClick = actions.onCloseMediaViewer,
                                modifier =
                                    Modifier.focusRequester(closeFocusRequester)
                                        .focusable()
                                        .heightIn(min = MINIMUM_TOUCH_TARGET_SIZE)
                                        .testTag(ProductDetailTestTags.MEDIA_CLOSE)
                            ) {
                                Text(stringResource(R.string.close), color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ViewerNavigationButton(
                            text = stringResource(R.string.product_media_previous),
                            onClick = { actions.onSelectMedia(index - 1) },
                            enabled = index > 0
                        )
                        ViewerNavigationButton(
                            text = stringResource(R.string.product_media_next),
                            onClick = { actions.onSelectMedia(index + 1) },
                            enabled = index < media.lastIndex
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerNavigationButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = MEDIA_VIEWER_CONTROL_ALPHA)
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.heightIn(min = MINIMUM_TOUCH_TARGET_SIZE)
        ) {
            Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = DISABLED_ALPHA))
        }
    }
}

private const val MEDIA_CONTROL_ALPHA = 0.92f
private const val MEDIA_VIEWER_CONTROL_ALPHA = 0.72f
internal fun isExpandedProductLayout(availableWidth: Dp): Boolean = availableWidth >= EXPANDED_PRODUCT_WIDTH

private val EXPANDED_PRODUCT_WIDTH = 840.dp
private const val DISABLED_ALPHA = 0.38f
private const val HALF_TURN_DEGREES = 180f
private val MINIMUM_TOUCH_TARGET_SIZE = 48.dp
