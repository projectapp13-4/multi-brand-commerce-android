# Toolchain Baseline

Captured: 2026-07-19, dedicated Windows 11 development workstation

## Installed and verified

| Component | Version / state | Role |
|---|---|---|
| Android Studio | `2026.1.2`, build `AI-261.25134.95.2612.15822958` | Primary native Android IDE; updated during reassessment |
| Microsoft OpenJDK | `17.0.19` LTS | Shopify Checkout Kit/Gradle-compatible project JDK baseline |
| Android SDK command-line tools | `21.0` | SDK/package management |
| Android platform tools / ADB | `37.0.0` / `1.0.41` | Device communication |
| Android platforms | API 34, 35, 36 installed; API 37 preview not installed | Phase 2 production baseline is compileSdk/targetSdk 36; API 37 is a separate preview compatibility lane |
| Android build tools | 28.0.3, 34.0.0, 36.0.0 | Project selects through AGP; do not bind to global default |
| Android Emulator | `36.6.11` | Installed, but not the primary test path on this machine |
| Android SDK licenses | All 7 accepted | Verified through `sdkmanager --licenses` during reassessment |
| Firebase CLI | `15.24.0`, official npm `firebase-tools` | Project/environment setup and emulators after authorization |
| Gitleaks | `8.30.1` | Local/CI secret scanning |
| Git | `2.54.0.windows.1` | Version control |
| Node.js / npm | `24.18.0` / `11.16.0` | Firebase CLI and approved auxiliary tooling; use `npm.cmd` |
| PowerShell | `5.1.19041.6456` | Preparation/validation automation |
| Flutter / Dart | `3.44.4` / `3.12.2` | Inventory and old-prototype validation only; no platform-selection weight |

Android SDK packages already include API 36, build-tools 36, the emulator, a Google Play API 36 x86_64 image, CMake, and NDKs. API 37 remains an optional preview compatibility-lane package and is not required for the production scaffold. Native production dependencies had deliberately not been installed into the Phase 1 repository before the Phase 2 authorization.

## Android SDK and release baseline

| Setting / obligation | Phase 2 baseline | Verification rule |
|---|---|---|
| `compileSdk` | `36` (final Android 16) | Use the installed API 36 platform and Build Tools 36.0.0; verify the pinned stable AGP/Kotlin/Compose/AndroidX/Shopify/Firebase stack |
| `targetSdk` | `36` | Review Android 16 target-behavior changes and run compatibility/instrumentation/device suites; this meets Play's requirement from 2026-08-31 |
| `minSdk` | Start at `23` | Shopify Checkout Kit and Firebase currently require API 23; before locking, take the highest dependency floor and validate approved device coverage, backports/desugaring and API 23 behavior |
| Preview/QPR | Separate API 37 compatibility lane | Test Android 17 previews/QPRs independently; do not make a preview SDK the production compile/target baseline |
| Developer verification | Release gate | Verify the developer identity and register final package names/signing ownership in the appropriate Android/Play console; never expose signing private material |

This is a current baseline, not a permanent pin. Recheck official Android SDK, Google Play target policy, Shopify Checkout Kit, Firebase, and developer-verification documentation at scaffold and release time.

## Device strategy

CPU virtualization firmware reports disabled and no Android device was connected at the capture point. The existing emulator/image may be used only if acceleration becomes reliable; physical Android hardware is the primary path for Checkout Kit, OAuth redirect, App Links, push, process-death, camera/permission, and lifecycle validation.

## Phase 2 project-pinned tools

Create these through the Gradle wrapper/version catalog in the authorized scaffold, not as global workstation dependencies:

- current compatible Android Gradle Plugin, Kotlin, Compose compiler/BOM, AndroidX and JDK toolchain;
- Hilt, Apollo Kotlin, Shopify Checkout Kit Android, AppAuth-Android, Room, DataStore, WorkManager and Firebase Android BoM main modules;
- Spotless/ktlint, detekt, Android Lint and dependency verification;
- JUnit, coroutine/Flow test support, Apollo test tooling/MockWebServer, Compose UI/instrumentation, Macrobenchmark and Baseline Profile tooling;
- a screenshot/visual-regression library only after representative brand screens and CI rendering constraints exist.

Use current stable mutually compatible versions at scaffold time, record the evidence, and lock them. Do not copy versions from the reference APK or treat the 2026-07-19 research snapshot as a permanent pin.

## Setup gates

| Item | When needed | Required action |
|---|---|---|
| Physical Android device | Before device integration acceptance | Enable approved USB debugging, connect, and capture `adb devices -l` without device identifiers in reports |
| Android API 37 preview SDK/image | Compatibility testing only | Install only when the preview lane can run; never replace production API 36 settings merely to test the preview |
| Owned Shopify test store/config | Before schema, OAuth, cart or checkout integration | Use non-production, project-owned configuration and current versioned schema checks |
| Owned Firebase projects | Before `firebase init` / app registration | Provision dev/staging ownership and classified public-client/sensitive configuration injection; do not log in during Phase 1 |
| Brand assets/content | Before UI acceptance | Approve Gurbakir identity, localization, legal and consent content |
| CI runner | During foundation | Pin JDK/SDK, cache safely, run scans/tests/builds, and keep signing secrets out of repo |
| macOS/Xcode | Only when native iOS work is authorized | Provision an independent Swift/SwiftUI build/test/signing path |

## Command conventions

- Use `npm.cmd`/`npx.cmd`; local PowerShell policy can block `npm.ps1`.
- Use the project Gradle wrapper, never a globally installed Gradle.
- Classify configuration before handling it: public Firebase client identifiers/store metadata may follow approved repo policy; Storefront public tokens remain controlled and redacted; signing/private keys, service-account private material, Admin/backend credentials, and customer/OAuth tokens stay out of tracked examples/logs and use approved secret boundaries.
- Gitleaks is in the user PATH registry; existing long-lived shells may need an environment refresh. The verified direct executable was the WinGet user link.
- Firebase CLI is installed in the npm user prefix and available as `firebase`/`firebase.cmd`; no login or project mutation occurred.

## APK-analysis network isolation

The previous static-analysis task's 12 exact `Codex-BusinessPartnerAPK-Static-Block-*` firewall rules were removed by its documented rollback. Revised Phase 1 validation requires zero enabled rules matching that defined display-name pattern; this does not claim that all firewall rules are absent. Re-enable exact isolation only for a future authorized static-analysis task through the evidence workspace scripts; normal Android development must not inherit it.
