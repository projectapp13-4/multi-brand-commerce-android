package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeContentContractId
import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeMediaFieldObservation
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeResourceBatch
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeResourceNodeObservation
import com.gurbakir.storefront.HomeResourceResolution
import com.gurbakir.storefront.HomeSectionNodeObservation
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.HomeSectionReferencesObservation
import com.gurbakir.storefront.HomeSectionsFieldObservation
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontHomeResource
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontResult
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class HomeContentV2RepositoryTest {
    @Test
    fun `restart rehydrates renamed and missing image and video targets without discarding media`() = runTest {
        val targetKey = HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/42")
        val videoKey = HomeResourceKey(HomeResourceKind.VIDEO, "gid://shopify/Video/43")
        val original = storedImageSnapshot()
        val image = (original.snapshot.sections.single() as RemoteHomeSection.Image).copy(
            target = RemoteHomeTarget(targetKey, "old-handle"),
            caption = "First\r\nSecond\rThird"
        )
        val video = RemoteHomeSection.Video(
            "gid://shopify/Metaobject/video", "mobile_home_video_v1", "video", "Video",
            "2026-09-17T18:59:00Z", videoKey, null, "Video", null, RemoteHomeTarget(targetKey, "old-handle")
        )
        val stored = original.copy(
            expiresAtMillis = original.acceptedAtMillis + HOME_EDITORIAL_TTL_MILLIS,
            snapshot = original.snapshot.copy(sections = listOf(image, video), declaredSectionCount = 2)
        )
        val codec = HomeContentCodec()
        val restarted = (codec.decodeSnapshot(codec.encodeSnapshot(stored)) as HomeCodecDecode.Accepted).value
        val media = listOf(imageResource("11"), videoResource(videoKey))
        listOf("renamed-handle", null).forEach { currentHandle ->
            val target = currentHandle?.let {
                StorefrontHomeResource.Collection(targetKey, it, "Current", true, HomeMediaObservation.Absent)
            }
            val gateway = FakeGateway(
                wrongVideoDocument(),
                HomeResourceBatch(
                    (media + listOfNotNull(target)).map {
                        HomeResourceResolution(it.key, it)
                    }
                )
            )
            val store = RecordingStore(
                HomeStoreRead.Established(
                    HomeEstablishmentRecord(PARTITION, NOW - 1_000, 2, HOME_CONTENT_STORAGE_VERSION_V2),
                    restarted,
                    HomeSnapshotRecovery.AVAILABLE
                )
            )
            val result =
                assertInstanceOf(
                    HomeLoadResult.Accepted::class.java,
                    repository(gateway, store).load(HomeLoadTrigger.INITIAL)
                )
            assertEquals(HomeContentSource.LKG, result.presentation.source)
            assertEquals(2, result.presentation.renderedSections.size)
            val expected = currentHandle?.let { RemoteHomeTarget(targetKey, it) }
            assertEquals(expected, (result.presentation.renderedSections[0] as HomeRenderedSection.Image).target)
            assertEquals(
                "First\nSecond\nThird",
                (result.presentation.renderedSections[0] as HomeRenderedSection.Image).caption
            )
            assertEquals(expected, (result.presentation.renderedSections[1] as HomeRenderedSection.Video).target)
            assertEquals(listOf(IMAGE_KEY, targetKey, videoKey), gateway.requested)
        }
    }

    private fun videoResource(key: HomeResourceKey) = StorefrontHomeResource.Video(
        key,
        "VIDEO",
        listOf(
            com.gurbakir.storefront.StorefrontVideoSource(
                URI("https://cdn.shopify.com/videos/video.mp4"),
                "video/mp4",
                "mp4",
                1280,
                720
            )
        ),
        1,
        HomeMediaObservation.Absent
    )

    @Test
    fun `resolved v2 image renders and persists only storage v2 editorial identity`() = runTest {
        val image = imageResource("10")
        val store = RecordingStore(HomeStoreRead.NeverEstablished)
        val repository = repository(FakeGateway(imageDocument(image)), store)

        val result = assertInstanceOf(HomeLoadResult.Accepted::class.java, repository.load(HomeLoadTrigger.INITIAL))

        assertEquals(HomePersistenceStatus.CONFIRMED, result.persistence)
        assertEquals(HomeResourceStatus.COMPLETE, result.presentation.resourceStatus)
        val rendered =
            assertInstanceOf(HomeRenderedSection.Image::class.java, result.presentation.renderedSections.single())
        assertEquals(URI("https://cdn.shopify.com/s/files/1/image-10.jpg"), rendered.media.uri)
        assertEquals(HomeImagePresentation.BANNER, rendered.presentation)
        assertEquals("c".repeat(64).length, requireNotNull(store.persisted).snapshot.sectionRevisionDigest.length)
        assertEquals(HOME_CONTENT_STORAGE_VERSION_V2, store.persisted?.storageVersion)
    }

    @Test
    fun `none renderable refresh keeps fresh v2 lkg and never replaces it`() = runTest {
        val image = imageResource("11")
        val stored = storedImageSnapshot()
        val read =
            HomeStoreRead.Established(
                HomeEstablishmentRecord(PARTITION, NOW - 1_000L, 2, HOME_CONTENT_STORAGE_VERSION_V2),
                stored,
                HomeSnapshotRecovery.AVAILABLE
            )
        val store = RecordingStore(read)
        val gateway =
            FakeGateway(wrongVideoDocument(), HomeResourceBatch(listOf(HomeResourceResolution(IMAGE_KEY, image))))

        val result =
            assertInstanceOf(
                HomeLoadResult.Accepted::class.java,
                repository(gateway, store).load(HomeLoadTrigger.INITIAL)
            )

        assertEquals(HomeContentSource.LKG, result.presentation.source)
        assertInstanceOf(HomeRenderedSection.Image::class.java, result.presentation.renderedSections.single())
        assertEquals(0, store.writes)
    }

    @Test
    fun `temporarily unresolved media persists as non playable rather than empty`() = runTest {
        val store = RecordingStore(HomeStoreRead.NeverEstablished)

        val result = assertInstanceOf(
            HomeLoadResult.Accepted::class.java,
            repository(FakeGateway(unresolvedImageDocument()), store).load(HomeLoadTrigger.INITIAL)
        )

        assertEquals(HomeResourceStatus.NON_PLAYABLE, result.presentation.resourceStatus)
        assertInstanceOf(HomeEditorialState.NonEmpty::class.java, result.presentation.editorial)
        assertEquals(emptyList<HomeRenderedSection>(), result.presentation.renderedSections)
        assertEquals(HomeDocumentQuality.NON_PLAYABLE, store.persisted?.snapshot?.quality)
        val storedSection = store.persisted?.snapshot?.sections?.single() as RemoteHomeSection.Image
        assertEquals(HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/12"), storedSection.media)
    }

    private fun repository(gateway: StorefrontHomeGateway, store: HomeContentStore) = DefaultHomeContentRepository(
        gateway = gateway,
        configuration =
            HomeConfiguration(
                HomeRemoteSource.ShopifyMetaobject(SELECTOR, HomeContentContractId.PILOT_MEDIA_V2),
                homeTestPackagedFallback
            ),
        validator = HomeContentValidator(),
        store = store,
        coordinator = HomeContentAcceptanceCoordinator(store),
        clock = FixedClock(NOW),
        partition = PARTITION
    )

    private fun imageDocument(image: StorefrontHomeResource.MediaImage): HomeDocumentObservation = root(
        HomeSectionObservation(
            sectionGid = "gid://shopify/Metaobject/image",
            handle = "hero",
            type = "mobile_home_image_v1",
            title = field("title", "single_line_text_field", "Hero"),
            collections = null,
            product = null,
            updatedAt = "2026-09-17T19:59:00Z",
            presentation = field("presentation", "single_line_text_field", "banner"),
            media =
                HomeMediaFieldObservation(
                    "file_reference",
                    image.key.gid,
                    HomeResourceNodeObservation("MediaImage", image),
                    "media"
                ),
            altText = field("alt_text", "single_line_text_field", "Copper cookware")
        )
    )

    private fun wrongVideoDocument(): HomeDocumentObservation = root(
        HomeSectionObservation(
            sectionGid = "gid://shopify/Metaobject/wrong-video",
            handle = "wrong-video",
            type = "mobile_home_video_v1",
            title = field("title", "single_line_text_field", "Wrong video"),
            collections = null,
            product = null,
            updatedAt = "2026-09-17T19:59:00Z",
            media =
                HomeMediaFieldObservation(
                    "file_reference",
                    "gid://shopify/Video/30",
                    HomeResourceNodeObservation("MediaImage", imageResource("30")),
                    "media"
                ),
            altText = field("alt_text", "single_line_text_field", "Wrong media")
        )
    )

    private fun unresolvedImageDocument(): HomeDocumentObservation = root(
        HomeSectionObservation(
            sectionGid = "gid://shopify/Metaobject/unresolved-image",
            handle = "unresolved-image",
            type = "mobile_home_image_v1",
            title = field("title", "single_line_text_field", "Unresolved image"),
            collections = null,
            product = null,
            updatedAt = "2026-09-17T19:59:00Z",
            presentation = field("presentation", "single_line_text_field", "photo"),
            media =
                HomeMediaFieldObservation(
                    "file_reference",
                    "gid://shopify/MediaImage/12",
                    reference = null,
                    key = "media"
                ),
            altText = field("alt_text", "single_line_text_field", "Awaiting provider hydration")
        )
    )

    private fun root(section: HomeSectionObservation): HomeDocumentObservation = HomeDocumentObservation(
        rootGid = "gid://shopify/Metaobject/root-v2",
        rootHandle = "primary",
        rootType = "mobile_home_v2",
        rootUpdatedAt = "2026-09-17T20:00:00Z",
        schemaVersion = field("schema_version", "number_integer", "2"),
        declaredSectionCount = field("declared_section_count", "number_integer", "1"),
        sections =
            HomeSectionsFieldObservation(
                "list.mixed_reference",
                "[\"${section.sectionGid}\"]",
                HomeSectionReferencesObservation(listOf(HomeSectionNodeObservation("Metaobject", section)), false),
                "sections"
            )
    )

    private fun storedImageSnapshot() = HomeStoredSnapshot(
        partition = PARTITION,
        acceptedAtMillis = NOW - 1_000L,
        expiresAtMillis = NOW + 10_000L,
        snapshot =
            RemoteHomeSnapshot(
                rootGid = "gid://shopify/Metaobject/root-v2",
                rootType = "mobile_home_v2",
                rootHandle = "primary",
                rootUpdatedAt = "2026-09-17T19:00:00Z",
                contentVersion = 2,
                sections =
                    listOf(
                        RemoteHomeSection.Image(
                            "gid://shopify/Metaobject/image",
                            "mobile_home_image_v1",
                            "hero",
                            "Hero",
                            "2026-09-17T18:59:00Z",
                            HomeImagePresentation.BANNER,
                            IMAGE_KEY,
                            "Copper cookware",
                            null,
                            null
                        )
                    ),
                declaredSectionCount = 1,
                sectionRevisionDigest = "d".repeat(64),
                quality = HomeDocumentQuality.COMPLETE
            ),
        storageVersion = HOME_CONTENT_STORAGE_VERSION_V2
    )

    private fun imageResource(suffix: String) = StorefrontHomeResource.MediaImage(
        key = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/$suffix"),
        contentType = "IMAGE",
        media =
            HomeMediaObservation.Accepted(
                StorefrontMedia(
                    URI("https://cdn.shopify.com/s/files/1/image-$suffix.jpg"),
                    "Copper cookware",
                    1200,
                    800
                )
            )
    )

    private fun field(key: String, type: String, value: String) = HomeFieldObservation(type, value, key)

    private class RecordingStore(private val read: HomeStoreRead) : HomeContentStore {
        var persisted: HomeStoredSnapshot? = null
        var writes = 0
        override suspend fun read(partition: HomeContentPartition, supportedContentVersion: Int, nowMillis: Long) = read
        override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite {
            writes += 1
            persisted = snapshot
            return HomeStoreWrite.CONFIRMED
        }
        override suspend fun evictSnapshot(partition: HomeContentPartition) = HomeStoreWrite.CONFIRMED
    }

    private class FakeGateway(
        private val document: HomeDocumentObservation,
        private val resources: HomeResourceBatch = HomeResourceBatch(emptyList())
    ) : StorefrontHomeGateway {
        var requested: List<HomeResourceKey> = emptyList()
        override suspend fun loadHomeDocument(selector: HomeDocumentSelector) = StorefrontResult.Success(document)
        override suspend fun loadHomeResources(keys: List<HomeResourceKey>): StorefrontResult<HomeResourceBatch> {
            requested = keys
            return StorefrontResult.Success(
                HomeResourceBatch(
                    keys.map { key ->
                        HomeResourceResolution(key, resources.resolutions.firstOrNull { it.requested == key }?.resource)
                    }
                )
            )
        }
        override suspend fun loadHomeCollection(handle: String) = StorefrontResult.Success(null)
        override suspend fun loadHomeProduct(handle: String) = StorefrontResult.Success(null)
    }

    private class FixedClock(private val now: Long) : HomeEditorialClock {
        override fun nowMillis(): Long = now
        override suspend fun awaitUntil(deadlineMillis: Long) = Unit
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val SELECTOR = HomeDocumentSelector("mobile_home_v2", "primary")
        val PARTITION =
            HomeContentPartition(
                "com.projectapp134.multibrandtrial.dev",
                "development",
                "multi-brand-trial-store.myshopify.com",
                "mobile_home_v2",
                "primary"
            )
        val IMAGE_KEY = HomeResourceKey(HomeResourceKind.MEDIA_IMAGE, "gid://shopify/MediaImage/11")
    }
}
