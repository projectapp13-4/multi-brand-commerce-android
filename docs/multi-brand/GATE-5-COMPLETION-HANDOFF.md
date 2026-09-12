# Gate 5 Firebase / Provider Isolation Completion Handoff

Status date: 2026-09-10

Status: **Local source implementation, stable-candidate verification and focused security/privacy review are complete. Fresh exact-HEAD CI/review/security triage, owner merge and exact merged-main verification remain pending. Gate 5 is not closed.**

This is the historical pre-merge implementation/evidence record for Gate 5. It does not claim live Firebase service health, physical-device delivery, production readiness or post-merge evidence. Read [current authority](../README.md), [owner boundaries](../OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md), [canonical architecture](../architecture/MULTI-BRAND-ARCHITECTURE.md) and the [legacy identity register](../architecture/GURBAKIR-LEGACY-IDENTITIES.md) with this record. Historical Gate 1–4 handoffs remain unchanged.

## Implemented ownership boundary

| Owner | Gate 5 result |
|---|---|
| `:foundation` | `AppConfiguration` no longer carries Firebase readiness or unused telemetry-provider policy. Public `TelemetryPolicy` was removed. `BrandConfiguration.analyticsEventNamespace` remains provider-neutral brand metadata. |
| `:mobile-core` | Existing `UpdatePolicyRemoteGateway`, result/snapshot/controller/store ownership, cache and UI remain provider-neutral and unchanged. The persisted `"firebase-remote-config"` source marker remains compatible. `:mobile-core` has no Firebase dependency edge. |
| `:firebase` | Firebase SDK mechanics, strict six-key policy parsing, safe defaults and public no-argument Remote Config factory remain. An internal client-factory seam permits construction-failure testing without changing the public API. Messaging remains debug-only. |
| Gürbakır `:app` | Retains the unconditional `:app -> :firebase` dependency, Google Services application, Hilt composition, provider selection and debug proof. `BuildConfig.FIREBASE_CONFIGURED` is derived solely from the complete validated four-file configuration set and is used consistently for policy selection, debug readiness, proof navigation and proof-DI precondition. |
| Non-production `:synthetic` | Still binds the neutral gateway to `LocalDefaults` and has no Firebase project dependency, plugin, configuration, generated resource, manifest component, archive entry or DEX namespace. |

No provider enum, plugin framework, runtime merchant switch, source-set binding override or replacement gateway seam was introduced. `FirebaseUpdatePolicyRemoteGateway` remains the Gürbakır adapter over the existing provider-neutral contract.

The tracked `firebase.enabled`, `telemetry.analyticsEnabled` and `telemetry.crashlyticsEnabled` controls and their BuildConfig fields were removed. The ignored local properties file is neither rewritten nor authoritative for these retired keys. Gradle applies Google Services only after the all-four completeness, exact-package client and development/staging project-relationship checks pass. The separately invoked Firebase validator proves ignore protection and prohibited-key absence in addition to those relationships. Zero files is a valid unconfigured state; a partial set remains forbidden; `-RequireConfigured` still requires and validates the complete set.

The four-file package/project relationship remains:

| Variant | Application ID | Project relationship |
|---|---|---|
| Development debug | `com.gurbakir.mobile.dev.debug` | Development project |
| Development release | `com.gurbakir.mobile.dev` | Same development project |
| Staging debug | `com.gurbakir.mobile.staging.debug` | Staging project |
| Staging release | `com.gurbakir.mobile.staging` | Same staging project, distinct from development |

No application ID, Firebase registration, OAuth/App Link identity, Room database/schema, SharedPreferences identity, Android Keystore alias, encrypted-store identity or persisted update-policy key changed. Firebase BoM/plugin versions, catalog, dependency locks and verification metadata are unchanged.

## Provider selection and debug-proof boundary

Gürbakır selects before Firebase SDK construction:

- When `FIREBASE_CONFIGURED` is false, `UpdatePolicyModule` returns a neutral `LocalDefaults` result without dereferencing `Provider<RemoteFeatureFlags>`, and `FirebaseModule` returns `LocalDefaultFeatureFlags` without invoking the SDK factory.
- When true, the provider is dereferenced once and wrapped by the existing `FirebaseUpdatePolicyRemoteGateway`; the Firebase Remote Config factory is invoked once.
- A Remote Config client-construction failure returns safe local defaults. Cancellation and the existing fail-closed parsing behavior remain intact.

The unconfigured Gürbakır production update-policy path is supported and local-safe. The debug Firebase proof is deliberately unavailable when unconfigured:

- Foundation shows Firebase as waiting/unconfigured and disables only its Details action. Other unready integrations retain their diagnostic actions.
- Notification proof requests are consumed without navigation.
- Direct or restored Firebase detail routes render a localized unavailable status and Back action.
- The route guard executes before the configured content lambda and therefore before `hiltViewModel<FirebaseProofViewModel>()`, `FirebaseProofController`, its target recorder or `PushRegistrationCoordinator` is resolved.
- `ProofModule` independently rejects accidental unconfigured push-coordinator construction before invoking the existing Firebase factory.

Configured debug retains the existing Firebase proof controller/ViewModel/screen and explicit-consent push actions. Constructing the graph does not register for push; `register()` remains action-driven. No unavailable/no-op coordinator was added and no nullable/lazy handling was spread through the controller or ViewModel. Proof UI and Messaging remain outside release reachability.

`FIREBASE_CONFIGURED` means a complete build-time configuration set, not live provider health. In an unconfigured Gürbakır APK, Firebase SDK classes and `FirebaseInitProvider` may remain packaged and its startup hook may run; no generated Google app ID or configured default app is expected. Physical Firebase absence is a synthetic contract, not an unconfigured Gürbakır contract.

## P3-14 and compatibility preservation

The six approved Remote Config keys, strict lowercase Boolean/number/schema parsing, safe defaults, 12-hour SDK cadence, 10-second timeout, 24-hour application cache, timestamp validation, revision behavior and rollback by remote false values are unchanged. Startup remains non-blocking; maintenance priority, optional-update behavior and legal/support access remain unchanged. Remote values cannot supply executable copy, URLs, routes, assets, components or code, and no hard force-update behavior was added.

Analytics, Crashlytics, advertising-ID collection, Messaging auto-init and delivery metrics remain hard-disabled by manifest policy. Gate 5 adds no Analytics/Crashlytics dependency, production FCM path or remote authority. No Firebase console/configuration value was mutated, no FCM message was sent and no Remote Config value was published.

## Actual implementation history

Execution baseline: `c62d1ead3e286ce84f3906ffbb25ecb8d9b16441`, initially clean and synchronized with `origin/main` at zero ahead/behind.

| Commit | Actual scope |
|---|---|
| `148d71ee23289e98ea27b84aed70ce0f47e17ecc` | `refactor(multibrand): isolate Gate 5 Firebase providers` — configuration ownership, file-derived readiness, eager-construction avoidance, explicit debug-proof boundary, tests and strengthened existing validators |
| Documentation-bearing candidate | `docs(multibrand): record Gate 5 completion handoff` — this historical evidence record; its own hash is intentionally not embedded recursively and must be resolved from Git/PR evidence |
| Final PR-review correction | `test(multibrand): close Firebase package validator gaps` — broader manifest/archive ownership predicates, resource-table validation, typed-route coverage and audit wording/count corrections |
| Exact-HEAD review correction | `test(multibrand): reject Firebase Analytics metadata` — bounded `google_analytics_*` manifest rejection, three discriminating fixtures and restoration-proof wording correction |

## RED and counterfactual evidence

The implementation was characterized with behavior-discriminating failures rather than API-removal compile failures alone:

- On the untouched baseline, a focused API 36 Compose test failed because the false-readiness Firebase card still invoked its Details callback. The corrected test proves only Firebase is disabled and other unready diagnostic actions remain available.
- In a zero-file unconfigured build, a temporary pre-fix direct/notification route counterfactual forced configured proof composition. It failed at `FirebaseMessaging.getInstance()` with `IllegalStateException: Default FirebaseApp is not initialized`, through `ProofModule` → `PushRegistrationCoordinator` → `FirebaseProofController` → Hilt ViewModel → configured content. Restoring the guard returned the worktree exactly to the committed source.
- Temporarily breaking the Remote Config construction fallback made its focused test fail; restoring the fallback passed.
- Temporarily dereferencing the false `RemoteFeatureFlags` supplier made the Firebase selection test fail; restoring selection-before-construction passed.
- Temporarily dereferencing the false update-policy provider made the gateway selection test fail; restoring laziness passed.
- Temporarily bypassing the `ProofModule` configured precondition made the factory-count assertion fail; restoring the precondition passed.

The first attempted UI counterfactual clicked the disabled Firebase action and failed only on a later root assertion; because it did not reach Hilt construction, it is deliberately excluded from the proof above.

## Local stable-candidate evidence

All Gradle commands used the pinned Windows wrapper. The stable source candidate is `148d71e`. Cache and UP-TO-DATE reuse are reported rather than presented as fresh body execution.

```powershell
.\gradlew.bat spotlessCheck detekt lint
.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest :checkout:testDebugUnitTest :storefront:testDebugUnitTest :firebase:testDebugUnitTest :mobile-core:testDebugUnitTest :synthetic:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:testStagingDebugUnitTest
.\gradlew.bat :mobile-core:assembleDebug :mobile-core:assembleRelease :app:assembleDevelopmentDebug :app:assembleDevelopmentRelease :app:assembleStagingDebug :app:assembleStagingRelease :app:assembleDevelopmentDebugAndroidTest :app:assembleStagingDebugAndroidTest :synthetic:assembleDebug :synthetic:assembleRelease :synthetic:assembleDebugAndroidTest
pwsh -NoProfile -File .\scripts\Test-FirebaseConfiguration.ps1
pwsh -NoProfile -File .\scripts\Test-RepositoryPortability.ps1 -SelfTest
pwsh -NoProfile -File .\scripts\Test-RepositoryPortability.ps1
pwsh -NoProfile -File .\scripts\Test-Gate2SyntheticPackage.ps1 -SelfTest
pwsh -NoProfile -File .\scripts\Test-Gate2SyntheticPackage.ps1 -Variant All
```

- Full `spotlessCheck detekt lint` passed in 6m25s: 327 tasks, 147 executed, 18 from cache and 162 up-to-date.
- The canonical nine-module JVM command passed in 1m36s: 227 tasks, 29 executed, 21 from cache and 177 up-to-date.
- The complete core/Gürbakır/synthetic assembly and AndroidTest-package matrix passed in 17m11s: 754 tasks, 346 executed, 192 from cache and 216 up-to-date.

| JVM lane | Tests | Skipped | Failures / errors |
|---|---:|---:|---:|
| foundation | 17 | 0 | 0 / 0 |
| account | 61 | 0 | 0 / 0 |
| checkout | 7 | 0 | 0 / 0 |
| storefront | 60 | 2 | 0 / 0 |
| firebase | 4 | 0 | 0 / 0 |
| mobile-core | 154 | 0 | 0 / 0 |
| synthetic | 5 | 0 | 0 / 0 |
| app development | 42 | 0 | 0 / 0 |
| app staging | 42 | 0 | 0 / 0 |
| **Total** | **392** | **2** | **0 / 0** |

The two skipped Storefront tests retain their explicit live-proof opt-in; 390 tests were non-skipped. No live Storefront behavior is inferred.

Configuration and validator evidence:

- Zero Firebase files passed the unconfigured validator. A mutation containing exactly one variant file failed with the required partial-set error; cleanup restored zero files and passed again.
- A temporary complete ignored four-file set passed `-RequireConfigured`; all files remained ignored. Generated `FIREBASE_CONFIGURED=true`, `google_app_id`, default-app initialization, exact package/project mapping and Messaging auto-init false were verified without printing configuration values. The files were removed from this worktree afterward and unconfigured validation passed.
- Repository portability self-tests passed **38/38** isolated fixtures; live structural validation passed **44/44** checks. Mutation assertions cover the removed generic Firebase/telemetry controls.
- Synthetic package self-tests passed **43/43** isolated fixtures, including counterfactual `FirebaseInitProvider`, Firebase collection/Messaging/delivery/Analytics metadata, manifest and resource-table `google_app_id`, Firebase DEX descriptor, project Firebase namespace, Kotlin-module/properties/raw-resource archive entries and near-match controls. Real debug/release package validation passed **59/59** checks.

Runtime evidence used an API 36 local AVD as a focused supplemental lane:

- Configured: `FirebaseRuntimeTest` plus `FirebaseProofHiltTest` passed **2/2**. The proof graph rendered; the registration action was not pressed.
- Unconfigured: `FoundationScreenTest` plus `FirebaseRuntimeTest` passed **16/16**. This covered false readiness, disabled-only-Firebase action, notification consumption without navigation, the unavailable destination branch, zero configured-content evaluation, no generated Google app ID/default app and Messaging auto-init false.
- After the final RED counterfactual was restored, the three provider/precondition JVM suites passed, and the same unconfigured API 36 focused lane again passed **16/16**.
- The PR-review correction added an actual typed `IntegrationDetail(FIREBASE)` navigation test. In the unconfigured API 36 lane it passed **1/1** executed assertion set before Firebase proof Hilt construction; the separate configured-only proof test was skipped by its explicit configuration assumption.

Artifact and dependency inspection found:

- Gürbakır debug retains Remote Config, Installations, debug-only Messaging and debug proof classes.
- Gürbakır release retains Remote Config and Installations, and excludes Messaging, Firebase proof, Analytics and Crashlytics.
- The unconfigured debug merged manifest retains `FirebaseInitProvider`, while its resource table has no `google_app_id`.
- `:mobile-core` and synthetic retain zero Firebase dependency edges. Synthetic debug and R8 release contain no Firebase manifest component, generated resource, archive entry or DEX namespace.
- No Firebase Analytics, Crashlytics or release Messaging dependency was resolved.

At source candidate `148d71e`, redacted Gitleaks inspection of all 28 changed source/validator files reported no leak across approximately 366.20 KB. Focused security/privacy review fully covered those 28 files across seven surfaces and returned zero findings. Its immutable snapshot digest is `codex-security-snapshot/v1:sha256:8186c993dbd477c085d63ff0d15c9aa6b535f118b2cc047cb237b55efbda13f0`. The detailed report is a local ephemeral artifact and its workstation path is intentionally excluded from this handoff. This was static analysis and review, not Firebase runtime proof; the later correction still requires fresh exact-HEAD security triage under the closure lifecycle below.

## Evidence limits

The canonical local API 30 managed-device command failed before tests at `:account:ciApi30Setup` because the configured `android-30/aosp_atd/x86` system image was unavailable. **NOT RUN: zero API 30 managed-device tests.**

The separate synthetic API 23 managed-device lane installed its image but the emulator remained offline and produced no test result before the bounded attempt was stopped. **NOT RUN: zero API 23 managed-device tests.** Fresh GitHub CI on the clean exact candidate remains authoritative for both managed-device lanes.

Physical-device Firebase delivery is **NOT RUN** and is not required for this ownership gate. Live Remote Config fetch, push delivery/token lifecycle, Firebase console registrations/projects, production configuration, Analytics, Crashlytics, production FCM, signing, Play publication and P3-16 remain unproven and out of scope. Managed-device configured proof was read-only apart from app installation and local UI navigation. No real order/payment/customer deletion or reference service was contacted.

## Required closure lifecycle

This handoff intentionally stops before closure. The remaining non-recursive lifecycle is:

1. Produce the final documentation-bearing Gate 5 candidate.
2. Obtain fresh exact-HEAD CI, whole-candidate review and focused security/privacy triage; after any accepted correction, repeat relevant local verification and all exact-HEAD gates.
3. Owner performs the merge.
4. Verify exact merged `main`: clean synchronization, candidate ancestry, push CI and relevant validators.
5. Gate 5 is technically **CLOSED** only then.
6. Do not rewrite this handoff to record those later lifecycle events.
7. From verified merged `main`, create a separate narrow reconciliation change that updates only live authority/status documents still saying Gate 5 is next or not started.
8. Require reconciliation PR CI/review, owner merge and post-merge verification.
9. Begin Gate 6 planning only after reconciliation completes. Reconciliation delay does not reopen technically closed Gate 5.

No current authority/status document is reconciled by this implementation candidate. Gate 6, real-brand onboarding, external-provider mutation and P3-16 remain unstarted.

Rollback is a normal reviewed revert of the Gate 5 source/validator commit and this documentation commit. No persistence migration, dependency/version change or external-provider mutation occurred, so no persisted-state or provider rollback is required. Preserve unrelated work and do not rewrite history.
