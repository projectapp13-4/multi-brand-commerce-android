# Multi-Brand Documentation

Status: **Gates 0–8 technically closed; Gate 9 implementation candidate açık**

## Purpose

This directory preserves accepted architecture reasoning, approved execution
plans, Gate 1–8 implementation/evidence records, and the open Gate 9 candidate
for evolving the Gürbakır
application into a multi-brand Android monorepo. It is a navigation and status
index, not a duplicate architecture specification.

## Authority model

```text
implemented behavior -> current source/configuration/tests
accepted target -> ../architecture/*
durable rationale -> ../decisions/*
investigation/history -> research/*
approved gate execution -> plans/* or recorded owner-approved scope
historical gate outcome -> GATE-1 through GATE-8 completion handoffs
current Gate 9 candidate -> GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md
current lifecycle status -> this index and ../README.md
product/release boundary -> ../phase3/*
```

Completion handoffs preserve the observed state and evidence available when
they were written. They are not retroactively rewritten to claim later CI,
review, merge, or post-merge events.

## Canonical architecture

- [Multi-Brand Architecture](../architecture/MULTI-BRAND-ARCHITECTURE.md) —
  target modules, dependency direction, ownership, and migration boundary.
- [Brand Boundaries](../architecture/BRAND-BOUNDARIES.md) — operational
  classification and anti-contamination rules.
- [Gürbakır Legacy Identities](../architecture/GURBAKIR-LEGACY-IDENTITIES.md) —
  exact compatibility identities and unresolved production state.
- [Brand Onboarding](../architecture/BRAND-ONBOARDING.md) — future real-brand
  ownership and evidence contract.
- [ADR-0004](../decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md)
  — one application module per real brand plus shared `:mobile-core`.

## Research and design evidence

- [Forensic Research](research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md)
  is historical research. Its older brand-flavor recommendation is superseded
  where it conflicts with ADR-0004 and current architecture.
- [Normative Design Evidence](research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md)
  preserves the reassessment that produced the accepted target.

Research supports decisions but does not override current source or canonical
architecture.

## Gate index

| Gate | Preserved execution/evidence | Current status |
|---|---|---|
| 0 | [Documentation plan](plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md) | Complete |
| 1 | [Plan](plans/GATE-1-MOBILE-CORE-EXTRACTION-IMPLEMENTATION-PLAN.md), [handoff](GATE-1-COMPLETION-HANDOFF.md) | Complete |
| 2 | [Plan](plans/GATE-2-SYNTHETIC-APPLICATION-IMPLEMENTATION-PLAN.md), [handoff](GATE-2-COMPLETION-HANDOFF.md) | Complete |
| 3 | [Plan](plans/GATE-3-IDENTITY-DOMAIN-MARKET-PERSISTENCE-IMPLEMENTATION-PLAN.md), [handoff](GATE-3-COMPLETION-HANDOFF.md) | Technically closed |
| 4 | Owner-approved execution scope, [handoff](GATE-4-COMPLETION-HANDOFF.md) | Technically closed |
| 5 | [Handoff](GATE-5-COMPLETION-HANDOFF.md) | Technically closed |
| 6 | [Plan](plans/GATE-6-SHOPIFY-NAVIGATION-DISCOVERY-IMPLEMENTATION-PLAN.md), [handoff](GATE-6-COMPLETION-HANDOFF.md) | Technically closed |
| 7 | [Plan](plans/GATE-7-BOUNDED-HOME-CONTENT-IMPLEMENTATION-PLAN.md), [handoff](GATE-7-COMPLETION-HANDOFF.md) | Technically closed |
| 8 | [Plan](plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.md), [handoff](GATE-8-COMPLETION-HANDOFF.md) | Technically closed |
| 9 | [Approved plan](plans/GATE-9-SECOND-STORE-MEDIA-PILOT-IMPLEMENTATION-PLAN.md), [candidate handoff](GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md), [owner guide](../operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md) | Open: A4 FAIL/setup required; bounded Samsung evidence and A13 provider rehearsal exist; A5/A8/A9/CI remain incomplete |

## Gate 6 closure evidence

Gate 6 moved Categories discovery to a bounded, application-selected Shopify
Menu. Application modules own their selectors; `:storefront` maps provider
data into neutral contracts; `:mobile-core` owns bounded projection and UI
behavior; `:synthetic` remains inert and provider-isolated.

Immediately before the repository migration, the integration state was
reverified:

- merged-main commit:
  `44202d31da8820a1f1766cb767bc0a1619d9c07e`;
- exact merged-main push run: `34710233503`;
- `validate`: successful;
- API 30 `instrumentation`: successful; and
- API 23 `minimum-sdk-instrumentation`: successful.

The [Gate 6 completion handoff](GATE-6-COMPLETION-HANDOFF.md) intentionally
remains its pre-merge record and therefore still says that merge and technical
closure were not yet proven at that checkpoint.

## Gate 7 closure evidence

Gate 7 moved the bounded Home editorial contract to an application-selected
Shopify metaobject root while Android retained finite native rendering,
capability ownership, typed Product/Collection navigation, refresh/expiry
behavior, validation, persistence policy and executable authority. Only the
approved collection-grid and featured-product section families are supported;
remote content cannot define arbitrary native routes, components or code.

Gate 7 closure was verified on the public repository:

- final PR-head candidate:
  `e0a62e9047f2cc6d3241bdb3b4c963fdbdae27e0`;
- protected PR: `#6`;
- merge commit:
  `3b22707f27f71973383cf176503f8d967ece9b05`;
- exact merged-main push run: `34816195929`;
- `validate`: successful;
- API 30 `instrumentation`: successful; and
- API 23 `minimum-sdk-instrumentation`: successful.

Configured non-production Shopify/client acceptance, bounded LKG behavior,
process-restart recovery and the Account-disabled Legal/Support correction were
recorded before merge. The public Storefront token noted during local evidence
collection was assessed as public mobile client configuration rather than a
private/Admin secret; it was not committed or placed in PR/CI evidence.

The [Gate 7 completion handoff](GATE-7-COMPLETION-HANDOFF.md) intentionally
remains its pre-merge implementation/evidence record and is not rewritten to
claim merge or post-merge events.

## Gate 8 closure evidence

Gate 8 replaced the single-root, Gürbakır-specific nonproduction setup with
explicit application/profile resolution, deterministic tracked projections,
scoped ignored client configuration, independently approved provider bindings,
explicit module/CI enrollment, and a bounded local PowerShell operator. It did
not add a runtime brand switch, a second real merchant application, or a hosted
control plane.

Gate 8 implementation and live-provider compatibility corrections merged in
four protected PRs:

- implementation PR #8: `d71353bddb9a4610b1ff9908404b90ac4261911d`;
- Shopify/public-HTTP correction PR #9:
  `3ce1d78c908d954568a7b3229c1f098cd9a7bf73`;
- Firebase quota-project correction PR #10:
  `6583f877674a837e8b252720f2d0bbf5b3bcf4eb`; and
- probe normalization/recovery PR #11:
  `77455f5e8f2711a85194626892f1ad6fab081e86`.

Exact merged-main run `35030134061` passed `validate`, API 30
`instrumentation`, and API 23 `minimum-sdk-instrumentation`. Configured
development and staging acceptance passed with trusted Shopify/Firebase
bindings, actual Storefront Menu/Home readback, Customer Account consistency,
strict configured builds, and configured emulator journeys. The selected Menu
and `mobile_home/primary` content remained unchanged. Exactly one authorized
`mobile_home/gate8-operator-acceptance-v1` resource exists; it remains DRAFT and
unselected. Merged-source recovery and two development plus two staging Apply
runs attributed that shared-shop probe and performed zero provider writes.

The [Gate 8 completion handoff](GATE-8-COMPLETION-HANDOFF.md) remains its
historically accurate pre-merge record. It is not rewritten to conceal the
provider-contract defects found during configured acceptance or to claim later
merge, correction, and closure events.

## Implemented migration state

```text
accepted application-module architecture
:app -> :mobile-core boundary established
:synthetic -> :mobile-core independent conformance edge established
application-owned locale, market, media, Search, territory and protected-store inputs
exact Gürbakır application, Room, preference, Keystore, OAuth, App Link and Firebase identities preserved
application-owned Search/Wishlist/Account capabilities and primary navigation order
guarded recovery for unavailable shared routes
application-owned Firebase/local-default selection with provider-neutral :mobile-core
physical Firebase exclusion for :synthetic
application-selected bounded Shopify Menu discovery for Categories
application-selected bounded Shopify Home editorial content with finite native rendering
editorial-only Home LKG persistence; current commerce truth remains live Storefront-owned
synthetic Home remote source disabled and credential-free
explicit application/profile enrollment and scoped nonproduction configuration
bounded read-only/provider-write operator with receipt-bound recovery
development-only Trial second real application/store pilot
Trial v1 provider checkpoint followed by bounded Home v2 image/video contract
isolated Trial Shopify, Customer Account, Firebase, persistence and signing inputs
bounded playback attempt, HTTP budget, lifecycle/audio cancellation and v2 cache
no runtime merchant switch
no normative brand flavor dimension
```

## Unfinished continuation boundary

Closing Gate 8 does not finish Multi-Brand work. Future slices must be separately
planned from current source and architecture. They must preserve:

- Gürbakır `:app` as the first real application and validation brand;
- the nine-module dependency graph and application-owned composition model;
- migration-sensitive application, persistence, provider, OAuth, App Link, and
  Firebase identities;
- the `:synthetic` isolation/conformance role;
- bounded merchant-content authority with native executable behavior retained by Android; and
- honest separation between reusable contracts and concrete brand behavior.

The second merchant/store implementation now exists as a development-only Gate 9
candidate, but Gate 9 cumulative conformance is not closed. Trial cart scope/setup,
safe synthetic-customer acceptance, full player/two-app commerce acceptance,
PR-head CI, owner merge and merged-main CI remain open. Bounded Samsung update/
performance evidence and Trial root-last rollback rehearsal are recorded, not
substitutes for those remaining rows. Production
onboarding/configuration/signing/publication and P3-16 remain separately governed.

The repository-wide authority map remains [docs/README.md](../README.md).
