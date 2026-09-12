# Current Gürbakır Audit

## Executive assessment

The existing `gurbakir` directory is a small, compilable proof of concept, not a production baseline. It proves that a project-owned Gürbakır Storefront endpoint and public client token can return catalog data, and it demonstrates a minimal Flutter/Riverpod/GraphQL flow. It does not implement a trustworthy account lifecycle, complete cart, production checkout integration, backend synchronization, Firebase, localization, release configuration, or a maintainable cross-platform identity.

Recommendation: preserve configuration knowledge and a few UI/flow observations; independently reimplement the application in the clean workspace.

## Build and project identity

| Area | Observed state | Assessment |
|---|---|---|
| Package | `baguette_jewellery` | Replace; unrelated/legacy identity |
| Android app ID | `com.example.baguette_jewellery` | Replace before any release |
| Android release signing | Debug signing configuration | Release blocker |
| iOS identity | legacy prototype brand display name and `com.example.baguetteJewellery` | Replace |
| Desktop identity | legacy prototype brand/default identifiers | Out of mobile scope; do not carry forward |
| App version | `1.0.0+1` | Prototype value only |
| API path | Storefront API `2026-04` | Working snapshot; choose the Phase 2 baseline deliberately |
| Git | No repository | New workspace needs an auditable repository |

## Actual architecture

```text
MaterialApp -> HomeScreen
  -> ProductListController -> GraphQLClient -> Shopify Storefront
  -> ProductDetailScreen -> CartController -> Shopify cart operations
  -> WishlistController -> Hive (Worker/metafield sync is a stub)
  -> ProfileScreen -> AuthController (in-memory mock token)
```

- Navigation is ad hoc `MaterialPageRoute`, with no typed route graph or deep-link model.
- Riverpod separates a few controllers from widgets, but there is no repository/service boundary.
- GraphQL responses are parsed through dynamic maps; no generated, schema-checked models exist.
- Hive holds GraphQL cache, cart ID, and wishlist IDs; no migration, expiry, encryption, or ownership model is defined.
- Error handling is mostly stringified exceptions and broad catch blocks.

## Feature audit

| Feature | Current reality | Classification |
|---|---|---|
| Home | First returned product becomes a hard-coded hero; remaining products form a horizontal list | Replace with independently designed merchandising model |
| Product list | Fetches first 20 products; pagination method exists but is never called | Refactor concept, replace implementation |
| Product detail | Title, one image, minimum price, first variant only | Replace |
| Cart | Create, get, and add line; stores cart ID in Hive | Potential contract knowledge only; replace controller/model |
| Checkout | Opens `checkoutUrl` in an external application | Replace with direct official Android Checkout Kit integration behind a project-owned adapter |
| Wishlist | Local Hive ID set; authenticated sync is a 500 ms no-op | Replace |
| Login/register | Any non-empty email/password produces a fixed mock token | Remove |
| Profile | Static name and four menu rows whose taps do nothing | Remove/reimplement |
| Categories/search/filter/sort | Absent | Implement independently |
| Orders/addresses/password/account deletion | Absent | Implement independently after auth decision |
| Firebase/push/remote config/analytics/crash | Absent | Audit fact unchanged; production direction is official native Android Firebase after owned configuration |
| Localization/accessibility | Hard-coded Turkish, several ASCII-only labels, no localization system | Replace |

## Correctness and reliability findings

- Product description is queried but discarded; product images are limited to one and variants to ten.
- `loadMore()` changes the whole state to loading, swallows failures, and has no UI caller.
- Adding a product silently chooses the first variant; if no variant exists it sends a product ID as a merchandise ID.
- Any add-line failure deletes the existing cart ID and creates a new cart, so transient errors can discard the user's cart.
- Cart quantity update, line removal, note, buyer identity, warnings, discounts, tax, and lifecycle handling are absent.
- Checkout launch failure has no user-visible error or recovery.
- The `AuthLink` reads an always-empty placeholder map and attempts an `Authorization: Bearer` header, while the app has no real Shopify customer operations.
- Auth state is in memory only. Restarting the app logs the user out; the mock token is not customer authentication.
- Wishlist rollback reloads the local box but no server mutation exists.
- `getOptimizedImageUrl()` is unreferenced.

## Security and configuration

- The public Storefront credential and endpoint are hard-coded in `lib/core/config/shopify_config.dart`. The value was not copied into any Phase 1 artifact.
- The configuration is functional: a read-only browser run loaded Gürbakır product data. This proves current reachability, not token governance, scope, rotation, or production readiness.
- There is no secure-storage dependency or implementation for customer/session credentials.
- The test asserts a literal store endpoint and only checks that the token is non-empty; it does not protect against leakage or test API behavior.
- Release signing uses debug keys.
- No Firebase configuration files, production signing material, or `.env` files were found outside generated/cache trees.

Future handling:

1. represent store domain, API version, and Firebase client identifiers as public client configuration through an approved environment/flavor and repository contract;
2. treat the public Storefront client token as extractable but controlled: permit required scanning while redacting ordinary output, prompts, logs, and reports;
3. never commit or log sensitive customer/OAuth tokens, Admin/backend credentials, service-account private material, or signing secrets;
4. store sensitive runtime tokens in Android Keystore-backed storage through a reviewed abstraction;
5. rotate/validate the current Storefront token according to project-owned Shopify policy before Phase 2 release work.

## Assets and remnants

- `assets/images/product_1.png` is a legacy prototype brand Jewellery logo.
- `product_2.jpg` through `product_5.jpg` are resized variants of the same legacy prototype brand campaign image.
- `products.json` is not JSON; it is a 103 KB legacy prototype brand "Sayfa bulunamadı" HTML response.
- `scrape.py` targets the old legacy prototype brand WordPress site, disables TLS hostname/certificate verification, and is unused.

All are `REMOVE` for the new project. No verified Gürbakır-owned visual asset was found in the prototype.

## Dependency assessment

| Dependency | Actual use | Future disposition |
|---|---|---|
| Flutter/Riverpod | UI and state/controllers | Do not migrate; retain only historical product-flow lessons |
| `graphql_flutter` | HTTP GraphQL client and Hive cache | Do not migrate; Apollo Kotlin is the independently selected typed GraphQL direction |
| Hive/Hive Flutter | GraphQL cache, cart ID, wishlist | Do not migrate; use Keystore-backed token storage, DataStore, and Room only by concern |
| Cached network image/shimmer | Product image presentation | Do not migrate; independently evaluate Coil Compose after design/performance tests |
| URL launcher | External checkout | Not sufficient for target checkout lifecycle |
| Freezed/JSON annotations | Declared but unused in handwritten code | Do not migrate; Kotlin/Apollo models own future typing |

## Tests

- The only unit test verifies three configuration facts; it passed.
- The `integration_test` file uses the legacy Flutter Driver API, fixed sleeps, live catalog/cart assumptions, and opens checkout. It is not safe or deterministic.
- There are no repository, parsing, cart, auth, navigation, accessibility, golden, failure-path, or offline tests.

The future project should treat the current tests as historical evidence, not reusable coverage.
