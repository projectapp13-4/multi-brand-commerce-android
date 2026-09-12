# Phase 2 Native Android Foundation Report

Date: 2026-08-06
Overall result: **PASS — all eight Phase 2 foundation gates pass**

This report covers the Phase 2 foundation only. It does not declare the 24-feature Gürbakır application complete or production-ready.

## 1. Platform, provenance, and architecture

- Native Android with Kotlin and Jetpack Compose is governed by ADR-0001; typed Navigation Compose 2 routes are governed by ADR-0002; the final Android API 36/JDK 17 baseline is governed by ADR-0003.
- The six modules are `app`, `foundation`, `storefront`, `account`, `checkout`, and `firebase`, with single-activity Compose, Hilt, StateFlow/ViewModels, project-owned interfaces, and separate Shopify schemas/clients.
- The sibling Flutter prototype remains a read-only owned configuration/provenance reference with zero platform-selection weight.
- The Business Partner/reference APK remains immutable behavioral and architectural evidence. It was not installed, launched, rebuilt, copied, or used as a source-code/credential donor; third-party reference infrastructure was not contacted.
- Phase 1 preparation, the governing Phase 2 prompt, the ADRs, and the historical gap analysis formed the authority chain at this checkpoint. The 24 candidate feature groups were decisions for the subsequent implementation phase, not already completed screens. Current dispositions are in the Phase 3 acceptance matrix, with compact historical knowledge under `docs/reference-model`.

## 2. Build, variants, localization, and brand reuse

- JDK 17, Gradle 9.4.1, AGP 9.2.1, Kotlin/Compose plugin 2.3.10, `compileSdk=36`, `targetSdk=36`, and provisional `minSdk=23` are pinned.
- Development/staging and debug/release variants use finalized non-production application IDs. There is intentionally no production flavor, package ID, Firebase project, OAuth callback, or signing identity yet.
- Turkish baseline and English resources back all foundation UI. Stable semantic/test tags do not depend on translated strings.
- Neutral typed brand identity, design tokens, locale support, assets/legal placeholders, feature identifiers, and analytics namespace are owned by `foundation`; `app` supplies the first compile-time `GurbakirBrand` selection.
- Deterministic Compose screens cover foundation status, Customer Account proof, synthetic cart/Checkout proof, and explicit Firebase proof without exposing sensitive identifiers.

## 3. Configuration and external services

- The paid-but-unlaunched Gürbakır store is the owner-designated non-production Shopify environment; plan type is not used as an environment classifier.
- The existing Public Web Customer Account client and Storefront token were preserved. The dedicated `Gürbakır Android Dev` Headless storefront owns the Public Mobile client, registered callback, public client ID, and controlled public Storefront configuration.
- Development Firebase maps to `Shopify App`; staging maps to the separate Spark-plan `Gurbakir Android Staging`. Four exact ignored `google-services.json` files map to the finalized debug/release application IDs.
- No production Firebase project/app, billing, backend, Firestore, Realtime Database, Storage, Firebase Authentication, service-account key, Admin token, client secret, or private signing material was created.
- Tracked defaults stay empty/disabled. Required public client values live only in ignored typed local configuration. Removing the ignored inputs returns the tree to a visible fail-closed state.

## 4. Storefront and cart — PASS

- Storefront API `2026-07` and Apollo 5.0.1 generate typed shop/catalog and cart create/read/add/update/remove/buyer-identity operations.
- The gateway validates owned URLs, bounded quantities/lines, complete opaque cart IDs, stable error codes, and redaction. The coordinator retains valid carts through transient failures and clears only definitive invalid/expired/completed state.
- Owned-service proofs exercised bounded synthetic cart creation/removal/add/update/read/final cleanup. The physical Compose proof also observed Shopify quantity normalization as one bounded warning without losing valid state.
- The Android 13 phone proved cart Keystore round-trip, corruption failure, and restoration after force-stop/reinstall/relaunch.

## 5. Customer Account OAuth/PKCE — PASS

- The separate Public Mobile configuration, live discovery, system browser, S256 PKCE, exact callback/state/nonce, one-time transaction, and end-session boundary are implemented.
- AppAuth owns browser/end-session presentation only. Exact OkHttp form POSTs perform current Shopify code exchange/refresh, including the provider behavior where `token_type` may be omitted but a present value must be `Bearer`.
- The separate Customer Account Apollo client uses the current raw-token `Authorization` contract and typed identity mapping; it does not invent a Bearer prefix or expose identity/errors.
- The physical phone proved browser callback/PKCE, live exchange, typed identity, refresh, Keystore restoration, logout/end-session, and signed-out state using a synthetic account.

## 6. Checkout Kit and Bogus Gateway — PASS

- Official Checkout Kit Android `3.5.4` is isolated behind the project adapter with exact-host checkout policy, refresh-before-launch, preload invalidation, and default-deny permission/file/geolocation/external-link behavior.
- The phone proved preload, password-page transition while preserving store protection, real checkout rendering, cancellation/cart retention, Bogus approval/completion/cart clearing, relaunch restoration, Bogus decline/no false completion, and final cart cleanup.
- Read-only Shopify Admin reconciliation verified one paid test order. The decline attempt created no second visible order.
- No real payment method, live payment, real customer/order data, or production mutation was used.

## 7. Firebase — PASS

- Development/staging isolation, four exact registrations/files, debug SHA-256 fingerprints, Gradle Google Services consumption, and physical one-default-app initialization with Messaging auto-init disabled are proven.
- The current foundation adds false local defaults for two exact Remote Config booleans, explicit fetch/fallback behavior, manual FCM `register()`/`unregister()` only after Android permission and user action, stored non-sensitive consent state, redacted temporary debug target handling, an exact single-field route allowlist, fixed local notification text, and an explicit immutable app `PendingIntent`.
- Analytics and Crashlytics SDKs are absent. Manifest collection, advertising-ID, FCM auto-init, and FCM delivery-metrics export remain disabled.
- The current development/staging app suites executed eleven tests each on the approved Android 13 phone. The development debug build then proved explicit Android permission and manual Installation ID registration, exactly one synthetic Console delivery from the verified development project, local notification display, allowlisted tap navigation, explicit Remote Config fetch/activate, unregister, and temporary-target cleanup.
- The Console proof used the current Firebase Installation ID contract; no deprecated token retrieval, raw identifier output, campaign publication, billing change, or production project was introduced.

## 8. Security boundaries

- OAuth/customer session, cart capability, checkout URL, Firebase target, public client configuration, Admin/backend secrets, and signing material have separate ownership/storage/redaction rules.
- Customer and cart material use independent environment-specific Android Keystore AES-GCM stores with backup disabled and fail-closed corruption/invalidation behavior.
- Remote configuration cannot select endpoints/code/consent/legal content. Notification data cannot supply display text or arbitrary intents. Checkout cannot open an unapproved origin.
- Analytics/Crashlytics stay absent until a lawful consent, taxonomy, retention, deletion/access, and withdrawal policy is approved.
- No project backend exists; none was created. A later backend requires a product requirement, ADR, authorization/authentication design, threat-model update, and deployment/secret ownership.

## 9. Automated and device evidence

- The pre-Firebase-expansion full local matrix passed `spotlessCheck`, `detekt`, Android Lint, all module/JVM lanes, four app assemblies, and both Android-test assemblies in 21m15s (711 tasks).
- The focused Firebase expansion passed Spotless, Firebase/App Detekt, Firebase JVM tests, development/staging app JVM tests, and both Android-test APK assemblies in 3m15s.
- After fixing the one directly related FCM callback Lint error without a baseline or broad suppression, the final full matrix passed in 21m55s. XML reports contain 96 discovered JVM tests, zero failures/errors, two intentional opt-in owned-service skips, two app Lint reports with zero issues, four app APKs, and two Android-test APKs.
- `Test-FirebaseConfiguration.ps1 -RequireConfigured` passed; `Test-Phase2Foundation.ps1` passed 25/25; redacted Gitleaks working-directory and tracked-history scans found no leaks.
- The original physical command group passed the current account 3 and Storefront 2 suites plus the then-nine-test app suites. After Firebase expansion, development and staging app suites ran again with eleven tests each. The authoritative unique current total is 27 tests with zero failures/skips; historical app results are not double-counted.
- A prior `No connected devices` attempt remains historical environmental evidence only. It was superseded by the successful expanded connected run and controlled Firebase proof.
- Exact final checkpoint commands, counts, validators, secret scans, and Git state are recorded in `PAUSE-HANDOFF.md` after the final run.

## 10. Candidate feature pack and next phase

All 24 reference-derived candidate groups have an explicit `ACCEPT`, `INTENTIONALLY CHANGE`, or `NEEDS PRODUCT DECISION` recommendation plus dependencies, privacy/security, state, tests, acceptance criteria, and unknowns. Gate 8 passes for this decision pack only.

The next phase is the separately planned full 24-feature application implementation. It must not repeat completed Shopify/Firebase setup, revive the deliberately removed early cart experiment, or assume the Phase 2 proof harness is the finished product UX.
