# P3-01 Production Shell and Real Home Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS — PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `4c097c17d052b38e8862a5ccb8bfd648e1f0403f`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the P3-01 production shell and real Home implementation authorized by the continuous Phase 3 goal. It does not claim the missing physical-device run as passing evidence and it authorizes no release or P3-16 work.

## User-visible outcome

- Cold start now enters the typed production `HomeRoute`, not the Phase 2 foundation/proof dashboard.
- Home contains exactly the approved `HOME_PRODUCT_RANGE` and `HOME_FEATURED_PRODUCT` sections in merchant order.
- Product Range resolves the five approved collection handles, drops null/empty/unsafe-media results, preserves the remaining order, and keeps valid content during a partial refresh failure.
- Featured Product resolves only the approved product handle and hides null, unavailable, or missing-safe-media results.
- Loading, partial, empty, connection/offline, configuration, service, refresh, and section-specific retry states are explicit. A startup network failure cannot remove the shell.
- Product Range and featured cards are intentionally non-interactive. Categories, Search, Wishlist, Account, Cart, checkout, quick-add, badges, and other later destinations are absent.
- An unavailable typed route opens the redacted recovery screen and offers only the functional Home destination.
- Turkish is the resource baseline, English app chrome is complete for this slice, the market is fixed to Turkey, and no market switch or onboarding screen is exposed.
- Shopify-returned money uses locale-aware formatting. A zero amount remains neutral `TRY` money and is never described as free, discounted, promotional, or intentional.

## Production architecture

The implementation preserves the Phase 2 single-activity Compose, Hilt, ViewModel/StateFlow, typed route, typed configuration, error, and Storefront boundaries.

- `StorefrontHomeGateway` adds only the two approved Home reads. `StorefrontApi` allows one configured Apollo instance to serve the existing commerce and new Home contracts.
- Generated Apollo operations query collection media with first-product fallback and the approved featured product money/media fields. Handles are variable-bound and validated before a request.
- `DefaultHomeContentRepository` runs the five independent collection reads concurrently, preserves configured order, compacts missing items, and maps only typed safe failure categories.
- `HomeViewModel` owns independently cancellable Product Range and Featured Product jobs. Retrying one section does not replace the other and valid content remains visible while refreshing.
- `HomeScreen` uses adaptive one/three/five-column behavior, 3:4 fit media frames, headings, safe drawing insets, neutral skeletons, localized money, and stable test semantics.
- The old `FoundationHome` and all integration proof destinations remain source-level Phase 2 harness material only. They are unreachable from the public production `GurbakirApp` graph.

## Media and dependency security boundary

`StorefrontMediaPolicy` accepts only HTTPS media whose host is exactly `cdn.shopify.com`, or exactly `gurbakir.com` with a path beginning `/cdn/shop/`. User-info, fragments, non-default ports, lookalike hosts, and other paths are rejected.

Coil `3.5.0` was added from its official artifacts with `coil-compose` and `coil-network-okhttp`. The singleton loader uses a dedicated OkHttp client with a network interceptor that checks every actual request hop, including redirect hops, against the same policy before bytes leave the process. Rejected origins produce only a generic error and no URL/token log. The version, rationale, license, dependency locks, and SHA-256 verification metadata are recorded in the repository.

No customer token, Admin credential, backend credential, signing material, private key, or Storefront token was written to source, documentation, test fixtures, command output, or logs.

## Read-only live Storefront evidence

On 2026-08-10 a single read-only GraphQL probe contacted only the validated project-owned `https://gurbakir.com` Storefront endpoint. The ignored local public token was read in process, used only as a request header, and never printed.

Observed result:

- five of five approved collection handles resolved;
- five of five collections contained at least one product;
- five of five selected tile media URLs used an approved host;
- the featured product resolved, was available for sale, and used `cdn.shopify.com` media;
- Shopify returned amount `0.0` with currency `TRY`.

The last point remains the already approved merchant data-quality item. The application renders the returned money neutrally and does not infer intent. The probe mutated no Shopify state.

## Automated evidence

The following completed successfully against the changed source:

```powershell
.\gradlew.bat --no-parallel :app:compileDevelopmentDebugKotlin --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessApply :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:assembleDevelopmentDebugAndroidTest --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessApply spotlessCheck detekt :app:lintDevelopmentDebug :storefront:lintDebug --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat --no-parallel spotlessApply :app:compileDevelopmentDebugAndroidTestKotlin :app:compileDevelopmentDebugKotlin --no-daemon --no-configuration-cache --console=plain
```

Results:

- production app Kotlin compilation: `PASS`;
- Storefront JVM tests: 21 tests, 0 failures, 0 errors, 2 existing conditional skips;
- app JVM tests: 20 tests, 0 failures, 0 errors, 0 skips;
- P3-01 added 12 JVM tests across generated-query mapping, exact media policy, repository behavior, zero money, locale fallback, and independent ViewModel retry/content retention;
- Android test APK assembly and the final Android test Kotlin compile: `PASS`;
- eight new instrumentation tests compile for loading, content, non-clickable semantics, partial retention, empty, offline retry, 200 percent text, typed route recovery, and saved-instance-state restoration;
- Spotless, all six module detekt tasks, app development Lint, and Storefront debug Lint: `PASS` (the final combined run completed 242 tasks with no finding);
- dependency locking covers development/staging, debug/release, unit/instrumentation, and lint configurations; verification metadata contains the new artifact hashes;
- `git diff --check`: `PASS`.

## Evidence not run

`adb devices -l` returned no connected device. Therefore the eight new Compose/navigation instrumentation tests were compiled but not executed, and the focused physical visual, TalkBack traversal, cold/warm/process-recreated launch, and real-image rendering checks are **NOT RUN**. They remain required evidence for integrated P3-15 acceptance or the first earlier checkpoint at which an approved device is connected.

This is an evidence availability gap, not permission to label device acceptance `PASS`. It does not block independent P3-02 implementation under the continuous owner goal because the production compile, schema contracts, JVM behavior, static analysis, live read-only data source, and instrumentation source all pass.

## Residual external and later-slice boundaries

- Current Shopify zero-price data remains merchant-owned and may be corrected without an app rewrite.
- P3-02 owns Categories, product listing, supported filter/sort, pagination, and the first navigation actions from Home.
- P3-04 owns product detail and media viewer interaction.
- P3-06 remains the first permitted Cart action, badge, or destination.
- Production Account, legal/support, deletion, update policy, telemetry/push, release identity, signing, store listing, and P3-16 remain outside this checkpoint.
- Final logo, custom font, campaign art, launcher/store assets, and release screenshots remain P3-16 inputs.

## Rollback

Revert only the local P3-01 checkpoint commit. This removes the Home schema/source/UI/tests and Coil dependency records while preserving the two earlier baseline/merchant commits. No remote data rollback is required because the only external operation was a read-only Storefront query.

## Exact next slice

P3-02 may begin from this checkpoint under the existing continuous goal: implement merchant-backed Categories, product listing, cursor pagination, and only schema-supported approved sort/filter controls; then make the currently non-interactive Home collection tiles functional. Do not expose Product Detail before P3-04 or Cart before P3-06.

## 2026-09-09 Gate 3 correction note

The historical P3-01 statement that an OkHttp network interceptor checks an image request before bytes leave the process is superseded. A network interceptor runs after connection establishment and therefore cannot provide the required pre-connect origin guarantee.

Gate 3 replaces that composition with the media-policy interceptor first among application interceptors, disables automatic HTTP/SSL redirects, validates the initial URL before the first `chain.proceed`, and manually resolves and validates every permitted redirect target before another proceed. Every media-producing mapper also receives the same app-derived policy. This dated correction preserves the original P3-01 record while making current security semantics explicit; it does not change the historical implementation status or authorize P3-16.
