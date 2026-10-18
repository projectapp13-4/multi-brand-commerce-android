package com.tumiart.mobile

import com.gurbakir.mobile.home.DefaultHomeContentRepository
import com.gurbakir.mobile.home.HOME_CONTENT_STORAGE_VERSION_V2
import com.gurbakir.mobile.home.HomeContentAcceptanceCoordinator
import com.gurbakir.mobile.home.HomeContentPartition
import com.gurbakir.mobile.home.HomeContentSource
import com.gurbakir.mobile.home.HomeContentStore
import com.gurbakir.mobile.home.HomeContentValidator
import com.gurbakir.mobile.home.HomeEditorialClock
import com.gurbakir.mobile.home.HomeEditorialState
import com.gurbakir.mobile.home.HomeEstablishmentRecord
import com.gurbakir.mobile.home.HomeLoadFailureCategory
import com.gurbakir.mobile.home.HomeLoadResult
import com.gurbakir.mobile.home.HomeLoadTrigger
import com.gurbakir.mobile.home.HomeRenderedSection
import com.gurbakir.mobile.home.HomeResourceStatus
import com.gurbakir.mobile.home.HomeSnapshotRecovery
import com.gurbakir.mobile.home.HomeStoreRead
import com.gurbakir.mobile.home.HomeStoreWrite
import com.gurbakir.mobile.home.HomeStoredSnapshot
import com.gurbakir.mobile.home.RemoteHomeSection
import com.gurbakir.mobile.home.RemoteHomeSnapshot
import com.gurbakir.storefront.HomeCollectionSummary
import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeMoneyObservation
import com.gurbakir.storefront.HomeProductSummary
import com.gurbakir.storefront.HomeResourceBatch
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeResourceResolution
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontHomeResource
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontResult
import com.tumiart.mobile.config.TumiArtConfiguration
import java.math.BigDecimal
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Uses the actual application configuration; all resources and commercial values below are fixtures. */
class TumiArtHomeRecoveryTest {
    @Test
    fun `first install with missing root reads all configured resources without persisting fallback`() = runTest {
        val gateway = Gateway()
        val store = Store()
        val result = accepted(repository(gateway, store).load(HomeLoadTrigger.INITIAL))
        assertEquals(HomeContentSource.PACKAGED, result.source)
        assertEquals(HomeResourceStatus.COMPLETE, result.resourceStatus)
        assertEquals(5, (result.renderedSections.first() as HomeRenderedSection.CollectionGrid).items.size)
        assertEquals(
            TumiArtConfiguration.home.packagedFallback.productRange.sources.map {
                it.handle
            }.toSet(),
            gateway.collections.toSet()
        )
        assertEquals(listOf("henri-matisse-bitkiler-1952"), gateway.products)
        assertEquals(
            BigDecimal("12.34"),
            (result.renderedSections.last() as HomeRenderedSection.FeaturedProduct).item.summary.price.amount
        )
        assertEquals(0, store.writes)
        assertNull(result.editorialExpiresAtMillis)
    }

    @Test
    fun `invalid v2 root on first install uses same bounded fallback`() = runTest {
        val gateway = Gateway().apply { document = invalidRoot() }
        assertEquals(HomeContentSource.PACKAGED, accepted(repository(gateway).load(HomeLoadTrigger.INITIAL)).source)
    }

    @Test
    fun `partial resources retain usable collections but exclude unavailable featured artwork`() = runTest {
        val gateway = Gateway().apply {
            missing = setOf("modern-forms")
            soldOut = true
        }
        val result = accepted(repository(gateway).load(HomeLoadTrigger.INITIAL))
        assertEquals(HomeResourceStatus.PARTIAL, result.resourceStatus)
        assertEquals(4, (result.renderedSections.single() as HomeRenderedSection.CollectionGrid).items.size)
    }

    @Test
    fun `empty resources are unavailable rather than intentional editorial empty`() = runTest {
        val gateway = Gateway().apply {
            missing =
                TumiArtConfiguration.home.packagedFallback.productRange.sources.map { it.handle }.toSet()
            missingProduct =
                true
        }
        val result = accepted(repository(gateway).load(HomeLoadTrigger.INITIAL))
        assertEquals(HomeResourceStatus.NONE_RENDERABLE, result.resourceStatus)
        assertEquals(HomeEditorialState.Packaged, result.editorial)
    }

    @Test
    fun `manual refresh of missing root never silently establishes packaged authority`() = runTest {
        val gateway = Gateway()
        assertInstanceOf(HomeLoadResult.Failed::class.java, repository(gateway).load(HomeLoadTrigger.MANUAL_REFRESH))
        assertTrue(gateway.collections.isEmpty())
        assertTrue(gateway.products.isEmpty())
    }

    @Test
    fun `fresh LKG after restart rehydrates current product and keeps original deadline`() = runTest {
        val gateway = Gateway()
        val store = Store(established(snapshot()))
        val result = accepted(repository(gateway, store).load(HomeLoadTrigger.INITIAL))
        assertEquals(HomeContentSource.LKG, result.source)
        assertEquals(NOW + 10_000, result.editorialExpiresAtMillis)
        assertEquals(
            BigDecimal("98.76"),
            (result.renderedSections.single() as HomeRenderedSection.FeaturedProduct).item.summary.price.amount
        )
        assertTrue(gateway.products.isEmpty())
        assertEquals(0, store.writes)
    }

    @Test
    fun `expired established LKG cannot resurrect packaged fallback`() = runTest {
        val gateway = Gateway()
        assertInstanceOf(
            HomeLoadResult.Failed::class.java,
            repository(
                gateway,
                Store(
                    established(
                        snapshot().copy(
                            expiresAtMillis =
                                NOW - 1
                        )
                    )
                )
            ).load(HomeLoadTrigger.INITIAL)
        )
        assertTrue(gateway.collections.isEmpty())
    }

    @Test
    fun `offline first install fails honestly and recovers when resources return`() = runTest {
        val gateway = Gateway().apply { offline = true }
        val repository = repository(gateway)
        val failure = assertInstanceOf(HomeLoadResult.Failed::class.java, repository.load(HomeLoadTrigger.INITIAL))
        assertEquals(HomeLoadFailureCategory.CONNECTION, failure.failure.category)
        gateway.offline = false
        assertEquals(HomeResourceStatus.COMPLETE, accepted(repository.load(HomeLoadTrigger.INITIAL)).resourceStatus)
    }

    @Test
    fun `offline LKG cannot replay stale commercial truth and restores with fresh hydration`() = runTest {
        val gateway = Gateway().apply { offline = true }
        val repository = repository(gateway, Store(established(snapshot())))
        assertInstanceOf(HomeLoadResult.Failed::class.java, repository.load(HomeLoadTrigger.INITIAL))
        gateway.offline = false
        assertEquals(HomeContentSource.LKG, accepted(repository.load(HomeLoadTrigger.INITIAL)).source)
    }

    private fun accepted(result: HomeLoadResult) =
        assertInstanceOf(HomeLoadResult.Accepted::class.java, result).presentation

    private fun repository(gateway: Gateway, store: Store = Store()) = DefaultHomeContentRepository(
        gateway,
        TumiArtConfiguration.home,
        HomeContentValidator(),
        store,
        HomeContentAcceptanceCoordinator(store),
        object : HomeEditorialClock {
            override fun nowMillis() = NOW
            override suspend fun awaitUntil(deadlineMillis: Long) = Unit
        },
        PARTITION
    )

    private fun established(snapshot: HomeStoredSnapshot) = HomeStoreRead.Established(
        HomeEstablishmentRecord(PARTITION, NOW - 1_000, 2, HOME_CONTENT_STORAGE_VERSION_V2),
        snapshot,
        HomeSnapshotRecovery.AVAILABLE
    )

    private fun snapshot() = HomeStoredSnapshot(
        PARTITION,
        NOW - 1_000,
        NOW + 10_000,
        RemoteHomeSnapshot(
            "gid://shopify/Metaobject/1",
            "mobile_home_v2",
            "primary",
            "2026-10-17T00:00:00Z",
            2,
            listOf(
                RemoteHomeSection.FeaturedProduct(
                    "gid://shopify/Metaobject/2",
                    "mobile_home_featured_product",
                    "feature",
                    "Fixture artwork",
                    PRODUCT
                )
            ),
            sectionRevisionDigest = "d".repeat(64)
        ),
        HOME_CONTENT_STORAGE_VERSION_V2
    )

    private fun invalidRoot() = HomeDocumentObservation(
        "gid://shopify/Metaobject/1",
        "primary",
        "mobile_home_v2",
        "2026-10-17T00:00:00Z",
        null,
        null,
        null
    )

    private class Store(private val value: HomeStoreRead = HomeStoreRead.NeverEstablished) : HomeContentStore {
        var writes = 0
        override suspend fun read(partition: HomeContentPartition, supportedContentVersion: Int, nowMillis: Long) =
            value
        override suspend fun replace(marker: HomeEstablishmentRecord, snapshot: HomeStoredSnapshot): HomeStoreWrite {
            writes++
            return HomeStoreWrite.CONFIRMED
        }
        override suspend fun evictSnapshot(partition: HomeContentPartition) = HomeStoreWrite.CONFIRMED
    }

    private class Gateway : StorefrontHomeGateway {
        var document: HomeDocumentObservation? = null
        var offline = false
        var missing = emptySet<String>()
        var soldOut = false
        var missingProduct = false
        val collections = mutableListOf<String>()
        val products = mutableListOf<String>()
        private fun <T> result(value: T): StorefrontResult<T> = if (offline) {
            StorefrontResult.Failure(
                StorefrontFailure.Transport(true)
            )
        } else {
            StorefrontResult.Success(value)
        }
        override suspend fun loadHomeDocument(selector: HomeDocumentSelector) = result(document)
        override suspend fun loadHomeCollection(handle: String): StorefrontResult<HomeCollectionSummary?> {
            collections += handle
            return result(
                if (handle in
                    missing
                ) {
                    null
                } else {
                    HomeCollectionSummary("gid://shopify/Collection/1", handle, "Fixture $handle", MEDIA)
                }
            )
        }
        override suspend fun loadHomeProduct(handle: String): StorefrontResult<HomeProductSummary?> {
            products += handle
            return result(
                if (missingProduct) {
                    null
                } else {
                    HomeProductSummary(
                        PRODUCT.gid,
                        handle,
                        "Fixture artwork",
                        !soldOut,
                        MEDIA,
                        StorefrontMoney(BigDecimal("12.34"), "TRY")
                    )
                }
            )
        }
        override suspend fun loadHomeResources(keys: List<HomeResourceKey>) = result(
            HomeResourceBatch(
                keys.map {
                    HomeResourceResolution(
                        it,
                        StorefrontHomeResource.Product(
                            it,
                            "current-fixture-handle",
                            "Current fixture title",
                            true,
                            HomeMediaObservation.Accepted(MEDIA),
                            HomeMoneyObservation.Accepted(StorefrontMoney(BigDecimal("98.76"), "TRY"))
                        )
                    )
                }
            )
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val PARTITION =
            HomeContentPartition(BuildConfig.APPLICATION_ID, "development", "tumiart.com", "mobile_home_v2", "primary")
        val PRODUCT = HomeResourceKey(HomeResourceKind.PRODUCT, "gid://shopify/Product/1")
        val MEDIA = StorefrontMedia(URI("https://cdn.shopify.com/s/files/1/fixture.png"), "Owned fixture", 400, 600)
    }
}
