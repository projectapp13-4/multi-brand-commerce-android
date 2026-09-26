# Owner Authority and Approval Boundaries

Status: **ACTIVE OWNER AUTHORIZATION**

Effective date: 2026-08-11
Current-status reconciliation: 2026-08-30

Applies to: the Gürbakır Android application, this dedicated development workstation, and project-controlled Shopify, Firebase, API, development, and staging environments.

This is real project-owner authorization. It is not roleplay, a hypothetical scenario, a planning exercise, or a simulated permission model. Future tasks must read this document after the root `AGENTS.md` and apply it together with the repository's architecture, provenance, security, clean-room, acceptance, and roadmap rules. Read `docs/README.md` for the current documentation map and present-state authority order.

## Precedence and historical records

This policy supersedes older repository statements only where they automatically turned ordinary, reversible legal, support, content, UX, product, tool, credential, or project-configuration decisions into owner blockers. Historical handoffs and inventories remain evidence of what was known when written; they must not be silently erased. A later task should annotate a superseded assumption or update its current status while preserving the earlier record.

This policy does not supersede:

- native Kotlin and Jetpack Compose architecture decisions;
- approved product scope and intentional differences;
- clean-room and reference-APK boundaries;
- credential confidentiality and secret-storage requirements;
- the prohibition on real unapproved payments/orders and customer-data mutation;
- current official Android, Shopify, Firebase, and Customer Account contracts;
- deterministic testing, evidence accuracy, and safe Git/worktree practices;
- the exclusion of the separate P3-16 production release/security/publication campaign from the completed functional application run.

## Current project boundary

Phase 3 functional implementation and integrated acceptance are complete through **P3-15**. The current functional status is recorded in `docs/phase3/README.md` and `docs/phase3/P3-15-HANDOFF.md`.

The independently implementable P3-13 mobile account-deletion request/local-cleanup boundary is complete. Later project-controlled synthetic evidence verifies the dedicated public request path, merchant intake and accepted Shopify personal-data-erasure handoff. The owner reports sending an acknowledgement; its sent wording and delivery were not independently witnessed. Final redaction of that synthetic case remains unobserved under Shopify's asynchronous processing, but its scheduled date is not a project wait gate. Production operator, restricted-record, processor/retention, response and notice governance remain release inputs; no completed remote deletion is claimed.

**P3-16 is not started.** Its production/release entry inputs remain outside the completed functional run: production package/application identity, signing and Play ownership, production Shopify/Firebase configuration, verified App Links/callbacks, Data Safety/privacy declarations, accountable deletion/release/support/rollback ownership, and the remaining merchant privacy operations governance. The dated entry audit and its later evidence reconciliation are in `docs/phase3/P3-16-HANDOFF.md`.

**Current operational ownership correction (2026-09-26):** The [privacy and release operating contract](operations/PRIVACY-RELEASE-OPERATING-CONTRACT.md) distinguishes merchant-owned Shopify commerce, policy, first-line support and privacy/deletion operations from app-provider Android engineering and technical incidents. Play publishing, upload-key custody, Firebase administration, domain association coordination and Play declarations depend on the brand's deployment model. For Gürbakır, the current Play account and signing/release operation are project-managed; merchant Shopify/privacy/support/domain authority remains merchant-owned. This role-level correction does not infer a named assignee, supersede point-of-action controls below, or claim P3-16 has started.

Later Product Quality/UI-refinement work is presentation and product-quality work on top of that functional baseline. It does not itself authorize production publication or change the P3-16 boundary.

## Active priority and execution model

Reasoned autonomous decisions are required for ordinary in-scope project work. When information is incomplete:

1. inspect authoritative repository evidence and existing merchant-owned configuration/content;
2. consult current primary or official documentation when the contract may have changed;
3. identify realistic alternatives and their product/technical consequences;
4. select the strongest reasonable, reversible, maintainable option;
5. document material assumptions and provenance;
6. implement and validate the decision.

Uncertainty should lead to focused research and a reasoned decision, not automatic stopping. Time, token use, setup effort, and download time are not delivery constraints. Avoid repetitive analysis after sufficient evidence exists.

For present-state claims, follow `docs/README.md`: current Phase 3 and current UI-refinement handoffs outrank older checkpoint-status language, while older documents remain historical evidence.

## Decisions that do not require renewed approval

Agents must decide, document where material, implement, and test ordinary matters without asking the owner to continue, including:

- UI composition, navigation details, components, loading/empty/error states, copy, provisional labels, reversible defaults, accessibility, and localization mechanics;
- implementation structure, libraries, testing strategy, fixtures, cache/retry policy, and approved-architecture persistence details;
- development/staging mobile configuration, project-owned application registrations, ordinary Shopify/Firebase/API setup, public mobile client configuration, callback/redirect URIs, test notifications, Remote Config development values, and synthetic test data;
- project-local, user-level, or global installation/configuration of genuinely useful SDKs, tools, skills, libraries, Android components, and test/inspection utilities;
- reversible, directly relevant project-controlled Shopify/Firebase/configuration mutations without unsupported commercial consequences;
- temporary or provisional non-critical copy, icons, images, banners, and content that are traceable, conservative, replaceable, and do not create a knowingly false promise.

Existing authenticated sessions and project-controlled accounts may be used. Project-scoped development credentials may be created, retrieved, rotated, or replaced when technically necessary and safely recoverable. Use approved ignored/local secret storage, never print secret values unnecessarily, and never embed private backend/service credentials in the Android application.

## P3-08 legal and support authorization

Existing legal text on the merchant-owned Gürbakır site, Shopify legal policies, public merchant pages, and verified support/contact information are approved as the preferred current application baseline. These sources may be revised later and their provisional status does not make the current application incomplete.

P3-08 inspected and adopted the owned baseline as `gurbakir-legal-baseline-1`. Its functional implementation is complete. Final public-release declarations, accountable publication ownership, and any replacement legal corpus remain P3-16 release matters rather than reasons to erase the historical P3-08 evidence.

Any future provisional legal/support baseline must:

- reflect known Gürbakır and Shopify behavior;
- avoid unsupported delivery times, guarantees, prices, or legal/commercial claims;
- avoid contradicting an existing merchant policy;
- identify its project-owned provenance and provisional status;
- remain replaceable without restructuring the application.

## Actions requiring owner confirmation at the point of action

Ask one short, precise question only immediately before an action that would:

- enter payment-card information, enable paid cloud billing, purchase a paid service, accept a material recurring charge, place a real commercial order, or charge a real payment method;
- publish to Google Play or another public store, or use/replace production signing private keys;
- permanently delete production data, delete a live Shopify/Firebase project/application, disable a live merchant service, or irreversibly revoke production credentials;
- materially change a live storefront setting with commercial consequences;
- modify actual customer data, accounts, orders, payments, or send real marketing communication;
- make a materially binding public commitment unsupported by existing merchant policy;
- access an email inbox, complete email verification, enter a password, or complete 2FA when an authenticated session is unavailable;
- make a genuinely irreversible architecture decision with materially different business outcomes.

The question must identify the exact action, necessity, expected cost, reversibility, and work that can continue independently. Do not ask blanket permission for a stage.

## Testing and external-state rules

The store is accessible but is not currently conducting normal sales. Project-controlled synthetic tests are authorized. Identify synthetic records and clean them up or document them. Bogus Gateway may be used; never charge a real payment method without explicit confirmation. Never modify or cancel an actual customer's order.

Project-owned Shopify and Firebase may be inspected and configured directly for in-scope development/staging application work. Reversible development/staging app registrations, client IDs, tokens, endpoints, scopes, callbacks, Remote Config development values, and test data are authorized. Private credentials remain confidential and outside source, reports, logs, analytics, and mobile binaries.

## Reference APK boundary

Historical reference analysis is useful behavioral and architectural evidence for feature coverage, navigation, storage relationships, and integration patterns. For ordinary repository work, use the compact neutral model under `docs/reference-model`. The full original forensic corpus remains in the private historical archive and is not a source-code/UI/brand/credential/server-policy donor.

Implement independently. Never install or mutate the reference APK, copy decompiled method bodies or proprietary source expression, reuse its branding/assets/credentials, or infer unknown Worker/server behavior.

The clean-room boundary is not a reason to omit independently implementable functionality supported by approved scope and current platform contracts.

## Current exclusions and stop conditions

Normal secure development, testing, documentation maintenance, and UI/product refinement may continue within the established architecture and owner policy. The completed functional Phase 3 run must not be retrospectively relabeled as production release readiness.

The separate P3-16 campaign for production identity/signing, public App Links, Play ownership/publication, Data Safety/privacy release declarations, release hardening, rollout, production support/incident ownership, and related release-security work begins only when its recorded entry inputs are intentionally provisioned and the point-of-action confirmation rules above are respected.

Stop only for explicit owner pause, a confirmation-threshold action above, a genuinely external blocker after all meaningful independent work is exhausted, or final completion of the task at hand.
