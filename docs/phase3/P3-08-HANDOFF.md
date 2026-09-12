# P3-08 Owned Legal and Support Web Baseline Handoff

Date: 2026-08-11

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS - 17/17 FOCUSED INSTRUMENTATION AND MANUAL CUSTOM TAB RETURN**

Branch: `main`

Starting checkpoint: `92f44ad` (`docs: codify owner approval boundaries`)

Implementation checkpoint: `33f6f9c` (`feat: complete P3-08 legal support baseline`)

Documentation checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff closes the P3-08 gate. It records the owner-authorized merchant content baseline, exact external-route policy, version/provenance model, public production navigation, accessibility/error behavior, dependency review, local/device evidence, and the exact P3-09 boundary. It does not claim final public release, a qualified legal review, hosted-page accessibility ownership, P3-09 Account completion, P3-13 deletion, P3-15 accumulated audit, or P3-16 release/security work.

## Authority, sources, and clean-room boundary

- `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md` authorizes verified merchant/Shopify legal and support content as the current application baseline and fixes integration version `gurbakir-legal-baseline-1` with adoption date `2026-08-11`.
- The authenticated Shopify Admin policy inventory and public merchant storefront were inspected without mutation. Shopify listed refund, privacy, terms, shipping, contact information, and legal notice policies. Public canonical routes resolved with real content.
- Shopify policy pages state source update date `2026-04-26`. The public support page exposes no separate source-effective date, so the app records only baseline adoption for that page.
- Prepared reference evidence was queried through the repository evidence-navigation workflow. `AgreementsScreenDestination` and `PagesScreenDestination` are high-confidence parameterless account destinations. This supports a typed public index/destination relationship only; no decompiled body, proprietary UI, reference branding, asset, credential, or unknown Worker behavior was copied.
- Current Android guidance favors Custom Tabs for a browser experience with visible origin and browser context. The implementation follows the [Android Custom Tabs guide](https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs) and uses the stable `androidx.browser:browser:1.10.0` recorded by the [AndroidX Browser release notes](https://developer.android.com/jetpack/androidx/releases/browser).

## Adopted versioned content record

Every route is public, HTTPS, exact-host/path allowlisted, merchant-owned, and represented by packaged metadata. The app does not cache hosted legal bodies, append language/tracking parameters, persist acknowledgement, or infer consent.

| Page ID | Canonical route | Source | Effective/adoption record |
|---|---|---|---|
| `SUPPORT` | `https://gurbakir.com/pages/contact` | Merchant public page | adopted 2026-08-11; no separate source date |
| `PRIVACY` | `https://gurbakir.com/policies/privacy-policy` | Shopify merchant policy | source updated 2026-04-26; adopted 2026-08-11 |
| `TERMS` | `https://gurbakir.com/policies/terms-of-service` | Shopify merchant policy | source updated 2026-04-26; adopted 2026-08-11 |
| `SHIPPING` | `https://gurbakir.com/policies/shipping-policy` | Shopify merchant policy | source updated 2026-04-26; adopted 2026-08-11 |
| `RETURNS` | `https://gurbakir.com/policies/refund-policy` | Shopify merchant policy | source updated 2026-04-26; adopted 2026-08-11 |
| `LEGAL_NOTICE` | `https://gurbakir.com/policies/legal-notice` | Shopify merchant policy | source updated 2026-04-26; adopted 2026-08-11 |

The local index remains available offline. Hosted content is deliberately not cached because it is merchant/Shopify-owned and may change; external failure leaves the user at the same local card with an explicit retry-oriented message. Updating the hosted body does not require application restructuring, but metadata/source-date review is required when a revision is adopted.

## Completed implementation

### User-visible production behavior

- Home exposes a functional `Help and Policies` card after current commerce content.
- Route Recovery also preserves a public escape to the legal/support index.
- The typed `LegalSupportRoute` renders six real entries with TR/EN title, summary, source class, effective/adoption date, and baseline version.
- Each action states that it opens a secure browser tab. AndroidX Custom Tabs shows the owned address and browser controls; share state is disabled.
- Returning from the tab shows a polite live-region message inside the same page card, scrolls the card into view, and returns Compose focus to an explicit focus target on the same action.
- Browser unavailable and rejected-route outcomes stay on the local index and use the same visible/announced/focused recovery pattern.
- The index is scrollable at 200 percent text and uses semantic headings and stable test tags.
- No placeholder legal body, generic WebView, arbitrary URL renderer, legal-acceptance checkbox, password/account UI, deletion promise, delivery-time promise, refund guarantee, or proof action ships in this slice.

### Architecture and ownership

- `LegalSupportRepository` owns immutable packaged metadata; Hilt binds `PackagedLegalSupportRepository` as the production source.
- `LegalSupportViewModel` owns page state, launch result, return feedback, pending return, and one-shot focus request state.
- `OwnedPagePolicy` validates exact known route identity and rejects query, fragment, user-info, alternate-port, HTTP, path drift, unknown ID, and unknown host values.
- `OwnedPageLauncher` is the only Custom Tabs boundary and launches only metadata that still matches the packaged policy.
- Navigation stores no raw URL. Page metadata, URLs, browser state, and feedback are not persisted in Room/DataStore/SavedState.
- `BrandLegalLinks` now carries the canonical privacy, terms, and support public configuration for consumers that already use that brand contract.
- No GraphQL operation, backend, Firebase feature, Remote Config value, Room schema/migration, Keystore envelope, cart/session ownership rule, analytics event, dangerous permission, or service endpoint was introduced.

### Dependency and supply-chain review

- Added only official `androidx.browser:browser:1.10.0`, required for Custom Tabs.
- The app dependency lock changed the previously transitive Browser `1.3.0` entry to direct `1.10.0` across app configurations.
- Gradle dependency verification records SHA-256 for the `1.10.0` AAR and module metadata. No unrelated module lock remained changed.
- Existing AndroidX Core KTX supplies `String.toUri()`; no additional parser/network dependency was added.

### Tests added or extended

- Repository/policy JVM tests cover all six routes, version/date/source metadata, uniqueness, and rejection of query/fragment/user-info/port/path/scheme/host drift.
- ViewModel JVM tests cover launch, return, unavailable browser, feedback, and one-shot focus request handling without inventing acceptance state.
- LegalSupport Compose tests cover versioned link activation, visible live-region failure plus deterministic focus, and 200 percent text scrolling.
- Home Compose tests cover reachability of the public index.
- Production navigation tests cover the typed route and Route Recovery access.

## Validation evidence

### Final local quality gate

```powershell
.\gradlew.bat spotlessCheck :app:detekt :app:testDevelopmentDebugUnitTest :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS**, 298 tasks, completed in 4 minutes 48 seconds.

- Spotless: **PASS**.
- App detekt: **PASS**.
- App JVM: **63 tests, 0 failures, 0 errors, 0 skips**.
- Development Lint: **PASS, 0 issues**.
- Development app APK and Android test APK assembly: **PASS**.
- `gitleaks dir . --config .gitleaks.toml --redact --no-banner --log-level error`: **PASS**.
- `git diff --check`: **PASS**.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-Phase3Planning.ps1`: **PASS, 15/15 checks**, including 24 feature rows, 27 screens, 17 slices, UTF-8/link consistency, five ignored local configuration files, zero secret-pattern hits, and the current two-file handoff-doc scope.

The first full gate exposed and fixed direct P3-08 quality issues rather than suppressing them: long composables/file counts were split, repository builder parameters reduced, date constants named, and `Uri.parse` replaced with the existing KTX `toUri()` contract.

### Final physical instrumentation

Device: Samsung SM-A225F, Android 13, API 33. Serial is intentionally omitted.

```powershell
.\gradlew.bat :app:connectedDevelopmentDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.gurbakir.mobile.legal.LegalSupportScreenTest,com.gurbakir.mobile.ProductionNavigationTest,com.gurbakir.mobile.home.HomeScreenTest' --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS, 17/17 tests, 0 skips, 0 failures**, completed in 52 seconds.

The initial device run found two useful gaps. Moving feedback into the originating card prevented LazyColumn disposal from removing the live region, and adding an explicit focus target made physical focus restoration deterministic. The existing route-recovery test adopted the already-proven semantic click helper to avoid raw touch injection variability. The final combined 17-test runner passed cleanly.

### Manual production-route device acceptance

The final development app APK was installed and opened as a normal application, not under instrumentation. The following passed:

- launch Home and scroll to `Help and Policies`;
- open the local legal/support index and inspect baseline, source, date, and external/offline copy on the small-screen device;
- open the real merchant support page in Chrome Custom Tabs and confirm visible `gurbakir.com` origin and hosted content;
- use system Back without submitting the contact form or changing hosted state;
- confirm the app returned to the support card, displayed the return message, and exposed the same action as `focused=true` in UIAutomator.

Manual full TalkBack speech/traversal is **NOT RUN** and remains part of P3-15's accumulated accessibility audit. Hosted page accessibility remains merchant/Shopify-owned. Manual forced-network-offline operation was **NOT RUN**; local-index independence and unavailable-browser state are covered by implementation and Compose/ViewModel tests.

## Artifacts

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 19,075,745 bytes
  - SHA-256 `D76E9D24DD18C1DDDA14DF10E910DFFD7DD78FC9D187E2A25FFDD34627855A6C`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,254,184 bytes
  - SHA-256 `B89F6E2D1330AB2782253364AD9D4A4837161A8F7EF3D644DB672F0F64247236`

The connected Gradle task removes its temporary test install at completion. The development application was manually reinstalled for the manual route, then later replaced/removed by the final connected task lifecycle. Install the exact app artifact above for continued manual testing.

## External state and privacy

- Shopify Admin: **READ-ONLY INSPECTION; NO MUTATION**.
- Public storefront: **READ-ONLY PAGE LOADS; NO FORM SUBMISSION OR MUTATION**.
- Firebase, Remote Config, Customer Account configuration, OAuth, backend, carts, accounts, orders, payments, and device push registration: **UNCHANGED**.
- No synthetic record was created.
- Physical device: only the project development app/test packages and temporary runner state were controlled. No unrelated package or user data was changed.
- Browser automation tabs were finalized. Existing Edge sessions were released and left intact; no active browser automation remains.
- Temporary device/local UIAutomator XML and screenshots were deleted after inspection because the public support page contains contact details. No PII-bearing screenshot or XML was committed.
- No credential, token, arbitrary URL, customer value, contact-form value, or hosted legal body entered source, logs, analytics, reports, or mobile persistence.

## Acceptance boundary and exact next slice

P3-08 exit criteria are satisfied: production privacy, terms, support, shipping, returns, and legal-notice routes are functional and versioned; placeholders are absent; unsafe routes fail closed; public access survives route recovery; the Account entry gate is open.

P3-09 may begin. Resume in this order:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, the P3-09 roadmap section, P3-00 Account screen/flow/persistence records, and the Phase 2 account/OAuth handoff.
2. Verify `main` contains implementation checkpoint `33f6f9c` and this handoff commit, with a clean worktree and preserved ignored local Shopify/Firebase configuration.
3. Inspect the existing `account` module, OAuth/PKCE session contracts, production navigation, cart ownership reconciliation, current Customer Account schema/operations, and existing tests before editing.
4. Begin the first unfinished atomic action: define the production signed-out/signed-in Account state and destination contract around the already-proven hosted Customer Account OAuth foundation. Do not add password, code, reset, or app-owned registration UI.
5. First targeted checks: account JVM suite plus app Kotlin/AndroidTest compilation; then hosted launch/cancel/callback/session-expiry tests and physical-device proof.
6. P3-09 exits only when signed-out legal/help, hosted passwordless launch/return/cancel/recovery, secure session restoration/expiry/logout, minimum identity, route reset, and cart association/detachment behavior meet its roadmap criteria.
7. Do not start P3-10/Profile until P3-09 is complete; do not implement P3-13 deletion or P3-16 release/security hardening prematurely.

## Rollback

Revert `33f6f9c` to remove the P3-08 implementation, AndroidX Browser direct dependency, adopted metadata, and acceptance-record updates. Reverting does not change hosted Shopify policies or support content because this slice performed no external mutation. Existing merchant pages remain public independently of the app.
