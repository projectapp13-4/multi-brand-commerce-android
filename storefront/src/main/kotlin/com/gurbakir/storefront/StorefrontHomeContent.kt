package com.gurbakir.storefront

data class HomeDocumentSelector(val type: String, val handle: String)

enum class HomeContentContractId(val rootType: String, val contentVersion: Int, val wireId: String) {
    GATE7_V1("mobile_home", 1, "gate7-v1"),
    PILOT_MEDIA_V2("mobile_home_v2", 2, "pilot-media-v2");

    fun accepts(selector: HomeDocumentSelector): Boolean = selector.type == rootType

    companion object {
        fun fromTuple(rootType: String, contentVersion: Int, wireId: String): HomeContentContractId =
            entries.singleOrNull {
                it.rootType == rootType && it.contentVersion == contentVersion && it.wireId == wireId
            } ?: throw IllegalArgumentException("HOME_CONTRACT_MISMATCH")
    }
}

enum class HomeResourceKind {
    COLLECTION,
    PRODUCT,
    MEDIA_IMAGE,
    VIDEO
}

data class HomeResourceKey(val kind: HomeResourceKind, val gid: String)

data class HomeFieldObservation(val type: String, val value: String?, val key: String? = null)

data class HomeDocumentObservation(
    val rootGid: String,
    val rootHandle: String,
    val rootType: String,
    val rootUpdatedAt: String,
    val schemaVersion: HomeFieldObservation?,
    val declaredSectionCount: HomeFieldObservation?,
    val sections: HomeSectionsFieldObservation?
)

data class HomeSectionsFieldObservation(
    val type: String,
    val value: String?,
    val references: HomeSectionReferencesObservation?,
    val key: String? = null
)

data class HomeSectionReferencesObservation(val nodes: List<HomeSectionNodeObservation>, val hasNextPage: Boolean)

data class HomeSectionNodeObservation(val runtimeType: String, val section: HomeSectionObservation?)

data class HomeSectionObservation(
    val sectionGid: String,
    val handle: String,
    val type: String,
    val title: HomeFieldObservation?,
    val collections: HomeCollectionsFieldObservation?,
    val product: HomeProductFieldObservation?,
    val updatedAt: String? = null,
    val presentation: HomeFieldObservation? = null,
    val media: HomeMediaFieldObservation? = null,
    val poster: HomeMediaFieldObservation? = null,
    val altText: HomeFieldObservation? = null,
    val caption: HomeFieldObservation? = null,
    val productTarget: HomeTargetFieldObservation? = null,
    val collectionTarget: HomeTargetFieldObservation? = null
)

data class HomeMediaFieldObservation(
    val type: String,
    val value: String?,
    val reference: HomeResourceNodeObservation?,
    val key: String? = null
)

data class HomeTargetFieldObservation(
    val type: String,
    val value: String?,
    val reference: HomeTargetNodeObservation?,
    val key: String? = null
)

data class HomeTargetNodeObservation(val runtimeType: String, val target: StorefrontHomeTarget?)

data class StorefrontHomeTarget(val key: HomeResourceKey, val handle: String, val title: String)

data class HomeCollectionsFieldObservation(
    val type: String,
    val value: String?,
    val references: HomeCollectionReferencesObservation?,
    val key: String? = null
)

data class HomeCollectionReferencesObservation(val nodes: List<HomeResourceNodeObservation>, val hasNextPage: Boolean)

data class HomeProductFieldObservation(
    val type: String,
    val value: String?,
    val reference: HomeResourceNodeObservation?,
    val key: String? = null
)

data class HomeResourceNodeObservation(val runtimeType: String, val resource: StorefrontHomeResource?)

sealed interface StorefrontHomeResource {
    val key: HomeResourceKey

    data class Collection(
        override val key: HomeResourceKey,
        val handle: String,
        val title: String,
        val hasProducts: Boolean,
        val media: HomeMediaObservation
    ) : StorefrontHomeResource

    data class Product(
        override val key: HomeResourceKey,
        val handle: String,
        val title: String,
        val availableForSale: Boolean,
        val media: HomeMediaObservation,
        val money: HomeMoneyObservation
    ) : StorefrontHomeResource

    data class MediaImage(override val key: HomeResourceKey, val contentType: String, val media: HomeMediaObservation) :
        StorefrontHomeResource

    data class Video(
        override val key: HomeResourceKey,
        val contentType: String,
        val sources: List<StorefrontVideoSource>,
        val observedSourceCount: Int,
        val preview: HomeMediaObservation
    ) : StorefrontHomeResource
}

data class StorefrontVideoSource(
    val uri: java.net.URI,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int
)

sealed interface HomeMediaObservation {
    data object Absent : HomeMediaObservation

    data object Rejected : HomeMediaObservation

    data class Accepted(val media: StorefrontMedia) : HomeMediaObservation
}

sealed interface HomeMoneyObservation {
    data object Malformed : HomeMoneyObservation

    data class Accepted(val money: StorefrontMoney) : HomeMoneyObservation
}

data class HomeResourceResolution(val requested: HomeResourceKey, val resource: StorefrontHomeResource?)

data class HomeResourceBatch(val resolutions: List<HomeResourceResolution>)
