# P3-06 Production Cart Handoff

Date: 2026-08-10

Implementation status: **COMPLETE**

Local acceptance status: **PASS - PHYSICAL DEVICE EVIDENCE PENDING**

Branch: `main`

Starting checkpoint: `bc1e1a872f41af21a700f321e45fe292cb96dba8`

Final checkpoint: the local commit containing this file; resolve its exact hash with `git rev-parse HEAD` after checkout.

This handoff records the first functional production Cart destination and action, authoritative Shopify reconciliation, explicit protected ownership state, and local P3-06 acceptance. It does not classify compiled instrumentation as device execution and does not authorize Checkout, payment, P3-16, release, or publication work.

## Product and clean-room boundary

The sibling reference APK remained immutable and was not installed, launched, rebuilt, signed, modified, or copied. The project evidence helper was used only for the narrow cart completeness question. It confirmed a parameterless Cart destination and the create/read/add/update/remove/buyer-identity operation categories. The reference Worker sync route and cart-note operation were not adopted because their server behavior is not project-owned and the accepted product decision excludes cart notes.

No decompiled method body, obfuscated structure, reference UI, branding, credential, endpoint, Worker route, or mutable data entered this implementation. Current Shopify Storefront contracts govern the independent implementation: a cart can contain at most 500 lines, line mutations accept at most 250 inputs, quantity rules come from the server, buyer identity affects the cart, and the complete cart ID is secret-bearing. See Shopify's [Cart object](https://shopify.dev/docs/api/storefront/latest/objects/cart), [cart lines update](https://shopify.dev/docs/api/storefront/latest/mutations/cartLinesUpdate), and [cart management guidance](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart/manage).

## Storefront and mapping contract

- Generated Apollo operations now read current cart lines, product/variant titles and identities, safe media, availability, standard-line mutation instructions, server quantity rules, per-unit/line money, subtotal, total, and buyer-association presence.
- Buyer customer identity is reduced immediately to an association boolean. A raw customer ID is not persisted, logged, routed, or exposed to presentation state.
- The complete cart ID and checkout URL remain redacting sensitive value types. Neither is present in `CartState`, Navigation routes, SavedState, resources, error copy, or logs.
- The first 250 cart lines are read with the cart snapshot. A bounded second 250-line page completes the Shopify maximum. Missing cursors, duplicate lines, unsupported line mappings, quantity drift, incomplete money, currency mismatch, or paging beyond the contract fail closed instead of publishing a partial cart.
- Add, update, and remove accept generated typed inputs, preserve Shopify user-error codes without raw upstream text, and publish only the latest authoritative server totals and line state.
- Componentizable/non-standard lines may render when their required commerce fields map, but quantity/remove controls remain disabled unless Shopify supplies the standard `CartLine` instructions.

## Protected cart ownership

P3-06 implements the accepted states exactly:

- `Anonymous`
- `CustomerAssociated`
- `DetachPending`
- `Quarantined`

The encrypted cart payload advances from version 1 to version 2 and stores complete cart ID, expiry, and ownership together behind the existing Android Keystore-backed AES-GCM boundary. A legacy version 1 record has no reliable association evidence and therefore decodes as `Quarantined`; it never silently becomes anonymous. Corrupt or unknown payloads fail closed.

Anonymous restoration reads Shopify before exposing lines and requires the remote cart to have no associated customer. An association mismatch is persisted as `Quarantined`. Customer association is accepted only after a matching remote response. Detachment persists `DetachPending` before the remote mutation; confirmed removal becomes `Anonymous`, an ambiguous retryable transport result stays pending, and a hard/unverifiable result becomes quarantined. Restricted states expose neither lines nor checkout capability and require explicit local discard confirmation.

All coordinator mutations are serialized. A definitive missing/expired cart clears its protected local reference. Transient transport/GraphQL failures retain it. Ambiguous add/update/remove results are re-read before any retry; server-confirmed state is accepted, while an unchanged result remains explicit and is never blindly replayed.

## User-visible outcome

- Home exposes a functional Cart action and current in-process badge. Product Detail submits only its currently selected valid, available server variant and quantity one.
- Cart has typed navigation plus loading, true empty, expired, active, restricted, and service/error states. It supports refresh, quantity increase/decrease from server `minimum`/`maximum`/`increment`, line removal, explicit protected-reference discard, mutation progress, retry, and safe discovery recovery.
- Product title, variant title, media, unit price, line price, subtotal, total, warnings, and availability are rendered from the latest Shopify response. There is no invented stock limit, price, discount, delivery promise, tax claim, campaign, or local money truth.
- Product Detail confirms a successful add and can open Cart. A failure states that an existing cart remains protected only when one exists; it does not claim a failed first creation was saved.
- Turkish and English resources cover Cart and Product Detail actions. Controls have text/accessibility descriptions, bounded touch targets, and layouts that remain scrollable under large text.
- Cart note/attribute input is absent. Checkout launch is deliberately absent until P3-07. Proof/Bogus/manual cart controls remain outside normal production navigation.

## Automated and live evidence

The following serialized checks completed successfully against the final implementation source:

```powershell
.\gradlew.bat :storefront:compileDebugKotlin :app:compileDevelopmentDebugKotlin --no-parallel --console=plain
.\gradlew.bat :storefront:detekt :app:detekt --no-parallel --console=plain
.\gradlew.bat :storefront:testDebugUnitTest :app:testDevelopmentDebugUnitTest --no-parallel --console=plain
.\gradlew.bat :app:compileDevelopmentDebugAndroidTestKotlin --no-parallel --console=plain
.\gradlew.bat :storefront:testDebugUnitTest -PgurbakirRunOwnedCartProof=true --tests "com.gurbakir.storefront.OwnedStorefrontCartProofTest" --no-parallel --console=plain
.\gradlew.bat :app:lintDevelopmentDebug :app:assembleDevelopmentDebug :app:assembleDevelopmentDebugAndroidTest --no-parallel --console=plain
.\gradlew.bat spotlessApply --no-parallel --console=plain
gitleaks dir . --config .gitleaks.toml --redact --no-banner --log-level error
```

Results:

- Storefront and app production Kotlin compilation: `PASS`.
- Storefront/app Detekt, app development Lint, Spotless, redacted Gitleaks directory scan, and `git diff --check`: `PASS`.
- App JVM suite: 50 tests, 0 failures, 0 errors, 0 skips.
- Storefront JVM suite: 34 tests, 0 failures, 0 errors, 2 skips. The two skips are the expected opt-in owned-store read/cart proofs; the cart proof was then enabled explicitly and passed.
- New tests cover payload v1 quarantine, ownership mismatch quarantine, confirmed association/detachment, pending offline detachment, concurrent mutation serialization, two-page cart reads, current generated cart mapping, first add/create, ambiguous-mutation reread without replay, server quantity-rule rejection, restricted-state presentation, and incomplete-snapshot failure.
- New/updated instrumentation source covers empty Cart at 200 percent text, server-driven quantity controls/removal, quarantine/discard confirmation, Product Detail add-to-cart, and typed production Cart navigation. Android test Kotlin compilation and test APK assembly: `PASS`.
- The opt-in owned non-production Storefront proof completed create, initial cleanup, add, update, restore, and final removal of a bounded synthetic line. It did not open Checkout or create an order/payment, and its cleanup guard removed any remaining line.

## APK evidence and evidence not run

The locally assembled artifacts are:

- `app/build/outputs/apk/development/debug/app-development-debug.apk` - 19,396,170 bytes - SHA-256 `6D85AA2E88870E0FAD1F9C17D86E07A1DBAD0472BBB988521ECC6D02AC94E19D`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk` - 1,232,297 bytes - SHA-256 `07CF070EF84D38DF95390C95AE408153C09B462E872C8B435452B393BF4DDF60`

The package/runner identities remain `com.gurbakir.mobile.dev.debug`, `com.gurbakir.mobile.dev.debug.test`, and `androidx.test.runner.AndroidJUnitRunner` targeting the application package.

The owner reported that a physical Android device is unavailable during this work window. The documented host condition also cannot boot the existing x86_64 AVD into ADB because firmware virtualization/hypervisor support is unavailable. No redundant emulator attempt was made.

Instrumentation was compiled and packaged but **NOT RUN**. Physical APK install/launch, add-to-cart interaction, process recreation, encrypted payload version 1/2 behavior on Android Keystore, offline mutation recovery, Cart scrolling at 200 percent text, adaptive layout, TalkBack announcements/traversal, touch behavior, and visual inspection therefore remain pending for P3-15 or the first earlier approved-device checkpoint. This is an evidence-availability gap, not a device `PASS`.

## Security review and residual boundaries

- No raw cart ID, checkout URL, buyer token, customer ID, upstream error message, or server exception is printed or placed in presentation/navigation state. Redacting `toString` behavior remains tested.
- No new host, permission, dependency, backend, analytics, crash reporter, background worker, note/attribute storage, plaintext Room/DataStore record, or backup path was introduced.
- The public Storefront token remains controlled by ignored local configuration and was not printed or committed. The directory secret scan was redacted and passed.
- The cart badge is process state rebuilt from secure restoration; it is not durable truth. Product/cart presentation has no offline mutable cache by design.
- P3-09 must connect hosted session lifecycle to the already implemented association/detachment coordinator calls. Until then the production UI exercises the anonymous path and never presents a restricted cart as anonymous.
- P3-07 owns Checkout eligibility refresh, Checkout Kit lifecycle, cancel/failure/completion semantics, and production removal of manual proof controls. No checkout action is reachable at this checkpoint.

## Rollback

Revert only the P3-06 checkpoint commit to remove Cart UI/navigation/mapping and restore the payload writer to version 1. A development/staging installation that has already written version 2 cannot be safely read by reverted version 1 code; clear that non-production app's local data or uninstall it before installing the reverted build. This discards that installation's local cart reference but does not delete the remote Shopify cart immediately. No order/payment or persistent merchant configuration requires rollback.

## Exact next slice

P3-07 may begin from this checkpoint under the continuous owner goal: refresh only an eligible non-restricted cart, pass its fresh allowlisted checkout URL to the existing official Checkout Kit adapter, preserve the cart on cancel/decline/failure/process restoration, clear it only after genuine completion, and keep real payments/orders prohibited.
