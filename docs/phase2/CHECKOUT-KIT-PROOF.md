# Checkout Kit Proof

Date: 2026-08-06
Gate status: **PASS**

## Implemented boundary

- Shopify's official Android Checkout Kit is pinned at `3.5.4` behind project-owned `CheckoutAdapter` types.
- [`DefaultCommerceProofController`](../../app/src/debug/kotlin/com/gurbakir/mobile/CommerceProofController.kt) refreshes the cart immediately before preload/present, passes only a fresh allowlisted checkout URL, invalidates preloaded state after each cart mutation, retains the cart on cancellation/failure, and clears it only after `Completed`.
- [`CheckoutUrlPolicy`](../../checkout/src/main/kotlin/com/gurbakir/checkout/CheckoutAdapter.kt) permits only HTTPS on the exact configured owned-store host, standard port, no user-info, and no fragment. The required opaque query remains in a redacting wrapper.
- Permissions, geolocation, file selection, unknown external links, web-pixel forwarding, raw SDK messages, and payload logging fail closed. Analytics and Crashlytics remain absent/disabled pending policy.
- Local JVM/Compose tests cover URL policy, typed failure categories, completion/cancellation precedence, cart retention, preload invalidation, and redacted UI state.

## Physical-device and owned-service evidence

On the approved Android 13 phone, using the owner-designated non-production Gürbakır store and only synthetic data:

1. an authenticated, customer-linked Storefront cart was created with one merchandise line;
2. remove, add, reload, and persistence paths were exercised; Shopify normalized a requested increment to quantity one and returned one bounded safe warning rather than an app failure;
3. the encrypted cart restored after force-stop/reinstall/relaunch;
4. Checkout Kit preload succeeded;
5. initial presentation correctly showed the store's password page inside Checkout Kit because storefront password protection was enabled; entering the visitor password revealed the real checkout without launching the store publicly;
6. closing Checkout produced `Cancelled` and retained the quantity-one cart;
7. the official Bogus Gateway approved test input completed checkout, Checkout Kit emitted `Completed`, and the local cart was cleared;
8. force-stop/relaunch showed no restorable cart, proving post-completion persistence cleanup;
9. a second synthetic cart used the official Bogus declined test input; Shopify displayed rejection, no completion was emitted, closing produced `Cancelled`, and the cart remained;
10. all remaining synthetic cart lines were removed after the decline proof.

A read-only Shopify Admin inspection then verified one corresponding paid **test** order. The declined attempt did not create another visible order. No raw checkout URL, order/customer/address/payment value, or identifier was copied into evidence.

No real payment method or live payment was used. The existing Public Web client/token and store password protection were preserved. No payment, client, token, or project was rotated or recreated.

## Automated evidence

The device checkpoint command executed the account/storefront/app connected suites with 23 tests and zero failures/skips. The Storefront suite contributed two cart Keystore tests; each app variant contributed the then-current nine tests. The expanded app suites later executed eleven tests per environment; `DEVICE-TEST-EVIDENCE.md` records the authoritative current total of 27 unique tests without retroactively changing or double-counting this checkout checkpoint.

Local command group:

```powershell
.\gradlew.bat :checkout:testDebugUnitTest :storefront:testDebugUnitTest `
  :app:testDevelopmentDebugUnitTest :app:testStagingDebugUnitTest `
  :app:assembleDevelopmentDebugAndroidTest :app:assembleStagingDebugAndroidTest `
  --no-daemon --no-configuration-cache --console=plain
```

## Claim boundary

Gate 6 is `PASS` for the Phase 2 cart-to-Checkout foundation: owned checkout rendering, preload, cancellation, completion, decline/no-false-completion, persistence restoration/cleanup, and Shopify test-order reconciliation were physically observed. Full product cart/checkout UI, production payment configuration, production release identity, exhaustive offsite-provider behavior, and real-customer transactions are outside this gate.

Authority: [Shopify Checkout Kit](https://shopify.dev/docs/storefronts/mobile/checkout-kit) and [Shopify test orders/Bogus Gateway](https://help.shopify.com/en/manual/checkout-settings/test-orders/payments-test-mode).
