# Brand Onboarding

Status: **Canonical future real-brand onboarding contract after Gate 5 closure; Gate 6 planning is next and not started**

This document describes the accepted onboarding model after the reusable-core, synthetic-edge, fixed-input, capability/navigation and provider-isolation gates. Gates 1–3 established reusable `:mobile-core`, bounded non-production `:synthetic` and application-owned fixed inputs. Gate 4 added validated capability presence and primary order. Gate 5 added application-owned Firebase/provider selection while keeping shared application behavior provider-neutral and strengthening the synthetic physical Firebase-absence boundary. Gates 4 and 5 are closed after their required final PR/review/CI, owner merge and post-merge `main` verification. No additional real merchant application exists.

This is an ownership and evidence contract, not authorization to start Gate 6, create a real brand application, provision production state, mutate an external provider, or publish an application.

## Repository and application model

Every future brand:

- stays in this shared monorepo;
- receives its own Android application module;
- is selected by building that application module;
- owns its Android distribution identity and brand-specific composition;
- depends inward on shared modules;
- is never imported by shared modules;
- does not receive a permanent branch or repository fork;
- represents one brand per built APK/AAB, with no runtime store switch.

Existing `:app` is the Gürbakır application/composition shell and depends on `:mobile-core`. Gate 2's logical `:synthetic` project maps to physical directory `apps/synthetic`; this intentionally demonstrates that logical Gradle identity and repository layout can differ without creating an implicit `:apps` Gradle project. A future real brand uses its own documented application project/path. The exact path name does not change the dependency boundary.

## Onboarding record

Before a future brand can be considered configured, its repository-local onboarding record must resolve the following without including secrets:

| Concern | Required non-secret evidence/decision |
|---|---|
| Application identity | Application ID for every real environment; identity owner and status |
| Environments | Only environments that actually exist, with their purpose and safe defaults |
| Display identity | App display name, launcher/adaptive icons, splash, fonts and approved packaged assets |
| Storefront | Owned domain, API version/configuration source and allowed media/origin policy; no token value in docs |
| Customer Account | Explicit `Disabled` or `Enabled(configuration)`; valid configuration, callback/logout registration owner and Order/tracking/deletion bindings when enabled |
| App Links | Owned hosts/routes, external domain association owner and verification status |
| Firebase | Enabled/disabled decision; Android registrations and environment ownership when enabled; build-time configuration completeness is app-owned and is not live-provider proof |
| Market and locale | Market/country/currency, default/supported locales and market-selection policy actually required |
| Persisted normalization | Explicit locale/policy for durable Search keys; never infer it from the foreground locale when compatibility state exists |
| Protected persistence | Exact distinct preference names and Keystore aliases for cart/customer state in every environment |
| Capabilities | Application-owned Search/Wishlist states and explicit Customer Account capability; no remote selection or missing-credential inference |
| Primary navigation | Exactly Home/Categories plus each enabled optional primary once; explicit order, immutable validated specification; Home remains the start/state anchor |
| Legal and support | Approved destinations, ownership and current status without unsupported public claims |
| Media/domain policy | Exact approved owned origins and restrictive validation behavior |
| Provider binding | Explicit application-owned provider/local-default selection through the smallest existing neutral seam; no generic provider framework for hypothetical variation |
| Compiled variation | Concrete brand-specific integration/UI/dependency requirements and the narrow shared seam, if any |
| Release boundary | Signing, publication, support and rollback ownership only when the production/release gate is intentionally entered |

Private Storefront/Admin credentials, OAuth/customer tokens, Firebase service credentials, signing material, service-account keys and other secrets must use approved ignored/secure mechanisms and never enter the onboarding record, source, logs or mobile binary.

## Packaged identity and composition

The brand application module owns:

- application ID, manifest and environment variants;
- application name, icons, splash, platform theme, fonts and brand-only assets;
- App Link and Customer Account callback identity;
- Firebase/Google Services configuration when used;
- Storefront/environment inputs;
- focused localization, fixed-market, persisted-normalization, territory-input, capability, navigation and provider bindings;
- exact database filename and protected-store identities for every environment;
- brand-specific compiled dependencies or implementation justified by a real requirement.

Shared `:mobile-core` consumes focused typed policies/configuration. It must not gain a concrete brand-name branch, domain or application ID when a brand is added, and it must not gain a Firebase dependency merely because one application selects Firebase.

Supply `AppConfiguration.applicationComposition` as the single topology source. Common Collection/Product deep-link bases remain mandatory; supply `CustomerAccountFeatureBindings` exactly when Account is enabled. Match the application manifest to the intended external surface as well as the shared graph. Disabling Account does not physically remove AppAuth: inspect the resulting package and ensure no unintended exported callback/URI handler remains. A placeholder used by manifest merging is not a runtime Account configuration.

For Firebase/provider composition, do not place provider readiness or telemetry-provider state back into generic `AppConfiguration`. When a brand uses Firebase, the application build/composition boundary owns configuration completeness and provider selection; provider-specific SDK construction remains inside the provider module. A local/unconfigured path must fail safely before unnecessary provider construction. A physically Firebase-free brand or fixture requires package/dependency proof rather than a runtime disabled flag alone.

## Merchant content

Catalog, merchant discovery/navigation and merchandising/editorial content should use bounded Shopify-owned structures when approved contracts exist. Prefer typed Product/Collection references where they fit the use case.

Do not:

- hardcode production product or collection handles into shared Kotlin;
- let Shopify select arbitrary native classes, routes, components, SDKs or executable behavior;
- define a final Home metaobject/schema in this onboarding contract;
- treat remote content as installed brand identity.

Android retains finite native renderers, validation, security, capability topology and fallback behavior.

## Data and migration isolation

Separate application IDs provide application-data isolation for new brands. A new brand receives independent storage/configuration identities and must not trigger a Gürbakır Room, SharedPreferences or Android Keystore migration.

Never copy Gürbakır's migration-sensitive identities as defaults for another brand. The exact Gürbakır compatibility register is [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md).

Disabling browsing preserves dormant Search/Wishlist state. Disabled Account must not initialize its encrypted store or network clients and does not erase or remotely revoke an existing session. When Account is enabled, deletion retains all local cleanup choices even when Search/Wishlist browsing is disabled: Search-history cleanup defaults on, Wishlist cleanup off, and Cart discard on. This privacy exception retains confirmation and outcome reporting; it does not establish merchant-side deletion.

## Synthetic conformance result through Gate 5

Gate 2 added a non-production synthetic application without teaching shared code its concrete name, domain, application ID, or catalog. Gate 3 retained that boundary while giving it fixed `en-CA` locale/Search policy, `ZZ/ZZ/XTS` market, text postal input and distinct encrypted cart/customer stores. Gate 4 retained those identities but enabled Search only, disabled Wishlist/Account and ordered the primary destinations `[SEARCH, HOME, CATEGORIES]`. Gate 5 retained the same application/capability identity while strengthening proof that synthetic is physically Firebase-free. Gate 4 local physical-device/manual attempts executed zero tests and remain NOT RUN/non-proof; the required final PR and post-merge managed-device CI lanes passed. Gate 5 exact-HEAD and merged-main CI, review and security closure gates passed; its historical handoff retains the detailed pre-merge evidence and non-proofs.

The proof is intentionally bounded:

| Dimension | Evidence boundary after Gate 5 closure |
|---|---|
| Separate application identity, manifest, resources, sandbox, and Gradle edge | Proven |
| Complete design-token values and runtime consumption | Proven; custom-font behavior deferred |
| Home/Catalog fixtures, fixed market and locale | Gate 3 evidence retained; Gate 4 removes Account Order/tracking/deletion runtime bindings; runtime switching/general internationalization not implemented |
| Firebase | Physical absence proven in project dependencies, plugin/configuration, generated resources, merged manifest/metadata, archive entries and DEX namespaces; no live-provider behavior is implied |
| Search, Wishlist, and Customer Account | Search enabled; Wishlist/Account disabled. Contract/session/JVM, package and required managed-device CI evidence passed; local physical-device/manual runtime remains NOT RUN |
| Persistence | Separate Room database/partitions plus distinct encrypted cart/customer identities, recreation, clear and process-restart evidence proven |
| Primary navigation and capabilities | Immutable validated `[SEARCH, HOME, CATEGORIES]` composition; Home remains the graph start; required final managed-device CI exercised the Gate 4 instrumentation lane |
| External URI surface | Package validators require only Collection/Product HTTPS filters, no Order/callback/custom scheme and non-exported AppAuth management; required managed-device CI covered installed-package assertions, while live external registration remains unproven |
| Domain/media policy | App-derived fail-closed policy and mapper/redirect enforcement proven; synthetic still has no INTERNET permission and does not prove live fetch |
| Provider selection boundary | Synthetic requires no Firebase selection path; Gürbakır proves app-owned Firebase/local-default selection through the existing neutral seam without making provider choice a shared capability |
| External registrations and production release | Not applicable to the synthetic fixture; not proven |

The synthetic application is not an onboarding template to copy blindly into a real brand. Its inert `.invalid` Storefront/Collection/Product inputs, blank public Storefront token, explicit disabled Account/Wishlist, fixed synthetic identities, unsigned release, and absent INTERNET/Firebase integration are deliberate conformance controls. Dummy Customer Account endpoints and the full callback URI are removed; the inert manifest-merge scheme input remains. A real brand must resolve the onboarding record above and enter separately approved gates. See the [Gate 5 completion handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md) and the earlier [Gate 4 completion handoff](../multi-brand/GATE-4-COMPLETION-HANDOFF.md).

## Prohibited onboarding shortcuts

- permanent per-brand Git branch or repository fork;
- runtime merchant/store switching;
- shared `if (brand)` logic or shared-to-brand module dependency;
- accidental copying of Gürbakır application IDs, Room/Keystore identities, Firebase packages, OAuth callback or App Links;
- committing secrets or private signing material;
- arbitrary remote UI/code or provider selection;
- speculative generic plugin, loyalty, backend, analytics or notification frameworks;
- inventing production identity, signing, callback, Firebase or public association state.

## Acceptance gate for a real future brand

Before calling a real brand onboarded, record evidence that:

1. application/distribution and external-service ownership are authoritative;
2. every enabled integration fails closed when required configuration is absent or inconsistent;
3. brand resources and identities resolve from its application module;
4. shared modules have no dependency or concrete-name branch for the brand;
5. navigation and capabilities are internally consistent;
6. approved origins reject foreign-brand content where required;
7. relevant debug, release, unit, integration, manifest, resource and migration checks pass;
8. no Gürbakır compatibility state changed merely because the brand was added;
9. release-only claims remain at their governing production gate until actually proven.

The exact verification lane belongs in the later brand/gate implementation plan, not here.

Current applicability: use this contract only to plan a future real brand through a separately approved gate. Gates 2–5 establish bounded second-application, fixed-input, capability-composition and provider-isolation boundaries, and Gate 5 is closed. They do not authorize Gate 6, real-brand onboarding, external-provider mutation, production configuration, signing, publication, or release readiness.

## Related authority

- [Multi-Brand Architecture](MULTI-BRAND-ARCHITECTURE.md)
- [Brand Boundaries](BRAND-BOUNDARIES.md)
- [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md)
- [ADR-0004](../decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md)
- [Gate 2 Completion Handoff](../multi-brand/GATE-2-COMPLETION-HANDOFF.md)
- [Gate 3 Completion Handoff](../multi-brand/GATE-3-COMPLETION-HANDOFF.md)
- [Gate 4 Completion Handoff](../multi-brand/GATE-4-COMPLETION-HANDOFF.md)
- [Gate 5 Completion Handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md)
