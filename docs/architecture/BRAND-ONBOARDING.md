# Brand Onboarding

Status: **Canonical future real-brand onboarding contract after Gate 7 closure**

This document describes the accepted onboarding model after the reusable-core, synthetic-edge, fixed-input, capability/navigation, provider-isolation, bounded Categories-discovery and bounded Home-content gates. Gates 1–3 established reusable `:mobile-core`, bounded non-production `:synthetic` and application-owned fixed inputs. Gate 4 added validated capability presence and primary order. Gate 5 added application-owned Firebase/provider selection while keeping shared application behavior provider-neutral and strengthening the synthetic physical Firebase-absence boundary. Gate 6 added application-selected bounded Shopify Menu discovery for Categories. Gate 7 added application-selected bounded Shopify Home editorial content with finite native rendering, editorial-only LKG persistence and an explicitly remote-disabled synthetic composition. Gates 4–7 are closed after their required candidate/CI/review evidence, owner-authorized merge and post-merge `main` verification. No additional real merchant application exists.

This is an ownership and evidence contract, not authorization to create a real brand application, begin Gate 8 provisioning, mutate production state, or publish an application. Gate 8 and later work require separate planning/approval from the then-current source and provider state.

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
| Categories discovery | Application-owned bounded Shopify Menu selector and verified Storefront readability |
| Home editorial content | Application-owned bounded `mobile_home` root selector, supported content version, Storefront readability/publication behavior and app-owned packaged fallback |
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

Private Storefront/Admin credentials, OAuth/customer tokens, Firebase service credentials, signing material, service-account keys and other secrets must use approved ignored/secure mechanisms and never enter the onboarding record, source, logs or mobile binary. Public Storefront client tokens remain controlled public client configuration: they may be packaged where required but must not be copied into ordinary logs, prompts, screenshots or documentation.

## Packaged identity and composition

The brand application module owns:

- application ID, manifest and environment variants;
- application name, icons, splash, platform theme, fonts and brand-only assets;
- App Link and Customer Account callback identity;
- Firebase/Google Services configuration when used;
- Storefront/environment inputs;
- bounded Categories Menu and Home-root selectors;
- app-owned packaged Home fallback values/resources;
- focused localization, fixed-market, persisted-normalization, territory-input, capability, navigation and provider bindings;
- exact database filename and protected-store identities for every environment;
- brand-specific compiled dependencies or implementation justified by a real requirement.

Shared `:mobile-core` consumes focused typed policies/configuration. It must not gain a concrete brand-name branch, domain or application ID when a brand is added, and it must not gain a Firebase dependency merely because one application selects Firebase.

Supply `AppConfiguration.applicationComposition` as the single topology source. Common Collection/Product deep-link bases remain mandatory; supply `CustomerAccountFeatureBindings` exactly when Account is enabled. Match the application manifest to the intended external surface as well as the shared graph. Disabling Account does not physically remove AppAuth: inspect the resulting package and ensure no unintended exported callback/URI handler remains. A placeholder used by manifest merging is not a runtime Account configuration.

For Firebase/provider composition, do not place provider readiness or telemetry-provider state back into generic `AppConfiguration`. When a brand uses Firebase, the application build/composition boundary owns configuration completeness and provider selection; provider-specific SDK construction remains inside the provider module. A local/unconfigured path must fail safely before unnecessary provider construction. A physically Firebase-free brand or fixture requires package/dependency proof rather than a runtime disabled flag alone.

For Home composition, the application owns whether the bounded remote source is enabled and which root it selects. Blank/unconfigured selection must fail closed to the app-owned packaged source; remote Home content must never infer or alter application capability/provider identity. Another brand receives its own application sandbox and Home-content partition.

## Merchant content

Catalog, merchant discovery/navigation and merchandising/editorial content use bounded Shopify-owned structures where approved contracts exist. Prefer typed Product/Collection references where they fit the use case.

Implemented bounded contracts now include:

- Gate 6 Categories discovery through an application-selected Shopify Menu; and
- Gate 7 Home editorial composition through an application-selected `mobile_home` root with at most one collection-grid section and one featured-product section.

Do not:

- hardcode production product or collection handles into shared Kotlin;
- let Shopify select arbitrary native classes, routes, components, SDKs or executable behavior;
- generalize the Gate 7 Home contract into an arbitrary page/layout/component DSL without a separately justified requirement;
- persist remote editorial data as authority for current price, availability, inventory, cart or checkout truth;
- treat remote content as installed brand identity.

Android retains finite native renderers, validation, security, capability topology, typed actions, fallback/LKG/expiry behavior and current-commerce resolution.

## Data and migration isolation

Separate application IDs provide application-data isolation for new brands. A new brand receives independent storage/configuration identities and must not trigger a Gürbakır Room, SharedPreferences or Android Keystore migration.

Never copy Gürbakır's migration-sensitive identities as defaults for another brand. The exact Gürbakır compatibility register is [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md).

Gate 7's `home_content_v1` store is editorial cache state, partitioned by application/environment/store/root. It is not a protected-session store and must never be used to share Home authority across brands or environments. Existing Gürbakır Room/Keystore/protected-preference identities remain unchanged.

Disabling browsing preserves dormant Search/Wishlist state. Disabled Account must not initialize its encrypted store or network clients and does not erase or remotely revoke an existing session. When Account is enabled, deletion retains all local cleanup choices even when Search/Wishlist browsing is disabled: Search-history cleanup defaults on, Wishlist cleanup off, and Cart discard on. This privacy exception retains confirmation and outcome reporting; it does not establish merchant-side deletion.

## Synthetic conformance result through Gate 7

Gate 2 added a non-production synthetic application without teaching shared code its concrete name, domain, application ID, or catalog. Gate 3 retained that boundary while giving it fixed `en-CA` locale/Search policy, `ZZ/ZZ/XTS` market, text postal input and distinct encrypted cart/customer stores. Gate 4 retained those identities but enabled Search only, disabled Wishlist/Account and ordered the primary destinations `[SEARCH, HOME, CATEGORIES]`. Gate 5 retained the same application/capability identity while strengthening proof that synthetic is physically Firebase-free. Gate 6 replaced compiled Categories discovery with an application-selected bounded Shopify Menu while keeping synthetic remote discovery inert and credential-free. Gate 7 retains synthetic as remote-disabled for Home, keeps its independent packaged Home fixtures, and exercises the shared Home store/codec/state/renderer offline without adding live Shopify credentials or effective INTERNET permission.

The proof is intentionally bounded:

| Dimension | Evidence boundary after Gate 7 closure |
|---|---|
| Separate application identity, manifest, resources, sandbox, and Gradle edge | Proven |
| Complete design-token values and runtime consumption | Proven; custom-font behavior remains deferred |
| Categories discovery | Bounded application-selected Shopify Menu contract implemented; synthetic remains inert/credential-free |
| Home editorial content | Bounded application-selected metaobject contract implemented for real configured acceptance; synthetic production source remains `Disabled` with independent packaged fixtures |
| Home persistence/lifecycle | Editorial-only LKG/cache partitioning, strict codec, refresh/expiry and process-restart behavior proven; no Room/protected-store migration |
| Firebase | Physical absence proven in project dependencies, plugin/configuration, generated resources, merged manifest/metadata, archive entries and DEX namespaces; no live-provider behavior is implied |
| Search, Wishlist, and Customer Account | Search enabled; Wishlist/Account disabled. Contract/session/JVM, package and required managed-device CI evidence passed |
| Persistence | Separate Room database/partitions plus distinct encrypted cart/customer identities, recreation, clear and process-restart evidence retained; Home editorial cache is separately partitioned |
| Primary navigation and capabilities | Immutable validated `[SEARCH, HOME, CATEGORIES]` composition; Home remains the graph start; Account-disabled Legal/Support is reachable without Account initialization |
| External URI surface | Package validators require only Collection/Product HTTPS filters, no Order/callback/custom scheme and non-exported AppAuth management |
| Domain/media policy | App-derived fail-closed policy and mapper/redirect enforcement proven; synthetic still has no effective INTERNET permission and does not prove live fetch |
| Provider selection boundary | Synthetic requires no Firebase selection path; Gürbakır proves app-owned Firebase/local-default selection through the existing neutral seam without making provider choice a shared capability |
| External registrations and production release | Not applicable to the synthetic fixture; not proven |

The synthetic application is not an onboarding template to copy blindly into a real brand. Its inert `.invalid` Storefront/Collection/Product inputs, blank public Storefront token, explicit disabled Account/Wishlist/Home remote source, fixed synthetic identities, unsigned release, and absent effective INTERNET/Firebase integration are deliberate conformance controls. Dummy Customer Account endpoints and the full callback URI are removed; the inert manifest-merge scheme input remains. A real brand must resolve the onboarding record above and enter separately approved gates.

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
7. bounded Categories/Home selectors and merchant-content visibility are verified through the intended Storefront client;
8. Home editorial cache/partitioning cannot consume another brand/environment/store/root authority;
9. relevant debug, release, unit, integration, manifest, resource and migration checks pass;
10. no Gürbakır compatibility state changed merely because the brand was added;
11. release-only claims remain at their governing production gate until actually proven.

The exact verification lane belongs in the later brand/gate implementation plan, not here.

Current applicability: use this contract only to plan a future real brand through a separately approved gate. Gates 2–7 establish bounded second-application, fixed-input, capability-composition, provider-isolation, Categories-discovery and Home-content boundaries, and Gate 7 is closed. They do not authorize Gate 8 provisioning, real-brand onboarding, production configuration, signing, publication, or release readiness.

## Related authority

- [Multi-Brand Architecture](MULTI-BRAND-ARCHITECTURE.md)
- [Brand Boundaries](BRAND-BOUNDARIES.md)
- [Gürbakır Legacy Identities](GURBAKIR-LEGACY-IDENTITIES.md)
- [ADR-0004](../decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md)
- [Gate 2 Completion Handoff](../multi-brand/GATE-2-COMPLETION-HANDOFF.md)
- [Gate 3 Completion Handoff](../multi-brand/GATE-3-COMPLETION-HANDOFF.md)
- [Gate 4 Completion Handoff](../multi-brand/GATE-4-COMPLETION-HANDOFF.md)
- [Gate 5 Completion Handoff](../multi-brand/GATE-5-COMPLETION-HANDOFF.md)
- [Gate 6 Completion Handoff](../multi-brand/GATE-6-COMPLETION-HANDOFF.md)
- [Gate 7 Completion Handoff](../multi-brand/GATE-7-COMPLETION-HANDOFF.md)
