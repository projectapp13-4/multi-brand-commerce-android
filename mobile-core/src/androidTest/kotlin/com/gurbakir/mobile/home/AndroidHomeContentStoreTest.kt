package com.gurbakir.mobile.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHomeContentStoreTest {
    private lateinit var context: Context
    private lateinit var store: AndroidHomeContentStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(HOME_CONTENT_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AndroidHomeContentStore(context, HomeContentCodec(), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(HOME_CONTENT_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun corruptSnapshotCleanupNeverErasesEstablishedOwnership() = runBlocking {
        assertEquals(HomeStoreWrite.CONFIRMED, store.replace(marker(), stored()))
        val preferences =
            context.getSharedPreferences(HOME_CONTENT_PREFERENCES_NAME, Context.MODE_PRIVATE)
        check(preferences.edit().putString(HOME_SNAPSHOT_KEY, "not-json").commit())

        val first = store.read(partition, supportedContentVersion = 1, nowMillis = 2_000L)
        val second = store.read(partition, supportedContentVersion = 1, nowMillis = 2_000L)
        val afterRecreation =
            AndroidHomeContentStore(context, HomeContentCodec(), Dispatchers.IO)
                .read(partition, supportedContentVersion = 1, nowMillis = 2_000L)

        assertEquals(
            HomeStoreRead.Established(marker(), null, HomeSnapshotRecovery.CORRUPT),
            first
        )
        assertEquals(
            HomeStoreRead.Established(marker(), null, HomeSnapshotRecovery.MISSING),
            second
        )
        assertEquals(second, afterRecreation)
    }

    @Test
    fun wrongPrimitiveTypeForMarkerFailsConservativelyWithoutThrowing() = runBlocking {
        val preferences =
            context.getSharedPreferences(HOME_CONTENT_PREFERENCES_NAME, Context.MODE_PRIVATE)
        check(preferences.edit().putLong(HOME_ESTABLISHMENT_KEY, 7L).commit())

        assertEquals(
            HomeStoreRead.OwnershipUnknown,
            store.read(partition, supportedContentVersion = 1, nowMillis = 2_000L)
        )
    }

    private val partition =
        HomeContentPartition(
            applicationId = "com.gurbakir.mobile.core.test",
            environmentId = "instrumentation",
            storefrontDomain = "merchant.example",
            rootType = "mobile_home",
            rootHandle = "primary"
        )

    private fun marker() = HomeEstablishmentRecord(partition, 1_000L, 1)

    private fun stored() = HomeStoredSnapshot(
        partition = partition,
        acceptedAtMillis = 1_000L,
        expiresAtMillis = 86_401_000L,
        snapshot =
            RemoteHomeSnapshot(
                rootGid = "gid://shopify/Metaobject/root",
                rootType = "mobile_home",
                rootHandle = "primary",
                rootUpdatedAt = "2026-09-13T20:00:00Z",
                contentVersion = 1,
                sections =
                    listOf(
                        RemoteHomeSection.FeaturedProduct(
                            sectionGid = "gid://shopify/Metaobject/featured",
                            type = "mobile_home_featured_product",
                            handle = "primary",
                            title = "Featured",
                            product =
                                HomeResourceKey(
                                    HomeResourceKind.PRODUCT,
                                    "gid://shopify/Product/1"
                                )
                        )
                    )
            )
    )
}
