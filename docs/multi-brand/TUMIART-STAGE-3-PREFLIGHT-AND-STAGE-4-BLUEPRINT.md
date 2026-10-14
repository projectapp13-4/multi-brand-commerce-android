# TumiArt Stage 3 preparation closure and Stage 4 blueprint

Status date: 2026-10-14, Europe/Istanbul.
Baseline source: d6d61381ef269471fba289302e7545cad074fc34.
Candidate branch: codex/tumiart-stage3-preflight.
Scope: provider registration, independent preflight, one bounded operator correction,
and a future implementation blueprint. No TumiArt Android application was created.

## Decision and evidence limits

**Provider preparation is CLOSED for the selected TumiArt Headless client.**
Public/Mobile type and the exact Android callback are persisted; Client ID and
Storefront public token match the owner's ignored files. Client ID was preserved.
No credential generation/revocation or shared-permission change occurred.

**Technical preparation is locally verified on this candidate.** A real
PowerShell 7 discovery failure exposed the operator's Shopify-host-only assumption.
The optional, independently trusted custom account origin correction passes its
focused and full offline regressions, current live discovery and local configuration
validation, and independent review. Protected PR-head/main acceptance of that
correction remains an integration obligation; this document is not merged-main CI proof.

**Stage 4 engineering may begin, with protected integration of this correction
as its first dependency.** Use the existing shared application architecture and
bounded Home contract. Asset/content and permanent distribution choices must be
resolved before their dependent implementation or publication. Missing remote
Home content does not block an isolated application skeleton or packaged fallback.

**End-to-end TumiArt Account, cart and checkout acceptance remains OPEN.**
Login, actual refresh-token issuance/renewal, hosted logout, authenticated GraphQL,
device callbacks and ownership isolation are NOT YET TESTABLE in this task:
no scoped installed TumiArt application/test harness and approved test-customer
execution gate exists. HTTP 400/401 and unrelated Trial acceptance are not substitutes.
Production identity/signing, Play, public App Links, privacy/release and P3-16 remain separate.

## Source of truth and chronology

| Evidence class | Current observation and authority |
|---|---|
| Repository | Authorized primary checkout on main, clean tracked/untracked status, HEAD d6d61381; origin https://github.com/projectapp13-4/multi-brand-commerce-android.git. Connected GitHub independently resolved main to the same SHA, merged PR #45. |
| Isolation | Managed worktree on codex/tumiart-stage3-preflight; original main and ignored original credential files preserved. Only onboarding scripts/schema/tests/docs change in the candidate. |
| Connected machine | Remote Desktop Commander DESKTOP-3EHSHCO responded, independently read the same SHA and clean status, and found the historical TEMP research artifacts. |
| Merchant | Connected TumiArt Admin: shop 80183034101, tumi-art.myshopify.com, primary tumiart.com, account URL https://hesabim.tumiart.com. The Gürbakır connector was never selected. |
| Exact Headless target | Official authenticated Chrome UI, Tumi Art Headless, storefront 399989. UI credential equality was compared without emitting either credential value. |
| Architecture | Current source/configuration and docs/README.md control implemented facts. Gates 6-9 remain closed only within their defined scopes; Trial is development-only and Synthetic offline. No gate is transferred to TumiArt. |
| Historical reports | All four Stage 1 reports, Stage 2, Stage 3 including its later Client ID appendix, and bundle manifest were reviewed. Instructions in supplementary reports did not expand authorization. |
| Newer evidence | Current UI superseded the older unknown client-registration state. Stage 1 SON_DOGRULAMA's mobile-menu gap is superseded by KESIF_KAPANIS's later two successful mobile drawer flows. |
| CI limit | The connected workflow helper returned no PR-triggered runs for the merge SHA; it filters to that class. This is not evidence that merged-main push CI is absent or passed. No new branch CI was dispatched in this task. |

The current source preserves the app-only Gürbakır API-24 change at this baseline.
Shared modules, Trial and Synthetic retain the shared API-23 policy. Do not copy
Gürbakır's release identity or SDK decision into TumiArt automatically.

The authority set read includes AGENTS.md, owner boundaries, documentation map,
BRAND-ONBOARDING, MULTI-BRAND-ARCHITECTURE, BRAND-BOUNDARIES, ADR-0004, Gate 6-9
handoffs, provenance, privacy operating contract and current Phase 3/release records.
Older preparation and pre-merge statuses remain historical evidence.

## Stage 3 resolution matrix

PASS below names a specific proof class; it does not mean a complete OAuth journey.

| Check | Status | Exact proof or next gate |
|---|---|---|
| Shop/customer account identity | PASS | Validated read-only Admin query: shop ID 80183034101, NEW_CUSTOMER_ACCOUNTS, OPTIONAL, login links true, required checkout login false, account URL above. |
| Exact Client ID association | PASS | UI value equals ignored Customer Accounts API.txt; final fresh UI comparison recorded only match=true. |
| Storefront token provenance | PASS | Public token on storefront 399989 equals ignored Storefront token file; no private token reveal or copy. |
| Client type | PASS | Public/Web changed to Public/Mobile; save completion observed, then re-read after navigating away. |
| Credential preservation | PASS | Pre/post Client ID exact equality; no generate-new or revoke action. |
| Callback | PASS for provider registration | Exact sole reviewed URI shop.80183034101.tumiart://oauth/callback persisted. Shopify's generic shop.80183034101.app://callback was automatically inserted on type change and replaced. Future registry/manifest/device must match. |
| Shared permissions | PASS for readback | customer_read_customers, customer_write_customers and customer_read_orders enabled; pre/post checked-scope sets identical. OAuth request scopes remain openid, email, customer-account-api:full. |
| Wider permission blast radius | PARTIAL | UI explicitly says Headless scopes are shared across storefronts. Existing company/subscription/draft/market permissions also remain; this task neither audits every integration nor narrows shared grants. |
| OpenID/account discovery | PASS, read-only | Both tumiart.com and tumi-art.myshopify.com discovery documents return 200 JSON without redirects; exact shop issuer, HTTPS account endpoints, S256, authorization_code and RS256. |
| Operator transport/custom domain | PASS on corrected candidate | PS 7.6.5 baseline failed CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH at 01:14:34.055Z; trusted-origin corrected real discovery PASS at 01:24:33.770Z and local preparation PASS at 01:25:17.964Z. No Windows PowerShell 5.1 success claim. |
| Provisioning/runtime API distinction | PASS for contract/schema | Configured provisioning endpoint stays /2026-07/graphql; runtime discovery returns /2026-10/graphql. All ten current Account operations validate individually against both exact versions: 20/20. Wrong configured 2026-10 pin remains rejected. |
| Public-client security contract | PASS for source/tests | Existing AppAuth authorization-code/S256/state/nonce, exact callback consumption/expiry, protected persistence, refresh and logout tests; unchanged Account JVM 119/119. This is source/test evidence. |
| Manifest/installed receiver | NOT YET TESTABLE | TumiArt application/registry entry does not exist. Later match exact scheme, host/path behavior and exported receiver surface on an installed isolated app. |
| Login and protected customer access | NOT YET TESTABLE | Approved synthetic account, app/harness, final manifest and explicit isolated execution required. No email, OTP, auth code or customer token captured. |
| Actual refresh and remote logout | NOT YET TESTABLE | Observe initial refresh_token/id_token issuance, natural expiry/renewal, restart, provider logout and signed-out restart; local cleanup alone is not remote logout. |
| Full Inspect/Plan/Apply/Readback enrollment | NOT YET TESTABLE | TumiArt is deliberately absent from registry/settings/modules. Bounded direct module checks do not mean full configured brand acceptance. |
| Production/release | BLOCKED for release, outside this task | Permanent identity/signing/Play/App Links and merchant privacy/support/deletion ownership need their release gate. |

Final independent provider poststate was recorded at
2026-10-14T01:30:12.275Z (04:30:12.275 Türkiye time).
Provider write times were not separately instrumented; do not invent them.
Credential-free saved screenshots and final redacted readback corroborate persistence.

The existing OAuth transaction is single-use with a ten-minute lifetime and exact
state/route checks. Session acceptance requires a future access expiry plus
refresh_token and id_token. ID-token code checks claims including issuer,
audience/azp, subject, time and nonce; this review does not establish an implemented
JWKS signature-verification path. Tokens come directly from the HTTPS token
endpoint; OpenID Connect Core 3.1.3.7 permits reliance on validated server TLS for
this direct code-flow response. Keep that transport/issuer trust boundary explicit
and do not describe claim checks as cryptographic signature verification.
Restore/refresh is serialized; transient refresh failure preserves protected state,
while invalid authentication clears it. Mobile logout calls the endpoint with
id_token_hint, requires HTTP200 and blocks redirects. Local session clearing may
still succeed when remote logout is unverified; the UI preserves that distinction.
The app must use an external browser/Custom Tabs, never a credential-handling WebView.

### Provider action rationale and recovery

Owner/boundary: owner-authorized, Android-only TumiArt storefront 399989;
registration fields are reversible and scoped to that client. Prestate was
Public/Web with no callbacks. Official Shopify documentation directs native
clients to Public/Mobile and a shop.{shop_id}.* scheme. Editing the type did not
request rotation and left the same Client ID in the draft and saved state.

The only writes were (1) Public/Mobile type and (2) replacement of Shopify's
autogenerated generic callback with the reviewed exact callback. Poststate
Client ID and scopes match prestate. No catalog, menu, Home, customer, order,
payment, release or Gürbakır operation occurred. Restoring type/callback is
possible through the same UI; any future recovery must first inspect consumers
and preserve the current registration evidence.

## Necessary source correction and validation

The original Test-OnboardingShopifyCustomerUri permitted only shopify.com and
subdomains. Both discovery and local configured endpoint validation used it.
Shopify's valid custom account host therefore failed independently of the
2026-07/2026-10 question. Broadly permitting every HTTPS discovery host would
have broken the existing attacker-origin rejection.

The correction adds optional shopify.customerAccountOrigin to the independent
ignored provider binding. It must be a canonical HTTPS domain origin, with no
explicit port, path/trailing slash, query, fragment, userinfo, IP, localhost,
invalid TLD or noncanonical casing. A custom endpoint must match that origin
exactly; discovery cannot establish its own trust. Existing bindings omit the
field and retain the old Shopify-host behavior. Fixed issuer, HTTPS/default-port,
redirect blocking, capability, callback and configured-version checks remain.

| Changed file | Purpose |
|---|---|
| config/onboarding/provider-binding.schema.v1.json | Optional non-secret trusted origin, closed schema/type/shape constraints. |
| scripts/onboarding/Onboarding.Registry.psm1 | Strict optional origin parsing, including raw string type before conversion. |
| scripts/onboarding/Onboarding.Common.psm1 | Exact trusted origin predicate alongside unchanged Shopify-host path. |
| scripts/onboarding/Onboarding.CustomerAccount.psm1 | Pass independent origin into all discovered endpoint checks. |
| scripts/onboarding/Onboarding.Operator.psm1 | Apply the same rule to ignored configured endpoints. |
| scripts/Test-MultiBrandOnboarding.ps1 | Positive custom origin and hostile origin/type/schema regressions. |
| docs/architecture/BRAND-ONBOARDING.md | Trust and pinned-versus-discovered version explanation. |
| This handoff and docs/README.md | Current TumiArt preparation/next-phase authority. |

Independent review found that a one-element JSON array could be coerced to a
string by the importer while the JSON schema rejected it. That discrepancy was
reproduced, corrected, and retested for array/object/null/number/boolean inputs.
The corrected patch has no unresolved reviewer finding.

No Kotlin runtime, dependency, registry enrollment, generated projection, API
pin, manifest, signing, app identity or existing brand persistence was changed.
Temporary offline test fixtures were restored; final diff independently checked.

| Executed verification | Outcome and boundary |
|---|---|
| PS OperatorReadOnly regression before correction | RED: discovery endpoint mismatch, exit 1. |
| PS Registry final focused suite | 120/120, exit 0. |
| PS Test-MultiBrandOnboarding -Suite All | 310/310, exit 0; injected transport/process fixtures, no live provider writes or Gradle. |
| Invoke-MultiBrandOnboarding -Command Validate | PASS; registry/projections valid, absent ignored profiles remain UNCONFIGURED. |
| Get-RegisteredGradleTasks unit/assemble/api30/api23 -ValidateOnly | Each exit 0; enrollment structure checked, no test/device execution implied. |
| git diff --check | Exit 0. |
| Worktree spotlessCheck, pinned wrapper/Temurin17, offline | Exit 0; three formatting tasks from cache, checks up-to-date. This is not a new Android runtime run. |
| Test-PublicReadiness | 23/23; source/publication boundary validator, not production acceptance or a substitute for credential scanning. |
| Scoped actual-credential literal scan | Zero matches in current diff, new handoff and sanitized text artifacts; original credential-file hashes unchanged. |
| Fresh JVM baseline, primary unchanged SHA | 863 tests, 858 passed, 0 failures/errors, 5 explicit opt-in live Storefront skips. Account119, Foundation18, Storefront159 incl5skip, Mobile-core567. |
| Current Account schema validation | 10 operations x 2026-07/2026-10, 20/20 success, resolved versions equal requested versions; no mutation executed. |
| Current exact PDP operation + HomeImageFields | Real TumiArt Storefront 2026-07: 200/no errors, two dynamic options, 9 complete variants, 3 complete images, TRY, currentlyNotInStock readable. |
| Corrected real discovery + ignored configuration | PS7.6.5 PASS; eight-key configuration accepted; deliberately mismatched provisioning pin rejected. |

The exact fresh JVM command was:
pwsh -NoProfile -File out/tools/java/Invoke-ProjectGradle.ps1 --offline --no-daemon
--no-configuration-cache --console=plain --rerun-tasks :account:testDebugUnitTest
:foundation:testDebugUnitTest :storefront:testDebugUnitTest :mobile-core:testDebugUnitTest.
It executed 80 tasks and completed in 3m49s. JUnit machine totals were saved.
No Android instrumentation, TumiArt build/install, signed APK/AAB or release was
performed; this change affects PowerShell onboarding and documentation.

### Ignored local preparation

Created TumiArt-only config/local/tumiart/development.properties with exactly the
existing enabled Storefront/Account eight-key contract and no client secret.
Created development.providers.json with shop 80183034101, admin domain,
independently observed account origin, application/profile and evidence reference;
Firebase omitted for the proposed disabled profile. Original owner text files
were preserved. Both new files are ignored and are not packaged or committed.
Strict binding import and JSON-schema validation also passed; the two new files
use the operator's owner-only file-permission helper.

These are prepared inputs, not a registered application. Stage 4 must copy them
through the approved scoped local process into its own active checkout and enroll
their paths. Privileged Admin/Firebase credentials remain process-only and absent
from Gradle. Public token and Client ID values remain outside this handoff.

## Independent live catalog and website review

Current read-only observations reconfirm Admin 103 = 99 ACTIVE + 3 DRAFT +
1 UNLISTED. Global Storefront listing terminates after two pages with 99 distinct
products, all currently available, TRY prices, cdn.shopify.com featured images and
nonempty image alt text. UNLISTED/DRAFT are absent from normal listing. The supplied
direct UNLISTED buyability observation remains historical; no new purchase was
attempted. Do not infer that UNLISTED means private or remove those products.

tum-koleksiyonlar returns six typed Collection actions and six Ustalar artist
children, with usable image sources. Native projection will flatten bounded
depth-first items; it does not guarantee an expandable web-style submenu.
Availability and Price filters are returned by Storefront. Current native input/
output supports productType filtering only; sort modes have local Turkish labels.
This is a native scope gap, not a provider access failure.

Metaobjects mobile_home_v2 list returns zero nodes without scope errors.
Missing content is configuration/editorial work, not provider failure.
No definitions or live content were created.

Official web UI was inspected at desktop default 1280x720 and mobile 390x844.
Accepted current captures show the warm interior-art hero, roomy white layout,
portrait mobile campaign, collection imagery and print-benefit treatment. The
initial signup overlay was dismissed without submitting data. The in-app full-page
capture failed twice; accepted bounded captures plus loaded-image/DOM evidence
support the sections examined, not every carousel/lazy-loading state.

| Capture step | Health and design implication |
|---|---|
| 1 Desktop hero | Loaded, warm taupe/cream, restrained functional sans; hero lettering is inside art. Initial overlay capture is retained but rejected as an unobstructed design reference. |
| 2 Mobile hero | Loaded at 390x844, no document width overflow observed; separate portrait artwork/rendition. A 16:9 native banner cannot preserve all this baked text/CTA automatically. |
| 3 Mobile collection discovery | Loaded main card with partial next card; interior-photo subject/crop dominates. Native square collection cards require specific crop inspection. |
| 4 Mobile new-arrival/print-benefit region | Loaded visible artwork and print-benefit card; white/stone negative space and local prose. Multiple carousel states were not exhaustively checked. |

Hero and About image links have empty image alt text in the inspected DOM.
Native adaptation needs real semantic title/caption/CTA labels and useful image
description rather than relying on embedded typography. Screenshot/DOM findings
do not establish TalkBack, WCAG, contrast or all keyboard-flow compliance.

Observed source asset names requiring rights/provenance and rendition approval:
tumiart_ana_slider_banner_dekstop.png, tumiart_ana_slider_banner_mobil.png,
tumiart-hakkimizda-mobil.png, the displayed TumiArt logo, collection interior
photography and selected product artwork. Historical desktop About art also needs
an inventory check before use. Website publication or a classical artist's age
does not prove rights to a scan, print edition, photograph, logo or embedded font.
No asset was packaged. Font/color extraction candidates remain unapproved;
no automatic red accent or licensed font was adopted.

## Home feasibility and options

Current schema/root: mobile_home_v2, schema 2, pilot-media-v2; 0-5 ordered sections.
Maximum one collection grid (1-6 references), one featured product, two images
(banner/photo) and one video. Types are finite; targets are only Product/Collection.
Source: shopify-home-schema.v2.json; HomeV2ContentValidator; HomeEditorialSections.

| TumiArt web section/need | Existing native fit | Stage 4 disposition |
|---|---|---|
| Desktop/mobile bespoke hero | One image/banner + semantic title/caption and typed Collection action fits, but fixed 16:9 Crop does not fit portrait baked text | Commission/approve a crop-safe rendition; choose mobile asset explicitly in app-owned selection/content, not automatic website CSS behavior. |
| Intro/brand positioning | Bounded real caption/title possible; no arbitrary rich-text layout | Keep verified brand copy concise; native typography/tokens stay app-owned. |
| Five collection cards | One grid of five typed Collections fits; six including all-products also fits | Use current verified menu/collection identities and inspect square crops; exact card order is content. |
| Ustalardan Seçkiler product carousel | One featured product only; multiple product row unsupported | One curated artwork + Collection destination for first release; finite product-list extension only if approved requirement later. |
| Hakkımızda editorial banner | Second image/photo and short caption fit; Page target unsupported | Brand story can live in native app-owned Legal/Support/About navigation. Do not abuse Product/Collection fields to open an arbitrary URL. |
| Yeni Gelenler carousel | Typed link to yeni-eklenenler fits; multiple-product strip unsupported | Hero/collection destination for initial scope, not false carousel parity. |
| Print-experience/benefit row | Small verified caption fits; icon/text carousel unsupported | App-owned native explanation/support route if needed; not remote UI DSL. |
| Footer/legal/contact/marketing | Existing app-owned legal/support seam; marketing signup not a Home family | Map TumiArt-owned routes, avoid embedding the web newsletter/discount flow automatically. |
| Video | Optional single manual-play Video section | No demonstrated TumiArt need before Stage 4; keep out of initial scope. |

| Option | Ordered composition | Tradeoff |
|---|---|---|
| A: recommended | Crop-safe banner -> five-collection grid -> one featured artwork | Three sections, current contract, clear gallery character and discovery; accepted omission of web product rows/Page target needed. |
| B: more story | A -> second photo with concise verified print/story caption | Four sections, current contract, more asset/crop/editorial work; no Page action. |
| C: catalog first | Collection grid -> featured artwork | Two sections, minimal media dependency; suitable development fallback, weaker branded first impression. |

A is the smallest clean starting recommendation. No shared Home extension is
necessary before this task ends. If product rows or price/stock filters become
accepted requirements, implement a separate finite neutral contract with budgets,
backward compatibility and tests during Stage 4; do not silently claim parity.

Ordering follows declared section IDs, not native class names from Shopify.
Invalid sections may yield partial/none-renderable content; optional unresolved
targets lose navigation. Product availability and money are always rehydrated.
LKG has 24-hour editorial TTL and app/environment/store/root partitioning, max
64KiB snapshot and independent establishment marker. First-install packaged
fallback is allowed before remote establishment; expired/corrupt established state
must not resurrect packaged editorial as current merchant authority.

Media remains exact safe HTTPS cdn.shopify.com or configured merchant /cdn/shop/,
with per-redirect checks. Banner16:9, photo4:3 and featured16:10 use Crop;
catalog product tiles use Fit. Select renditions per usage, add accessible text,
inspect decoded size and loading on small/large-text screens. Image decoding
uses a 1600x1600 FIT request/decode bound (2,560,000 pixels) and an 8MiB
response limit. Admin original video
preflight is <=25MiB/1080p; runtime selects MP4 <=1280 max edge and <=1280x720
pixel area, with a 32MiB attempt response budget and bounded first-frame/buffering.
Those are different layers, not permission to stream arbitrary originals.

## Other commerce and integration seams

| Seam | Reuse and gap | Required future proof |
|---|---|---|
| PDP | Dynamic provider option sets, full variant accumulation and exact selected variant; current model lacks vendor/artist and structured paper guide | Actual artwork Fit/gallery, paper/size price transitions, unavailable combination, refresh and process restart; decide whether vendor/guide extension is required. |
| Search | Turkish app locale/history normalization is supported; provider query semantics separate | Dotted/dotless I, accents, mixed artist names, no results, pages and localized errors. |
| Wishlist | Device-local Room state, live hydration; no automatic Shopify account sync | Independent app database/partition and cross-app isolation, lifecycle and failure behavior. |
| Cart | Protected app-owned preferences/Keystore identity; current customer verification/quarantine/detach and mutation reconciliation | Isolated synthetic cart lifecycle and account transition once authorized; no live TumiArt cart mutation here. |
| Checkout | Existing Checkout Kit boundary/session-aware outcomes and allowlisted HTTPS origins | Exact TumiArt checkout URL, cancellation/reopen/completion cleanup and buyer linkage later; no order/payment test in this task. |
| Storefront permissions | Inventory scope unchecked; no production query requests quantityAvailable/totalAvailable; nested cart customer-read scope already enabled | No speculative shared scope change. Current PDP availability/backorder field read passed. Actual cart/checkout still untested. |
| Legal/support/deletion | App-owned typed legal/support routes and deletion request provider | TumiArt policies/contact readback, dedicated signed-out deletion URL, merchant intake/fulfillment owner; no copied Gürbakır policy or claimed remote erasure. |
| App Links | Public merchant/customer domains observed; domain ownership/signing association not proven | Canonical product/collection/order paths, exact association endpoint/fingerprints and device behavior at their later gate. OAuth custom scheme is a separate concern. |
| Firebase/notifications | App-owned provider selection; shared core remains provider-neutral | Recommend disabled/local defaults for first isolated development scaffold; decide owned Firebase/project/push/telemetry later, never inherit another brand's project. |
| Privacy | Gürbakır's Türkiye-only Shopify-hosted operating contract is scoped evidence | Confirm TumiArt market/SDK/data flows and merchant/app-provider/deployment duties. Do not automatically transfer a visitorConsent/Play declaration decision. |

## Stage 4 execution-ready blueprint

These are proposed reversible development choices, not approved permanent
production identities or existing source files.

| Input/path | Proposed implementation |
|---|---|
| Gradle application | Explicit :tumiart -> apps/tumiart in settings.gradle.kts, one application per APK; depends inward on mobile-core and selected provider interfaces. |
| Application shell | apps/tumiart/build.gradle.kts, src/main/AndroidManifest.xml, application/activity/Hilt composition and resources; independent com.tumiart.mobile namespace candidate. Keep shared historical packages unchanged. |
| Identity | Proposed developmentDebug application ID com.tumiart.mobile.development.debug; package owner/permanent release ID still unresolved. Do not reuse Gürbakır/Trial. Register only real environments; staging waits for its actual provider/isolation design. |
| Registry/projection | Add module/application/profile with complete CI enrollment to application-registry.v1.json; generated non-secret config/onboarding/generated/tumiart/development.properties through Update-OnboardingProjections, never hand-edit generated output. |
| Provider inputs | Storefront tumiart.com, Admin tumi-art.myshopify.com, shop80183034101, TR/TRY, Turkish locale tr/tr-TR; retain project2026-07 pins. Categories menu tum-koleksiyonlar. |
| Account | Reuse same preserved Public/Mobile Client ID privately; exact registered callback suffix tumiart, host oauth, path /callback. Match Gradle AppAuth receiver placeholder and route validator; scopes openid email customer-account-api:full. |
| Local files | Prepared ignored development.properties and development.providers.json; explicit independent account origin https://hesabim.tumiart.com. No client secret or privileged token in build inputs. |
| Persistence | Unique proposed tumiart-development Room filename, cart/customer preference names and Keystore aliases; validate registry-wide uniqueness before committing identities. Home partition receives exact app/environment/store/root. |
| Topology | Proposed HOME,CATEGORIES,SEARCH,WISHLIST,ACCOUNT with enabled shared capabilities; Legal/Support remains reachable. Match manifest external surface exactly. No runtime brand switch. |
| Home | Existing schema2/pilot-media-v2. root primary is a proposal and currently absent; develop app-owned five-collection/one-featured packaged fallback before any approved root provisioning. Merchant selects featured artwork and approved crops. |
| Theme/assets | Application-owned warm neutral tokens, restrained sans functional typography; initial system font is a reversible default. Owned logo/icon/splash and artwork require provenance/licensing. No new shared brand conditional. |
| Firebase | Disabled/local-default initial development proposal. Add a separate owned project/configuration only after a demonstrated requirement; shared core remains Firebase-free. |
| Legal/privacy | TumiArt-specific verified URLs and truthful support/deletion flow; app-provider vs merchant vs release/deployment owners recorded separately. |
| SDK/CI | Shared API23 coverage stays; choose TumiArt app floor with actual device/business/release policy. API24 is a reasonable proposal if Play Automatic Protection is intended, not inherited authority. |

Ordered work:
1. Integrate the reviewed custom-origin correction through normal protected PR,
   exact PR-head checks and merged-main verification.
2. Record reversible development identities, unique stores and intended capability
   topology; enroll explicit module/profile and every required CI task together.
3. Create the thin application shell/composition, configured fail-closed startup,
   Turkish resources and app-owned legal/support surface.
4. Implement the selected Home A/C fallback and brand tokens; approve asset rights,
   accessible text and crop-safe renditions before packaging.
5. Accept Categories/PDP/search/wishlist/cart lifecycle on exact TumiArt source;
   decide scoped price/availability/vendor enhancements rather than promising them.
6. Run read-only operator Inspect/Plan against trusted binding. Keep selected
   merchant Menu/Home validate-only. Any later Home provisioning requires approved
   content/provenance and a separate guarded provider-write/readback checkpoint.
7. After test-account/app/callback authorization, run Account login/cancel/wrong
   callback/restore/natural refresh/logout/signed-out restart and two-app isolation.
8. Accept Checkout Kit under synthetic scope without real order/payment, then
   separately enter release identity/signing/App Links/privacy/Play gates.

Focused acceptance:
- Registry/projection consistency, CI enrollment, no secrets, provider and data isolation.
- Account/Storefront/Foundation/Mobile-core/application JVM contracts; exact version schema check.
- Configured debug and minified release/package/manifest checks in the later scope.
- API30 and shared API23 lanes, plus the chosen app's minimum-SDK lane; no implicit removal.
- Home order/partial/empty/missing target, never-established fallback, established LKG expiry,
  renamed/unavailable commerce resources, media origin/size/decode and manual-play limits.
- Dynamic paper/size price choice, Turkish search/sort/filter copy, gallery ratios, loading/retry,
  TalkBack/large text/touch targets/insets and lifecycle screenshots from exact current source.
- Synthetic identity/cart isolation and checkout presentation/outcome integrity.
- Real TumiArt hosted Account lifecycle only at its explicit isolated execution gate.

## Review together before dependent Stage 4 work

The smallest product/merchant inputs are:
1. Home A vs B, featured artwork, approved logo/campaign/gallery rights and safe renditions.
2. Whether multi-product Home rows, price/stock filters and artist/paper-guide presentation
   are required for the first Android acceptance or deliberately deferred.
3. Permanent package/distribution owner and real environment plan; development names above
   remain proposals. Decide Firebase/notifications from actual scope.
4. Verified TumiArt support/legal/deletion route and accountable merchant processing;
   isolated test-customer/login execution details when the app exists.

These decisions do not justify a shared architecture rewrite or delay the
independent source integration/skeleton work. The provider registration gap is
closed; authenticated runtime and production acceptance are deliberately separate.

## Evidence artifacts and primary references

Sanitized local evidence is under ignored out/tumiart-stage3-20261014 in the
authorized primary checkout: provider-final-readback.json, local-preparation-readback.json,
current-admin-readback.json, current-storefront-readback.txt, current-pdp-readback.json,
customer-schema-validation.json, jvm-results.json, focused/full PS logs, independent
review, source patch, source reviews and accepted screenshots01/02/05/06/07/09.
Do not commit local credentials, old research ZIPs or commercial artwork.
The artifact manifest records exact file hashes/timestamps and accepted/rejected captures.

- [Shopify client setup](https://shopify.dev/docs/storefronts/headless/building-with-the-customer-account-api/getting-started):
  Public Mobile/custom callback and mobile logout distinction.
- [Customer Account reference](https://shopify.dev/docs/api/customer/latest):
  discovery uses the configured account domain/current supported version; a specific
  version can be constructed deliberately.
- [2026-10 removed incomplete-checkout fields](https://shopify.dev/changelog/posts/customer-account-api-last-incomplete-checkout-and-checkout-types-removed):
  current operations do not select that deprecated subtree.
- [AppAuth Android](https://github.com/openid/AppAuth-Android):
  public authorization-code/PKCE and external browser/Custom Tabs model.
- [OpenID Connect ID-token validation](https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation):
  direct token-endpoint TLS and claim-validation requirements.
- [Storefront scopes](https://shopify.dev/docs/api/usage/access-scopes) and
  [2026-07 QuantityRule](https://shopify.dev/docs/api/storefront/2026-07/objects/QuantityRule):
  current source does not require inventory-quantity permission.

Source pointers: account OAuth/session files; AppConfiguration; apps/trial manifest,
build and composition; shopify-home-schema.v2.json; HomeV2ContentValidator,
HomeContentRepository, HomeEditorialSections, HomeScreen, HomeVideoSourcePolicy;
StorefrontCatalogGateway/ApolloStorefrontCatalogGateway, ProductDetailPage,
VariantSelectionResolver; CartRepository/CartCoordinator/CheckoutController;
application-registry, provider binding schema, onboarding modules and
.github/workflows/android-foundation.yml. Exact baseline line references are
preserved in the ignored independent source reviews; candidate correction is the
immutable local commit accompanying this handoff.
