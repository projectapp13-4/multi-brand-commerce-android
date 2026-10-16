package com.gurbakir.mobile.catalog

import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gurbakir.mobile.ui.LARGE_TEXT_FONT_SCALE
import com.gurbakir.storefront.CatalogDiscoveryRequest

sealed interface CatalogPresentation {
    data object Standard : CatalogPresentation

    data class Gallery(val featuredHandle: String, val allProductsHandle: String, val copy: CatalogGalleryCopy) :
        CatalogPresentation {
        init {
            require(CatalogDiscoveryRequest(featuredHandle).isValid())
            require(CatalogDiscoveryRequest(allProductsHandle).isValid())
            require(featuredHandle != allProductsHandle)
        }
    }
}

data class CatalogGalleryCopy(
    @StringRes val screenTitle: Int,
    @StringRes val introKicker: Int,
    @StringRes val introTitle: Int,
    @StringRes val introBody: Int,
    @StringRes val continuationTitle: Int,
    @StringRes val childrenTitle: Int
)

internal data class CatalogGallerySections(
    val featured: CatalogCategoryItem?,
    val covers: List<CatalogCategoryItem>,
    val children: List<CatalogCategoryItem>,
    val remaining: List<CatalogCategoryItem>,
    val allProducts: CatalogCategoryItem?
)

internal fun gallerySections(
    items: List<CatalogCategoryItem>,
    policy: CatalogPresentation.Gallery
): CatalogGallerySections {
    val roots = items.filter { it.depth == 0 }
    val featured = roots.firstOrNull { it.collection.handle == policy.featuredHandle }
    val allProducts = roots.firstOrNull { it.collection.handle == policy.allProductsHandle }
    val covers = roots.filterNot { it == featured || it == allProducts }.take(GALLERY_COVER_LIMIT)
    val children = featured?.let { parent -> items.filter { it.parentStableId == parent.stableId } }.orEmpty()
    val assigned = (listOfNotNull(featured, allProducts) + covers + children).map { it.stableId }.toSet()
    return CatalogGallerySections(featured, covers, children, items.filterNot { it.stableId in assigned }, allProducts)
}

internal fun galleryColumnCount(availableWidth: Dp, fontScale: Float): Int =
    if (fontScale >= LARGE_TEXT_FONT_SCALE || availableWidth < GALLERY_TWO_COLUMN_WIDTH) 1 else 2

private const val GALLERY_COVER_LIMIT = 4
private val GALLERY_TWO_COLUMN_WIDTH = 300.dp
