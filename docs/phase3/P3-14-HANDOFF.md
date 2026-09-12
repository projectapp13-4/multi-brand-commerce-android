# P3-14 Safe Remote Config and Optional Update Policy Handoff

Date: 2026-08-13

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS**

Live development Remote Config fetch status: **PASS WITH SAFE DEFAULT VALUES**

Hard minimum-version gate status: **NOT IMPLEMENTED — DEFERRED BY PRODUCT DECISION TO P3-16 GOVERNANCE**

Branch: `main`

Starting checkpoint: `3bc61b8cb2bc6155bddb4dc84d0be880a4978996` (`docs: checkpoint Phase 3 after P3-13`)

Implementation checkpoint: `9165bcc3ce050925264fd047e91a92bc24a083f4` (`feat: add P3-14 safe remote update policy`)

This handoff closes P3-14. The production application now has a bounded, localized, non-blocking maintenance/optional-update policy with typed Remote Config input, deterministic local defaults, an expiring app-owned cache, version comparison, retry/defer behavior, rollback semantics, and a persistent public help/legal escape route. It does not add or authorize a force-update gate, Play Store destination, arbitrary remote copy/URL/route/asset/code, Analytics coupling, production Firebase identity, release signing, or production rollout. P3-15 integrated acceptance may proceed from this checkpoint; P3-16 retains all release governance and any possible hard-gate decision.

## Authority and governing decisions

- Feature group 21 remains `INTENTIONALLY_DIFFERENT`: Remote Config is useful, but an ungoverned force-update mechanism can strand users and remove support/legal access.
- The P3-14 product decision is **no hard gate**. There is no minimum-version Remote Config key, blocking destination, countdown, disabled navigation, or forced external launch in this implementation.
- `BuildConfig.VERSION_CODE` is the sole installed-version source. Current development version code is `1`.
- Remote policy input is owned by the mobile application/Firebase development or staging operator. Release ownership, Play identity/listing, grace periods, support staffing, emergency release handling, and any enforcement rule remain P3-16 decisions.
- Remote Config values are treated as untrusted typed input. They can select only packaged behavior/copy; they cannot supply text, URLs, routes, hosts, assets, executable code, credentials, consent overrides, or security relaxations.
- Firebase Messaging consent/registration remains separate and unchanged. P3-14 adds no FCM auto-init, notification permission request, Analytics, Crashlytics, advertising ID, or telemetry event.
- The product host performs one explicit app-owned asynchronous refresh when its process-level policy ViewModel starts. The UI first resolves packaged defaults or an unexpired local cache and never waits for the network. The Firebase SDK enforces a 12-hour minimum fetch interval and a 10-second fetch timeout. No real-time listener or screen-transition fetch loop exists.
- Android/Firebase implementation contracts were checked against the current official Firebase Remote Config Android setup, loading-strategy, API, and settings documentation on 2026-08-13:
  - <https://firebase.google.com/docs/remote-config/android/get-started>
  - <https://firebase.google.com/docs/remote-config/loading>
  - <https://firebase.google.com/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig>
  - <https://firebase.google.com/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfigSettings.Builder>

## Approved policy schema

Exactly these packaged parameters are read:

| Parameter | Type | Safe local default | Bounded effect |
|---|---|---:|---|
| `mobile_policy_schema_version` | strict long | `1` | Entire snapshot is accepted only for schema `1`; any other/malformed value returns all-safe defaults. |
| `mobile_policy_revision` | strict long, `0..1,000,000,000` | `0` | Scopes maintenance dismissal and operator rollback/republication. Invalid values become `0`. |
| `maintenance_message_enabled` | strict lowercase boolean | `false` | Shows a dismissible packaged maintenance notice; commerce/navigation is not blocked. |
| `checkout_preload_enabled` | strict lowercase boolean | `false` | Preserves the pre-existing bounded flag contract; P3-14 does not add a new consumer or alter Checkout ownership. |
| `optional_update_message_enabled` | strict lowercase boolean | `false` | Allows a packaged optional-update notice only when the recommended version is newer. |
| `recommended_version_code` | strict long in the non-negative Android `Int` range | `0` | Compared only with packaged `BuildConfig.VERSION_CODE`; it cannot block the app or choose a destination. |

No `minimum_supported_version_code`, force-update, arbitrary message, URL, host, route, asset, or code parameter exists. Unknown Remote Config keys are never read. Boolean aliases such as `1` or uppercase values are rejected to `false` rather than interpreted permissively.

## Completed implementation

### Remote adapter and cache

- Expanded the Phase 2 `RemoteFeatureFlags` adapter with an immutable `RemotePolicySnapshot` while retaining the existing typed boolean interface and local-default implementation.
- Installs all six safe local defaults before fetch, configures explicit SDK cadence/timeout, fetches/activates once per policy host process, reads only approved keys as strings, and performs strict app-owned parsing.
- Preserves coroutine cancellation. Ordinary configuration, Firebase, fetch, activation, type, or initialization failure resets the adapter snapshot to safe defaults and returns `LocalDefaults`.
- Records only the SDK last-successful-fetch timestamp. A timestamp must be positive, no more than five minutes in the future, and less than 24 hours old before remote values can replace a valid cache.
- Added app-private `SharedPreferences` storage `bounded-update-policy` with storage schema/source, typed values, fetch time, and an exact 24-hour expiry. Wrong types, unknown schema/source, impossible ranges, malformed duration, expiry, or excessive clock skew fail closed.
- The store contains no token, Firebase Installation ID, user/account/cart/order data, URL, free text, or credential. Android backup remains disabled by the existing manifest.

### Product policy and UI

- Added `DefaultUpdatePolicyController`, `UpdatePolicyViewModel`, Hilt bindings, and a root production update-policy host.
- Startup uses safe defaults or a still-valid cache immediately; asynchronous fetch never blocks Home, primary navigation, account, cart, legal, or support routes.
- An optional update notice exists only when both the flag is true and `recommended_version_code` is greater than the installed version. Equal/older/malformed values are absent.
- The optional notice offers **Check again**, **Help and policies**, and **Later**. `Later` is stored for exactly 24 hours for that recommended version. A higher recommended version is not hidden by an older deferral.
- Maintenance takes display priority over the optional update notice and can be dismissed for the current saved-state lifetime/revision. Operators must increment the revision to re-announce changed maintenance content in the same process; a fresh process can show an active notice again.
- Remote false values overwrite an unexpired true cache on the next successful fetch. This is the emergency rollback path. Offline users may see the last non-blocking notice only until its 24-hour expiry; no cached value can hard-block them.
- All displayed text and action labels are packaged TR/EN resources. The card has a heading and polite live-region semantics; physical UI proof includes 200-percent text.
- **Help and policies** always navigates to the existing public typed `LegalSupportRoute`. P3-08 exact owned URL policy remains the only web-launch boundary.
- There is no update-store action because a permanent approved Play identity/listing does not yet exist. The app does not invent or open a distribution URL.

### Production/proof separation

- The production `GurbakirApp` hosts only `UpdatePolicyDestination`; it has no manual Remote Config proof button or proof route.
- `ProductionApp.kt` contains no `FirebaseProofScreen`, proof ViewModel/controller, or manual refresh action.
- `apkanalyzer dex packages --defined-only` against the minified development release found no Firebase, commerce, customer-account, or foundation proof class names. R8 retains the production policy behavior while removing unreachable proof UI classes from this release artifact.
- Reusable Firebase, push-consent, and deterministic test adapters remain; P3-15/P3-16 own the final repository-wide proof-removal ledger.

### Main changed files

Production:

- `firebase/src/main/kotlin/com/gurbakir/firebase/FirebaseContracts.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/update/UpdatePolicyContracts.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/update/AndroidUpdatePolicyStore.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/update/UpdatePolicyViewModel.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/update/UpdatePolicyBanner.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/di/UpdatePolicyModule.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Tests:

- `firebase/src/test/kotlin/com/gurbakir/firebase/FirebaseRemoteFeatureFlagsTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/update/UpdatePolicyControllerTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/update/UpdatePolicyViewModelTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/update/AndroidUpdatePolicyStoreTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/update/UpdatePolicyBannerTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/FirebaseProofViewModelTest.kt` (result-contract compatibility only)

No dependency, Gradle catalog, Room schema/migration, GraphQL operation, manifest permission, deep link, backend, Storefront/Customer Account API contract, cart/session owner, notification schema, or service endpoint changed.

## Validation evidence

### Focused and broad local checks

- Remote adapter focused JVM: **3/3 PASS** during the first focused run; final complete Firebase module suite: **5/5 PASS**, zero failures/errors/skips.
- P3-14 controller/ViewModel focused JVM: **8/8 PASS**. Existing Firebase proof contract compatibility: **4/4 PASS**.
- Final complete app JVM: **130/130 PASS**, zero failures/errors/skips.
- Spotless apply/check: **PASS**.
- Firebase Detekt: **PASS**.
- App Detekt: **PASS**.
- Firebase Debug Lint: **PASS**.
- App Development Debug Lint: **PASS**, zero final errors.
- Final combined `spotlessCheck :app:detekt :app:lintDevelopmentDebug`: **PASS** in 2 minutes 51 seconds after the 200-percent device-test source was added; 231 tasks, 6 executed and 225 up-to-date.
- Development Android-test Kotlin compilation: **PASS**.
- Development debug app and Android-test APK assembly: **PASS**.
- Minified unsigned development release assembly: **PASS** in 21 minutes 22 seconds. The original shell capture timed out at 15 minutes without terminating the healthy wrapper; the preserved Gradle daemon completed successfully and produced the recorded artifact.
- `gitleaks detect --source . --no-banner --redact --exit-code 1`: **PASS**, 43 commits and approximately 4.35 MB scanned, no leaks.
- `git diff --check` and staged diff check: **PASS**.
- `scripts/Test-Phase3Planning.ps1`: **15/15 PASS**, with 24 feature rows, 27 unique screens, all 17 roadmap slices represented, ignored local configuration present, and repository boundaries intact.
- Release manifest identity is `com.gurbakir.mobile.dev`, version code `1`, version name `0.1.0`, min SDK 23, target SDK 36. It is not a production identity or signed release.

Direct implementation-time failures were fixed and rerun:

- DI provider visibility initially exposed internal types; providers were kept internal and the focused JVM build passed.
- The local Compose test API did not provide `assertDoesNotExist`; tests use the repository-standard zero-node assertion and Android-test compilation passed.
- Detekt reported four method/return-count structure issues; code was split/simplified and final Detekt passed.
- Lint requested KTX preference edits at two sites. The synchronous `commit()` boolean is part of the fail-closed acknowledgement contract, so two method-local documented `UseKtx` suppressions were used; final Lint passed.
- No product test failed. No failed operation is represented as PASS without a successful rerun.

### Physical device and live startup

Device: Samsung SM-A225F, Android 13 / API 33, 720x1600, authorized over ADB.

Final matching-APK physical group:

- `UpdatePolicyBannerTest`: **4/4 PASS**, including optional/maintenance priority, retry/help/defer actions, safe-default absence, and 200-percent text action visibility.
- `AndroidUpdatePolicyStoreTest`: **2/2 PASS**, including storage recreation/process-style restoration, exact expiry, deferral, malformed metadata, and clock-skew failure.
- `LegalSupportScreenTest`: **3/3 PASS**, including 200-percent text and public owned-page recovery/access.
- `ProductionNavigationTest`: **8/8 PASS**, including typed critical-route recovery and legal/support reachability.
- Final combined physical result: **17/17 PASS**, zero failures/skips.

The current app APK was then cold-started independently:

- Activity launch: **PASS**, `TotalTime=2519 ms`, `WaitTime=2528 ms`.
- Home and primary navigation were present after eight seconds: **PASS**.
- Process-scoped logcat contained no fatal/AndroidRuntime failure: **PASS**.
- Temporary instrumentation preferences were absent after cleanup: **PASS**.
- An earlier 12-second cold-start observation also showed complete Home/catalog/primary navigation and no fatal log; the captured screenshot remained under ignored build output only.

The configured development Remote Config project returned a successful fetch timestamp. The app-private cache contained only schema/source metadata, all-safe `false/0` typed values, and an exact 24-hour expiry. No raw Firebase identifier or credential was displayed, logged, copied, or recorded. Live non-default notice publication and Firebase-console rollback were **NOT RUN** because backend values/configuration were not changed; parser/UI/cache/rollback behavior is covered deterministically and the live safe fetch proves the current owned development adapter path.

Manual full TalkBack speech review, staging-device runtime repetition, a production Firebase app, signed/Play update flow, and hard-gate behavior are **NOT RUN / OUTSIDE P3-14**. No unexecuted check is labelled PASS.

## APK artifacts

Current matching artifacts:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 20,549,695 bytes
  - SHA-256 `CF07C8288B0EC1CEC263B56CF2699E323E32D13A935EF59DEB3BE06B837667BD`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,350,645 bytes
  - SHA-256 `A8F866393C954AD6FE35D687B393337ADA133C0086D366D343A2795EF14B658E`
- `app/build/outputs/apk/development/release/app-development-release-unsigned.apk`
  - 3,800,517 bytes
  - SHA-256 `91F3446C5F7A2F9690A028E1EB331CF365062B43124F3F93FB4AD919942B8083`

The debug APK is suitable for current manual/device testing. The Android-test APK must be paired with this matching debug application artifact. The release APK is minified and useful only for local release-graph/build validation; it is unsigned and not suitable for distribution.

## External and workstation state

- Firebase/Remote Config configuration and backend values: **UNCHANGED**. The development client performed a normal read/fetch and may create/refresh project-scoped Firebase Installations metadata as required by the official SDK; no raw identifier was exposed or retained in evidence.
- Shopify Admin, Customer Account configuration/data, Storefront configuration, backend, accounts, profiles, addresses, carts, orders, payments, and synthetic remote records: **UNCHANGED**. Cold-start Home performed only ordinary read-only catalog access.
- No Remote Config parameter was published, changed, rolled back, or deleted in the console. No notification was sent or registration action performed.
- No camera, media, contacts, location, messages, unrelated application, reference APK, or third-party reference service was accessed.
- No signed-in Edge/Chrome session was used or changed. Public official Firebase documentation was read through the web research tool only.
- The development and matching Android-test APKs were installed/replaced on the authorized device. App data was retained; only uniquely named empty test preference files from the first instrumentation run were removed, and the tests now delete their own temporary stores.
- Required ignored local configuration files remain present, ignored, and untracked. No credential value appears in this handoff.
- No dependency/tool/SDK/library was installed for P3-14.

## Acceptance and exact next slice

P3-14 is **COMPLETE**. Feature group 21 remains intentionally different by design: optional notice and bounded flags are implemented, normal startup is usable under absent/offline/malformed/stale config, cache and deferral expire, rollback is fail-safe, and critical legal/support routes remain available. The force-update gate is absent.

The exact next slice is **P3-15 Product acceptance and hardening**:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, the P3-15 roadmap entry, `PHASE-3-ACCEPTANCE-MATRIX.md`, and the existing P3-01 through P3-14 handoffs as routed evidence; do not restart accepted slices.
2. Verify `main` contains implementation checkpoint `9165bcc` plus this documentation checkpoint, with a clean worktree, empty stash, required ignored configuration present, no active wrapper build, and current device state recorded.
3. Preserve P3-13 feature group 17 as `EXTERNALLY_BLOCKED`; do not submit a deletion request or convert local cleanup/browser return into remote proof.
4. Begin with the P3-15 entry criteria: reconcile every P3-01 through P3-14 disposition, current migration/data inventory, proof-versus-production removal ledger, integrated acceptance suite, accessibility/localization/lifecycle matrices, and remaining external/merchant-owned items.
5. First targeted action: inventory current production routes, proof classes/release reachability, persistent stores/schema versions, permissions, and all handoff test counts; then run the smallest deterministic integrated validation group needed to establish a current baseline.
6. P3-15 exit requires accumulated product acceptance evidence and honest dispositions across all in-scope slices. Do not label P3-13 remote deletion, production identity/signing, Play distribution, production Firebase/push, hard update enforcement, Analytics/Crashlytics, or unrun manual/accessibility/external checks as PASS.
7. Do not begin P3-16 release/privacy/security governance, Play release, production rollout, or a separate comprehensive security-hardening campaign before P3-15 is coherently completed and checkpointed.

## Rollback

Revert implementation checkpoint `9165bcc` to remove the P3-14 typed schema, policy cache/controller/ViewModel/banner, production host, resources, Hilt bindings, and tests together. Existing Firebase safe defaults, messaging consent, legal/support routes, application data, and external service configuration require no server rollback because P3-14 changed no backend value. Deleting the app-private `bounded-update-policy` preference is optional and recoverable; absence resolves to safe defaults.
