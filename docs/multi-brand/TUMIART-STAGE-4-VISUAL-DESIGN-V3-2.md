# TumiArt Stage 4 — accepted static visual target V3.2

Decision: **2026-10-16; accepted by the user in the project conversation.**
Scope: first **static four-screen visual acceptance only**.
Status: the static design decision is accepted; its implementation, PR integration and Android runtime acceptance are tracked and verified independently.

## Source of truth and version history

The current selected *visual design target* for the four planned Android screens is the **Gallery V3.2** candidate:

- Exact accepted HTML (unchanged source bytes): `out/tumiart-design-acceptance-review-v1/gallery-v3-2/index.html`.
- SHA-256: `3776d119699a1b09d1dc0c2d8f5e1624920f27b3f779cbf51c301619967b4b1b`.
- Local approval record: `out/tumiart-design-acceptance-review-v1/TUMIART_V3_2_FIRST_VISUAL_ACCEPTANCE_2026-10-16.md`.
- Local reproducible acceptance bundle: `out/tumiart-design-acceptance-review-v1/approved-v3.2/`. The bundle contains `SOURCE-V3.2.html` (byte-for-byte selected source), `index.html` (local-asset/offline-font reproduction), `manifest.json`, file hashes, screenshot evidence and local research assets.

All paths above are relative to the repository root and point to **ignored local evidence**. The public repository does not contain these asset bytes. A later CI, PR or different computer cannot inspect them without a separately retained authorized copy. Preserve a private local backup, and do not assert Git/CI artifact availability before such a copy exists.

The following remain historical reference/decision evidence; **their overall brand direction is retained but their four-screen layouts are superseded by V3.2**:

- `out/tumiart-design-final-v1/TUMIART-FINAL-VISUAL-REFERENCE.png`; SHA-256 `4bcb9943be060d6a1c1f50e0a955d30ef2a5accaca4be6c336abdb4a14a25f41`.
- `out/tumiart-design-final-v1/TUMIART-FINAL-VISUAL-REFERENCE.html`; SHA-256 `2600dc4083844ac6803dd7cba15ae4de1f3610436d8a9d5dc9f76d6b878dab01`.
- `TUMIART_STAGE4_DESIGN_DECISION_V1_2026-10-14.md` and `TUMIART_STAGE4_DESIGN_HANDOFF_V1_2026-10-14.md` (user's Downloads): 14 October design contract/history.
- `TUMIART_ASAMA4_UYGULAMA_PLANI_V1_2026-10-15.md` (user's Downloads): Stage 4 sequencing and acceptance plan. Its references to static v1 should now be read through the accepted V3.2 visual target, **not** as a change to P2/P3/P4/P5 scope or technical requirements.

Do not silently rewrite these historical documents or overwrite the v1 assets. Intermediate V2, V3 and V3.1 are comparison evidence, not the currently selected target.

## Accepted presentation contract

**Direction:** contemporary art gallery — warm, editorial and artwork-first, with recognizable, usable native Android commerce rather than a generic ecommerce card wall.

**Palette and typography:** ivory `#FAF7F2`, stone `#E8DDD0`, ink `#302A26`, restrained sage `#536A5D` as primary/discovery/selected accent, secondary slate `#586F87` for informative tools and guides. Newsreader is reserved for short editorial headings; Karla for all functional commerce copy, product names, prices and controls. Light and dark use distinct accessible roles as implemented under P2.

**Selected four-screen compositions:**

1. **Home / P5:** short warm room-photo hero, bounded collection preview, one featured artwork, short print/art story. Retain a concise four-section composition without changing the Home-v2 content authority/fallback schema.
2. **Collection discovery / P3:** a differentiated warm Ustalar lifestyle photo + editorial heading, four smaller 4:5 secondary collection covers (five **highlights**, not five total catalog items), and a restrained continuation for other collections/artist groupings/artworks *subject to verified menu and destinations*. Cover image and holder share the same boundary; no empty inset card/mat visible around a lifestyle cover.
3. **Collection detail / P3:** two-column normal gallery, legible names and prices, meaningful sorting/favorite affordances, art **Fit** with preserved ratio and optionally neutral mat, never destructive artwork cropping.
4. **Artwork detail / P4:** art-first ratio-safe gallery/zoom, current sourced variant and price/availability, clear secondary guides, stable purchase affordance and no five-tab primary bar on this secondary screen.

The accepted V3.1 restrained sage/slate distribution and V3.2 lightly softened category/card corners are part of this target. Do not reintroduce oversized rounded generic cards, cover-photo letterboxing, repeated accent stripes or unnecessary new surfaces.

## What acceptance does **not** prove

- **Native UI:** V3.2 is a Chrome/HTML design visualization; real Compose light/dark, fontScale, TalkBack, 48dp hit targets, keyboard/gesture insets, responsiveness, scroll and state presentation require later Android implementation and screenshot inspection.
- **Extreme navigation typography:** in the 320px / 2× static prototype all five tabs remain visible with deliberate line breaks. This expresses the no-clipping intent, not an approved Compose line-breaking algorithm or completed accessibility test. Choose a usable native adaptive policy and verify it against actual font scaling in the owning P3–P7 screens.
- **Dark logo:** V3.2 uses a browser `grayscale/invert/screen` blend to visually remove white image backing. This is a **provisional illustration** rather than the authorized Android asset/rendering algorithm. Prefer an approved transparent/light-on-dark logo or a separately verified native equivalent.
- **Catalog/navigation:** `Diğer koleksiyonlar`, `Sanatçı seçkileri` and `Tüm eserleri keşfet` are design intents, **not verified routes**. Revalidate the current Shopify `tum-koleksiyonlar` menu, typed actions, artist subtree and any actual all-artworks destination in P3. Historical category limits do not become a hard five-item product requirement.
- **Merchant content/rights:** artwork, lifestyle photography and logo in local research assets do **not** have established rights for bundling into public Git or an APK. Real media, product/artist metadata, sizes, prices and inventory must come from authorized sources. Font OFL rights do not extend to merchant images.
- **Real commerce states:** offline/empty/loading/error, sorting, long names, missing media, sold-out variants, zoom, selection, cart/checkout and Home-v2 unavailable/fallback are implemented and accepted in their owning slices, not by the static render.

## Roadmap and handoff

- **P0/P1:** merged baseline, PR #47. Historical P1 CI is not P2 CI.
- **P2/PR-B:** implementation, local theme/font testing and pre-PR refinement are complete per [P2 record](TUMIART-STAGE-4-P2.md); branch remains uncommitted until separate integration decision. V3.2 acceptance does not make final shopping screens part of P2.
- **Before PR-B:** independently check current HEAD, diff (including this documentation reconciliation), font license, scoped regressions and current GitHub main. Then PR-head CI, merge decision, and exact merged-main CI are separate controls; no stage can claim those were already run.
- **P3/PR-C:** live Shopify collection hierarchy and accurate navigation, native collection discovery and collection detail, compare Android screenshots against this V3.2 direction, obtain separate real-screen visual acceptance.
- **P4/PR-D:** artwork gallery, actual variants and purchase behavior, Android visual/runtime acceptance.
- **P5/PR-E:** Home composition and bounded Home-v2 first-open/fallback integrity, Android visual/runtime acceptance.
- **P6/P7:** remaining commerce/account flows, integrated runtime accessibility, cross-screen visual and performance acceptance.
- Production identity, asset rights, Play, signing and release remain separate gates.

**No Android source change, Git staging, commit, push, PR, merge or provider mutation is implied by this document.**
