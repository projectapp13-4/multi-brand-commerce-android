# Gate 1 Mobile Core Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. At execution time, use `superpowers:using-git-worktrees` before modifying source so Gate 1 runs in an isolated worktree. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the shared Android application boundary by extracting reusable application implementation from the current Gürbakır-first `:app` into a new Android library module `:mobile-core`, while preserving Gürbakır runtime behavior, Android application identity, persisted state and release boundary exactly.

**Architecture:** Keep `:app` as the Gürbakır Android application/composition shell. Move reusable Compose UI, typed navigation, ViewModels/controllers, application orchestration and shared local-data implementation to `:mobile-core`. Before the physical move, introduce only the narrow composition seams required to prevent concrete Gürbakır identity, merchant content, application `BuildConfig`, Firebase provider types, market-specific territory/tracking values and migration-sensitive persistence identities from becoming defaults inside `:mobile-core`. Perform the physical source/resource/DI/Room extraction as one coherent structural commit after those seams compile in `:app`.

**Tech Stack:** Android Gradle Plugin 9.2.1, Kotlin 2.3.10, KSP 2.3.10, Java/Kotlin JVM 17, Jetpack Compose BOM 2026.06.01, Navigation Compose 2.9.8, Hilt 2.60.1, Room 2.8.4, Apollo Kotlin 5.0.1, Shopify Checkout Kit 3.5.4, Firebase BoM 34.16.0, Gradle 9.4.1.

**Spec:**
- `docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md`
- `docs/architecture/MULTI-BRAND-ARCHITECTURE.md`
- `docs/architecture/BRAND-BOUNDARIES.md`
- `docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md`
- `docs/architecture/BRAND-ONBOARDING.md`
- `docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md`
- `docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md`

## Global Constraints

- Gate 1 establishes `:mobile-core`; it does not add a second or synthetic brand.
- `:app` remains the Gürbakır Android application and concrete composition shell.
- Do not introduce a global brand flavor.
- Do not add runtime merchant or brand switching.
- Do not redesign capability/navigation topology.
- Do not implement Shopify Navigation, metaobjects, provisioning or new merchant schemas.
- Do not perform a broad Firebase provider redesign beyond the narrow update-policy seam required to prevent `:mobile-core -> :firebase`.
- Do not implement final Customer Account disabled composition.
- Do not create production application identity, signing configuration, Firebase production registration, production OAuth callback or verified production App Links.
- Do not rename Kotlin packages merely for aesthetic neutrality.
- Preserve all current Gürbakır application IDs.
- Preserve `gurbakir-local.db` exactly.
- Preserve the current Room schema history and `1 -> 2` migration.
- Preserve Customer Account secure preference and Keystore naming exactly.
- Preserve cart secure preference and Keystore naming exactly.
- Preserve Firebase package-registration continuity.
- Preserve OAuth callback behavior.
- Preserve current Gürbakır App Links.
- Preserve `development` and `staging` environment behavior.
- `:mobile-core` must not depend on `:app`, future brand applications or `:firebase`.
- No concrete `GurbakirBrand`, Gürbakır merchant domain, Gürbakır catalog handles or app `BuildConfig` may become `:mobile-core` production dependencies.
- Existing historical Phase, Product Quality and research documents are not rewritten.
- Gate 1 stops before Gate 2.

---

# 1. Planning Baseline

This plan was finalized against:

```text
Repository: private historical repository
Default branch: main
Planning SHA: 4f00a9d28f9c4bdf6ee2a1332a11f6e85268fede
Gate 0: merged
Gate 1 implementation: not started
```

The planning SHA is evidence for this document, not the execution base.

The executor must establish the then-current `origin/main` in Task 0 before any edit.

---

# 2. Current Module Graph and Gate 1 Target

Current:

```text
:app
:foundation
:storefront
:account
:checkout
:firebase
```

Gate 1 target:

```text
:app
 ├──> :mobile-core
 ├──> :foundation
 ├──> :storefront
 ├──> :account
 ├──> :checkout
 └──> :firebase

:mobile-core
 ├──> :foundation
 ├──> :storefront
 ├──> :account
 └──> :checkout

:storefront -> :foundation
:account    -> :foundation
:firebase   -> :foundation

:mobile-core -X-> :app
:mobile-core -X-> :firebase

shared modules -X-> concrete brand application modules
```

Existing direct `:app` dependencies on shared modules may remain where application composition or debug evidence legitimately consumes them.

Gate 1 is not a dependency-minimization exercise.

---

# 3. Explicit Production Source Ownership Map

The executor must use this ownership map instead of rediscovering the module split.

## 3.1 Existing root Kotlin files

Current root:

```text
app/src/main/kotlin/com/gurbakir/mobile/
├── AccountDeletionDestination.kt
├── AccountFeatureDestinations.kt
├── CartDestination.kt
├── GurbakirApplication.kt
├── LegalSupportDestination.kt
├── MainActivity.kt
└── ProductionApp.kt
```

### Move to `:mobile-core`

After Task 2 has separated the concrete `GurbakirApp` wrapper from reusable root composition, move:

```text
app/src/main/kotlin/com/gurbakir/mobile/AccountDeletionDestination.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/AccountDeletionDestination.kt

app/src/main/kotlin/com/gurbakir/mobile/AccountFeatureDestinations.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/AccountFeatureDestinations.kt

app/src/main/kotlin/com/gurbakir/mobile/CartDestination.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/CartDestination.kt

app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt
```

`ProductionApp.kt` becomes the home of the reusable:

```text
MobileCoreApp
ProductionAppShell
ProductionNavHost
ProductionDestinationContent
common navigation destination composition
route-recovery mechanics
primary-navigation mechanics
```

### Remain in `:app`

```text
app/src/main/kotlin/com/gurbakir/mobile/GurbakirApplication.kt
app/src/main/kotlin/com/gurbakir/mobile/MainActivity.kt
app/src/main/kotlin/com/gurbakir/mobile/LegalSupportDestination.kt
```

Create during Task 2 and retain:

```text
app/src/main/kotlin/com/gurbakir/mobile/GurbakirApp.kt
```

`GurbakirApp.kt` is the concrete thin wrapper that calls `MobileCoreApp`.

---

# 4. Feature-Directory Ownership Map

## 4.1 Move whole reusable directories

Move the complete current production contents of:

```text
app/src/main/kotlin/com/gurbakir/mobile/account/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/account/

app/src/main/kotlin/com/gurbakir/mobile/accountdeletion/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/accountdeletion/

app/src/main/kotlin/com/gurbakir/mobile/cart/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/cart/

app/src/main/kotlin/com/gurbakir/mobile/checkout/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/checkout/

app/src/main/kotlin/com/gurbakir/mobile/product/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/product/

app/src/main/kotlin/com/gurbakir/mobile/profile/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/profile/

app/src/main/kotlin/com/gurbakir/mobile/search/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/search/

app/src/main/kotlin/com/gurbakir/mobile/ui/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/ui/

app/src/main/kotlin/com/gurbakir/mobile/wishlist/
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/wishlist/
```

The application-specific Search/Wishlist DB/partition construction does **not** live inside these feature directories today; it is in `di/` and remains/splits according to section 5.

## 4.2 Move directory except newly split app-owned concrete file

### Address

Reusable directory moves:

```text
app/src/main/kotlin/com/gurbakir/mobile/address/**
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/address/**
```

except the new app-owned file:

```text
app/src/main/kotlin/com/gurbakir/mobile/address/GurbakirAddressTerritoryPolicy.kt
```

`AddressTerritoryPolicy.kt` itself moves to core.

### Catalog

Split current `CatalogConfiguration.kt` first.

Core:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/catalog/CatalogConfiguration.kt
mobile-core/src/main/kotlin/com/gurbakir/mobile/catalog/**
```

App retains newly separated:

```text
app/src/main/kotlin/com/gurbakir/mobile/catalog/GurbakirCatalogConfiguration.kt
```

### Home

Split current `HomeConfiguration.kt` first.

Core:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/home/HomeConfiguration.kt
mobile-core/src/main/kotlin/com/gurbakir/mobile/home/**
```

App retains:

```text
app/src/main/kotlin/com/gurbakir/mobile/home/GurbakirHomeConfiguration.kt
```

### Navigation

Core receives existing navigation implementation plus:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/navigation/MobileDeepLinkConfiguration.kt
```

App retains new concrete:

```text
app/src/main/kotlin/com/gurbakir/mobile/navigation/GurbakirDeepLinkConfiguration.kt
```

### Order

Move existing Order feature implementation.

Core includes:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/order/TrackingUrlPolicy.kt
```

App retains:

```text
app/src/main/kotlin/com/gurbakir/mobile/order/GurbakirTrackingUrlPolicy.kt
```

### Update policy

Move provider-neutral update implementation:

```text
app/src/main/kotlin/com/gurbakir/mobile/update/**
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/update/**
```

except newly created app provider adapter:

```text
app/src/main/kotlin/com/gurbakir/mobile/update/FirebaseUpdatePolicyRemoteGateway.kt
```

## 4.3 Remain entirely in `:app`

```text
app/src/main/kotlin/com/gurbakir/mobile/brand/**
app/src/main/kotlin/com/gurbakir/mobile/config/**
app/src/main/kotlin/com/gurbakir/mobile/legal/**
```

Specifically:

```text
brand/GurbakirBrand.kt
config/BuildConfigurationSource.kt
legal/**
```

remain Gürbakır/app-owned.

---

# 5. Exact DI Ownership Map

Current DI files are not moved wholesale.

## 5.1 `ApplicationModule.kt` — split

Current file:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/ApplicationModule.kt
```

### Remain in app `ApplicationModule`

Retain/provide:

```text
AppConfiguration = BuildConfigurationSource.create()

HomeConfiguration = GurbakirHomeConfiguration.value

CatalogConfiguration = GurbakirCatalogConfiguration.value

AddressTerritoryPolicy = GurbakirAddressTerritoryPolicy

CartSessionStore =
    AndroidKeystoreCartSessionStore(
        context,
        configuration.environment.name
    )
```

App owns `CartSessionStore` construction because its persisted identity depends on the current environment and must not change.

### Move reusable construction to new core module

Create:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CoreApplicationModule.kt
```

Move/provide:

```text
CheckoutAdapter
HomeContentRepository
CartCoordinator
```

`CheckoutAdapter` may consume `AppConfiguration` supplied by app; it remains reusable behavior.

`HomeContentRepository` consumes `HomeConfiguration`.

`CartCoordinator` consumes the app-provided `CartSessionStore`.

## 5.2 `CartModule.kt` — move

Move intact except import/resource adjustments required by new module:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/CartModule.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CartModule.kt
```

It owns reusable bindings:

```text
CoordinatedCartOperations -> CartOperations
DefaultCartRepository -> CartRepository
CoordinatedCheckoutCartCompleter -> CheckoutCartCompleter
```

## 5.3 `CatalogModule.kt` — move and inject configuration

Move:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/CatalogModule.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CatalogModule.kt
```

Update provider to accept:

```text
CatalogConfiguration
StorefrontHomeGateway
StorefrontCatalogGateway
```

and construct:

```text
DefaultCatalogRepository(
    homeGateway,
    catalogGateway,
    configuration
)
```

## 5.4 `CustomerAccountModule.kt` — split

Current physical file contains three Hilt modules:

```text
CustomerAccountModule
CustomerOrderModule
ProductionAccountModule
```

### App retains only migration-sensitive store construction

Create/retain app-side module:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/CustomerAccountPersistenceModule.kt
```

It provides only:

```text
CustomerSessionStore =
    AndroidKeystoreCustomerSessionStore(
        context,
        environmentId = configuration.environment.name
    )
```

### Core receives reusable account composition

Create:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CoreCustomerAccountModule.kt
```

Move the reusable providers currently in `CustomerAccountModule.kt`:

```text
CustomerAccountDiscoveryClient
CustomerAccountAuthorizationCoordinator
CustomerAccountTokenClient
CustomerAccountLogoutClient
CustomerAccountSessionCoordinator
CustomerAccountGateway
CustomerProfileGateway
CustomerAddressGateway
CustomerOrderGateway
```

Also move:

```text
ProductionAccountModule
```

bindings for:

```text
DefaultAccountController -> AccountController
DefaultAccountDeletionController -> AccountDeletionController
DefaultProfileController -> ProfileController
DefaultAddressController -> AddressController
DefaultOrderController -> OrderController
```

Core consumes the app-provided `CustomerSessionStore`.

Delete the old mixed app `CustomerAccountModule.kt` once its responsibilities have been split and both replacement modules compile.

## 5.5 `FirebaseModule.kt` — remain app

Keep:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/FirebaseModule.kt
```

unchanged except imports required by the update-policy adapter binding.

`:mobile-core` must never import or depend on it.

## 5.6 `LegalModule.kt` — remain app

Keep:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/LegalModule.kt
```

The concrete legal feature remains Gürbakır-owned.

## 5.7 `ProductModule.kt` — move

Move:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/ProductModule.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/di/ProductModule.kt
```

Retain binding:

```text
DefaultProductDetailRepository -> ProductDetailRepository
```

## 5.8 `SearchModule.kt` — split using the revised boundary

Current file combines product repository, DB, TR partition and Search History repository.

### App `SearchModule.kt` retains only composition values

Keep:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt
```

with providers for:

```text
LocalCommerceDatabase

SearchHistoryPartition(
    environmentId = configuration.environment.name,
    marketId = "TR"
)
```

Database construction stays exactly:

```kotlin
Room.databaseBuilder(
    context,
    LocalCommerceDatabase::class.java,
    "gurbakir-local.db"
)
    .addMigrations(WISHLIST_MIGRATION_1_2)
    .build()
```

Do not expose `RoomSearchHistoryStore` or `DefaultSearchHistoryRepository` to the app.

### Core new module

Create:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CoreSearchModule.kt
```

Provide:

```text
ProductSearchRepository =
    DefaultProductSearchRepository(StorefrontSearchGateway)

SearchHistoryStore =
    RoomSearchHistoryStore(LocalCommerceDatabase)

SearchHistoryClock =
    SearchHistoryClock(System::currentTimeMillis)

SearchHistoryRepository =
    DefaultSearchHistoryRepository(
        store,
        SearchHistoryPartition,
        clock
    )
```

## 5.9 `StorefrontModule.kt` — move

Move:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/StorefrontModule.kt
    -> mobile-core/src/main/kotlin/com/gurbakir/mobile/di/StorefrontModule.kt
```

It already consumes `AppConfiguration` and creates reusable Shopify Storefront gateways.

Do not redesign Storefront ownership in Gate 1.

## 5.10 `UpdatePolicyModule.kt` — split

### App `UpdatePolicyModule.kt`

Retain/provide only:

```text
UpdatePolicyRemoteGateway =
    FirebaseUpdatePolicyRemoteGateway(RemoteFeatureFlags)

CurrentAppVersionCode =
    CurrentAppVersionCode(BuildConfig.VERSION_CODE)
```

`CurrentAppVersionCode` becomes public because app must instantiate it across the module boundary.

### Core

Create:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CoreUpdatePolicyModule.kt
```

Provide:

```text
UpdatePolicyStore =
    AndroidUpdatePolicyStore(applicationContext)

UpdatePolicyClock =
    UpdatePolicyClock(System::currentTimeMillis)

UpdatePolicyController =
    DefaultUpdatePolicyController(
        remoteGateway,
        store,
        currentVersionCode,
        clock
    )
```

## 5.11 `WishlistModule.kt` — split using the revised boundary

### App retains only concrete partition

Keep:

```text
app/src/main/kotlin/com/gurbakir/mobile/di/WishlistModule.kt
```

and provide only:

```text
WishlistPartition(
    environmentId = configuration.environment.name,
    marketId = "TR"
)
```

### Core

Create:

```text
mobile-core/src/main/kotlin/com/gurbakir/mobile/di/CoreWishlistModule.kt
```

Provide:

```text
WishlistStore =
    RoomWishlistStore(LocalCommerceDatabase)

WishlistClock =
    WishlistClock(System::currentTimeMillis)

WishlistRepository =
    DefaultWishlistRepository(
        store,
        StorefrontProductGateway,
        WishlistPartition,
        clock
    )
```

The app does not construct or reference:

```text
RoomWishlistStore
DefaultWishlistRepository
```

after extraction.

---

# 6. Debug and Source-Set Ownership

Remain app-owned:

```text
app/src/debug/**
app/src/testDebug/**
app/src/debug/AndroidManifest.xml
```

In particular, keep:

```text
Stage3EvidenceActivity.kt
Stage4EvidenceActivity.kt
```

and the existing deterministic proof/evidence surfaces.

They validate the concrete Gürbakır application, not generic shared-core behavior.

Because they call reusable shell/navigation helpers, expose only the exact API listed in section 9.

---

# 7. Resource and Manifest Ownership

## App retains existing concrete resources

Do not delete or rewrite the existing:

```text
app/src/main/res/**
```

Gate 1 treats that tree as the exact Gürbakır app resource overlay.

App-only resources include:

```text
launcher/adaptive launcher identity
Theme.Gurbakir
brand/application resources
data_extraction_rules.xml
manifest-owned application identity
```

Do not copy `ic_launcher_foreground.xml` to core.

## Core receives reusable resources required by moved source

Known reusable drawable baseline from current application source:

```text
ic_account_address.xml
ic_account_cart.xml
ic_account_delete.xml
ic_account_device.xml
ic_account_help.xml
ic_account_orders.xml
ic_arrow_back.xml
ic_expand_more.xml
ic_nav_account.xml
ic_nav_account_selected.xml
ic_nav_categories.xml
ic_nav_categories_selected.xml
ic_nav_home.xml
ic_nav_home_selected.xml
ic_nav_search.xml
ic_nav_search_selected.xml
ic_nav_wishlist.xml
ic_nav_wishlist_selected.xml
ic_search_clear.xml
```

This list is an evidence-backed starting inventory.

The mechanical inventory in Task 3.5 is the final authority and must detect any additional resource types/names.

Known plural resources include:

```text
R.plurals.order_list_added
R.plurals.wishlist_product_count
```

Both Turkish/default and English definitions must move to core without text or quantity changes.

---

# 8. `:mobile-core` Gradle Contract

Create:

```text
mobile-core/build.gradle.kts
```

## 8.1 Plugins

Use the current version-catalog aliases:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.room)
}
```

Do **not** apply:

```text
libs.plugins.android.application
libs.plugins.google.services
```

No Google Services plugin belongs in core.

## 8.2 Android contract

Configure:

```kotlin
android {
    namespace = "com.gurbakir.mobile.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        managedDevices {
            localDevices {
                create("ciApi30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    lint {
        lintConfig = rootProject.file("config/lint.xml")
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        sarifReport = true
        xmlReport = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/AL2.0",
            "/META-INF/LGPL2.1",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md"
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

There are no:

```text
applicationId
target application identity
productFlavors
environment flavor dimension
signing configs
resValue app_name
manifest appAuth redirect placeholder
Firebase JSON checks
app BuildConfig fields
```

in `:mobile-core`.

## 8.3 Project dependencies

```kotlin
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    api(projects.foundation)

    implementation(projects.storefront)
    implementation(projects.account)
    implementation(projects.checkout)
```

`foundation` is `api` because public core composition/signatures already expose foundation-owned configuration/design types such as `BrandConfiguration`.

Do not add:

```kotlin
implementation(projects.app)
implementation(projects.firebase)
implementation(platform(libs.firebase.bom))
```

## 8.4 AndroidX/application runtime dependencies

Mirror the exact current aliases required by moved application source:

```kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    implementation(libs.serialization.json)

    implementation(libs.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
```

`okhttp` and Coil network support are retained because the moved application/UI source currently compiles against the same app dependency set; do not opportunistically prune them during the extraction commit. Dependency cleanup is separate from ownership extraction.

## 8.5 Compose dependencies

```kotlin
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
```

## 8.6 JVM test dependencies

```kotlin
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
```

## 8.7 Instrumentation dependencies

```kotlin
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```

Do not add Firebase instrumentation dependencies.

---

# 9. Cross-Module API / Visibility Map

Expose the minimum required app → core surface.

## Public core contracts

### Root composition

```kotlin
@Composable
fun MobileCoreApp(
    brand: BrandConfiguration,
    deepLinks: MobileDeepLinkConfiguration,
    trackingUrlPolicy: TrackingUrlPolicy,
    legalSupportDestination: @Composable (onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController()
)
```

### Deep links

```kotlin
data class MobileDeepLinkConfiguration(
    val collectionBasePath: String,
    val productBasePath: String,
    val orderBasePath: String
)
```

### Existing Home configuration types remain public

```text
MarketConfiguration
HomeCollectionSource
HomeProductRangeConfiguration
HomeFeaturedProductConfiguration
HomeConfiguration
```

### Existing Catalog configuration types remain public

```text
CatalogCategorySource
CatalogConfiguration
```

### Address policy

```kotlin
data class AddressTerritoryPolicy(
    val supportedTerritoryCode: String
) {
    init {
        require(supportedTerritoryCode.isNotBlank())
    }

    fun supports(territoryCode: String?): Boolean =
        territoryCode == supportedTerritoryCode
}
```

### Tracking

Keep public:

```text
TrackingUrlPolicy
```

### Provider-neutral update-policy API

```kotlin
data class UpdatePolicySnapshot(
    val maintenanceMessageEnabled: Boolean = false,
    val checkoutPreloadEnabled: Boolean = false,
    val optionalUpdateMessageEnabled: Boolean = false,
    val recommendedVersionCode: Int = 0,
    val policyRevision: Long = 0L
)

sealed interface UpdatePolicyRefreshResult {
    data class Fetched(
        val snapshot: UpdatePolicySnapshot,
        val fetchedAtEpochMillis: Long
    ) : UpdatePolicyRefreshResult

    data object LocalDefaults : UpdatePolicyRefreshResult
}

fun interface UpdatePolicyRemoteGateway {
    suspend fun refresh(): UpdatePolicyRefreshResult
}

data class CurrentAppVersionCode(val value: Int)
```

`CurrentAppVersionCode` changes from current `internal` to public specifically because app provides it from `BuildConfig.VERSION_CODE`.

### Persistence/composition types already required by app

Keep public:

```text
LocalCommerceDatabase
SearchHistoryPartition
WishlistPartition
WISHLIST_MIGRATION_1_2
```

### Debug-evidence compatibility surface

Change to public:

```text
ProductionAppShell
ProductionNavHost
ProductionDestinationContent
NavHostController.popBackStackOrHome
```

These are repository-internal cross-module APIs, not a general plugin framework.

## Keep internal in core

```text
PrimaryDestination
ProductionTestTags

UpdatePolicySource
UpdatePolicyPresentation
CachedUpdatePolicy
UpdatePolicyStore
UpdatePolicyClock
UpdatePolicyController
DefaultUpdatePolicyController
AndroidUpdatePolicyStore
UPDATE_POLICY_CACHE_TTL_MILLIS
UPDATE_POLICY_CLOCK_SKEW_TOLERANCE_MILLIS

RoomSearchHistoryStore
RoomWishlistStore

core-only destination composables
generic Hilt implementation details
```

Generic navigation/shell tests move into core so `ProductionTestTags` does not need public visibility.

## Keep internal in app

```text
GurbakirHomeConfiguration
GurbakirCatalogConfiguration
GurbakirDeepLinkConfiguration
GurbakirAddressTerritoryPolicy
GurbakirTrackingUrlPolicy
FirebaseUpdatePolicyRemoteGateway
concrete legal/support destination
```

Do not fix module-boundary compilation errors by broadly changing declarations to `public`.

For every visibility error:

1. identify the actual app consumer;
2. move the consumer to core if it proves reusable behavior;
3. otherwise expose only the specific justified contract/helper listed here.

---

# 10. Extraction-Required Contracts

## 10.1 Concrete Gürbakır deep links

App owns exactly:

```text
collectionBasePath = https://gurbakir.com/collections
productBasePath    = https://gurbakir.com/apps/mobile/products
orderBasePath      = https://gurbakir.com/apps/mobile/orders
```

Manifest remains app-owned and unchanged.

## 10.2 Home

Change:

```text
DefaultHomeContentRepository(gateway)
```

to:

```text
DefaultHomeContentRepository(
    gateway,
    configuration
)
```

Remove:

```text
GurbakirHomeConfiguration.value
```

from reusable repository implementation.

## 10.3 Catalog

Change:

```text
DefaultCatalogRepository(
    homeGateway,
    catalogGateway
)
```

to:

```text
DefaultCatalogRepository(
    homeGateway,
    catalogGateway,
    configuration
)
```

Remove static Gürbakır configuration access.

---

# 11. Complete Address Territory Seam

Remove the reusable production constant:

```text
SUPPORTED_ADDRESS_TERRITORY = "TR"
```

## `AddressContent`

Change from computed global behavior to mapped state:

```kotlin
data class AddressContent(
    val id: String,
    val firstName: String,
    val lastName: String,
    val company: String,
    val address1: String,
    val address2: String,
    val city: String,
    val zip: String,
    val phoneNumber: String,
    val territoryCode: String?,
    val formatted: List<String>,
    val isDefault: Boolean,
    val isSupported: Boolean
)
```

Keep redacted `toString()` semantics.

## `DefaultAddressController`

Constructor:

```kotlin
class DefaultAddressController @Inject constructor(
    gateway: CustomerAddressGateway,
    private val sessionCoordinator: CustomerAccountSessionCoordinator,
    private val territoryPolicy: AddressTerritoryPolicy
) : AddressController
```

Construct:

```kotlin
private val reader =
    AddressReader(
        gateway = gateway,
        territoryPolicy = territoryPolicy
    )

private val mutations =
    AddressMutationCoordinator(
        gateway = gateway,
        reader = reader,
        sessionCoordinator = sessionCoordinator,
        territoryPolicy = territoryPolicy
    )
```

## Mapping

Replace:

```text
CustomerAddress.toAddressContent()
```

with:

```kotlin
CustomerAddress.toAddressContent(
    territoryPolicy: AddressTerritoryPolicy
)
```

and set:

```kotlin
isSupported = territoryPolicy.supports(territoryCode)
```

## Load/list

`loadAddresses()` maps every API address using the injected policy.

Existing UI continues consuming:

```text
AddressContent.isSupported
```

without needing the policy itself.

## Form load

Replace direct constant comparison with:

```kotlin
!territoryPolicy.supports(address.territoryCode)
```

and map the ready address with the same policy.

## Reader

Constructor:

```kotlin
private class AddressReader(
    private val gateway: CustomerAddressGateway,
    private val territoryPolicy: AddressTerritoryPolicy
)
```

Any conversion to `AddressContent` passes that policy.

## Mutation coordinator

Store:

```kotlin
private val territoryPolicy: AddressTerritoryPolicy
```

Use:

```text
!current.address.isSupported
```

for existing-address update/save and set-default support checks.

Do not add a new unsupported-territory rejection to delete flow because current delete semantics do not have that rule.

## Draft creation

Change:

```text
AddressInput.toDraft()
```

to:

```kotlin
AddressInput.toDraft(
    territoryPolicy: AddressTerritoryPolicy
)
```

Set:

```kotlin
territoryCode = territoryPolicy.supportedTerritoryCode
```

Both create and update paths pass the policy.

## Gürbakır binding

App:

```kotlin
internal val GurbakirAddressTerritoryPolicy =
    AddressTerritoryPolicy("TR")
```

Core address production code contains no concrete `"TR"` default.

## Required Address tests

Core `AddressControllerTest`:

- `"TR"` is supported when test policy is `"TR"`;
- non-TR is unsupported under `"TR"` policy;
- unsupported form load remains `UNSUPPORTED_COUNTRY`;
- unsupported existing-address save/update remains rejected;
- unsupported set-default remains rejected;
- create draft sends policy territory;
- update draft sends policy territory;
- synthetic `"US"` policy marks `"US"` supported and creates/updates `"US"` drafts;
- delete semantics remain unchanged.

Core `AddressScreenTest` fixtures set:

```text
isSupported = true
```

or:

```text
isSupported = false
```

explicitly.

Verify:

- unsupported warning behavior unchanged;
- edit/default actions behave exactly as before;
- supported UI unchanged.

Update all direct `AddressContent(...)` test/debug fixtures, including:

```text
AddressViewModelTest
AddressScreenTest
Stage3EvidenceActivity
```

---

# 12. Tracking Policy Seam

Keep validation algorithm in core.

Change:

```kotlin
class TrackingUrlPolicy(
    private val allowedHosts: Set<String>
)
```

There is no default host set.

`TrackingLauncher` receives an explicit `TrackingUrlPolicy`.

App owns exact current host set:

```text
ptt.gov.tr
yurticikargo.com
araskargo.com.tr
suratkargo.com.tr
```

Do not change validation rules for:

```text
HTTPS
ports
userinfo
fragments
normalization
subdomain matching
maximum URL length
```

---

# 13. Provider-Neutral Update-Policy Seam

## New core contract

Use section 9 `UpdatePolicySnapshot`, `UpdatePolicyRefreshResult`, `UpdatePolicyRemoteGateway`.

Change `CachedUpdatePolicy.snapshot` from Firebase `RemotePolicySnapshot` to core `UpdatePolicySnapshot`.

## Controller failure semantics

Preserve exactly:

```kotlin
val result =
    try {
        remoteGateway.refresh()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        UpdatePolicyRefreshResult.LocalDefaults
    }
```

Then:

- `LocalDefaults` → existing `readCacheOrDefaults(now)`;
- invalid/stale/future timestamp → existing `readCacheOrDefaults(now)`;
- valid `Fetched` → cache `result.snapshot`;
- `store.writePolicy` remains fail-soft through `runCatching`;
- valid fetched policy can still return `REMOTE_CONFIG` presentation when cache persistence fails.

## App Firebase adapter

Create:

```text
app/src/main/kotlin/com/gurbakir/mobile/update/FirebaseUpdatePolicyRemoteGateway.kt
```

Conceptual implementation:

```kotlin
internal class FirebaseUpdatePolicyRemoteGateway(
    private val remoteFeatureFlags: RemoteFeatureFlags
) : UpdatePolicyRemoteGateway {
    override suspend fun refresh(): UpdatePolicyRefreshResult =
        when (val result = remoteFeatureFlags.refresh()) {
            RemoteConfigResult.LocalDefaults ->
                UpdatePolicyRefreshResult.LocalDefaults

            is RemoteConfigResult.Fetched ->
                UpdatePolicyRefreshResult.Fetched(
                    snapshot = remoteFeatureFlags.policySnapshot().toUpdatePolicySnapshot(),
                    fetchedAtEpochMillis = result.fetchedAtEpochMillis
                )
        }
}
```

Do not consume/catch `CancellationException` in this adapter.

Do not carry `activatedNewValues`; core does not use it.

## Persisted compatibility

Keep SharedPreferences name:

```text
bounded-update-policy
```

Keep storage schema:

```text
1
```

Keep source marker:

```text
firebase-remote-config
```

Keep exact keys:

```text
schema_version
source
fetched_at_epoch_millis
expires_at_epoch_millis
maintenance_message_enabled
checkout_preload_enabled
optional_update_message_enabled
recommended_version_code
policy_revision
deferred_recommended_version_code
deferred_at_epoch_millis
deferred_until_epoch_millis
```

The persisted source marker may remain provider-specific for migration compatibility; that does not create a Gradle/runtime dependency on `:firebase`.

## Required tests

Core `UpdatePolicyControllerTest`:

1. valid `Fetched` produces `REMOTE_CONFIG`;
2. valid `Fetched` stores exact `UpdatePolicySnapshot`;
3. `LocalDefaults` uses unexpired cache;
4. `LocalDefaults` with no valid cache uses safe defaults;
5. non-cancellation gateway exception uses identical cache/default fallback;
6. `CancellationException` propagates;
7. stale timestamp cannot replace valid cache;
8. future timestamp cannot replace valid cache;
9. false remote values overwrite cached notice as current rollback path;
10. deferral behavior remains unchanged.

App `FirebaseUpdatePolicyRemoteGatewayTest`:

1. Firebase `Fetched` maps all fields/timestamp;
2. Firebase `LocalDefaults` maps to core `LocalDefaults`;
3. cancellation propagates.

Core `AndroidUpdatePolicyStoreTest`:

1. provider-neutral snapshot survives store recreation;
2. expiry remains unchanged;
3. malformed metadata fails closed;
4. manually seed the existing key/value layout and verify it reads into new snapshot;
5. write a new snapshot and inspect SharedPreferences to prove exact key names/source/types remain unchanged.

---

# Task 0: Prove the Gate 1 Execution Base and Capture Baseline Evidence

**Files:** read-only.

**Produces:** exact `$Gate1BaseSha`, baseline APKs/manifests, Room schema hashes.

- [ ] Fetch current remote state:

```powershell
git fetch --prune origin main
```

- [ ] Resolve current remote main:

```powershell
$OriginMainSha = (git rev-parse refs/remotes/origin/main).Trim()

if ([string]::IsNullOrWhiteSpace($OriginMainSha)) {
    throw "Could not resolve origin/main."
}
```

- [ ] At execution time, create/use the isolated Gate 1 worktree using `superpowers:using-git-worktrees`, based on this fetched `origin/main`.

Do not touch unrelated working trees.

- [ ] Inside that isolated worktree, prove exact equality before editing:

```powershell
$HeadSha = (git rev-parse HEAD).Trim()
$OriginMainSha = (git rev-parse refs/remotes/origin/main).Trim()

if ($HeadSha -ne $OriginMainSha) {
    throw "Gate 1 must start exactly from current origin/main. HEAD=$HeadSha origin/main=$OriginMainSha"
}
```

- [ ] Prove worktree cleanliness:

```powershell
$Status = @(git status --porcelain=v1 --untracked-files=normal)

if ($Status.Count -ne 0) {
    throw "Gate 1 worktree contains pre-existing changes. Preserve them and use a fresh isolated worktree."
}
```

Do not use:

```text
git reset
git clean
git stash
```

to dispose of unrelated work.

- [ ] Record base only after equality and cleanliness pass:

```powershell
$Gate1BaseSha = $OriginMainSha
```

- [ ] Create evidence directory outside repository:

```powershell
$BaselineDir = Join-Path $env:TEMP "gurbakir-gate1-$Gate1BaseSha"
New-Item -ItemType Directory -Force $BaselineDir | Out-Null
```

## Baseline Room hashes

- [ ] Capture schema hashes relative to the schema root:

```powershell
$SchemaRoot = (Resolve-Path 'app/schemas').Path

$BaselineSchemas =
    Get-ChildItem $SchemaRoot -Recurse -File -Filter '*.json' |
    ForEach-Object {
        [pscustomobject]@{
            RelativePath =
                [IO.Path]::GetRelativePath($SchemaRoot, $_.FullName).
                    Replace('\', '/')
            Sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        }
    } |
    Sort-Object RelativePath

$BaselineSchemas |
    Export-Csv (Join-Path $BaselineDir 'room-schema-hashes.csv') -NoTypeInformation
```

## Baseline build/JVM verification

- [ ] Run:

```powershell
.\gradlew.bat `
  :foundation:testDebugUnitTest `
  :account:testDebugUnitTest `
  :checkout:testDebugUnitTest `
  :storefront:testDebugUnitTest `
  :firebase:testDebugUnitTest `
  :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest `
  :app:assembleDevelopmentDebug `
  :app:assembleDevelopmentRelease `
  :app:assembleStagingDebug `
  :app:assembleStagingRelease
```

All must pass before Gate 1 source edits.

## Preserve baseline Development Debug APK for optional physical-device upgrade proof

- [ ] Copy the exact pre-Gate-1 Development Debug APK into the external baseline evidence directory:

```powershell
Copy-Item `
  'app/build/outputs/apk/development/debug/app-development-debug.apk' `
  (Join-Path $BaselineDir 'app-development-debug-baseline.apk') `
  -Force
```

This APK is evidence only. It is retained so a later optional physical-device preservation pass can perform a true pre-Gate-1 -> post-Gate-1 `adb install -r` upgrade using the same Development Debug application identity/signing lineage.

Do not install it during Task 0 unless a physical-device validation pass is intentionally being performed.

## Baseline exact APK application IDs

- [ ] Resolve `apkanalyzer`:

```powershell
$ApkAnalyzer = (Get-Command apkanalyzer.bat -ErrorAction Stop).Source
```

- [ ] Extract all four IDs:

```powershell
& $ApkAnalyzer manifest application-id `
  app/build/outputs/apk/development/debug/app-development-debug.apk

& $ApkAnalyzer manifest application-id `
  app/build/outputs/apk/development/release/app-development-release.apk

& $ApkAnalyzer manifest application-id `
  app/build/outputs/apk/staging/debug/app-staging-debug.apk

& $ApkAnalyzer manifest application-id `
  app/build/outputs/apk/staging/release/app-staging-release.apk
```

Expected exactly, in the same order:

```text
com.gurbakir.mobile.dev.debug
com.gurbakir.mobile.dev
com.gurbakir.mobile.staging.debug
com.gurbakir.mobile.staging
```

Any mismatch blocks Gate 1 until the execution baseline is understood.

## Baseline merged manifests

- [ ] Capture:

```powershell
& $ApkAnalyzer manifest print `
  app/build/outputs/apk/development/debug/app-development-debug.apk |
    Out-File -Encoding utf8 `
      (Join-Path $BaselineDir 'development-debug-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/development/release/app-development-release.apk |
    Out-File -Encoding utf8 `
      (Join-Path $BaselineDir 'development-release-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/staging/debug/app-staging-debug.apk |
    Out-File -Encoding utf8 `
      (Join-Path $BaselineDir 'staging-debug-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/staging/release/app-staging-release.apk |
    Out-File -Encoding utf8 `
      (Join-Path $BaselineDir 'staging-release-manifest.xml')
```

## Portability baseline

- [ ] Run:

```powershell
pwsh ./scripts/Test-RepositoryPortability.ps1
```

**Expected outcome:** verified clean execution base, passing current app baseline, exact package IDs, captured manifests and path-independent Room hashes.

**Commit:** none.

---

# Task 1: Scaffold `:mobile-core`

**Files:**
- Modify `settings.gradle.kts`
- Create `mobile-core/build.gradle.kts`
- Create `mobile-core/src/main/AndroidManifest.xml`
- Create `mobile-core/consumer-rules.pro`
- Create/generated `mobile-core/gradle.lockfile`
- Modify `app/build.gradle.kts`

- [ ] Add:

```kotlin
include(":mobile-core")
```

using the existing settings style/project accessors.

- [ ] Create `mobile-core/build.gradle.kts` exactly from section 8.

- [ ] Create minimal library manifest:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

unless the Android plugin in the then-current verified baseline requires the standard package-free manifest form generated by this repository. No application/activity/provider declarations belong here.

- [ ] Copy the typed Navigation R8 compatibility rule currently in:

```text
app/proguard-rules.pro
```

into:

```text
mobile-core/consumer-rules.pro
```

Do not remove the app copy in Gate 1.

- [ ] Add to app:

```kotlin
implementation(projects.mobileCore)
```

Do not remove existing app project dependencies yet.

- [ ] Generate locks through real resolution:

```powershell
.\gradlew.bat `
  :mobile-core:assembleDebug `
  :mobile-core:assembleRelease `
  :mobile-core:testDebugUnitTest `
  --write-locks
```

- [ ] Inspect locks:

```powershell
git diff -- '*gradle.lockfile'
```

At this scaffold point, expect the new:

```text
mobile-core/gradle.lockfile
```

and no unexplained dependency/version drift in existing modules.

- [ ] Verify:

```powershell
.\gradlew.bat `
  :mobile-core:assembleDebug `
  :mobile-core:assembleRelease `
  :app:assembleDevelopmentDebug
```

**Expected outcome:** a real empty reusable Android library exists; Gürbakır app behavior is unchanged.

**Recommended commit:**

```text
build(mobile-core): add shared Android application module
```

---

# Task 2: Introduce Extraction Seams While Code Still Lives in `:app`

This task isolates behavioral refactoring from physical file-move noise.

## Files

Modify/create:

```text
app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt
app/src/main/kotlin/com/gurbakir/mobile/GurbakirApp.kt
app/src/main/kotlin/com/gurbakir/mobile/LegalSupportDestination.kt

app/src/main/kotlin/com/gurbakir/mobile/home/HomeConfiguration.kt
app/src/main/kotlin/com/gurbakir/mobile/home/GurbakirHomeConfiguration.kt
app/src/main/kotlin/com/gurbakir/mobile/home/HomeContentRepository.kt

app/src/main/kotlin/com/gurbakir/mobile/catalog/CatalogConfiguration.kt
app/src/main/kotlin/com/gurbakir/mobile/catalog/GurbakirCatalogConfiguration.kt
app/src/main/kotlin/com/gurbakir/mobile/catalog/CatalogRepository.kt

app/src/main/kotlin/com/gurbakir/mobile/navigation/MobileDeepLinkConfiguration.kt
app/src/main/kotlin/com/gurbakir/mobile/navigation/GurbakirDeepLinkConfiguration.kt

app/src/main/kotlin/com/gurbakir/mobile/address/AddressTerritoryPolicy.kt
app/src/main/kotlin/com/gurbakir/mobile/address/GurbakirAddressTerritoryPolicy.kt
app/src/main/kotlin/com/gurbakir/mobile/address/AddressController.kt

app/src/main/kotlin/com/gurbakir/mobile/order/TrackingUrlPolicy.kt
app/src/main/kotlin/com/gurbakir/mobile/order/GurbakirTrackingUrlPolicy.kt

app/src/main/kotlin/com/gurbakir/mobile/update/UpdatePolicyContracts.kt
app/src/main/kotlin/com/gurbakir/mobile/update/UpdatePolicyRemoteGateway.kt
app/src/main/kotlin/com/gurbakir/mobile/update/FirebaseUpdatePolicyRemoteGateway.kt
app/src/main/kotlin/com/gurbakir/mobile/update/AndroidUpdatePolicyStore.kt

app/src/main/kotlin/com/gurbakir/mobile/di/ApplicationModule.kt
app/src/main/kotlin/com/gurbakir/mobile/di/CatalogModule.kt
app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt
app/src/main/kotlin/com/gurbakir/mobile/di/WishlistModule.kt
app/src/main/kotlin/com/gurbakir/mobile/di/UpdatePolicyModule.kt
```

## Home

- [ ] Split concrete object from reusable configuration data.
- [ ] Make `DefaultHomeContentRepository` require `HomeConfiguration`.
- [ ] Add exact app binding.
- [ ] Change reusable tests to explicit neutral config.
- [ ] Add app composition assertions for current exact TR/TRY/handles/resources.

## Catalog

- [ ] Split concrete object.
- [ ] Require `CatalogConfiguration` in `DefaultCatalogRepository`.
- [ ] Add app binding.
- [ ] Change generic tests to explicit fixtures.
- [ ] Preserve exact current Gürbakır order/handles.

## Deep links

- [ ] Add `MobileDeepLinkConfiguration`.
- [ ] Add exact app-owned `GurbakirDeepLinkConfiguration`.
- [ ] Replace hardcoded origins in reusable NavHost.
- [ ] Do not change manifest.

## Root

- [ ] Move concrete wrapper semantics out of `ProductionApp.kt` into new `GurbakirApp.kt`.

`GurbakirApp` supplies:

```text
GurbakirBrand.configuration
GurbakirDeepLinkConfiguration
GurbakirTrackingUrlPolicy
Gürbakır LegalSupportDestination
```

`MainActivity.kt` should continue:

```kotlin
setContent { GurbakirApp() }
```

## Legal seam

- [ ] Change app-owned legal destination to:

```kotlin
@Composable
internal fun LegalSupportDestination(
    onBack: () -> Unit
)
```

The app implementation still owns its launcher/ViewModel/policy.

## Address

- [ ] Implement section 11 completely.

## Tracking

- [ ] Implement section 12 completely.

## Update policy

- [ ] Implement section 13 completely.
- [ ] Do not leave any provider-specific model in controller/store signatures.

## Search/Wishlist

- [ ] Refactor DI now so later app/core split exposes only DB and partitions from app, not generic store/repository implementation types.

## Required focused tests before movement

- [ ] Run:

```powershell
.\gradlew.bat `
  :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest
```

- [ ] Run all four app builds:

```powershell
.\gradlew.bat `
  :app:assembleDevelopmentDebug `
  :app:assembleDevelopmentRelease `
  :app:assembleStagingDebug `
  :app:assembleStagingRelease
```

Required behaviors:

```text
Home explicit configuration
Catalog explicit configuration
exact Gürbakır Home fixture
exact Gürbakır Catalog fixture
exact Gürbakır deep-link config
Address complete policy behavior
Tracking exact current host behavior
Update-policy cancellation propagation
Update-policy provider exception fallback
Fetched mapping
LocalDefaults mapping
persisted update-policy compatibility
BuildConfig.VERSION_CODE supplied only through CurrentAppVersionCode
```

**Recommended commit:**

```text
refactor(app): introduce mobile-core composition seams
```

---

# Task 3: Atomically Extract Reusable Production Implementation

This is one coherent structural commit.

## 3.1 Move root and feature production source

Perform the exact moves from sections 3 and 4.

After movement, app production source should primarily contain:

```text
GurbakirApplication.kt
MainActivity.kt
GurbakirApp.kt
LegalSupportDestination.kt

brand/**
config/**
legal/**

home/GurbakirHomeConfiguration.kt
catalog/GurbakirCatalogConfiguration.kt
navigation/GurbakirDeepLinkConfiguration.kt
address/GurbakirAddressTerritoryPolicy.kt
order/GurbakirTrackingUrlPolicy.kt
update/FirebaseUpdatePolicyRemoteGateway.kt

app-specific di/**
```

Do not use file count alone as proof; use ownership.

## 3.2 Split/move DI

Perform section 5 exactly.

Do not move `di/` wholesale.

## 3.3 Move Room source ownership

Move:

```text
LocalCommerceDatabase
SearchHistoryEntity
SearchHistorySettingEntity
SearchHistoryDao
RoomSearchHistoryStore

WishlistEntity
WishlistDao
RoomWishlistStore
WISHLIST_MIGRATION_1_2
```

with their existing owning source files to core.

Move the entire:

```text
app/schemas/
```

to:

```text
mobile-core/schemas/
```

including historical retained schema directories, not only the currently active database directory.

Remove Room schema-directory/plugin/compiler ownership from app only after the core Room build is configured.

Keep:

```kotlin
implementation(libs.room.runtime)
```

and:

```kotlin
implementation(libs.room.ktx)
```

in app if required by the app-side `Room.databaseBuilder` call and current imports after extraction.

Do not leave Room compiler/schema generation in app.

## 3.4 Verify Room relocation by relative schema path + SHA

- [ ] Run:

```powershell
$Baseline =
    Import-Csv (Join-Path $BaselineDir 'room-schema-hashes.csv') |
    Sort-Object RelativePath

$CurrentRoot = (Resolve-Path 'mobile-core/schemas').Path

$Current =
    Get-ChildItem $CurrentRoot -Recurse -File -Filter '*.json' |
    ForEach-Object {
        [pscustomobject]@{
            RelativePath =
                [IO.Path]::GetRelativePath($CurrentRoot, $_.FullName).
                    Replace('\', '/')
            Sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        }
    } |
    Sort-Object RelativePath

$SchemaDiff =
    Compare-Object `
      -ReferenceObject $Baseline `
      -DifferenceObject $Current `
      -Property RelativePath, Sha256

if ($SchemaDiff) {
    $SchemaDiff | Format-Table -AutoSize
    throw "Room schema history changed during Gate 1 extraction."
}
```

Expected:

```text
zero differences
```

## 3.5 Build mechanically complete core resource set

Define:

```powershell
function Get-AndroidResourceReferences {
    param(
        [Parameter(Mandatory)]
        [string[]] $Roots
    )

    $pattern =
        '(?<![\w.])R\.(?<Type>[A-Za-z_][A-Za-z0-9_]*)\.(?<Name>[A-Za-z_][A-Za-z0-9_]*)'

    foreach ($root in $Roots) {
        if (-not (Test-Path $root)) {
            continue
        }

        Get-ChildItem $root -Recurse -File -Filter '*.kt' |
            ForEach-Object {
                $file = $_
                $content = Get-Content $file.FullName -Raw

                foreach ($match in [regex]::Matches($content, $pattern)) {
                    [pscustomobject]@{
                        SourceRoot = $root
                        File       = $file.FullName
                        Type       = $match.Groups['Type'].Value
                        Name       = $match.Groups['Name'].Value
                        Reference  = $match.Value
                    }
                }
            }
    }
}
```

- [ ] Inventory production references:

```powershell
$CoreMainResources =
    Get-AndroidResourceReferences @(
        'mobile-core/src/main/kotlin'
    )

$CoreMainResources |
    Sort-Object Type, Name, File |
    Format-Table -AutoSize

$CoreMainResources |
    Select-Object Type, Name -Unique |
    Sort-Object Type, Name
```

This must account for every discovered type:

```text
string
plurals
drawable
color
dimen
font
array
bool
integer
raw
xml
style
```

or any additional valid resource type actually present.

- [ ] Copy each directly referenced resource into:

```text
mobile-core/src/main/res/
```

### Strings and plurals

Copy exact matching default XML nodes from:

```text
app/src/main/res/values/**
```

and English overrides from:

```text
app/src/main/res/values-en/**
```

Preserve resource type.

Specifically verify:

```text
order_list_added
wishlist_product_count
```

exist as `<plurals>` in both core default and English resources.

### Drawables

Copy exact referenced drawables.

Known baseline:

```text
ic_account_address.xml
ic_account_cart.xml
ic_account_delete.xml
ic_account_device.xml
ic_account_help.xml
ic_account_orders.xml
ic_arrow_back.xml
ic_expand_more.xml
ic_nav_account.xml
ic_nav_account_selected.xml
ic_nav_categories.xml
ic_nav_categories_selected.xml
ic_nav_home.xml
ic_nav_home_selected.xml
ic_nav_search.xml
ic_nav_search_selected.xml
ic_nav_wishlist.xml
ic_nav_wishlist_selected.xml
ic_search_clear.xml
```

Do not copy:

```text
ic_launcher_foreground.xml
```

unless source ownership was incorrectly classified; if core production code appears to need launcher identity, fix the ownership rather than moving launcher identity.

### XML transitive references

- [ ] Scan copied XML:

```powershell
$XmlReferencePattern =
    '@(?<Type>[A-Za-z_][A-Za-z0-9_]*)/(?<Name>[A-Za-z_][A-Za-z0-9_]*)'

$CoreXmlReferences =
    Get-ChildItem 'mobile-core/src/main/res' -Recurse -File -Include '*.xml' |
    ForEach-Object {
        $file = $_
        $content = Get-Content $file.FullName -Raw

        foreach ($match in [regex]::Matches($content, $XmlReferencePattern)) {
            [pscustomobject]@{
                File = $file.FullName
                Type = $match.Groups['Type'].Value
                Name = $match.Groups['Name'].Value
            }
        }
    }

$CoreXmlReferences |
    Sort-Object Type, Name, File |
    Format-Table -AutoSize
```

Every non-framework referenced resource must exist in core.

Do not use final app resource overlay to hide an incomplete standalone library resource graph.

## 3.6 Update `R` ownership

Core namespace:

```text
com.gurbakir.mobile.core
```

Kotlin packages remain:

```text
com.gurbakir.mobile...
```

Therefore moved source with unqualified `R.*` must import:

```kotlin
import com.gurbakir.mobile.core.R
```

- [ ] Verify main:

```powershell
$CoreMainFilesWithR =
    $CoreMainResources |
    Select-Object -ExpandProperty File -Unique

foreach ($file in $CoreMainFilesWithR) {
    $content = Get-Content $file -Raw

    if ($content -notmatch '(?m)^import com\.gurbakir\.mobile\.core\.R\s*$') {
        throw "Missing mobile-core R import: $file"
    }
}
```

- [ ] Ensure no app `R` import:

```powershell
git grep -n 'import com.gurbakir.mobile.R' -- mobile-core/src/main/kotlin
```

Expected: no output.

## 3.7 Apply visibility map

Change only declarations in section 9.

Do not expose `ProductionTestTags`.

## 3.8 Structural scans immediately

Run:

```powershell
git grep -n 'projects.app' -- `
  mobile-core foundation storefront account checkout firebase

git grep -n 'projects.firebase' -- mobile-core

git grep -n 'import com.gurbakir.firebase' -- mobile-core/src/main

git grep -n 'BuildConfig' -- mobile-core/src/main

git grep -n 'GurbakirBrand' -- mobile-core/src/main

git grep -n 'GurbakirHomeConfiguration' -- mobile-core/src/main

git grep -n 'GurbakirCatalogConfiguration' -- mobile-core/src/main

git grep -n 'gurbakir.com' -- mobile-core/src/main

git grep -n 'gurbakir-local.db' -- mobile-core/src/main

git grep -n 'SUPPORTED_ADDRESS_TERRITORY' -- mobile-core/src/main
```

Every command above must produce no matching line.

Do not scan for generic:

```text
gurbakir
```

because preserved historical Kotlin package names are legitimate.

## 3.9 Compile immediately

Run:

```powershell
.\gradlew.bat `
  :mobile-core:processDebugResources `
  :mobile-core:compileDebugKotlin `
  :mobile-core:testDebugUnitTest `
  :mobile-core:assembleDebug `
  :app:compileDevelopmentDebugKotlin `
  :app:assembleDevelopmentDebug
```

This is the first hard checkpoint for:

```text
R namespace
Hilt/KSP
serialization
Room compiler
cross-module visibility
provider direction
app composition
```

**Recommended commit:**

```text
refactor(mobile-core): extract reusable application implementation
```

---

# Task 4: Move Tests According to What They Prove

## Move reusable JVM tests to core

Move feature-level tests associated with moved production behavior under:

```text
account/**
accountdeletion/**
address/**
cart/**
catalog/**
checkout/**
home/**
order/**
product/**
profile/**
search/**
update/**
wishlist/**
AdaptiveLayoutPolicyTest.kt
```

from:

```text
app/src/test/kotlin/com/gurbakir/mobile/**
```

to:

```text
mobile-core/src/test/kotlin/com/gurbakir/mobile/**
```

except concrete Gürbakır composition/provider tests.

## Keep app JVM tests

Keep/create under app:

```text
brand/**
legal/**
Gurbakir composition contract tests
FirebaseUpdatePolicyRemoteGatewayTest
```

The app composition test asserts exact:

```text
GurbakirBrand
GurbakirHomeConfiguration
GurbakirCatalogConfiguration
GurbakirDeepLinkConfiguration
GurbakirAddressTerritoryPolicy
GurbakirTrackingUrlPolicy
```

## Move reusable instrumentation tests

Move screen/database tests whose production owner is now core.

Move Room migration tests to core.

Generic:

```text
ProductionAppShellTest
ProductionNavigationTest
```

move to core, with generic/non-Gürbakır deep-link fixtures.

This allows `ProductionTestTags` to stay internal.

## Keep assembled-app instrumentation

Keep app-owned proof for:

```text
FirebaseRuntimeTest
LocalizationResourceTest
manifest/App Link integration
concrete Gürbakır deep-link composition
debug proof/evidence flows
```

Keep any test whose primary subject is the assembled Gürbakır application rather than the reusable feature itself.

## Add app DB filename integration proof

App instrumentation must construct/obtain the app-provided DB and prove:

```text
gurbakir-local.db
```

is the physical sandbox filename.

Close/delete only the test database.

Do not touch actual customer/device production data.

## Test-source resource inventory

After movement:

```powershell
$CoreTestResources =
    Get-AndroidResourceReferences @(
        'mobile-core/src/test',
        'mobile-core/src/androidTest'
    )

$CoreTestResources |
    Sort-Object Type, Name, File |
    Format-Table -AutoSize
```

Every moved test with core-owned unqualified `R.*` must import:

```kotlin
com.gurbakir.mobile.core.R
```

App-owned tests continue using app `R`.

## Run

```powershell
.\gradlew.bat `
  :mobile-core:testDebugUnitTest `
  :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest `
  :mobile-core:assembleDebug
```

Then:

```powershell
.\gradlew.bat `
  :mobile-core:ciApi30DebugAndroidTest `
  :app:ciApi30DevelopmentDebugAndroidTest `
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

**Recommended commit:**

```text
test(multi-brand): split core and gurbakir application proof
```

---

# Task 5: Update CI and Structural Repository Guards

**Files:**
- `.github/workflows/android-foundation.yml`
- `scripts/Test-RepositoryPortability.ps1`

## Seven-module model

Update inventory to:

```text
app
mobile-core
foundation
storefront
account
checkout
firebase
```

Require:

```text
mobile-core/build.gradle.kts
mobile-core/gradle.lockfile
mobile-core/consumer-rules.pro
mobile-core/src/main/AndroidManifest.xml
mobile-core/schemas/
```

Remove stale “six module” assertions.

## Architecture guards

Make validator fail on:

```text
shared -> app dependency
mobile-core -> firebase dependency
app BuildConfig usage inside mobile-core production source
Firebase imports in core
GurbakirBrand in core production
GurbakirHomeConfiguration in core
GurbakirCatalogConfiguration in core
gurbakir.com in core production
gurbakir-local.db in core production
SUPPORTED_ADDRESS_TERRITORY in core
```

Do not create a global `gurbakir` ban.

## CI JVM lane

Add:

```text
:mobile-core:testDebugUnitTest
```

Keep:

```text
:app:testDevelopmentDebugUnitTest
:app:testStagingDebugUnitTest
```

## CI build lane

Add:

```text
:mobile-core:assembleDebug
:mobile-core:assembleRelease
```

Retain:

```text
:app:assembleDevelopmentDebug
:app:assembleDevelopmentRelease
:app:assembleStagingDebug
:app:assembleStagingRelease
:app:assembleDevelopmentDebugAndroidTest
:app:assembleStagingDebugAndroidTest
```

## CI instrumentation

Run:

```text
:mobile-core:ciApi30DebugAndroidTest
:app:ciApi30DevelopmentDebugAndroidTest
```

No full core × app × environment instrumentation Cartesian product.

## Re-resolve lockfiles

Run:

```powershell
.\gradlew.bat `
  :mobile-core:assembleDebug `
  :mobile-core:assembleRelease `
  :app:assembleDevelopmentDebug `
  --write-locks
```

Inspect all Gate 1 dependency lock/verification changes against base:

```powershell
git diff $Gate1BaseSha -- `
  '*gradle.lockfile' `
  gradle/verification-metadata.xml
```

Expected:

- new `mobile-core/gradle.lockfile`;
- app lock changes only where dependency ownership changed;
- no dependency version upgrades;
- no unrelated shared-module drift;
- no unexplained verification-metadata changes.

**Recommended commit:**

```text
ci(multi-brand): enforce mobile-core boundary
```

---

# Task 6: Final Gate 1 Validation Matrix

No single passing build completes Gate 1.

## 6.1 Formatting/static

```powershell
.\gradlew.bat spotlessCheck detekt lint
```

Required: PASS.

## 6.2 JVM tests

```powershell
.\gradlew.bat `
  :foundation:testDebugUnitTest `
  :account:testDebugUnitTest `
  :checkout:testDebugUnitTest `
  :storefront:testDebugUnitTest `
  :firebase:testDebugUnitTest `
  :mobile-core:testDebugUnitTest `
  :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest
```

Required: PASS.

## 6.3 Core library generation

```powershell
.\gradlew.bat `
  :mobile-core:assembleDebug `
  :mobile-core:assembleRelease
```

This proves:

```text
library R
Hilt/KSP
serialization
Room compiler/schema export
consumer R8 rules
release library build
```

## 6.4 All Gürbakır variants

```powershell
.\gradlew.bat `
  :app:assembleDevelopmentDebug `
  :app:assembleDevelopmentRelease `
  :app:assembleStagingDebug `
  :app:assembleStagingRelease `
  :app:assembleDevelopmentDebugAndroidTest `
  :app:assembleStagingDebugAndroidTest
```

Both release APKs are mandatory because typed navigation moved behind a library/R8 boundary.

## 6.5 Final exact APK application IDs

Resolve analyzer:

```powershell
$ApkAnalyzer = (Get-Command apkanalyzer.bat -ErrorAction Stop).Source
```

Capture IDs:

```powershell
$ActualApplicationIds = @(
    (& $ApkAnalyzer manifest application-id `
        app/build/outputs/apk/development/debug/app-development-debug.apk).Trim()

    (& $ApkAnalyzer manifest application-id `
        app/build/outputs/apk/development/release/app-development-release.apk).Trim()

    (& $ApkAnalyzer manifest application-id `
        app/build/outputs/apk/staging/debug/app-staging-debug.apk).Trim()

    (& $ApkAnalyzer manifest application-id `
        app/build/outputs/apk/staging/release/app-staging-release.apk).Trim()
)

$ExpectedApplicationIds = @(
    'com.gurbakir.mobile.dev.debug'
    'com.gurbakir.mobile.dev'
    'com.gurbakir.mobile.staging.debug'
    'com.gurbakir.mobile.staging'
)

for ($i = 0; $i -lt $ExpectedApplicationIds.Count; $i++) {
    if ($ActualApplicationIds[$i] -ne $ExpectedApplicationIds[$i]) {
        throw "Application ID mismatch at index $i. Expected=$($ExpectedApplicationIds[$i]) Actual=$($ActualApplicationIds[$i])"
    }
}

$ActualApplicationIds
```

Required output:

```text
com.gurbakir.mobile.dev.debug
com.gurbakir.mobile.dev
com.gurbakir.mobile.staging.debug
com.gurbakir.mobile.staging
```

## 6.6 Capture final merged manifests

```powershell
$FinalDir = Join-Path $env:TEMP "gurbakir-gate1-final-$Gate1BaseSha"
New-Item -ItemType Directory -Force $FinalDir | Out-Null

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/development/debug/app-development-debug.apk |
    Out-File -Encoding utf8 `
      (Join-Path $FinalDir 'development-debug-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/development/release/app-development-release.apk |
    Out-File -Encoding utf8 `
      (Join-Path $FinalDir 'development-release-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/staging/debug/app-staging-debug.apk |
    Out-File -Encoding utf8 `
      (Join-Path $FinalDir 'staging-debug-manifest.xml')

& $ApkAnalyzer manifest print `
  app/build/outputs/apk/staging/release/app-staging-release.apk |
    Out-File -Encoding utf8 `
      (Join-Path $FinalDir 'staging-release-manifest.xml')
```

Compare:

```powershell
$ManifestFiles = @(
    'development-debug-manifest.xml'
    'development-release-manifest.xml'
    'staging-debug-manifest.xml'
    'staging-release-manifest.xml'
)

foreach ($name in $ManifestFiles) {
    $baseline = Get-Content (Join-Path $BaselineDir $name)
    $final = Get-Content (Join-Path $FinalDir $name)

    $difference = Compare-Object $baseline $final

    if ($difference) {
        Write-Host "Manifest difference detected: $name"
        $difference | Format-Table -AutoSize
        throw "Merged APK manifest changed during Gate 1: $name"
    }
}
```

Required: no differences.

A difference is a review blocker; do not dismiss it as harmless without source-level explanation.

The extraction is expected to preserve:

```text
application class
MainActivity
launcher intent filter
OAuth callback scheme
gurbakir.com App Links
App Link paths
autoVerify state
Firebase metadata
permissions
application theme
```

## 6.7 Existing app manifest/resource diff

Run:

```powershell
git diff $Gate1BaseSha -- `
  app/src/main/AndroidManifest.xml `
  app/src/main/res
```

Expected: no changes to existing app manifest/resource content.

Core resources are additive elsewhere.

## 6.8 Resource completeness

Re-run:

```powershell
$CoreAllResources =
    Get-AndroidResourceReferences @(
        'mobile-core/src/main',
        'mobile-core/src/test',
        'mobile-core/src/androidTest'
    )

$CoreAllResources |
    Sort-Object SourceRoot, Type, Name, File |
    Format-Table -AutoSize
```

Verify no missing referenced resource.

Verify:

```powershell
Select-String `
  -Path 'mobile-core/src/main/res/values/*.xml' `
  -Pattern 'name="order_list_added"','name="wishlist_product_count"'

Select-String `
  -Path 'mobile-core/src/main/res/values-en/*.xml' `
  -Pattern 'name="order_list_added"','name="wishlist_product_count"'
```

Both names must appear in both locale sets.

## 6.9 Room schema continuity

Re-run the Task 3.4 comparison.

Required:

```text
zero relative-path/SHA differences
database version remains 2
schema 1 retained
schema 2 retained
WISHLIST_MIGRATION_1_2 retained
```

Then run migration instrumentation from core.

## 6.10 Physical DB identity

App instrumentation must prove:

```text
gurbakir-local.db
```

remains the actual filename.

No:

```text
mobile-core.db
commerce.db
application-ID-derived replacement
```

is allowed.

## 6.11 Customer Account/cart persistence

Reverify source:

```powershell
git grep -n -F 'gurbakir_secure_customer_session_' -- account app
git grep -n -F 'gurbakir.customer.session.' -- account app

git grep -n -F 'gurbakir_secure_cart_' -- storefront app
git grep -n -F 'gurbakir.cart.' -- storefront app
```

Expected existing patterns remain.

No rename is part of Gate 1.

## 6.12 Address territory verification

Run:

```powershell
git grep -n -F '"TR"' -- `
  mobile-core/src/main/kotlin/com/gurbakir/mobile/address

git grep -n -F 'SUPPORTED_ADDRESS_TERRITORY' -- mobile-core
```

Expected: no matches.

Verify app exact binding:

```powershell
git grep -n -F 'AddressTerritoryPolicy("TR")' -- app/src/main
```

Expected: the concrete Gürbakır policy definition.

Run all Address unit/UI tests, including synthetic `"US"` policy proof.

## 6.13 Update policy verification

Required observations:

```text
CancellationException propagates
non-cancellation provider exception uses cache/default fallback
Fetched maps all snapshot fields and timestamp
LocalDefaults maps to cache/default path
stored key names unchanged
stored source marker unchanged
storage schema remains 1
Firebase adapter remains app-owned
mobile-core has no Firebase import/dependency
```

Run the core and app update-policy focused tests before relying solely on aggregate test tasks.

## 6.14 Firebase continuity

Verify app build still owns:

```text
developmentDebug   com.gurbakir.mobile.dev.debug
developmentRelease com.gurbakir.mobile.dev
stagingDebug       com.gurbakir.mobile.staging.debug
stagingRelease     com.gurbakir.mobile.staging
```

and complete four-file Firebase configuration behavior.

`FirebaseRuntimeTest` remains app-owned and passes in the app instrumentation lane.

## 6.15 Navigation and App Links

Application integration proof must verify:

```text
https://gurbakir.com/collections/...
https://gurbakir.com/apps/mobile/products/...
https://gurbakir.com/apps/mobile/orders/...
```

continue resolving to the same typed route families.

Malformed/cross-domain route recovery remains unchanged.

## 6.16 Localization

Run core resource tests and app:

```text
LocalizationResourceTest
```

Required:

```text
Turkish/default resources unchanged
English overrides unchanged
plural formatting unchanged
pseudo-locale expansion works
RTL pseudo locale works
```

## 6.17 Managed devices

Run:

```powershell
.\gradlew.bat `
  :mobile-core:ciApi30DebugAndroidTest `
  :app:ciApi30DevelopmentDebugAndroidTest `
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Required: PASS.

## 6.17A Optional physical-device upgrade-in-place preservation evidence

This is **supplemental evidence**, not a Gate 1 merge blocker when the required automated Room/persistence, JVM, build, manifest and managed-device evidence has passed.

Run this only when an intentionally selected physical Android test device is connected and the executor actually has ADB access.

Do not claim this evidence if it was not performed.

### Preconditions

Use the baseline Development Debug APK captured in Task 0:

```text
$BaselineDir/app-development-debug-baseline.apk
```

Use the final Gate 1 Development Debug APK:

```text
app/build/outputs/apk/development/debug/app-development-debug.apk
```

Package:

```text
com.gurbakir.mobile.dev.debug
```

Do not clear application data between the baseline and upgraded installations.

Do not use:

```text
adb shell pm clear
uninstall/reinstall
```

because that would destroy the persistence-preservation evidence.

### Baseline installation

- [ ] Install the captured pre-Gate-1 APK:

```powershell
adb install -r `
  (Join-Path $BaselineDir 'app-development-debug-baseline.apk')
```

Required: install succeeds.

- [ ] Launch the baseline app:

```powershell
adb shell am start -W `
  -n com.gurbakir.mobile.dev.debug/com.gurbakir.mobile.MainActivity
```

- [ ] Create only safe local preservation state through the existing app UI:

```text
Search:
- create at least one local search-history entry

Wishlist:
- save at least one product to the local wishlist
```

Do not perform:

```text
checkout/payment
order mutation
Customer Account mutation
address/profile mutation
Firebase mutation
Shopify Admin mutation
production/external provisioning
```

Ordinary read-only Storefront loading needed to reach the Search/Wishlist UI is acceptable.

- [ ] Confirm the local Room database exists without reading private record contents:

```powershell
adb shell run-as com.gurbakir.mobile.dev.debug `
  ls databases
```

Expected listing includes:

```text
gurbakir-local.db
```

If `run-as` is unavailable on the selected build/device, record that limitation and rely on the UI persistence check below; do not weaken device security or copy private database contents.

### Upgrade to final Gate 1 APK

- [ ] Force-stop without clearing data:

```powershell
adb shell am force-stop com.gurbakir.mobile.dev.debug
```

- [ ] Upgrade in place:

```powershell
adb install -r `
  'app/build/outputs/apk/development/debug/app-development-debug.apk'
```

Required: install succeeds without uninstalling the existing package.

- [ ] Relaunch:

```powershell
adb shell am start -W `
  -n com.gurbakir.mobile.dev.debug/com.gurbakir.mobile.MainActivity
```

Required:

```text
MainActivity launches successfully
no startup crash
primary navigation remains usable
```

### Preservation checks

- [ ] Verify through the application UI that the local Search history created before the upgrade remains present.

- [ ] Verify through the application UI that the local Wishlist state created before the upgrade remains present.

- [ ] Re-check database filename when `run-as` is available:

```powershell
adb shell run-as com.gurbakir.mobile.dev.debug `
  ls databases
```

Expected:

```text
gurbakir-local.db
```

- [ ] Record device evidence explicitly:

```text
device model
Android/API level
baseline APK identity
final APK identity
adb install -r result
launch result
Search persistence result
Wishlist persistence result
database filename observation if available
anything not observed
```

This physical-device pass strengthens the claim that the module/source relocation did not become an accidental install-time persistence migration. It does not replace the required automated Room migration/schema and app instrumentation evidence.

## 6.18 Final dependency-lock inspection

Run:

```powershell
git diff $Gate1BaseSha -- `
  '*gradle.lockfile' `
  gradle/verification-metadata.xml
```

Every change must be attributable to module ownership movement.

No dependency-version upgrade belongs in Gate 1.

## 6.19 Structural dependency/brand scans

Run:

```powershell
git grep -n 'projects.app' -- `
  mobile-core foundation storefront account checkout firebase

git grep -n 'projects.firebase' -- mobile-core

git grep -n 'import com.gurbakir.firebase' -- mobile-core/src/main

git grep -n 'BuildConfig' -- mobile-core/src/main

git grep -n 'GurbakirBrand' -- mobile-core/src/main

git grep -n 'GurbakirHomeConfiguration' -- mobile-core/src/main

git grep -n 'GurbakirCatalogConfiguration' -- mobile-core/src/main

git grep -n 'gurbakir.com' -- mobile-core/src/main

git grep -n 'gurbakir-local.db' -- mobile-core/src/main

git grep -n 'SUPPORTED_ADDRESS_TERRITORY' -- mobile-core/src/main
```

All must produce no matches.

## 6.20 Final changed-path review

Run:

```powershell
git diff --name-status $Gate1BaseSha..HEAD
git diff --stat $Gate1BaseSha..HEAD
```

Review every path against the ownership map.

There must be no:

```text
production identity invention
production signing
production Firebase files
production OAuth registration
second app module
synthetic brand
Shopify content schema
Gate 2 implementation
```

## 6.21 Portability validator

After intended Gate 1 commits and a clean Gate 1 worktree:

```powershell
pwsh ./scripts/Test-RepositoryPortability.ps1 -RequireCleanWorktree
```

Required: PASS.

---

# Task 7: Update Canonical Documentation

Create/preserve:

```text
docs/multi-brand/plans/GATE-1-MOBILE-CORE-EXTRACTION-IMPLEMENTATION-PLAN.md
```

Modify:

```text
AGENTS.md
README.md
docs/README.md
docs/architecture/MULTI-BRAND-ARCHITECTURE.md
docs/architecture/BRAND-BOUNDARIES.md
docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md
docs/architecture/BRAND-ONBOARDING.md
docs/multi-brand/README.md
```

Do not modify merely to modernize history:

```text
docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md
docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md
docs/phase2/**
docs/phase3/**
docs/product-quality/**
```

ADR-0004 changes only if actual implementation evidence contradicts its accepted decision. This plan expects no contradiction.

## `AGENTS.md`

Record:

```text
:mobile-core now exists
:app is the Gürbakır application/composition shell
shared modules must not depend on app
mobile-core must not depend on Firebase provider
Gate 2 has not started
```

Update validation command guidance to include core lanes.

## Root `README.md`

Current module list becomes:

```text
:app          Gürbakır application/composition shell
:mobile-core  reusable Android application/features/navigation/local data
:foundation
:storefront
:account
:checkout
:firebase
```

Do not claim second-brand conformance yet.

## `docs/README.md`

Update current-state wording from future/nonexistent `:mobile-core` to actual Gate 1 implementation.

P3-16 status remains unchanged.

## `MULTI-BRAND-ARCHITECTURE.md`

Mark the base:

```text
:app -> :mobile-core
```

boundary as implemented.

Future apps/synthetic brand remain future.

## `BRAND-BOUNDARIES.md`

Change `:mobile-core` from future ownership to current implementation ownership.

Document that concrete:

```text
brand config
provider binding
physical DB filename
environment/market partitions
protected-store construction
```

remain app-side.

## `GURBAKIR-LEGACY-IDENTITIES.md`

Do not change identity values.

Update source provenance:

```text
LocalCommerceDatabase implementation -> mobile-core
Room schemas -> mobile-core/schemas
physical gurbakir-local.db construction -> app SearchModule
```

Record explicitly that Gate 1 changed source ownership, not persisted identity.

## `BRAND-ONBOARDING.md`

Update applicability:

```text
mobile-core exists
additional brand app modules do not
Gate 2 has not started
```

## `docs/multi-brand/README.md`

Record:

```text
Gate 0 complete
Gate 1 complete only after this plan's evidence passes
Gate 2 not started
```

Link this plan.

**Recommended commit:**

```text
docs(multi-brand): record Gate 1 mobile-core boundary
```

## Post-documentation repository close-out

The documentation commit occurs after the main implementation validation matrix, so perform one final repository-level close-out against the complete Gate 1 branch state.

- [ ] Verify the complete committed diff has no whitespace/error-marker problems:

```powershell
git diff --check $Gate1BaseSha..HEAD
```

Required: no output / success.

- [ ] Run the updated portability validator against the final clean worktree:

```powershell
pwsh ./scripts/Test-RepositoryPortability.ps1 -RequireCleanWorktree
```

Required: PASS.

- [ ] Prove the final worktree is clean:

```powershell
git status --short
```

Required: no output.

- [ ] Review the complete Gate 1 path set one last time:

```powershell
git diff --name-status $Gate1BaseSha..HEAD
```

Every path must be attributable to the approved Gate 1 source/resource/DI/Room/test/CI/documentation migration.

Do not proceed to PR handoff if this close-out reveals an unexplained path or a dirty worktree.

---

# 14. Recommended Commit Shape

Use one short-lived Gate 1 branch/worktree and one PR.

Recommended commits:

```text
1. build(mobile-core): add shared Android application module

2. refactor(app): introduce mobile-core composition seams

3. refactor(mobile-core): extract reusable application implementation

4. test(multi-brand): split core and gurbakir application proof

5. ci(multi-brand): enforce mobile-core boundary

6. docs(multi-brand): record Gate 1 mobile-core boundary
```

Each commit must compile/test at the level appropriate to its change before proceeding.

The physical source/resource/DI/Room move remains a single coherent extraction commit.

Do not leave a long-lived state where half the reusable application is in app and half in core merely to produce smaller commits.

---

# 15. Explicit Gate 1 Non-Goals

Gate 1 does not implement:

```text
synthetic/second brand application
brand capability system
Account-disabled composition
configurable primary navigation
Shopify Navigation migration
Home metaobject schema
mobile provisioning tooling
full MarketPolicy
multi-country address UI
tracking-provider framework
StorefrontMediaPolicy gurbakir.com cleanup
Firebase-free second-brand implementation
loyalty/backend provider seams
analytics/notification architecture
production package ID
production signing
Play ownership
production Firebase
production Customer Account registration
production App Links
repository-wide package neutralization
feature-module explosion
build convention plugins
```

The existing `:storefront` media-origin debt is known and remains deferred.

---

# 16. Major Migration Risks and Required Controls

| Risk | Required Gate 1 control |
|---|---|
| Execution begins from stale/local branch state | prove exact `HEAD == origin/main` after fetch |
| User work is overwritten | isolated worktree; no reset/clean/stash of unrelated work |
| App version becomes library version | public app-supplied `CurrentAppVersionCode` |
| Firebase leaks into core | neutral gateway; app Firebase adapter |
| Cancellation behavior changes | explicit propagation test |
| Provider exceptions start crashing | explicit cache/default fallback test |
| Persisted update-policy state breaks | exact old keys/schema/source compatibility tests |
| Home/Catalog merchant data enters core | concrete objects stay app-owned |
| Address still contains hidden `TR` | complete policy flow + structural scan |
| Address UI changes | explicit mapped `isSupported`; UI tests |
| Tracking carrier hosts become shared defaults | required policy constructor + app exact set |
| Legal merchant data moves into core | legal directory/destination remain app-owned |
| `gurbakir-local.db` renamed | physical DB builder remains app |
| Room history changes because module moves | relative-path + SHA equality |
| Search/Wishlist implementations become public APIs | app exposes only DB/partitions; core creates stores/repos/clocks |
| Resource inventory misses plurals | complete mechanical `R.<type>.<name>` scan |
| Resource XML has transitive missing refs | XML `@type/name` scan |
| Core source keeps app `R` | core R import verification |
| Core tests keep app `R` | test/androidTest R verification |
| App visual/resource identity changes | existing app resource tree untouched |
| Hilt graph breaks across modules | immediate core + final app compilation |
| Typed Navigation breaks under R8 | core consumer rule + both release builds |
| Debug evidence needs broad public API | expose only four justified shell/navigation helpers |
| `ProductionTestTags` becomes public accidentally | generic tests move core |
| Scope turns into all multi-brand work | explicit non-goals and stop before Gate 2 |

---

# 17. Final Plan Self-Review

This self-review has been rerun after restoring the original execution detail.

## Architecture consistency

The plan still implements exactly:

```text
focused seam preparation
+
atomic source/resource/DI/Room extraction
```

No architecture redesign was introduced.

## Source ownership consistency

Every current root production Kotlin file has an explicit disposition:

```text
MOVE:
AccountDeletionDestination.kt
AccountFeatureDestinations.kt
CartDestination.kt
ProductionApp.kt

REMAIN:
GurbakirApplication.kt
LegalSupportDestination.kt
MainActivity.kt

CREATE + REMAIN:
GurbakirApp.kt
```

Every current feature directory has an explicit move/remain/exclusion rule.

`brand`, `config`, `legal` remain app.

All reusable feature directories move.

The six concrete seam/provider files remain app after their reusable directories move:

```text
GurbakirHomeConfiguration.kt
GurbakirCatalogConfiguration.kt
GurbakirDeepLinkConfiguration.kt
GurbakirAddressTerritoryPolicy.kt
GurbakirTrackingUrlPolicy.kt
FirebaseUpdatePolicyRemoteGateway.kt
```

## DI consistency

Every current DI module has a disposition:

```text
ApplicationModule       split
CartModule              move core
CatalogModule           move core
CustomerAccountModule   split
FirebaseModule          remain app
LegalModule             remain app
ProductModule           move core
SearchModule            split
StorefrontModule        move core
UpdatePolicyModule      split
WishlistModule          split
```

Search/Wishlist preserve the accepted revised boundary: app supplies only DB/partition composition; core constructs stores/repositories/clocks.

## Address type consistency

Flow is:

```text
app AddressTerritoryPolicy("TR")
       ↓
DefaultAddressController
       ↓
AddressReader / AddressMutationCoordinator
       ↓
CustomerAddress.toAddressContent(policy)
       ↓
AddressContent(isSupported = policy.supports(...))
```

Create/update drafts use:

```text
policy.supportedTerritoryCode
```

There is no remaining reason for `SUPPORTED_ADDRESS_TERRITORY`.

## Update-policy type consistency

Flow is:

```text
:firebase RemoteFeatureFlags
        ↓
:app FirebaseUpdatePolicyRemoteGateway
        ↓
public UpdatePolicyRemoteGateway
        ↓
:mobile-core DefaultUpdatePolicyController
        ↓
internal UpdatePolicyStore
```

`CurrentAppVersionCode` is public because app provides it.

All controller/store implementation types remain internal.

## Resource consistency

The plan inventories:

```text
R.<type>.<name>
```

across:

```text
mobile-core/src/main
mobile-core/src/test
mobile-core/src/androidTest
```

and scans XML transitive references.

Known plural resources are explicitly preserved in default Turkish and English:

```text
order_list_added
wishlist_product_count
```

## Room consistency

Comparison key is:

```text
relative path below schema root + SHA-256
```

not module path.

Thus:

```text
app/schemas/... -> mobile-core/schemas/...
```

is allowed while byte changes fail.

## Gradle consistency

`:mobile-core` uses actual current version-catalog aliases for:

```text
Android library
Compose compiler
serialization
KSP
Hilt
Detekt
Room
desugaring
AndroidX core/activity/browser
Lifecycle
Navigation
Hilt Navigation
coroutines
serialization JSON
OkHttp
Coil
Room
Compose UI/foundation/material3/adaptive navigation
JUnit Jupiter
coroutines-test
Turbine
AndroidX test runner/ext/Espresso
Room testing
Compose UI test/tooling/test manifest
```

It explicitly excludes:

```text
Firebase
Google Services
application plugin
applicationId
environment flavors
signing
app BuildConfig fields
```

## Validation consistency

The plan contains executable commands for:

```text
HEAD == origin/main proof
baseline JVM/build
baseline application IDs
baseline APK manifests
baseline Development Debug APK capture
Room baseline hashes
scaffold build
dependency locks
resource inventory
XML resource dependency scan
core R ownership
schema comparison
structural greps
core compilation
JVM tests
all four app APK builds
final application IDs
final manifest capture/comparison
app resource/manifest diff
managed devices
optional physical-device upgrade preservation
portability validation
documentation changes
post-documentation repository close-out
```

## Placeholder scan

The plan contains no:

```text
TBD
TODO
fill this in later
unknown implementation placeholder
```

Execution-time values are captured programmatically into variables such as:

```text
$OriginMainSha
$Gate1BaseSha
$BaselineDir
$FinalDir
```

rather than represented as unresolved textual placeholders.

No review correction from the accepted revised plan has been removed.

No adjacent compile-time issue discovered during the merge requires changing the selected architecture.

---

# 18. Gate 1 Completion Criteria

Gate 1 is complete only when all are observed:

- execution started from exact then-current `origin/main`;
- execution worktree was clean before edits;
- `:mobile-core` exists as an Android library;
- `:app` depends on `:mobile-core`;
- reusable app implementation executes from core;
- `:app` remains Gürbakır application/composition shell;
- `MainActivity` remains the app entry point;
- `GurbakirApplication` remains Hilt application;
- four current non-production application IDs are exact;
- environment flavors remain `development` and `staging`;
- no production identity is invented;
- manifest/App Links remain identical to baseline;
- OAuth callback behavior remains unchanged;
- Firebase package-registration logic remains app-owned;
- core has no dependency on app;
- core has no dependency on Firebase;
- core has no app `BuildConfig`;
- core has no `GurbakirBrand`;
- core has no concrete `gurbakir.com`;
- concrete Home/Catalog merchant state remains app-owned;
- no `SUPPORTED_ADDRESS_TERRITORY` remains in core;
- no hidden core Address `"TR"` default remains;
- Gürbakır app supplies exact `"TR"`;
- `AddressContent.isSupported`, load/form/mutation/default/draft behavior are all policy-driven;
- Turkey tracking hosts remain app-owned;
- legal/support implementation remains app-owned;
- Search/Wishlist app boundary exposes only DB/partition composition values;
- `gurbakir-local.db` remains exact;
- Room schema remains version 2;
- all retained schema JSON files match baseline by relative path + SHA;
- migration `1 -> 2` passes;
- Customer Account secure preference/alias remain exact;
- cart secure preference/alias remain exact;
- update-policy cancellation propagates;
- non-cancellation provider exceptions use existing cache/default fallback;
- Firebase Fetched mapping passes;
- Firebase LocalDefaults mapping passes;
- update-policy persisted key/schema/source compatibility passes;
- complete core resource inventory is satisfied;
- current plural resources exist in core default and English resources;
- moved core main/test/androidTest code owns the correct core `R`;
- existing app resource/manifest content remains unchanged;
- Hilt graph compiles through final app;
- typed navigation survives both minified release builds;
- all static checks pass;
- all JVM tests pass;
- core managed-device tests pass;
- app Development managed-device tests pass;
- all four Development/Staging APKs build;
- portability validator passes with seven-module architecture;
- CI contains core build/test coverage;
- canonical docs match implemented state;
- P3-16 status is unchanged;
- no second brand exists;
- Gate 2 has not started;
- no production/external system was mutated.

Physical-device upgrade-in-place preservation evidence is supplemental rather than mandatory when all required automated preservation evidence above passes; if run, report it separately and do not claim it if unavailable.

---

# 19. Handoff to Gate 1 Executor

1. Fetch `origin/main` before doing anything else.

2. Use `superpowers:using-git-worktrees` and start the isolated Gate 1 worktree directly from that fetched `origin/main`.

3. Prove exact `HEAD == origin/main` and clean worktree before assigning `$Gate1BaseSha`.

4. Preserve unrelated user work. Never reset, clean or stash it away.

5. Capture the exact pre-refactor package IDs, four APK manifests, baseline Development Debug APK and relative-path Room schema hashes.

6. Scaffold `:mobile-core` using the exact Gradle/plugin/dependency contract in this plan.

7. Introduce the focused seams in `:app` before physical source movement.

8. Complete the Address seam across model mapping, UI semantic state, form/load, mutations and draft creation. Do not leave a core `TR` default.

9. Preserve update-policy cancellation, provider-exception fallback, cache/default behavior and stored key format exactly.

10. Split Search/Wishlist so app exposes only DB/partition composition values and core constructs generic implementation objects.

11. Execute the explicit source ownership map rather than rediscovering module ownership.

12. Execute the exact DI map rather than moving `di/` wholesale.

13. Move Room schema/source ownership without changing persistence identity.

14. Mechanically inventory every `R.<type>.<name>` in moved source, not only strings.

15. Preserve Turkish/default and English resources, including plurals.

16. Scan copied XML for transitive resource dependencies.

17. Apply the cross-module visibility map literally; do not broaden the public API to silence compilation.

18. Compile core and app immediately after structural movement.

19. Move generic tests to core while retaining assembled Gürbakır proof in app.

20. Run the full validation matrix, including both release variants and both managed-device lanes.

21. If a suitable physical Android test device and ADB access are available, run the optional section 6.17A pre-Gate-1 -> post-Gate-1 `adb install -r` preservation pass and report it separately as supplemental evidence; do not block Gate 1 solely because physical-device evidence is unavailable when all mandatory automated preservation checks pass.

22. Compare final application IDs and all four merged manifests against the captured baseline.

23. Compare Room schemas by relative path + SHA-256.

24. Inspect dependency-lock and verification-metadata changes explicitly.

25. Run the updated portability validator on the clean final worktree.

26. Update canonical docs only after implementation evidence establishes the real final state.

27. Perform the post-documentation repository close-out and verify `git diff --check`, portability, clean worktree and complete changed paths.

28. Report the actual execution-base SHA, final commit SHAs, moved files/modules, package IDs, manifest comparison, Room hashes/migration results, JVM/static/build/instrumentation evidence, lockfile changes and portability result.

29. State explicitly anything that was not run or not observed.

30. Stop after Gate 1.

31. Do not create a synthetic or second brand.

32. Do not begin Gate 2.
