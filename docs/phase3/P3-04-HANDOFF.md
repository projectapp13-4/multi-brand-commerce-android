# P3-04 Product Detail Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS - PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `773d46730d09b150d3992a0cbdc57de0f7a9d6fd`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the P3-04 Storefront product-detail, safe-media, deterministic variant-selection, price/availability, and typed deep-link implementation authorized by the continuous Phase 3 goal. It does not classify missing physical-device evidence as a pass and does not authorize Wishlist, Cart, release, or P3-16 work.

## Clean-room and source authority

The sibling reference APK remained immutable and was not installed, launched, rebuilt, signed, or copied. The checked evidence indexes were queried through the project clean-room helper before implementation:

- the private reference corpus identifies the product-detail/related feature group with high-confidence `GetProductDetail` and `GetRelatedProducts` observations.
- the private reference corpus records a fact/high-confidence product-detail destination with required `productId` and optional `variantId` inputs. The indexed raw provenance points to a private forensic source path:30`.
- the private reference corpus records the high-confidence `GetProductDetail(id: ID!)` operation shape.

Only behavioral facts were used. No decompiled method body, obfuscated structure, third-party reference branding, credential, endpoint, string corpus, or asset entered this project.

Current implementation semantics follow Shopify's official [Storefront product query](https://shopify.dev/docs/api/storefront/2026-07/queries/product), [Product object](https://shopify.dev/docs/api/storefront/2026-07/objects/Product), [ProductVariant object](https://shopify.dev/docs/api/storefront/2026-07/objects/ProductVariant), and [Storefront access scopes](https://shopify.dev/docs/api/usage/access-scopes).

## Adopted product contract

- Product routes carry strict positive numeric Shopify product and optional variant IDs; the Storefront boundary reconstructs and validates exact `gid://shopify/Product/...` and `gid://shopify/ProductVariant/...` identifiers.
- The owned mobile HTTPS route is `https://gurbakir.com/apps/mobile/products/{productId}` with optional `variantId`. It is a typed app deep link, not a claim that the website uses the same product URL. `autoVerify` remains disabled, so no Android App Links association claim is made.
- Product detail is queried by ID with generated Apollo models. Variants page in batches of 250 and media in batches of 50, with a bounded 16-page fail-closed limit, stable-core checks, cursor progression checks, duplicate rejection, and declared-variant-count reconciliation.
- Plain merchant description is bounded and control-character filtered. HTML rendering, reviews, ratings, scarcity, shipping/delivery promises, badges, and marketing claims remain absent.
- Media accepts only safe Shopify/project-owned HTTPS image hosts through the existing media policy. Non-image media is omitted. The UI provides bounded previous/next movement, position semantics, alternative text/fallback text, and a dismissible expanded viewer.
- CI-14 still has no approved related-product source, so the related-products section and `GetRelatedProducts` operation remain absent. CI-15 prohibits invented review/claim content.
- `quantityAvailable` is deliberately absent. The final read-only proof demonstrated that it returns `ACCESS_DENIED` with the current public token, and Shopify documents the field as requiring `unauthenticated_read_product_inventory`. The screen does not need inventory counts. It uses `availableForSale` for purchase eligibility and `currentlyNotInStock` only to distinguish an orderable out-of-stock/backorder state.
- No local product snapshot, cache, Room table, migration, background worker, analytics event, or new network endpoint was introduced. Saved UI state contains only bounded option selections and a media index.

## Deterministic variant and price behavior

- A complete option selection resolves only when exactly one real Shopify variant has the same full set of option/value pairs.
- Partial option values remain selectable only when a real variant can satisfy them together with all current selections. Impossible combinations are disabled rather than silently changed.
- Multi-option products never auto-select an arbitrary first variant. A product with one unambiguous variant may select it automatically.
- An incoming valid variant ID restores that exact variant. A missing, malformed, cross-product, or stale variant ID shows an honest notice and requires a fresh valid selection.
- Existing unavailable variants remain inspectable, but no purchase intent is emitted for them. A valid exact selection alone drives current price, compare-at price, selected image, availability, and the future variant ID.
- Compare-at price is shown only when it has the same currency and is greater than the current price. Before an exact variant is selected, the UI shows the honest variant price range.
- The ViewModel exposes a typed `ProductPurchaseIntent` seam for P3-06, but P3-04 has no add-to-cart control, cart destination, badge, mutation, or fake action.

## User-visible outcome

- Home featured products, collection product cards, and search result cards now open the same typed product-detail destination.
- Loading, retryable/non-retryable error, not-found, stale incoming variant, choose-options, available, unavailable, and orderable-out-of-stock states are distinct.
- Unknown well-formed product IDs render a not-found recovery back to browsing. Malformed typed routes recover through the existing safe-route destination.
- Product title, bounded plain description, price/range, compare-at price, availability, options, media, and viewer controls use Turkish source strings with English resource parity.
- No product state, opaque server response, credential, customer data, or cart ID is persisted.

## Read-only live Storefront evidence

The generated P3-04 product gateway was exercised on 2026-08-10 against only the validated project-owned `https://gurbakir.com` Storefront endpoint. The ignored local public token was used only as a request header and was never printed.

The proof first read a current published catalog product and then loaded its full product detail by ID. It verified the exact product ID, at least one variant, complete option coverage for every variant, and agreement between product and variant sale availability. Existing shop, generic catalog, collection/filter, price-sort, and exact-title search read proofs also remained passing. No Shopify mutation occurred.

The first P3-04 proof attempt returned the controlled GraphQL code `ACCESS_DENIED` because the query included token-gated `quantityAvailable`. The field was removed from the operation and domain model rather than expanding token scope. The final proof passed with the least-privilege product-listing capability.

## Automated evidence

The following serialized commands completed successfully against the final source:

```powershell
.\gradlew.bat --no-parallel spotlessApply :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin :app:detekt :storefront:detekt --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :app:lintDevelopmentDebug :storefront:lintDebug --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :storefront:testDebugUnitTest -PgurbakirRunOwnedStorefrontProof=true --tests com.gurbakir.storefront.OwnedStorefrontReadProofTest --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat spotlessApply :storefront:testDebugUnitTest -PgurbakirRunOwnedStorefrontProof=true :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin :app:detekt :storefront:detekt :app:lintDevelopmentDebug :storefront:lintDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-parallel --no-daemon --no-configuration-cache --console=plain
```

Final results:

- Storefront JVM tests: 28 tests, 0 failures, 0 errors, 1 unrelated conditional skip. The owned live proof ran and passed.
- App JVM tests: 38 tests, 0 failures, 0 errors, 0 skips.
- New JVM tests cover multi-page Apollo variables/mapping, invalid IDs without network access, not-found distinction, exhaustive valid/impossible option combinations, unavailable variants, repository error mapping, incoming/stale variant behavior, price/media derivation, and SavedState restoration.
- New/updated instrumentation source covers product-card navigation, typed product routes and owned HTTPS deep links, selection-driven price/availability, absence of a cart action, media viewer continuity, not-found recovery, and 200 percent font scale.
- Android test Kotlin compilation, development app APK assembly, and development test APK assembly: `PASS`.
- Spotless, app/storefront Detekt, app development Lint, and Storefront debug Lint: `PASS`.
- `git diff --check`: `PASS`.

## APK evidence and evidence not run

The final locally assembled artifacts are:

- `app/build/outputs/apk/development/debug/app-development-debug.apk` - 19,418,567 bytes - SHA-256 `00F30E437189A23C5C1C1F701B3317E83B53E302B26B999C15E6C089C9F252F2`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk` - 1,199,595 bytes - SHA-256 `2FABCA1590DF9A177E9E720DC6FD9DBB40384952DE182AC8282AC99A49B70ECA`

`aapt` verifies application package `com.gurbakir.mobile.dev.debug`, test package `com.gurbakir.mobile.dev.debug.test`, runner `androidx.test.runner.AndroidJUnitRunner`, and test target `com.gurbakir.mobile.dev.debug`.

The project owner reported that a physical Android device is unavailable during this work window. The unchanged host condition documented in P3-03 also has firmware virtualization and Hyper-V unavailable, and the existing API 36 x86_64 AVD could not register with ADB in two prior read-only software-rendered attempts. P3-04 did not repeat those already-diagnosed host attempts.

The new/updated instrumentation tests were compiled and packaged but not executed. Physical APK install/launch, deep-link dispatch, process recreation, media rendering/viewer behavior, TalkBack traversal/announcements, keyboard/focus behavior, 200 percent text visual inspection, and device screenshots are therefore **NOT RUN**.

This is an evidence-availability gap, not a device `PASS`. It remains required for integrated P3-15 acceptance or the first earlier checkpoint with an approved device. It does not block P3-05 because generated contracts, JVM behavior, static quality, read-only live data, instrumentation compilation, and both APK assemblies pass.

## Residual and later-slice boundaries

- Storefront product/media state remains live server authority; offline product cache behavior is intentionally absent.
- Merchant media quality and alternative text depend on current catalog content; unsafe media is omitted and no local substitute is invented.
- Related products remain optional and absent until an approved Shopify-native owned source exists.
- No token-scope expansion was made for inventory quantity. P3-06 must continue using authoritative server validation and honest availability/reconciliation rather than inventing stock bounds.
- Wishlist belongs to P3-05. Cart/add-to-cart belongs to P3-06. Release identity, signing, publication, real payment, production telemetry/push, and P3-16 remain outside this checkpoint.

## Rollback

Revert only the local P3-04 checkpoint commit. This removes the generated product-detail operation/gateway, product feature/routes/deep link, product-card actions, tests, resources, and this documentation. It does not require a Room migration or local/remote data rollback because P3-04 introduced no persistence and all external operations were read-only.

## Exact next slice

P3-05 may begin from this checkpoint under the continuous owner goal: add a durable local-only, account-independent wishlist using the existing Room database with a tested version 1-to-2 migration, explicit SR-08 lifecycle, product rehydration, unavailable/deleted-item recovery, and no sync/account promise.
