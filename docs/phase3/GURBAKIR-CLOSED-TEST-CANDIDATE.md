# Gürbakır closed-test candidate preparation

Status: **IN PROGRESS** (2026-09-26). This is a bounded Google Play closed-test candidate, not public production release acceptance. The historical [P3-16 entry audit](P3-16-HANDOFF.md) remains the record of the earlier blocked state; this document records the later authorized continuation.

## Identity and configuration

- The registered Gür Bakır Play app owns `com.gurbakir.mobile`. The production release variant uses that exact application ID; its debug variant uses `.debug`. Development and staging package IDs remain distinct.
- Version code starts at `1` and version name at `0.1.0` for this new package. A later upload must increment version code. No earlier bundle upload for this package was observed during the preflight.
- The production profile has distinct protected cart and customer-session storage names/aliases. It consumes its own ignored `config/local/gurbakir/production.properties` and tracked registry projection. Public Shopify client values are intentionally absent from Git.
- The dedicated Shopify Headless mobile Customer Account client registers `shop.81051975919.gurbakir.production://oauth/callback`. Development/staging retain their existing callback suffix. The app's configured manifest callback is derived from the selected profile's local value and checked against the projection.
- No production Firebase project was identified or required for the initial tested graph. The production profile is explicitly disabled for Firebase and selects local defaults; development/staging Firebase configuration remains separate. Firebase-dependent push/remote feature behavior is outside the initial candidate's proven graph.
- Integration proof screens and their localized strings live in the debug source set. They are absent from the production release resource graph; the release UI uses the normal merchant-facing application shell.
- The registry's `nonproduction-only` release boundary still governs the onboarding operator's remote Plan/Apply/Recover actions. Those commands explicitly reject the production profile. The production package identity authorizes closed-test preparation only; it does not authorize public publication or imply P3-16 completion.

## Signing and Play

- Play App Signing uses a Google-held app-signing key. A distinct Gürbakır upload key is kept outside Git under the project owner's restricted Windows profile; Gradle reads it only from process environment when explicitly supplied. No signing password or private key is stored in this repository.
- An unsigned CI release assembly checks compilation. The exact closed-test AAB requires an explicitly selected, configured production profile and signed-bundle build, followed by package/version/certificate inspection. Upload-certificate registration in Play is not established until the first accepted bundle upload.
- An existing 15-entry `Gür Bakır Tester` email list is selected on Gür Bakır's closed-test Alpha track. The track is inactive. List selection alone does not start the opt-in or 14-day testing requirement.

## Open acceptance gates

- Fresh exact-head protected PR `validate`, API 30 and API 23, merge, and exact merged-main CI are required before any candidate is attributed to protected main.
- The merchant domain currently serves `[]` from `/.well-known/assetlinks.json`; the Play app-signing certificate is not yet associated. HTTPS App Links remain unverified. The registered custom-scheme Customer Account callback is separate.
- The Storefront Menu/Home readback, Customer Account login/return, cart/checkout, legal/support/deletion routes, production release graph, signed AAB contents, and Play declarations must be verified against the exact candidate before a closed-test release is submitted.
- Public release, real commerce transactions, production customer mutation, and production readiness remain outside this closed-test campaign.
