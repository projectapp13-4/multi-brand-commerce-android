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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

const val HOME_CONTENT_STORAGE_VERSION = 1
const val HOME_ESTABLISHMENT_MAX_BYTES = 4 * 1024
const val HOME_SNAPSHOT_MAX_BYTES = 64 * 1024
private const val MAX_STORED_SECTIONS = 2
private const val MAX_STORED_COLLECTIONS = 6
private const val MAX_STORED_TITLE_CODE_POINTS = 80

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
            put("snapshot", stored.snapshot.toJson())
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
            HomeStoredSnapshot(
                partition = root.objectValue("partition").toPartition(),
                acceptedAtMillis = root.longValue("acceptedAtMillis"),
                expiresAtMillis = root.longValue("expiresAtMillis"),
                snapshot = root.objectValue("snapshot").toSnapshot(),
                storageVersion = root.intValue("storageVersion")
            ).also { require(it.isValid()) }
        }

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

private fun RemoteHomeSnapshot.toJson(): JsonObject = buildJsonObject {
    put("rootGid", rootGid)
    put("rootType", rootType)
    put("rootHandle", rootHandle)
    put("rootUpdatedAt", rootUpdatedAt)
    put("contentVersion", contentVersion)
    put("sections", buildJsonArray { sections.forEach { add(it.toJson()) } })
}

private fun RemoteHomeSection.toJson(): JsonObject = when (this) {
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

private fun JsonObject.toSnapshot(): RemoteHomeSnapshot {
    requireExactKeys("rootGid", "rootType", "rootHandle", "rootUpdatedAt", "contentVersion", "sections")
    return RemoteHomeSnapshot(
        rootGid = stringValue("rootGid"),
        rootType = stringValue("rootType"),
        rootHandle = stringValue("rootHandle"),
        rootUpdatedAt = stringValue("rootUpdatedAt"),
        contentVersion = intValue("contentVersion"),
        sections = arrayValue("sections").map { (it as? JsonObject ?: error("Section must be an object")).toSection() }
    )
}

private fun JsonObject.toSection(): RemoteHomeSection = when (stringValue("kind")) {
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

    else -> error("Unknown section kind")
}

private fun JsonObject.toResourceKey(): HomeResourceKey {
    requireExactKeys("kind", "gid")
    return HomeResourceKey(
        kind = runCatching { HomeResourceKind.valueOf(stringValue("kind")) }.getOrElse { error("Invalid kind") },
        gid = stringValue("gid")
    )
}

private fun HomeEstablishmentRecord.isValid(): Boolean = storageVersion == HOME_CONTENT_STORAGE_VERSION &&
    partition.isValid() &&
    firstEstablishedAtMillis >= 0L &&
    lastAcceptedContentVersion > 0

private fun HomeStoredSnapshot.isValid(): Boolean = storageVersion == HOME_CONTENT_STORAGE_VERSION &&
    partition.isValid() &&
    acceptedAtMillis >= 0L &&
    HomeEditorialClockPolicy.deadline(acceptedAtMillis, HOME_EDITORIAL_TTL_MILLIS) == expiresAtMillis &&
    snapshot.isValid() &&
    snapshot.rootType == partition.rootType &&
    snapshot.rootHandle == partition.rootHandle

private fun HomeContentPartition.isValid(): Boolean = applicationId.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,255}")) &&
    environmentId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) &&
    storefrontDomain == storefrontDomain.lowercase() &&
    storefrontDomain.matches(Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) &&
    rootType.matches(Regex("[a-z0-9_]{3,255}")) &&
    rootHandle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

private fun RemoteHomeSnapshot.isValid(): Boolean =
    hasValidRootIdentity() && hasValidSectionSet() && sections.all(RemoteHomeSection::isValid)

private fun RemoteHomeSnapshot.hasValidRootIdentity(): Boolean = rootGid.isGid("Metaobject") &&
    rootType == "mobile_home" &&
    rootHandle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) &&
    runCatching { Instant.parse(rootUpdatedAt) }.isSuccess &&
    contentVersion > 0

private fun RemoteHomeSnapshot.hasValidSectionSet(): Boolean = sections.size <= MAX_STORED_SECTIONS &&
    sections.map { it.sectionGid }.toSet().size == sections.size &&
    sections.map { it.type }.toSet().size == sections.size

private fun RemoteHomeSection.isValid(): Boolean = hasValidIdentity() &&
    when (this) {
        is RemoteHomeSection.CollectionGrid -> hasValidCollections()
        is RemoteHomeSection.FeaturedProduct -> hasValidProduct()
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

private fun JsonObject.arrayValue(key: String): JsonArray =
    getValue(key) as? JsonArray ?: error("$key must be an array")

private fun String.byteSize(): Int = toByteArray(StandardCharsets.UTF_8).size

private fun String.isGid(resource: String): Boolean =
    matches(Regex("gid://shopify/${Regex.escape(resource)}/[A-Za-z0-9_-]+"))

private class UnknownFieldException : IllegalArgumentException()
