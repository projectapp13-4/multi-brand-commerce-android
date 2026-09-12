# ADR-0001: Native Android with Kotlin and Jetpack Compose

- Status: Accepted
- Date: 2026-07-19
- Decision owners: Project owner and Phase 1 architecture reassessment
- Supersedes: Initial uncommitted/then-baselined Flutter recommendation preserved in commit `9c0d9af3c5ab2d81b6fbc3dc563b9adef960d6d1`

## Context

Gurbakir is Android-first. A future iOS app may be independently implemented, and multi-brand reuse does not require shared runtime code. The old Flutter prototype and installed tools are sunk-cost inventory, not decision inputs. Setup time is negligible compared with long-term technical quality.

The product depends on Shopify Storefront GraphQL, current Customer Account API OAuth/PKCE, authenticated checkout, official Checkout Kit, Firebase services, deep links, secure session storage, reliable lifecycle handling, accessibility, and independent implementation of approved Gürbakır requirements. The APK contributes 24 inventoried candidate feature groups pending product acceptance; their presence does not make them automatic scope.

The reference APK is a functioning native Android implementation with Compose, Hilt/Dagger, coroutines/Flow, Apollo, DataStore, Shopify Checkout Kit, and Firebase. This is high-value feasibility evidence but not source code or a dependency prescription.

## Decision

Create a new native Android application using Kotlin, Jetpack Compose, Android's recommended layered/UDF architecture, Hilt, coroutines/Flow, typed navigation as separately recorded in ADR-0002, Apollo Kotlin, official Shopify Checkout Kit Android, AppAuth-Android/Custom Tabs with PKCE, Android Keystore-backed token storage, DataStore, Room where relational storage is justified, WorkManager for durable work, and Firebase Android main modules through the BoM.

Use Shopify's current Customer Account API. Do not implement legacy customer email/password accounts solely for reference parity.

Do not share application runtime code with a future iOS app by default. A future Swift + SwiftUI app should share platform-neutral requirements, API operations/contracts, design-token schemas, fixtures, acceptance criteria, and release conventions.

## Consequences

### Positive

- Official first-class Shopify Checkout Kit and Firebase integration on the immediate platform.
- No permanent Flutter Checkout bridge or JavaScript/native runtime boundary.
- Direct Android lifecycle, security, deep-link, accessibility, profiling, testing, and release tooling.
- Highest implementation/debugging predictability for Android and strong correspondence with empirical reference behavior.
- Platform-native evolution is independent; iOS can choose the best current Swift stack later.

### Costs

- Future iOS UI/application work is separately implemented.
- Cross-platform feature parity requires strong neutral contracts, shared acceptance criteria, and disciplined release coordination rather than shared runtime code.
- The team must maintain Android-native expertise and a Gradle/Kotlin dependency baseline.

### Risks and mitigations

- Customer Account mobile OAuth and checkout identity still require owned-store proof: make them early Phase 2 integration gates.
- Static APK evidence may be wrong about live services: preserve `UNKNOWN` and test only approved non-production services.
- Native selection can tempt implementation copying: retain immutable evidence/provenance rules and independent authorship.
- Brand variability can create conditional sprawl: use typed brand/config contracts and build variants/modules with explicit validation.

## Alternatives

- Flutter: rejected because the project would own Checkout Kit bridges and their lifecycle/auth/privacy/deep-link upgrades without a required shared-iOS payoff.
- React Native: rejected because official Checkout support does not offset the added JavaScript/native architecture and debugging surface for an Android-first product.
- Kotlin Multiplatform: deferred; no stable duplicated cross-platform domain code exists yet to justify the coordination cost.

## Reconsideration triggers

Reopen this ADR only with current primary evidence and a material changed requirement, such as mandatory shared runtime delivery, a first-class official Flutter integration that removes the key bridge burden, or measured duplication that makes a narrow KMP layer clearly superior. Existing code, installed tools, setup effort, and schedule convenience alone are not valid triggers.
