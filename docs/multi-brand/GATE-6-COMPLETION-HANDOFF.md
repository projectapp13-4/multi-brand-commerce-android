# Multi-Brand Gate 6 — Shopify Navigation Discovery Completion Handoff

**Status:** SOURCE IMPLEMENTED / CUTOVER ACCEPTANCE COMPLETE / NOT READY TO MERGE
**Evidence date:** 2026-09-12
**Immutable execution base:** `9969250e369acc52718f8733672a2cccc5da4be5`
**Feature implementation checkpoint:** `92532e605689d06bc567131e0c1b30ea3c6ceeeb`
**Final code checkpoint:** `5ed82f6514f4a141f72115271aa103a18a5a6db5`
**Branch:** `codex/multibrand-gate-6-shopify-navigation-discovery`

This is the historical pre-merge implementation and evidence record for Multi-Brand Gate 6. It does not claim an exact PR-HEAD CI result, owner merge, merged-main verification, post-merge CI, production readiness, or technical closure.

## 1. Outcome

Categories discovery now comes from a bounded, application-selected Shopify Menu instead of compiled Android merchant lists. The implementation:

- keeps the application module responsible for the Menu selector;
- maps the fixed Storefront operation into provider-neutral models in `:storefront`;
- projects only eligible Collections in `:mobile-core` using depth-first pre-order;
- renders validated MenuItem titles and routes only validated Collection handles through the existing typed route;
- observes tags and excludes filtered Collection actions rather than widening them;
- enforces three supported levels, 64 returned nodes, and 24 accepted Categories;
- distinguishes repeated item identity, repeated destination, and conflicting Collection identity;
- removes Catalog's dependency on `StorefrontHomeGateway` while leaving Home unchanged;
- keeps synthetic unconfigured, without a token or effective `INTERNET` permission.

No persistent cache, LKG, packaged Categories fallback, new native route, Shopify Admin behavior, dependency upgrade, schema refresh, Firebase/provider change, persistence migration, or production/release work was introduced.

## 2. Commit sequence

| Commit | Purpose |
|---|---|
| `ba7270b` | Add bounded Catalog Menu GraphQL operation, neutral models, gateway mapping, and Storefront tests |
| `fd4ed6c` | Transfer Categories configuration, projection, UI, resources, DI, and synthetic composition to Menu discovery |
| `408040b` | Add structural ownership enforcement and dedicated opt-in owned Menu proof |
| `ec5e8f6` | Clarify projection decisions and satisfy core static-analysis constraints |
| `39c7043` | Isolate the finite Apollo discovery mapper and satisfy Storefront static-analysis constraints |
| `92532e6` | Close three review-identified ownership-validator false negatives with mutation-tested enforcement |
| `fef32be` | Make the GraphQL ownership mutations newline-agnostic after exact-PR-HEAD Linux CI exposed a fixture-construction mismatch |
| `5ed82f6` | Bind the selector ownership guard to `GurbakirCatalogConfiguration.value` and reject a compliant decoy beside a hardcoded value |

The documentation-bearing commit is intentionally not embedded in this file because a file cannot contain the hash of the commit that contains its own final bytes.

## 3. Changed contracts and principal files

- `storefront/src/main/graphql/com/gurbakir/storefront/CatalogDiscoveryMenu.graphql` adds the fixed operation, tags, three content levels, and terminal child-ID sentinel.
- `storefront/src/main/kotlin/com/gurbakir/storefront/CatalogDiscoveryModels.kt` adds neutral discovery request/tree/target/Collection contracts and public bounds.
- `StorefrontCatalogGateway` adds `loadCatalogDiscovery`; Apollo and unconfigured implementations are exhaustive.
- `ApolloCatalogDiscoveryMapper.kt` validates root identity, observes depth/count, classifies provider resources, and applies the existing media policy.
- `CatalogConfiguration` is now `CatalogConfiguration(menuHandle)`; `CatalogCategorySource` is removed.
- `DefaultCatalogRepository` consumes only `StorefrontCatalogGateway` for Categories and owns bounded projection and failure semantics.
- `CatalogCategoryItem` now carries Menu item identity/title plus `CatalogDiscoveryCollection`.
- `CategoriesScreen` displays the Menu title and opens the validated Collection handle while retaining grid/media behavior.
- `app/build.gradle.kts` exposes trimmed `BuildConfig.CATALOG_MENU_HANDLE`; tracked defaults/examples remain blank.
- Gürbakır composition consumes the BuildConfig selector; synthetic uses fixed inert `synthetic-catalog-menu`.
- Obsolete app `category_label_*` resources are removed; truthful Categories empty copy is aligned in app/core Turkish and English overlays.
- `scripts/Test-RepositoryPortability.ps1` adds field-aware GraphQL and ownership enforcement with adversarial fixtures.

The implementation changed 29 files through the last code checkpoint: 1,845 insertions and 215 deletions. Home configuration/source, primary navigation, typed-route declarations, deep-link configuration, Firebase composition, persistence, permissions, and CI workflow were unchanged.

## 4. Test-driven evidence

### Storefront RED/GREEN

- A compile-safe temporary discovery stub was introduced after every gateway implementation/fake was adapted.
- The focused discovery class produced behavioral RED: 10 of 11 tests failed against the stub; only the operation-document shape assertion passed.
- Implementing the actual mapper made the focused suite and Storefront/core regressions green.

### Categories ownership RED/GREEN

- A counterfactual repository test asserted Menu order and zero Home calls while the old static source still controlled output; it failed behaviorally.
- After final models compiled, a temporary fail-closed repository produced 11 of 12 expected behavioral failures.
- Final projection tests passed for pre-order traversal, descendants of ineligible parents, filtered actions, repeated IDs, repeated destinations, identity conflicts, invalid titles/resources, 64/65 nodes, 24/25 outputs, fake fourth depth, missing Menu, provider failures, refresh retention, and cancellation/latest-request behavior.

### Enforcement RED/GREEN

- Fourteen initial negative ownership/query fixtures failed before the validator checks existed while the valid fixture remained accepted.
- PR review later identified three enforcement gaps. Five focused mutations reproduced the false negatives: a decoy BuildConfig reference beside a hardcoded selector, a GraphQL comment brace hiding a later `MenuItem.url`, and appended/reordered/remapped Home sources.
- The corrected validator passes all 59 self-test fixtures. It requires `GurbakirCatalogConfiguration.value` to bind its Catalog constructor argument directly to `BuildConfig.CATALOG_MENU_HANDLE`, ignores GraphQL comment text during brace matching, compares the exact ordered Home handle/label pairs, and retains the earlier URL, tags/sentinel, static-ownership, and synthetic-isolation checks. After Linux CI showed that four GraphQL mutation builders depended on a source newline representation, `fef32be` made those finite mutations newline-agnostic. A final CodeRabbit counterexample then proved that a compliant decoy constructor could hide a hardcoded `value`; `5ed82f6` binds the check to the consumed property. The 59 fixtures and live validation passed locally afterward.

The API 30 Catalog instrumentation source was compiled after correcting a test import. Runtime execution was not reached because managed-device setup is unavailable locally; this is recorded separately below.

## 5. Storefront operation validation and selected Menu

The exact operation plus `HomeImageFields` was validated against Shopify Storefront API `2026-07` using the official validation workflow. Validation succeeded under artifact `1c68ac9a-8df7-46b3-95ae-a991ac2cb30b`, revision 1. No schema download or checked-in schema change followed.

`main-menu` was selected for the reversible development/staging configuration after read-only inspection showed a bounded projection suitable for the current flat Categories experience. The selector exists only in ignored `config/local.properties`; tracked defaults remain blank. No Menu was created or edited.

Fresh final-code selected-client command:

```powershell
.\gradlew.bat :storefront:testDebugUnitTest `
  -PgurbakirRunOwnedStorefrontProof=true `
  --tests "com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest" `
  --rerun-tasks --no-build-cache --no-parallel `
  --no-daemon --no-configuration-cache --console=plain
```

Result: **BUILD SUCCESSFUL in 2m 3s**; 27/27 tasks executed. JUnit XML records 1 test, 0 skipped, 0 failures, and 0 errors at `2026-09-12T12:50:20.816Z`. The proof used the actual configured public Storefront client, verified owned shop identity, read the selected Menu, confirmed a bounded tree and at least one usable unfiltered Collection, and separately observed `Success(null)` for a bounded nonexistent handle. No credentials or returned Menu content were logged.

## 6. Configured Gürbakır acceptance

After the last production refactor, the exact development-debug APK was installed on the approved headless `GurBakir_API36` AVD:

```powershell
.\gradlew.bat :app:installDevelopmentDebug --no-daemon --no-configuration-cache --console=plain
```

Result: **BUILD SUCCESSFUL in 5m 4s**; 160 tasks (1 executed, 159 up-to-date); installed on one device.

Read-only UI acceptance passed:

- Turkish chrome displayed the nine eligible Menu titles in depth-first order: `ÜRÜNLERİMİZ`, `Bardaklar ve Kadehler`, `Cezveler`, `Hamam ve Banyo`, `Sofra ve Sunum`, `Tabaklar ve Kaseler`, `Tavalar ve Sahanlar`, `Tencereler`, `Özel Ürünlerimiz`.
- No configuration error was visible.
- Selecting `Tencereler` opened the existing Collection journey and displayed `Yüklenen ürün: 5`; Back returned to Categories.
- Primary Home navigation returned to `Ürün Gamımız`.
- Under English foreground locale, chrome displayed `Categories` while the merchant-default title `Cezveler` remained; the obsolete compiled English label `Turkish Coffee Pots` was absent.

Two ADB assertion attempts failed because of test-driver PowerShell mistakes (string coordinate concatenation, then reserved automatic variable names). Each cause was isolated, corrected, and the same UI state was re-read successfully. These were not application failures. The emulator was terminated after acceptance.

No login, cart, checkout, payment, order, customer, Menu, or provider mutation occurred.

## 7. Final local verification evidence

| Verification | Result |
|---|---|
| Fresh focused baseline before implementation | `BUILD SUCCESSFUL in 2m 25s`; 207 tasks |
| `spotlessCheck detekt lint` after final code corrections | `BUILD SUCCESSFUL in 6m 9s`; 327 tasks: 151 executed, 17 from cache, 159 up-to-date |
| Canonical JVM suite across foundation/account/checkout/storefront/firebase/mobile-core/synthetic/app variants | `BUILD SUCCESSFUL in 2m 2s`; 227 tasks: 32 executed, 17 from cache, 178 up-to-date |
| Full app/core/synthetic assembly and Android-test APK matrix | `BUILD SUCCESSFUL in 18m 20s`; 754 tasks: 347 executed, 169 from cache, 238 up-to-date |
| Repository portability self-test | PASS, 59/59 fixtures |
| Repository portability validation | PASS, 45/45 checks |
| Synthetic package-validator self-test | PASS, 43/43 fixtures |
| Fresh synthetic debug/release package validation | PASS, 59/59 checks |
| Gitleaks on immutable base through last code checkpoint | PASS; 5 commits, about 85.53 KB scanned, no leaks found |
| `git diff --check` | PASS |
| Ownership/source audit | No `CatalogCategorySource` or `category_label_*`; all gateway implementers accounted for; no Home/navigation/workflow diff |

Build output retained existing unrelated Kotlin annotation/deprecation warnings; no Gate 6 warning was promoted or hidden.

## 8. Review and security evidence

CodeRabbit CLI 0.7.6 reviewed the immutable base through `39c7043` with `AGENTS.md` context. It completed successfully and raised **0 issues** across all 29 changed implementation/test/configuration files.

GitHub PR #22 (private historical archive) CodeRabbit review of `ab72a5f1db585b303d973c3a49879badf7557d1f` reported three validator false negatives. Each was verified with a failing mutation fixture and corrected in `92532e6`; CodeRabbit subsequently confirmed the fixes and resolved all three threads on `77d00a1`. Its later review reported the stale checkpoint label and the selector-value decoy; both were verified and corrected in the handoff and `5ed82f6`. Exact final-HEAD status remains required after those corrections are pushed.

Focused Codex Security diff scan `29308b61-495e-4e07-9a31-cbb68316223b` completed against exact commit `77d00a16dda7da6997464b27f47b2567e1910836` with **0 reportable findings** and complete recorded coverage. It reviewed untrusted Storefront input, selector/configuration handling, provider discrimination, bounds, filtered actions, identity/de-duplication, media egress, native routing, synthetic isolation, proof hygiene, and the review-corrected ownership validator. Delegated workers were unavailable under active session policy, so all security-relevant inventory items were reviewed sequentially in the parent task without reducing scope. The later `fef32be` change alters only newline-agnostic construction of existing negative self-test inputs.

The readable sealed report and SARIF export are retained under the scan's Codex Security artifact bundle. The immutable scan ID above is the portable lookup authority; no workstation-specific artifact path is embedded in this handoff.

Measured scan usage reported complete coverage: 4,230,091 total tokens; 4,219,089 input; 3,995,392 cached input. Daybreak access was not granted, so protected scan outputs may not be displayable without enrollment; this did not gate the scan.

## 9. Honest missing evidence

The final local managed-device commands did not execute instrumentation tests:

- API 30 stopped at `:account:ciApi30Setup` because the configured `system-images/android-30/aosp_atd/x86` image does not exist in the registered local SDK. Result: **BUILD FAILED in 1m 11s** at setup; 41 tasks (5 executed, 12 from cache, 24 up-to-date). Classification: **API 30 instrumentation NOT RUN — local environment unavailable**.
- API 23 compiled the synthetic app/test artifacts but stopped at `:synthetic:ciApi23Setup` with `java.util.concurrent.TimeoutException`. Result: **BUILD FAILED in 10m 25s** at setup; 175 tasks (1 executed, 174 up-to-date). Classification: **API 23 instrumentation NOT RUN — managed-device provisioning timed out**.

The offline managed emulator left by the timeout was terminated. These are environment setup failures, not failing test assertions. Exact-candidate GitHub CI must supply both managed-device lanes before merge readiness.

PR #22 (private historical archive) is open against `main`, and the branch is pushed. Run #114 on `77d00a1` passed both API 30 and API 23 instrumentation, but `validate` failed before repository validation because the direct-URL negative fixture did not mutate its Linux here-string. That superseded candidate therefore is not a green exact-HEAD run. `fef32be` makes the affected GraphQL mutations newline-agnostic, and local self-tests (58/58) plus live validation (45/45) passed afterward. Exact final-HEAD CI remains pending. No owner merge, merged-main ancestry/tree verification, post-merge CI, or documentation reconciliation occurred.

Therefore the correct current lifecycle state is:

**SOURCE IMPLEMENTED / CUTOVER ACCEPTANCE COMPLETE / NOT READY TO MERGE**

Multi-Brand Gate 6 is not technically closed.

## 10. Rollback

Rollback remains a normal reviewed revert of the Gate 6 implementation commits. It restores the compiled Catalog configuration and obsolete category-label resources. No persistence, user-data migration, dependency upgrade, checked-in schema replacement, external Shopify mutation, Firebase/provider mutation, signing/public identity change, or Android permission requires operational rollback. Removing the ignored selector is independent and has no external side effect.

After an exact candidate has passing API 30/API 23 CI, final review as needed, owner merge, merged-main verification, and successful post-merge CI, **Multi-Brand Gate 6 is technically CLOSED**. Perform the separate narrow live-authority reconciliation after technical closure. Do not begin Multi-Brand Gate 7 planning until that reconciliation is complete.
