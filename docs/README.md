# Documentation Map

Status date: 2026-09-17

This is the repository documentation entry point. It distinguishes current
authority and constraints from historical evidence so that older records remain
useful without becoming present-state instructions.

This public repository is the forward-development authority. Its
[migration handoff](PUBLIC-REPOSITORY-MIGRATION-HANDOFF.md) records the fresh
history, protected bootstrap, and historical-archive cutover evidence.

## Current project status

- The accepted platform remains native Android: Kotlin + Jetpack Compose,
  single-activity architecture, typed Navigation Compose routes,
  ViewModel/`StateFlow` unidirectional data flow, Hilt, Apollo Kotlin, Room,
  DataStore, Android Keystore-backed protected state, Shopify Storefront and
  Customer Account APIs, Checkout Kit, and bounded Firebase infrastructure.
- Multi-Brand Gates 0–7 are complete. Gate 7 is technically closed: protected
  PR #6 merged the final candidate `e0a62e9047f2cc6d3241bdb3b4c963fdbdae27e0`
  as merge commit `3b22707f27f71973383cf176503f8d967ece9b05`,
  and exact merged-main push run `34816195929` completed `validate`, API 30
  `instrumentation`, and API 23 `minimum-sdk-instrumentation` successfully.
- Gate 7 implements bounded Shopify-driven Home editorial content through an
  application-selected metaobject root, finite native collection-grid and
  featured-product rendering, typed Product/Collection actions, editorial-only
  LKG persistence and deterministic fallback/expiry behavior. Android retains
  executable/navigation/capability/security authority and live commerce truth.
- [`multi-brand/GATE-7-COMPLETION-HANDOFF.md`](multi-brand/GATE-7-COMPLETION-HANDOFF.md)
  remains the historically accurate pre-merge implementation/evidence record;
  it is not rewritten to claim later lifecycle events.
- Gate 8 provisioning/onboarding is technically closed. Implementation PR #8
  merged as `d71353bddb9a4610b1ff9908404b90ac4261911d`; corrective PRs #9,
  #10 and #11 merged through final main
  `77455f5e8f2711a85194626892f1ad6fab081e86`; and exact merged-main run
  `35030134061` passed `validate`, API 30 `instrumentation`, and API 23
  `minimum-sdk-instrumentation`. Configured development/staging acceptance
  passed with trusted bindings, preserved Menu and selected Home content, one
  DRAFT/unselected acceptance probe, and repeated zero-write idempotence.
  [`multi-brand/GATE-8-COMPLETION-HANDOFF.md`](multi-brand/GATE-8-COMPLETION-HANDOFF.md)
  remains the historical pre-merge record rather than the closure authority.
- The Gate 9 implementation candidate adds development-only `:trial` as the
  second real application/store pilot and bounded Home v2 image/video support.
  Real Trial v1/v2 provider checkpoints and local source/artifact validation
  exist. The exact Trial cart provider lifecycle now passes after enabling only
  the required customer-read scope for the query's nested buyer field; physical
  add/cart/Checkout Kit/return/cleanup is proven up to the development-store
  password gate. Samsung API33 configured Gürbakır/Trial update, same-handle,
  logged-out cart/process-death isolation, bounded player/measurement, Firebase
  debug runtime and Trial publish/change/remove/rollback evidence now exist.
  Live Firebase instrumentation is explicitly opt-in as of `fcbbe84`; credential-free
  API 23/API 30 lanes keep the normal identity test running and report the two provider
  proofs as skipped/not requested, while configured opt-in still executes both proofs.
  Real Trial PKCE/profile/address/order-empty/restart/logout acceptance now passes
  its executed substeps. Physical Home-video rotation/rebuild continuity also passes
  at `91ae165`, while natural token expiry, cross-app authenticated-session isolation,
  physical noisy/deadline player rows and hosted exact-SHA CI remain partial or not
  run. Gate 9 therefore remains open; see
  [`multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md`](multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md).
  Trial is never a production application.
- Phase 3 functional implementation and integrated acceptance are complete
  through **P3-15**. Account-deletion merchant acknowledgement,
  retention/SLA execution, and actual remote deletion remain externally
  unverified.
- **P3-16 production/release readiness is not started.** Repository migration,
  builds, tests, public CI and Multi-Brand Gate closure do not constitute
  production-release proof.

## Authority order

Use the narrowest current source for the question:

1. approved Gürbakır requirements and
   [`phase3/PHASE-3-PRODUCT-DECISIONS.md`](phase3/PHASE-3-PRODUCT-DECISIONS.md)
   define product scope and intentional differences;
2. current source/configuration and tests define implemented behavior;
3. [`architecture`](architecture) and accepted [`decisions`](decisions) define
   the current target and durable rationale;
4. [`phase3/PHASE-3-ACCEPTANCE-MATRIX.md`](phase3/PHASE-3-ACCEPTANCE-MATRIX.md)
   and current handoffs define functional acceptance and release boundaries;
5. current official Android, Kotlin, Shopify, Firebase, OAuth, and related
   documentation governs external contracts;
6. approved project-owned non-production observations validate runtime facts;
7. compact [`reference-model`](reference-model) documents preserve historical
   behavioral and boundary knowledge only where higher authority is silent.

Historical handoffs, research, plans, and reports retain their checkpoint
scope. They do not override current source or later accepted decisions.

## Current authority

| Path | Role |
|---|---|
| [`OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`](OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md) | Durable decision and approval boundary |
| [`architecture`](architecture) | Accepted Multi-Brand target, brand boundaries, onboarding, and migration-sensitive identities |
| [`decisions`](decisions) | Accepted platform, navigation, SDK, and Multi-Brand ADRs |
| [`multi-brand/README.md`](multi-brand/README.md) | Current Gate index and unfinished continuation point |
| [`multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md`](multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md) | Gate 9 implementation candidate, A1–A14 evidence and open blockers |
| [`operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md`](operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md) | Turkish Shopify/Firebase/content/release owner operations and rollback guide |
| [`PUBLIC-REPOSITORY-MIGRATION-HANDOFF.md`](PUBLIC-REPOSITORY-MIGRATION-HANDOFF.md) | Fresh-history public bootstrap, rights, protection, CI, and authority-cutover evidence |
| [`phase3/README.md`](phase3/README.md) | Current functional status index |
| [`phase3/PHASE-3-PRODUCT-DECISIONS.md`](phase3/PHASE-3-PRODUCT-DECISIONS.md) | Final product decisions |
| [`phase3/PHASE-3-ACCEPTANCE-MATRIX.md`](phase3/PHASE-3-ACCEPTANCE-MATRIX.md) | Feature-by-feature acceptance status |
| [`phase3/P3-13-HANDOFF.md`](phase3/P3-13-HANDOFF.md) | Account-deletion mobile/external boundary |
| [`phase3/P3-15-HANDOFF.md`](phase3/P3-15-HANDOFF.md) | Integrated functional acceptance |
| [`phase3/P3-16-HANDOFF.md`](phase3/P3-16-HANDOFF.md) | Unstarted production/release entry boundary |
| [`product-quality/UI-REFINEMENT-WORKSTREAM-HISTORY-AND-HANDOFF.md`](product-quality/UI-REFINEMENT-WORKSTREAM-HISTORY-AND-HANDOFF.md) | Current UI-refinement continuation point |
| [`preparation/ARCHITECTURE-DIRECTION.md`](preparation/ARCHITECTURE-DIRECTION.md) | Durable native application direction |

## Current constraints and supporting references

- [`preparation/SOURCE-OF-TRUTH-AND-PROVENANCE.md`](preparation/SOURCE-OF-TRUTH-AND-PROVENANCE.md)
  governs clean-room, provenance, reference, and secret handling.
- Selected [`phase2`](phase2) proof and threat-model records remain relevant to
  dependencies, configuration, Storefront, Customer Account, Checkout Kit,
  Firebase, and trust boundaries.
- Remaining [`phase3`](phase3), [`product-quality`](product-quality),
  [`testing`](testing), and [`reports`](reports) records retain their explicitly
  scoped constraints and evidence.
- [`reference-model/COMMERCE-BEHAVIOR.md`](reference-model/COMMERCE-BEHAVIOR.md)
  preserves neutral feature, navigation, state, UX, and relationship knowledge.
- [`reference-model/SYSTEM-BOUNDARIES-AND-LIMITATIONS.md`](reference-model/SYSTEM-BOUNDARIES-AND-LIMITATIONS.md)
  preserves neutral provider, network, data, storage, trust, and uncertainty
  knowledge.

The two reference-model documents are deliberately non-forensic and
non-authoritative. The original analysis remains in the private historical
archive and is not required for normal development.

## Historical Multi-Brand evidence

The [`multi-brand`](multi-brand) directory retains accepted research and plans
plus Gate 1–8 historical completion handoffs and the open Gate 9 candidate
handoff. Handoffs preserve the
evidence and lifecycle wording of their checkpoint; current closure status is
recorded only in the current indexes.

## Historical phase relationship

Phase 2, Phase 3, preparation, testing, and Product Quality records were written
at different checkpoints. A file is not obsolete merely because it uses
Gürbakır terminology or records historical context. Concrete Gürbakır facts are
intentional; live constraints remain in force until explicitly superseded.

The private migration ledger classifies every document as current authority,
current constraint/reference, historical evidence worth retaining, content to
consolidate, obsolete/superseded, or private historical-only. Exclusion from
the public tree occurs only after unique information is mapped to retained
authority or the neutral reference model.

## Publication and licensing boundary

The root `LICENSE`, `TRADEMARKS.md`, `ASSET-LICENSES.md`,
`THIRD_PARTY_NOTICES.md`, and `SECURITY.md` govern the public repository
boundary. Apache-2.0 does not grant Gürbakır or third-party trademark/asset
rights. Any unresolved asset-rights entry blocks public visibility.
