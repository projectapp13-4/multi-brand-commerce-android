# TumiArt Gallery readability and rhythm polish

Date: 2026-10-19. Bounded visual refinement from `main`
`f5d36115b639679999675bd7183ad2a70e13e8a2`, after PR #54.
Delivery is one **Draft PR**; owner native visual acceptance and merge remain separate.

## Evidence and design decisions

Both owner-supplied review documents were read completely. Their original phone
screenshots/video were not supplied as local media in this task; their observations
are attributed to that review, not presented as newly inspected phone evidence.
The accepted [V3.2 contract](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md),
[native context](TUMIART-NATIVE-DESIGN-CONTEXT.md) and P3/P4/P5 records were compared
with current source. The retained private V3.2 HTML was also found: its SHA-256
matches the accepted `3776d119...b4b1b` record, and its target screenshot was
inspected. This does not make browser pixels native acceptance.

| Finding | Current evidence and bounded response |
| --- | --- |
| Joined description sections | A read-only Storefront probe of nine Geometric Balance products returned 1,051-character descriptions with zero newlines and 13 structural HTML tags in each corresponding HTML field. Mapping preserves existing newline/tab characters. The loss precedes native rendering: Shopify documents `description` as single-line, HTML-stripped text. Retain existing words and recover source block boundaries through a bounded, inert native text adapter. |
| Detached favorite row | Source and failing native regressions confirm a separate Gallery action row before the label. Put the independent favorite target alongside the title; keep artwork Fit and full prices below the label. |
| Inconsistent Wishlist tiles | Wishlist previously used Standard tiles even under Gallery composition. Forward the existing opt-in policy; preserve Wishlist state, removal, clear and retry owners. |
| Cramped enlarged product labels | Drawing-enabled 360dp/1.3x captures showed excessive wrapping beside the favorite target. Collection and Wishlist use one column below 400dp at 1.3x or greater. Normal text keeps two columns; Discovery cover policy is unchanged. A native regression failed against the previous product policy before this correction. |
| Artist-list spacing | A native regression confirms the 20dp cover-grid gap also separates continuation links. Retain cover spacing explicitly and use contiguous accessible link rows. |
| Dominant sort border | Replace only the Gallery outline with a quiet native text button; selected value, menu entries and callbacks stay unchanged. |

The additive typed `descriptionHtml` selection does not replace the existing
plain description or change product/variant/cart contracts. Complete HTML is
retained only within 48,000 characters. Android `HtmlCompat` converts it to a
String; Gallery uses the result only within 12,000 characters and when its
non-whitespace content equals the authoritative plain field. This provides
paragraph separation, not a browser/HTML renderer, arbitrary styling or a new
content platform. Unsupported/non-equivalent input falls back without guessing.
No regex punctuation repair or merchant-content mutation is performed.

Palette, Karla/Newsreader roles and runtime tokens are unchanged. Long names and
prices retain native scaling and natural wrapping. Range endpoints keep their
actual amounts and currency; the en dash remains explicit. Standard, Search and
other application compositions retain their existing visual defaults.

## Verification

Private logs, source/APK hashes and screenshots are retained under ignored
`out/gallery-polish/`; original merchant/customer media are not published.

- The registered JVM lane completed with 1,107 cases: 1,102 passed, five optional
  Storefront cases skipped, zero failures/errors. Global Detekt, affected
  Storefront/mobile-core/TumiArt Lint and all four applications' debug builds
  passed. After the final product-column delta, 584 core JVM cases passed with
  zero skips/failures/errors; formatting, global Detekt, core/TumiArt Lint and
  TumiArt app/test builds passed again (320 Gradle tasks, exit 0).
- Drawing-enabled API36: Catalog/Artwork 21 cases and shared Standard
  Catalog/Wishlist/PDP/cart-attribution/price/description 46 cases passed before
  the final product-column delta. Final shared parser/price/Wishlist checks
  passed 11 cases on API36; parser/price passed five on API23. These are fixture
  regressions, not authenticated Shopify/Checkout acceptance.
- After the final adaptive delta, all 22 TumiArt Catalog/Artwork native cases
  passed again on drawing-enabled API36, including the new 1.3x product reflow
  regression and unchanged two-column Discovery-cover behavior.
- TumiArt, Synthetic and Trial debug package validators passed 13, 31 and 12
  checks respectively. Public-readiness and portability results remain distinct
  from release readiness.
- The opt-in native capture harness uses owned geometric media and actual system
  settings, without a composition density override. The matrix covers
  393dp/1x/light/English, 360dp/1x/light/Turkish,
  360dp/1.3x/dark/Turkish and 320dp/2x/light/Turkish. Each run captures Collection
  labels/bottom, Wishlist, artist continuation and PDP description; logs assert
  complete text/range endpoints, independent favorite actions and 48dp controls.
  All four final-source runs passed (one capture/assertion case each), producing
  20 valid native frames. Revised 1.3x Collection/Wishlist renders were inspected
  again, along with normal-width, large-text, artist-list and description frames.
  Source/APK/frame hashes and configuration results are recorded in the private
  artifact manifest. Earlier partial and pre-adaptive captures are excluded.

The last price could scroll fully above the real navigation-bar inset. Text
layout checks inspect actual glyph bounds, retained characters and ellipsis:
Compose's shrink-wrapped paragraph-width flag alone was not clipping evidence.
No permanent system-inset defect was reproduced and no inset patch was added.

The source-bound regressions failed before implementation for description
provenance, image-to-label distance and artist-link spacing. An API30 ATD target
produced black screenshots; these are rejected as visual evidence. Rendering
checks use a separate drawing-enabled Android target. Static Premium audit alone
does not establish native behavior, accessibility or final visual acceptance.

## Open boundaries

The owner phone's exact locale, font scale and theme are unknown. Fixture screen
captures do not establish full-shell or original-phone inset acceptance. Account loading,
Search transitions and permanent system-inset defects are not inferred from a
single review frame. Full TalkBack spoken traversal remains a separate manual
acceptance item; semantics/layout assertions are not a substitute.

Home hero text repetition, similar collection covers and small text embedded in
the About banner remain merchant-content/creative observations. No imagery,
editorial content, menu route, provider configuration, account/OAuth, Checkout,
payment, production signing or release behavior is changed. Existing API23 live
TLS history and production/release gates are not closed by fixture tests.

Primary contract references:
[Shopify Product fields](https://shopify.dev/docs/api/storefront/2026-07/objects/Product),
[Android HtmlCompat](https://developer.android.com/reference/androidx/core/text/HtmlCompat).
