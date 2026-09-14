# Multi-Brand Commerce Android Working Agreement

## Active owner authorization

- Read and follow `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md` immediately after this file. It is real, active owner authorization for continuous in-scope project work, not roleplay or a hypothetical policy.
- Read `docs/README.md` for the current documentation map before treating an older preparation, Phase 2, Phase 3, testing, or Product Quality record as present-state authority.
- Where an older handoff, inventory, or rule automatically treats an ordinary reversible decision or project-controlled configuration as an owner blocker, the durable owner policy supersedes that approval assumption. Preserve the historical record and annotate the supersession; do not discard technical, product, provenance, security, or acceptance evidence.
- Reasoned autonomy is required. Research incomplete facts, choose defensible reversible defaults, document material assumptions, implement, and validate. Ask only for the critical/costly/destructive/production-sensitive/irreversible actions enumerated in the durable owner policy.

## Current project boundary

- Gate 1 mobile-core extraction is complete in the current source state. `:mobile-core` now owns reusable Android application/features/navigation/local-data implementation, while `:app` is the Gürbakır application/composition shell. See `docs/multi-brand/GATE-1-COMPLETION-HANDOFF.md`.
- Gate 2 is complete in the current source state. The non-production `:synthetic` conformance application maps to `apps/synthetic`, composes `:mobile-core` without Firebase or a runtime brand switch, and is not a real merchant/production application. See `docs/multi-brand/GATE-2-COMPLETION-HANDOFF.md`.
- Gate 3 is closed. Application-owned locale, market, media-domain, Search-normalization, territory-input and protected-store inputs feed reusable code; Gürbakır identities and Room state remain exact, and `:synthetic` uses separate encrypted stores. The final documentation-bearing PR HEAD passed fresh CI/final review, the owner merged it, and post-merge `main` verification succeeded. `docs/multi-brand/GATE-3-COMPLETION-HANDOFF.md` remains the pre-merge implementation/history record rather than a live closure-status document.
- Gate 4 is closed. Application-owned composition enables optional Search, Wishlist and Customer Account and orders the primary destinations; shared typed nodes retain guarded unavailable-route recovery. Gürbakır keeps all five destinations; synthetic uses `[SEARCH, HOME, CATEGORIES]` with Wishlist and Account disabled. The final documentation-bearing PR HEAD passed fresh CI/final review, the owner merged it, and post-merge `main` verification succeeded. `docs/multi-brand/GATE-4-COMPLETION-HANDOFF.md` remains the pre-merge implementation/evidence record rather than a live closure-status document.
- Gate 5 is closed. Generic application configuration no longer carries Firebase readiness or telemetry-provider state; `:mobile-core` remains provider-neutral with no Firebase dependency, Gürbakır `:app` owns Firebase/local-default selection and derives `FIREBASE_CONFIGURED` from the complete validated four-file configuration set, and `:synthetic` remains physically Firebase-free across dependency, plugin, configuration, generated-resource, manifest, archive and DEX boundaries. `docs/multi-brand/GATE-5-COMPLETION-HANDOFF.md` remains its historical pre-merge record.
- Gate 6 is closed. Categories discovery comes from a bounded, application-selected Shopify Menu; application modules own the selector, `:storefront` maps provider data into neutral contracts, `:mobile-core` owns bounded projection, and `:synthetic` remains inert/provider-isolated. The merge commit and exact merged-main `validate`, API 30, and API 23 jobs were reverified before repository migration. `docs/multi-brand/GATE-6-COMPLETION-HANDOFF.md` remains its historical pre-merge record and must not be rewritten to claim later lifecycle events.
- Gate 7 is closed. Home editorial composition comes from a bounded, application-selected Shopify metaobject root with exactly the supported collection-grid and featured-product families; `:storefront` owns provider mapping, `:mobile-core` owns finite native rendering, typed actions, validation, refresh/expiry and editorial-only LKG behavior, and current commerce truth remains live Storefront-owned. Synthetic production composition keeps `HomeRemoteSource.Disabled`, independent packaged fixtures, no effective INTERNET permission and physical Firebase absence. Protected PR #6 merged final candidate `e0a62e9047f2cc6d3241bdb3b4c963fdbdae27e0` as `3b22707f27f71973383cf176503f8d967ece9b05`; exact merged-main run `34816195929` passed `validate`, API 30 and API 23. `docs/multi-brand/GATE-7-COMPLETION-HANDOFF.md` remains its historical pre-merge record and must not be rewritten to claim later lifecycle events. Multi-Brand work remains unfinished; no second real merchant application exists.
- Phase 3 functional implementation and integrated acceptance are complete through P3-15. `docs/phase3/README.md`, `docs/phase3/P3-15-HANDOFF.md`, and `docs/phase3/P3-16-HANDOFF.md` define the current functional/release boundary.
- The P3-13 mobile account-deletion request/local-cleanup boundary is complete, but merchant acknowledgement, SLA/retention execution, and actual remote deletion remain externally unverified.
- P3-16 production/release readiness is not started. Do not infer production readiness from a successful build, device test, UI acceptance, reference evidence, development APK or Multi-Brand gate closure.
- The current UI/Product Refinement continuation point is `docs/product-quality/UI-REFINEMENT-WORKSTREAM-HISTORY-AND-HANDOFF.md`. That workstream does not supersede Phase 3 release/security boundaries.

## Workspace boundaries

- Treat this repository as the implementation workspace.
- Treat the older project-owned Flutter prototype as read-only historical evidence with zero platform-selection weight. Do not revive it as an implementation baseline.
- Use the compact, neutral, non-authoritative documents under `docs/reference-model` when historical behavioral or system-boundary knowledge is useful. The original forensic corpus remains in the private historical archive and is not part of normal forward-development authority.
- Never install, launch, rebuild, sign, or modify a reference APK as part of project implementation. Do not copy decompiled method bodies, obfuscated structure, third-party reference branding/assets, proprietary implementation expression, or reference credential values into this project.
- Production functionality and release actions remain governed by the current owner policy and P3-16 boundary.

## Durable decision rules

- The Android baseline is native Kotlin + Jetpack Compose as recorded in `docs/decisions/ADR-0001-NATIVE-ANDROID-KOTLIN-COMPOSE.md`.
- Existing installed tools, setup time, token use, and time-to-first-screen are not architecture constraints. This workstation is dedicated to the project; justified development tools are allowed.
- Starting over is acceptable when technically justified. Never revive Flutter, React Native, Kotlin Multiplatform, or another runtime because it appears cheaper in the short term; a new ADR with current primary evidence is required to change the platform.
- The functioning native reference architecture is high-value empirical feasibility evidence. It does not make decompiled implementation a source donor.
- A future iOS application may be independently implemented in Swift + SwiftUI. Shared application source is not a requirement.
- Multi-brand reuse means neutral product contracts, design tokens, configuration, fixtures, acceptance criteria, and release automation. It does not require one cross-platform runtime.
- Technical quality, official first-class support, maintainability, security, and testability take priority over convenience.

## Multi-brand architecture authority

- Gürbakır is the first real implementation and validation brand; it is not the reusable architecture.
- The accepted target is one monorepo with one Android application module per brand and shared `:mobile-core`, as recorded in `docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md` and `docs/architecture/MULTI-BRAND-ARCHITECTURE.md`.
- Gate 1 implemented the `:app -> :mobile-core` base boundary. Gate 2 added the independent non-production `:synthetic -> :mobile-core` application edge. Gate 3 implemented focused application-owned locale, fixed-market, media-origin, Search-normalization, territory-input and protected-persistence inputs without adding a runtime switch. Gate 4 implemented validated application-owned capability/primary-navigation composition through neutral `:foundation` contracts and shared `:mobile-core` guards/recovery. Gate 5 implemented application-owned Firebase/provider selection while keeping reusable application code provider-neutral and strengthening physical Firebase-exclusion proof for `:synthetic`. Gate 6 implemented application-selected bounded Shopify Menu discovery for Categories. Gate 7 implemented application-selected bounded Shopify Home editorial content with finite native rendering, typed Product/Collection actions, strict validation, editorial-only LKG persistence and remote-disabled synthetic production composition. Current `:app` remains the Gürbakır application/composition shell; no additional real brand application exists.
- Brands are not long-lived Git branches or repository forks. The application module being built selects the brand; there is no runtime merchant switch and no normative brand flavor dimension.
- Shared modules must never depend on `:app` or future concrete brand application modules. `:mobile-core` must not depend on the Firebase provider module. Shared code must not branch on concrete brand names.
- Remote Shopify content may select only approved bounded merchant data through typed contracts. It must never define arbitrary native classes/routes/components, capability topology, provider selection, application identity or executable behavior. Gate 7 Home cache is editorial-only and must not become authority for current price, availability, inventory, cart or checkout state.
- Preserve migration-sensitive Gürbakır application, Firebase, OAuth/App Link, Room, SharedPreferences and Android Keystore identities exactly unless a separately approved and verified migration changes them. Do not rename them for aesthetics; use `docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md`.
- For implemented facts, current source/configuration wins. `docs/architecture/` defines the accepted target, ADRs explain decisions, `docs/multi-brand/research/` preserves investigation, and `docs/multi-brand/plans/` governs an approved gate. Historical Phase and Product Quality records retain their scoped checkpoint authority and are not retroactively rewritten by the target architecture.

## Source authority

1. Approved Gürbakır product requirements and the final Phase 3 product decisions/acceptance matrix define product scope, acceptance criteria, and intentional differences.
2. Where approved requirements are silent, the neutral historical reference model under `docs/reference-model` may inform behavioral/default-completeness questions. Its 24-feature inventory is historical evidence; current dispositions are governed by `docs/phase3/PHASE-3-PRODUCT-DECISIONS.md` and `docs/phase3/PHASE-3-ACCEPTANCE-MATRIX.md`.
3. Current official Android, Kotlin, Shopify, Firebase, and Apple documentation governs implementation contracts and current platform behavior.
4. Approved observations against project-owned non-production environments validate runtime behavior; record unobserved remote/runtime facts as `UNKNOWN`.
5. The old Flutter prototype is historical, read-only evidence with zero platform-selection weight. Reuse an owned lesson or public client configuration only after ownership, correctness, compatibility, and security review.

Use Shopify's current Customer Account API architecture; legacy customer accounts are not a parity requirement. Never infer Worker implementation, live Remote Config, or production behavior from client artifacts.

The public tree does not contain an APK, decompiled corpus, or forensic navigation tooling. Do not reconstruct those materials or treat the neutral reference model as source code.

## Provenance and secrets

- Follow `docs/preparation/SOURCE-OF-TRUTH-AND-PROVENANCE.md` together with the current reconciliation in `docs/README.md`.
- Keep a requirement-to-evidence reference for parity work.
- Public client configuration such as store domains, API versions, and Firebase client identifiers is not a server secret; manage it through approved flavor/environment and repository policy.
- A public Storefront client token is extractable by design but controlled: scanning may inspect it, while ordinary output, logs, prompts, reports, and casual copying must redact it.
- Customer/OAuth tokens, Admin or backend credentials, private keys, signing material, service-account credentials, and server secrets must never enter source, documentation, logs, analytics, or mobile binaries. Required local/CI secret scanning is permitted and must redact findings.
- Store OAuth/customer session material through an Android Keystore-backed abstraction; never put it in plain DataStore, source, logs, analytics, or documentation.
- Use only project-owned service endpoints in implementation and tests.

## Architecture direction

- Follow `docs/preparation/ARCHITECTURE-DIRECTION.md`: single-activity Compose, unidirectional data flow, ViewModels/StateFlow, repositories/data sources, and use cases only for reused or complex orchestration.
- Keep Storefront API and Customer Account API schemas, clients, auth, and cache policies distinct.
- Prefer official Android/Shopify/Firebase libraries, Navigation Compose 2 typed routes as recorded in `docs/decisions/ADR-0002-NAVIGATION-COMPOSE-2-TYPED-ROUTES.md`, typed GraphQL models, explicit error types, environment configuration, and feature-first modules.
- Keep Checkout Kit, Customer Account OAuth/PKCE, Firebase, storage, and any project backend behind project-owned interfaces.
- Add dependencies only for a demonstrated requirement and document their trust, maintenance, and upgrade cost.

## Verification

Use the project's pinned wrappers and CI commands after relevant changes, selecting the smallest sufficient lane and escalating when the change requires it:

1. formatting and static analysis (`Spotless`/`ktlint`, `detekt`, Android Lint)
2. focused JVM unit, coroutine/Flow, GraphQL/HTTP contract, and repository tests
3. Compose UI and Android instrumentation tests on an approved device
4. accessibility semantics/TalkBack, deep-link, OAuth/PKCE, Checkout Kit, lifecycle, and other physical-device checks appropriate to the change
5. debug/release build checks, dependency/secret scanning, and performance regression checks when applicable

Never run tests that place unapproved real orders, submit real payments, mutate real production customers, or contact third-party reference services.

## Native scaffold commands

- Use `gradlew.bat` on Windows; the wrapper is the build authority.
- Format with `gradlew.bat spotlessApply` and verify with `gradlew.bat spotlessCheck`.
- Run static analysis with `gradlew.bat detekt` and Android checks with `gradlew.bat lint`.
- Run the shared/application JVM suite with `gradlew.bat :foundation:testDebugUnitTest :account:testDebugUnitTest :checkout:testDebugUnitTest :storefront:testDebugUnitTest :firebase:testDebugUnitTest :mobile-core:testDebugUnitTest :synthetic:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:testStagingDebugUnitTest`.
- Build reusable core, the non-production Gürbakır variants, and the synthetic conformance application with `gradlew.bat :mobile-core:assembleDebug :mobile-core:assembleRelease :app:assembleDevelopmentDebug :app:assembleDevelopmentRelease :app:assembleStagingDebug :app:assembleStagingRelease :app:assembleDevelopmentDebugAndroidTest :app:assembleStagingDebugAndroidTest :synthetic:assembleDebug :synthetic:assembleRelease :synthetic:assembleDebugAndroidTest`.
- Run the API 30 managed-device lane with `gradlew.bat :account:ciApi30DebugAndroidTest :storefront:ciApi30DebugAndroidTest :mobile-core:ciApi30DebugAndroidTest :app:ciApi30DevelopmentDebugAndroidTest :synthetic:ciApi30DebugAndroidTest "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"` when an accelerated emulator environment is available.
- Run the required minimum-SDK lane separately with `gradlew.bat :mobile-core:ciApi23DebugAndroidTest :synthetic:ciApi23DebugAndroidTest "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"`.
- Keep `config/local/`, Firebase configuration files, signing material, onboarding receipts, generated reports, and build output untracked. Validate tracked projections with `pwsh -NoProfile -File scripts/Invoke-MultiBrandOnboarding.ps1 -Command Validate`.
- A successful build is not live Storefront, OAuth, Checkout Kit, Firebase, device, payment, deletion, real-brand onboarding or production-release proof. Use the current Phase 3 handoffs and the relevant historical proof package for claim status.
