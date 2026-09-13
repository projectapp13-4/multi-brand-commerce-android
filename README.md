# Multi-Brand Commerce Android

This repository contains an independently authored native Android platform for
building concrete commerce applications with Kotlin and Jetpack Compose.
Gürbakır is the first real brand application and `:app` remains its application
and composition shell. The repository is brand-neutral; the existing Gürbakır
implementation is intentionally concrete.

This repository is the forward-development authority. The owner-controlled
private predecessor is retained only as an archived historical evidence source;
new Multi-Brand work starts here.

The architecture uses a single activity, typed Navigation Compose routes,
ViewModel/`StateFlow` unidirectional data flow, Hilt, Apollo Kotlin,
Room/DataStore, Android Keystore-backed protected state, Shopify Storefront and
Customer Account APIs, official Shopify Checkout Kit, and bounded Firebase
infrastructure.

Historical prototypes and reference analysis are evidence only. They are not
build inputs, source donors, or current engineering authority. The compact
neutral lessons that remain useful are preserved in
[`docs/reference-model`](docs/reference-model).

## Current status

- Multi-Brand Gates 0–6 are complete and Gates 3–6 are technically closed.
  Gate 6 replaced compiled merchant Categories with bounded,
  application-selected Shopify Menu discovery while keeping selector ownership
  in each application and shared projection/provider contracts brand-neutral.
- `:mobile-core` owns reusable Android application/features/navigation/local
  data; `:app` owns Gürbakır composition, identity, resources, configuration,
  and provider selection; non-production `:synthetic` proves independent
  composition and remains fail-closed and Firebase-free.
- The Multi-Brand migration is unfinished. No second real merchant application
  exists, and future gates must continue from the current architecture and
  Gate 1–6 evidence.
- Phase 3 functional implementation and integrated acceptance are complete
  through **P3-15**. The account-deletion request/local-cleanup boundary is
  implemented, but merchant acknowledgement, retention/SLA execution, and
  actual remote deletion remain externally unverified.
- **P3-16 production/release readiness is not started.** Repository
  publication, a successful build, or CI does not prove production identity,
  signing, service configuration, public associations/callbacks, privacy
  declarations, support/rollback ownership, or release readiness.

Start with the [documentation authority map](docs/README.md),
[Multi-Brand architecture](docs/architecture/MULTI-BRAND-ARCHITECTURE.md), and
[Gate 6 completion handoff](docs/multi-brand/GATE-6-COMPLETION-HANDOFF.md). The
handoff remains an accurate pre-merge record; current indexes carry the later
technical-closure status.

The [public repository migration handoff](docs/PUBLIC-REPOSITORY-MIGRATION-HANDOFF.md)
records the sanitized baseline, publication-rights boundary, protected
bootstrap, and authority cutover evidence.

## Modules

- `app`: Gürbakır application/composition shell, Android entry points, build
  variants, packaged identity/resources, concrete brand policies, provider
  bindings, and app-owned dependency injection.
- `synthetic` (physical path `apps/synthetic`): non-production conformance app
  with separate identity/resources/storage, fail-closed service configuration,
  Firebase-free composition, and reduced capabilities/navigation.
- `mobile-core`: reusable Android application, Compose features, typed
  navigation, local-data implementation, shared resources, and
  integration-neutral orchestration.
- `foundation`: neutral configuration, error, logging/redaction, route policy,
  design foundations, and shared infrastructure.
- `storefront`: Storefront API contracts, generated operations, repositories,
  and typed domain boundary.
- `account`: Customer Account OAuth/PKCE, protected session handling,
  profile/address/order/account flows, and related contracts.
- `checkout`: narrow project-owned adapter around official Shopify Checkout Kit.
- `firebase`: typed Remote Config and bounded Firebase adaptation; reusable
  application code does not depend on this provider module.

There is no runtime merchant switch and no normative brand flavor dimension.
The application module being built selects the concrete brand. Shared modules
must not depend on concrete application modules or branch on brand names.

## Bootstrap

Requirements are JDK 17 and Android SDK Platform 36 with Build Tools 36.0.0.
The pinned wrapper supplies Gradle 9.4.1; do not substitute a global Gradle.

```powershell
Copy-Item .\config\local.properties.example .\config\local.properties
.\gradlew.bat spotlessCheck detekt lint
.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest `
  :checkout:testDebugUnitTest :storefront:testDebugUnitTest `
  :firebase:testDebugUnitTest :mobile-core:testDebugUnitTest `
  :synthetic:testDebugUnitTest :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest
.\gradlew.bat :mobile-core:assembleDebug :mobile-core:assembleRelease `
  :app:assembleDevelopmentDebug :app:assembleDevelopmentRelease `
  :app:assembleStagingDebug :app:assembleStagingRelease `
  :app:assembleDevelopmentDebugAndroidTest :app:assembleStagingDebugAndroidTest `
  :synthetic:assembleDebug :synthetic:assembleRelease :synthetic:assembleDebugAndroidTest
.\scripts\Test-PublicReadiness.ps1 -SelfTest
.\scripts\Test-RepositoryPortability.ps1 -SelfTest
.\scripts\Test-Gate2SyntheticPackage.ps1 -SelfTest
```

`config/local.properties`, Firebase configuration, signing material, generated
reports, and build output are ignored. Never put Admin/backend secrets,
customer tokens, signing material, service-account credentials, or private keys
in the Android client or repository.

## Licensing and marks

Original publishable source and documentation are licensed under
[Apache-2.0](LICENSE). The license does not grant rights to Gürbakır or Shopify
marks, brand assets, or third-party material. See [trademark terms](TRADEMARKS.md),
[asset rights](ASSET-LICENSES.md), and [third-party notices](THIRD_PARTY_NOTICES.md).
The project integrates with Shopify but is not affiliated with, endorsed by, or
sponsored by Shopify Inc.

Report security issues privately as described in [SECURITY.md](SECURITY.md).
