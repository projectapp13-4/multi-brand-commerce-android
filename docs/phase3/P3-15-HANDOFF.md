# P3-15 Integrated Product Acceptance and Hardening Handoff

Date: 2026-08-13 (Europe/Istanbul)

Status: **P3-15 COMPLETE**

Branch: `main`

Starting checkpoint: `94a269f1068f0e15093597b748797171fb467af5` (`docs: checkpoint Phase 3 after P3-14`)

Implementation checkpoint: `f338ded` (`feat(app): harden integrated production boundary`)

This handoff closes P3-15. The implemented application journeys have been reconciled across the Phase 3 matrix, full local JVM and physical-device suites pass, the current Room migration chain is exercised on a real device, production deep links were launched through Android, critical accessibility behavior was checked with the device TalkBack service, localization was checked with both supported and Android pseudolocales, measured startup/route transitions meet the recorded P3-15 budgets, and the minified release graph contains no proof, synthetic, production-push, Analytics, or Crashlytics surface.

P3-15 does not make the unsigned development release distributable. It does not close the real remote account-deletion process, create a production application identity, provision release signing, claim a Play listing, publish Data Safety/privacy declarations, configure production Firebase/Shopify services, add production push or telemetry, or authorize a hard update gate. Those boundaries remain explicit for P3-16.

## Entry reconciliation

| Slice | State entering P3-15 | Current evidence boundary |
|---|---|---|
| P3-01 | COMPLETE | Production shell/Home and deterministic startup are implemented; earlier device gaps are superseded by the integrated physical suite and manual cold launches. |
| P3-02 | COMPLETE | Categories/listing, pagination, supported sort/filter, and typed collection deep links are implemented; a live collection route was opened on device. |
| P3-03 | COMPLETE | Debounced search and bounded partitioned Room history are implemented; current physical storage tests pass. |
| P3-04 | COMPLETE | Product detail, explicit variant state, safe unavailable recovery, and product deep links are implemented; a nonexistent product route safely recovered on device. |
| P3-05 | COMPLETE | Local Wishlist and Room `1 -> 2` migration are implemented; the migration and current repository behavior pass on device. |
| P3-06 | COMPLETE | Server-authoritative cart and four-state encrypted ownership are implemented and retained. |
| P3-07 | COMPLETE | Checkout Kit lifecycle was previously proven without payment/order placement; the production release graph retains the adapter and excludes proof UI. |
| P3-08 | COMPLETE | Owned legal/support baseline and exact external URL allowlist are implemented and remain reachable as critical routes. |
| P3-09 | COMPLETE | Hosted Customer Account OAuth/session and cart reconciliation are implemented; no credential or remote customer mutation was performed in P3-15. |
| P3-10 | COMPLETE | Approved first/last-name-only profile boundary remains implemented and memory-only. Live remote mutation remains externally unverified. |
| P3-11 | COMPLETE | Market-aware address UI/contracts and private-data handling remain implemented. Live remote mutation remains externally unverified. |
| P3-12 | COMPLETE | Private order list/detail/tracking policy remains implemented; order deep-link auth recovery was exercised on device without private-data disclosure. |
| P3-13 | PARTIAL / EXTERNALLY BLOCKED | The complete mobile request/local-clear boundary passes. Merchant acknowledgement, SLA, retention execution, and actual remote deletion remain unobserved and cannot be inferred. |
| P3-14 | COMPLETE | Typed non-blocking Remote Config/update policy, bounded cache, rollback behavior, and safe defaults pass; no hard gate exists. |

Last fully completed slice before this gate: **P3-14**.

P3-15 integrated acceptance is **COMPLETE**. P3-16 is the next slice and must preserve the P3-13 external blocker and all deferred feature decisions.

## Acceptance-matrix reconciliation

The authoritative product distribution remains **11 `ACCEPTED`, 8 `INTENTIONALLY_DIFFERENT`, 2 `DEFERRED`, 2 `EXTERNALLY_BLOCKED`, and 1 `NOT_APPLICABLE`**. P3-15 changes no product disposition.

| IDs | Status | Integrated P3-15 evidence |
|---|---|---|
| 01, 03, 04, 05, 07 | `ACCEPTED` | Cold startup, Home/catalog UI, real Storefront collection content, typed collection/product deep links, safe unavailable-product recovery, full JVM/device navigation and Compose coverage. |
| 09, 10, 22 | `ACCEPTED` | Existing cart ownership/Checkout lifecycle tests remain green; release artifact retains production adapters and contains no proof/synthetic checkout UI. No payment or order was placed. |
| 14, 15, 16 | `ACCEPTED` | Profile/address/order contracts and private lifecycle tests pass; order deep-link signed-out recovery was manually exercised; no private cache was added. |
| 02, 06, 08, 24 | `INTENTIONALLY_DIFFERENT` | No marketing onboarding; bounded local search and Wishlist remain partitioned/clearable; TR/EN key parity and en-XA/ar-XB pseudolocale behavior pass. |
| 11, 12, 19, 21 | `INTENTIONALLY_DIFFERENT` | Hosted account journey, allowlisted external pages, typed non-blocking update policy, and critical legal/support escape routes remain intact. No generic credential WebView or hard gate exists. |
| 20, 23 | `DEFERRED` | Production push, Analytics, and Crashlytics are absent. Firebase Messaging exists only in the debug proof graph and is absent from the release dependency/Dex/manifest graph. |
| 17 | `EXTERNALLY_BLOCKED` | Mobile request guidance and local cleanup are complete; remote acknowledgement/deletion/retention execution are externally unverified. |
| 18 | `EXTERNALLY_BLOCKED` in the historical matrix, superseded for mobile implementation by P3-08 | The owned `gurbakir-legal-baseline-1` routes are implemented and device-tested. Final production legal/privacy/store ownership and publication remain P3-16 release conditions. |
| 13 | `NOT_APPLICABLE` | No legacy password-reset feature was introduced; recovery remains in the hosted account journey. |

No placeholder or synthetic production destination was accepted as functional evidence. No unimplemented or externally unverified item is labelled PASS.

## P3-15 hardening implementation

### Production entry and route lifecycle

- Added the missing exact HTTPS order deep-link prefix `/apps/mobile/orders/` to `MainActivity`.
- Removed `singleTask` from the production activity. Default Android `standard` launch behavior now lets Navigation Compose consume each new typed deep-link entry without stale manual `onNewIntent` proof state.
- Removed the Firebase notification-proof nonce and notification proof-intent handling from production `MainActivity` and `GurbakirApp`.
- Retained the three app-owned deep-link families: collection, product, and authenticated order. Unknown/unavailable targets continue to use typed route recovery without rendering raw IDs or URIs.

### Proof and production separation

- Moved Foundation, commerce, Customer Account, and Firebase proof controllers/screens/ViewModels/routes from `app/src/main` to `app/src/debug` without changing their debug behavior.
- Moved their JVM tests from `app/src/test` to `app/src/testDebug` and added a debug-only Hilt `ProofModule`.
- Removed proof providers from production Hilt modules.
- Removed the custom proof-only `GurbakirFirebaseMessagingService` and release proof target recorder.
- Moved Firebase push-registration proof contracts and runtime inspector from `firebase/src/main` to `firebase/src/debug`.
- Changed Firebase Messaging to `debugImplementation` and removed the app module's direct Messaging dependency.
- Moved Messaging auto-init/installations/metrics metadata to the debug manifest. Production still declares no notification permission or messaging service.
- Updated dependency locks so Messaging/data-transport/cloud-messaging scopes are debug-only while verified dependency versions remain pinned.
- The main source resource set still contains some reusable proof copy for the debug source graph. Resource shrinking proves those strings are absent from the minified release artifact; no production route can reach them.

### Localization hardening

- Enabled Android pseudolocales for debug builds.
- Added physical instrumentation coverage proving one shared production key resolves to Turkish and English, en-XA expands production copy, and ar-XB resolves with RTL layout direction.
- Verified base/English key parity: **525 / 525**, missing from either locale: **0 / 0**.

## Production route and artifact ledger

### Merged-manifest result

| Boundary | Development debug | Development release |
|---|---|---|
| Core permissions | Internet/network state | Internet/network state |
| Notification permission / FCM receiver or service | Present only through the debug Messaging proof graph | Absent |
| Messaging auto-init metadata | Present and `false` | Absent |
| Collection/product/order app links | Present | Present |
| MainActivity launch mode | Android default (`standard`) | Android default (`standard`) |

The release manifest's separately observed `launchMode=2` belongs to AppAuth's `AuthorizationManagementActivity`, not `MainActivity`.

### Minified release artifact inspection

The unsigned development release contains zero matches for all of the following:

- `CommerceProof`, `CustomerAccountProof`, `FirebaseProof`, `FoundationViewModel`, `FoundationProofApp`;
- `GurbakirFirebaseMessagingService`, `BuildVariantFirebaseProof`;
- `com.google.firebase.messaging`, Firebase Analytics, or Crashlytics classes;
- `integration_proof_title`, `commerce_proof_title`, `customer_account_proof_title`, `firebase_proof_title`;
- notification-opened proof copy or Turkish/English synthetic-cart/proof labels.

Required production application classes, navigation, Storefront, account, checkout, local storage, legal/support, and update-policy resources remain present.

## Persistence, migration, and privacy inventory

| Owner | Persistent material | Version / partition | P3-15 result |
|---|---|---|---|
| Room `gurbakir-local.db` | Search history/settings and local Wishlist product references | Schema `2`; explicit exported `1.json` and `2.json`; environment + market partitions where applicable | Actual `1 -> 2` migration and current search/Wishlist clear/partition behavior pass on device. No destructive fallback exists. |
| Customer session store | OAuth/customer session payload ciphertext and IV | Keystore AES-GCM; environment-specific preference and key alias; payload v2 | Existing JVM/device lifecycle/terminal-session tests pass; no token was logged or added to evidence. |
| Cart session store | Cart capability ID, expiry, four-state ownership ciphertext and IV | Keystore AES-GCM; environment-specific preference and key alias; legacy payload v1 migrates conservatively to quarantined; current payload v2 | Existing ownership, restoration, cleanup, and Checkout tests remain green; no raw cart ID was recorded. |
| Update policy store | Schema/source, bounded typed booleans/version values, fetch/deferral timestamps | App-private `bounded-update-policy`; 24-hour expiry | Device recreation/expiry/malformed/clock-skew tests pass; no identifier, URL, token, or user data is stored. |
| Profile/address/order models | Current authenticated private UI state | Memory-only | No persistent private cache was introduced. Lifecycle/session clearing remains covered. |
| Packaged legal/brand/locale data | Public app-owned resources | Versioned package content | Retained; no remote code/copy/asset injection exists. |

Android cloud backup and device transfer exclude shared preferences, databases, and files. P3-15 introduced no schema or data migration beyond exercising the existing Room chain.

## Validation evidence

### Local deterministic gates

- `spotlessApply` and `spotlessCheck`: **PASS**.
- `:firebase:detekt` and `:app:detekt`: **PASS**.
- `:app:lintDevelopmentDebug`: **PASS**, final report contains zero issue elements.
- Full six-module JVM group: **244 tests, 242 PASS, 0 failures/errors, 2 existing conditional live-proof skips**:
  - Foundation: 10;
  - Account: 56;
  - Checkout: 5;
  - Storefront: 38 with two existing conditional skips;
  - Firebase: 5;
  - App: 130.
- Development debug app and Android-test APK assembly: **PASS**.
- Minified unsigned development release assembly including Lint Vital: **PASS**.
- `gitleaks detect` across 44 commits and `gitleaks protect --staged --redact`: **PASS**, no leaks.
- `git diff --check` and staged diff check: **PASS**.
- `scripts/Test-Phase3Planning.ps1` on clean implementation checkpoint `f338ded`: **15/15 PASS**, features 24, screens 27, slices 17.

One release build initially failed because Gradle 9.4.1 omitted already resolved Kotlin standard-library scopes while serializing dependency locks after Messaging became debug-only. The existing same-version lock entry was reconciled with those resolved scopes; no version was changed. The minified release build then passed. No product failure remains hidden behind this tooling correction.

### Physical instrumentation

Device: Samsung SM-A225F (`R68RC006LPE`), Android 13 / API 33, 720 x 1600, authorized over ADB.

Final complete matching-APK run: **83 / 83 PASS**, zero failures/errors, 77.177 seconds. This run includes:

- current production Compose screens and navigation;
- local search and Wishlist persistence, partitioning, and clear behavior;
- the actual Room `1 -> 2` migration;
- cart/session ownership and lifecycle behavior appropriate to the installed debug environment;
- legal/support, account, profile, address, order, deletion-boundary, and update-policy UI contracts;
- 200-percent text cases;
- Turkish/English and three new pseudolocale/RTL resource tests;
- typed Remote Config safe-default/cache behavior.

### Manual Android deep-link proof

Android package resolution selected only `MainActivity` for the three owned URL families.

| Scenario | Result | Observed launch/transition time |
|---|---|---:|
| Cold collection deep link | Opened `Tencereler` with four live Storefront products | 2,653 ms |
| Warm nonexistent product `123?variantId=456` | Safe localized unavailable-product recovery; no raw ID shown | 536 ms |
| Warm order `1001` while signed out | Safe Account entry; no password/code collection or order existence disclosure | 534 ms |

Screenshots were retained only under ignored build output: `build/p3-15-collection.png`, `build/p3-15-product.png`, and `build/p3-15-order.png`.

### Performance budget and samples

P3-15 records a conservative local low/mid-range-device budget of **<= 5,000 ms for cold owned deep-link launch** and **<= 1,500 ms for a warm owned route transition**. It is an integrated regression threshold, not a Play production SLA.

- Five cold launch samples: 2,588; 2,639; 2,579; 2,533; 2,525 ms. Average 2,572.8 ms; maximum/p95 sample 2,639 ms: **PASS**.
- Alternating non-zero warm transitions: 1,334; 653; 695; 631 ms. Maximum 1,334 ms: **PASS**.
- One Android-reported `0 ms` no-op transition was excluded rather than treated as a performance success.

### Accessibility proof and restoration

- The device's existing Kaspersky accessibility service was recorded before testing.
- Samsung TalkBack was enabled temporarily without accepting its optional phone permission; service binding and a visible TalkBack focus ring were observed on Home/Categories.
- Keyboard Tab focus reached Cart, and Enter opened the empty Cart with `Your cart is empty` and `Continue shopping` exposed.
- TalkBack was then disabled and the exact pre-existing Kaspersky accessibility service was restored. `accessibility_enabled` remains `1` for that restored service.
- The app was force-stopped to a neutral state after proof.

This is focused critical-route evidence, not a claim that every sentence was manually reviewed with speech output on every supported device.

### Security/privacy regression boundary

- Only app-owned Shopify/Firebase endpoints and the existing exact external-route allowlists remain in production code.
- Release manifest/Dex/resources exclude deferred Messaging/telemetry and all proof/synthetic surfaces.
- No secrets, session/cart IDs, raw deep-link values, customer PII, Firebase Installation ID, or payment data were added to source, documentation, screenshots, or ordinary logs.
- No payment, order, profile, address, account, deletion request, Remote Config publication, notification, or synthetic remote record was submitted.
- The unrelated Samsung `com.sec.android.diagmonagent` system crash noise observed in logcat was not a Gur Bakir process failure; no Gur Bakir fatal marker was present in the checked application scope.

## APK artifacts

Current matching artifacts:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 20,176,991 bytes
  - SHA-256 `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,493,645 bytes
  - SHA-256 `C8B4CC7BF002FB1C3CC23F68A1641224D894561E6EA5CF02B4DEF9E248A96141`
- `app/build/outputs/apk/development/release/app-development-release-unsigned.apk`
  - 3,499,407 bytes
  - SHA-256 `6F1F1713C7FEE1165390B7B1017940A06F103ECD7AA883C4A32F141A64AFD78E`

The debug APK is the artifact suitable for current manual/device testing. The Android-test APK must be paired with that exact debug artifact. The release APK is minified, unsigned, uses the development identity, and is suitable only for release-graph inspection; it must not be distributed as a production release.

## External and workstation state

- Shopify Admin, Customer Account configuration/data, Firebase, Remote Config, backend, store data, accounts, profiles, addresses, carts, orders, payments, and synthetic remote records: **UNCHANGED** by P3-15.
- Storefront access during Home/collection/deep-link proof was read-only.
- Firebase/Remote Config publication, rollback, notification sending, and registration actions: **NOT RUN**.
- Physical device installation and instrumentation: **PERFORMED** only for the development app/test packages.
- Camera, media, contacts, location, messages, unrelated applications, reference APK, and third-party reference services: **NOT ACCESSED**.
- Browser/Edge service state: **UNCHANGED** by P3-15. No active browser automation remains.
- Five required local configuration files remain present, ignored, and untracked; no credential value is recorded here.
- Stash is empty and the repository has no remote. Only normal idle Gradle/Kotlin daemons remain; no wrapper build is active.

## P3-15 exit and exact P3-16 entry

P3-15 exit criteria are satisfied for every locally implementable item:

- matrix rows resolve to this evidence ledger or an explicit blocker/deferred disposition;
- integrated local and device suites pass;
- no severity-high functional, accessibility, or ordinary implementation-security defect was found;
- recorded startup/route budgets pass;
- the production route/release graph contains no proof, synthetic, push, Analytics, or Crashlytics surface;
- migration, persistence, localization, critical accessibility, lifecycle, and deep-link evidence are current.

P3-16 may now begin only as **production/release readiness reconciliation**:

1. Verify `main` contains implementation checkpoint `f338ded` plus the documentation checkpoint containing this handoff, with a clean worktree, empty stash, the five ignored configuration files present, no active wrapper build, and the current device state recorded.
2. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, the P3-16 roadmap entry, the acceptance matrix, P3-13/P3-14 handoffs, and current release/build configuration.
3. Start with a read-only inventory of production package/application identity, signing source, Play ownership/listing, Shopify/Firebase production configuration, App Links/domain verification, Customer Account callbacks, Data Safety/privacy declarations, deletion/support/release owners, dependency/license/SBOM state, schema/rollback policy, and release automation.
4. Run the smallest release-graph/configuration checks needed to distinguish project-completable work from owner/external provisioning. Do not repeat the unchanged 83-test physical suite or foundation proofs merely for reassurance.
5. Preserve groups 20 and 23 as deferred; do not add production push, Analytics, Crashlytics, or a hard update gate without separate product/privacy/release authorization.
6. Preserve feature group 17 as externally blocked until a real owned deletion process provides observable acknowledgement, SLA/retention execution, and remote completion evidence.
7. P3-16 cannot declare a releasable production candidate while package/signing/Play/service/App Links/Data Safety/legal/deletion/support/release ownership is absent, proof/synthetic/deferred SDK content appears in the release graph, or rollback is undefined.

## Rollback

Revert implementation checkpoint `f338ded` to restore the previous activity launch mode/notification-proof handling, proof classes in the main source set, and Messaging in the release graph. That rollback is not recommended because it would reintroduce the production-boundary defects P3-15 closed. No server, Shopify, Firebase, customer, cart, order, payment, or device-data rollback is required because P3-15 made no external mutation.
