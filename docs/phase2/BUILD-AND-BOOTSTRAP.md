# Build and Bootstrap

## Pinned baseline

| Component | Pinned value | Role |
| --- | --- | --- |
| JDK | 17 (validated with Microsoft OpenJDK 17.0.19 LTS) | AGP/Gradle runtime and Java target |
| Gradle | 9.4.1 wrapper | Reproducible build entry point |
| Android Gradle Plugin | 9.2.1 | Current officially documented stable AGP line used by this project |
| Kotlin / Compose compiler plugin | 2.3.10 | AGP 9.2 built-in Kotlin-compatible line |
| compileSdk / targetSdk | 36 / 36 | Current final stable production lane; see ADR-0003 |
| minSdk | 23, provisional | Must be confirmed against the approved device-support policy |
| Android Build Tools | 36.0.0 | AGP 9.2 default/validated build tools |

The validation workstation also had Android SDK Command-line Tools 21.0 and Platform Tools 37.0.0. Platform Tools do not set the application target API.

## Required local software

1. Install a supported JDK 17 and set `JAVA_HOME`.
2. Install Android SDK Platform 36, Build Tools 36.0.0, and current Platform Tools.
3. Set `ANDROID_SDK_ROOT` or create the usual ignored root `local.properties` with the SDK path.
4. Use `gradlew.bat`; do not use an unpinned global Gradle.

The wrapper distribution is protected by `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.

## Environment bootstrap

Create the ignored local configuration file without inserting values into source or chat:

```powershell
Copy-Item .\config\local.properties.example .\config\local.properties
```

Only verified Gurbakir development/staging public-client values belong there. Empty tracked defaults intentionally keep the shell buildable and visibly unconfigured. The current application IDs are non-production development/staging placeholders; no final production application ID or production signing identity has been chosen.

Populate and revalidate public Customer Account discovery values without printing them:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\Provision-CustomerAccountDiscovery.ps1
```

The Storefront schema is versioned. Regenerate it only from the verified ignored domain/token configuration:

```powershell
.\gradlew.bat :storefront:downloadStorefrontApolloSchemaFromIntrospection
```

The explicit owned-store proofs are opt-in. The read proof contains no mutation; the bounded cart proof creates a synthetic cart, exercises its line lifecycle, and removes every remaining line without opening checkout or creating an order/payment:

```powershell
.\gradlew.bat :storefront:testDebugUnitTest `
  -PgurbakirRunOwnedStorefrontProof=true
.\gradlew.bat :storefront:testDebugUnitTest `
  -PgurbakirRunOwnedCartProof=true `
  --no-daemon --no-configuration-cache --console=plain
```

## Canonical validation

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat detekt
.\gradlew.bat lint
.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest `
  :checkout:testDebugUnitTest :storefront:testDebugUnitTest `
  :firebase:testDebugUnitTest :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest
.\gradlew.bat :app:assembleDevelopmentDebug
.\gradlew.bat :app:assembleDevelopmentRelease
.\gradlew.bat :app:assembleStagingDebug
.\gradlew.bat :app:assembleStagingRelease
.\gradlew.bat :app:assembleDevelopmentDebugAndroidTest `
  :app:assembleStagingDebugAndroidTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\Test-FirebaseConfiguration.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\Test-Phase2Foundation.ps1
```

For a single audit run that combines every module/flavor lint model, unit-test lane, and development/staging debug/release assembly, pass `--no-configuration-cache`. AGP 9.2.1's combined lint-model graph is not configuration-cache serializable in that unusually broad invocation; focused day-to-day commands may continue to use the configured cache.

The final Phase 2 audit used one `--no-parallel --no-daemon --no-configuration-cache` invocation containing `spotlessCheck`, `detekt`, `lint`, all seven JVM variant/module tasks above, four app assemblies, and both Android-test assemblies. It completed successfully in 21m55s. The XML reports contain 96 discovered JVM tests, zero failures/errors, and two intentionally skipped opt-in owned-service proof tests; both app Lint XML reports contain zero issues. All six expected APK directories contain one output.

The two `assemble*DebugAndroidTest` tasks compile and package deterministic Compose instrumentation APKs for development and staging. They do not install or execute those tests. Physical execution uses the explicit connected tasks below only after a count-only ADB check confirms exactly one approved device:

```powershell
.\gradlew.bat --no-parallel `
  :account:connectedDebugAndroidTest `
  :storefront:connectedDebugAndroidTest `
  :app:connectedDevelopmentDebugAndroidTest `
  :app:connectedStagingDebugAndroidTest
```

The current physical evidence consists of 27 unique tests on the approved Android 13 phone: account 3, Storefront 2, app development 11, and app staging 11, all with zero failures or skips. The explicit Firebase permission/registration, controlled delivery/tap, Remote Config fetch/activate, and unregister cleanup were additionally verified manually on the visible development build. Compilation and device execution remain recorded separately in `DEVICE-TEST-EVIDENCE.md`.

## Build variants

- `development`: project-owned development services only.
- `staging`: project-owned staging/test services only.
- `debug`: locally debug-signed and debuggable.
- `release`: minified release-verification build; no production signing material is stored in the repository.

Firebase configuration is all-or-nothing across four exact source sets:

- `app/src/developmentDebug/google-services.json` -> `com.gurbakir.mobile.dev.debug`
- `app/src/developmentRelease/google-services.json` -> `com.gurbakir.mobile.dev`
- `app/src/stagingDebug/google-services.json` -> `com.gurbakir.mobile.staging.debug`
- `app/src/stagingRelease/google-services.json` -> `com.gurbakir.mobile.staging`

All files stay ignored. Development debug/release resolve to one project, staging debug/release resolve to a second project, and the two project identities must differ. Google Services is applied only when all four files exist and ignored `firebase.enabled=true`; partial or mismatched provisioning stops during configuration. Run `Test-FirebaseConfiguration.ps1 -RequireConfigured` after provisioning.

There is no production flavor in Phase 2 because the permanent production application ID, Play identity, signing ownership, and legal/brand decisions are not yet approved.

## Troubleshooting boundaries

- If API 37 is still preview, do not raise the production `compileSdk` merely to consume an AndroidX release that requires it. Pin the newest API-36-compatible stable dependency and record the decision.
- AppAuth's receiver is bound to the verified discovery-derived ignored scheme and the same callback is saved on the dedicated Shopify Public Mobile client. AppAuth owns browser/end-session presentation; the exact Shopify exchange/refresh client is separate. Never invent or reuse another app's callback.
- Do not add Firebase files until both project identities are verified. The conditional Google Services guard accepts either zero files or the complete validated four-file set, with separate development/staging project identities.
- Never contact reference third-party reference infrastructure while diagnosing this project.
