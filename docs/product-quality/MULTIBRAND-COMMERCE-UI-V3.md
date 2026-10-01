# Multi-Brand Base Commerce UI and Gür Bakır v3

Status: **baseline characterization in progress**, 2026-10-01.

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
Live Play readback is pending: Chrome is unavailable in this tool session,
and the in-app browser could not attach its Play Console page. No track or
audience change has been made.

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
| Product grids | `mobile-core/catalog/ProductCard.kt` and grid policy | Frameless square, fit-image tiles; two title lines; price; exceptional availability only |
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
are not application test results. No UI code/version change or release artifact
has yet been produced by this task.

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

## Completion evidence

Visual iterations, focused/broad executed tests, PR/merge/CI identifiers,
final exact-source screenshot index, signed AAB receipt and Closed Alpha
readback will be added as they occur. Until then, engineering acceptance,
visual acceptance and v3 release are **NOT COMPLETE**.
