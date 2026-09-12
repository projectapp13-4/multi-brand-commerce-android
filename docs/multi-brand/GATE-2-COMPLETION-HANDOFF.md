# Gate 2 Synthetic Application Completion Handoff

> **Implementation status:** COMPLETE IN SOURCE. Gate 2 acceptance remains conditional on fresh GitHub Actions and CodeRabbit results attached to the current review-cleanup PR HEAD. Confirm both on PR #14 before treating this handoff as accepted-current evidence.
>
> **Scope stop:** Gate 3 and P3-16 are not started. This handoff does not authorize either one.
>
> **Observed on:** 2026-09-08 execution closeout; 2026-09-09 owner-requested review cleanup

## Executive outcome

Gate 2 implemented the approved second Android application edge without changing the shared runtime API or Gürbakır production source/configuration. Logical Gradle project `:synthetic` maps to physical directory `apps/synthetic`, owns a separate Android identity and sandbox, and composes the existing `:mobile-core` implementation through the same focused configuration/policy contracts used by the Gürbakır shell.

The synthetic application is deliberately non-production. It has `.invalid` service origins, blank controlled public-client inputs, disabled telemetry, no Firebase dependency or packaged Firebase registration, no INTERNET permission, an unsigned minified release, isolated local persistence, and process-memory cart/customer stores. It proves that a second APK can be built, packaged, launched, navigated, and tested without turning Gürbakır into the reusable architecture or introducing a runtime merchant switch.

Gate 2 does **not** prove a real second merchant, production configuration, capability-driven navigation, durable reusable protected stores, generic providers, complete market/localization variation, reusable domain/media policy, external association/registration, signing, publication, or production readiness.

## Authority, base, and repository line

| Item | Final Gate 2 value |
|---|---|
| Approved plan | `docs/multi-brand/plans/GATE-2-SYNTHETIC-APPLICATION-IMPLEMENTATION-PLAN.md` |
| Planning/execution base | `a6f3b3ffbfedd0e188b49c31936b9dce80ce0628` |
| Branch | `codex/multibrand-gate-2-synthetic` |
| Execution workspace | Local isolated worktree for PR #14; machine-specific path intentionally omitted |
| Pull request | #14 (private historical archive) |
| Merge status at Gate 2 execution closeout | NOT MERGED on 2026-09-08; any later owner-controlled PR merge is a subsequent repository event, not a contradiction of this historical observation |

At execution start, a fresh fetch confirmed `origin/main` exactly equalled the planning SHA; the local/remote Gate 2 branch and worktree did not exist; the new worktree began clean at that SHA with zero inherited commits. Immediately before the first push, another `git fetch origin --prune` confirmed that `origin/main` and the branch merge-base still equalled the same SHA and that the remote Gate 2 branch was absent. The normal push created the remote branch; no force push, amend, squash, merge, or branch deletion was used.

## Exact final architecture state

Gradle reports nine project objects including the root and exactly eight included subprojects:

```text
Root project 'gurbakir-android'
├── :account
├── :app
├── :checkout
├── :firebase
├── :foundation
├── :mobile-core
├── :storefront
└── :synthetic -> apps/synthetic
```

The implemented application edges are:

```text
:app (Gürbakır application/composition shell)
   └──> :mobile-core

:synthetic (non-production conformance application)
   └──> :mobile-core
           ├──> :foundation
           ├──> :storefront
           ├──> :account
           └──> :checkout

:app ──> :firebase (when selected by existing Gürbakır environment composition)
:synthetic -X-> :firebase
shared/provider modules -X-> application modules
application modules -X-> one another
```

There is no `:apps` Gradle project, no `:apps:synthetic`, no brand flavor, no runtime merchant selector, and no additional real-brand application. The `apps/synthetic` filesystem grouping is intentionally independent of the flat logical project name.

## What was implemented

### Synthetic Android application

`apps/synthetic` now contains an Android application with:

- namespace/release ID `com.example.gate2synthetic` and debug ID `com.example.gate2synthetic.debug`;
- `Gate2SyntheticApplication`, `MainActivity`, edge-to-edge Compose setup, Hilt composition, and a direct call to `MobileCoreApp`;
- debug and release build types only, Java 17, minimum SDK 23, compile/target SDK 36;
- an R8/resource-shrunk, deliberately unsigned release;
- app-owned label, launcher vector, theme, legal/support screen, deletion rejection, restrictive backup/data-transfer policy, and cleartext prohibition;
- three inert `links.gate2.invalid` deep-link filters with `autoVerify=false` plus AppAuth scheme `shop.0.gate2synthetic`;
- explicit removal of transitive INTERNET permission;
- app-owned default and English resource overlays, including `Gate 2 Synthetic`, `Synthetic Lab`, and `Synthetic Picks`.

### Configuration and design composition

The app supplies existing typed contracts rather than adding a shared runtime seam:

- brand key/display/locale/analytics namespace, the complete light/dark color sets, complete typography values, spacing, shape, motion, asset references, and legal URLs;
- `DEVELOPMENT` environment, `ZZ`/`XTS` fixed market, `en-CA` locale data, synthetic Home/Catalog fixtures, `AB-0000`-shape postal policy, tracking allowlist, and deep-link bases;
- `.invalid` Storefront and Customer Account origins, empty Storefront token/client ID, Firebase/analytics/crash reporting disabled, and local-default update policy;
- Search and Wishlist partitions `DEVELOPMENT / ZZ`;
- Room database `gate2-synthetic-local.db` at schema 2, created fresh without a Gürbakır migration;
- singleton Mutex-protected process-memory implementations of `CartSessionStore` and `CustomerSessionStore`.

Configuration deliberately reports only the two expected validation issues, `STOREFRONT_PUBLIC_TOKEN` and `CUSTOMER_ACCOUNT_CLIENT_ID`, so existing unconfigured gateways fail closed. The fixed shared Home/Categories/Search/Wishlist/Account graph remains visible because capability-driven optionality is not a Gate 2 concern.

### Tests and enforcement

Synthetic JVM tests own exact configuration/design values, deliberate validation issues, policies, fixture identities, update-policy behavior, and process-memory store read/write/clear plus instance-isolation behavior. They do not deterministically prove the source-level Mutex implementation's mutual-exclusion behavior. Four instrumentation classes own Hilt composition, launch/Home ownership, theme/resource fallback, database/partition/store isolation, fixed navigation, and typed deep-link routing. The post-review synthetic JVM result is 8/8 and the instrumentation result is 8/8.

`scripts/Test-RepositoryPortability.ps1` now models logical path, physical directory, and role. It enforces the exact eight-subproject topology/mapping, rejects implicit or explicit `:apps`, derives source/build/lock paths from physical directories, checks supported Gradle dependency syntaxes, rejects shared/provider-to-app and app-to-app edges, rejects synthetic/mobile-core Firebase edges, preserves all Gate 1 Gürbakır guards, and scans decoded XML alongside Kotlin/Java. Its isolated `-SelfTest` fixture suite covers both accepted and rejected topology, mapping, dependency, Firebase, and contamination cases.

`scripts/Test-Gate2SyntheticPackage.ps1` discovers artifacts from `output-metadata.json` rather than filenames. For both debug and release it inspects package/version/SDK/label/launcher, debuggability, merged permission/manifest/link/AppAuth state, default and English packaged strings, theme, DEX readability/entrypoints/Firebase namespaces, signing state, and release R8 output. Its six pure parser fixtures reject typed-true hardening values, typed-true App Link verification, extra `activity` or `activity-alias` URI handlers, and resource-referenced alias schemes before inspecting the real APKs.

`.github/workflows/android-foundation.yml` retains every existing lane and adds synthetic JVM, debug, release, AndroidTest, package-contract, portability self-test, and API 30 GMD targets.

## Why the important decisions were made

1. **A flat logical `:synthetic` project with `apps/synthetic` physical storage** follows the approved plan and proves that repository organization is not an accidental Gradle parent-project contract. It also keeps exact task/accessor names unambiguous.
2. **No new shared runtime API** was necessary. Existing Gate 1 seams expressed configuration, resources, policies, navigation, persistence construction, and provider selection. Adding a generic brand/provider framework would have been speculative architecture rather than conformance proof.
3. **No INTERNET permission plus `.invalid` endpoints and blank client inputs** makes accidental merchant/customer traffic structurally unavailable while still exercising the real typed configuration and unconfigured-gateway paths.
4. **Firebase absence is composition, dependency, package, and DEX evidence**, not a runtime boolean alone. The synthetic module does not apply Google Services, does not depend on `:firebase`, and its built artifacts are inspected for Firebase registrations/namespaces.
5. **A separate Room file and process-memory sessions** prove sandbox/partition/composition separation without inventing reusable Keystore/SharedPreferences identities. Durable protected-store behavior is intentionally left for the gate that owns it.
6. **Main-source read-only Hilt evidence entry points** allow both debug and minified release composition to be tested/inspected without adding an AndroidTest-only KSP/Hilt processor path solely for assertions. They expose configuration objects only; they do not widen the product architecture.
7. **An app-local legal surface and rejected deletion launches** prevent external navigation from synthetic content and avoid pretending that `.invalid` legal/customer processes are real.
8. **Package inspection uses output metadata** because Android Gradle Plugin output filenames are implementation details; metadata is the stable source for variant/application identity and output path.

## Implementation findings, defects, and corrections

The following are retained because they changed the implementation or its proof. Failed attempts are not counted as PASS.

| Finding or failure | Technical cause | Resolution and resulting evidence |
|---|---|---|
| Package validator initially did not parse | PowerShell regex quoting used an invalid escape form | Rewrote the expressions using PowerShell-safe quoting; the script then executed. |
| Package validator then corrupted a match variable | Helper data used `$matches`, colliding case-insensitively with PowerShell's automatic `$Matches` | Renamed the helper value and preserved strict-mode behavior; final debug/release checks pass. |
| Portability checks rejected valid source | Broad Firebase and `Gur\s*Bakir` patterns treated `firebaseEnabled=false` and the historical `com.gurbakir` namespace as violations | Narrowed detection to real dependencies/imports/registrations and human-readable brand text; negative fixtures still prove the intended failures. |
| Detekt reported three findings | One Hilt module and one evidence entry point exposed too many functions; a color expectation helper exceeded the method threshold | Split configuration/persistence Hilt modules and evidence entry points; split color helpers. Final detekt passes without suppressing these findings. |
| Lint reported eight `MissingTranslation` errors | `values-en` initially contained only the three planned ownership markers while inherited shared keys required complete overlay entries | Added all eleven synthetic English strings. Final lint passes; the three required markers still exist in both default and English configurations. |
| First physical instrumentation run reached only 5/8 | Test-only Hilt entry points were unavailable in the production Hilt graph used by the installed APK | Moved bounded read-only evidence entry points to main source instead of adding a new `kspAndroidTest` dependency. |
| Second physical run reached 7/8 | The test expected `home_title` as visible Home page title, but shared Home intentionally renders `BrandConfiguration.displayName`; `home_title` is the Home accessibility/navigation description | Corrected the test to assert the actual `Gate 2 Synthetic` display name and separately assert the `Synthetic Lab` content description/resource contract. Final direct instrumentation is 8/8. |
| OEM utility replaced the foreground during early device setup | INFINIX Phone Master installer scanning intercepted the APK-install flow | Returned to HOME and launched instrumentation directly; no project change or passing claim was made from the interrupted attempt. |
| First GMD command was parsed as an invalid task | PowerShell consumed the unquoted dotted `-Pandroid.testoptions...` property incorrectly | Quoted the property argument. The corrected canonical command reached the managed devices. |
| First full corrected GMD run had 1/98 core failure | Existing `ProductDetailScreenTest.approvedImageOpensEdgeToEdgeViewerAndSystemBackRestoresFocus` timed out at `pressBack` with `RootViewWithoutFocus`; no Gate 2 core source changed | Ran the exact test in isolation (1/1), then reran the full mandatory three-target lane. The fresh unfiltered rerun passed core 98/98, app 22/22, synthetic 8/8. |
| Successful GMD run printed a UTP/DDMLib class-loading stack trace | Test infrastructure logged `NoClassDefFoundError` for an internal Guava `AbstractFuture` class between tasks | Did not hide or reinterpret it. Gradle exited 0 and all three complete XML reports contain zero failures/errors/skips; recorded as an infrastructure diagnostic rather than an app failure. |
| Direct shell deep-link exercise was not executed | Host command policy blocked the manually composed `adb shell am start -d ...` command before device execution | Recorded as NOT RUN, not an application failure. The eight-test instrumentation suite independently resolves all three manifest links and their typed destinations. |
| First GitHub Actions package-contract step printed PASS but exited 1 | The release APK is intentionally unsigned, so its expected nonzero `apksigner verify` result remained in PowerShell's native exit state; Linux `pwsh` propagated that stale status when the script ended | Added an explicit `exit 0` after all 41 checks and the failure gate. Local child-process execution now returns 0, and the correction receives its own fresh PR run. |
| The security rank-input heuristic omitted production Kotlin under `com/example/...` | The generic source classifier treated the package path component `example` as non-production even though this is the production source set of the synthetic fixture | Manually added all eight production Kotlin files, plus the workflow and shrinker rules, to the deep-review worklist with recorded reasons; the final review covers every changed production/build/script/resource surface. |
| Focused security review found four plausible validator bypasses | Loose `aapt` regexes could match attribute resource IDs instead of typed boolean values and did not enumerate URI handlers; source-only Gradle parsing ignored computed `include(...)` and `project(...)` arguments | Replaced the manifest checks with typed-value and element-block validation, added negative parser fixtures, rejected computed/interpolated project arguments and unsupported include expressions, and compared the real Gradle-evaluated topology/mapping. Follow-up review found and closed `activity-alias` handlers and `":$name"` project paths. A final AAPT2 probe then proved that an alias scheme encoded through a resource reference was still invisible to the string extractor, so the validator now requires the raw scheme-attribute count and parsed exact-value count to agree. The corrected package self-tests pass 6/6, portability self-tests pass 19/19, repository checks pass 37/37, and the real debug/release contract remains 41/41. |
| A broad all-Markdown link sweep reported two broken Phase 2 source links | `docs/phase2/CHECKOUT-KIT-PROOF.md` and `docs/phase2/FIREBASE-PROOF.md` still point at pre-Gate-1 `app/src/main/...` locations | Verified both links and missing targets already existed at the Gate 2 base. Gate 2 did not rewrite historical Phase 2 evidence outside its scope. A separate check of all eight Gate 2-modified documentation files passes with no broken local link; the two historical links remain pre-existing documentation debt. |
| Final CodeRabbit review found that the planned store-concurrency JVM test was non-discriminating | `runTest` scheduled the `async` writes on one test thread, and each store operation only assigned one reference. The final accepted-value assertion still passed when the Mutex was temporarily removed, so it could not prove mutual exclusion. | Ran that counterfactual explicitly: all four store tests passed without the Mutex. Restored production source unchanged, removed only the invalid test and its unused helper/imports, and reduced the honest synthetic JVM result to 8/8: five configuration/policy tests and three store behavior tests. The Mutex remains a source-level implementation fact, not deterministic test proof. Adding an artificial compound operation or shared API solely to make a lock test fail was rejected as out of scope and YAGNI. |

No correction required a shared API, dependency version, Gürbakır source/configuration, production service, or gate-scope change.

## Planning assumptions: confirmed, refined, or disproved

| Planning assumption | Outcome | Evidence/meaning |
|---|---|---|
| Existing public seams can compose a second app | **Confirmed** | `:synthetic` compiles, packages, launches through Hilt, navigates `MobileCoreApp`, and required no shared runtime source change. |
| Exactly eight flat included subprojects plus root are sufficient | **Confirmed** | `gradlew projects` reports the eight approved modules; mapping is `:synthetic -> apps/synthetic`; no `:apps` project exists. |
| Firebase-free composition is possible | **Confirmed** | Dependency/topology checks, manifest/resources/archive/DEX checks, Hilt runtime evidence, and no-INTERNET packaging agree. |
| The existing fixed graph remains usable with empty capability data | **Confirmed, but bounded** | All five destinations launch and return Home; this proves current composition, not optionality or reordered navigation. |
| Three English marker strings would be sufficient | **Disproved by lint** | Strict dependency lint required eight additional English overlays; the package now contains eleven complete synthetic English strings. |
| AndroidTest-local Hilt inspection would work without processor changes | **Disproved** | Installed-production Hilt graph could not expose test-only entry points; main-source read-only entry points were the smallest dependency-free correction. |
| `home_title` is the visible Home heading | **Disproved** | Shared Home displays brand `displayName`; `home_title` supplies accessibility/navigation semantics. Tests now assert both contracts separately. |
| Positive manifest/source regexes were sufficient as exclusive conformance guards | **Disproved by security review** | Exact typed-value parsing, full URI-handler enumeration, fail-closed project-call syntax, and the evaluated Gradle model were required to prevent false-positive PASS results. |
| GMD would be deterministic on the first full run | **Refined** | One existing focus-sensitive core test failed once, then passed isolated and in the complete fresh rerun. Gate 2 records both rather than classifying the first run as pass. |
| Persistence can be fully proven in Gate 2 | **Refined to partial** | Separate Room/partitions and ephemeral session behavior are proven; durable protected-store identities, restore, and upgrade remain intentionally absent. |
| Store JVM tests can prove process-memory concurrency behavior | **Disproved during final review** | Removing the Mutex did not make the original test fail because no observable compound invariant existed. The test was removed, the 8/8 suite now claims only read/write/clear and instance isolation, and no production API was added for test convenience. |

## Alternatives investigated and rejected

- **Add a brand flavor or runtime brand switch:** rejected because application-module build selection is the accepted architecture and installed identity must not be remotely/mutably selected.
- **Create a nested logical `:apps:synthetic` project:** rejected because it would violate the approved exact topology and add an implicit parent project with no product role.
- **Add a shared capability/navigation framework now:** rejected because the fixed graph already composes; optionality/order belongs to Gate 4 and no Gate 2 compiler/runtime failure justified the abstraction.
- **Generalize Storefront domain/media policy in Gate 2:** rejected because the synthetic app can fail closed with no INTERNET permission; the reusable media-origin debt belongs to Gate 3.
- **Create reusable encrypted Customer/Cart stores for the fixture:** rejected because that would prematurely choose shared Keystore/SharedPreferences identities. Process-memory stores provide real state semantics without migration claims.
- **Add a compound or test-only store operation solely to prove Mutex exclusion:** rejected because no product/shared contract requires that operation; adding it would reopen Gate 2 architecture only to satisfy a test rather than prove existing behavior.
- **Add `kspAndroidTest` and an AndroidTest Hilt graph only for inspection:** rejected after the device failure because bounded main-source evidence entry points avoid a new processor configuration and are valid in debug/release composition.
- **Use a custom font to force asset variance:** rejected because no existing shared contract requires one and runtime custom-font proof is explicitly outside Gate 2; the synthetic brand uses complete typography tokens with `fontResourceName=null`.
- **Permit networking to prove `.invalid` failure:** rejected because network denial is stronger and avoids accidental external calls; fail-closed configuration/gateway behavior is covered locally.
- **Blacklist every `gurbakir` byte sequence in the whole APK:** rejected because shared library bytecode legitimately retains historical namespaces and dormant compatibility constants. Checks target concrete app identity, Firebase implementation namespaces, human-readable contamination, manifest/resources, and runtime-created state.
- **Assume fixed APK filenames:** rejected in favor of Android Gradle Plugin `output-metadata.json`, with traversal/uniqueness checks.

## Observed validation evidence

Only commands that actually completed successfully are marked PASS. The final close-out reruns the canonical local lanes after the documentation is complete; CI evidence is separated below.

| Area | Status | Actual evidence |
|---|---|---|
| Initial baseline | PASS | Shared/application JVM suite: 195 actionable tasks, 89 executed, 106 from cache; portability 25/25. |
| Gradle topology/task discovery | PASS | `gradlew projects` reports eight included subprojects and `:synthetic -> apps/synthetic`; synthetic `test`, `assemble`, `assembleDebugAndroidTest`, and `ciApi30DebugAndroidTest` tasks resolve under `:synthetic:*`. |
| Synthetic JVM after review cleanup | PASS | 8 tests, zero failures/errors/skips: five configuration/policy tests and three session-store behavior tests. No deterministic Mutex-exclusion claim is made. |
| Formatting/static/lint during implementation | PASS | `spotlessApply`, focused `spotlessCheck`, `:synthetic:detekt`, and `:synthetic:lint` passed after the structural/translation corrections above. |
| Package-validator self-tests | PASS | 6/6 isolated `aapt`-shaped fixtures, including typed-true hardening/autoVerify values, extra `activity`/`activity-alias` URI handlers, and a resource-referenced alias scheme reproduced with a disposable AAPT2-compiled APK. |
| Portability self-tests | PASS | 19/19 isolated valid/invalid fixtures, including computed include and computed/interpolated project-dependency rejection. |
| Repository portability | PASS | 37/37 source and Gradle-evaluated topology, mapping, dependency, Firebase, contamination, and preserved Gate 1 checks. |
| Synthetic packaging before final close-out | PASS | Debug, release, and AndroidTest packages built; package contract passed 41/41 across debug/release. Final hashes are recorded below after the close-out rebuild. |
| API 30 GMD | PASS | Canonical three-target rerun: core 98/98 (109.336s), Gürbakır Development app 22/22 (54.478s), synthetic 8/8 (9.201s); zero failures/errors/skips; Gradle exit 0, 315 tasks, 11m45s. |
| API 31 physical device | PASS (supplemental) | Direct synthetic instrumentation 8/8; cold launch and sandbox evidence described below. |
| Original canonical local close-out at `b22bd52` | PASS | Synthetic task discovery passed. `spotlessApply spotlessCheck detekt lint` passed (330 tasks; 53 executed, 1 from cache, 276 up-to-date); the nine-module JVM lane passed (227 tasks; 57 executed, 15 from cache, 155 up-to-date); the complete core/Gürbakır/synthetic package lane passed (754 tasks; 221 executed, 52 from cache, 481 up-to-date). After validator review corrections, package parser fixtures passed 6/6, portability fixtures passed 19/19, repository checks passed 37/37, and real package validation passed 41/41 with a separate `pwsh -File` process exit of 0. This row preserves the original closeout run; the removed ninth JVM test is not used as concurrency evidence. |
| Owner-requested review-cleanup targeted validation | PASS | The Mutex-free counterfactual passed all four old store tests, proving the concurrency assertion was non-discriminating; production source was then restored unchanged. An initial `:synthetic:spotlessApply` invocation failed at task discovery because this repository owns Spotless at the root, so it is not counted as validation. The authoritative root `spotlessApply` passed (6 tasks), then fresh `spotlessCheck :synthetic:detekt :synthetic:testDebugUnitTest --rerun-tasks` passed (121/121 tasks in 5m07s). JUnit XML reports 8 tests, zero failures/errors/skips. Strict UTF-8, workstation-path/account disclosure, diff-hygiene, stale-claim, and changed-handoff link checks passed; the handoff currently contains zero local link targets. The fresh full GitHub lane on the review-cleanup HEAD remains the final cross-module/GMD authority. |
| Focused security diff/secret review | PASS | The sealed branch-diff review covered all 20 changed production Kotlin, manifest/resource, application build, shrinker, workflow, settings, and validator files. Four plausible defense-in-depth validator bypasses were reproduced and corrected through three validation passes, including an AAPT2-compiled resource-reference probe. All four were suppressed after correction; zero reportable vulnerabilities remain in the reviewed scope. The canonical scan contract validates, and the pre-documentation local Gitleaks scan found no leak. Documentation remains covered by the mandatory final CI Gitleaks run. |
| Completion-document integrity | PASS (scoped) | All local Markdown links in the eight Gate 2-modified documentation files resolve. The broader repository sweep is not claimed as PASS because of the two pre-existing historical Phase 2 links recorded above. |

The final locally rebuilt synthetic artifacts are:

| Artifact | Application ID | Bytes | SHA-256 |
|---|---|---:|---|
| `apps/synthetic/build/outputs/apk/debug/synthetic-debug.apk` | `com.example.gate2synthetic.debug` | 18,895,291 | `B93A4986A91A5893A4950EC570D7FFC5A5252CCBDCE18CB6E1AE0E31A03E9115` |
| `apps/synthetic/build/outputs/apk/release/synthetic-release-unsigned.apk` | `com.example.gate2synthetic` | 3,376,006 | `099376C11F7FFC57EE87B4F0BD0E2FFC764B93AA084B4B311996DF82880C830A` |
| `apps/synthetic/build/outputs/apk/androidTest/debug/synthetic-debug-androidTest.apk` | `com.example.gate2synthetic.debug.test` | 1,139,655 | `39770155AD3AA3FCCB1685D6D901DA0E5149965F76FFDB3DBA27943AC43BCB2B` |

### API 30 managed-device evidence

The successful mandatory command was:

```powershell
.\gradlew.bat :mobile-core:ciApi30DebugAndroidTest :app:ciApi30DevelopmentDebugAndroidTest :synthetic:ciApi30DebugAndroidTest "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"
```

Retained XML at the successful source state reports:

| Target | Timestamp | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `:mobile-core`, API 30 | 2026-09-08 17:22:19 | 98 | 0 | 0 | 0 |
| Gürbakır `:app` Development, API 30 | 2026-09-08 17:29:26 | 22 | 0 | 0 | 0 |
| `:synthetic`, API 30 | 2026-09-08 17:30:46 | 8 | 0 | 0 | 0 |

This is the required GMD evidence. The API 31 device evidence below supplements but does not replace it.

### Physical Android 12/API 31 evidence

The dedicated device was identified before use:

```text
manufacturer/model  INFINIX Infinix X6817
Android             12
API                 31
build               SP1A.210812.016
serial              intentionally omitted
```

The meaningful device command set actually used, with the serial deliberately omitted because only the dedicated target was connected, was:

```powershell
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.id
adb install -r -t .\apps\synthetic\build\outputs\apk\debug\synthetic-debug.apk
adb install -r -t .\apps\synthetic\build\outputs\apk\androidTest\debug\synthetic-debug-androidTest.apk
adb shell pm clear com.example.gate2synthetic.debug
adb shell pm clear com.example.gate2synthetic.debug.test
adb shell am instrument -w com.example.gate2synthetic.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop com.example.gate2synthetic.debug
adb shell am start -W -n com.example.gate2synthetic.debug/com.example.gate2synthetic.MainActivity
```

Focused UI-tree dumps, scoped `adb shell input tap` events, `adb shell run-as com.example.gate2synthetic.debug ...` file listings, process/activity inspection, and a bounded `adb logcat -d ...` query were also used. UI coordinates and time-window selectors are device/session artifacts and are intentionally not made into a reusable test contract.

The latest debug and test APKs were installed with `adb install -r -t`; app and test data were cleared for the final clean run. Direct AndroidJUnitRunner execution returned `OK (8 tests)` in 6.686 seconds. A cold `am start -W` of `com.example.gate2synthetic.debug/com.example.gate2synthetic.MainActivity` returned `Status: ok`, `LaunchState: COLD`, and `TotalTime: 2287`; process/activity inspection found the expected package and resumed Activity.

The Android 12 device locale was Turkish. The UI hierarchy showed the synthetic-owned `Gate 2 Synthetic` and `Synthetic Lab` markers while the unchanged shared fixed navigation resolved its Turkish resources (`Kategoriler`, `Ara`, `Listem`, `Hesap`, and `Sepet`). Each of the five primary destinations was exercised and returned Home. Fail-closed content state appeared because the fixture has no network/client credentials; this is the intended synthetic behavior.

`run-as com.example.gate2synthetic.debug` found only `gate2-synthetic-local.db`, its `-shm` and `-wal` files; the app's `shared_prefs` directory was empty. It did not create `gurbakir-local.db` or Gürbakır protected preference files, and instrumentation also checked that Gürbakır Keystore aliases were absent. Focused logcat inspection reported no Android Runtime fatal entry for the final launch/test window.

The older connected-test XML from an intermediate failing run was not overwritten by the final direct runner invocation and is not used as final evidence; the final 8/8 result is the captured direct instrumentation output. The earlier 5/8 and 7/8 runs remain documented above.

## Dependency, lock, package, and security boundary

No version-catalog entry, dependency version, repository, verification policy, or external library was added. The synthetic module uses only already approved catalog dependencies and generated its own `apps/synthetic/gradle.lockfile`.

Gradle also rewrote `mobile-core/gradle.lockfile` while resolving the new release/lint/package task graph. Review found the same 348 coordinate keys before and after: 204 records only gained `releaseLintChecksClasspath`, and the `empty=` record only gained `lintChecks`. No coordinate or version was added or removed. Existing implementation source/configuration under `app`, `mobile-core`, `foundation`, `storefront`, `account`, `checkout`, or `firebase` did not change; `mobile-core/gradle.lockfile` is the only changed path under those module roots.

Package validation requires no INTERNET permission; restrictive backup/data-transfer/cleartext flags; exact synthetic application/activity, links and AppAuth scheme; no Firebase manifest/resources/archive/DEX namespace; correct debug/release IDs and debuggability; debug signing; unsigned release signing failure; and an R8 mapping file. It deliberately does not claim that all dormant Gürbakır strings are absent from shared bytecode.

The repository CI retains its pinned `gitleaks` action. No secret, token value, customer/OAuth session, signing material, service-account credential, private key, or production identifier was introduced. External origins are reserved `.invalid` names and public-client token/client ID values are empty.

The focused security review is bound to committed range `a6f3b3ffbfedd0e188b49c31936b9dce80ce0628..14f5c17c4c54eeb452e1b16acddfa79b1e8251ac` and snapshot digest `codex-security-snapshot/v1:sha256:4ca4e071dcd010a34356e4ab03f043d67aab285e09bd520e1a878afbdc7c1aa2`. Its sealed canonical contract validates with complete coverage for the declared 20-file production/build/validator scope and zero reportable findings. Test source, generated lockfiles, and documentation were explicitly outside deep source tracing; tests and lock state received their dedicated validation, and the final documentation commit remains subject to the PR's full secret scan and CI lane.

## Gürbakır compatibility evidence

Gate 2 did not edit Gürbakır or shared production source/configuration. Base-to-code-HEAD module diff contains only the generated `mobile-core/gradle.lockfile` configuration-membership update described above. Existing tests, builds, package inspection, GMD, and portability controls therefore test the preserved composition rather than relying only on absence of intent.

| Compatibility item | Gate 2 evidence |
|---|---|
| Development/staging application IDs and flavor model | Existing app package/build lanes retained; no `app/` source/build change. |
| OAuth callback and `gurbakir.com` App Links | Existing app instrumentation/package lanes retained; synthetic uses distinct inert identifiers. |
| `gurbakir-local.db`, Room schema 2, and migration 1→2 | Existing core GMD suite 98/98 includes Room migration/state proof; synthetic creates a separate database with no Gürbakır migration. |
| Customer/cart SharedPreferences and Keystore aliases | No existing store source/config change; physical synthetic sandbox/alias checks found no Gürbakır state. |
| Optional four-file Firebase configuration behavior | Existing app/Firebase lanes retained; synthetic does not apply or import Firebase/Google Services. |
| Fixed navigation and Gürbakır resources | Existing app GMD 22/22 and package/build lanes pass; synthetic overlays are app-local. |
| `:app -> :mobile-core` dependency/composition | Existing edge remains; portability rejects synthetic/app cross-dependencies and shared/provider back-edges. |
| Functional/release governance | P3-13 remote deletion remains externally unverified; P3-16 remains not started. |

The successful build/device evidence is compatibility evidence for the tested non-production variants. It is not live Storefront, Customer Account, Checkout Kit, Firebase, payment, deletion, production signing, Play, or release proof.

## Gate 2 conformance result

| Dimension | Final classification | What is and is not proven |
|---|---|---|
| Separate app edge/topology/identity/manifest/resources | **PROVEN IN GATE 2** | Independent logical/physical project, APK identity, manifest/resource ownership, sandbox, debug/release/test tasks. |
| Design-token values and wiring | **PROVEN IN GATE 2** | Complete literal contract and representative runtime MaterialTheme/composition-local consumption. |
| Runtime custom font | **DEFERRED BEYOND GATE 2** | Typography tokens work; no custom font resource/runtime claim. |
| Market/locale/Home/Catalog/address/tracking/deep links | **PARTIALLY PROVEN IN GATE 2** | Synthetic values, policies, resources, typed routes, and bounded flows work; this is not a full multi-market/localization/content architecture. |
| Domain/media and reusable persistence inputs | **DEFERRED TO GATE 3** | Network is structurally disabled; no reusable media-origin or durable persistence-identity generalization was introduced. |
| Search/Wishlist/Customer Account | **PARTIALLY PROVEN IN GATE 2** | Fixed-graph construction and isolated database/ephemeral session behavior work; capability optionality remains Gate 4. |
| Firebase absence | **PROVEN IN GATE 2** | Dependency, plugin, manifest, resources, archive, DEX, and runtime composition evidence. |
| Generic provider architecture | **DEFERRED TO GATE 5** | No speculative plugin/provider framework. |
| Persistence | **PARTIALLY PROVEN IN GATE 2** | Separate Room/partitions and memory stores; no durable protected-store, restore, or upgrade claim. |
| Unsigned non-production release and managed-device launch | **PROVEN IN GATE 2** | R8/resource-shrunk release package plus API 30 execution. |
| External registrations | **NOT APPLICABLE TO SYNTHETIC** | `.invalid`/inert identities are intentionally unregistered. |
| Production readiness | **DEFERRED BEYOND GATE 2** | P3-16 remains not started. |

## Deferred work and explicit stop

Gate 2 intentionally leaves these unimplemented:

- a real second merchant application or reusable brand scaffolding generator;
- capability-driven feature presence and configurable primary navigation;
- optional Customer Account/Wishlist/Search composition;
- reusable domain/media origins and Storefront media-policy cleanup;
- durable brand-specific protected-store identities, backup/restore, and upgrade contracts;
- custom-font runtime variation;
- generic provider/Firebase/analytics/notification architecture;
- Shopify Navigation, Home/metaobject, broader market/address/localization architecture;
- production application IDs, Customer Account/Firebase/App-Link registrations, signing, Play, publication, support, rollback, and P3-16;
- Gate 3 implementation.

No later gate should reinterpret the synthetic fixture as a real-brand template or production-ready app. Begin any next gate from then-current source, its own approved plan, and the unresolved items above.

## Git, PR, and CI close-out protocol

The approved plan proposed four commit roles: plan approval, implementation, boundary tests, and completion evidence. The actual PR history expanded transparently to nine commits without amend or squash. The planned roles correspond to entries 1-3 and 8; entries 4-7 are corrective CI/security-validation commits, and entry 9 is the owner-requested post-closeout review cleanup:

1. `239ffdd` — `docs(multi-brand): approve Gate 2 execution plan`
2. `55bebf0` — `feat(multi-brand): add synthetic application composition`
3. `4ccb0f7` — `test(multi-brand): enforce synthetic application boundaries`
4. `ef9f641` — `fix(multi-brand): make package validation CI portable`
5. `df7723a` — `fix(multi-brand): harden Gate 2 conformance validators`
6. `639b654` — `fix(multi-brand): close validator edge cases`
7. `14f5c17` — `fix(multi-brand): reject indirect URI handlers`
8. `b22bd52` — `docs(multi-brand): record Gate 2 evidence`
9. review-cleanup commit containing this revision — `fix(multi-brand): address Gate 2 review findings`

The four validation/security correction commits and the later review-cleanup commit are deliberate expansions of the originally proposed four-commit structure, not architectural or scope deviations. Amending or squashing earlier commits would have contradicted the approved no-amend/no-squash rule and hidden implementation/review findings. Every correction therefore remains independently reviewable.

PR #14 was opened as draft after the first three commits. GitHub Actions run #63 (private historical archive) is the first pre-documentation implementation-head run. Each pushed correction received a separate runner run, with concurrency cancellation retained honestly below. Commit 8 recorded the original local/security closeout evidence; after CodeRabbit reviewed that HEAD, the owner requested commit 9 to narrow the JVM claim and make the handoff durable. Commit 9 must receive fresh CI and CodeRabbit results on the same HEAD. Because an immutable commit cannot embed the future run/review identifiers generated for itself without creating another HEAD, those final statuses are verified on PR #14 and reported in the final execution response rather than self-referentially embedded here.

**Initial pre-documentation PR run #63:** **FAIL**. Gitleaks, formatting/static analysis, JVM tests, and all package builds passed. The package validator then reported all 41 assertions as PASS but the step exited 1 because Linux `pwsh` inherited the expected failed release-signature probe. Subsequent portability and instrumentation jobs were skipped. Commit `ef9f641` explicitly terminates the successful validator with exit 0; no APK or contract expectation was weakened.

**Intermediate correction PR run #64 (private historical archive):** superseded and automatically canceled by the concurrency policy when the security hardening commit was pushed; it is not used as PASS evidence.

**Intermediate correction PR run #65 (private historical archive):** superseded and automatically canceled by the concurrency policy when the follow-up edge-case correction was pushed; it is not used as PASS evidence.

**Intermediate correction PR run #66 (private historical archive):** superseded and automatically canceled by the concurrency policy when the resource-reference correction was pushed; it is not used as PASS evidence.

**Corrected pre-documentation PR run #67 (private historical archive):** **PASS** at `14f5c17c4c54eeb452e1b16acddfa79b1e8251ac`. The validate job completed successfully in 25m43s, including secret scanning, formatting/static/lint, JVM tests, all package builds, package self-tests/contract, and portability self-tests/repository checks. The dependent mandatory API 30 instrumentation job then completed successfully in 16m05s. This is the final code-only runner proof before the documentation commit.

**Original documentation-bearing PR run #68 (private historical archive):** **PASS** at `b22bd52085895723460061b92fae02437045c6cd`; both `validate` and dependent `instrumentation` completed successfully. This is durable Gate 2 execution-closeout evidence, but it is not final-HEAD evidence after the owner-requested review-cleanup commit.

**Review-cleanup final PR run/review:** GitHub Actions must be PASS and a fresh CodeRabbit review must leave no actionable finding on commit 9 before PR #14 is declared ready for owner merge. This work neither authorizes nor performs that merge.

## Rollback and recovery

Gate 2 was isolated to its branch/worktree and not merged at execution closeout. That is a historical observation, not a promise that PR #14 will remain unmerged forever. Before an owner merge, the safest rollback is to close PR #14 and leave `main` unchanged; no destructive local command is required. If the owner later merges it, rollback should use the repository's normal revert of the selected merge result rather than resetting shared history. Synthetic test-device packages/data may be removed independently because their application IDs and sandbox are separate from Gürbakır. No production service, customer, order, payment, signing, Play, Firebase, or domain state was mutated.

## Final handoff rule

At Gate 2 execution closeout on 2026-09-08, PR #14 was unmerged. After the owner-requested review-cleanup HEAD receives fresh passing GitHub Actions and a fresh CodeRabbit review with no actionable finding, the PR is ready for an owner-controlled merge. This readiness rule does not claim perpetual merge state and does not authorize this work to merge. Stop there. Gate 3, P3-16, production onboarding, release, merge execution, and branch deletion remain outside this authority.
