# TumiArt Stage 4 P7-A: pre-signing candidate

Status: **IN PROGRESS / NOT SIGNING READY**, 2026-10-19.
Base: `a394e86d3920e1e515a5767b83dd1b94ed51c252` (P6 merged main).
This record distinguishes reviewable source preparation from production acceptance.
P7-B, production keys, final signed artifacts, Play upload and self-merge are excluded.

## Application-owned preparation

The owner approved `com.tumiart.mobile`, versionCode `1`, versionName `1.0.0`.
The owner subsequently deferred Play account selection/registration until the
brand selects its deployment account. It is not a prerequisite for completing
application engineering. Publisher ownership/package availability remain unverified
distribution inputs, not an authorization to reserve or upload the package now.
Production debug uses `com.tumiart.mobile.debug`; development identities remain exact.
Production has its own Room database, encrypted preference names, Keystore aliases
and exact callback `shop.80183034101.tumiart.production://oauth/callback`.
Development retains `shop.80183034101.tumiart://oauth/callback`.
Opposite-environment and near-match callbacks are rejected.

The production projection contains public configuration only. Ignored production
client properties and provider binding are absent. The runtime setup gate remains
closed; a production-flavor launch is not live production acceptance. Do not copy
development credentials merely to open that gate. Existing onboarding production
write barriers remain intact. Firebase is intentionally absent.

Production release packaging requires explicit
`-PallowUnsignedProductionDiagnostic=true`. When scoped production client properties
exist it additionally requires `-PonboardingApplication=tumiart`,
`-PonboardingProfile=production`, and `-PrequireConfiguredProfile=true`.
There is no production signing configuration. These controls permit unsigned APK
and AAB preparation later; they do not confer signing or publication authority.

The launcher uses an original app-owned T monogram in the existing paper/ink palette,
with adaptive and Android 13 monochrome resources. It contains no merchant artwork
or copied logo. Final owner visual acceptance remains distinct from resource validity.

## Supported Android decision

TumiArt alone moves to API29 / Android 10, retaining target36. Default Android trust
failed on API24 for the owned Storefront, account and media hosts; the root cause
was missing trust anchors. API25 passed the same normal-trust probes and live Home,
but its stock hosted browser failed to render sign-in. That browser observation
does not establish an app defect. API29 passed normal-trust probes and actual
Home/catalog/account/Checkout journeys with a functioning browser/WebView.

API29 is the lowest defensible candidate in this investigation because current
[Chrome support starts at Android 10](https://support.google.com/chrome/a/answer/7100626).
TLS success alone would not justify a secure commerce floor. The API29 diagnostic
WebView used for acceptance is not evidence of the latest security update; browser
and WebView update/support policy remains a release operations obligation.
No trust manager, hostname, certificate or SDK compatibility bypass was introduced.
Shared core, Synthetic and Trial API23 regression lanes remain enrolled. Both TumiArt
profiles are enrolled at API30; its development minimum lane now uses API29.

## Legal and account deletion

TumiArt's verified merchant routes are contact, KVKK/privacy, membership terms,
shipping/delivery and returns/cancellation. The adapter uses exact role-specific
origin/path checks and exposes only available resources. Legal-notice content is
unverified and remains unavailable. Hostile URI and role-forgery tests reject before
invoking a browser. Shared typed deletion UI and cleanup behavior are reused.

An unpublished dedicated request Page was created under prior exact owner approval.
The owner subsequently saved `templates/page.account-deletion.json` in Live02.
Readback matched the approved clean JSON, using only the existing main-page and
contact-form sections; the sample contact-page map is excluded.
The owner confirmed `info@tumiart.com` as the request/contact inbox and
MİRA DİJİTAL DANIŞMANLIK LİMİTED ŞİRKETİ as the merchant business. This supersedes
the earlier suggestion that intake would use a different email. Inbox delivery,
named primary/backup assignment and operational execution remain unobserved.
The latest owner direction explicitly defers public visibility until the app is
ready and the website step is discussed again. It supersedes earlier publication
approval. **Keep the page unpublished; do not submit a form or enable the Android
request binding.** The route is prepared, not accepted for public deletion intake.
No account erasure, remote deletion, local cleanup or completed request is claimed.

Before enabling the route: renew publication authorization, re-read page/template
and section dependencies, inspect signed-out rendering and accessibility, submit
one plainly Turkish test-only request, obtain merchant receipt, and identify the
primary/backup privacy operator and retention/verification/notification process.
The exact private review package and provider IDs are in the ignored checkpoint.

## App Links and release operations

TumiArt's existing `web.manifestAutoVerify` policy now drives all three manifest
filters. Both profiles request verification for `https://tumiart.com/collections/`,
`/products/`, and `/apps/mobile/orders/`; mode remains `validate-only`. This is an
intentional TumiArt development change as well as production preparation.
Other-brand generated projection changes are registry digests only.
[Android verification](https://developer.android.com/training/app-links/verify-applinks)
also requires a matching public Digital Asset Links association. The prior public
readback was an empty array; the latest workstation request encountered a connection
challenge, so current contents are unverified. The connector cannot read mobile
platform registrations without `read_mobile_platform_applications`. No fingerprint,
registration, domain content or verification result has been fabricated.

Certificate-dependent association publication/readback and installed final-signer
verification belong to separately authorized P7-B. Public reachability without an
interactive challenge must also be established there. Source `autoVerify=true`
does not mean verified links.

## Data flow and accountability inputs

| Flow | Current implementation / receiver | Remaining release input |
| --- | --- | --- |
| Catalog, media, Home and Search | HTTPS Shopify Storefront / owned media policy; commercial values stay live | Merchant content/media authorization and production binding readback |
| Sign-in and account | Hosted Shopify Customer Account OAuth/PKCE; protected Android session storage | Exact production Public/Mobile registration, callback/logout and live acceptance |
| Cart and checkout | Shopify Cart and Checkout Kit; customer association detached on native logout | Production configured journey; merchant processors/payment/retention review |
| Wishlist and cached editorial content | Local Room / bounded editorial-only cache | Explain local clearing separately from remote erasure |
| Support and privacy requests | Owned HTTPS merchant pages / merchant intake | Confirmed info@tumiart.com inbox; accountable operator and backup, verified receipt/process |
| Telemetry | TumiArt composition is Firebase-free | Complete SDK/data-flow and hosted privacy review before Play declarations |

The Türkiye/TRY fixed-market model and intentional Android `visitorConsent` omission
use the shared implementation boundary. Gürbakır's operational approval is not
TumiArt approval. Merchant legal/privacy/support authority, app-provider technical
incident/recovery duties, Play publisher, signer custody and rollback authority must
be assigned for TumiArt. Do not submit Data Safety answers by inference from a
Firebase-free APK or a debug journey. No public response deadline or retention
promise has been invented.

## Verification evidence

Local registered JVM verification reports 1100 cases: 1095 passed, five explicit
Storefront opt-in skips, zero failures/errors across all 13 enrolled tasks.
TumiArt's two profiles account for 52 passing tests. Onboarding contracts passed
320/320; package-validator fixtures 11/11 and public-readiness fixtures 23/23.
Both-profile lint, formatting/static checks and four diagnostic APK builds passed;
the experimental accessibility harness remains private. Actual package inspection passed
54/54 checks. ZIP and 64-bit ELF alignment passed static 16 KiB inspection; runtime
16 KiB verification remains the separate CI lane.

On API29, the ordinary native suite executed 30 passing cases and nine explicit
opt-in skips. The private fixture matrix covers all nine portrait combinations of
320/360/393dp and font scales 1/1.3/2, with Turkish/English, light/dark, loading,
error, empty, unavailable, partial and refresh-error states. A separate confirmed
landscape run passed three cases at 720x288dp usable space and font1.3. An earlier
rotation-only capture stayed portrait and is explicitly excluded as landscape
proof. Original display/font/rotation/network settings were restored.

A minified development-only diagnostic copy, signed with the existing standard
Android debug key, passed actual live Home, Gallery/collection/PDP media and
fresh-process offline/error/retry recovery. Shipping and returns opened their
merchant content in Custom Tabs; Android App Info rendered the original monogram.
No authentication was attempted in that copy because both development build types
share their intended callback. This is bounded R8 evidence, not final production
signing. Four existing Gallery lambda optimization warnings remain recorded;
observed successful paths do not establish universal harmlessness.

A private basic TalkBack probe observed one complete seven-control pass using
original owned fixtures and isolated callbacks: meaningful labels/programmatic
focus, zero callbacks from one touch and exactly one expected double-touch callback.
A subsequent run on the final formatted harness failed to activate the artwork
purchase control. Repeatability is therefore **PARTIAL**, not closed acceptance;
input timing versus component behavior remains unresolved. Experimental privileged
emulator input code and its APK remain private, outside the tracked test suite and
production artifacts. No speech, swipe traversal, physical-device ergonomics,
provider operation or complete focus-order claim is made. A direct, reliable basic
TalkBack activation smoke must close this remaining acceptance gap before readiness.
Fresh per-run failure evidence was verified after the earlier passing run; device
accessibility settings were restored. The failed repeat is preserved alongside the
pass, not discarded or reported as an application defect without evidence.

## Acceptance and continuation

| Gate | Status | Evidence / limitation |
| --- | --- | --- |
| Source production identity/isolation | PASS for preparation | Both-profile configuration/callback/persistence tests; owner-approved identity/version |
| Minimum-device live commerce | PARTIAL | API29 development Home/Search/Wishlist/PDP/exact variant/Cart/account/Checkout, offline/restart; production provider absent |
| Native account lifecycle | PARTIAL | Fresh owner-supplied code, protected restart, native logout, anonymous Checkout and signed-out restart; natural expiry/remote revocation unobserved |
| Legal resource routing | PASS for published five routes | Owned paths and strict adapter; dedicated deletion route still absent |
| Deletion public intake | BLOCKED / OWNER DEFERRED | Hidden page, no controlled receipt or named operator/backup |
| Production provider registration/runtime | BLOCKED | Scoped production client configuration and binding absent |
| Publisher selection | OWNER DEFERRED | Account selection/registration belongs to distribution; application engineering continues |
| Privacy operational ownership | PARTIAL | Merchant business and info@tumiart.com supplied; receipt/process and primary/backup unverified |
| App Links association | DEFERRED TO P7-B | Manifest prepared; final certificate and public verification pending |
| Basic TalkBack | PARTIAL | One seven-control pass observed; final repeat missed the artwork purchase control, so reliable activation acceptance remains open |
| Native validation / protected integration | PARTIAL | Local API29, matrix and package evidence is indexed privately; verify API30, minimum-SDK and inherited 16KiB operations on the selected PR head. Recent-Android evidence is indexed in the checkpoint; protected merge remains separate. |

The API29 account/Cart test used a controlled account. No inbox access, real order,
payment, address submission, or actual-customer mutation occurred. Controlled Cart
line and Wishlist were removed; this is native UI confirmation, not a separate
backend erasure query. Private merchant images, account evidence and client inputs
remain outside Git. Deterministic native fixtures must be labeled separately from
live merchant/provider evidence.

P7-B cannot begin until material pre-signing blockers are closed, the accepted PR
is owner-merged, and exact merged-main CI passes. Then freeze the accepted source
and verified production configuration, confirm Play App Signing/upload-key custody,
build both artifacts from that same source/configuration, independently verify
package/version/SDK/manifest/native alignment/signatures/hashes, publish only the
authorized certificate association, and verify final-signer App Links/callbacks.
P7-A neither performs nor pre-authorizes those actions.
