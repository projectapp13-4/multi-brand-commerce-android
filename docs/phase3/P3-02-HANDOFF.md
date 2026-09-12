# P3-02 Categories and Listing Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS — PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `d127cb5a139cb5e0c882b9f0615626a4d7ce7ddf`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the P3-02 merchant-backed Categories and product-listing implementation authorized by the continuous Phase 3 goal. It neither claims the missing physical-device run as passing evidence nor authorizes Product Detail, Cart, release, or P3-16 work.

## Adopted merchant and schema contract

The current project-owned Shopify store and Storefront API `2026-07` schema were inspected read-only on 2026-08-10. P3-02 uses Shopify collections as the flat category source and does not invent a hierarchy that the merchant data does not express.

The production Categories order is:

1. `bardaklar`
2. `cezveler`
3. `tavalar-sahanlar`
4. `tencereler`
5. `tabaklar-ve-kaseler`
6. `ozel-urunlerimiz`

Empty or missing collections are omitted. This P3-02 source is deliberately independent from the five-handle P3-01 Home Product Range contract, which remains unchanged.

The current live collection filter contract exposes only `filter.p.product_type`. The UI therefore renders a product-type filter only when the selected collection returns more than one positive-count value; it shows no dead filter control for single-type collections. The supported user-facing sort choices are collection order, best-selling, newest, price low-to-high, price high-to-low, title A–Z, and title Z–A. Internal/schema-only sort keys are not exposed.

Collection pages request 20 products at a time with Storefront cursors. Catalog data is rebuildable and memory-only; P3-02 adds no Room schema or persistent price/inventory cache.

## User-visible outcome

- Home now exposes a functional Categories action and functional collection tiles.
- Categories shows the current non-empty Shopify collections in merchant order with safe media and explicit loading, partial, empty, and error states.
- A category opens a typed collection route and a cursor-paged adaptive product grid.
- Listing state supports the seven approved sort modes, only meaningful server-returned product-type filters, de-duplication by product ID, retry of a failed next page without discarding loaded rows, and stale-request cancellation when sort/filter changes.
- Price ranges use Shopify-returned currency and amounts. Availability is stated honestly; no discount, scarcity, delivery, or inventory quantity is inferred.
- Product cards remain intentionally non-interactive until P3-04. No dead Product Detail route or Cart action is present.
- The owned HTTPS collection pattern `https://gurbakir.com/collections/{handle}` resolves into the typed collection destination. Unknown or missing collections remain a truthful unavailable state rather than a synthesized screen.
- Turkish remains the source locale and the new production strings have English counterparts.

## Production architecture and safety boundary

- `StorefrontCatalogGateway` owns validated collection-page requests and typed page/filter/product responses.
- `StorefrontGatewaySet` creates one configured Apollo client shared by the existing commerce/Home gateway and the catalog gateway without merging their domain contracts.
- The generated `CollectionCatalogPage` operation binds handle, cursor, sort, direction, and product-type filters as variables. Handles and filter values are bounded before network use.
- `DefaultCatalogRepository` preserves configured category order under concurrent reads and maps Storefront results into app-owned models.
- `CategoriesViewModel` and `CollectionViewModel` own refresh, pagination, sort/filter restoration, cancellation, and content-retention behavior through `StateFlow` and `SavedStateHandle`.
- Existing `StorefrontMediaPolicy` remains authoritative for every displayed image. Opaque Shopify IDs and request details are not shown in user-facing errors or logs.
- No new dependency, credential, server endpoint, persistent database, analytics event, or production mutation was introduced.

## Read-only live Storefront evidence

The final P3-02 gateway was exercised against only the validated project-owned Storefront endpoint. The ignored local public token was read in process as a request header and was not printed.

Observed current data included six non-empty production categories. `tabaklar-ve-kaseler` contained four products, which is a legitimate merchant-data change from the earlier P3-01 snapshot that recorded it empty. `ozel-urunlerimiz` returned both `Fondü Tavası` and `Şişe` product-type values, proving the dynamic filter contract; the proof also exercised descending price order. The query was read-only and mutated no Shopify state.

## Automated evidence

The following serialized commands completed successfully against the final source:

```powershell
.\gradlew.bat --no-parallel spotlessApply :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessCheck :app:detekt :storefront:detekt :app:lintDevelopmentDebug :storefront:lintDebug --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel :storefront:testDebugUnitTest -PgurbakirRunOwnedStorefrontProof=true :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-daemon --no-configuration-cache --console=plain
```

Results:

- Storefront JVM tests: 23 tests, 0 failures, 0 errors; the ordinary local run had two conditional skips and the owned live-proof run had one remaining unrelated conditional skip.
- App JVM tests: 24 tests, 0 failures, 0 errors, 0 skips.
- New focused coverage proves generated query variables and response mapping, invalid request rejection, current live collection/filter/sort behavior, category order and partial failure, cursor pagination de-duplication, and cancellation of stale listing requests.
- Three new Compose catalog instrumentation tests cover category opening, sort/filter/load-more behavior, unavailable products, and 200 percent font scaling; Home and typed-navigation tests were updated for the new functional routes and HTTPS deep link.
- Android test Kotlin compilation, development app APK assembly, and development test APK assembly: `PASS`.
- Spotless, app/storefront Detekt, app development Lint, and Storefront debug Lint: `PASS`.
- `git diff --check`: `PASS` before the handoff edit.

## Evidence not run

`adb devices -l` returned no connected device. The new and updated instrumentation tests were therefore compiled and packaged but not executed. Focused physical-device grid rendering, scrolling, filter focus/announcement behavior, TalkBack traversal, deep-link launch, process recreation, and real-image behavior are **NOT RUN**.

This is an evidence-availability gap, not a device `PASS`. It remains required for integrated P3-15 acceptance or the first earlier checkpoint at which an approved device is connected. It does not block independent P3-03 work because schema mapping, pure behavior, static quality, live read-only data, instrumentation source compilation, and APK assembly pass.

## Residual and later-slice boundaries

- Shopify category membership, titles, counts, and product types remain merchant-owned live data and can change without an app release.
- Memory-only catalog content is intentionally unavailable offline after process death; the screen shows a truthful retry state rather than stale price or inventory claims.
- Product Detail interaction and variant selection belong to P3-04.
- Wishlist belongs to P3-05; Cart and any cart badge/action remain prohibited until P3-06.
- Search and its local retention boundary begin in P3-03.
- Physical-device acceptance remains pending as recorded above.
- Release identity, signing, publication, final brand assets, and P3-16 remain outside this checkpoint.

## Rollback

Revert only the local P3-02 checkpoint commit. This removes the collection query, catalog gateway/repository/state/UI/routes/tests, and documentation while preserving the complete P3-01 Home checkpoint. No database or remote-data rollback is needed because this slice added no migration and performed no external mutation.

## Exact next slice

P3-03 may begin from this checkpoint under the continuous owner goal: implement cancellable Storefront product search and the approved local bounded, clearable history with its storage/migration/privacy contract. Keep Product Detail absent until P3-04 and Cart absent until P3-06.
