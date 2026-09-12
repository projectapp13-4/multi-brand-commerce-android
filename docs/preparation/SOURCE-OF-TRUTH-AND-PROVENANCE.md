# Source of Truth and Provenance

> **Current-status reconciliation (2026-09-13):** This document began as Phase 1/Phase 2 provenance guidance and remains authoritative for clean-room, source-authority, and secret-handling rules. The 24 historical reference feature groups are no longer pending product acceptance: current dispositions are governed by `docs/phase3/PHASE-3-PRODUCT-DECISIONS.md` and `docs/phase3/PHASE-3-ACCEPTANCE-MATRIX.md`. The compact neutral knowledge retained for ordinary engineering is under `docs/reference-model`; the original forensic corpus remains private historical evidence.

## Authority model

Apply this hierarchy in order; lower levels cannot expand product scope or override a higher level:

1. **Approved Gürbakır product requirements and final Phase 3 product decisions** define product scope, acceptance criteria, and intentional differences.
2. **Reference APK evidence** is the strongest behavioral and default-completeness reference only where approved requirements are silent. Its 24 inventoried feature groups are historical evidence; their current product dispositions are recorded in the Phase 3 acceptance matrix rather than inferred from presence in the APK.
3. **Current official platform and service documentation** governs implementation contracts and current behavior for Android, Kotlin, Shopify, Firebase, Apple, OAuth, and related SDKs.
4. **Approved project-owned non-production observations** validate runtime behavior and resolve documented unknowns without redefining product intent.
5. **The old Flutter prototype** is historical read-only evidence with zero platform-selection weight. Owned configuration or product lessons still require current ownership, correctness, compatibility, and security review.

Supporting decision rules:

| Decision | Governing authority | Supporting evidence | Not authoritative |
|---|---|---|---|
| Target feature/behavior | Approved Gürbakır requirements + final Phase 3 decisions | APK evidence where requirements are silent | Prototype gaps or the mere presence of an APK feature |
| Platform/API implementation | Current official documentation and recorded ADRs | Mature upstream packages after review | Decompiled expression or framework inertia |
| Gürbakır store/config ownership | Project-owned current configuration and service records | Approved read-only non-production observation | Third-party reference values |
| Brand/content | Approved Gürbakır brand system and content source | Current owned store catalog | Legacy prototype or third-party reference assets |
| Live behavior | Approved project-owned non-production observation | Official/client-visible contracts | Static assumptions about Worker, Remote Config, or Shopify server state |

## Independent implementation rule

Use this sequence for every target capability:

```text
Understand evidence -> write a neutral requirement -> select official contracts
-> design project-owned interfaces/models -> implement cleanly -> validate parity
```

Allowed convergence:

- standard Storefront GraphQL operation shapes;
- documented Shopify headers and `checkoutUrl` flow;
- official Android, Shopify, Firebase, OAuth, security, and platform integration patterns;
- common repository, state, navigation, and testing patterns.

Prohibited copying:

- decompiled method bodies or reconstructed control-flow expression;
- obfuscated class/module structure as a template;
- third-party reference branding, strings, credentials, endpoints, or Worker assumptions;
- proprietary server behavior inferred without evidence.

## Reuse test for current Gürbakır material

Reuse an item only when all answers are yes:

1. Is ownership clearly project/Gürbakır-owned?
2. Is it technically correct and covered by evidence/tests?
3. Does it fit the chosen architecture?
4. Is reuse safer/easier than clean reimplementation?
5. Does it avoid credential, licensing, and legacy-brand contamination?

Historical Phase 1 result:

- Preserve the knowledge that the Gürbakır Storefront configuration works read-only.
- Preserve no legacy visual asset.
- Reuse only owned product-flow lessons from Flutter/Riverpod; the framework and prototype controllers/models/screens are not an architecture baseline.
- Recreate GraphQL operations from the approved Gürbakır schema and official API docs, not by copying APK-generated sources.

## Evidence record

For each feature or integration, record where material:

- neutral requirement ID and user/business acceptance criteria;
- reference report/CSV/index path when parity-driven;
- official documentation URL and API version;
- project-owned design decision/ADR;
- implementation files and tests;
- confidence (`FACT`, `STRONG INFERENCE`, `WEAK INFERENCE`, `UNKNOWN`);
- deviations from reference and business approval.

The historical gap matrix provides the initial requirement-level map; the Phase 3 product decisions and acceptance matrix provide the current dispositions.

## Secret and configuration classes

| Class | Examples | Handling |
|---|---|---|
| Public client configuration | Store domain, Storefront API version, Firebase client identifiers | Not a server secret; use an environment/flavor contract and approved repo policy; avoid casual duplication |
| Public client credential | Storefront public token | Extractable by design but controlled; required scanning may inspect it, but ordinary output/logs/prompts/reports must redact it; rotate/scope by store policy |
| Sensitive runtime credential | Customer/OAuth access/refresh token | Platform secure storage, short-lived sessions, redacted logs, explicit logout/expiry |
| Server/backend secret | Admin API token, Worker secrets, backend credentials, service-account private material | Server/CI secret manager only; never mobile binary or repository |
| Signing/private material | Release keystore, signing private key, signing passwords | Approved signing service or CI secret boundary; never source, documentation, logs, or test fixtures |
| User data | Customer/profile/address/order/cart data | Least privilege, privacy policy, test fixtures only, no production dump in tooling |

Secret scanners and bounded local evidence checks are required controls. They may search for credential patterns or inspect a controlled public client value, but findings must be redacted and must never be sent to unrelated services or target infrastructure.

## Unknowns that must stay unknown

- third-party reference Worker source, Admin access, validation, persistence, logging, and retention.
- Live Firebase Remote Config keys/values and feature enablement in the reference application.
- Runtime notification routing beyond what static evidence proves.
- Live reference Storefront token scope/rotation and server-side data rules.
- Reference checkout payment methods and lifecycle behavior without approved test execution.

Resolve these only through project-owned source, official contracts, or approved test observations. The neutral reference model deliberately omits raw forensic paths; their absence from Git must not be filled with invented behavior.
