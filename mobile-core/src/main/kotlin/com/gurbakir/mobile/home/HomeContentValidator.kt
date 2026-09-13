package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.StorefrontHomeResource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private const val ROOT_TYPE = "mobile_home"
private const val COLLECTION_GRID_TYPE = "mobile_home_collection_grid"
private const val FEATURED_PRODUCT_TYPE = "mobile_home_featured_product"
private const val MAX_SECTIONS = 2
private const val MAX_COLLECTIONS = 6
private const val MAX_TITLE_CODE_POINTS = 80
private const val MAX_HANDLE_CHARACTERS = 255

enum class HomeDocumentRejection {
    ROOT_IDENTITY,
    INVALID_VERSION,
    INVALID_DECLARED_COUNT,
    INVALID_SECTION_LIST,
    UNRESOLVED_SECTION_REFERENCES,
    SECTION_OVERFLOW,
    INVALID_SECTION,
    REPEATED_SECTION_GID,
    REPEATED_SECTION_FAMILY,
    CONFLICTING_TYPED_HANDLE,
    INVALID_TITLE,
    INVALID_RESOURCE_LIST,
    INVALID_RESOURCE_REFERENCE
}

sealed interface HomeDocumentValidation {
    data class Accepted(val snapshot: RemoteHomeSnapshot) : HomeDocumentValidation

    data class Rejected(val reason: HomeDocumentRejection) : HomeDocumentValidation
}

@Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "ReturnCount",
    "TooManyFunctions"
) // The closed schema is intentionally audited as explicit fail-closed branches.
class HomeContentValidator(private val json: Json = Json) {
    fun validate(
        selector: HomeDocumentSelector,
        observation: HomeDocumentObservation,
        supportedContentVersion: Int
    ): HomeDocumentValidation {
        if (
            selector.type != ROOT_TYPE ||
            observation.rootType != selector.type ||
            observation.rootHandle != selector.handle ||
            !observation.rootGid.isGid("Metaobject") ||
            observation.rootUpdatedAt.isBlank()
        ) {
            return rejected(HomeDocumentRejection.ROOT_IDENTITY)
        }

        val version = observation.schemaVersion.strictInteger("number_integer")
            ?: return rejected(HomeDocumentRejection.INVALID_VERSION)
        if (version != supportedContentVersion) return rejected(HomeDocumentRejection.INVALID_VERSION)

        val declaredCount = observation.declaredSectionCount.strictInteger("number_integer")
            ?: return rejected(HomeDocumentRejection.INVALID_DECLARED_COUNT)
        if (declaredCount !in 0..MAX_SECTIONS) {
            return rejected(HomeDocumentRejection.INVALID_DECLARED_COUNT)
        }

        val sectionsField = observation.sections
        if (declaredCount == 0 && sectionsField == null) {
            return accepted(observation, version, emptyList())
        }
        if (sectionsField == null || sectionsField.type != "list.mixed_reference") {
            return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        }
        val declaredIds = parseGidList(sectionsField.value, "Metaobject", MAX_SECTIONS)
            ?: return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        val references = sectionsField.references
            ?: return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        if (references.hasNextPage) return rejected(HomeDocumentRejection.SECTION_OVERFLOW)
        if (declaredIds.size != declaredCount) {
            return rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
        }
        if (declaredCount == 0) {
            return if (references.nodes.isEmpty()) {
                accepted(observation, version, emptyList())
            } else {
                rejected(HomeDocumentRejection.INVALID_SECTION_LIST)
            }
        }
        if (references.nodes.size != declaredCount) {
            return rejected(HomeDocumentRejection.UNRESOLVED_SECTION_REFERENCES)
        }

        val sections = mutableListOf<RemoteHomeSection>()
        references.nodes.forEachIndexed { index, node ->
            val section = node.section
                ?: return rejected(HomeDocumentRejection.INVALID_SECTION)
            if (node.runtimeType != "Metaobject" || section.sectionGid != declaredIds[index]) {
                return rejected(HomeDocumentRejection.INVALID_SECTION)
            }
            when (val mapped = mapSection(section)) {
                is SectionMapping.Accepted -> sections += mapped.section
                is SectionMapping.Rejected -> return rejected(mapped.reason)
            }
        }

        if (sections.map { it.sectionGid }.toSet().size != sections.size) {
            return rejected(HomeDocumentRejection.REPEATED_SECTION_GID)
        }
        if (sections.map { it.type }.toSet().size != sections.size) {
            return rejected(HomeDocumentRejection.REPEATED_SECTION_FAMILY)
        }
        val conflictingHandle =
            sections.groupBy { it.type to it.handle }.values.any { sameTypedHandle ->
                sameTypedHandle.map { it.sectionGid }.distinct().size > 1
            }
        if (conflictingHandle) return rejected(HomeDocumentRejection.CONFLICTING_TYPED_HANDLE)

        return accepted(observation, version, sections)
    }

    private fun mapSection(section: HomeSectionObservation): SectionMapping {
        if (!section.sectionGid.isGid("Metaobject") || !section.handle.isShopifyHandle()) {
            return SectionMapping.Rejected(HomeDocumentRejection.INVALID_SECTION)
        }
        val title = section.title.strictText("single_line_text_field")
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_TITLE)
        return when (section.type) {
            COLLECTION_GRID_TYPE -> mapCollectionGrid(section, title)
            FEATURED_PRODUCT_TYPE -> mapFeaturedProduct(section, title)
            else -> SectionMapping.Rejected(HomeDocumentRejection.INVALID_SECTION)
        }
    }

    private fun mapCollectionGrid(section: HomeSectionObservation, title: String): SectionMapping {
        if (section.product != null) return SectionMapping.Rejected(HomeDocumentRejection.INVALID_SECTION)
        val field = section.collections
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        if (field.type != "list.collection_reference") {
            return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        }
        val declaredIds = parseGidList(field.value, "Collection", MAX_COLLECTIONS)
            ?.takeIf { it.isNotEmpty() }
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        if (declaredIds.toSet().size != declaredIds.size) {
            return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        }
        val references = field.references
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        if (references.hasNextPage || references.nodes.size > declaredIds.size) {
            return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_LIST)
        }
        var lastIndex = -1
        val resolvedIds = mutableSetOf<String>()
        references.nodes.forEach { node ->
            val resource = node.resource as? StorefrontHomeResource.Collection
                ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
            val index = declaredIds.indexOf(resource.key.gid)
            if (
                node.runtimeType != "Collection" ||
                resource.key.kind != HomeResourceKind.COLLECTION ||
                index <= lastIndex ||
                !resolvedIds.add(resource.key.gid)
            ) {
                return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
            }
            lastIndex = index
        }
        return SectionMapping.Accepted(
            RemoteHomeSection.CollectionGrid(
                sectionGid = section.sectionGid,
                type = section.type,
                handle = section.handle,
                title = title,
                collections = declaredIds.map { HomeResourceKey(HomeResourceKind.COLLECTION, it) }
            )
        )
    }

    private fun mapFeaturedProduct(section: HomeSectionObservation, title: String): SectionMapping {
        if (section.collections != null) return SectionMapping.Rejected(HomeDocumentRejection.INVALID_SECTION)
        val field = section.product
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
        if (field.type != "product_reference") {
            return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
        }
        val gid = field.value?.takeIf { it.isGid("Product") }
            ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
        field.reference?.let { reference ->
            val resource = reference.resource as? StorefrontHomeResource.Product
                ?: return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
            if (
                reference.runtimeType != "Product" ||
                resource.key.kind != HomeResourceKind.PRODUCT ||
                resource.key.gid != gid
            ) {
                return SectionMapping.Rejected(HomeDocumentRejection.INVALID_RESOURCE_REFERENCE)
            }
        }
        return SectionMapping.Accepted(
            RemoteHomeSection.FeaturedProduct(
                sectionGid = section.sectionGid,
                type = section.type,
                handle = section.handle,
                title = title,
                product = HomeResourceKey(HomeResourceKind.PRODUCT, gid)
            )
        )
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

    private fun HomeFieldObservation?.strictInteger(expectedType: String): Int? {
        if (this?.type != expectedType) return null
        val raw = value ?: return null
        if (!raw.matches(Regex("0|[1-9][0-9]*"))) return null
        return raw.toIntOrNull()
    }

    private fun HomeFieldObservation?.strictText(expectedType: String): String? {
        if (this?.type != expectedType) return null
        val text = value ?: return null
        val codePoints = text.codePointCount(0, text.length)
        return text.takeIf {
            it.isNotBlank() &&
                codePoints in 1..MAX_TITLE_CODE_POINTS &&
                it.none(Char::isISOControl)
        }
    }

    private fun String.isGid(resource: String): Boolean =
        startsWith("gid://shopify/$resource/") && substringAfterLast('/').isNotBlank()

    private fun String.isShopifyHandle(): Boolean =
        length in 1..MAX_HANDLE_CHARACTERS && matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

    private fun accepted(observation: HomeDocumentObservation, version: Int, sections: List<RemoteHomeSection>) =
        HomeDocumentValidation.Accepted(
            RemoteHomeSnapshot(
                rootGid = observation.rootGid,
                rootType = observation.rootType,
                rootHandle = observation.rootHandle,
                rootUpdatedAt = observation.rootUpdatedAt,
                contentVersion = version,
                sections = sections
            )
        )

    private fun rejected(reason: HomeDocumentRejection) = HomeDocumentValidation.Rejected(reason)

    private sealed interface SectionMapping {
        data class Accepted(val section: RemoteHomeSection) : SectionMapping

        data class Rejected(val reason: HomeDocumentRejection) : SectionMapping
    }
}
