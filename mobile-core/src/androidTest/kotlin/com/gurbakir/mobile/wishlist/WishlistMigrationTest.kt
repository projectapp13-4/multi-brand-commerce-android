package com.gurbakir.mobile.wishlist

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.search.LocalCommerceDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WishlistMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LocalCommerceDatabase::class.java
        )

    @Test
    fun migration1To2PreservesHistoryAndCreatesEmptyWishlist() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO search_history
                    (environmentId, marketId, normalizedQuery, displayQuery, searchedAtEpochMillis)
                VALUES ('development', 'TR', 'cezve', 'Cezve', 42)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, WISHLIST_MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT displayQuery FROM search_history").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Cezve", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM wishlist").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "wishlist-migration-test"
    }
}
