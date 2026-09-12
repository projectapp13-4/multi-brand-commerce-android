# Customer Account OAuth Proof

Date: 2026-08-06
Gate status: **PASS**

## Owned service configuration

- The signed-in Shopify Admin was verified as the project-owned, owner-designated non-production Gürbakır store.
- The pre-existing Public Web Customer Account client and its Storefront token were preserved because their consumers could not be attributed confidently.
- A separate Headless storefront, `Gürbakır Android Dev`, is dedicated to this Android project. Its Customer Account client is Public Mobile with the discovery-derived callback and required scopes `openid email customer-account-api:full` saved.
- Store domain, API version, controlled public Storefront token, public mobile client ID, discovery endpoints, scopes, and callback remain only in the approved ignored typed configuration boundary. No Admin token, client secret, backend credential, or private key entered the app.
- No further routine Shopify Admin mutation is required for this gate.

## Implemented mobile boundary

- [`CustomerAccountAuthorizationBrowser.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorizationBrowser.kt) uses AppAuth `0.11.1` only for the system-browser authorization and end-session surfaces; there is no embedded WebView.
- [`CustomerAccountAuthorization.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorization.kt), [`Pkce.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/Pkce.kt), and [`OAuthTransaction.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/OAuthTransaction.kt) enforce S256 PKCE, independent high-entropy state/nonce, exact callback routing, one-time consumption, expiry, duplicate-key rejection, and redacting authorization-code wrappers.
- [`CustomerAccountDiscovery.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountDiscovery.kt) retrieves bounded no-redirect OIDC and Customer Account discovery documents from the exact trusted shop host and validates issuer, HTTPS endpoints, authorization-code/S256/RS256 support, and the Customer Account GraphQL endpoint.
- [`CustomerAccountTokenClient.kt`](../../account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenClient.kt) performs exact form-encoded authorization-code and refresh POSTs with OkHttp. The current Shopify response may omit `token_type`; omission is accepted, while a present value must be `Bearer`. Redirects, raw response messages, missing fields, invalid scope/expiry, and invalid ID-token claims fail closed.
- [`CustomerAccountGateway.kt`](../../account/src/main/kotlin/com/gurbakir/account/CustomerAccountGateway.kt) owns a separate generated Apollo schema/client and sends the Customer Account access token in the current Shopify `Authorization` contract without adding a guessed `Bearer` prefix. Tokens and customer fields are not logged or rendered.
- [`AndroidKeystoreCustomerSessionStore.kt`](../../account/src/main/kotlin/com/gurbakir/account/session/AndroidKeystoreCustomerSessionStore.kt) encrypts the bounded versioned session envelope with an environment-specific non-exportable Android Keystore AES-GCM key and fresh IV. Corruption, invalidation, expiry, and logout clear local material.
- [`CustomerAccountSessionCoordinator.kt`](../../account/src/main/kotlin/com/gurbakir/account/session/CustomerAccountSessionCoordinator.kt) serializes exchange, restore, refresh, and logout; clears rejected/superseded sessions; and retains encrypted state only for a safe transient refresh retry.
- The app coordinator and ViewModel expose only generic proof states and one-shot browser effects. Callback parameters, tokens, identity, and raw provider errors never cross the UI/log boundary.

## Physical-device evidence

An approved Samsung Android 13 phone was connected with USB debugging and the development APK was installed and visibly opened. Using only a synthetic test account, the device proved:

1. system-browser authorization and return through the registered custom callback;
2. exact callback/state/nonce/PKCE validation and live authorization-code exchange;
3. a typed Customer Account identity query without rendering identity data;
4. live refresh;
5. Android Keystore session round-trip plus restoration after force-stop/reinstall/relaunch;
6. logout/end-session handling, local credential clearing, and signed-out restoration.

The account connected suite executed three Android tests with zero failures/skips: Keystore round-trip, corruption fail-closed, and exact token POST contract. The development and staging app suites each executed nine tests with zero failures/skips at the OAuth/Checkout checkpoint. Physical proof is not inferred from APK compilation.

Relevant command group:

```powershell
.\gradlew.bat --no-parallel `
  :account:connectedDebugAndroidTest `
  :storefront:connectedDebugAndroidTest `
  :app:connectedDevelopmentDebugAndroidTest `
  :app:connectedStagingDebugAndroidTest
```

Result at that device checkpoint: **BUILD SUCCESSFUL in 1m56s**; 23 tests executed, zero failures/skips. The app suites later expanded and reran successfully; `DEVICE-TEST-EVIDENCE.md` records the authoritative current total of 27 unique tests without double-counting these historical app executions.

## Claim boundary

Gate 5 is `PASS` for the Phase 2 Customer Account foundation. This proves the owned mobile client, current discovery/token/GraphQL contracts, physical redirect/PKCE/session lifecycle, and Keystore persistence. It does not approve production callbacks, production signing, broad shared Customer Account permissions, real customer data, or the subsequent product account screens.

Authority: [Shopify Customer Account API](https://shopify.dev/docs/api/customer/2026-07) and [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html).
