# Dependency and Version Decisions

Date: 2026-07-19; last updated 2026-08-10

This document records the independently selected Phase 2 dependency baseline. The reference APK did not supply versions or implementation code.

## Selection rules

1. Prefer current stable official releases with first-class Android support.
2. Keep the production lane on final Android API 36 while Android 17/API 37 remains preview.
3. Where a newest AndroidX release requires API 37, use the newest stable release whose AAR metadata supports compileSdk 36.
4. Pin through the version catalog and Gradle wrapper; verify artifacts by Gradle dependency verification and lock resolved configurations.
5. Keep Shopify Storefront, Customer Account, Checkout Kit, and Firebase clients behind project-owned interfaces.
6. Do not add a dependency merely because the reference application or old prototype used it.

## Build and Android foundations

| Dependency | Version | Decision |
| --- | --- | --- |
| Android Gradle Plugin | 9.2.1 | Official Android documentation's stable AGP 9.2 patch line; supports API 37 for the preview lane while the app compiles against 36. |
| Gradle | 9.4.1 | AGP 9.2 documented default/required-compatible wrapper line. The wrapper distribution checksum is pinned. |
| Kotlin / Compose plugin | 2.3.10 | Matches AGP 9.2 built-in Kotlin and the separately applied Compose compiler plugin. |
| Compose BoM | 2026.06.01 | Stable BoM whose Compose UI 1.11.x line remains compatible with compileSdk 36. |
| AndroidX Core | 1.18.0 | AAR metadata declares minCompileSdk 36; 1.19.0 requires API 37. |
| Activity | 1.12.4 | AAR metadata declares minCompileSdk 36; 1.13.0 requires API 37. |
| Lifecycle | 2.10.0 | Stable API-36-compatible line; 2.11.0 Compose artifacts require API 37. |
| Hilt Navigation Compose | 1.3.0 | Stable minCompileSdk 35 line; 1.4.0 requires API 37. |
| Navigation Compose | 2.9.8 | Current stable Navigation Compose 2 typed-route line at validation time. |
| Dagger/Hilt | 2.60.1 | Current stable dependency-injection line at validation time. |
| desugar_jdk_libs | 2.1.5 | Official Google Maven stable release used to keep Java time/Base64 behavior compatible with provisional minSdk 23. |

Android Lint suppresses only the version-advisory IDs that recommend API 37, AGP 9.3, or Gradle 9.6.1 simply because those artifacts exist. ADR-0003 and current official AGP documentation explain why the production lane remains on API 36 and the documented AGP 9.2/Gradle 9.4.1 combination. Security, API, manifest, correctness, accessibility, and dependency lint checks remain enabled and warnings are errors.

## Integration foundations

| Dependency | Version | Decision |
| --- | --- | --- |
| Apollo Kotlin | 5.0.1 | Generated operation-specific types for Storefront and Customer Account GraphQL clients; schemas and clients remain separate. |
| Kotlin coroutines | 1.11.0 | Structured concurrency and Flow boundaries. |
| Kotlin serialization | 1.11.0 | Typed Navigation Compose routes and narrow local payloads. |
| Shopify Checkout Kit Android | 3.5.4 | Current stable SDK selected over the 4.0.0 release candidate. The project adapter was checked against Shopify tag `3.5.4`, commit `6ef1a99e8d2e68e05be717d7a6373fa130f2a97d`. |
| AppAuth Android | 0.11.1 | Owns only the verified system-browser/Custom Tabs authorization and end-session surfaces. S256/state/nonce and exact callback policy replace embedded WebView handling; Shopify token exchange/refresh use the exact current HTTP contract separately. The dedicated Mobile client/callback and physical redirect proof are complete. |
| Firebase BoM | 34.16.0 | Current main Android Messaging, Installations, and Remote Config artifacts; no retired standalone `-ktx`, Analytics, or Crashlytics artifact. FCM auto-init remains false and current manual `register()`/`unregister()` begins only after explicit permission/consent. |
| OkHttp | 5.4.0 | Performs exact no-redirect discovery and form-encoded Shopify authorization-code/refresh exchange, plus bounded HTTP contract tests. No unrelated backend is introduced. |
| Coil Compose | 3.5.0 | Added in P3-01 for size-aware Compose loading of allowlisted project-owned Shopify media. Network support uses the OkHttp artifact, a project-owned final-response-origin check, bounded cache behavior, and Apache-2.0-licensed upstream artifacts. |
| AndroidX Room | 2.8.4 | Added in P3-03 as the current stable AndroidX persistence line for the approved device-local search history. KSP generates the typed DAO/database implementation; the Room Gradle plugin exports a checked schema. Runtime, compiler, test artefacts, transitive SQLite/KSP inputs, locks and SHA-256 verification metadata are pinned. No destructive migration fallback is enabled. |

Checkout Kit's SDK types are confined to `OfficialCheckoutKitClient`; application and domain code consume only the project-owned `CheckoutAdapter`. Storefront and account modules may both use Apollo runtime, but they must never share schemas, authorization headers, token stores, cache policy, or generated models.

## Quality and test tooling

| Tool | Version | Use |
| --- | --- | --- |
| Spotless | 8.8.0 | Deterministic source/project formatting. |
| ktlint | 1.8.0 | Kotlin formatting engine. |
| detekt | 1.23.8 | Kotlin static analysis with zero accepted findings. |
| JUnit Jupiter | 6.1.2 | JVM foundation tests. |
| AndroidX Test runner / JUnit / Espresso | 1.7.0 / 1.3.0 / 3.7.0 | Instrumentation and Compose UI foundations. |
| Gitleaks | workstation-pinned executable; exact result recorded by the Phase 2 validator | Repository and working-tree secret scan. |

## Upgrade policy

- Recheck Android 17 final stability and official AGP compatibility before any API 37 production-baseline change.
- Treat Shopify API versions, Customer Account OAuth requirements, Checkout Kit major versions, and Firebase BoM changes as integration-contract changes, not routine patch bumps.
- Regenerate Apollo models only from the pinned owned-environment schemas using the documented redacted commands.
- Review release notes, AAR metadata, transitive dependency changes, license/provenance, and focused tests before upgrades.
- Never relax lint or dependency verification merely to consume a newer artifact.

## Primary references

- Android Gradle Plugin releases: <https://developer.android.com/build/releases/gradle-plugin>
- AGP built-in Kotlin migration: <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Compose compiler plugin: <https://developer.android.com/develop/ui/compose/compiler>
- Android core library desugaring: <https://developer.android.com/studio/write/java8-support>
- Shopify API versioning: <https://shopify.dev/docs/api/usage/versioning>
- Shopify mobile commerce and Checkout Kit: <https://shopify.dev/docs/storefronts/mobile>
- Checkout Kit Android source: <https://github.com/Shopify/checkout-sheet-kit-android/tree/3.5.4>
- Firebase Android BoM: <https://firebase.google.com/docs/android/learn-more#bom>
- AndroidX Room releases and schema/migration guidance: <https://developer.android.com/jetpack/androidx/releases/room> and <https://developer.android.com/training/data-storage/room/migrating-db-versions>
