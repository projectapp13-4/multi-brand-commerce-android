# P3-12 Orders and Tracking Implementation and Physical Acceptance Handoff

Date: 2026-08-13

Implementation status: **COMPLETE**

Local acceptance status: **PASS**

Physical-device acceptance status: **PASS**

External live order/tracking status: **EXTERNALLY UNVERIFIED**

Branch: `main`

Starting checkpoint: `71ea450` (`docs: checkpoint Phase 3 after P3-11`)

Implementation checkpoint: `527a033177eafc418480c9e78739d357f206a518` (`feat: complete P3-12 orders and tracking`).

This handoff closes P3-12 Orders and tracking. It records an independently implemented, current Customer Account API order history/detail boundary, conservative status vocabulary, partial-fulfillment rendering, fail-closed official-carrier handoff, private-data lifecycle, local and physical evidence, and the exact P3-13 boundary. It does not claim a live customer order, a real carrier-site journey, overall Phase 3 completion, release readiness, or P3-16 security/release hardening.

## Authority and decisions

- The current Shopify Customer Account API `Order`, `Customer.orders`, `Fulfillment`, `FulfillmentLineItem`, `LineItem`, `TrackingInformation`, `MoneyV2`, `OrderFinancialStatus`, `OrderFulfillmentStatus`, `FulfillmentStatus`, and `FulfillmentEventStatus` contracts are the API authority. Operations target the repository's configured 2026-07 Customer Account schema.
- CI-30 and CI-31 were historically marked unapproved. The active owner policy supersedes their automatic owner-blocker interpretation while preserving the historical record. P3-12 uses only current Shopify enum values and conservative reversible TR/EN labels. Unknown values stay generic; no timeline, ETA, merchant promise, or fulfillment completion is inferred.
- The carrier allowlist is deliberately small: `ptt.gov.tr`, `yurticikargo.com`, `araskargo.com.tr`, and `suratkargo.com.tr`, including their subdomains. These are the official domains for the four Turkey carriers documented by Shopify and independently verified against the carriers' public sites.
- Tracking URLs must be exact normalized ASCII HTTPS URLs, have no userinfo or fragment, use only implicit/explicit port 443, stay within 2048 characters, and resolve to an exact allowlisted host or subdomain. Rejected, absent, unsupported, or unlaunchable tracking falls back to the existing owned P3-08 contact route.
- Orders and order-derived PII are never cached. The app reads only through the current Keystore-backed Customer Account session, clears in-memory order state when screens leave the foreground or the session ends, and reloads on resume.
- Production order screens set Android `FLAG_SECURE`; the flag remains set between list/detail and is cleared when leaving private order destinations. No screenshot proof containing synthetic/private order detail was captured.
- A typed owned deep link accepts only `https://gurbakir.com/apps/mobile/orders/{positiveNumericOrderId}`. The route contains only the bounded numeric Shopify order component and redacts itself in string rendering. Invalid/unowned/not-found/access-denied detail produces one non-oracular unavailable state.
- No reference APK was installed, launched, rescanned, or used as a source donor. Phase 2 synthetic order evidence remains test evidence and is not exposed through production navigation.

Primary public sources:

- <https://shopify.dev/docs/api/customer/latest/objects/Order>
- <https://shopify.dev/docs/api/customer/latest/objects/Fulfillment>
- <https://shopify.dev/docs/api/customer/latest/objects/TrackingInformation>
- <https://shopify.dev/docs/api/customer/latest/enums/OrderFulfillmentStatus>
- <https://shopify.dev/docs/api/customer/latest/enums/OrderFinancialStatus>
- <https://www.ptt.gov.tr/>
- <https://www.yurticikargo.com/>
- <https://www.araskargo.com.tr/>
- <https://www.suratkargo.com.tr/>

## Completed implementation

### User-visible behavior

- Signed-in Account exposes Orders; signed-out Account hides it.
- Order history loads the current customer's newest orders with 20-item cursor pages, refresh, explicit empty/loading/error/support states, deduplication, and a recoverable page error that retains the already verified list.
- Order summaries show only server-returned order name/date, financial status, fulfillment status, and total. Detail renders server-returned totals, shipping address lines, line items, cancellation date when present, fulfillment groups, partial quantities, shipment/fulfillment status, carrier name/number, and available tracking actions.
- Status is always conveyed by text rather than color alone. Dates and currency use the active locale with explicit unavailable fallbacks. TR and EN resources cover every implemented order/status/recovery surface.
- Partial fulfillments remain separate and never imply the entire order shipped. Absent or unsupported tracking offers the owned support route without fabricating a URL or status.
- Allowlisted tracking opens in AndroidX Custom Tabs with sharing disabled. A rejected URL cannot reach an external intent.
- Order list/detail clear their private state offscreen and reload on resume. A generation guard prevents an older in-flight response from repopulating PII after the screen backgrounds or the session expires.
- Terminal auth failure clears the Customer Account session and returns to a fresh Account destination. Ordinary connection/service failures retain only verified in-memory content and expose explicit retry.
- Lists/details reflow and scroll at 200-percent text. Headings, live-region feedback, stable semantics tags, external-transition wording, and support recovery are present.

### API, state, navigation, and persistence

- Added generated `CustomerOrders` and `CustomerOrderDetail` operations. List pagination is reverse `PROCESSED_AT`; detail uses bounded nested connections and fails closed if any requested nested connection reports another page, preventing silent partial detail.
- Added a discovery/session/Apollo-backed `CustomerOrderGateway`, redacted domain models, official status mappings, cursor validation, canonical `gid://shopify/Order/{positiveNumeric}` conversion, and a generic absent result for null/unowned detail.
- Added `OrderController`, `OrderListViewModel`, `OrderDetailViewModel`, screen state/effect/action models, Compose list/detail screens, money/date/status resources, `TrackingUrlPolicy`, and `TrackingLauncher`.
- Added typed `OrderListRoute` and redacted `OrderDetailRoute`, Account entry, owned HTTPS deep link, Hilt bindings, session reset, lifecycle cleanup, support fallback, and private-window capture policy.
- No dependency, Room schema, migration, DataStore record, manifest permission, Shopify/Firebase/OAuth configuration, backend, proof UI, or persistent order record was added.

### Main changed files

Account/API:

- `account/build.gradle.kts`
- `account/src/main/graphql/com/gurbakir/account/schema.graphqls`
- `account/src/main/graphql/com/gurbakir/account/CustomerOrders.graphql`
- `account/src/main/graphql/com/gurbakir/account/CustomerOrderDetail.graphql`
- `account/src/main/kotlin/com/gurbakir/account/CustomerOrderGateway.kt`
- `account/src/test/kotlin/com/gurbakir/account/CustomerOrderGatewayTest.kt`

Application:

- `app/src/main/kotlin/com/gurbakir/mobile/order/OrderController.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/order/OrderListViewModel.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/order/OrderDetailViewModel.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/order/OrderScreens.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/order/TrackingUrlPolicy.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/ProductionApp.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/account/AccountScreen.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/di/CustomerAccountModule.kt`
- `app/src/main/kotlin/com/gurbakir/mobile/navigation/ProductionRoutes.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Tests:

- `app/src/test/kotlin/com/gurbakir/mobile/order/OrderControllerTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/order/OrderViewModelTest.kt`
- `app/src/test/kotlin/com/gurbakir/mobile/order/TrackingUrlPolicyTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/order/OrderScreenTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/ProductionNavigationTest.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/account/AccountScreenTest.kt`

P3-11 device-found accessibility correction included in this checkpoint:

- `app/src/main/kotlin/com/gurbakir/mobile/address/AddressFormScreen.kt`
- `app/src/androidTest/kotlin/com/gurbakir/mobile/address/AddressScreenTest.kt`

## Validation evidence

### Generated schema and focused tests

- `:account:generateCustomerAccountApolloSources`: **PASS**.
- `CustomerOrderGatewayTest`: **7/7 PASS**.
- Focused App order tests (`OrderControllerTest`, `OrderViewModelTest`, `TrackingUrlPolicyTest`): **14/14 PASS** after fixing retry generation and offscreen privacy generation behavior found by the first run.
- Android-test source compilation: **PASS**.

### Broad local gates

- Account JVM: **56/56 PASS**.
- App JVM: **115/115 PASS**.
- Combined broad JVM: **171/171 PASS**, zero failures/errors/skips.
- Account/App Detekt: **PASS** after structural refactors; no new Detekt suppression was introduced.
- Account/App Android Lint: **PASS**, empty final XML reports. The first P3-12 Lint run correctly found API-23 locale and plural-resource errors; both were fixed with `ConfigurationCompat` and plural resources.
- Final combined command `spotlessCheck :account:detekt :app:detekt :account:testDebugUnitTest :app:testDevelopmentDebugUnitTest :app:compileDevelopmentDebugAndroidTestKotlin :account:lintDebug :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest`: **PASS** in 4 minutes 34 seconds.
- `gitleaks dir . --no-banner --redact --exit-code 1 --log-level error`: **PASS**.
- `git diff --check`: **PASS**.
- The P3-00 documentation validator passed 14 substantive documentation/configuration/secret/Git checks before commit and intentionally failed only its docs-only dirty-scope guard because the valid P3-12 implementation was still uncommitted.
- From clean implementation checkpoint `527a033`, `scripts/Test-Phase3Planning.ps1`: **15/15 PASS** (`features=24`, `screens=27`, `slices=17`).

### Physical device

Device: Samsung SM-A225F, Android 13, 720x1600.

The focused P3-12 connected group installed matching development app/test APKs and executed:

- `OrderScreenTest`: **4/4 PASS**.
- `ProductionNavigationTest`: **8/8 PASS** including typed Orders navigation and owned deep-link resolution.
- `AccountScreenTest`: **4/4 PASS** including signed-in visibility/action and signed-out absence.
- P3-12 combined physical evidence: **16/16 PASS**.

The same device was used to close the previously deferred P3-11 evidence. The OEM diagnostic crash loop and reboot are documented in `P3-11-HANDOFF.md`; the package's pre-existing disabled state was preserved.

Manual real-account order history, real carrier handoff, and full TalkBack speech review are **EXTERNALLY UNVERIFIED / DEFERRED TO P3-15**. No unexecuted check is labelled PASS.

## APK artifacts

Final matching artifacts:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
  - 19,981,671 bytes
  - SHA-256 `11D8D69A6D3755673882971568DBFA3923B6958D059D8BD360E9941C7D1BB580`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
  - 1,305,287 bytes
  - SHA-256 `74E27781DDF0EEE32B6134A87D5F0CA363C90816232C44FDBBC671A11B96F2A1`

The development application APK is suitable for current manual testing. The Android test APK must be used only with its matching application artifact and configured runner.

## External state

- Shopify Admin, storefront data, Customer Account configuration, Firebase, Remote Config, backend, accounts, profiles, addresses, carts, orders, payments, and synthetic remote records: **UNCHANGED**.
- No live Customer Account order was read or mutated and no real carrier link was opened. Official public documentation/carrier sites were read-only evidence.
- The development and test APKs were installed on the authorized Samsung device for instrumentation. The device was rebooted once to clear a broken OEM diagnostic-process loop; the OEM package state was not changed. No camera, media, contacts, location, messages, or unrelated application was accessed.
- No credential, token, customer/order ID, address, tracking number, private key, or remote message is recorded in tracked source, documentation, or ordinary output. All test order data is explicitly synthetic.

## Acceptance and exact next slice

P3-12 exit criteria are satisfied: order data is scoped to the current Customer Account session; list/detail/pagination and partial fulfillment are typed; no status, ownership, ETA, or tracking URL is invented; unsafe/absent links fail closed to support; private state is not persisted and is cleared across lifecycle/session boundaries; TR/EN/accessibility and physical focused evidence pass.

The exact next slice is **P3-13 Account deletion request**. Before implementing it:

1. Read `AGENTS.md`, `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`, this handoff, P3-08/P3-09 handoffs, and the P3-13 roadmap entry.
2. Verify this implementation checkpoint, clean worktree, empty stash, preserved ignored configuration, connected-device state, and no active Gradle job.
3. Do not repeat P3-12 implementation/schema/carrier research or live service proof without a regression or contract change.
4. First determine whether a real owned account-deletion request resource, operator, privacy/retention process, re-auth rule, affected-system inventory, acknowledgement language, and synthetic cleanup path now exist.
5. If that functional external process still does not exist, preserve P3-13 as externally blocked; do not add a placeholder button or claim that local data clearing deletes a remote account. Continue only with safe independent later-slice work allowed by the roadmap.
6. Do not begin P3-15 integrated acceptance before all preceding in-scope functional slices have their honest exit disposition.

## Rollback

Revert the single P3-12 implementation checkpoint to remove order schema/operations, gateway, list/detail/tracking screens, routes, resources, tests, and the device-found address accessibility correction together. No migration, persistent order data, remote record, dependency, or service configuration requires rollback.
