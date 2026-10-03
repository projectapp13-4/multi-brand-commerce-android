@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@file:Suppress("MagicNumber", "LongMethod")

package com.gurbakir.mobile

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.account.AccountTestTags
import com.gurbakir.mobile.catalog.CatalogTestTags
import com.gurbakir.mobile.home.HomeTestTags
import com.gurbakir.mobile.search.SearchTestTags
import com.gurbakir.mobile.wishlist.WishlistTestTags
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in exact-application visual evidence. Public reads and disposable device data only. */
@RunWith(AndroidJUnit4::class)
class CommerceUiRuntimeEvidenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun requireExplicitEvidenceRun() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureUi") == "true")
    }

    @After
    fun restoreDisposableRuntimeAppearance() {
        if (InstrumentationRegistry.getArguments().getString("captureUi") == "true") {
            shell("settings put system font_scale 1.0")
            shell("cmd uimode night no")
        }
    }

    @Test
    fun publicHomeShopCollectionAndEmptyCart() {
        waitForHomeEditorial()
        capture("home-top")
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(
                hasTestTag(HomeTestTags.TITLE) and hasAnyAncestor(hasTestTag(HomeTestTags.PRODUCT_RANGE))
            )
        capture("home-lower")
        navigate("categories", CatalogTestTags.CATEGORIES_ROOT)
        waitForText("ÜRÜNLERİMİZ")
        capture("shop")
        composeRule.onNodeWithText("ÜRÜNLERİMİZ").performClick()
        waitFor(CatalogTestTags.COLLECTION_RESULT_COUNT)
        waitForProductTiles()
        capture("collection")
        pressBack()
        navigate("home", HomeTestTags.ROOT)
        composeRule.onNodeWithTag(HomeTestTags.CART).performClick()
        waitFor("cart-empty")
        capture("cart-empty")
    }

    @Test
    fun publicSearchWishlistAndProductPurchaseStates() {
        search()
        capture("search-results")
        val title = "Oval Kulplu Bakır Tava"
        openSearchProduct(title)
        capture("product-initial")
        scrollToProductOptions()
        capture("product-variants")
        val option = composeRule.onAllNodes(tagPrefix("product-option-"))
            .fetchSemanticsNodes().first { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        composeRule.onNodeWithTag(option.config[SemanticsProperties.TestTag]).performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onNodeWithTag("product-detail-add-to-cart").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Disabled) == null
        }
        composeRule.onNodeWithTag("product-detail-add-to-cart").assertIsEnabled()
        capture("product-selected")
        composeRule.onNodeWithTag("product-detail-content")
            .performScrollToNode(hasTestTag("product-detail-description"))
        capture("product-description")
        toggleProductWishlist()
        pressBack()
        navigate("wishlist", WishlistTestTags.ROOT)
        waitForWishlistItems()
        capture("wishlist-populated")
        search()
        val additionalTitles = composeRule.onAllNodes(tagPrefix("catalog-product-title-"), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].first().text }
            .filter { it != title }.take(2)
        check(additionalTitles.size == 2)
        additionalTitles.forEach { additionalTitle ->
            openSearchProduct(additionalTitle)
            toggleProductWishlist()
            pressBack()
            waitFor(SearchTestTags.RESULT_COUNT)
        }
        navigate("wishlist", WishlistTestTags.ROOT)
        waitForWishlistItems()
        capture("wishlist-many")
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR).performClick()
        composeRule.onNodeWithTag(WishlistTestTags.CLEAR_CONFIRM).performClick()
        waitFor(WishlistTestTags.EMPTY)
        capture("wishlist-empty")
        search()
        openSearchProduct("Çelik Saplı Bakır Hamsi Tavası")
        composeRule.onNodeWithTag("product-detail-add-to-cart").assertIsNotEnabled()
        capture("product-unavailable")
        scrollToProductOptions()
        capture("product-unavailable-options")
        pressBack()
        composeRule.onNodeWithTag(SearchTestTags.GRID).performScrollToNode(hasTestTag(SearchTestTags.INPUT))
        composeRule.onNodeWithTag(SearchTestTags.CLEAR).performClick()
        waitFor(SearchTestTags.HISTORY)
        capture("search-history")
    }

    @Test
    fun signedOutAccountAndOwnedPolicyIndex() {
        navigate("account", AccountTestTags.ROOT)
        waitFor(AccountTestTags.SIGN_IN)
        capture("account-signed-out")
        composeRule.onNodeWithTag(AccountTestTags.LEGAL_SUPPORT).performScrollTo().performClick()
        waitForText(composeRule.activity.getString(R.string.legal_support_page_privacy))
        capture("help")
    }

    @Test
    fun controlledPrivateAndCartFixtures() {
        fixture(Stage3EvidenceActivity::class.java, "ACCOUNT", AccountTestTags.SUMMARY, "account-authenticated-fixture")
        fixture(Stage3EvidenceActivity::class.java, "PROFILE", "profile-content", "profile-fixture")
        fixture(Stage3EvidenceActivity::class.java, "ADDRESS", "address-form-content", "address-form-fixture")
        fixture(Stage4EvidenceActivity::class.java, "CART_CUSTOMER", "cart-root", "cart-populated-fixture")
    }

    @Test
    fun darkCommerceAndUtilityRemainReadable() {
        shell("cmd uimode night yes")
        composeRule.activityRule.scenario.recreate()
        check(
            composeRule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        )
        waitForHomeEditorial()
        check(
            (composeRule.activity.window.decorView.background as? ColorDrawable)?.color ==
                com.gurbakir.mobile.brand.GurbakirBrand.configuration.designTokens.colors.dark.background.value.toInt()
        )
        capture("dark-home")
        search()
        capture("dark-search")
        openSearchProduct("Oval Kulplu Bakır Tava")
        capture("dark-product")
        fixture(Stage3EvidenceActivity::class.java, "ACCOUNT", AccountTestTags.SUMMARY, "dark-account-fixture")
    }

    @Test
    fun largeTextCommerceAndUtilityReflow() {
        shell("settings put system font_scale 2.0")
        composeRule.activityRule.scenario.recreate()
        check(composeRule.activity.resources.configuration.fontScale >= 1.9f)
        waitForHomeEditorial()
        capture("large-text-home")
        search()
        capture("large-text-search")
        openSearchProduct("Oval Kulplu Bakır Tava")
        scrollToProductOptions()
        capture("large-text-product-options")
        fixture(Stage3EvidenceActivity::class.java, "ACCOUNT", AccountTestTags.SUMMARY, "large-text-account-fixture")
        fixture(Stage4EvidenceActivity::class.java, "CART_CUSTOMER", "cart-root", "large-text-cart-fixture")
    }

    private fun search() {
        navigate("search", SearchTestTags.ROOT)
        composeRule.onNodeWithTag(SearchTestTags.INPUT).performTextReplacement("tava")
        composeRule.onNodeWithTag(SearchTestTags.SUBMIT).performClick()
        waitFor(SearchTestTags.RESULT_COUNT)
        waitForProductTiles()
    }

    private fun openSearchProduct(title: String) {
        composeRule.onNodeWithTag(SearchTestTags.GRID).performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).performClick()
        waitFor("product-detail-title")
    }

    private fun scrollToProductOptions() {
        composeRule.onNodeWithTag("product-detail-content")
            .performScrollToNode(tagPrefix("product-option-"))
    }

    private fun navigate(destination: String, root: String) {
        waitFor("production-primary-$destination")
        composeRule.onNodeWithTag("production-primary-$destination").performClick()
        waitFor(root)
    }

    private fun waitFor(tag: String) {
        composeRule.waitUntilExactlyOneExists(hasTestTag(tag), 60_000)
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(60_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(text))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForProductTiles() {
        composeRule.waitUntil(60_000) {
            composeRule.onAllNodes(tagPrefix("catalog-product-title-"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForHomeEditorial() {
        waitFor(HomeTestTags.ROOT)
        composeRule.waitUntil(60_000) {
            composeRule.onAllNodes(tagPrefix("home-video-play-")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForWishlistItems() {
        composeRule.waitUntil(60_000) {
            composeRule.onAllNodes(tagPrefix("catalog-product-title-"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun toggleProductWishlist() {
        composeRule.onAllNodes(tagPrefix("wishlist-toggle-")).fetchSemanticsNodes().single().let {
            composeRule.onNodeWithTag(it.config[SemanticsProperties.TestTag]).performClick()
        }
    }

    private fun tagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun <T : androidx.activity.ComponentActivity> fixture(
        activity: Class<T>,
        screen: String,
        tag: String,
        name: String
    ) {
        val intent = Intent(instrumentation.targetContext, activity).putExtra("screen", screen)
        ActivityScenario.launch<T>(intent).use { scenario ->
            waitFor(tag)
            scenario.onActivity { fixtureActivity ->
                val night = fixtureActivity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                val colors = com.gurbakir.mobile.brand.GurbakirBrand.configuration.designTokens.colors
                val expectedBackground = if (night) colors.dark.background else colors.light.background
                check(
                    (fixtureActivity.window.decorView.background as? ColorDrawable)?.color ==
                        expectedBackground.value.toInt()
                )
            }
            capture(name)
            if (screen == "CART_CUSTOMER") {
                composeRule.onNodeWithTag("cart-content").performScrollToNode(hasTestTag("cart-checkout"))
                composeRule.onNodeWithTag("cart-checkout").performScrollTo().assertIsDisplayed()
                capture("$name-checkout")
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        SystemClock.sleep(1_200) // Allow asynchronous public product imagery to complete before inspecting pixels.
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "commerce-ui-v3")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(directory, "$name.json").writeText(
            org.json.JSONObject().put("name", name)
                .put("api", android.os.Build.VERSION.SDK_INT)
                .put("widthPx", bitmap.width).put("heightPx", bitmap.height)
                .put("densityDpi", instrumentation.targetContext.resources.displayMetrics.densityDpi)
                .put("fontScale", instrumentation.targetContext.resources.configuration.fontScale)
                .put("uiMode", instrumentation.targetContext.resources.configuration.uiMode)
                .put("applicationId", BuildConfig.APPLICATION_ID)
                .put("version", BuildConfig.VERSION_NAME)
                .toString(2)
        )
        bitmap.recycle()
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
