# Multi-Brand Documentation

Status: **Gates 0–7 complete; Gate 8 implementation in progress; Multi-Brand work remains unfinished**

## Purpose

This directory preserves accepted architecture reasoning, approved execution
plans, and Gate 1–7 implementation/evidence records for evolving the Gürbakır
application into a multi-brand Android monorepo. It is a navigation and status
index, not a duplicate architecture specification.

## Authority model

```text
implemented behavior -> current source/configuration/tests
accepted target -> ../architecture/*
durable rationale -> ../decisions/*
investigation/history -> research/*
approved gate execution -> plans/* or recorded owner-approved scope
historical gate outcome -> GATE-1 through GATE-7 completion handoffs
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
| 8 | [Plan](plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.md) | Implementation in progress |

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
no second real merchant application
no runtime merchant switch
no normative brand flavor dimension
```

## Unfinished continuation boundary

Closing Gate 7 does not finish Multi-Brand work. Future gates must be separately
planned from current source and architecture. They must preserve:

- Gürbakır `:app` as the first real application and validation brand;
- the eight-module dependency graph and application-owned composition model;
- migration-sensitive application, persistence, provider, OAuth, App Link, and
  Firebase identities;
- the `:synthetic` isolation/conformance role;
- bounded merchant-content authority with native executable behavior retained by Android; and
- honest separation between reusable contracts and concrete brand behavior.

Gate 8 provisioning/onboarding implementation is in progress under its approved
digest-bound plan. A second real merchant/store pilot, Gate 9 cumulative
conformance, production onboarding/configuration/signing/publication and P3-16
remain unstarted or separately governed unless later evidence and approval
establish otherwise.

The repository-wide authority map remains [docs/README.md](../README.md).
