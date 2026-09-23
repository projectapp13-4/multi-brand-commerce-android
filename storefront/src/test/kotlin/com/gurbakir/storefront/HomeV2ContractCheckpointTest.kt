package com.gurbakir.storefront

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeV2ContractCheckpointTest {
    private val repositoryRoot = findRepositoryRoot(File(requireNotNull(System.getProperty("user.dir"))))
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    @Suppress("LongMethod") // The literal is the closed schema checkpoint and must stay reviewable as one value.
    fun `v2 schema drift is rejected by the closed checkpoint`() {
        val actual = readJson("config/onboarding/shopify-home-schema.v2.json")
        val expected =
            json.parseToJsonElement(
                """
                {
                  "schemaVersion": 2,
                  "contract": "pilot-media-v2",
                  "adminApiVersion": "2026-07",
                  "access": { "admin": "PUBLIC_READ_WRITE", "storefront": "PUBLIC_READ" },
                  "capabilities": { "publishable": true },
                  "rootSectionTypeLimits": {
                    "mobile_home_collection_grid": 1,
                    "mobile_home_featured_product": 1,
                    "mobile_home_image_v1": 2,
                    "mobile_home_video_v1": 1
                  },
                  "definitions": [
                    {
                      "type": "mobile_home_collection_grid",
                      "displayNameKey": "title",
                      "fields": [
                        { "key": "title", "type": "single_line_text_field", "required": true, "validations": { "min": 1, "max": 80 } },
                        { "key": "collections", "type": "list.collection_reference", "required": true, "validations": { "min": 1, "max": 6 } }
                      ]
                    },
                    {
                      "type": "mobile_home_featured_product",
                      "displayNameKey": "title",
                      "fields": [
                        { "key": "title", "type": "single_line_text_field", "required": true, "validations": { "min": 1, "max": 80 } },
                        { "key": "product", "type": "product_reference", "required": true, "validations": {} }
                      ]
                    },
                    {
                      "type": "mobile_home_image_v1",
                      "displayNameKey": "title",
                      "fields": [
                        { "key": "title", "type": "single_line_text_field", "required": true, "validations": { "min": 1, "max": 80 } },
                        { "key": "presentation", "type": "single_line_text_field", "required": true, "validations": { "choices": ["banner", "photo"] } },
                        { "key": "media", "type": "file_reference", "required": true, "validations": { "fileTypes": ["IMAGE"] } },
                        { "key": "alt_text", "type": "single_line_text_field", "required": true, "validations": {} },
                        { "key": "caption", "type": "multi_line_text_field", "required": false, "validations": {} },
                        { "key": "product_target", "type": "product_reference", "required": false, "validations": {} },
                        { "key": "collection_target", "type": "collection_reference", "required": false, "validations": {} }
                      ]
                    },
                    {
                      "type": "mobile_home_video_v1",
                      "displayNameKey": "title",
                      "fields": [
                        { "key": "title", "type": "single_line_text_field", "required": true, "validations": { "min": 1, "max": 80 } },
                        { "key": "media", "type": "file_reference", "required": true, "validations": { "fileTypes": ["VIDEO"] } },
                        { "key": "poster", "type": "file_reference", "required": false, "validations": { "fileTypes": ["IMAGE"] } },
                        { "key": "alt_text", "type": "single_line_text_field", "required": true, "validations": {} },
                        { "key": "caption", "type": "multi_line_text_field", "required": false, "validations": {} },
                        { "key": "product_target", "type": "product_reference", "required": false, "validations": {} },
                        { "key": "collection_target", "type": "collection_reference", "required": false, "validations": {} }
                      ]
                    },
                    {
                      "type": "mobile_home_v2",
                      "displayNameKey": "schema_version",
                      "fields": [
                        { "key": "schema_version", "type": "number_integer", "required": true, "validations": { "min": 2, "max": 2 } },
                        { "key": "declared_section_count", "type": "number_integer", "required": true, "validations": { "min": 0, "max": 5 } },
                        {
                          "key": "sections",
                          "type": "list.mixed_reference",
                          "required": false,
                          "validations": {
                            "max": 5,
                            "definitionTypes": [
                              "mobile_home_collection_grid",
                              "mobile_home_featured_product",
                              "mobile_home_image_v1",
                              "mobile_home_video_v1"
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `root operation cannot lose ordered ids bounded references child revisions or media fields`() {
        val operation = readText(ROOT_OPERATION)

        assertTrue(operation.contains("value\n      references(first: 6)"))
        assertTrue(operation.contains("pageInfo {\n          hasNextPage"))
        assertTrue(
            operation.contains(
                "... on Metaobject {\n            id\n            handle\n            type\n            updatedAt"
            )
        )
        listOf(
            "title",
            "presentation",
            "media",
            "poster",
            "alt_text",
            "caption",
            "product_target",
            "collection_target"
        ).forEach { key ->
            assertTrue(operation.contains("field(key: \"$key\")"), "root operation must request $key")
        }
        assertFalse(operation.contains("field(key: \"target\")"))
        assertTrue(operation.contains("... on MediaImage"))
        assertTrue(operation.contains("... on Video"))
        assertTrue(operation.contains("sources {"))
    }

    @Test
    fun `resource hydration remains one bounded node batch for all supported resource types`() {
        val operation = readText(RESOURCE_OPERATION)

        assertTrue(operation.contains("query HomeV2Resources(${DOLLAR}ids: [ID!]!)"))
        assertTrue(operation.contains("nodes(ids: ${DOLLAR}ids)"))
        listOf("Collection", "Product", "MediaImage", "Video").forEach { type ->
            assertTrue(operation.contains("... on $type"), "resource hydration must support $type")
        }
    }

    @Test
    fun `fixture drift cannot erase the hand derived result matrix`() {
        val expected =
            linkedMapOf(
                "both-targets-populated.json" to "NONE_RENDERABLE",
                "intentional-empty.json" to "INTENTIONAL_EMPTY",
                "none-renderable.json" to "NONE_RENDERABLE",
                "optional-unresolved-target.json" to "ACCEPTED",
                "partial.json" to "PARTIAL",
                "transient-unresolved-media.json" to "NON_PLAYABLE",
                "transient-unresolved-reference.json" to "NON_PLAYABLE",
                "wrong-count.json" to "REJECTED_DOCUMENT",
                "wrong-order.json" to "REJECTED_DOCUMENT",
                "wrong-root-type.json" to "REJECTED_DOCUMENT",
                "wrong-schema-version.json" to "REJECTED_DOCUMENT",
                "wrong-typename.json" to "NONE_RENDERABLE"
            )
        val actualFiles = fixtureDirectory().listFiles { file -> file.extension == "json" }.orEmpty()
            .map(File::getName)
            .filterNot { it in REVISION_FIXTURES }
            .sorted()

        assertEquals(expected.keys.sorted(), actualFiles)
        expected.forEach { (name, expectedClassification) ->
            val root = readJson("$FIXTURE_ROOT/$name").objectAt("data").objectAt("metaobject")
            assertEquals(expectedClassification, classify(root), name)
        }
    }

    @Test
    fun `parent timestamp cannot hide a changed child revision`() {
        val fixture = readJson("$FIXTURE_ROOT/parent-unchanged-child-changed.json")
        val before = fixture.objectAt("before").objectAt("data").objectAt("metaobject")
        val after = fixture.objectAt("after").objectAt("data").objectAt("metaobject")

        assertEquals(before.stringAt("updatedAt"), after.stringAt("updatedAt"))
        assertNotEquals(firstChild(before).stringAt("updatedAt"), firstChild(after).stringAt("updatedAt"))
        assertEquals("ACCEPTED", classify(before))
        assertEquals("ACCEPTED", classify(after))
    }

    @Test
    fun `same media gid and url cannot prove freshness`() {
        val fixture = readJson("$FIXTURE_ROOT/same-id-url-freshness-unsupported.json")
        val before = fixture.objectAt("before").objectAt("data").objectAt("metaobject")
        val after = fixture.objectAt("after").objectAt("data").objectAt("metaobject")

        assertEquals(mediaIdentity(before), mediaIdentity(after))
        assertEquals("UNSUPPORTED_WITHOUT_SEPARATE_FRESHNESS_PROOF", fixture.stringAt("expectedRefreshPolicy"))
    }

    @Test
    fun `default media refresh requires a new file gid`() {
        val fixture = readJson("$FIXTURE_ROOT/new-file-gid-refresh.json")
        val before = fixture.objectAt("before").objectAt("data").objectAt("metaobject")
        val after = fixture.objectAt("after").objectAt("data").objectAt("metaobject")

        assertNotEquals(mediaIdentity(before).first, mediaIdentity(after).first)
        assertEquals("NEW_FILE_GID", fixture.stringAt("expectedRefreshPolicy"))
    }

    @Test
    fun `manifest rejects missing extra or modified checkpoint files`() {
        val manifest = readJson(MANIFEST)
        val files = manifest.arrayAt("checkpointFiles")
        val entries = checkpointEntries(files)
        val expectedPaths =
            buildSet {
                add("config/onboarding/shopify-home-schema.v2.json")
                add(ROOT_OPERATION)
                add(RESOURCE_OPERATION)
                fixtureDirectory().listFiles { file -> file.extension == "json" }.orEmpty()
                    .mapTo(this) { "$FIXTURE_ROOT/${it.name}" }
            }

        assertEquals(expectedPaths, entries.keys)
        entries.forEach { (path, expectedHash) -> assertEquals(expectedHash, sha256(file(path)), path) }
        assertEquals(2, manifest.objectAt("contract").intAt("schemaVersion"))
        assertEquals("pilot-media-v2", manifest.objectAt("contract").stringAt("id"))
        assertEquals("2026-07", manifest.stringAt("storefrontApiVersion"))
        assertEquals(14, manifest.objectAt("contract").intAt("resourceHydrationMaximumUniqueIds"))
    }

    @Test
    fun `manifest duplicate checkpoint paths cannot collapse before hash checks`() {
        val files = readJson(MANIFEST).arrayAt("checkpointFiles")
        val duplicate = JsonArray(files + files.first() + files[1])

        val failure =
            assertThrows(IllegalStateException::class.java) {
                checkpointEntries(duplicate)
            }

        assertTrue(failure.message.orEmpty().contains("Duplicate checkpoint path"))
    }

    @Test
    fun `schema validation and live public client readback remain distinct evidence`() {
        val manifest = readJson(MANIFEST)
        val validation = manifest.objectAt("schemaValidation")
        val readback = manifest.objectAt("actualPublicClientReadback")

        assertEquals("PASS", validation.stringAt("status"))
        assertEquals("shopify-storefront-graphql@1.9.1", validation.stringAt("tool"))
        assertEquals(2, validation.arrayAt("operations").size)
        assertEquals("PASS", readback.stringAt("status"))
        assertEquals("A6", readback.stringAt("acceptance"))
        assertEquals("trial", readback.stringAt("application"))
        assertEquals("development", readback.stringAt("profile"))
        assertEquals("gid://shopify/Metaobject/79655141505", readback.stringAt("rootId"))
        assertEquals(1, readback.objectAt("junit").intAt("tests"))
        assertEquals(0, readback.objectAt("junit").intAt("skipped"))
        assertEquals(0, readback.objectAt("junit").intAt("failures"))
        assertEquals(0, readback.objectAt("junit").intAt("errors"))
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // Each exit is a distinct contract classification boundary.
    private fun classify(root: JsonObject): String {
        if (root.stringAt("type") != "mobile_home_v2") return "REJECTED_DOCUMENT"
        if (!fieldIs(root, "schemaVersion", "schema_version", "number_integer", "2")) return "REJECTED_DOCUMENT"
        val countField = root.objectAt("declaredSectionCount")
        if (countField.stringAt("key") != "declared_section_count" || countField.stringAt("type") != "number_integer") {
            return "REJECTED_DOCUMENT"
        }
        val count = countField.stringAt("value").toIntOrNull() ?: return "REJECTED_DOCUMENT"
        if (count !in 0..5) return "REJECTED_DOCUMENT"
        val sections = root.objectAt("sections")
        if (sections.stringAt("key") != "sections" || sections.stringAt("type") != "list.mixed_reference") {
            return "REJECTED_DOCUMENT"
        }
        val orderedIds =
            runCatching {
                json.parseToJsonElement(sections.stringAt("value")).jsonArray.map { it.jsonPrimitive.content }
            }.getOrElse { return "REJECTED_DOCUMENT" }
        if (orderedIds.size != count || orderedIds.distinct().size != orderedIds.size) return "REJECTED_DOCUMENT"
        if (count == 0) return "INTENTIONAL_EMPTY"
        val references = sections.objectAt("references")
        if (references.objectAt("pageInfo").booleanAt("hasNextPage")) return "REJECTED_DOCUMENT"
        val nodesById = references.arrayAt("nodes").associateBy { it.jsonObject.stringAt("id") }
        var valid = 0
        var rejected = 0
        var nonPlayable = false
        orderedIds.forEach { id ->
            val section = nodesById[id]?.jsonObject
            if (section == null) {
                nonPlayable = true
            } else if (section.stringAt("__typename") != "Metaobject" || !sectionIsValid(section)) {
                rejected += 1
            } else if (sectionMediaIsTransientlyUnresolved(section)) {
                nonPlayable = true
            } else {
                valid += 1
            }
        }
        return when {
            nonPlayable -> "NON_PLAYABLE"
            valid > 0 && rejected > 0 -> "PARTIAL"
            valid == 0 && rejected > 0 -> "NONE_RENDERABLE"
            else -> "ACCEPTED"
        }
    }

    @Suppress("CyclomaticComplexMethod") // The closed type switch mirrors the four approved section schemas.
    private fun sectionIsValid(section: JsonObject): Boolean = when (section.stringAt("type")) {
        "mobile_home_collection_grid" ->
            fieldIsPresent(section, "title", "title", "single_line_text_field") &&
                fieldIsPresent(section, "collections", "collections", "list.collection_reference")

        "mobile_home_featured_product" ->
            fieldIsPresent(section, "title", "title", "single_line_text_field") &&
                fieldIsPresent(section, "product", "product", "product_reference")

        "mobile_home_image_v1" ->
            fieldIsPresent(section, "title", "title", "single_line_text_field") &&
                fieldValueIn(
                    section,
                    "presentation",
                    "presentation",
                    "single_line_text_field",
                    setOf("banner", "photo")
                ) &&
                fieldIsPresent(section, "media", "media", "file_reference") &&
                fieldIsPresent(section, "altText", "alt_text", "single_line_text_field") &&
                referenceHasType(section.objectAt("media"), "MediaImage") &&
                optionalTargetsAreValid(section)

        "mobile_home_video_v1" ->
            fieldIsPresent(section, "title", "title", "single_line_text_field") &&
                fieldIsPresent(section, "media", "media", "file_reference") &&
                fieldIsPresent(section, "altText", "alt_text", "single_line_text_field") &&
                referenceHasType(section.objectAt("media"), "Video") &&
                optionalReferenceHasType(section["poster"], "MediaImage") &&
                optionalTargetsAreValid(section)

        else -> false
    }

    private fun sectionMediaIsTransientlyUnresolved(section: JsonObject): Boolean {
        if (section.stringAt("type") !in setOf("mobile_home_image_v1", "mobile_home_video_v1")) return false
        val media = section.objectAt("media")
        return media.stringAt("value").startsWith("gid://shopify/") && media.nullableObjectAt("reference") == null
    }

    private fun fieldIs(owner: JsonObject, alias: String, key: String, type: String, value: String): Boolean {
        val field = owner.nullableObjectAt(alias) ?: return false
        return field.stringAt("key") == key && field.stringAt("type") == type && field.stringAt("value") == value
    }

    private fun fieldIsPresent(owner: JsonObject, alias: String, key: String, type: String): Boolean {
        val field = owner.nullableObjectAt(alias) ?: return false
        return field.stringAt("key") == key && field.stringAt("type") == type && field.stringAt("value").isNotBlank()
    }

    private fun fieldValueIn(
        owner: JsonObject,
        alias: String,
        key: String,
        type: String,
        values: Set<String>
    ): Boolean {
        val field = owner.nullableObjectAt(alias) ?: return false
        return field.stringAt("key") == key && field.stringAt("type") == type && field.stringAt("value") in values
    }

    private fun referenceHasType(field: JsonObject, expectedType: String): Boolean {
        val reference = field.nullableObjectAt("reference") ?: return true
        return reference.stringAt("__typename") == expectedType
    }

    @Suppress("ReturnCount") // Null field, unresolved reference, and resolved reference are separate valid states.
    private fun optionalReferenceHasType(element: JsonElement?, expectedType: String): Boolean {
        if (element == null || element is JsonNull) return true
        val reference = element.jsonObject.nullableObjectAt("reference") ?: return true
        return reference.stringAt("__typename") == expectedType
    }

    @Suppress("ReturnCount") // Fail-fast keeps the mutually exclusive target contract explicit.
    private fun optionalTargetsAreValid(section: JsonObject): Boolean {
        val productTarget = section["productTarget"]
        val collectionTarget = section["collectionTarget"]
        if (!optionalTargetIsValid(productTarget, "product_target", "product_reference", "Product")) return false
        if (!optionalTargetIsValid(collectionTarget, "collection_target", "collection_reference", "Collection")) {
            return false
        }
        return !(optionalTargetIsPopulated(productTarget) && optionalTargetIsPopulated(collectionTarget))
    }

    @Suppress("ReturnCount") // Each exit maps one optional-field wire state to the closed validity result.
    private fun optionalTargetIsValid(
        element: JsonElement?,
        expectedKey: String,
        expectedFieldType: String,
        expectedRuntimeType: String
    ): Boolean {
        if (element == null) return false
        if (element is JsonNull) return true
        val field = element.jsonObject
        if (field.stringAt("key") != expectedKey || field.stringAt("type") != expectedFieldType) return false
        if (field.stringAt("value").isBlank()) return false
        val reference = field.nullableObjectAt("reference") ?: return true
        return reference.stringAt("__typename") == expectedRuntimeType
    }

    private fun optionalTargetIsPopulated(element: JsonElement?): Boolean =
        element != null && element !is JsonNull && element.jsonObject.stringAt("value").isNotBlank()

    private fun firstChild(root: JsonObject): JsonObject =
        root.objectAt("sections").objectAt("references").arrayAt("nodes").single().jsonObject

    private fun mediaIdentity(root: JsonObject): Pair<String, String> {
        val media = firstChild(root).objectAt("media").objectAt("reference")
        val image = media.objectAt("image")
        return media.stringAt("id") to image.stringAt("url")
    }

    private fun checkpointEntries(files: JsonArray): Map<String, String> {
        val entries =
            files.map { entry ->
                val value = entry.jsonObject
                value.stringAt("path") to value.stringAt("sha256")
            }
        val duplicatePath = entries.groupingBy { it.first }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        check(duplicatePath == null) { "Duplicate checkpoint path: $duplicatePath" }
        check(entries.map { it.first }.toSet().size == entries.size) { "Checkpoint paths must be unique" }
        return entries.associate { it }
    }

    private fun readJson(relativePath: String): JsonObject = json.parseToJsonElement(readText(relativePath)).jsonObject

    private fun readText(relativePath: String): String {
        val target = file(relativePath)
        assertTrue(target.isFile, "$relativePath must exist")
        return target.readText(Charsets.UTF_8).replace("\r\n", "\n")
    }

    private fun fixtureDirectory(): File {
        val directory = file(FIXTURE_ROOT)
        assertTrue(directory.isDirectory, "$FIXTURE_ROOT must exist")
        return directory
    }

    private fun file(relativePath: String): File = File(repositoryRoot, relativePath.replace('/', File.separatorChar))

    private fun sha256(target: File): String {
        assertTrue(target.isFile, "${target.relativeTo(repositoryRoot).invariantSeparatorsPath} must exist")
        return MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun findRepositoryRoot(start: File): File = generateSequence(start.canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject
    private fun JsonObject.arrayAt(key: String): JsonArray = getValue(key).jsonArray
    private fun JsonObject.stringAt(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.intAt(key: String): Int = getValue(key).jsonPrimitive.content.toInt()
    private fun JsonObject.booleanAt(key: String): Boolean = getValue(key).jsonPrimitive.content.toBooleanStrict()
    private fun JsonObject.nullableObjectAt(key: String): JsonObject? =
        this[key]?.takeUnless { it is JsonNull }?.jsonObject

    private companion object {
        const val DOLLAR = '$'
        const val ROOT_OPERATION = "storefront/src/main/graphql/com/gurbakir/storefront/HomeContentV2Metaobject.graphql"
        const val RESOURCE_OPERATION = "storefront/src/main/graphql/com/gurbakir/storefront/HomeV2Resources.graphql"
        const val MANIFEST = "config/onboarding/shopify-home-contract.v2.manifest.json"
        const val FIXTURE_ROOT = "storefront/src/test/resources/home-v2"
        val REVISION_FIXTURES =
            setOf(
                "new-file-gid-refresh.json",
                "parent-unchanged-child-changed.json",
                "same-id-url-freshness-unsupported.json"
            )
    }
}
