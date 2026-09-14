@file:Suppress("LongMethod")

package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionReferencesObservation
import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeCollectionsFieldObservation
import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeMoneyObservation
import com.gurbakir.storefront.HomeProductFieldObservation
import com.gurbakir.storefront.HomeResourceBatch
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeResourceNodeObservation
import com.gurbakir.storefront.HomeResourceResolution
import com.gurbakir.storefront.HomeSectionNodeObservation
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.HomeSectionReferencesObservation
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontHomeResource
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontResult
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class HomeCombinedContentRepositoryTest {
    @Test
    fun `remote order is preserved and malformed product presentation does not discard collection`() = runTest {
        val gateway = FakeGateway(document = StorefrontResult.Success(document()))
        val repository = repository(gateway, HomeStoreRead.NeverEstablished)

        val result = assertInstanceOf(HomeLoadResult.Accepted::class.java, repository.load(HomeLoadTrigger.INITIAL))

        assertEquals(HomeContentSource.REMOTE, result.presentation.source)
        assertEquals(HomeResourceStatus.PARTIAL, result.presentation.resourceStatus)
        assertEquals(
            listOf("gid://shopify/Metaobject/grid"),
            result.presentation.renderedSections.map { it.stableId }
        )
        assertEquals(listOf("grid", "featured"), result.presentation.editorial.sectionStableIds())
    }

    @Test
    fun `first install failure uses packaged fallback and established failure never resurrects it`() = runTest {
        val failure = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
        val firstInstall = repository(FakeGateway(document = failure), HomeStoreRead.NeverEstablished)
        val established = repository(FakeGateway(document = failure), establishedWithoutSnapshot())

        val fallback = assertInstanceOf(HomeLoadResult.Accepted::class.java, firstInstall.load(HomeLoadTrigger.INITIAL))
        val unavailable = assertInstanceOf(HomeLoadResult.Failed::class.java, established.load(HomeLoadTrigger.INITIAL))

        assertEquals(HomeContentSource.PACKAGED, fallback.presentation.source)
        assertEquals(HomeLoadFailureCategory.CONNECTION, unavailable.failure.category)
    }

    @Test
    fun `packaged fallback is partial when only configured collection cards render`() = runTest {
        val failure = StorefrontResult.Failure(StorefrontFailure.Transport(retryable = true))
        val gateway =
            FakeGateway(
                document = failure,
                packagedCollection =
                    HomeCollectionSummary(
                        "gid://shopify/Collection/1",
                        "current-handle",
                        "Current title",
                        media()
                    )
            )

        val result = assertInstanceOf(
            HomeLoadResult.Accepted::class.java,
            repository(gateway, HomeStoreRead.NeverEstablished).load(HomeLoadTrigger.INITIAL)
        )

        assertEquals(HomeContentSource.PACKAGED, result.presentation.source)
        assertEquals(HomeResourceStatus.PARTIAL, result.presentation.resourceStatus)
    }

    @Test
    fun `invalid fixed selector performs no storefront or cache request`() = runTest {
        val gateway = FakeGateway(StorefrontResult.Success(null))
        val store = FakeStore(HomeStoreRead.NeverEstablished)
        val repository =
            DefaultHomeContentRepository(
                gateway,
                HomeConfiguration(
                    HomeRemoteSource.ShopifyMetaobject(HomeDocumentSelector("mobile_home", "INVALID")),
                    homeTestPackagedFallback
                ),
                HomeContentValidator(),
                store,
                HomeContentAcceptanceCoordinator(store),
                FixedClock(NOW),
                PARTITION
            )

        val result = assertInstanceOf(HomeLoadResult.Failed::class.java, repository.load(HomeLoadTrigger.INITIAL))

        assertEquals(HomeLoadFailureCategory.CONFIGURATION, result.failure.category)
        assertEquals(0, gateway.documentCalls)
        assertEquals(0, store.reads)
    }

    @Test
    fun `fresh lkg hydration preserves its original editorial deadline`() = runTest {
        val key = HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/1")
        val stored = storedGridSnapshot(key)
        val gateway =
            FakeGateway(
                StorefrontResult.Failure(StorefrontFailure.Transport(true)),
                HomeResourceBatch(
                    listOf(
                        HomeResourceResolution(
                            key,
                            StorefrontHomeResource.Collection(
                                key,
                                "current-handle",
                                "Current title",
                                true,
                                HomeMediaObservation.Accepted(media())
                            )
                        )
                    )
                )
            )
        val read =
            HomeStoreRead.Established(
                HomeEstablishmentRecord(PARTITION, NOW - 1_000L, 1),
                stored,
                HomeSnapshotRecovery.AVAILABLE
            )

        val result = assertInstanceOf(
            HomeLoadResult.Accepted::class.java,
            repository(gateway, read).load(HomeLoadTrigger.INITIAL)
        )

        assertEquals(HomeContentSource.LKG, result.presentation.source)
        assertEquals(stored.expiresAtMillis, result.presentation.editorialExpiresAtMillis)
    }

    @Test
    fun `lkg hydration rejects a null resource in the exact requested batch`() = runTest {
        val key = HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/1")
        val stored = storedGridSnapshot(key)
        val gateway =
            FakeGateway(
                StorefrontResult.Failure(StorefrontFailure.Transport(true)),
                HomeResourceBatch(listOf(HomeResourceResolution(key, null)))
            )
        val read =
            HomeStoreRead.Established(
                HomeEstablishmentRecord(PARTITION, NOW - 1_000L, 1),
                stored,
                HomeSnapshotRecovery.AVAILABLE
            )

        assertInstanceOf(
            HomeLoadResult.Failed::class.java,
            repository(gateway, read).load(HomeLoadTrigger.INITIAL)
        )
    }

    @Test
    fun `late failed request is superseded after newer remote acceptance`() = runTest {
        val store = FakeStore(HomeStoreRead.NeverEstablished)
        val coordinator = HomeContentAcceptanceCoordinator(store)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val lateGateway = DeferredFailureGateway(started, release)
        val lateRepository = repository(lateGateway, store, coordinator)
        val newerRepository = repository(FakeGateway(StorefrontResult.Success(document())), store, coordinator)

        val late = async { lateRepository.load(HomeLoadTrigger.INITIAL) }
        started.await()
        assertInstanceOf(HomeLoadResult.Accepted::class.java, newerRepository.load(HomeLoadTrigger.INITIAL))
        release.complete(Unit)

        assertEquals(HomeLoadResult.Superseded, late.await())
    }

    private fun repository(gateway: FakeGateway, read: HomeStoreRead): DefaultHomeContentRepository {
        val store = FakeStore(read)
        return repository(gateway, store, HomeContentAcceptanceCoordinator(store))
    }

    private fun repository(
        gateway: StorefrontHomeGateway,
        store: HomeContentStore,
        coordinator: HomeContentAcceptanceCoordinator
    ): DefaultHomeContentRepository = DefaultHomeContentRepository(
        gateway = gateway,
        configuration = remoteConfiguration(),
        validator = HomeContentValidator(),
        store = store,
        coordinator = coordinator,
        clock = FixedClock(NOW),
        partition = PARTITION
    )

    private fun remoteConfiguration() = HomeConfiguration(
        remoteSource = HomeRemoteSource.ShopifyMetaobject(HomeDocumentSelector("mobile_home", "primary")),
        packagedFallback = homeTestPackagedFallback
    )

    private fun document(): HomeDocumentObservation {
        val collectionKey = HomeResourceKey(HomeResourceKind.COLLECTION, "gid://shopify/Collection/1")
        val productKey = HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/1")
        val collection = StorefrontHomeResource.Collection(
            collectionKey,
            "collection-current",
            "Collection",
            hasProducts = true,
            media = HomeMediaObservation.Accepted(media())
        )
        val product = StorefrontHomeResource.Product(
            productKey,
            "product-current",
            "Product",
            availableForSale = true,
            media = HomeMediaObservation.Accepted(media()),
            money = HomeMoneyObservation.Malformed
        )
        val grid = HomeSectionNodeObservation(
            "Metaobject",
            HomeSectionObservation(
                "gid://shopify/Metaobject/grid",
                "primary",
                "mobile_home_collection_grid",
                HomeFieldObservation("single_line_text_field", "Collections"),
                HomeCollectionsFieldObservation(
                    "list.collection_reference",
                    "[\"${collectionKey.gid}\"]",
                    HomeCollectionReferencesObservation(
                        listOf(HomeResourceNodeObservation("Collection", collection)),
                        false
                    )
                ),
                null
            )
        )
        val featured = HomeSectionNodeObservation(
            "Metaobject",
            HomeSectionObservation(
                "gid://shopify/Metaobject/featured",
                "primary",
                "mobile_home_featured_product",
                HomeFieldObservation("single_line_text_field", "Featured"),
                null,
                HomeProductFieldObservation(
                    "product_reference",
                    productKey.gid,
                    HomeResourceNodeObservation("Product", product)
                )
            )
        )
        return HomeDocumentObservation(
            "gid://shopify/Metaobject/root",
            "primary",
            "mobile_home",
            "2026-09-14T00:00:00Z",
            HomeFieldObservation("number_integer", "1"),
            HomeFieldObservation("number_integer", "2"),
            com.gurbakir.storefront.HomeSectionsFieldObservation(
                "list.mixed_reference",
                "[\"gid://shopify/Metaobject/grid\",\"gid://shopify/Metaobject/featured\"]",
                HomeSectionReferencesObservation(listOf(grid, featured), false)
            )
        )
    }

    private fun establishedWithoutSnapshot(): HomeStoreRead.Established = HomeStoreRead.Established(
        marker = HomeEstablishmentRecord(PARTITION, NOW - 1_000L, 1),
        snapshot = null,
        recovery = HomeSnapshotRecovery.MISSING
    )

    private fun storedGridSnapshot(key: HomeResourceKey) = HomeStoredSnapshot(
        PARTITION,
        NOW - 1_000L,
        NOW + 10_000L,
        RemoteHomeSnapshot(
            "gid://shopify/Metaobject/root",
            "mobile_home",
            "primary",
            "2026-09-14T00:00:00Z",
            1,
            listOf(
                RemoteHomeSection.CollectionGrid(
                    "gid://shopify/Metaobject/grid",
                    "mobile_home_collection_grid",
                    "primary",
                    "Collections",
                    listOf(key)
                )
            )
        )
    )

    private fun HomeEditorialState.sectionStableIds(): List<String> =
        (this as HomeEditorialState.NonEmpty).sections.map {
            it.handle.removePrefix("primary").ifEmpty {
                when (it) {
                    is RemoteHomeSection.CollectionGrid -> "grid"
                    is RemoteHomeSection.FeaturedProduct -> "featured"
                }
            }
        }

    private fun media() = StorefrontMedia(URI("https://cdn.shopify.com/s/files/1/test.jpg"), null, 100, 100)

    private class FixedClock(private val now: Long) : HomeEditorialClock {
        override fun nowMillis(): Long = now
        override suspend fun awaitUntil(deadlineMillis: Long) = Unit
    }

    private class FakeStore(private val read: HomeStoreRead) : HomeContentStore {
        var reads = 0
        override suspend fun read(
            partition: HomeContentPartition,
            supportedContentVersion: Int,
            nowMillis: Long
        ): HomeStoreRead {
            reads += 1
            return read
        }
        override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot) =
            HomeStoreWrite.CONFIRMED
        override suspend fun evictSnapshot(partition: HomeContentPartition) = HomeStoreWrite.CONFIRMED
    }

    private class FakeGateway(
        private val document: StorefrontResult<HomeDocumentObservation?>,
        private val resources: HomeResourceBatch? = null,
        private val packagedCollection: HomeCollectionSummary? = null
    ) : StorefrontHomeGateway {
        var documentCalls = 0
        override suspend fun loadHomeDocument(
            selector: HomeDocumentSelector
        ): StorefrontResult<HomeDocumentObservation?> {
            documentCalls += 1
            return document
        }
        override suspend fun loadHomeResources(keys: List<HomeResourceKey>) = StorefrontResult.Success(
            resources ?: HomeResourceBatch(keys.map { HomeResourceResolution(it, null) })
        )
        override suspend fun loadHomeCollection(handle: String) = StorefrontResult.Success(packagedCollection)
        override suspend fun loadHomeProduct(handle: String) = StorefrontResult.Success(null)
    }

    private class DeferredFailureGateway(
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>
    ) : StorefrontHomeGateway {
        override suspend fun loadHomeDocument(
            selector: HomeDocumentSelector
        ): StorefrontResult<HomeDocumentObservation?> {
            started.complete(Unit)
            release.await()
            return StorefrontResult.Failure(StorefrontFailure.Transport(true))
        }

        override suspend fun loadHomeResources(keys: List<HomeResourceKey>) =
            StorefrontResult.Success(HomeResourceBatch(emptyList()))

        override suspend fun loadHomeCollection(handle: String) = StorefrontResult.Success(null)

        override suspend fun loadHomeProduct(handle: String) = StorefrontResult.Success(null)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val PARTITION = HomeContentPartition(
            "com.gurbakir.mobile.dev.debug",
            "development",
            "gurbakir.com",
            "mobile_home",
            "primary"
        )
    }
}
