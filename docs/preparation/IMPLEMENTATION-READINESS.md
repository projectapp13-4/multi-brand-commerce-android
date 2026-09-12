# Implementation Readiness

Date: 2026-07-19
Status: **READY FOR A NEWLY AUTHORIZED PHASE 2 NATIVE-ANDROID FOUNDATION PROMPT**

This status does not authorize or claim production implementation. Revised Phase 1 intentionally leaves the workspace without an Android/Flutter application scaffold. It closes the platform decision, evidence, toolchain, and governance needed for a separate Phase 2 prompt.

## Requirement closure

| Preparation requirement | Result |
|---|---|
| Review all initial Phase 1 outputs and validator | Complete |
| Preserve initial Phase 1 in Git before reassessment | Complete: `9c0d9af3c5ab2d81b6fbc3dc563b9adef960d6d1` |
| Apply corrected sunk-cost, setup, iOS, multi-brand and APK assumptions | Complete |
| Freshly research native Android, Flutter and React Native from primary sources | Complete |
| Re-decide architecture from first principles | Complete: native Kotlin + Compose |
| Define current Customer Account API/OAuth/PKCE path | Complete at architecture level |
| Reassess libraries, tooling, Codex skills and MCP | Complete |
| Install/configure clearly useful global tooling | Complete and execution-tested |
| Update affected documents, index, validator and durable rules | Complete |
| Preserve APK/prototype identities and avoid production scaffold | Required by closing validator |

## Reassessment stop-condition audit

| # | Stop condition | Closure |
|---:|---|---|
| 1 | All initial Phase 1 outputs reviewed | Complete |
| 2 | Changed assumptions incorporated | Complete and durable in `AGENTS.md` |
| 3 | Platform options freshly researched | Complete from current primary sources |
| 4 | Functioning reference architecture correctly weighted | Complete as empirical evidence, never source donor |
| 5 | Architecture re-made from first principles | Complete with weighted scorecard |
| 6 | Current official platform/Shopify evidence supports conclusion | Complete; links indexed |
| 7 | Tooling reassessed after platform selection | Complete |
| 8 | Useful tools installed/configured | Complete and execution-tested |
| 9 | Skills/MCP/project infrastructure reassessed | Complete |
| 10 | Affected Phase 1 documents updated consistently | Complete; automated contradiction/link gates added |
| 11 | Durable rules prevent future misinterpretation | Complete in root `AGENTS.md` and ADR |
| 12 | Revised Phase 1 validation passes | Historical close complete: `gurbakir.preparation-validation.v3` PASS is preserved in `evidence/pre-scaffold-validation-v3-2026-07-19.json`; the SDK transition uses v4 |
| 13 | Initial and revised states recoverable in Git | Initial baseline plus final local reassessment commit |
| 14 | No production implementation begun | Complete: zero scaffold target and production source files |
| 15 | Ready for a newly written Phase 2 prompt | Yes, subject to separately listed product/service/device gates |

## Ready assets

- Accepted [ADR-0001](../decisions/ADR-0001-NATIVE-ANDROID-KOTLIN-COMPOSE.md), focused [ADR-0002](../decisions/ADR-0002-NAVIGATION-COMPOSE-2-TYPED-ROUTES.md), corrected [ADR-0003](../decisions/ADR-0003-ANDROID-SDK-BASELINE.md), and a first-principles [reassessment](PHASE-1-ARCHITECTURE-REASSESSMENT.md).
- A native Android architecture with explicit Compose/UI, ViewModel/StateFlow, use-case, repository/data-source, Shopify, Firebase, storage, OAuth, and testing boundaries.
- A validated 24-candidate-feature behavioral inventory pending product acceptance and an immutable APK evidence identity.
- A machine-readable index and matrix, repository-local evidence skill, and revised preparation validator.
- Android Studio/JDK/SDK, Firebase CLI, Gitleaks, and physical-device-first tool strategy.
- Durable rules that prevent Flutter sunk cost, installed-tool convenience, forced shared iOS code, or cross-platform-by-brand assumptions from reappearing.

## Gates before feature acceptance

### Product and identity

- Approve Gurbakir brand tokens/assets, Turkish/localized content, legal pages, analytics consent, notification rationale, and account-deletion policy.
- Confirm intended Shopify Customer Account API capabilities in the owned store. Legacy email/password parity is not required.
- Accept, reject, or intentionally change each of the 24 candidate feature groups through approved requirements; the APK is evidence, not automatic product scope.

### External systems and security

- Provision project-owned non-production Shopify and Firebase environments.
- Define configuration/secrets injection and complete a threat model before auth, backend, checkout, Firebase, or release implementation.
- Fetch/version Storefront and Customer Account schemas without committing client tokens.
- Prove Mobile-client OAuth authorization code + PKCE, redirect handling, rotation, expiry, logout, and Keystore-backed token storage.
- Prove authenticated cart/checkout behavior with official Android Checkout Kit in the owned test store.
- Introduce a project backend only after an ADR assigns ownership, authorization, validation, deletion, privacy, logging, and availability responsibilities.

### Device and release

- Reverify the already accepted Android SDK licenses on the future CI runner.
- Connect an approved physical Android device; no device was connected at Phase 1 close.
- Establish debug/staging/release application IDs, signing boundaries, CI runner, dependency verification, and secret scanning.
- Use the installed stable Android 16/API 36 packages with `compileSdk = 36`, `targetSdk = 36`, and provisional `minSdk = 23`; keep API 37 in a separate preview compatibility lane and confirm every dependency floor/device policy before locking `minSdk`.
- Test Android preview/QPR releases in a separate compatibility lane; do not adopt preview SDKs as the production baseline.
- Complete Android developer identity verification and final package-name/signing-ownership registration before release.
- Provision macOS/Xcode only if/when independent native iOS work is authorized.

## Recommended Phase 2 foundation order

1. Create the native Android Gradle scaffold with wrapper, version catalog, application IDs/build variants, formatting, linting, tests, Gitleaks, dependency verification, and CI.
2. Add classified project configuration, design-token schema, localization, error taxonomy, logging/redaction, Compose theme, Navigation Compose 2 typed routes, startup state, and Hilt boundaries.
3. Add separate Apollo Kotlin Storefront and Customer Account schema/client modules with generated types and deterministic fakes.
4. Complete Customer Account OAuth/PKCE and Keystore-backed session proof, including deep-link/session-expiry/logout tests.
5. Complete a Storefront cart-to-Checkout Kit physical-device proof, including preload, lifecycle, error, privacy, and offsite-return paths.
6. Configure owned Firebase development resources and safe defaults/consent boundaries.
7. Reconfirm the architecture and integration proofs before broad feature implementation.

## Definition of done for future features

- Accepted behavior links to a matrix row, product decision, and evidence source.
- Implementation is independent, typed, and follows the ADR boundaries.
- Loading, empty, error, offline, lifecycle/process-death, accessibility, localization, and privacy states match risk.
- Unit/contract/Compose/instrumentation/device tests include failure paths.
- Defined local/CI scans report zero potential sensitive/server/signing credential literals; public client configuration follows approved repository policy, and controlled public tokens never appear in ordinary output/logs/analytics/reports.
- Native Android is validated; any future iOS parity is a separate explicit acceptance claim.
- Deviations from reference behavior are recorded product decisions.

## Residual risks, not architecture blockers

- Static evidence cannot prove live Shopify, Firebase, Checkout, or Worker behavior.
- The reference Worker's authorization, validation, persistence, and deletion semantics remain unknown.
- Owned Shopify/Firebase environments, brand assets, physical device, and product decisions are still external Phase 2 inputs.
- Customer Account OAuth and authenticated checkout are credible, documented paths but require owned-store/device proof.
- A later iOS product duplicates platform application work; neutral contracts and acceptance tests mitigate drift.

## Rollback and recoverability

- Initial Phase 1 is recoverable at `9c0d9af3c5ab2d81b6fbc3dc563b9adef960d6d1`.
- The original reassessment is recoverable at `feabeb9793f00456e1e36427ae36c21453481b3f`; this correction is preserved in a descendant local commit after all validation passes.
- The prototype and APK/reference bytes remain read-only and are rehashed by the validator.
- Android Studio can be downgraded with the official publisher installer; Gitleaks via WinGet and Firebase CLI via npm can be removed without touching project artifacts.
- There is no production scaffold, dependency lockfile, service mutation, account login, or feature code to unwind.
