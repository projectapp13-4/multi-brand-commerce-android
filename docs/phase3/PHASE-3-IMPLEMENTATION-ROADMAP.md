# Phase 3 Implementation Roadmap

Date: 2026-08-06

Status: **FINAL DOCUMENTATION-ONLY ROADMAP — NO PHASE 3 IMPLEMENTATION AUTHORIZED**

This roadmap implements the final scope in [PHASE-3-PRODUCT-DECISIONS.md](PHASE-3-PRODUCT-DECISIONS.md) through small, auditable vertical slices. The complete order is **P3-00 through P3-16**. A later slice may start only when its entry criteria are evidenced; an earlier technical proof never waives a product, content, legal, privacy, migration, or release gate.

## Roadmap principles

- P3-00 is a mandatory product-design, information-architecture, and content-readiness gate. It creates no production code.
- **P3-01 is not implementation-ready until P3-00 passes.**
- Each production slice must expose only complete, functional destinations. Do not ship disabled or fake production controls.
- The reference application supplies bounded behavior evidence and feasibility, not code, UI, branding, or server behavior.
- Current official Android, Shopify, and Firebase contracts govern implementation.
- Phase 2 production-compatible infrastructure is preserved; temporary proof surfaces are retired progressively.
- Every slice records requirement/evidence provenance, independently designed implementation, intentional differences, and remaining `UNKNOWN` server/runtime facts.
- Each slice ends in a clean commit/checkpoint after proportional verification. External proofs are performed only when the slice newly requires them; completed Phase 2 Shopify, OAuth, Checkout, Firebase, Bogus, and physical-device proofs are not repeated as setup work.

## Production information architecture direction

P3-00 must validate the final hierarchy. The working recommendation is primary navigation for **Home, Categories, Search, Wishlist, and Account**. A destination appears only after its functional slice passes. Cart is a contextual commerce action/destination and **must not be shown at all until P3-06 passes**; P3-01 must contain no disabled cart icon, fake cart screen, or placeholder count. The same no-placeholder rule applies to other incomplete production destinations.

## Slice order at a glance

| Slice | Name | Primary product outcome | Feature groups |
|---|---|---|---|
| P3-00 | Product Design, Information Architecture, and Content Readiness | Accepted production design and content contract | 02, 03, 04, 18, 24; constraints for all |
| P3-01 | Production shell and real Home | First real production entry surface | 01, 02, 03, 24 |
| P3-02 | Categories and listing | Browse catalog hierarchy and lists | 04, 05, 24 |
| P3-03 | Search and local history | Search and manage bounded local history | 06, 24 |
| P3-04 | Product detail | Inspect and select a valid product variant | 07, 24 |
| P3-05 | Local wishlist | Keep a local account-independent shortlist | 08, 24 |
| P3-06 | Production cart | Manage a complete cart with explicit ownership | 09, 22, 24 |
| P3-07 | Checkout handoff | Complete safe Checkout Kit handoff and return | 09, 10, 22 |
| P3-08 | Owned legal/support web baseline | Functional legal/support routes before Account | 18, 19, 24 |
| P3-09 | Account access, hosted journeys, and session | Sign in/out and recover session through hosted passwordless flow | 11, 12, 13, 19, 22 |
| P3-10 | Profile | View/edit only approved profile fields | 14, 24 |
| P3-11 | Addresses | Manage customer addresses | 15, 24 |
| P3-12 | Orders and tracking | View orders, fulfillment, and cargo tracking | 16, 19, 24 |
| P3-13 | Account deletion request | Submit or reach a real owned deletion request process | 17, 19, 22 |
| P3-14 | Safe Remote Config/update policy | Governed non-blocking update messaging and flags | 01, 21 |
| P3-15 | Product acceptance and hardening | Close integrated behavior, accessibility, migration, performance, and lifecycle gaps | all implemented groups |
| P3-16 | Production/release readiness | Prove release identity, policy, configuration, and exclusion boundaries | all; 20/23 remain deferred unless separately approved |

## Detailed slices

### P3-00 — Product Design, Information Architecture, and Content Readiness

- **Entry criteria:** Stage 3 planning checkpoint is committed and clean; a fresh task explicitly authorizes design/planning work; product, design, merchant-content, legal/support, and engineering owners are named.
- **User-visible outcome:** none in the application. The outcome is an accepted, implementable production experience specification.
- **Included groups:** direct decisions for 02, 03, 04, 18, and 24; requirements and layouts for every later group.
- **Dependencies:** final 24-group matrix; Phase 2 capability inventory; merchant catalog/taxonomy/market/address/currency inputs; owned brand and content inventory; legal/support ownership. Missing mandatory inputs are recorded as blockers, never fabricated.
- **Phase 2 reuse:** typed brand/design/content contracts, TR/EN resource structure, typed navigation/configuration, error taxonomy, accessibility semantics approach, and existing integration boundaries as feasibility constraints.
- **Required planning work:** define production IA; primary navigation and destination hierarchy; complete screen inventory; key end-to-end flows; Gürbakır visual direction; design tokens; reusable Compose component families; Home/category/list/product/cart/account layouts; loading/empty/error/offline patterns; accessibility baseline; TR/EN content structure; brand and merchant content inventories; market/currency/taxonomy/address inputs; image/media policy; design-review evidence and acceptance.
- **Permitted temporary neutral assets:** licensed Material icons, a text-only Gürbakır wordmark, neutral geometric or skeleton shapes, and project-owned Shopify product media. Each asset must have provenance.
- **Prohibited placeholders:** invented logos, commercial claims, promotions, prices, reviews, products, legal/support/deletion content, unlicensed stock, and any reference/legacy prototype brand/third-party reference asset, layout, copy, or remote brand value.
- **Proof UI:** inspected only to identify reusable infrastructure and removal obligations; no production code or screen is created.
- **Data/migration:** approve Room/DataStore/Keystore ownership map, provisional search-history policy, wishlist schema intent, cart ownership states, migration rules, local-data clear/delete matrix, and synthetic-data cleanup plan.
- **Security/privacy:** approve data inventory, external URL classes, account field/scope allowlist, local retention defaults, permission purposes, legal/support critical routes, and explicitly unresolved telemetry/push boundaries.
- **Accessibility/localization:** define contrast, large text/reflow, touch targets, TalkBack traversal/naming, reduced motion, keyboard/IME behavior, TR/EN content parity, pseudolocale review, and no text-in-image dependency.
- **Validation:** document/link/matrix review; design-flow walkthroughs; content/provenance review; accessibility checklist; requirement-to-screen and screen-to-slice traceability. No Gradle, browser, service, or device test.
- **Exit criteria:** every production destination and key state has an accepted specification; blockers are named by owner and phase; Home and shared component specs are sufficient for implementation; cart is explicitly absent from P3-01; permitted assets are enumerated; design review records `PASS`.
- **Rollback/checkpoint:** documentation/design-only commit. If `PASS` is absent, stop; P3-01 remains not implementation-ready.
- **Disposition:** new mandatory gate; sequential predecessor to every implementation slice.

### P3-01 — Production shell and real Home

- **Entry criteria:** P3-00 `PASS`; accepted Home/component/token/navigation specs; merchant Home content contract; image policy; neutral/owned asset list; cart remains absent.
- **User-visible outcome:** cold start reaches a real localized Home with coherent loading, partial, empty, offline, and retry states. Only functional navigation destinations are exposed.
- **Included groups:** 01, conditional outcome of 02, 03, and cross-cutting 24.
- **Dependencies/reuse:** preserve single-activity Compose, typed routes, Hilt, ViewModel/StateFlow, typed config/brand/error/localization, and Storefront gateway. Reuse Storefront contracts where sufficient; add only schema operations required by the accepted Home.
- **New production work:** production app shell, Home state/repository mapping, approved component families, safe media loading, progressive destination registration, and conditional first-run choice only if P3-00 required it.
- **Proof UI disposition:** remove Phase 2 proof Home/start controls and their production routing as equivalent production functions land. Retain reusable coordinators/adapters and any separately gated non-production harness source only outside the release route/variant.
- **Data/migration:** no new database unless P3-00 approved a versioned non-secret first-run preference; restore scroll/filter only when useful and bounded.
- **Security/privacy:** validate merchant URLs and content; no customer personalization or raw navigation telemetry; startup remote failure cannot brick the shell.
- **Accessibility/localization:** TalkBack order/headings, 200% text/reflow, focus after retry, image descriptions or decorative semantics, TR/EN and pseudolocale.
- **Tests:** reducer/mapper/repository unit tests; Storefront fixtures; Compose loading/partial/empty/error/offline/navigation/large-text tests; process-death startup restoration; focused device visual/accessibility check only for newly built UI.
- **Exit criteria:** deterministic start and accepted Home work from cold/warm/process-recreated states; no dead destination; no cart action or placeholder; old proof route is unreachable in production; links and requirements updated.
- **Rollback/checkpoint:** one clean shell/Home commit; revert does not alter Phase 2 adapters.
- **Disposition:** first implementation slice, but blocked until P3-00 passes.

### P3-02 — Categories and listing

- **Entry criteria:** P3-01 complete; accepted taxonomy/category IA; supported markets, sort/filter vocabulary, page-size/cache policy, and current Storefront schema confirmed.
- **User-visible outcome:** browse categories/collections, open a product list, sort/filter only where supported, paginate without duplicate/lost items, and recover from partial failures.
- **Included groups:** 04, 05, 24.
- **Dependencies/reuse:** typed routes, Home components, Storefront gateway/generated models, shared product card/media/error components.
- **New production work:** taxonomy repository/mapping, collection/list state machines, cursor paging, supported filter/sort presentation, deep-link resolution, empty/stale/offline behavior.
- **Proof UI:** no catalog proof control remains in production routing after this slice.
- **Data/migration:** define in-memory or bounded rebuildable cache; Room only if accepted offline needs justify it. If persisted, add explicit schema/version/migration tests and market partition keys.
- **Security/privacy:** bound query variables; validate media URLs; do not reveal opaque/internal IDs in user-visible errors or logs.
- **Accessibility/localization:** accessible grid/list semantics, filter controls, result-count announcements, focus after apply, TR/EN long labels and pseudolocale.
- **Tests:** pagination/property and cancellation tests; schema fixtures; repository cache/error tests; Compose list/filter/sort/scroll restoration; typed-route/deep-link instrumentation.
- **Exit criteria:** category-to-list journey is coherent online and under approved cache/offline policy; price/inventory absence is honest; no unsupported control appears.
- **Rollback/checkpoint:** clean catalog-browse commit; cache schema change is independently migratable/revertible.
- **Disposition:** preserved, with explicit taxonomy/schema/cache prerequisites.

### P3-03 — Search and local history

- **Entry criteria:** P3-02 listing contracts stable; P3-00 accepted search content/privacy and the reversible history default; Room schema/migration/clear policy approved.
- **User-visible outcome:** search with debounce/cancellation, distinguish empty result from error, view/remove/clear bounded local history, and leave no history when disabled.
- **Included groups:** 06 and 24; SR-08.
- **Dependencies/reuse:** Storefront search operation, product cards, error/cache conventions, typed local storage boundary.
- **New production work:** search state machine, cancellation/stale-result protection, history repository, Room entity/DAO/database if approved, clear controls, and optional local suggestions strictly from approved sources.
- **Proof UI:** none retained for search.
- **Data/migration:** start with an explicit Room version. Default is last 10 unique normalized queries and 30-day expiry until P3-00 changes it. Test upgrade, expiry, uniqueness, disabled-history cleanup, environment/market separation, corruption fallback, and deletion matrix. Never use destructive migration for retained history without approval.
- **Security/privacy:** queries may reveal interests; do not log or send them to analytics; clear action is immediate and honest.
- **Accessibility/localization:** IME/search actions, keyboard focus, result announcements without chatter, clear-history confirmation where appropriate, TR/EN/pseudolocale.
- **Tests:** virtual-time debounce/cancellation; repository and Room migration/integrity tests; Compose keyboard/history/empty/error tests; process recreation.
- **Exit criteria:** stale responses cannot replace newer results; local history obeys exact bound/expiry/clear/disable rules; all storage paths appear in SR-08.
- **Rollback/checkpoint:** clean search/history commit with schema migration and rollback note.
- **Disposition:** preserved and given an explicit privacy/migration gate.

### P3-04 — Product detail

- **Entry criteria:** P3-02 complete; accepted product content/media/related-source rules; current schema and inventory/price semantics confirmed.
- **User-visible outcome:** open a product, inspect approved media/content, select only a valid variant, see current price/availability, and prepare a valid add-to-cart intent without yet exposing cart navigation.
- **Included groups:** 07 and 24.
- **Dependencies/reuse:** catalog routes/cards, Storefront gateway, design components and URL/media policy.
- **New production work:** product repository and mapper, option/variant resolver, media gallery, content renderer, price/inventory refresh states, related products only if an approved source exists.
- **Proof UI:** Phase 2 synthetic product/cart proof controls remain excluded from production; the production detail emits a typed cart intent consumed only when P3-06 lands.
- **Data/migration:** no authoritative local product snapshot unless cache policy approves it; saved UI state contains only bounded selection identifiers.
- **Security/privacy:** sanitize/limit merchant rich content and URLs; never auto-select an invalid first variant; no invented stock or delivery promise.
- **Accessibility/localization:** variant groups/names/states, media descriptions, zoom semantics, dynamic price announcements, long copy/reflow, TR/EN/pseudolocale.
- **Tests:** exhaustive variant-combination unit tests; schema/media mapper fixtures; price/inventory change tests; Compose selection/loading/error/large-text; process recreation.
- **Exit criteria:** selected variant deterministically drives price, media, availability, and later cart ID; invalid/unavailable combinations cannot be submitted.
- **Rollback/checkpoint:** clean product-detail commit independent of cart implementation.
- **Disposition:** preserved; related products remain optional until source approval.

### P3-05 — Local wishlist

- **Entry criteria:** P3-04 stable product identity; approved local-only expectation/copy; Room ownership/migration/SR-08 rules accepted.
- **User-visible outcome:** add/remove products from detail/list and browse a durable local wishlist without signing in or implying cross-device sync.
- **Included groups:** 08 and 24; SR-08.
- **Dependencies/reuse:** product identity/card/detail routes, shared Room database if introduced in P3-03.
- **New production work:** local wishlist repository, idempotent add/remove, product rehydration and deleted/unavailable-item handling, future sync interface seam with no implementation.
- **Proof UI:** prototype fake sync and reference Worker behavior are excluded.
- **Data/migration:** stable product key plus environment/market partition and timestamps as justified; migration tests; deletion/clear behavior explicitly documented. Preserve user preference data across ordinary logout because it is account-independent.
- **Security/privacy:** preference data stays local; no account association or analytics by default; copy states “saved on this device” when relevant.
- **Accessibility/localization:** toggles expose state/action, not icon color alone; list empty/error/unavailable semantics; TR/EN/pseudolocale.
- **Tests:** repository idempotency/property tests; Room migrations; product rehydration/cache failure; Compose toggle/list/process-death tests.
- **Exit criteria:** local persistence and deletion behavior match SR-08; no network sync call, Worker endpoint, account requirement, or cross-device promise exists.
- **Rollback/checkpoint:** clean local-wishlist commit and reversible database migration.
- **Disposition:** moved earlier because it is account-independent and reuses product surfaces.

### P3-06 — Production cart

- **Entry criteria:** P3-04 valid product intents; market/currency and price/inventory policy; cart note decision; guest/auth reconciliation and four-state ownership matrix; local migration strategy.
- **User-visible outcome:** create/read/add/update/remove a complete cart, see honest price/inventory changes, recover from typed failures, and open the first functional cart destination/action.
- **Included groups:** 09, 22, and 24; SR-01 and SR-02.
- **Dependencies/reuse:** preserve Phase 2 Storefront cart gateway/coordinator, complete opaque cart ID, Keystore-backed cart store, typed errors, concurrency and invalid-cart rules.
- **New production work:** cart UI/state/repository orchestration; quantity/line/note policy; ownership state machine (`Anonymous`, `CustomerAssociated`, `DetachPending`, `Quarantined`); buyer-identity attach/detach contract; guest/auth reconciliation; price/inventory refresh; production cart navigation/badge.
- **Proof UI:** retire cart proof controls from production routing. Keep the proven coordinator and Keystore implementation. Any non-production harness must remain unreachable and excluded from release configuration.
- **Data/migration:** cart ID/session remains Keystore-backed, not Room. Room may store only approved rebuildable presentation metadata. Migration must not silently relabel ownership. Legacy or ambiguous customer association fails closed to `Quarantined` until resolved/cleared.
- **Security/privacy:** never log cart IDs, attributes, notes, checkout URLs, buyer identity, or server messages; bound notes/quantities; quarantine prevents identity leakage after logout/expiry.
- **Accessibility/localization:** quantity controls, line removal, totals/status announcements, focus after mutation, large totals/long names, TR/EN/currency formatting.
- **Tests:** ownership transition/state-machine tests including offline detach; concurrency/cancellation; Storefront userError/GraphQL/HTTP fixtures; process death; Compose empty/mutation/inventory/price/error/quarantine states; focused device check for production UI only.
- **Exit criteria:** valid carts survive transient failures; replacement occurs only on definitive invalid/expired state; logout/expiry preserves only proven anonymous cart; customer-associated cart detaches or quarantines; cart action appears only now and is fully functional.
- **Rollback/checkpoint:** clean cart commit; schema/state transition documented and independently reversible without weakening identity controls.
- **Disposition:** preserved, materially refined, and moved from the old P3-05 number.

### P3-07 — Checkout handoff

- **Entry criteria:** P3-06 complete; checkout entry eligibility, cart refresh, authenticated/anonymous buyer-identity policy, URL policy, and production UI copy accepted.
- **User-visible outcome:** start Checkout Kit from a refreshed eligible cart; cancel/failure returns to the retained cart; confirmed completion clears only the completed cart and routes safely.
- **Included groups:** 09, 10, 22.
- **Dependencies/reuse:** preserve proven Checkout Kit adapter/coordinator, exact-host policy, callback mapping, preload invalidation, and Phase 2 physical proof evidence.
- **New production work:** production checkout entry/state/confirmation routing, current cart integration, lifecycle restoration, user-facing recovery, and optional account-aware handoff only through official contract.
- **Proof UI:** remove Phase 2 Checkout/Bogus proof controls from production routing/release. Do not repeat Bogus/device setup merely to re-prove the unchanged adapter.
- **Data/migration:** no payment data storage; clear local cart only on genuine completion; retain on cancel/decline/failure; reconcile process death without guessing completion.
- **Security/privacy:** exact owned checkout host; restrictive permission/external-link policy; no checkout URL, SDK payload, payment/order details, or screenshots in logs/telemetry.
- **Accessibility/localization:** accessible launch/progress/recovery; external-context explanation; localized outcomes controlled by app where applicable; hosted checkout content remains Shopify-owned.
- **Tests:** adapter tests only for changed mappings; app coordinator/Compose/lifecycle/offsite-return tests; targeted physical-device proof only if production routing or SDK contract materially differs from the Phase 2-proven path.
- **Exit criteria:** no false completion; cart lifecycle matches all callbacks and process states; production route contains no Bogus/test affordance.
- **Rollback/checkpoint:** clean checkout-UX commit while retaining Phase 2 adapter contract.
- **Disposition:** preserved after production cart.

### P3-08 — Owned legal/support web baseline

- **Entry criteria:** owned privacy, terms, support, and required mobile-page sources exist; content owner, provenance, canonical URL, version/effective date, cache/offline behavior, and update process are recorded.
- **User-visible outcome:** reach real, accessible, allowlisted legal/support pages with honest offline/error behavior before any production Account feature appears.
- **Included groups:** 18, 19, 24; SR-06 and SR-07.
- **Dependencies/reuse:** typed URL and external-route policy, brand/content contracts, navigation/error taxonomy.
- **New production work:** page index, version/provenance model, allowlisted Custom Tabs or browser adapter, safe fallback/support route, contextual permission rationale pattern. No generic credential WebView.
- **Proof UI:** any Phase 2 legal placeholder remains excluded and is removed where production pages replace it.
- **Data/migration:** cache only if approved, with version/expiry/source and clear rules; legal acceptance state is not invented. Critical page access must not depend solely on Remote Config.
- **Security/privacy:** HTTPS/host/path allowlist, redirect/open-with policy, no auth tokens in URLs, external-context clarity, minimum permissions.
- **Accessibility/localization:** page titles, external-navigation announcements, focus return, TR/EN link labels and summaries; hosted content accessibility has an accountable owner.
- **Tests:** URL/redirect policy unit tests; page metadata/cache tests; Compose navigation/offline/error/accessibility; no live browser/service operation during planning. Later implementation tests use controlled owned URLs.
- **Exit criteria:** production privacy/terms/support routes are functional and versioned; placeholders are absent; Account slice entry gate is satisfied.
- **Rollback/checkpoint:** clean legal/support baseline commit; if any mandatory source is unavailable, stop with group 18 externally blocked and do not begin P3-09.
- **Disposition:** split from the old combined legal/deletion work and reordered before Account.

### P3-09 — Account access, hosted journeys, and session

- **Entry criteria:** P3-08 complete; hosted passwordless copy and states; approved Customer Account field/scope allowlist; data inventory; market-aware auth; callback/deep-link matrix; cart reconciliation/detachment rules; synthetic-user cleanup process.
- **User-visible outcome:** sign in through the system browser/hosted Customer Account journey, return safely, restore/refresh session, sign out, recover from cancellation/expiry, and obtain help without any password screen.
- **Included groups:** 11, 12, 13, 19, 22; SR-01, SR-05, SR-07.
- **Dependencies/reuse:** preserve Phase 2 OAuth discovery, AppAuth presentation, exact exchange/refresh client, one-time state/nonce/PKCE, Keystore session, typed Customer Account client, redaction, and proven callback coordinator.
- **New production work:** account entry/status UI, hosted-journey orchestration, session-expiry recovery, legal/support links, market context, production navigation reset, and cart ownership reconciliation.
- **Proof UI:** remove OAuth proof screens/manual controls from production routing/release; retain underlying adapters and non-production test seams.
- **Data/migration:** tokens remain Keystore-backed; non-secret account display cache has explicit expiry/clear rules; logout/session expiry executes SR-08 and cart detach/quarantine without erasing local-only wishlist.
- **Security/privacy:** no credentials/passwords collected; strict callback allowlist/state replay protection; least Customer Account scopes; generic redacted errors; no raw identity/token logs.
- **Accessibility/localization:** browser transition and return announcements, progress/cancel/retry/help, TR/EN hosted-semantics copy, TalkBack focus restoration.
- **Tests:** state/reducer/navigation; changed OAuth orchestration contracts with fakes; callback/replay/deep-link instrumentation; process death and refresh/terminal-expiry; cart reconciliation. Repeat live OAuth only if new production callback identity or official contract requires it, not as routine proof.
- **Exit criteria:** all hosted success/cancel/failure/expiry/logout paths are coherent; no legacy login/register/password UI exists; customer-associated cart is never exposed anonymously; legal/support links remain reachable.
- **Rollback/checkpoint:** clean account-access commit preserving validated OAuth/session infrastructure.
- **Disposition:** account work begins only after P3-08.

### P3-10 — Profile

- **Entry criteria:** P3-09 complete; approved field/scope allowlist and current Customer Account schema; privacy/content owner approves display/edit semantics. Birthdate/metafield remains excluded unless SR-03 passes separately.
- **User-visible outcome:** view and edit only approved profile fields with clear validation, save, conflict, offline, and session-expiry behavior.
- **Included groups:** 14 and 24; SR-03.
- **Dependencies/reuse:** Customer Account client/session, account layouts, error/redaction/localization.
- **New production work:** profile query/mutation repository, explicit editable-field model, validation, optimistic or confirmed save policy, stale/conflict recovery.
- **Proof UI:** remove static proof identity display where the production profile replaces it.
- **Data/migration:** avoid persistent PII unless needed; any cache is encrypted/short-lived as justified and covered by SR-08. No birthdate/metafield storage without separate approval.
- **Security/privacy:** least fields/scopes, no PII logs/analytics, re-auth only where official contract/policy requires it.
- **Accessibility/localization:** correct labels/errors/required semantics, keyboard order, large text, TR/EN names and validation copy.
- **Tests:** mapper/validation/repository conflict and session tests; Compose view/edit/error/offline; process recreation with no unsafely persisted form data.
- **Exit criteria:** only approved fields can be read/edited; session/permission failures recover safely; SR-03 remains explicitly excluded or separately approved.
- **Rollback/checkpoint:** clean profile commit with no schema expansion by inference.
- **Disposition:** preserved and narrowed.

### P3-11 — Addresses

- **Entry criteria:** P3-09 complete; supported countries/markets, address-field rules, default-address behavior, validation and privacy copy approved; current schema confirmed.
- **User-visible outcome:** list, add, edit, delete, and set default addresses within supported market rules.
- **Included groups:** 15 and 24.
- **Dependencies/reuse:** Customer Account client/session, account shell, market/localization/error contracts.
- **New production work:** typed address repository, country-aware form model, server-error mapping, default/delete confirmation, concurrency/conflict recovery.
- **Proof UI:** none retained for addresses.
- **Data/migration:** do not persist full addresses locally by default; draft restoration, if approved, must be bounded/protected and included in SR-08.
- **Security/privacy:** strict PII redaction, no address telemetry, deletion honesty, safe autofill/keyboard behavior, no excessive permissions.
- **Accessibility/localization:** dynamic field labels/order by country, errors linked to fields, TalkBack traversal, TR/EN formats and long text.
- **Tests:** validation/property tests by supported country; Customer Account fixtures; CRUD/conflict/session tests; Compose form/accessibility/process-death tests.
- **Exit criteria:** supported CRUD/default paths succeed and fail safely; unsupported market behavior is explicit; no address PII survives outside approved boundaries.
- **Rollback/checkpoint:** clean address commit.
- **Disposition:** preserved after legal and account access.

### P3-12 — Orders and tracking

- **Entry criteria:** P3-09 complete; current order/fulfillment schema; approved status vocabulary, pagination, retention/cache, support escalation, carrier allowlist and partial-fulfillment rules.
- **User-visible outcome:** view private order list/detail, fulfillment status, and available cargo tracking; unavailable tracking is honest and recoverable.
- **Included groups:** 16, 19, 24; SR-04.
- **Dependencies/reuse:** Customer Account session/client, legal/support URL baseline, typed external URL policy and shared states.
- **New production work:** paginated order repository, detail/fulfillment mapper, tracking model, allowlisted carrier handoff, refresh and support recovery.
- **Proof UI:** any Phase 2 synthetic identity/order evidence remains test evidence only and is not presented in production.
- **Data/migration:** private order cache is off by default unless an approved encrypted/expiry policy exists; clear on terminal session/account-deletion path as required by SR-08.
- **Security/privacy:** no order/customer/tracking detail in logs, notifications, analytics, screenshots policy, or untrusted URLs; account ownership enforced by API/session.
- **Accessibility/localization:** status not color-only, timeline/list semantics, dates/currency/addresses localized, accessible external transition, TR/EN status/support copy.
- **Tests:** pagination/mapping/partial fulfillment; session/permission/error fixtures; carrier URL policy; Compose empty/detail/tracking/offline/accessibility.
- **Exit criteria:** only current customer orders appear; status/tracking never invented; unsupported or absent carrier links fail safely to support.
- **Rollback/checkpoint:** clean orders/tracking commit.
- **Disposition:** preserved with a new explicit cargo/fulfillment sub-requirement.

### P3-13 — Account deletion request

- **Entry criteria:** a real owned web or service request resource; accountable operator; legal/privacy process; re-auth rule; affected-system/data inventory; retention exceptions; support/SLA; success/failure language; synthetic-user cleanup. If absent, this slice remains blocked.
- **User-visible outcome:** authenticated user can initiate/reach a functional deletion request, understand scope and consequences, receive honest acknowledgement, and safely end local session when policy requires.
- **Included groups:** 17, 19, 22; SR-08.
- **Dependencies/reuse:** P3-08 web baseline, P3-09 account/re-auth/session, typed URL policy, local deletion matrix.
- **New production work:** only the approved request mechanism and local orchestration. A new backend requires a separate ADR, threat model, owner, authz, validation, secret, retention, logging, availability, and operations plan.
- **Proof UI:** no placeholder deletion button or false “account deleted” result.
- **Data/migration:** execute documented local clear/delete actions by data class; preserve only data the approved policy explicitly permits; server completion is never inferred from local clear.
- **Security/privacy:** re-auth where required; CSRF/replay/authz if native API exists; no Admin/backend secret in mobile; deletion request/audit data minimized and protected.
- **Accessibility/localization:** consequence/confirmation/error/support content accessible, non-coercive, and TR/EN reviewed.
- **Tests:** policy/state tests; re-auth/session/local-clear matrix; owned request contract or URL policy; Compose confirmation/error/accessibility. External functional proof only against an authorized project-owned non-production resource.
- **Exit criteria:** request works end to end or the group remains explicitly `EXTERNALLY_BLOCKED`; local and remote outcomes are not conflated; operator/support evidence exists.
- **Rollback/checkpoint:** clean deletion-request commit only after external gate passes; otherwise documentation-only blocked checkpoint.
- **Disposition:** preserved as release-critical and externally blocked.

### P3-14 — Safe Remote Config/update policy

- **Entry criteria:** P3-08 critical routes exist; accepted flag owners/types/defaults; release-version source; user messaging and retry policy; decision on whether any minimum-version enforcement is allowed; grace/rollback/support path.
- **User-visible outcome:** safe optional update messaging and bounded flags work without preventing normal startup when Remote Config is unavailable. A hard gate exists only if separately approved and tested.
- **Included groups:** 01 and 21.
- **Dependencies/reuse:** preserve Phase 2 explicit-fetch Remote Config adapter, typed false defaults, consent/network boundary, environment separation.
- **New production work:** product-owned update policy/state/UI, version comparison, refresh cadence, cached-value expiry, emergency rollback, and always-available critical legal/support access.
- **Proof UI:** remove manual Remote Config proof controls from production routing/release.
- **Data/migration:** store only typed non-sensitive values and fetch metadata with explicit expiry; incompatible/unknown values fall back safely.
- **Security/privacy:** Remote Config cannot introduce arbitrary routes/assets/code/hosts or weaken security; avoid fingerprinting/analytics coupling.
- **Accessibility/localization:** update message/retry/defer semantics, TalkBack focus, large text, TR/EN/pseudolocale.
- **Tests:** flag parsing/default/cached/expired/version policy; offline and malformed response; rollback; Compose message/gate/critical-route access; process death. No repeat Firebase console/device proof unless contract/config changes require it.
- **Exit criteria:** fetch failure is non-blocking; any approved hard gate has grace, rollback, support/legal access, and release-owner evidence; proof controls are absent.
- **Rollback/checkpoint:** clean remote-policy commit; server rollback path documented before enforcement.
- **Disposition:** preserved with non-blocking default and conditional hard-gate governance.

### P3-15 — Product acceptance and hardening

- **Entry criteria:** all intended P3-01 through P3-14 slices are complete or explicitly blocked/deferred; migration inventory and integrated acceptance suite are current.
- **User-visible outcome:** implemented journeys behave coherently across navigation, session/cart transitions, offline/retry, process death, accessibility, localization, and realistic performance.
- **Included groups:** every implemented group; blocked/deferred/non-applicable statuses remain explicit.
- **Dependencies/reuse:** all production modules and validated Phase 2 adapters; existing format/static/unit/UI/instrumentation lanes.
- **New production work:** only evidence-driven fixes, integration state restoration, deep-link routing, performance and accessibility remediation. No scope expansion.
- **Proof UI:** prove all temporary proof routes/controllers/ViewModels are absent from production navigation and release variants while reusable infrastructure remains.
- **Data/migration:** run the full Room migration chain from every supported version, rollback/fail-closed policy, local delete matrix, environment/market isolation, corruption recovery, and synthetic data cleanup.
- **Security/privacy:** focused trust-boundary regression for auth/cart/checkout/URLs/PII/deletion/config; redaction and secret/dependency scanning.
- **Accessibility/localization:** full TalkBack critical journeys, switch access where practical, 200% text, contrast/touch targets, TR/EN, pseudolocale, RTL readiness where specified.
- **Tests/device proof:** formatting/static analysis; focused unit/contract/repository; Compose/instrumentation; deep-link/OAuth/Checkout/push lifecycle only where changed; offline/network/process-death; baseline/performance measurements. Use authorized device/environment narrowly; do not place real orders or repeat unchanged foundation proofs.
- **Exit criteria:** acceptance matrix rows link to passing evidence or explicit blocker; no severity-high functional/a11y/security defect; performance budgets met; no placeholder/synthetic production content.
- **Rollback/checkpoint:** one or more coherent hardening commits without mixing release secrets/config.
- **Disposition:** preserved as integrated acceptance gate.

### P3-16 — Production/release readiness

- **Entry criteria:** P3-15 passes; final brand/legal/deletion state; production package/application identity, signing and Play ownership; production Shopify/Firebase configuration; App Links/callbacks; Data Safety/privacy disclosures; support and release owners; dependency/schema/release policy.
- **User-visible outcome:** a releasable production candidate with honest policies, identity, support, safe rollout/rollback, and no proof/test surface or synthetic data.
- **Included groups:** all final statuses. Groups 20 and 23 remain unimplemented unless a separate product/privacy authorization changes scope.
- **Dependencies/reuse:** production-compatible infrastructure from Phase 2 and all passed Phase 3 slices; approved release automation and secret provisioning only.
- **New production/release work:** production identity/configuration, signing and store metadata through separately authorized secure processes; App Links; final policy/content; release monitoring decision; migration/rollback rehearsal; release artifact checks.
- **Proof UI:** release graph/artifact contains no proof controllers, manual OAuth/cart/Checkout/Firebase/Bogus actions, test credentials, synthetic products/users/orders, or non-production destination.
- **Data/migration:** production upgrade and rollback plan, backup/recovery where applicable, local-data deletion, cache expiry, and environment-switch isolation proven.
- **Security/privacy:** final threat-boundary review, secrets/dependency/SBOM/license checks, Play Data Safety alignment, least scopes/permissions, account deletion availability, support incident path.
- **Telemetry decision:** Analytics remains absent by default. Crashlytics remains absent unless this gate records accountable owner, lawful/consent basis, data/retention/deletion, environment separation, redaction, local unsent-report behavior, user control, and updated acceptance/security evidence.
- **Remote-update decision:** any hard minimum-version gate requires owner, staged rollout, grace, rollback, support/legal access, and failure-mode proof; otherwise only non-blocking behavior ships.
- **Accessibility/localization/performance:** final TalkBack, large text, color/contrast, TR/EN/pseudolocale/content review, startup/catalog/cart/account performance budgets and regression checks.
- **Tests/external proof:** release build, signing/config validation, focused production-like non-production smoke, App Links/callback, migration, policy and rollback checks. External mutations, production credentials, paid transactions, and production deployment require explicit separate authorization.
- **Exit criteria:** every release blocker is closed with evidence; externally blocked group 17/18 requirements are satisfied or release stops; deferred/absent SDK/features are verified absent; release candidate is independently auditable.
- **Rollback/checkpoint:** final pre-release commit/tag boundary and documented rollout rollback. No remote push or deployment is implied by this roadmap.
- **Disposition:** expanded release gate.

## Room schema and migration boundary

| Data class | Storage owner | Phase introduced | Migration/deletion rule |
|---|---|---|---|
| Search history | Room if P3-00 approves retention | P3-03 | Versioned schema; unique normalized query; environment/market partition; expiry/clear/disable migration; SR-08 |
| Local wishlist | Room | P3-05 | Stable product key; environment/market partition; preserve across logout; explicit user/account-deletion policy; no destructive migration without approval |
| Customer OAuth/session | Existing Keystore-backed abstraction | Phase 2/P3-09 | Never Room/DataStore plaintext; fail closed on corruption; clear on terminal logout/deletion policy |
| Cart opaque ID/ownership-sensitive session | Existing Keystore-backed cart abstraction plus typed state | Phase 2/P3-06 | Never silently migrate customer-associated to anonymous; ambiguous association quarantines; clear only by explicit invalid/completed/user policy |
| Non-secret scalar preferences | Typed DataStore | As justified | Version/default behavior documented; no PII/token/cart ID |
| Rebuildable catalog/page cache | Prefer memory; Room only with accepted offline need | P3-02/P3-08 | Market/version/expiry partition; safe clear on incompatibility; never source of truth for price/inventory/legal version |
| Profile/address/order PII | No persistence by default | P3-10–P3-12 | Any later cache needs explicit encryption, expiry, migration, deletion, and privacy approval |

Every Room change requires exported/checked schema evidence where project conventions support it, forward migration tests from every supported version, deterministic fixture coverage, a rollback/fail-closed decision, and an update to SR-08. Destructive migration is not an acceptable convenience for user-retained wishlist data.

## Cross-cutting concern matrix

| Concern | Owning slices | Required entry/exit evidence |
|---|---|---|
| Navigation and deep links | P3-00, P3-01, P3-08, P3-09, P3-12, P3-15 | Destination/source/auth/session matrix; allowlisted routes; unknown/replay fallback; implemented destinations only; integrated deep-link tests |
| Identity and session | P3-00, P3-08–P3-11, P3-13, P3-15 | Field/scope allowlist; hosted passwordless states; Keystore lifecycle; expiry/logout/re-auth and local-clear evidence |
| Anonymous/authenticated cart | P3-00, P3-06, P3-07, P3-09, P3-15 | Four ownership states; reconcile/detach/quarantine; price/market effects; no silent anonymous reclassification |
| Customer Account permissions | P3-00, P3-09–P3-13, P3-16 | Minimum scopes/fields, protected-data availability, permission errors, release configuration |
| Local persistence and migrations | P3-00, P3-03, P3-05, P3-06, P3-15, P3-16 | Storage ownership table, Room version chain, no destructive wishlist migration, SR-08 and rollback proof |
| Market and currency | P3-00–P3-07, P3-09, P3-11, P3-15 | Supported markets, selection/inference, buyer identity, cache partitions, localized currency and market-change behavior |
| Price and inventory changes | P3-02, P3-04, P3-06, P3-07, P3-15 | Refresh points, honest stale/unavailable states, mutation reconciliation, no invented promise |
| Process death and restoration | P3-01–P3-15 | Per-slice saved/persistent state classification, no secret in SavedState, restart tests and safe external-return handling |
| Offline and cache | P3-00–P3-15 | Per-screen stale/empty/error policy, authoritative-source boundary, expiry/invalidation, mutation restrictions |
| Image and media policy | P3-00, P3-01, P3-02, P3-04, P3-15 | Provenance/host/format/size rules, loading/error/accessibility, no copied or invented assets |
| Secure external URLs | P3-00, P3-07–P3-09, P3-12–P3-16 | HTTPS/host/path/source allowlist, redirect/external-context policy, no token leakage, controlled tests |
| Privacy and local deletion | P3-00, P3-03, P3-05, P3-09–P3-13, P3-16 | Data inventory and SR-08 by user clear/logout/expiry/deletion/uninstall/environment; remote outcome not inferred |
| Localization and pseudolocales | P3-00 through P3-16 | TR/EN key/content parity, locale/market distinction, pseudolocale and long-text checks each slice |
| Accessibility and TalkBack | P3-00 through P3-16 | Baseline component semantics; slice-specific focus/state/error tests; full critical-journey audit in P3-15/16 |
| Error taxonomy and recovery | P3-00, each implementation slice, P3-15 | Typed userError/GraphQL/HTTP/auth/session/policy classes; redacted messages; retry only when safe |
| Performance | P3-00, P3-01–P3-07, P3-12, P3-15/16 | Agreed budgets, list/image/pagination measurement, startup and critical-journey regression evidence |
| Dependency/schema upgrades | Each schema/dependency slice, P3-15/16 | Requirement-based additions, lock/verification update, current schema diff review, focused regression and rollback |
| Proof versus production surfaces | P3-01, P3-06–P3-09, P3-14–P3-16 | Removal ledger; production route/release graph excludes proof controllers/ViewModels/actions; adapters retained |
| Release identity/configuration | P3-00, P3-08, P3-09, P3-14, P3-16 | Package/signing/Play/Firebase/Shopify/App Links/URLs/owners provisioned securely; no invented identity |
| Synthetic-data cleanup | P3-00, P3-07, P3-09, P3-13, P3-15/16 | Named cleanup owner/process; zero test accounts/carts/orders/config values in production UI/artifact/logs |

## Dependency and blocker map

### Before P3-00

- this Stage 3 checkpoint committed on clean `main`, empty stash;
- fresh task explicitly authorizes P3-00 planning/design only;
- product, design, merchant-content, legal/support, and engineering owners named.

### Before P3-01

- P3-00 `PASS` with production IA, navigation, screen inventory, flows, Home/layout/component/token specifications;
- merchant catalog/Home content, image policy, approved neutral/owned asset list;
- market/language inference decision and any minimal first-run requirement;
- explicit rule that cart and every incomplete destination are absent, not placeholders.

### Before catalog and cart implementation

- supported market/currency behavior and price/inventory display rules;
- merchant taxonomy and supported filter/sort policy;
- current Storefront schema and operation requirements;
- cache/offline policy, variant/media/related-product source;
- cart ownership/reconciliation/detachment/quarantine matrix and buyer-identity effects;
- cart-note policy;
- Room schema, migration, deletion, environment/market partition, and rollback boundaries.

### Before account features

- functional owned privacy, terms, support, and other required page routes;
- hosted passwordless behavior/copy and route/callback matrix;
- Customer Account field/scope allowlist and user-data inventory;
- market-aware authentication/address rules;
- account-deletion process design and responsible owner, even if the final resource is still P3-13-blocked;
- synthetic account/data cleanup process.

### Before release

- final brand assets/content and legal/support/deletion request resource;
- production package/application ID, signing, Play identity, Shopify/Firebase configuration and secure provisioning;
- App Links/callbacks, Data Safety/privacy/support artifacts;
- accessibility and performance acceptance; dependency/secret/license scans;
- Remote Config hard-gate decision with grace/rollback/critical routes;
- explicit Analytics/Crashlytics decision, with absence as the default;
- Room migration/rollback and local-deletion proof;
- all proof UI/controllers/actions and synthetic data excluded from production release.

### Deferred beyond Phase 3

- production push campaigns, sender/backend and token association;
- behavioral Analytics;
- cross-device wishlist synchronization;
- reference Worker parity or any unowned Worker semantics;
- a new project backend without a separately approved need/ADR/owner/threat model;
- iOS implementation;
- arbitrary remote branding, code, assets, routes, or hosts.

## Critical path and safe parallelism

The sequential critical path is:

`Stage 3 checkpoint -> P3-00 PASS -> P3-01 -> P3-02 -> P3-04 -> P3-06 -> P3-07 -> P3-08 -> P3-09 -> P3-10/P3-11/P3-12 -> P3-13 -> P3-15 -> P3-16`

After the shared prerequisites exist:

- P3-03 and P3-04 may progress in parallel after P3-02, but shared product-card/schema/storage changes must be serialized cleanly.
- P3-05 may progress after P3-04 without Account work and is intentionally early.
- Legal/support content preparation for P3-08 and deletion-process design for P3-13 should run in parallel with catalog work, but production Account implementation still waits for P3-08.
- P3-10, P3-11, and P3-12 may proceed independently after P3-09 if shared Customer Account schema/client changes are coordinated.
- P3-14 can proceed after P3-08 critical-route availability, while account feature work continues.

Parallelism never waives a shared schema migration, navigation, content, security, or release entry gate.

## First-slice readiness assessment

### Already available

- clean completed Phase 2 checkpoint with all eight foundation gates `PASS`;
- six native modules and single-activity Compose/UDF architecture;
- typed configuration, navigation, brand/design, localization, errors, logging/redaction, and safe environment boundaries;
- production-compatible Storefront gateway/cart coordinator and Keystore cart storage;
- Customer Account OAuth/PKCE/session and separate typed client;
- Checkout Kit adapter/coordinator;
- Firebase Remote Config/FCM safe-default and explicit-consent boundaries;
- Turkish/English resource parity, 24-group inventory, prepared reference reports, project-owned non-production evidence, physical proof, and security controls.

Nothing material prevents starting **P3-00** after this documentation checkpoint and a fresh task explicitly authorizes that design/planning slice.

### Missing for P3-00 completion

- accepted visual direction and brand-asset inventory;
- merchant taxonomy, catalog/editorial content, markets, currencies, address policy, and legal/support inventory;
- full production screen/layout/component/flow specifications;
- accepted loading/empty/error/offline/accessibility/localization patterns;
- design-review evidence and `PASS` decision.

### Missing for P3-01 start

- P3-00 `PASS` and the resulting production IA, Home, component, token, content, media, and asset specifications.

Therefore **P3-00 is ready to be planned in the next authorized task; P3-01 is not implementation-ready**.

## Phase 2 proof UI versus reusable infrastructure

### Preserve as production-compatible infrastructure

- typed environment, brand, localization, navigation, URI, error, logging/redaction, and security contracts;
- Hilt/UDF/ViewModel/StateFlow architecture and module boundaries;
- Storefront Apollo client/gateway, current cart coordinator and Keystore-backed cart abstraction;
- Customer OAuth/PKCE/discovery/exchange/refresh/callback/session and Keystore storage;
- Checkout Kit project adapter/coordinator and restrictive policies;
- Firebase typed safe defaults, explicit consent/registration boundary, and route allowlist;
- pinned wrappers/dependencies/verification, CI quality lanes, tests, scanners, locks, and Phase 2 proof evidence.

### Retire from production surfaces

- temporary proof Home/startup navigation;
- manual Storefront/cart/Checkout/Bogus actions and synthetic result presentation;
- OAuth proof controllers/screens/manual callbacks;
- Firebase registration, notification, Remote Config, and proof controls;
- proof-only ViewModels/controllers/routes/test data exposed by the app.

Removal is progressive: the corresponding production slice must remove the proof surface from production routing and release variants while preserving reusable adapters and test seams. P3-15 and P3-16 must prove that no proof or synthetic surface remains in the production route graph or artifact.

## Completion boundary

This roadmap is a planning artifact, not authorization to execute P3-00 or any implementation slice. The next task starts at [PHASE-3-IMPLEMENTATION-GOAL.md](PHASE-3-IMPLEMENTATION-GOAL.md), performs P3-00 first, and stops unless that gate records `PASS`.
