# P3-07 Production Checkout Handoff

Date: 2026-08-11

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS - FOCUSED PRODUCTION ROUTE AND 44/44 GROUPED INSTRUMENTATION**

Branch: `main`

Resume checkpoint: `7bd31e095eb4ed1e68f19d89a5f5d74888e25311`

Initial P3-07 implementation checkpoint: `0ec5565` (`feat: checkpoint P3-07 checkout handoff`)

Physical-acceptance fix checkpoint: `a61ecb6` (`fix: close P3-07 physical acceptance gaps`)

Final documentation checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff closes the production Cart-to-Checkout Kit slice. It records fresh-cart eligibility, session-scoped SDK callbacks, cancellation/failure retention, exact-cart completion, process-recreation behavior, physical Android execution, and cleanup of the bounded non-production test cart line. It does not authorize a real order/payment, production rollout, P3-16, or work past the P3-08 entry gate.

> **Post-handoff authorization update (2026-08-11):** `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md` supersedes only this handoff's former assumption that P3-08 required final owner-supplied legal/support records. The owner authorized verified existing merchant/Shopify policies and conservative traceable provisional content as the current baseline. P3-07 remains complete; P3-08 is authorized to proceed. The historical blocker evidence below is retained to show the state at handoff time.

## Product and clean-room boundary

The existing official Shopify Checkout Kit dependency and project adapter were reused. P3-07 did not install or execute the sibling reference APK and did not copy a decompiled body, proprietary UI expression, reference branding, credential, Worker route, or unknown backend behavior. The production application contains no Bogus Gateway, manual result injection, or proof action.

The physical proof used the project-owned development application and non-production Shopify configuration. A single bounded synthetic merchandise line was added to an anonymous cart only to reach Checkout Kit. The hosted checkout was inspected and cancelled before contact, delivery, payment, or order submission. The synthetic line was then removed through the remote Cart mutation; the final application state was `Your cart is empty.`

## Completed implementation

### Production route and user-visible behavior

- Cart exposes checkout only while an active, non-mutating, complete cart is eligible. It explains that payment, delivery, and order confirmation occur in Shopify's secure in-app checkout.
- `CartDestination` owns the production `CheckoutViewModel`, obtains the current `Activity`, and launches the existing official adapter without placing a cart ID or checkout URL in Navigation, SavedState, Compose state, resources, or logs.
- Progress, cancel, failure, completion, different-current-cart preservation, blocked external link, and secure-cleanup-required outcomes have Turkish and English UI resources.
- Confirmed completion suppresses a generic empty-cart message and offers a safe continue-shopping route.
- Cancel and failure retain the protected cart, re-read it from Shopify, and render current remote state. Returning from the sheet or losing an Activity never implies completion.
- No proof screen, Bogus control, manual completion button, or synthetic result appears in the production route. The Phase 2 development proof harness source remains unreachable from `MainActivity`; final release-graph removal/exclusion remains P3-16 work.

### Eligibility, session, and completion contracts

- `DefaultCartRepository.prepareCheckout()` performs exactly one authoritative protected-cart restoration immediately before launch.
- Eligibility requires a complete non-empty snapshot, complete paging, reconciled line quantities and totals, saleable lines, anonymous/allowed ownership, and the existing exact-host checkout URL policy. Empty, expired, restricted, incomplete, unavailable, or failed state is fail-closed.
- `CheckoutController`, `CheckoutState`, `CheckoutViewModel`, `CheckoutFailureMapping`, and `CheckoutCartCompleter` own the production lifecycle.
- Each presentation receives a positive `CheckoutSessionId`. Only a callback for the active presented session can cancel, fail, or complete it; stale callbacks are ignored.
- Only `CheckoutEvent.Completed` is success. Cancellation and typed failure retain and refresh the protected cart. Process recreation begins at `IDLE` and never infers a successful order from a returned or missing Activity.
- Completion calls `CartCompletionCoordinator.complete(expectedCartId)`. The exact launched cart is cleared, an already absent cart is idempotent success, a different current cart survives, and protected-store cleanup failure becomes `CLEANUP_REQUIRED` instead of false success.
- External-link callbacks remain blocked at the application boundary and cannot clear the cart. Existing file, geolocation, permission, analytics/pixel, and URL restrictions were not broadened.
- The SDK cache is invalidated immediately before presentation and after terminal outcomes; production does not preload a possibly stale checkout.

### Data, GraphQL, storage, and configuration

- P3-07 added no GraphQL operation. It reuses the generated P3-06 cart read and its redacting sensitive checkout URL.
- No Room schema, migration, DataStore record, payment data, permission, backend, dependency, Firebase setting, Customer Account setting, or new host was introduced.
- Cart ownership remains `Anonymous`, `CustomerAssociated`, `DetachPending`, or `Quarantined` behind the version 2 Keystore-backed envelope.
- The checkout URL, complete cart ID, buyer identity, payment/order data, and raw SDK/upstream messages remain absent from logs and presentation/navigation state.

### Physical-evidence fixes

The physical device exposed two application defects that compilation and JVM tests did not reveal:

1. Cart used `SensitiveCartLineId` as a Compose lazy-item key. `SaveableStateHolder` attempted to persist that non-Bundle-safe value and crashed the production Cart. `CartContent` now lets Compose use a safe positional key; raw cart identity still does not enter SavedState.
2. Product Detail placed the complete page in one oversized lazy item. On the connected small-screen device, this prevented reliable traversal to lower content. The media, title, price, wishlist, option selectors, cart action, and description are now separate lazy items with Bundle-safe string option keys.

The acceptance-fix commit also adds stable scroll tags to Home, Catalog, Product Detail, Search, and Wishlist surfaces and a semantic `OnClick` test helper. Tests no longer depend on fragile raw screen coordinates when an actionable Compose semantic node is available.

## Automated acceptance

### Final serialized local quality gate

```powershell
.\gradlew.bat spotlessCheck :app:detekt :app:testDevelopmentDebugUnitTest :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-parallel --console=plain
```

Result: **PASS**, 298 tasks, completed in 3 minutes 37 seconds.

- Spotless: **PASS**.
- App Detekt: **PASS**.
- App JVM: 59 tests, 0 failures, 0 errors, 0 skips - **PASS**.
- App development Lint: 0 issues - **PASS**.
- Development app APK and Android test APK assembly: **PASS**.
- `gitleaks dir . --config .gitleaks.toml --redact --no-banner --log-level error`: **PASS**.
- `git diff --check`: **PASS**.

The initial P3-07 implementation checkpoint also passed checkout JVM 5/5, storefront JVM 36 discovered with 34 passes and 2 expected opt-in owned-store proof skips, and app JVM 59/59. Combined P3-07 focused local test evidence is 100 discovered, 98 passed, 2 expected opt-in skips, and no failure/error. The P3-06 live proof and Phase 2 OAuth/FCM/Checkout proofs were not repeated merely for reassurance.

### Final physical instrumentation

Device: Samsung SM-A225F, Android 13, API 33. The serial is intentionally omitted.

The exact final app/test APK pair was installed through ADB. One instrumentation runner process on this device became unreliable during a long 44-test run: window focus and coordinate touch injection intermittently produced `No compose hierarchies found` or missed taps in different tests. That monolithic execution is **NOT CLAIMED AS PASS**.

The same installed APK pair was then executed serially as six isolated runner groups so each group received a fresh runner/window lifecycle:

| Group | Coverage | Result |
|---|---:|---|
| Foundation and Firebase | 11 | **PASS** |
| Production navigation, Cart, and Catalog | 13 | **PASS** |
| Home | 7 | **PASS** |
| Product Detail | 4 | **PASS** |
| Search | 4 | **PASS** |
| Wishlist | 5 | **PASS** |
| **Total** | **44** | **44 PASS / 0 FAIL** |

The final grouped result is the accepted device execution. It covers the current foundation/Firebase runtime, typed production navigation, Cart/checkout Compose contract, Home, listing, Product Detail, Search/history, Wishlist/database/migration, semantics, long-text/scroll, and the evidence-driven fixes above. A full manual TalkBack speech/traversal audit was **NOT RUN** and remains part of P3-15's accumulated accessibility audit; P3-07 does not misclassify that future cross-application audit as complete.

## Manual production-route acceptance

The following focused checks passed on the installed production application route:

- launch Home, browse collection/listing, open Product Detail, select an eligible variant, add one bounded synthetic line, and open Cart;
- refresh the authoritative remote cart total and open the official Checkout Kit in-app surface;
- observe the Shopify-hosted test checkout/Bogus instructions without entering contact, address, card, payment, or order data;
- close/cancel the SDK surface and return to Cart with the exact cart retained and refreshed;
- force-stop and cold-relaunch the application, confirming the protected cart persists and no completion is inferred;
- remove the synthetic line through the remote Cart remove action and confirm the authoritative empty state.

The unchanged adapter's successful completion callback was already physically proven in Phase 2 and is covered by current session/completion JVM and Compose tests. It was not re-triggered through Bogus Gateway because doing so would create transaction/order evidence solely for reassurance. No real payment method was used and no payment or order was submitted.

## Artifacts

The final device-tested development artifacts are:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 19,408,737 bytes
  - SHA-256 `365E92533BFF6F019F68736BE72550F399C66147A56976DBA16F01AF0CE0F45F`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,256,314 bytes
  - SHA-256 `84B9A28CF0ED6C8C2290CA13FD9B64F78C24F3CCACA5531A4290B82E6B9A610D`

Package/runner identities remain `com.gurbakir.mobile.dev.debug`, `com.gurbakir.mobile.dev.debug.test`, and `androidx.test.runner.AndroidJUnitRunner` targeting the application package.

## External state and security

- Shopify: one bounded anonymous non-production cart line was added for the manual route and removed remotely before exit. No synthetic line remains. No Admin configuration was changed.
- Checkout/payment/order: hosted checkout was opened and cancelled. No customer/contact/address/card data was entered; no payment or order was submitted or created by this P3-07 acceptance run.
- Firebase, Remote Config, Customer Account configuration, OAuth, backend, accounts, and device push registration: unchanged.
- Physical device: app and androidTest APKs were installed. Only the project application/test packages were controlled.
- Browser/Edge/Chrome: no browser automation or browser state mutation occurred in the resumed device run; existing sessions were untouched.
- No checkout URL, cart ID, credential, PII, payment/order detail, or hosted checkout screenshot was stored in source, documentation, logs, or evidence artifacts.

## Acceptance boundary and exact next slice

P3-07 is **COMPLETE**. At the time of this handoff, P3-08 was classified **EXTERNALLY BLOCKED AT ENTRY** by the recorded missing owner inputs below. The later active owner authorization linked above supersedes that approval classification and authorizes P3-08 to verify/adopt merchant-owned sources or create a conservative provisional baseline:

- `CI-22`: canonical owned support contact/channel/process and accountable support owner;
- `CI-23`: canonical owned privacy policy, URL, provenance, version/effective date, languages, and offline/update policy;
- `CI-24`: equivalent owned terms-of-service record;
- `CI-25` and `CI-26`: required shipping and returns/refund/cancellation sources for the supported market;
- `CI-34`: approved allowlisted legal/support page URLs and rendering/provenance policy.

Under the original handoff boundary, placeholders and a generic credential WebView remained prohibited. Under the later owner policy, P3-08 may create only conservative, traceable, replaceable provisional project-owned content where verified merchant content is absent. P3-09 still must not begin until P3-08 exit criteria pass.

When the owner supplies the missing records, resume by reading `AGENTS.md`, this handoff, `P3-00-CONTENT-ASSET-MARKET-INVENTORY.md`, and the P3-08 roadmap section. Verify the supplied provenance/ownership/canonical HTTPS URLs/version/effective date/languages/cache-offline/update/accessibility records against CI-22 through CI-26 and CI-34. Only then implement the P3-08 typed page index and allowlisted external browser adapter, run focused URL/accessibility/offline tests, and close P3-08 before starting Account work.

## Rollback

Revert `a61ecb6` to remove only the device-evidence fixes, or revert `0ec5565` after it to remove the P3-07 production checkout orchestration. Reverting P3-07 does not delete a remote Shopify cart. If a development install contains incompatible local test state after a rollback, clear only the development package data or uninstall that package; this removes local protected references but does not imply remote cart deletion.
