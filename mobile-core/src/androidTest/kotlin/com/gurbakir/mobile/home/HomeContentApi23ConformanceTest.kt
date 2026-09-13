package com.gurbakir.mobile.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.mobile.CoreTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentApi23ConformanceTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun clearStore() {
        context.getSharedPreferences(HOME_CONTENT_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun realStoreCodecAndIntentionalEmptyRendererExecuteOffline() = runBlocking {
        val store = AndroidHomeContentStore(context, HomeContentCodec(), Dispatchers.IO)
        val partition =
            HomeContentPartition(
                "com.gurbakir.mobile.core.test",
                "api23",
                "offline.example",
                "mobile_home",
                "primary"
            )
        val snapshot =
            HomeStoredSnapshot(
                partition,
                1_000L,
                1_000L + HOME_EDITORIAL_TTL_MILLIS,
                RemoteHomeSnapshot(
                    "gid://shopify/Metaobject/root",
                    "mobile_home",
                    "primary",
                    "2026-09-14T00:00:00Z",
                    1,
                    emptyList()
                )
            )
        assertEquals(
            HomeStoreWrite.CONFIRMED,
            store.replace(HomeEstablishmentRecord(partition, 1_000L, 1), snapshot)
        )
        assertEquals(
            HomeStoreRead.Established(
                HomeEstablishmentRecord(partition, 1_000L, 1),
                snapshot,
                HomeSnapshotRecovery.AVAILABLE
            ),
            store.read(partition, 1, 2_000L)
        )

        composeRule.setContent {
            CoreTestTheme {
                HomeScreen(
                    state =
                        HomeUiState(
                            presentation =
                                HomePresentation(
                                    HomeEditorialState.IntentionalEmpty,
                                    emptyList(),
                                    HomeContentSource.LKG,
                                    HomeResourceStatus.COMPLETE,
                                    snapshot.expiresAtMillis
                                ),
                            loading = false,
                            requestActive = false
                        ),
                    brandDisplayName = "Offline fixture",
                    actions = HomeActions(refreshContent = {})
                )
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.REFRESH).assertIsDisplayed().assertIsEnabled()
        Unit
    }

    @Test
    fun nonrenderableEditorialUsesItsDistinctSealedRendererBranch() {
        composeRule.setContent {
            CoreTestTheme {
                HomeScreen(
                    state =
                        HomeUiState(
                            presentation =
                                HomePresentation(
                                    HomeEditorialState.NonEmpty(emptyList()),
                                    emptyList(),
                                    HomeContentSource.LKG,
                                    HomeResourceStatus.NONE_RENDERABLE,
                                    HOME_EDITORIAL_TTL_MILLIS
                                ),
                            loading = false,
                            requestActive = false
                        ),
                    brandDisplayName = "Offline fixture",
                    actions = HomeActions(refreshContent = {})
                )
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.NONRENDERABLE).assertIsDisplayed()
    }
}
