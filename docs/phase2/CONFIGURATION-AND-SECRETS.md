# Configuration and Secrets

Date: 2026-08-06
Scope: Phase 2 native Android foundation

The security skills drove three foundation decisions: fail visibly when required configuration is missing, make secret-bearing types redact themselves, and keep every customer/server/signing credential outside general logging and configuration maps. This document is a policy and implementation contract; it contains no credential values.

## Brand and environment model

- Code namespace: `com.gurbakir.mobile`; this is independent from distributable application IDs.
- Environments: `development` and `staging` build flavors.
- Application IDs: non-production placeholders only. No final production application ID exists.
- Brand: the app-owned `GurbakirBrand` compile-time selection supplies neutral typed identity, design-token, locale, asset, legal-link, feature, and analytics-namespace contracts. Shared commerce modules remain brand-neutral and do not select a brand at runtime.
- Localization: Turkish is the baseline resource set and English is the current alternate resource set. User-visible foundation text is resource-backed; identifiers and test selectors do not depend on translated text.
- Scope guard: the initial brand feature set is deliberately empty. Its typed feature schema is a configuration boundary, not authorization to implement any of the 24 candidate feature groups.
- Assets and legal links: unapproved logo/icon references and legal URLs remain absent rather than being invented. HTTPS validation applies when those values are approved and supplied.
- Tracked defaults: `config/local.defaults.properties` contains only safe empty/disabled values.
- Local provisioning: `config/local.properties` is ignored and may contain only verified project-owned non-production public-client configuration.
- Firebase files: every `google-services.json` is ignored. A complete set consists of development debug/release and staging debug/release source-set files; partial sets fail Gradle configuration. The dedicated validator checks exact packages, one project per environment, and distinct development/staging project identities.

The app reads a fixed allowlist of keys during Gradle configuration and emits typed BuildConfig fields. It does not read arbitrary environment variables into the binary or accept remote executable configuration.

## Configuration classes

| Class | Examples | Provisioning | Storage and Git policy | Logging/CI policy | Rotation owner | Allowed runtime consumers |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Public client configuration | Store domain, Shopify API version, Customer Account public client ID/endpoints/callback, Firebase client identifiers, legal/support URLs | Verified owned admin or approved untracked local/CI inputs after application IDs exist | Ignored local file or approved variant resources; may be extractable from APK; values are not copied into docs | Log only normalized environment/status, never full URLs containing query data; CI uses non-production variables only where live tests require them | Shopify/Firebase/project owner | Typed configuration and the specific integration client |
| 2. Controlled extractable Storefront token | Storefront public access token | Owned Shopify development/test store; create/rotate only after identity verification | Ignored local/CI input, then BuildConfig and APK by design; never committed or printed | `ControlledPublicToken.toString()` is always redacted; ordinary tests use fakes; secret scanning may inspect but must redact | Shopify store owner | Storefront HTTP authorization interceptor only |
| 3. Customer OAuth/session material | Authorization code, PKCE verifier, access token, refresh token, ID token | Runtime Customer Account Mobile client OAuth authorization-code flow with S256 PKCE | Never BuildConfig/Git/docs; encrypted AES-GCM ciphertext in private SharedPreferences with a non-exportable Android Keystore key | `SensitiveToken.toString()` is redacted; no log, analytics, crash, screenshot, fixture, or report value; CI uses deterministic synthetic tokens only | Customer session subsystem; user revokes via logout; Shopify controls remote expiry/revocation | Account token/end-session clients, Customer Account GraphQL auth, authenticated-checkout handoff where officially required |
| 4. Cart and checkout capability values | Complete cart ID including its secret query component; checkout URL | Runtime Storefront cart responses | Never Git/docs/logs; only the full cart ID plus expiry is encrypted in environment-specific Android Keystore AES-GCM storage; checkout URL stays memory-only | Sensitive wrappers and cart snapshots redact IDs/URLs; tests use synthetic values and stable error codes only | Cart coordinator clears explicit invalid/expired/completed state; Shopify expires abandoned carts | Storefront cart mutations and exact-host Checkout Kit handoff only |
| 5. Backend/Admin/server secrets | Admin API tokens, backend credentials, webhook secrets, confidential OAuth client secrets | Approved backend/CI secret manager only, if a future backend is authorized | Never mobile binary, local Android config, Git, docs, APK, test fixture, or analytics | Never exposed to Android CI jobs or logs; server-specific scanning/rotation outside this app repository | Backend/service owner | Project-owned server process only |
| 6. Signing/private automation material | Upload/release keys, signing passwords, service-account JSON, private keys | Owner-controlled keystore/HSM and CI secret mechanism after release ownership exists | Never Git or APK assets; public certificate fingerprints may be documented after approval | No private bytes/passwords in logs, reports, prompts, screenshots, or general CI artifacts | Release/security owner | Signing job only; mobile runtime has no access |

Firebase Android API keys/client identifiers belong to class 1 and are not authorization. Firebase Security Rules, API restrictions, App Check where justified, authenticated authorization, and product-specific controls protect data. No Firebase database/storage/authentication product is authorized merely because the client config is public.

## Customer session and cart storage design

`AndroidKeystoreCustomerSessionStore` and `AndroidKeystoreCartSessionStore` create separate environment-specific AES keys in `AndroidKeyStore` on API 23+. Keys are non-exportable through the application API, randomized encryption is required, and each write uses AES/GCM with a fresh IV. Only IV plus ciphertext are written to private SharedPreferences. The versioned customer payload contains bounded access, refresh, and ID tokens plus expiry; the cart payload contains only the bounded complete cart ID plus expiry. Corrupt, undecryptable, invalidated, or structurally invalid material is cleared rather than returned.

Deliberate limitations:

- User authentication is not required for each key use because the app must restore a normal customer session after restart. This protects against straightforward file extraction, not an attacker controlling an unlocked/rooted device or the application process.
- Android Keystore availability and hardware backing vary by device. The current contract does not claim StrongBox.
- The runtime necessarily holds decrypted token strings briefly while making an authorized request; redaction and short consumer scope reduce accidental exposure but cannot defeat a fully compromised process.
- Backup is disabled and data-extraction rules exclude app preferences/files. Logout removes stored ciphertext; remote token revocation still depends on the Shopify endpoint contract.
- Keystore invalidation or ciphertext corruption results in local session loss and a required sign-in, never an insecure fallback.

The approved Android 13 phone proved customer/cart encrypted round-trip, corruption fail-closed, process restart restoration, logout/completion clearing, and no sensitive UI output. JVM tests separately cover payload bounds/redaction and coordinator policy; neither test class is substituted for the other.

## Logging, analytics, and crash policy

`ProjectLogger` accepts stable low-cardinality event IDs and an enum allowlist of attributes. `RedactionPolicy` removes bearer tokens, Shopify secret-like prefixes, Google API-key shapes, JWT shapes, and credential query fields. That is defense in depth, not permission to pass sensitive strings to the logger.

Never record:

- customer names, email, phone, addresses, orders, cart attributes containing private content, or free-form GraphQL errors;
- access/refresh tokens, authorization codes, PKCE verifier, state, cookies, Storefront token, payment/checkout details, or full checkout URLs;
- credential-bearing request/response bodies, screenshots, Web Pixel payloads, or third-party exception descriptions.

Analytics and Crashlytics collection, FCM auto-initialization, FCM delivery-metrics export, and advertising-ID collection default to disabled in the manifest. Analytics and Crashlytics SDKs are absent until product/legal policy is approved. Current manual FCM registration begins only after notification permission and an explicit app action; a non-sensitive app-private consent boolean makes unregister reachable after restart. The redacted app-instance target is temporary debug proof data, never tracked/logged/rendered, and release builds do not record it. Checkout Kit logging is configured to `ERROR`, web-pixel forwarding is disabled, and SDK error descriptions/completion payload details do not cross the project adapter.

The physical development proof used the current Firebase Installation ID only after explicit permission/action, delivered one synthetic message, then explicitly unregistered. The app-private target, temporary Windows transfer file, diagnostic screenshots, and clipboard contents were removed after the proof; no identifier is retained in tracked evidence.

## Route and callback policy

- HTTPS URLs require a valid host, no user info, and the normal HTTPS port.
- External links and notification routes are validated by project-owned allowlists before an intent is created. The FCM service is non-exported, accepts one exact route field, ignores remote display text, and creates only an explicit immutable app `PendingIntent` with fixed local notification content.
- Checkout Kit external-link callbacks are forwarded as data; the SDK's permissive default launcher is not used.
- Checkout permission, file chooser, and geolocation requests are denied/cancelled by default pending explicit product and privacy requirements.
- AppAuth's redirect receiver is packaged with the exact ignored `shop.{shop_id}.*` scheme derived from verified Shopify discovery. The same callback is persistently registered on the dedicated Shopify Public Mobile client. The full callback route is allowlisted before OAuth data is consumed. OAuth state and nonce are high entropy; state is one-time, constant-time compared, expiry-bounded, and consumed on the first valid-route callback attempt.

## CI and repository enforcement

- `.gitignore` excludes local config, Firebase files, signing material, build output, and known private-key formats.
- `.gitleaks.toml` extends the default rules and excludes generated/cache directories plus only the four exact ignored Firebase public-client files. Those four files are separately checked by `Test-FirebaseConfiguration.ps1` for exact package/environment mapping and prohibited server/private-key fields; all other source and tracked history remain under redacted Gitleaks scanning.
- Gradle dependency verification checks artifact hashes and dependency locking records resolved graphs.
- GitHub Actions receive read-only repository permission, pin third-party actions to full commit SHAs, disable checkout credential persistence, and use empty safe defaults for normal builds.
- Live integration jobs, if later added, must target only verified project-owned non-production systems and use environment-scoped CI controls. They must never make paid orders or mutate real customers.

## Provisioning sequence and current state

1. Development/staging application IDs exist as provisional non-production placeholders.
2. The owner-designated paid-but-unlaunched Shopify store is verified as the non-production Phase 2 environment; plan type does not change this classification.
3. Owned Storefront domain/API/token configuration is present only in ignored local configuration and the generated Apollo read proof passed without printing token material.
4. The pre-existing Public Web Customer Account client was preserved because its consumers were not confidently attributable. A dedicated Headless storefront named `Gürbakır Android Dev` now owns a Public Mobile client, the verified callback, and an ignored public client ID. Runtime OIDC and Customer API discovery remain authoritative for issuer/endpoints.
5. The owner-provided `Shopify App` Firebase project maps to development; its legacy `Shopify.App` registration was preserved. A separate `Gurbakir Android Staging` Spark-plan project maps to staging. Analytics was disabled during staging setup and remains disabled in the Android manifest for every variant.
6. Four exact Android applications are registered and the matching public config files are present only in their ignored variant source sets. Both debug applications have the locally generated debug SHA-256 fingerprint; no private key left the workstation.
7. Production package ID, Play App Signing/upload certificates, production Firebase apps, and production OAuth callbacks wait for explicit owner decisions.

At this revision ignored Shopify Storefront, Customer Account Mobile, discovery/callback, and Firebase client configuration exists. The merged manifest contains the redacted verified callback scheme; all four Google Services variant processors pass. The localized foundation shell consumes the typed app-owned brand selection, while shared configuration and theme contracts remain neutral. Tracked defaults remain empty/disabled, and deleting the ignored local inputs returns the repository to a visibly fail-closed state.
