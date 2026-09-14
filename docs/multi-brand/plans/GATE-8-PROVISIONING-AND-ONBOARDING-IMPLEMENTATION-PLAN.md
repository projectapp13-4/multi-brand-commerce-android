# Multi-Brand Gate 8 — Provisioning and Onboarding Implementation Plan

> **For agentic workers:** After explicit owner approval and exit from Plan Mode, use `superpowers:executing-plans` or `superpowers:subagent-driven-development`, `superpowers:using-git-worktrees`, test-driven implementation, and verification-before-completion. Execute each task as an independent review boundary.

**Status:** Final reviewed plan; not execution authority  
**Plan revision:** Gate 8 Final — 2026-09-14  
**Repository:** `projectapp13-4/multi-brand-commerce-android`  
**Planning base:** `ea936053b6d221a2abdfccf2f207167797ca330e`  
**Durable plan path:** `docs/multi-brand/plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.md`

**Goal:** Make current nonproduction application/profile configuration and bounded provider onboarding repeatable without altering existing or selected merchant-managed content, installed identities, or the accepted multi-brand architecture.

**Architecture:** Preserve explicit application modules and inward dependency direction. Introduce a versioned repository registry, deterministic tracked non-secret build projections, per-application/per-profile ignored client configuration, independently approved provider bindings, and a local PowerShell operator. External writes are restricted to exact missing Gate 7 Home definitions and an explicitly selected, unselected DRAFT acceptance probe.

**Tech stack:** Android/Kotlin/Gradle, PowerShell 7, strict JSON and UTF-8 property contracts, Shopify Admin/Storefront/Customer Account API `2026-07`, Firebase Management REST `v1beta1`, existing GitHub Actions lanes.

**Approval integrity:** SHA-256 `3478c953…` identifies the superseded submission and must not authorize this revision. Before owner approval, this exact plan must be exported verbatim as UTF-8 with LF endings and hashed. Approval must identify the resulting digest. No digest is asserted in this Plan Mode response because no durable file may be written. Any later semantic edit invalidates that approval and requires a new digest.

## Global constraints

- One monorepo and one Android application module per real brand.
- `:app` remains the Gürbakır application shell; `:synthetic` remains a nonproduction conformance application.
- No runtime merchant switch, normative brand flavor, or shared `if (brand)` logic.
- Shared/provider modules never depend on application modules; applications never depend on one another.
- `:mobile-core` remains Firebase-neutral.
- Synthetic remains Firebase-free, credential-free, effectively offline, and without effective `INTERNET`.
- Preserve every Gürbakır application ID, callback, App Link, Room, preference, Keystore, Search, and Firebase identity exactly.
- Gate 6 Menu and Gate 7 Home mobile contracts do not change.
- Admin credentials, customer tokens, service credentials, signing material, and private keys never enter Android, tracked files, logs, receipts, or evidence.
- Ordinary required CI remains credential-free.
- Production, P3-16, a real second brand, and Gate 9 remain outside Gate 8.

---

## A. Planning base and evidence

### Verified repository state

| Item | Verified state |
|---|---|
| Remote | `https://github.com/projectapp13-4/multi-brand-commerce-android.git` |
| Local branch | `main` |
| Local HEAD | `ea936053b6d221a2abdfccf2f207167797ca330e` |
| Remote `main` | Same SHA |
| Working tree | Clean: `## main...origin/main` |
| Existing worktree | `C:\src\projects\multi-brand-commerce-android-gate7`, Gate 7 candidate `e0a62e9…`; preserve and do not reuse |
| Remote branches | `main` only |
| Open PRs | None |
| Gate 8 work | No plan, code, branch, or PR found |
| Canonical CI | Android foundation run `34821833133`, run #10, exact planning SHA, push, completed successfully |
| Successful jobs | `validate`, `instrumentation`, `minimum-sdk-instrumentation` |
| Ruleset | Protect main, ID `23107007`, active, strict required checks, PR-only merge, resolved conversations, deletion/non-fast-forward protection, no bypass actor |

### Authority used

Current `AGENTS.md`, owner authority, documentation indexes, public migration handoff, Multi-Brand architecture/onboarding documents, ADR-0004, legacy identity inventory, Gate 6/7 plans and handoffs, Phase 3/P3-16 records, current Gradle/source/scripts/workflow, and preserved architecture audit.

No Graphify artifact exists. Building one would create repository artifacts, so direct read-only inspection is the Plan Mode source map.

### Current external contracts

- Shopify Admin, Storefront, and Customer Account API pins are independently `2026-07`; never use an implicit `latest`. Shopify currently identifies `2026-07` as latest stable and documents the versioned Admin endpoint and GraphQL error model. [Shopify Admin API](https://shopify.dev/docs/api/admin-graphql/2026-07)
- Menu creation exists and requires `write_online_store_navigation`, but Gate 8 deliberately implements no Menu mutation. [Shopify `menuCreate`](https://shopify.dev/docs/api/admin-graphql/latest/mutations/menucreate)
- Storefront access is distinct from Admin access; publishable metaobjects have DRAFT/ACTIVE state. [Metaobject access](https://shopify.dev/docs/apps/build/metaobjects/data-modeling-with-metafields-and-metaobjects), [publishable capability](https://shopify.dev/docs/apps/build/metaobjects/use-metaobject-capabilities)
- Customer Account discovery exposes current endpoints and requires PKCE S256. No supported Android-client registration mutation was found, so registration remains manual. [Customer Account API](https://shopify.dev/docs/api/customer/latest)
- Firebase apps in one project share backend resources; apps in a project should be variants of the same end-user application. Android package names cannot change after registration. [Firebase project model](https://firebase.google.com/docs/projects/learn-more), [Android registration](https://firebase.google.com/docs/android/setup)
- Firebase Management REST can inspect Android apps and retrieve configuration. Gate 8 uses it read-only. [Firebase `getConfig`](https://firebase.google.com/docs/reference/firebase-management/rest/v1beta1/projects.androidApps/getConfig)
- Verified App Links require manifest intent filters, `autoVerify=true`, published `assetlinks.json`, package identity, and certificate fingerprints. [Android App Links](https://developer.android.com/training/app-links/configure-assetlinks)

### Evidence limitations

- No Storefront, Admin, Customer Account, or Firebase credential was loaded during planning.
- No live provider resource is claimed verified.
- Earlier public requests to Gürbakır discovery/association endpoints returned HTTP 403; implementation must reclassify them.
- No Gradle, instrumentation, operator, security scan, or live provider acceptance was run while preparing this final plan.

---

## B. Gate 8 readiness decision

**Decision: proceed after owner approval.**

Gates 6 and 7 are sufficiently stable:

- Gate 6 selects `main-menu`, accepts at most three hierarchy levels, 64 provider nodes, and 24 typed Collection actions.
- Gate 7 selects `mobile_home/primary`, schema version 1, exactly two supported section families, at most two sections, and editorial-only LKG behavior.
- Merchant editability and Storefront readback responsibilities are already defined.
- Gate 8 can automate configuration, validation, compatible schema creation, and readback without changing either mobile contract.

No issue blocks planning or offline implementation. Provider credentials and approved provider bindings block only the corresponding configured acceptance rows and technical closure.

---

## C. Historical audit revalidation

| Finding | Current classification | Evidence | Gate placement | Blocks planning | Gate 8 result |
|---|---|---|---|---|---|
| MB-04 — per-brand integration configuration | Open | `:app` and `:storefront` still consume one root properties source | Gate 8 core | No | Explicit application/profile resolution and scoped ignored files |
| MB-06 — Firebase isolation | Partially controlled | Four-file all-or-none and dev/staging separation exist, but paths/packages are Gürbakır-specific | Gate 8 validation | No | Registry-aware exact package/project/app checks; no Firebase writes |
| MB-08 — application enrollment | Open | Eight projects and their tasks are hardcoded independently | Gate 8 core | No | Explicit registry enrollment plus independent CI minimums |
| MB-09 — second-store proof | Correctly unresolved | Synthetic is not a merchant/store | Separate pilot | No | Do not create or claim a second brand |
| MB-07 — CI enforcement | Resolved | Active ruleset and exact-main successful CI | Existing governance | No | Preserve check names and reverify exact candidate/main |

---

## D. Current provisioning/configuration map

| Concern | Current behavior | Gate 8 treatment |
|---|---|---|
| Storefront domain/version/token | Root properties → BuildConfig; Storefront proof code separately reads the root file | Domain/version tracked per profile; token stays in scoped ignored input |
| Catalog Menu | Root `shopify.catalogMenuHandle` | Tracked exact selector; merchant content validate-only |
| Home root | Root `shopify.homeContentRootHandle` | Tracked type/handle/schema and management modes |
| Customer Account | Root client/discovery data; helper rejects non-Gürbakır domains and writes root config | Generic profile-bound discovery, callback validation, local generation, manual registration checkpoint |
| Customer token User-Agent | `Gurbakir-Android` hardcoded in `:account` | Required application-owned configuration; exact Gürbakır value preserved |
| Locale/market/Search | Gürbakır Kotlin constants | Tracked projection consumed by `:app`; no runtime selector |
| Persistence | Database, preference names, and aliases hardcoded in `:app` | Tracked projection with independent legacy-identity tests and collision checks |
| App Links | Gürbakır manifest/Kotlin literals; `autoVerify=false` | Role-specific projected hosts/paths; association validate-only |
| Legal/support | Exact host/routes in Gürbakır Kotlin | Named role mappings from projection; no generic host list |
| Firebase | Four ignored files, exact package paths, local-default fallback | Preserve behavior; validate against independent provider binding |
| Synthetic | Fixed offline/provider-free configuration | Enroll exact current state; never external target |
| Enrollment/CI | Independent eight-module/task lists | Registry/settings/workflow consistency plus invariant minimums |
| Historical scripts | Checkpoint-specific | Leave frozen unless an active current validator consumes obsolete configuration |

---

## E. Gate 8 exact problem and claim boundary

Gate 8 converts single-root, Gürbakır-specific, partly manual nonproduction setup into a versioned, explicit, fail-closed application/profile contract and local operator workflow.

Gate 8 may claim:

- explicit application/profile configuration;
- no cross-profile or Gürbakır fallback;
- explicit module enrollment and CI coverage;
- bounded inspection, planning, authorized apply, and readback;
- exact current Gürbakır compatibility;
- Firebase/project/package and callback/domain consistency;
- idempotence, redaction, recovery evidence, and merchant-edit preservation.

Gate 8 does not claim:

- second-store onboarding;
- production or commercial-brand readiness;
- verified production App Links;
- Firebase project/app provisioning;
- Customer Account client-registration automation;
- general source generation, hosted control plane, or runtime brand selection.

---

## F. Alternatives considered

| Alternative | Decision |
|---|---|
| Extend PowerShell tooling | **Chosen.** Existing Windows/Linux CI convention, no new runtime, suitable for bounded operator work |
| Kotlin/JVM/Gradle operator project | Rejected: adds a ninth project and couples privileged network operations to Android build infrastructure |
| Another CLI runtime | Rejected: no demonstrated benefit sufficient to add dependency/trust cost |
| Shopify app/TOML deployment | Not introduced: no Shopify application deployment lifecycle is required |
| Hosted service/control plane | Rejected: no tenant, uptime, dashboard, or remote orchestration requirement |
| Dynamic Android module discovery | Rejected: would bless unreviewed applications |
| Explicit registry plus validators and app Gradle logic | **Chosen.** Settings remain explicit; registry drives validation and projections |
| Real second-store pilot inside Gate 8 | Rejected; separate later slice |

---

## G. Versioned onboarding manifest and registry

### G.1 Files

- `config/onboarding/application-registry.v1.json` — authoritative tracked registry.
- `config/onboarding/shopify-home-schema.v1.json` — exact Gate 7 provider schema.
- `config/onboarding/provider-binding.schema.v1.json` — closed schema for ignored expected-provider bindings.
- `config/onboarding/operator-receipt.schema.v1.json` — closed plan/result/recovery receipt schema.
- `config/onboarding/generated/gurbakir/development.properties`
- `config/onboarding/generated/gurbakir/staging.properties`

Generated projections are tracked, non-secret, deterministic, and never hand-edited.

### G.2 Registry top-level schema

Exactly these fields are permitted:

```json
{
  "schemaVersion": 1,
  "providerContracts": {
    "shopifyAdminApiVersion": "2026-07",
    "shopifyCustomerAccountApiVersion": "2026-07",
    "allowedStorefrontApiVersions": ["2026-07"],
    "gate7HomeContentSchemaVersion": 1
  },
  "modules": [],
  "applications": [],
  "ciLanes": {
    "unit": [],
    "assemble": [],
    "api30": [],
    "api23": []
  }
}
```

A module record contains only:

- `key`
- `gradleProject`
- `directory`
- `role`: `real-brand-application`, `synthetic-conformance-application`, `shared`, or `provider`
- `allowedDirectProjects`
- `ciTasks`: exact `unit`, `assemble`, `api30`, and `api23` arrays

An application record contains only:

- `key`
- `module`
- `role`
- `fixtureOnly`
- `releaseBoundary`
- `providerModules`
- `configurationProjection`
- `identity`
- `profiles`

`identity` contains only:

- `displayName`
- `analyticsNamespace`
- `compositionSource`
- `brandKey`
- `defaultLocale`
- `supportedLocales`
- `market`
- `supportedTerritory`
- `searchNormalizationLocale`
- `databaseName`
- `customerAccount`
- `webRoles`
- `nativeCompositionAssertions`

A profile contains only:

- `key`
- `runtimeEnvironment`
- `displayName`
- `localConfiguration`
- `providerBindingFile`
- `variants`
- `storefront`
- `customerAccount`
- `firebase`
- `protectedPersistence`

`storefront.sharedResourceGroup` is required when two profiles intentionally share one Shopify store/Menu/Home identity. It is prohibited for disabled Storefront profiles.

### G.3 Strict validation

- Registry limit: 256 KiB.
- Limits: 64 modules, 16 applications, 8 profiles per application, 8 variants per profile, 128 tasks per lane.
- Unknown, missing, duplicate, or case-colliding fields fail.
- JSON duplicate detection occurs while traversing `JsonDocument`, before object projection.
- Keys match `^[a-z][a-z0-9-]{1,31}$`.
- Flat Gradle paths match `^:[a-z][a-z0-9-]{0,63}$`.
- Repository paths use forward slashes and reject absolute paths, drives, backslashes, `.`, `..`, reserved Windows device names, and reparse/symlink escape.
- Ordinary strings contain 1–256 Unicode scalar values; URLs may contain at most 2,048.
- Reject NUL, C0/C1 controls, bidi overrides/isolates, unpaired surrogates, and Unicode noncharacters.
- Domains are lower-case IDNA ASCII without wildcard, IP literal, port, user-info, trailing dot, or localhost.
- HTTPS URLs must match the declared role host and allowed path.
- Arrays have canonical ordering: modules by ordinal Gradle path, applications/profiles by key; semantic navigation/path order remains declared.
- Final application IDs are globally unique.
- Persistence collision checks apply between distinct installed applications/profiles, while documented Gürbakır profile/variant sharing remains allowed.
- Full callbacks are compared only after trusted numeric shop identity is available.
- Different real applications cannot reuse Gürbakır callback, persistence, application, or Firebase identities.
- Shared Storefront/discovery hosts and Shopify resources are validated by explicit ownership grouping, not rejected as generic duplicates.
- Tracked registry records require `fixtureOnly=false`.
- `fixtureOnly=true` is accepted only by injected-transport temporary self-tests and can never enable credential loading or external commands.
- Synthetic `.invalid` hosts are accepted only for the synthetic offline role.
- Changing format/interpretation requires a new schema version. Adding valid records or changing supported v1 data changes the content digest, not the schema version.

### G.4 Exact projection format

The projection grammar is UTF-8 without BOM, LF-only, one `key=value` pair per line, with the first `=` as separator. Blank lines, comments, continuations, literal backslashes, duplicate/case-colliding keys, controls, leading/trailing value whitespace, and unknown keys fail.

The first three keys are always:

```properties
onboarding.schemaVersion=1
onboarding.sourceRegistrySha256=<64-lowercase-hex>
onboarding.application=<application-key>
```

`onboarding.profile` follows. `<64-lowercase-hex>` above denotes the required value shape, not a committed value in this plan.

Arrays use comma separators. Array elements may not contain commas and are independently validated. Values may contain `=`. Generation never emits timestamps.

The exact ordered key inventory is:

```text
onboarding.schemaVersion
onboarding.sourceRegistrySha256
onboarding.application
onboarding.profile
app.brandKey
app.brandDisplayName
app.profileDisplayName
app.analyticsNamespace
app.environmentId
app.defaultLocale
app.supportedLocales
app.marketId
app.marketCountryCode
app.marketCurrencyCode
app.supportedTerritory
app.searchNormalizationLocale
app.databaseName
app.customerAccountMode
app.customerAccountUserAgent
app.customerAccountCallbackSchemeSuffix
app.customerAccountCallbackHost
app.customerAccountCallbackPath
app.customerAccountScopes
app.cartPreferences
app.cartKeyAlias
app.customerPreferences
app.customerKeyAlias
android.applicationId.debug
android.applicationId.release
web.collectionAppLinkOrigin
web.collectionAppLinkPathPrefix
web.productAppLinkOrigin
web.productAppLinkPathPrefix
web.orderAppLinkOrigin
web.orderAppLinkPathPrefix
web.legalSupportOrigin
web.legalSupportPath.support
web.legalSupportPath.privacy
web.legalSupportPath.terms
web.legalSupportPath.shipping
web.legalSupportPath.returns
web.legalSupportPath.legalNotice
web.checkoutHostPolicy
web.assetLinksMode
web.manifestAutoVerify
shopify.storefrontMode
shopify.storefrontDomain
shopify.storefrontApiVersion
shopify.storefrontMediaOrigins
shopify.catalogMenuHandle
shopify.homeRootType
shopify.homeRootHandle
shopify.homeContentSchemaVersion
firebase.mode
firebase.ownershipKey
firebase.configPath.debug
firebase.configPath.release
```

Conditional keys must still occur in this order when applicable. Empty required values fail; disabled capability unions omit their inapplicable keys. Synthetic has no generated projection.

All JVM consumers use an explicit UTF-8 `Reader`; no `Properties.load(InputStream)` is allowed.

### G.5 Scoped ignored client configuration

Only these keys are accepted:

```text
shopify.storefrontPublicToken
shopify.customerAccountClientId
shopify.customerAccountIssuer
shopify.customerAccountAuthorizationEndpoint
shopify.customerAccountTokenEndpoint
shopify.customerAccountLogoutEndpoint
shopify.customerAccountGraphqlEndpoint
shopify.customerAccountRedirectUri
```

Rules:

- Storefront token: 1–4,096 visible ASCII characters, no whitespace/control.
- Customer client ID: 1–1,024 visible ASCII characters, no whitespace/control.
- URLs: absolute HTTPS, maximum 2,048, no user-info/fragment; host/path must match expected provider contracts.
- Redirect URI: exact derived custom callback; maximum 2,048.
- Scopes are tracked registry data and cannot be overridden locally.
- Unknown, duplicate, retired, Admin, Firebase bearer, customer token, signing, selector, domain, market, application-ID, or persistence keys fail.

### G.6 Exact provider-binding schema

`providerBindingFile` is nullable. A configured real profile uses one ignored JSON record with only:

```json
{
  "schemaVersion": 1,
  "application": "fixture-app",
  "profile": "development",
  "approvedEvidenceRef": "owner-evidence:fixture-001",
  "shopify": {
    "adminShopDomain": "fixture-shop.myshopify.com",
    "shopId": "1234567890"
  },
  "firebase": {
    "projectId": "fixture-project-123",
    "projectNumber": "123456789012",
    "androidAppIdsByVariant": {
      "developmentDebug": "1:123456789012:android:0123456789abcdef",
      "developmentRelease": "1:123456789012:android:fedcba9876543210"
    }
  }
}
```

- `application` and `profile` must exactly match the selected registry record.
- `approvedEvidenceRef` is 1–256 characters and matches `^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$`.
- `adminShopDomain` must be a canonical lower-case `<shop>.myshopify.com` hostname, not a URL.
- `shopId` and `projectNumber` are digit strings of 1–20 characters.
- Firebase project IDs match the documented Google project-ID grammar.
- `androidAppIdsByVariant` must contain exactly the configured Firebase variants.
- `firebase` is required for Firebase-configured acceptance and omitted for disabled providers.
- Tokens, service credentials, private URLs, headers, and arbitrary metadata are prohibited.
- Missing bindings are `UNCONFIGURED`; expected identities are never learned from the state being validated.

### G.7 Exact operator-receipt schema

Receipts are canonical UTF-8 JSON, maximum 256 KiB, and allow only:

```text
receiptSchemaVersion
operationContractVersion
kind
application
profile
runtimeEnvironment
releaseBoundary
createdAtUtc
expiresAtUtc
verifiedTarget
digests
stateFingerprint
actions
overallStatus
diagnosticCodes
readback
recovery
```

Rules:

- `receiptSchemaVersion`: exactly `1`.
- `operationContractVersion`: exactly `gate8-v1`.
- `kind`: `PLAN`, `RESULT`, or `RECOVERY`.
- Timestamps: RFC 3339 UTC, second precision.
- PLAN expiry: exactly 15 minutes after creation; RESULT/RECOVERY use `expiresAtUtc=null`.
- `verifiedTarget` permits only `shopId`, `adminShopDomain`, `firebaseProjectId`, and `firebaseProjectNumber`.
- `digests` requires exactly `registrySha256`, `providerBindingSha256`, `homeSchemaSha256`, and `operatorSha256`.
- Every digest/fingerprint is 64 lower-case hexadecimal characters.
- Maximum 16 typed actions/readback/recovery records.

An action permits only:

```text
ordinal
resourceKind
resourceKey
managementMode
beforeClassification
intendedAction
beforeFingerprint
providerResourceId
status
afterClassification
afterFingerprint
```

Allowed enums:

- `resourceKind`: `LOCAL_CONFIGURATION`, `SHOPIFY_HOME_DEFINITION`, `SHOPIFY_HOME_ACCEPTANCE_PROBE`
- `managementMode`: `LOCAL_APPLY`, `CREATE_IF_MISSING`, `PROBE_CREATE_IF_MISSING`
- classifications: `ABSENT`, `CORRECT`, `COMPATIBLE`, `INCOMPATIBLE`, `DRIFTED`, `UNKNOWN`
- intended action: `NONE`, `CREATE`, `ATOMIC_REPLACE`
- status: `PLANNED`, `NO_OP`, `SUCCEEDED`, `BLOCKED`, `FAILED`, `AMBIGUOUS`

`providerResourceId` accepts only a validated Shopify GID or null; it is an identity, not a network target.

Readback records permit only:

```text
surface
resourceKey
classification
identityFingerprint
```

`surface` is one of `SHOPIFY_ADMIN`, `SHOPIFY_STOREFRONT`, `CUSTOMER_DISCOVERY`, `FIREBASE_MANAGEMENT`, `ANDROID_BUILD`, or `ANDROID_RUNTIME`. Classification is `PASS`, `FAIL`, `NOT_RUN`, `PARTIAL`, `EXTERNALLY_BLOCKED`, or `NOT_APPLICABLE`.

Recovery records permit only:

```text
ordinal
resourceKind
resourceKey
classification
nextAction
```

`nextAction` is `REINSPECT`, `RESTORE_LOCAL_BACKUP`, `MANUAL_REVIEW`, or `NO_ACTION`.

No receipt may contain request URLs, GraphQL documents or variables, commands, scripts, headers, tokens, provider bodies, stack traces, or arbitrary free-form messages. `diagnosticCodes` is a closed enum implemented with the schema; unknown codes fail parsing.

### G.8 Canonical application examples

The Gürbakır application record uses exact current identities:

```json
{
  "key": "gurbakir",
  "module": ":app",
  "role": "real-brand-application",
  "fixtureOnly": false,
  "releaseBoundary": "nonproduction-only",
  "providerModules": [":firebase"],
  "configurationProjection": "config/onboarding/generated/gurbakir",
  "identity": {
    "displayName": "Gürbakır",
    "analyticsNamespace": "gurbakir",
    "compositionSource": "app/src/main/kotlin/com/gurbakir/mobile/config/BuildConfigurationSource.kt",
    "brandKey": "gurbakir",
    "defaultLocale": "tr",
    "supportedLocales": ["tr", "en"],
    "market": {
      "id": "TR",
      "countryCode": "TR",
      "currencyCode": "TRY"
    },
    "supportedTerritory": "TR",
    "searchNormalizationLocale": "tr-TR",
    "databaseName": "gurbakir-local.db",
    "customerAccount": {
      "mode": "enabled",
      "userAgent": "Gurbakir-Android",
      "callbackSchemeSuffix": "gurbakir",
      "callbackHost": "oauth",
      "callbackPath": "/callback",
      "scopes": ["openid", "email", "customer-account-api:full"]
    },
    "webRoles": {
      "collectionAppLink": {
        "origin": "https://gurbakir.com",
        "pathPrefix": "/collections/"
      },
      "productAppLink": {
        "origin": "https://gurbakir.com",
        "pathPrefix": "/apps/mobile/products/"
      },
      "orderAppLink": {
        "origin": "https://gurbakir.com",
        "pathPrefix": "/apps/mobile/orders/"
      },
      "legalSupport": {
        "origin": "https://gurbakir.com",
        "paths": {
          "support": "/pages/contact",
          "privacy": "/policies/privacy-policy",
          "terms": "/policies/terms-of-service",
          "shipping": "/policies/shipping-policy",
          "returns": "/policies/refund-policy",
          "legalNotice": "/policies/legal-notice"
        }
      },
      "checkout": {
        "hostPolicy": "storefront-origin-only"
      },
      "assetLinks": {
        "mode": "validate-only",
        "manifestAutoVerify": false
      }
    },
    "nativeCompositionAssertions": {
      "search": "ENABLED",
      "wishlist": "ENABLED",
      "customerAccount": "ENABLED",
      "primaryNavigation": ["HOME", "CATEGORIES", "SEARCH", "WISHLIST", "ACCOUNT"]
    }
  },
  "profiles": [
    {
      "key": "development",
      "runtimeEnvironment": "DEVELOPMENT",
      "displayName": "Gürbakır Geliştirme",
      "localConfiguration": "config/local/gurbakir/development.properties",
      "providerBindingFile": "config/local/gurbakir/development.providers.json",
      "variants": [
        {
          "name": "developmentDebug",
          "buildType": "debug",
          "applicationId": "com.gurbakir.mobile.dev.debug",
          "firebaseConfig": "app/src/developmentDebug/google-services.json"
        },
        {
          "name": "developmentRelease",
          "buildType": "release",
          "applicationId": "com.gurbakir.mobile.dev",
          "firebaseConfig": "app/src/developmentRelease/google-services.json"
        }
      ],
      "storefront": {
        "mode": "enabled",
        "domain": "gurbakir.com",
        "apiVersion": "2026-07",
        "publicTokenLocalKey": "shopify.storefrontPublicToken",
        "mediaOrigins": ["gurbakir.com"],
        "sharedResourceGroup": "gurbakir-existing-shop",
        "catalog": {
          "menuHandle": "main-menu",
          "managementMode": "validate-only"
        },
        "home": {
          "rootType": "mobile_home",
          "rootHandle": "primary",
          "contentSchemaVersion": 1,
          "definitionContract": "gate7-v1",
          "definitionManagementMode": "create-if-missing",
          "entryManagementMode": "validate-only",
          "sourceMode": "shopify-metaobject"
        }
      },
      "customerAccount": {
        "mode": "enabled-manual-registration",
        "discoveryOrigin": "https://gurbakir.com",
        "clientIdLocalKey": "shopify.customerAccountClientId"
      },
      "firebase": {
        "mode": "firebase-or-local-default",
        "ownershipKey": "gurbakir-development",
        "registrationManagementMode": "validate-only",
        "projectIdentitySource": "provider-binding"
      },
      "protectedPersistence": {
        "cartPreferences": "gurbakir_secure_cart_development",
        "cartKeyAlias": "gurbakir.cart.development.v1",
        "customerPreferences": "gurbakir_secure_customer_session_development",
        "customerKeyAlias": "gurbakir.customer.session.development.v1"
      }
    },
    {
      "key": "staging",
      "runtimeEnvironment": "STAGING",
      "displayName": "Gürbakır Hazırlık",
      "localConfiguration": "config/local/gurbakir/staging.properties",
      "providerBindingFile": "config/local/gurbakir/staging.providers.json",
      "variants": [
        {
          "name": "stagingDebug",
          "buildType": "debug",
          "applicationId": "com.gurbakir.mobile.staging.debug",
          "firebaseConfig": "app/src/stagingDebug/google-services.json"
        },
        {
          "name": "stagingRelease",
          "buildType": "release",
          "applicationId": "com.gurbakir.mobile.staging",
          "firebaseConfig": "app/src/stagingRelease/google-services.json"
        }
      ],
      "storefront": {
        "mode": "enabled",
        "domain": "gurbakir.com",
        "apiVersion": "2026-07",
        "publicTokenLocalKey": "shopify.storefrontPublicToken",
        "mediaOrigins": ["gurbakir.com"],
        "sharedResourceGroup": "gurbakir-existing-shop",
        "catalog": {
          "menuHandle": "main-menu",
          "managementMode": "validate-only"
        },
        "home": {
          "rootType": "mobile_home",
          "rootHandle": "primary",
          "contentSchemaVersion": 1,
          "definitionContract": "gate7-v1",
          "definitionManagementMode": "create-if-missing",
          "entryManagementMode": "validate-only",
          "sourceMode": "shopify-metaobject"
        }
      },
      "customerAccount": {
        "mode": "enabled-manual-registration",
        "discoveryOrigin": "https://gurbakir.com",
        "clientIdLocalKey": "shopify.customerAccountClientId"
      },
      "firebase": {
        "mode": "firebase-or-local-default",
        "ownershipKey": "gurbakir-staging",
        "registrationManagementMode": "validate-only",
        "projectIdentitySource": "provider-binding"
      },
      "protectedPersistence": {
        "cartPreferences": "gurbakir_secure_cart_staging",
        "cartKeyAlias": "gurbakir.cart.staging.v1",
        "customerPreferences": "gurbakir_secure_customer_session_staging",
        "customerKeyAlias": "gurbakir.customer.session.staging.v1"
      }
    }
  ]
}
```

Synthetic remains an actual enrollment record, not a provider target:

```json
{
  "key": "synthetic",
  "module": ":synthetic",
  "role": "synthetic-conformance-application",
  "fixtureOnly": false,
  "releaseBoundary": "never-production",
  "providerModules": [],
  "configurationProjection": null,
  "identity": {
    "displayName": "Gate 2 Synthetic",
    "analyticsNamespace": "gate2_synthetic",
    "compositionSource": "apps/synthetic/src/main/kotlin/com/example/gate2synthetic/config/Gate2SyntheticConfiguration.kt",
    "brandKey": "gate2-synthetic",
    "defaultLocale": "en-CA",
    "supportedLocales": ["en-CA"],
    "market": {
      "id": "ZZ",
      "countryCode": "ZZ",
      "currencyCode": "XTS"
    },
    "supportedTerritory": "ZZ",
    "searchNormalizationLocale": "en-CA",
    "databaseName": "gate2-synthetic-local.db",
    "customerAccount": {
      "mode": "disabled"
    },
    "webRoles": {
      "collectionAppLink": {
        "origin": "https://links.gate2.invalid",
        "pathPrefix": "/collections/"
      },
      "productAppLink": {
        "origin": "https://links.gate2.invalid",
        "pathPrefix": "/apps/mobile/products/"
      },
      "orderAppLink": null,
      "legalSupport": null,
      "checkout": {
        "hostPolicy": "inert-storefront-origin-only"
      },
      "assetLinks": {
        "mode": "disabled",
        "manifestAutoVerify": false
      }
    },
    "nativeCompositionAssertions": {
      "search": "ENABLED",
      "wishlist": "DISABLED",
      "customerAccount": "DISABLED",
      "primaryNavigation": ["SEARCH", "HOME", "CATEGORIES"]
    }
  },
  "profiles": [
    {
      "key": "conformance",
      "runtimeEnvironment": "DEVELOPMENT",
      "displayName": "Gate 2 Synthetic",
      "localConfiguration": null,
      "providerBindingFile": null,
      "variants": [
        {
          "name": "debug",
          "buildType": "debug",
          "applicationId": "com.example.gate2synthetic.debug",
          "firebaseConfig": null
        },
        {
          "name": "release",
          "buildType": "release",
          "applicationId": "com.example.gate2synthetic",
          "firebaseConfig": null
        }
      ],
      "storefront": {
        "mode": "disabled-offline-fixture",
        "domain": "storefront.gate2.invalid",
        "apiVersion": "2026-07",
        "publicTokenPolicy": "must-be-blank",
        "catalog": {
          "menuHandle": "synthetic-catalog-menu",
          "managementMode": "disabled"
        },
        "home": {
          "sourceMode": "disabled",
          "definitionManagementMode": "disabled",
          "entryManagementMode": "disabled"
        }
      },
      "customerAccount": {
        "mode": "disabled"
      },
      "firebase": {
        "mode": "disabled"
      },
      "protectedPersistence": {
        "cartPreferences": "gate2_synthetic_secure_cart_development",
        "cartKeyAlias": "gate2.synthetic.cart.development.v1",
        "customerPreferences": "gate2_synthetic_secure_customer_session_development",
        "customerKeyAlias": "gate2.synthetic.customer.session.development.v1"
      }
    }
  ]
}
```

The future-brand fixture is accepted only in test-fixture mode:

```json
{
  "key": "future-fixture",
  "module": ":future-fixture",
  "role": "real-brand-application",
  "fixtureOnly": true,
  "releaseBoundary": "test-fixture-only",
  "providerModules": [],
  "configurationProjection": "config/onboarding/generated/future-fixture",
  "identity": {
    "displayName": "Future Fixture",
    "analyticsNamespace": "future_fixture",
    "compositionSource": "apps/future-fixture/src/main/kotlin/com/example/future/FutureConfiguration.kt",
    "brandKey": "future-fixture",
    "defaultLocale": "en",
    "supportedLocales": ["en"],
    "market": {
      "id": "FIXTURE",
      "countryCode": "US",
      "currencyCode": "USD"
    },
    "supportedTerritory": "US",
    "searchNormalizationLocale": "en-US",
    "databaseName": "future-fixture-local.db",
    "customerAccount": {
      "mode": "disabled"
    },
    "webRoles": {
      "collectionAppLink": {
        "origin": "https://future.invalid",
        "pathPrefix": "/collections/"
      },
      "productAppLink": {
        "origin": "https://future.invalid",
        "pathPrefix": "/products/"
      },
      "orderAppLink": null,
      "legalSupport": null,
      "checkout": {
        "hostPolicy": "disabled"
      },
      "assetLinks": {
        "mode": "disabled",
        "manifestAutoVerify": false
      }
    },
    "nativeCompositionAssertions": {
      "search": "ENABLED",
      "wishlist": "DISABLED",
      "customerAccount": "DISABLED",
      "primaryNavigation": ["SEARCH", "HOME", "CATEGORIES"]
    }
  },
  "profiles": [
    {
      "key": "development",
      "runtimeEnvironment": "DEVELOPMENT",
      "displayName": "Future Fixture Development",
      "localConfiguration": "config/local/future-fixture/development.properties",
      "providerBindingFile": null,
      "variants": [
        {
          "name": "developmentDebug",
          "buildType": "debug",
          "applicationId": "com.example.futurefixture.dev.debug",
          "firebaseConfig": null
        },
        {
          "name": "developmentRelease",
          "buildType": "release",
          "applicationId": "com.example.futurefixture.dev",
          "firebaseConfig": null
        }
      ],
      "storefront": {
        "mode": "disabled-offline-fixture"
      },
      "customerAccount": {
        "mode": "disabled"
      },
      "firebase": {
        "mode": "disabled"
      },
      "protectedPersistence": {
        "cartPreferences": "future_fixture_secure_cart_development",
        "cartKeyAlias": "future-fixture.cart.development.v1",
        "customerPreferences": "future_fixture_secure_customer_session_development",
        "customerKeyAlias": "future-fixture.customer.session.development.v1"
      }
    }
  ]
}
```

Design tokens, assets, Home packaged fallback, address/postal behavior, tracking, and capability construction remain compiled application code. `nativeCompositionAssertions` validates that code; it does not replace it.

---

## H. Configuration resolution model

1. Tracked registry owns non-secret application/profile intent.
2. Tracked generated projection is the only Gradle-readable non-secret projection.
3. One exact ignored profile file supplies controlled client values.
4. Firebase remains in its four exact ignored variant files.
5. Provider binding separately supplies trusted expected non-secret external identities.
6. Privileged credentials exist only in profile-prefixed process environment variables.

Precedence is not an override chain:

- Registry/projection fields cannot be overridden.
- Scoped local fields cannot be read from another file or environment.
- Provider binding cannot supply client tokens.
- Privileged environment variables cannot become Gradle properties.
- A missing required configured value produces `UNCONFIGURED`, never a Gürbakır fallback.

Exact selection:

```powershell
.\gradlew.bat :app:assembleDevelopmentDebug `
  -PonboardingApplication=gurbakir `
  -PonboardingProfile=development `
  -PrequireConfiguredProfile=true
```

Rules:

- `requireConfiguredProfile=true` requires both identifiers.
- Requested application tasks must belong to the selected app/profile variants.
- Development can build configured while staging is absent, and vice versa.
- A malformed present file always fails.
- Ordinary shared-library tasks do not select an application or load local client configuration.
- Every projection-backed Gradle task depends on `Validate -ProjectionOnly`.
- Projection validation is file-only: no Gradle recursion, repair, credentials, or network.
- Missing/tampered projections fail before BuildConfig, resources, or manifest output.

Migration:

- `Migrate-GurbakirLocalConfiguration.ps1` requires `-Profile development` or `-Profile staging`.
- It translates only the eight retained local keys.
- It validates tracked values against the registry, refuses overwrite, and leaves the root file unchanged.
- It never copies one profile into another automatically.
- It preserves a deliberately blank Home selector as a conflict requiring review; it never silently activates remote Home.
- Root defaults/example files are removed only in the same passing cutover as all active consumers and documentation.
- The old ignored root file remains inert and recoverable after source rollback.

Local writes:

- Verify ignored/untracked status and canonical containment before reading credentials.
- Reject Windows reparse/symlink escape.
- Write a same-directory temporary file, apply restrictive permissions, then atomically replace.
- Preserve one bounded ignored backup only for an explicit replacement.
- Failure leaves the previous file unchanged and emits no values.

---

## I. Application enrollment and CI model

`settings.gradle.kts` remains explicit. The registry must exactly match it.

### Current module records

| Module | Role | Allowed direct project dependencies |
|---|---|---|
| `:app` | real-brand-application | `:mobile-core`, `:foundation`, `:storefront`, `:account`, `:checkout`, `:firebase` |
| `:synthetic` | synthetic-conformance-application | `:mobile-core`, `:foundation`, `:storefront`, `:account`, `:checkout` |
| `:mobile-core` | shared | `:foundation`, `:storefront`, `:account`, `:checkout` |
| `:foundation` | shared | none |
| `:storefront` | provider | `:foundation` |
| `:account` | provider | `:foundation` |
| `:checkout` | provider | none |
| `:firebase` | provider | `:foundation` |

Independent invariants override editable edge lists:

- no shared/provider → application;
- no application → application;
- no `:mobile-core` → `:firebase`;
- no synthetic → Firebase;
- no shared concrete-brand composition branch.

### Exact CI lane union

`unit`:

```text
:mobile-core:testDebugUnitTest
:foundation:testDebugUnitTest
:account:testDebugUnitTest
:checkout:testDebugUnitTest
:storefront:testDebugUnitTest
:firebase:testDebugUnitTest
:synthetic:testDebugUnitTest
:app:testDevelopmentDebugUnitTest
:app:testStagingDebugUnitTest
```

`assemble`:

```text
:mobile-core:assembleDebug
:mobile-core:assembleRelease
:app:assembleDevelopmentDebug
:app:assembleDevelopmentRelease
:app:assembleStagingDebug
:app:assembleStagingRelease
:app:assembleDevelopmentDebugAndroidTest
:app:assembleStagingDebugAndroidTest
:synthetic:assembleDebug
:synthetic:assembleRelease
:synthetic:assembleDebugAndroidTest
```

`api30`:

```text
:account:ciApi30DebugAndroidTest
:storefront:ciApi30DebugAndroidTest
:mobile-core:ciApi30DebugAndroidTest
:app:ciApi30DevelopmentDebugAndroidTest
:synthetic:ciApi30DebugAndroidTest
```

`api23`:

```text
:mobile-core:ciApi23DebugAndroidTest
:synthetic:ciApi23DebugAndroidTest
```

Top-level lanes must equal the deterministic ordered union of module `ciTasks`. Independent role/variant rules reject mutually consistent empty or incomplete lists.

Validation must inspect evaluated Android plugins, direct project dependencies, variant/task existence, package outputs, and synthetic artifacts. Source scanning remains a secondary guard, not role authority.

`Get-RegisteredGradleTasks.ps1` emits validated task names as separate process arguments. It rejects flags, shell syntax, line breaks, unknown projects, and arbitrary command text. Any child failure propagates immediately.

Future app enrollment requires one reviewed change set covering settings, registry, thin application module, app composition/resources, profile configuration, and CI coverage. No source generator is introduced.

---

## J. Operator tool architecture

Entry point:

```powershell
pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1
```

Commands:

| Command | Behavior |
|---|---|
| `Validate` | Offline registry, projection, local config, structure, enrollment |
| `Validate -ProjectionOnly` | File-only Gradle preflight; no credentials/network/Gradle |
| `Inspect` | Read local/provider state; no writes |
| `Plan` | Emit immutable redacted action plan; no writes |
| `Apply` | Reinspect and apply only allowed typed actions |
| `Readback` | Provider-control-plane and mobile-facing verification |
| `Recover` | Read-only reclassification/instructions; no bypass |
| `GenerateLocalConfiguration` | One atomic scoped ignored-file write |
| `RecordManualCheckpoint` | Sanitized local Customer Account registration evidence |

External commands require explicit `-Application` and `-Profile`. Synthetic and fixture-only records are rejected before credential lookup or request construction.

Examples:

```powershell
pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1 `
  -Command Validate -Application gurbakir -Profile development
```

```powershell
pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1 `
  -Command Plan -Application gurbakir -Profile development `
  -OutputPath out/onboarding/gurbakir-development-plan.json
```

```powershell
pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1 `
  -Command Apply -Application gurbakir -Profile development `
  -PlanReceipt out/onboarding/gurbakir-development-plan.json `
  -ConfirmApplication gurbakir -ConfirmProfile development -ConfirmApply
```

Credentials use:

```text
MB_GURBAKIR_DEVELOPMENT_STOREFRONT_PUBLIC_TOKEN
MB_GURBAKIR_DEVELOPMENT_SHOPIFY_ADMIN_TOKEN
MB_GURBAKIR_DEVELOPMENT_CUSTOMER_ACCOUNT_CLIENT_ID
MB_GURBAKIR_DEVELOPMENT_FIREBASE_ACCESS_TOKEN
```

`<PROFILE>` consistently means the registry profile key. `runtimeEnvironment` remains the separate `DEVELOPMENT`/`STAGING` enum.

Apply requires:

- valid unexpired PLAN receipt;
- matching target and confirmations;
- matching registry, provider-binding, Home-schema, and executable digests;
- matching state fingerprint;
- fresh recomputation of typed allowed actions;
- explicit `-ConfirmApply`;
- both Plan and Apply to include `-IncludeAcceptanceProbe` for the probe;
- nonproduction release boundary.

There is no default target, production mode, delete, publish, raw query, arbitrary endpoint, or generic reconcile command.

Exit codes:

| Code | Meaning |
|---:|---|
| 0 | Valid/no-op/successful readback; any `UNCONFIGURED` rows remain explicit |
| 2 | Registry/projection/local/repository validation failure |
| 3 | Missing credential, binding, or manual prerequisite |
| 4 | Incompatible state or drift |
| 5 | Provider transport/throttling/API/user error |
| 6 | Mobile-facing readback mismatch |
| 7 | Unsafe target or authorization failure |
| 8 | Partial/ambiguous apply with recovery receipt |
| 9 | Internal invariant failure |

---

## K. External resource ownership matrix

| Resource | Owner | Mode | Safe automatic change | Prohibited change | Readback/rollback |
|---|---|---|---|---|---|
| Registry/projections | Repository | Tool-managed | Deterministic regeneration | Hand editing | Byte comparison/Git revert |
| Scoped local properties | Application operator | Local apply | Atomic create/replace | Cross-profile fallback/commit | Build validation/ignored backup |
| Storefront token | Merchant/client config | Local reference | Store in scoped ignored file | Log/receipt/manifest | Redacted client readback |
| Shopify Admin token | Merchant/Admin | Session only | Fixed-operation use | Android/files/args/logs | Unset process variable |
| Catalog Menu | Merchant | Validate-only | None | Create/update/delete/reorder | Admin + Storefront fingerprint |
| Home definitions | Merchant-owned; Gate 8 owns expected schema contract | Create-if-missing | Create exact absent compatible contract | Update/delete incompatible schema | Admin readback; survives Git revert |
| Home selected entries | Merchant | Validate-only | None | Reconcile/update/delete/publish | Storefront/native readback |
| DRAFT acceptance probe | Merchant resource with receipt-bound tool attribution | Explicit probe create | One exact new DRAFT root per verified shop | Adopt collision/update/publish/delete | Stable GID/DRAFT readback |
| Customer discovery | Shopify | Inspect | None | Endpoint changes | Repeat discovery |
| Customer client registration | Merchant settings | Manual + validate | Sanitized local checkpoint | Invent API/claim from discovery | Manual evidence + consistency |
| Firebase project/app | Project owner | Validate-only | None | Create/delete/retarget/IAM/billing | Management API read |
| `google-services.json` | Local application config | Validate-only | None | Commit/rewrite/download | Semantic package/project/app check |
| Android manifest | Application | Repository-managed | Exact placeholder projection | Foreign host/autoVerify change | Merged manifest/package test |
| `assetlinks.json` | Web owner | Validate-only | None | Website mutation | Public HTTPS inspection |
| Receipts | Operator | Local ignored evidence | Canonical redacted write | Raw payload/token capture | Delete local receipt; copy sanitized summary only |

---

## L. Shopify Menu provisioning

- Selector remains `main-menu`.
- Gate 8 validates the exact handle, maximum three levels, 64 provider nodes, and current 24 accepted typed Collection actions.
- Gate 6 behavior remains authoritative: unsupported resource types do not become native actions; tag-filtered Collection links remain skipped; descendant traversal, duplicates, media handling, and partial failures remain unchanged.
- Admin URLs are merchant data, not routing authority.
- Title, hierarchy, order, URL, and Collection selection remain merchant-editable.
- Gate 8 v1 never calls `menuCreate`, `menuUpdate`, or delete.
- Missing Menu is `MANUAL_REQUIRED`.
- Extra menus are ignored.
- Admin success and Storefront readability are separate evidence.
- Repeated execution performs no mutation.
- Missing, valid-empty, malformed, and partial native projections remain distinct.

---

## M. Shopify Home provisioning

Gate 7 schema is unchanged:

| Type | Exact fields |
|---|---|
| `mobile_home` | required `schema_version:number_integer=1`; required `declared_section_count:number_integer` in `0..2`; optional `sections:list.mixed_reference`, max 2, child types only |
| `mobile_home_collection_grid` | required `title:single_line_text_field`, 1–80 code points; required `collections:list.collection_reference`, 1–6 |
| `mobile_home_featured_product` | required `title:single_line_text_field`, 1–80 code points; required `product:product_reference` |

All definitions are merchant-owned, `PUBLIC_READ`, and publishable.

Definition algorithm:

1. Inspect all three unprefixed types.
2. Normalize field keys/types/required flags/validations/access/capabilities; ignore response ordering.
3. If all are absent, create both child definitions, read them back, then create root using their returned GIDs.
4. If a compatible subset exists and root is absent, create only missing dependencies in order.
5. An existing root referencing absent/replaced child definition IDs blocks creation.
6. Any field/access/capability conflict or additional field is `BLOCKED_OPERATOR_SCHEMA_POLICY`.
7. Preserve display names/descriptions and all existing definitions.
8. Recheck each dependency immediately before create.
9. Never update or delete definitions.

Entry behavior:

- Existing `mobile_home/primary` and children are validate-only.
- Never reconcile titles, order, references, handles, or status.
- Optional probe requires explicit selection in Plan and Apply.
- Exact probe:

```text
type: mobile_home
handle: gate8-operator-acceptance-v1
schema_version: 1
declared_section_count: 0
sections: absent
status: DRAFT
```

- One probe is allowed per independently verified Shopify shop, not per profile alias.
- A matching handle without a prior receipt-bound shop/GID/field/DRAFT identity is a collision, not tool ownership.
- The probe is never selected by Android, published, updated, or automatically deleted.
- A second Apply produces zero writes.
- Storefront acceptance always reads the real configured `primary` root through the current Storefront and native contracts.

---

## N. Customer Account boundary

Each enabled profile declares:

- discovery origin;
- Customer Account GraphQL API version `2026-07`;
- exact required scopes;
- application-owned User-Agent;
- callback suffix/host/path;
- independently approved shop identity;
- controlled client-ID source.

Discovery validation preserves current native requirements:

- issuer `https://shopify.com/authentication/{numericShopId}`;
- exact independently pinned shop ID;
- authorization, token, logout, JWKS, and GraphQL fields;
- PKCE `S256`;
- authorization-code grant;
- `RS256`;
- fixed-size, no-redirect HTTP handling.

Callback:

```text
shop.{shopId}.{applicationSuffix}://{callbackHost}{callbackPath}
```

Gürbakır remains exactly:

```text
suffix: gurbakir
host: oauth
path: /callback
```

Manifest placeholder, derived callback, scoped local redirect, provider binding, and manual checkpoint must agree.

`CustomerAccountConfiguration` gains required `userAgent`. `Gurbakir-Android` is preserved. It must be bounded visible ASCII without header/control characters and applied to exchange and refresh requests.

Client creation and callback registration remain manual. `RecordManualCheckpoint` stores only:

- application/profile/shop identity;
- client-ID SHA-256;
- exact callback;
- UTC timestamp;
- sanitized evidence reference.

It does not change Shopify and does not prove a live OAuth journey.

Disabled Account profiles require no dummy configuration or provider initialization. Existing typed recovery/tombstone routes remain.

---

## O. Firebase boundary

- Preserve the existing all-four-or-none Gürbakır file contract.
- Development and staging remain different expected Firebase projects.
- Debug/release variants within one environment intentionally share its project.
- A release build type is not a production environment.
- Expected project ID, project number, and Android app IDs come from the provider binding, never from the candidate files.
- Compare each local file and Management API observation against:
  - exact variant package;
  - project ID;
  - project number;
  - Firebase Android app ID.
- Reject coherently swapped development/staging files and cross-brand project sharing.
- Zero files remains valid local-default behavior but does not pass configured Firebase acceptance.
- Partial/mismatched files fail.
- Configuration comparison is semantic; raw byte equality is not required.
- Synthetic remains physically Firebase-free.
- Gate 8 performs no Firebase project creation, Android app registration/removal, billing/IAM change, service enablement, or configuration-file download/write.
- Read-only `getConfig` may be inspected in memory and never printed or persisted.
- Firebase bearer credentials use only a profile-prefixed environment variable and a read-only scope.

---

## P. App Links and domain boundary

Keep distinct roles for Storefront API, media, checkout, legal/support, Collection App Link, Product App Link, Order App Link, Customer discovery, and Digital Asset Links.

- Generated manifest placeholders and runtime configurations must match the registry.
- Manifest path prefixes retain terminal `/`.
- Runtime base paths retain the current no-terminal-`/` form.
- Derivation removes exactly one declared terminal slash and rejects doubled separators.
- Merged-manifest tests assert scheme, host, path, application ID, callback, and `autoVerify=false`.
- Public inspection requests only `https://<declared-host>/.well-known/assetlinks.json`, with redirects disabled.
- Validate JSON, package, and known certificate fingerprints.
- A 403, missing association, or unavailable production fingerprint is reported as informational/nonblocking while `autoVerify=false`.
- Native host/path/package mismatch or unexpected `autoVerify=true` fails Gate 8.
- Gate 8 does not publish domain files or change verification/signing state.

---

## Q. Idempotence, drift, and recovery state machine

| State | Plan | Apply | Recovery |
|---|---|---|---|
| Absent/create-supported | `CREATE` | Exact bounded create | Read stable ID/state |
| Correct | `NO_OP` | No mutation | Stable receipt |
| Compatible existing | `NO_OP_COMPATIBLE` | Preserve metadata/content | Re-read |
| Incompatible | `BLOCKED_INCOMPATIBLE` | No mutation | Manual remediation |
| Changed after Plan | `BLOCKED_DRIFT` | Abort before write | New Inspect/Plan |
| Partial prior run | `RESUME_SAFE` or `BLOCKED_PARTIAL` | Only exact missing dependency | Receipt lists completed/pending |
| Read failure | `EXTERNAL_FAILURE` | No write | Bounded retry/reinspect |
| Ambiguous mutation | `PARTIAL_READBACK` | Stop all later writes | Reinspect stable identity |
| Storefront failure | `MOBILE_READBACK_FAILURE` | No compensating mutation | Diagnose visibility/token/selector |
| Merchant edit | `MERCHANT_DRIFT` | Preserve edit | New operator-reviewed Plan |

Operational bounds:

- Read retries: at most three attempts and 30 seconds total, honoring `Retry-After`.
- Writes are never blindly retried.
- GraphQL `errors`, mutation `userErrors`, and throttling are evaluated even on HTTP 200.
- Persist an intent/recovery receipt before each mutation.
- Failure to persist recovery evidence blocks the write.
- Reinspect the specific resource before every write.
- Use a local lock keyed by verified shop/resource identity.
- Do not claim remote transactions or protection against other machines/merchant races.
- On partial/ambiguous state, stop before further writes.
- Fixed pagination: 100 records per page, maximum five pages.
- Repeated cursor, incomplete results after page five, or unknown total fails comparison; absence cannot be inferred.
- Response limits: 1 MiB decompressed JSON, 128 KiB discovery document, nesting depth 32.
- Disable redirects on all HTTP clients.
- Never forward credentials between Admin, Storefront, Firebase, discovery, or association clients.
- Fixture traffic uses injected transports, never a production-host bypass.

---

## R. Secret and redaction contract

| Class | Source | Android | Handling |
|---|---|---|---|
| Storefront public token | Scoped local file or profile-prefixed generation variable | Allowed | Redacted from output/receipts |
| Shopify Admin token | `MB_<APP>_<PROFILE>_SHOPIFY_ADMIN_TOKEN` | Never | Process memory only |
| Customer Account client ID | Scoped local file/generation variable | Allowed | Routine output redacted; receipt hash only |
| Customer/OAuth token | Android protected runtime storage | Runtime only | Operator rejects |
| Firebase OAuth token | `MB_<APP>_<PROFILE>_FIREBASE_ACCESS_TOKEN` | Never | Process memory only |
| Service-account JSON | Unsupported | Never | Path/value input rejected |
| `google-services.json` | Exact ignored path | Existing build behavior | Identifiers/hashes only; no raw output |
| Signing material | Existing release mechanism | Build only | Out of Gate 8 |
| Receipts | `out/onboarding` | Never | Closed schema only |

- Privileged token maximum: 16 KiB visible ASCII, no CR/LF/control.
- Sanitize errors into allowlisted status/diagnostic codes before output.
- Never serialize raw exceptions, provider bodies, headers, GraphQL variables, PowerShell `ErrorRecord`, or child-process environment.
- Test success, error, warning, verbose, debug, information, and progress streams.
- Do not enable PowerShell transcripts for credential-bearing commands.
- Child Gradle/proof processes receive a sanitized environment with Admin/Firebase bearer variables removed.
- Never clear unrelated user environment variables globally.
- Public Storefront tokens remain controlled public client values, distinct from private credentials.

---

## S. Second-store decision

**Option A — deferred.**

Gate 8 closes after current project-owned nonproduction configured acceptance and adversarial fixture/self-test coverage. A second owned Storefront/application pilot is a separate, explicitly approved slice before Gate 9.

Synthetic remains offline and cannot be relabeled as second-store proof.

---

## T. File-by-file change map

| Path | Change | Responsibility |
|---|---|---|
| `docs/multi-brand/plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.md` | Create first | Durable approved plan |
| `docs/multi-brand/plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.sha256` | Create beside plan | Approval artifact digest |
| `config/onboarding/application-registry.v1.json` | Create | Enrollment/configuration authority |
| `config/onboarding/shopify-home-schema.v1.json` | Create | Exact Gate 7 provider schema |
| `config/onboarding/provider-binding.schema.v1.json` | Create | Closed non-secret binding schema |
| `config/onboarding/operator-receipt.schema.v1.json` | Create | Closed receipt schema |
| `config/onboarding/generated/gurbakir/development.properties` | Create | Deterministic projection |
| `config/onboarding/generated/gurbakir/staging.properties` | Create | Deterministic projection |
| `config/onboarding/examples/gurbakir-development.properties.example` | Create | Scoped client template |
| `config/onboarding/examples/gurbakir-staging.properties.example` | Create | Scoped client template |
| `scripts/Update-OnboardingProjections.ps1` | Create | Explicit projection generator |
| `scripts/onboarding/Onboarding.Common.psm1` | Create | UTF-8 parsing, paths, hashing, redaction, HTTP policy |
| `scripts/onboarding/Onboarding.Registry.psm1` | Create | Registry/binding/projection/enrollment validation |
| `scripts/onboarding/Onboarding.Operator.psm1` | Create | State machine, receipts, drift/recovery |
| `scripts/onboarding/Onboarding.Shopify.psm1` | Create | Static Menu/Home operations |
| `scripts/onboarding/Onboarding.CustomerAccount.psm1` | Create | Discovery/callback/checkpoint |
| `scripts/onboarding/Onboarding.Firebase.psm1` | Create | Local/Management API read validation |
| `scripts/onboarding/Onboarding.AppLinks.psm1` | Create | Manifest/public association inspection |
| `scripts/Invoke-MultiBrandOnboarding.ps1` | Create | Public CLI |
| `scripts/Migrate-GurbakirLocalConfiguration.ps1` | Create | Explicit one-profile migration |
| `scripts/Get-RegisteredGradleTasks.ps1` | Create | Validated task selection |
| `scripts/Test-MultiBrandOnboarding.ps1` | Create | Self-test harness and temporary fixtures |
| `app/build.gradle.kts` | Modify | Profile projections/local inputs/BuildConfig/manifest/Firebase preflight |
| `storefront/build.gradle.kts` | Modify | Explicit proof target; remove root config |
| `app/src/main/AndroidManifest.xml` | Modify | Role-specific placeholders; retain `autoVerify=false` |
| `foundation/src/main/kotlin/com/gurbakir/foundation/config/AppConfiguration.kt` | Modify | Add required Customer Account User-Agent |
| `foundation/src/test/kotlin/com/gurbakir/foundation/config/AppConfigurationTest.kt` | Modify | User-Agent/config validation |
| `account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenClient.kt` | Modify | Use configured User-Agent |
| `account/src/test/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenClientTest.kt` | Create | Exchange/refresh User-Agent JVM tests |
| `account/src/androidTest/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenRequestContractTest.kt` | Modify | Android exchange/refresh request contract |
| `app/src/main/kotlin/com/gurbakir/mobile/config/BuildConfigurationSource.kt` | Modify | Consume projected app/profile values |
| `app/src/main/kotlin/com/gurbakir/mobile/brand/GurbakirBrand.kt` | Modify | Project identity/legal values; preserve design/assets |
| `app/src/main/kotlin/com/gurbakir/mobile/navigation/GurbakirDeepLinkConfiguration.kt` | Modify | Role-specific links |
| `app/src/main/kotlin/com/gurbakir/mobile/GurbakirApp.kt` | Modify | Projected Order link |
| `app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt` | Modify | Projected DB/Search identity |
| `app/src/main/kotlin/com/gurbakir/mobile/legal/OwnedPagePolicy.kt` | Modify | Configured legal origin and named routes |
| `app/src/main/kotlin/com/gurbakir/mobile/legal/LegalSupportRepository.kt` | Modify | Exact named legal URLs |
| `app/src/test/kotlin/com/gurbakir/mobile/config/BuildConfigurationSourceTest.kt` | Modify | No-fallback/exact identities |
| `app/src/test/kotlin/com/gurbakir/mobile/GurbakirCompositionTest.kt` | Modify | Exact preserved composition |
| `app/src/test/kotlin/com/gurbakir/mobile/MediaPolicyCompositionTest.kt` | Modify | Separate media role |
| `app/src/test/kotlin/com/gurbakir/mobile/legal/LegalSupportRepositoryTest.kt` | Modify | Named URLs/foreign-host rejection |
| `app/src/androidTest/kotlin/com/gurbakir/mobile/GurbakirDeepLinkIntegrationTest.kt` | Modify | Manifest/runtime equality |
| `storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontReadProofTest.kt` | Modify | Explicit target resolver |
| `storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontCartProofTest.kt` | Modify | Explicit target resolver; retain separate opt-in |
| `storefront/src/test/kotlin/com/gurbakir/storefront/OwnedCatalogDiscoveryProofTest.kt` | Modify | Explicit Menu readback |
| `storefront/src/test/kotlin/com/gurbakir/storefront/OwnedHomeContentReadbackTest.kt` | Create | Durable live Home proof |
| `storefront/src/test/kotlin/com/gurbakir/storefront/OwnedOnboardingConfiguration.kt` | Create | Strict proof-only resolver |
| `scripts/Provision-CustomerAccountDiscovery.ps1` | Modify | Compatibility wrapper requiring app/profile |
| `scripts/Test-FirebaseConfiguration.ps1` | Modify | Registry/binding-aware validation |
| `scripts/Test-RepositoryPortability.ps1` | Modify | Enrollment/projection/current-config checks |
| `scripts/Test-PublicReadiness.ps1` | Modify | Registry-aware identities and paths |
| `scripts/Test-PublicReadiness.Tests.ps1` | Modify | Counterexamples |
| `scripts/Test-Gate2SyntheticPackage.ps1` | Modify | Registry role input without weaker checks |
| `.github/workflows/android-foundation.yml` | Modify | Run onboarding tests/resolved tasks; retain job names |
| `.gitignore` | Modify | Scoped local files/backups/receipts |
| `config/local.defaults.properties` | Delete | Superseded root defaults |
| `config/local.properties.example` | Delete | Replaced by scoped examples |
| `docs/architecture/BRAND-ONBOARDING.md` | Modify | Registry/operator/ownership model |
| `docs/architecture/MULTI-BRAND-ARCHITECTURE.md` | Modify | Explicit enrollment/local operator decision |
| `docs/README.md` | Modify twice | Feature PR: in-progress/config map; later PR: closure status |
| `docs/multi-brand/README.md` | Modify twice | Feature PR: plan/evidence link; later PR: closure status |
| `README.md` | Modify | Current developer/operator commands |
| `AGENTS.md` | Modify twice | Feature PR: usable command/config guidance; later PR: closure status only |
| `docs/multi-brand/GATE-8-COMPLETION-HANDOFF.md` | Create near candidate | Pre-merge evidence and limitations |

`settings.gradle.kts`, synthetic production implementation/resources, prior Gate handoffs, research/audit history, and Phase 3 records remain unchanged.

---

## U. Step-by-step implementation sequence

### Task 0 — Freeze approval artifact and safe execution base

**Files:** durable plan and SHA sidecar.

**Behavior:** Export this exact plan with UTF-8/LF, compute SHA-256, verify it matches the digest cited by owner approval, reverify remote/main/CI/ruleset/worktrees, and create `codex/multibrand-gate-8-provisioning` in `C:\src\projects\multi-brand-commerce-android-gate8`.

**Precondition:** Explicit owner execution authority cites the plan digest.

**GREEN commands:**

```powershell
git fetch origin --prune
git ls-remote --heads origin
git status --short --branch
git worktree list --porcelain
Get-FileHash -Algorithm SHA256 docs/multi-brand/plans/GATE-8-PROVISIONING-AND-ONBOARDING-IMPLEMENTATION-PLAN.md
```

**Expected:** exact approved digest, verified base, no conflicting branch/path.

**Side effects:** isolated worktree/branch and plan documentation only.

**Rollback:** remove only the new worktree/branch after confirming it contains no user work.

**Commit:** `docs(gate8): freeze provisioning and onboarding plan`

### Task 1 — Registry, binding, receipt, and projection contracts

**Files:** all `config/onboarding` contracts/projections/examples, projection generator, Common/Registry modules, offline CLI surface, Registry self-tests.

**Interfaces produced:**

- strict registry/profile resolver;
- strict provider-binding resolver;
- strict receipt reader/writer;
- deterministic projection generator/validator;
- `Validate` and `Validate -ProjectionOnly`.

**RED cases:** valid current records, wrong frozen Gürbakır/synthetic identity, future fixture isolation, duplicates/case collisions, unknown fields/roles, unsupported version, unsafe path/domain, Unicode/control/size limits, persistence/application/callback conflicts, aliasing rules, projection tampering, UTF-8 and `=` round trip, invalid receipt/binding fields.

**Expected RED:** current repository has no contract/parser/projection implementation.

**GREEN:**

```powershell
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite Registry
pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1 -Command Validate
```

**Expected:** deterministic PASS and byte-identical regenerated projections.

**External side effects:** none; temporary test roots only.

**Rollback:** protected Git revert.

**Commit:** `feat(gate8): add versioned application enrollment registry`

### Task 2 — Scoped build/runtime configuration cutover

**Files:** Gradle scripts, manifest, foundation/account/app sources/tests, Storefront proof resolver/tests, migration helper, ignore/config/docs/active validators.

**Interfaces consumed:** Task 1 projection/config readers.

**Interfaces produced:** profile-scoped BuildConfig/resources/manifest and application-owned Customer Account User-Agent.

**RED cases:** development reads staging; root fallback; missing/partial scoped input; tracked-field override; wrong IDs/callback/storage/domain; User-Agent not applied to exchange/refresh; strict selection mismatch; Home-disabled migration silently enabled; stale projection accepted by direct Gradle; Unicode corruption; unsafe migration write.

**Expected RED:** current root loader and hardcoded shared User-Agent violate the assertions.

**GREEN:**

```powershell
.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest `
  :app:testDevelopmentDebugUnitTest :app:testStagingDebugUnitTest `
  :app:processDevelopmentDebugMainManifest `
  :app:processStagingDebugMainManifest `
  :storefront:testDebugUnitTest

pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite Configuration
```

**Expected:** exact current Gürbakır values and no fallback.

**External side effects:** Gradle outputs only; migration tests use temporary roots.

**Rollback:** revert source; old ignored root input remains untouched and becomes active only after code rollback.

**Commit:** `refactor(gate8): scope configuration by application and profile`

### Task 3 — Explicit enrollment and CI coverage

**Files:** task resolver, structural validators, workflow, enrollment tests.

**RED cases:** missing enrolled module, unregistered app, directory mismatch, false AGP role, shared→app, app→app, core→Firebase, synthetic Firebase/INTERNET/credential state, future app without minimum CI, empty self-consistent lanes, missing either API 23 task, unknown task, child failure masked by later success.

**GREEN:**

```powershell
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite Enrollment
.\scripts\Test-RepositoryPortability.ps1 -SelfTest
.\scripts\Test-PublicReadiness.ps1 -SelfTest
pwsh -NoProfile -File scripts/Get-RegisteredGradleTasks.ps1 -Lane unit -ValidateOnly
pwsh -NoProfile -File scripts/Get-RegisteredGradleTasks.ps1 -Lane assemble -ValidateOnly
pwsh -NoProfile -File scripts/Get-RegisteredGradleTasks.ps1 -Lane api30 -ValidateOnly
pwsh -NoProfile -File scripts/Get-RegisteredGradleTasks.ps1 -Lane api23 -ValidateOnly
```

**External side effects:** none beyond temporary fixtures.

**Rollback:** workflow and validators revert together.

**Commit:** `build(gate8): enforce explicit enrollment and CI coverage`

### Task 4 — Read-only operator inspection and diff

**Files:** Operator/Shopify/Customer/Firebase/AppLinks modules and CLI.

**Interfaces produced:** fixed provider clients, state classifications, redacted PLAN receipts.

**RED cases:** no-op/absent/compatible/incompatible, independent shop mismatch, non-Gürbakır discovery, coherent Firebase swap, public association classification, redirects, 100×5 pagination, repeated cursor, oversized/chunked/invalid UTF-8 response, GraphQL HTTP-200 errors, throttling, secret leakage on every stream, synthetic credential lookup.

**GREEN:**

```powershell
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite OperatorReadOnly
```

**External side effects:** none; injected fake transports only.

**Rollback:** Git revert.

**Commit:** `feat(gate8): add read-only provider inspection and diff`

### Task 5 — Bounded apply, idempotence, and recovery

**Files:** Operator/Shopify modules, Home schema, receipt schema, self-tests.

**RED cases:** missing confirmation/receipt; stale/tampered receipt; target/digest mismatch; missing definitions; compatible partial set; existing-root/missing-child conflict; incompatible schema; ambiguous write; later-step drift; receipt persistence failure; probe collision/ACTIVE state; same-shop reuse; second-run write; raw exception leakage.

**GREEN:**

```powershell
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite OperatorApply
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite Security
```

**External side effects:** fake adapters and temporary files only.

**Rollback:** Git revert.

**Commit:** `feat(gate8): add bounded Shopify apply and recovery receipts`

### Task 6 — Configured nonproduction acceptance

**Files:** ignored local profile files/bindings/receipts only; no tracked source edits while gathering evidence.

**Behavior:** Execute Section V for development and staging.

**External effects:** exact scoped local writes; exact missing Home definitions only if absent; one explicitly authorized DRAFT probe per verified shop.

**Rollback:** restore ignored local backup. Provider-created resources remain identified and inactive; no automatic delete.

**Commit:** none until sanitized evidence is written in Task 7.

### Task 7 — Stable candidate verification and handoff

Run:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck detekt lint

.\gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest `
  :checkout:testDebugUnitTest :storefront:testDebugUnitTest `
  :firebase:testDebugUnitTest :mobile-core:testDebugUnitTest `
  :synthetic:testDebugUnitTest :app:testDevelopmentDebugUnitTest `
  :app:testStagingDebugUnitTest

.\gradlew.bat :mobile-core:assembleDebug :mobile-core:assembleRelease `
  :app:assembleDevelopmentDebug :app:assembleDevelopmentRelease `
  :app:assembleStagingDebug :app:assembleStagingRelease `
  :app:assembleDevelopmentDebugAndroidTest `
  :app:assembleStagingDebugAndroidTest `
  :synthetic:assembleDebug :synthetic:assembleRelease `
  :synthetic:assembleDebugAndroidTest

.\scripts\Test-Gate2SyntheticPackage.ps1 -SelfTest
.\scripts\Test-Gate2SyntheticPackage.ps1 -Variant All
.\scripts\Test-FirebaseConfiguration.ps1
.\scripts\Test-PublicReadiness.ps1 -SelfTest
```

Also run every onboarding suite, projection byte validation, applicable live public-readiness validation, Gitleaks tree/history scans, and `Test-RepositoryPortability.ps1 -RequireCleanWorktree` without concurrent Gradle model queries.

Write `GATE-8-COMPLETION-HANDOFF.md` with observed classifications only. Commit it before final exact-candidate review and CI.

**Commit:** `docs(gate8): record provisioning and onboarding evidence`

Any later source correction requires affected local reruns and fresh exact-candidate review/CI.

### Task 8 — Protected PR, merge, and lifecycle reconciliation

1. Push the Gate 8 branch and open a protected PR.
2. Require exact PR-head review/security triage.
3. Require successful `validate`, API 30 `instrumentation`, and API 23 `minimum-sdk-instrumentation`.
4. Merge through protected PR flow only.
5. Verify merged-main ancestry/tree and canonical post-merge CI.
6. Only then declare technical closure.
7. Open a narrow lifecycle reconciliation PR changing only current status in `AGENTS.md`, `docs/README.md`, and `docs/multi-brand/README.md`.
8. Require exact-head and post-merge CI for that PR.
9. Do not start the second-store pilot or Gate 9.

The feature PR may update command/configuration guidance in `AGENTS.md`; it must still say Gate 8 is in progress. Only the reconciliation PR may state closure.

---

## V. External nonproduction acceptance procedure

Execute for `gurbakir/development` and `gurbakir/staging`.

1. Establish independently approved provider bindings.
2. Confirm both profiles share one Shopify resource group while Firebase projects remain distinct.
3. Restore complete scoped Storefront/Account inputs without printing them.
4. Run offline `Validate`; require exact projection, native, package, and role consistency.
5. Validate the four-file Firebase contract against its independent binding.
6. Run `Inspect` and `Plan`.
7. Confirm verified shop/project targets and zero Menu/selected-Home/Firebase/web mutation.
8. Review the exact receipt action set.
9. Apply only missing compatible Home definitions and, if explicitly authorized, the one DRAFT probe.
10. Read back Admin/provider state.
11. Run exact Storefront proofs:

```powershell
.\gradlew.bat :storefront:testDebugUnitTest `
  --tests "com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest" `
  -PgurbakirRunOwnedStorefrontProof=true `
  -PonboardingApplication=gurbakir `
  -PonboardingProfile=development
```

```powershell
.\gradlew.bat :storefront:testDebugUnitTest `
  --tests "com.gurbakir.storefront.OwnedHomeContentReadbackTest" `
  -PonboardingRunOwnedHomeReadback=true `
  -PonboardingApplication=gurbakir `
  -PonboardingProfile=development
```

Repeat for staging. Opted-in missing/invalid configuration fails; it does not skip. The Cart proof remains separately opt-in and is never enabled by onboarding commands.

12. Validate Customer Account issuer, client, callback, manifest, and manual checkpoint.
13. Validate Firebase package/project/app/config consistency.
14. Record public assetlinks status separately.
15. Repeat fresh Plan/Apply and require zero writes.
16. Compare selected Menu/Home fingerprints and preserve merchant edits.
17. Run strict configured builds for both profiles.
18. Inspect effective application ID, manifest, callback, links, Firebase state, dependencies, permissions, and redacted public configuration against independent expectations.
19. Exercise Gate 6/7 journeys for development and staging on an approved device or managed emulator: Home refresh/selection, Categories, typed navigation/Back, capabilities, and support behavior.
20. Do not place orders, submit payments, mutate customers, or enable Cart mutation proof.
21. Rehearse local rollback according to whether the scoped file originally existed.
22. Leave the probe DRAFT, unselected, and receipt-identified.
23. Classify every row as `PASS`, `FAIL`, `NOT_RUN`, `PARTIAL`, or `EXTERNALLY_BLOCKED`.

If definitions already exist, live definition creation remains `NOT_RUN` or `PARTIAL`; fake creation/recovery coverage remains mandatory. A DRAFT entry proves entry creation/idempotence, not definition creation.

A required configured provider row that fails or remains unavailable blocks technical closure. Informational current-`autoVerify=false` association rows do not.

---

## W. Verification matrix

| Claim | Required proof |
|---|---|
| Explicit app/profile resolution | Registry tests, Gradle tests, configured builds |
| No Gürbakır fallback | Missing/cross-profile/root-inert fixtures |
| Registry compatibility | Strict parser and schema-version tests |
| Projection integrity | Exact bytes, UTF-8 round trip, direct Gradle tamper failure |
| Explicit enrollment | Registry/settings validator |
| Dependency direction | Evaluated Gradle dependency/role validation |
| Enrolled-app CI coverage | Independent role/variant minimums and real task existence |
| Synthetic isolation | Structural checks, package proof, API 23/30 CI |
| Firebase isolation | Independent binding, local JSON, Management API, package inspection |
| Customer callback consistency | Discovery, derivation, manifest, client, manual checkpoint |
| App Link consistency | Registry/merged-manifest/runtime equality |
| Public association status | Separate informational live inspection |
| Menu idempotence/readability | Admin fingerprint, Storefront proof, repeat Plan |
| Home definition compatibility | Fixtures and Admin inspection |
| Home bootstrap idempotence | Fake definition tests and live probe evidence |
| Merchant edit preservation | Counterexamples and live before/after fingerprints |
| Storefront readback | Actual Apollo/gateway/mapper tests and native configured journeys |
| Dry-run no mutation | Fake counters and live Plan |
| Drift detection | Receipt/fingerprint/reinspection tests |
| Credential redaction | All-stream/exception/receipt tests and Gitleaks |
| Recovery | Partial/ambiguous fixtures and configured rehearsal |
| Gürbakır compatibility | JVM, variants, manifest, Gate 6/7 runtime journeys |
| Required GitHub checks | Exact PR-head and post-merge runs |
| Second-store onboarding | Explicitly not claimed |

---

## X. Security and privacy review

| Threat | Mitigation |
|---|---|
| Privileged token leakage | Environment-only intake, value-aware redaction, no args/files/receipts |
| Wrong-shop targeting | Independently approved binding and canonical `myshopify.com` Admin host |
| Production targeting | Nonproduction release boundary plus typed confirmations |
| SSRF | Fixed provider endpoints and declared role hosts only |
| Arbitrary GraphQL | Static documents; no query/file/endpoint parameters |
| Path traversal/reparse escape | Canonical containment and ignored-target checks |
| Shell injection | No `Invoke-Expression` or constructed shell strings |
| Manifest tampering | Closed schema, duplicate detection, digests, projection preflight |
| Receipt tampering | Closed schema, expiry, four digests, recomputed allowed actions |
| Cross-profile bleed | Explicit app/profile and no fallback |
| Firebase sharing | Independent ownership/project checks |
| Callback/domain hijack | Exact cross-system identity validation |
| Merchant edit loss | Validate-only content and no reconcile/delete |
| TOCTOU | Per-write reinspection, local resource lock, read-after-write |
| Retry storm | Three reads/30 seconds; no blind write retry |
| Partial apply | Intent receipt before write, stop after ambiguity, exit 8 |
| Malicious response | Size/depth/type/host/ID/pagination bounds |
| CI credential inheritance | Credential-free required CI and sanitized child environments |
| Future identity collision | Global application ID, callback, storage, and provider checks |

Any observed credential exposure is recorded honestly by credential class and evaluated for rotation.

---

## Y. Documentation and evidence plan

- Make the approved plan and its SHA sidecar durable before substantive implementation.
- Feature PR documentation records Gate 8 as in progress and exposes usable commands/configuration.
- Preserve all Gate 6/7 handoffs, audits, migration evidence, and Phase 3 records.
- Completion handoff is an immutable pre-merge candidate record.
- Receipts contribute only sanitized identities, hashes, and classifications.
- Exact merge and post-merge evidence goes into current lifecycle indexes, not historical handoffs.
- Closure status is applied only in the separate reconciliation PR.

---

## Z. Rollback

### Repository

Use a protected revert PR. Source rollback does not undo external state.

### Local configuration

Restore the exact ignored backup or remove only a file proven absent before the operation. Never commit either version.

### Shopify Menu

No mutation; verify fingerprints remain unchanged.

### Home definitions

Created definitions survive Git revert. The tool never deletes them. Partial compatible creation may resume; incompatible state blocks.

### Home entries

Selected merchant content is untouched. A tool-created probe remains DRAFT and unselected. Cleanup requires separate manual authorization.

### Customer Account

Discovery/checkpoint writes have no provider effect. Any manually changed registration survives Git revert and must be restored manually from captured prior settings.

### Firebase

Gate 8 is read-only. Existing projects/apps/configuration survive Git revert. Project deletion is not rollback.

### App Links/web

No website mutation. Manifest changes revert through Git; external associations are unaffected.

---

## AA. Deferred work

- Separate second owned Storefront/application pilot.
- Gate 9 replanning and cumulative conformance.
- P3-16 and production release readiness.
- Production Firebase, Customer Account, App Links, signing, Play Console, privacy/Data Safety, and rollout.
- Real orders, payments, customers, or production data.
- Runtime brand/market/language switching.
- Hosted control plane, generic module/source generator, CMS, or provider framework.
- Full international commerce.
- Custom-font completion, broad UI work, namespace rename, dependency upgrades, or unrelated extraction.

---

## AB. Closure criteria

### Contract frozen

- Exact durable plan and SHA sidecar exist.
- Owner approval cites the digest.
- Registry, bindings, receipts, configuration, ownership, credential, and second-store decisions are fixed.

### Implementation complete

- Registry/config/enrollment/operator behavior exists.
- Focused local tests pass.
- All current applications/modules remain covered.
- Ordinary CI remains credential-free.

### Configured nonproduction acceptance complete

- Required development and staging rows pass.
- Menu/Home read through the actual Storefront client.
- Customer/Firebase identities validate.
- Authorized Apply and repeat no-op execute against a verified target.
- Merchant-selected content remains unchanged.
- Unexercised greenfield branches remain honestly `NOT RUN`/`PARTIAL` and are excluded from claims.

### Merge-ready

- Clean stable candidate.
- Full local deterministic matrix complete.
- Security/privacy and whole-candidate review complete.
- Completion handoff records exact evidence and limitations.

### Gate 8 technically closed

- Required configured acceptance complete.
- Exact PR-head required checks pass.
- Protected implementation PR merges.
- Merged-main ancestry/tree verifies.
- Canonical merged-main required checks pass.

### Current documentation reconciled

- Narrow lifecycle PR merges and passes exact-head/post-merge checks.
- Current authority records Gate 8 closed.
- Historical handoffs remain unchanged.
- Second-store pilot, Gate 9, and P3-16 remain unstarted.

---

## AC. Remaining true blockers

No blocker prevents final planning or offline implementation.

Before owner approval, the exact durable approval artifact must receive a new SHA-256; the superseded submission digest cannot authorize this revision.

Configured acceptance requires:

- approved scoped Storefront/Admin/Firebase inputs;
- independently approved provider bindings;
- access to current Customer Account registration settings or equivalent sanitized evidence;
- fresh classification of prior Customer discovery HTTP 403 behavior.

Unavailable required Customer/provider evidence blocks configured acceptance and technical closure. Unavailable Digital Asset Links/production-signing evidence remains a visible but nonblocking status while `autoVerify=false`.

No second store is required for Gate 8 closure. Its absence blocks only the separately named pilot and any second-store claim.
