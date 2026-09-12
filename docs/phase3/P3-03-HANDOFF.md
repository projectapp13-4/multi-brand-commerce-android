# P3-03 Search and Local History Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS — PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `f1875f2863e73804cee30acf55a0a991b79e2d8d`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the P3-03 Storefront product search and device-local history implementation authorized by the continuous Phase 3 goal. It does not classify missing physical-device evidence as a pass and does not authorize Product Detail, Cart, release, or P3-16 work.

## Adopted current contracts

- Full-text product discovery uses Shopify Storefront API `2026-07` `search`, not the general `products` connection. The operation requests only `PRODUCT` items, relevance order, last-term prefix matching, cursor pages of 20, and explicit unavailable-product results.
- Search input is whitespace-normalized, bounded to 2–100 characters, rejects control characters locally, and is passed only as a generated GraphQL variable. Raw queries are never logged or sent to analytics.
- Text changes use a 400 ms cancellable debounce. An explicit IME/button submission records history when enabled; debounced previews do not create a history entry.
- History is enabled by the accepted reversible default, stores at most 10 unique Turkish-normalized queries for 30 days, and is partitioned by environment plus `TR` market.
- History is device-local only. Remove, clear, and disable are available; disabling persists and clears the active partition atomically. Re-enabling cannot restore cleared entries.
- Shopify results and Room are separate authorities: Room never caches result titles, prices, media, inventory, opaque IDs, or server responses.

These choices follow Shopify's current [Storefront search connection](https://shopify.dev/docs/api/storefront/2026-07/queries/search) and Android's stable [Room release](https://developer.android.com/jetpack/androidx/releases/room) and [schema/migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions).

## User-visible outcome

- Home now exposes a functional Search destination alongside Categories.
- Search automatically refreshes after the bounded debounce and also supports the keyboard/search button action.
- Loading, too-short, empty-result, connection/configuration/service error, loaded-count, next-page loading, and next-page retry states are distinct.
- A newer query cancels the older request, and request-generation checks prevent a stale response from replacing current results.
- Search results use the shared honest price/availability product card and cursor de-duplication. Cards remain intentionally non-interactive until P3-04.
- Blank search shows the local-history policy, recent entries, individual remove, clear-all, and enabled/disabled controls. Storage failure leaves search functional while showing that history is unavailable and will not be saved.
- Query text is restored through bounded `SavedStateHandle`; no result, credential, customer data, or opaque Shopify identifier enters SavedState.
- Turkish is the source locale and all new production strings have English counterparts.

## Room schema, privacy, and rollback boundary

P3-03 adds `gurbakir-local.db`, Room schema version 1, with checked exported schema:

`app/schemas/com.gurbakir.mobile.search.SearchHistoryDatabase/1.json`

The `search_history` primary key is environment, market, and normalized query; it stores display query and last-used epoch only. `search_history_settings` stores the enabled flag per partition. Expiry and overflow cleanup execute transactionally around reads/writes, and disable stores the setting plus clears entries in one transaction.

There is no prior Room schema in this application, so version 1 has no predecessor migration. P3-05 may add wishlist data to this database only with a tested version 1-to-2 forward migration that preserves history. `fallbackToDestructiveMigration` and downgrade fallback are absent. A Room/SQLite failure is caught at the history boundary, marks history unavailable for the process, returns no records, and performs no later writes; it does not crash or block remote search.

The app-wide backup/data-extraction prohibition remains unchanged. Ordinary logout/session expiry keeps device-local history; explicit remove/clear/disable, all-local-data deletion policy, environment isolation, uninstall, and 30-day expiry follow SR-08 in `P3-00-DATA-OWNERSHIP-AND-PERSISTENCE.md`.

## Dependency and supply-chain review

AndroidX Room `2.8.4` is the current stable official line and supports Kotlin/KSP. Only runtime, coroutine extension, compiler, and test artefacts required by this feature were added. The Room Gradle plugin exports the schema. Application locks now cover production, unit/instrumentation, lint, KSP/compiler, development/staging, debug/release configurations; SHA-256 verification metadata records Room, SQLite, and compiler transitives. No unrelated service, permission, telemetry SDK, network endpoint, or global tool was introduced.

## Read-only live Storefront evidence

The final generated search gateway was exercised on 2026-08-10 against only the validated project-owned `https://gurbakir.com` Storefront endpoint. The ignored local public token was read in process as a request header and was never printed.

The proof first read a current published catalog product title, then searched that exact title through the P3-03 `search` operation. The response had a positive total, at least one product, and contained the exact current product title. Existing shop, generic catalog, collection/filter, and price-sort read proofs also remained passing. No Shopify mutation occurred.

## Automated evidence

The following serialized commands completed successfully against the final source:

```powershell
.\gradlew.bat --no-parallel spotlessApply :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessApply :app:detekt :storefront:detekt --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessCheck :app:lintDevelopmentDebug :storefront:lintDebug --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel :storefront:testDebugUnitTest -PgurbakirRunOwnedStorefrontProof=true :app:testDevelopmentDebugUnitTest :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-daemon --no-configuration-cache --console=plain
```

Results:

- Storefront JVM tests: 25 tests, 0 failures, 0 errors; the ordinary run had two conditional skips and the owned live-proof run had one remaining unrelated conditional skip.
- App JVM tests: 30 tests, 0 failures, 0 errors, 0 skips.
- New focused JVM tests cover exact generated search variables/mapping, local invalid-query rejection, debounce cancellation/latest-query-wins, explicit history recording, cursor de-duplication, Turkish uniqueness, 10-entry/30-day policy, disable/clear behavior, and fail-closed SQLite errors.
- New instrumentation source covers real Room partition/bound/expiry/disable cleanup, history actions, non-interactive results/pagination, 200 percent text, Home Search action, and typed Search navigation.
- Android test Kotlin compilation, development app APK assembly, and development test APK assembly: `PASS`.
- Spotless, app/storefront Detekt, app development Lint, and Storefront debug Lint: `PASS`.
- Room version 1 schema export, dependency locks, and SHA-256 verification metadata are present.

## Evidence not run

`adb devices -l` returned no connected physical device. The assembled APK identities were verified locally as `com.gurbakir.mobile.dev.debug` and `com.gurbakir.mobile.dev.debug.test`, with `androidx.test.runner.AndroidJUnitRunner` targeting the application package.

The existing API 36 `medium_phone` AVD was then started twice in headless, read-only, software-rendered mode without snapshots. Both attempts exited before registering with ADB. The host reports `VirtualizationFirmwareEnabled=False` and `HypervisorPresent=False`; the emulator also reports that its hypervisor driver is not installed and warns that x86_64 emulation may not work without hardware acceleration. Resolving this host-level blocker requires a firmware/restart path and was not treated as an application change.

The new/updated instrumentation tests were compiled and packaged but not executed. Real Room file persistence/relaunch, process recreation, IME behavior, TalkBack traversal/announcements, large-screen grid behavior, and physical visual checks are therefore **NOT RUN**.

This is an evidence-availability gap, not a device `PASS`. It remains required for integrated P3-15 acceptance or the first earlier checkpoint with an approved device. It does not block P3-04 because generated contracts, JVM behavior, schema export, static quality, read-only live data, instrumentation compilation, and APK assembly pass.

## Residual and later-slice boundaries

- Search behavior depends on live Shopify indexing and merchant catalog text; no typo-tolerance or suggestion claim is made.
- Search history is intentionally not cross-device, account-linked, backed up, or sent to analytics.
- The initial Room schema has no predecessor migration; the first migration obligation begins when P3-05 or another approved slice changes version 1.
- Product Detail/variant selection belongs to P3-04, Wishlist to P3-05, and Cart remains absent until P3-06.
- Release identity, signing, publication, production telemetry/push, and P3-16 remain outside this checkpoint.

## Rollback

Revert only the local P3-03 checkpoint commit. This removes Search code/routes/schema/dependencies/tests/doc updates. Existing installations of this non-production build may retain the private `gurbakir-local.db` file until app data is cleared or the app is uninstalled; the reverted app does not read it. No remote-data rollback is required because all external operations were read-only.

## Exact next slice

P3-04 may begin from this checkpoint under the continuous owner goal: implement product detail, safe media presentation, explicit valid variant selection, honest price/availability state, and typed product deep links. Keep Cart/add-to-cart absent until P3-06.
