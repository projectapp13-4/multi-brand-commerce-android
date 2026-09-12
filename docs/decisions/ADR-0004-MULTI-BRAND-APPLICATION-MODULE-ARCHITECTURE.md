# ADR-0004: Multi-Brand Android Application-Module Architecture

- Status: Accepted
- Date: 2026-09-01
- Decision owners: Project owner and multi-brand architecture reassessment
- Related decisions: [ADR-0001](ADR-0001-NATIVE-ANDROID-KOTLIN-COMPOSE.md), [ADR-0002](ADR-0002-NAVIGATION-COMPOSE-2-TYPED-ROUTES.md), [ADR-0003](ADR-0003-ANDROID-SDK-BASELINE.md)

## Context

Gürbakır is the first real implementation and validation brand. Current source is therefore legitimately Gürbakır-first in application identity, composition, merchant content, domain policy and some internal naming. That history is provenance, not the reusable architecture for future brands.

Future brands may differ materially in packaged identity, UI, primary navigation, capabilities, market and locale policy, Customer Account availability, Firebase use and compiled integrations. Repository forks, long-lived per-brand branches, shared `if (brand)` logic, runtime merchant switching and arbitrary remotely supplied native UI or executable behavior would make shared fixes, security boundaries and application ownership harder to maintain.

The current `:app` module also has two responsibilities: it assembles the installed Android application and owns most reusable Compose features, navigation and orchestration. A durable multi-brand boundary must separate those responsibilities without rewriting the working commerce application.

## Decision

Adopt the following target architecture:

```text
one monorepo
one Android application module per brand
shared :mobile-core
existing :app remains the Gürbakır application shell during migration
environment flavors remain inside each real brand application
no brand flavor dimension as the normative brand boundary
no runtime merchant switching
shared code never depends on concrete brand applications
```

The Android application module being built selects the brand. Each brand application owns its distribution identity and brand-specific composition. Shared modules own reusable product, commerce and security behavior and must not depend on `:app` or future `:apps:<brand>` modules.

Gate 0 records this decision only. It does not create `:mobile-core`, another application module or any runtime behavior.

## Why `:mobile-core` is justified

`:mobile-core` will separate reusable application features, common Compose implementation, typed navigation mechanics, ViewModels/controllers, application orchestration and shared local-data behavior from the Android distribution shell. This is a responsibility boundary required by the current `:app` ownership mix, not modularization for aesthetics.

The existing `:app` name intentionally remains the Gürbakır shell during migration so current task names, CI commands and historical evidence do not churn without a concrete benefit.

## Alternative: one application plus brand flavors

A single application module with a `brand` flavor dimension is technically valid and was the earlier forensic recommendation. It was not selected as the normative boundary because application package/signing identity, Firebase registration, OAuth callbacks, App Links, platform resources and provider-specific dependencies align naturally with an Android application module. A global brand × environment × build-type graph would grow with every brand, Kotlin source sets are not arbitrary implementation override/plugin mechanisms, and module dependency direction gives stronger structural isolation.

Product flavors are not considered broken. Environment remains a valid flavor concern within each real brand application.

## Rejected alternatives

- **Separate repository per brand:** shared Android, Shopify, security, Room, Account, Checkout and CI fixes would duplicate and diverge.
- **Long-lived per-brand branches:** ordinary product variation would become permanent merge debt; `main` is the shared canonical development line.
- **Runtime multi-merchant APK:** no product evidence requires it, and it conflicts with installed application identity, signing, Firebase, OAuth, App Links, platform resources and application-data isolation.

## Identity and persistence preservation

Current Gürbakır installed, external and persistence identities are migration-sensitive compatibility state. The Room database filename and schema/migration chain continue. Customer Account and cart SharedPreferences names and Android Keystore aliases remain exact unless a separately designed, verified migration changes them.

Internal `com.gurbakir.*` namespace and package names are not automatically migration targets. Neutral-looking names do not prove correct ownership, and aesthetic renaming is not a reason to risk existing application or persisted state.

Production application identity, signing, Firebase, Customer Account callback, verified App Links and Play ownership remain unresolved P3-16 inputs. They must not be invented by this migration. See the [Gürbakır legacy identity register](../architecture/GURBAKIR-LEGACY-IDENTITIES.md).

## Consequences

### Benefits

- Strong brand isolation and machine-enforceable dependency direction.
- Coherent ownership of application identity, signing, manifests, resources, Firebase, OAuth and App Links.
- A clear owner for future compiled brand-specific behavior and dependencies.
- Shared commerce/security behavior remains reusable without concrete brand branches.

### Costs

- The first migration is larger than adding a flavor.
- `:app` must shed reusable implementation while preserving Gürbakır behavior and identities.
- App-owned `BuildConfig` values consumed by shared code need explicit inputs.
- Repeated application Gradle configuration may later justify convention build logic, but only after real duplication exists.

## Reversibility and deferred decisions

The decision remains reversible before multiple production brands depend on it. Returning to a flavor model is possible if later measured evidence shows the application-module boundary is operationally worse; convenience alone is not sufficient.

Deferred until concrete evidence or release authority exists:

- permanent production Gürbakır package and signing identity;
- the first real second brand and future application-ID convention;
- final Shopify mobile-content schema;
- loyalty, custom-backend and alternate-provider contracts;
- whether an Account-disabled binary should physically exclude `:account`;
- common Android application convention build logic.

## Evidence and canonical guidance

- [Forensic research](../multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md)
- [Accepted normative design evidence](../multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md)
- [Canonical multi-brand architecture](../architecture/MULTI-BRAND-ARCHITECTURE.md)
- [Brand ownership boundaries](../architecture/BRAND-BOUNDARIES.md)
- [Gürbakır legacy identity register](../architecture/GURBAKIR-LEGACY-IDENTITIES.md)
