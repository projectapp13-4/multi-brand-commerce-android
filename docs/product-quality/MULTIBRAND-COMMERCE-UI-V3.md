# Multi-Brand Base Commerce UI and Gür Bakır v3

Status: **iteration 3 UI accepted and merged; release-candidate gates open**, 2026-10-03.
The final reviewed UI candidate is visually accepted within the evidence limits
below. Shared UI engineering acceptance is complete; release-candidate native,
artifact and Closed Alpha acceptance remain incomplete.

## Scope and source authority

The owner's v3 brief authorizes shared source changes, PR/CI/merge work,
configured builds/signing, runtime validation and the existing Gür Bakır
Closed Alpha update. Public Production, Internal Testing, tester configuration,
Geldik Mi, unrelated merchant configuration, upload-key backup and Shopify
Support ticket 70706239 are outside this work.

The freshly verified local and remote baseline is
`b780e30fc439ebee58629e25ecebcd027539d17b`. PR #25 merged as
`232a0f451808d6f7921593eef458d2c0b554ae95`; PR #26 merged as the baseline.
Baseline Android foundation run `36743789533` reports success. These remote
facts were read on 2026-10-01; they are distinct from this task's executions.

The owner reports Closed Alpha package `com.gurbakir.mobile`, code 2,
name `0.2.0`. The existing local signed bundle was freshly hashed and matches
the expected SHA-256
`9142EDD2EB4CD9E512C6ED5BBC04A72758421F54334D4DA2E528E63252792DEB`.
Live Play readback is pending. The initial session could not attach its Play
Console page. On 2026-10-03 the resumed browser inventory exposes the existing
signed-in Edge Play Console tab, but attaching to that tab timed out. No track
or audience change has been made.

## Evidence classes

- Owner phone/reference screenshots: the owner's described visual problems
  guide investigation. The images themselves are not attached in this task.
  They are not exact-baseline runtime proof and no reference assets are used.
- Baseline runtime: fresh production-profile debug build and API 36 captures
  must precede UI implementation. Source, artifact hashes, device dimensions,
  locale and configuration are recorded with the local evidence package.
- Candidate runtime: captures carry the exact candidate source or patch
  digest. Fixture and configured public-provider evidence remain separate.
- Private surfaces: controlled synthetic Compose fixtures prove layout and
  interactions only. They do not establish live private Customer Account reads.
- Code inference: architectural ownership and state rules are traceable to
  source; runtime claims require executed evidence.

## Design and ownership decisions

The shopper's primary work is scanning imagery and prices, opening products,
understanding available options and buying. Account is a utility hub. Home is
editorial commerce. The shared base owns each hierarchy; application modules
own their tokens, identities, provider configuration and supplied content.

| Surface | Owner | Intended grammar |
| --- | --- | --- |
| Product grids | `mobile-core/catalog/ProductTile.kt` and grid policy | Frameless square, fit-image tiles; two title lines; price; exceptional availability only |
| Search | `mobile-core/search/SearchScreen.kt` | Search field starts the destination; separate submit and clear actions; secondary result/history metadata |
| Wishlist | `mobile-core/wishlist/WishlistScreen.kt` | Count and clear in one summary row, device-only note, shared grid |
| Shop | `mobile-core/catalog/CategoriesScreen.kt` | Compact navigation rows; supplied Menu labels and collections remain truthful |
| Home | `mobile-core/home` | Compact header/cart, manual editorial media, restrained section headings, frameless collections/products |
| Product | `mobile-core/product` | Compact secondary bar; swipeable fit-image media; product identity/price; wrapped options; compact purchase context/action |
| Account | `mobile-core/account` | Compact identity; section labels and light grouped rows; discoverable lower-emphasis deletion/logout |
| States | `mobile-core/ui/CommerceStateComponents.kt` | One icon/heading/body/action language with restrained container treatment |
| Navigation | `ProductionApp.kt`, `ui/AppNavigationItem.kt` | Existing adaptive NavigationSuite and all five destinations; low-contrast selected treatment |
| Brand | `foundation` contracts and each application | Existing finite tokens; no brand-key branches or screen copies |
| Locale | `AppLocale.kt`, application context/configuration | Production `tr`-only policy tested on an English device; fix only a reproduced defect |

No architecture redesign, runtime merchant switch, decorative font, fabricated
logo, automatic video playback, or merchant Menu/catalog rewrite is planned.
The existing owned palette and neutral sans-serif remain the starting point.
Square imagery is evaluated with `ContentScale.Fit` to preserve product edges.
Intermediate spacing is added only if runtime evidence shows a recurring gap;
all enrolled brands must explicitly satisfy any changed token contract.

Primary/secondary app bars use restrained title roles. Commerce tiles have no
filled outer Card. Utility groups use modest surface contrast and separators.
State panels retain recovery. Purchase UI is the strongest action surface.
Normal availability is silent; sold-out/backorder states remain explicit.

## Interaction and accessibility contract

All actions retain at least 48dp interaction targets. Decorative icons carry
no duplicate descriptions. Headings, prices, disabled option state and recovery
remain accessible without relying on color. Search IME and visible submission
use the same action; clear only clears. Wishlist clearing still confirms.
Variant wrapping changes presentation, preserving selection/provider truth.
The media viewer preserves Back, modal safety, focus restoration and accessible
navigation. The purchase dock never obscures body content. Large text reflows
grids and purchase controls; dark mode uses application tokens. Typed routes,
capabilities, protected stores and private-order capture protections remain.

Current official implementation guidance reviewed for this work:
[Compose pager](https://developer.android.com/develop/ui/compose/layouts/pager),
[flow layouts](https://developer.android.com/develop/ui/compose/layouts/flow),
and [accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## Execution and verification ledger

1. Verify mutable source/release baseline and preserve the primary checkout.
2. Build exact baseline, render configured public screens and existing private
   fixtures, inspect captures and record concrete defects before implementation.
3. Pin changed behavior with focused tests; implement shared browsing/search/
   wishlist/shop surfaces; render, critique and revise.
4. Implement shared Home, product media/options/dock and utility/state/nav
   hierarchy; render, critique and revise each major surface.
5. Reproduce Turkish production locale on an English device; cover any fix
   with regression tests. Capture dark and large-text representatives.
6. Run formatting/static/JVM/application/isolation/instrumentation gates,
   inspect final screenshots and perform focused trust-boundary review.
7. Create a coherent refinement PR; check exact-head CI/review; merge and
   verify merged-main CI. Keep v2 version values until acceptance is complete.
8. Prepare version 3/`0.3.0` as the final validated candidate, merge/check that
   source, build the signed exact-merged-source AAB, verify its full receipt,
   and upload only that bundle to existing Closed Alpha if the session permits.

Required runtime surfaces: Home top/lower, Shop, Search history/results,
Wishlist empty/populated, Account signed-out/authenticated fixture, Product
initial/options/description/valid/unavailable, Collection, Cart empty/populated
fixture, Help/Policies, Profile, Addresses/form, Orders/detail fixtures, dark
commerce/utility and large text. Compact acceptance is 360–420dp; API 23 and
API 30 deterministic lanes remain required independently of API 36 visuals.

Current local diagnostics: the workstation has approximately 6GiB RAM, so
builds and emulator captures are serialized. An unchanged LF-encoded Kotlin
DSL application script produced parse errors across its entire first line;
its error-reporting path subsequently exhausted the Java heap. A local CRLF
conversion compiled successfully and produces no Git content diff. Repository
line-ending policy remains unchanged. A fresh scoped Gradle home uses the
pinned wrapper and existing dependency cache. The onboarding validator rejects
the diagnostic short-path junction (`UNSAFE_PATH`); builds therefore use the
registered worktree path, where projection validation passes. These diagnostics
are not application test results. At this baseline checkpoint, no UI code,
version change or release artifact had been produced by this task.

Fresh baseline build completed successfully in 11m 23s: production debug app,
production debug instrumentation APK and shared-core instrumentation APK.
The app APK SHA-256 is
`088CA8CEF4F6D72D68D4CDDA6B1A30C68456110B353CA5C0FEC14A180F78A782`.
Manifest readback confirms `com.gurbakir.mobile.debug`, code 2, name `0.2.0`
and target API 36. This debug artifact is baseline evidence, not a v3 release.
The configured profile still declares `defaultLocale=tr`, `supportedLocales=tr`.
Eighteen readable baseline captures are indexed with SHA-256 in the ignored
`out/ui-v3/baseline/screenshots.json` evidence package in the primary checkout.
Runtime: API 36, 720x1600px, 320dpi (360dp), font scale 1, English `en-US`
system. The fresh configured production native shell renders Turkish. No locale
defect was reproduced. Authenticated Account, Profile, Address form and populated
Cart captures use the existing debug-only controlled fixtures; they are not
live private customer passes.

### Baseline visual review

| Observed defect | Runtime evidence | Shared ownership and direction |
| --- | --- | --- |
| Search shows only two complete priced tiles; three reserved title lines, 3:4 media, filled outer cards and normal availability consume space | `search-results.png`, `collection.png` | Shared product tile: square fit media, two title lines, frameless text, exceptional availability |
| Search duplicates its title/field and gives history settings equal emphasis | `search-empty.png` | Search entry starts the destination; explicit submit separate from clear; lower-emphasis settings |
| Wishlist clearing sits after the product and far from its count | `wishlist-populated.png` | Count/clear summary before grid; device-only disclosure remains secondary |
| Shop uses costly editorial-style square categories | `shop.png` | Compact collection navigation rows; preserve Menu order/labels |
| Home already renders its video first; header, section headings and play control are oversized | `home-top.png`, `home-lower.png` | Compact shared header/cart, restrained headings and manual play control |
| Product is already media-first, but generic title, grey fit-image side areas, large arrows and repeated price/helper compete | `product-initial.png`, `product-selected.png` | Compact action bar, pager, fit imagery on quiet background, concise options and dock |
| Product description is visible behind the dock's bottom navigation inset | `product-initial.png`, `product-description.png` | Dock surface must cover its safe inset; body remains fully scrollable above it |
| Account stacks large identity/tasks/support cards with nested padding; logout falls below viewport | `account-authenticated-fixture.png` | Compact identity and lighter grouped utility rows |
| Navigation's peach selected capsule dominates otherwise neutral content | Representative primary captures | Shared low-contrast selection; preserve adaptive suite, labels and targets |

Shop's repeated first image and `ÜRÜNLERİMİZ` entry are supplied Menu/collection
content, distinct from renderer defects. No merchant Menu/catalog mutation is
part of this work. Baseline Search captured the emulator-entered query `atav`
and four provider results; this is layout evidence, not a query-input verdict.
Capture acceptance requires a fresh matching screen hierarchy. Delayed activity
frames and a System UI startup ANR were diagnosed before accepting the final
baseline captures; screenshots use binary-safe `adb exec-out screencap`.

### Browsing iteration 1

Source `ce2f073e3b63b5f5529d0aa372966825e433d6d7`, still version 2/`0.2.0`.
Configured production-debug APK SHA-256:
`8055954744B4D44BE6E04240188FAB54BA01666A65BCA4D0DC8F8CFCCA0B99E7`.
Formatting, the five focused variant JVM tests and app/core instrumentation
assemblies passed. The four new browsing Android tests first failed against
the unchanged baseline for their intended differences. All 23 Search, Catalog,
Wishlist and Foundation component checks then passed on the settled API 36
emulator (61.344 seconds). A prior attempt also passed but encountered a
System UI startup ANR; its log is retained separately from the settled run.

Six reviewed runtime screenshots and hashes are indexed in
`out/ui-v3/iteration-1/screenshots.json` in the primary checkout. These are
configured public Storefront and local-device data evidence. Search now shows
four complete priced products rather than two in the baseline viewport, with
normal availability silent and sold-out state explicit. Categories use scanning
rows; Wishlist clearing sits beside its count and still requires confirmation.
Search begins with its entry field, has separate submit/clear controls and
lower-emphasis settings. Navigation selection is visibly quieter.

Review identified two remaining browsing defects: the reserved second title
line leaves too much space before short-title prices, and fitted category
thumbnails retain grey side areas. Both require revision before final acceptance.
Wishlist empty uses the shared compact state language. Collection shows the
same frameless tile with range pricing and more product content; its existing
filter controls and provider ordering are preserved.

### Product, editorial and utility iteration 2

Source `d0067e66c4e9a233964344210ca7458ada690a2d`, still version 2/`0.2.0`.
Configured production-debug APK SHA-256:
`DCF272081B6912222E70A949680B0EAF8A69885A8DBDC1F41F5B1BE125898C33`.
The installed emulator APK hash matches this receipt. Formatting, scoped app/core
detekt and production app/core instrumentation assemblies passed. Global detekt
with a selected production application was rejected by the enrollment guard
because it also selects Trial tasks; this is not a broad static-analysis pass.
The guard was preserved and the scoped tasks were used for this candidate.

Nineteen readable, reviewed captures are indexed in
`out/ui-v3/iteration-2/screenshots.json` in the primary checkout. Home retains
the typed video-first content and manual play, with a smaller header/cart action,
restrained section headings and an accessible pull-to-refresh action. Product
Detail retains media-first ordering, adds a swipe pager with quiet accessible
controls, concise FlowRow options and a compact purchase dock covering its safe
inset. Selected price and compare-at truth remain visible. The complete
description scrolls above the dock. Account uses a compact identity and grouped
rows; all account navigation and sign-out fit in the normal compact viewport.
Help uses rows rather than cards. Profile and Address retain their existing
protected editing and IME behavior with tighter presentation.

The runtime attempt ran 52 Product/Home/Account/Profile/Address/Order checks:
50 passed, two failed. One wrap assertion incorrectly assumed that three short
labels could not fit on the actual 360dp runtime; the revised fixture uses an
explicit compact width and a longer label. The other failure was an optional
Order fixture capture's PixelCopy timeout, not an order-routing assertion.
The fixture helper is being revised to use Android UiAutomation's native
runtime screenshot after the existing display/idle checks. These changes still
require a new executed pass. The separate production Locale/Help suite passed
all five checks. The configured Turkish-only policy also remains Turkish with
an unrelated English configuration; no locale defect was reproduced or
locale-policy change made.

Visual inspection found a real remaining defect: an entirely sold-out product
showed disabled size values but the dock still said to choose a size. The next
candidate must state unavailability instead. Account deletion also needs lower
emphasis, and Cart's checkout shape must match the shared purchase controls.
Collection's count/sort toolbar can use a common compact row while reflowing
at large text. These are review-driven revisions, not accepted final renders.

The pager, wrapping and optional fixture screenshot use existing pinned
dependencies/platform APIs. Primary contract references:
[Compose pager](https://developer.android.com/develop/ui/compose/layouts/pager),
[Flow layouts](https://developer.android.com/develop/ui/compose/layouts/flow),
[UiAutomation](https://developer.android.com/reference/android/app/UiAutomation).
No dependencies, design-token schema, brand layout forks, merchant content,
identity, App Link, signing or release configuration changed in this iteration.

### Interrupted-work recovery and iteration 3

On 2026-10-03 the existing native worktree, branch, both implementation commits
and all twelve interrupted iteration-3 edits were inspected and preserved.
Remote main still resolves to `b780e30fc439ebee58629e25ecebcd027539d17b`;
the baseline foundation run remains successful. There is no remote UI-v3 branch,
pull request or candidate CI run yet. The existing v2 bundle hash still matches
the receipt above. These are fresh readbacks, not candidate acceptance.

Iteration 3 addresses the reviewed sold-out dock, low-emphasis account deletion,
Cart purchase-control shape, Collection count/sort reflow and optional fixture
screenshot capture. The count is shopper-facing and reports loaded products;
the selected sort value remains visible. The native screenshot helper uses
UiAutomation only in opt-in controlled Android tests. Private production capture
protection is unchanged. New coverage checks disabled variant accessibility
state and a populated Collection toolbar at 200 percent text.

The resumed formatting/scoped static checks passed before the build's shared
JVM suite ran 237 tests: 236 passed and one old ViewModel expectation failed.
That test attempted a new sold-out Blue choice and expected it to be retained,
contrary to the accepted unavailable-choice rule. The corrected test starts
from the existing unavailable variant's supported inspection route, rejects an
impossible combination, and still verifies the exact sellable price/media.
A separate ViewModel test verifies that new sold-out choices are ignored.
The retry completed successfully in 5m 9s (245 tasks: 24 executed, 221
up-to-date). All 238 shared-core JVM tests passed; configured production-debug
app/app-test and core-test APKs assembled. The initial iteration-3 app APK is
`CED9B49BD8D92AD81D3BCAD72F2849280BC07B239B57DF503CAA17A991C43B7D`.
It is preserved with the `d0067e6` patch digest
`8F20CD6B0C45D0C92CB7EE469BF55B217EE401F73B27216F198DE8A9885A964E`
in `out/ui-v3/iteration-3/provenance.json`. This is not the final candidate.

The resumed API 36 cold boot timed out before framework startup; restoring its
existing snapshot also failed to become usable within the diagnostic window.
No black/startup frame is accepted as UI evidence. Only the owned emulator was
stopped; no device data was wiped. A modern 360dp Test Lab runtime is being
prepared with opt-in exact-app captures, existing private fixtures, dark and
200-percent text representatives. Runtime acceptance is still pending.

Independent source review found two additional shared defects requiring final
coverage: the refresh indicator can overlap Home's header, and a media button's
external index can be lost during pager motion. A top-inset-aware shared refresh
indicator and atomic pager synchronization address these finite presentation
contracts. Review also found a variant-selection trap when sellable combinations
are separated by unavailable combinations. A low-emphasis shared clear-selection
action provides a safe escape without making unavailable combinations selectable.
The review revision build passed in 8m 5s (245 tasks: 46 executed, 199
up-to-date), including formatting, scoped app/core detekt, all 244 shared-core
JVM tests with zero failures/skips, and configured production app/app-test and
core-test assemblies. The exact app APK SHA-256 is
`C845142D4948ED68CBAB3D9BCF5D15D275977A4DEB7A5BDD3A471C539546E241`.
Its patch/file/APK manifest is preserved in
`out/ui-v3/iteration-3-review/provenance.json`, including the new opt-in
application evidence suite. A fresh independent source review found no remaining
concrete defect; runtime validation is still required.

Firebase Test Lab accepted the application matrix `matrix-2t77kzy54d2h8` and
self-instrumenting shared-core matrix `matrix-14a27piena1xq` on SmallPhone.arm,
API 35, 720x1280 at 320dpi (360dp), English system locale. Both use the archived
exact APKs, opt-in UiAutomation captures and the existing project authorization.
The application suite includes public commerce journeys, controlled debug
private/cart fixtures, dark theme, 200-percent text and Locale/Help checks;
the core matrix selects the relevant UI/media suites. Submission is confirmed;
execution, readable screenshots and visual acceptance are not yet confirmed.

Both initial Test Lab executions completed with test failures, not launch
failures. Application XML reports 11 tests: nine passed and two failed; all five
Locale/Help checks passed. The evidence suite's enabled-purchase assertion failed
after a size tap, and its large-text product lookup targeted an uncomposed lazy
grid item. Shared-core XML and runner output agree on 109 tests: 99 passed, two
failed and eight skipped. Its media test assumed a full swipe could not settle
before Previous; the corrected partial-swipe fixture now proves its starting
state and captures the actual requested index. The Order large-text fixture now
scrolls its owning lazy list before querying an offscreen fulfillment. These
fixture corrections still require execution. Eight owned-video tests skipped
because no explicitly selected `ownedHomeMediaUrl` was supplied; no real-video
pass is claimed.

Twenty application PNGs with per-frame API/density/font/theme metadata and nine
controlled core-fixture PNGs were retrieved from the exact matrix GCS paths.
Their hashes and test reports are preserved under
`out/ui-v3/iteration-3-review/testlab-app` and `testlab-core`. Original-resolution
review confirms the calmer utility/cart controls, compact Collection count/sort,
image-led Home and readable dark commerce content. The Home lower capture keeps
its header but targets an oversized collection section, placing its section
heading above the viewport; the next capture targets that existing heading.

The short 640dp-high runtime exposed a real purchase-layout defect: Product
Detail's lazy viewport extended behind its dock. Scroll-to-visible left a variant
under the dock, so a native tap missed. Compact Product Detail now bounds the
viewport above the dock and has normal/200-percent-text native-click regression
fixtures. Cart showed its checkout action under the gesture area; its list is
likewise bounded by scaffold insets and its primary control has an explicit 48dp
minimum. Fresh captures must prove these revisions. Dark production captures
also show light window background behind white system-bar icons; the shared app
composition now applies the current brand canvas to the owned window background
and restores it on disposal. No brand conditionals, capture-security flags or
locale policy changed.

Broad formatting and all enrolled-module detekt passed after repairing the
temporary Windows build-script line endings. The lint lane then failed in
36m 42s with 14 reported errors: one count requiring plural grammar and obsolete
UI strings left by the refinements. The compact count now uses EN/TR plurals,
and the seven declaration-only obsolete identifiers were removed from app/core
translations. Lint has not yet passed for that repaired source. The memory
diagnostic confirmed pressure near the build's 512MiB metaspace limit; the next
invocation uses a scoped JVM limit rather than changing project/global settings.

Six executed Gitleaks 8.30.1 hygiene lanes passed with exit zero and no findings:
146 reachable commits at `d0067e6`, a 738-file tracked/new-test snapshot, patch and
new-test inputs, plus refreshed changed-source/patch inputs after the Product
viewport fix. Redacted logs and immutable receipts are under
`out/ui-v3/iteration-3-review/secret-hygiene-20261003-181932`. Later review repairs
still require final scanning; this is not final exact-head CI evidence.

Executed isolated validator self-tests passed: public readiness 20/20,
repository portability 68, Synthetic 49 and Trial 6. Projection validation and
all four registered-lane validations passed. The onboarding `-Suite All` attempt
failed with `DESTINATION_EXISTS` because its OperatorApply fixture refuses to
overwrite already configured ignored development properties. The suite's
temporary inputs restored byte-for-byte; tracked registry/projections are
unchanged, and no remote provider action occurred. This is a local harness
limitation, not an onboarding-All pass. Clean exact-source CI remains required.

The full suite then passed 255/255 in an independent captured source projection
with no ignored private inputs, 2026-10-03 14:43:56–14:45:11 UTC. Its 738-file
digest manifest, source/patch context and complete execution log are preserved
under `out/ui-v3/contracts-source-186c36aba57c458897f0cd218733043e` in the
primary checkout. Capture and post-suite byte drift are zero; original private
configuration hashes are unchanged. Both provider-binding and historical Home
restoration assertions passed. The earlier configured-harness failure remains
recorded separately. The successful log SHA-256 is
`821163F67422297A7E53F731800AD2EC43159CD796DDCB1DABB9038698B84723`.

The viewport/resource revision build passed in 14m 30s (245 tasks: 50
executed, 195 up-to-date), including scoped app/core detekt, formatting, all
244 shared-core JVM tests with zero failures/errors/skips, and the three
configured instrumentation/application assemblies. Its unreviewed preflight
APKs and exact patch/file manifest are preserved in
`out/ui-v3/iteration-3-viewport-preflight/provenance.json`. The production-debug
APK is `24BFAE99BAEA6879AF7C960698338F2AA5CAA08A54C6E6C0383623EF2891320A`.
No runtime pass is inferred from this build.

Read-only configured Home v2 readback established the selected merchant-owned
Video `gid://shopify/Video/40448681705711`, section
`gurbakir-pretester-video-v1`, under `mobile_home_v2/primary`. Its provider-returned
720x1080 MP4 responds with HTTP 200 and `video/mp4`. No redirect or canonical
CDN alias was observed. Sanitized ownership/schema/HEAD receipts are under
`out/ui-v3/iteration-3-review/owned-home-media-*.json`. The real-media tests
previously used a Trial-only merchant policy; their next run explicitly receives
the verified media URL and merchant domain through androidTest arguments.
Production media policy is unchanged. Final review also identified that debug
private fixtures enter the shared app shell directly; the native window canvas
policy is moving to that themed shell boundary, with a fixture-window assertion.
These last source/test revisions still require a new build and executed runtime
screenshots.

The final viewport/shell/media-fixture build then passed in 10m 24s (245 tasks:
30 executed, 215 up-to-date), including formatting, scoped detekt, all 244
shared-core JVM checks and all three APK assemblies. Exact source, patch and
APK hashes are preserved in `out/ui-v3/iteration-3-viewport/provenance.json`.
The app APK is `995B0805ACCA0B5F5BFC39387194DCA52F9A694F7383E47476BC16F83CA7DD31`.
Test Lab accepted application matrix `matrix-2dsynq4nhpbow` (all ten application
test classes) and core matrix `matrix-1anopw5y6da7y` (relevant UI/media suites,
with the explicit verified media URL/domain). Execution and visual review remain
pending; broad static verification is running separately.

A further read-only attachment to the existing Gür Bakır Play tab timed out
after 20 seconds and reset the browser-control session. No Console content or
track state was read and no external mutation occurred. The precise access
receipt is `out/ui-v3/iteration-3-viewport/play-console-access.json`. Source,
cloud runtime and CI work continue independently of that release-access limit.

The viewport matrices executed on a readable API 35 SmallPhone.arm runtime,
720x1280 pixels at 320dpi (360x640dp), English system language with the Turkish
production shell. Application results were 37 checks: 32 passed, four failed,
one skipped; core results were 113 checks: 111 passed, two failed, none skipped.
The eight explicit owned-media checks ran rather than skipping: seven passed,
and the lazy-viewport visibility check timed out after a scroll that did not
establish complete player invisibility. A measured-geometry fixture repair and
rerun are required before ruling out a playback defect.

The other failures locate uncomposed lazy items: Search clear after returning
to a scrolled grid, Cart checkout at 200-percent text in both app/core, and two
debug Foundation assertions assuming all four integration rows are composed
simultaneously. The latter fixture explicitly sets all integrations unready;
it is not a production configuration failure. Corrected tests will scroll the
owning lists before locating and asserting each child. Assertions will remain
strict; no passing result is inferred from this diagnosis.

All 31 application and 11 core PNGs were retrieved, hash-verified and visually
reviewed at their original resolution. Application review shows readable
wrapped Product choices above the dock, correct dark system-bar canvas/contrast,
quiet browsing/navigation, an intentional one/three-product Wishlist and a
complete normal-text Cart checkout control above the gesture inset. Account
deletion is lower emphasis; short-phone Account sign-out remains reachable by
scrolling rather than fitting the first viewport. Public commerce captures use
actual Storefront content; account/cart/profile/address/order captures are
controlled fixtures, not live private acceptance. Core fixture captures use a
neutral English test theme, including its separate native system-bar harness
limitation; they do not prove the production-brand private window appearance.

Search history and large-text Cart checkout were not captured because of the
above harness failures. The sold-out dock now rejects purchase correctly, but
its option-level wording is inaccurate for a completely sold-out product; a
distinct shared product-level message remains required. The 200-percent-text
primary bar retains the baseline selected-label-only policy and full accessible
labels; the selected Account label visibly ellipsizes. This is an inherited
visual limitation, not a new regression. Merchant menu duplicate imagery and
the all-products menu entry remain content/provider findings; no merchant
configuration was changed. Final visual acceptance is still open.

The later broad static invocation passed formatting and all enrolled-module
detekt, then continued lint analysis under severe local memory pressure. It was
interrupted with scoped Ctrl-C (exit 1) before lint completion. The task-owned
Gradle daemon was confirmed absent afterward; the subsequent process-stop
diagnostic found no process and performed no termination. No worktree reset or
source loss occurred. `iteration-3-broad-static-final.log` retains the incomplete
attempt; it is not a lint pass. The next per-invocation budget is 1536MiB heap /
768MiB metaspace with one worker, without changing project/global configuration.

The source/APK provenance and original screenshots are under
`out/ui-v3/iteration-3-viewport` in the primary checkout. The bounded independent
application review is `testlab-app/app-original-resolution-review-validation-inventory.md`;
JUnit reports retain every failure. These captures follow the earlier baseline,
iteration 1, iteration 2 and initial iteration-3 reviews; they do not replace
those historical records or imply a merged/released v3.

The final UI fixture/copy repair build passed in 22m 51s (245 tasks: 44 executed,
201 up-to-date), including formatting, scoped app/core detekt, all 244 shared-core
JVM checks with zero failures/errors/skips, and the three configured APK
assemblies. Immutable provenance is `out/ui-v3/iteration-3-final/provenance.json`:
HEAD `d0067e66c4e9a233964344210ca7458ada690a2d` plus patch SHA-256
`2821E8E8874804BEB1D72FFEACDCA3239AF0BC8D26D5A722A315EB73B5EB8E50`.
The application APK SHA-256 is
`8BD751D3C78C8ADDC2730B647C5964312B37E39C7258A5D389F5252347DB7A7F`;
the core test APK is
`70F2302952FBF20890E65DED753222E81C4A221C38ABCD04B606C39CD42B1EC7`.
Test Lab accepted application matrix `matrix-8d1ufmqeqsgqa` and core matrix
`matrix-1nxwt3q1yts0n`, using the same readable API 35 device and explicit owned
media inputs for core. The completed results and final review follow below.

### Final iteration-3 runtime review

Both exact-candidate API 35 matrices finished successfully. Application matrix
`matrix-8d1ufmqeqsgqa` ran 37 checks: 36 passed, zero failed/errored and one skipped.
The skip is the optional configured-Firebase Hilt proof, because its complete
four-file configuration is unavailable; this is not configured-Firebase proof.
Core matrix `matrix-1nxwt3q1yts0n` ran 113 checks: all passed, with no skips,
including the eight owned-media checks with explicit approved merchant inputs.
The previous lazy-list/screenshot fixture failures are closed by these executed
results; earlier failure reports remain preserved.

All 45 original 720x1280 PNGs were retrieved and hash-verified: 33 application
captures and 12 shared-core fixtures. Root reviewed the final critical commerce
states and all 12 core images at original resolution; an independent reviewer
inspected all 33 application images individually. Review found no remaining
blocking visual defect in the required commerce/utility set. Public/local
journeys comprise 24 application captures; nine application captures and all
12 core captures use controlled private/cart or neutral fixtures. No private
live-customer pass is claimed. Device geometry is 360x640dp, with representative
200-percent text and dark theme; the earlier baseline/iteration-1 density
comparison used 360x800dp, so complete-tile counts must not be compared across
those different heights.

The final review confirms image-led Home with manual video, frameless compact
products, quieter navigation, a direct Search entry with distinct submit/clear,
readable recent history, intentional empty/one/multiple-product Wishlist,
navigation-oriented Shop, compact collection count/sort, light Account rows,
and media-first Product Detail with wrapped options and a compact purchase dock.
Completely sold-out products now use product-level Turkish unavailability copy;
selected sellable variants alone show the selected dock price. Variant controls
and normal/200-percent checkout controls remain above the purchase/gesture
insets, with native-click regression checks passing. Public production-profile
journeys retain Turkish on an English system. No locale-policy change was needed.

The evidence includes Home top/lower, Shop, Search results/history, empty and
populated Wishlist, signed-out and fixture-authenticated Account, Product initial,
options/description/valid purchase/sold-out states, Collection, empty and fixture
Cart, Help, Profile, Address list/form and Order list/detail. Dark Home/Search/
Product/Account and large-text Home/Search/Product/Account/Cart are included.
Local evidence is `out/ui-v3/iteration-3-final/testlab-app` and `testlab-core`;
the complete filename/provenance index is
`screenshot-inventory-20261003-final.json`, SHA-256
`F91DB9884CB4C85D850E27AA15948290FF94ED79C355541F7AB65AC866381E39`.
The 83-file retrieval receipt is `cloud-evidence-retrieval-20261003-181014.json`,
SHA-256 `A6D19D1A25C2A24DC9E9A1F8D7CD989128312FE2420FBF4F170CF1094BE4E103`.
Original cloud evidence is retained in bucket
`test-lab-dujbp8ia2y6nw-n69jv46qj1jm8`, directories
`commerce-ui-v3-app-final-8bd751d3-20261003` and
`commerce-ui-v3-core-final-70f23029-20261003`.

Runtime histories:
- [Application final runtime](https://console.firebase.google.com/project/shopify-app-37295/testlab/histories/bh.f5db80769c5cd138/matrices/8432956476173282135)
- [Shared-core final runtime](https://console.firebase.google.com/project/shopify-app-37295/testlab/histories/bh.d1c23141f628e9f8/matrices/7592678078065178760)

Remaining bounded visual limitations are inherited large-text navigation's
selected-label policy and Account ellipsis, merchant-owned duplicate category
imagery/all-products menu entry, short-phone Account actions requiring scrolling,
and the neutral core fixture's system-bar harness appearance. None is disguised
as production private proof. No brand fork, merchant write or Xiaomi interaction
was introduced. Final UI source review found no actionable issue. Subsequent
documentation/native-probe-only changes do not alter the production UI captured
above; merged-source provenance still requires verification before release.

A read-only v2 AAB verification-helper self-check completed successfully without
changing its bytes, signing or uploading. It reconfirmed package/version/API
levels, cryptographic signature, bundle validation and native LOAD alignment.
The separate documented RELRO end-alignment check fails for the inherited
64-bit graphics-path 1.0.1 / DataStore 1.1.7 library entries. Their exact bytes
also appear in the archived iteration-3 App APK; this is inherited evidence,
not a newly introduced UI-library defect. Read-only inspection of official
stable AARs shows DataStore 1.2.1 passes both ELF criteria, while graphics-path
1.1.0 still fails the RELRO formula. No production dependency has been changed
based only on those results. A neutral, opt-in, self-instrumenting native probe
is being prepared to exercise public conic JNI and a synthetic multiprocess
DataStore operation on the already installed 16KiB image, with compatibility
mode disabled only for that test package. Runtime native acceptance remains open.

The native probe is now implemented exclusively in the standalone core test
source set. Its exact inherited graphics-path 1.0.1 and DataStore 1.1.7 inputs
do not add a production dependency edge. Gradle-generated locking adds six
coordinates/assignments only in Android-test graphs, including DataStore's
verified JVM lint variant and two Kotlin runtime transitives; independent
normalized audit found no removed assignment or production version/configuration
change. Existing dependency verification covers the artifacts; the JVM POM was
also compared with official Google Maven. Fresh 740-file source, baseline patch
and final lock/patch Gitleaks scans all exited zero with zero findings.

The first native build reached compilation but was stopped with scoped Ctrl-C
under severe host memory pressure; it is not a passing build. Targeted lock
resolution then passed in 1m 59s with zero failed dependency resolutions. The
reduced-budget retry passed in 3m 4s (139 tasks: 13 executed, 126 up-to-date),
including format, core detekt and standalone test assembly. No source/output
reset or global memory-setting change occurred. Immutable native probe APK
SHA-256 is `F459C9C5FBDCE9740B68962E2E4D0582F63F79F23834C753C80B4F9E42AFB59F`,
with provenance under `out/ui-v3/native-probe-final-20261003`. All 33 archived
non-documentation final UI/test source files still match; the new probe/manifest/
locking additions are separately reviewed.

The bounded native runner passed syntax review. Its first preflight rejected
decimal `64` from aapt2 because the helper expected hexadecimal; the compiled
manifest correctly uses SDK36's disabled enum. The helper was corrected without
changing the APK, and the isolated existing 16KiB AVD was launched read-only on
owned serial `emulator-5584`. ZIP alignment, native entry hashes and self-target
package identity passed preflight. Actual kernel/native method results remain
pending; no runtime or Play pass is inferred from those static checks.

Supported browser inventory/attachment repeatedly timed out before Play Console
could be read, including a fresh post-reset attempt. No alternative cookie or
browser transport was used and no publishing connector is available. The exact
read-only access limitation is saved under
`out/ui-v3/iteration-3-final/play-console-access-20261003.json`. Closed Alpha v2
availability remains owner-reported rather than newly read back; no track,
tester, Internal Testing, public Production or Geldik Mi mutation occurred.

## Source PR and engineering validation recovery

The preserved branch's third implementation commit is
`4bbdaba13031b3c6a1dc5c6cb330a03177f7fabe`. Focused source
[PR #34](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/34)
is open against freshly verified baseline `b780e30`; its independent exact-head
review found no actionable defect. It is not yet merged. Exact-head
[CI run 37146924719](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37146924719)
completed with API 30 and API 23 instrumentation jobs passing. The validate job
passed secret scanning, public-readiness checks and onboarding/enrollment, then
failed Android Lint with three errors: the window background constructor must
use the available KTX extension, and the two native-probe Android-test
coordinates must use the version catalog. Later JVM/package/portability steps
were skipped in that run, so it is not broad acceptance.

The corrections retain the same ColorDrawable behavior and exact dependency
versions; only the Android-test declarations consume the new catalog aliases.
No reviewed layout, production dependency or provider configuration changes.
Focused lint verification and new exact-head CI remain pending at this checkpoint.

The first isolated native runtime attempt timed out before full boot. A second,
smaller per-launch attempt also ran no native method. Investigation found that
the reduced runner incorrectly rejected the correct owned AVD name because ADB
returned doubled carriage returns. Actual kernel/PackageManager readiness was
therefore not measured; that attempt does not establish a native incompatibility.
Its emulator logs also report 4GiB guest RAM and 1080x2400 despite smaller launch
arguments. Both APK and AVD configuration were preserved; only the process tree
started by the runner was stopped. Native runtime acceptance remains open.

After host cleanup, supported browser inventory recovered and identified the
existing Gür Bakır Play Console tab. Binding that exact tab still timed out in
the browser driver's focus-emulation command before Console content could be
read. This is an access limitation, not a track readback. Receipt:
`out/ui-v3/iteration-3-final/play-console-access-after-cleanup-20261003.json`.
No version bump, v3 signing, upload, tester or other track mutation occurred.

## Accepted UI source merge

Corrected exact-head [CI 37148388803](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37148388803)
passed all three required jobs on `4b460c97823714a0ac7a18a63a9ac2435f9496e2`:
full formatting/detekt/Android Lint, registered JVM and assembly lanes,
public-readiness/onboarding, Synthetic and Trial package isolation,
repository portability, API 30 and API 23 instrumentation. The duplicate
local focused lint run was interrupted with scoped Ctrl-C under host memory
pressure after CI static analysis passed; its exit 1 is not recorded as a pass.

[PR #34](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/34)
was independently reviewed, marked ready and merged with an expected-head guard
as `6d70bb38cd29de16e0bf6cfddaf1a6623a2985f8`. Baseline main was freshly
verified unchanged before merge. Source and merged-main Git tree hashes both
equal `cca190c531e3eb1844ee016bcdf34bbacb0251c1`; the preserved worktree
fast-forwarded without changing that file tree or discarding pending work.
Exact merged-main [CI 37150312892](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37150312892)
passed all three required jobs, freshly observed at 20:25:54 UTC. The source UI's
engineering and bounded visual acceptance are complete.

The additional configured version-2 debug rebuild started at 20:10 UTC and
remained at `:mobile-core:kspDebugKotlin` with no log progress from 20:21:39
through 20:41 UTC. It was stopped only through its owned session (exit 1);
owned daemon PID 3588 was subsequently absent. Source and existing artifacts
were preserved. This duplicate build is interrupted, not a passing build.
It does not replace the already executed runtime acceptance or merged-main CI.

Only after the accepted UI merge and passing merged-main CI, the release
candidate source is set to versionCode 3 / versionName 0.3.0. The second focused
PR includes the finite native-runtime CI gate. No v3 bundle has been signed or
uploaded at this checkpoint.

A finite stable API 36 ps16k CI probe is being completed for the second release
candidate PR. It reuses the existing pinned setup actions/KVM and tests the
same inherited native bytes without production dependency or compatibility-mode
changes. Local boot/parser failures are preserved as harness/environment evidence;
they do not substitute for actual kernel/native-operation results. The new probe
has not run in CI at this checkpoint. ELF RELRO and ARM64 evidence remain separate.
The finite runner's Bash syntax, scoped diff check and 21 pure protocol/manifest
fixtures passed; independent source review found no remaining actionable issue.
Runner SHA-256: `D180BBBF3E5A1AFD1F1CB046751B7EF69616532487EC4619748005D6707BB7BE`.
Its actual execution requires kernel 16384, x86_64, owned AVD, responsive Android
services, application-scoped disabled compatibility, exact native bytes, and
two separate JNI operations with no skip, crash or timeout accepted as a pass.

Signing preparation has been reviewed without opening credentials or running
the helper. The ignored invoker requires exact fresh merged main, a clean
worktree and 3 / 0.3.0; signing material stays in process environment, heap
dumps/configuration caching are disabled, credentials are cleaned up, and
only its exact known source-byte rewrite can be restored. Unexpected edits
are preserved. The separate ignored AAB verifier's 19 pure parser fixtures
passed after fail-closed ELF/RELRO and exact unique signer checks were tightened.
Neither helper preparation nor those fixtures constitutes signing, artifact
validation, native execution, or Play proof.

## Version-3 candidate validation

The second source [PR #35](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/35)
opens candidate `8469f7019ec21ab55235eac8c57e01509aa8e010` against accepted
UI main `6d70bb3`. [Initial CI 37153164581](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37153164581)
passed full validation, API 30 and API 23. It also built the standalone native
APK successfully (132 tasks, 66 executed and 66
from cache). Manifest identity/disabled test compatibility, ZIP alignment and
both inherited x86_64 native hashes passed. The native job then failed before
boot because the emulator could not find the AVD registered by `avdmanager`.
Kernel page size and both native operations were unobserved. The harness now
gives both tools the same fresh, process-scoped `ANDROID_AVD_HOME` under the
runner's owned temporary evidence directory and verifies its registry file
before launch. No runtime acceptance checks are relaxed.

Independent binary parsing confirmed the literal RELRO arithmetic findings
without a `MemSiz`/parser error. For all four inherited 64-bit libraries, the
16KiB-rounded protection range has zero writable `PT_LOAD` content outside
the declared RELRO range. The [immutable Android 16 linker source](https://android.googlesource.com/platform/bionic/+/09a271af557444c9a6b3f3146d6d474156fd6cdb/linker/linker_phdr.cpp)
rounds touched pages; the documented crash mechanism requires content there
that must remain writable. That mechanism is not indicated by these exact
binary layouts. This is source/binary-derived evidence, not ARM64 runtime proof.
The original literal flags remain false. Official Maven/release readback still
finds no newer Graphics Path than 1.1.0; a forced upgrade is not justified by
this diagnostic alone. Final acceptance still requires actual native CI,
unchanged final native bytes and the separate signed-artifact/runtime checks.

Subsequent supported Play browser selection and a scoped new-tab attempt also
timed out before any Console content could be read. The new tab's creation
outcome is unknown; no release action was issued. Those access failures do not
establish a Closed Alpha or tester readback.

## Completion evidence

Visual iterations, focused/broad executed tests, screenshot provenance and
the accepted UI PR/merge/CI are recorded above. Candidate native-runtime CI,
final version-3 merged source, signed AAB receipt and Closed Alpha readback will
be added as they occur. Until those gates close, v3 release acceptance is
**NOT COMPLETE**. A passing x86_64 JNI probe cannot erase the separately recorded
inherited ARM64/static RELRO limitations or validate a final signed bundle.
