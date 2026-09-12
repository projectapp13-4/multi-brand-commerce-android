# P3-01 Merchant Home and Market Input Packet

Date: 2026-08-10

Packet status: **APPROVED — P3-01 ENTRY GATE SATISFIED**

Implementation status: **IMPLEMENTED — SEE [P3-01 HANDOFF](P3-01-HANDOFF.md)**

This is the single merchant input packet required by [P3-00 Authoritative Handoff](P3-00-HANDOFF.md). It records current Shopify evidence and the owner-approved P3-01 Home, market, money, media, and temporary-design contracts. It changes no Shopify state. The continuous Phase 3 implementation goal supplied on 2026-08-10 authorizes P3-01 and all later independently implementable roadmap slices without reopening this gate.

The packet is governed by [P3-00 Content, Asset, Legal, Market, and Merchant Inventory](P3-00-CONTENT-ASSET-MARKET-INVENTORY.md) and [P3-00 Design System and UX Baseline](P3-00-DESIGN-SYSTEM-AND-UX.md). Missing evidence is not converted into a product claim.

## Evidence boundary and capture

Evidence was captured read-only on 2026-08-10 from the existing authenticated Gür Bakır Shopify Admin session and the public project-owned storefront. The Admin surfaces inspected were Home, Markets, the active Turkey market, Languages, Products, Collections, and each collection detail. The public storefront Home, one collection, and a narrow product sample were inspected to verify live merchandising, public handles, media delivery, accessible image labels, and displayed money behavior.

No Save, edit, create, install, export, import, customer, order, checkout, or configuration mutation was performed. No credential, cookie, token, private Admin store identifier, user account value, customer value, or private configuration is retained here. Public collection/product handles and public media origins are content identifiers, not credentials.

All counts below are an evidence snapshot, not values to hardcode. Runtime Shopify responses remain authoritative.

## Current Shopify facts

### Market and language

| Fact | Current observed state | P3-01 consequence |
|---|---|---|
| Active markets | Exactly one active market: Turkey | P3-01 supports Turkey only |
| Store default | Turkey is the store-default market | Startup resolves directly to Turkey; no setup screen |
| Market condition | Country/region is Turkey | No other country is inferred or silently added |
| Market currency | Turkish Lira | Expected currency code is `TRY` |
| Market catalog | Inherits all products | No market-specific product subset was observed |
| Storefront language | Turkish is default and published | Shopify catalog text is currently Turkish-authoritative |
| English storefront content | English is suggested in Admin but is not added/published | English app chrome must not imply that Shopify product/collection content is translated |
| Domain/language inheritance | Public owned domain inherits Turkish | App language and market remain separate |
| Tax signal | Admin states that sales tax is collected | This does **not** prove that displayed prices are tax-inclusive; no tax claim is permitted |

### Active catalog

- The Products table contained **41 products** and all 41 were `Active`.
- Every product was attributed to the Gür Bakır vendor and exposed to the same four-channel count in the Admin table.
- Product-type distribution was: 7 coffee pots, 7 teapots, 6 drinkware products, 5 pans, 4 pots, 4 snack/breakfast bowls, 4 fondue pans, 2 warmers, 1 deep plate, and 1 bottle.
- The Admin table exposed a primary image for all 41 products. All 41 image requests resolved from `cdn.shopify.com`; all 41 visible image labels matched their product titles.
- Two current public product pages sampled from different live Home/collection paths displayed `0.00 TL`. This narrow sample does not prove that all 41 prices are zero, but it is sufficient to make zero-price intent a commercial approval blocker. The live collection grid did not present product-card prices.
- One public editorial “special group” product link returned a not-found page. It is not an approved P3-01 source.

The app must never infer price, availability, discount, tax inclusion, shipping, scarcity, quality, health, durability, or purchasing policy from these observations.

### Collection inventory

All ten collection records were published to four channels and none had a collection image. Product counts, conditions, sort order, and handles were read from current Admin collection records.

| Collection | Public handle | Current products | Default sort | P3-01 disposition |
|---|---|---:|---|---|
| Bardaklar | `bardaklar` | 6 | Manual | Include in Product Range |
| Cezveler | `cezveler` | 7 | Manual | Include in Product Range |
| Tavalar ve Sahanlar | `tavalar-sahanlar` | 5 | Manual | Include in Product Range |
| Tencereler | `tencereler` | 4 | Manual | Include in Product Range |
| Özel Ürünlerimiz | `ozel-urunlerimiz` | 5 | Manual | Include in Product Range |
| Tabaklar ve Kaseler | `tabaklar-ve-kaseler` | 0 | Manual | Omit while empty |
| Hamam ve Banyo | `hamam-ve-banyo` | 0 | Relevance | Omit while empty |
| Sofra ve Sunum | `sunum` | 0 | Relevance | Omit while empty |
| Tüm Ürünler | `tum-urunler` | 0 | No effective product order | Omit; do not mistake for Shopify's system `/collections/all` path |
| Home page | `frontpage` | 0 | No effective product order | Legacy/empty; never use as the app Home source |

The current public storefront Product Range order is Bardaklar, Cezveler, Tavalar ve Sahanlar, Tencereler, Tabaklar ve Kaseler, and Özel Ürünlerimiz. P3-01 preserves that merchant order but removes the empty `tabaklar-ve-kaseler` tile at runtime.

## Approved P3-01 market and money contract

1. Initial and default supported market is Turkey only.
2. P3-01 has no user-facing market selector and no conditional setup screen.
3. Currency is `TRY`. Display `MoneyV2.amount` and `MoneyV2.currencyCode` returned by Shopify using locale-aware formatting; never parse a rendered string or replace the currency code with an assumption.
4. Do not display “tax included”, “tax excluded”, discount, shipping, delivery, or similar pricing claims unless a later approved owned source defines them.
5. App languages are Turkish and English. System app-language resolution follows P3-00: `tr` uses Turkish, `en` uses English, and unsupported locales use the Turkish baseline.
6. Changing app language never changes the market, currency, buyer identity, cart ownership, or Shopify request market context.
7. Shopify product titles, collection titles, descriptions, option names, and other merchant fields display exactly as returned. Because English is not currently published in Shopify, English app chrome may coexist with Turkish catalog content; the app does not machine-translate or invent English catalog data.
8. A zero money amount remains zero. The app must not silently hide it, substitute “contact us”, synthesize another price, or infer that a WhatsApp flow replaces Shopify commerce. The owner-approved `OA-04` decision permits neutral, locale-correct rendering of the Shopify-returned amount. Zero-price products remain merchant data-quality items that may be corrected later without blocking implementation.

## Approved exact P3-01 Home contract

Only the following two sections are permitted. Their stable IDs are code/config identities and do not change when labels change.

| Order | Stable ID | Shopify source | TR label | EN label | Item limit | Empty/error behavior |
|---:|---|---|---|---|---:|---|
| 10 | `HOME_PRODUCT_RANGE` | Ordered collection handles: `bardaklar`, `cezveler`, `tavalar-sahanlar`, `tencereler`, `ozel-urunlerimiz` | Ürün Gamımız | Our Product Range | 5 collection tiles | Drop each null, unpublished, empty, or unsafe-media tile; compact the order; hide the whole section if no valid tile remains |
| 20 | `HOME_FEATURED_PRODUCT` | `product(handle: "bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu")` | Öne Çıkan Ürün | Featured Product | 1 product | Hide if null, inactive/unavailable, missing safe media, or rejected by the approved zero-price decision; a failure in this section does not remove Product Range |

### Product Range item contract

| Item order | Handle | TR label | EN label | Current count | Media source |
|---:|---|---|---|---:|---|
| 1 | `bardaklar` | Bardaklar | Drinkware | 6 | `collection.image` when non-null; otherwise the first safe product media in Shopify collection-default order |
| 2 | `cezveler` | Cezveler | Turkish Coffee Pots | 7 | Same fallback rule |
| 3 | `tavalar-sahanlar` | Tavalar ve Sahanlar | Pans and Sahan Pans | 5 | Same fallback rule |
| 4 | `tencereler` | Tencereler | Pots | 4 | Same fallback rule |
| 5 | `ozel-urunlerimiz` | Özel Ürünlerimiz | Special Selection | 5 | Same fallback rule |

Counts are acceptance evidence only. A tile remains data-driven and is hidden when its current Shopify result is empty. No blank collection card, fake product, stock image, skeleton that resembles real content, or disabled future destination remains after loading.

### P3-01 interaction boundary

- In P3-01, Product Range tiles and the featured product are non-interactive content groups with no navigation affordance. Collection/list navigation becomes available only when P3-02 passes; product-detail navigation only when P3-04 passes.
- No disabled “coming soon” control is shown. Semantics must not announce a button or link before the destination exists.
- No cart badge, cart route, add-to-cart action, quick add, checkout, wishlist, search, categories, account, or other unfinished primary destination is exposed by this packet. Cart remains completely absent until P3-06.
- When a later slice enables a destination, it reuses the stable Shopify handle/identifier and updates the owning acceptance evidence; P3-01 does not pre-register a dead route.
- If both sections are genuinely empty, Home shows only the reviewed TR/EN no-content state and a retry action when the failure is transient. It does not offer Categories or Search before those destinations are functional.

## Content explicitly excluded from P3-01

The public storefront currently contains editorial reasons/claims, group slides, care-service copy, external marketplace/contact actions, a graphical logo, and other promotional material. Their presence proves only that they are published on the public storefront. It does not establish app-specific provenance, legal/commercial approval, translation approval, image focal/crop rules, or release ownership.

P3-01 therefore includes no hero, campaign artwork, “why Gür Bakır” claims, health/durability/performance claims, care-service promotion, external marketplace/WhatsApp CTA, editorial slideshow, public-site logo, policy content, or broken “special group” product link. Reopening any item requires an owned asset/content record and the appropriate later slice. Legal/support remains P3-08; final logo, icon, font, campaign, and store-listing assets remain P3-16.

## Media allowlist and rendering contract

### Verified allowlist

Only HTTPS URLs satisfying one of these exact rules are permitted for P3-01 Shopify media:

1. host exactly `cdn.shopify.com`; or
2. host exactly `gurbakir.com` and path begins exactly `/cdn/shop/`.

Subdomains, lookalike suffixes, HTTP, user-info URLs, arbitrary public-site paths, data/blob/file schemes, and redirects whose final origin is outside the allowlist are rejected. No other host is pre-approved. A newly returned Shopify media host is a contract change requiring evidence and an allowlist update, not a reason to weaken validation.

### Product truth, aspect, crop, and alt text

- Current public collection tiles use portrait source media observed at approximately `320 x 428`; the featured product uses the same source family at approximately `640 x 856`. P3-01 therefore uses a **3:4 media frame** with fit/center-inside behavior, a neutral token surface, and no aggressive crop.
- Do not crop handles, rims, bases, lids, product edges, or other identifying product geometry to fill a decorative frame. Do not upscale beyond the image loader's safe quality limit.
- Use Shopify `MediaImage.altText` when non-blank. Otherwise use the current Shopify product title. For a collection tile with no collection image, use the selected product media alt text; if absent, use the collection's approved localized label.
- Decorative wordmark/background treatment has no duplicate spoken description. Broken or rejected media retains the real text label and uses only the neutral non-marketing fallback defined by P3-00.
- No public editorial image, reference-APK asset, stock image, embedded claim, or third-party marketplace image is permitted.

## Approved temporary design baseline

For P3-01 only, the existing P3-00 light/dark color tokens, spacing/shape system, system/Material font, Material icons, and text `Gür Bakır` wordmark are the proposed approved baseline. They remain reversible token/content choices, not a final brand-kit claim.

Final logo, launcher/store icon, custom font, splash/campaign artwork, public-site graphical logo reuse, screenshots, listing artwork, and other release identity remain P3-16 inputs with ownership/provenance review.

## Final owner approval record

The continuous Phase 3 implementation goal supplied on 2026-08-10 approved the complete temporary merchant baseline and resolved every row below. This approval is sufficient for application implementation; final replaceable identity assets and later business-owned release inputs retain their own explicit gates.

| ID | Approved decision | Result |
|---|---|---|
| `OA-01` | Turkey-only/default market, no P3-01 market switch, `TRY`, Shopify money authority, and no invented tax/pricing claim | `APPROVED` |
| `OA-02` | TR/EN app languages remain independent of market; English app chrome may show current Turkish Shopify catalog fields until owned English Shopify translations exist | `APPROVED` |
| `OA-03` | Two-section Home order, stable IDs, sources, TR/EN labels, limits, empty behavior, and Product Range item order exactly as specified | `APPROVED` |
| `OA-04` | Render `0.00 TRY` neutrally and correctly when returned by Shopify; do not characterize it as free, promotional, discounted, or intentional; record it as a merchant data-quality item | `APPROVED — OPTION A WITH NEUTRAL WORDING` |
| `OA-05` | P3-01 content remains non-interactive until its destination-owning slices pass; no cart or incomplete destination is exposed | `APPROVED` |
| `OA-06` | Exact media allowlist, 3:4 fit/no-aggressive-crop behavior, and alt-text fallback chain | `APPROVED` |
| `OA-07` | Existing P3-00 tokens, system font, Material icons, and text wordmark are the temporary P3-01 baseline; final identity remains a replaceable release input | `APPROVED` |
| `OA-08` | Public storefront editorial/promotional/logo/service/external-action content and the broken product link are excluded from P3-01 | `APPROVED` |

## Gate result

The merchant input gate is **PASS**. P3-01 may begin without another owner checkpoint. Later Shopify price corrections remain data maintenance and require no application rewrite because Shopify money remains authoritative.

At this packet checkpoint no P3-01 source, route, ViewModel, repository, resource, schema, test, dependency, build, or Shopify/Firebase configuration change had started. The continuous Phase 3 goal now supersedes the earlier instruction to stop after this packet.
