# Gate 8 provisioning and onboarding candidate handoff

Status: **Final pre-merge candidate evidence; configured nonproduction provider acceptance is externally blocked; Gate 8 is not technically closed.**

Evidence date: 2026-09-15.

Implementation base: `ea936053b6d221a2abdfccf2f207167797ca330e`.

Reviewed implementation-source commit: `82d32fcb6d149f697e8f31781c758235b6686d23`.

Approved durable plan digest: `ce66e5ad7c5d830b4fcfc895e32dbb41a98871eaedf4636b239359a87bf6175a`.

The final documentation-bearing pull-request commit is resolved externally after this handoff is committed. A Git commit cannot contain its own SHA. The pull request and final operator report must identify that exact candidate and show this handoff commit as its ancestor; this record does not predict or self-assert that SHA.

## Implemented boundary

Gate 8 implements the approved offline provisioning and onboarding foundation:

- strict versioned application enrollment, provider-binding, Home-schema, receipt, and UTF-8 projection contracts;
- explicit application/profile configuration with no root or cross-profile fallback;
- deterministic tracked non-secret projections and scoped ignored client configuration;
- explicit enrollment-aware Gradle task lanes while preserving the eight-project architecture and existing required-check names;
- application-owned Customer Account User-Agent and exact Gürbakır identity preservation;
- read-only Shopify, Customer Account, Firebase, and App Link inspection with fixed endpoints, disabled redirects, bounded responses, and fail-closed identity checks;
- redacted Plan/receipt behavior, drift detection, mobile-facing Storefront proof dispatch, and guarded Apply limited to compatible missing merchant-owned Home definitions plus one explicitly selected, receipt-attributed unselected DRAFT probe;
- no Menu mutation, provider deletion/reconcile, Firebase mutation, Customer Account registration mutation, website/domain mutation, production work, runtime brand switch, or second real application.

## Completed local verification

All results below were obtained from the Gate 8 worktree. The complete Android-source static-analysis, JVM, assembly, package, secret-scan, and managed-device matrix was run through `5e2bdebea6d233d183355407201ff49b437f093d`. Corrective commit `7a28a994ff85d7e13a0067384d4067a22502b8f5` changes only the protected workflow and its PowerShell regression/structural tests; its registry-driven JVM and clean-output assembly lanes, Synthetic package validation, onboarding, portability, public-readiness, Spotless, Detekt, and focused security review were rerun. Gradle-managed emulator targets were executed with the repository-declared API 30 AOSP ATD x86 and API 23 default x86_64 definitions. The connected physical Android device and its system image were not used or changed; no system-image change was necessary for these repository-owned lanes. Fresh protected CI on the final documentation-bearing candidate remains required.

| Verification | Result | Evidence |
|---|---|---|
| Full onboarding self-test suite | **PASS** | `Test-MultiBrandOnboarding.ps1 -Suite All`: 100/100 |
| Focused operator security suite | **PASS** | `Test-MultiBrandOnboarding.ps1 -Suite Security`: 46/46 |
| Offline registry/projection validation | **PASS** | Current registry/projections valid; missing ignored inputs classified `UNCONFIGURED` |
| Task-lane resolution | **PASS** | `unit`, `assemble`, `api30`, and `api23` all validate against registered tasks |
| Fresh-shell workflow lane execution | **PASS** | The real four workflow run blocks each invoked a harmless fake native Gradle executable with every resolved task; the prior `$LASTEXITCODE` guard and the Unix literal-`\n` fixture both fail discriminating assertions |
| Spotless | **PASS** | `spotlessCheck` completed successfully |
| Detekt | **PASS** | Root `detekt` completed successfully |
| Full Android Lint | **PASS** | Root `lint` completed successfully |
| Relevant JVM suites | **PASS** | Foundation, Account, Checkout, Storefront, Firebase, Mobile Core, Synthetic, and both Gürbakır profile unit-test tasks completed successfully |
| Planned debug/release/androidTest assembly | **PASS** | Mobile Core debug/release; all four Gürbakır nonproduction variants; both Gürbakır Android-test APKs; Synthetic debug/release/Android-test APK completed successfully |
| API 30 Account instrumentation | **PASS** | 7/7, zero failures |
| API 30 Storefront instrumentation | **PASS** | Fresh force-rerun on reviewed source: 7/7, zero failures |
| API 30 Mobile Core instrumentation | **PASS** | 126/126, zero failures; executed again in the exact combined current-source lane |
| API 30 Gürbakır development instrumentation | **PASS** | 31 tests reported, one expected Firebase-configured proof skip with configuration absent, zero failures |
| API 30 Synthetic instrumentation | **PASS** | 14 tests reported, two expected paired process-restart proof skips, zero failures; executed again in the exact combined current-source lane |
| API 23 Mobile Core instrumentation | **PASS** | Fresh reviewed-source force-rerun: 124/124, zero failures |
| API 23 Synthetic instrumentation | **PASS** | Fresh reviewed-source force-rerun: 14 tests reported, two expected paired process-restart proof skips, zero failures |
| Repository portability self-tests | **PASS** | 68/68 |
| Current repository portability validation | **PASS** | 46/46; final `-RequireCleanWorktree` run 47/47 |
| Public-readiness self-tests | **PASS** | 19/19, including legacy Android SDK package, missing lane guard, and native-exit-code-after-PowerShell-resolver counterexamples |
| Current public-readiness validation | **PASS** | 23/23 |
| Firebase zero-file boundary | **PASS** | All four files absent; local-default state accepted and configured Firebase evidence not claimed |
| Synthetic package self-tests | **PASS** | 49 hostile/positive fixtures |
| Synthetic debug/release package validation | **PASS** | 63 checks, including Firebase/INTERNET/credential/identity boundaries |
| Gitleaks current tree | **PASS** | Gitleaks 8.30.1 found no leaks in approximately 5.16 MB scanned through reviewed implementation source |
| Gitleaks branch history | **PASS** | Gitleaks 8.30.1 found no leaks in 51 inspected commits / approximately 6.68 MB scanned through reviewed implementation source |

The final reviewed-source combined Gradle matrix reran `spotlessCheck`, root Detekt and full Android Lint, every listed JVM suite, and every planned debug/release/Android-test assembly task. It completed successfully in 2 minutes 30 seconds with 950 actionable tasks: 18 executed and 932 up-to-date. Cache reuse is reported rather than concealed; the managed-device commands below used `--rerun-tasks` where a fresh instrumentation execution was required.

The first combined forced API 30 run completed Account 7/7 and Storefront 7/7, then the host could not start the Mobile Core emulator because available RAM was insufficient. After stopping only the stranded Gradle-managed emulator, Mobile Core passed 126/126 in isolation and Gürbakır development passed 31 reported tests with its one expected Firebase-configured skip. Synthetic's first forced attempt stopped before tests when UTP could not initialize AndroidDebugBridge; after restarting only the local ADB server, the unchanged task passed 14 reported tests with two expected paired process-restart skips. The first forced API 23 Synthetic attempt similarly stopped before tests because available RAM was insufficient; after stopping that stranded emulator and restarting ADB, the unchanged task passed 14 reported tests with the same two expected skips. API 23 Mobile Core passed 124/124 in isolation. These failed infrastructure attempts remain recorded as failures and are not promoted to PASS; the table records the later successful actual executions.

The corrective verification began by running the Gradle-owned `:mobile-core:clean`, `:app:clean`, and `:synthetic:clean` tasks and confirming that `apps/synthetic/build/outputs/apk` was absent. Offline registry validation passed and the assemble resolver emitted exactly 11 tasks: Mobile Core debug/release, all four Gürbakır nonproduction app variants, both Gürbakır Android-test APKs, and Synthetic debug/release/Android-test. Passing that exact array to Gradle completed successfully with 755 actionable tasks and created `synthetic-debug.apk`, `synthetic-release-unsigned.apk`, and `synthetic-debug-androidTest.apk`. The ensuing 49-fixture self-test and 63-check `-Variant All` validation passed from those newly produced outputs, so no pre-existing local APK supplied the evidence.

The final offline tooling sequence then passed all 100 onboarding self-tests, all 46 focused security tests, all four registry-driven task-lane validations and executions, 68 portability fixtures, 19 public-readiness fixtures, 23 current public-readiness checks, the Firebase zero-file boundary, 49 Synthetic package fixtures, and all 63 Synthetic debug/release package checks. The registry-driven nine-task JVM lane passed, and root Spotless/Detekt passed. The commands were serialized where their temporary fixtures or Gradle state could interact; `-RequireCleanWorktree` remains a final post-handoff candidate check rather than overlapping a Gradle model query.

## Whole-candidate and security review

The candidate was reviewed against the durable Gate 8 plan and the preserved Gate 0–7 architecture. The main final review corrections are contained in `7f4bda23ddd692eac460369d6634c06fff676726` and cover:

- same-verified-shop DRAFT-probe attribution across the two explicitly shared Gürbakır profiles;
- nonproduction boundary revalidation during Apply;
- nonzero fail-closed Customer/Firebase identity classifications;
- real Storefront Menu/Home proof execution for Readback with privileged environment removal;
- current Shopify `PUBLIC_READ_WRITE` Home-definition compatibility;
- enrollment-driven CI execution plus independent role/dependency/task minimums;
- stricter registry unions and fixture-only future-app isolation;
- safe exact-destination local migration writes;
- bounded strict-UTF-8 fixture transport and receipt/operator digest hardening;
- exact Shopify Customer Account endpoint-host validation before local configuration or discovery acceptance.

The first immutable formal security scan of documentation candidate `20bd66f778e884cffb946b4e766645def1495bdc` found one medium-severity evidence-integrity defect: `RecordManualCheckpoint` could hash and record a semantically mismatched callback and unconstrained evidence reference. Focused RED tests reproduced both cases. Commit `91b45a6a3499b0301575ba09c3dfa41a33b52dd0` invokes the independent binding/profile client validator, enforces the approved evidence-reference grammar, verifies the sanitized digest-only record, and corrects the Windows private-file ACL SID form exercised by that path.

A whole-branch CodeRabbit review after the CI correction reported nine items. Four valid findings were fixed in `bd03abf`: registry-driven Synthetic directory resolution, registry-selected Storefront proof paths instead of Gürbakır fallbacks, creation of the scoped README destination before copy, and byte-for-byte preservation of pre-existing ignored local/binding files during self-tests. Five suggestions were rejected against the approved cutover and closed architecture: restoring the retired root `-ConfigurationPath` flow, weakening strict terminal-slash projection validation, changing the verified Gate 7 Shopify Admin readback value, treating the current evidence date as future, and weakening the exact current Gürbakır Firebase four-file contract. The focused Storefront resolver test proves a fixture-like future application/profile can resolve without Gürbakır path inheritance.

A second whole-branch CodeRabbit review reported three items. Two valid fixture/clarity defects were fixed in `4c45f67`: the enrollment negative fixture now replaces only the intended first ordinal token, and the App Links helper no longer shadows PowerShell's read-only `$Host` variable. The remaining suggestion to move the same privileged-environment filtering to a lazy provider was rejected: PowerShell 7 is an explicit project prerequisite and the actual `Exec`/readback child-process boundaries already receive filtered environments. The updated onboarding/security suites pass 90/90 and 43/43.

A final whole-branch CodeRabbit review reported eight items. Four valid contract gaps were reproduced with focused failing tests and fixed in `802ca40`: the receipt schema now matches the executable whole-second UTC rule, Plan creation derives both timestamps from one clock sample, all four registry-driven CI lanes explicitly fail on resolver error or an empty task list, and certificate fingerprints accept either hexadecimal case while preserving the exact 32-byte shape. The other four suggestions were rejected against current source and the approved plan: the three `com.gurbakir.storefront` owner proof classes intentionally retain their historical Gürbakır assertions while the new resolver has independent future-app coverage, and production token requests already construct a validating `CustomerAccountTokenRequestPlanner` before the internal request factory can consume the User-Agent. The affected suites pass 93/93 onboarding, 45/45 security, 18/18 public-readiness fixtures, and 23/23 current public-readiness checks.

A subsequent final-delta CodeRabbit review identified three valid strict-I/O gaps: provider response headers were deadline-bound but production body reads were synchronous and unbounded in time; the Storefront proof resolver used permissive Java `Properties` parsing instead of the approved closed UTF-8 grammar; and repository-relative path validation did not resolve existing reparse or symbolic-link components before containment checks. Focused negative tests reproduced all three issues. Commit `5e2bdebea6d233d183355407201ff49b437f093d` adds a shared cancellation deadline to bounded asynchronous body reads, applies the strict projection/local-property grammar in the proof resolver, and walks/resolves existing path components fail-closed. CodeRabbit then found one additional memory-boundary issue in that resolver; it now checks file length before reading and retains the post-read bound as defense in depth. The focused Storefront test, root Spotless/Detekt, Registry 30/30, and OperatorReadOnly 18/18 checks pass after the correction.

Formal parent-only Codex Security diff scan `426fa3ff-97ec-4c6f-a93e-94a489c6d8df` reviewed the exact range from the implementation base through `4c45f67acd445fa0b10a73ff3cdc349265b5b56f`, recorded all 36 security review items and the remaining changed test/configuration/evidence paths, and completed with zero reportable findings. It covered credential handling, provider targeting, SSRF/redirect boundaries, strict parsing, receipt/action integrity, path and reparse controls, merchant-content preservation, partial apply/recovery, CI credential inheritance, and Android identity isolation. Focused security review of the later delta through `5e2bdebea6d233d183355407201ff49b437f093d` found only the contract-hardening changes described above; their hostile-input, workflow-guard, strict-parser, cancellation, and reparse-containment regression tests pass, and no new credential, target, provider mutation, or Android runtime surface was added. The scan and delta review explicitly excluded live provider execution; no live acceptance result is inferred from source review. Delegation was unavailable under the active session policy, so this scan was sequential in the parent rather than an independent worker review.

Focused Codex Security diff scan `9bcd3ff7-8b27-447c-b977-4790bb55ab95` reviewed the three-file clean-runner correction against required-check bypass, task/option injection, stale-artifact acceptance, secret inheritance, and regression-test execution. It completed with zero reportable findings and complete recorded coverage. CodeRabbit independently reviewed the same three-file working-tree delta and raised zero issues. Neither review contacted providers or changed the configured-provider evidence boundary.

Focused Codex Security diff scan `0f5feb06-cf75-41d3-8561-32d27c969788` reviewed the one-file Unix fixture correction through `82d32fcb6d149f697e8f31781c758235b6686d23` against command injection, task omission, platform-dependent parsing, and false-positive lane-execution evidence. It completed with zero reportable findings and complete recorded coverage. CodeRabbit independently reviewed the same one-file delta and raised zero issues. The correction changes test-fixture serialization only; it adds no credential, provider, Android runtime, or external-mutation behavior.

Pull-request run `34892805324` on `ae5c8c4f255d480380b25abbff8b4307453bf812` then exposed two CI-contract failures after the local matrix. The onboarding command printed PASS 85/85 but returned the final expected negative fixture's native exit code to the multi-command Linux PowerShell step. Both Android jobs stopped before Gradle because the pinned `android-actions/setup-android` action's implicit package list still included the removed legacy SDK package `tools`. Commit `75d26c1a513cc724b51d597599d72a6233575d5b` explicitly resets the successful suite exit state, configures all three pinned setup actions with `packages: platform-tools`, and adds a public-readiness counterexample that rejects the legacy implicit package. The exact workflow command block now completes through all four registered lane validations with exit code zero. The failed run remains evidence and is not relabeled; fresh exact pull-request-head required checks are still required.

Pull-request run `34921498882` (run #12) on `ee5216a3242e4b72aaa935548faaba6ac1972c03` then failed `validate` in `Synthetic package contract`: the 49 self-test fixtures passed, but `apps/synthetic/build/outputs/apk` did not exist. The preceding registry-driven assemble step was marked successful even though its log contained no Gradle output. The same status-channel defect affected all four registry-driven Gradle run blocks, so that run's API 30 and API 23 jobs are retained as GitHub `success` conclusions but are not treated as actual instrumentation execution evidence.

The root cause was the guard immediately after the PowerShell resolver: a fresh `pwsh` has `$LASTEXITCODE = $null`, and `$LASTEXITCODE -ne 0` therefore evaluated true. `exit $null` ended the step with code zero before the nonempty guard or Gradle invocation. Local pre-existing artifacts had masked that skipped prerequisite. Commit `7a28a994ff85d7e13a0067384d4067a22502b8f5` checks PowerShell invocation status with `$?` after the resolver while retaining `$LASTEXITCODE` after native Gradle. Its behavioral regression executes each actual workflow block in a fresh child PowerShell and proves every resolved task reaches a harmless fake native Gradle process; restoring the former guard reproduces the failure. Public readiness independently rejects that former pattern. The clean-output reproduction and package evidence are recorded above. Fresh exact-head protected checks are required and run #12 is not relabeled as passing Gate 8 verification.

Pull-request run `34933345344` (run #13) on `05d738525999ece28e233c3beb266bcfa3e5e15f` then failed `validate` in `Gate 8 onboarding contract and enrollment` before JDK or Gradle setup. The production workflow correction remained valid: API 23 reached and passed the actual registry-driven managed-device Gradle lane. The failing assertion was confined to the cross-platform regression harness. Its generated Unix fake Gradle script used Bash `printf '%s\\n'`; Bash therefore wrote literal `\n` text between arguments instead of one task per line, so the harness could not recognize the tasks that the workflow passed correctly. The defect was first reproduced locally by an exact fixture-script assertion, then commit `82d32fcb6d149f697e8f31781c758235b6686d23` changed the fixture to `printf '%s\n'`. The focused Enrollment suite passes 12/12 and the complete onboarding suite passes 100/100. Run #13 remains failed historical evidence and is not promoted to candidate success.

Focused review of authentication, privileged credentials, target binding, SSRF, redirects, receipt tampering, path/reparse escape, shell construction, response bounds, pagination, drift, merchant-content preservation, partial apply, redaction, and CI credential inheritance found no remaining release-blocking source issue. Final documentation-only candidate checks and protected pull-request review/CI remain required after this handoff commit.

The approved plan contains four intentional Markdown hard-break lines. `git diff --check origin/main...HEAD` reports their two trailing spaces; they are part of the owner-approved, SHA-bound artifact and are not silently rewritten. No other whitespace error was found.

## Configured provider acceptance

| Acceptance surface | Classification | Closure effect |
|---|---|---|
| Scoped development/staging client configuration | **NOT RUN — EXTERNALLY BLOCKED** | Both ignored profile files are absent |
| Independently approved provider bindings | **NOT RUN — EXTERNALLY BLOCKED** | Both ignored binding files are absent |
| Shopify Admin Inspect/Plan | **NOT RUN — EXTERNALLY BLOCKED** | No approved binding/Admin credential loaded |
| Guarded Home-definition Apply | **NOT RUN — EXTERNALLY BLOCKED** | No provider target authorization; no external mutation performed |
| DRAFT acceptance probe and repeat no-op | **NOT RUN — EXTERNALLY BLOCKED** | No provider target authorization; no probe created |
| Live Menu/Home Storefront readback | **NOT RUN — EXTERNALLY BLOCKED** | No scoped Storefront client configuration loaded |
| Customer Account discovery/registration consistency | **NOT RUN — EXTERNALLY BLOCKED** | No scoped client input or sanitized current registration checkpoint; planning-time HTTP 403 is not promoted to current evidence |
| Firebase Management readback | **NOT RUN — EXTERNALLY BLOCKED** | No binding/access token/config files; no live project/app identity claimed |
| Configured Firebase file/package proof | **NOT RUN — EXTERNALLY BLOCKED** | Four `google-services.json` files are absent |
| Public Digital Asset Links association | **NOT RUN** | Informational and nonblocking while current manifests retain `autoVerify=false` |
| Configured development/staging Gate 6/7 device journeys | **NOT RUN — EXTERNALLY BLOCKED** | Required provider-bound inputs are absent |
| Local configuration rollback rehearsal | **NOT RUN** | No credential-bearing local write was authorized or performed |

No Shopify, Firebase, Customer Account, domain, production, customer, order, payment, or second-brand state was read with privileged credentials or mutated. No local credential/configuration file was created.

The missing live evidence prevents the stronger configured-provider claim and, under the approved closure criteria, prevents Gate 8 from being called technically closed. It does not invalidate the completed credential-free implementation or prevent opening a reviewable protected pull request with this limitation stated explicitly.

## Remaining lifecycle boundary

Gate 8 is **not technically closed** by this candidate or by opening its pull request. Closure still requires:

1. the closure-critical configured development/staging acceptance above using independently approved bindings and credentials;
2. exact final pull-request-head `validate`, API 30 `instrumentation`, and API 23 `minimum-sdk-instrumentation` success;
3. protected merge authorization and merge through the active ruleset;
4. exact merged-main ancestry/tree verification and canonical post-merge required-check success; and
5. a separate narrow lifecycle-status reconciliation pull request.

Second-store validation, Gate 9, P3-16, and production readiness remain unstarted and unclaimed.
