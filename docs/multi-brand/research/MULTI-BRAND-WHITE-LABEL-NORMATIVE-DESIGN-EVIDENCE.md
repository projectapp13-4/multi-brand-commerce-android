# MULTI-BRAND WHITE-LABEL NORMATIVE ARCHITECTURE DESIGN

> **Classification:** Accepted architecture-design investigation and decision evidence
>
> **Accepted architecture basis:** 2026-09-01
>
> **Authority:** This document preserves the reasoning that produced the accepted multi-brand decision. Gate 0 will distill concise operational rules into `docs/architecture/` and ADR-0004. Current source remains authoritative for implemented behavior.
>
> **Repository snapshot used by the investigation:** `main@75f4b57054e9ddd5dd1dcc209a293407ce5086d2`
>
> **Implementation status at acceptance:** architecture decision only; no multi-brand runtime/module migration had been performed.
>
> **Repository mutations in the architecture-design session:** none
>
> **External-system mutations:** none
>
> **Builds/tests executed in the architecture-design session:** none

The earlier forensic report was read completely and treated as high-value research evidence, not as an approved architecture. This design independently challenged that report and selected a different primary brand boundary.

---

# 1. Normative decision

The repository should become a **single multi-brand monorepo with one thin Android application module per brand and one shared native mobile application core**.

The brand is selected by **which Android application module is built**, not by runtime merchant switching and not by a `brand` product-flavor dimension.

Target shape:

```text
                       shared Android implementation
                                :mobile-core
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
                    ▼                ▼                ▼
             :foundation       :storefront        :account
                    │                                 │
                    └──────── other shared adapters ──┘
                              :checkout
                              :firebase

          ┌─────────────────────────┴────────────────────────┐
          │                                                  │
          ▼                                                  ▼
 :app  (Gürbakır application)                  :apps:synthetic
 applicationId / signing /                     conformance application
 Firebase / OAuth / manifest /
 resources / brand composition
```

Future production brands follow:

```text
:apps:<brand>
        └──> :mobile-core
```

A built APK/AAB represents exactly one brand. There is no runtime merchant selector.

## Brand application module ownership

Each brand application module owns things that define a separately installed Android application:

- application ID;
- signing/release identity;
- app name;
- launcher/adaptive icons;
- splash/platform theme resources;
- packaged fonts and brand-specific assets;
- manifest and App Links;
- Customer Account callback identity;
- Firebase/Google Services configuration when used;
- Storefront/environment configuration;
- focused runtime brand policies/capability bindings;
- genuinely brand-specific compiled dependencies or code.

## `:mobile-core` ownership

`:mobile-core` owns reusable application behavior:

- shared Compose feature implementation;
- typed navigation contracts and common graph mechanics;
- ViewModels/controllers/repositories currently located in `:app`;
- cart/search/wishlist/application orchestration;
- shared design-system consumption;
- common capability mechanics;
- common route recovery;
- common persistence implementation;
- integration-neutral application policy.

---

# 2. Why this differs from the forensic report

The forensic report correctly diagnosed Gürbakır-first composition, rejected repository forks/long-lived brand branches, identified useful Storefront/Account/Checkout/Hilt/navigation/design-token/Room/Keystore seams, and required second-brand proof.

Its main recommendation was one `:app` with `brand × environment × buildType` variants and brand source sets.

That solution is technically valid, but the normative design rejected it as the primary long-term brand boundary after applying Android ownership mechanics to the current repository.

Key distinction:

> **A brand is not merely a configuration dimension. It is an Android application/distribution identity.**

Package identity, signing, Firebase registration, OAuth callback, App Links, platform resources and potentially provider-specific dependencies already follow application ownership.

Therefore the stronger boundary is:

```text
brand application -> shared
shared -X-> brand application
```

rather than placing all brands inside one application plugin's flavor matrix.

---

# 3. Verified baseline

At decision time the repository was verified as:

```text
branch: main
HEAD: 75f4b57054e9ddd5dd1dcc209a293407ce5086d2
modules:
  :app
  :foundation
  :storefront
  :account
  :checkout
  :firebase
```

Current toolchain/source was inspected sufficiently to reason about AGP/Kotlin/Hilt/Compose/Room/Firebase/Shopify boundaries. No build/test result from the architecture session was promoted as runtime proof.

Current source is Gürbakır-first. The accepted architecture is a target migration state, not a claim that `:mobile-core` or other brand applications already exist.

---

# 4. Evidence authority

For implemented behavior:

1. current source/configuration at current HEAD;
2. current authoritative platform documentation where version-sensitive;
3. root `AGENTS.md`, `docs/README.md`, accepted ADRs/current handoffs for constraints/status;
4. historical Phase records for provenance/regression boundaries;
5. forensic report as research evidence;
6. reference APK as clean-room behavioral evidence only.

Target architecture documents must never be cited as proof that target modules already exist.

---

# 5. Current architecture reconstruction

Current module ownership was approximately:

```text
:app
 ├──> :foundation
 ├──> :storefront ──> :foundation
 ├──> :account    ──> :foundation
 ├──> :checkout
 └──> :firebase   ──> :foundation
```

`:app` owned both Android application assembly and most product feature/UI/orchestration code. This double responsibility is the primary boundary to change.

Current build dimension:

```text
environment:
  development
  staging

build types:
  debug
  release
```

Current Gürbakır non-production application identities:

```text
developmentRelease = com.gurbakir.mobile.dev
developmentDebug   = com.gurbakir.mobile.dev.debug
stagingRelease     = com.gurbakir.mobile.staging
stagingDebug       = com.gurbakir.mobile.staging.debug
```

Current app manifest owns Gürbakır application/theme/icon/deep-link identity. Hilt composition is concentrated in `:app`. Customer Account is structurally installed. `MainActivity` calls the Gürbakır application composable. Primary navigation and many routes are fixed. Home/Catalog embed merchant handles and TR/TRY assumptions. Provisioning scripts are Gürbakır-specific.

---

# 6. Adversarial assessment of the forensic report

| Earlier conclusion | Normative assessment |
|---|---|
| repository not genuinely white-label | CONFIRMED |
| preserve one monorepo | CONFIRMED |
| add brand flavor dimension | REJECTED AS NORMATIVE |
| retain environment dimension | CONFIRMED WITH MODIFICATION |
| use brand resources | CONFIRMED WITH MODIFICATION: application resources override shared library resources |
| optional brand modules later | REPLACED: every brand has a thin app module; extra modules only for substantial code/provider domains |
| shared -> brand forbidden | CONFIRMED AND STRENGTHENED |
| preserve Storefront/Account/Checkout seams | CONFIRMED |
| preserve typed navigation | CONFIRMED |
| preserve bounded remote policy semantics | CONFIRMED |
| synthetic second brand | CONFIRMED as second app module |
| Shopify Navigation/metaobjects/metafields direction | CONFIRMED |
| preserve Gürbakır persistence/external IDs | CONFIRMED |
| mass-rename `com.gurbakir.*` | REJECTED |
| one giant `BrandConfiguration`/experience seam | CONFIRMED ONLY PARTIALLY; use focused concerns |

---

# 7. Architecture alternatives

## A. One `:app` with brand product flavors/source sets

Strengths:

- lower initial migration;
- mature resource/source-set mechanics;
- quick second APK.

Weaknesses for this repository:

- application identity and reusable product remain in one large Gradle module;
- brand × environment × build-type graph grows;
- brand-specific dependencies complicate flavor-qualified configuration;
- Kotlin source sets are not arbitrary class override/plugin points;
- architectural isolation relies more on conventions than module dependency direction;
- Firebase-free/provider-different brands still share one application plugin surface.

**Verdict:** technically good fallback, strategically second-best.

## B. Thin application module per brand + `:mobile-core`

Strengths:

- maps directly to Android distribution identity;
- strongest dependency isolation;
- application ID/signing/App Links/OAuth/Firebase/resources remain together;
- provider-specific dependencies do not contaminate unrelated brands;
- no global cross-brand flavor matrix;
- Hilt regular multi-module composition fits naturally;
- future compiled brand customization has an explicit owner;
- shared-to-brand dependency can be structurally prohibited.

Costs:

- larger first migration;
- `:app` must shed reusable feature implementation;
- common app Gradle logic may later justify convention build logic;
- app-owned `BuildConfig` consumers need explicit shared inputs;
- tests/Room/resource ownership require migration planning.

**Verdict:** SELECTED.

## C. Aggressive feature-module architecture now

Rejected for initial migration. Extract one meaningful `:mobile-core` boundary first; split features only when independent lifecycle/dependency pressure exists.

## D. Separate repository per brand

Rejected because common security/Shopify/Android/Room/Account/Checkout/CI work would diverge.

## E. Long-lived brand branches

Rejected because product variation becomes permanent merge debt.

## F. Runtime multi-tenant APK

Rejected because no product evidence requires it and it conflicts with package identity, signing, Firebase, OAuth, App Links, platform resources and persistence isolation.

---

# 8. Normative target repository organization

Conceptual target:

```text
/
├── app/                         # Gürbakır Android application shell
│   └── src/
│       ├── main/
│       ├── development/
│       ├── staging/
│       └── variant-specific configuration/resources
│
├── apps/
│   └── synthetic/               # conformance application
│
├── mobile-core/                 # reusable application/features
│   └── src/main/
│       ├── common Compose app shell
│       ├── feature screens
│       ├── ViewModels/controllers
│       ├── typed routes/navigation mechanics
│       ├── local persistence
│       └── common DI
│
├── foundation/
├── storefront/
├── account/
├── checkout/
├── firebase/
├── config/
├── docs/
└── build-logic/                 # only when duplication actually warrants it
```

The exact `apps/synthetic` spelling is not architectural; the application-module boundary is.

---

# 9. Preserve current `:app` as Gürbakır shell

Do not immediately rename `:app` to `:apps:gurbakir`.

Reason: current build task names, CI commands, workflows and historical evidence already revolve around `:app`. The asymmetry is acceptable historical debt.

Normative interpretation:

```text
:app = Gürbakır application shell
:mobile-core = shared application implementation
```

Rename later only if concrete tooling/ownership benefit exceeds churn.

---

# 10. Target dependency graph

```text
:app
 ├──> :mobile-core
 ├──> :firebase             # where Gürbakır needs provider binding
 └──> future brand-only integration only when justified

:apps:synthetic
 └──> :mobile-core
     [Firebase-free conformance case]

:mobile-core
 ├──> :foundation
 ├──> :storefront
 ├──> :account
 ├──> :checkout
 └──> neutral integration contracts

:storefront -> :foundation
:account    -> :foundation
:firebase   -> :foundation
```

Shared modules must never depend on:

```text
:app
:apps:*
```

This must become machine-enforceable.

---

# 11. Current module disposition

## `:foundation`

Preserve. Own genuinely shared foundational concepts: design-token value types, configuration validation, logging/redaction/security helpers and small integration-neutral contracts. No brand implementations.

Do not mass-rename `com.gurbakir.foundation` for aesthetics.

## `:storefront`

Preserve and neutralize. Own GraphQL schema/operations, Apollo client, Shopify models/gateways/content queries. Remove hardcoded Gürbakır origin ownership while retaining restrictive validation.

## `:account`

Preserve as Shopify Customer Account implementation. Do not split merely because some brands may disable Account.

## `:checkout`

Preserve narrow adapter boundary. Do not rewrite Checkout Kit for white-labeling.

## `:firebase`

Preserve as provider adapter, but generic application policy must not equate remote policy with Firebase. A neutral policy contract can later be bound to Firebase for Gürbakır and local safe policy for Firebase-free brands.

## `:mobile-core`

New and justified. This is the only major module required by the initial architecture. It receives reusable portions of current `:app`.

---

# 12. Namespace strategy

Do not perform repository-wide namespace/package neutralization.

Internal `com.gurbakir.*` naming can remain historical naming debt initially. Application ID is external installed identity; dependency direction/runtime ownership are architectural. Namespace/package spelling alone is not a white-label proof.

During module separation, internal namespaces may be adjusted where technically required, but existing Gürbakır application IDs must remain exact.

---

# 13. Brand selection and composition

There is no `BrandManager`, mutable current-brand singleton or merchant selector.

Selection occurs at build invocation/application module:

```text
:app:*              -> Gürbakır
:apps:synthetic:*   -> synthetic conformance brand
:apps:<brand>:*     -> future brand
```

Environment remains an application-module variant concern.

At runtime, the application shell supplies focused configuration/policy bindings to `:mobile-core`.

Remote Shopify/Firebase data cannot change installed brand identity.

---

# 14. Avoid a giant `BrandExperience`

Current `BrandConfiguration` mixes display identity, locale, design tokens, assets, legal URLs, feature flags and analytics namespace. Do not simply expand it into a God Object.

Focused concerns may include conceptually:

```text
DesignSystemConfiguration
MarketPolicy
LocalizationPolicy
ApplicationCapabilities
PrimaryNavigationSpec
LegalContentPolicy
MediaOriginPolicy
RuntimeServiceConfiguration
PersistenceIdentity
```

These are ownership concepts; do not create speculative interfaces for every future integration. A common validator may aggregate configuration consistency.

---

# 15. Design system and resources

Preserve existing design-token semantics for light/dark colors, shapes, spacing, motion and typography metrics.

Current custom-font configuration is incomplete because runtime uses a fixed system font. Target ownership:

```text
brand application packaged font resource
        ↓
resolved native FontFamily
        ↓
shared CommerceTheme
```

Brand application module owns app name, launcher/adaptive icons, splash, packaged logo, fonts, platform theme and brand-only illustrations. Application resources may override stable shared resource slots where appropriate.

---

# 16. Build model

## Gürbakır

Preserve current four non-production combinations:

```text
development × debug
development × release
staging     × debug
staging     × release
```

Do not invent production for symmetry.

## Synthetic

Only create the matrix necessary for conformance, initially development debug/release.

## Future real brands

Each application module defines only environments that actually exist for that merchant. This avoids a global brand × environment × build-type cross product.

---

# 17. Application identity ownership

| Concern | Owner |
|---|---|
| applicationId | brand application module |
| namespace | module implementation detail |
| app name | brand resources |
| signing | brand release boundary |
| launcher/splash | brand resources |
| App Links | brand manifest + external domain state |
| Customer Account callback | brand/environment config + Shopify registration |
| Firebase app | brand/environment external registration |
| `google-services.json` | brand app/environment |
| Storefront client config | brand/environment |
| Play identity | brand release boundary |

Firebase's package-registration model reinforces the application boundary, but Firebase is not the reason brand flavors are invalid; it is an ownership alignment argument.

---

# 18. Runtime service configuration

Current `AppConfiguration` makes Customer Account too structurally mandatory for a reusable foundation.

Conceptually target:

```text
StorefrontConfiguration          required
CustomerAccountCapability        optional
RemotePolicyCapability           provider-specific or local
Telemetry                        only when real production requirement exists
```

Customer Account should eventually distinguish disabled vs configured semantics rather than encoding disabled as blank strings.

---

# 19. Capability architecture

Do not create one boolean for every button.

Initial justified application-level capability pressure:

```text
SEARCH
WISHLIST
CUSTOMER_ACCOUNT
```

Checkout remains shared commerce behavior for now. Loyalty/custom-backend/alternate-checkout/provider frameworks remain deferred until concrete requirements exist.

---

# 20. Navigation architecture

Typed Navigation Compose remains normative.

A brand supplies a finite ordered primary-navigation specification over known destinations. Shared validation ensures enabled capabilities, no duplicates, valid root and deterministic order.

Disabled features must be absent from normal navigation/actions, but route contracts may remain compiled to allow safe recovery from stale saved state/deep links. Recommended behavior:

```text
compiled typed route
        ↓
capability guard
        ↓
enabled -> destination
disabled -> bounded RouteRecovery
```

Do not turn Shopify Navigation into unrestricted native application navigation.

Future brand-specific compiled destinations should be introduced only when a real use case exists, through the smallest shared seam necessary.

---

# 21. UI variability rule

| Variation | Mechanism |
|---|---|
| scalar visual semantics | shared design tokens |
| packaged identity assets/fonts/icons/splash | brand application resources |
| merchant-editable commerce/editorial content | Shopify |
| finite reusable visual family | typed shared component variant |
| feature presence | application capability |
| navigation order | primary navigation spec |
| strongly brand-specific native behavior | compiled brand implementation through narrow seam when required |
| commerce/security correctness | shared invariant |

Tokens must not become a fake universal UI DSL.

Home should eventually support bounded Shopify-driven section composition. Product card variants may be finite when real repeated needs appear. Do not prebuild Product Detail/account plugin frameworks.

---

# 22. Market, locale, domain and URL policy

Current TR/TRY/Turkey assumptions appear across Home, address, Search, Wishlist and tracking. A focused `MarketPolicy` is therefore justified.

It should provide semantics actually consumed by current code (market/country/currency/default locale/supported locales/market-selection capability) without pretending to solve all international commerce/addressing now.

Current security policies are generally strong but sometimes own the wrong merchant value. Keep strict validation algorithms; move approved origins/domain values to application/brand configuration.

---

# 23. Non-UI variability

Support now where current pressure is real:

- Customer Account enabled/disabled;
- Wishlist enabled/disabled;
- Search enabled/disabled;
- market policy;
- tracking/carrier ownership.

Defer:

- loyalty framework;
- generic custom backend bus;
- pre-checkout plugin chain;
- analytics provider abstraction;
- notification provider abstraction;
- radically custom Product Detail extension registry.

When a concrete future provider appears, brand/provider code depends on a narrow shared domain contract; shared code does not branch on brand identity.

---

# 24. Shopify content boundary

Durable ownership:

```text
Storefront catalog
  -> products/variants/collections/prices/inventory/media

Shopify Navigation
  -> merchant category/discovery hierarchy

Metaobjects
  -> structured mobile pages/ordered native sections

Resource metafields
  -> Product/Collection mobile-specific metadata/overrides

Android
  -> finite native renderer, behavior, security and capability topology
```

Typed references are preferred over embedding merchant catalog handles when appropriate reference types exist.

Shopify must never control application ID, signing, Firebase project, OAuth credentials, Android permissions, arbitrary route/component classes, unrestricted hosts, arbitrary SDK/provider selection, arbitrary Compose trees, executable code, Room/Keystore identity or native security validation.

The exact mobile merchandising schema is intentionally deferred from the initial module migration.

---

# 25. Persistence and external identity safety

Multi-brand migration is not permission to orphan existing Gürbakır state.

Current migration-sensitive identities include:

```text
application IDs:
  com.gurbakir.mobile.dev.debug
  com.gurbakir.mobile.dev
  com.gurbakir.mobile.staging.debug
  com.gurbakir.mobile.staging

Room:
  gurbakir-local.db
  schema/migration chain

Customer Account:
  gurbakir_secure_customer_session_<environment>
  gurbakir.customer.session.<environment>.v1

Cart:
  gurbakir_secure_cart_<environment>
  gurbakir.cart.<environment>.v1
```

Update-policy storage identity is already generic and should be preserved.

Introduce focused persistence-identity input only where implementation actually needs it. For Gürbakır it must resolve to exact historical values. New brands get independent application sandboxes/identities.

Do **not** add `brandId` to every Room table merely because the monorepo is multi-brand.

Production application ID/signing/Firebase/callback/App Links/Play ownership remain unresolved P3-16 external inputs and must not be invented.

---

# 26. Synthetic second-brand proof

Create a synthetic brand as a second Android application module, not a production merchant.

It should deliberately differ from Gürbakır:

- different application identity;
- different app name/assets/splash/font/design tokens;
- non-TR locale/market;
- non-Gürbakır domain fixture;
- different primary navigation order;
- Customer Account disabled;
- Wishlist disabled;
- Search enabled in a different position;
- Firebase absent/local policy;
- different Home/catalog fixture.

The repository may claim multi-brand architecture only when:

1. existing Gürbakır identities still build;
2. synthetic debug builds;
3. synthetic minified release builds;
4. synthetic owns its resources/identity;
5. Account/Wishlist can be disabled cleanly;
6. non-TR market works without hidden Turkey assumptions;
7. synthetic requires no Firebase application config;
8. cross-brand URL policies reject each other's origins where appropriate;
9. shared source contains no synthetic-brand condition;
10. `:mobile-core` depends on neither application module;
11. Gürbakır Room/Keystore continuity remains verified.

Core acceptance statement:

> Adding the synthetic brand must not require teaching shared code the synthetic brand's name, domain, application ID or merchant catalog.

---

# 27. Test and CI distribution

`:mobile-core` should eventually own most reusable JVM/Compose tests for feature state, common route behavior, capability validation, market-policy consumption, shared UI, persistence and recovery.

Brand applications own tests for merged manifest, application ID, resource resolution, App Links, OAuth scheme, Firebase presence/absence, Hilt assembly and brand-specific configuration.

Keep explicit Gürbakır migration tests for Room schema/file and secure persistence identities.

Do not build every theoretical variant for every future brand on every PR. Use shared/static lanes plus targeted application-shell integration lanes. Synthetic minified release compilation is important because debug alone can miss resource/R8 assumptions.

---

# 28. Architectural guardrails

Enforce structurally:

- shared modules cannot depend on `:app`/`:apps:*`;
- merchant domains cannot appear in shared production runtime outside approved configuration/fixtures;
- production Product/Collection handles cannot be embedded as shared merchant content;
- exact Gürbakır application IDs remain asserted;
- Firebase package matching remains strict where enabled;
- Firebase-free applications do not require provider initialization;
- capability/navigation configuration is internally consistent;
- cross-brand URL policy rejects foreign owned origins;
- synthetic release build remains healthy.

Do not use crude repository-wide blacklists for generic merchant phrases or the word `gurbakir`, because historical docs/packages/persistence identities legitimately contain it.

---

# 29. Documentation authority after migration

Gate 0 should establish:

```text
AGENTS.md
  -> durable repo-wide invariants and reading pointers

docs/README.md
  -> documentation authority/navigation map

docs/decisions/ADR-0004-...
  -> why application-module architecture was selected

docs/architecture/MULTI-BRAND-ARCHITECTURE.md
  -> concise canonical target architecture

docs/architecture/BRAND-BOUNDARIES.md
  -> ownership decision rules

docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md
  -> migration-safety identity register

docs/architecture/BRAND-ONBOARDING.md
  -> future brand onboarding contract

docs/multi-brand/research/*
  -> research/design evidence

docs/multi-brand/plans/*
  -> gate execution plans
```

Historical Phase documents remain historically accurate and must not be rewritten to imply the project was always multi-brand.

---

# 30. Migration gates

These are architectural gates, not a substitute for later source-level plans.

## Gate 0 — Normative documentation

No runtime change. Establish ADR, canonical architecture, brand boundaries, legacy identity register, onboarding/authority structure.

## Gate 1 — Extract shared application boundary

Create `:mobile-core`; move only genuinely reusable current `:app` implementation; keep `:app` as Gürbakır shell; refactor only app-owned `BuildConfig` accesses required for extraction. Do not redesign navigation/content/capabilities/persistence identity in the same gate.

## Gate 2 — Synthetic application

Add second application module and only seams necessary for materially different conformance application to compile.

## Gate 3 — Identity/domain/market/persistence inputs

Remove shared merchant/domain/market assumptions while preserving exact Gürbakır historical values.

## Gate 4 — Capability/navigation composition

Make Customer Account/Wishlist/Search optional and primary navigation configurable with safe disabled-route recovery.

## Gate 5 — Firebase provider isolation

Remove Firebase-specific policy types from generic app policy; bind Gürbakır to Firebase where configured and synthetic to local-safe provider.

## Gate 6 — Shopify Navigation discovery migration

Replace Android-owned merchant hierarchy with bounded Storefront Menu data.

## Gate 7 — Bounded mobile Home content

Design/implement metaobject/metafield schema, finite renderer, validation, cache/LKG and packaged fallback.

## Gate 8 — Provisioning/control plane

Only after schema stability; Admin credentials never enter Android.

## Gate 9 — Final CI/documentation canonicalization

Make conformance/architecture checks durable and required.

---

# 31. Explicit non-goals

Do not use this migration to:

- rename every `com.gurbakir.*` package;
- rename `gurbakir-local.db`;
- rename secure Customer Account/cart preferences/aliases;
- add `brandId` to every Room table;
- rewrite Storefront/Apollo;
- rewrite Customer Account OAuth;
- replace Checkout Kit;
- replace Hilt;
- replace typed Navigation Compose;
- create feature modules for ceremony;
- create generic plugin framework;
- create arbitrary JSON-driven Compose;
- create runtime merchant switching;
- prebuild loyalty/backend/analytics abstractions;
- define fake production identities;
- create permanent brand branches;
- create one repository per merchant;
- rewrite historically correct Gürbakır documents.

---

# 32. Deferred / YAGNI decisions

Safely deferred until evidence exists:

- permanent production Gürbakır application ID;
- production signing/Play ownership;
- production Firebase decision;
- exact first real second-brand requirements;
- future application-ID naming convention;
- full international market/address model;
- remote wishlist/search providers;
- loyalty/custom backend/pre-checkout provider contracts;
- analytics/notification providers;
- radically custom Product Detail seam;
- generic brand destination registry;
- final Shopify Home metaobject schema;
- Admin provisioning implementation;
- whether account-disabled binaries should physically exclude `:account`;
- when/if common application Gradle convention plugin is justified.

None blocks the selected architecture.

---

# 33. Pre-mortem / failure analysis

The architecture was actively tested against realistic failure modes.

### Gürbakır application ID accidentally changes

Prevent with explicit shell ownership and CI assertions; recover before artifact distribution.

### Wrong Firebase project/package bundled

Retain exact client/package/environment validation; block artifact on mismatch.

### OAuth callback mismatch

Keep callback application/environment owned and fail closed; validate against approved registration.

### App Link host leaks across brands

Keep manifests/domain policies application-owned; test merged manifest/cross-domain rejection.

### Brand resources leak

Platform identity resources live in application modules; assert resolution per app.

### `:mobile-core` extraction breaks Hilt

Ensure application module has shared Hilt modules/classes transitively; prove debug/release compilation.

### Disabled Account still reachable

Capability-aware navigation/actions plus route guards and bounded recovery.

### Account optionalization destroys Gürbakır sessions

Exact existing secure preference/Keystore identities remain unchanged and tested.

### Room extraction creates empty DB

Keep `gurbakir-local.db` exact and preserve migration chain/schema evidence.

### Synthetic brand gives false confidence

Require Account off, Wishlist off, Firebase absent, non-TR market, different nav/resources/domain/content.

### Future custom integration produces `if (brand)`

Brand/provider code stays on application side; introduce only narrow shared domain seam when requirement exists.

### Shopify malformed content destabilizes Home

When mobile content arrives, use finite schema, strict validation, last-known-good and packaged fallback.

### Application modules duplicate Gradle logic

Only after real duplication, extract common Android application convention build logic.

### Historical `:app` name confuses future agents

Canonical docs explicitly state `:app` is Gürbakır shell and `:mobile-core` is shared; rename only if later concrete benefit warrants it.

No pre-mortem result required changing the selected architecture.

---

# 34. Final normative architecture

```text
ONE MONOREPO

ONE ANDROID APPLICATION MODULE PER BRAND

EXISTING :app REMAINS THE GÜRBAKIR APPLICATION SHELL

NEW :mobile-core CONTAINS REUSABLE APPLICATION FEATURES

ENVIRONMENT REMAINS A FLAVOR DIMENSION INSIDE EACH REAL BRAND APP

NO BRAND FLAVOR DIMENSION

NO RUNTIME MERCHANT SWITCHING

SHARED MODULES MUST NEVER DEPEND ON BRAND APPLICATION MODULES

BRAND APPLICATION MODULES OWN:
    application identity
    signing
    manifest
    platform resources
    Firebase configuration
    OAuth/App Links
    environment configuration
    brand-specific compiled dependencies
    focused runtime policy bindings

MOBILE-CORE OWNS:
    common Compose application
    navigation mechanics
    features
    ViewModels/controllers
    shared local data behavior
    capability mechanics
    common commerce UX

SHOPIFY OWNS:
    catalog
    merchant navigation/discovery
    merchandising/editorial content
    bounded resource-specific metadata

SHARED CORE OWNS:
    commerce correctness
    security validation
    typed contracts
    recovery
    Storefront/Account/Checkout integrations
    persistence mechanics

GÜRBAKIR LEGACY IDENTITIES REMAIN EXACT
WHEN THEY ARE INSTALL/PERSISTENCE/EXTERNAL CONTRACTS
```

This makes Gürbakır the first implementation of the product without making Gürbakır the architecture.

---

# IMPLEMENTATION HANDOFF

## Normative architecture

Use a single monorepo with one thin Android application module per brand and shared `:mobile-core`. Keep current `:app` as Gürbakır shell. Do not introduce a brand flavor dimension. Application module selects brand; environment flavors remain inside brand applications.

## Invariants to preserve

```text
main baseline behavior unless a gate explicitly changes it
current Gürbakır development/staging application IDs exactly
Room filename gurbakir-local.db
Room schema/migration continuity
Customer Account secure preference names and Keystore aliases
cart secure preference names and Keystore aliases
existing restrictive URL/security behavior
typed Navigation Compose
Hilt architecture
Storefront / Customer Account separation
CheckoutAdapter boundary
bounded remote-policy semantics
historical Phase documentation
```

Shared modules must not depend on `:app` or future `:apps:<brand>`.

## First implementation sequence

Gate 0 establishes repository-local architecture documentation/source of truth only.

Gate 1 later creates `:mobile-core`, extracts reusable current `:app` implementation, leaves `:app` as thin Gürbakır shell and separates only the app-owned values required for that extraction. It must not simultaneously redesign capabilities/navigation/Shopify content/persistence identity/Firebase/provider architecture.

## Verification principle

No future agent may claim preservation without executing relevant builds/tests/diff checks. Architecture documentation itself is not runtime proof.
