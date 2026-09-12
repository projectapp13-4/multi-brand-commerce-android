# Gürbakır Legacy Identities

Status: **Migration-safety register; Gate 6 and repository-root continuity reverified**

This file records existing Gürbakır identifiers that later multi-brand work must preserve, migrate explicitly, or keep classified as unresolved. It contains no credentials or private signing material.

Identity values below were preserved from the 2026-09-01 register and reverified during Gate 3 against last implementation checkpoint `ee476adb8204c81cb1c24420180297f6fe7ad0ea`. Ignored configuration contents were not copied into this document; Firebase continuity evidence recorded only safe file/package membership facts.

Gate 4 rechecked tracked continuity from baseline `6a368ab7409581f4a2bdd056376ad380032a9654` through the final Gate 4 branch and merge candidate. Build files, Gürbakır manifest, Room/schema/migration, Search/Wishlist partition wiring and encrypted store implementations have no Gate 4 diff. The moved Account configuration retains every prior BuildConfig input, and the app's protected-store literals remain exact. The Order base moved from common deep-link configuration to app-owned Account bindings without changing its value. Final PR and post-merge validation completed without introducing an identity or persistence migration. This is source/identity continuity evidence, not a new installed-upgrade or live-registration test; see the preserved [Gate 4 handoff](../multi-brand/GATE-4-COMPLETION-HANDOFF.md) for implementation evidence and limits.

## Current non-production application IDs

| Variant | Application ID | Classification |
|---|---|---|
| `developmentDebug` | `com.gurbakir.mobile.dev.debug` | Preserve exact existing Gürbakır non-production identity |
| `developmentRelease` | `com.gurbakir.mobile.dev` | Preserve exact existing Gürbakır non-production identity |
| `stagingDebug` | `com.gurbakir.mobile.staging.debug` | Preserve exact existing Gürbakır non-production identity |
| `stagingRelease` | `com.gurbakir.mobile.staging` | Preserve exact existing Gürbakır non-production identity |

The `defaultConfig` fail-closed value is:

```text
com.gurbakir.mobile.unconfigured
```

It is a build-safety default, not a production identity. Do not repurpose it as one.

## Internal namespace and project naming

```text
app        = com.gurbakir.mobile
mobile-core = com.gurbakir.mobile.core
foundation = com.gurbakir.foundation
storefront = com.gurbakir.storefront
account    = com.gurbakir.account
checkout   = com.gurbakir.checkout
firebase   = com.gurbakir.firebase
historical root project = gurbakir-android
public repository root project = multi-brand-commerce-android
```

The Gradle root name is repository/build identity, not an Android application or
persistence identity. The fresh public repository therefore uses
`multi-brand-commerce-android`. This one-time project-level change does not
authorize changing any namespace, package, application ID, database,
preference, Keystore, OAuth, App Link, Firebase, provider, or runtime string.
The remaining names are historical internal or compatibility-sensitive names;
they must not be renamed merely for aesthetics. Namespace neutralization is not
proof of multi-brand ownership.

## Room database and schema continuity

```text
database filename = gurbakir-local.db
database class = com.gurbakir.mobile.search.LocalCommerceDatabase
schema version = 2
exported schemas = 1.json, 2.json
migration = WISHLIST_MIGRATION_1_2 / Migration(1, 2)
```

`gurbakir-local.db`, the exported schema chain and the explicit `1 -> 2` migration are compatibility state for existing Gürbakır installs. Gate 1 moved the database implementation and schema ownership to `:mobile-core`, while `:app` still selects the exact physical filename and retains environment/market partition composition. The move changed source ownership, not persisted identity. Adding a new brand does not justify adding `brandId` to every table or migrating this database.

## Customer Account secure persistence

Gate 3 replaced internal environment-string derivation with exhaustive application-owned identity literals. The resulting persisted identities are byte-for-byte unchanged:

| Environment | SharedPreferences | Android Keystore alias |
|---|---|---|
| Development | `gurbakir_secure_customer_session_development` | `gurbakir.customer.session.development.v1` |
| Staging | `gurbakir_secure_customer_session_staging` | `gurbakir.customer.session.staging.v1` |

The reusable customer-session store consumes the supplied `ProtectedStoreIdentity` verbatim and contains no Gürbakır default. Both identities and the v2 encrypted wire format remain exact unless an explicit secure-storage migration is designed, tested on upgrade and proven not to orphan active sessions. No token or encrypted payload belongs in documentation.

## Cart secure persistence

| Environment | SharedPreferences | Android Keystore alias |
|---|---|---|
| Development | `gurbakir_secure_cart_development` | `gurbakir.cart.development.v1` |
| Staging | `gurbakir_secure_cart_staging` | `gurbakir.cart.staging.v1` |

The reusable cart store likewise consumes the exact app-owned identity. Gate 3 preserved v2 writes, v1/v2 reads, v1 quarantine, ownership codes, preference payload keys, AES/GCM parameters and clear-without-key-deletion behavior. These identities and wire contracts follow the same explicit-migration rule.

## Persisted Search normalization and partitions

Gürbakır Search normalization remains the fixed locale tag `tr-TR` for all foreground UI locales. Existing durable partition keys remain `DEVELOPMENT/TR` and `STAGING/TR`; Wishlist uses the same environment/market partitions. Gate 3 moved market ownership into `AppConfiguration` but did not rewrite stored keys or infer normalization from the foreground locale.

## Firebase package registration contract

Tracked build validation expects the following exact packages when Firebase is configured:

```text
com.gurbakir.mobile.dev.debug
com.gurbakir.mobile.dev
com.gurbakir.mobile.staging.debug
com.gurbakir.mobile.staging
```

The configuration set must be absent or complete for all four non-production variants. Current validation requires exactly one matching client per package, one shared Firebase project for development debug/release, one shared project for staging debug/release, and distinct development/staging projects.

This register records only the public package contract. It does not record `google-services.json` contents, project identifiers, credentials or service-account material.

## Customer Account OAuth callback

Tracked discovery constructs:

```text
shop.<shopId>.gurbakir://oauth/callback
```

Tracked fail-closed manifest scheme:

```text
shop.unconfigured.gurbakir
```

The concrete shop ID and mobile-client registration are ignored/external and migration-sensitive. Do not invent or document a concrete value. The callback must remain aligned among application configuration, Android manifest handling and the project-owned Shopify registration.

## Current App Link routes

The tracked main manifest contains:

```text
https://gurbakir.com/collections/
https://gurbakir.com/apps/mobile/products/
https://gurbakir.com/apps/mobile/orders/
```

All three intent filters currently use `android:autoVerify="false"`. These are tracked routing contracts, not proof of verified production App Links or deployed domain association.

## Production and release identity

The following remain outside tracked current source and governed by the [P3-16 entry handoff](../phase3/P3-16-HANDOFF.md):

```text
production applicationId: UNRESOLVED / NOT REPRESENTED
production signing: UNRESOLVED / NOT REPRESENTED
production Firebase: UNRESOLVED / EXTERNAL
production Customer Account callback: UNRESOLVED / EXTERNAL
verified production App Links: UNRESOLVED / EXTERNAL
Play ownership/listing: UNRESOLVED / EXTERNAL
```

Gate 1 did not provision or infer any of them.

## Provenance

| Recorded fact | Tracked source reverified |
|---|---|
| Current root project and exact module set | `settings.gradle.kts` |
| Application IDs, namespaces, Firebase package validation and fail-closed OAuth scheme | `app/build.gradle.kts` and module build files |
| App Link hosts/paths and `autoVerify` state | `app/src/main/AndroidManifest.xml` |
| Room filename and registered migration | `app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt` |
| Room class and version | `mobile-core/src/main/kotlin/com/gurbakir/mobile/search/LocalCommerceDatabase.kt` |
| Exported schemas | `mobile-core/schemas/com.gurbakir.mobile.search.LocalCommerceDatabase/1.json`, `2.json` (with the retained `SearchHistoryDatabase` history in the same schema root) |
| Explicit `1 -> 2` migration | `mobile-core/src/main/kotlin/com/gurbakir/mobile/wishlist/WishlistRepository.kt` |
| Exact Customer Account and cart identities | `app/src/main/kotlin/com/gurbakir/mobile/config/BuildConfigurationSource.kt` |
| Reusable protected-store identity consumption | `account/src/main/kotlin/com/gurbakir/account/session/AndroidKeystoreCustomerSessionStore.kt` and `storefront/src/main/kotlin/com/gurbakir/storefront/AndroidKeystoreCartSessionStore.kt` |
| Search normalization and environment/market partitions | `app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt` and `app/src/main/kotlin/com/gurbakir/mobile/di/WishlistModule.kt` |
| Callback construction | `scripts/Provision-CustomerAccountDiscovery.ps1` |
| Production/release unknowns | `docs/phase3/P3-16-HANDOFF.md` |

## Migration rule

For each registered identity, later work must choose one explicit outcome:

1. preserve it exactly;
2. migrate it through a separately reviewed and tested compatibility procedure; or
3. retain its `UNRESOLVED / EXTERNAL` status until authoritative production state exists.

Silently renaming or filling an unknown is not permitted. See [Multi-Brand Architecture](MULTI-BRAND-ARCHITECTURE.md), [Brand Boundaries](BRAND-BOUNDARIES.md) and [ADR-0004](../decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md).

Gate 1 changed module/source provenance only. Gate 3 changed where focused inputs are owned without changing the four application IDs, Firebase package contract, OAuth/App Link identities, physical database name, Room schema/history/migration, persisted Search keys, or Customer Account/cart protected-store names, aliases and wire formats. Gate 4 changed capability/navigation composition and moved the unchanged Order base into app-owned Account bindings without introducing a persistence or identity migration. Gate 5 changed provider ownership without changing those identities. Gate 6 changed Categories discovery without changing runtime/application or persistence identity. The fresh-public-repository migration changes only the Gradle root/repository identity recorded above. Independent frozen-reader/writer instrumentation and schema-byte comparison are recorded in the [Gate 3 completion handoff](../multi-brand/GATE-3-COMPLETION-HANDOFF.md); later gate continuity and evidence limits remain in their individual completion handoffs.
