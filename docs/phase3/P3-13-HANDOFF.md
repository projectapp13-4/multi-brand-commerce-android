# P3-13 Account Deletion Request Boundary and Physical Acceptance Handoff

Date: 2026-08-13

Implementation status: **LOCAL IMPLEMENTATION COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS**

End-to-end request, acknowledgement, SLA, and remote-deletion status: **EXTERNALLY UNVERIFIED / EXTERNALLY BLOCKED**

Branch: `main`

Starting checkpoint: `3cee589` (`docs: checkpoint Phase 3 after P3-12`)

Implementation checkpoint: `0afc32acb973a1a550b601fbbb91d9b3dbb9158c` (`feat: add P3-13 account deletion request boundary`)

This handoff closes the safe independently implementable P3-13 mobile boundary. It does not claim that an account-deletion request was submitted, acknowledged, completed within an SLA, or executed in Shopify or another merchant system. Feature group 17 therefore remains `EXTERNALLY_BLOCKED` at the roadmap exit boundary. P3-14 may proceed independently under the active owner policy; P3-15 must retain this external disposition.

## Authority and product decision

- The active owner policy supersedes the older assumption that every reversible project-controlled P3-13 decision requires another owner approval. It does not authorize inventing an external operator, acknowledgement, SLA, retention decision, backend behavior, or completed deletion.
- The current owned privacy policy at `https://gurbakir.com/policies/privacy-policy` states that a person may request access, correction, or deletion through merchant contact channels and describes lawful retention exceptions. The current owned contact page at `https://gurbakir.com/pages/contact` exposes the merchant form. Both were inspected read-only on 2026-08-13; no form was filled or submitted.
- Current Shopify evidence shows no Customer Account API mutation for self-deleting the current customer. Shopify Admin `customerDelete` requires privileged server-side access and can delete only eligible customers; no Admin credential or mutation belongs in the mobile app.
- The safe product contract is therefore request-only: a signed-in customer can read the owned policy, reach the exact owned contact form, receive identity-safety guidance, and separately choose local cleanup. Returning from a browser never becomes a submitted/deleted acknowledgement.
- The request resource is exposed only after the app revalidates the protected Customer Account session. There is no deep link to the private screen and no account identifier, email, message, token, or other PII is placed in a URL.
- The existing P3-08 `OwnedPagePolicy` remains the external-boundary authority. It permits only exact packaged Gür Bakır HTTPS routes without query, fragment, userinfo, or alternate host/path.
- A native/backend deletion service was not added. The reference Worker remains excluded and its authz, retention, logging, deletion, and acknowledgement semantics remain `UNKNOWN`.

Primary public sources:

- <https://gurbakir.com/policies/privacy-policy>
- <https://gurbakir.com/pages/contact>
- <https://shopify.dev/docs/api/admin-graphql/latest/mutations/customerDelete>

## Completed implementation

### User-visible behavior

- Signed-in Account shows **Account deletion request**; signed-out Account hides it.
- The typed destination first restores/revalidates the protected customer session. A signed-out result returns to a fresh Account destination; a retained transient session failure exposes only retry and performs no cleanup.
- The screen clearly distinguishes a remote merchant request from cleanup on this device. It never displays “account deleted,” “request submitted,” or an equivalent unverified success.
- Customers can open only the packaged owned privacy-policy and contact-form pages through AndroidX Custom Tabs with sharing disabled. No form is prefilled, read, or automatically submitted.
- Copy instructs customers to use their account email, warns that the merchant may verify identity, and explicitly says never to disclose a password or one-time code.
- Browser feedback reports only opening, returning, no-browser, or rejected URL. A return message explicitly says submission was not verified.
- Separate local cleanup requires a second explicit confirmation. Search history and current cart are selected by default. The account-independent device wishlist is preserved by default and requires explicit opt-in.
- Local cleanup attempts protected-session logout and the selected data classes independently. Session, search, wishlist, and cart each receive `CLEARED`, `FAILED`, or `NOT_SELECTED`; one failure does not falsely convert another class to success or skip later selected cleanup.
- Remote logout uncertainty is reported separately. The final result always states that local outcomes do not prove remote account or personal-data deletion.
- Language/market preferences and packaged legal metadata are retained. Profile, addresses, orders, and account summary have no persistent app cache and remain governed by their existing session/lifecycle clear boundaries.
- TR/EN resources, headings/live regions, full-row checkbox semantics, scrolling, explicit confirmation, stable test tags, and 200-percent text reflow are present.

### State, navigation, data, and security

- Added `AccountDeletionRoute` without an external deep link.
- Added `AccountDeletionViewModel`, injected `AccountDeletionController`, UI state/effects, exact clear plan/outcomes, cancellation-safe cleanup orchestration, Hilt binding, and destination adapter.
- Reused `AccountController.logout`, `SearchHistoryRepository.clear`, `WishlistRepository.clear`, `CartRepository.discard`, `LegalSupportRepository`, `OwnedPagePolicy`, and `OwnedPageLauncher`; no duplicate data store or web policy was introduced.
- The controller catches ordinary per-class failures while preserving coroutine cancellation. Cart or remote-logout ambiguity remains a failure/unverified result rather than being converted into success.
- No dependency, schema, migration, manifest permission, persistent deletion-request record, server endpoint, Admin token, remote configuration, notification, analytics event, proof UI, or device permission was added.

### Main changed files

Production:

- `app/src/main/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionController.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionViewModel.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionScreen.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/AccountDeletionDestination.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/account/AccountDeletionEntryButton.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/account/AccountScreen.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/navigation/ProductionRoutes.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/di/CustomerAccountModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Tests:

- `app/src/test/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionControllerTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionViewModelTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/accountdeletion/AccountDeletionScreenTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/account/AccountScreenTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/ProductionNavigationTest.kt`

## SR-08 local-clear reconciliation

| Data class | P3-13 behavior | Evidence status |
|---|---|---|
| OAuth transaction and customer session | Revalidate on entry; confirmed local action invokes existing logout/session clear | PASS in controller/ViewModel tests |
| In-memory account/profile/address/order state | Existing session/lifecycle owners clear or re-read; P3-13 persists none | PASS by architecture and prior slice gates |
| Search history | Selected by default; explicit local `clear()`; exact result | PASS in JVM/device tests |
| Wishlist | Account-independent; preserved by default; explicit opt-in clear only | PASS in JVM/device tests |
| Cart capability and ownership | Selected by default; explicit `discard()`; restricted/failure stays failure | PASS in JVM tests |
| Language and market | Retained; no P3-13 writer | PASS by implementation inspection |
| Legal/support metadata | Packaged public metadata retained; no request record | PASS by implementation inspection |
| Remote account/profile/addresses/orders | Mobile app does not delete or claim deletion | EXTERNALLY BLOCKED |
| Merchant request/acknowledgement/SLA | User can reach owned form; app cannot observe submission or merchant execution | EXTERNALLY UNVERIFIED |
| Firebase/Analytics/Crashlytics | No production registration/telemetry added; no customer association or deletion mutation | UNCHANGED |

## Validation evidence

### Focused local validation

- Focused P3-13 JVM: **7/7 PASS** (`AccountDeletionControllerTest` 3, `AccountDeletionViewModelTest` 4).
- Production Kotlin compilation: **PASS**.
- Android-test Kotlin compilation: **PASS**.
- Detekt initially found only method/file count thresholds introduced by the entry; production code was structurally split and the final Detekt run is **PASS**. The ViewModel retains one narrow `TooManyFunctions` suppression because its explicit Compose event handlers are independently testable.

### Broad application gates

- App JVM: **122/122 PASS**, zero failures/errors/skips.
- App Detekt: **PASS**.
- App Android Lint: **PASS**, zero issues in the final XML report.
- Spotless check: **PASS**.
- Android-test source compilation: **PASS**.
- Development app and Android-test APK assembly: **PASS**.
- Final combined command `spotlessCheck :app:detekt :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest`: **PASS** in 6 minutes 3 seconds.
- `gitleaks dir . --no-banner --redact --exit-code 1 --log-level error`: **PASS**.
- `git diff --check` and staged diff check: **PASS**.
- From clean implementation checkpoint `0afc32a`, `scripts/Test-Phase3Planning.ps1`: **15/15 PASS** (`features=24`, `screens=27`, `slices=17`).

### Physical device

Device: Samsung SM-A225F, Android 13, 720x1600.

The final focused matching-APK group installed the development app/test APKs and executed:

- `AccountDeletionScreenTest`: **4/4 PASS**.
- `AccountScreenTest`: **4/4 PASS** including signed-in entry/action and signed-out absence.
- `ProductionNavigationTest`: **8/8 PASS** including the typed P3-13 route.
- Final combined physical evidence: **16/16 PASS**, zero failures/skips.

An earlier 16-test run passed 13 and failed two new tests because they attempted to address uncomposed lazy descendants directly. The tests were corrected to scroll the list semantics container. One existing route-recovery method also had a transient visibility failure; an isolated matching-APK rerun passed 1/1 and the final full 16-test run passed. Product code did not change for those three test-only failures.

Manual authenticated form handoff, real form submission, merchant acknowledgement, completed remote deletion, retention-exception execution, response SLA, and full TalkBack speech review are **EXTERNALLY UNVERIFIED**. No unexecuted check is labelled PASS.

## APK artifacts

Final matching artifacts:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 20,003,227 bytes
  - SHA-256 `E5F572D50CF4458AC8D0D06476C3BA9DAC75BA9AA6BCC7B47846971BF969F59D`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,328,149 bytes
  - SHA-256 `A8B4D88ADEC18BD1FD4ACFCB97EA53E7B371EF92D0C8B6AE281BFF8B4658CC60`

The development APK is suitable for current manual testing. The Android-test APK must be used only with this matching application artifact and configured runner.

## External and workstation state

- Shopify Admin, Storefront data, Customer Account configuration/data, Firebase, Remote Config, backend, accounts, profiles, addresses, carts, orders, payments, and synthetic remote records: **UNCHANGED**.
- The owned privacy/contact pages and official Shopify documentation were inspected read-only. No contact form, deletion request, account, order, cart, or other remote mutation was submitted.
- The development and test APKs were installed on the authorized Samsung device for instrumentation. No camera, media, contacts, location, messages, or unrelated app was accessed.
- In-app Browser research tabs were finalized/closed. Existing Edge sessions were not changed by P3-13.
- Five required ignored configuration files remain present, ignored, and untracked. Credential values are not recorded.

## Acceptance and exact next slice

The safe mobile portion of P3-13 is complete: a real owned request resource is reachable from a revalidated signed-in boundary; owned URL policy, identity guidance, explicit local confirmation, SR-08 per-class behavior, honest local/remote outcomes, TR/EN/accessibility, local gates, and physical tests pass.

Feature group 17 and the full P3-13 roadmap exit remain **EXTERNALLY_BLOCKED** until an authorized synthetic request is actually submitted and the accountable merchant process supplies observable acknowledgement, response/SLA and completed deletion/retention outcomes across affected systems. Local cleanup and browser return must never be used as substitutes.

The exact next independent slice is **P3-14 Safe Remote Config/update policy**:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, `P3-08-HANDOFF.md`, and the P3-14 roadmap entry.
2. Verify `main` contains implementation checkpoint `0afc32a` plus the P3-13 documentation checkpoint, with a clean worktree, empty stash, five ignored configuration files present, no wrapper build, and the current device state recorded.
3. Preserve P3-13 as an externally blocked release-critical acceptance row. Do not submit the public contact form or claim remote deletion merely to turn it green.
4. Reconcile the existing Phase 2 explicit-fetch Remote Config adapter, production/proof UI separation, accepted non-blocking default, typed flag owners/types, version source, cadence/expiry, rollback, critical legal/support access, and current production configuration before editing.
5. Begin P3-14 with bounded local policy models/defaults and focused tests. Do not add an ungoverned hard force-update gate, arbitrary routes/assets/code/hosts, Analytics coupling, or proof controls to production navigation.
6. Do not begin P3-15 integrated acceptance until P3-14 is coherently completed or honestly dispositioned.

## Rollback

Revert implementation checkpoint `0afc32a` to remove the P3-13 typed route, signed-in entry, request/local-cleanup screen, controller/ViewModel, resources, Hilt binding, and tests together. No dependency, schema, migration, permission, remote record, service configuration, or backend requires rollback.
