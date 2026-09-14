# Gate 8 provisioning and onboarding candidate handoff

Status: **Final pre-merge candidate evidence; configured nonproduction provider acceptance is externally blocked; Gate 8 is not technically closed.**

Evidence date: 2026-09-14.

Implementation base: `ea936053b6d221a2abdfccf2f207167797ca330e`.

Reviewed implementation-source commit: `91b45a6a3499b0301575ba09c3dfa41a33b52dd0`.

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

All results below were obtained from the Gate 8 worktree. Android managed-device results were obtained on ancestor `7f4bda23ddd692eac460369d6634c06fff676726`; the only later implementation change is the PowerShell-only manual-checkpoint validation/Windows private-file fix at the reviewed implementation-source commit above. Static analysis, JVM, assembly, tooling, portability, package, and secret checks were rerun after that correction and the final handoff commit.

| Verification | Result | Evidence |
|---|---|---|
| Full onboarding self-test suite | **PASS** | `Test-MultiBrandOnboarding.ps1 -Suite All`: 85/85 |
| Focused operator security suite | **PASS** | `Test-MultiBrandOnboarding.ps1 -Suite Security`: 42/42 |
| Offline registry/projection validation | **PASS** | Current registry/projections valid; missing ignored inputs classified `UNCONFIGURED` |
| Task-lane resolution | **PASS** | `unit`, `assemble`, `api30`, and `api23` all validate against registered tasks |
| Spotless | **PASS** | `spotlessCheck` completed successfully |
| Detekt | **PASS** | Root `detekt` completed successfully |
| Full Android Lint | **PASS** | Root `lint` completed successfully |
| Relevant JVM suites | **PASS** | Foundation, Account, Checkout, Storefront, Firebase, Mobile Core, Synthetic, and both Gürbakır profile unit-test tasks completed successfully |
| Planned debug/release/androidTest assembly | **PASS** | Mobile Core debug/release; all four Gürbakır nonproduction variants; both Gürbakır Android-test APKs; Synthetic debug/release/Android-test APK completed successfully |
| API 30 Account instrumentation | **PASS** | 7/7, zero failures |
| API 30 Storefront instrumentation | **PASS** | 7/7, zero failures |
| API 30 Mobile Core instrumentation | **PASS** | 126/126, zero failures |
| API 30 Gürbakır development instrumentation | **PASS** | 31 tests reported, one expected Firebase-configured proof skip with configuration absent, zero failures |
| API 30 Synthetic instrumentation | **PASS** | 14 tests reported, two expected paired process-restart proof skips, zero failures |
| API 23 Mobile Core instrumentation | **PASS** | 124/124, zero failures |
| API 23 Synthetic instrumentation | **PASS** | 14 tests reported, two expected paired process-restart proof skips, zero failures |
| Repository portability self-tests | **PASS** | 68/68 |
| Current repository portability validation | **PASS** | 46/46; final `-RequireCleanWorktree` run 47/47 |
| Public-readiness self-tests | **PASS** | 16/16 |
| Current public-readiness validation | **PASS** | 21/21 |
| Firebase zero-file boundary | **PASS** | All four files absent; local-default state accepted and configured Firebase evidence not claimed |
| Synthetic package self-tests | **PASS** | 49 hostile/positive fixtures |
| Synthetic debug/release package validation | **PASS** | 63 checks, including Firebase/INTERNET/credential/identity boundaries |
| Gitleaks current tree | **PASS** | No leaks in approximately 5.12 MB scanned |
| Gitleaks branch history | **PASS** | No leaks in 44 commits / approximately 6.64 MB scanned on the documentation-bearing candidate |

The first combined API 30 invocation exhausted local emulator resources after the Account target had completed 7/7. The transient Gradle-managed emulator was stopped, and the remaining targets were rerun as isolated module invocations. Every required target then completed with zero test failures. This was an execution-environment resource failure, not a promoted PASS; the table records only the successful actual target executions.

The package validator initially found no Synthetic release artifact because the preceding API 23 clean had removed it. The planned release APK was rebuilt and the unchanged validator then passed all 63 checks. No source correction was needed.

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

The first immutable formal security scan of documentation candidate `20bd66f778e884cffb946b4e766645def1495bdc` found one medium-severity evidence-integrity defect: `RecordManualCheckpoint` could hash and record a semantically mismatched callback and unconstrained evidence reference. Focused RED tests reproduced both cases. Commit `91b45a6a3499b0301575ba09c3dfa41a33b52dd0` now invokes the existing independent binding/profile client validator, enforces the approved evidence-reference grammar, verifies the sanitized digest-only record, and corrects the Windows private-file ACL SID form exercised by that path. The updated security suite passes 42/42. A fresh exact-final-candidate diff scan is required after this handoff commit; the superseded finding is not concealed or promoted as final-candidate evidence.

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
