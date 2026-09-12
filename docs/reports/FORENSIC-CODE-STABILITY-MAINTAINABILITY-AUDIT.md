# FORENSIC CODE STABILITY & MAINTAINABILITY AUDIT

**Repository:** `private historical repository`
**Audit date:** 2026-08-30
**Mode:** CANONICAL SECOND-PASS, READ-ONLY forensic repository audit
**Default branch:** `main`
**Audited HEAD:** `84e8b53853df216f1af377a41d03d9937a4264ec`
**HEAD subject:** `ci: repair fresh-clone validation (#9)`

> This document is the canonical code-stability and maintainability audit for the audited HEAD and supersedes the preliminary first-pass audit. The prior report was treated as a set of hypotheses, not as authority, and its findings were independently re-verified here.
>
> No source file, branch, commit, pull request, issue, Shopify/Firebase state, customer/account/cart/order/payment state, Remote Config value, signing state, schema, lockfile, or other repository/remote state was modified during the audit.
>
> A temporary local clone was attempted only to improve static inventory coverage, but the execution runtime could not resolve `github.com`; the clone failed before any checkout existed. Therefore all repository evidence below comes from the GitHub connector at the exact audited commit, plus current tracked authority/history documents. No Gradle task was executed in this follow-up.

---

# 1. Executive Summary

## Does the first audit's overall conclusion still hold?

**Mostly, but it was too broad in two places.**

The repository is still not a codebase with widespread production dead code, uncontrolled coroutine use, or an abandoned second runtime architecture. The first audit was correct that the cart/session, Search, Room migration, typed navigation, and production/debug separation contain several strong design decisions.

However, the second pass found a **new P1 Checkout ownership/concurrency risk** that materially weakens the first report's broad positive statement that Checkout session IDs make stale events safe. Session IDs are useful, but they do not solve the fact that a singleton Checkout adapter exposes a `Channel.receiveAsFlow()` to potentially multiple unscoped Checkout controllers/ViewModels. A channel event can be consumed by the wrong controller and then discarded by its session-ID check.

The second pass also proved more disconnected/cleanup surface than the first audit reported:

- `foundation/error/AppFailure.kt` is a **confirmed disconnected production contract**.
- production `firebase/FirebaseContracts.kt` still contains Analytics and notification-route contracts with no production caller after P3-15 moved/removed the corresponding proof/push runtime.
- the production `ApplicationModule` provides `ProjectLogger`, but the only code consumer found is the debug-only `FoundationViewModel`.
- `checkout` declares `api(project(":foundation"))` although its complete two-file production Kotlin surface does not import or expose Foundation.
- the first audit's `foundation` dependency-hygiene finding remains valid and is more concrete after enumerating the entire six-file Foundation production surface.

## What the first audit missed

1. **Checkout event fan-out / DI scope mismatch.**
2. A file-level confirmed dead production contract: `AppFailure`.
3. Proof-era Firebase contracts that remain in `src/main` despite production push/Analytics being absent.
4. A main-source Hilt logger provider used only by debug proof code.
5. An unused inter-module `checkout -> foundation` API dependency.
6. The lifecycle consequence of Wishlist's N-request rehydration: a retained Wishlist back-stack ViewModel continues observing membership and can restart network rehydration after Wishlist is no longer visible.
7. A quantified audit surface: 155 production Kotlin files, 16 debug Kotlin files, 64 JVM/testDebug Kotlin files, 28 Android instrumentation Kotlin files, 24 GraphQL operations, three GraphQL fragments, three Room entities, two DAOs, 16 production route types, 20 drawables, and seven repository scripts.

## Highest-confidence new findings

| ID | Priority | Confidence | Summary |
|---|---:|---|---|
| FUP-001 | P1 | HIGH | Singleton `CheckoutAdapter` channel is collected by potentially multiple unscoped `CheckoutController`/`CheckoutViewModel` instances; a Checkout SDK event can be consumed and discarded by the wrong controller. |
| FUP-002 | P3 | CONFIRMED | `foundation/error/AppFailure.kt` has no runtime/test/framework consumer. |
| FUP-003 | P3 | HIGH | Analytics and notification-route contracts remain in `firebase/src/main` with no production consumer after P3-15 removed/moved the proof/push runtime. |
| FUP-004 | P3 | HIGH | `ApplicationModule.provideProjectLogger()` is in the production Hilt graph, but the only code consumer found is debug-only `FoundationViewModel`. |
| FUP-005 | P3 | CONFIRMED | `checkout`'s `api(project(":foundation"))` dependency is unused by its complete production source surface. |
| FUP-006 | P2 | HIGH | Wishlist membership changes can trigger full N-request rehydration from a retained, off-screen Wishlist ViewModel. This amplifies original AUD-003. |

## Areas now more strongly proven clean

- All 14 Storefront GraphQL operations are accounted for by production gateways/pagers.
- All 10 Customer Account GraphQL operations are accounted for by production account/profile/address/order gateways.
- Cart paging has explicit page, cursor, duplicate-line, and cart-changed-during-paging guards.
- Search uses both cancellation and generation fencing.
- Production manifest contains one exported app Activity and three bounded owned HTTPS deep-link families; proof Activities are debug-only.
- Room DAO methods are all connected to their stores/repositories; no orphan DAO query was found.
- Room migration 1 -> 2 is explicit and tested.
- Customer Account and Storefront Apollo executors preserve coroutine cancellation and apply per-request timeouts.
- Production push/Analytics/Crashlytics runtime remains absent by current P3-15 boundary; debug Messaging/proof wiring is source-set separated.
- No production `GlobalScope`, arbitrary long-lived `CoroutineScope(...)`, `Thread.sleep`, unbounded retry loop, or destructive Room fallback was identified in the inspected surface.

---

# 2. Current Baseline

## Repository state

| Item | Current value |
|---|---|
| Repository | `private historical repository` |
| Default branch | `main` |
| HEAD | `84e8b53853df216f1af377a41d03d9937a4264ec` |
| Subject | `ci: repair fresh-clone validation (#9)` |
| HEAD date | 2026-08-30 |
| P3-15 status | COMPLETE |
| P3-16 | NOT STARTED / intentionally deferred production-release boundary |

No newer HEAD was found during the follow-up.

## Modules

- `:app`
- `:foundation`
- `:storefront`
- `:account`
- `:checkout`
- `:firebase`

## App variants

Dimension: `environment`

Flavors:

- `development`
- `staging`

Build types:

- `debug`
- `release`

There is no final production flavor/build boundary. Current authority explicitly leaves production identity, signing, Play governance, final production Firebase/Shopify configuration and related release work to P3-16.

## Source sets observed

| Module | Source sets |
|---|---|
| app | main, debug, test, testDebug, androidTest |
| foundation | main, test |
| storefront | main, graphql, test, androidTest |
| account | main, graphql, test, androidTest |
| checkout | main, test |
| firebase | main, debug, test |

No Java production source was found in the inspected trees.

## Documentation authority

Current authority order used:

1. current source/build/manifest/CI at audited HEAD;
2. `AGENTS.md`;
3. `docs/README.md`;
4. `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`;
5. current P3-14/P3-15 handoffs where they explain intentional retention or deferred behavior;
6. older Phase 2/Phase 3/Product Quality/reference evidence as historical evidence only.

## CI

Current workflow: `.github/workflows/android-foundation.yml`.

It performs:

- checkout;
- secret scan;
- JDK/Android SDK setup;
- Spotless check;
- Detekt;
- Lint;
- JVM/unit tests;
- development/staging debug/release app assembly;
- development/staging Android-test APK assembly;
- repository portability validation.

It does **not** execute connected/emulator instrumentation tests.

## Generated vs tracked boundaries

Tracked generated-contract inputs/evidence include:

- `.graphql` operations/fragments;
- GraphQL schemas;
- Room exported schemas;
- dependency locks.

Generated compiler/Apollo/Room/Hilt outputs are build outputs, not source ownership.

---

# 3. Previous Audit Verification

| Original ID | Original conclusion | Follow-up verdict | Follow-up evidence | Changed priority? |
|---|---|---|---|---|
| AUD-001 | CI assembles but does not execute instrumentation tests. | **VERIFIED AS WRITTEN** | Workflow still has Android-test assembly only. App has 25 androidTest Kotlin files; storefront 1; account 2. | No — P1 remains appropriate. |
| AUD-002 | Cart mutation path performs redundant pre-mutation reconciliation. | **VERIFIED AS WRITTEN** | `DefaultCartRepository.add/update/remove` call `operations.restore()`; `CoordinatedCartOperations.add/update/remove` then call `sessionCoordinator.restore()` and `CartCoordinator.restoreAuthenticated()`/`detach()` before the actual mutation. | No — P2. |
| AUD-003 | Wishlist performs sequential N-request full Product Detail rehydration. | **VERIFIED BUT SCOPE IS DIFFERENT** | `DefaultWishlistRepository.load()` maps stored entries sequentially through `StorefrontProductGateway.loadProductDetail`; `WishlistViewModel` observes membership for its whole ViewModel lifetime and calls `refresh()` on every membership emission. | P2 remains; lifecycle amplification is stronger than first report. |
| AUD-004 | `ProductionApp.kt` is a cross-feature composition-root hotspot. | **VERIFIED AS WRITTEN** | It still owns root shell, route declarations, destination adapters, ViewModel/effect wiring, deep-link destinations, back-stack helpers, secure-order window policy and recovery. | No — P2. |
| AUD-005 | Product Detail has per-call timeout/page bound but no aggregate deadline. | **NEEDS RUNTIME EVIDENCE** | Static fact is verified: up to 16 sequential detail-page calls, each independently timeout-bounded. Material user impact still depends on real page counts/latency. | Keep P2 hypothesis; do not call runtime defect. |
| AUD-006 | Direct Order deep link + terminal session reset + Back behavior not proven together. | **VERIFIED BUT SCOPE IS DIFFERENT** | P3-15 documents a real device signed-out Order deep-link recovery to Account. Automated suite still tests owned order deep link and terminal reset separately; Back-after-terminal-reset remains uncharacterized. | P2 test gap remains, but “signed-out deep-link recovery unproven” is no longer accurate. |
| AUD-007 | Shared Search+Wishlist DB naming/ownership has drifted. | **VERIFIED AS WRITTEN** | `SearchHistoryDatabase` contains SearchHistoryEntity, SearchHistorySettingEntity and WishlistEntity; SearchModule owns DB provider while WishlistModule imports it. Physical DB/schema should stay stable. | No — P3. |
| AUD-008 | Foundation exposes likely unused coroutine/serialization build surface. | **VERIFIED BUT SCOPE IS DIFFERENT** | Entire Foundation production source is six Kotlin files; none needs kotlinx-coroutines or kotlinx-serialization. `androidx.core.ktx` also has no identified Foundation-source use. | P3; scope is broader/more concrete. |
| AUD-009 | Unused direct JUnit4 catalog alias. | **VERIFIED AS WRITTEN** | No `libs.junit4` use was found; AndroidX JUnit/Compose JUnit4 remain separate live aliases and JUnit4 may remain transitive in locks. | No — P3. |
| AUD-010 | Product Detail screen file carries too many UI/platform responsibilities. | **VERIFIED AS WRITTEN** | File still owns compact/expanded layouts, gallery, media dialog, focus restoration, system-bar/window behavior and purchase integration under `TooManyFunctions`/`LongMethod`/`LongParameterList`/`MagicNumber` suppression. | No — P3. |

---

# 4. Audit Coverage Ledger

## Source inventory

| Area | Total candidates established | Inspected/accounted | Findings | Clean / intentional | Unknown |
|---|---:|---:|---:|---:|---:|
| Production Kotlin files | 155 | 155 by module/package inventory; critical owners inspected at symbol level | several | majority | 0 file groups unaccounted |
| Debug Kotlin files | 16 | 16 by source-set inventory | 0 production bugs | 16 debug/evidence | 0 |
| JVM/testDebug Kotlin files | 64 | 64 by tree inventory; critical behavior tests mapped | AUD-001 consequence | majority intentional | individual assertion quality not re-executed |
| Android-test Kotlin files | 28 | 28 by tree inventory; critical classes mapped | AUD-001 | tests themselves are valuable | none executed in current CI |
| Production route types | 16 | 16 | AUD-006 | 16 registered | combined Back scenario unknown |
| Production Hilt module files | 10 | 10 | FUP-001/FUP-004 | remaining providers/bindings connected | compile-removal tests not run |
| Debug Hilt module files | 1 | 1 | 0 | debug-only ProofModule | 0 |
| Production Hilt ViewModels | 18 | 18 by feature ownership | FUP-001/FUP-006 | remaining owners reachable | dynamic multiple-entry Checkout behavior not device-reproduced |
| Storefront GraphQL operations | 14 | 14 | AUD-002/AUD-003/AUD-005 path effects | 14 have production gateway/pager caller | 0 orphan ops found |
| Storefront GraphQL fragments | 3 | 3 | 0 | active mapping inputs | 0 |
| Customer Account GraphQL operations | 10 | 10 | 0 new | all mapped to production gateways | 0 orphan ops found |
| Room entities | 3 | 3 | AUD-007 naming | all live | 0 |
| Room DAO interfaces | 2 | 2 | 0 | all DAO methods connected | 0 |
| Room DB classes | 1 | 1 | AUD-007 | live | 0 |
| Room migrations | 1 explicit app migration | 1 | 0 | intentionally retained | 0 |
| Main manifest app components | 1 app Activity | 1 | 0 | clean | merged dependency manifest not regenerated |
| Debug manifest app components | 2 evidence Activities | 2 | 0 | intentional debug | 0 |
| Main drawables | 20 | 20 inventoried; production reference search performed | no unused resource proven | most have direct production references | exact AAPT reachability not rerun |
| Base strings | 525 keys per current P3-15 acceptance record | key set historically/currently verified there; static usage sampled/searchable | proof-copy retained | intentional locale parity | complete fresh AAPT unused-string graph unavailable |
| English strings | 525 keys per P3-15 record | parity documented at current implementation line | 0 locale mismatch reported | intentional | fresh parser count not locally rerun |
| XML resources | 1 | 1 | 0 | manifest-referenced data extraction rules | 0 |
| Repository scripts | 7 | 7 inventoried; caller/document role classified | no deletion finding | CI/manual/historical roles | not every script body re-executed |
| Direct module build files | 6 + root/catalog | all read | FUP-005 + AUD-008/009 | remaining major edges have source users | no compile-removal experiment |
| ProGuard app rules | 1 | 1 | 0 | narrow route/annotation support | R8 not rerun |
| Current CI workflows | 1 | 1 | AUD-001 | otherwise broad | device execution absent |

## Production Kotlin count by module

| Module | Production Kotlin |
|---|---:|
| app | 113 |
| foundation | 6 |
| storefront | 19 |
| account | 14 |
| checkout | 2 |
| firebase | 1 |
| **Total** | **155** |

Additional generated-contract inputs:

- Storefront: 14 `.graphql` operations + 3 fragments + schema.
- Account: 10 `.graphql` operations + schema.

---

# 5. Repository Runtime Classification

## `app/src/main` — ACTIVE RUNTIME / FRAMEWORK / SUPPORT

113 Kotlin files grouped under:

- root: application, activity, production composition/navigation destinations;
- `account`;
- `accountdeletion`;
- `address`;
- `brand`;
- `cart`;
- `catalog`;
- `checkout`;
- `config`;
- `di`;
- `home`;
- `legal`;
- `navigation`;
- `order`;
- `product`;
- `profile`;
- `search`;
- `ui`;
- `update`;
- `wishlist`.

No production feature package was found without a route/composition/controller/repository connection.

## `foundation/src/main`

| File | Classification |
|---|---|
| `config/AppConfiguration.kt` | ACTIVE RUNTIME SUPPORT |
| `config/BrandConfiguration.kt` | ACTIVE RUNTIME SUPPORT |
| `error/AppFailure.kt` | **CONFIRMED DISCONNECTED** |
| `logging/ProjectLogger.kt` | ACTIVE DEBUG SUPPORT; no production consumer found |
| `navigation/ExternalRoutePolicy.kt` | ACTIVE RUNTIME SUPPORT |
| `ui/CommerceTheme.kt` | ACTIVE RUNTIME |

## `storefront/src/main`

All 19 Kotlin files classify ACTIVE RUNTIME / ACTIVE RUNTIME SUPPORT:

- Apollo Storefront aggregate gateway;
- catalog/search/product gateways;
- cart paging/mapping/validation;
- cart coordinator/persistence;
- Storefront contracts;
- sensitive value wrappers.

No disconnected Storefront Kotlin file was established.

## `account/src/main`

All 14 Kotlin files classify ACTIVE RUNTIME / ACTIVE RUNTIME SUPPORT / FRAMEWORK SUPPORT:

- Account/Profile/Address/Order gateways;
- OAuth authorization/browser/discovery/logout/token;
- PKCE/transaction;
- Keystore session store;
- session coordinator/contracts.

No orphan Account production file was established.

## `checkout/src/main`

- `CheckoutAdapter.kt`: ACTIVE RUNTIME.
- `OfficialCheckoutKitClient.kt`: ACTIVE RUNTIME.

`preload()` is not a normal production flow caller at current HEAD, but it remains debug-proof/compatibility surface and is explicitly retained by P3-14; it is not called dead.

## `firebase/src/main`

`FirebaseContracts.kt` is mixed:

- Remote Config/update-policy contracts: ACTIVE RUNTIME.
- Analytics reporter/event contracts: HIGH-CONFIDENCE DISCONNECTED from production.
- Notification route/payload parser contracts: TEST/HISTORICAL/COMPATIBILITY only at current HEAD; no production consumer.

This mixed responsibility is FUP-003.

## Debug

`app/src/debug` and `firebase/src/debug` classify DEBUG ONLY / FORENSIC EVIDENCE. P3-15 deliberately moved proof code out of main and release inspection recorded it absent from minified release.

## Tests

`test`, `testDebug`, `androidTest` classify TEST ONLY.

## Historical / evidence

- the private forensic corpus: FORENSIC EVIDENCE.
- older Phase 2/Preparation/P3 handoffs: HISTORICAL SUPPORT.
- exported Room schemas: MIGRATION / COMPATIBILITY.
- older validators/provisioners: ACTIVE MANUAL, HISTORICAL SUPPORT, or MUTATION-CAPABLE SUPPORT depending script.

---

# 6. Navigation Reachability Matrix

| Route | Registered | Main source(s) | Deep link | Auth/private | Session/recovery | VM owner | Test status |
|---|---|---|---|---|---|---|---|
| `HomeRoute` | yes | app start, primary nav, recovery | no | public | fallback home | HomeViewModel | navigation/android tests exist; CI compile only |
| `CategoriesRoute` | yes | primary nav, Home | no | public | normal back | CategoriesViewModel | route test |
| `CollectionRoute(handle)` | yes | Categories/Home | `https://gurbakir.com/collections/...` | public | invalid result handled in screen/repo | CollectionViewModel | owned deep-link test |
| `SearchRoute` | yes | primary nav, Home, Account | no | public/local | saved query state | SearchViewModel | screen/JVM tests |
| `WishlistRoute` | yes | primary nav, Home/Account | no | local | storage unavailable state | WishlistViewModel + membership VM | screen/repo tests |
| `AccountRoute` | yes | primary nav, terminal resets | no | public entry/auth host | terminal destination | AccountViewModel | route/session tests |
| `AccountDeletionRoute` | yes | Account | no | authenticated flow | ReturnToAccount effect | AccountDeletionViewModel | JVM/android tests |
| `ProfileRoute` | yes | Account | no | authenticated/private | ReturnToAccount effect | ProfileViewModel | JVM/android tests |
| `AddressListRoute` | yes | Account | no | authenticated/private | ReturnToAccount effect | AddressListViewModel | JVM/android tests |
| `AddressFormRoute` | yes | Address List | no | authenticated/private | Saved/ReturnToAccount effects | AddressFormViewModel | JVM/android tests |
| `OrderListRoute` | yes | Account | no | authenticated/private | clear on stop; ReturnToAccount | OrderListViewModel | JVM/android tests |
| `OrderDetailRoute` | yes | Order List | owned HTTPS | authenticated/private | route-id validation; session reset | OrderDetailViewModel | deep link tested; combined Back gap AUD-006 |
| `CartRoute` | yes | Home/Product/Account | no | public with ownership restrictions | server/ownership states | CartViewModel + CheckoutViewModel | JVM/android tests; multi-entry Checkout gap FUP-001 |
| `LegalSupportRoute` | yes | Home/Account/recovery/update/order support | no | public | always-public escape | LegalSupportViewModel | JVM/android tests |
| `ProductRoute` | yes | Home/Catalog/Search/Wishlist/Cart | owned HTTPS | public | invalid IDs -> recovery | ProductDetailViewModel | route/screen tests |
| `RouteRecoveryRoute` | yes | invalid typed product/order/navigation IDs | no | public | Home + Legal Support escape | none | restoration/recovery tests |

### Reachability conclusions

- No navigation call to an unregistered production route was found.
- No registered route lacked an intentional source.
- All three manifest deep-link families match typed NavHost declarations.
- Private Order routes have explicit signed-out/terminal recovery.
- P3-15 manually exercised signed-out direct Order deep-link recovery.
- Remaining gap: direct Order deep link -> terminal expiry/reset -> Back is not one automated characterization.

### Back-stack concern relevant to FUP-001

`CartRoute` is reached from multiple destinations using ordinary `navigate(CartRoute)`, not one globally singleTop cart owner. Navigation Compose can therefore hold multiple Cart back-stack entries/ViewModels. This matters because each Cart entry creates a CheckoutViewModel while the Checkout adapter event source is singleton.

---

# 7. Hilt / DI Reachability Matrix

## Production module files

| Module file | Key bindings/providers | Scope | Consumer status |
|---|---|---|---|
| `ApplicationModule.kt` | AppConfiguration | Singleton | live |
| | ProjectLogger | Singleton | **debug consumer only** — FUP-004 |
| | CheckoutAdapter | Singleton | live |
| | HomeContentRepository | unscoped | live/stateless |
| | CartSessionStore | Singleton | live |
| | CartCoordinator | Singleton | live |
| `CartModule.kt` | CartOperations | Singleton | live |
| | CartRepository | Singleton | live |
| | CheckoutCartCompleter | Singleton | live |
| `CatalogModule.kt` | CatalogRepository | unscoped | live/stateless |
| `CustomerAccountModule.kt` | session/discovery/token/logout/auth/gateways | mostly Singleton | live |
| `CustomerOrderModule` in same file | CustomerOrderGateway | Singleton | live |
| `ProductionAccountModule` in same file | Account/Profile/Address/Order/Deletion controllers | Singleton | live |
| `FirebaseModule.kt` | RemoteFeatureFlags | Singleton | live |
| `LegalModule.kt` | LegalSupportRepository | Singleton | live |
| `ProductModule.kt` | ProductDetailRepository | unscoped | live/stateless |
| `SearchModule.kt` | ProductSearchRepository | unscoped | live/stateless |
| | Room DB | Singleton | live |
| | SearchHistoryRepository | Singleton | live |
| `StorefrontModule.kt` | StorefrontGatewaySet | Singleton | live |
| | gateway interface aliases | unscoped providers returning singleton-owned members | live |
| `UpdatePolicyModule.kt` | policy store/controller | Singleton | live |
| | version/clock values | unscoped value objects | live |
| `WishlistModule.kt` | WishlistRepository | Singleton | live |

Debug:

- `app/src/debug/.../di/ProofModule.kt`: DEBUG ONLY.

## Scope finding

`CheckoutController` is stateful and `@Inject`-constructible but unscoped. Each `CheckoutViewModel` receives a separate controller. All controllers share the singleton CheckoutAdapter. That scope combination is the root of FUP-001.

## DI clean areas

- Cart mutable state is singleton-owned.
- Session coordinator is singleton-owned.
- Update-policy controller/store are singleton-owned.
- Account controllers are singleton-owned.
- Storefront gateway set shares a single Apollo client/aggregate owner.
- Debug proof module is not in `src/main`.

---

# 8. GraphQL / Network Operation Matrix

## Storefront — all 14 operations accounted for

| Operation | Production owner/caller | Flow | Policy |
|---|---|---|---|
| `ShopSummary` | ApolloStorefrontGateway | foundation/Home proof/data | per-call timeout |
| `CatalogPage` | ApolloStorefrontGateway | catalog baseline | cursor page |
| `HomeCollection` | ApolloStorefrontGateway/Home repo | Home | independent Home async |
| `HomeProduct` | ApolloStorefrontGateway/Home repo | Home | independent Home async |
| `CollectionCatalogPage` | ApolloStorefrontCatalogGateway | Categories/Collection | cursor + sort/filter |
| `ProductSearchPage` | ApolloStorefrontSearchGateway | Search | cursor; ViewModel generation fence |
| `ProductDetailPage` | ApolloStorefrontProductGateway | Product + Wishlist rehydration | max 16 pages; per-call timeout |
| `CartCreate` | ApolloStorefrontGateway | first add | mutation ambiguity handled above |
| `CartById` | StorefrontCartPager | cart restore | page consistency |
| `CartLinesPage` | StorefrontCartPager | cart line completion | max two pages |
| `CartLinesAdd` | ApolloStorefrontGateway | add | serialized |
| `CartLinesUpdate` | ApolloStorefrontGateway | quantity update | serialized |
| `CartLinesRemove` | ApolloStorefrontGateway | remove | serialized |
| `CartBuyerIdentityUpdate` | ApolloStorefrontGateway | auth attach/detach | ownership state machine |

Fragments:

- `CartLineFields`: live.
- `CartSnapshotFields`: live.
- `HomeImageFields`: live.

No orphan Storefront operation/fragment was found.

## Customer Account — all 10 operations accounted for

| Operation | Production gateway | User flow |
|---|---|---|
| `CustomerIdentity` | CustomerAccountGateway | Account |
| `CustomerProfile` | CustomerProfileGateway | Profile load |
| `CustomerProfileUpdate` | CustomerProfileGateway | Profile save |
| `CustomerAddresses` | CustomerAddressGateway | Address list |
| `CustomerAddressCreate` | CustomerAddressGateway | Address create |
| `CustomerAddressUpdate` | CustomerAddressGateway | Address edit |
| `CustomerAddressDelete` | CustomerAddressGateway | Delete |
| `CustomerAddressSetDefault` | CustomerAddressGateway | Default address |
| `CustomerOrders` | CustomerOrderGateway | order paging |
| `CustomerOrderDetail` | CustomerOrderGateway | order detail |

## Network policy

Confirmed good:

- Storefront Apollo calls have per-request timeout and cancellation propagation.
- Customer Account Apollo executor has per-request timeout and rethrows CancellationException.
- discovery uses cancellable OkHttp bridge and bounded document size.
- Customer Account discovery is singleton/cached, so separate gateways do not repeatedly fetch discovery in normal process lifetime.
- cart pager explicitly checks page limit, missing cursor, duplicate lines and cart mutation during paging.
- Order detail rejects incomplete bounded connection results instead of pretending partial data is complete.

Material efficiency findings:

- AUD-002: repeated cart/session reconciliation.
- AUD-003/FUP-006: Wishlist Product Detail N-request fan-out.
- AUD-005: no aggregate Product Detail deadline.

---

# 9. Room / Persistence Matrix

Database: `gurbakir-local.db`
Class: `SearchHistoryDatabase`
Version: 2

## Entity / DAO matrix

| Entity | Key/partition | DAO operations | Caller | Transaction/consistency | Tests |
|---|---|---|---|---|---|
| SearchHistoryEntity | environment + market + normalized query identity | load recent, insert, expiry delete, trim, delete one, clear | RoomSearchHistoryStore | record/load maintenance grouped in DAO transaction functions | JVM + Android persistence tests |
| SearchHistorySettingEntity | environment + market | load/store enabled setting | RoomSearchHistoryStore | setting update explicit | JVM/android tests |
| WishlistEntity | environment + market + product ID; index for ordered load | observe, load, insert, delete, clear | RoomWishlistStore | single-row operations; DB uniqueness provides idempotence | JVM + Android + migration tests |

## Migration

`WISHLIST_MIGRATION_1_2`:

- preserves Search History;
- creates Wishlist table/index;
- has `MigrationTestHelper` coverage.

No destructive fallback was found.

## Dead-query analysis

All DAO methods are referenced by their respective Store classes. No “write never read” or “read never written” Room path was established.

## Naming drift

The physical database now owns Search + Wishlist but is named `SearchHistoryDatabase`, and the provider remains in `SearchModule`. This is AUD-007.

**Do not rename physical DB/table identifiers as cosmetic cleanup.** A neutral Kotlin package/class/provider rename can be considered when the persistence area is next changed.

---

# 10. Compose / UI Reachability Inventory

## Production ownership

Production Compose is concentrated in app feature packages:

- Home;
- Categories/Collection/catalog cards;
- Search;
- Product;
- Wishlist;
- Cart/Checkout section;
- Account;
- Profile;
- Addresses;
- Orders;
- Account deletion;
- Legal/support;
- update-policy banner;
- common destination/price/state/navigation components.

GitHub code search accounts for all production files containing `@Composable`; debug proof composables are separately under `src/debug`.

No composable was classified dead solely because it lacked a simple text call: destination registration, callbacks, preview/test references and source-set ownership were considered.

## Key maintainability hotspots

- `ProductionApp.kt` — AUD-004.
- `ProductDetailScreen.kt` — AUD-010.
- `OrderScreens.kt` — complexity suppression hotspot.
- `AccountScreen.kt` — complexity/parameter suppression hotspot.
- `AddressListScreen.kt` — LongMethod hotspot.
- `AccountDeletionScreen.kt` / ViewModel — many responsibilities, but domain flow is inherently multi-step; no generic abstraction rewrite recommended.
- `CartCoordinator` has `TooManyFunctions`, but the suppression is explicitly justified by the requirement that ownership transitions share one mutex/persistence boundary; splitting it merely to satisfy Detekt could harm correctness.

## State/effect observations

Good:

- production destinations use lifecycle-aware state collection;
- repository/network work is not launched directly from ordinary recomposition;
- invalid typed route recovery uses effects;
- Product media focus restoration is effect-driven;
- screen-local state is mainly transient UI state.

No material `remember`/`rememberSaveable` misuse was established.

---

# 11. Dependency / Gradle Usage Matrix

Verdicts are static source/build verdicts. No dependency-removal build was run.

## Foundation

| Dependency/plugin | Config | Direct source use | Verdict |
|---|---|---|---|
| Android library | plugin | yes | REQUIRED |
| Compose compiler | plugin | CommerceTheme | REQUIRED |
| Kotlin serialization | plugin | no Foundation production serialization source found | LIKELY UNUSED / NEEDS COMPILE REMOVAL |
| Detekt | plugin | build gate | REQUIRED BUILD |
| coroutines-core | `api` | no Foundation production use found | LIKELY UNUSED; if consumer relies transitively, consumer should declare directly |
| serialization-json | `api` | no Foundation production use found | LIKELY UNUSED |
| core-ktx | implementation | no six-file Foundation production use found | LIKELY UNUSED |
| Compose UI/material3/BOM | implementation | CommerceTheme | REQUIRED |

## Storefront

- `api(project(":foundation"))`: required by public configuration/factory contract.
- Apollo runtime: required.
- coroutines-core: required.
- okhttp: required by client/network stack.
- test dependencies: live.

## Account

- `api(project(":foundation"))`: required by CustomerAccountConfiguration/public factories.
- Apollo runtime: required.
- AppAuth: required by hosted authorization.
- coroutines-core: required.
- okhttp: required.
- serialization-json: used by OAuth/discovery/token parsing.
- test/androidTest dependencies: live.

## Checkout

| Dependency | Verdict |
|---|---|
| `api(project(":foundation"))` | **CONFIRMED UNUSED by complete two-file production source** — FUP-005 |
| AndroidX Activity | REQUIRED (`ComponentActivity`) |
| Shopify Checkout Sheet Kit | REQUIRED |
| coroutines-core | REQUIRED (Channel/Flow) |
| JUnit/Mockito/test | test-only |

## Firebase

- `api(project(":foundation"))`: currently required by notification-route policy contract in main; if FUP-003 cleanup removes that contract, reassess.
- Firebase Config: required.
- Firebase Installations: used by Firebase adapter/proof boundary.
- Firebase coroutines bridge: required.
- Firebase Messaging: debug-only by build configuration.
- Analytics/Crashlytics are constraints/collection-off boundaries, not normal production runtime features.

## App

Major direct dependencies are connected:

- module dependencies;
- Hilt;
- Lifecycle ViewModel/runtime-compose;
- Navigation Compose;
- Activity Compose;
- Room runtime/ktx/compiler;
- Coil Compose;
- AppAuth;
- test/instrumentation libraries.

No additional confirmed app-module direct dependency orphan was established without a compile-removal experiment.

## Version catalog

- `junit4` direct alias/version: CONFIRMED UNUSED (AUD-009).
- AndroidX JUnit and Compose JUnit4 aliases remain live.
- Lockfile transitive JUnit4 presence must not be hand-edited.

## R8/ProGuard

App rules are narrow:

- runtime annotation attributes;
- typed route serialization companions.

No broad keep-all rule was found.

---

# 12. Resource Reachability Analysis

## Inventory

- 20 drawable XML files.
- base `values/strings.xml`.
- base `values/themes.xml`.
- English `values-en/strings.xml`.
- one `xml/data_extraction_rules.xml`.
- no app-main raw/assets/menu resource tree observed.

P3-15 current acceptance evidence records:

- base/English key parity: 525 / 525;
- pseudolocale/RTL verification;
- minified release removal of proof resource copy;
- release artifact retention of required production resources.

## Static drawable analysis

Production `R.drawable.ic_...` references were found in:

- `ProductionApp`;
- Account menu/components;
- Search;
- Wishlist;
- Product Detail;
- `DestinationScaffold`;
- other active UI components.

One content-level duplication is visible:

- `ic_nav_search.xml`
- `ic_nav_search_selected.xml`

have the same blob SHA.

Both names are intentionally referenced by selected/unselected navigation configuration. This is not a runtime bug; tint/state styling may still distinguish them. It is not promoted to a finding.

## Proof copy in main resources

P3-15 explicitly records that some reusable proof copy remains in the main resource set for the debug graph and is removed by release resource shrinking.

Classification: **INTENTIONALLY RETAINED / release-unreachable**, not dead production UX.

## Limits

A fresh Lint/AAPT unused-resource report was not regenerated in this follow-up. Therefore no string/drawable is labelled “confirmed unused” solely from absence of a code-search hit.

---

# 13. State Machine / Lifecycle / Coroutine Analysis

## Home

Action -> HomeViewModel -> HomeContentRepository -> independent Storefront reads.

- independent ranges are loaded with structured concurrency;
- no unbounded retry;
- loading timer is structured child work;
- partial section failure is represented.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Categories / Collection

Actions -> Catalog ViewModels -> CatalogRepository -> StorefrontCatalogGateway.

- bounded cursor paging;
- sort/filter resets page state;
- stale pagination is guarded at ViewModel layer.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Search

Query change -> SearchViewModel:

- bounds query length;
- cancels previous job;
- increments request generation;
- debounces;
- generation-checks initial/pagination result before state commit;
- persists only bounded local history.

This is a strong latest-wins implementation.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Product

Product route -> ProductDetailViewModel -> ProductDetailRepository -> Product Detail gateway.

- load job cancellation;
- persisted non-sensitive selection/media index in SavedStateHandle;
- cart double action protected by `cartJob`;
- invalid requested variant represented explicitly.

AUD-005 applies to aggregate gateway paging.

## Wishlist

Room membership Flow -> WishlistViewModel -> `refresh()` -> repository sequential Product Detail resolution.

- cancellation is preserved in repository;
- storage failure explicit;
- mutation guard prevents duplicate local mutation;
- removed/failed products retain identities.

Problem: ViewModel observes membership for its whole back-stack lifetime, not screen STARTED visibility. A retained Wishlist entry can therefore rehydrate off-screen. See FUP-006.

## Cart

UI -> CartViewModel -> singleton DefaultCartRepository -> CartOperations -> session/cart coordinator.

Good:

- ViewModel ignores overlapping action;
- repository mutex serializes all cart state transitions;
- coordinator mutex serializes persisted ownership transitions;
- ambiguous mutation is reread and never blindly replayed;
- restricted/quarantined/detach-pending states are explicit.

Efficiency issue: AUD-002.

## Checkout

UI -> CheckoutViewModel -> CheckoutController -> singleton CheckoutAdapter / Checkout Kit.

Good:

- preparation has explicit states;
- Checkout URL is validated;
- active session IDs prevent one controller from accepting a mismatched stale event;
- cart completion handles same/different cart and persistence cleanup.

Critical ownership problem: FUP-001.

## Account

Sign in -> AccountViewModel/controller -> authorization effect -> browser -> token/session -> identity.

- one-shot effect channel is buffered;
- session transitions centralized;
- authorization launch failure has explicit callback.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Profile

- private state memory-only by explicit privacy decision;
- save guarded;
- terminal session returns to Account.

Do not add SavedState persistence of PII as cleanup.

## Addresses

- list/form separated;
- confirmation states explicit;
- terminal session effect;
- private form memory-only by policy.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Orders

- list/detail private;
- content cleared on stop;
- `FLAG_SECURE` at destination policy;
- cursor and duplicate/stale response handling;
- terminal session returns to Account.

AUD-006 is the remaining combined navigation characterization gap.

## Account deletion

- mobile local-clear/sign-out boundary explicit;
- remote merchant deletion remains externally blocked by authority;
- no code pretends remote deletion completed.

**INSPECTED — NO MATERIAL ISSUE FOUND.**

## Update policy

- safe local/cache-first;
- one process-scoped async refresh;
- bounded Remote Config cadence/timeout;
- no hard gate;
- public legal/help escape.

`checkoutPreloadEnabled` has no current consumer by explicit P3-14 decision; not a bug.

## Coroutine/Flow inventory conclusions

Observed production primitives include:

- `viewModelScope.launch`;
- `async` / `coroutineScope`;
- `Mutex`;
- `StateFlow`;
- buffered `Channel`;
- `withTimeoutOrNull`;
- `suspendCancellableCoroutine`.

Good patterns:

- cancellation rethrow in network and Wishlist handling;
- no GlobalScope;
- no ad-hoc permanent CoroutineScope;
- no sleep-based production timing;
- structured parent-child work;
- mutex boundaries around cart/session mutable state.

Risk:

- `Channel.receiveAsFlow()` is appropriate for one event consumer but unsafe for the current multi-controller Checkout topology (FUP-001).

---

# 14. Test-to-Production Coverage Matrix

| Behavior | JVM | Android test | CI executes runtime Android test? | Gap |
|---|---|---|---|---|
| Search latest wins/debounce/history | yes | screen/storage | **no** | Android UI/storage runtime outside CI |
| Product selection/cart UI | yes | yes | **no** | device Compose outside CI |
| Cart ambiguity/no replay | yes | supporting Android storage | **no Android execution** | integrated production call-count not asserted |
| Customer token/session refresh | yes | Keystore/platform tests | **no** | Android secure storage outside CI |
| Room Search/Wishlist | yes | yes | **no** | platform persistence outside CI |
| Room 1 -> 2 migration | limited JVM | MigrationTestHelper | **no** | migration is compilation-only in CI |
| typed Navigation routes | some JVM helpers | ProductionNavigationTest | **no** | runtime back stack outside CI |
| state restoration | no equivalent | StateRestorationTester | **no** | outside CI |
| Order deep links | instrumentation | yes | **no** | direct deep-link terminal Back scenario missing |
| Checkout controller state | yes | no dedicated multi-entry test | n/a | FUP-001 not covered |
| Checkout SDK lifecycle | checkout/app JVM/proof history | historical physical proof | not current CI | multiple-controller event ownership gap |
| Wishlist N-request behavior | functional repo tests | UI/storage | no | no request-count/lifecycle performance contract |
| update-policy store/banner | JVM | yes | no | CI lacks Android runtime |
| localization/pseudolocale | some JVM | yes | no | current CI lint helps but does not execute UI |
| legal/support | yes | yes | no | Android policy execution outside CI |

## Source-test counts

- app JVM: 33 Kotlin files.
- app testDebug: 4.
- app androidTest: 25.
- foundation JVM: 3.
- storefront JVM: 8; androidTest: 1.
- account JVM/support: 11; androidTest: 2.
- checkout JVM: 3.
- firebase JVM: 2.

Total:

- **64 JVM/testDebug Kotlin test files**
- **28 Android-test Kotlin files**

Current CI executes JVM tasks but none of the 28 Android-test source files on a device/emulator.

This confirms AUD-001 with stronger coverage evidence.

---

# 15. Complexity / Suppression / Churn Hotspots

## Production complexity suppressions observed

### `TooManyFunctions`

- `app/.../ProductionApp.kt`
- `app/.../order/OrderScreens.kt`
- `app/.../account/AccountScreen.kt`
- `app/.../product/ProductDetailScreen.kt`
- `app/.../accountdeletion/AccountDeletionViewModel.kt`
- `app/.../accountdeletion/AccountDeletionScreen.kt`
- `storefront/.../CartCoordinator.kt`

### `LongMethod`

Observed in:

- `catalog/ProductCard.kt`
- `product/ProductDetailScreen.kt`
- `order/OrderScreens.kt`
- `address/AddressListScreen.kt`
- `account/AccountScreen.kt`
- debug evidence Activities.

### `LongParameterList`

Observed in common UI and feature UI including:

- `AppNavigationItem`;
- `DestinationScaffold`;
- `CommerceStateComponents`;
- `PriceBlock`;
- `AccountMenuRow`;
- Account/Profile/Address/Product UI.

These are not automatically bugs; Compose component signatures often aggregate explicit UI contracts.

### `MagicNumber`

Concentrated in:

- brand tokens;
- Home;
- Search;
- Collection;
- Product.

### Lint suppression

`AndroidUpdatePolicyStore` has two narrowly documented `UseKtx` suppressions because the boolean result of synchronous `SharedPreferences.commit()` is part of fail-closed behavior.

That suppression is justified and should remain unless the contract changes.

## Hotspot verdicts

- ProductionApp: real maintainability hotspot — AUD-004.
- ProductDetailScreen: real mixed-responsibility hotspot — AUD-010.
- CartCoordinator: complexity is largely intentional correctness cohesion; rejected as refactor-for-metric.
- Account/Orders/Deletion: high feature responsibility, but no second architecture or generic abstraction target established.

## Git history/churn

Recent history is dominated by sequential Phase 3 feature slices, P3-15 proof separation/hardening, documentation audit, and fresh-clone CI repair.

High churn in ProductionApp/navigation/test/evidence files is therefore partly explained by planned feature integration rather than uncontrolled regression churn.

No file was labelled bad solely because it changed frequently.

---

# 16. Scripts / Legacy / Historical Classification

Repository scripts inventoried:

1. `Invoke-AndroidVirtualDevice.ps1`
2. `Provision-CustomerAccountDiscovery.ps1`
3. `Test-FirebaseConfiguration.ps1`
4. `Test-Phase2Foundation.ps1`
5. `Test-Phase3Planning.ps1`
6. `Test-Preparation.ps1`
7. `Test-RepositoryPortability.ps1`

| Script | Classification | Evidence |
|---|---|---|
| Test-RepositoryPortability | ACTIVE CI | current workflow caller |
| Invoke-AndroidVirtualDevice | ACTIVE MANUAL / TEST SUPPORT | current testing documentation/evidence references |
| Provision-CustomerAccountDiscovery | ACTIVE MANUAL / MUTATION-CAPABLE SUPPORT | provisioning purpose; must never be run merely for audit |
| Test-FirebaseConfiguration | ACTIVE MANUAL / CONFIG VALIDATOR | configuration proof/support |
| Test-Phase2Foundation | HISTORICAL EVIDENCE / SUPPORT | older foundation acceptance; still cited historically |
| Test-Phase3Planning | HISTORICAL + MANUAL ACCEPTANCE | P3-14/P3-15 handoffs record execution |
| Test-Preparation | HISTORICAL EVIDENCE / SUPPORT | preparation validation |

No script is called confirmed obsolete.

The follow-up did not execute or line-audit every script body after the connector time window closed; classification is based on current CI caller plus current documentation/reference evidence. That is an explicit remaining limitation.

---

# 17. Documentation Drift

## Current docs aligned

- `docs/README.md` correctly distinguishes present-state authority from historical evidence.
- Owner/authority doc still places production/release decisions outside current completed P3-15 boundary.
- P3-15 correctly describes proof source moving from main to debug.
- P3-15 correctly describes production push/Analytics/Crashlytics as absent.

## Minor implementation/documentation tension

### Firebase reusable contracts

P3-15 describes push runtime/proof relocation and production absence, but `firebase/src/main/FirebaseContracts.kt` still contains Analytics and notification-route contracts without a production consumer.

This is not necessarily false historical documentation: the runtime is indeed absent. The code should be classified as stale/reusable contract surface, not as an active production feature.

### Checkout preload flag

The source has no production preload caller. P3-14 explicitly states that this is intentional: the flag preserves a bounded pre-existing contract but P3-14 adds no new consumer.

Classification: **not documentation drift; intentional dormant contract**.

### CI vs historical acceptance

P3-15 records a complete physical Android suite. Current CI does not reproduce that suite. Historical PASS evidence is valid history, but it is not a present pull-request regression gate.

This distinction should remain explicit in future docs.

---

# 18. New Findings Summary

| ID | Priority | Confidence | Category | Location | Summary |
|---|---:|---|---|---|---|
| FUP-001 | P1 | HIGH | Stability / concurrency / DI | CheckoutAdapter, CheckoutController, CheckoutViewModel, Cart navigation | Shared Channel event can be consumed/dropped by wrong Checkout controller when multiple Cart entries/ViewModels coexist. |
| FUP-002 | P3 | CONFIRMED | Dead code | `foundation/error/AppFailure.kt` | Entire sealed failure hierarchy has no code consumer. |
| FUP-003 | P3 | HIGH | Dead/disconnected contracts | `firebase/FirebaseContracts.kt` | Analytics and notification route contracts have no production caller after P3-15 proof/push removal. |
| FUP-004 | P3 | HIGH | Source-set / DI hygiene | `ApplicationModule.provideProjectLogger`, `ProjectLogger`, debug FoundationViewModel | Production Hilt graph provides logger only consumed by debug proof. |
| FUP-005 | P3 | CONFIRMED | Gradle dependency hygiene | `checkout/build.gradle.kts` | `api(project(":foundation"))` unused by checkout production source. |
| FUP-006 | P2 | HIGH | Lifecycle / API efficiency | WishlistViewModel + WishlistRepository | Retained off-screen Wishlist VM can repeat sequential Product Detail rehydration on any membership change. |

---

# 19. Detailed New Findings

## FUP-001 — Checkout events can be consumed and discarded by the wrong controller

**Priority:** P1
**Confidence:** HIGH
**Category:** Runtime stability / concurrency / DI scope
**Scope:** production main

### Files/symbols

- `checkout/src/main/kotlin/com/gurbakir/checkout/CheckoutAdapter.kt`
  - `ShopifyCheckoutAdapter.eventChannel`
  - `events = eventChannel.receiveAsFlow()`
  - `presentWithOperation`
- `app/src/main/kotlin/com/gurbakir/mobile/checkout/CheckoutController.kt`
  - unscoped `@Inject constructor`
  - `activeCheckout`
  - `events`
  - `acceptEvent`
- `app/src/main/kotlin/com/gurbakir/mobile/checkout/CheckoutViewModel.kt`
  - init collector
- `app/src/main/kotlin/com/gurbakir/mobile/di/ApplicationModule.kt`
  - singleton CheckoutAdapter provider
- `app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt`
  - multiple ordinary `navigate(CartRoute)` sources
- `app/src/main/kotlin/com/gurbakir/mobile/CartDestination.kt`

### Upstream callers

Home, Product and Account can navigate to Cart. Each Cart NavBackStackEntry owns Hilt ViewModels.

### Downstream dependency

All Checkout controllers receive the same singleton CheckoutAdapter and therefore the same channel-backed Flow.

### Observed evidence

`Channel.receiveAsFlow()` distributes channel elements among collectors; it is not a broadcast stream that guarantees every collector sees every event.

Every CheckoutViewModel starts:

`controller.events.collect(controller::acceptEvent)`

Each controller has its own `activeCheckout`.

If an event for session N is delivered to a controller that does not own session N, `acceptEvent()` returns without action.

### Concrete failure scenario

1. User opens Cart A.
2. Another navigation path creates Cart B while A remains on back stack.
3. Two CheckoutViewModels/controllers remain alive.
4. Both collect the singleton adapter's `receiveAsFlow`.
5. Checkout is started from B; B owns session 7.
6. SDK emits `Completed(session=7)`.
7. Channel delivers the element to A's collector.
8. A sees session mismatch/null active session and discards it.
9. B never receives that element.
10. B can remain IN_PROGRESS and cart completion/refresh is not performed.

### Impact

Potential stuck Checkout UI and missed completion/cancel/failure transition.

### Change risk

High. Checkout callback ownership and cart completion are sensitive.

### Recommended future action

Characterize first, then establish **one event-routing owner**:

- one app-/activity-scoped CheckoutController, or
- one adapter collector that routes by session ID to the owning session/controller.

Do not simply change Channel to SharedFlow without defining ownership/lifecycle semantics.

### Required validation

- one Cart entry;
- two Cart back-stack entries;
- completion/cancel/failure/external-link events;
- stale prior session event;
- ViewModel destruction/recreation;
- process/activity recreation constraints;
- no duplicate completion;
- cart preserved/cleared outcomes.

---

## FUP-002 — `AppFailure` is confirmed disconnected

**Priority:** P3
**Confidence:** CONFIRMED
**Category:** Dead code
**Scope:** foundation production main

### File/symbol

`foundation/src/main/kotlin/com/gurbakir/foundation/error/AppFailure.kt`

Entire sealed hierarchy.

### Upstream callers

None found.

### Downstream

No framework annotation, DI registration, serialization annotation, Room registration, navigation registration or reflection contract.

### Evidence

Repository-wide symbol search finds the declaration file but no `AppFailure` consumer and no `AppFailure.` branch use.

Feature layers use their own semantically specific result/failure models.

### Why it matters

It suggests an abandoned generic-error abstraction and can mislead future engineers into adopting a type the active architecture deliberately did not use.

### Recommended action

Safe cleanup candidate after a normal compile-all-modules verification.

### Validation

- remove on a branch;
- compile all modules;
- run unit tests;
- ensure no public binary/API consumer is intentionally supported outside repository.

---

## FUP-003 — Analytics/notification contracts remain in Firebase main with no production consumer

**Priority:** P3
**Confidence:** HIGH
**Category:** Disconnected code / source-set cleanliness
**Scope:** firebase production main

### File/symbols

`firebase/src/main/kotlin/com/gurbakir/firebase/FirebaseContracts.kt`

High-confidence disconnected groups:

- `AnalyticsReporter`
- `AnalyticsEvent`
- `DisabledAnalyticsReporter`
- `NotificationRoute`
- `NotificationRouteParser`
- `NotificationDestination`
- `NotificationPayloadParser`

### Upstream callers

- Analytics symbols: no code caller found.
- Notification parser contracts: unit-test references exist; no production app caller.
- Debug Firebase proof contracts do not require these production notification parser symbols.

### Historical context

P3-15:

- moved Firebase push-registration proof/runtime inspector to `firebase/src/debug`;
- removed custom proof Messaging service;
- made Messaging debug-only;
- states production push/Analytics/Crashlytics are absent.

### Why it matters

These types make `firebase/src/main` look as though production notification routing/analytics architecture exists when it does not.

They also keep Foundation's `ExternalRoutePolicy` dependency relevant to Firebase main even though Remote Config itself does not need that route parser.

### Risk

Low-to-medium. Historical/debug tests may intentionally preserve parser behavior; P3-16 may also have expected cleanup sequencing.

### Recommended action

During P3-16 proof-removal/production-boundary work, explicitly decide whether these are:

- future approved contracts;
- debug/test contracts that should move source set;
- dead contracts that should be removed.

Do not infer production push authorization from their presence.

### Validation

- current Firebase JVM tests;
- app debug proof build;
- release dependency/Dex inspection;
- ensure no planned P3-16 contract depends on the exact public API.

---

## FUP-004 — Production logger DI binding exists only for debug proof consumption

**Priority:** P3
**Confidence:** HIGH
**Category:** DI/source-set hygiene
**Scope:** app main + foundation main + app debug

### Files/symbols

- `app/src/main/.../di/ApplicationModule.kt`
  - `provideProjectLogger()`
- `foundation/src/main/.../logging/ProjectLogger.kt`
- `app/src/debug/.../FoundationViewModel.kt`

### Reachability

Production provider -> `ProjectLogger`.

Only concrete code consumer found -> debug `FoundationViewModel`.

No production feature injects ProjectLogger.

### Why it matters

P3-15 intentionally moved proof providers/dependencies out of production source. This remaining binding weakens that source-set separation conceptually even though Hilt does not eagerly instantiate an unused provider and R8 may remove unused release code.

### Recommended action

If the logger is not a planned production contract, move the binding into debug ProofModule or otherwise make debug ownership explicit.

The Foundation logging API itself is DEBUG SUPPORT, not dead, while debug proof exists.

### Validation

- debug Hilt graph;
- testDebug;
- release Hilt compile;
- release R8 graph.

---

## FUP-005 — Checkout exposes an unused Foundation API dependency

**Priority:** P3
**Confidence:** CONFIRMED
**Category:** Gradle/dependency hygiene
**Scope:** checkout module

### File

`checkout/build.gradle.kts`

`api(project(":foundation"))`

### Production source inventory

The entire production Kotlin surface is:

- `CheckoutAdapter.kt`
- `OfficialCheckoutKitClient.kt`

Neither imports or exposes a Foundation type.

### Why it matters

An unnecessary `api` edge widens the transitive compile surface and can conceal consumer dependency ownership.

### Recommended action

Remove in isolation on a future branch.

### Validation

- checkout compile/test;
- app debug/release compile;
- dependency lock regeneration only through Gradle if required;
- no hand lockfile editing.

---

## FUP-006 — Retained off-screen Wishlist can repeat N-request rehydration

**Priority:** P2
**Confidence:** HIGH
**Category:** Lifecycle / API efficiency
**Scope:** app production main

### Files/symbols

- `WishlistViewModel.init`
- `WishlistViewModel.refresh`
- `DefaultWishlistRepository.load`
- `DefaultWishlistRepository.resolve`

### Reachability

Any screen wishlist toggle -> singleton WishlistRepository/Room -> membership Flow emission -> every alive WishlistViewModel collector -> `refresh()` -> sequential Product Detail requests for every saved product.

### Why this extends AUD-003

The first audit proved N requests on visible load. The follow-up traces the observer lifetime.

A ViewModel owned by a NavBackStackEntry remains alive while that entry remains in the back stack. Wishlist can therefore continue reacting after navigation to a product/other destination.

### Concrete scenario

1. User visits Wishlist with 20 items.
2. Opens Product Detail from Wishlist; Wishlist entry remains below Product.
3. Toggles saved state on Product.
4. Room membership changes.
5. Hidden WishlistViewModel receives emission and starts 20-item rehydration.
6. User is not on Wishlist and gains no immediate value from that network work.

### Impact

Extra Storefront traffic, battery/radio work, cancellation/restart churn, contention with visible Product work.

### Recommended action

Fix request shape and lifecycle together:

- purpose-built/batched wishlist summary query;
- and/or refresh tied to destination visible lifecycle/on-resume rather than permanent membership-driven full rehydrate;
- preserve lightweight membership Flow for save icon state.

### Validation

- Wishlist visible;
- Product opened from Wishlist;
- membership changed while Wishlist hidden;
- primary navigation save/restore;
- process recreation;
- request-count instrumentation.

---

# 20. Confirmed Dead / Disconnected Inventory

The classifications below deliberately distinguish *dead source*, *unused dependency edges*, *high-confidence disconnected contracts*, *intentional retention*, and items that require build/runtime confirmation. Absence of a plain-text call site was not, by itself, enough to label framework-registered or source-set-specific code dead.

## CONFIRMED DEAD SOURCE

### `foundation/src/main/kotlin/com/gurbakir/foundation/error/AppFailure.kt`

**Classification:** `CONFIRMED DEAD`

Evidence:

- the file defines a generic sealed failure hierarchy;
- repository-wide symbol search returns the declaration file and no consumer;
- it has no Hilt, Room, Navigation, serialization, manifest, reflection or generated-code registration role;
- active features use their own domain-specific failure/result types.

**Safe future action:** remove on a normal cleanup branch, then compile all modules and run the JVM suite.

## CONFIRMED UNUSED BUILD SURFACE

### Direct JUnit 4 version-catalog alias

**Classification:** `CONFIRMED DEAD / UNUSED CATALOG ENTRY`

- `gradle/libs.versions.toml` declares `junit4 = "4.13.2"` and the `junit4` library alias;
- repository code/build search finds no `libs.junit4` consumer;
- AndroidX JUnit and Compose JUnit4 remain separate, live dependencies;
- JUnit 4 may still appear transitively in dependency locks and should not be removed by hand from lockfiles.

### `checkout -> foundation` API dependency

**Classification:** `CONFIRMED UNUSED DEPENDENCY EDGE`

- `checkout/build.gradle.kts` declares `api(projects.foundation)`;
- Checkout production contains exactly two Kotlin source files;
- neither source imports or exposes a Foundation type;
- a repository search for `com.gurbakir.foundation` under `checkout` returns zero matches.

This is a dependency cleanup candidate, not a dead Kotlin class.

## HIGH-CONFIDENCE DISCONNECTED PRODUCTION-MAIN CONTRACTS

### Firebase Analytics proof contracts

**Classification:** `HIGH-CONFIDENCE DISCONNECTED`

Symbols in `firebase/src/main/kotlin/com/gurbakir/firebase/FirebaseContracts.kt`:

- `AnalyticsReporter`
- `AnalyticsEvent`
- `AnalyticsEvent.IntegrationProofViewed`
- `DisabledAnalyticsReporter`

No production caller was found. Current P3-15 authority states production Analytics is absent. Historical Phase 2 proof material still references the concept.

### Firebase notification-route proof contracts

**Classification:** `HIGH-CONFIDENCE DISCONNECTED / TEST-HISTORICAL COMPATIBILITY`

Symbols:

- `NotificationRoute`
- `NotificationRouteParser`
- `NotificationDestination.FIREBASE_PROOF`
- `NotificationPayloadParser`

Current code-search evidence finds the parser declaration, historical threat-model material and its unit test, but no production app caller. P3-15 moved/removed the push-proof runtime and keeps Messaging debug-only.

These contracts should receive an explicit P3-16 disposition rather than being silently assumed to be a production push architecture.

### Production `ProjectLogger` Hilt provider

**Classification:** `HIGH-CONFIDENCE PRODUCTION-DISCONNECTED PROVIDER`

- `ApplicationModule.provideProjectLogger()` is in `app/src/main`;
- the only concrete source consumer found is debug-only `FoundationViewModel`;
- its test is `testDebug`;
- the underlying logger remains useful to debug/evidence code.

The provider, not necessarily the logger API itself, is the cleanup target.

## LIKELY UNUSED — COMPILE CONFIRMATION REQUIRED

Foundation build surface:

- Kotlin serialization plugin;
- `api(libs.coroutines.core)`;
- `api(libs.serialization.json)`;
- `implementation(libs.androidx.core.ktx)`.

The complete Foundation production source surface contains no `kotlinx.*` or `androidx.core.*` source use. These remain `LIKELY UNUSED`, not `CONFIRMED DEAD`, because `api` dependencies may currently mask undeclared dependencies in consuming modules. Remove one at a time and compile all consumers.

---

# 21. Intentionally Retained / False Positives

## INTENTIONALLY RETAINED

### Debug proof/evidence code

- `app/src/debug/**`
- `firebase/src/debug/**`
- debug `ProofModule`
- `Stage3EvidenceActivity`
- `Stage4EvidenceActivity`

P3-15 deliberately moved proof code out of production main. Current historical release inspection records proof classes/resources as absent from the minified release graph. Debug exported evidence Activities must not be misreported as production exported Activities.

### Historical documentation and reference evidence

- private forensic corpus (not present in the public tree)
- Phase 2 handoffs/proofs/threat-model material;
- Preparation/Phase 3 handoffs and acceptance evidence;
- product-quality history retained by documentation authority.

These are evidence, not runtime code.

### Room schemas and migration

- exported Room schema history;
- schema 1 and schema 2 evidence;
- `WISHLIST_MIGRATION_1_2`.

These are compatibility assets and must not be deleted as “old generated files.”

### P3-16 deferred production boundary

The absence of a final production identity/flavor/signing/Play/Firebase/Shopify release boundary is an explicit product/release-governance deferral, not dead or forgotten configuration.

### Checkout preload contract / flag

`CHECKOUT_PRELOAD_ENABLED` has no current production preload caller. P3-14 explicitly states that it preserves an existing bounded contract and does not add a consumer. Therefore it is **not** classified dead in this audit.

### Main-resource proof copy that release shrinking removes

P3-15 records that some reusable proof strings remain in the main resource set for the debug graph while release resource shrinking removes them. Without a fresh AAPT reachability run, they are not classified as confirmed dead resources.

### Profile/address memory-only private state

The lack of process-death restoration for private profile/address data is an explicit privacy/data-ownership decision. Do not add PII to SavedState merely for generic “state restoration” consistency.

## NOT DEAD / FALSE POSITIVE

### `CartCoordinator` complexity

`CartCoordinator` is large and suppresses `TooManyFunctions`, but its ownership transitions intentionally share one mutex and persistence boundary. Splitting it only to satisfy a metric could weaken correctness. Treat the complexity as justified cohesion unless a concrete responsibility can be extracted without splitting the state machine/lock owner.

### Sensitive wrappers and redacted `toString()` implementations

These are active safety boundaries, not boilerplate to collapse into plain Strings.

### Typed route serialization support / ProGuard rules

The app's route keep/annotation rules support live typed navigation and are narrow. They are not broad legacy keep rules.

### Debug-only Firebase Messaging surface

Messaging in the debug proof graph is intentional. It does not imply production push is implemented or authorized.

### GraphQL operation inputs and Room DAO methods

All inspected Storefront/Customer Account operations and DAO methods have production owners/callers. No orphan operation or DAO query was established.

---

# 22. Prioritized Recommended Cleanup / Hardening Sequence

This sequence is intentionally ordered by correctness risk, not by code-style attractiveness.

## Phase A — Characterize the two P1 regression gaps before structural cleanup

### A1. Checkout multi-owner event characterization — FUP-001

Add a deterministic test that creates two independent Checkout controllers/ViewModel owners over one adapter event source, then proves which owner receives a session event. Also add an instrumentation/navigation characterization for a realistic `Cart -> Product -> Cart` back stack.

**Success criterion:** completion/cancel/failure/external-link events are delivered exactly once to the owning Checkout session and cannot be consumed-and-dropped by another retained Cart owner.

Do not refactor the channel/event architecture until this test exists.

### A2. Execute Android instrumentation in CI — AUD-001

Introduce an emulator/managed-device regression gate or an equivalent reliable Android test runner.

At minimum prioritize:

- typed navigation/deep links/back stack;
- StateRestorationTester cases;
- Room 1 -> 2 migration;
- Search/Wishlist Room behavior;
- Keystore-backed customer/cart session tests;
- critical Compose UI contracts.

The current workflow assembles app Android-test APKs but executes none of the repository's Android instrumentation tests.

## Phase B — Low-risk, high-confidence cleanup

Perform separately so failures identify the responsible removal:

1. remove `AppFailure.kt`;
2. remove the unused direct `junit4` catalog alias/version;
3. remove `checkout`'s unused `api(projects.foundation)` edge;
4. move `ProjectLogger` binding to debug ownership if no production consumer is intentionally introduced;
5. audit/remove Foundation serialization plugin/dependencies one by one.

For each dependency/API removal, compile every consumer module; do not hand-edit dependency locks.

## Phase C — P2 lifecycle/network efficiency

### C1. Wishlist — AUD-003 + FUP-006

Measure request counts in both visible and hidden-Wishlist scenarios. Prefer a purpose-fit/batched product-summary resolution path and stop full rehydration from being permanently coupled to every membership emission.

Keep the lightweight Room membership Flow for save-button state.

### C2. Cart mutation preparation — AUD-002

Add integrated call-count/state tests around signed-out, authenticated, failed-session-retained, restricted and ambiguous mutation cases. Then consolidate pre-mutation reconciliation so one authoritative context is prepared per mutation.

Preserve:

- ownership attach/detach rules;
- secure persistence behavior;
- ambiguous-mutation reread;
- no-blind-replay semantics.

### C3. Product Detail aggregate paging — AUD-005

Instrument actual catalog page counts and end-to-end latency first. Add an aggregate operation deadline only if real evidence shows a meaningful tail-latency problem.

## Phase D — Characterization and structural maintainability

### D1. Order direct-deep-link terminal-session Back test — AUD-006

Automate:

`direct Order deep link -> terminal session expiry/reset -> Account -> Back`

Verify that stale private Order content/route is not reachable and no navigation loop occurs.

### D2. ProductionApp composition-root split — AUD-004

Retain exactly one typed NavHost and the current route ownership. Move feature-specific destination adapters/effect wiring into cohesive internal files. Avoid introducing a generic navigation framework.

### D3. Product Detail screen extraction — AUD-010

When the feature is next modified, extract cohesive media viewer/platform-window/focus behavior from `ProductDetailScreen.kt`. Do not rewrite stable product-selection/purchase logic solely for metrics.

### D4. Shared Room naming — AUD-007

When local persistence is next changed, consider neutral Kotlin naming for the shared database/provider. Preserve:

- physical DB filename `gurbakir-local.db`;
- table names;
- schema versions;
- exported schemas;
- explicit migrations.

## Phase E — P3-16 production boundary decisions

Explicitly decide the fate of proof-era Firebase main contracts:

- approved future production contracts;
- move to debug/test source sets;
- or remove.

Do not turn this cleanup into an implicit decision to ship production push/Analytics/Crashlytics. Those remain product/release-governance decisions.

---

# 23. Suggested Verification Matrix

| Change area | Minimum static/JVM verification | Android/runtime verification | Artifact/release verification |
|---|---|---|---|
| Checkout event ownership | checkout + app JVM; new multi-controller test | `Cart -> Product -> Cart`, complete/cancel/fail, stale session | release Checkout adapter still retained; no proof classes |
| CI instrumentation gate | workflow syntax + Gradle task discovery | emulator/managed-device execution | uploaded reports/artifacts optional; no secrets |
| Remove AppFailure | Foundation + all consumer compiles; JVM suites | none expected | no API consumer break |
| Remove JUnit4 catalog alias | Gradle configuration + JVM/Android-test compile | none | lockfile changed only through Gradle if needed |
| Remove checkout->foundation edge | checkout/app compile + tests | Checkout smoke | release dependency graph |
| Move ProjectLogger provider | debug Hilt/testDebug + release Hilt compile | debug proof launch if retained | release graph still clean |
| Foundation dep/plugin cleanup | compile all six modules after each removal | relevant smoke only | dependency verification/locks |
| Wishlist request/lifecycle change | Wishlist repo/VM tests + request counts | visible/hidden Wishlist navigation, process restore | network contract unchanged unless new GraphQL op added |
| Cart reconciliation change | CartRepository/CoordinatedCartOperations/CartCoordinator tests | authenticated/signed-out cart/Checkout smoke | secure persistence and Storefront graph intact |
| Product aggregate deadline | gateway timeout/cancellation tests | real catalog latency sample | no partial-detail acceptance |
| Order terminal-reset characterization | navigation/order JVM where possible | direct owned order deep link -> expiry -> Account -> Back | private route not leaked |
| ProductionApp split | all app JVM + navigation compile | full critical navigation suite | typed-route serialization/R8 rules intact |
| Room naming refactor | Room/repository tests | migration + current DB open | physical filename/schema unchanged |
| Firebase contract disposition | Firebase/app debug + release compile | debug proof only if retained | production Messaging/Analytics/Crashlytics boundary re-inspected |

---

# 24. Audit Limitations / Unknowns

This follow-up intentionally does **not** convert unknowns into findings.

## No local build or Gradle execution in this follow-up

A temporary clone was attempted only to improve static inventory, but the runtime could not resolve `github.com`; it failed before any checkout existed. No repository worktree was created and no Gradle task was run.

Consequences:

- dependency removals were not compile-tested;
- a fresh Lint/AAPT unused-resource report was not generated;
- R8/Dex analysis was not rerun;
- instrumentation was not executed in this session.

Historical P3-15 PASS evidence is useful evidence of the then-current implementation, but it is not represented as a fresh follow-up execution.

## GitHub code-search index is not the authority for file totals

During the follow-up, GitHub code search reported 111 indexed `app/src/main/kotlin` Kotlin files while the exact Git tree/package inventory used in the audit accounts for 113. This is consistent with code-search index lag/coverage differences on a recently updated private repository. Therefore file totals in this report use the exact commit tree/directory inventory, not code-search totals. Code search is used for symbol reachability only.

## Checkout issue is a statically proven topology risk, not a reproduced production incident

The following are directly established:

- singleton channel-backed adapter;
- unscoped stateful controller;
- NavBackStackEntry-scoped CheckoutViewModel;
- multiple Cart entries can exist;
- every CheckoutViewModel collects the shared channel Flow;
- session mismatch discards an event.

What is **not** claimed:

- a real customer has hit the failure;
- the event loss was reproduced on a physical device in this follow-up.

That is why FUP-001 requires a characterization test before the implementation choice.

## Product Detail aggregate timeout remains performance-dependent

Static maximum sequential page count and per-call timeout are known. Real page counts and latency distribution are not. AUD-005 remains a runtime-measurement finding, not a confirmed user-visible defect.

## Resource reachability is conservative

No string/drawable is called confirmed dead solely because GitHub text search did not find a reference. Android resource indirection, tests, manifests, generated references and shrinker behavior require AAPT/Lint/R8 evidence for high-confidence removal.

## Scripts were classified by current callers and authority/history

All seven repository scripts were inventoried and their CI/manual/historical role was classified. Not every script body was executed or independently function-tested in this follow-up. Mutation-capable provisioning scripts were intentionally not run.

## External systems were not mutated or used as hidden validation

No Shopify Admin, Customer Account remote mutation, cart/order/payment operation, Firebase console change, Remote Config publication, signing action, Play action, or customer data mutation was performed.

## P3-16 may intentionally change today's dead/disconnected classification

Some proof-era contracts may be deliberately adopted by an approved production design later. Current classification means “no current production consumer at audited HEAD,” not “this concept may never exist.”

---

# 25. Final Conclusion

The second pass changes the first audit in one important way: the repository has a **credible P1 Checkout event-ownership risk** that the first report did not identify. The problem is not stale-event identification itself; session IDs correctly identify ownership. The problem is that a channel element can be delivered to a non-owner collector and discarded before the owner sees it.

Outside that issue, the first audit's overall architecture assessment remains valid. The codebase shows strong deliberate boundaries around cart ownership, secure session persistence, cancellation, typed navigation, private-order handling, Room migration, debug/release proof separation and remote-policy fail-closed behavior. There is no evidence of widespread abandoned production code or a competing second application architecture.

The follow-up also narrows the cleanup inventory substantially:

- one confirmed dead generic failure source file;
- one confirmed unused direct catalog alias;
- one confirmed unused Checkout module dependency edge;
- a small set of high-confidence proof-era Firebase/main and logger DI surfaces;
- likely-unused Foundation build exports that require compile confirmation.

The highest-value next engineering work is therefore **not a broad cleanup rewrite**. It is:

1. characterize and fix Checkout event ownership;
2. make Android instrumentation a current CI regression gate;
3. perform isolated, compile-verified dead/dependency cleanup;
4. reduce Wishlist hidden/off-screen N-request behavior;
5. preserve the cart/session/privacy/migration invariants already working well;
6. defer product/release decisions that properly belong to P3-16.

No repository change is required merely to “satisfy” this report. Findings marked `NEEDS RUNTIME CONFIRMATION`, `LIKELY UNUSED`, or `INTENTIONALLY RETAINED` should remain untouched until their stated verification/authority condition is met.

---

# Appendix A — Classification Snapshot

## P1

- **AUD-001** — Android instrumentation is not executed by current CI.
- **FUP-001** — Checkout channel/event ownership can fan out to the wrong retained controller.

## P2 / verify before structural change

- **AUD-002** — duplicate cart mutation reconciliation.
- **AUD-003 + FUP-006** — Wishlist N-request full-detail rehydration, including retained off-screen ViewModel amplification.
- **AUD-004** — ProductionApp composition-root hotspot.
- **AUD-005** — Product Detail aggregate deadline: static concern, runtime measurement required.
- **AUD-006** — direct Order deep-link terminal-reset Back characterization gap.

## P3 / cleanup or maintainability

- **AUD-007** — shared Room naming drift.
- **AUD-008** — Foundation build/dependency surface likely wider than source needs.
- **AUD-009** — unused direct JUnit4 catalog alias.
- **AUD-010** — Product Detail UI/platform responsibility hotspot.
- **FUP-002** — confirmed dead `AppFailure` hierarchy.
- **FUP-003** — proof-era Firebase Analytics/notification contracts disconnected from production.
- **FUP-004** — production logger Hilt provider used only by debug proof.
- **FUP-005** — unused Checkout -> Foundation API edge.

## Explicitly retain / do not “clean up” casually

- debug proof/evidence source sets;
- Room schema history and migration;
- reference APK/forensic evidence;
- private Profile/Address memory-only behavior;
- cart ambiguity reread/no-replay logic;
- cart/session ownership state machine;
- P3-16 deferred release boundary;
- Checkout preload flag until its authority disposition changes;
- narrow typed-route R8 rules.

---

# Appendix B — Evidence Basis

Primary evidence was the repository at exact commit:

`84e8b53853df216f1af377a41d03d9937a4264ec`
`ci: repair fresh-clone validation (#9)`

High-value files/directories inspected include:

- `.github/workflows/android-foundation.yml`
- `settings.gradle.kts`
- root/module Gradle build files and `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt`
- production navigation route declarations and destination adapters
- all production Hilt module files and debug ProofModule
- Cart repository/operations/coordinator/persistence/pager surfaces
- Checkout adapter/client/controller/ViewModel/destination surfaces
- Search, Wishlist, Product, Home, Account, Profile, Address, Order, deletion and update-policy state owners
- Storefront Apollo gateway/catalog/search/product/cart paging code
- Customer Account discovery/OAuth/session/account/profile/address/order gateways
- all Storefront and Customer Account GraphQL operation inventories
- Search/Wishlist Room database, DAOs, stores and migration
- Foundation production files and dependency declarations
- Firebase main/debug contracts
- production/debug resources and manifests
- JVM/testDebug/androidTest source trees
- repository scripts and current documentation authority/history.

The preliminary `FORENSIC-CODE-STABILITY-MAINTAINABILITY-AUDIT.md` was used only as a hypothesis list for verification. Current source at the audited commit remained the source of truth.
