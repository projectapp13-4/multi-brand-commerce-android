# ADR-0002: Navigation Compose 2 with typed routes

- Status: Accepted
- Date: 2026-07-19
- Scope: Android navigation library only; ADR-0001 remains unchanged
- Research snapshot: Navigation Compose `2.9.8`, Navigation 3 stable `1.1.4`, Navigation 3 development `1.2.0-alpha06`

## Context

The application is Compose-first and depends early on Customer Account OAuth callbacks, verified App Links, offsite checkout returns, route restoration, and deterministic deep-link tests. Both Navigation Compose 2 and Navigation 3 can support a native Compose application, so this decision compares current implementation risk rather than reopening the platform choice.

Navigation 3 is stable, Compose-first, gives the application direct back-stack ownership, and has a strong adaptive-layout model. However, its first-party `DeepLinkRequest` and `DeepLinkMatcher` APIs are currently in the Navigation 3 `1.2` alpha line, not the stable `1.1.4` line. Adopting that alpha solely for a foundational OAuth/App Link contract would weaken the stable release baseline.

Navigation Compose `2.9.8` is the current stable Navigation 2 line. It has Kotlin-serialization type-safe routes, established `NavHost`/`NavController` behavior, deep-link handling, testing support, state restoration, and mature Android integration. Its maintenance-mode status is accepted as a deliberate near-term tradeoff.

Primary evidence: [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3), [Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3), [Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation), and [Navigation Compose guidance](https://developer.android.com/develop/ui/compose/navigation).

## Decision

Start Phase 2 on the current stable Navigation Compose 2 line with Kotlin-serialization typed route objects/data classes. Keep route models project-owned, pass identifiers rather than complex domain objects, allowlist external inputs, and test OAuth callbacks, App Links, checkout returns, process restoration, and back-stack transitions.

Pin the exact stable version through the future version catalog after compatibility verification at scaffold time. `2.9.8` is evidence for this decision, not a permanent version pin.

## Consequences

- The foundation uses stable, mature deep-link and test contracts for the app's highest-risk navigation paths.
- Navigation 2 maintenance mode creates a future migration cost, contained by project-owned typed route contracts and navigation tests.
- Navigation 3 adaptive scenes and direct back-stack ownership are deferred, not rejected.
- No alpha Navigation 3 dependency is introduced merely to obtain deep-link matching.

## Reassessment trigger

Reassess Navigation 3 after its deep-link matching APIs reach a stable release and the scaffold's OAuth/App Link/checkout-return contract tests exist. Migrate only if those tests pass and Navigation 3 provides a material adaptive-layout, back-stack, or maintenance benefit. This trigger does not reopen Kotlin, Compose, or the native Android platform decision.
