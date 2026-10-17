# TumiArt Stage 4 — native design direction and grouped roadmap

Decision date: **2026-10-17**. The owner accepted this **direction and execution strategy** after the V3.2 static acceptance and the P3 native catalog refinement.

**Authority classification:** This is the forward-looking **TumiArt-specific design and integration contract**. It does not change the accepted four-screen V3.2 static reference, existing runtime token values, Shopify source of commerce truth, module architecture, or P3/Android acceptance claims. The typography values below are **provisional design targets** to validate on real Android screens, not approved changed runtime tokens. The accepted historical Stage 4 plan in the owner's Downloads remains preserved as the original execution baseline; this document clarifies the later grouping and cross-screen design intent.

Read together with:
- [V3.2 selected visual contract](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md) — static visual target and image-rights restrictions.
- [P3 source and runtime evidence](TUMIART-STAGE-4-P3.md) — existing gallery behavior, commerce preservation and remaining technical limits.
- [P4 Codex implementation brief](TUMIART-STAGE-4-P4-CODEX-BRIEF.md) — executable next-task scope, prerequisites and verification.
- [Stage 3 preflight / Stage 4 blueprint](TUMIART-STAGE-3-PREFLIGHT-AND-STAGE-4-BLUEPRINT.md), architecture ADR-0004, and docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md.

## Current integration checkpoint — verify again before execution

As observed on 2026-10-17:
- Stage 4 P0/P1 merged through PR #47; P2 merged through PR #48, main SHA **358d78f4815fa41072a37238a5240a893004717e**, post-merge CI run **38929060046** passed its four jobs.
- P3 [PR #49](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/49) is **OPEN, DRAFT, UNMERGED**, head **b17235ccd8539bc70ba924c4beda0d24a0c4b4f6** before this documentation-only update. Exact-head [CI 38975642710](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/38975642710) passed **validate, instrumentation, minimum-sdk-instrumentation and native-16k**. Do not reuse this CI result for a later documentation commit or merger.
- P3 changed Collection Discovery and Collection Product Listing, and a later native refinement made the discovery copy and 4:5 covers more compact. It did **not** harmonize the entire application or implement P4/P5.
- P3 verified **12/12 configured merchant Menu destinations** (six roots and six artist children); Shopify Admin had **16 collections**. Four not present in that menu at the checkpoint: **En Çok Satanlar, Yeni Eklenenler, Minik Düşler, Van Gogh Koleksiyonu**. Menu completeness and Admin inventory are different claims. Decide separately whether to expose additional collections; do not silently invent native routes or change the merchant menu.
- Current known native issues: 320dp/2x primary navigation label ellipsis, and API 23 live Storefront TLS certificate-path failure. Fixture tests are not live API 23 proof.

This checkpoint is **historical after the next commit**; the next agent MUST re-check origin/main, PR #49, checked-out worktree, exact PR-head CI, and merged-main state before taking a dependency.

## Design north star: gallery label meets trustworthy commerce

A visitor should encounter an **entire artwork** with a calm, minimally framed gallery-like presentation, then a clear, concise label identifying the artwork and explaining the **real** available print variants and price. The signature is a restrained **artwork + readable exhibition label** rhythm, not a generic ecommerce card wall.

Retain the accepted palette: ivory **#FAF7F2**, stone **#E8DDD0**, ink **#302A26**, sage **#536A5D**, secondary slate **#586F87**. Material3 light/dark semantic color roles are currently owned by **apps/tumiart/src/main/kotlin/com/tumiart/mobile/brand/TumiArtBrand.kt** and implemented through foundation/CommerceTheme. Retain fonts from **TumiArtTypography.kt**: **Newsreader** for short editorial display, **Karla** for prices, products, options, buttons, information and forms.

Use established shape (8/10/12dp), spacing (4/8/16/24dp), and motion (150/220/300ms) as the first design vocabulary. Layout spacing conveys information structure, not decoration; prefer 16–20dp phone horizontal gutters, 4–8dp close label/detail spacing, 12–16dp group spacing and about 24dp between major sections. Maintain 48dp interactive targets and accessible focus, selection, error and disabled states.

**Avoid** enormous repeated headings, oversized inflated chips, nested cards, extraneous colored rules, decorative shadows, aggressive cropping of artwork, fake artist/year labels, and reducing the user's Android fontScale to improve a screenshot.

### Typography roles — 1x system scale, provisional for native evaluation

| Context / semantic role | Proposed type and size (sp/line sp) | Rule |
| --- | --- | --- |
| One short editorial thesis on Home or Discover | Newsreader Regular **24/30** | Not on every commerce screen |
| Editorial section heading | Newsreader Regular **18/24** | Sparse; do not make labels decorative |
| Native top bar | Karla SemiBold **16/22** | Keep native Up action and semantics |
| PDP full artwork title | Karla SemiBold **20/27** | Long Turkish title accessible without silent clipping |
| Two-column collection/product card title | Karla Medium/SemiBold **14/20** | Complete semantic name remains reachable |
| PDP selected-variant price | Karla SemiBold **17/24** | Exact variant and currency; never mini-caption |
| Card price / genuine price range | Karla SemiBold **14/20** | Pricing remains legible |
| Normal details/print description | Karla Regular **14/21** | Readable line measure |
| Secondary intro/instructions | Karla Regular **13/19** | Not for essential errors or prices |
| Option group name | Karla Medium **13/19** | Selected/unavailable clearly distinguished |
| Button / primary CTA text | Karla SemiBold **14/20** | Control remains at least 48dp |
| Optional, verified artist/series/kicker | Karla Medium **11–12/16–18** | Omit when source is not verified |

**Do not globally apply P3's screen-local font overrides across all screens**. Prefer semantic, brand-scoped presentation roles consuming existing theme tokens; if a durable token change is justified by native evidence, update the canonical TumiArt runtime owner and this contract **together** in the implementing PR. Neither this document nor a new root DESIGN.md should redefine all brands. No second independently maintained runtime token source.

Text stays left-aligned where appropriate. Improve layout through line length, whitespace, image-to-label proportion and action density, **not** a blanket font-size reduction. Test actual Android non-linear font scaling at 1x, ~1.3x and 2x: text and controls must reflow, full names remain available, and the fixed purchase area must not cover options.

## Screen-level ownership

### P3 — Discovery / Collection Listing, preserve the existing investment

Discovery uses the compact V3.2 editorial intro and smaller four 4:5 secondary covers, two columns at normal phone size and one at large font scale; continuation exposes configured Menu descendants/remaining targets. Keep artwork **Fit** for product imagery, bounded Shopify routes and current prices. A later harmonization may adjust listing title, price, wishlist spacing and control density in a **TumiArt opt-in**, without broadening P3's data scope or silently importing excluded Admin collections.

### P4 — Artwork detail (PDP), highest-priority design and correctness surface

Order on a compact phone:
1. Secondary native top bar with Up, Wishlist/cart utilities, **no five-tab primary navigation**.
2. Large media gallery with **ContentScale.Fit** for artwork, aspect-ratio-safe reserved space, calm surface/mat, real counter and accessible full viewer/Back/focus. Keep memory/image request policy; do not force an arbitrary high-resolution image download.
3. If truly available from the typed Storefront contract, a quiet artist/series label; otherwise **omit** it (the current ProductDetailPage GraphQL query does not fetch a separate vendor/artist/year field).
4. Full product title (Karla, left), **real selected variant price / correct range**, availability.
5. Actual option groups supplied by Shopify (name/order/value), with compact but 48dp+ accessible selected/unavailable controls; never hardcode "paper" or "size" as universal data.
6. Reliable descriptive information and an inset-safe sticky price/purchase area that stacks at 2x; a disabled CTA clearly explains incomplete/unavailable selection.

**Canonical current owners:** mobile-core/.../product/ProductDetailScreen.kt, ProductCartAction.kt, VariantSelector.kt, ProductDetailViewModel.kt, VariantSelectionResolver.kt, ProductDetailRepository.kt and mobile-core/.../ui/PriceBlock.kt. Prefer reuse and narrow TumiArt-only presentation opt-in; default Gürbakır, Trial, Synthetic and checkout/payment contracts must not change. Real read-only TumiArt Admin samples included a Henri Matisse title and Geometric Balance A01 with multiple material/size combinations and different variant prices; this **does not prove Storefront publishability**.

**Safety invariants:** one/multiple/unselected/impossible/sold-out variants; variant selection after loading/rotation/restart; exact merchandise ID and quantity sent to cart; pricing/currency and inventory truth from selected Storefront variant; disabled/pending semantics; safe failure/retry; art Fit, gallery/zoom, back/focus restoration, portrait/landscape/media missing, sticky bar keyboard/system insets. Beautiful UI with wrong cart variant ID is a rejection.

### P5 — Home and restrained global harmonization

Home is the second major experience: short media hero, bounded collection previews, a featured artwork and a short, real print/story section **only when authorized data and route exist**. First characterize Home-v2's never-configured/invalid/expired/offline/fallback states. Do not enlarge the remote schema, create fabricated provider copy or treat historical design mock art as redistributable. Low-risk text density/spacing consistency across Search, Wishlist, Cart, Account and P3 list may share this PR **only when behavior does not change and the diff remains independently reviewable**.

### P6/P7 — commerce and integrated native acceptance

Do not fold OAuth/PKCE/session, Checkout Kit, payment, legal/deletion or production-provider side effects into a visual polish PR. Actual P6 functional gaps merit **targeted separate implementation** only when evidenced, not one automatic PR per planned feature. P7 performs cross-screen Android acceptance (320/360/393dp, wide/landscape, 1x/1.3x/2x, light/dark, Turkish/English, states, TalkBack, real Storefront read-only, API30/minSdk, owner review); Play/signing/release remain independent.

## Grouped PR / merge strategy — owner's 2026-10-17 decision

**A. Existing PR #49: P3.** Review, close owner P3 native/design issues, require its **latest exact-head** CI, then owner merge decision and exact **merged-main** CI. No new PR merely for this handoff.

**B. One next draft PR: P4 + TumiArt native design-system foundation.** Separate logical commits within the same PR: (i) brand-scoped visual-role mapping and design contract; (ii) native PDP gallery/variants/pricing/CTA with characterized tests; (iii) only closely related source/test consolidation. Do not pull Home, auth or checkout into this PR. Obtain an especially strong PDP visual/commerce acceptance.

**C. One later draft PR: P5 Home + low-risk cross-screen *presentation-only* harmonization.** Keep Home-v2 state/fallback characterization and Home implementation distinguishable from visual-only consistency changes. If real provider mutations, navigation refactoring, identity/payment changes or too broad a regression surface appear, stop and split at that risk boundary rather than forcing one huge PR.

**D. P6 only if real functional gaps justify another focused PR. E. P7 acceptance without an automatic new PR.**

The planned PR numbers (e.g., #50/#51) are **not reservations**. No agent should claim those numbers before GitHub allocates them. Each merged PR needs fresh exact-head and post-merge-main CI evidence.

## Codex interruption-safe delivery contract

For each milestone, save to an **ignored private evidence folder**: base/head SHA, source/branch/worktree status, changed filenames, tests actually run with exit codes and log paths, device configurations, Android capture manifest and hashes, unresolved limitations, and **next precise action**. Do not wipe previous evidence or assume an interrupted agent's partial implementation is complete. Before each continuation, inspect current worktree/status/logs and PR again. Never force-reset/rebase/clean or overwrite other worktrees to make progress.

Native visual acceptance is **with the owner**, not inferred from V3.2 browser pixels. Screenshots contain merchant imagery and must stay private unless publication rights are established. Shopify Admin/provider writes, live customer/order/payment actions, Play and production credentials remain outside these design PRs.

## Sources and limits

Current reviewed Kotlin/Compose sources, V3.2 acceptance, actual P3 API30 captures, the Stage 4 plan, and read-only connected TumiArt Admin product examples support this roadmap. Loaded frontend-design and frontend-design-premium design skills inform distinctive brand direction and system consistency, but **their web-specific UI rules do not override native Android ownership**. Official Android Material3, scalable-content/accessibility and Shopify Storefront ProductVariant docs define relevant platform/provider behavior.

This file records **design and planning acceptance only**. A Codex task must still independently confirm the mutable repository state and prove tests/captures on its own exact source SHA.

## 2026-10-17 P4 continuation

P3 prerequisite is verified closed: PR #49 merged as `c34acba633eef2ae6f939c123997c912fa90a7ac`; merged-main run `38988797933` passed all four required jobs. See the [P4 implementation and native evidence record](TUMIART-STAGE-4-P4.md) and [native role mapping](TUMIART-NATIVE-DESIGN-CONTEXT.md). Provisional PDP type roles were evaluated in native Android fixtures; source tokens remain canonical. Owner P4 visual/commerce acceptance is still required. P5 and conditional later work have not begun.
