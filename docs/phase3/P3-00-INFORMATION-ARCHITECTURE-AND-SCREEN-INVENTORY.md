# P3-00 Information Architecture and Screen Inventory

Date: 2026-08-10

Status: **ACCEPTED P3-00 SPECIFICATION — NO ROUTES IMPLEMENTED**

Machine-readable row authority: [p3-00-screen-inventory.csv](p3-00-screen-inventory.csv)

## Production information architecture

The production hierarchy is progressive. A primary destination is registered and shown only after its slice passes.

```text
Startup gate
|- Conditional setup (normally absent)
`- Primary shell
   |- Home
   |  `- Product list -> Product detail -> Media viewer
   |- Categories -> Product list -> Product detail
   |- Search -> Product list -> Product detail
   |- Wishlist -> Product detail
   `- Account
      |- Signed-out state -> hosted Customer Account journey
      |- Profile
      |- Addresses -> create/edit address
      |- Orders -> order detail -> external tracking
      |- Legal and support index -> external owned page
      |- Local data controls
      `- Account deletion request (only after external gate)

Contextual, not primary: Cart -> hosted Checkout Kit sheet
Policy surfaces: optional update notice; hard update gate only if separately approved
Safety surface: route recovery
```

Working primary navigation is **Home, Categories, Search, Wishlist, Account**. On compact widths it is a bottom bar; on medium/expanded widths it may become a navigation rail while preserving the same five destination identities. Destination visibility is progressive: P3-01 initially exposes Home plus only other already-functional destinations. Cart is a contextual top-level action and destination that is completely absent until P3-06 passes.

## Route, entry, and back-stack contract

- Route models are project-owned serializable types. Pass stable identifiers, never tokens, checkout URLs, cart IDs, customer objects, arbitrary web URLs, or raw external payloads.
- Cold start resolves local configuration and protected state first. Remote Config is never required to choose a usable destination.
- Switching primary destinations restores each primary stack at its root. Re-selecting the active primary destination pops to its root. Back exits only from a primary root after Android system behavior is honored.
- Product list and detail are secondary stacks under Home, Categories, Search, or Wishlist. Back returns to the originating list with scroll/filter/search state when still valid.
- Cart is a contextual route. Back returns to the originating product/list/home state. Checkout overlays the cart through Checkout Kit; cancel/failure returns to the same retained cart. Genuine completion normalizes the stack before routing to an approved success destination.
- Signed-out Account is a valid Account state, not a separate password screen. Protected deep links first store only an allowlisted destination enum, launch the hosted journey, then consume it once after a valid session. Failure/cancel returns to signed-out Account.
- Terminal session expiry removes protected Account routes from the back stack. It detaches or quarantines any customer-associated cart before signed-out UI can expose it.
- Legal/support routes are public and remain reachable from signed-out Account, route recovery, and any approved update gate.
- Unknown, replayed, malformed, not-yet-implemented, or unauthenticated routes go to `ROUTE_RECOVERY`; they never create placeholder destinations.
- Notifications are deferred. The Phase 2 notification proof route is not a production entry point. Future routes require a new allowlisted source/auth/session matrix.

## Deep-link and external-source matrix

| Source | Allowed Phase 3 target | Auth rule | Failure/replay behavior |
|---|---|---|---|
| Owned HTTPS product link | Product detail after P3-04 | Public | Validate product ID; unknown or unavailable target goes to route recovery/browse |
| Owned HTTPS collection link | Product list after P3-02 | Public | Validate collection and market; never synthesize a missing destination |
| Customer Account OAuth callback | Hosted journey coordinator only | One-time transaction | Exact route/state/nonce/PKCE; reject malformed, replayed, expired, or mismatched callbacks |
| Checkout/offsite return | Existing Checkout Kit/coordinator only | Cart/session policy | Never infer completion from URL; coordinator callback/process reconciliation owns outcome |
| Legal/support link | Local page ID mapped to owned URL | Public | Exact allowlist; unsafe or offline route stays on local index |
| Order tracking | Verified order fulfillment to carrier allowlist | Signed in | No raw route URL; reject unknown carrier/redirect and offer support |
| Firebase notification | None in Phase 3 | Not applicable | Ignore production routing; P3-16 proves proof route/registration is absent |
| Unknown app link or internal restore | Route recovery | Depends on target | Redact raw input; offer only functional safe destinations |

## Complete production screen/surface inventory

The CSV records every required field: purpose, owner group/slice, entry and exit, arguments, auth, state holder, Phase 2 dependency, data source/persistence, loading/empty/success/recoverable/terminal/offline states, accessibility, localization/content, analytics policy, evidence/rationale, and release disposition.

| Screen or surface | Slice | Auth | Key disposition |
|---|---|---|---|
| `STARTUP_GATE`, `CONDITIONAL_SETUP`, `HOME`, `ROUTE_RECOVERY` | P3-01 | Mixed/public | Conditional setup normally absent; Home blocked on merchant packet |
| `CATEGORIES`, `PRODUCT_LIST`, `FILTER_SORT` | P3-02 | Public | Only approved taxonomy and supported controls |
| `SEARCH` | P3-03 | Public | Local bounded history; no query telemetry |
| `PRODUCT_DETAIL`, `MEDIA_VIEWER` | P3-04 | Public | Explicit valid variant; cart action absent until P3-06 |
| `WISHLIST` | P3-05 | Public/local | Device-local and account-independent |
| `CART` | P3-06 | Mixed | First cart action; four-state ownership |
| `CHECKOUT_SHEET` | P3-07 | Mixed/hosted | External Shopify surface; exact callback lifecycle |
| `LEGAL_SUPPORT_INDEX`, `EXTERNAL_OWNED_PAGE` | P3-08 | Public | Versioned merchant-owned routes implemented with exact allowlist and Custom Tabs; P3-08 complete |
| `ACCOUNT`, `HOSTED_ACCOUNT_JOURNEY`, `LOCAL_DATA_CONTROLS` | P3-09 | Mixed | Hosted passwordless semantics; no password screens |
| `PROFILE` | P3-10 | Signed in | Approved fields only |
| `ADDRESS_LIST`, `ADDRESS_FORM` | P3-11 | Signed in | Market-aware PII with no default local cache |
| `ORDER_LIST`, `ORDER_DETAIL`, `TRACKING_EXTERNAL` | P3-12 | Signed in | Verified customer data and allowlisted carriers only |
| `ACCOUNT_DELETION` | P3-13 | Signed in/re-auth | Externally blocked; no placeholder or false completion |
| `UPDATE_NOTICE`, `HARD_UPDATE_GATE` | P3-14/P3-16 | Public | Notice optional; hard gate absent by default |

`CHECKOUT_SHEET`, `HOSTED_ACCOUNT_JOURNEY`, `EXTERNAL_OWNED_PAGE`, and `TRACKING_EXTERNAL` are external/hosted surfaces, not Compose screens, but they remain in the inventory because their transition, failure, privacy, accessibility, and return contracts are product-visible.

## Phase 2 proof-surface replacement ledger

| Current Phase 2 surface | Current role | Production disposition | Reusable layer retained | Owning removal/replacement slice |
|---|---|---|---|---|
| `FoundationHome` / `FoundationScreen` | Configuration and integration status dashboard | Remove from production route graph; replace with startup gate and Home | App configuration, theme, localization, typed errors | P3-01 |
| Generic `IntegrationDetail` / `IntegrationDetailScreen` | Placeholder integration explanation | Remove; never map to a production placeholder | Typed route conventions only | P3-01/P3-02 |
| `CustomerAccountProofScreen`, proof ViewModel/controller | Manual OAuth/session proof | Replace with Account and hosted journey; proof actions absent from release | Discovery, browser, PKCE, token, callback, session, Customer Account client | P3-09 |
| `CommerceProofScreen`, proof ViewModel/controller | Synthetic cart and Checkout controls | Replace with product cart/checkout flows; Bogus/manual controls absent | Storefront gateway, cart coordinator/Keystore, Checkout adapter/coordinator | P3-06/P3-07 |
| `FirebaseProofScreen`, proof ViewModel/controller | Manual Remote Config/FCM registration proof | Remove from production; optional separately gated internal diagnostic surface may exist only outside release routing | Typed Remote Config safe defaults and disabled consent boundary | P3-14/P3-16 |
| `MainActivity` notification proof nonce/route | Synthetic FCM tap target | Remove from production route and release graph; production push stays deferred | Future route allowlist pattern only | P3-16 |
| Synthetic labels, products, customer/cart/order results | Visible engineering evidence | Test fixtures/evidence only; never production content | Deterministic fakes and tests | Progressive, final proof at P3-15/P3-16 |

Any retained diagnostic surface must be development-only, unreachable from production navigation, excluded from release variants/artifacts, and unable to expose credentials or target values. P3-00 changes none of these source files.

## Reference-evidence boundary

The bounded evidence helper confirmed 25 serialized destinations, including Home, categories, product list/detail, search, wishlist, cart, account/profile/address/order, onboarding/splash, agreements/pages/permissions, auth/verification/password, and WebView destinations. Exact destination and parameter rows are `FACT`; `ProductListScreenDestination` and `WebViewScreenDestination` parameter interpretation remain the query helper's `MEDIUM` confidence. The complete directed runtime graph, Worker behavior, live URLs, and live remote state remain `UNKNOWN`.

Gürbakır intentionally does not reproduce the reference credential screens, password flows, generic WebView, visual hierarchy, brand, content, or assets. This inventory is independently designed against the accepted product decisions and current official platform/service contracts.
