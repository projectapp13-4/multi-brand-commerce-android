# P3-05 Local Wishlist Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS - PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `dc9e8e2dafe8b15adeebb58efeed530de609534b`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the P3-05 durable device-local, account-independent wishlist implementation authorized by the continuous Phase 3 goal. It does not classify missing physical-device evidence as a pass and does not authorize Cart, release, remote wishlist sync, or P3-16 work.

## Product and clean-room boundary

The sibling reference APK remained immutable and was not installed, launched, rebuilt, signed, or copied. The project evidence helper returned high-confidence observations from `analysis/17_features/features.csv`, `analysis/09_ui_navigation/destinations.csv`, and `analysis/15_local_storage/storage-map.csv`: a parameterless Wishlist destination and a locally retained identifier set. Indexed raw provenance points to a private forensic source path:28` and a private forensic source path:455`. The evidence also records reference GraphQL/Worker synchronization behavior, but its implementation/authorization/retention semantics remain unknown and the approved product disposition is intentionally different: this implementation is device-local, has no account dependency, and makes no cross-device promise.

No decompiled method body, obfuscated structure, reference branding, credential, Worker route, endpoint, GraphQL wishlist operation, or mutable reference data entered this project. No new network host, permission, dependency, backend, background worker, analytics event, or telemetry SDK was added.

The Room migration follows Android's official [database migration and testing guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions) and the current [MigrationTestHelper contract](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).

## Adopted local data contract

- `gurbakir-local.db` advances from exported schema version 1 to version 2. The existing `search_history` and `search_history_settings` tables remain unchanged.
- The new `wishlist` table stores only `environmentId`, `marketId`, the full validated Shopify product GID, and `addedAtEpochMillis`. Its primary key is environment/market/product identity; an environment/market/timestamp index supports stable newest-first reads.
- `WISHLIST_MIGRATION_1_2` creates only the new table/index. It is registered explicitly in the database builder. No destructive or downgrade fallback exists.
- Add is idempotent and preserves the original added timestamp. Remove and clear are local operations and require no network. Clear-all has an explicit confirmation.
- Development/staging and `TR` partitions cannot cross-display. Ordinary logout/session expiry keeps the wishlist. Explicit remove/clear, environment isolation, all-local-data policy, uninstall, and disabled backup follow SR-08.
- Database/SQLite failure is fail-closed: UI states storage is unavailable and does not claim a mutation succeeded.

## Rehydration and unavailable-item behavior

- Mutable title, price, media, availability, variant, and server responses are never persisted as wishlist truth.
- Opening Wishlist reads local identities newest-first and reuses the generated P3-04 product-detail gateway to obtain current product data.
- A current product renders honest current media, price range, and availability and opens the typed product route.
- A deleted/unpublished product remains locally saved and is labeled as no longer available until the user removes it.
- A connection/configuration/service failure retains the local identity, labels the row without raw server text, and offers retry plus local removal. No account/sync error is shown.
- Rehydration is read-only. No Shopify mutation or wishlist synchronization request exists.

## User-visible outcome

- Home exposes the now-functional Wishlist destination. Wishlist is also a typed primary route with safe back/browse recovery.
- Home featured product, collection cards, search results, product detail, and Wishlist items use one Room-backed membership stream and explicit “saved on this device” wording.
- Save/remove controls expose action and saved state as text and accessibility state description; state is not communicated by color alone.
- Wishlist distinguishes loading, empty, storage-unavailable, current product, unavailable product, deleted product, and retryable rehydration states.
- Empty state explains device-local storage and links only to functional discovery. No sign-in, sync, cloud, reinstall, or cross-device persistence claim exists.

## Automated evidence

The following serialized gates completed successfully against the final implementation source:

```powershell
.\gradlew.bat :app:compileDevelopmentDebugKotlin --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :app:testDevelopmentDebugUnitTest --tests "com.gurbakir.mobile.wishlist.*" --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :app:compileDevelopmentDebugAndroidTestKotlin --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat spotlessApply :app:testDevelopmentDebugUnitTest :app:detekt --no-parallel --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :app:compileDevelopmentDebugAndroidTestKotlin :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-parallel --no-daemon --no-configuration-cache --console=plain
```

Results:

- App JVM tests: 43 tests, 0 failures, 0 errors, 0 skips.
- New JVM tests cover idempotency, original-timestamp preservation, partition membership, clear, invalid GID rejection, storage failure, current/deleted/connection-failed rehydration, retained identities, local remove without a second remote read, and honest ViewModel storage states.
- Exported Room schemas `1.json` and `2.json` are checked in. The version 2 schema contains both history tables plus `wishlist`.
- New instrumentation source covers real Room idempotency/partition/clear, exported-schema `1 -> 2` migration with history-row preservation, empty/local-only UI, current and failed entries, retry/remove/clear confirmation, storage failure, 200 percent text, Home/list/search/detail toggles, and typed Wishlist navigation.
- Android test Kotlin compilation, development app APK assembly, and development test APK assembly: `PASS`.
- Spotless, app Detekt, app development Lint, changed-file UTF-8/mojibake scan, and `git diff --check`: `PASS`.

## APK evidence and evidence not run

The final locally assembled artifacts are:

- `app/build/outputs/apk/development/debug/app-development-debug.apk` - 19,377,418 bytes - SHA-256 `3795264319248392CE9818DA12BD5FE1BDD5B4CEA7F9F990778688D8FF65C312`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk` - 1,217,282 bytes - SHA-256 `38551E4B533DA021C6338C94737A1852624328A9CED1B849E43AFBBB81F5070B`

The package/runner identities remain the P3-04-verified `com.gurbakir.mobile.dev.debug`, `com.gurbakir.mobile.dev.debug.test`, and `androidx.test.runner.AndroidJUnitRunner` targeting the application package.

The owner reported that a physical Android device is unavailable during this work window. The unchanged host condition documented in P3-03 also cannot boot the existing x86_64 AVD into ADB because firmware virtualization/hypervisor support is unavailable. No redundant emulator attempt was made.

The new/updated instrumentation tests were compiled and packaged but not executed. Actual `1 -> 2` Room migration on Android SQLite, relaunch/process-death persistence, local remove while physically offline, Compose interactions, TalkBack state announcements/traversal, top-bar/card layout, and 200 percent text visual inspection are therefore **NOT RUN**.

This is an evidence-availability gap, not a device `PASS`. It remains required for P3-15 integrated acceptance or the first earlier checkpoint with an approved device. It does not block P3-06 because schema generation, migration source/fixture compilation, JVM behavior, static quality, instrumentation compilation, and both APK assemblies pass.

## Residual and later-slice boundaries

- Wishlist rehydration depends on live Storefront availability; there is intentionally no mutable offline product cache. Local identities and local removal remain available when rehydration fails.
- The wishlist is not encrypted because it contains only non-account preference identifiers under the accepted threat model and app sandbox; Android backup/data extraction remains disabled.
- No account-deletion behavior beyond the accepted SR-08 policy default is invented. P3-13 owns the final external deletion process.
- Cart/add-to-cart remains absent until P3-06. Checkout, real payment, release signing/publication, production telemetry/push, and P3-16 remain outside this checkpoint.

## Rollback

Revert only the local P3-05 checkpoint commit to remove Wishlist UI/repository/navigation/schema v2 and restore the source schema to v1. A development/staging installation that has already opened version 2 cannot safely open a reverted version 1 database because downgrade fallback is deliberately absent; clear that non-production app's local data or uninstall it before installing the reverted build. This loses that installation's local history/wishlist. No device was migrated during this checkpoint, and no remote rollback exists because all external operations were read-only.

## Exact next slice

P3-06 may begin from this checkpoint under the continuous owner goal: expose the first production Cart action/destination, consume only a valid P3-04 variant intent, implement the complete Anonymous/CustomerAssociated/DetachPending/Quarantined ownership state machine, preserve Keystore cart capability boundaries, reconcile all mutations from Shopify, and keep cart notes absent.
