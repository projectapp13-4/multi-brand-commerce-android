# Gate 1 Mobile-Core Extraction Completion Handoff

Status: **Gate 1 complete / local PASS; PR #13 run #58 PASS; final source-review correction validated locally; Gate 2 not started**

Evidence date: 2026-09-08

Branch: `refactor/multibrand-gate-1-mobile-core`

This is the durable result record for the execution governed by
[`plans/GATE-1-MOBILE-CORE-EXTRACTION-IMPLEMENTATION-PLAN.md`](plans/GATE-1-MOBILE-CORE-EXTRACTION-IMPLEMENTATION-PLAN.md).
The plan remains the approved execution authority; this handoff records the implementation and evidence actually observed. Current source and Git remain authoritative if a later change makes this document stale.

## Outcome and commit anchors

Gate 1 achieved its approved goal: reusable Android application, feature, navigation, local-data, resource and generic test implementation now lives in `:mobile-core`; `:app` remains the Gürbakır Android application and composition shell. All mandatory Gate 1 validation completed successfully. The change did not create a second/synthetic brand or enter Gate 2.

```text
execution base          dcf7d0337ae7677cef632fe48526a3ec88a4a13a
validated implementation 5e90d1b900538494d485c905e2538a4925180ed8
final Gate 1 ref        refs/heads/refactor/multibrand-gate-1-mobile-core
```

The final local Gate 1 HEAD is the latest commit updating this handoff, with subject `docs(multi-brand): record final source-review corrections`. A Git commit cannot contain its own content-derived SHA. Resolve the immutable final SHA with:

```powershell
git log -1 --format=%H -- docs/multi-brand/GATE-1-COMPLETION-HANDOFF.md
```

At the recorded final post-documentation close-out, that commit is also the branch and remote-ref HEAD. The final execution report and local SDD checkpoint record the resolved SHA.

Ordered Gate 1 commits:

| Order | Commit | Purpose |
|---:|---|---|
| 1 | `08af6763334f39bdfabae8f8b52c70f9d653eb31` | Add the shared Android library module and wire `:app` to it. |
| 2 | `977f6f9ef6b0116bc28ab2467792ef4f153d9a3f` | Introduce app-to-core composition seams before moving implementation. |
| 3 | `87cd8d6c60fc79ab4144520635ea9da50e3b40cc` | Keep generic composition providers internal after review. |
| 4 | `35c0f01781b51ae0a831fcddc48537618bdc4227` | Atomically extract reusable production code, resources, DI and Room schemas. |
| 5 | `8f652252751fc1771ea7ebf3ba6a3725b805279b` | Move generic JVM/instrumentation proof to core and retain assembled Gürbakır proof in app. |
| 6 | `7d55ef9c3b763e3329e7e027527bcb3274a22c16` | Remove a debug/main Compose singleton facade collision without behavior changes. |
| 7 | `5b4e620bb7d015bd40bb220e66360819a44008ab` | Add core CI lanes, dependency locks and repository boundary enforcement. |
| 8 | `6d91305432eb6054ebc6e44aacfe2f0035250c1d` | Close reviewed Kotlin DSL/module-inventory portability guard gaps. |
| 9 | `cf8e5f4854f1b2ea5f51d9f5981fe4132a44cfd8` | Extend portable core-source guards across the approved ordinary-text source set. |
| 10 | `6771287084edb06b70d912e6486ead54371bb0c3` | Add the initial canonical current-state documentation and Gate 1 handoff. |
| 11 | `c4b96d0802e6e35598f7d56a97d1d866cc1aba5f` | Replace leaked Gürbakır order-support copy in core with neutral fallbacks and guard ordinary production text. |
| 12 | `597c176e5167ef9770b389f7026e82bff61dbca1` | Record the accepted correction, current-implementation GMD rerun and final close-out state. |
| 13 | `516b845ddc54060f3841c70a128d1a8f03658809` | Add the exact required AndroidTest coroutines BOM POM checksum and record PR #13 remediation evidence. |
| 14 | `5e90d1b900538494d485c905e2538a4925180ed8` | Close final source-review gaps in core resources, address policy, callback handling, portability validation, and generic fixtures. |
| 15 | This document's final source-review handoff commit | Record the correction and its final local validation evidence. |

The extra focused fix commits reflect review findings and keep each correction auditable; they do not expand Gate 1 architecture or scope.

## Accepted design intent

The approved design is one monorepo with one Android application module per brand, inward dependencies on shared code, and no runtime merchant switch or normative brand flavor. Gate 1 was limited to establishing the first reusable base:

```text
focused app-to-core seams
        +
atomic reusable implementation extraction
        +
preservation and structural proof
```

Gate 1 did not need to prove another brand. Concrete installed identity, packaged resources, merchant configuration, provider selection and migration-sensitive construction remain on the application side of narrow shared contracts.

## Implemented final state

### Module and dependency graph

The repository contains exactly seven Gradle modules:

```text
:app (Gürbakır Android application/composition shell)
  ├──> :mobile-core
  ├──> :foundation
  ├──> :storefront
  ├──> :account
  ├──> :checkout
  └──> :firebase

:mobile-core (Android library)
  ├── api ----> :foundation
  ├── impl ---> :storefront
  ├── impl ---> :account
  └── impl ---> :checkout

:mobile-core -X-> :app
:mobile-core -X-> :firebase
other shared modules -X-> :app
```

The application module being built still selects Gürbakır and its `development` or `staging` environment. There is no second application module, brand flavor or runtime store selector.

### Production ownership

The extraction mechanically moved 104 production Kotlin files from `:app`, then added two split core DI files for 106 production Kotlin files in `:mobile-core`. Core now owns the reusable root/nav graph, common Compose destinations and UI, account/deletion core, address, cart, catalog, checkout, home, order, product, profile, search, update-policy and wishlist behavior. It also owns reusable default/English strings, plurals and drawables, Room implementation, four schema-history files, and integration-neutral DI.

The 26 production Kotlin files remaining in `:app` own:

- `MainActivity`, `GurbakirApplication` and the `GurbakirApp` composition wrapper;
- Gürbakır brand/configuration and packaged application identity;
- legal/support UI, repository, URL policy/launcher and the deletion-page adapter;
- concrete Home and Catalog merchant configuration;
- concrete App Link/deep-link, address territory and carrier-tracking policy;
- Firebase update-policy adapter and Firebase module;
- application configuration, physical Room filename/partitions, and protected Customer Account/cart store construction.

DI was split by responsibility. Core constructs reusable repositories, stores, controllers, clocks and shared provider-neutral orchestration. App `SearchModule` supplies the database and search partition, app `WishlistModule` supplies the wishlist partition, app `ApplicationModule` supplies brand/environment policies and cart protected storage, and app `CustomerAccountPersistenceModule` supplies Customer Account protected storage. Generic core Hilt modules and Room store implementations remain internal.

### App-to-core composition surface

`GurbakirApp` invokes `MobileCoreApp` with explicit app-owned values/callbacks:

```text
BrandConfiguration
MobileDeepLinkConfiguration
TrackingUrlPolicy
legal-support destination callback
deletion-page launch callback
NavHostController (optional/defaulted)
```

Focused public contracts needed for composition include Home/Catalog configuration types, `AddressTerritoryPolicy`, tracking policy, update-policy remote gateway/current app version, and the Room database/migration boundary. `ProductionAppShell`, `ProductionNavHost`, `ProductionDestinationContent` and `popBackStackOrHome` retain their required debug/proof compatibility. Exactly six scaffold declarations are public because the app-owned legal screen calls them: `DestinationLevel`, `DestinationTitleAlignment`, `DestinationScaffold`, `centeredDestinationContent`, `consumeDestinationInsets`, and `withDestinationSpacing`. Other implementation and test tags remain internal.

### Address, tracking and update/provider boundaries

- Address behavior is policy-driven end to end. Core contains no hard-coded `"TR"`, Turkish postal regex or `SUPPORTED_ADDRESS_TERRITORY`. `AddressTerritoryPolicy.supports()` retains exact case-sensitive equality, while its narrow `AddressPostalCodePolicy` validates optional postal input. App binds territory `"TR"` and the existing five-digit regex; a synthetic `"ZZ"` policy proves the same core ViewModel accepts `AB-1234` and rejects five digits under a materially different policy. Controller/server mutation and server-rejection behavior are unchanged.
- Customer Account redirects are consumed only while `RESTORING` or `AWAITING_BROWSER`. Authenticated and other inactive/busy phases ignore replayed callbacks without replacing the current account summary or invoking the coordinator again; coordinator callback/state validation remains unchanged.
- Core `TrackingUrlPolicy` requires allowed hosts. App supplies exactly `ptt.gov.tr`, `yurticikargo.com`, `araskargo.com.tr`, and `suratkargo.com.tr`.
- Core owns provider-neutral update controller/store/contracts. App supplies `CurrentAppVersionCode(BuildConfig.VERSION_CODE)` and maps `:firebase` `RemoteFeatureFlags` through `FirebaseUpdatePolicyRemoteGateway`.
- Core has no Firebase dependency, Firebase import, app `BuildConfig`, concrete Gürbakır brand/domain/database/address literal, or dependency back to `:app`.

## Migration-sensitive identity preservation

Gate 1 changed source ownership, not installed, external or persisted identity.

### Application, Firebase, OAuth and App Links

| Variant | Preserved application ID |
|---|---|
| Development Debug | `com.gurbakir.mobile.dev.debug` |
| Development Release | `com.gurbakir.mobile.dev` |
| Staging Debug | `com.gurbakir.mobile.staging.debug` |
| Staging Release | `com.gurbakir.mobile.staging` |

The fail-closed default remains `com.gurbakir.mobile.unconfigured`. The same four package names remain the tracked Firebase registration contract. Four ignored local Firebase configuration files were verified by path, hash, package membership and expected client count without copying their contents; package-registration validation and Firebase binding remain app-owned.

Customer Account callback construction remains:

```text
shop.<shopId>.gurbakir://oauth/callback
fail-closed scheme: shop.unconfigured.gurbakir
```

The tracked App Link routes remain:

```text
https://gurbakir.com/collections/
https://gurbakir.com/apps/mobile/products/
https://gurbakir.com/apps/mobile/orders/
```

All four final merged manifests had zero line differences from the Task 0 baseline, preserving application/activity identity, OAuth receiver, routes, `autoVerify` state, Firebase metadata, permissions and theme.

### Room and protected storage

The app still opens exactly `gurbakir-local.db` from `app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt`. Core owns `LocalCommerceDatabase`, schema version 2, and `WISHLIST_MIGRATION_1_2` / `Migration(1, 2)`. Schema files moved to `mobile-core/schemas` with unchanged relative paths and bytes:

| Relative schema path | Preserved SHA-256 |
|---|---|
| `com.gurbakir.mobile.search.LocalCommerceDatabase/1.json` | `867B64F0CD0CB96BD8FFB0C2C3F07E20E8AC0B0195AAB5E3906E9D43480B3D4B` |
| `com.gurbakir.mobile.search.LocalCommerceDatabase/2.json` | `61DDCED2A6556F34091780B60483CC6DAEC423DD6DDEF62C4C290851897230F6` |
| `com.gurbakir.mobile.search.SearchHistoryDatabase/1.json` | `DFDFA4DC38200901E22BEAA89E8CB745F25AFBB15EDB011EE182B6C6F0090627` |
| `com.gurbakir.mobile.search.SearchHistoryDatabase/2.json` | `61DDCED2A6556F34091780B60483CC6DAEC423DD6DDEF62C4C290851897230F6` |

Protected-store identities remain exact:

```text
Customer Account SharedPreferences:
gurbakir_secure_customer_session_<normalizedEnvironmentId>
Customer Account Android Keystore alias:
gurbakir.customer.session.<normalizedEnvironmentId>.v1

Cart SharedPreferences:
gurbakir_secure_cart_<normalizedEnvironmentId>
Cart Android Keystore alias:
gurbakir.cart.<normalizedEnvironmentId>.v1
```

## Resources and test ownership

Core resource inventory resolved all 551 production `R` references across strings, plurals and drawables. Copied definitions matched, XML transitive references resolved, and `order_list_added` plus `wishlist_product_count` remained in default Turkish and English resources. Core production/test code uses `com.gurbakir.mobile.core.R`; the app manifest and production resource tree have no Gate 1 content diff.

A final review found one human-readable brand leak in the shared resource copy: core `order_support_action` still said `Gür Bakır desteğine git` and `Go to Gür Bakır support`. Commit `c4b96d0802e6e35598f7d56a97d1d866cc1aba5f` changed only the core values to reusable fallbacks, `Desteğe git` and `Go to support`. The existing app-owned overlays remain byte-identical and still resolve to the exact Gürbakır Turkish and English copy in the assembled application, so user-visible Gürbakır behavior did not change.

Source review after green GitHub Actions run #58 found that raw XML scanning had missed entity-encoded text. A decoded XML inventory confirmed 34 additional concrete core fallback values: 17 Turkish and 17 English address/account-deletion values containing Gür Bakır, Turkey/Türkiye/TR, `+90`, or fixed five-digit postal guidance. Every resource name already had the exact Gürbakır value in the corresponding app overlay. Commit `5e90d1b900538494d485c905e2538a4925180ed8` replaced only those core values with neutral supported-region/support-channel fallbacks. The app overlay files remained byte-for-byte unchanged at SHA-256 `9664D1679C45F7D6A6F54A60DCD2F1E0B13523FA9A2A75A053E50698C53CA08B` (Turkish) and `A894C3ACF5F1D0AE413B9C7AC25A5EA6A87379E5B0F6A7411AF394E3909480AE` (English); the app GMD resource test resolves all 34 exact overlay values.

Task 4 moved 32 generic JVM test/fixture files and 20 generic Android instrumentation files to core. Core now owns reusable controller/repository/ViewModel/UI/navigation/resource/Room migration proof. Generic Customer Account callback fixtures in core now use the `.example` identity, while app tests continue to prove the exact Gürbakır OAuth/App-Link identities. App retains concrete composition, packaged manifest/application identity, Gürbakır deep-link/database/legal/deletion/Firebase/localization and debug-harness proof. The final JVM discovery check found all 135 core ordinary test methods; app Development and Staging each ran 30 tests in 10 suites at that checkpoint.

## CI and portability controls

`.github/workflows/android-foundation.yml` now includes core JVM, debug/release AAR and API 30 managed-device targets while retaining app lanes. `scripts/Test-RepositoryPortability.ps1` enforces:

- exactly the seven expected Gradle modules and required core files/schema root;
- no shared module dependency on `:app` across supported Kotlin DSL dependency forms;
- no `:mobile-core` dependency on `:firebase`;
- no app `BuildConfig`, Firebase imports, case-insensitive human-readable brand text, or the approved concrete brand/domain/database/address symbols in core production Kotlin/Java/XML sources;
- no decoded Android resource value containing `Gür Bakır`, `Gürbakır`, ASCII `Gurbakir`, Turkey/Türkiye/Turkish/standalone `TR`, `+90`, or fixed five-digit postal guidance. Package identifiers such as `com.gurbakir` remain intentionally outside the unrestricted ASCII text rule.

Fifteen original injected negative fixtures proved the guard rejects named/map project-dependency forms, extra modules and guarded Kotlin/Java/XML literals. The first post-review raw scanner accepted the real entity-encoded leak and accepted temporary encoded/plain resource fixtures, proving the gap. The resource-aware validator parses `<resources>` XML and scans decoded `InnerText`; it rejected `G&#252;r Bak&#305;r` and a plain `Gurbakir / Turkey (TR) / +90 / 5 digits` fixture, then passed restored production resources. The script remains UTF-8 without BOM. The final clean-worktree validator passed 26/26 checks. Gate 1 dependency locking changed only `app/gradle.lockfile` and the new `mobile-core/gradle.lockfile`; the sole resolved coordinate added to the combined lock set was Hilt processor transitive `com.squareup:kotlinpoet:1.11.0`. PR #13 later exposed one missing POM checksum for an already-resolved AndroidTest transitive, so the final state intentionally changes verification metadata by exactly one component/artifact entry. The version catalog and declared dependency versions remain unchanged.

### PR #13 dependency-verification remediation

GitHub Actions run #57 passed `spotlessCheck` and `detekt`, then stopped in the validate job at `:mobile-core:generateDebugAndroidTestLintModel` because strict dependency verification lacked metadata for `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.1` / `kotlinx-coroutines-bom-1.8.1.pom`. JVM, build, portability and instrumentation jobs were skipped after that validate-job failure. No test failed or ran in those skipped jobs, so run #57 is a dependency-verification metadata failure before those executions, not a test regression.

`dependencyInsight` traced the triggering request through the existing approved test graph: `:mobile-core` uses `androidx.test.espresso:espresso-core:3.7.0`, which brings `androidx.test:core:1.7.0`, which requests `kotlinx-coroutines-core-jvm:1.8.1`. The published coroutines core JVM 1.8.1 module metadata imports `kotlinx-coroutines-bom:1.8.1`; Gradle conflict resolution still selects the repository-declared coroutines 1.11.0 runtime. There is no direct BOM declaration and no dependency or catalog version change.

The exact POM was obtained from the configured Maven Central path `https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-bom/1.8.1/kotlinx-coroutines-bom-1.8.1.pom`. Its downloaded bytes, Maven Central `.sha256` sidecar and Gradle cache copy all matched SHA-256 `563e4aa29fa8fe09a6e1746d0a5b51308f7c7dedc468dc5a0ca810a485877030`. Gradle's `--write-verification-metadata sha256` workflow, with refreshed dependency resolution, added only this component and POM artifact to `gradle/verification-metadata.xml`; review found no unrelated generated metadata.

After the correction, a strict non-writer rerun of `:mobile-core:generateDebugAndroidTestLintModel` with `--refresh-dependencies --rerun-tasks` executed 142/142 tasks and passed in 2m55s. The exact CI static-analysis lane, `spotlessCheck detekt lint`, then passed with exit 0 in 5m03s.

GitHub Actions run #58 on commit `516b845ddc54060f3841c70a128d1a8f03658809` completed successfully, confirming that the dependency-verification remediation allowed the full workflow to proceed. The later source-review findings below were discovered after that green run and were corrected in `5e90d1b900538494d485c905e2538a4925180ed8`; their final proof is the fresh local validation recorded here and the new PR run triggered by the final branch push.

## Observed verification evidence

Every mandatory canonical lane is RUN/PASS. Historical failed or interrupted attempts are not counted as PASS.

| Plan section | Actual status | Observed evidence |
|---|---|---|
| 6.1 formatting/static | **RUN/PASS** | Exact `spotlessCheck detekt lint` final rerun after all source/test corrections, exit 0; 3m26s. |
| 6.2 JVM tests | **RUN/PASS** | Exact canonical eight-task final rerun exited 0 in 55s; final affected Account + Address packages passed 29/29 and app composition passed 5/5. |
| 6.3 core library | **RUN/PASS** | `:mobile-core:assembleDebug` and `assembleRelease`, exit 0 after clearing a corrupt generated KSP cache through `:mobile-core:clean`. |
| 6.4 app variants/test APKs | **RUN/PASS** | Four Development/Staging debug/release APKs plus two app AndroidTest APKs, exit 0; release R8/package paths completed. |
| 6.5 application IDs | **RUN/PASS** | All four APK IDs exactly matched the registered values above. |
| 6.6 merged manifests | **RUN/PASS** | Four final manifests, zero line differences from Task 0 baseline. |
| 6.7 app manifest/resources | **RUN/PASS** | App production resource files have zero diff and retain the exact pre-correction SHA-256 values recorded above. |
| 6.8 resource completeness | **RUN/PASS** | 551 core references resolved; 34 concrete core fallbacks neutralized; core resource proof 3/3 and exact app overlay proof 4/4. |
| 6.9 Room schema/migration | **RUN/PASS** | Four relative paths/hashes exact; version 2; final GMD `1 -> 2` migration 1/1. Room plugin generated `copyRoomSchemasToAndroidTestAssetsDebugAndroidTest`; no manual asset sourceSet was needed. |
| 6.10 physical database identity | **RUN/PASS** | Database composition 1/1; physical and upgrade evidence observed `gurbakir-local.db`. |
| 6.11 Customer/cart persistence | **RUN/PASS** | Store sources/constructor arguments and four preference/alias patterns matched baseline. |
| 6.12 address policy | **RUN/PASS** | Final Address JVM suite 19/19; app policy composition 5/5; GMD Address UI 8/8. Exact `TR` support remains case-sensitive, Gürbakır retains five digits, and synthetic `ZZ` accepts `AB-1234`. |
| 6.13 update policy | **RUN/PASS** | Focused JVM 14/14 and physical store 3/3; cancellation/fallback/Firebase mapping/persisted constants covered. |
| 6.14 Firebase continuity | **RUN/PASS** | Four package/config mappings verified safely; physical `FirebaseRuntimeTest` 1/1. |
| 6.15 navigation/App Links | **RUN/PASS** | Core navigation 11/11 and app deep-link integration 2/2; manifest comparison exact. |
| 6.16 localization | **RUN/PASS** | Core resources 3/3 and app localization/pseudo-locale/overlay proof 4/4. |
| 6.17 API 30 managed devices | **RUN/PASS** | Final corrected state: fresh core 98/98 plus fresh app 22/22 = 120/120, zero failures/errors/skips; details below. |
| 6.17A upgrade in place | **RUN/PASS (supplemental)** | Baseline/final `adb install -r`, no clear/uninstall; Search/Wishlist state and database identity survived. |
| 6.18 dependency locks/verification | **RUN/PASS** | Two expected lock paths; no version-catalog or declared version change. Verification metadata now intentionally adds only the SHA-256 for the coroutines BOM 1.8.1 POM required by the resolved AndroidTest graph. |
| 6.19 structural scans | **RUN/PASS** | Eleven required negative scans empty; positive address/migration checks passed. |
| 6.20 changed paths | **RUN/PASS** | Final 232-path base-to-HEAD review attributes the added app localization test to exact overlay preservation; no excluded-scope match. |
| 6.21 portability | **RUN/PASS** | Final clean-worktree validator 26/26, including four decoded-resource guards. |

The exact final managed-device command was:

```powershell
.\gradlew.bat :mobile-core:ciApi30DebugAndroidTest :app:ciApi30DevelopmentDebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

It ran on GMD `ciApi30`, API 30, AOSP ATD x86. The retained result XML files report core 98 tests at `2026-09-08T08:22:54` and app Development 22 tests at `2026-09-08T08:44:37`, with zero failures, errors, or skips.

After correction `5e90d1b`, the final canonical two-target command exited 0 with `BUILD SUCCESSFUL in 4m 23s`; core passed 98/98 and app passed 22/22. Core resource 3/3, Address UI 8/8, Room migration 1/1, and app localization/overlay 4/4 are included in those fresh totals. A later app build-cache cleanup removed its generated report, so the unfiltered app target was executed again and passed 22/22 in 1m27s; this regenerated the retained app XML without changing source. During debugging, an unquoted PowerShell launch of the dotted `-P` property failed before task execution, and an earlier full device run exposed a locale-bound test expectation rather than a production-copy defect. A focused rerun then exposed a same-value text fixture that did not emit a callback. Both test fixtures were corrected, the focused Address UI test passed, and only successful unfiltered runs are counted as final PASS.

Physical-device preservation evidence used Samsung `SM-A225F`, Android 13/API 33, serial `R68RC006LPE`. The full Task 4 split suites passed core 97/97 and app 21/21. At final code HEAD, a focused 32-test set passed: 25 core and 7 app. The supplemental upgrade installed the baseline and final Development Debug APK with `adb install -r`; synthetic Search history and one Wishlist entry survived, `gurbakir-local.db` existed before/after, and final `MainActivity` launched.

The initial Task 7 commit and commit `597c176` changed documentation only. Corrections `c4b96d0` and `5e90d1b` changed production resources/Kotlin, so the affected JVM, resource, build, static-analysis and API 30 GMD proofs were rerun on the final source state. PR #13 run #58 remains proof of the dependency-verification fix at `516b845`; it predates the final source correction. The required post-documentation close-out reruns repository diff checks and the clean-worktree portability validator against the documented final commit.

## Environment incident and remediation

The first exact API 30 managed-device attempt failed before tests because firmware virtualization was disabled. Controlled diagnosis showed:

- SVM disabled allowed Windows to boot but provided no emulator acceleration;
- SVM enabled with the Windows hypervisor/WHPX launch path caused a black screen after POST;
- SVM enabled with `hypervisorlaunchtype Off`, Windows hypervisor absent, and AEHD 2.2 `SYSTEM_START`/Running booted normally and made `emulator.exe -accel-check` return 0.

After AEHD was usable, a stale failed-run AVD and then an incomplete local API 30 AOSP ATD package caused separate setup failures. Kernel evidence showed missing `metadata` and `super` devices. The installed package lacked `advancedFeatures.ini` and `source.properties`, although both were present in Google's official `x86-30_r01.zip`. Only those missing SDK files were restored from the official archive with ZIP CRC verification; recorded SHA-256 values were `A00D90A17E68A18C581A8E5675D86D079806293690C7BFF3D571DF15D6C3350D` and `D76BB0888F8684BF40717A23FDEE4ACFE0E34FC27984C050EBCA5D8D965980A3`. The invalid generated AVD was deleted with `avdmanager`, and the exact unmodified canonical command then passed.

These were host/SDK incidents. No tracked project source or Gate 1 design changed during remediation. Earlier failed, timed-out and interrupted attempts remain historical non-PASS evidence.

## Implementation-time rulings and deviations

The implementation stayed within the approved architecture. These evidence-backed execution rulings explain differences from the plan's simplest anticipated path:

1. Account deletion depended on app-owned legal URL policy. A deletion-specific page-source/result seam and explicit `openDeletionPage` callback were added so legal metadata, URL security policy and browser launching stayed in `:app`; this is why the final `MobileCoreApp` signature has that callback.
2. The retained app legal screen called six shared scaffold declarations. Exactly those six were made public; no blanket visibility widening was used.
3. A main/debug top-level facade collision required a distinct JVM facade/filename for the debug proof source. Behavior did not change.
4. One expression-bodied coroutine test inferred a non-`Unit` return and was not discovered. It was made explicitly `Unit`; moved coroutine tests received the required opt-in. Final discovery proved all 135 core JVM methods.
5. Physical Compose proof found a generated singleton collision. The debug proof file was renamed without content changes, then both physical suites passed.
6. Independent CI review found dependency-syntax, unexpected-module, Java and XML guard bypasses. Two focused follow-up commits closed them; 15 red/green fixtures exercised the real validator.
7. Gradle lock ownership moved with Room/Hilt processing. Gradle generated the lock changes; no dependency version was intentionally upgraded.
8. API 30 acceleration/package repair changed host and Android SDK state only, as described above.
9. Final review found human-readable Gürbakır support copy in core resources even though the app already overlaid the same resource name. The core fallback was neutralized and the portability validator now rejects spaced or joined human-readable brand text in ordinary core production source; app copy remained unchanged.
10. PR #13 run #57 exposed missing verification metadata for a POM imported by the existing AndroidTest dependency graph. The exact Maven Central artifact checksum was added without changing dependency declarations, versions, repositories or verification policy.
11. Source review after green run #58 confirmed that raw XML scanning missed 34 entity-encoded brand/market values, core owned a Turkish postal regex, and Account accepted callbacks in inactive phases. Core fallbacks and generic callback/address fixtures were neutralized, postal validation moved behind the existing app-to-core policy boundary, and callback consumption was limited to `RESTORING`/`AWAITING_BROWSER`.
12. The suggested manual `androidTest.assets.srcDir("$projectDir/schemas")` addition was not applied. `:mobile-core` already uses the Room Gradle Plugin with `room { schemaDirectory(...) }`; the final task graph ran the plugin-generated `copyRoomSchemasToAndroidTestAssetsDebugAndroidTest`, and the API 30 `WishlistMigrationTest` passed 1/1. Official Room migration guidance uses the explicit assets sourceSet alternative when the plugin is not used.

No ruling introduced a brand capability framework, provider plugin architecture or Gate 2 behavior.

## Deferred and intentionally unimplemented

The following remain outside Gate 1:

- synthetic or second brand application and cross-brand conformance;
- brand capability system, Account-disabled composition and configurable primary navigation;
- Shopify Navigation or Home metaobject migration;
- mobile provisioning tooling, full market/multi-country address policy, tracking-provider framework, or cleanup of the known `:storefront` media-origin debt;
- Firebase-free second-brand implementation, loyalty/backend seams, analytics/notification architecture;
- production application ID/signing/Play/Firebase/Customer Account/App Links, publication or release ownership;
- repository-wide package neutralization, feature-module expansion or build convention plugins.

The optional standalone third-party YAML parser check discussed during Task 5 was **NOT RUN** because no parser dependency was installed; it was not a canonical requirement. Workflow requirements were instead verified by focused structural assertions and the actual Gradle targets. Production/customer/payment/order/profile/address mutation, real checkout/payment, release signing, publication, PR and merge were intentionally **NOT RUN / NOT PERFORMED**.

P3-16 remains not started and unchanged. Passing Gate 1 builds/device tests does not establish production or release readiness.

## Gate 2 follow-up boundary

Gate 2 has not started. A future Gate 2 plan must begin from current source and decide how to prove a materially different non-production synthetic application against `:mobile-core`, including packaged identity/resources, capability and navigation differences, market/locale, Customer Account/Wishlist state, Firebase absence, domain/media policy, Home/Catalog fixtures, debug/release evidence and non-contamination checks. It must preserve every Gürbakır identity listed above and must not infer production authority.

Do not start that work from this handoff alone. Gate 2 requires its own explicit plan, evidence boundary and authorization.

## Repository-state boundary

At Gate 1 close-out:

```text
Push: PERFORMED — normal non-force push to origin/refactor/multibrand-gate-1-mobile-core
PR: #13 OPEN — updated by the same normal branch push
Merge: NOT PERFORMED
main mutation: NOT PERFORMED
Gate 2: NOT STARTED
```

The working branch and matching remote branch remain available for owner review. No production service was mutated.
