# Phase 3 Product Decisions

> **Current authorization note (2026-08-11):** the active owner policy supersedes group 18's historical entry-gate assumption below. Verified merchant/Shopify policies and support content are approved as the current baseline, including a conservative traceable provisional baseline where necessary. P3-08 therefore may and did close independently; final public-release work remains outside this functional run and the original row is retained as historical decision evidence.

Date: 2026-08-06

Status: **FINAL PLANNING AUTHORITY — IMPLEMENTATION REQUIRES SLICE-SPECIFIC AUTHORIZATION**

This document preserves the complete Stage 1 decision pass and its later critical reassessment. The reassessment supersedes the earlier draft wherever they differ. It finalizes product scope and planning boundaries; it does not claim that any Phase 3 feature is implemented or production-ready.

The row-counted companions are [PHASE-3-ACCEPTANCE-MATRIX.md](PHASE-3-ACCEPTANCE-MATRIX.md) and [phase-3-acceptance-matrix.csv](phase-3-acceptance-matrix.csv). The implementation order is governed by [PHASE-3-IMPLEMENTATION-ROADMAP.md](PHASE-3-IMPLEMENTATION-ROADMAP.md).

## Authority and clean-room boundary

Apply this order:

1. approved Gürbakır product requirements and the final decisions below;
2. current official Android, Shopify, and Firebase contracts;
3. validated Phase 2 project-owned infrastructure and non-production proof;
4. prepared reference-application evidence where requirements are silent;
5. conservative, reversible engineering judgment.

For every slice, the implementation record must distinguish:

- **validated reference behavior:** client-visible behavior supported by the prepared reports;
- **accepted behavioral parity:** behavior deliberately retained because it serves Gürbakır;
- **intentional Gürbakır difference:** a documented product or safety difference;
- **independently designed implementation:** project-owned native design using current official contracts;
- **unavailable or unverified server behavior:** behavior that remains `UNKNOWN` and must not be imitated.

The reference application proves useful behavior and native architectural feasibility. It is not a source-code, UI, brand, credential, server-policy, or remote-configuration donor. Decompiled method bodies, obfuscated structure, third-party reference/legacy prototype brand assets and copy, proprietary implementation details, controlled token values, and inferred Worker behavior are prohibited inputs.

## Reassessment result

### Material status changes

1. **02 Onboarding:** `NOT_APPLICABLE` became `INTENTIONALLY_DIFFERENT`. A marketing carousel remains excluded. P3-00 may approve a minimal, conditional first-run setup only when a required market or language choice cannot be inferred safely. This is reversible, local, and must not bundle marketing consent.
2. **13 Password recovery/update:** `INTENTIONALLY_DIFFERENT` became `NOT_APPLICABLE`. The current Customer Account hosted journey is passwordless; Gürbakır will not manufacture a legacy password-management feature. Sign-in help and restart/recovery from an interrupted hosted journey remain part of account access.

### Confirmed or refined challenge outcomes

- **Local-only wishlist survives challenge.** It avoids an unowned Worker/backend and cross-device privacy contract. Its repository keeps a future sync extension point, but Phase 3 owns no sync service. It is moved before cart.
- **Push remains deferred.** Phase 2 proves a consented delivery boundary, not a production notification purpose, sender, campaign policy, token association service, or operations owner.
- **Analytics remains deferred; Crashlytics remains absent.** A disabled SDK is not operationally neutral because crash material can be retained locally. P3-16 must make an explicit privacy, retention, consent, and release decision before adding either.
- **Hosted account journeys remain intentional.** Current Customer Account OAuth/PKCE and hosted passwordless semantics replace legacy login, registration, verification, and recovery parity.
- **Account deletion remains externally blocked and release-critical.** It requires an owned request mechanism, re-authentication policy, retention/support process, and accountable operator; no Admin or backend secret may enter the app.
- **Legal/pages remain externally blocked and move before production Account UI.** Real privacy, terms, support, provenance, version, cache/offline, and URL policy are prerequisites, not placeholders.
- **Remote Config stays non-blocking by default.** A hard minimum-version gate is possible only after release governance, grace/rollback behavior, and continued access to critical legal/support routes exist.
- **Anonymous cart preservation is narrowed.** Logout or terminal session expiry may preserve only a truly anonymous cart. A customer-associated cart must detach buyer identity successfully or become quarantined; it must never be silently reclassified as anonymous.
- **Profile/address work moves after legal/support readiness.** Profile accepts only approved current fields. Birthdate/metafield use needs a separate purpose, privacy, schema, and content approval.
- **Orders explicitly include cargo/fulfillment tracking.** Display is limited to verified Customer Account data or allowlisted carrier URLs; reference Worker behavior is not assumed.

## Eight newly identified sub-requirements

| ID | Sub-requirement | Owning feature groups | Planning consequence |
|---|---|---|---|
| SR-01 | Guest-to-authenticated cart reconciliation | 09, 22 | Define ownership transition, buyer-identity association/detachment, quarantine, retry, and no-duplicate rules before production cart. |
| SR-02 | Cart note policy | 09 | Decide whether notes exist, allowed length/content, privacy treatment, and clearing behavior before exposing UI. |
| SR-03 | Profile birthdate/metafield purpose | 14 | Do not request or persist the field until purpose, lawful/privacy basis, schema, edit semantics, and deletion behavior are approved. |
| SR-04 | Cargo/fulfillment tracking | 16 | Define supported statuses, partial fulfillment, carrier URL allowlist, unavailable tracking, and refresh behavior. |
| SR-05 | Hosted passwordless account semantics | 11, 12, 13 | Content and recovery states must describe hosted passwordless access; no app-owned password promise or screen. |
| SR-06 | Legal/mobile-page provenance, version, and offline behavior | 18, 19 | Record owner, canonical URL, version/effective date, cache policy, offline failure, and critical-route availability. |
| SR-07 | Notification, deep-link, and callback route matrix | 01, 19, 20, 22 | Maintain an allowlisted route/source/auth/session matrix with safe fallback and replay/unknown-route handling. |
| SR-08 | Local-data clear/delete matrix | 06, 08, 17, 23 | Define logout, account deletion, user clear, uninstall, expiry, and environment-switch treatment for each local data class. |

## Complete 24-feature critical decision record

The table records evidence-based conclusions rather than hidden reasoning. “Reference” means prepared static reports and indexes; server/runtime statements remain unknown unless Phase 2 proved them against the project-owned non-production environment.

| # | Feature | Proposed status entering reassessment | Validated reference behavior | Gürbakır requirement and Phase 2 capability | Strongest support / strongest objection | Consequence if wrong; dependencies | Reversibility; release/security/external input | Final status | Confidence / disposition |
|---:|---|---|---|---|---|---|---|---|---|
| 01 | Bootstrap and splash | ACCEPTED | Splash, configuration, startup routing | Deterministic typed startup; Phase 2 typed config/navigation and safe defaults exist | Required coherent entry; objection: remote/session checks can delay or brick startup | Wrong routing blocks every flow; depends on 07, 11, 21, 22 and SR-07 | Reversible routing policy; release-critical; no secrets; needs approved splash identity | **ACCEPTED** | High; refined |
| 02 | Onboarding | NOT_APPLICABLE | Persisted onboarding flow | No marketing carousel; conditional market/language setup only if P3-00 proves inference insufficient | Avoids needless friction; objection: a required market/language choice may be ambiguous | Wrong omission can misprice/localize; depends on 24 and P3-00 | Reversible versioned local state; consent must remain separate; needs actual market/language rules | **INTENTIONALLY_DIFFERENT** | High; **materially changed** |
| 03 | Home | ACCEPTED | Dedicated home query and merchandising sections | A real commerce entry surface; Phase 2 typed Storefront and brand contracts exist | Necessary discovery value; objection: layout/content are not ready | A guessed Home causes rework or false claims; depends on P3-00, catalog/content | Reversible UI, but high design cost; merchant content and owned assets required | **ACCEPTED** | High; refined |
| 04 | Categories | ACCEPTED | Category/collection navigation | Shopify-backed taxonomy browse with typed routes | Expected discovery parity; objection: Shopify collections may not equal merchant IA | Wrong taxonomy fragments navigation; depends on P3-00 and merchant taxonomy/market rules | Reversible mapping; no PII; merchant hierarchy/naming required | **ACCEPTED** | High; confirmed |
| 05 | Product listing | ACCEPTED | Lists, filters, sorting | Cursor-paginated listing with only supported filters/sorts | Core browse capability; objection: filter availability is schema/merchant dependent | Wrong contracts cause dead controls and duplicates; depends on 04, market/schema/cache | Reversible controls; price/inventory correctness is release-critical; merchant policy required | **ACCEPTED** | High; refined |
| 06 | Search and history | INTENTIONALLY_DIFFERENT | Search plus persisted history | Debounced search; local, bounded, clearable history | Useful and backend-free; objection: queries are sensitive and retention lacks owner policy | Wrong retention creates privacy debt; depends on catalog, Room policy and SR-08 | Reversible default: last 10 unique/30 days, subject to P3-00 approval; never analytics by default | **INTENTIONALLY_DIFFERENT** | Medium-high; refined |
| 07 | Product detail | ACCEPTED | Options, variants, media, related products | Explicit valid variant selection and safe merchant content | Required shoppable flow; objection: related/source/media policy is unresolved | Wrong variant can create wrong purchase; depends on 05, 09, current schema/media/content | UI reversible, variant correctness release-critical; no invented reviews/claims/assets | **ACCEPTED** | High; refined |
| 08 | Wishlist | INTENTIONALLY_DIFFERENT | Local identity plus apparent remote sync route | Phase 3 local-only, account-independent wishlist with future repository extension point | Valuable offline capability without backend; objection: users may expect cross-device sync | Wrong expectation loses data/trust; depends on Room/migration and SR-08, not account | Highly reversible toward later sync; preference data; content must not promise synchronization | **INTENTIONALLY_DIFFERENT** | High; confirmed and reordered |
| 09 | Cart | ACCEPTED | Full lines, note, buyer identity operations | Complete typed cart with explicit ownership states and safe recovery | Required commerce path; objection: identity transitions and note policy add hidden complexity | Wrong ownership leaks identity/pricing or loses cart; depends on 07, 11, 22, SR-01/02, market and migrations | State model change is costly after release; privacy/release-critical; merchant note policy required | **ACCEPTED** | High; materially refined |
| 10 | Checkout | ACCEPTED | Checkout Kit lifecycle, callbacks, preload | Preserve proven official Checkout Kit adapter; production UX only after cart | Phase 2 physically proves feasibility; objection: payment/offsite behavior is not exhaustive | Wrong completion handling can lose cart or misstate order; depends on 09 and release config | Adapter boundary reversible; payment/privacy critical; production configuration required later | **ACCEPTED** | High; confirmed |
| 11 | Login and session | INTENTIONALLY_DIFFERENT | Legacy customer journey plus authenticated capability/expiry | Current Customer Account Mobile OAuth/PKCE, hosted passwordless semantics, Keystore session | Secure current official route; objection: hosted experience offers less app control | Wrong copy/state breaks access; depends on 18/19, market, scopes, 22 and SR-05 | Architecture deliberate; security-critical; real legal/support routes and approved fields required | **INTENTIONALLY_DIFFERENT** | High; refined |
| 12 | Registration and verification | INTENTIONALLY_DIFFERENT | Account creation/verification journey | Shopify-hosted current account journey; app handles return/cancel/help only | Avoids legacy credential ownership; objection: hosted variants must be content-tested | Wrong parity promise confuses users; depends on 11, 18/19, SR-05/07 | Reversible content/routing; auth/privacy critical; hosted behavior must be observed in owned environment when needed | **INTENTIONALLY_DIFFERENT** | High; confirmed |
| 13 | Password recovery/update | INTENTIONALLY_DIFFERENT | Recovery/update journeys | No app password feature; retain sign-in help/restart for hosted passwordless flow | Matches current Customer Account semantics; objection: users may use “password reset” language | Wrong inclusion creates a dead/insecure legacy contract; depends on 11/12 and SR-05 | Easy to revisit if Shopify model changes; avoid credential collection; support copy required | **NOT_APPLICABLE** | High; **materially changed** |
| 14 | Profile | ACCEPTED | Customer profile and metafield | Approved current fields only through Customer Account API | Useful account capability; objection: reference birthdate/metafield has no approved purpose | Wrong collection creates unnecessary sensitive data; depends on 11, 18, scopes/schema and SR-03 | Field additions reversible only with migration/deletion plan; legal/privacy input required | **ACCEPTED** | High; refined |
| 15 | Addresses | ACCEPTED | Address CRUD/default selection | Typed customer-scoped CRUD with market-aware validation | Necessary fulfillment/account value; objection: country-specific rules and permissions vary | Wrong validation blocks checkout or corrupts PII; depends on 11, 18, market/address policy | Schema/UI reversible with care; high PII impact; supported countries/rules required | **ACCEPTED** | High; refined |
| 16 | Orders | ACCEPTED | Order list/detail | Private paginated history plus fulfillment/cargo tracking subitem | Expected post-purchase value; objection: carrier/partial fulfillment data varies | Wrong status/link misleads customers; depends on 11, 18/19, schema and SR-04 | Display policy reversible; high privacy; support/status vocabulary and URL allowlist required | **ACCEPTED** | High; refined |
| 17 | Account deletion | EXTERNALLY_BLOCKED | Dedicated backend route | User-visible request path only after owned process/resource, re-auth and retention rules | Release/privacy obligation; objection: mobile cannot safely invent server deletion semantics | Wrong implementation can falsely promise deletion; depends on 11, 18/19 and SR-08 | Not safely reversible after commitments; release-critical; owner/operator/legal/backend input required | **EXTERNALLY_BLOCKED** | High on block; server behavior unknown; confirmed |
| 18 | Agreements, pages, permissions | EXTERNALLY_BLOCKED | Mobile pages, agreements, contextual permission prompts | Owned terms/privacy/support corpus and contextual least privilege | Required before production Account; objection: content is external to engineering | Placeholder content creates legal/support failure; depends on owners, provenance/version/offline and SR-06 | Content replaceable but promises are not trivial; release-critical; legal/support owners required | **EXTERNALLY_BLOCKED** | High; confirmed and reordered |
| 19 | Web content | INTENTIONALLY_DIFFERENT | Controlled web destinations | Allowlisted Custom Tabs/browser routes; no generic credential-bearing WebView | Safer untrusted-content boundary; objection: external transition is less visually seamless | Wrong URL policy enables phishing or broken critical pages; depends on 18 and SR-06/07 | Reversible renderer; security-critical allowlist; canonical owned URLs required | **INTENTIONALLY_DIFFERENT** | High; refined |
| 20 | Push notifications | DEFERRED | Firebase Messaging and launch routing | No production notifications in Phase 3; preserve proven disabled/consent boundary | Avoids backend/campaign/privacy work without purpose; objection: may reduce re-engagement/order updates | Wrong deferral could miss business value; depends on approved purposes, sender, routes, consent, token lifecycle and SR-07 | Reversible later; privacy/operations significant; no production sender/backend approved | **DEFERRED** | High; confirmed |
| 21 | Force update and Remote Config | INTENTIONALLY_DIFFERENT | Remote config and update gating | Safe local non-blocking defaults; hard minimum version only after governance | Prevents remote bricking; objection: critical security releases may need enforcement | Wrong hard gate strands users/support access; depends on P3-16 release policy and critical routes | Flag policy reversible, outages are not; release/security owners required for hard gate | **INTENTIONALLY_DIFFERENT** | High; refined |
| 22 | Session expiry | ACCEPTED | Expiry handling and route reset | Central refresh/terminal reset plus cart ownership protection | Required secure lifecycle; objection: buyer-identity detachment can fail offline | Wrong reset leaks session or misowns cart; depends on 09, 11, SR-01/07 | Policy hard to migrate after release; security/privacy critical; no new external content | **ACCEPTED** | High; materially refined |
| 23 | Analytics and crash reporting | DEFERRED | Firebase analytics/crash behavior | No Analytics in Phase 3; Crashlytics absent until P3-16 explicit decision | No lawful/operational purpose yet; objection: reduced production diagnostics | Wrong inclusion stores/transmits data; wrong exclusion slows diagnosis; depends on consent, taxonomy, retention, deletion, owner and SR-08 | Reversible only with careful data lifecycle; privacy/release decision required | **DEFERRED** | High; confirmed and refined |
| 24 | Localization and remote branding | INTENTIONALLY_DIFFERENT | Localized text and remote branding | Turkish/English resources and owned local tokens; only approved bounded remote content | Independent brand and predictable UI; objection: merchant may want rapid remote changes | Wrong scope permits unreviewed assets/hosts or stale content; depends on P3-00, markets, legal/content | Tokens/content reversible; asset licensing and locale correctness release-critical | **INTENTIONALLY_DIFFERENT** | High; confirmed |

## Final distribution

| Status | Count | Feature IDs |
|---|---:|---|
| `ACCEPTED` | 11 | 01, 03, 04, 05, 07, 09, 10, 14, 15, 16, 22 |
| `INTENTIONALLY_DIFFERENT` | 8 | 02, 06, 08, 11, 12, 19, 21, 24 |
| `DEFERRED` | 2 | 20, 23 |
| `EXTERNALLY_BLOCKED` | 2 | 17, 18 |
| `NOT_APPLICABLE` | 1 | 13 |

There are zero unresolved product-decision statuses. External inputs remain explicit blockers rather than being mislabeled as unresolved product choices.

## Reference-application coverage audit

### Audit basis and authority

The bounded audit used the prepared `analysis/99_master_report` system: `COMPLETE-APPLICATION-MAP.md`, `FEATURE-MAP.md`, `SCREEN-NAVIGATION-MAP.md`, `GRAPHQL-MAP.md`, `API-MAP.md`, `LOCAL-STORAGE-MAP.md`, `DATA-FLOW-MAP.md`, and `UNKNOWN-AREAS.md`, with the project evidence guide and summary helper. The package summary reports **24 feature groups, 25 destinations, 31 GraphQL operations, 5 endpoints/routes, and 9 storage records**. This is static client evidence, not independent proof of server or live remote state.

All 25 reference destinations and material client-visible capabilities fit the 24 groups. No genuinely independent twenty-fifth product group is justified.

### Represented or absorbed capability

| Reference capability | Phase 3 ownership |
|---|---|
| Verification/account-journey states | Group 12 and hosted semantics SR-05 |
| Guest/authenticated cart restoration and recovery | Groups 09 and 22; SR-01 |
| Cart note | Group 09; SR-02 |
| Cargo/fulfillment tracking | Group 16; SR-04 |
| Profile birthdate/metafield | Group 14; SR-03, blocked until purpose approval |
| Mobile pages and dynamic URLs | Groups 18 and 19; SR-06 |
| Notification/deep-link/callback routing | Groups 01, 19, 20, and 22; SR-07 |
| Language header and market-aware account entry | Groups 24 and 11 |

### Intentionally excluded reference behavior

- legacy email/password login, registration, verification, recovery, and password update contracts;
- the reference Worker’s cart, wishlist, profile, or deletion implementation;
- profile birthdate/metafield use without an approved purpose and schema;
- generic credential-bearing WebView behavior;
- reference UI hierarchy, visual design, copy, branding, assets, or remotely supplied branding values;
- production push, Analytics, Crashlytics, or analytics taxonomy;
- a policy-less hard force-update gate;
- unverified cart/wishlist merge semantics;
- live reference checkout, offsite-provider, or payment assumptions.

### Facts that remain `UNKNOWN`

- Worker authorization, persistence, merge, validation, deletion, logging, retention, availability, and error semantics;
- live Remote Config values, conditions, rollout, and enablement;
- FCM routing beyond the proven client launch target;
- dynamic page/WebView URLs and their live content;
- live reference checkout, offsite return, and payment behavior;
- whether Apollo SQL persistence is enabled merely because a library is present;
- live Shopify schema, products, token scope/rotation, market configuration, and server-side data rules;
- the complete directed runtime navigation graph.

These unknowns must not be converted into Gürbakır requirements. Only official contracts, project-owned source, or specifically authorized project-owned observations may resolve them.

## Product-level data and ownership rules

### Cart ownership

The production cart model must represent at least:

- `Anonymous`: no customer buyer identity is associated;
- `CustomerAssociated`: buyer identity is associated with a valid customer session;
- `DetachPending`: logout/expiry requested but buyer identity detachment has not been confirmed;
- `Quarantined`: the app cannot prove that customer association has been removed, so the cart cannot be exposed as anonymous or sent to checkout.

Login must reconcile a guest cart without silently discarding or duplicating valid lines. Logout and terminal expiry preserve only `Anonymous` carts. A `CustomerAssociated` cart may become anonymous only after confirmed detachment; otherwise it moves through `DetachPending` to `Quarantined`. Buyer identity is also a pricing, market, and authenticated-checkout input, not merely a UI sign-in marker.

### Local data and Room boundary

Room is appropriate for bounded production records that require querying, uniqueness, relationships, migrations, or deterministic deletion: local wishlist, approved search history, and their metadata. Session and cart secrets/opaque identifiers remain behind their existing Keystore-backed abstractions and are not migrated into Room. Non-secret scalar preferences may remain in typed DataStore.

Before the first Room-backed slice, record:

- schema version and stable table/entity names;
- uniqueness and environment/market partition keys;
- explicit forward migrations with fixture tests;
- downgrade/rollback behavior (normally fail closed or clear only the affected rebuildable cache after approval);
- local-data clear/delete mappings for logout, account deletion, user clear, expiry, environment switch, and uninstall;
- no destructive migration for retained user-created preference data without an explicit product decision.

The working search-history default is the last 10 unique queries for 30 days, but P3-00 must treat it as a reversible product/privacy default, not a permanent fact.

## Decision exit boundary

These decisions authorize planning only. P3-00 is the next slice and must pass before P3-01 becomes implementation-ready. No later slice may bypass its own external, content, security, schema, migration, or release entry criteria.
