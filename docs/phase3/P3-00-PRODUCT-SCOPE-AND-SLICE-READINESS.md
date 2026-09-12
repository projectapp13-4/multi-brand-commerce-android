# P3-00 Product Scope and Slice Readiness

Date: 2026-08-10

Gate result: **PASS — planning and implementation-readiness specification only**

Machine-readable companion: [p3-00-feature-readiness.csv](p3-00-feature-readiness.csv)

This document closes the P3-00 product-scope and slice-readiness work. It does not authorize or implement P3-01. The final product distribution remains exactly 11 `ACCEPTED`, 8 `INTENTIONALLY_DIFFERENT`, 2 `DEFERRED`, 2 `EXTERNALLY_BLOCKED`, and 1 `NOT_APPLICABLE` across 24 groups.

## Authority and review result

The accepted behavior remains governed by [Phase 3 Product Decisions](PHASE-3-PRODUCT-DECISIONS.md), with the concise status source in [Phase 3 Acceptance Matrix](PHASE-3-ACCEPTANCE-MATRIX.md). P3-00 adds implementation-readiness detail without changing a status. Phase 2 adapters and proofs are reusable foundation evidence; proof UI is not a product requirement. Prepared reference evidence is behavioral/completeness evidence only.

The complete reference-destination query returned 25 typed destinations. They are accounted for by the 24 groups, but legacy login/create/verification/reset/update-password destinations and the generic WebView destination do not become Gürbakır screens. The authoritative local evidence paths are the query rows under the private reference corpus and the prepared reports named in [Phase 3 Product Decisions](PHASE-3-PRODUCT-DECISIONS.md#reference-application-coverage-audit). Confidence is `FACT` for exact serialized destinations and parameters; the complete runtime graph remains `UNKNOWN`.

## Product decisions finalized by P3-00

- Conditional onboarding is not required for P3-01. Use the Android/system app language when it is `tr` or `en`; use the Turkish resource baseline for unsupported locales. Market is a separate merchant configuration. Add a minimal versioned choice later only if an approved multi-market rule proves inference unsafe.
- Search history uses the reversible default of the last 10 unique normalized queries for 30 days. It is device-local, clearable, disableable, excluded from analytics, and partitioned by environment/market.
- Wishlist is complete but local-only and account-independent. Ordinary logout does not delete it. UI copy must not imply account or cross-device sync.
- Cart notes are out of Phase 3. No note field is shown or sent unless a later approved product decision defines purpose, bounds, privacy, lifecycle, and merchant handling.
- Cart ownership is `Anonymous`, `CustomerAssociated`, `DetachPending`, or `Quarantined`. Only a proven anonymous cart can remain visible after logout or terminal expiry.
- No app-owned password, registration, verification, recovery, or password-update screen exists. The hosted Customer Account journey owns those interactions; the app owns launch, return, cancellation, help, restart, session, and safe route reset.
- No production push, Analytics, or Crashlytics capability is introduced. Remote Config stays non-blocking by default.
- Account deletion and legal/support content remain real external release blockers. Placeholders cannot satisfy them.

## All-feature reconciliation

The CSV companion is the row-complete record. This table is the review index.

| IDs | Final status | Gürbakır disposition | Phase 2 reuse | Owning slices |
|---|---|---|---|---|
| 01, 03, 04, 05, 07 | `ACCEPTED` | Startup and catalog discovery are independently designed around owned Shopify data and explicit state recovery. | Typed configuration/navigation/theme, Storefront gateway and errors | P3-01 to P3-04, P3-14 to P3-16 |
| 09, 10, 22 | `ACCEPTED` | Complete four-state cart ownership and exact Checkout Kit lifecycle; no cart note. | Cart gateway/coordinator/Keystore and Checkout Kit adapter/proofs | P3-06, P3-07, P3-09, P3-15, P3-16 |
| 14, 15, 16 | `ACCEPTED` | Approved Customer Account fields, market-aware addresses, and verified order/fulfillment data only. | Customer Account OAuth/session/client and external-route policy | P3-10 to P3-12, P3-15 |
| 02, 06, 08, 24 | `INTENTIONALLY_DIFFERENT` | Conditional-no-op onboarding, bounded local search history, local wishlist, and owned TR/EN design system. | Locale/brand contracts, DataStore/Room boundaries, product identifiers | P3-00 to P3-05, P3-15, P3-16 |
| 11, 12, 19, 21 | `INTENTIONALLY_DIFFERENT` | Hosted passwordless account journeys, allowlisted external pages, and safe non-blocking config. | OAuth/PKCE/session, route policy, Remote Config adapter | P3-08, P3-09, P3-14 to P3-16 |
| 20, 23 | `DEFERRED` | Production push and telemetry remain absent. | Disabled/consent and redaction boundaries only | P3-16 verifies absence; otherwise beyond Phase 3 |
| 17, 18 | `EXTERNALLY_BLOCKED` | Functional deletion process and real legal/support corpus are required; placeholders are forbidden. | Re-auth, local-clear, typed URL and localization contracts | P3-08, P3-13, P3-16 |
| 13 | `NOT_APPLICABLE` | No legacy password feature; help/restart belongs to account access. | Hosted OAuth journey and support route | P3-09 |

## P3-01 through P3-16 execution contracts

Every slice is a clean local checkpoint. Its permitted scope includes only the named feature source, tests, resources, schemas, and documentation needed for that outcome; it excludes later destinations and fake controls. Each slice updates its acceptance rows, screen/flow records, evidence, and handoff. A failed entry criterion is a stop condition, not permission to improvise.

| Slice | Exact outcome and permitted scope | Prerequisites and external inputs | Required proof | Stop condition |
|---|---|---|---|---|
| P3-01 | Production shell, deterministic startup, real Home, and only functional destinations; app/navigation/Home code and owned resources only | P3-00 PASS; merchant Home packet; supported market/currency rule; approved text wordmark/token baseline | Reducer/repository/fixture tests; Compose loading/partial/empty/offline/navigation/large-text; process restoration; focused device visual/TalkBack | Stop before code if the merchant packet is absent; never expose cart or later placeholders |
| P3-02 | Categories, listings, supported sort/filter, pagination and deep-link resolution | P3-01; owned taxonomy; filters/sorts; current Storefront schema; cache policy | Pagination/cancellation/deduplication, schema fixtures, Compose and route tests | Stop on ambiguous taxonomy or unsupported controls |
| P3-03 | Debounced search and bounded local history | Stable listing; accepted Room schema/migration and SR-08 rules | Virtual-time cancellation; migration/expiry/uniqueness/clear tests; keyboard and restoration UI tests | Stop before persistence without version/migration/export and deletion rules |
| P3-04 | Product detail, media and deterministic valid-variant selection | Stable listing; media/content/related-source policy; current schema | Variant-combination property tests; mapper/media/UI/process tests | Stop if variant, price, inventory, or source semantics are unverified |
| P3-05 | Durable local-only wishlist and unavailable-item recovery | Stable product identity; Room migration; local-only copy | Idempotency, migration, rehydration, offline and process-death UI tests | Stop if any sync/account promise or destructive retained-data migration appears |
| P3-06 | First production cart action/destination with complete mutations and four-state ownership | Product intent; market/price rules; no-note decision; reconciliation/detachment/quarantine contract | Ownership/state-machine, concurrency, API failure, invalid-cart, UI and process tests | Stop if associated ownership can be relabeled anonymous or cart UI is partial |
| P3-07 | Refreshed eligible cart to Checkout Kit and exact completion/cancel/failure return | P3-06; checkout eligibility, identity and URL policy | Changed adapter mappings only; coordinator/UI/lifecycle/offsite-return tests; device proof only for changed load-bearing path | Stop on guessed completion, unsafe host, payment data storage, or test affordance |
| P3-08 | Real versioned privacy/terms/support routes and safe external-page baseline | Owned content, owner, canonical URL, effective date, offline/update policy | URL/redirect/provenance/cache/accessibility tests against controlled owned routes | Stop and keep Account hidden while any mandatory source is absent |
| P3-09 | Signed-out/signed-in Account, hosted OAuth journey, restore/refresh/logout/expiry and cart reconciliation | P3-08; minimum fields/scopes; hosted copy; route matrix; market rule; cleanup process | State, callback/replay/deep-link, process-death, session and cart tests; live OAuth only if identity/contract changed | Stop if legal routes, callback identity, scopes, or cart policy are unresolved |
| P3-10 | Read/edit approved profile fields only | P3-09; current schema and field/privacy allowlist | Validation, mapper, conflict/session, no-PII-persistence and UI tests | Stop on any unapproved birthdate/metafield or scope expansion |
| P3-11 | Market-aware address list/add/edit/delete/default | P3-09; supported countries and address policy | Country property tests, CRUD/conflict/session fixtures, form accessibility and restoration | Stop if supported-country rules or PII lifecycle are missing |
| P3-12 | Private order list/detail, fulfillment and safe tracking handoff | P3-09; schema; status vocabulary; carrier allowlist; support fallback | Pagination/partial-fulfillment/session/URL/UI tests | Stop if status, carrier, ownership, or tracking data would be invented |
| P3-13 | Functional deletion-request path and honest local/remote outcomes | Owned resource/process/operator, re-auth, retention, support/SLA and affected-data inventory | Owned contract/URL, re-auth, SR-08 local-clear and acknowledgement proof | Remain documentation-only `EXTERNALLY_BLOCKED` until every external criterion exists |
| P3-14 | Optional update messaging and bounded flags with non-blocking failure | P3-08 critical routes; flag/version owners; cadence/expiry; rollback; hard-gate decision | Parsing/default/cache/offline/malformed/rollback/UI/process tests | Stop any hard gate without grace, rollback, release owner and critical-route proof |
| P3-15 | Integrated acceptance and evidence-driven hardening only | Intended slices complete or explicitly blocked/deferred; migration inventory current | Full focused format/static/unit/UI/instrumentation, critical TalkBack, process/offline/performance, migration and security regression | Stop scope expansion; no high-severity functional/accessibility/security defect may remain |
| P3-16 | Auditable release candidate and secure release process | Final brand/legal/deletion, production identity/signing/Play/services/App Links/Data Safety/support/release owners | Release build/signing/config, migration/rollback, policy, secrets/dependencies/licenses/SBOM, final a11y/performance and release-graph exclusion | Stop release for any open group 17/18 blocker, proof UI, synthetic data, deferred SDK, or missing rollback |

## P3-01 readiness decision

P3-01 is **not yet executable**. The technical/design specification is ready, but the project owner/merchant must provide one approved input packet:

1. supported Phase 3 market(s), storefront default market, currency display and whether the user may switch market;
2. Home section order and source for each section (for example owned collection handle or explicit product query), Turkish and English section labels, and behavior when a source is empty;
3. confirmation that only project-owned Shopify product media may appear and that the existing text wordmark plus P3-00 token baseline is acceptable until final release assets arrive.

This is an exact external input blocker, not a planning defect. Once supplied, a fresh task can implement P3-01 without reopening P3-00 or repeating Phase 2 proof.

## Gate conclusion

P3-00 passes because scope, screens, flows, design, content/asset gaps, data ownership, proof replacement, and every later slice contract are complete and traceable. P3-01 remains blocked only on the packet above. No production source, resource, manifest, schema, dependency, or Gradle change is authorized by this result.
