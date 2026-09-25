# Phase 3 Acceptance Matrix

> **Current authorization note (2026-08-11):** group 18's historical `EXTERNALLY_BLOCKED` classification below recorded the absence of approved legal/support sources at P3-00. `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md` subsequently approved verified merchant/Shopify content and conservative traceable baselines. P3-08 adopted the existing owned corpus as `gurbakir-legal-baseline-1`; its implementation handoff is the current acceptance evidence. The historical row remains unchanged for provenance and does not keep P3-09 blocked.

> **Integrated evidence note (2026-08-13):** [P3-15 Integrated Product Acceptance and Hardening Handoff](P3-15-HANDOFF.md) is the current row-by-row implementation, device, migration, accessibility, localization, performance, proof-removal, and explicit-blocker ledger. It preserves the final status distribution below; it does not convert P3-13 remote deletion or P3-16 release provisioning into a local PASS.

> **Current group 17 reconciliation (2026-09-26):** The `EXTERNALLY_BLOCKED` row below is the P3-00 product-scope classification and remains unchanged for provenance. Later [account-deletion process evidence](PRE-P3-16-ACCOUNT-DELETION-ROUTE-AND-PROCESS.md) verifies the dedicated Android/public request route, merchant intake and an accepted Shopify personal-data-erasure handoff on a no-order synthetic customer. Final redaction of that case is unobserved, but Shopify's scheduled processing date is not a project wait gate. The current release condition is accountable merchant operations: restricted cases, processor/retention review, response/notice rules and support ownership. P-18 remains partial for those governance inputs; the Android capability and Shopify handoff are no longer unverified.

Date: 2026-08-06

Status: **FINAL PRODUCT-SCOPE MATRIX**

Machine-readable companion: [phase-3-acceptance-matrix.csv](phase-3-acceptance-matrix.csv)

This matrix is the concise requirement-to-roadmap index. The full evidence challenge and boundaries are in [PHASE-3-PRODUCT-DECISIONS.md](PHASE-3-PRODUCT-DECISIONS.md); implementation details and gates are in [PHASE-3-IMPLEMENTATION-ROADMAP.md](PHASE-3-IMPLEMENTATION-ROADMAP.md).

| ID | Feature | Final status | Accepted behavior / difference | Required subitems | Roadmap slices | External or release condition |
|---:|---|---|---|---|---|---|
| 01 | Bootstrap and splash | `ACCEPTED` | Deterministic typed startup with local safe defaults; no remote-bricked launch | SR-07 route matrix | P3-01, P3-14, P3-16 | Final production identity/splash before release |
| 02 | Onboarding | `INTENTIONALLY_DIFFERENT` | No marketing carousel; only P3-00-approved conditional market/language setup | Market/language inference rule | P3-00, P3-01 | Missing required choice must be proven before adding setup |
| 03 | Home | `ACCEPTED` | Independently designed merchant-backed Home | P3-00 layout/content readiness | P3-00, P3-01 | Merchant content and owned/neutral assets |
| 04 | Categories | `ACCEPTED` | Shopify-backed category/collection browse with typed routes | Taxonomy/market mapping | P3-00, P3-02 | Merchant taxonomy and market visibility |
| 05 | Product listing | `ACCEPTED` | Cursor pagination and only supported filter/sort controls | Price/inventory/cache policy | P3-02, P3-15 | Current schema and merchant rules |
| 06 | Search and history | `INTENTIONALLY_DIFFERENT` | Debounced search; local bounded clearable history | SR-08; provisional 10 unique/30 days | P3-03, P3-15 | P3-00 privacy/content approval of default |
| 07 | Product detail | `ACCEPTED` | Explicit valid variant selection, safe media/content, cart-ready product | Media/related source and inventory states | P3-04, P3-15 | Current product schema/content rules |
| 08 | Wishlist | `INTENTIONALLY_DIFFERENT` | Local-only, account-independent; future sync extension point only | SR-08; Room migration | P3-05, P3-15 | Do not promise cross-device sync |
| 09 | Cart | `ACCEPTED` | Complete cart with Anonymous/CustomerAssociated/DetachPending/Quarantined ownership | SR-01, SR-02; migration and market rules | P3-06, P3-07, P3-09, P3-15 | Buyer identity and note policy approved |
| 10 | Checkout | `ACCEPTED` | Official Checkout Kit behind existing adapter; exact completion/cancel/failure handling | Cart refresh and checkout URL policy | P3-07, P3-15, P3-16 | Production Shopify/payment/release configuration |
| 11 | Login and session | `INTENTIONALLY_DIFFERENT` | Current Customer Account OAuth/PKCE and hosted passwordless journey | SR-05, SR-07; field/scope allowlist | P3-08, P3-09, P3-15 | Legal/support baseline before UI; production callback later |
| 12 | Registration and verification | `INTENTIONALLY_DIFFERENT` | Shopify-hosted journey; app handles return, cancel, help, and recovery | SR-05, SR-07 | P3-08, P3-09, P3-15 | Owned-environment content observation when required |
| 13 | Password recovery/update | `NOT_APPLICABLE` | No legacy password feature; sign-in help/restart remains under account access | SR-05 | P3-09 | Revisit only if official account model changes |
| 14 | Profile | `ACCEPTED` | Approved current fields only; no purpose-less birthdate/metafield | SR-03 | P3-10, P3-15 | Purpose/privacy/schema approval for any added field |
| 15 | Addresses | `ACCEPTED` | Customer-scoped CRUD with market-aware validation and strict PII handling | Address policy and clear/delete behavior | P3-11, P3-15 | Supported country/address inputs |
| 16 | Orders | `ACCEPTED` | Private history/detail plus fulfillment/cargo tracking | SR-04 | P3-12, P3-15 | Supported status vocabulary/carrier URL policy |
| 17 | Account deletion | `EXTERNALLY_BLOCKED` | Functional owned request path only; no false local-only deletion claim | SR-08; re-auth, retention, support | P3-13, P3-16 | Owned process/resource/operator and legal policy |
| 18 | Agreements, pages, permissions | `EXTERNALLY_BLOCKED` | Real owned legal/support pages and contextual permissions | SR-06 | P3-00, P3-08, P3-16 | Legal/support corpus, owner, canonical routes |
| 19 | Web content | `INTENTIONALLY_DIFFERENT` | Allowlisted Custom Tabs/browser; no generic credential WebView | SR-06, SR-07 | P3-08, P3-09, P3-12, P3-16 | Canonical owned URLs and offline policy |
| 20 | Push notifications | `DEFERRED` | No production notification capability in Phase 3; preserve disabled/consent boundary | SR-07 | Beyond Phase 3; P3-16 verifies absence | Approved purpose/sender/backend/consent/operations |
| 21 | Force update and Remote Config | `INTENTIONALLY_DIFFERENT` | Non-blocking safe defaults; governed hard gate only if later approved | Grace, rollback, critical-route access | P3-14, P3-16 | Release owner and enforcement policy |
| 22 | Session expiry | `ACCEPTED` | Central terminal reset without exposing customer-associated cart as anonymous | SR-01, SR-07 | P3-06, P3-09, P3-15 | Detachment/quarantine behavior must pass |
| 23 | Analytics and crash reporting | `DEFERRED` | Analytics absent; Crashlytics absent until explicit P3-16 decision | SR-08 | P3-16; otherwise beyond Phase 3 | Consent, taxonomy, retention, deletion, owner |
| 24 | Localization and remote branding | `INTENTIONALLY_DIFFERENT` | TR/EN resources and approved local tokens; bounded owned remote content only | Pseudolocale, content/asset provenance | P3-00 through P3-16 | Brand kit, supported markets/locales, owned assets |

## Counts

- `ACCEPTED`: 11
- `INTENTIONALLY_DIFFERENT`: 8
- `DEFERRED`: 2
- `EXTERNALLY_BLOCKED`: 2
- `NOT_APPLICABLE`: 1
- Total: **24**

No `NEEDS PRODUCT DECISION`, `ACCEPT`, `INTENTIONALLY CHANGE`, `REJECT`, or other obsolete decision state is authoritative after this checkpoint.
