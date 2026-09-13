# Documentation Map

Status date: 2026-09-13

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
- Multi-Brand Gates 0–6 are complete. Gate 6 is technically closed: its final
  pull request was merged into `44202d31da8820a1f1766cb767bc0a1619d9c07e`,
  and the exact merged-main push run `34710233503` completed `validate`, API 30
  `instrumentation`, and API 23 `minimum-sdk-instrumentation` successfully.
  This was reverified immediately before the public-repository migration.
- [`multi-brand/GATE-6-COMPLETION-HANDOFF.md`](multi-brand/GATE-6-COMPLETION-HANDOFF.md)
  remains the historically accurate pre-merge implementation/evidence record;
  it is not rewritten to claim later lifecycle events.
- The Multi-Brand migration remains unfinished. Gürbakır `:app` is the first
  real brand application; `:synthetic` is not a merchant application; no second
  real brand application exists.
- Phase 3 functional implementation and integrated acceptance are complete
  through **P3-15**. Account-deletion merchant acknowledgement,
  retention/SLA execution, and actual remote deletion remain externally
  unverified.
- **P3-16 production/release readiness is not started.** Repository migration,
  builds, tests, and public CI do not constitute release proof.

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
plus the individual Gate 1–6 completion handoffs. Handoffs preserve the
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
