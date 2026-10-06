# Wave 2A: mutation transport and action outcomes

Status: implementation, local verification and independent source review complete on `codex/remediation-mutation-outcomes`, based on protected main `12ead74b7045147ad237ef46b7ae2a71f9a7ad54`. Wave 1 [PR #39](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/39) and its exact merged-main [run 37389377378](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37389377378) passed before this wave began. This is a pre-merge checkpoint; protected acceptance and final-source reassessment remain required. It does not select final v3 source or establish release readiness.

## Contract and shared ownership

One shopper action must not resend an uncertain, fully sent mutation body through HTTP recovery or follow-ups. A valid provider cart snapshot and the outcome of the requested action are separate facts. Preserve verified customer work, publish provider truth and confirm completion only when the requested postcondition is established.

`:storefront` owns operation-specific transport, generated parsing, merchandise rejection taxonomy, complete cart data and exact ownership verification. `:mobile-core` owns requested-action postconditions and honest Product/Cart feedback. The application module continues to select configuration and providers. Another enrolled merchant inherits these repairs through the shared modules; there is no application-package, merchant-name or domain business rule. Synthetic remains offline/nonproduction and Trial remains development-only.

## Transport recovery — C-07

The production factory selects the mutation HTTP delegate using Apollo's typed `Mutation` contract. A wrapper marks the already composed OkHttp request body `isOneShot`, before the HTTP retry/follow-up interceptor sees it, while delegating its bytes, length, content type and duplex property. Query requests retain the repeatable body and normal safe recovery. Apollo operation retry is explicitly disabled in the factory; that setting alone would not prevent lower HTTP replay.

Both delegates share a private lazy client with the pinned engine's 60-second connect/read timeout contract. Existing headers, generated serialization/parsing and cancellation remain intact. Disposal is idempotent and releases only this transport's resources. The internal endpoint helper gives permanent loopback tests access to the same production construction; it does not add a public application endpoint switch.

The permanent matrix covers create, add, update, remove and buyer-identity mutations after a complete-body lost response, HTTP 408 with and without `Retry-After: 0`, HTTP 503 with `Retry-After: 0`, and body-preserving HTTP 307/308 redirects. Healthy ordinary mutations and query recovery remain controls. Actual request counts prove the client boundary; they do not measure live Shopify commit incidence or provider deduplication.

## Rejection and authoritative state — C-01

Merchandise/input user errors do not establish whole-cart invalidity. The generated payload mapping retains optional complete cart data alongside typed user errors. Before publication or persistence, the coordinator verifies the original cart ID and exact owner, including returned error data. Unknown, incomplete or mismatched data cannot authorize cart presentation. A rejection with no trusted payload uses one bounded cart read, preserving the handle while uncertainty remains.

Proven missing/expired cart state retains its explicit cleanup behavior. Mutation-time Empty/Expired or a missing planned cart/line cannot report completion. Preparation may still intentionally create a new cart for Add when no prior valid cart exists. A valid empty returned cart remains a cart with a retained reference; it is distinct from expiry or absence.

Single-page mutation data now receives the same complete-line total validation as paged data before action evaluation. Quantities are compared as `Long`; no incomplete snapshot certifies a requested result.

## Requested-action outcomes — C-02

Direct responses and ambiguity rereads use the same exact postcondition: create/add compare the sum across all matching merchandise lines; update compares the exact target line quantity; remove requires the previously present target line to be absent. Warnings do not decide success. Achieved intent with unrelated warnings remains completed.

Direct corrected/partial state returns a typed adjustment carrying the action and previous, requested and observed quantities. An unchanged or rejected action fails honestly. An unachieved ambiguous mutation remains ambiguous after the bounded reread, without claiming that the observed change came from this attempt. Every recovered publication retains the verified ownership state.

Product shows distinct localized adjustment feedback and offers cart review. Only exact completion uses the Added confirmation. Cart shows adjustment feedback even for a verified empty cart or a correction with no provider warning. Provider state remains current commerce authority; no automatic partial-Add replay is introduced. Product selection attribution and operation cancellation are the separate Wave 2B boundary.

## Permanent regressions and verification

`StorefrontMutationTransportTest` executes the production factory and generated operations against loopback HTTP. Its corrected old-policy RED contains 41 cases: 30 intended replay/redirect failures and 11 healthy controls passing. An earlier warm-query fixture error is preserved separately and does not count as behavioral RED.

`CartMutationPayloadTest` exercises actual generated merchandise errors, top-level GraphQL partial-data trust and inconsistent single-page totals. The initial three-case old-behavior run has two intended assertion failures. `CartMutationOutcomeTest` connects the real gateway, coordinator, shared operations and repository; its initial 20 cases have 13 intended old-behavior failures covering false completion, reference/data loss and incorrect quantities. Final focused GREEN passes 41 transport, five payload and 28 connected outcome cases, including exact ownership and healthy warning controls. Four affected module suites contain 535 cases: zero failures/errors and five existing live-provider opt-in skips. Product ViewModel's 14 cases and Wave 1's 25 connected ownership controls pass within that inventory.

Actual API 36 Compose RED reproduced the missing adjustment notice for a verified empty cart. The immutable old test APK, one-failure log and 642-file source inventory are retained under ignored task evidence. Permanent Cart/Product screen tests also cover a typed empty correction without warnings, localized partial-Add review at font scale 2 and normal exact-Add confirmation.

The new connected core tests add only the already pinned Apollo and MockWebServer test edges. Strict dependency verification remains enabled. One previously unlisted AndroidX metadata checksum was independently matched to Google's exact published bytes; no version, production dependency, wildcard trust or verification policy changed. Failed launcher/setup and verification attempts remain preserved separately from behavioral results.

The focused run also passes Spotless, affected detekt and Android test APK assembly. Earlier static/setup failures remain preserved; fixture extraction satisfies the existing method/class limits without adding suppressions or changing thresholds. Core lock changes add only test memberships and the same pinned MockWebServer 5.4.0 components already present in Storefront.

All 34 Cart/Product screen cases pass on API 36, including the four new regressions. Initial full/isolated runs and the immutable accepted Wave 1 APK encountered an unchanged image-viewer test's Back failure. Native window diagnostics then identified a visible Pixel Launcher ANR dialog taking input over the project test. After its observed Wait control was dismissed on the task-owned emulator, the unchanged baseline Back test and full current suite passed. Temporary native-key/synchronization diagnostic edits were removed; the final patch retains the original Back test and its assertions. Failed attempts remain preserved.

Broader validation passes all static checks, four relevant Lint lanes, eleven JVM tasks and registered shared/application debug/release/test builds, including minified releases. The full JVM inventory contains 700 cases: zero failures/errors and five existing opt-in skips; cached/up-to-date distinctions remain in the log. Synthetic package checks pass 63, Trial 25, onboarding fixtures 255 and portability 46. The broad snapshot's sole diagnostic Back-test difference is superseded by the restored test, already passing focused static checks and the exact source inventory. Production, resources and JVM source are identical across those snapshots.

Independent full source/dependency review passes with no actionable findings. The final 645-file implementation inventory exactly matches the focused GREEN source, SHA-256 `c1d2019c1430a4ffbdd89e6d8171bd93be5de92ea798976e163a01d81c0c70cb`; its immutable current test APK is bound to the passing full Compose run. Protected PR and exact merged-main CI still govern wave acceptance; all thirteen findings require reassessment on the eventual exact final main.

No live merchant/customer writes, order/payment, Play upload, track/tester change, App Links change, signing-key/reset change or version edit is used for this wave.
