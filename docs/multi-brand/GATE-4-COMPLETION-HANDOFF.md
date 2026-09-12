# Gate 4 Capability / Navigation Completion Handoff

Status date: 2026-09-10

Status: **Source implementation and whole-branch review/security triage complete on pull request #18; one PR-review validator correction is applied, while fresh review/checks on the final documentation-bearing HEAD, owner merge and post-merge verification remain pending. Gate 4 is not closed.**

This is the pre-merge implementation/evidence record. It does not claim device, live-service or production acceptance. Read [current authority](../README.md), [owner boundaries](../OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md) and [canonical architecture](../architecture/MULTI-BRAND-ARCHITECTURE.md) with this record. The owner-approved conversation plan governed execution; no separate Gate 4 implementation-plan file was created. Historical Gate 1–3 handoffs remain unchanged.

## Implemented composition and ownership

| Input / behavior | Gürbakır `:app` | Non-production `:synthetic` at `apps/synthetic` |
|---|---|---|
| Search | `ENABLED` | `ENABLED` |
| Wishlist | `ENABLED` | `DISABLED` |
| Customer Account | `Enabled(configuration)` using the existing BuildConfig inputs | `Disabled`, with no dummy Account endpoints/client/full callback URI |
| Ordered primary specification | `[HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT]` | `[SEARCH, HOME, CATEGORIES]` |
| Graph start / state anchor | Home | Home, despite appearing second in primary presentation |
| Common deep-link bases | Existing Collection and Product bases | Inert synthetic Collection and Product bases |
| Account bindings | Exact existing Order base, `GurbakirTrackingUrlPolicy`, deletion-page launcher | `null`; no Order/tracking/deletion runtime bindings |
| Persistence | Exact existing identities and partitions | Existing separate Room and encrypted cart/customer identities retained |
| Firebase / INTERNET | Existing application-owned configuration | No Firebase composition/dependency or INTERNET permission |

`AppConfiguration.applicationComposition` is the sole application-owned capability/topology input. `:foundation` owns neutral value contracts: `ApplicationCapability`, `CapabilityState`, `CustomerAccountCapability`, `ApplicationCapabilities`, `PrimaryNavigationDestination`, `PrimaryNavigationSpec` and `ApplicationComposition`. The obsolete unused Brand feature property/enum was removed; the historical Phase 2 validator's corresponding executable assertion was adapted without rerunning or relabeling that historical validator as present-state proof.

The specification defensively copies its input and exposes an unmodifiable list. It requires exactly one Home and Categories plus exactly one of each enabled optional destination. Duplicates, missing mandatory entries, omitted enabled entries, extra disabled entries and capability/spec mismatches fail at construction; application composition revalidates even on copies. All eight capability combinations and reordered/non-first Home cases have JVM coverage. Account configuration belongs only to explicit `Enabled(configuration)`: invalid enabled configuration retains validation issues and fail-closed gateways rather than becoming disabled.

`:mobile-core` owns typed routes, exhaustive enum-to-route/label/icon mapping, shared feature adapters, graph construction, primary save/restore/single-top behavior and recovery. No remote Shopify/Firebase value, arbitrary route string or concrete-brand branch selects executable composition. No runtime merchant/market/language switch or normative brand flavor was introduced.

## Reachability, recovery and privacy

Home, Categories, Collection, Product, Cart, Legal/Support and route recovery remain common journeys. Search and Wishlist are optional; Customer Account owns Account, AccountDeletion, Profile, AddressList, AddressForm, OrderList and OrderDetail.

All nine optional typed nodes remain registered as guarded tombstones. A disabled direct/restored entry branches before feature content is created, including before disabled AddressForm/OrderDetail argument conversion. Recovery navigates to `RouteRecovery(UNAVAILABLE_DESTINATION)`, removes the current branch above Home without saving it, or clears destination entries to the graph if a direct-link stack contains no Home. This removes disabled restored ancestors in the current branch and preserves the existing Home/Legal recovery actions. Disabled primary/session-terminal helpers recover through the shared policy. Account-disabled composition does not register Order deep links or compose the private Order window policy.

Home/Collection/Search/Product obtain Wishlist membership only inside an enabled branch. Nullable state/actions remove Wishlist controls and semantics, including both Product layouts; Product navigation remains independent. Account's Search-history and Wishlist rows/copy vary independently, with truthful default/English copy for all four combinations. Wishlist Browse and item navigation remain available without Search. These UI/navigation behaviors are implemented and covered by compiled instrumentation sources; local runtime assertions were not executed.

Capability absence is not data deletion. Disabled Search/Wishlist browsing leaves dormant persisted records intact. Disabled Account restore/refresh/exchange return SignedOut, logout returns SignedOut, clear is inert, and authorization cannot prepare or consume a valid plan/callback. Provider-backed lazy session/client wiring avoids opening secure storage or initializing discovery/token/logout clients on disabled paths. Existing Cart safety still detaches signed-out ownership and retains `DETACH_PENDING` or `QUARANTINED` restrictions when applicable; disabling Account does not bypass those controls.

The deliberate privacy exception is Account-enabled deletion: all three local cleanup choices remain available even when Search/Wishlist browsing is disabled. Defaults remain Search-history cleanup **on**, Wishlist cleanup **off**, Cart discard **on**, with existing confirmation and result paths. This allows explicit cleanup of dormant data. Disabling Account alone neither erases its stored session nor proves remote logout/revocation. Merchant acknowledgement, retention/SLA execution and actual remote deletion remain externally unverified.

## Manifest, package and compatibility boundary

Synthetic advertises exactly Collection and Product HTTPS VIEW/BROWSABLE filters with `autoVerify=false`. The Order filter is removed; an explicit merger removal deletes `RedirectUriReceiverActivity`. The existing `appAuthRedirectScheme = "shop.0.gate2synthetic"` remains an inert merge input, not a full runtime callback or externally resolvable handler. No custom-scheme handler is allowed. Common and Account deep-link base constructors reject nonabsolute/non-HTTPS, user-info, query and fragment inputs using redacted errors.

AppAuth remains a dependency through shared Account. Package validation requires the compiled `AuthorizationManagementActivity` to remain non-exported and the redirect receiver to be absent from the merged manifest. Debug can retain unused AppAuth DEX classes; release R8 can remove them. This proves the bounded external surface, not physical AppAuth/Account-module exclusion or Gate 5 provider isolation. Retired synthetic Account endpoint strings, the full callback URI and retired Order base are rejected in DEX. Backup/transfer exclusion, four exact synthetic protected identities and absence of Gürbakır protected identities remain package requirements.

Gürbakır's manifest is unchanged, retaining Collection/Product/Order and callback contracts. App AndroidTest sources add installed callback-resolution expectations; synthetic AndroidTest sources expect only Collection/Product and reject Order/custom scheme. These installed-package assertions compile but remain runtime NOT RUN locally.

Comparison from `6a368ab7409581f4a2bdd056376ad380032a9654` through the current branch found no changes to module topology, Gradle files/locks/catalog/verification metadata, CI workflow, application IDs, Gürbakır manifest, Room filename/schema/history/migration, Search/Wishlist partition wiring, or encrypted store implementations/wire formats. Exact protected-store literals and every prior Gürbakır BuildConfig input match the baseline; the Order URL moved into app-owned Account bindings unchanged. No dependency or persistence migration was required. See the [identity register](../architecture/GURBAKIR-LEGACY-IDENTITIES.md). Ignored credentials/configuration contents were not printed or copied; this is tracked continuity evidence, not live-registration or installed-upgrade proof.

## Actual implementation history

Execution baseline: `6a368ab7409581f4a2bdd056376ad380032a9654` on the isolated Gate 4 branch.

| Commit | Actual scope |
|---|---|
| `91333ba3e907ddeda1823aefbf1bf96097913cbc` | `refactor(multibrand): define Gate 4 application composition` — contracts, application inputs, Account lazy disabled boundary and minimal common/Account deep-link binding seam |
| `86df0d274635105186ac3c01ba18d1dc0c8fc55a` | `feat(navigation): enforce capability-aware routes and actions` — guarded graph, recovery, optional UI/actions/copy and discriminating tests |
| `dc0f94af22076d57baac2786a16282a58e559bff` | `test(synthetic): prove reduced Gate 4 composition` — manifest, package/portability validators and installed-link test sources |
| `05e1af11a7ee86fa2351aa554208d318ccc0fdd8` | `fix(synthetic): scope manifest removal lint suppression` — one element-scoped `MissingClass` suppression on the receiver removal marker |
| `dc6e9c18d2dfb112667a4aa233ad5936cce2b41a` | `docs(multi-brand): record Gate 4 completion handoff` — canonical boundary, onboarding and pre-merge audit evidence |
| `228e4399b33c1149c7a8fb05f41a04b696605911` | `test(synthetic): isolate deep-link validator fixtures` — a discriminating Product-only fixture correction identified during whole-branch review |
| Final PR-review correction | `test(multibrand): reject concrete brand providers in shared code` — mutation-verified Gürbakır/synthetic provider fixtures plus the matching shared-source structural guard and audit wording |

The documentation commit containing this file uses `docs(multi-brand): record Gate 4 completion handoff`. Its own hash is intentionally not embedded recursively. Resolve the documentation-bearing HEAD from Git/PR evidence after commit. No implementation commit was amended.

## Local stable-candidate evidence

All Gradle commands used the repository's pinned Windows wrapper. The final static/JVM/build checks target the corrected code candidate above. Gradle UP-TO-DATE/cache reuse is reported as such; a fresh invocation is not a claim that every test body reran.

```powershell
.\gradlew.bat spotlessApply --console=plain
.\gradlew.bat spotlessCheck detekt lint --console=plain
.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest :checkout:testDebugUnitTest :storefront:testDebugUnitTest :firebase:testDebugUnitTest :mobile-core:testDebugUnitTest :synthetic:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:testStagingDebugUnitTest --console=plain
.\gradlew.bat :mobile-core:assembleDebug :mobile-core:assembleRelease :app:assembleDevelopmentDebug :app:assembleDevelopmentRelease :app:assembleStagingDebug :app:assembleStagingRelease :app:assembleDevelopmentDebugAndroidTest :app:assembleStagingDebugAndroidTest :synthetic:assembleDebug :synthetic:assembleRelease :synthetic:assembleDebugAndroidTest --console=plain
pwsh -NoProfile -File .\scripts\Test-RepositoryPortability.ps1 -SelfTest
pwsh -NoProfile -File .\scripts\Test-RepositoryPortability.ps1 -RequireCleanWorktree
pwsh -NoProfile -File .\scripts\Test-Gate2SyntheticPackage.ps1 -SelfTest
pwsh -NoProfile -File .\scripts\Test-Gate2SyntheticPackage.ps1 -Variant All
git diff --check
git diff 6a368ab..HEAD --no-ext-diff --no-color | gitleaks stdin --redact --no-banner --no-color
```

The initial `spotlessApply` passed without source changes. The initial full static lane failed on synthetic's merger-removal `MissingClass` diagnostic; after corrective commit `05e1af1`, the exact full `spotlessCheck detekt lint` command passed in 52s (327 actionable tasks: 9 executed, 318 up-to-date). After the review correction at `228e439`, a fresh exact-HEAD invocation again passed (327 actionable tasks: 8 executed, 319 up-to-date). The final full JVM invocation passed with 227 tasks up-to-date, reusing the preceding successful test results. Reported XML totals follow.

| JVM lane | Tests | Skipped | Failures / errors |
|---|---:|---:|---:|
| foundation | 17 | 0 | 0 / 0 |
| account | 61 | 0 | 0 / 0 |
| checkout | 7 | 0 | 0 / 0 |
| storefront | 60 | 2 | 0 / 0 |
| firebase | 3 | 0 | 0 / 0 |
| mobile-core | 154 | 0 | 0 / 0 |
| synthetic | 5 | 0 | 0 / 0 |
| app development | 35 | 0 | 0 / 0 |
| app staging | 35 | 0 | 0 / 0 |
| **Total** | **377** | **2** | **0 / 0** |

The two skipped cases are `OwnedStorefrontReadProofTest` and `OwnedStorefrontCartProofTest`; their explicit live-proof opt-in assumptions were not enabled. Thus 375 tests are non-skipped and neither live Storefront proof is claimed.

The complete build matrix passed in 9m31s (754 actionable tasks: 132 executed, 56 from cache, 566 up-to-date); its final exact-`228e439` rerun also passed (6 executed, 748 up-to-date). Both Gürbakır development/staging debug and release variants, both Gürbakır AndroidTest APKs, core debug/release and synthetic debug/release/AndroidTest builds succeeded. Test-APK construction is compilation/package evidence, not execution.

Repository portability self-tests passed **33/33** fixtures after two new concrete-provider counterfactuals first failed against the incomplete matcher; full clean-candidate mode passed **44/44** checks (43 structural checks plus clean worktree). Synthetic package self-tests passed **22/22** fixtures and final debug/release package validation passed **57/57** checks. The package checks establish the reduced merged-manifest/DEX/identity boundary described above. Review identified one Product-filter self-test isolation problem; commit `228e439` corrected it and the strengthened **22/22** self-test rerun passed.

Protected-file checks and exact comparisons of the app's protected identity functions and BuildConfig inputs passed; source/working/staged diff whitespace checks passed. Documentation scope/link validation passed for exactly nine documentation/authority files and 78 local links, with historical Gate 1–3 handoffs unchanged and no Gate 4 plan file. Separate staged-documentation and complete Gate 4 diff scans passed redacted Gitleaks with no leaks. The final pre-PR exact-`228e439` redacted Gitleaks branch-diff scan also reported no leaks across 285.40 KB.

## Corrections and evidence limits

Task 1 first exposed missing-contract compile failures. A temporary disabled-restore guard removal then caused actual failures in Account and Cart/DI tests that requested forbidden encrypted-store providers; restoring the guard passed. An initially incorrect expectation that the Cart planner was never invoked was replaced by a real-repository test asserting restricted state and zero gateway mutation, preserving existing Cart behavior.

Task 2 includes an actual two-test Wishlist adapter counterfactual with one expected failure before the absence guard was implemented. Navigation/UI tests first produced missing-contract compile failures, but their runtime RED/GREEN cycle could not execute. Formatting, scoped detekt thresholds and a lowercase Composable test-helper lint failure were corrected before integration; no device success is inferred from compilation.

Task 3's strengthened package check initially over-required unused AppAuth classes in the minified release. Inspection showed R8 retained only the manifest-referenced non-exported management activity; the validator was corrected to that required boundary without keep rules or dependency changes. The full Task 4 static lane then found `MissingClass` on the merger-only redirect-receiver removal node. Commit `05e1af1` adds `tools:ignore="MissingClass"` solely to that `tools:node="remove"` element. Scoped re-review found the issue addressed; the complete static lane subsequently passed.

The first whole-branch review found that the Product-removal negative fixture also removed Collection, allowing a rejection for the wrong missing-link condition. Commit `228e439` isolates the Product mutation, asserts that Collection remains present, and retains the mutation-effect assertion. A scoped rereview accepted the correction. A subsequent independent CodeRabbit whole-branch review of all 71 changed files at exact `228e439` completed with zero findings.

PR review then found that the new shared-source composition matcher omitted the concrete `GurbakirBrand` and `Gate2SyntheticBrand` provider symbols. Two isolated fixtures each asserted that its mutation entered the inspected record set and then failed against the old matcher. Adding only those symbols made all **33/33** portability self-tests pass; full structural validation remained **43/43** before the clean-worktree check. The same correction clarifies the protected-identity evidence phrase without changing its result.

Local runtime attempts and non-proofs:

- Task 2 `:mobile-core:connectedDebugAndroidTest` failed during installation after the API 31 Infinix target disconnected; cleanup also reported `DELETE_FAILED_INTERNAL_ERROR`. **NOT RUN: zero tests**, neither assertion failure nor PASS.
- Task 2 focused `:mobile-core:ciApi30DebugAndroidTest` failed at managed-device setup because the configured API 30 image was unavailable. **NOT RUN: zero tests**.
- Task 3 correction's focused `:synthetic:ciApi30DebugAndroidTest` also failed before emulator creation because its configured API 30 image was not installed. **NOT RUN: zero tests**.
- Task 4 did not troubleshoot or retry device lanes. Full local API 30 and separate API 23 managed-device execution are **NOT RUN**; fresh CI is the remaining device authority. Compiled instrumentation includes direct/restored disabled-node recovery, primary save/restore/order/semantics, Wishlist absence and remaining Product journeys, Account row/copy combinations, deletion cleanup and installed link resolution.
- Rendered UI, TalkBack, touch/focus/device lifecycle, physical upgrade/persistence and live Shopify/OAuth/Firebase/Checkout Kit behavior are **NOT RUN** for Gate 4. Real orders/payments, actual remote deletion, production registrations/signing/Play/publication and release readiness are not proven; unobserved external state remains **UNKNOWN**.

Two nonblocking test-hardening notes remain recorded after whole-branch triage; neither contradicts the implemented behavior or passing production contracts:

1. Primary-order assertions compare horizontal centers and do not prove vertical navigation-rail order.
2. Compact/expanded Product tests do not explicitly assert which layout branch rendered before checking Wishlist absence.
Existing Gradle deprecation/experimental managed-device warnings and the prior Compose-rule deprecation warning are not new failures. The focused security diff scan reconciled and fully read all 62 changed non-Markdown files (46 primary plus 16 supplemental), separately reviewed the nine changed Markdown files, validated its scan contract and returned zero findings. Its immutable snapshot digest is `codex-security-snapshot/v1:sha256:b6347ae2569fdc8558161347780bc8b128d10f68fd989c2a0465a75b6521b94c`. This was static review, not Android runtime evidence.

## Remaining closeout and exclusions

Pull request #18 (private historical archive) is the owner-controlled integration surface. Require fresh checks on its final documentation-bearing HEAD: `validate`, `instrumentation` (Account/Storefront/core/Gürbakır development/synthetic API 30) and `minimum-sdk-instrumentation` (synthetic API 23). Any corrective commit requires relevant revalidation and fresh exact-HEAD CI/review. Preserve the existing CI jobs and coverage; no CI optimization change was needed.

Owner merge and post-merge `main` verification are separate, pending lifecycle events. Record their eventual evidence outside this pre-merge record without recursively committing new handoff hashes or declaring closure early.

Gate 5 generic Firebase/provider isolation, real additional merchants, production onboarding/provisioning, generalized Shopify Navigation/Home/metaobject ownership, arbitrary remote native behavior, runtime switching, namespace cleanup and P3-16 release/security/publication remain outside Gate 4. Phase 3 functional acceptance through P3-15 and the externally unverified merchant deletion boundary are unchanged.

Rollback is a reviewed revert of the Gate 4 commits in reverse order. No data migration, dependency upgrade or external-service mutation occurred in this gate; reverting does not require changing persisted identities. Do not overwrite unrelated work or rewrite commit history.
