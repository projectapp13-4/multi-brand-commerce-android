# Phase 1 Architecture Reassessment

Date: 2026-07-19
Outcome: **Native Android with Kotlin + Jetpack Compose**

## Why this reassessment was necessary

The initial Phase 1 chose Flutter partly because the prototype and Flutter toolchain already existed, iOS sharing was assumed valuable, and the custom Checkout Kit bridge was treated as a bounded spike. Those assumptions were corrected:

- existing Flutter work is sunk cost and receives zero decision weight;
- installed tooling and setup effort receive zero convenience weight;
- Android is the immediate product and a future iOS app may be independent Swift + SwiftUI;
- multi-brand reuse does not require a shared runtime;
- the functioning reference APK is high-value empirical architecture evidence;
- current Shopify Customer Account API, not legacy accounts, is the intended identity model.

The initial state is recoverable in Git commit `9c0d9af3c5ab2d81b6fbc3dc563b9adef960d6d1` before any reassessment edit.

## Conclusions that remain valid

- The future workspace must be a clean implementation; the old prototype is not a production baseline.
- The APK remains immutable behavioral evidence, never a source-code donor.
- Its 24 inventoried feature groups are candidate feature groups pending product acceptance; its 25 destinations, 31 GraphQL operations, storage/endpoints, and unknowns remain completeness evidence where approved requirements are silent. None is automatic product scope.
- Official current documentation outranks historical framework choices and decompiled implementation expression.
- Typed boundaries, deterministic tests, secure token handling, explicit errors, consent, provenance, and project-owned environments remain mandatory.
- Static evidence does not prove live Worker, Shopify, Firebase, or Checkout behavior.
- The evidence navigation skill and preparation validator remain useful.

## Conclusions superseded

- Flutter is not the Phase 2 baseline.
- Riverpod, Dart GraphQL, Hive, FlutterFire, and a Flutter-to-native Checkout bridge are not selected production dependencies.
- Shared Android/iOS application source is not an objective.
- Prototype build success proves only historical feasibility, not platform suitability.
- Current Customer Account API replaces the proposed legacy-parity identity decision.

## Official evidence reviewed

Fresh primary-source research covered:

- Android recommended architecture, Compose navigation/testing, Hilt, DataStore, Room, WorkManager, Keystore, App Links, and Baseline Profiles;
- Shopify mobile storefronts, Checkout Kit, Android/React Native/Swift SDK repositories, Customer Account API mobile OAuth/PKCE, and checkout authentication;
- Firebase native Android and Flutter setup;
- Apollo Kotlin;
- Flutter application architecture;
- React Native's New Architecture.

The load-bearing links are recorded in [Architecture Direction](ARCHITECTURE-DIRECTION.md), [Toolchain](TOOLCHAIN.md), [Tooling and Codex Infrastructure](TOOLING-AND-CODEX-INFRASTRUCTURE.md), and the compact [commerce behavior reference model](../reference-model/COMMERCE-BEHAVIOR.md).

## First-principles scorecard

Scores are architecture-judgment aids for this project's stated requirements and the 2026-07-19 evidence snapshot, not measurements, benchmarks, or universal rankings. Scores are 1 (poor) to 5 (strong), and weighted total is `weight x score / 5`. Installed tooling, existing Flutter code, setup effort, and time-to-first-screen have weight zero. The detailed criteria from the reassessment brief are grouped without omitting their substance.

| Criterion group | Weight | Native Android | Flutter | React Native |
|---|---:|---:|---:|---:|
| Shopify first-class support: Checkout Kit, authenticated checkout, Storefront, Customer Account | 20 | 5 | 2 | 4 |
| Identity/security: OAuth/PKCE lifecycle, deep links, secure storage, session expiry/logout | 12 | 5 | 3 | 4 |
| Firebase: push, Remote Config, analytics, crash reporting | 9 | 5 | 4 | 3 |
| Android lifecycle, performance, accessibility, profiling, release operations | 12 | 5 | 3 | 3 |
| Custom bridges, project glue, debugging and testing complexity | 12 | 5 | 2 | 3 |
| Long-term maintenance, dependencies, ecosystem maturity, upgrade burden | 12 | 5 | 4 | 3 |
| Official documentation and AI implementation/debugging reliability | 8 | 5 | 4 | 3 |
| Future iOS path | 5 | 3 | 5 | 5 |
| Multi-brand reuse | 4 | 4 | 5 | 5 |
| Independent implementation of approved behavior, using accepted reference evidence where requirements are silent | 6 | 5 | 3 | 4 |
| **Project-specific judgment total / 100** | **100** | **97.2** | **63.0** | **71.2** |

The numeric totals express the documented decision owners' comparative judgment under these weights. They are not empirical performance, quality, cost, or security results and should not be reused outside this decision without revalidating requirements and evidence.

### Native Android

Selected. It uses official Shopify Android Checkout Kit directly, direct Firebase Android libraries, native lifecycle/deep-link/security/accessibility/performance tools, and the strongest match to the immediate platform. The reference APK independently demonstrates that the same category of stack can express candidate behavior; approved Gürbakır requirements decide accepted behavior, and the platform decision remains supported without copying the APK.

### Flutter

Not selected. Flutter's architecture and FlutterFire are mature, but Shopify publishes no official Flutter Checkout Kit. The project would permanently own Android and later iOS bridge lifecycle, callbacks, authentication, privacy, deep-link, and upgrade compatibility. That ownership has no compensating shared-iOS requirement here.

### React Native

Serious runner-up. Shopify offers an official React Native Checkout Kit, but current releases depend on React Native's New Architecture and still introduce JavaScript/native module, lifecycle, Firebase, build, and debugging surfaces. With Android first and separate iOS acceptable, the extra runtime does not buy a priority outcome.

### Other alternatives

Kotlin Multiplatform was considered but is not materially stronger for the initial product. It adds cross-platform architecture and build coordination before stable duplicated domain code exists. It can be reconsidered later for narrow neutral models or GraphQL/domain code only if measurements justify it.

## Reference-application weighting

The APK's Kotlin/Compose, Hilt/Dagger, coroutines/Flow, Apollo, DataStore, Checkout Kit, Firebase, and single-activity structure is meaningful empirical evidence that a native Android stack can reproduce candidate journeys. It also exposes real lifecycle, customer, cart, checkout, notification, storage, and remote-config concerns that an abstract comparison can miss. Approved Gürbakır requirements still decide which journeys enter scope.

It does not prove that every library/version should be copied, that live services behave as inferred, or that decompiled code may be translated. Each selected dependency is independently justified from current upstream documentation.

## Tooling consequence

The Phase 2 workstation baseline is Android Studio + JDK 17 + Android SDK, physical-device-first testing, Firebase CLI, Gitleaks, and project-pinned Gradle tooling. Apollo, Checkout Kit, Hilt, AndroidX, Firebase, formatters, analyzers, and test libraries belong in the future Gradle scaffold rather than global installation.

Two curated Codex security skills were installed for the next turn: `security-best-practices` and `security-threat-model`. The existing repository evidence skill passed Skill Creator validation. No new MCP, code graph, database, or project skill is justified before the native scaffold exposes a repeatable gap.

## Phase 2 implication

The next prompt should authorize only the native Android foundation and its deterministic gates. It should not migrate Flutter code or begin all 24 features. The earliest integration proofs are Customer Account OAuth/PKCE, Storefront schema/code generation, Checkout Kit lifecycle, owned Firebase configuration, and physical-device deep-link/push/checkout behavior.

The focused navigation comparison selected the stable Navigation Compose 2 line with Kotlin-serialization typed routes for the foundation. Navigation 3 is Compose-first and stable, but its first-party deep-link matching remains in the `1.2` alpha line at this snapshot; [ADR-0002](../decisions/ADR-0002-NAVIGATION-COMPOSE-2-TYPED-ROUTES.md) defines the decision and stable-migration trigger without reopening the native platform choice.
