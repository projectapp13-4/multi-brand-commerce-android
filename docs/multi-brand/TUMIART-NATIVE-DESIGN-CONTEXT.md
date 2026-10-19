# TumiArt native design context

Scope: TumiArt only. The accepted [Gallery V3.2](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md),
[native roadmap](TUMIART-STAGE-4-NATIVE-DESIGN-AND-ROADMAP.md) and
[P4 brief](TUMIART-STAGE-4-P4-CODEX-BRIEF.md) govern this context. Native visual
acceptance remains a separate owner decision.

## Runtime ownership

The existing runtime token system is canonical. This document maps intent to
owners; it does not generate or independently redefine tokens.

| Role | Canonical value/binding owner | Adapter / consumer |
| --- | --- | --- |
| Light/dark semantic colors, shapes, spacing and motion | `apps/tumiart/.../brand/TumiArtBrand.kt` | `foundation/.../ui/CommerceTheme.kt`, native Material components |
| Karla functional / Newsreader editorial font resources | `apps/tumiart/.../brand/TumiArtTypography.kt` | `BrandFontFamilies`; product roles always use functional fonts |
| PDP title, price, body, option and action typography | `TumiArtBrand.kt` optional `productDetail` tokens | `BrandTypographyTokens.toProductTypography`, `LocalBrandProductTypography`; Gallery PDP only |
| Gallery presentation selection | TumiArt application composition | `ProductDetailPresentation.Gallery`; other applications default to `Standard` |
| Product/variant/price/currency/availability/media | Typed live Storefront product gateway and repository | Existing PDP state, resolver, `PriceBlock`, selector and purchase bar |
| Cart submission and feedback | Existing `ProductDetailViewModel` / `CartRepository` | Exact selected merchandise ID, quantity one, confirmed result and pending guard |

PDP functional targets start at title 20/27sp, price 17/24sp, body 14/21sp,
option label 13/19sp and action 14/20sp. They retain Android font scaling.
Material and editorial roles used by sibling screens are unchanged. Any future
token revision must change the runtime owner and this mapping together.

## Presentation and behavior

The signature is a complete artwork followed by a readable exhibition label.
Use the existing ivory/stone/sage/slate semantic roles, restrained small shapes,
16dp phone gutters, 8dp close label spacing and 16–24dp group spacing. Product
titles, prices and options are functional Karla. Newsreader is reserved for
short editorial content; the PDP does not invent an editorial headline.

The secondary destination retains Up, Wishlist when enabled, and cart utilities.
Primary navigation is absent. A stable neutral mat preserves artwork with Fit;
portrait, landscape and missing dimensions never authorize destructive Crop.
The accessible viewer offers bounded zoom/pan, explicit controls, Back and
opener focus restoration. The mat is capped at 420dp and 46% of the usable window height,
leaving the title/price visible sooner without cropping artwork. The short native
top-bar labels are Eser / Artwork; the complete merchant title stays in the body. Image failure has visible recovery; it does not
change purchase availability. Artwork requests reuse the existing trusted
media loader and size-resolved decoding; zoom reuses that decoded image without
initiating a higher-resolution request. Network transfer limits remain owned
by the existing media client.

Options retain merchant names/order and selected/unavailable semantics. A
partial, impossible or unavailable selection cannot purchase. Missing choices
are explained by the existing purchase summary. No artist/year, material guide,
commercial promise or inventory quantity is fabricated. Product description
is the sourced detail content. No unsupported guide destination is added.

Gallery detail additionally retains the Storefront `descriptionHtml` source to
recover real paragraph, heading-text and line-break boundaries as inert native
body text. Android's existing HTML-to-text adapter supplies boundaries only; no
WebView, remote styling, link action or image loading is introduced. Complete
HTML is bounded to 48,000 characters and converted text to 12,000; conversion is
used only when the non-whitespace content matches the existing plain description.
Missing, oversized or non-equivalent structure retains the plain-field fallback.
Standard detail continues to display that plain field. Source wording and
commerce values remain provider-owned.

Gallery Collection and Wishlist share the artwork-label presentation: fitted
media, a close title row with an independent 48dp favorite control, then the full
price/range. Two title lines reserve a baseline rhythm without limiting longer
names. A range places its complete localized endpoints on separate lines; it
does not substitute a minimum-only price. The catalog presentation policy is
forwarded to Wishlist; Search and Standard remain on their existing presentation.
At 1.3x text or greater, product grids below 400dp use one column to leave room
for the label and independent favorite target. Discovery's cover policy is unchanged.
Discovery keeps the cover spacing while its continuation links form contiguous
48dp-minimum rows. Collection sorting retains the native menu with a quiet text
button. See [scoped validation and limits](TUMIART-GALLERY-READABILITY-POLISH.md).

Wide layouts must leave usable width for both the art and purchase controls;
large text falls back to one scrollable column when needed. The purchase bar
reflows and respects system/keyboard insets. Controls retain at least 48dp
targets. Loading, missing product, connection failure, pending, adjusted cart
and failure feedback remain explicit.

## Verification boundary

Characterize exact variant/price/currency, selection restoration and pending
cart behavior before presentation edits. Verify Gallery and Standard separately,
including API30/API23 fixtures, narrow/landscape layouts, Turkish/English,
light/dark and 1x/1.3x/2x text. Private native screenshots and merchant readback
stay in ignored evidence; no merchant image redistribution right is inferred.
Known API23 live TLS-chain failure requires separate investigation without
trust bypass. Passing fixtures does not close that issue or establish release
readiness. P5 Home, OAuth/checkout and production work are outside P4.


## P5 Home continuation

P5 selects the neutral Gallery Home policy in TumiArt composition. Short section
headings reuse `LocalBrandEditorialTypography.title`; artwork names and the
provider minimum price reuse `LocalBrandProductTypography.title/price`.
Functional collection labels, feedback and actions retain Material/Karla roles.
No new type token or global theme change is required. Standard Home stays default.

Collection previews conservatively Fit both cover art and first-product media,
since the current Home contract does not expose that provenance. Featured artwork
uses the existing native artwork image loading/error/retry owner. Bound its mat
by actual Home viewport height after scaffold insets; headings and purchase-free
artwork actions remain separate lazy content so landscape/large text can scroll.
Only validated provider sections may supply hero/story images or editorial copy.

For the observed compact large-text bottom bar, TumiArt selects the existing
five native icons with a full-width selected-destination caption. Full accessible
names and 48dp targets remain on every tab. This replaces neither route topology
nor navigation actions. Native wrapped-word experiments were rejected after
inspection. Ordinary text, Standard navigation and other brands retain their
existing policy. See the [P5 record](TUMIART-STAGE-4-P5.md) for evidence boundaries.

The opt-in shell retains one content composition owner across primary/detail
routes, text-size policy changes and adaptive layouts. Its caption changes with
the visual policy; the existing Standard scaffold remains the default.
