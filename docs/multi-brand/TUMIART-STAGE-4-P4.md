# TumiArt Stage 4 P4 — native artwork detail candidate

Status: implementation prepared for **one draft PR** from verified P3 merged main; **do not merge**. Owner native visual and commerce acceptance remains open. P5 has not started.

## Verified base and scope

Base: `c34acba633eef2ae6f939c123997c912fa90a7ac`, P3 [PR #49](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/49). Independently rechecked [merged-main run 38988797933](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/38988797933): `validate`, `instrumentation`, `minimum-sdk-instrumentation`, `native-16k` all succeeded. This closes the earlier P3 prerequisite, without rewriting historical checkpoints.

The accepted [V3.2 target](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md), [native roadmap](TUMIART-STAGE-4-NATIVE-DESIGN-AND-ROADMAP.md) and [P4 brief](TUMIART-STAGE-4-P4-CODEX-BRIEF.md) govern this candidate. [Native design context](TUMIART-NATIVE-DESIGN-CONTEXT.md) maps the existing runtime owners. Merchant imagery and native captures remain private ignored evidence; no redistribution rights are inferred.

## Resulting behavior

TumiArt selects `ProductDetailPresentation.Gallery`; all other application compositions retain `Standard`. The existing TumiArt color/font resources remain canonical. Optional product typography roles adapt functional Karla at title 20/27sp, price 17/24sp, body 14/21sp, option 13/19sp and action 14/20sp. Material/editorial roles and other screens remain unchanged.

The secondary PDP has Up, Wishlist/cart utilities and the short Eser / Artwork title, without primary tabs. Artwork uses Fit in a neutral mat capped by actual usable window height. The full merchant title and live price/range lead the detail area. The gallery exposes media position, previous/next and expansion with 48dp controls. Its full-screen viewer supports bounded zoom/pan, accessible alternatives, image recovery, Back and artwork-opener focus restoration. Decoded intrinsic dimensions govern pan bounds when provider dimensions are absent. Large text reflows; narrow purchase controls stack; wide composition requires usable text width and retains scrolling/insets.

Existing `ProductDetailViewModel`, `VariantSelectionResolver`, typed repository/gateway, `VariantSelector`, `PriceBlock` and purchase bar still own commerce. Actual option names/order, exact variant ID, price/currency, availability, quantity one, restoration, pending guard and confirmed feedback remain authoritative. No artist/year or commercial claims are invented. No production repository, provider, GraphQL, navigation topology, OAuth, Checkout Kit, identity or signing contract changed.

## Validation and evidence

Local evidence is retained in ignored `out/tumiart-stage4-p4/`. `CHECKPOINT.md` records the interrupted/resumed work, failed attempts, final source generation and precise commands; failed or incomplete captures are excluded from the accepted screenshot manifest.

| Check | Observed result / boundary |
| --- | --- |
| Global format, Detekt, Android Lint, registered JVM and build lanes | Broad run succeeded: 922 tasks; 1,059 JVM cases, 1,054 passed, five opt-in provider proofs skipped, zero failures/errors. TumiArt debug/test/release, shared core release/test and other application debug builds succeeded. |
| Final test-harness revision | Format application, Detekt, TumiArt Lint and test APK build succeeded. The only changes after the broad run are evidence tests and their legacy screenshot adapter; runtime source and app APK remain identical. |
| Native Artwork behavior | Six mandatory cases passed on API30 and API23: functional typography, invalid/pending/unavailable purchase guards, missing media, viewer zoom/reset/Back/focus, actual media count/index, and missing-dimensions pan bounds. The pan regression was observed failing before its production correction. |
| Standard presentation regression | Existing PDP, cart-attribution and `PriceBlock` tests: 28 passed on API30 and 28 on API23 using the unchanged shared test APK. No Standard/default application selects Gallery. |
| Actual Android16 text scaling | Six Artwork behavior tests passed on API36. Six additional native capture runs passed at real system scales 1x/1.3x/2x in Turkish and English, including viewer/error controls. No density/configuration override; observed 20sp title conversion was 55 / 64.9 / 93.5px at 440dpi. |
| TalkBack where installed | API36 TalkBack 16.0 was bound with touch exploration enabled; artwork focus and viewer open/Back were observed. **PARTIAL**: injected keyboard/swipe input did not establish sequential TalkBack traversal, and spoken output was not assessed. Physical/service-user acceptance remains open. Accessibility settings were restored. |
| Real Storefront | API30 typed product reads and decoded media succeeded for three products, each with nine variants and two media. All 27 variant IDs, prices, currencies and availability values matched resolved quantity-one intents. Unselected/selected screenshots cover each phone width. This does not prove a live cart mutation. |
| Minimum-SDK live boundary | Fresh API23 product read failed with `CONNECTION`; the independent project HTTPS probe observed `SSLHandshakeException / CertificateException / CertPathValidatorException`. **BLOCKED** live acceptance; fixture success does not close it. No TLS trust bypass. |
| Repository/package boundaries | Public-readiness 23/23, portability 46 checks, onboarding 312/312; TumiArt package 27, Trial debug package 12 and Synthetic debug package 31 checks passed. Redacted public-source secret scan passed. |
| Independent source review | Two material findings (decoded-dimension pan bounds and Retry contrast) were corrected and re-reviewed; no remaining material source finding. This excludes physical-device, owner and CI acceptance. |

The private `source-apk-manifest.json` records normalized source inputs, separate runtime digest and complete APK SHA-256 values. The configured debug APK is `4358e6a67d78628968be6863102f01e8c352fa1034d105e3704f2a7b906c43f7`; installed application/test/core APKs were pulled and checked on API23. Captures retain their original test-source generation: the later API23 screenshot adapter does not relabel earlier API30 evidence. It preserves the exact rendered-pixel assertion, using native `screencap` only when legacy `UiAutomation` returns no bitmap.

`SCREENSHOT-MANIFEST.json` indexes 350 accepted native frames by API, dp width, actual/controlled font scale, locale, theme, image hash and source/APK provenance; separate TalkBack diagnostics retain their partial status. API30 covers 320/360/393dp and actual landscape, light/dark, Turkish/English and 1x/1.3x/2x; API23 adds the 393dp fixture matrix, and API36 adds actual system-font captures. The isolated fixture host controls body density/configuration, while Dialog uses the real system context. Those viewer frames are labelled with their actual system scale/locale. Separate native system-font captures and full-app 320dp/2x captures prove real large-text behavior without overriding Android scaling.

The full-app read-only journey uses ordinary Home → discovery → collection → product navigation and verifies the secondary top bar, real merchant media/options, purchase dock and insets. It performs no Add, Wishlist or cart action. A debug-emulator memory snapshot observed about 110 MiB total PSS and no swap; it is diagnostic evidence, not a physical-device performance or regression benchmark. Final draft-PR head SHA and all four CI conclusions belong to the PR readback; local checks do not substitute for them.

## Native comparison and limits

The V3.2 direction is translated into native controls, semantic colors and functional typography. Complete merchant artwork is fitted, including merchant room/mockup images; the app does not replace merchant data with static reference art. Unsupported artist/year lines and guide routes are omitted. The short native title avoids the observed 320dp/2x topbar ellipsis while retaining the complete title in the body. The bounded mat leaves title/price visible sooner; it does not crop art. State, scrolling and font-scale captures are separate from compact static mockups.

Read-only Storefront checks can prove product/media publication and selected-variant intent, not a successful live cart mutation, account, payment or checkout. No live cart/customer/order/Admin mutation was performed. Known API23 live TLS-chain failure remains a separate open issue; TLS validation is never bypassed. TalkBack acceptance depends on an available service/physical target and is not inferred from semantics tests. Final native visual/commerce acceptance belongs to the owner. Production/P3-16, P5 Home and later stages remain outside this PR.
