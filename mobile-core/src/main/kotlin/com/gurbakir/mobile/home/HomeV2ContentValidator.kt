@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "ReturnCount",
    "TooManyFunctions"
) // The v2 provider boundary is deliberately explicit and fail closed.

package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionReferencesObservation
import com.gurbakir.storefront.HomeCollectionsFieldObservation
import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeMediaFieldObservation
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeProductFieldObservation
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.HomeTargetFieldObservation
import com.gurbakir.storefront.StorefrontHomeResource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private const val V2_ROOT_TYPE = "mobile_home_v2"
private const val V2_COLLECTION_GRID_TYPE = "mobile_home_collection_grid"
private const val V2_FEATURED_PRODUCT_TYPE = "mobile_home_featured_product"
private const val V2_IMAGE_TYPE = "mobile_home_image_v1"
private const val V2_VIDEO_TYPE = "mobile_home_video_v1"
private const val V2_MAX_SECTIONS = 5
private const val V2_MAX_IMAGE_SECTIONS = 2
private const val V2_MAX_HANDLE_LENGTH = 255
private const val V2_MAX_COLLECTIONS = 6
private const val V2_MAX_TITLE_CODE_POINTS = 80
private const val V2_MAX_PRESENTATION_CODE_POINTS = 6
private const val V2_MAX_ALT_CODE_POINTS = 500
private const val V2_MAX_CAPTION_CODE_POINTS = 1_000
private const val V2_MAX_VIDEO_WIDTH = 1280
private const val V2_MAX_VIDEO_HEIGHT = 720
private const val SHA_256_HEX_LENGTH = 64

internal class HomeV2ContentValidator(private val json: Json) {
    fun validate(selector: HomeDocumentSelector, observation: HomeDocumentObservation): HomeDocumentValidation {
        if (!observation.hasValidRoot(selector)) {
            return rejected(HomeDocumentRejection.ROOT_IDENTITY)
        }
        if (observation.schemaVersion.strictInteger("schema_version", "number_integer") != 2) {
            return rejected(HomeDocumentRejection.INVALID_VERSION)
        }
        val declaredCount = observation.declaredSectionCount
            .strictInteger("declared_section_count", "number_integer")
            ?: return rejected(HomeDocumentRejection.INVALID_DECLARED_COUNT)
        if (declaredCount !in 0..V2_MAX_SECTIONS) {
            return rejected(HomeDocumentRejection.INVALID_DECLARED_COUNT)
        }
        val declaredIds = observation.parseDeclaredIds(declaredCount)
            ?: return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        if (declaredCount == 0) {
            return accepted(observation, declaredCount, emptyList(), HomeDocumentQuality.COMPLETE)
        }
        val references = observation.sections?.references
            ?: return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        if (references.hasNextPage || references.nodes.size > declaredCount) {
            return rejected(HomeDocumentRejection.SECTION_OVERFLOW)
        }
        val resolved = linkedMapOf<String, HomeSectionObservation>()
        var lastResolvedIndex = -1
        references.nodes.forEach { node ->
            val section = node.section
                ?: return rejected(HomeDocumentRejection.INVALID_SECTION)
            val index = declaredIds.indexOf(section.sectionGid)
            if (
                node.runtimeType != "Metaobject" ||
                index <= lastResolvedIndex ||
                resolved.put(section.sectionGid, section) != null
            ) {
                return rejected(HomeDocumentRejection.INVALID_SECTION)
            }
            lastResolvedIndex = index
        }

        val acceptedSections = mutableListOf<RemoteHomeSection>()
        var rejectedCount = 0
        var nonPlayableCount = declaredCount - resolved.size
        var playableCount = 0
        val familyCounts = mutableMapOf<String, Int>()
        declaredIds.forEach { gid ->
            val section = resolved[gid] ?: return@forEach
            val mapped = mapSection(section)
            if (mapped == null) {
                rejectedCount += 1
                return@forEach
            }
            val nextFamilyCount = familyCounts.getOrDefault(section.type, 0) + 1
            if (nextFamilyCount > section.type.familyLimit()) {
                rejectedCount += 1
                return@forEach
            }
            familyCounts[section.type] = nextFamilyCount
            acceptedSections += mapped.section
            if (mapped.playable) playableCount += 1 else nonPlayableCount += 1
        }
        if (acceptedSections.isEmpty() && rejectedCount > 0 && nonPlayableCount == 0) {
            return HomeDocumentValidation.NoneRenderable(rejectedCount)
        }
        val quality = when {
            acceptedSections.isEmpty() -> HomeDocumentQuality.NON_PLAYABLE
            playableCount == 0 && rejectedCount == 0 -> HomeDocumentQuality.NON_PLAYABLE
            rejectedCount > 0 || nonPlayableCount > 0 -> HomeDocumentQuality.PARTIAL
            else -> HomeDocumentQuality.COMPLETE
        }
        return accepted(observation, declaredCount, acceptedSections, quality)
    }

    private fun mapSection(section: HomeSectionObservation): MappedSection? {
        if (!section.hasValidIdentity()) return null
        val title = section.title.strictText("title", "single_line_text_field", V2_MAX_TITLE_CODE_POINTS)
            ?: return null
        return when (section.type) {
            V2_COLLECTION_GRID_TYPE -> mapCollectionGrid(section, title)
            V2_FEATURED_PRODUCT_TYPE -> mapFeaturedProduct(section, title)
            V2_IMAGE_TYPE -> mapImage(section, title)
            V2_VIDEO_TYPE -> mapVideo(section, title)
            else -> null
        }
    }

    private fun mapCollectionGrid(section: HomeSectionObservation, title: String): MappedSection? {
        if (section.product != null || section.hasV2OnlyFields()) return null
        val ids = section.collections.validCollectionIds() ?: return null
        val references = section.collections?.references ?: return null
        var previousIndex = -1
        references.nodes.forEach { node ->
            val resource = node.resource as? StorefrontHomeResource.Collection ?: return null
            val index = ids.indexOf(resource.key.gid)
            if (node.runtimeType != "Collection" || index <= previousIndex) return null
            previousIndex = index
        }
        return MappedSection(
            RemoteHomeSection.CollectionGrid(
                section.sectionGid,
                section.type,
                section.handle,
                title,
                ids.map { HomeResourceKey(HomeResourceKind.COLLECTION, it) }
            ),
            playable = references.nodes.isNotEmpty()
        )
    }

    private fun mapFeaturedProduct(section: HomeSectionObservation, title: String): MappedSection? {
        if (section.collections != null || section.hasV2OnlyFields()) return null
        val field = section.product ?: return null
        if (field.key != "product" || field.type != "product_reference") return null
        val gid = field.value?.takeIf { it.isGid("Product") } ?: return null
        val reference = field.reference
        if (reference != null) {
            val product = reference.resource as? StorefrontHomeResource.Product ?: return null
            if (reference.runtimeType != "Product" || product.key.gid != gid) return null
        }
        return MappedSection(
            RemoteHomeSection.FeaturedProduct(
                section.sectionGid,
                section.type,
                section.handle,
                title,
                HomeResourceKey(HomeResourceKind.PRODUCT, gid)
            ),
            playable = reference != null
        )
    }

    private fun mapImage(section: HomeSectionObservation, title: String): MappedSection? {
        if (section.collections != null || section.product != null || section.poster != null) return null
        val presentation = when (
            section.presentation.strictText(
                "presentation",
                "single_line_text_field",
                V2_MAX_PRESENTATION_CODE_POINTS
            )
        ) {
            "banner" -> HomeImagePresentation.BANNER
            "photo" -> HomeImagePresentation.PHOTO
            else -> return null
        }
        val alt = section.altText.strictText("alt_text", "single_line_text_field", V2_MAX_ALT_CODE_POINTS)
            ?: return null
        val caption = section.caption.optionalText("caption", "multi_line_text_field", V2_MAX_CAPTION_CODE_POINTS)
            ?: return null
        val target = section.mapTarget() ?: return null
        val media = section.media.mapImageMedia() ?: return null
        return MappedSection(
            RemoteHomeSection.Image(
                section.sectionGid,
                section.type,
                section.handle,
                title,
                requireNotNull(section.updatedAt),
                presentation,
                media.key,
                alt,
                caption.value,
                target.value
            ),
            playable = media.playable
        )
    }

    private fun mapVideo(section: HomeSectionObservation, title: String): MappedSection? {
        if (section.collections != null || section.product != null || section.presentation != null) return null
        val alt = section.altText.strictText("alt_text", "single_line_text_field", V2_MAX_ALT_CODE_POINTS)
            ?: return null
        val caption = section.caption.optionalText("caption", "multi_line_text_field", V2_MAX_CAPTION_CODE_POINTS)
            ?: return null
        val target = section.mapTarget() ?: return null
        val media = section.media.mapVideoMedia() ?: return null
        val poster = section.poster.mapOptionalPoster() ?: return null
        return MappedSection(
            RemoteHomeSection.Video(
                section.sectionGid,
                section.type,
                section.handle,
                title,
                requireNotNull(section.updatedAt),
                media.key,
                poster.value,
                alt,
                caption.value,
                target.value
            ),
            playable = media.playable
        )
    }

    private fun HomeSectionObservation.mapTarget(): OptionalValue<RemoteHomeTarget?>? {
        val populated = listOfNotNull(productTarget?.value, collectionTarget?.value)
        if (populated.size > 1) return null
        val field = productTarget?.takeIf { it.value != null }
            ?: collectionTarget?.takeIf { it.value != null }
            ?: return OptionalValue(null)
        val expected = if (field === productTarget) TargetExpectation.PRODUCT else TargetExpectation.COLLECTION
        if (field.key != expected.key || field.type != expected.fieldType) return null
        val gid = field.value ?: return OptionalValue(null)
        if (!gid.isGid(expected.gidType)) return null
        val reference = field.reference ?: return OptionalValue(null)
        val target = reference.target ?: return null
        if (
            reference.runtimeType != expected.runtimeType ||
            target.key.kind != expected.kind ||
            target.key.gid != gid ||
            !target.handle.isShopifyHandle()
        ) {
            return null
        }
        return OptionalValue(RemoteHomeTarget(target.key, target.handle))
    }

    private fun HomeMediaFieldObservation?.mapImageMedia(): MediaMapping? {
        if (this == null || key != "media" || type != "file_reference") return null
        val gid = value?.takeIf { it.isGid("MediaImage") } ?: return null
        val resourceNode = reference ?: return MediaMapping(HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, gid), false)
        val resource = resourceNode.resource as? StorefrontHomeResource.MediaImage ?: return null
        if (
            resourceNode.runtimeType != "MediaImage" ||
            resource.key.gid != gid ||
            resource.contentType != "IMAGE"
        ) {
            return null
        }
        return when (resource.media) {
            is HomeMediaObservation.Accepted -> MediaMapping(resource.key, true)
            HomeMediaObservation.Absent -> MediaMapping(resource.key, false)
            HomeMediaObservation.Rejected -> null
        }
    }

    private fun HomeMediaFieldObservation?.mapVideoMedia(): MediaMapping? {
        if (this == null || key != "media" || type != "file_reference") return null
        val gid = value?.takeIf { it.isGid("Video") } ?: return null
        val resourceNode = reference ?: return MediaMapping(HomeResourceKey(HomeResourceKind.VIDEO, gid), false)
        val resource = resourceNode.resource as? StorefrontHomeResource.Video ?: return null
        if (
            resourceNode.runtimeType != "Video" ||
            resource.key.gid != gid ||
            resource.contentType != "VIDEO"
        ) {
            return null
        }
        val playableSources = resource.sources.filter { source ->
            source.mimeType.equals("video/mp4", ignoreCase = true) &&
                source.format.equals("mp4", ignoreCase = true) &&
                source.width in 1..V2_MAX_VIDEO_WIDTH &&
                source.height in 1..V2_MAX_VIDEO_HEIGHT
        }
        if (resource.observedSourceCount > 0 && playableSources.isEmpty()) return null
        return MediaMapping(resource.key, playableSources.isNotEmpty())
    }

    private fun HomeMediaFieldObservation?.mapOptionalPoster(): OptionalValue<HomeResourceKey?>? {
        if (this == null) return OptionalValue(null)
        if (key != "poster" || type != "file_reference") return null
        val gid = value?.takeIf { it.isGid("MediaImage") } ?: return null
        val resourceNode = reference ?: return OptionalValue(HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, gid))
        val resource = resourceNode.resource as? StorefrontHomeResource.MediaImage ?: return null
        if (
            resourceNode.runtimeType != "MediaImage" ||
            resource.key.gid != gid ||
            resource.contentType != "IMAGE" ||
            resource.media is HomeMediaObservation.Rejected
        ) {
            return null
        }
        return OptionalValue(resource.key)
    }

    private fun HomeDocumentObservation.parseDeclaredIds(count: Int): List<String>? {
        val field = sections
        if (count == 0 && field == null) return emptyList()
        if (field?.key != "sections" || field.type != "list.mixed_reference") return null
        val values = parseGidList(field.value, "Metaobject", V2_MAX_SECTIONS) ?: return null
        if (values.size != count || values.distinct().size != values.size) return null
        if (count == 0 && field.references?.nodes.orEmpty().isNotEmpty()) return null
        return values
    }

    private fun HomeDocumentObservation.hasValidRoot(selector: HomeDocumentSelector): Boolean =
        selector.type == V2_ROOT_TYPE &&
            rootType == selector.type &&
            rootHandle == selector.handle &&
            rootGid.isGid("Metaobject") &&
            runCatching { Instant.parse(rootUpdatedAt) }.isSuccess

    private fun HomeSectionObservation.hasValidIdentity(): Boolean = sectionGid.isGid("Metaobject") &&
        handle.isShopifyHandle() &&
        updatedAt?.let { runCatching { Instant.parse(it) }.isSuccess } == true

    private fun HomeSectionObservation.hasV2OnlyFields(): Boolean =
        presentation != null || media != null || poster != null || altText != null || caption != null ||
            productTarget != null || collectionTarget != null

    private fun HomeCollectionsFieldObservation?.validCollectionIds(): List<String>? {
        if (this?.key != "collections" || type != "list.collection_reference") return null
        val ids = parseGidList(value, "Collection", V2_MAX_COLLECTIONS)?.takeIf { it.isNotEmpty() } ?: return null
        if (ids.distinct().size != ids.size) return null
        val resolved = references ?: return null
        if (resolved.hasNextPage || resolved.nodes.size > ids.size) return null
        return ids
    }

    private fun parseGidList(value: String?, resource: String, maximum: Int): List<String>? {
        val array = runCatching { json.parseToJsonElement(value ?: return null) as? JsonArray }.getOrNull()
            ?: return null
        if (array.size > maximum) return null
        return array.map { element ->
            (element as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isGid(resource) }
                ?: return null
        }
    }

    private fun accepted(
        observation: HomeDocumentObservation,
        declaredCount: Int,
        sections: List<RemoteHomeSection>,
        quality: HomeDocumentQuality
    ): HomeDocumentValidation.Accepted = HomeDocumentValidation.Accepted(
        RemoteHomeSnapshot(
            rootGid = observation.rootGid,
            rootType = observation.rootType,
            rootHandle = observation.rootHandle,
            rootUpdatedAt = observation.rootUpdatedAt,
            contentVersion = 2,
            sections = sections,
            declaredSectionCount = declaredCount,
            sectionRevisionDigest = observation.sectionRevisionDigest(),
            quality = quality
        )
    )

    private fun HomeDocumentObservation.sectionRevisionDigest(): String {
        val canonical = buildString {
            sections?.let { field ->
                part(field.value)
                field.references?.nodes.orEmpty().forEach { node ->
                    part(node.runtimeType)
                    node.section?.appendRevisionParts(this)
                }
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .also { require(it.length == SHA_256_HEX_LENGTH) }
    }

    private fun HomeSectionObservation.appendRevisionParts(target: StringBuilder) = with(target) {
        part(sectionGid)
        part(updatedAt)
        part(type)
        part(handle)
        listOf(title, presentation, altText, caption).forEach { field ->
            part(field?.key)
            part(field?.type)
            part(if (field == caption) field?.value?.normalizeHomeCaptionLineEndings() else field?.value)
        }
        part(collections?.value)
        part(product?.value)
        listOf(media, poster).forEach { field ->
            part(field?.key)
            part(field?.type)
            part(field?.value)
            field?.reference?.resource?.appendMediaRevisionParts(this)
        }
        listOf(productTarget, collectionTarget).forEach { field ->
            part(field?.key)
            part(field?.type)
            part(field?.value)
        }
    }

    private fun StorefrontHomeResource.appendMediaRevisionParts(target: StringBuilder) = with(target) {
        part(key.kind.name)
        part(key.gid)
        when (this@appendMediaRevisionParts) {
            is StorefrontHomeResource.MediaImage -> {
                part(contentType)
                part((media as? HomeMediaObservation.Accepted)?.media?.uri?.toString())
                part((media as? HomeMediaObservation.Accepted)?.media?.width?.toString())
                part((media as? HomeMediaObservation.Accepted)?.media?.height?.toString())
            }

            is StorefrontHomeResource.Video -> {
                part(contentType)
                sources.forEach { source ->
                    part(source.uri.toString())
                    part(source.mimeType)
                    part(source.format)
                    part(source.width.toString())
                    part(source.height.toString())
                }
            }

            is StorefrontHomeResource.Collection, is StorefrontHomeResource.Product -> Unit
        }
    }

    private fun StringBuilder.part(value: String?) {
        val normalized = value?.trim().orEmpty()
        append(normalized.length).append(':').append(normalized).append('|')
    }

    private fun HomeFieldObservation?.strictInteger(expectedKey: String, expectedType: String): Int? {
        if (this?.key != expectedKey || type != expectedType) return null
        val raw = value ?: return null
        if (!raw.matches(Regex("0|[1-9][0-9]*"))) return null
        return raw.toIntOrNull()
    }

    private fun HomeFieldObservation?.strictText(
        expectedKey: String,
        expectedType: String,
        maximumCodePoints: Int
    ): String? {
        if (this?.key != expectedKey || type != expectedType) return null
        val text = value ?: return null
        if (expectedType == "multi_line_text_field") {
            return text.normalizeHomeCaptionLineEndings().takeIf { it.isBoundedHomeCaption(maximumCodePoints) }
        }
        val length = text.codePointCount(0, text.length)
        return text.takeIf { it.isNotBlank() && length in 1..maximumCodePoints && it.none(Char::isISOControl) }
    }

    private fun HomeFieldObservation?.optionalText(
        expectedKey: String,
        expectedType: String,
        maximumCodePoints: Int
    ): OptionalValue<String?>? {
        if (this == null) return OptionalValue(null)
        val text = strictText(expectedKey, expectedType, maximumCodePoints) ?: return null
        return OptionalValue(text)
    }

    private fun String.familyLimit(): Int = when (this) {
        V2_COLLECTION_GRID_TYPE, V2_FEATURED_PRODUCT_TYPE, V2_VIDEO_TYPE -> 1
        V2_IMAGE_TYPE -> V2_MAX_IMAGE_SECTIONS
        else -> 0
    }

    private fun String.isGid(resource: String): Boolean =
        matches(Regex("gid://shopify/${Regex.escape(resource)}/[A-Za-z0-9_-]+"))

    private fun String.isShopifyHandle(): Boolean =
        length in 1..V2_MAX_HANDLE_LENGTH && matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

    private fun rejected(reason: HomeDocumentRejection) = HomeDocumentValidation.Rejected(reason)

    private data class MappedSection(val section: RemoteHomeSection, val playable: Boolean)

    private data class MediaMapping(val key: HomeResourceKey, val playable: Boolean)

    private data class OptionalValue<T>(val value: T)

    private enum class TargetExpectation(
        val key: String,
        val fieldType: String,
        val gidType: String,
        val runtimeType: String,
        val kind: HomeResourceKind
    ) {
        PRODUCT("product_target", "product_reference", "Product", "Product", HomeResourceKind.PRODUCT),
        COLLECTION(
            "collection_target",
            "collection_reference",
            "Collection",
            "Collection",
            HomeResourceKind.COLLECTION
        )
    }
}
