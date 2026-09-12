# P3-10 Profile Implementation and Physical Acceptance Handoff

Date: 2026-08-11

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS - 15/15 FOCUSED INSTRUMENTATION**

External live profile-mutation status: **EXTERNALLY UNVERIFIED**

Branch: `main`

Starting checkpoint: `f51c3f7` (`docs: complete P3-09 acceptance handoff`)

Implementation checkpoint: `f0d01a5` (`feat: complete P3-10 profile`)

Documentation checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff closes the P3-10 engineering gate. It records the independently implemented first-name/last-name Profile surface, current Customer Account schema authority, confirmed-save and conflict rules, privacy boundary, deterministic local/device evidence, and exact P3-11 boundary. It does not claim a live customer mutation, address management, orders, account deletion, full TalkBack audit, release readiness, or P3-16 security/release hardening.

## Authority, field decision, and clean-room boundary

- The active owner policy authorizes a conservative, reversible allowlist selected from current primary API evidence while preserving privacy and explicit product exclusions.
- Shopify Customer Account API 2026-07 documents [`customerUpdate`](https://shopify.dev/docs/api/customer/latest/mutations/customerupdate) with `firstName` and `lastName` inputs and exposes those nullable fields on the current [`Customer`](https://shopify.dev/docs/api/customer/latest/objects/Customer) object. The existing `customer-account-api:full` scope already contains the required customer write authority; no scope or callback change was made.
- The P3-10 production allowlist is exactly `firstName` and `lastName`. Email, phone, birthdate, metafields, marketing permissions, and every inferred field are absent. Birthdate/metafield remains excluded under SR-03 until a separate purpose, privacy, schema, edit, and deletion decision exists.
- The 255-character client limit is a reversible application input-safety bound. Shopify remains authoritative and any structured server rejection is shown without exposing raw remote messages.
- No reference APK was installed, launched, rescanned, or used as a source donor. No decompiled implementation, proprietary expression, branding, asset, credential, or unknown Worker behavior was copied.

## Completed implementation

### User-visible behavior

- Authenticated Account now exposes a typed Profile destination; signed-out Account does not expose it.
- Profile reads and edits only first name and last name, with Turkish and English labels, validation, progress, saved/no-change feedback, retry, conflict, connection, service, and unconfirmed-save states.
- Names are trimmed for mutation; blank values intentionally clear the corresponding nullable server field. Control characters and values longer than 255 trimmed characters are rejected locally.
- A save is never optimistically announced. The UI accepts success only from the mutation's returned customer data.
- Before mutation, the controller rereads the current server profile and compares it with the loaded baseline. A stale baseline produces an explicit conflict without issuing the mutation.
- Null and empty server names share one display/conflict baseline, preventing a false conflict for semantically blank values.
- A top-level GraphQL or transport failure after mutation dispatch is classified as `SAVE_UNCONFIRMED`; the draft remains visible but disabled until an explicit authoritative server reload.
- Structured server field errors focus and announce the first rejected editable field. Form-level rejections remain redacted.
- Terminal session/authentication failure clears the Keystore-backed customer session, removes the stale Account back-stack entry, recreates signed-out Account state, and reruns existing cart ownership reconciliation.
- The form scrolls at 200 percent font scale, uses heading/error/live-region semantics and deterministic field focus, and keeps Back reachable.

### Data, API, and architecture

- Added the minimal versioned Customer Account schema slice plus generated `CustomerProfile` query and `CustomerProfileUpdate` mutation.
- Added a separate discovery-backed `CustomerProfileGateway`, reusing the existing authenticated Apollo call executor and session resolver. Authorization/token values remain redacted and outside UI/state/log output.
- Added `ProfileController`, `ProfileViewModel`, typed `ProfileRoute`, Compose screen, Hilt bindings, Account entry action, and production navigation reset on terminal session failure.
- The ViewModel uses unidirectional `StateFlow` state and a one-shot effect. First/last name and drafts are memory-only and redacted by state/model `toString` implementations.
- Process recreation creates a new ViewModel and reloads the server; an unsaved draft is not placed in `SavedStateHandle`, Room, DataStore, Bundle state, Android backup, logs, or analytics.
- No Room schema, migration, local deletion category, dependency, Gradle lock, dependency verification, manifest permission, Shopify configuration, Firebase configuration, OAuth callback identity, or persistent storage changed.
- Existing proof controllers/test seams remain unreachable from production routing; no new proof UI exists.

### Tests added or extended

- Gateway contract tests cover authenticated query/mutation headers, nullable inputs, success mapping, structured field rejection, signed-out behavior, and top-level GraphQL failure.
- Controller tests cover the approved field projection, confirmed server result, structured rejection, stale conflict without mutation, null/empty baseline equivalence, ambiguous save, and terminal session clearing.
- ViewModel tests cover validation/trimming, confirmed save, server field focus, unconfirmed draft/reload behavior, process recreation without draft persistence, and signed-out effects.
- Profile Compose tests cover the exact two-field surface, confirmed-save feedback, field error focus/announcement, unconfirmed reload state, and 200 percent font scale.
- Account Compose tests prove Profile is absent signed out and available authenticated.
- Production navigation tests prove the typed Profile route and that terminal profile expiry replaces, rather than reuses, the stale Account back-stack/ViewModel entry.

## Validation evidence

### Broad implementation gate

```powershell
.\gradlew.bat spotlessCheck :account:detekt :app:detekt :account:testDebugUnitTest :app:testDevelopmentDebugUnitTest :account:lintDebug :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS**, 307 tasks, completed in 4 minutes 44 seconds.

- Account JVM: **43/43 PASS**.
- Application JVM at this milestone: **84/84 PASS**.
- Spotless, account/app detekt, account/app lint, application APK assembly, and Android test APK assembly: **PASS**.

After final navigation-session review and the null/empty conflict-baseline regression, the proportional final application gate was:

```powershell
.\gradlew.bat spotlessApply spotlessCheck :app:detekt :app:testDevelopmentDebugUnitTest :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS**, 192 tasks, completed in 4 minutes 7 seconds.

- Final application JVM: **85/85 PASS, 0 skipped, 0 failures, 0 errors**.
- Final Spotless, app detekt, application APK assembly, and matching Android test APK assembly: **PASS**.
- The previously completed final app lint report contains **0 issues**; no lint-relevant file changed after that report.
- `gitleaks dir . --no-banner --redact --exit-code 1 --log-level error`: **PASS**.
- `git diff --check`: **PASS**.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-Phase3Planning.ps1`: recorded after this handoff is added; it must remain **15/15 PASS** before the documentation commit.

Initial focused compilation found XML apostrophe escaping and a JVM-incompatible test-fake `MutableList.removeFirst()` call; both were fixed. Detekt reported return-count, long-method, long-parameter-list, and line-length findings; the implementation was refactored without suppressing those findings. The final review found stale signed-in Account state after terminal Profile expiry and null/empty baseline equivalence; both are covered by regressions. No failing result is hidden.

### Physical instrumentation

Device: Samsung SM-A225F, Android 13, API 33. Serial is intentionally omitted.

```powershell
.\gradlew.bat spotlessApply :app:connectedDevelopmentDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.gurbakir.mobile.profile.ProfileScreenTest,com.gurbakir.mobile.account.AccountScreenTest,com.gurbakir.mobile.ProductionNavigationTest' --no-configuration-cache --no-parallel --console=plain
```

Result: **PASS, 15/15 tests, 0 skips, 0 failures, 0 errors**, runner time 26.263 seconds; Gradle command completed in 4 minutes 45 seconds.

The device group covers the Profile UI/large-text/focus/error states, signed-in/signed-out Account exposure, typed navigation, and terminal-session back-stack replacement. The later null/empty baseline adjustment is controller-only and is covered by the final JVM regression; it did not change Compose/device behavior. Manual full TalkBack speech/traversal is **NOT RUN** and remains part of P3-15's accumulated accessibility audit.

### Live/external proof boundary

- A live authenticated profile read/mutation was **NOT RUN** because no synthetic hosted Customer Account session was established in this slice and no email/code/inbox interaction was entered.
- Existing hosted OAuth/session success remains **PREVIOUSLY PROVEN** by Phase 2; P3-10 did not change the callback identity, token contract, or remote configuration.
- Query/mutation behavior is locally contract-tested with generated Apollo models and MockWebServer fixtures; this is not misreported as a live remote mutation.

## Artifacts

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 19,911,492 bytes
  - SHA-256 `E1AC82C72E227C97A5D81408C72069140BAEE1838809A004F407B2210BFDB105`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,258,106 bytes
  - SHA-256 `B5B70410AA50E3490603020B753CE1C07979470FA0B43B54B8657300DC235133`

Install the application APK above for continued manual testing. Use the Android test APK only with its matching application artifact and runner configuration.

## External state and privacy

- Shopify Admin, storefront, Customer Account configuration, Firebase, Remote Config, and project backend: **UNCHANGED**.
- Customer profiles, accounts, addresses, carts, orders, payments, and synthetic remote records: **NONE CREATED OR MUTATED**.
- No email, verification code, password, 2FA value, token, customer ID, or profile PII was entered in a browser or persisted by the application during this slice.
- Physical device: project development/test packages and focused runner state only. No unrelated application/package or user data was intentionally changed.
- Edge/browser automation: **NOT USED**. No browser automation remains active.
- Camera: the Gürbakır application declares no camera capability or `CAMERA` permission; P3-10 performed no camera operation.

## Acceptance boundary and exact next slice

P3-10 exit criteria are satisfied: the exact approved field allowlist is enforced; birthdate/metafield and unrelated customer fields are absent; saves are server-confirmed; stale and ambiguous outcomes require authoritative reload; unsafe drafts are memory-only; terminal permission/session failures clear private state and recover to a fresh Account surface; focused local and physical tests pass.

P3-11 may begin. Resume in this order:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, the P3-11 roadmap section, CI-29, address screen/flow/persistence records, and the current Account/Profile/session implementation.
2. Verify `main` contains implementation checkpoint `f0d01a5` and this handoff commit with a clean worktree, empty stash, and preserved ignored configuration.
3. Inspect the current official Customer Account API address object/input and create/update/delete/default mutations. Record exact field nullability, error paths, required scopes, and default-address semantics before changing the local schema.
4. Decide and document the smallest market-aware country/field policy consistent with the established Turkey/TRY market and active owner policy; do not silently claim unsupported worldwide behavior.
5. First implementation action: define the typed address domain/field/error model and gateway contract, with full PII redaction and no local persistence, before adding screens.
6. First targeted command: compile generated Account GraphQL sources and run focused address gateway/mapper tests, then app compilation.
7. P3-11 exits only when list/add/edit/delete/default behavior, confirmations, conflicts, session expiry, large text, process recreation, and no-address-PII-persistence are proven for the supported market policy.
8. Do not begin P3-12 orders, P3-13 deletion, P3-15 accumulated quality, or P3-16 release/security hardening before the P3-11 exit evidence is reconciled.

## Rollback

Revert implementation checkpoint `f0d01a5` to remove P3-10 Profile query/mutation/UI/navigation while preserving P3-09 Account/session/cart behavior. Reverting it does not require a database migration or external cleanup because P3-10 added no local persistence and performed no remote mutation.
