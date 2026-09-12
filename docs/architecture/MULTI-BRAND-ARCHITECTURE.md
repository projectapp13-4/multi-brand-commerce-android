# Multi-Brand Architecture

Status: **Canonical architecture; Gates 1–5 implemented and closed; Gate 6 planning is next and not started**

This document defines the repository's accepted multi-brand architecture. Current source remains authoritative for what is implemented today.

Gate 0 established the design in documentation. Gate 1 implemented and verified the `:app -> :mobile-core` base. Gate 2 implemented and verified a second, non-production application edge: logical project `:synthetic` maps to physical directory `apps/synthetic` and independently composes `:mobile-core`. Gate 3 moved the remaining fixed locale, market, territory-input, merchant-media and protected-persistence inputs to application composition while preserving Gürbakır compatibility state. Gate 4 implemented and verified application-owned Search/Wishlist/Customer Account capability presence and primary-navigation ordering. Gate 5 implemented and verified application-owned Firebase/provider selection while preserving a provider-neutral `:mobile-core` and strengthening physical Firebase-exclusion proof for `:synthetic`. Gürbakır remains the first real validation brand; no additional real merchant application exists. See the [Gate 5 completion handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md) for its preserved historical pre-merge implementation/evidence record.

## Context and development line

Gürbakır was the first real implementation and validation brand. Its history, names and current composition are legitimate current facts, but Gürbakır is not the reusable architecture.

This repository remains one monorepo. `main` is the canonical shared development line; brands are not long-lived Git branches or repository forks. A built Android application represents exactly one brand.

## Accepted architecture

```text
:app (Gürbakır shell; implemented)
   └──> :mobile-core
           ├──> :foundation
           ├──> :storefront
           ├──> :account
           └──> :checkout

:synthetic (non-production conformance app; implemented at apps/synthetic)
   └──> :mobile-core

:<future-real-brand> (future application module)
   └──> :mobile-core

brand app ──> provider adapter such as :firebase when required
shared modules -X-> concrete brand application modules
```

The exact future real-brand module path spelling is secondary to the boundary. Existing `:app` intentionally remains the Gürbakır application/composition shell. Shared `:mobile-core` owns reusable application behavior. Gates 2–5 prove that a separately identified application can consume that behavior with distinct fixed configuration, protected storage, capability/navigation composition and provider ownership without importing `:app`, requiring Firebase in shared application code, or adding a runtime merchant switch; they do not prove production onboarding, runtime switching, live provider health or every planned variation dimension.

## Build selection

- The Android application module being built selects the brand.
- Environment may remain a product-flavor dimension inside each real brand application.
- Build type remains an Android build concern.
- There is no normative `brand` flavor dimension.
- There is no runtime merchant/store switch or mutable current-brand singleton.
- Remote Shopify or Firebase data cannot change the installed application identity.

## Ownership

| Concern | Target owner |
|---|---|
| `applicationId`, signing and Play identity | Brand Android application module/release boundary |
| App name, launcher/adaptive icons, splash, fonts and packaged assets | Brand application resources |
| Manifest, Android permissions, App Links and platform theme | Brand application module |
| Customer Account callback identity | Brand application + environment configuration and external Shopify registration |
| Firebase enablement, registration and `google-services.json` | Brand application + environment/external Firebase boundary |
| Storefront client configuration | Brand application + environment configuration |
| Foreground locale policy and fixed application market | Brand application configuration; shared deterministic resolution/formatting mechanics |
| Persisted Search normalization and territory input mode | Brand application policy; shared normalization/UI behavior |
| Merchant media origin | Brand application Storefront-domain input; shared fail-closed mapping/egress enforcement |
| Protected SharedPreferences/Keystore identities | Brand application configuration; shared encrypted store implementations |
| Search/Wishlist/Customer Account presence and ordered primary destinations | Application-owned `ApplicationComposition`; neutral validated value contracts in `:foundation` |
| Enabled Customer Account configuration and Order/tracking/deletion bindings | Application configuration and composition; bindings supplied exactly when Account is enabled |
| Provider selection and build-time Firebase readiness | Brand application composition/build boundary; provider mechanics remain in the provider module |
| Common Compose features and application orchestration | Shared `:mobile-core` |
| Typed navigation contracts and common graph/recovery mechanics | Shared `:mobile-core` |
| Design-token value types and shared consumption mechanics | Shared code; concrete brand values/assets originate from the brand app |
| Storefront, Customer Account and Checkout implementations | Their shared integration modules behind project-owned contracts |
| Commerce correctness and security validation | Shared code |
| Catalog, merchant navigation and bounded merchandising/editorial content | Shopify where an approved typed contract exists |
| Finite native renderers and executable behavior | Android shared or brand-compiled code, never arbitrary remote input |
| Existing Gürbakır Room/Keystore/application identities | Migration-sensitive legacy state, preserved by the Gürbakır app |

See [Brand Boundaries](BRAND-BOUNDARIES.md) for the operational ownership test.

## Dependency invariants

```text
Shared and provider modules MUST NOT depend on :app, :synthetic, or a future concrete brand application.
Brand applications MAY depend on shared modules.
Shared code MUST NOT branch on concrete brand names.
Brand-specific compiled behavior stays on the brand side of a narrow shared contract when a real requirement justifies it.
```

Gate 1 made the initial dependency/literal subset machine-enforceable. Gate 2 extended CI and `scripts/Test-RepositoryPortability.ps1` to the exact eight-subproject topology, the logical-to-physical synthetic mapping, bidirectional application contamination, synthetic-to-Firebase exclusion, and decoded XML ownership checks. Gate 3 added focused shared-production checks for concrete merchant domains, protected identities, market fallbacks and media-policy bypass construction, including decoded qualified resources and adversarial fixtures. Gate 4 added a focused check against concrete application capability/navigation composition references in shared production source. Gate 5 removed generic Firebase/telemetry-provider readiness state from `AppConfiguration`, retained `:mobile-core` without a Firebase dependency edge, and strengthened repository/synthetic package validation across Firebase manifest metadata, generated resources, archive entries and DEX namespaces. Neutral composition/deep-link/provider contracts remain allowed. Namespace spelling alone is not an architecture boundary; internal `com.gurbakir.*` names may remain until a technical need justifies a safe change.

## Variation mechanisms

| Kind of variation | Mechanism |
|---|---|
| Scalar visual semantics | Shared typed design tokens supplied by brand composition |
| Packaged identity, fonts, icons, splash and brand-only assets | Brand application resources |
| Merchant-editable catalog, discovery and editorial merchandising | Bounded Shopify structures |
| Repeated visual family with known shapes | Finite typed shared component variant |
| Feature presence and primary topology | Validated application capabilities and navigation specification |
| Genuinely brand-specific native UI, behavior or provider | Brand-compiled implementation through the smallest justified shared seam |
| Commerce and security correctness | Shared invariant, not brand variation |

Tokens must not become a universal UI DSL. A future brand-specific extension is introduced only when a real requirement demonstrates that tokens, resources, bounded content or a finite shared variant are insufficient.

## Implemented capability and navigation boundary

`AppConfiguration.applicationComposition` is the sole capability/topology input. `:foundation` owns `ApplicationCapabilities`, explicit `CapabilityState`, `CustomerAccountCapability.Disabled` / `Enabled(configuration)`, the finite `PrimaryNavigationDestination` enum and immutable `PrimaryNavigationSpec`. Construction rejects duplicates, missing Home/Categories, disabled destinations and omitted enabled destinations. `ApplicationComposition` revalidates the specification against its capabilities, including copies. Caller order is retained; Home need not be the first displayed item and remains the graph start/state anchor. Invalid enabled Account configuration remains invalid and is never interpreted as disabled.

Gürbakır enables all three optional capabilities and supplies `[HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT]`. Synthetic enables Search only and supplies `[SEARCH, HOME, CATEGORIES]`. Home, Categories, Collection, Product, Cart, Legal/Support and recovery remain common journeys.

`:mobile-core` owns enum-to-route/presentation mapping, typed routes, graph construction and recovery. Search, Wishlist and the seven Account-family typed nodes remain registered as guarded tombstones when disabled; they recover before creating feature content, including before disabled AddressForm/OrderDetail argument conversion. Recovery removes the current branch above Home without saving it, or clears destination entries to the graph when no Home entry exists. Disabled restored ancestors cannot remain beneath recovery in that branch. Existing Home and Legal recovery actions remain available. These mechanics are implemented and passed the required final PR and post-merge managed-device CI lanes; local physical-device/manual execution remains a non-proof as preserved in the Gate 4 handoff.

Common deep-link configuration contains Collection/Product bases. `CustomerAccountFeatureBindings` supplies Order base, tracking policy and deletion-page launcher exactly when Account is enabled; disabled Account registers no Order deep link. Disabled session/authorization operations return signed-out/unavailable outcomes without evaluating encrypted-store, discovery, token or logout providers. Existing Cart detach/pending/quarantine safety remains active independently of Account presence.

Disabling Search or Wishlist does not delete persisted data. Optional browsing/actions and Wishlist membership observation are absent at their guarded entry points. When Account remains enabled, deletion retains Search-history, Wishlist and Cart cleanup controls even if the corresponding browsing capabilities are disabled; this explicit privacy exception can clear dormant data. Disabling Account itself leaves its encrypted session dormant and does not claim remote logout/revocation or deletion.

## Implemented Firebase/provider isolation boundary

Generic `AppConfiguration` no longer carries Firebase readiness or unused telemetry-provider policy. `:mobile-core` keeps the provider-neutral `UpdatePolicyRemoteGateway` contract and persisted update-policy behavior without depending on `:firebase`. `:firebase` owns Firebase SDK mechanics, strict Remote Config parsing/defaults and its provider adapter behavior; it does not own an application identity.

Gürbakır `:app` retains its explicit `:app -> :firebase` dependency and owns provider selection. `BuildConfig.FIREBASE_CONFIGURED` derives only from the complete validated development/staging four-file Google Services configuration set. When false, application composition selects local defaults before Firebase Remote Config construction; when true, it selects the existing Firebase adapter. The unconfigured debug Firebase proof is unavailable before proof ViewModel/controller/push-coordinator construction, while configured debug retains the existing explicit-consent proof path. `FIREBASE_CONFIGURED` proves build-time configuration completeness, not live Firebase health.

Synthetic remains the stronger physical-absence conformance boundary: no Firebase project dependency, Google Services plugin/configuration, generated Google app resource, Firebase manifest component/metadata, archive entry or Firebase DEX namespace is permitted. An unconfigured Gürbakır APK may still package Firebase SDK classes and `FirebaseInitProvider`; that state is intentionally distinct from synthetic physical absence. Gate 5 did not introduce a provider enum/plugin framework, runtime merchant switch, external Firebase mutation, Analytics/Crashlytics dependency, production FCM path, persistence migration or identity change.

## Shopify and native boundary

Shopify may own:

- products, variants, collections, prices, inventory and media;
- merchant category/discovery hierarchy through Shopify Navigation;
- structured mobile pages or ordered sections through an approved finite schema;
- Product/Collection-specific mobile metadata through typed resource references.

Android retains:

- application ID, signing, permissions and installed identity;
- Firebase/provider selection and OAuth/App Link security boundaries;
- finite native component/rendering types and route contracts;
- capability/navigation validation and recovery;
- Room/Keystore identity and native security validation;
- all executable code and behavior.

Remote content must never supply arbitrary native class, route or component identifiers, unrestricted hosts, arbitrary SDK selection, Compose trees or executable behavior. Unknown or malformed future content requires strict validation and bounded fallback; this document does not define the final mobile-content schema.

## Persistence and migration safety

Separate application IDs already provide per-application data isolation. Do not add `brandId` to every Room table merely because the monorepo serves multiple brands.

Existing Gürbakır application IDs, `gurbakir-local.db`, Room schema/migration chain, Customer Account secure preferences/Keystore aliases and cart secure preferences/Keystore aliases remain exact unless a separately designed migration proves a change safe. Gate 3 moved those protected identities into exhaustive application-owned configuration literals and made the reusable encrypted stores consume them unchanged; it did not migrate data or derive new names. See [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md).

New brands receive independent application identities and sandboxes. Adding one must not trigger a Gürbakır persistence migration.

## Current and next gate boundary

Gate 0 established this source of truth and [ADR-0004](../decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md). Gate 1 created `:mobile-core`, extracted reusable implementation, and retained `:app` as the Gürbakır shell without changing existing installed/persisted identities. Gate 2 added the separate `:synthetic -> :mobile-core` edge with independent Android identity, resources, manifest, sandbox, configuration, design-token values, local database, fail-closed external-service inputs, and no Firebase dependency or packaged Firebase registration.

Gate 3 implemented focused immutable application inputs for locale, fixed market, territory-specific postal input, merchant media origin, Search normalization and protected persistence. It gives `:synthetic` separate encrypted stores, keeps Gürbakır's `tr-TR` persisted Search normalization and exact protected-store wire/identity contracts, and leaves Room schema/version/migrations untouched. Its media client validates the initial URL and every manual redirect before another request attempt. The exact evidence and limits are in the [Gate 3 completion handoff](../multi-brand/GATE-3-COMPLETION-HANDOFF.md).

Gate 4 implemented capability-driven presence and ordered primary navigation while retaining typed route compatibility and unchanged persisted identities. Synthetic's manifest/package boundary removes Order and callback handlers; AppAuth remains compiled behind a non-exported management activity. Gate 4 closure completed after final whole-branch review/security triage, fresh documentation-bearing HEAD CI including API 30 and separate API 23 lanes, owner merge and successful post-merge `main` verification.

Gate 5 implemented generic Firebase/provider isolation without adding a shared provider framework: Firebase readiness/provider policy moved out of generic application configuration, application composition selects local defaults before Firebase SDK construction when unconfigured, `:mobile-core` remains provider-neutral, and synthetic physical Firebase exclusion is enforced across dependency/package/resource/manifest/archive/DEX surfaces. The exact implementation and proof limits remain in the [Gate 5 completion handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md), which intentionally stops before closure. Gate 5 is now closed because its final exact-HEAD candidate passed CI, whole-candidate review and focused security/privacy triage, the owner merged it, and exact merged-`main` push CI succeeded.

Gate 6 planning is the next multi-brand activity and is not started. This reconciliation does not define Gate 6 implementation scope. Runtime market/language switching, Storefront `@inContext`, generalized merchant Navigation/Home content, real-brand onboarding/registrations, external-provider mutation, signing, publication and P3-16 remain unstarted or outside this reconciliation unless separately evidenced and approved.
