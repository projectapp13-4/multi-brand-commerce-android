# Device Test Evidence

Date: 2026-08-06
Physical-device evidence: **PASS for OAuth, Keystore, Storefront cart, Checkout Kit, Remote Config, and controlled FCM delivery/navigation**
Current device state at final Firebase proof: **CONNECTED AND AUTHORIZED IN ADB**
Emulator history: **EXTERNALLY BLOCKED BEFORE BOOT**
Current instrumentation APK compilation and physical execution: **PASS**

## Approved physical device

One approved Samsung phone running Android 13 was connected with USB debugging. The device identifier is intentionally omitted. The development APK was installed and visibly opened; the user confirmed the on-screen app and supplied only interactive browser/store/customer/test-checkout input when required.

The following physical evidence was completed:

| Area | Physical result | Evidence boundary |
| --- | --- | --- |
| Customer Account OAuth/PKCE | **PASS** | System browser, exact callback, live exchange, typed identity query, refresh, logout/end-session, and signed-out restoration with a synthetic account. |
| Customer session Keystore | **PASS** | Round-trip, corruption fail-closed, and restoration after force-stop/reinstall/relaunch. |
| Storefront cart Keystore | **PASS** | Round-trip, corruption fail-closed, cart restoration, mutation, bounded warning, and cleanup. |
| Checkout Kit | **PASS** | Preload, password-page transition, real checkout rendering, cancellation/cart retention, Bogus approval/completion/clear, Bogus decline/no false completion, relaunch restoration, and Admin test-order reconciliation. |
| Firebase initialization | **PASS** | Development and staging each initialized one correct default app with Messaging auto-init disabled. |
| Expanded Firebase Remote Config/FCM proof | **PASS** | Explicit permission/registration, one controlled development-project delivery, allowlisted notification tap route, explicit Remote Config fetch/activate, unregister, and temporary-target cleanup. |

No reference APK was installed/launched. No third-party reference service was contacted. No real payment or real customer data was used.

## Connected automated tests

The account/Storefront Keystore suites and the first app checkpoint ran together:

```powershell
.\gradlew.bat --no-parallel `
  :account:connectedDebugAndroidTest `
  :storefront:connectedDebugAndroidTest `
  :app:connectedDevelopmentDebugAndroidTest `
  :app:connectedStagingDebugAndroidTest
```

Result: **BUILD SUCCESSFUL in 1m56s**. The app suites contained nine tests at that checkpoint.

After the Firebase harness expansion, the current app suites ran again:

```powershell
.\gradlew.bat --no-parallel `
  :app:connectedDevelopmentDebugAndroidTest `
  :app:connectedStagingDebugAndroidTest `
  --no-daemon --no-configuration-cache --console=plain
```

Result: **BUILD SUCCESSFUL in 2m2s**. Development and staging each executed eleven tests. The table below uses the current app results and counts the unchanged account/Storefront suites once.

| Suite | Executed on physical device | Tests | Failures | Skips |
| --- | --- | ---: | ---: | ---: |
| Account debug | yes | 3 | 0 | 0 |
| Storefront debug | yes | 2 | 0 | 0 |
| App development debug | yes | 11 | 0 | 0 |
| App staging debug | yes | 11 | 0 | 0 |
| Total unique current suites | yes | 27 | 0 | 0 |

The earlier nine-test app executions are historical and are not added to the 27-test total. Successful Android-test APK compilation is recorded separately from the 27 tests that actually executed on the phone.

## Manual Firebase development proof

The latest development debug APK was installed after the connected runner removed its temporary test installation. On the visible Firebase proof screen:

1. registration remained absent until the user explicitly selected the test-registration action and approved Android notification permission;
2. the app produced a current Firebase Installation ID and retained it only in app-private debug cache;
3. Edge attached to the existing authenticated session, verified the `Shopify App` development project, its no-cost Spark plan, and the exact development debug app before any send;
4. Firebase Console accepted the Installation ID and sent exactly one synthetic test notification with the allowlisted custom route;
5. Android reported the app notification present, and the user's tap foregrounded the app with the allowlisted Firebase proof-route status;
6. an explicit Remote Config fetch/activate completed and exposed only approved boolean flags;
7. explicit unregister succeeded; the app-private target, Windows transfer file, diagnostic screenshots, and clipboard content were removed.

No raw installation ID, Firebase identifiers, notification payload URI, customer value, or credential is stored in this report. No notification campaign was published; the unsaved compose tab was closed after the one device test.

## Current device and compilation boundary

The approved phone was connected and ADB-authorized for the final Firebase proof. A device may disconnect after this checkpoint, so a future session must verify current `adb devices` state rather than assuming this snapshot remains live. No emulator result is inferred from the physical proof.

Current APK outputs:

- `app/build/outputs/apk/development/debug/app-development-debug.apk`
- `app/build/outputs/apk/androidTest/development/debug/app-development-debug-androidTest.apk`
- `app/build/outputs/apk/androidTest/staging/debug/app-staging-debug-androidTest.apk`

Do not rerun the controlled Firebase send merely to reconfirm this completed gate. Recheck the device only when later feature work genuinely needs it, and do not recreate Shopify/Firebase configuration.

## Emulator history

The installed API 36 AVD previously remained offline because firmware virtualization was disabled and the Emulator hypervisor driver was unavailable. No Android userspace booted and no emulator test ran. This historical limitation does not invalidate the later physical-device evidence and should not trigger another emulator attempt while the approved phone can be reconnected.
