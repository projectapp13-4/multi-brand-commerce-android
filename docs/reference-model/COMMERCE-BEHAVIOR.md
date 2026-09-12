# Commerce Behavior Reference Model

## 1. Status, scope, provenance, and authority

This is a compact, brand-neutral preservation of product and interaction
knowledge that materially informed the Android implementation. It was derived
from historical static reference analysis and then reconciled against the
approved Phase 3 product decisions.

It is not a specification, parity mandate, release claim, or source-code
authority. Current approved requirements, source, architecture, tests, and the
Phase 3 acceptance matrix take precedence. Historical observations that were
not reproduced in a project-owned environment remain observations, not facts
about a live service.

Confidence terms:

- **Observed:** directly represented by the preserved historical evidence.
- **Inferred:** a likely relationship supported by multiple observations.
- **Unknown:** static evidence could not establish runtime or server behavior.

No decompiled expression, obfuscated symbol, reference credential, competitor
branding, private path, or forensic navigation information is retained here.

## 2. Feature and commerce-domain inventory

The historical model contained 24 feature groups:

1. bootstrap and splash;
2. onboarding;
3. home merchandising;
4. category browsing;
5. product listing, filtering, and sorting;
6. product search and history;
7. product detail and related products;
8. wishlist;
9. cart lifecycle;
10. Shopify checkout;
11. login and session;
12. account creation and verification;
13. password recovery and update;
14. profile;
15. address create/read/update/delete and default selection;
16. orders and order detail;
17. account deletion;
18. agreements, pages, and permissions;
19. web content;
20. push notifications;
21. force update and remote configuration;
22. session expiry and logout;
23. analytics and crash reporting; and
24. localization and remotely supplied brand content.

These groups organize evidence; they do not assert that every capability is
present or required in the current application. Current dispositions include
intentional differences, deferrals, non-applicable legacy flows, and externally
blocked business operations.

## 3. Navigation model and user-journey transitions

The observed navigation surface comprised 25 typed destinations across
startup, home, catalog, search, product, wishlist, cart, account, order, legal,
and web-content journeys. The important reusable relationship is the journey
shape, not the historical route names:

```text
startup -> home
home/categories/search -> product list -> product detail -> cart -> checkout
home/search/product detail -> wishlist -> product detail
account access -> profile/addresses/orders/legal/support/deletion request
order list -> order detail -> allowlisted tracking or support content
session expiry -> terminal reset -> account-access recovery
deep link -> validated supported target -> guarded fallback when unavailable
```

Parameters were limited to identifiers and optional selection context: address
ID, order ID, product ID, optional variant ID, optional category/search inputs,
and optional web-content metadata. The current implementation uses its own
typed routes and rejects or safely recovers from unsupported destinations.

Application-owned capabilities and destination ordering determine what is
reachable. A route is not globally valid merely because shared code contains a
screen. Disabled or missing capabilities must recover to a safe available
destination without exposing a hidden flow.

## 4. Domain behavior

### Startup and onboarding

Startup should resolve local configuration and durable state deterministically,
show a bounded loading surface, and reach a safe application destination even
when an optional provider is absent. Remote content must not be able to brick
launch. The current product intentionally avoids a marketing carousel;
onboarding is limited to setup that cannot be inferred safely.

### Home

Home composes merchant-backed content into independently designed sections.
Each section handles its own availability so one failed or empty source does
not erase useful content from other sections. Items route through validated
catalog/product targets; promotional or external content must use an approved
allowlist.

### Categories and catalog

Categories lead into collection/product listing. Listings use cursor-based
pagination and expose only filter/sort controls supported by the provider and
approved product contract. Refresh replaces stale first-page state only after
a successful response; next-page failure retains already loaded items.

The current Multi-Brand implementation obtains Categories from a bounded,
application-selected Shopify Menu, validates collection targets, preserves the
menu's useful ordering, and rejects unsupported or conflicting entries. This
is current source behavior and supersedes any historical packaged category
list.

### Search

Search is debounced, cancellable, and latest-request-wins. Blank queries do not
perform a provider search. Bounded local history is clearable and must not
silently become cross-account data. Search results reuse product-list/detail
contracts while preserving query-specific empty and retry states.

### Product detail

Product detail presents safe media, product information, availability, variant
selection, related items when available, wishlist state, and cart handoff. A
valid purchasable variant is selected explicitly; price and availability must
correspond to the chosen variant. A missing product, invalid deep link, or
unavailable selection must fail safely rather than create an invalid cart line.

### Wishlist

Wishlist is intentionally local and account-independent in the current
product. It may navigate to product detail and reflects add/remove state
optimistically only when persistence semantics remain truthful. A future sync
provider is an extension point, not an implied current feature.

### Cart

Cart owns line creation, quantity changes, removal, totals, recovery, and the
handoff to checkout. Customer association has explicit ownership states so
logout or terminal session reset cannot expose a customer-associated cart as
anonymous. Mutation failure retains or restores a coherent cart representation
and offers a bounded retry.

### Checkout

Checkout is launched through the project-owned adapter around the official
Shopify Checkout Kit. Completion, cancellation, and failure are distinct
outcomes. A checkout URL is treated as provider output, validated by the
adapter boundary, and never presented as proof of a completed order. The test
boundary must not submit real payments or orders.

### Account access and session

The current product uses Customer Account OAuth with PKCE and a hosted,
passwordless journey. Historical native login, registration, verification,
password-reset, and password-update screens do not create current parity
requirements. App return, cancellation, session expiry, restart, and help
states remain necessary user journeys.

### Profile and addresses

Profile exposes only approved fields. Address operations are customer-scoped,
market-aware, and explicit about validation failures and default selection.
Personally identifiable information must not appear in logs, analytics,
fixtures derived from real customers, or cross-account cache state.

### Orders

Order history and detail are private customer data. Loading, empty history,
partial fulfillment, provider failure, and session expiry remain distinct.
Tracking or support transitions use validated, allowlisted external content.

### Account deletion

The mobile application can submit the approved deletion request and clear
local customer state. It must not claim that remote deletion occurred merely
because local cleanup succeeded. Merchant acknowledgement, retention/SLA
execution, and proof of remote deletion remain externally unverified.

### Legal, support, permissions, and web content

Legal and support content must be project-owned and versioned or loaded from an
approved source. Runtime permissions are contextual, not a generic onboarding
checklist. External content uses an allowlisted browser or Custom Tab; a
generic credential-bearing WebView is intentionally excluded.

### Update, notifications, analytics, and localization

Optional configuration uses safe local defaults and cannot create an
unreviewed hard launch gate. Production push, analytics, and crash reporting
remain deferred unless current authority explicitly enables them. Turkish and
English resources are supported; locale, market, and brand configuration are
application-owned inputs rather than a runtime merchant switch.

## 5. State contracts

Every screen or domain operation should distinguish the states it can actually
represent:

| State | Contract |
|---|---|
| Loading | Bounded progress; retain useful prior content when a refresh is in flight |
| Success | Complete validated data for the requested operation |
| Empty | Successful response with no applicable content; not an error |
| Partial | Some useful content retained while one section/page/provider failed |
| Error | Typed, user-safe failure without credential or PII leakage |
| Retry | Repeats only the failed or explicitly refreshed work |
| Cancellation | Expected control flow; do not surface as a provider error |
| Refresh | Latest request wins; stale responses cannot replace newer state |

Mutation UIs must avoid impossible combinations such as showing both a
successful removal and the removed line, or treating a canceled checkout as a
failure. Pagination errors preserve prior pages. Session-terminal errors reset
all customer-bound state through the central session boundary.

## 6. Cross-domain relationships and invariants

- Home, Categories, Search, and Wishlist converge on the same validated product
  and collection identities.
- Product availability and selected variant govern whether cart mutation is
  permitted.
- Cart customer association follows session state; logout has explicit cart
  detach/quarantine behavior.
- Checkout consumes a valid cart/checkout URL but does not own cart persistence
  or order history.
- Profile, addresses, orders, and account deletion require an authenticated
  customer boundary.
- Deep links and notifications may request navigation but cannot bypass
  capability, authentication, allowlist, or route-validation checks.
- Market, locale, media origin, provider selection, and primary navigation are
  application-owned configuration inputs.
- Durable state is partitioned so environment, market, brand application, and
  account data do not collide.

## 7. Material UX and product observations

The useful historical lesson is consistent state communication across a broad
commerce journey: predictable back behavior, retained content during refresh,
explicit empty states, per-section recovery, clear destructive-action
confirmation, and continuity from discovery to purchase and account service.
These observations informed current acceptance criteria without authorizing
visual copying, competitor trade dress, or identical interaction expression.

Accessibility semantics, readable error copy, touch-target sizing, focus and
keyboard behavior, loading stability, and lifecycle recovery are product
quality requirements derived from current authority and tests—not from this
historical model alone.

## 8. Mapping to current contracts and intentional differences

The current Phase 3 acceptance matrix is the disposition authority. In
particular:

- onboarding, Search/history, Wishlist, account access, web content, update
  policy, localization, and remote branding intentionally differ from the
  historical observations;
- legacy password recovery/update is not applicable to the Customer Account
  architecture;
- push notifications, analytics, and crash reporting are deferred;
- account deletion and some owned legal/support inputs remain externally
  blocked; and
- Categories discovery now follows the Gate 6 Menu contract.

No historical feature becomes required merely because it appears in this
inventory. Any future change needs current requirements, architecture, tests,
and—where appropriate—a new Multi-Brand gate or release decision.

## 9. Unresolved behavioral observations

Static analysis did not prove live provider data, server-side validation,
Remote Config values, notification routing, payment methods, checkout
completion behavior, account-deletion execution, analytics collection, or
production service configuration. Those facts remain **Unknown** until proven
through approved project-owned source, official contracts, or authorized
non-production observation.
