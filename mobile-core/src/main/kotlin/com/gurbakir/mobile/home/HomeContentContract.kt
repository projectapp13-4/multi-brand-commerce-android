package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontVideoSource

sealed interface RemoteHomeSection {
    val sectionGid: String
    val type: String
    val handle: String
    val title: String

    data class CollectionGrid(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val collections: List<HomeResourceKey>
    ) : RemoteHomeSection

    data class FeaturedProduct(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val product: HomeResourceKey
    ) : RemoteHomeSection

    data class Image(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val updatedAt: String,
        val presentation: HomeImagePresentation,
        val media: HomeResourceKey,
        val altText: String,
        val caption: String?,
        val target: RemoteHomeTarget?
    ) : RemoteHomeSection

    data class Video(
        override val sectionGid: String,
        override val type: String,
        override val handle: String,
        override val title: String,
        val updatedAt: String,
        val media: HomeResourceKey,
        val poster: HomeResourceKey?,
        val altText: String,
        val caption: String?,
        val target: RemoteHomeTarget?
    ) : RemoteHomeSection
}

enum class HomeImagePresentation {
    BANNER,
    PHOTO
}

data class RemoteHomeTarget(val key: HomeResourceKey, val handle: String)

enum class HomeDocumentQuality {
    COMPLETE,
    PARTIAL,
    NON_PLAYABLE
}

data class RemoteHomeSnapshot(
    val rootGid: String,
    val rootType: String,
    val rootHandle: String,
    val rootUpdatedAt: String,
    val contentVersion: Int,
    val sections: List<RemoteHomeSection>,
    val declaredSectionCount: Int = sections.size,
    val sectionRevisionDigest: String = "",
    val quality: HomeDocumentQuality = HomeDocumentQuality.COMPLETE
)

enum class HomeContentSource {
    REMOTE,
    LKG,
    PACKAGED
}

sealed interface HomeEditorialState {
    data object IntentionalEmpty : HomeEditorialState

    data class NonEmpty(val sections: List<RemoteHomeSection>) : HomeEditorialState

    data object Packaged : HomeEditorialState
}

enum class HomeResourceStatus {
    COMPLETE,
    PARTIAL,
    NON_PLAYABLE,
    NONE_RENDERABLE,
    HYDRATION_FAILED
}

sealed interface HomeRenderedSection {
    val stableId: String
    val title: HomeText

    data class CollectionGrid(
        override val stableId: String,
        override val title: HomeText,
        val items: List<HomeCollectionItem>
    ) : HomeRenderedSection

    data class FeaturedProduct(
        override val stableId: String,
        override val title: HomeText,
        val item: HomeFeaturedItem
    ) : HomeRenderedSection

    data class Image(
        override val stableId: String,
        override val title: HomeText,
        val presentation: HomeImagePresentation,
        val media: StorefrontMedia,
        val altText: String,
        val caption: String?,
        val target: RemoteHomeTarget?,
        val revisionKey: String
    ) : HomeRenderedSection

    data class Video(
        override val stableId: String,
        override val title: HomeText,
        val sources: List<StorefrontVideoSource>,
        val poster: StorefrontMedia?,
        val altText: String,
        val caption: String?,
        val target: RemoteHomeTarget?,
        val revisionKey: String
    ) : HomeRenderedSection
}

data class HomeCollectionItem(val stableId: String, val label: HomeText, val summary: HomeCollectionSummary)

data class HomeFeaturedItem(val summary: HomeProductSummary)

data class HomePresentation(
    val editorial: HomeEditorialState,
    val renderedSections: List<HomeRenderedSection>,
    val source: HomeContentSource,
    val resourceStatus: HomeResourceStatus,
    val editorialExpiresAtMillis: Long?,
    val refreshing: Boolean = false
)

enum class HomePersistenceStatus {
    CONFIRMED,
    UNCONFIRMED,
    NOT_APPLICABLE
}

enum class HomeLoadTrigger {
    INITIAL,
    MANUAL_REFRESH,
    EXPIRY
}

sealed interface HomeLoadResult {
    data class Accepted(val presentation: HomePresentation, val persistence: HomePersistenceStatus) : HomeLoadResult

    data class Failed(val failure: HomeLoadFailure, val retained: HomePresentation? = null) : HomeLoadResult

    data object Superseded : HomeLoadResult
}
