# Storefront Integration Proof

Date: 2026-08-06
Gate status: **PASS**

## Owned environment and configuration

- The project-owned, paid-but-unlaunched Gürbakır store remains the owner-designated non-production environment. Plan type is not used as an environment classifier.
- The dedicated **Gürbakır Android Dev** Headless storefront, API `2026-07`, controlled public Storefront token, and ignored `config/local.properties` boundary were reused. No Admin credential, token rotation, client recreation, or reference third-party reference service was involved.
- Existing owned shop/catalog read evidence remains valid. This checkpoint added a separately opt-in, bounded mutation proof using only a public Storefront client and existing saleable catalog merchandise.

## Implemented cart boundary

- Generated Apollo operations now cover catalog variants plus cart create, read, add, update, remove, and Customer Account buyer-identity update. All operations use the checked-in API `2026-07` schema.
- `ApolloStorefrontGateway` validates line counts and quantities, preserves the complete opaque cart ID, accepts Shopify's HTTPS checkout URL including its required query component, maps only stable GraphQL/user-error codes, and never renders raw cart IDs or checkout URLs.
- `CartCoordinator` serializes create/restore/mutation operations, retains the last valid cart across transient failures, clears only definitive invalid/expired carts, rejects an unexpected cart-ID change, and applies a bounded 30-day local lifetime.
- `AndroidKeystoreCartSessionStore` persists only the full opaque cart ID and expiry in an environment-specific AES-GCM Android Keystore envelope. The codec is versioned and bounded; malformed or trailing data fails closed. The approved Android 13 phone executed round-trip and corruption fail-closed instrumentation tests, and the cart restored after force-stop/reinstall/relaunch.
- App-level commerce coordination uses an authenticated buyer token only when an already valid Customer Account session exists. The token remains inside sensitive wrappers and a typed `CartBuyerIdentityInput`.

## Automated and owned-store evidence

Local tests cover:

- generated request/header and typed response mapping;
- create/add/update/remove/load and buyer-identity inputs against MockWebServer;
- invalid local input, unsupported merchandise, unsafe URLs, GraphQL codes, warning codes, and redaction;
- persistence lifetime, expiry, secure-store failure, transient retention, definitive invalid-cart clearing, and payload corruption;
- deterministic app state for empty and active cart states.

The opt-in owned proof performed this bounded lifecycle against the verified Gürbakır store:

1. discover an available variant within at most ten catalog pages;
2. create a synthetic cart;
3. remove its initial line;
4. add the synthetic line again;
5. execute a quantity update;
6. reload the cart by its complete opaque ID;
7. remove every remaining line and verify an empty cart.

The physical proof then repeated the customer-linked quantity-one cart lifecycle in the Compose harness. Shopify normalized the requested increment back to quantity one and returned one bounded safe warning; the app preserved valid state rather than treating normalization as transport failure. Checkout completion later cleared the encrypted local cart, and the declined test path retained it until explicit cleanup.

No customer, order, checkout completion, or payment mutation occurred. The test includes best-effort cleanup after any post-create failure and prints no token, cart ID, checkout URL, product ID, or response message.

Commands:

```powershell
.\gradlew.bat :storefront:testDebugUnitTest --no-daemon --no-configuration-cache --console=plain
.\gradlew.bat :storefront:testDebugUnitTest -PgurbakirRunOwnedCartProof=true `
  --no-daemon --no-configuration-cache --console=plain
```

Final owned mutation result after the mapping refactor: **BUILD SUCCESSFUL in 33s**; 27 actionable tasks, one executed and 26 up-to-date. The ordinary lane skips both owned-service tests unless their explicit properties are set.

## Claim boundary

Gate 4 is `PASS` for the Phase 2 foundation: versioned typed operations, real owned-service cart lifecycle, error behavior, redaction, physical Keystore persistence/restoration, and valid checkout-URL handoff are proved. This does not mean a production cart screen or the full application is complete. Checkout lifecycle and Bogus/Test evidence are recorded separately in `CHECKOUT-KIT-PROOF.md`.

Contracts follow Shopify's current [cart management guidance](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart/manage) and [Storefront cart overview](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart).
