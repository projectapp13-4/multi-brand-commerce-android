# Phase 3 Implementation Goal

Date: 2026-08-06

Use: paste or reference this document in a fresh Codex task after the planning checkpoint and usage reset.

Authority: [PHASE-3-PRODUCT-DECISIONS.md](PHASE-3-PRODUCT-DECISIONS.md), [PHASE-3-ACCEPTANCE-MATRIX.md](PHASE-3-ACCEPTANCE-MATRIX.md), and [PHASE-3-IMPLEMENTATION-ROADMAP.md](PHASE-3-IMPLEMENTATION-ROADMAP.md).

> **P3-00 completion note (2026-08-10):** P3-00 is now completed by the [authoritative P3-00 handoff](P3-00-HANDOFF.md). This file remains the historical P3-00 start instruction. Do not rerun P3-00 or begin P3-01 until the handoff's exact merchant input packet is approved.

## Objective

Continue from the completed documentation-only Phase 3 planning checkpoint. Execute the roadmap slice by slice, beginning with **P3-00 — Product Design, Information Architecture, and Content Readiness**.

P3-00 is planning/design work only. Do not create or modify production application code during P3-00. Record a reviewable `PASS` only when every P3-00 exit criterion in the roadmap is satisfied. **P3-01 is not implementation-ready and must not begin until P3-00 passes.**

After P3-00 passes, request/confirm authorization for the next implementation slice and implement only that authorized slice. Do not silently begin the full roadmap.

## Required start checks

1. Read the root `AGENTS.md`, Phase 2 completion handoff/gate status, all documents in `docs/phase3`, the source-of-truth/provenance guide, architecture direction, and relevant ADRs.
2. Confirm the exact planning handoff commit from [PHASE-3-PLANNING-HANDOFF.md](PHASE-3-PLANNING-HANDOFF.md), branch `main`, clean worktree, and empty stash.
3. Confirm the five required ignored local configuration files exist without reading, printing, copying, or modifying their values.
4. Confirm the requested slice and its external/content/design/security entry criteria. Stop honestly if a genuine blocker is unmet.
5. Use the prepared reference reports and `the retired private evidence-navigation helper` only for a narrow unresolved behavior question. Never rescan, run, install, modify, or contact the reference application or its services.

## Governing product boundaries

- Preserve the final 24-feature status distribution: 11 `ACCEPTED`, 8 `INTENTIONALLY_DIFFERENT`, 2 `DEFERRED`, 2 `EXTERNALLY_BLOCKED`, and 1 `NOT_APPLICABLE`.
- Preserve both material status changes: conditional minimal onboarding is `INTENTIONALLY_DIFFERENT`; legacy password recovery/update is `NOT_APPLICABLE` under hosted passwordless Customer Account semantics.
- Implement all eight sub-requirements in their owning slices: cart reconciliation, cart note policy, birthdate/metafield purpose, cargo tracking, hosted passwordless semantics, legal-page provenance/version/offline behavior, route matrix, and local-data clear/delete matrix.
- Preserve the explicit reference-parity/intentional-difference/independent-implementation/unknown-server distinction for each slice.
- Never copy decompiled code, obfuscated structure, UI, layout, branding, strings, assets, credentials, proprietary implementation details, or unverified Worker/Remote Config behavior.
- Use current official Android, Shopify, and Firebase contracts and only project-owned endpoints/environments.
- Do not invent legal text, support details, commercial claims, brand assets, market/address policies, production identity, credentials, or backend behavior.

## Mandatory implementation order

Follow this order unless a new approved roadmap document changes it:

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

Only the limited parallelism documented in the roadmap is allowed, and only with clean ownership of shared schema, navigation, and migrations.

## Preserve Phase 2; do not repeat it

Preserve the production-compatible Phase 2 Storefront, cart/Keystore, Customer OAuth/PKCE/session, Checkout Kit, Firebase safe-default, typed configuration/navigation/brand/localization/error/redaction, module, test, validation, lock, scanning, and CI infrastructure.

Do not repeat Shopify/Firebase client creation, OAuth setup, Checkout Kit/Bogus transactions, FCM delivery, physical-device foundation proofs, or broad environment setup merely to demonstrate activity. Run a live/device proof only when a newly implemented slice changes a load-bearing contract or has a roadmap exit criterion that cannot be established locally. Never place a real order, use a real payment method, mutate production data, or contact reference services.

Temporary proof screens, routes, controllers, ViewModels, synthetic actions, and manual test controls are not production UI. Remove them from production routing/release variants as their corresponding production slices land; retain only reusable infrastructure and non-production test seams.

## Slice execution contract

For every authorized slice:

1. Reconfirm entry criteria and link the applicable acceptance rows, evidence, official contract, and intentional differences.
2. State the exact user-visible outcome and the proof UI being retired.
3. Implement independently in the approved native Kotlin/Compose architecture with typed routes, UDF, ViewModels/StateFlow, repositories/data sources, and distinct Storefront/Customer Account boundaries.
4. Add only dependencies demonstrated by the slice; review trust, maintenance, version, schema, and rollback cost.
5. Apply the roadmap’s storage owner and Room migration rules. Never move tokens or opaque cart identifiers to plaintext Room/DataStore. Never use destructive migration as a shortcut for retained wishlist data.
6. Implement loading, empty, partial, offline, retry, process-death, accessibility, localization, privacy, and typed-error behavior proportional to the feature.
7. Validate in the project-prescribed order: formatting/static analysis; focused unit/contract/repository tests; Compose/instrumentation; accessibility/deep-link/lifecycle/device proof only where relevant; build/scans/performance when required by the slice.
8. Update human and machine acceptance evidence together. Do not claim service/runtime behavior that was not observed.
9. Create a coherent local Git checkpoint containing only the slice and its evidence. Preserve unrelated user work and leave no required work only in a stash.
10. Report passed, failed, skipped, externally blocked, and remaining `UNKNOWN` facts honestly. Stop when missing authorization, content, legal commitment, production credentials, spending, backend ownership, or external process prevents safe progress.

## Non-negotiable product rules

- P3-01 exposes no cart action/destination until P3-06 and no other non-functional production destination.
- Wishlist is local-only and account-independent in Phase 3; no Worker/backend sync.
- Cart ownership uses `Anonymous`, `CustomerAssociated`, `DetachPending`, and `Quarantined`. Logout/expiry preserves only a truly anonymous cart. Confirm detachment or quarantine.
- Cart buyer identity affects market/pricing/authenticated checkout; it is not merely UI state.
- Profile uses only approved current fields; birthdate/metafield requires separate purpose/privacy/schema approval.
- Production Account UI waits for the owned legal/support baseline.
- Account deletion remains blocked until a functional owned request process/resource and accountable policy exist. Never place Admin/backend secrets in mobile code.
- Remote Config failure remains non-blocking. A hard minimum-version gate requires the P3-14/P3-16 governance and rollback criteria.
- Production push and Analytics remain beyond Phase 3. Crashlytics remains absent unless P3-16 explicitly approves its full privacy/retention/local-report lifecycle.
- Final release must contain no proof UI, synthetic content/data, reference branding, placeholder legal/support content, or ignored secret/config values.

## Stop conditions

- Stop after P3-00 with a clear `PASS`/`BLOCKED` handoff; do not automatically start P3-01.
- Stop before any external mutation, spending, legal/commercial commitment, production credential use, backend creation, production deployment, remote push, or roadmap-scope change without explicit authorization.
- If an external blocker persists, document the exact missing owner/input/evidence and preserve a clean checkpoint. Do not simulate completion.

The first requested deliverable in the new task is the complete P3-00 design/IA/content-readiness package and its acceptance evidence, not application code.
