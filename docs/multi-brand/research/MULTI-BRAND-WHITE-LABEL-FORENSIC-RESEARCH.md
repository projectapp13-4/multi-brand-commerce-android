# Multi-Brand / White-Label Architecture & Forensics Research Report

> **Classification:** Research evidence / historical architecture investigation
>
> **Authority:** This document is evidence, not the canonical accepted architecture. The later normative design supersedes its architecture recommendation where they conflict. Gate 0 will distill canonical rules into `docs/architecture/` and ADR-0004.
>
> **Repository:** `private historical repository`
>
> **Verified research baseline:** `main @ 75f4b57054e9ddd5dd1dcc209a293407ce5086d2`
>
> **Research date:** 2026-09-01
>
> **Mutations performed in the research session:** none
>
> **Builds/tests executed in the research session:** none

This repository-local document preserves the substantive findings and reasoning of the original multi-brand forensic research so future engineering agents do not need the originating chat history. Where this report recommends a `brand` flavor/source-set architecture, that recommendation is intentionally retained as historical research evidence; the later normative design independently reassessed it and selected one Android application module per brand instead.

---

## Evidence terminology

- **VERIFIED CURRENT FACT** — observed directly in current source/configuration at the research baseline or current authoritative platform documentation.
- **HISTORICAL FACT** — recorded by project handoffs/reports but not independently revalidated as current runtime behavior in that research session.
- **EVIDENCE-BACKED INFERENCE** — strongly supported by verified source/configuration without runtime execution.
- **HYPOTHESIS** — plausible explanation requiring further evidence.
- **UNKNOWN / NEEDS DECISION** — insufficient evidence or explicit product/ownership decision required.

Evidence strengths used by the research: **HIGH**, **MEDIUM**, **LOW**.

---

# 1. Executive verdict

The repository was not yet a genuine multi-brand/white-label application foundation at the verified baseline. It was a production-oriented native Android Shopify application with several strong generic seams, but application composition remained Gürbakır-first.

The problem was broader than branding. Gürbakır/store assumptions appeared in at least five ownership classes:

1. build/application identity;
2. merchant/catalog/runtime content;
3. navigation and feature topology;
4. domain, market, locale and integration policy;
5. migration-sensitive persisted identities.

The existing system was nevertheless a strong starting point and should be generalized incrementally rather than rewritten. Reusable assets already included typed `BrandConfiguration`, design tokens, `AppConfiguration`, Storefront/Customer Account/Checkout interfaces, Hilt DI, typed Navigation Compose routes, restrictive URL policies, bounded Firebase Remote Config, Room schemas/migrations and explicit security/validation contracts.

### Historical research recommendation

The research initially recommended a hybrid monorepo with:

```text
app/src/main                  brand-neutral application/features
app/src/gurbakir              Gürbakır brand implementation/resources
app/src/syntheticbrand        second-brand conformance fixture
optional brand modules        only for substantial custom code/dependencies
```

and proposed adding a `brand` flavor dimension beside the existing `environment` dimension.

The durable dependency principle was already correct:

```text
shared modules/source MUST NOT depend on a concrete brand
brand implementations MAY depend on shared contracts/features
final application assembly MAY select the brand implementation
```

The later normative design retained the monorepo, dependency direction, synthetic proof and migration-safety principles, but rejected the brand-flavor boundary in favor of one Android application module per brand plus shared `:mobile-core`.

---

# 2. Verified baseline and evidence authority

The research reverified:

```text
default branch: main
HEAD: 75f4b57054e9ddd5dd1dcc209a293407ce5086d2
root project: gurbakir-android
modules:
  :app
  :foundation
  :storefront
  :account
  :checkout
  :firebase
```

Current source/configuration was treated as stronger evidence for implemented behavior than Phase 1/2/3 historical reports. Root `AGENTS.md`, `docs/README.md`, accepted ADRs and current handoffs remained important for project intent, constraints and provenance.

An important documentation tension was identified: root documentation still described the repository as the Gürbakır Android application while other project material described reusable/brand-neutral goals. The research therefore treated multi-brand generalization as a new architectural migration, not as an already-completed property.

No Gradle build, emulator, physical-device test, Shopify mutation, Firebase mutation or reference-service contact was performed as part of this research.

---

# 3. Reference APK conclusion

Curated reference evidence showed one distributed Android identity for third-party reference (`com.appcent.kristalkutu`). That proves only that the inspected artifact is one brand build; it does not prove whether the original source project was single-brand or white-label.

Reusable clean-room lessons were data-model patterns rather than implementation details:

- Shopify Navigation (`menu(handle:)`) was used for category/discovery hierarchy.
- Home/mobile content used Shopify metaobjects.
- Metaobject/metafield fields used typed Product/Collection/media references.

Valid lesson:

```text
Shopify can act as a bounded merchant/mobile content source while Android owns native renderers and behavior.
```

Do not copy reference branding, assets, proprietary schema names, hidden/decompiled implementation, credentials/endpoints or arbitrary remote-layout systems.

---

# 4. Current architecture map

At the research baseline the module relationship was approximately:

```text
:app
 ├──> :foundation
 ├──> :storefront ──> :foundation
 ├──> :account    ──> :foundation
 ├──> :checkout
 └──> :firebase   ──> :foundation
```

`:app` simultaneously owned Android application assembly and most feature/UI/orchestration code.

`BuildConfigurationSource` directly constructed one `AppConfiguration` using `GurbakirBrand.configuration`; `MainActivity`/the application composable were also directly Gürbakır-named/composed. There was no real brand selection mechanism.

The existing build dimension was:

```text
environment:
  development
  staging

build types:
  debug
  release
```

No production environment/identity was represented in tracked source.

---

# 5. Existing reusable seams

The research classified important existing seams as follows.

| Seam | Research verdict |
|---|---|
| `BrandConfiguration` | useful typed contract; preserve but split/extend ownership |
| `BrandDesignTokens` | genuinely reusable; preserve |
| typography font resource-name field | contract exists but runtime support incomplete |
| `BrandAssetReferences` | insufficiently wired for real packaged application identity |
| `enabledFeatures` | typed field exists but did not constitute a real capability system |
| `AppConfiguration` | strong typed config; Customer Account structurally too mandatory |
| Storefront gateway interfaces | preserve |
| Customer Account gateways | preserve; capability-gate composition later |
| `CheckoutAdapter` | strong SDK boundary; preserve |
| `CheckoutUrlPolicy` | good example of restrictive shared policy receiving configured origin |
| Hilt | preserve for composition/extension seams |
| typed Navigation Compose | preserve route typing; generalize topology |
| bounded Firebase Remote Config | preserve bounded-policy property |
| Room repositories/schemas/migrations | preserve and protect migration identity |
| validation/fail-closed configuration | preserve |
| Home/Catalog typed config structures | shape useful, ownership wrong for merchant content |

A representative evidence-discipline example: typography contained a font resource-name concept, but runtime `CommerceTheme` used fixed `FontFamily.SansSerif`; therefore custom font support was **present in contract but not fully wired**, not an implemented capability.

---

# 6. Complete brand-leakage findings

The research grouped findings by architectural significance rather than listing every `com.gurbakir.*` package occurrence.

High-value findings included:

| Classification | Current anchor/behavior | Why it matters | Historical target recommendation |
|---|---|---|---|
| `BUILD_IDENTITY` | `app/build.gradle.kts` app IDs/namespaces/app names | one brand encoded in assembly | brand-owned build boundary |
| `BUILD_IDENTITY` | `app/src/main/AndroidManifest.xml` Gürbakır application/theme/icons | platform identity in shared main | brand-owned platform resources |
| `DOMAIN_OR_URL_POLICY_LEAK` | `gurbakir.com` App Links | other brands cannot own deep links | brand URL/domain config |
| `UI_OR_NAVIGATION_VARIATION_GAP` | application defaults to `GurbakirBrand` | shared composition knows one brand | selectable composition |
| `UI_OR_NAVIGATION_VARIATION_GAP` | fixed HOME/CATEGORIES/SEARCH/WISHLIST/ACCOUNT | topology cannot vary | typed capability/navigation spec |
| `FEATURE_OR_CAPABILITY_LEAK` | routes/features structurally always present | every brand assumed same product capabilities | coarse capability model |
| `SHARED_CODE_BRAND_LEAK` | `BuildConfigurationSource` directly selects Gürbakır | no true brand composition seam | brand-selected provider/composition |
| `RUNTIME_CONTENT_LEAK` | Home fixed collection/product handles | merchant content embedded in APK | Shopify content/navigation |
| `RUNTIME_CONTENT_LEAK` | Catalog fixed collection handles | catalog hierarchy embedded in APK | Shopify Navigation |
| `DOMAIN_OR_URL_POLICY_LEAK` | media/legal/deep-link code knows `gurbakir.com` | security policy has wrong owner | inject approved owned origins |
| `MARKET_OR_LOCALE_POLICY_LEAK` | TR/TRY and Turkey-specific assumptions across Home/address/search/wishlist/tracking | shared behavior assumes one market | focused market policies |
| `FEATURE_OR_CAPABILITY_LEAK` | Customer Account structurally mandatory | future account-free brand difficult | optional semantic Account capability |
| `TOOLING_OR_PROVISIONING_LEAK` | provisioning script refuses non-Gürbakır storefront | onboarding tooling is merchant-specific | generic/versioned tooling later |
| `PERSISTENCE_OR_MIGRATION_SENSITIVE` | `gurbakir-local.db`, secure prefs/Keystore aliases | rename can orphan existing state | preserve exact Gürbakır identities |
| `NAMING_ONLY` | many `com.gurbakir.*` internal packages | aesthetic naming, not necessarily architecture | do not mass-rename |

The research explicitly rejected a simplistic “search and delete every Gürbakır string” cleanup. Ownership, dependency direction and persistence/external identity are more important than neutral-looking names.

---

# 7. UI and asset variation analysis

Recommended ownership rule from the research:

| Variation | Mechanism |
|---|---|
| colors, typography metrics, radii, spacing, motion | design tokens |
| launcher/adaptive icon, splash, fonts, packaged logo/app name/platform theme | Android packaged brand resources |
| merchant-editable campaigns/banners/product-collection ordering/editorial content | Shopify |
| finite repeated visual family | typed shared component variant |
| known feature presence/absence | capability configuration |
| genuinely brand-specific native component/screen/integration | brand-owned compiled extension |
| commerce/security/domain correctness | shared core |

The research warned against pretending tokens can represent arbitrary brand UI. Strongly different Home, Product Detail or account experiences may eventually require narrow compiled seams, but they should not be prebuilt speculatively.

---

# 8. Capability, behavior and integration variation

The correct abstraction level was identified as coarse semantic capabilities rather than hundreds of booleans.

Immediate pressure existed for:

- Customer Account optionality;
- Search enable/disable;
- Wishlist enable/disable;
- navigation ordering/topology;
- market/country policy;
- Turkey carrier/tracking policy ownership.

YAGNI/deferred:

- universal loyalty framework;
- generic custom-backend bus;
- speculative pre-checkout plugin chain;
- analytics provider framework before a real approved provider;
- notification provider framework before a real requirement.

Firebase Remote Config was considered a good bounded pattern only because it exposed a small allowlist of known application policy. It should not become a screen/navigation/component DSL.

---

# 9. Dependency-direction analysis

The research's core invariant:

```text
brand -> shared        allowed
shared -> brand        forbidden
```

The final Android assembly is a composition root and may depend on selected brand/shared implementations; shared libraries/features must not import concrete brand code.

Module-specific guidance:

- `foundation`: brand-neutral contracts/tokens/security helpers; package name need not be renamed.
- `storefront`: Shopify Storefront implementation; should not know Gürbakır domain/catalog/UI/topology.
- `account`: reusable Customer Account implementation; should not be mandatory for every brand.
- `checkout`: already near ideal narrow SDK boundary.
- `firebase`: bounded provider adapter; must not imply every brand uses Firebase.
- `app`: mixed application assembly, reusable features and Gürbakır composition; primary ownership issue.

---

# 10. Build/source-set/module alternatives considered in the forensic research

### Option 1 — one app module with brand flavors/source sets

Advantages: smallest migration, natural Android resource overlays, shared feature code remains in `app/src/main`, simple first second-brand compile.

Disadvantages: large `:app`, brand-specific dependencies can pollute it, weaker structural enforcement than modules.

Research assessment: good initial foundation.

### Option 2 — generic app/core plus brand modules immediately

Advantages: strongest Gradle isolation and explicit dependency graph.

Research concern: because most feature UI lived in `:app`, extracting contracts/shared UI first might be disruptive or create ownership cycles.

Research assessment: architecturally clean but initially too disruptive.

### Option 3 — hybrid brand source sets plus optional modules

Research recommendation: source sets for identity/resources/config/composition; brand modules only for substantial custom code/dependencies.

### Option 4 — separate repo per brand

Rejected because common security/Shopify/Android/dependency/account/checkout/Room/CI fixes would diverge and duplicate.

### Option 5 — long-lived brand branches

Rejected because product variation would become permanent merge debt.

The later normative design independently revisited this decision and selected a thin application module per brand plus `:mobile-core`, while preserving the monorepo and dependency principles.

---

# 11. Historical recommended target repository architecture

The forensic report originally proposed:

```text
app/src/main            neutral application/features
app/src/gurbakir        Gürbakır composition/resources
app/src/syntheticbrand  adversarial second-brand fixture
foundation
storefront
account
checkout
firebase
optional brand extensions
tools/shopify-mobile-schema
docs/architecture
```

A separate mobile-content module was explicitly considered unnecessary until content parsing/cache/schema complexity justified it.

This layout is retained here only as research history; the accepted normative design changes the application boundary.

---

# 12. Historical target dependency graph

The report proposed shared modules independent of concrete brand code and optional future brand-extension modules depending inward on shared contracts. CI should make the prohibited shared-to-brand arrow enforceable.

---

# 13. Configuration/data/content ownership conclusions

Durable ownership conclusions that survived later review:

- application/distribution identity is brand/application owned;
- reusable commerce/security behavior is shared;
- merchant catalog/navigation/merchandising should be Shopify-owned where appropriate;
- externally registered Firebase/OAuth/App Link identities are application/environment concerns;
- persisted Gürbakır identity is compatibility state, not branding cleanup fodder;
- arbitrary remote native code/UI is prohibited.

---

# 14. Shopify Mobile Content architecture direction

The research recommended a bounded native CMS model:

```text
Shopify Navigation
  -> merchant/category/discovery hierarchy

Metaobjects
  -> structured mobile pages / ordered native sections

Resource metafields
  -> Product/Collection-specific mobile overrides

Android
  -> finite native section types, validation, behavior and security
```

Typed Shopify resource references should be preferred over plain production catalog handles when appropriate reference types exist.

Home was identified as the strongest candidate for Shopify-managed native composition because merchandising changes independently of Play releases.

The runtime must validate unknown/malformed section types and use safe fallback/last-known-good behavior; Shopify must never control arbitrary native class names, route classes, executable code, unrestricted hosts or SDK selection.

---

# 15. Shopify provisioning/control-plane direction

The research recommended eventually provisioning the mobile schema programmatically and idempotently through Shopify Admin GraphQL rather than manually creating many fields for every merchant.

Principles:

- version schema;
- validate current definitions;
- create/update idempotently;
- migrate deliberately;
- keep merchant-editable values in Shopify Admin;
- Admin credentials never enter the Android APK;
- start tooling in a monorepo tool only when schema is approved, and split later only if independent lifecycle warrants it.

This tooling was not to be implemented before the content schema itself became an approved contract.

---

# 16. Persistence and external-identity risk

Migration-sensitive areas identified included:

- existing non-production application IDs;
- Firebase Android registrations;
- Customer Account OAuth callback identity;
- App Links/domain association;
- Room physical DB filename and migration chain;
- Search/Wishlist persisted data;
- secure Customer Account preferences and Keystore aliases;
- secure cart preferences and Keystore aliases;
- update-policy persistence;
- signing/Play identity where later established.

The report explicitly recommended leaving `com.gurbakir.*` internal naming alone unless an independent benefit justified risky churn.

---

# 17. Second-brand conformance design

The project should not claim multi-brand success merely because a second color theme compiles.

A synthetic/conformance brand should differ materially in dimensions such as:

- application identity;
- assets/fonts/colors;
- domain;
- locale/market;
- navigation order/topology;
- enabled capabilities;
- Account state;
- Wishlist state;
- Firebase state;
- Home/content fixtures.

The crucial proof condition:

> adding the second brand must not require teaching shared code the brand's concrete name, domain, application ID or merchant catalog.

---

# 18. CI and architecture guardrails

The research recommended structural rather than brittle word-list enforcement:

- shared modules/source may not depend on concrete brand implementations;
- literal merchant domains must not live in shared runtime except approved fixtures/configuration;
- production Product/Collection handles must not be embedded as shared merchant content;
- brand configuration/resources must be complete for compiled application variants;
- synthetic second-brand debug/release should compile;
- capability/navigation consistency should be testable;
- security/status CI should remain intact.

Generic phrases such as “Özel Ürünlerimiz” should not be globally banned; architectural ownership is the invariant.

---

# 19. Historical migration sequence recommendation

The forensic research originally proposed phased migration rather than a single rewrite:

1. document architecture/identity invariants;
2. introduce brand composition/build boundary;
3. introduce second-brand proof;
4. move domain/market/content leaks behind correct ownership;
5. make capabilities/navigation real;
6. isolate provider integrations as needed;
7. migrate Shopify Navigation/Home content separately;
8. add schema provisioning only after schema approval;
9. finalize CI/documentation guardrails.

The later normative design revised the first implementation boundary to `:mobile-core` extraction plus thin brand application modules.

---

# 20. Documentation/source-of-truth plan

The research recommended preserving history rather than rewriting it. A future no-history agent should be able to distinguish:

- current source = implemented fact;
- canonical architecture = accepted target;
- ADRs = why decisions were made;
- research = evidence/history;
- gate plans = how approved migrations are executed;
- Phase/Product Quality/reference docs = scoped historical/current evidence according to their original purpose.

---

# 21. Explicit non-goals

The research rejected using multi-brand work as an excuse to:

- mass-rename `com.gurbakir.*`;
- rename Room/Keystore/session/cart identities for aesthetics;
- rewrite Storefront/Customer Account/Checkout without evidence;
- create arbitrary plugin systems;
- build arbitrary JSON-driven Compose;
- create runtime merchant switching;
- prebuild loyalty/backend/analytics abstractions;
- make one permanent Git branch/repository per merchant.

---

# 22. Unresolved questions / deferred evidence

Not required to settle the initial architecture:

- first real second merchant and exact requirements;
- final production Gürbakır package/signing/Play ownership;
- production Firebase choice;
- exact future brand application-ID convention;
- international address model;
- alternate wishlist/search providers;
- loyalty/custom backend/provider requirements;
- analytics/notification providers;
- exact Home metaobject schema;
- whether provisioning later deserves its own repository.

These were deliberately subject to YAGNI.

---

# 23. Final research recommendation at the time

At the time of this forensic report, the recommended first architecture direction was:

```text
one monorepo
brand flavor + existing environment flavor
brand source sets/resource overlays
shared -> concrete brand prohibited
synthetic second brand required
Shopify owns merchant content where appropriate
migration-sensitive Gürbakır identities preserved
```

This conclusion was explicitly a research recommendation rather than an approved design. The subsequent independent architecture-design investigation challenged it and selected one Android application module per brand with shared `:mobile-core` instead.

---

# 24. Evidence anchors inspected

The research inspected or reasoned from current repository areas including:

```text
settings.gradle.kts
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/gurbakir/mobile/MainActivity.kt
app/src/main/kotlin/com/gurbakir/mobile/GurbakirApplication.kt
app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt
app/src/main/kotlin/com/gurbakir/mobile/config/BuildConfigurationSource.kt
app/src/main/kotlin/com/gurbakir/mobile/brand/GurbakirBrand.kt
app/src/main/kotlin/com/gurbakir/mobile/di/*
app/src/main/kotlin/com/gurbakir/mobile/home/*
app/src/main/kotlin/com/gurbakir/mobile/catalog/*
app/src/main/kotlin/com/gurbakir/mobile/legal/*
app/src/main/kotlin/com/gurbakir/mobile/order/*
app/src/main/kotlin/com/gurbakir/mobile/search/*
app/src/main/kotlin/com/gurbakir/mobile/wishlist/*
foundation/*
storefront/*
account/*
checkout/*
firebase/*
scripts/Provision-CustomerAccountDiscovery.ps1
.github/workflows/android-foundation.yml
AGENTS.md
README.md
docs/README.md
docs/preparation/*
docs/phase3/P3-16-HANDOFF.md
docs/product-quality/*
private forensic corpus (not present in the public tree)
```

External platform verification was used where needed for Android build variants/source sets, Hilt modules, Firebase package/config semantics, Shopify Customer Account callbacks, Storefront Menu, Metaobjects and resource references.

---

# HANDOFF FROM THE FORENSIC RESEARCH

**Verified research baseline:** `main @ 75f4b57054e9ddd5dd1dcc209a293407ce5086d2`.

**Durable conclusions:** current source was Gürbakır-first despite useful generic contracts; do not rewrite the application; generalize ownership incrementally; shared code must not depend on concrete brands; migration-sensitive Gürbakır application/persistence/external identities must not be casually renamed; Shopify Navigation/metaobjects/metafields are appropriate bounded merchant-content mechanisms; a materially different second brand is necessary proof.

**Superseded research recommendation:** this report initially preferred brand flavors/source sets. The accepted normative design later rejected that as the primary brand boundary and selected separate thin Android application modules plus shared `:mobile-core`.
