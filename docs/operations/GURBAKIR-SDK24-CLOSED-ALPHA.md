# Gür Bakır SDK 24 Closed Alpha candidate

Owner-authorized scope: 2026-10-12. This is a Gür Bakır Closed Alpha release
transition, not public Production readiness or a change to the multi-brand
minimum-SDK policy.

## Decision and impact

Google Play's enabled Automatic Protection rejected the previous API-23 v3
candidate. The current [Google guide](https://support.google.com/googleplay/android-developer/answer/10183279?hl=en)
requires minimum API 24. The owner approves dropping Android 6/API 23 support
for Gür Bakır while keeping Automatic Protection and Play App Signing enabled.

`:app` therefore uses `minSdk = 24` in `defaultConfig`, consistently across its
development, staging and production profiles. Shared implementation/provider
modules, `:mobile-core`, Synthetic and Trial retain their API-23 baseline and
existing minimum-SDK CI lane. No compatibility code, dependency, target SDK,
provider configuration, storage identity or signing architecture changes.

Devices running API 23 cannot install this new Gür Bakır candidate. Raising
the application minimum also permits application-level manifest/desugaring/R8
optimization for API 24; a fresh minified production artifact and focused
API-24 runtime verification are required. Shared API-23 behavior remains
covered independently by the existing protected CI.

## Version and evidence authority

Live Play readback on 2026-10-12 showed exactly two accepted bundles: codes 1
and 2. Code 2 is available on the existing Alpha track; its next draft (release
ID 3) is empty. The rejected code-3 bundle did not enter the bundle library.
The next available code is therefore 3; `versionName = 0.3.0` remains the
appropriate product version. The new API-24 artifact must have a distinct
filename, hash and receipt even though these unused Play version values remain.

The thirteen remediation findings remain accepted at baseline
`cc28c49650ec6c0d338ef50c7bc424df1abe3fde`. Their scoped evidence is reused
for unchanged implementation; it is not represented as execution of the new
artifact. The historical API-23 AAB and support submission remain preserved.
Google Support need not answer before this supported SDK transition proceeds.

## Acceptance and release gates

The source change requires normal protected PR review/checks and exact
merged-main verification (`validate`, API 30 instrumentation, shared API 23
instrumentation and native 16-KiB CI). Focused local configuration, JVM/static,
API-24 runtime and fresh production R8/artifact checks supplement those gates.

Final signing/upload authority is the accepted merged source and the current
Play-authorized replacement upload certificate. A sanitized ignored receipt
under `out/releases/gurbakir-sdk24-alpha-20261012/` records actual execution,
source, certificate, bundle identity and Play milestones. Source acceptance,
upload acceptance, submission, review, publication and distributed runtime
verification remain separate claims.

Only the existing Closed Alpha audience is authorized. Automatic Protection,
Play App Signing, keys, testers, countries, other tracks, Shopify, Firebase and
App Links are preserved. P3-16 public Production readiness remains separate.
