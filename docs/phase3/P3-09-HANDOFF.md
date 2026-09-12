# P3-09 Account Access, Hosted Journey, and Session Handoff

Date: 2026-08-11

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS - 18/18 FOCUSED INSTRUMENTATION AND MANUAL HOSTED LAUNCH/CANCEL/RETURN**

Branch: `main`

Starting checkpoint: `2e995921` (`docs: complete P3-08 acceptance handoff`)

Primary implementation checkpoint: `8c7b007` (`feat: complete P3-09 account access`)

Final implementation checkpoint: `29ece10` (`fix: preserve P3-09 callback across recreation`)

Documentation checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff closes the P3-09 gate. It records the production Account destination, hosted passwordless Customer Account journey, session restore/refresh/logout behavior, minimum identity boundary, cart ownership reconciliation, local/device evidence, and the exact P3-10 boundary. It does not claim a new live OAuth success proof, editable profile fields, addresses, orders, account deletion, a production release, or P3-16 security/release hardening.

## Authority, reuse, and clean-room boundary

- `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md` authorizes conservative, reversible implementation decisions from verified primary APIs and owned configuration while preserving explicit privacy, commercial, architecture, and release gates.
- P3-08 supplies the owned legal/support routes used by signed-out and signed-in Account states. P3-01 supplies the current Turkey/TRY market context.
- Phase 2's already-proven OAuth discovery, AppAuth system-browser launch, PKCE/state/nonce transaction, token exchange/refresh, Keystore-backed session, callback coordinator, and typed Customer Account client were reused rather than recreated.
- The existing `CustomerIdentityQuery` was reused with its minimum `id` and `displayName` allowlist. Only `displayName` reaches the UI; customer IDs and all token material remain outside UI, logs, analytics, and documentation.
- No reference APK was installed, launched, rescanned, or used as a source donor. No proprietary UI, branding, credential, decompiled body, or unknown backend behavior was copied.

## Completed implementation

### User-visible production behavior

- Production bottom navigation now has five stable destinations: Home, Categories, Search, Wishlist, and Account. Cart remains a contextual commerce action and is not promoted into a sixth primary destination.
- Signed-out Account shows Turkey/TRY context, a hosted sign-in action, and reachable help/policy content. It does not collect an email, password, verification code, or registration data.
- Sign-in opens Shopify's owned passwordless Customer Account page in the system browser/Custom Tab and returns through the existing strict callback contract.
- Preparing, browser handoff, exchange, restore, refresh, cancellation, retryable failure, terminal expiry, authenticated, and logout states are explicit and localized in Turkish and English.
- Signed-in Account exposes only the minimum display name plus refresh, logout, cart, and help/policy actions. No editable profile form or inferred customer fields ship in P3-09.
- Cancellation and browser-launch failure remain recoverable in Account. Terminal authentication failure returns to a safe signed-out state with redacted copy.
- The Account surface is scrollable at 200 percent text, has semantic headings/live regions, and restores focus deterministically after hosted-flow feedback.
- Proof screens and manual OAuth controls are absent from production routing. Phase 2 adapters and test seams remain retained but unreachable from production UI.

### Session, callback, and cart ownership

- `AccountController` is the production orchestration boundary for restore, authorization preparation, validated callback consumption, refresh, logout, minimum identity fetch, and cart ownership transitions.
- `AccountViewModel` owns the unidirectional UI state and one-shot browser effect. A generation guard prevents stale asynchronous restore/preparation results from overwriting a newer callback or user action.
- A non-null validated callback is accepted after Activity/ViewModel recreation even when the new ViewModel is still restoring. Duplicate exchange is rejected. A local null cancellation is accepted only while the browser journey is awaiting a result.
- HTTP 401/403 and terminal Customer Account GraphQL authorization codes clear the session rather than retaining an unusable identity.
- `CoordinatedCartOperations` associates the current cart after authentication, supplies the customer access token to authenticated mutations, verifies response ownership, and detaches or quarantines on logout/terminal expiry according to the existing four-state ownership model.
- A customer-associated cart is never silently reclassified or exposed as anonymous. Local Wishlist and Search history remain device-local and survive account logout/expiry.
- Account display state is memory-only. No customer PII cache, Room table, DataStore field, schema change, migration, or new local deletion category was introduced.

### API, configuration, resources, and dependencies

- Reused Customer Account OAuth discovery/token/logout and `CustomerIdentityQuery`; no new GraphQL operation or schema artifact was added.
- Reused the Storefront cart mutations and encrypted ownership payload established in P3-06. Their authenticated input and response-ownership checks were completed for the production Account flow.
- Added production Account/controller/ViewModel/navigation wiring and TR/EN resources.
- No dependency, Gradle lock, dependency verification, Firebase configuration, Shopify configuration, OAuth callback identity, manifest permission, Room schema, or migration changed.
- No camera capability or `CAMERA` permission is used by the application.

### Tests added or extended

- Account controller JVM tests cover restore, identity, browser preparation, callback, refresh, logout, retryable failure, terminal expiry, and cart association/detachment outcomes.
- Account ViewModel JVM tests cover browser effects, cancellation, state recovery, and the process-recreation race where a validated callback must beat the stale initial restore.
- Customer Account gateway tests cover terminal HTTP authorization rejection.
- Cart coordinator and production cart operation tests cover token propagation, association, detach, quarantine, and response ownership.
- Account Compose tests cover signed-out and authenticated behavior, absent password UI, localized/status semantics, 200 percent text, and deterministic focus.
- Production navigation and Home tests cover the five primary destinations and contextual Cart/Account reachability.

## Validation evidence

### Relevant module and application gate

```powershell
.\gradlew.bat spotlessCheck :account:detekt :storefront:detekt :app:detekt :account:testDebugUnitTest :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest :account:lintDebug :storefront:lintDebug :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS**, 316 tasks, completed in 6 minutes 19 seconds.

- Account JVM: **40/40 PASS**.
- Storefront JVM: **36 PASS, 2 SKIPPED, 0 failures/errors**. The skipped tests are the pre-existing opt-in owned Storefront live-read/cart proofs; they were previously proven and intentionally were not repeated for reassurance.
- The final application JVM run after the recreation-race regression test: **74/74 PASS**.
- Relevant final aggregate: **150 PASS, 2 SKIPPED, 0 failures, 0 errors**.
- Spotless, detekt, account/storefront/app lint, application APK assembly, and Android test APK assembly: **PASS**.

The final callback-race checkpoint was then verified with:

```powershell
.\gradlew.bat spotlessApply spotlessCheck :app:detekt :app:testDevelopmentDebugUnitTest :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS**, 301 tasks, completed in 5 minutes 23 seconds.

- `gitleaks dir . --no-banner --redact --exit-code 1 --log-level error`: **PASS**.
- `git diff --check`: **PASS**.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-Phase3Planning.ps1`: recorded after this handoff is added; it must remain **15/15 PASS** before the handoff commit.

One initial Gradle attempt encountered a transient ASM output-directory lock after an earlier timed-out parent left a child process finishing. The existing process was allowed to finish and the serialized gate passed. Early focused UI compilation/focus assertions and detekt findings were fixed in scope; no failing result is hidden. The final handoff review then found the process-recreation callback race, which is covered by the new deterministic JVM regression and fixed by `29ece10`.

### Final physical instrumentation

Device: Samsung SM-A225F, Android 13, API 33. Serial is intentionally omitted.

```powershell
.\gradlew.bat :app:connectedDevelopmentDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.gurbakir.mobile.account.AccountScreenTest,com.gurbakir.mobile.ProductionNavigationTest,com.gurbakir.mobile.home.HomeScreenTest' --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS, 18/18 tests, 0 skips, 0 failures**, completed in 1 minute 55 seconds.

The final runner covers the Account screen, five-destination navigation, Home contextual actions, large text, and focus behavior on the physical device. The connected runner removes its temporary application/test installs when it finishes.

### Manual hosted journey acceptance

The normal development application was installed and the following passed without instrumentation:

- open Account in signed-out state and confirm Turkey/TRY context, five primary destinations, help/policy access, and absence of password UI;
- start sign-in and confirm a genuine Shopify-hosted Gür Bakır passwordless page in a Chrome Custom Tab;
- enter no email, code, credential, or account value;
- use system Back, return to Account, and confirm the visible cancellation state and stable signed-out UI.

A live hosted OAuth success was **NOT RERUN** because the callback identity and official OAuth contract are unchanged. The Phase 2 live success, restore, refresh, and logout proof is **PREVIOUSLY PROVEN**; changed P3-09 orchestration is covered by deterministic JVM/instrumentation tests. Manual full TalkBack speech/traversal is **NOT RUN** and remains part of P3-15's accumulated accessibility audit.

## Artifacts

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 19,902,081 bytes
  - SHA-256 `854B85991E9110000D601AEB83EFFE9C966BF64CE913BDC662AF2A4079488E0A`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,247,810 bytes
  - SHA-256 `91304B563FA1B68EB4A409312628138A1B1AA31A5C5CA021CEC10497B2F6A3FB`

Install the application APK above for continued manual testing. Use the Android test APK only with its matching application artifact and runner configuration.

## External state and privacy

- Shopify Admin, storefront configuration, Customer Account configuration, Firebase, Remote Config, and any project backend: **UNCHANGED**.
- Accounts, customers, carts, orders, payments, and synthetic remote records: **NONE CREATED OR MUTATED**.
- The hosted Shopify sign-in page was opened read-only; no email or verification code was submitted.
- Physical device: only the project development app/test packages and temporary runner state were controlled. No unrelated package or user data was intentionally changed.
- Edge was not used. The Chrome Custom Tab returned to the app; no active browser automation remains.
- No credential, token, customer ID, arbitrary URL, hosted form value, or PII entered source, committed documentation, logs, analytics, or application persistence.

## Acceptance boundary and exact next slice

P3-09 exit criteria are satisfied: hosted launch/cancel/failure/expiry/logout states are coherent; no legacy password/register UI exists; session material remains Keystore-backed; the customer-associated cart cannot leak into anonymous state; legal/support remains reachable; production proof controls are absent.

P3-10 may begin. Resume in this order:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, the P3-10 roadmap section, CI-32, the Profile screen/flow/persistence records, and current Customer Account schema/operation evidence.
2. Verify `main` contains `8c7b007`, final implementation checkpoint `29ece10`, and this handoff commit with a clean worktree and preserved ignored local configuration.
3. Confirm the smallest privacy-preserving field allowlist supported by the current owned Customer Account schema and active owner policy. Keep birthdate/metafield excluded and do not infer protected fields.
4. Begin the first unfinished atomic action: define the explicit production Profile read/edit field model and validation/confirmed-save contract before adding UI.
5. First targeted check: profile mapper/validation/repository JVM tests plus app Kotlin compilation; then add session/conflict/offline and Compose coverage.
6. P3-10 exits only when only approved fields can be read/edited, saves are server-confirmed, unsafe form data is not persisted across process recreation, and permission/session failures recover safely.
7. Do not begin P3-11, P3-12, P3-13, P3-15, or P3-16 before the P3-10 exit evidence is reconciled. P3-11/P3-12 may proceed independently only after their own explicit input gates are satisfied.

## Rollback

Revert `29ece10` and then `8c7b007` to remove the P3-09 production Account flow and its cart/session integration while preserving the Phase 2 foundation and P3-08 legal/support baseline. Reverting these commits does not change Shopify, Firebase, Customer Account, browser, account, cart, or order state because this slice performed no external mutation.
