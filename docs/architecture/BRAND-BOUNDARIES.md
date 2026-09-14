# Brand Boundaries

Status: **Canonical ownership rules**

This document classifies multi-brand ownership decisions under the architecture described in [Multi-Brand Architecture](MULTI-BRAND-ARCHITECTURE.md). Gates 1–3 established the reusable-core base, second non-production application and application-owned fixed inputs. Gate 4 implemented and verified capability presence and primary order. Gate 5 implemented and verified application-owned Firebase/provider selection while retaining provider-neutral shared application behavior and a physically Firebase-free synthetic conformance boundary. Gate 6 implemented and verified application-selected bounded Shopify Menu discovery for Categories. Gate 7 implemented and verified application-selected bounded Shopify Home editorial content with finite native rendering, typed resource actions, editorial-only LKG persistence and remote-disabled synthetic composition. Gates 4–7 are closed. Gürbakır is still the first real validation brand; no second real merchant application exists.

## Ownership categories

### 1. Brand Android application module

Owns separately installed/distributed Android identity and brand composition:

- application ID, version/release channel and signing boundary;
- app name, launcher/adaptive icons, splash, packaged fonts and brand-only assets;
- application manifest, App Links and Customer Account callback identity;
- environment-specific Storefront configuration;
- Firebase enablement and application registrations when used;
- focused market, locale, capability, primary-navigation and provider bindings;
- physical database filename, environment/market persistence partitions, and protected-store construction;
- bounded merchant-content selectors such as the Categories Menu and Home root handle;
- genuinely brand-specific compiled code or dependencies.

Current example: `:app` owns the Gürbakır development/staging IDs, Gürbakır application/theme/icons, `gurbakir.com` App Links, current Customer Account callback scheme and the application-selected Storefront/Menu/Home selectors.

Gate 3 example: logical project `:synthetic`, physically stored at `apps/synthetic`, owns the separate `com.example.gate2synthetic` identity, synthetic resources/theme/inert `.invalid` links, Firebase-free/fail-closed service policy, `gate2-synthetic-local.db`, fixed `ZZ/ZZ/XTS` market and `en-CA` localization/Search policies, and exact synthetic encrypted cart/customer identities. It is a conformance fixture, not a merchant or production app.

Gate 4 example: Gürbakır owns its all-enabled `[HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT]` composition. Synthetic owns Search-enabled/Wishlist-disabled/Account-disabled `[SEARCH, HOME, CATEGORIES]` and only Collection/Product HTTPS intent filters. Account configuration exists only within explicit `Enabled(configuration)`; Account Order/tracking/deletion bindings are present exactly when enabled. The synthetic callback scheme placeholder remains an inert build input after the receiver is removed from the merged manifest.

Gate 5 example: Gürbakır `:app` retains its explicit Firebase dependency and decides between the existing Firebase adapter and local defaults from app-owned build-time configuration. `FIREBASE_CONFIGURED` is derived only from the complete validated four-file Google Services set and is not a generic shared capability or proof of live provider health. Synthetic does not model “Firebase disabled” through a runtime flag; it remains physically Firebase-free across dependency, plugin/configuration, generated resource, manifest, archive and DEX boundaries.

Gate 7 example: Gürbakır may enable the bounded remote Home source by supplying an application-owned root handle; blank configuration fails closed to the packaged Home source. Synthetic explicitly selects `HomeRemoteSource.Disabled` and retains independent packaged fixtures without live Shopify credentials or network access.

### 2. Shared `:mobile-core`

Owns the reusable native product behavior extracted in Gate 1:

- common Compose application and feature implementation;
- common typed navigation mechanics and bounded route recovery;
- ViewModels, controllers, cart/search/wishlist orchestration and shared UI state;
- common capability mechanics and local-data behavior;
- design-system consumption and integration-neutral application policy;
- finite Home rendering, validation/recovery policy and editorial cache mechanics behind neutral contracts.

It must not know a concrete brand name, domain, application ID or merchant catalog. It consumes focused contracts and values supplied by the application composition. Concrete brand configuration, provider binding, database filename, environment/market partitions, persisted Search-normalization policy and protected-store identities remain application-owned; shared modules own only the reusable database/store implementations and validation mechanics.

The shared graph retains typed optional nodes as guarded unavailable destinations. Shared code maps finite primary enums to routes/labels/icons and recovers disabled direct/restored entries before feature content is created. It does not infer capabilities from missing credentials, remote content, brand names or arbitrary route strings. `:foundation` validates the exact primary set and preserves application order.

Capability absence is not a persistence migration or deletion request. Disabled browsing leaves dormant Search/Wishlist data intact; disabled Account avoids session-store/client initialization. Account-enabled deletion keeps all local cleanup choices, including dormant Search/Wishlist data, with existing confirmation/defaults and result handling. Cart continues its independent ownership detach/pending/quarantine rules.

Gate 5 preserves the existing provider-neutral `UpdatePolicyRemoteGateway` seam and persisted update-policy behavior in shared application code. `:mobile-core` has no dependency on `:firebase` and does not interpret Firebase configuration/readiness as an application capability.

Gate 7 keeps merchant Home content non-executable. `:mobile-core` accepts only the finite validated collection-grid/featured-product contract, owns Home refresh/expiry/LKG behavior, and exposes a normal Legal/Support entry when Account is absent. Remote content cannot enable capabilities, alter primary topology, choose providers or define arbitrary native routes/components.

### 3. Shared integration or domain module

Owns reusable provider/domain implementation behind project-owned contracts:

- `:storefront`: Shopify Storefront models, operations and gateways;
- `:account`: Shopify Customer Account OAuth/session/profile/address/order behavior;
- `:checkout`: the narrow Checkout Kit adapter boundary;
- `:foundation`: typed configuration, design-token values, validation, logging/redaction and small neutral contracts;
- `:firebase`: a provider adapter when an application chooses Firebase, not proof that every brand must use it.

Shared integration modules receive approved origins/configuration; they do not own a merchant identity. The Storefront module constructs a narrow media policy from the app-supplied merchant domain and enforces it in every mapper and before the first and each redirected image request.

Gate 5 makes the provider boundary concrete without introducing a provider enum or plugin framework. `:firebase` owns Firebase SDK mechanics, strict Remote Config parsing/defaults and construction fallback; Gürbakır `:app` owns whether that adapter is selected. An unconfigured Gürbakır build may still package Firebase SDK classes and `FirebaseInitProvider`, which is distinct from the synthetic physical-absence contract.

Gate 6 and Gate 7 keep Shopify-specific Menu/metaobject GraphQL and provider observations in `:storefront`; `:mobile-core` receives neutral bounded contracts and never raw provider executable authority.

### 4. Shopify merchant content

May own bounded merchant-editable commerce and merchandising data:

- catalog products, variants, collections, prices, inventory and media;
- category/discovery hierarchy through Shopify Navigation;
- the approved Gate 7 Home editorial root, ordered section entries and remote titles;
- Product/Collection-specific mobile metadata through typed references.

Gate 6 makes Categories membership/order/title merchant-editable through the selected Menu. Gate 7 makes Home section order, section titles and typed Product/Collection references merchant-editable through the selected finite metaobject contract. Android still owns finite layouts, capability checks, typed actions, fallback/LKG/expiry policy and all executable behavior. Current price, availability and media eligibility remain live Storefront truth rather than persisted editorial authority.

### 5. External service or configuration boundary

Owns state registered outside source while the brand application owns the corresponding mobile contract:

- Firebase Android application/project registration;
- Shopify Customer Account mobile-client callback/logout registration;
- domain `assetlinks.json` and App Link association;
- Play application/signing ownership;
- Shopify Admin content/schema provisioning when separately approved.

Documentation may record public identifiers and status but never credentials, private signing material, service-account secrets or customer tokens.

Gate 5 did not mutate this external boundary. Build-time Firebase configuration completeness is local application evidence only; it must not be described as registration health, Remote Config publication, FCM delivery, Analytics/Crashlytics operation or production readiness.

Gate 7 used a narrowly authorized project-owned non-production Shopify setup to prove the selected Home definitions, publication/readability states and actual mobile-client behavior. That evidence does not create generic provisioning. Repeatable per-brand/environment provisioning and enrollment remain later Gate 8 work.

### 6. Migration-sensitive legacy identity

Existing installed, external or persisted state must be preserved or migrated explicitly:

- Gürbakır development/staging application IDs and Firebase package contracts;
- `gurbakir-local.db`, exported Room schemas and explicit migrations;
- Customer Account SharedPreferences name and Android Keystore alias;
- cart SharedPreferences name and Android Keystore alias;
- current OAuth callback scheme and App Link routes;
- unresolved production identities, which must remain unresolved rather than be invented.

The authoritative inventory is [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md).

The Gate 7 `home_content_v1` store is new bounded editorial cache state, not a migration or renaming of these protected legacy identities.

## Dependency and contamination rules

```text
no shared if-brand switches
no shared module -> concrete brand application dependency
no merchant domain literals in shared production runtime except approved configuration/fixtures
no production Product/Collection handles in shared merchant configuration
no arbitrary remotely supplied native class/route/component identifiers
```

Brand code may depend inward on shared contracts. Shared/provider code must not import `:app`, `:synthetic`, or any future concrete brand application. Application modules must not depend on one another. If a real brand needs compiled behavior, put the implementation on the brand side and introduce only the narrow reusable contract the use case proves necessary.

Do not enforce these rules through a crude repository-wide blacklist. The word `gurbakir`, generic merchant phrases and historical package names may legitimately appear in history, application identity, fixtures and persistence contracts. Validate ownership and dependency direction instead.

## Decision test

Classify every future value, component or dependency by asking in order:

1. **Is this Android distribution identity?** Put it in the brand application/release boundary.
2. **Is it reusable native product behavior?** Put it in shared `:mobile-core` or an existing shared module.
3. **Is it merchant-editable Shopify content?** Use an approved bounded Shopify contract while Android retains native behavior.
4. **Is it an external-provider boundary?** Keep registration/credentials external and bind the provider from the brand application.
5. **Is it migration-sensitive existing state?** Preserve it exactly or require an explicit tested migration.
6. **Is it genuinely brand-specific compiled behavior?** Keep it on the brand side of the smallest justified shared contract.

If none fits, defer the abstraction until a concrete requirement clarifies ownership. Do not add a generic plugin, boolean or provider framework for hypothetical variation.

## Representative decisions

| Example | Classification | Owner |
|---|---|---|
| Gürbakır development/staging application IDs | Distribution + legacy identity | `:app`; preserve exact |
| Launcher icon and splash | Packaged identity | Brand application resources |
| `gurbakir.com` App Link routes | Application/external contract | `:app` manifest + owned domain state |
| Customer Account callback | Application/external contract | Brand environment config + Shopify registration |
| Categories hierarchy | Merchant-editable discovery | Shopify bounded Menu contract selected by application |
| Home section order/titles/Product-Collection references | Merchant-editable editorial content | Shopify bounded Gate 7 Home contract selected by application |
| Home renderer/actions/LKG policy | Reusable native behavior | `:mobile-core`; finite and non-executable remotely |
| Storefront gateway and validation | Reusable integration | `:storefront` |
| Customer Account OAuth/session behavior | Reusable integration | `:account`, enabled/bound by brand composition |
| Checkout Kit adapter | Reusable integration | `:checkout` |
| Firebase Remote Config adapter | Provider boundary | `:firebase`, selected by a brand app when required |
| Build-time Firebase configured state | Application/provider composition input | Concrete brand application build boundary; never a shared capability |
| Common navigation and route recovery | Reusable application behavior | `:mobile-core` |
| Optional feature presence and primary order | Application executable composition | Application values; neutral validation in `:foundation`, graph/UI consumption in `:mobile-core` |
| `gurbakir-local.db` | Migration-sensitive state | Gürbakır persistence composition; preserve exact |
| `gate2-synthetic-local.db` and exact synthetic encrypted stores | Synthetic conformance state | `:synthetic`; durable and isolated, still intentionally non-production |

## Enforcement direction

Gate 3 CI and `scripts/Test-RepositoryPortability.ps1` retain the Gate 2 topology/dependency/Firebase/application-contamination controls and add merchant-domain, protected-identity, concrete-market and media-policy-bypass guards across production Kotlin/Java and decoded resources. Adversarial self-tests include computed/raw strings and qualified/entity-encoded resources. `scripts/Test-Gate2SyntheticPackage.ps1` retains identity, manifest, permission, Firebase, signing and R8 inspection and additionally proves manifest-bound backup/transfer exclusions plus exact synthetic protected identities with Gürbakır identities absent from DEX. Runtime tests own Hilt selection, media chains, Keystore persistence and locale behavior. See the [Gate 3 completion handoff](../multi-brand/GATE-3-COMPLETION-HANDOFF.md) for observed results and proof limits.

Gate 4 extended source ownership validation to concrete application composition references, and package validation to the exact reduced synthetic URI surface, retired Account inputs and non-exported compiled AppAuth management boundary. Its required final PR and post-merge API 30/API 23 managed-device CI passed; local physical/manual and live-service limitations remain preserved in the [Gate 4 handoff](../multi-brand/GATE-4-COMPLETION-HANDOFF.md).

Gate 5 removes retired generic Firebase/telemetry controls from tracked configuration, requires app-owned Firebase configuration completeness before Google Services/configured selection, and strengthens portability/synthetic package enforcement across Firebase manifest components and metadata (including Analytics metadata), generated Google app resources, archive entries and DEX namespaces. Provider-selection, construction-fallback, proof-route and unconfigured-boundary tests discriminate app-owned selection from Firebase SDK mechanics. Its final exact-HEAD CI/review/security triage and merged-main push CI passed; detailed pre-merge evidence and non-proofs remain in the [Gate 5 handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md).

Gate 6 adds only bounded, application-selected Shopify Menu discovery for Categories. Shopify owns merchant hierarchy, order and titles within the validated Menu contract; Android retains typed Collection routes, capability and primary-navigation topology, media safety and executable behavior. Its final exact-HEAD candidate and merged-main validation passed; detailed pre-merge evidence and non-proofs remain in the [Gate 6 handoff](../multi-brand/GATE-6-COMPLETION-HANDOFF.md).

Gate 7 adds only the approved bounded Home contract and its required native lifecycle mechanics. Shopify owns ordered editorial section references/titles through the application-selected metaobject root; Android retains finite rendering, current commerce truth, capability/route authority, validation, refresh/expiry and editorial LKG policy. The final candidate merged through protected PR #6, and exact merged-main validate/API 30/API 23 CI passed. Detailed pre-merge evidence and non-proofs remain in the [Gate 7 handoff](../multi-brand/GATE-7-COMPLETION-HANDOFF.md). Later provisioning, second-store and release work must not be inferred from Gate 7 closure.
