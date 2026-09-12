# SECURITY PHASE S1R — ADVERSARIAL REVALIDATION

**Repository:** `private historical repository`
**Original audited SHA:** `cfffecb633d89663e05b9cebbbf974214196e87c`
**Current main SHA:** `cfffecb633d89663e05b9cebbbf974214196e87c`
**Source drift:** **NO** — final HEAD verification still resolves `main` to the original audited SHA.
**Graphify used:** **NO** — direct source/configuration navigation was sufficient. No Graphify relationship was used as evidence.
**Superpowers workflows:** `using-superpowers`, `systematic-debugging`, `verification-before-completion`.
**Current APK corroboration:** **NOT PERFORMED.** The available `APP.apk` locally hashes to SHA-256 `652ca23751a74f67f53409c29b4e347a02c76ab2f870a6824de3dad5ce88f602`, which is the repository's immutable reference-APK identity, not a build from audited HEAD.
**Audit limitations:** Static/source/configuration/provider-contract revalidation only. No real customer authentication, malicious callback registration, authorization-code interception, client impersonation, customer/order enumeration, cart exploitation, payment attempt, Firebase mutation, Shopify-console mutation, GitHub-settings mutation, or current APK build was performed.

---

# A. EXECUTIVE REVALIDATION RESULT

## Bottom line

The first-pass S1 report was **substantially sound, but not fully correct**.

Five of SEC-001 through SEC-005 survive with corrections to scope, wording, or evidentiary boundaries. SEC-006 was too categorical: PKCE does solve legitimate-flow authorization-code interception, but it does **not by itself solve RFC 8252 client impersonation where a malicious application initiates its own authorization request using the public client ID and its own verifier**. Shopify's current documented mobile contract requires the private-use `shop.{shop_id}.*` callback scheme, and no official documentation was identified proving Android package/signature/application-attestation binding for that Customer Account OAuth client. SEC-006 is therefore better treated as an **external/provider native-client boundary**, not a categorical `NOT_A_VULNERABILITY`.

More importantly, the dependency-advisory revalidation produced one **new, independently confirmed build-tool vulnerability** that S1 missed:

**SEC-R01 — Kotlin Gradle Plugin 2.3.10 / CVE-2026-53914.**

The actual resolved verification metadata contains `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10`, and the repository enables the Gradle build cache with `org.gradle.caching=true`. JetBrains' authoritative CNA record identifies Kotlin versions before `2.4.20` as affected by unsafe deserialization of build-cache metadata that can result in code execution. JetBrains rates the attack as local, high-complexity, and requiring high privileges, so this is **P3 in this project's threat model**, not a production-runtime P1/P2.

As of **August 30, 2026**, Kotlin's current stable line is still below the vendor's `2.4.20` fix version; stable Kotlin 2.4.20 is planned for September 2026, while 2.4.20-RC2 was released August 26, 2026.

## Disposition counts for SEC-001 through SEC-006

| Disposition | Count |
|---|---:|
| CONFIRMED_AS_WRITTEN | 0 |
| CONFIRMED_WITH_CORRECTION | **5** |
| DOWNGRADED | 0 |
| UPGRADED | 0 |
| INVALIDATED | 0 |
| NEEDS_RUNTIME_EVIDENCE | 0 |
| EXTERNAL_OR_PROVIDER_BOUNDARY | **1** |

## New findings

| ID | Severity | Classification |
|---|---|---|
| **SEC-R01** | **P3** | **CONFIRMED_VULNERABILITY** |

## Revalidated severity totals

| Severity | Count |
|---|---:|
| P0 | **0** |
| P1 | **0** |
| P2 | **0** |
| P3 | **2** |
| INFO | **5** |

## What changed from S1

**Survived substantively:** SEC-001, SEC-002, SEC-003, SEC-004, SEC-005.

**Corrected:** All five above needed at least a scope/evidence correction, so none is preserved literally `AS_WRITTEN`.

**Too categorical:** SEC-006.

**Newly confirmed security vulnerability:** **YES — SEC-R01**, limited to the build-tool/build-cache trust boundary. It is not an Android runtime vulnerability and does not imply compromise has occurred.

The original conclusion of **“0 confirmed vulnerabilities” is therefore no longer correct after S1R dependency-advisory revalidation.**

---

# B. REVALIDATION MATRIX

| ID | Original severity | Revalidated severity | Original classification | Revalidated classification | Disposition | Confidence | Reason |
|---|---|---|---|---|---|---|---|
| SEC-001 | P3 | **P3** | CONFIRMED_SECURITY_WEAKNESS | **CONFIRMED_SECURITY_WEAKNESS** | **CONFIRMED_WITH_CORRECTION** | HIGH | Screen privacy gap is real but scope omitted Account and Address List. |
| SEC-002 | INFO | **INFO** | HARDENING_OPPORTUNITY | **HARDENING_OPPORTUNITY** | **CONFIRMED_WITH_CORRECTION** | HIGH | Checksums/locks are strong; signature verification should be targeted, not treated as a universal mandatory toggle. |
| SEC-003 | INFO | **INFO** | EXTERNAL_OR_RELEASE_BOUNDARY | **EXTERNAL_OR_RELEASE_BOUNDARY** | **CONFIRMED_WITH_CORRECTION** | HIGH | App Links remain P3-16. Legacy branch protection is absent; complete effective-ruleset visibility is not proven. |
| SEC-004 | INFO | **INFO** | NOT_A_VULNERABILITY | **NOT_A_VULNERABILITY** | **CONFIRMED_WITH_CORRECTION** | HIGH | Exact Shopify token is demonstrably Public Storefront; Firebase API key secrecy and restriction state must be separated. |
| SEC-005 | INFO | **INFO** | NOT_A_VULNERABILITY | **NOT_A_VULNERABILITY** | **CONFIRMED_WITH_CORRECTION** | HIGH | Direct-token TLS exception is valid; some OIDC claim-continuity checks are incomplete but do not create an attacker-controlled JWT ingress. |
| SEC-006 | INFO | **INFO** | NOT_A_VULNERABILITY | **EXTERNAL_OR_PROVIDER_BOUNDARY** | **EXTERNAL_OR_PROVIDER_BOUNDARY** | HIGH | PKCE blocks intercepted-code redemption but does not prove legitimate app identity for attacker-initiated flows. |

---

# C. SEC-001 THROUGH SEC-006 — DETAILED REVALIDATION

## SEC-001 — Screen privacy coverage

**Original severity:** P3
**Revalidated severity:** P3
**Original classification:** CONFIRMED_SECURITY_WEAKNESS
**Revalidated classification:** CONFIRMED_SECURITY_WEAKNESS
**Disposition:** CONFIRMED_WITH_CORRECTION
**Confidence:** HIGH

### Exact evidence

S1 correctly identified Profile and Address Form as PII-rendering screens without the private-order `FLAG_SECURE` policy.

S1 did not inventory the complete surface.

Current production PII-bearing destinations include:

- **Account:** authenticated identity card renders `displayName`.
- **Profile:** renders editable first and last name.
- **Address List:** renders formatted postal-address lines.
- **Address Form:** renders name, address, city, postal code and potentially phone.
- **Order List / Order Detail:** private data, but these **are protected** by the current `PrivateOrderCapturePolicy` and also clear private content on lifecycle transitions.
- **Account Deletion:** current UI is generic guidance/actions and does not itself expose customer-specific PII.
- **Checkout:** sensitive checkout content is provider-owned inside Checkout Kit; SDK-internal screen-capture behavior is not source-proven by this app and is therefore separate from SEC-001.

The production root applies `PrivateOrderCapturePolicy` only when the destination is `OrderListRoute` or `OrderDetailRoute`; otherwise it clears `FLAG_SECURE`. Account/Profile/Address therefore sit outside it. Orders also explicitly clear private ViewModel content on `ON_STOP`.

### Threat model

Device-local visual disclosure through:

- screenshots;
- background/task-switcher snapshots;
- screen mirroring;
- screen recording where platform/OEM handling permits it.

### Attack prerequisite

Local device access, screen-sharing/capture capability, physical observation, malicious-local-app context, or another environment capable of capturing rendered content.

### Existing defenses

Authentication controls access to these customer screens; customer session tokens remain encrypted; orders get stronger lifecycle and capture protection.

### Residual risk

Real but local and bounded. This does not expose PII remotely over the network.

Android documents `FLAG_SECURE` as protection against screenshots and non-secure-display presentation, including background screenshot risk, while explicitly warning that the approach is not reliable against every overlay/screen-recording condition and that Android 11 and lower have device-specific reliability limitations. OWASP likewise treats inconsistent application of capture prevention to sensitive screens as a failure mode.

### Reasoning

The finding survives, but the correct scope is:

**Account + Profile + Address List + Address Form**, not merely Profile/Address Form.

Order List/Detail should not be included because they already receive the intended protection.

### Phase S2 implication

**REMEDIATE**, preserving the existing per-destination privacy model rather than introducing broad unrelated architecture changes.

A synthetic-data device characterization remains useful but is not required to prove the source gap.

---

## SEC-002 — Dependency publisher provenance

**Original severity:** INFO
**Revalidated severity:** INFO
**Original classification:** HARDENING_OPPORTUNITY
**Revalidated classification:** HARDENING_OPPORTUNITY
**Disposition:** CONFIRMED_WITH_CORRECTION
**Confidence:** HIGH

### Exact evidence

Gradle verification metadata currently has:

- `verify-metadata=true`
- `verify-signatures=false`
- SHA-256 verification for resolved artifacts.

The project additionally has:

- dependency locking;
- constrained standard repositories;
- Gradle wrapper distribution SHA-256 validation;
- full-SHA GitHub Action pinning;
- Gitleaks in CI.

Gradle explicitly distinguishes:

- **checksums → integrity**
- **signatures → provenance/authenticity**

and also documents that not every artifact is signed, unsigned artifacts fall back to checksums, and a valid signature does not make the signing key trustworthy unless that key is independently trusted.

### Threat model

Compromised repository/mirror or malicious newly bootstrapped artifact accepted together with a newly generated checksum.

### Attack prerequisite

Ability to influence dependency publication/resolution at the point a new artifact/hash becomes trusted.

### Existing defenses

Current locks and SHA-256 verification substantially reduce post-bootstrap artifact substitution risk.

### Residual risk

Initial publisher identity/provenance is weaker than artifact integrity.

### Reasoning

S1's direction was sound, but “enable signatures” was too generic.

A better project-specific statement is:

**Use targeted signature/provenance verification where the actual publisher produces verifiable `.asc` signatures and an authoritative key fingerprint can be independently established. Keep checksums as fallback.**

A global assumption that every Android/Kotlin dependency can cleanly participate in PGP verification would be unsupported.

### Phase S2 implication

**NO immediate security remediation.** Retain as later hardening after actual vulnerabilities and release controls.

---

## SEC-003 — GitHub governance / normal HTTPS App Links

**Original severity:** INFO
**Revalidated severity:** INFO
**Original classification:** EXTERNAL_OR_RELEASE_BOUNDARY
**Revalidated classification:** EXTERNAL_OR_RELEASE_BOUNDARY
**Disposition:** CONFIRMED_WITH_CORRECTION
**Confidence:** HIGH

### A. GitHub

Fresh GitHub evidence shows:

- repository is **private**;
- owner is an individual `User`, not an organization;
- `main` has `protected:false`;
- legacy branch-protection `enabled:false`;
- no required status checks at that branch-protection layer.

However, S1's wording needed tightening.

GitHub documents that **rulesets and branch-protection rules are separate layers** and all applicable rules are combined.

Repository-ruleset enumeration was not available through the current repository/plan boundary during S1R. Therefore:

**Confirmed:** no legacy `main` branch protection.

**Not fully proven:** absence of every conceivable effective repository rule.

Because this is a user-owned repository, organization-level rulesets are not an applicable hidden layer.

The conclusion must therefore not be phrased as “GitHub has no effective protection whatsoever.”

### B. App Links

The current production manifest's normal `https://gurbakir.com/...` deep-link filters use `android:autoVerify="false"`.

This affects normal HTTPS routes such as:

- collections;
- products;
- orders.

Android App Links use `autoVerify=true` plus domain-side `/.well-known/assetlinks.json` and signing-certificate association to establish OS-verified ownership.

Current domain-side association is an **external state** and was not runtime-refetched/proven in this S1R.

This normal App Links question must **not** be conflated with Shopify Customer Account's custom OAuth callback. Shopify's current mobile OAuth contract separately requires a `shop.{shop_id}.*` private-use scheme.

### Threat model

Repository governance: unauthorized or insufficiently reviewed source changes.

App Links: competing/unverified handlers for ordinary HTTPS navigation.

### Attack prerequisite

Write access/account compromise for source governance; malicious/competing app and unverified domain association for deep links.

### Existing defenses

CI is read-only and pinned; deep-link route parsing still validates actual IDs and does not make client navigation an authorization boundary.

### Residual risk

Release trust/governance rather than currently demonstrated remote application compromise.

### Phase S2 implication

**P3-16 / EXTERNAL**, not an emergency S2 application patch.

---

## SEC-004 — Public identifiers and tokens

**Original severity:** INFO
**Revalidated severity:** INFO
**Original classification:** NOT_A_VULNERABILITY
**Revalidated classification:** NOT_A_VULNERABILITY
**Disposition:** CONFIRMED_WITH_CORRECTION
**Confidence:** HIGH

### Shopify Storefront token — exact type proven

The implementation places the configured Storefront token in:

`X-Shopify-Storefront-Access-Token`

rather than:

`Shopify-Storefront-Private-Token`.

Shopify's current Storefront documentation says:

- `X-Shopify-Storefront-Access-Token` = **public access**, explicitly intended for browsers/mobile apps where buyers can see the token;
- `Shopify-Storefront-Private-Token` = **private/server-side**, must remain secret.

Therefore the existing token is not merely “assumed public because it is mobile”; its **exact public token type is proven by the header contract**.

### Firebase

The prior secrecy conclusion also remains correct, with one important correction:

**NOT_SECRET ≠ CORRECTLY_RESTRICTED.**

Firebase states that Firebase client API keys are public by design and that authorization belongs to IAM, Firebase Security Rules and App Check. Firebase separately instructs projects to apply appropriate API restrictions and to review/manage them in project settings.

Repository source can prove that treating the key as an APK secret would be wrong.

Repository source **cannot prove current Google Cloud/Firebase Console restriction state.**

Therefore:

- Firebase API key secrecy: **NOT_A_VULNERABILITY**
- Firebase API restriction correctness: **UNVERIFIED_EXTERNAL**

### OAuth client ID

Project evidence identifies the configured Customer Account client as Shopify **Public Mobile**, and Shopify's current contract defines public clients as using PKCE rather than a confidential client secret.

### Threat model

APK extraction of public identifiers.

### Attack prerequisite

Ability to inspect the client binary/configuration.

### Existing defenses

Server-side authorization, OAuth PKCE, customer tokens, Firebase security controls and product-specific restrictions remain the real authorization boundaries.

### Residual risk

Public credentials can still be abused for quota/traffic if external restrictions are weak; that does not make them confidential credentials.

### Phase S2 implication

**NO ACTION** on secrecy.

Firebase restriction state belongs to **EXTERNAL/P3-16 verification**, not secret rotation based merely on APK visibility.

---

## SEC-005 — ID Token local JWS verification

**Original severity:** INFO
**Revalidated severity:** INFO
**Original classification:** NOT_A_VULNERABILITY
**Revalidated classification:** NOT_A_VULNERABILITY
**Disposition:** CONFIRMED_WITH_CORRECTION
**Confidence:** HIGH

### Exact evidence

The S1 conclusion that absence of local JWS verification is not automatically a vulnerability survives.

The source path was independently traced end to end.

`CustomerIdTokenValidator` does parse the JWT header/payload without verifying its JWS signature locally. It verifies:

- `alg == RS256`;
- exact configured issuer;
- non-empty subject;
- client ID in audience;
- `azp` when the token has multiple audiences;
- expiration;
- issue time;
- initial nonce.

But the source establishes that production ID Tokens reach this validator through the direct Token Endpoint path:

1. authorization returns **code only**;
2. exact callback route and state are checked;
3. original PKCE verifier is attached;
4. code is exchanged at the discovered HTTPS token endpoint;
5. token HTTP redirects and SSL redirects are disabled;
6. only a successful bounded JSON Token Response is parsed;
7. its ID Token reaches the validator.

Discovery itself begins at fixed HTTPS URLs on the configured storefront domain, disables redirects, requires exact expected issuer equality and requires discovered auth/token/logout/JWKS/GraphQL endpoints to be secure HTTPS endpoints.

### Specification result

OIDC Core §3.1.3.7 is explicit:

For an ID Token received through direct Client ↔ Token Endpoint communication in Authorization Code Flow, **TLS server validation MAY be used in place of checking the token signature**. It does not waive the remaining claim-validation obligations.

Therefore authenticity here derives from:

**trusted discovery + standard HTTPS/TLS server authentication of the discovered Token Endpoint**, rather than local JWS verification.

### Corrections to S1

The implementation is not a perfect implementation of every OIDC claim-validation requirement.

Two notable differences:

1. OIDC requires rejection when `aud` contains additional audiences the client does not trust. The current validator only requires its own client ID and, for multiple audiences, matching `azp`; it does not maintain an explicit trusted-additional-audience set.

2. For a refreshed ID Token, OIDC requires `iss`, `sub` and `aud` continuity with the original token and requires any refresh-response nonce, if present, to equal the original authentication nonce. The current refresh path validates the returned token in isolation with `expectedNonce=null`; it does not compare new `sub`/full `aud`/optional nonce against the stored original token.

Those are real conformance/defense-in-depth limitations.

They do **not**, under the current architecture, give an attacker who merely controls an arbitrary JWT string an ingress route to the validator.

### Threat model

Tested attacker:

**Controls arbitrary JWT bytes but does not control the Shopify Token Endpoint/TLS channel/application process.**

Result: no production ingress path found.

A JWT from a deep link, intent extra, GraphQL response, arbitrary caller, or plaintext local storage does not enter this path.

### Stronger prerequisites that invalidate the trust assumption

- compromised application process;
- compromised/root-controlled device trust environment;
- compromise of the trusted storefront discovery response;
- compromise of the discovered Shopify Token Endpoint;
- TLS/CA/hostname trust failure;
- provider-side issuance bug severe enough to violate OIDC claims.

These exceed the “attacker can hand the app an arbitrary JWT” model.

### Existing defenses

PKCE, one-use state, nonce, strict callback, trusted discovery, disabled redirects, standard OkHttp TLS, encrypted session persistence.

### Residual risk

Reliance on provider/TLS trust rather than cryptographic validation of a bearer token after it has left that channel, plus the noted OIDC claim-continuity gaps.

### Phase S2 implication

**NO vulnerability remediation required for local JWS verification.**

The audience/refresh-continuity checks are reasonable **hardening/conformance work**, but should not be mislabeled as a current token-forgery exploit.

---

## SEC-006 — Custom-scheme OAuth / PKCE / client impersonation

**Original severity:** INFO
**Revalidated severity:** INFO
**Original classification:** NOT_A_VULNERABILITY
**Revalidated classification:** EXTERNAL_OR_PROVIDER_BOUNDARY
**Disposition:** EXTERNAL_OR_PROVIDER_BOUNDARY
**Confidence:** HIGH

This is the most important conceptual correction to S1.

### Threat model A — legitimate-flow code interception

Legitimate Gürbakır generates its PKCE verifier/challenge. A malicious app claims the same callback scheme and intercepts the callback.

**Result:** PKCE is effective.

The malicious app has the authorization code but not Gürbakır's verifier, so it cannot redeem the code.

RFC 8252 explicitly describes this attack and PKCE mitigation.

### Threat model B — callback denial / availability

The malicious handler receives the callback instead of Gürbakır.

**Result:** login can fail/timeout.

**Impact:** availability/UX, not direct account takeover.

### Threat model C — malicious-client initiation / client impersonation

This is different.

A malicious application can theoretically:

1. learn the public `client_id`;
2. learn the registered private-use callback URI;
3. create **its own** PKCE verifier/challenge;
4. initiate OAuth using Gürbakır's public client ID;
5. have the user authenticate in the external browser;
6. receive the private-use-scheme callback if Android dispatches it there;
7. redeem the code using **its own verifier**, which matches the challenge it supplied.

PKCE does **not** defeat this scenario, because the attacker owns the verifier.

RFC 8252 explicitly says native applications are public clients and, absent additional identity measures, are subject to **client impersonation**. It notes that authorization servers may use claimed HTTPS redirects or platform-specific app identity information as additional proof.

### Shopify-specific contract

Shopify currently states that public mobile Customer Account clients:

- are public clients;
- use PKCE;
- must use a redirect URI whose scheme matches `shop.{shop_id}.*`.

Shopify's Checkout authentication tutorial also configures Customer Account API access as a public mobile client, gives a default callback of the form `shop.{your_shop_id}.app://callback`, and instructs the mobile client to use OAuth with PKCE.

No official Customer Account documentation located in this pass states that Shopify additionally binds OAuth authorization to:

- Android package name;
- signing-certificate fingerprint;
- Play Integrity;
- application attestation;
- OS-verified App Link ownership.

By contrast, Shopify documents Android package ID + SHA-256 certificate binding when configuring **ordinary Android App Links/offsite checkout links**, demonstrating that Shopify documents such binding where it applies.

This is not proof that Shopify has no undocumented anti-abuse defense. It means that such a defense **cannot be relied upon as proven security evidence** in this audit.

### Why this is not upgraded to an application vulnerability

The application is following Shopify's currently documented Public Mobile contract.

The documented provider contract itself requires the private-use scheme.

Replacing it with an ordinary Android App Link is therefore not something application source can unilaterally do without changing the Shopify client contract.

This is best characterized as:

**generic public-native-client / Shopify provider residual risk**, not a source defect proven exploitable against Shopify.

### Phase S2 implication

**EXTERNAL / PROVIDER.**

Do not “fix” it by embedding a client secret.

Do not remove PKCE.

Revisit only if Shopify exposes a supported application-bound redirect or package/signature/attestation mechanism.

---

# D. SEC-005 — END-TO-END TRUST-CHAIN PROOF

```text
Configured storefront domain
        |
        v
https://<shop>/.well-known/openid-configuration
        |
        | fixed HTTPS authority
        | redirects disabled
        | expected issuer exact-match
        | discovered endpoints must be HTTPS
        v
Validated Shopify discovery
        |
        v
Authorization Request
  response_type=code
  client_id=Public Mobile client
  state=random 256-bit
  nonce=random 256-bit
  PKCE=S256
        |
        v
External browser / AppAuth
        |
        v
Private-use callback
        |
        | exact route check
        | unique parameters
        | one-use constant-time state check
        | CODE only enters grant
        v
CustomerAccountAuthorizationGrant
        |
        | contains original PKCE verifier + nonce
        v
POST discovered Token Endpoint
  grant_type=authorization_code
  code
  code_verifier
        |
        | standard OkHttp TLS/hostname validation
        | HTTP/HTTPS redirects disabled
        v
Successful bounded JSON Token Response
        |
        v
CustomerTokenPayload
        |
        v
CustomerIdTokenValidator
  RS256 header expectation
  exact iss
  sub
  aud/client_id
  exp/iat
  initial nonce
        |
        | Local JWS signature not checked
        | OIDC 3.1.3.7 direct-token TLS exception applies
        v
CustomerSession
        |
        v
AES-GCM + Android Keystore persistence
        |
        +----------------------+
        |                      |
      restore                refresh
        |                      |
 encrypted prior          refresh_token POST
 validated token          discovered Token Endpoint
        |                      |
        |                direct HTTPS response
        |                      |
        |                optional new ID Token
        |                      |
        |                validate(expectedNonce=null)
        |                      |
        +-----------> updated encrypted session
                               |
                               v
                             logout
                               |
                       stored ID Token used
                       only as id_token_hint
```

### Authenticity boundary

The ID Token's production-path authenticity comes from:

**Shopify discovery integrity + TLS server authentication of the direct Token Endpoint response.**

It does **not** come from local JWS verification.

That is permitted by OIDC Core specifically for this direct Authorization Code Flow token response.

### What does not have an ingress path

No production source path was found that allows an arbitrary ID Token from:

- callback query;
- deep link;
- intent extra;
- GraphQL;
- arbitrary UI input;
- test fixture wired into release;
- caller-supplied token payload.

Initial tokens and refresh-returned tokens both originate in direct Token Endpoint responses.

### Remaining conformance limitations

- extra `aud` trust is not fully enforced;
- refresh `sub` and full `aud` continuity are not compared to original;
- refresh nonce continuity is not checked if a provider returns a nonce.

These do not overturn the present authenticity conclusion but should not be hidden.

---

# E. SEC-006 — THREAT-MODEL MATRIX

| Threat | Attacker capability | PKCE effect | State effect | Platform/provider defense | Remaining consequence | Classification |
|---|---|---|---|---|---|---|
| Legitimate-flow code interception | Malicious app receives Gürbakır callback/code | **Blocks redemption:** attacker lacks Gürbakır verifier | Not principal defense | Shopify requires PKCE | Authentication flow may fail; code unusable by interceptor | NOT_A_VULNERABILITY for account takeover |
| Callback availability / DoS | Competing app owns same private-use scheme | None | None | Android dispatch/user defaults may influence handler | Login interruption / denial | Residual availability risk |
| Malicious-client initiation / client impersonation | Attacker copies public client ID/callback and starts own flow with own verifier | **Does not help:** attacker possesses its verifier | Attacker owns its own state | No documented package/signature binding found in Shopify Customer Account contract | Potential public-native-client impersonation risk depends on provider/user-consent behavior | **EXTERNAL_OR_PROVIDER_BOUNDARY** |
| Cross-app request forgery into legitimate Gürbakır flow | Attacker sends crafted callback to Gürbakır | No | **Strong defense:** unpredictable, one-use state | Exact callback parsing | Crafted callback rejected | VERIFIED defense |
| Authorization-server mix-up | Attacker attempts alternate issuer/provider | Not primary | Not primary | Single configured issuer; discovery issuer exact-match; HTTPS | No practical multi-provider mix-up path identified | NOT_A_VULNERABILITY |

RFC 8252 explicitly distinguishes authorization-code interception from client impersonation and notes that claimed HTTPS redirects/platform identity can provide stronger application identity.

---

# F. VERIFIED DEFENSE MATRIX

| Defense | S1 claim | S1R result | Evidence / residual limitation |
|---|---|---|---|
| No cleartext app traffic | secure | **VERIFIED** | Production manifest sets `usesCleartextTraffic=false`; security-sensitive URLs also enforce HTTPS. |
| No trust-all TLS | secure | **VERIFIED** | No app custom `X509TrustManager`/`HostnameVerifier` bypass found; token/discovery clients use normal OkHttp TLS. |
| No production general-purpose WebView | secure | **VERIFIED** | Production WebView use located only at the Shopify Checkout Kit event-processor boundary. |
| Debug evidence activities excluded from production source | secure | **VERIFIED** | Stage3/Stage4 manifest entries live under `src/debug`; current APK corroboration still not performed. |
| Customer OAuth tokens not plaintext-persisted | secure | **VERIFIED** | Session store is Keystore AES-GCM; corrupt state fails closed. |
| AES-GCM IV handling | secure | **VERIFIED** | Cipher-generated fresh IV is persisted with ciphertext; randomized encryption required. |
| Backup/device-transfer exclusions | secure | **VERIFIED** | Shared prefs/database/files excluded from cloud backup and device transfer. |
| No confirmed customer IDOR | secure | **VERIFIED** | Private account/order APIs resolve authenticated Customer Account bearer session; client route IDs are not authorization. |
| Checkout Kit remains payment authority | secure | **VERIFIED** | Shopify documents that Checkout Kit presents checkout and Shopify processes payment/creates order. |
| Checkout URL allowlist | secure | **VERIFIED** | HTTPS + configured host + no userinfo/fragment + standard HTTPS port. |
| Checkout SDK permissions reduced | secure | **VERIFIED** | File chooser canceled, geolocation denied, permission requests denied, Web Pixel forwarding no-op. |
| Carrier tracking URL allowlist | secure | **VERIFIED** | HTTPS canonical URL + official carrier host allowlist. |
| Production logging/redaction | secure | **VERIFIED** | Stable event IDs, enum attributes, token/email/JWT/query-secret redaction; no independent production Log usage found. |
| Firebase Messaging debug-only | secure | **VERIFIED** | Messaging dependency is debug-only. |
| Remote Config not authorization authority | secure | **VERIFIED** | Fixed boolean/numeric policy schema and safe-default fallback; no executable/auth configuration. |
| Profile/address/order PII not in Room | secure | **VERIFIED** | Room entities found are search history/settings and wishlist; no customer-profile/address/order entity. |
| Ambiguous cart mutations not blindly replayed | secure | **VERIFIED** | Ambiguous outcome triggers server reread and intended-state reconciliation, not mutation reissue. |
| Public mobile identifiers are non-secret | secure | **VERIFIED** | Exact Shopify Storefront public header and Public Mobile OAuth contract proven. |
| Firebase API key restrictions are correct | implied too broadly | **UNVERIFIED_EXTERNAL** | Client key is not secret, but console-side restriction state is not source-verifiable. |
| Normal App Links verified | not complete | **UNVERIFIED_EXTERNAL** | Manifest currently has `autoVerify=false`; current domain asset-link state not proven. |
| Full GitHub effective protection absent | too broad | **CORRECT_BUT_OVERSTATED** | Legacy branch protection is absent, but repository ruleset enumeration was not available. |

---

# G. NEW FINDINGS

## SEC-R01 — Affected Kotlin Gradle Plugin used with build caching enabled

**Severity:** P3
**Classification:** CONFIRMED_VULNERABILITY
**Confidence:** HIGH
**CVE:** CVE-2026-53914
**Surface:** developer/CI build toolchain — not Android runtime.

### Exact affected code/configuration

Resolved dependency metadata contains:

`org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10`

including the actual KGP JAR and related plugin API artifacts.

The repository globally enables:

`org.gradle.caching=true`

### Advisory applicability

The authoritative JetBrains CNA record for CVE-2026-53914 states:

- vendor/product: JetBrains Kotlin;
- affected versions: semver versions before `2.4.20`;
- weakness: CWE-502 unsafe deserialization;
- impact: code execution through build-cache metadata;
- CNA CVSS: **6.7 Medium**;
- vector: `CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:C/C:H/I:L/A:L`.

JetBrains' own Fixed Security Issues page lists the Kotlin issue as code execution via unsafe deserialization in build cache metadata and identifies **2.4.20** as the fix version.

Current KGP `2.3.10` falls inside the CNA affected range.

### Threat model

Attacker obtains the ability to place/manipulate malicious Kotlin build-cache metadata that a developer or CI build consumes.

### Attack prerequisite

Per the JetBrains CNA vector:

- local attack position;
- high complexity;
- high privileges.

This is not a remote zero-click attack against application users.

The project's GitHub cache configuration also reduces one obvious CI poisoning path: `setup-gradle` defaults non-default branches to read-only cache writes, while the default branch may write cache entries.

### Attack path

Malicious build-cache metadata → vulnerable Kotlin Gradle Plugin deserialization → code execution in the build process.

### Impact

Potential compromise of developer/CI build execution, with possible access to whatever files/credentials the affected build process itself can access and potential alteration of build output.

### Existing defenses

- dependency artifact integrity checks;
- pinned dependencies;
- GitHub-hosted CI;
- read-only cache writes for non-default branches by default;
- repository CI permissions kept minimal.

These controls make unauthorized creation of poisoned trusted cache state harder, but they do not remove the vulnerable deserialization code.

### Why P3 rather than P2

The vendor CNA vector requires local access, high complexity and high privileges.

This is also a **build-time** vulnerability rather than an exploitable path in the shipped Android application.

There is no evidence the vulnerability has been exploited or that any existing cache is malicious.

### Current vendor release state

As of August 30, 2026:

- JetBrains' authoritative fix version for CVE-2026-53914 is `2.4.20`;
- Kotlin's official release schedule lists stable `2.4.20` as planned for September 2026;
- `2.4.20-RC2` was released August 26, 2026 and is a pre-stable/EAP build.

Therefore S1R does **not** recommend blindly switching production tooling to a prerelease in this read-only phase.

### Why not already covered by SEC-002

SEC-002 concerns **artifact provenance**.

SEC-R01 is different: the artifact itself is a **known-vulnerable legitimate Kotlin build tool**.

Checksums can prove the vulnerable JAR is authentic and unchanged; they cannot remove a vulnerability already present in the authentic dependency. Gradle dependency verification is an integrity/provenance control, not a vulnerability-remediation mechanism.

### Phase S2 implication

**REMEDIATE.**

S2 should specifically address CVE-2026-53914 with a vendor-supported safe-version or cache-related mitigation compatible with the project once implementation is authorized.

---

# H. EXTERNAL / PROVIDER / RUNTIME GAPS

## Shopify

**SEC-006:** Shopify Public Mobile currently requires a custom `shop.{shop_id}.*` callback. Whether Shopify has undocumented Android caller-attestation or anti-impersonation logic is **not provable from official public documentation**.

Do not convert this uncertainty into either “definitely exploitable” or “definitely safe.”

## Firebase / Google Cloud

Firebase client API key secrecy: resolved.

Actual Google Cloud API/Application restriction state: **UNVERIFIED_EXTERNAL**.

## GitHub

Legacy `main` protection: confirmed absent.

Complete effective repository-ruleset visibility: unavailable in this pass.

No organization ruleset layer applies because the repository owner is a personal user account.

## P3-16

Production:

- package identity;
- signing;
- verified ordinary App Links;
- release governance;
- required GitHub protections/checks;

remain P3-16/external work and should not be retroactively called S1 application vulnerabilities.

## Current APK

**CURRENT APK CORROBORATION = NOT PERFORMED.**

The reference APK must not be used to assert:

- current merged manifest;
- current exported library components;
- release `debuggable` state;
- current packaged dependencies;
- current release permissions.

A separate authorized build/runtime evidence phase is needed for those assertions.

---

# I. OFFICIAL SOURCES

## 1. OpenID Connect Core 1.0 — §3.1.3.7 ID Token Validation

**URL:**
https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation

**Exact supported proposition:**
For an ID Token received by direct communication between the Client and Token Endpoint in Authorization Code Flow, TLS server validation **MAY** be used to validate the issuer in place of checking the token signature. Other ID Token validation requirements in the section remain applicable, including exact issuer, audience, expiry and nonce handling where applicable.

---

## 2. OpenID Connect Core 1.0 — §12.2 Successful Refresh Response

**URL:**
https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokenResponse

**Exact supported proposition:**
If an ID Token is returned from a refresh-token request, its `iss`, `sub` and `aud` must be consistent with the original authentication ID Token; `iat` must represent issuance of the new token; a refresh-response nonce should normally be absent, but if present must equal the original nonce; otherwise the normal ID Token rules apply.

---

## 3. RFC 8252 — §8.1 Protecting the Authorization Code

**URL:**
https://www.rfc-editor.org/rfc/rfc8252.html#section-8.1

**Exact supported proposition:**
Private-use URI schemes can be registered by multiple applications, allowing authorization-code interception. PKCE was created specifically to mitigate that attack: an interceptor that receives the authorization code does not possess the legitimate client's verifier and therefore cannot use the intercepted code.

---

## 4. RFC 8252 — §8.6 Client Impersonation

**URL:**
https://www.rfc-editor.org/rfc/rfc8252.html#section-8.6

**Exact supported proposition:**
Native apps are public clients and cannot establish identity using a statically embedded shared secret. Without additional measures they are subject to client impersonation. Authorization servers can use stronger mechanisms such as claimed HTTPS redirects or platform-specific app identity information where available. This is distinct from authorization-code interception.

---

## 5. Shopify Customer Account API — Authentication / Public Mobile Clients

**URL:**
https://shopify.dev/docs/api/customer/latest

**Exact supported proposition:**
Shopify Customer Account public clients use PKCE to mitigate authorization-code interception; authorization uses code flow; for public mobile applications the registered `redirect_uri` scheme must match `shop.{shop_id}.*`; `state` is used for CSRF/XSRF protection.

**Additional primary tutorial used for the same provider contract:**
https://shopify.dev/docs/storefronts/mobile/checkout-kit/authenticate-checkouts?extension=swift

**Additional supported proposition:**
Shopify's Checkout Kit authentication tutorial configures Customer Account API access as a Public mobile client, gives a default callback form `shop.{your_shop_id}.app://callback`, and instructs the mobile client to use OAuth with PKCE.

---

## 6. Shopify Storefront API — Public vs Private Access Tokens

**URL:**
https://shopify.dev/docs/api/storefront/2026-04

**Exact supported proposition:**
Public Storefront access is intended for browsers/mobile apps where the token is visible to buyers and uses the `X-Shopify-Storefront-Access-Token` header. Private Storefront access is intended for server/private contexts and uses `Shopify-Storefront-Private-Token`; private tokens should be kept secret and off client devices.

---

## 7. Shopify Checkout Kit

**URL:**
https://shopify.dev/docs/storefronts/mobile/checkout-kit

**Exact supported proposition:**
Checkout Kit presents Shopify checkout from a `checkoutUrl`; the buyer enters shipping/payment information in Shopify checkout; Shopify processes payment, applies applicable rules and creates the order; Checkout Kit reports lifecycle completion/failure/cancellation to the app.

---

## 8. Shopify Checkout Kit — Android App Links / Offsite Payments

**URL:**
https://shopify.dev/docs/storefronts/mobile/checkout-kit/offsite-payments

**Exact supported proposition:**
For ordinary Android App Links on a storefront/custom domain, Shopify documents configuring an Android application ID and SHA-256 signing-certificate fingerprint and generating a domain-side `assetlinks.json`. This is a separate mechanism from the Customer Account Public Mobile custom OAuth callback contract.

---

## 9. Android Developers — Secure Sensitive Activities / `FLAG_SECURE`

**URL:**
https://developer.android.com/security/fraud-prevention/activities

**Exact supported proposition:**
`FLAG_SECURE` prevents screenshots and presentation on non-secure displays in the supported platform path, can protect background screenshots, and helps with screen sharing. Android also explicitly documents limitations: it is not a complete overlay-defense mechanism, screen-recording detection/protection is not perfect in all cases, and Android 11 and lower have device-specific reliability limitations.

---

## 10. Android Developers — Verify App Links

**URL:**
https://developer.android.com/training/app-links/verify-applinks

**Exact supported proposition:**
When an appropriate HTTP/HTTPS intent filter uses `android:autoVerify="true"`, Android automatically verifies declared hosts and fetches the corresponding `https://<host>/.well-known/assetlinks.json` Digital Asset Links document. Verified association is therefore distinct from merely declaring an HTTPS deep-link filter.

---

## 11. OWASP MASTG — MASTG-BEST-0014 Preventing Screenshots and Screen Recording

**URL:**
https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0014/

**Exact supported proposition:**
Sensitive information should be protected from screenshots, screen recording, non-secure displays, task-switcher thumbnails and remote screen sharing. On Android, `FLAG_SECURE` is a primary window-level control for this purpose, with platform/runtime testing still relevant.

---

## 12. Firebase Documentation — Learn About and Manage API Keys for Firebase

**URL:**
https://firebase.google.com/docs/projects/api-keys

**Exact supported proposition:**
Firebase API keys are public by design and identify the Firebase project/app rather than authorizing access to backend data. Authorization is provided by controls such as IAM, Firebase Security Rules and App Check. The same documentation separately requires reviewing and applying appropriate API restrictions and limits, which are project/console state rather than proof that the key is secret.

---

## 13. Firebase FAQ — API Keys for Firebase

**URL:**
https://firebase.google.com/support/faq

**Exact supported proposition:**
Firebase API keys for Firebase services are not secrets when used according to Firebase guidance; they identify a project/app, while authorization belongs to other Firebase/Google controls. Firebase separately tells developers to apply suitable API restrictions, so `NOT_SECRET` does not itself prove `CORRECTLY_RESTRICTED`.

---

## 14. Gradle — Dependency Verification

**URL:**
https://docs.gradle.org/current/userguide/dependency_verification.html

**Exact supported proposition:**
Gradle defines checksums as artifact-integrity verification and signatures as artifact-provenance verification. Not all artifacts publish signatures, so signature verification can fall back to checksums. A passing signature does not by itself establish that the signing key should be trusted; keys must be independently trusted. Dependency verification verifies the resolved artifact's integrity/provenance and is not a substitute for vulnerability assessment of an authentic dependency.

---

## 15. GitHub Docs — About Rulesets

**URL:**
https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets

**Exact supported proposition:**
Repository rulesets and legacy branch-protection rules are distinct mechanisms. They can both apply to a branch, applicable rules are layered/aggregated, and the most restrictive applicable rule wins. Consequently, `branch.protected=false` proves absence of that legacy branch-protection state but must not automatically be generalized to “no effective repository protection” without ruleset visibility.

**Related primary branch-protection reference:**
https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches

**Additional supported proposition:**
Branch protection rules can separately enforce status checks, review requirements, force-push/deletion restrictions and other controls on matching branches.

---

## 16. JetBrains / CVE-2026-53914

**JetBrains vendor advisory URL:**
https://www.jetbrains.com/privacy-security/issues-fixed/

**Authoritative CVE Program record URL:**
https://www.cve.org/CVERecord?id=CVE-2026-53914

**Exact supported proposition:**
JetBrains identifies CVE-2026-53914 for Kotlin as code execution through unsafe deserialization in build-cache metadata, CWE-502, Medium severity, with fix version `2.4.20`. The JetBrains CNA record defines the affected semver range as versions before `2.4.20` and supplies CVSS `6.7` with vector `CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:C/C:H/I:L/A:L`.

**Documentation note:**
The JetBrains Fixed Security Issues page is the primary vendor advisory. The CVE Program URL is the authoritative record location; its browser presentation is JavaScript-dependent, but the S1R version boundary is the JetBrains CNA boundary and is consistent with the JetBrains vendor advisory's `2.4.20` fix version.

---

## 17. Kotlin Documentation — Release Process and Current 2.4.20 Preview

**Stable release schedule URL:**
https://kotlinlang.org/docs/releases.html

**2.4.20-RC2 EAP URL:**
https://kotlinlang.org/docs/whatsnew-eap.html

**Exact supported proposition:**
Kotlin's official release process lists stable `2.4.20` as planned for September 2026 and lists `2.4.10` as the latest stable bug-fix release in the 2.4 line at the time of S1R. The EAP documentation records `2.4.20-RC2` as released August 26, 2026 and identifies it as a pre-stable/EAP release.

---

## 18. Gradle Actions — `setup-gradle` Cache Behavior

**URL:**
https://github.com/gradle/actions/blob/main/docs/setup-gradle.md

**Exact supported proposition:**
The official `gradle/actions` documentation states that Gradle User Home caching includes the local build cache; by default the action writes cache entries from jobs on the default branch, while jobs on other branches read but do not write updated cache entries. It also documents GitHub cache branch scoping and the local build-cache content stored by the action.

---

# J. SECURITY PHASE S2 INPUT

## REMEDIATE

### 1. SEC-R01 — Kotlin build-cache vulnerability

Highest remediation priority from S1R.

Scope strictly to CVE-2026-53914 and its build-cache exposure.

Do not bundle unrelated dependency upgrades into the security fix unless dependency compatibility requires them.

### 2. SEC-001 — PII capture protection

Cover all verified app-owned PII surfaces:

- Account;
- Profile;
- Address List;
- Address Form.

Preserve the already-correct Order protection.

---

## RUNTIME-CHARACTERIZE-FIRST

### Screen privacy runtime confirmation

Using synthetic PII only, characterize:

- screenshot;
- Recents/background snapshot;
- screen recording/mirroring;

across supported Android versions after SEC-001 remediation is designed.

### Current merged APK verification

When separately authorized, build the actual current app and inspect:

- merged manifest;
- exported library components;
- release `debuggable`;
- final permissions;
- debug-only component exclusion.

---

## P3-16 / EXTERNAL

- verified ordinary Android App Links;
- domain-side `assetlinks.json`;
- final package/signing identity;
- release governance;
- GitHub protection/ruleset policy;
- Firebase API-key restriction verification;
- Shopify provider-side OAuth caller-identity capabilities if/when documented.

---

## NO ACTION

- SEC-004 public Storefront token secrecy;
- Firebase client API-key secrecy;
- OAuth Public Mobile client-ID secrecy;
- SEC-005 local JWS signature validation under the existing direct trusted Token Endpoint architecture;
- PKCE/state/nonce architecture;
- Keystore AES-GCM session/cart persistence;
- cart ownership/quarantine state machine;
- no-blind-replay cart reconciliation;
- Checkout Kit payment authority.

---

# FINAL S1R VERDICT

The adversarial second pass changes the first report in two material ways.

First, **SEC-006 was too categorical**. The correct conclusion is not simply “custom-scheme OAuth is safe because PKCE exists.” PKCE protects intercepted codes, while RFC 8252 client impersonation remains a distinct problem. Under Shopify's current documented Public Mobile callback contract, that residual belongs to the **provider/native-client boundary**, not to a confirmed Gürbakır source defect.

Second, **the original dependency-advisory conclusion was incomplete**. The actual resolved Kotlin Gradle Plugin 2.3.10 is in JetBrains' published affected range for **CVE-2026-53914**, and this repository enables Gradle build caching. That produces new finding **SEC-R01, P3 CONFIRMED_VULNERABILITY**, limited to developer/CI build execution and carrying significant attacker prerequisites.

Everything else security-critical survived the adversarial challenge with corrections rather than collapse:

- no P0/P1/P2 issue was confirmed;
- the PII capture weakness remains P3 but covers more destinations;
- OAuth code interception defenses remain strong;
- the OIDC direct-Token-Endpoint TLS exception is correctly applicable;
- Storefront public-token classification is now proven by the exact header type;
- Firebase API-key secrecy is correctly separated from restriction configuration;
- cart, checkout, Keystore, logging, TLS and local-data defenses remain materially valid.

**Final S1R counts:**

- **P0: 0**
- **P1: 0**
- **P2: 0**
- **P3: 2**
- **INFO: 5**
- **Confirmed application/runtime vulnerabilities: 0**
- **Confirmed build-tool vulnerability: 1 — SEC-R01**
- **Confirmed security weaknesses: 1 — SEC-001**

No remediation has been performed.
