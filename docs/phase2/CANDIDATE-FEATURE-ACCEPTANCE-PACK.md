# Candidate Feature Acceptance Pack

Date: 2026-08-06
Status: **SUPERSEDED AS A DECISION SURFACE; FINALIZED BY PHASE 3 PLANNING**

This pack originally converted the complete 24-row Phase 1 inventory into a recommendation surface. Stage 3 has now finalized every row. The authoritative decisions and reasoning are [Phase 3 Product Decisions](../phase3/PHASE-3-PRODUCT-DECISIONS.md); the current human and machine matrices are [PHASE-3-ACCEPTANCE-MATRIX.md](../phase3/PHASE-3-ACCEPTANCE-MATRIX.md) and [phase-3-acceptance-matrix.csv](../phase3/phase-3-acceptance-matrix.csv). They supersede the earlier recommendations wherever they differ.

The original row-by-row recommendation evidence—including reference behavior/evidence, approved-requirement mention, dependencies, privacy/security, brand input, acceptance criteria, failure/offline/lifecycle states, tests, and irreducible unknowns—is preserved in [candidate-feature-acceptance-pack.csv](candidate-feature-acceptance-pack.csv). Its recommendation column is explicitly historical. This Markdown view records the final reconciliation.

## Final reconciliation

| # | Candidate | Final decision | Roadmap location | Decision headline |
| ---: | --- | --- | --- | --- |
| 1 | Bootstrap and splash | `ACCEPTED` | P3-01/P3-14/P3-16 | Deterministic typed startup with safe local defaults. |
| 2 | Onboarding | `INTENTIONALLY_DIFFERENT` | P3-00/P3-01 | No carousel; conditional market/language setup only if P3-00 proves it necessary. |
| 3 | Home | `ACCEPTED` | P3-00/P3-01 | Independently designed merchant-backed Home. |
| 4 | Categories | `ACCEPTED` | P3-00/P3-02 | Shopify-backed taxonomy browse with typed routes. |
| 5 | Product listing | `ACCEPTED` | P3-02/P3-15 | Cursor pagination with supported filter/sort only. |
| 6 | Search and history | `INTENTIONALLY_DIFFERENT` | P3-03/P3-15 | Local, bounded, clearable history under an approved retention default. |
| 7 | Product detail | `ACCEPTED` | P3-04/P3-15 | Explicit valid variant selection and safe media/content. |
| 8 | Wishlist | `INTENTIONALLY_DIFFERENT` | P3-05/P3-15 | Local-only and account-independent; no Worker/backend sync. |
| 9 | Cart | `ACCEPTED` | P3-06/P3-07/P3-09/P3-15 | Complete cart with explicit ownership, buyer identity, reconciliation, and quarantine. |
| 10 | Checkout | `ACCEPTED` | P3-07/P3-15/P3-16 | Preserve the official Checkout Kit adapter and exact lifecycle handling. |
| 11 | Login and session | `INTENTIONALLY_DIFFERENT` | P3-08/P3-09/P3-15 | Current Customer Account OAuth/PKCE and hosted passwordless semantics. |
| 12 | Registration and verification | `INTENTIONALLY_DIFFERENT` | P3-08/P3-09/P3-15 | Shopify-hosted current account journey. |
| 13 | Password recovery/update | `NOT_APPLICABLE` | P3-09 | No legacy password feature; retain sign-in help/restart. |
| 14 | Profile | `ACCEPTED` | P3-10/P3-15 | Approved current fields only; birthdate/metafield requires separate purpose approval. |
| 15 | Addresses | `ACCEPTED` | P3-11/P3-15 | Market-aware customer-scoped CRUD with strict PII handling. |
| 16 | Orders | `ACCEPTED` | P3-12/P3-15 | Private history/detail plus fulfillment/cargo tracking. |
| 17 | Account deletion | `EXTERNALLY_BLOCKED` | P3-13/P3-16 | Functional owned request process/resource and operator required. |
| 18 | Agreements/pages/permissions | `EXTERNALLY_BLOCKED` | P3-00/P3-08/P3-16 | Real owned legal/support corpus is required before Account UI and release. |
| 19 | Web content | `INTENTIONALLY_DIFFERENT` | P3-08/P3-09/P3-12/P3-16 | Allowlisted Custom Tabs/browser; no generic credential WebView. |
| 20 | Push notifications | `DEFERRED` | Beyond Phase 3 | Production purpose, consent, sender/backend, and operations are not approved. |
| 21 | Force update/Remote Config | `INTENTIONALLY_DIFFERENT` | P3-14/P3-16 | Non-blocking safe defaults; hard gate requires governance and rollback. |
| 22 | Session expiry | `ACCEPTED` | P3-06/P3-09/P3-15 | Secure reset plus confirmed cart detachment or quarantine. |
| 23 | Analytics/crash reporting | `DEFERRED` | P3-16 decision/beyond Phase 3 | Analytics absent; Crashlytics absent until a complete privacy/retention decision. |
| 24 | Localization/remote branding | `INTENTIONALLY_DIFFERENT` | P3-00 through P3-16 | TR/EN resources and owned tokens; no legacy or arbitrary remote branding. |

Final counts: 11 `ACCEPTED`, 8 `INTENTIONALLY_DIFFERENT`, 2 `DEFERRED`, 2 `EXTERNALLY_BLOCKED`, and 1 `NOT_APPLICABLE`. Total: 24. There are no unresolved product-decision rows.

## External and release inputs that remain required

1. **Permanent identity:** final production application ID, Play listing/ownership, signing owner, upload/App Signing certificate strategy, and release environments.
2. **Brand/content/legal:** final logo/icon/imagery provenance, design tokens, Turkish copy, supported locales, terms/privacy/support content, and content owners/versioning.
3. **Markets:** countries, currencies, languages, inventory/delivery promises, price display, address validation, and market switching.
4. **P3-00 acceptance:** final IA, screen/layout/component specifications, content inventory, permitted assets, accessibility/localization baseline, and design-review `PASS`.
5. **Wishlist:** Phase 3 is local-only. Cross-device sync is a future independently approved backend capability.
6. **Account deletion:** lawful process, data controller/processor responsibilities, Shopify capability, any owned backend, re-authentication, audit, retention exceptions, and user support.
7. **Notifications:** deferred beyond Phase 3 pending approved use cases, OS opt-in timing, lock-screen content, channels/quiet hours, sender/backend, token association, and deletion.
8. **Telemetry:** Analytics is deferred; Crashlytics stays absent unless P3-16 approves lawful basis/consent, data taxonomy, local-report behavior, retention, deletion/access, environment separation, and accountable ownership.
9. **Backend necessity:** authorize a project backend only for a demonstrated requirement that Shopify/current client APIs cannot satisfy; define owner, authz, secret, privacy, observability, and operations boundaries first.
10. **Remote release control:** whether force update is permitted, supported channels, minimum-version/grace behavior, emergency rollback owner, and critical support/legal access during maintenance.

## Historical recommendation boundary

The earlier `ACCEPT`, `INTENTIONALLY CHANGE`, and `NEEDS PRODUCT DECISION` values are retained only as historical Stage 1 input in the CSV field renamed `phase2_recommendation`. They are not current statuses and require no further owner decision. Any future scope change must update the Phase 3 product decision, both Phase 3 matrices, roadmap dependencies, and affected security/test evidence together.
