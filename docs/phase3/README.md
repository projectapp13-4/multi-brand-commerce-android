# Phase 3 Documentation Index

Current checkpoint purpose: controlled product implementation through P3-00 to P3-16 under the owner-supplied continuous Phase 3 goal.

Active approval policy: [Owner Authority and Approval Boundaries](../OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md). This policy supersedes older assumptions that ordinary reversible content/configuration decisions automatically require a new owner approval, while preserving architecture, clean-room, security, evidence, and roadmap rules.

## Governing product and roadmap authority

- [Phase 3 Product Decisions](PHASE-3-PRODUCT-DECISIONS.md)
- [Phase 3 Acceptance Matrix](PHASE-3-ACCEPTANCE-MATRIX.md) and [machine-readable matrix](phase-3-acceptance-matrix.csv)
- [Phase 3 Implementation Roadmap](PHASE-3-IMPLEMENTATION-ROADMAP.md)
- [Original Phase 3 Planning Handoff](PHASE-3-PLANNING-HANDOFF.md)
- [Original P3-00 Implementation Goal](PHASE-3-IMPLEMENTATION-GOAL.md)

## Completed P3-00 planning gate

- [P3-00 Authoritative Handoff](P3-00-HANDOFF.md)
- [Product Scope and Slice Readiness](P3-00-PRODUCT-SCOPE-AND-SLICE-READINESS.md) and [machine-readable feature readiness](p3-00-feature-readiness.csv)
- [Information Architecture and Screen Inventory](P3-00-INFORMATION-ARCHITECTURE-AND-SCREEN-INVENTORY.md) and [machine-readable screen inventory](p3-00-screen-inventory.csv)
- [Critical User Flows](P3-00-CRITICAL-USER-FLOWS.md)
- [Design System and UX Baseline](P3-00-DESIGN-SYSTEM-AND-UX.md)
- [Content, Asset, Legal, Market, and Merchant Inventory](P3-00-CONTENT-ASSET-MARKET-INVENTORY.md)
- [Data Ownership and Persistence](P3-00-DATA-OWNERSHIP-AND-PERSISTENCE.md)

Run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-Phase3Planning.ps1` from the repository root for the P3-00 documentation consistency gate.

## P3-01 production shell and real Home

- [Approved Merchant Home and Market Input Packet](P3-01-MERCHANT-INPUT-PACKET.md)
- [P3-01 Implementation and Acceptance Handoff](P3-01-HANDOFF.md)

P3-01 implementation, locked dependencies, local quality gates, and a read-only live Storefront probe are complete. The focused physical-device visual/TalkBack and eight new instrumentation test executions remain pending because `adb devices -l` returned no connected device; the handoff does not misclassify that missing evidence as a pass.

## P3-02 Categories and product listing

- [P3-02 Implementation and Acceptance Handoff](P3-02-HANDOFF.md)

P3-02 implementation, typed Categories/collection navigation, schema-supported sort/filter controls, cursor pagination, deep-link handling, local quality gates, APK packaging, and a read-only live Storefront proof are complete. Instrumentation source and APKs pass compilation/assembly, but device execution and focused visual/TalkBack checks remain pending because no device is connected; see the handoff for the exact evidence boundary.

## P3-03 Search and local history

- [P3-03 Implementation and Acceptance Handoff](P3-03-HANDOFF.md)

P3-03 full-text Storefront product search, cancellable debounce, cursor results, and bounded device-local Room history are complete. The version 1 schema, SR-08 lifecycle, dependency locks, static/JVM/live read-only gates, and app/test APKs pass. Physical Room/Compose/TalkBack execution remains pending because no device is connected; the handoff keeps that boundary explicit.

## P3-04 Product detail

- [P3-04 Implementation and Acceptance Handoff](P3-04-HANDOFF.md)

P3-04 generated Storefront product detail, safe media, explicit valid-variant selection, honest price/availability, typed product routes/deep links, static/JVM/live read-only gates, and app/test APKs are complete. Related products remain absent without an approved source, Cart remains absent until P3-06, and physical Compose/deep-link/TalkBack execution remains pending because no device is available.

## P3-05 Local wishlist

- [P3-05 Implementation and Acceptance Handoff](P3-05-HANDOFF.md)

P3-05 durable device-local Wishlist, Room schema version 2 and explicit `1 -> 2` migration, current-product rehydration, unavailable/deleted-item recovery, functional save/remove surfaces, static/JVM gates, and app/test APKs are complete. No backend/account/Worker sync or cross-device promise exists. Physical migration/persistence/Compose/TalkBack execution remains pending because no device is available.

## P3-06 Production cart

- [P3-06 Implementation and Acceptance Handoff](P3-06-HANDOFF.md)

P3-06 generated Storefront Cart reads/mutations, server-driven quantity and money reconciliation, encrypted ownership payload version 2, explicit `Anonymous`/`CustomerAssociated`/`DetachPending`/`Quarantined` handling, functional Product Detail/Home/Cart navigation, live bounded owned-store proof, static/JVM gates, and app/test APKs are complete. Checkout and cart notes remain absent by design. Physical Android Keystore/Compose/TalkBack execution remains pending because no device is available.

## P3-07 Production Checkout Kit handoff

- [P3-07 Implementation, Physical Acceptance, and Blocked Next-Slice Handoff](P3-07-HANDOFF.md)

P3-07 fresh-cart checkout eligibility, official Checkout Kit presentation, session-scoped callbacks, cancel/failure retention, exact-cart completion, process-recreation safety, production proof-surface exclusion, local quality gates, and physical-device acceptance are complete. The final installed APK pair passed 44/44 tests in six isolated physical runner groups, and the manual production route passed launch, cancel/return, cold-relaunch retention, and remote synthetic-line cleanup without a payment or order. The single-process monolithic device run is not claimed as pass because Samsung window-focus/touch injection was variable. P3-08's former final-content blocker was superseded by the active owner policy: verified merchant/Shopify policies and conservative traceable provisional content may now be used. P3-08 is authorized to proceed; P3-09 still waits for its exit criteria.

## P3-08 owned legal/support web baseline

- [P3-08 Implementation and Physical Acceptance Handoff](P3-08-HANDOFF.md)

P3-08 is complete at implementation checkpoint `33f6f9c`. Six verified merchant-owned support/policy routes are packaged as `gurbakir-legal-baseline-1`, exposed from Home and route recovery, and opened only through an exact HTTPS host/path allowlist in AndroidX Custom Tabs. The local TR/EN index, external-context/error copy, return announcement, and deterministic focus restoration are device-tested. The final local gate passed 63/63 JVM tests, lint, detekt, formatting, app/test APK assembly, Gitleaks, and diff checks; the focused physical-device group passed 17/17. Shopify Admin and public content inspection were read-only and no external service state changed. P3-09 is authorized to begin from this checkpoint.

## P3-09 account access, hosted journey, and session

- [P3-09 Implementation and Physical Acceptance Handoff](P3-09-HANDOFF.md)

P3-09 is complete at final implementation checkpoint `29ece10`. The production Account destination uses the existing hosted passwordless Customer Account OAuth foundation, restores/refreshes/logs out securely, exposes only minimum in-memory identity, and reconciles customer cart ownership without erasing local Wishlist/Search data. No password, code, registration, proof UI, customer PII cache, dependency, schema, migration, permission, or external configuration change was added. Relevant JVM evidence is 150 pass plus two pre-existing opt-in live-proof skips; the final physical group passed 18/18 and a manual hosted launch/cancel/return passed without submitting a credential or creating a remote record. P3-10 is authorized to begin from the explicit profile-field/schema boundary in the handoff.

## P3-10 profile

- [P3-10 Implementation and Physical Acceptance Handoff](P3-10-HANDOFF.md)

P3-10 is complete at implementation checkpoint `f0d01a5`. Authenticated customers can view and edit only current Customer Account `firstName` and `lastName` fields through a typed, memory-only Profile flow with confirmed saves, stale-baseline conflict prevention, ambiguous-save reload, terminal-session cleanup, TR/EN accessibility behavior, and no birthdate/metafield or inferred PII. The final app JVM gate passed 85/85, the focused physical group passed 15/15, and secret/diff/static/build checks pass. No live profile mutation or external configuration change occurred, so remote profile mutation remains explicitly externally unverified. P3-11 is authorized to begin from the address-schema and Turkey/TRY market boundary recorded in the handoff.

## P3-11 address management

- [P3-11 Address Management Implementation Handoff](P3-11-HANDOFF.md)

P3-11 is complete. After the owner lifted the pause, its deferred physical proof was executed on a Samsung SM-A225F (Android 13): six scenarios passed in the final full-class run and the corrected create-form scenario passed in an isolated matching-APK run. Device evidence also exposed and fixed an accessibility defect that had left failure/reload feedback outside the composed lazy viewport. No live address mutation or external service change occurred.

## P3-12 orders and tracking

- [P3-12 Orders and Tracking Implementation and Physical Acceptance Handoff](P3-12-HANDOFF.md)

P3-12 is complete. Authenticated customers can use typed, private, memory-only order history and detail routes with bounded pagination, official Customer Account statuses, partial fulfillments, localized totals/dates/addresses, and a fail-closed official-carrier Custom Tabs handoff with owned-support recovery. Order screens use `FLAG_SECURE`, terminal-session and lifecycle cleanup, redacted routes/models, and no local order cache. Account/App JVM, Lint, Detekt, formatting, APK assembly, secret/diff checks and the focused physical P3-12 group pass; the Samsung SM-A225F executed 16/16 P3-12 order/navigation/account tests. No live customer order, carrier, Shopify, or Firebase mutation occurred. P3-13 must begin only from its functional owned deletion-process entry gate.

## P3-13 account deletion request boundary

- [P3-13 Account Deletion Request Boundary and Physical Acceptance Handoff](P3-13-HANDOFF.md)

The independently implementable P3-13 mobile boundary is complete at implementation checkpoint `0afc32a`. A revalidated signed-in customer can read the owned privacy policy, reach the exact Gür Bakır contact form, and separately confirm per-class local cleanup with honest session/search/wishlist/cart outcomes. The app never prefills/submits the form or claims remote deletion. App JVM, Lint, Detekt, formatting, APK assembly, secret/diff checks and the focused Samsung physical group pass; the final device run executed 16/16 P3-13/account/navigation tests. Feature group 17 and the roadmap exit remain `EXTERNALLY_BLOCKED` because real request acknowledgement, SLA, retention execution, and remote deletion were not performed or observed. P3-14 may proceed independently while P3-15 preserves this release-critical external disposition.

## P3-14 safe Remote Config and optional update policy

- [P3-14 Safe Remote Config and Optional Update Policy Handoff](P3-14-HANDOFF.md)

P3-14 is complete at implementation checkpoint `9165bcc`. Production startup now resolves safe defaults or an unexpired 24-hour typed cache before a bounded asynchronous development/staging Remote Config refresh; unavailable, malformed, stale, clock-skewed, or incompatible input cannot block the app. Only six packaged schema/boolean/version values are read, all displayed copy and routes remain local, optional updates compare against `BuildConfig.VERSION_CODE`, and maintenance/update notices expose retry, help and defer/dismiss without a force-update gate. Firebase/App JVM (5/5 and 130/130), formatting, Detekt, Lint, debug/test/release assembly, secret/diff checks, live safe-default device fetch/cold start, and the final Samsung 17/17 policy/help/navigation group pass. Firebase/Shopify configuration and remote records were not mutated. P3-15 integrated product acceptance is the next slice; P3-16 still owns production identity, signing/Play governance, privacy/release policy and any separately approved hard gate.

## P3-15 integrated product acceptance and hardening

- [P3-15 Integrated Product Acceptance and Hardening Handoff](P3-15-HANDOFF.md)

P3-15 is complete at implementation checkpoint `f338ded`. The production entry/deep-link lifecycle was corrected, all proof UI/controllers/tests and Firebase Messaging were isolated to debug source/dependency graphs, pseudolocale coverage was added, and the minified release artifact was inspected for proof/synthetic/deferred SDK exclusion. The complete six-module JVM group recorded 244 tests with zero failures/errors and two existing conditional live-proof skips; the matching physical-device suite passed 83/83. Real Android collection/product/order deep links, critical TalkBack/keyboard navigation, Room `1 -> 2` migration, TR/EN/en-XA/ar-XB localization, and measured cold/warm route budgets pass. P3-13 remote deletion remains externally blocked and P3-16 still owns production identity, signing, Play/App Links, Data Safety/privacy, service, support, rollback, and release governance.

## P3-16 production/release readiness entry

- [P3-16 Production and Release Readiness Entry Handoff](P3-16-HANDOFF.md)

P3-16 is **not started** because its mandatory production inputs are not provisioned and the active owner policy keeps the separate release/security/publication campaign outside the functional application run. The entry audit confirms that the current release is the unsigned `com.gurbakir.mobile.dev` development artifact, the public App Links association is empty, no Play package exists at the proposed identity, and no production flavor/Firebase/signing/Data Safety/release-owner configuration is present. Feature group 17 also remains externally blocked. The handoff records the exact owner/provisioning inputs and safe ordered resume procedure without inventing release identity or exposing credentials.
