# P3-16 Production and Release Readiness Entry Handoff

Date: 2026-08-13 (Europe/Istanbul)

> **Operational ownership correction (2026-09-26):** The [current operating contract](../operations/PRIVACY-RELEASE-OPERATING-CONTRACT.md) now assigns merchant Shopify policy, first-line support and deletion/privacy processes to the merchant side; Android engineering and technical remediation to the app provider; and Play/signing/Firebase/domain association/Play submission operations by deployment model. Gürbakır uses the existing project-managed Play account and project-managed signing/release operation, while merchant Shopify/support/privacy/domain ownership remains distinct. These are accountable functions, not invented named staff. The owner has confirmed this personal Play account was created after 2023-11-13; the corresponding new-app testing rule applies unless account-specific UI contradicts it. The dated audit and the earlier 2026-09-26 notes below retain their historical wording and do not override this current correction. P3-16 remains **NOT STARTED** at this documentation checkpoint.

> **Pre-entry governance reconciliation (2026-09-26):** The [privacy and release operating contract](../operations/PRIVACY-RELEASE-OPERATING-CONTRACT.md) now decides Shopify-hosted controls with intentional Android `visitorConsent` omission for the observed Türkiye-only model, and defines deletion/support, release, incident, recovery, signing-custody and evidence roles. This removes the consent-model decision and missing role *contract* as pre-entry design gaps. Actual accountable privacy/support/release/incident/recovery assignments, permanent production identity/signing/provider decisions and provisioning remain before P3-16 entry under the roadmap; final Data Safety, pixel/server-path, artifact, Play, callback/App Links and signed-candidate proof remain P3-16/action-time items. Signed-in Play readback did not expose the account's creation date, so new-app tester-rule applicability needs further account-specific evidence before Play app creation. P3-16 is **NOT STARTED**. Do not use the historical table below as a present-state claim that the Shopify handoff or privacy-model decision is still missing.

> **Current evidence reconciliation (2026-09-26):** This dated entry audit and its table below preserve what was known on 2026-08-13. The later [account-deletion continuation](PRE-P3-16-ACCOUNT-DELETION-ROUTE-AND-PROCESS.md) verifies the dedicated Android/public request route, merchant intake and an accepted Shopify personal-data-erasure handoff on a no-order synthetic customer. Its final redaction remains unobserved under Shopify's asynchronous lifecycle, but the scheduled processing date is **not** a P3-16 start or engineering wait gate. The remaining deletion-related release inputs are permanent operator/case ownership, processor and retention review, response and notice rules, support escalation and truthful Data Safety/privacy disclosures. Production identity, signing, Play, service bindings and App Links remain separate missing inputs. P3-16 remains **NOT STARTED**; the historical `EXTERNALLY_BLOCKED` row below does not mean the Shopify handoff is still unverified.

Status: **ENTRY BLOCKED / P3-16 NOT STARTED**

Branch: `main`

Starting checkpoint: `29bc104` (`docs(phase3): record P3-15 integrated acceptance`)

Last implementation checkpoint: `f338ded` (`feat(app): harden integrated production boundary`)

This handoff records the P3-16 entry audit after P3-15 passed. It does not claim P3-16 completion and does not turn an unsigned development artifact into a production release candidate.

All independently implementable functional application slices through P3-15 are complete and integrated. P3-16 cannot start its production/release work because its mandatory entry inputs are not provisioned, feature group 17 remains externally blocked, and the active owner policy keeps the separate P3-16 release/security campaign outside the current functional implementation run. The audit therefore stops at the safe boundary without inventing production identity, signing, store ownership, legal declarations, deletion outcomes, or release operators.

## Governing boundary

The P3-16 roadmap requires all of the following before production/release work begins:

- final brand/legal/deletion state;
- production package/application identity;
- production signing and Play ownership;
- production Shopify/Firebase configuration;
- verified App Links and Customer Account callbacks;
- Data Safety/privacy declarations;
- support and release owners;
- dependency/schema/release and rollback policy.

The active owner policy permits autonomous development/staging configuration but requires confirmation at the point of using or replacing production signing private keys or publishing to a public store. It also explicitly excludes a separate P3-16 release/security/obfuscation/publication/rollout campaign from the current functional application run. No signing key or public-store operation was attempted.

## Entry audit

| Required input | Current evidence | Status |
|---|---|---|
| Production package/application identity | `app/build.gradle.kts` defines only `development` and `staging`. `defaultConfig` is `com.gurbakir.mobile.unconfigured`; the current release artifact is `com.gurbakir.mobile.dev`. | **MISSING** |
| Version policy | Current artifact is version code `1`, version name `0.1.0`; no production channel/version/rollout owner is recorded. | **MISSING** |
| Production signing | No `signingConfigs`, `storeFile`, alias, or release signing binding exists. `apksigner verify` reports `DOES NOT VERIFY` / missing signature manifest. | **MISSING** |
| Play ownership/listing | Read-only request to `https://play.google.com/store/apps/details?id=com.gurbakir.mobile` returned HTTP 404 on 2026-08-13. No repository Play ownership/listing record exists. | **MISSING / EXTERNAL** |
| Verified App Links | Production manifest uses `autoVerify="false"`. Read-only request to `https://gurbakir.com/.well-known/assetlinks.json` returned HTTP 200 with the empty JSON array `[]`. | **MISSING / EXTERNAL** |
| Production Firebase | Exactly four ignored development/staging files are configured. No production Firebase flavor/file/app registration is represented. | **MISSING / EXTERNAL** |
| Production Shopify/Customer Account callbacks | The ignored configuration is explicitly the verified non-production environment. No production flavor/callback identity is defined. | **MISSING / EXTERNAL** |
| Final remote deletion process | P3-13 mobile guidance/local-clear boundary is complete, but merchant acknowledgement, SLA, retention execution, and actual remote deletion remain unobserved. | **EXTERNALLY BLOCKED** |
| Legal/support baseline | P3-08 `gurbakir-legal-baseline-1` is functional and accepted for the application. Final store/release declarations and accountable publication owners are not recorded. | **FUNCTIONAL BASELINE COMPLETE; RELEASE OWNER MISSING** |
| Data Safety/privacy declarations | No approved Play Data Safety response set, SDK/data-purpose/retention disclosure owner, or publication record exists. | **MISSING / EXTERNAL** |
| Push and telemetry decision | Feature groups 20 and 23 remain deferred. The P3-15 release graph correctly excludes Messaging, Analytics, and Crashlytics. | **DEFERRED; ABSENCE PROVEN** |
| Hard update gate | Product decision remains no hard gate. P3-14 non-blocking policy is complete. | **INTENTIONALLY ABSENT** |
| Release support/incident/rollback owners | No accountable production release operator, support escalation owner, staged rollout rule, incident rule, or signed-artifact rollback procedure is recorded. | **MISSING / EXTERNAL** |
| Database migration | Room schemas 1 and 2 are exported; explicit `1 -> 2` physical migration passed in P3-15. | **PASS FOR CURRENT APP** |
| Proof/synthetic release exclusion | P3-15 minified release manifest/Dex/resource inspection found no proof UI/controllers, synthetic copy, Messaging, Analytics, or Crashlytics. | **PASS FOR CURRENT DEVELOPMENT RELEASE GRAPH** |
| Dependency locks and secrets | All six modules retain lockfiles and verification metadata; P3-15 format/static/test/build/Gitleaks gates pass. | **PASS FOR CURRENT CHECKPOINT** |

## Safe independent work completed

- Confirmed the P3-15 implementation and documentation commits are present on `main` with a clean worktree and empty stash before this handoff edit.
- Re-read the P3-16 roadmap entry and active owner approval boundaries.
- Inspected build variants, application IDs, version metadata, signing configuration, Firebase variant contract, main manifest App Links, public association file, public Play package URL, current APK signature/package metadata, schema exports, and existing release-readiness references.
- Confirmed the five required development/staging local configuration files remain present, ignored, and untracked without printing credential values.
- Reused the current P3-15 release-graph, secret, dependency-lock, migration, accessibility, localization, performance, deep-link, JVM, device, and APK evidence. No unchanged foundation proof or full Gradle/device matrix was repeated merely for reassurance.
- Kept feature groups 20 and 23 absent and the hard update gate unimplemented as required.

## Validation and claim status

| Check | Result |
|---|---|
| P3-15 integrated acceptance handoff/commit | **PASS / PRESENT** |
| Current Git branch and clean checkpoint before documentation | **PASS** (`main`, `29bc104`) |
| ADB device availability | **PASS** (`R68RC006LPE`, authorized) |
| Current release APK package/version | **OBSERVED**: `com.gurbakir.mobile.dev`, `1`, `0.1.0` |
| Current release APK signature | **FAIL AS PRODUCTION ARTIFACT / EXPECTED**: unsigned, `apksigner` exit 1 |
| Public asset association | **FAIL AS APP-LINKS ENTRY**: HTTP 200 but `[]` |
| Public Play package lookup | **FAIL AS STORE-OWNERSHIP ENTRY**: HTTP 404 |
| Production flavor/Firebase/signing configuration | **NOT PRESENT** |
| New Gradle/build/device matrix | **NOT RUN**; P3-15 evidence is current and no code/config changed |
| Production publication/signing/store mutation | **NOT RUN / NOT AUTHORIZED WITHOUT POINT-OF-ACTION CONFIRMATION** |
| Shopify/Firebase/customer/order/payment/deletion mutation | **NOT RUN** |

The HTTP results are current observations, not proof of permanent non-ownership. A future release task must revalidate them after the production package and signing certificate are intentionally chosen.

## External state

- Shopify Admin/storefront configuration and data: **UNCHANGED**.
- Firebase projects/apps/Remote Config: **UNCHANGED**.
- Customer Account registration/callbacks/data: **UNCHANGED**.
- Play Console/listing: **UNCHANGED**; no authenticated console action occurred.
- Domain/`assetlinks.json`: **UNCHANGED**; public read-only request only.
- Signing material: **NOT ACCESSED, CREATED, COPIED, OR USED**.
- Customers, accounts, profiles, addresses, carts, orders, payments, deletion requests, and synthetic remote records: **UNCHANGED**.
- Physical device: no additional APK install/test was required for the entry audit; it remained connected and authorized.
- Browser/Edge sessions: **UNCHANGED**; public status checks used non-authenticated read-only HTTP requests.

## Exact resume inputs and procedure

P3-16 must resume as a separately authorized release-readiness task only after the owner supplies or designates the missing accountable inputs. Credential values and private keys must use approved ignored/secure mechanisms and must never be pasted into documentation or chat.

Required decisions/provisioning:

1. Permanent production Android application ID and responsible Play Console application owner.
2. Upload key / Play App Signing strategy, signing owner, secure key location/provider, rotation/recovery policy, and explicit point-of-action approval before a private key is used or replaced.
3. Production release channel, initial version code/name, rollout owner, support contact, rollback threshold, and incident owner.
4. Production Shopify Storefront and Customer Account mobile-client registration, exact callback/logout/App Links identities, scopes, and accountable configuration owner.
5. Production Firebase project/app decision, or an explicit decision that production Firebase is absent; groups 20 and 23 remain deferred unless separately authorized.
6. Domain owner deployment of non-empty `assetlinks.json` containing the chosen package and the correct Play/App Signing certificate fingerprints.
7. Approved Play Data Safety/privacy disclosure responses tied to the actual production SDK/data inventory.
8. Account-deletion operator/process capable of observable acknowledgement, response SLA, retention exceptions, and completed remote execution; this is the unresolved feature group 17 blocker.
9. Final legal/support publication owner and confirmation that the P3-08 baseline is the release corpus or a replacement version.

Ordered resume procedure:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, `docs/phase3/P3-15-HANDOFF.md`, this handoff, the P3-16 roadmap entry, and the acceptance matrix.
2. Verify `main` contains `f338ded`, `29bc104`, and the documentation commit containing this handoff; verify a clean worktree, empty stash, ignored local configuration, and no active duplicate build.
3. Reconcile the nine owner/provisioned inputs above without recording secrets.
4. Create the production flavor/identity and secure signing wiring only after identity and key ownership are authoritative. Keep development/staging behavior unchanged.
5. Provision production Shopify/Firebase/client callbacks and App Links through project-owned consoles/domain state; validate exact package/certificate association and OAuth return behavior.
6. Produce approved Data Safety/privacy/release/support/rollback records from the actual artifact and service configuration.
7. Run focused configuration/signing/App Links/Customer Account checks first, then the required release build, dependency/license/SBOM, migration/rollback, release-graph exclusion, final accessibility/performance, and signed-device smoke lanes.
8. Do not publish publicly, use/replace a production signing private key, charge a payment method, place a real order, or mutate real customer data without the point-of-action approval required by owner policy.
9. P3-16 may be marked complete only when the signed production candidate, identities, owned external associations, disclosures, deletion process, support/release/rollback ownership, and final evidence all pass. A locally built development APK is not a substitute.

## Current testable artifact

For ongoing application testing, use:

`app/build/outputs/apk/development/debug/app-development-debug.apk`

- Size: 20,176,991 bytes
- SHA-256: `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E`

The matching instrumentation artifact and the minified unsigned artifact remain recorded in `P3-15-HANDOFF.md`. None is a signed production release.

## Stop boundary

The current functional implementation run stops here because meaningful independent application work is complete, P3-16's mandatory production inputs are external or owner-controlled, feature group 17 remains externally blocked, and the active authorization excludes starting a separate release/security/publication campaign. Phase 3 as a production release is **not complete** and is not labelled blocked by local code quality; it is waiting at an explicit release-entry boundary.
