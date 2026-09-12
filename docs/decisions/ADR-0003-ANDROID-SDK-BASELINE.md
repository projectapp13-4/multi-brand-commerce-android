# ADR-0003: Android SDK production baseline

- Status: Accepted
- Date: 2026-07-19
- Scope: Phase 2 Android SDK, build-tool, and compatibility-test baseline

## Context

Phase 1 recorded Android 17/API 37 as a stable production baseline. A mandatory Phase 2 recheck found that this claim was premature. The official Android 17 SDK page, last updated 2026-07-15, still labels the SDK package `Android Cinnamon Bun Preview`; the Android 17 overview directs developers to beta channels for testing. Platform Stability does not make the platform/SDK release final.

Google Play's current policy requires new mobile applications and updates to target Android 16/API 36 or later from 2026-08-31. The workstation already has Android SDK Platform 36 and Build Tools 36.0.0 installed.

The official Android Gradle Plugin page, last updated 2026-07-14, documents AGP 9.2.1 as the current stable patch line. AGP 9.2 requires Gradle 9.4.1 and JDK 17 and supports API levels through 37. JDK 17 remains the supported build JDK.

## Decision

- Use `compileSdk = 36` and `targetSdk = 36` for the Phase 2 production scaffold.
- Keep `minSdk = 23` provisional until every selected dependency floor and the approved device-support policy are verified; lock the highest required floor.
- Use Android 17/API 37 only in a separate preview compatibility lane. Installing its preview SDK/image is optional until that lane can be run; it must not alter production compile/target settings.
- Start with the documented stable AGP 9.2.1, Gradle 9.4.1, and JDK 17 combination. Pin the exact versions in the scaffold and change them only after reproducible build/test evidence.
- Revisit `compileSdk` and `targetSdk` when Android 17 reaches a final stable platform/SDK release and the pinned Android, Shopify, Firebase, Apollo, and quality-tool stack passes the foundation gates.

## Consequences

- The production baseline satisfies the announced 2026 Google Play requirement without treating a preview SDK as final.
- API 37 behavior can be tested early without making release builds depend on preview tooling.
- Any Phase 1 statement describing API 37 as stable is superseded by this ADR and the Phase 2 transition index/validator.
- ADR-0001 and ADR-0002 remain unchanged; this does not reopen the native Android or typed-navigation decisions.

## Evidence

- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk)
- [Android 17 overview](https://developer.android.com/about/versions/17)
- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Android Gradle Plugin release notes](https://developer.android.com/build/releases/gradle-plugin)
- Local pre-change evidence: `docs/preparation/evidence/pre-scaffold-validation-v3-2026-07-19.json`
