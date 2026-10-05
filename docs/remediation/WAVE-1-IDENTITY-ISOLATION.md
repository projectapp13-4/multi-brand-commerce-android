# Wave 1: exact customer/cart isolation

Status: implementation and local verification completed on `codex/remediation-identity`, based on protected main `1f3e3c9f532731ff637da98756c78886b623adca`. This document records the repair contract and pre-merge evidence. Protected PR and exact merged-main acceptance remain pending. It does not select final v3 source or establish production readiness. The [program plan](PRODUCTION-DEFECT-REMEDIATION-PLAN.md) governs dependent waves.

## Contract and ownership

An authenticated cart action must use the exact current Customer Account customer ID and a token from the same leased session. A previous associated cart must prove that exact owner before any buyer-identity write, line mutation or checkout launch. Missing identity, another owner, uncertain protected storage and unresolved ownership remain restricted. Keep the original protected handle so a transient verification problem does not destroy customer work.

`:account` owns protected session replacement, the lease and explicit-session identity reads. `:storefront` owns exact cart buyer mapping, protected ownership state and provider verification/rebind. `:mobile-core` coordinates that contract, presents retained-session recovery and holds the lease through the bounded SDK launch. The lease ends before collecting checkout events; checkout lifetime/finality remains Wave 3.

Shopify documents global IDs across its Admin, Storefront and Customer Account APIs in its [global-ID contract](https://shopify.dev/docs/api/usage/gids). The bridge queries the authenticated Customer Account `customer.id` and compares the actual Storefront `buyerIdentity.customer.id`. It neither derives customer identity from OIDC `sub` nor treats an association Boolean as ownership proof.

The application module still chooses configuration, provider and protected-store identities. Every enrolled real application inherits the fix through these shared modules. There is no package, merchant-name or domain business rule. Disabled Customer Account composition must not construct the lazy identity gateway. Synthetic remains offline/nonproduction and Trial remains development-only.

## Persistence and recovery

Supported v1/v2 cart payloads remain readable. Existing v2 vectors keep their exact bytes when no exact owner is present. New exact-owner payloads use version 3 inside the existing Keystore encryption and retain redacted value wrappers. A legacy associated cart with no stored exact owner must be verified remotely, rather than assumed to belong to the current session.

Write `VERIFY_PENDING` with the expected owner before rebind. A canceled or uncertain rebind retains this restriction. A retry with the same authenticated customer verifies remote truth and handles both before-apply and after-apply ambiguity. A different customer cannot retry the pending cart as its own. Verified mismatch, including a returned different cart ID, quarantines the original handle. Proven expiry/missing-cart controls retain their explicit cleanup behavior. A generic secure cart read failure must preserve uncertainty and the handle.

Authorization exchange must not overwrite an existing protected session. The Account presentation tracks retained durable uncertainty independently of display-name availability: sign-in remains unavailable, while retry and logout remain available without private customer tasks. A clear that applies but returns an unsuccessful receipt still requires recovery; its receipt does not authorize immediate replacement.

## Permanent behavioral regressions and evidence

`CustomerCartOwnershipTest` connects actual session/account/cart coordinators, the real repository and Account ViewModel to synthetic provider/store boundaries. Its 25 controls cover retained-A identity failure, failed logout before and after clear, legitimate B callbacks through the real authorization parser, B against an A legacy/current cart, normal logout followed by B, same-A continuity, anonymous attachment, wrong/missing/oversized identity, ambiguous rebind before/after application, cancellation, secure-write/read failure and logout ordering. Launch regressions connect the actual checkout controller to the bounded presentation callback and prove that a presented event stream does not retain the session lease.

`CustomerAccountSessionCoordinatorTest` preserves the prior protected session when another exchange is attempted. Its old supersession assertion described unsafe overwrite/clear behavior; the new expectation protects the existing session. `CustomerAccountGatewayTest` verifies the explicit leased token's actual HTTP Authorization header with generated Apollo parsing and no resolver reentry. `CartCoordinatorTest` covers exact ownership and persistence-format controls.

Actual Compose regressions exercise `CartScreenTest` pending-ownership retry and `AccountScreenTest` retained-session/no-summary logout and retry, without private tasks. Android Keystore tests cover encrypted exact-owner/pending roundtrip and healthy corruption/anonymous controls; a separate process regression exercises the durable boundary with a test-only service.

The session coordinator has no new static-analysis suppression. `DefaultCartRepository` and `CheckoutController` each use a narrow class-level function-count exception because their actions share a mutex and published state, consistent with the existing cart coordinator. Return count, complexity, nesting, global thresholds, baselines and CI policy remain enforced.

Behavioral RED receipts are preserved under ignored `out/remediation/production-20261006/wave1/`: corrected connected ownership RED, session nonoverwrite RED, checkout-lease RED, preservation RED, oversized-ID RED and actual API 36 cart/account UI RED. Invalid setup and intermediate failed GREEN attempts remain separate failed history; labels and filenames alone are not acceptance.

Final local evidence binds 638 implementation files to SHA-256 `205b24de85fbc6676aed9ca558ed5fa8ba8208471314c57ce95d64844e18d9a2`. The affected account/checkout/storefront/mobile-core JVM inventory contains 459 tests: 454 passed, five existing live-provider opt-in skips, zero failures/errors. All 25 connected ownership controls passed without skips. Rebuilt API 36 suites passed 26 Account/Cart screen tests and four encrypted-store controls, including the distinct-process read. The immutable APK hashes and actual XML/logs are retained in the ignored evidence package.

`wave1-broad-validation-3` passed Spotless, all-module detekt, all four affected Lint lanes, affected JVM suites, both shared Android test APK assemblies and all 17 registered application builds, including minified releases: 1,226 tasks (428 executed, 159 from cache, 639 up-to-date). The logs distinguish executed, cached and up-to-date checks. Public-readiness self-tests (20), repository rules (23), onboarding suite (255), registered lane projections and portability checks (46) passed.

Fresh debug/release binary checks passed Synthetic's 63 package rules and Trial's 25 package rules; validator self-tests passed 49 and six fixtures respectively. Synthetic retains disabled remote Home, no effective INTERNET permission and physical Firebase absence. Trial retains its development-only identities and provider configuration boundary.

Earlier broad validation first failed static analysis and then crashed in the Temurin JVM's concurrent GC thread after affected Lint and JVM checks passed. Both failed receipts and the native crash log are preserved. The successful retry used the unchanged source with a task-local SerialGC/3 GiB heap/1 GiB metaspace override and disabled heap dumps; no tracked build or CI policy changed. Independent source review verified ownership ordering, preservation, disabled-capability composition and checkout lease release. Protected PR CI and exact merged-main CI still govern wave acceptance.

No live Customer Account exchange, real customer mutation, hosted checkout completion, order or payment was used. Synthetic fixture verification does not establish live PII consequences, payment behavior or an installed code2-to-code3 upgrade. The old distributed supported-signature artifact remains a separate final-run availability check.
