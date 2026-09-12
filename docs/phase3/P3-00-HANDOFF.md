# P3-00 Authoritative Handoff

Date: 2026-08-10

Gate status: **PASS — PRODUCT/UX/IMPLEMENTATION-READINESS PLANNING ONLY**

Branch: `main`

Starting checkpoint: `fb59dc0369355fc298576768984a53f3646d89ad`

Final checkpoint: the single documentation/validator commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This is the self-contained resume record for P3-00. It authorizes no production application implementation and explicitly stops before P3-01.

## What P3-00 completed

- Reconciled all 24 feature groups without changing the accepted distribution: 11 `ACCEPTED`, 8 `INTENTIONALLY_DIFFERENT`, 2 `DEFERRED`, 2 `EXTERNALLY_BLOCKED`, and 1 `NOT_APPLICABLE`.
- Defined the production information architecture, progressive five-destination primary navigation, cart visibility gate, route/deep-link/auth/back-stack rules and safe route recovery.
- Inventoried 27 production or hosted surfaces with ownership, routes, state holders, Phase 2 dependencies, data/persistence, complete loading/empty/success/error/offline behavior, accessibility, localization, analytics and evidence/rationale fields.
- Specified 17 critical flow contracts including first launch, discovery, browse/list/search/filter, product/variant, wishlist, cart restoration/editing/ownership, checkout, hosted sign-in/callback/session, account states, addresses, orders/tracking, legal/support, logout/local clear and account deletion.
- Accepted an independent Material 3 design-system baseline using the existing owned tokens, verified light/dark foreground pairs, semantic component families, state/error/destructive patterns, adaptive layouts, reduced motion, 48dp targets, 200 percent text/reflow and TR/EN resource policy.
- Produced the complete content/asset/legal/market/merchant inventory with role owners and exact affected slices. No fake content or final brand asset was created.
- Finalized data source-of-truth, Keystore/DataStore/Room/cache ownership, retention, encryption, migration, backup/reinstall, cross-device and SR-08 local-clear behavior.
- Finalized cart states (`Anonymous`, `CustomerAssociated`, `DetachPending`, `Quarantined`), restoration/reconciliation/detachment/quarantine and visibility rules. Cart notes are excluded from Phase 3.
- Mapped every Phase 2 proof route/screen/controller/ViewModel to replacement/removal and preserved only production-compatible adapters/contracts/test seams.
- Defined exact P3-01 through P3-16 outcomes, scope, prerequisites, tests/device evidence, acceptance, checkpoint and stop conditions.

## Phase 2 foundation confirmed for reuse

- Six modules and single-activity Kotlin/Compose/Hilt/UDF/StateFlow architecture.
- Typed environment, brand/theme, localization, navigation, URI, errors, logging/redaction and safe-default configuration.
- Separate typed Storefront and Customer Account schemas/clients.
- Storefront gateway, cart coordinator, complete protected cart ID and Android Keystore cart store.
- Customer Account discovery, system-browser OAuth/PKCE, token exchange/refresh, callback/session/logout and Android Keystore customer store.
- Official Checkout Kit adapter/coordinator and restrictive origin/permission/payload policy.
- Firebase Remote Config false defaults and disabled/explicit-consent FCM boundary, while production push and telemetry remain absent.
- Pinned wrappers, dependency verification/locks, CI quality lanes, tests, validators, secret scanning and Phase 2 physical proof evidence.

No Shopify, Firebase, OAuth, cart, Checkout, Bogus, FCM, device or payment proof was repeated. P3-00 changed no load-bearing runtime contract.

## Reference evidence used

The required local skill queried only prepared structured evidence. The immutable sibling package exists, and the summary confirmed its fixed hash plus 24 feature groups, 25 destinations, 31 GraphQL operations, 5 endpoints/routes and 9 storage records. A complete destination query confirmed serialized route names and arguments in the private reference corpus, with raw evidence paths returned by the helper.

Authority/confidence:

- exact serialized destinations and parameters are `FACT` at the helper's `HIGH` confidence except the helper's `MEDIUM` interpretation for product-list and WebView optional parameters;
- reference capability coverage is behavior/completeness evidence only;
- the complete directed runtime graph, Worker source/policy, live Remote Config, live URLs/content and unobserved service behavior remain `UNKNOWN`;
- no APK, decompiled body, brand, asset, copy, credential, endpoint value or server behavior was copied or contacted.

## Information architecture and proof replacement

Primary navigation is Home, Categories, Search, Wishlist and Account, adapted between bottom navigation and rail by current app-window width. Destinations appear only when functional. Cart remains completely absent until P3-06. Account is one mixed signed-out/signed-in destination; registration/verification/password behavior stays hosted. Legal/support is public and critical. External pages use an allowlisted browser/Custom Tabs boundary. Unknown/unavailable/replayed routes use the redacted recovery screen.

Current Phase 2 `FoundationHome`, generic integration details, Customer Account proof, cart/Checkout proof, Firebase proof and notification proof routing are not production UI. P3-01/P3-06/P3-07/P3-09/P3-14 progressively remove or replace them in production routing; P3-16 proves exclusion from release. Reusable service adapters, coordinators, stores and deterministic test seams remain.

## External blockers and release consequences

The complete ownership record is [P3-00 Content, Asset, Legal, Market, and Merchant Inventory](P3-00-CONTENT-ASSET-MARKET-INVENTORY.md). Material blockers include:

- P3-01: supported market/currency behavior and exact merchant Home section/media packet;
- P3-02: taxonomy and supported filter/sort policy;
- P3-08: real owned privacy, terms, support and policy corpus with canonical versioned routes;
- P3-10/P3-11/P3-12: profile field, address-country, status/carrier and support vocabularies;
- P3-13: functional owned deletion resource/process, operator, re-auth, retention and SLA;
- P3-16: final logo/icon/store assets, production identity/signing/Play/Shopify/Firebase/App Links/Data Safety/support/release ownership and rollback.

Production Account UI cannot begin before P3-08. Production release cannot pass with groups 17 or 18 blocked. Deferred push/Analytics/Crashlytics, a hard update gate, cross-device wishlist sync, reference Worker parity and a new backend remain absent unless separately approved.

## P3-01 readiness

P3-00 is `PASS`, but P3-01 is **blocked on one exact external merchant input packet**:

1. supported markets, default market, market switching, currency/tax display and language/market relationship;
2. each Home section's owned source, order, TR/EN labels, item limit and empty fallback;
3. permitted Shopify media hosts and crop/aspect/focal/alt-text policy;
4. acceptance of the text wordmark and current P3-00 token/system-font baseline for P3-01 while final identity assets remain P3-16 inputs.

P3-01 must not start until that packet is approved. This is an external-input stop, not a P3-00 planning gap.

## Validation contract and results

The P3-00 validator must pass before the checkpoint commit and after staging:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\Test-Phase3Planning.ps1
git diff --check
gitleaks dir . --config .gitleaks.toml --redact --no-banner
```

The final commit review must additionally confirm:

- all required P3-00 artifacts and local Markdown links exist;
- both 24-row machine matrices agree on IDs/statuses and the 11/8/2/2/1 distribution;
- all 27 screen rows and required fields are present;
- P3-00 through P3-16 remain represented;
- strict UTF-8 and mojibake checks pass;
- defined credential-pattern and redacted Gitleaks checks detect no potential secret;
- all five ignored configuration files remain present, ignored and untracked without reading values;
- changed paths are documentation plus `scripts/Test-Phase3Planning.ps1` only; no production code/resource/manifest/Gradle/dependency/schema change exists;
- branch is `main`, stash is empty, no remote is configured, and the final worktree is clean.

No Gradle, browser/device, live-service or broad security scan is required for this documentation-only change.

Pre-commit validation on 2026-08-10 passed all 15 validator checks: 14 required artifacts, 16 Phase 3 files under strict UTF-8/mojibake inspection, 13 Markdown files with resolving local links, 24 reconciled feature rows, the `11/8/2/2/1` status distribution, 27 complete unique screen rows, 17 represented roadmap slices, 9/9 boundary assertions, five present/ignored/untracked local configuration files, expected Git state, and documentation/validator-only scope. `git diff --check` passed. Redacted Gitleaks directory scanning examined approximately 1.48 MB and reported no leaks. The same validator is rerun against the staged checkpoint and with `-RequireCleanWorktree` after commit.

## Exact next goal

Create this new goal only after supplying/approving the merchant packet:

> Provide and approve the P3-01 merchant Home/market input packet, then implement and locally checkpoint P3-01 production shell and real Home only from `docs/phase3/P3-00-HANDOFF.md`; expose no cart or later incomplete destination, preserve Phase 2 adapters, replace the foundation proof dashboard in production routing, and stop after P3-01 acceptance.

## Absolute stop

P3-00 is complete at the commit containing this handoff. No P3-01 Compose component, production route, resource, ViewModel, repository, schema, test, dependency or build change was started. Stop here.
