@file:Suppress("TooManyFunctions") // Strict closed-shape codec helpers stay private to this format.

package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

const val HOME_CONTENT_STORAGE_VERSION = 1
const val HOME_CONTENT_STORAGE_VERSION_V2 = 2
const val HOME_ESTABLISHMENT_MAX_BYTES = 4 * 1024
const val HOME_SNAPSHOT_MAX_BYTES = 64 * 1024
private const val MAX_STORED_SECTIONS = 2
private const val MAX_STORED_SECTIONS_V2 = 5
private const val MAX_STORED_COLLECTIONS = 6
private const val MAX_STORED_TITLE_CODE_POINTS = 80
private const val MAX_STORED_ALT_CODE_POINTS = 500
private const val MAX_STORED_CAPTION_CODE_POINTS = 1_000

data class HomeContentPartition(
    val applicationId: String,
    val environmentId: String,
    val storefrontDomain: String,
    val rootType: String,
    val rootHandle: String
)

data class HomeEstablishmentRecord(
    val partition: HomeContentPartition,
    val firstEstablishedAtMillis: Long,
    val lastAcceptedContentVersion: Int,
    val storageVersion: Int = HOME_CONTENT_STORAGE_VERSION
)

data class HomeStoredSnapshot(
    val partition: HomeContentPartition,
    val acceptedAtMillis: Long,
    val expiresAtMillis: Long,
    val snapshot: RemoteHomeSnapshot,
    val storageVersion: Int = HOME_CONTENT_STORAGE_VERSION
)

enum class HomeStoreWrite {
    CONFIRMED,
    UNCONFIRMED
}

enum class HomeSnapshotRecovery {
    AVAILABLE,
    MISSING,
    CORRUPT,
    INCOMPATIBLE,
    EXPIRED,
    CLOCK_INVALID
}

sealed interface HomeStoreRead {
    data object NeverEstablished : HomeStoreRead

    data object OwnershipUnknown : HomeStoreRead

    data class Established(
        val marker: HomeEstablishmentRecord,
        val snapshot: HomeStoredSnapshot?,
        val recovery: HomeSnapshotRecovery
    ) : HomeStoreRead
}

interface HomeContentStore {
    suspend fun read(partition: HomeContentPartition, supportedContentVersion: Int, nowMillis: Long): HomeStoreRead

    suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite

    suspend fun evictSnapshot(partition: HomeContentPartition): HomeStoreWrite
}

enum class HomeCodecRejection {
    OVERSIZED,
    MALFORMED,
    UNKNOWN_FIELD,
    INVALID_VALUE
}

sealed interface HomeCodecDecode<out T> {
    data class Accepted<T>(val value: T) : HomeCodecDecode<T>

    data class Rejected(val reason: HomeCodecRejection) : HomeCodecDecode<Nothing>
}

class HomeContentCodec @Inject constructor() {
    private val json: Json = Json
    fun encodeMarker(record: HomeEstablishmentRecord): String {
        require(record.isValid())
        return buildJsonObject {
            put("storageVersion", record.storageVersion)
            put("partition", record.partition.toJson())
            put("firstEstablishedAtMillis", record.firstEstablishedAtMillis)
            put("lastAcceptedContentVersion", record.lastAcceptedContentVersion)
        }.toString().also { require(it.byteSize() <= HOME_ESTABLISHMENT_MAX_BYTES) }
    }

    fun decodeMarker(raw: String): HomeCodecDecode<HomeEstablishmentRecord> =
        decode(raw, HOME_ESTABLISHMENT_MAX_BYTES) { root ->
            root.requireExactKeys(
                "storageVersion",
                "partition",
                "firstEstablishedAtMillis",
                "lastAcceptedContentVersion"
            )
            HomeEstablishmentRecord(
                partition = root.objectValue("partition").toPartition(),
                firstEstablishedAtMillis = root.longValue("firstEstablishedAtMillis"),
                lastAcceptedContentVersion = root.intValue("lastAcceptedContentVersion"),
                storageVersion = root.intValue("storageVersion")
            ).also { require(it.isValid()) }
        }

    fun encodeSnapshot(stored: HomeStoredSnapshot): String {
        require(stored.isValid())
        return buildJsonObject {
            put("storageVersion", stored.storageVersion)
            put("partition", stored.partition.toJson())
            put("acceptedAtMillis", stored.acceptedAtMillis)
            put("expiresAtMillis", stored.expiresAtMillis)
            put("snapshot", stored.snapshot.toJson(stored.storageVersion))
        }.toString().also { require(it.byteSize() <= HOME_SNAPSHOT_MAX_BYTES) }
    }

    fun decodeSnapshot(raw: String): HomeCodecDecode<HomeStoredSnapshot> =
        decode(raw, HOME_SNAPSHOT_MAX_BYTES) { root ->
            root.requireExactKeys(
                "storageVersion",
                "partition",
                "acceptedAtMillis",
                "expiresAtMillis",
                "snapshot"
            )
            val storageVersion = root.intValue("storageVersion")
            HomeStoredSnapshot(
                partition = root.objectValue("partition").toPartition(),
                acceptedAtMillis = root.longValue("acceptedAtMillis"),
                expiresAtMillis = root.longValue("expiresAtMillis"),
                snapshot = root.objectValue("snapshot").toSnapshot(storageVersion),
                storageVersion = storageVersion
            ).also { require(it.isValid()) }
        }

    @Suppress("ReturnCount") // Each exit preserves a distinct bounded rejection classification.
    private fun <T> decode(raw: String, maximumBytes: Int, block: (JsonObject) -> T): HomeCodecDecode<T> {
        if (raw.byteSize() > maximumBytes) return HomeCodecDecode.Rejected(HomeCodecRejection.OVERSIZED)
        val parsed = try {
            json.parseToJsonElement(raw)
        } catch (_: Exception) {
            return HomeCodecDecode.Rejected(HomeCodecRejection.MALFORMED)
        }
        val root = parsed as? JsonObject
            ?: return HomeCodecDecode.Rejected(HomeCodecRejection.INVALID_VALUE)
        return try {
            HomeCodecDecode.Accepted(block(root))
        } catch (_: UnknownFieldException) {
            HomeCodecDecode.Rejected(HomeCodecRejection.UNKNOWN_FIELD)
        } catch (_: Exception) {
            HomeCodecDecode.Rejected(HomeCodecRejection.INVALID_VALUE)
        }
    }
}

private fun HomeContentPartition.toJson(): JsonObject = buildJsonObject {
    put("applicationId", applicationId)
    put("environmentId", environmentId)
    put("storefrontDomain", storefrontDomain)
    put("rootType", rootType)
    put("rootHandle", rootHandle)
}

private fun RemoteHomeSnapshot.toJson(storageVersion: Int): JsonObject = buildJsonObject {
    put("rootGid", rootGid)
    put("rootType", rootType)
    put("rootHandle", rootHandle)
    put("rootUpdatedAt", rootUpdatedAt)
    put("contentVersion", contentVersion)
    if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) {
        put("declaredSectionCount", declaredSectionCount)
        put("sectionRevisionDigest", sectionRevisionDigest)
        put("quality", quality.name)
    }
    put("sections", buildJsonArray { sections.forEach { add(it.toJson(storageVersion)) } })
}

private fun RemoteHomeSection.toJson(storageVersion: Int): JsonObject = when (this) {
    is RemoteHomeSection.CollectionGrid -> buildJsonObject {
        put("kind", "COLLECTION_GRID")
        putSectionIdentity(this@toJson)
        put("collections", buildJsonArray { collections.forEach { add(it.toJson()) } })
    }

    is RemoteHomeSection.FeaturedProduct -> buildJsonObject {
        put("kind", "FEATURED_PRODUCT")
        putSectionIdentity(this@toJson)
        put("product", product.toJson())
    }

    is RemoteHomeSection.Image -> {
        require(storageVersion == HOME_CONTENT_STORAGE_VERSION_V2)
        buildJsonObject {
            put("kind", "IMAGE")
            putSectionIdentity(this@toJson)
            put("updatedAt", updatedAt)
            put("presentation", presentation.name)
            put("media", media.toJson())
            put("altText", altText)
            put("caption", caption?.normalizeHomeCaptionLineEndings())
            put("target", target?.toJson() ?: JsonNull)
        }
    }

    is RemoteHomeSection.Video -> {
        require(storageVersion == HOME_CONTENT_STORAGE_VERSION_V2)
        buildJsonObject {
            put("kind", "VIDEO")
            putSectionIdentity(this@toJson)
            put("updatedAt", updatedAt)
            put("media", media.toJson())
            put("poster", poster?.toJson() ?: JsonNull)
            put("altText", altText)
            put("caption", caption?.normalizeHomeCaptionLineEndings())
            put("target", target?.toJson() ?: JsonNull)
        }
    }
}

private fun RemoteHomeTarget.toJson(): JsonObject = buildJsonObject {
    put("key", key.toJson())
    put("handle", handle)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putSectionIdentity(section: RemoteHomeSection) {
    put("sectionGid", section.sectionGid)
    put("type", section.type)
    put("handle", section.handle)
    put("title", section.title)
}

private fun HomeResourceKey.toJson(): JsonObject = buildJsonObject {
    put("kind", kind.name)
    put("gid", gid)
}

private fun JsonObject.toPartition(): HomeContentPartition {
    requireExactKeys("applicationId", "environmentId", "storefrontDomain", "rootType", "rootHandle")
    return HomeContentPartition(
        applicationId = stringValue("applicationId"),
        environmentId = stringValue("environmentId"),
        storefrontDomain = stringValue("storefrontDomain"),
        rootType = stringValue("rootType"),
        rootHandle = stringValue("rootHandle")
    ).also { require(it.isValid()) }
}

private fun JsonObject.toSnapshot(storageVersion: Int): RemoteHomeSnapshot {
    if (storageVersion == HOME_CONTENT_STORAGE_VERSION) {
        requireExactKeys("rootGid", "rootType", "rootHandle", "rootUpdatedAt", "contentVersion", "sections")
    } else {
        require(storageVersion == HOME_CONTENT_STORAGE_VERSION_V2)
        requireExactKeys(
            "rootGid",
            "rootType",
            "rootHandle",
            "rootUpdatedAt",
            "contentVersion",
            "declaredSectionCount",
            "sectionRevisionDigest",
            "quality",
            "sections"
        )
    }
    val sections = arrayValue("sections").map {
        (it as? JsonObject ?: error("Section must be an object")).toSection(storageVersion)
    }
    return RemoteHomeSnapshot(
        rootGid = stringValue("rootGid"),
        rootType = stringValue("rootType"),
        rootHandle = stringValue("rootHandle"),
        rootUpdatedAt = stringValue("rootUpdatedAt"),
        contentVersion = intValue("contentVersion"),
        sections = sections,
        declaredSectionCount =
            if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) intValue("declaredSectionCount") else sections.size,
        sectionRevisionDigest =
            if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) stringValue("sectionRevisionDigest") else "",
        quality =
            if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) {
                runCatching { HomeDocumentQuality.valueOf(stringValue("quality")) }
                    .getOrElse { error("Invalid quality") }
            } else {
                HomeDocumentQuality.COMPLETE
            }
    )
}

private fun JsonObject.toSection(storageVersion: Int): RemoteHomeSection = when (stringValue("kind")) {
    "COLLECTION_GRID" -> {
        requireExactKeys("kind", "sectionGid", "type", "handle", "title", "collections")
        RemoteHomeSection.CollectionGrid(
            sectionGid = stringValue("sectionGid"),
            type = stringValue("type"),
            handle = stringValue("handle"),
            title = stringValue("title"),
            collections = arrayValue("collections").map {
                (it as? JsonObject ?: error("Key must be an object")).toResourceKey()
            }
        )
    }

    "FEATURED_PRODUCT" -> {
        requireExactKeys("kind", "sectionGid", "type", "handle", "title", "product")
        RemoteHomeSection.FeaturedProduct(
            sectionGid = stringValue("sectionGid"),
            type = stringValue("type"),
            handle = stringValue("handle"),
            title = stringValue("title"),
            product = objectValue("product").toResourceKey()
        )
    }

    "IMAGE" -> toImageSection(storageVersion)

    "VIDEO" -> toVideoSection(storageVersion)

    else -> error("Unknown section kind")
}

private fun JsonObject.toImageSection(storageVersion: Int): RemoteHomeSection.Image {
    require(storageVersion == HOME_CONTENT_STORAGE_VERSION_V2)
    requireExactKeys(
        "kind",
        "sectionGid",
        "type",
        "handle",
        "title",
        "updatedAt",
        "presentation",
        "media",
        "altText",
        "caption",
        "target"
    )
    return RemoteHomeSection.Image(
        sectionGid = stringValue("sectionGid"),
        type = stringValue("type"),
        handle = stringValue("handle"),
        title = stringValue("title"),
        updatedAt = stringValue("updatedAt"),
        presentation =
            runCatching { HomeImagePresentation.valueOf(stringValue("presentation")) }
                .getOrElse { error("Invalid presentation") },
        media = objectValue("media").toResourceKey(),
        altText = stringValue("altText"),
        caption = nullableStringValue("caption")?.normalizeHomeCaptionLineEndings(),
        target = nullableObjectValue("target")?.toTarget()
    )
}

private fun JsonObject.toVideoSection(storageVersion: Int): RemoteHomeSection.Video {
    require(storageVersion == HOME_CONTENT_STORAGE_VERSION_V2)
    requireExactKeys(
        "kind",
        "sectionGid",
        "type",
        "handle",
        "title",
        "updatedAt",
        "media",
        "poster",
        "altText",
        "caption",
        "target"
    )
    return RemoteHomeSection.Video(
        sectionGid = stringValue("sectionGid"),
        type = stringValue("type"),
        handle = stringValue("handle"),
        title = stringValue("title"),
        updatedAt = stringValue("updatedAt"),
        media = objectValue("media").toResourceKey(),
        poster = nullableObjectValue("poster")?.toResourceKey(),
        altText = stringValue("altText"),
        caption = nullableStringValue("caption")?.normalizeHomeCaptionLineEndings(),
        target = nullableObjectValue("target")?.toTarget()
    )
}

private fun JsonObject.toTarget(): RemoteHomeTarget {
    requireExactKeys("key", "handle")
    return RemoteHomeTarget(objectValue("key").toResourceKey(), stringValue("handle"))
}

private fun JsonObject.toResourceKey(): HomeResourceKey {
    requireExactKeys("kind", "gid")
    return HomeResourceKey(
        kind = runCatching { HomeResourceKind.valueOf(stringValue("kind")) }.getOrElse { error("Invalid kind") },
        gid = stringValue("gid")
    )
}

private fun HomeEstablishmentRecord.isValid(): Boolean =
    storageVersion == storageVersionForContent(lastAcceptedContentVersion) &&
        partition.isValid() &&
        firstEstablishedAtMillis >= 0L &&
        partition.rootType == if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) "mobile_home_v2" else "mobile_home"

private fun HomeStoredSnapshot.isValid(): Boolean =
    storageVersion == storageVersionForContent(snapshot.contentVersion) &&
        partition.isValid() &&
        acceptedAtMillis >= 0L &&
        HomeEditorialClockPolicy.deadline(acceptedAtMillis, HOME_EDITORIAL_TTL_MILLIS) == expiresAtMillis &&
        snapshot.isValid(storageVersion) &&
        snapshot.rootType == partition.rootType &&
        snapshot.rootHandle == partition.rootHandle

private fun HomeContentPartition.isValid(): Boolean = applicationId.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,255}")) &&
    environmentId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) &&
    storefrontDomain == storefrontDomain.lowercase() &&
    storefrontDomain.matches(Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) &&
    rootType.matches(Regex("[a-z0-9_]{3,255}")) &&
    rootHandle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

private fun RemoteHomeSnapshot.isValid(storageVersion: Int): Boolean = hasValidRootIdentity(storageVersion) &&
    hasValidSectionSet(storageVersion) &&
    sections.all { it.isValid(storageVersion) }

private fun RemoteHomeSnapshot.hasValidRootIdentity(storageVersion: Int): Boolean = rootGid.isGid("Metaobject") &&
    rootType == (if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) "mobile_home_v2" else "mobile_home") &&
    rootHandle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) &&
    runCatching { Instant.parse(rootUpdatedAt) }.isSuccess &&
    contentVersion == storageVersion &&
    if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) {
        declaredSectionCount in 0..MAX_STORED_SECTIONS_V2 &&
            sections.size <= declaredSectionCount &&
            sectionRevisionDigest.matches(Regex("[a-f0-9]{64}")) &&
            (declaredSectionCount != 0 || sections.isEmpty())
    } else {
        declaredSectionCount == sections.size && sectionRevisionDigest.isEmpty() &&
            quality == HomeDocumentQuality.COMPLETE
    }

private fun RemoteHomeSnapshot.hasValidSectionSet(storageVersion: Int): Boolean {
    val maximum = if (storageVersion == HOME_CONTENT_STORAGE_VERSION_V2) MAX_STORED_SECTIONS_V2 else MAX_STORED_SECTIONS
    val identitiesValid = sections.size <= maximum && sections.map { it.sectionGid }.toSet().size == sections.size
    val familiesValid = if (storageVersion == HOME_CONTENT_STORAGE_VERSION) {
        sections.map { it.type }.toSet().size == sections.size
    } else {
        sections.count { it is RemoteHomeSection.CollectionGrid } <= 1 &&
            sections.count { it is RemoteHomeSection.FeaturedProduct } <= 1 &&
            sections.count { it is RemoteHomeSection.Image } <= 2 &&
            sections.count { it is RemoteHomeSection.Video } <= 1
    }
    return identitiesValid && familiesValid
}

private fun RemoteHomeSection.isValid(storageVersion: Int): Boolean = hasValidIdentity() &&
    when (this) {
        is RemoteHomeSection.CollectionGrid -> hasValidCollections()
        is RemoteHomeSection.FeaturedProduct -> hasValidProduct()
        is RemoteHomeSection.Image -> storageVersion == HOME_CONTENT_STORAGE_VERSION_V2 && hasValidImage()
        is RemoteHomeSection.Video -> storageVersion == HOME_CONTENT_STORAGE_VERSION_V2 && hasValidVideo()
    }

private fun RemoteHomeSection.hasValidIdentity(): Boolean = sectionGid.isGid("Metaobject") &&
    handle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) &&
    title.isNotBlank() &&
    title.codePointCount(0, title.length) <= MAX_STORED_TITLE_CODE_POINTS &&
    title.none(Char::isISOControl)

private fun RemoteHomeSection.CollectionGrid.hasValidCollections(): Boolean = type == "mobile_home_collection_grid" &&
    collections.size in 1..MAX_STORED_COLLECTIONS &&
    collections.distinct().size == collections.size &&
    collections.all { it.kind == HomeResourceKind.COLLECTION && it.gid.isGid("Collection") }

private fun RemoteHomeSection.FeaturedProduct.hasValidProduct(): Boolean = type == "mobile_home_featured_product" &&
    product.kind == HomeResourceKind.PRODUCT &&
    product.gid.isGid("Product")

private fun RemoteHomeSection.Image.hasValidImage(): Boolean = type == "mobile_home_image_v1" &&
    runCatching { Instant.parse(updatedAt) }.isSuccess &&
    media.kind == HomeResourceKind.MEDIA_IMAGE &&
    media.gid.isGid("MediaImage") &&
    altText.isBoundedText(MAX_STORED_ALT_CODE_POINTS) &&
    caption.isOptionalBoundedText(MAX_STORED_CAPTION_CODE_POINTS) &&
    target.isValidTarget()

private fun RemoteHomeSection.Video.hasValidVideo(): Boolean = type == "mobile_home_video_v1" &&
    runCatching { Instant.parse(updatedAt) }.isSuccess &&
    media.kind == HomeResourceKind.VIDEO &&
    media.gid.isGid("Video") &&
    (poster == null || (poster.kind == HomeResourceKind.MEDIA_IMAGE && poster.gid.isGid("MediaImage"))) &&
    altText.isBoundedText(MAX_STORED_ALT_CODE_POINTS) &&
    caption.isOptionalBoundedText(MAX_STORED_CAPTION_CODE_POINTS) &&
    target.isValidTarget()

private fun RemoteHomeTarget?.isValidTarget(): Boolean = this == null ||
    (
        key.kind in setOf(HomeResourceKind.COLLECTION, HomeResourceKind.PRODUCT) &&
            key.gid.isGid(if (key.kind == HomeResourceKind.COLLECTION) "Collection" else "Product") &&
            handle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))
        )

private fun String.isBoundedText(maximumCodePoints: Int): Boolean =
    isNotBlank() && codePointCount(0, length) in 1..maximumCodePoints && none(Char::isISOControl)

private fun String?.isOptionalBoundedText(maximumCodePoints: Int): Boolean =
    this == null || normalizeHomeCaptionLineEndings().isBoundedHomeCaption(maximumCodePoints)

private fun storageVersionForContent(contentVersion: Int): Int = when (contentVersion) {
    1 -> HOME_CONTENT_STORAGE_VERSION
    2 -> HOME_CONTENT_STORAGE_VERSION_V2
    else -> -1
}

private fun JsonObject.requireExactKeys(vararg expected: String) {
    val expectedKeys = expected.toSet()
    if (keys.any { it !in expectedKeys }) throw UnknownFieldException()
    require(keys == expectedKeys)
}

private fun JsonObject.stringValue(key: String): String {
    val primitive = getValue(key) as? JsonPrimitive ?: error("$key must be a string")
    require(primitive.isString)
    return primitive.content
}

private fun JsonObject.nullableStringValue(key: String): String? = when (val value = getValue(key)) {
    JsonNull -> null
    is JsonPrimitive -> value.takeIf { it.isString }?.content ?: error("$key must be a string or null")
    else -> error("$key must be a string or null")
}

private fun JsonObject.longValue(key: String): Long {
    val primitive = getValue(key) as? JsonPrimitive ?: error("$key must be a number")
    require(!primitive.isString)
    return primitive.longOrNull ?: error("$key must be an integer")
}

private fun JsonObject.intValue(key: String): Int = longValue(key).also {
    require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
}.toInt()

private fun JsonObject.objectValue(key: String): JsonObject =
    getValue(key) as? JsonObject ?: error("$key must be an object")

private fun JsonObject.nullableObjectValue(key: String): JsonObject? = when (val value = getValue(key)) {
    JsonNull -> null
    is JsonObject -> value
    else -> error("$key must be an object or null")
}

private fun JsonObject.arrayValue(key: String): JsonArray =
    getValue(key) as? JsonArray ?: error("$key must be an array")

private fun String.byteSize(): Int = toByteArray(StandardCharsets.UTF_8).size

private fun String.isGid(resource: String): Boolean =
    matches(Regex("gid://shopify/${Regex.escape(resource)}/[A-Za-z0-9_-]+"))

private class UnknownFieldException : IllegalArgumentException()
