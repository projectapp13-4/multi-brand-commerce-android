package com.gurbakir.mobile.wishlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.search.LocalCommerceDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WishlistDatabaseTest {
    private lateinit var database: LocalCommerceDatabase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                LocalCommerceDatabase::class.java
            ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomStoreIsIdempotentPartitionedAndClearable() = runTest {
        val store = RoomWishlistStore(database)
        val development = WishlistPartition("development", "TR")
        val staging = WishlistPartition("staging", "TR")
        val productId = "gid://shopify/Product/1"

        store.setSaved(development, productId, true, 10L)
        store.setSaved(development, productId, true, 20L)
        store.setSaved(staging, productId, true, 30L)

        assertEquals(listOf(StoredWishlistEntry(productId, 10L)), store.observe(development).first())
        assertEquals(listOf(StoredWishlistEntry(productId, 30L)), store.load(staging))
        store.clear(development)
        assertEquals(emptyList<StoredWishlistEntry>(), store.load(development))
        assertEquals(1, store.load(staging).size)
    }
}
