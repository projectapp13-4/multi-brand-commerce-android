# System Boundaries and Limitations Reference Model

## 1. Status, provenance, confidence, and authority

This document preserves compact architectural and integration knowledge from
historical static reference analysis. It is deliberately brand-neutral and
non-forensic. It contains no decompiled expression, obfuscated symbol,
reference credential, private evidence path, or third-party brand material.

It is subordinate to current approved requirements, source, ADRs,
`docs/architecture`, tests, official platform/service documentation, and the
Phase 3 acceptance matrix. It cannot prove live server behavior or production
readiness.

- **Observed:** directly represented in the historical static evidence.
- **Strong inference:** supported by multiple independent observations.
- **Weak inference:** plausible but not sufficiently corroborated.
- **Unknown:** not established by static evidence.

## 2. Observed architecture, layers, state ownership, and concurrency

The transferable structure was a client commerce application with UI
destinations, presentation state, domain/repository boundaries, provider
clients, and local persistence. The valuable principles are separation of
screen state from transport models, explicit ownership of durable state, and
cancellation-aware asynchronous work.

The current implementation expresses those principles independently through
single-activity Jetpack Compose, typed Navigation Compose routes,
ViewModel/`StateFlow` unidirectional data flow, repositories/data sources, Hilt
composition, and narrowly scoped use cases. Historical framework or generated
class structure has no authority.

Latest-request-wins behavior is material for search, refresh, pagination, and
session changes. Cancellation is not an error. Shared mutable state must be
serialized or reduced through one owner; UI collectors must not create
duplicate provider mutations after lifecycle recreation.

## 3. Commerce network and provider boundaries

### Storefront

Catalog, collection, product, cart, and checkout-URL discovery belong behind a
typed Storefront boundary. Provider GraphQL response types are mapped into
project-owned domain models. Pagination cursors, user errors, nullability,
media-origin policy, and throttling/failure conditions remain explicit at that
boundary.

Current source uses Shopify Storefront API contracts, but shared application
code depends on project-owned interfaces rather than brand names or raw SDK
types. A public Storefront client token is extractable by design yet controlled
by repository and logging policy; Admin/backend secrets never belong in the
mobile client.

### Customer Account

Customer identity, profile, addresses, orders, and session operations use a
separate Customer Account API/auth boundary. Storefront and Customer Account
schemas, clients, credentials, retry rules, and cache policies must not be
collapsed. OAuth authorization uses PKCE, validated state/redirect handling,
protected token storage, refresh/expiry behavior, and a central terminal-reset
path.

### Checkout

Checkout remains behind a project-owned adapter around the official SDK. The
adapter owns SDK translation and completion/cancellation/failure signals, not
cart persistence or server order truth. Provider UI or a returned URL is not
evidence that a payment or order completed.

### Other network/provider services

Legal/support content, tracking links, remote configuration, notifications,
and analytics are separate trust decisions. Each requires an approved owner,
allowlist/configuration contract, safe default, privacy analysis, and explicit
composition. The historical presence of a client interface does not prove its
server implementation or authorize contacting it.

## 4. Data models and principal data flows

```text
application configuration
  -> provider/client composition
  -> repository request
  -> transport response and typed errors
  -> project-owned domain model
  -> ViewModel state reduction
  -> Compose rendering and user event

user event
  -> validated domain mutation
  -> provider/local result
  -> durable ownership update
  -> coherent UI state
```

Principal entities include collection/menu targets, products and variants,
media, cart/lines/costs, customer/session, profile, addresses, orders and
fulfillment, wishlist identifiers, search history, and feature/provider
configuration. Stable provider IDs are opaque. Display labels, handles, URLs,
and media locations are validated independently and cannot substitute for
identity.

Transport nullability and provider user errors must be mapped explicitly.
Partial responses cannot be promoted to complete domain success without a
defined product rule. Personally identifiable or credential-bearing models
must not cross into logs, analytics, screenshots, or unprotected fixtures.

## 5. Storage boundaries

Historical evidence distinguished ordinary preferences, provider-owned SDK
storage, and possible library cache facilities. It did not prove every writer,
encryption property, eviction rule, or live cache configuration.

The current architecture uses these explicit categories:

| Boundary | Appropriate data | Required properties |
|---|---|---|
| Local application database | Search/history and wishlist state selected by current source | Explicit schema/version/migration, environment and market partitioning |
| Protected application store | OAuth/customer session and cart ownership state | Android Keystore-backed encryption, stable aliases/names, versioned format, safe corruption handling |
| Ordinary preferences/config | Non-sensitive local choices and bounded flags | No tokens, secrets, private customer payloads, or false server truth |
| SDK-owned storage | Provider-internal state | Treated as a dependency boundary; not assumed to satisfy app persistence policy |
| Remote provider state | Catalog, customer, cart, order, configuration | Access-controlled API contract; local state cannot claim remote mutation without evidence |

Gürbakır's Room filename/schema/migrations, preference names, Keystore aliases,
wire formats, OAuth callback, application IDs, and partition keys are
compatibility-sensitive application identities. They remain exact under
`GURBAKIR-LEGACY-IDENTITIES.md`. A neutral repository name does not authorize a
persistence or identity migration.

Each concrete application owns separate configuration and durable namespaces.
The synthetic conformance app must remain isolated from Gürbakır stores and
must not acquire effective production network/provider composition.

## 6. Firebase, configuration, analytics, and notifications

Historical static evidence showed Firebase SDK participation and client-side
configuration surfaces but could not prove live Remote Config values,
notification routing, analytics policy, server retention, or production
enablement.

Current Multi-Brand architecture keeps reusable `:mobile-core` provider-neutral.
The concrete application owns Firebase/local-default provider selection, and
the `:firebase` module adapts SDK behavior behind project-owned contracts.
Configuration is absent or a complete validated per-variant set; unconfigured
selection occurs before Firebase SDK construction. The `:synthetic` app remains
physically Firebase-free across dependency, plugin/configuration, generated
resource, manifest, archive, and DEX boundaries.

Remote configuration must have bounded keys, validation, safe local defaults,
and no unapproved launch-bricking behavior. Production notifications,
analytics, and crash reporting remain absent/deferred unless current authority
and release/privacy work explicitly enable them.

## 7. Security and trust boundaries

- The mobile package and repository are public-client environments. They must
  contain no Admin/backend credentials, service-account secrets, signing keys,
  private keys, or customer tokens.
- OAuth authorization code flow requires PKCE, state validation, exact callback
  registration, protected tokens, and terminal session cleanup.
- Storefront public configuration is controlled and redacted from casual
  output even when it is not a server secret.
- Customer/account/order/address data is private and must follow least
  privilege, environment isolation, and log/analytics redaction.
- Checkout, App Links, Custom Tabs, tracking URLs, media, and remote content are
  untrusted inputs until scheme/host/path/content policy validates them.
- Deep links and notification intents cannot bypass capability,
  authentication, or typed-route validation.
- Firebase/provider presence is an application composition decision; shared
  modules cannot infer it from a brand or build environment name.
- Reference services and third-party production systems are outside the
  authorized test boundary.

## 8. Dependency, native-platform, and integration observations

The independently selected current stack is native Kotlin/Compose with pinned
Gradle/Android tooling, Hilt, Apollo Kotlin, Room/DataStore, Android Keystore,
official Shopify Checkout Kit, and bounded Firebase infrastructure. Current
official documentation and locked dependency metadata govern their behavior;
historical reference-library versions do not.

Native platform integration includes lifecycle restoration, network state,
browser/Custom Tabs, intent/App Link validation, OAuth return, Android
permissions, accessibility, localization, secure storage, and process death.
These boundaries require current tests on supported Android versions. A
successful JVM build or static scan is not runtime, device, live-provider, or
release proof.

Dependencies are added only for a demonstrated requirement. Their transitives,
initializers, permissions, manifest components, generated resources, native
libraries, licenses, update posture, and effect on the synthetic app must be
reviewed as part of composition—not inferred from a top-level declaration.

## 9. Static-analysis limitations and unknown behavior

Historical static evidence could identify destinations, operation shapes,
client interfaces, persistence references, resources, and SDK presence. It
could not establish:

- server source, validation, authorization, persistence, logging, retention,
  or rate limits;
- live tokens, scopes, rotation, configuration values, experiments, or feature
  enablement;
- actual notification delivery/routing or analytics collection;
- payment methods, checkout lifecycle, or completed-order semantics;
- behavior reachable only through server data, time, account state, or
  production configuration;
- whether every declared storage/provider component was used at runtime; or
- complete concurrency, lifecycle, accessibility, and error behavior.

These remain **Unknown** until supported by approved project-owned source,
official contracts, or authorized non-production observation. Client artifacts
must never be used to infer or recreate a third party's private backend.

## 10. Implications for current implementation

This model supports engineering by reminding maintainers where boundaries and
failure modes exist. It does not require historical screens, providers,
libraries, route names, visuals, or implementation expression.

Changes must follow current authority and preserve the accepted Multi-Brand
dependency direction: concrete application modules compose shared modules;
shared modules do not depend on concrete applications; `:mobile-core` does not
depend on `:firebase`; and shared code does not branch on concrete brand names.
Provider, persistence, runtime identity, release, and security changes require
their own current evidence and validation.

P3-16 production/release readiness remains not started. Nothing in historical
analysis, repository publication, a build, or CI establishes production
identity, signing, service configuration, public associations/callbacks,
privacy declarations, support/rollback ownership, or remote-deletion proof.
