# Phase 3 Planning Handoff

Date: 2026-08-06

Handoff type: documentation-only Stage 3 finalization

Branch: `main`

Starting Phase 2 checkpoint: `e06663294236ade802cb76b2e70773fea908f065`

Authoritative planning checkpoint: the commit containing this document; confirm after checkout with `git rev-parse HEAD`

This is the self-contained resume record for the finalized Phase 3 product scope and roadmap. It authorizes no application implementation. The next fresh task must begin from [PHASE-3-IMPLEMENTATION-GOAL.md](PHASE-3-IMPLEMENTATION-GOAL.md) and execute P3-00 first.

> **P3-00 completion note (2026-08-10):** The planning instruction below has been executed. Continue from the [authoritative P3-00 handoff](P3-00-HANDOFF.md); do not repeat P3-00 or start P3-01 before its merchant-input gate is satisfied.

## Final product decision result

The six originally unresolved normal product decisions were resolved autonomously and then challenged:

1. **02 Onboarding — `INTENTIONALLY_DIFFERENT`:** no marketing carousel; a minimal market/language first-run choice is allowed only if P3-00 proves it cannot be inferred safely.
2. **08 Wishlist — `INTENTIONALLY_DIFFERENT`:** local-only and account-independent in Phase 3; no Worker/backend or cross-device promise.
3. **17 Account deletion — `EXTERNALLY_BLOCKED`:** release-critical but cannot proceed without a functional owned request resource/process, accountable operator, re-authentication, retention, and support rules.
4. **18 Agreements/pages/permissions — `EXTERNALLY_BLOCKED`:** real owned, versioned legal/support content and routes are required before production Account UI and release.
5. **20 Push notifications — `DEFERRED`:** Phase 2 infrastructure stays disabled/consent-bound; production push needs purpose, sender/backend, routing, token lifecycle, privacy, and operations ownership beyond Phase 3.
6. **23 Analytics/crash reporting — `DEFERRED`:** Analytics remains absent; Crashlytics remains absent unless P3-16 makes a complete privacy, retention, consent, local-report, deletion, environment, and ownership decision.

The deeper reassessment made two material status changes:

- 02 Onboarding moved from the intermediate `NOT_APPLICABLE` decision to `INTENTIONALLY_DIFFERENT` for the narrowly conditional first-run case.
- 13 Password recovery/update moved from `INTENTIONALLY_DIFFERENT` to `NOT_APPLICABLE` because current hosted Customer Account access is passwordless; sign-in help/restart remains under account access.

Final distribution across exactly 24 feature groups:

- 11 `ACCEPTED`;
- 8 `INTENTIONALLY_DIFFERENT`;
- 2 `DEFERRED`;
- 2 `EXTERNALLY_BLOCKED`;
- 1 `NOT_APPLICABLE`.

There is no current unresolved product-decision state. Historical Phase 1/2 recommendation values are explicitly labeled historical and are superseded by the Phase 3 matrices.

## Newly explicit requirements and boundaries

The planning corpus adds and assigns all eight sub-requirements:

1. guest-to-authenticated cart reconciliation — 09/22;
2. cart note policy — 09;
3. profile birthdate/metafield purpose — 14;
4. cargo/fulfillment tracking — 16;
5. hosted passwordless account semantics — 11/12/13;
6. legal/mobile-page provenance, version, and offline behavior — 18/19;
7. notification/deep-link/callback route matrix — 01/19/20/22;
8. local-data clear/delete matrix — 06/08/17/23.

The reference coverage audit accounts for all 25 prepared destinations within the existing 24 groups. No new feature group was added. Client-visible behavior is separated from accepted parity, intentional Gürbakır differences, independent design, and server/runtime facts that remain `UNKNOWN`. Reference code, UI, branding, credentials, proprietary implementation details, Worker semantics, live Remote Config, and unobserved checkout/push behavior remain prohibited or unknown inputs.

Cart ownership is finalized as `Anonymous`, `CustomerAssociated`, `DetachPending`, and `Quarantined`. Logout/session expiry preserves only a truly anonymous cart. A customer-associated cart must detach buyer identity successfully or remain quarantined; it is never silently relabeled anonymous. Buyer identity also affects market/pricing and authenticated checkout.

Room is limited to justified local queryable data such as approved search history and local wishlist. OAuth/session and opaque cart identifiers remain behind existing Keystore-backed abstractions. Forward migrations, environment/market partitioning, rollback/fail-closed behavior, and the local-data clear/delete matrix are mandatory. Destructive migration is not a convenience option for retained wishlist data.

## Authoritative roadmap

The complete order is:

1. P3-00 Product Design, Information Architecture, and Content Readiness
2. P3-01 Production shell and real Home
3. P3-02 Categories and listing
4. P3-03 Search and local history
5. P3-04 Product detail
6. P3-05 Local wishlist
7. P3-06 Production cart
8. P3-07 Checkout handoff
9. P3-08 Owned legal/support web baseline
10. P3-09 Account access, hosted journeys, and session
11. P3-10 Profile
12. P3-11 Addresses
13. P3-12 Orders and tracking
14. P3-13 Account deletion request
15. P3-14 Safe Remote Config/update policy
16. P3-15 Product acceptance and hardening
17. P3-16 Production/release readiness

P3-00 is mandatory and planning-only. It must produce accepted IA, navigation, screen inventory, flows, visual direction/tokens/components/layouts, state patterns, accessibility/localization baseline, content/asset/market inventories, and design-review evidence. It creates no production code. **P3-01 is not implementation-ready until P3-00 records `PASS`.**

The working primary navigation is Home, Categories, Search, Wishlist, and Account, subject to P3-00 acceptance and progressive enablement. The cart action/destination is completely absent until P3-06 passes; no disabled placeholder is permitted.

## First-slice readiness and blockers

P3-00 can start after this clean Stage 3 checkpoint and a fresh task explicitly authorizes design/planning work. Available inputs include the complete Phase 2 foundation, all eight passing gates, six modules, typed configuration/navigation/brand/localization, Storefront/OAuth/cart/Checkout/Firebase adapters, TR/EN resources, reference reports, non-production evidence, physical proof, and security boundaries.

P3-00 still needs visual direction, brand inventory, merchant taxonomy/content/markets/currency/address/legal inventories, complete layouts/flows/components, and accepted design-review evidence. P3-01 additionally needs P3-00 `PASS` and resulting specifications; it is not ready now.

Remaining material external/release blockers are:

- brand/content/legal/support owners and real assets/pages;
- merchant taxonomy, market/currency/address/content policies;
- functional account-deletion process/resource/operator/retention/support;
- production identity, signing, Play, Shopify/Firebase, App Links, Data Safety, and release ownership;
- any production push, telemetry, hard-update, backend, or cross-device sync capability not currently approved.

The phase-classified blocker map, cross-cutting concern matrix, migration rules, parallelism constraints, and first-slice readiness are in [PHASE-3-IMPLEMENTATION-ROADMAP.md](PHASE-3-IMPLEMENTATION-ROADMAP.md).

## Phase 2 preservation and proof-surface boundary

Preserve production-compatible Phase 2 Storefront, cart/Keystore, Customer OAuth/PKCE/session, Checkout Kit, Firebase safe defaults, typed config/navigation/brand/localization/error/redaction, module/test/CI/lock/scanning infrastructure and evidence.

Temporary proof routes, screens, controllers, ViewModels, manual Storefront/cart/Checkout/Bogus/OAuth/Firebase actions, and synthetic output are not production UI. Remove them from production routing/release variants as the corresponding production slices land. P3-15/P3-16 must prove their exclusion without deleting reusable adapters or test seams.

## Documentation finalized

Created:

- [PHASE-3-PRODUCT-DECISIONS.md](PHASE-3-PRODUCT-DECISIONS.md)
- [PHASE-3-IMPLEMENTATION-ROADMAP.md](PHASE-3-IMPLEMENTATION-ROADMAP.md)
- [PHASE-3-ACCEPTANCE-MATRIX.md](PHASE-3-ACCEPTANCE-MATRIX.md)
- [phase-3-acceptance-matrix.csv](phase-3-acceptance-matrix.csv)
- [PHASE-3-IMPLEMENTATION-GOAL.md](PHASE-3-IMPLEMENTATION-GOAL.md)
- this handoff

Reconciled without rewriting unaffected historical evidence:

- [Phase 2 Candidate Feature Acceptance Pack](../phase2/CANDIDATE-FEATURE-ACCEPTANCE-PACK.md)
- [Phase 2 recommendation evidence CSV](../phase2/candidate-feature-acceptance-pack.csv)
- Phase 1 gap/reuse analysis, consolidated into the Phase 3 acceptance matrix and neutral reference model
- Phase 1 machine-readable reconciliation, preserved by the current Phase 3 acceptance CSV

## Targeted validation at handoff

- human and machine Phase 3 acceptance matrices contain exactly 24 matching IDs/statuses;
- distribution is 11/8/2/2/1 and gap/reuse CSV final-status columns agree;
- product decision table contains exactly 24 rows;
- roadmap contains exactly P3-00 through P3-16;
- P3-00/P3-01 and cart-visibility gates are explicit;
- changed Markdown local links resolve;
- obsolete decision values survive only as explicitly historical `phase2_recommendation` evidence;
- external blockers and server/runtime unknowns remain explicit;
- architecture remains native Kotlin/Jetpack Compose with distinct Storefront/Customer Account and project-owned interfaces;
- targeted credential-pattern scan finds no introduced credential value;
- all five required ignored local configuration files remain present, ignored, and untouched;
- staged diff is documentation-only and passes whitespace/error checks;
- no Gradle build, app/device test, browser, Shopify/Firebase/OAuth/Checkout/Bogus proof, external-service operation, application implementation, or remote push occurred.

## Exact resume instruction

Open [PHASE-3-IMPLEMENTATION-GOAL.md](PHASE-3-IMPLEMENTATION-GOAL.md) in a fresh task. Verify the carrier commit with `git rev-parse HEAD`, confirm clean `main` and empty stash, and perform **P3-00 only**. Stop after its `PASS` or honest blocked handoff; do not begin P3-01 automatically.
