# Multi-Brand Documentation

Status: **Gates 0–6 complete; Gate 6 technically closed; Multi-Brand work remains unfinished**

## Purpose

This directory preserves accepted architecture reasoning, approved execution
plans, and Gate 1–6 implementation/evidence records for evolving the Gürbakır
application into a multi-brand Android monorepo. It is a navigation and status
index, not a duplicate architecture specification.

## Authority model

```text
implemented behavior -> current source/configuration/tests
accepted target -> ../architecture/*
durable rationale -> ../decisions/*
investigation/history -> research/*
approved gate execution -> plans/* or recorded owner-approved scope
historical gate outcome -> GATE-1 through GATE-6 completion handoffs
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
no second real merchant application
no runtime merchant switch
no normative brand flavor dimension
```

## Unfinished continuation boundary

Closing Gate 6 does not finish Multi-Brand work. Future gates must be separately
planned from current source and architecture. They must preserve:

- Gürbakır `:app` as the first real application and validation brand;
- the eight-module dependency graph and application-owned composition model;
- migration-sensitive application, persistence, provider, OAuth, App Link, and
  Firebase identities;
- the `:synthetic` isolation/conformance role; and
- honest separation between reusable contracts and concrete brand behavior.

A future real brand, production onboarding/configuration/signing/publication,
external provider mutation, and P3-16 are not authorized or proven by the
completed gates or by repository publication.

The repository-wide authority map remains [docs/README.md](../README.md).
