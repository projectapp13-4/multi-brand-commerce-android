# Firebase Development Proof

Date: 2026-08-06
Gate status: **PASS**

## Owned project and application mapping

| Environment | Firebase display name | Registered Android applications | Ignored variant files |
| --- | --- | --- | --- |
| Development | `Shopify App` | `com.gurbakir.mobile.dev.debug`; `com.gurbakir.mobile.dev` | `app/src/developmentDebug/google-services.json`; `app/src/developmentRelease/google-services.json` |
| Staging | `Gurbakir Android Staging` | `com.gurbakir.mobile.staging.debug`; `com.gurbakir.mobile.staging` | `app/src/stagingDebug/google-services.json`; `app/src/stagingRelease/google-services.json` |

- The development project's pre-existing legacy `Shopify.App` registration was preserved.
- The development/staging debug registrations contain the approved debug SHA-256 fingerprint. Release registrations do not claim a production/release certificate.
- Development and staging remain separate projects. Staging is on the no-cost Spark plan; no billing account was enabled.
- Analytics was disabled during setup. No production Firebase project/app, Firestore, Realtime Database, Storage, Authentication, service account, private key, or backend credential was created.
- Completed console setup is not to be repeated. Public identifiers remain only inside the four ignored client files.

## Implemented privacy and runtime boundary

- Firebase BoM `34.16.0` supplies current main Android Messaging, Installations, and Remote Config artifacts; retired standalone KTX artifacts and Analytics/Crashlytics SDKs are absent.
- [`FirebaseContracts.kt`](../../firebase/src/main/kotlin/com/gurbakir/firebase/FirebaseContracts.kt) installs false local defaults for exactly two approved booleans, fetches/activates only on an explicit UI action, reads only enum-backed keys, and fails closed to local defaults. Remote Config cannot replace endpoints, code, consent, legal links, or arbitrary content.
- FCM auto-initialization is disabled. The current SDK `register()`/`unregister()` and Firebase Installation ID contract runs only after the notification permission and an explicit in-app action. A non-sensitive stored-consent boolean restores access to unregister; a failed consent-state write rolls registration back.
- The target identifier redacts itself. Debug builds may place it temporarily in app-private cache solely for the controlled Console proof; release builds record nothing. It is never shown, logged, documented, or placed in tracked/static configuration.
- The historical Gürbakır Firebase messaging service at this checkpoint was non-exported and accepted exactly one allowlisted custom route field. It ignored remote title/body content, created a local fixed notification only when Android permission was present, and used an explicit immutable `PendingIntent` to the typed Firebase proof route. Later Gate 5 provider isolation superseded its source location; current source and the Gate 5 handoff govern the implemented boundary.
- Analytics, Crashlytics, advertising-ID collection, FCM automatic initialization, and FCM delivery-metrics export remain disabled in the manifest. `DisabledAnalyticsReporter` emits nothing.
- Unit tests cover approved Remote Config keys, safe fetch failure fallback, exact notification payload/route rejection, explicit permission/registration/unregister state, stored consent restoration, and no target rendering. Development/staging instrumentation APKs compile with deterministic Firebase UI semantics.

## Validated runtime evidence

- `Test-FirebaseConfiguration.ps1 -RequireConfigured` passes exact packages, one project per environment, distinct project identities, ignored status, enablement, and prohibited server/private fields.
- All four Google Services processor tasks pass.
- The approved Android 13 phone executed the current development and staging app suites with eleven tests each. `FirebaseRuntimeTest` proved one configured default Firebase app and Messaging auto-init disabled for both variants; the deterministic Compose suite exercised the explicit Firebase proof states for both environments.
- The account and Storefront Keystore suites executed three and two additional physical tests respectively. The current physical total is 27 unique tests with zero failures or skips.
- On the development debug build, registration did not begin until the user explicitly requested it and approved the Android notification permission. The generated Firebase Installation ID was held only in app-private debug cache and was transferred to the verified development project's Console without being displayed or logged.
- The verified no-cost development project accepted that Installation ID and delivered exactly one synthetic test notification. The device notification service was observed, and tapping it opened only the allowlisted in-app Firebase proof route; remote title/body content was not trusted for local rendering.
- The user explicitly invoked Remote Config. Fetch/activate completed on the physical device and only the two enum-backed boolean keys could become active.
- The user then explicitly unregistered. The app reported success, the app-private temporary target was absent, the Windows staging file and diagnostic screenshots were deleted, and the clipboard was cleared.
- The current expanded local group passes Firebase/App Detekt, Firebase JVM tests, development/staging app JVM tests, and both instrumentation APK assemblies.

## Claim boundary

Gate 7 is `PASS` for the Phase 2 development/staging foundation. The proof does not authorize or claim Analytics/Crashlytics collection, a production Firebase project, billing, a database, Storage, Authentication, service-account credentials, a backend, broad notification campaigns, or production push delivery. Analytics/Crashlytics event or crash transmission is intentionally not a missing test: those SDKs stay absent until a consent, taxonomy, retention, and deletion/access policy is approved.

Authority: [Firebase Remote Config Android](https://firebase.google.com/docs/remote-config/android/get-started), [FCM Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started), and [FCM message receipt](https://firebase.google.com/docs/cloud-messaging/android/receive-messages).
