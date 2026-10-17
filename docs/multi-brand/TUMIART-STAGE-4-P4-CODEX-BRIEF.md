# Codex execution brief — TumiArt Stage 4 P4: artwork detail + design-system foundation

**Status:** Owner-approved scope, now implemented for draft review; see the [P4 evidence and handoff](TUMIART-STAGE-4-P4.md). This brief defines scope, not native acceptance. One focused **draft PR** with milestone commits; do **not merge**.

## Step 0 — immutable scope and current-state gate

Read the COMPLETE current:
- AGENTS.md and docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md;
- docs/README.md and docs/multi-brand/README.md;
- [Accepted V3.2 static target](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md);
- [New native design and grouped roadmap](TUMIART-STAGE-4-NATIVE-DESIGN-AND-ROADMAP.md) — owner accepted the overall direction and PR grouping, **not yet the final native type-size result**;
- [P3 implementation and known gaps](TUMIART-STAGE-4-P3.md);
- Stage 4 execution baseline, if separately available to the owner: TUMIART_ASAMA4_UYGULAMA_PLANI_V1_2026-10-15.md (historical local plan, **not distributed in public Git**). Its historical static-v1 and P2-unmerged statuses are superseded by verified later records where applicable. Do not assume a local workstation path exists on another machine.

**2026-10-17 prerequisite readback:** PR #49 merged as `c34acba633eef2ae6f939c123997c912fa90a7ac`; [merged-main run 38988797933](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/38988797933) passed all four jobs. This later verified readback supersedes the planning snapshot below.

**Prerequisite rule (historical planning snapshot retained):** Verify PR #49 has actually merged, obtain its merge SHA and fresh **merged-main** GitHub Actions results including all required checks. As of the 2026-10-17 planning checkpoint PR #49 was **draft/unmerged** and exact PR-head CI was green. Do not confuse those facts. If not merged, STOP at the P4 code-change boundary, report the precise blocker and do not advance on old origin/main by assumption. Ask the owner only if a different dependency strategy is genuinely desired.

After prerequisite passes, independently fetch fresh origin/main. Create a **new isolated P4 worktree/branch**, with an unambiguous base SHA and clean status. Never implement P4 in the P3 worktree, discard ignored evidence, force-reset another worktree or mutate protected main.

## P4 product outcome — gallery-grade art, trustworthy purchase

Implement only TumiArt's native Artwork Detail / Product Detail Page and the narrow TumiArt-native design-system foundation needed to support it.

1. **Design contract + token ownership:** Add/maintain a TumiArt-scoped durable native design-context mapping for the implementation. The runtime token owner remains apps/tumiart/src/main/kotlin/com/tumiart/mobile/brand/TumiArtBrand.kt and its CommerceTheme adapter; fonts are Karla/Newsreader from TumiArtTypography.kt. Work through a brand-selected presentation policy or similarly narrow opt-in. Preserve Standard/default visuals for Gürbakır, Trial and Synthetic. Do not create a global root DESIGN.md that imposes TumiArt on every brand or duplicate runtime token authority.
2. **Artwork-first PDP:** Use the accepted V3.2 directional composition and the newer [role/spacing targets](TUMIART-STAGE-4-NATIVE-DESIGN-AND-ROADMAP.md). In phone layout, preserve a native secondary Up top bar and utilities; no primary five-tab bottom navigation. Offer a calm neutral mat and ratio-preserving artwork display using Fit; visible media count, real accessible viewer, Back/focus restoration and reliable image states. Product title and actual price lead the functional detail area. Responsive tablet/landscape layouts and the sticky bar remain usable.
3. **Variant integrity:** Reuse existing ProductDetailScreen, ProductDetailViewModel, VariantSelectionResolver, VariantSelector, PriceBlock, ProductPurchaseBar, ProductDetailRepository and typed Storefront gateway rather than reimplementing checkout/cart state. Use **Shopify-provided option names, option-value order, price, currency, availability and selected exact merchandise variant ID**. Do not hardcode "paper"/"size" groups, sizes or prices; no fake variant defaults. One, multiple, unavailable and only-default-variant products must work. A disabled add-to-cart action explains missing/invalid selections. Preserve previous in-flight request and lifecycle safety.
4. **Honest metadata:** Current ProductDetailPage.graphql selects product title, description, options, variants and media but no separate vendor/artist/year fields. Do **not** insert faux "Matisse · 1952" text just because it appears in static V3.2. If meaningful metadata requires a provider contract extension, first verify source and Storefront publication in a small, neutral, tested seam; otherwise omit it from this PR.
5. **Accessibility and fidelity:** Proposed role sizes in the design document are 1x **starting points**, not absolute requirements. They must be checked in native UI at 320/360/393dp and fontScale 1x/1.3x/2x, light/dark, long Turkish text, TalkBack, 48dp targets, keyboard/gesture insets, missing/portrait/landscape media, loading/empty/offline and errors. Never constrain Android fontScale to improve compact screenshots.

**Exclude:** P5 Home changes, broad Search/Wishlist/Cart/Account restyling, merchant Shopify Admin writes, production identity/OAuth/payment/Checkout modifications, Play/release/signing, new unlicensed art/logo assets, and a generalized navigation rewrite.

## Characterization and regression tests

Before modifying commerce UI, identify existing invariants and source/test owners. Write the smallest appropriate characterization tests, preserving:
- Exact selected Shopify variant ID, price/currency, unavailable state and cart quantity, including variant switching and restoring from saved state.
- Correct selected option values with one-option and multiple-option products; no accidental partial selection purchase.
- No duplicate cart adds during pending state; feedback matches confirmed provider outcome.
- Media Fit (not destructive artwork Crop), viewer/Back/focus, keyboard/insets and missing media.
- API30/minSdk native UI and cross-brand Standard presentation/commerce behavior.
- Real read-only TumiArt Storefront evidence; the connected Shopify **Admin** variant examples are not proof that the same fields are published/usable to the customer-facing Storefront.
- Performance and memory behavior of artwork; no unconditional full-size downloads or new data fetch contracts without evidence.

Run format/static analysis/affected JVM/instrumentation/Android build and read-only runtime screenshots. Derive task names from the **current** repository rather than copying historical Gradle invocations blindly. Protect any merchant art images and screenshots inside ignored private evidence; never commit them without a distribution-rights decision.

## Interruption-safe milestones: checkpoint every time

Maintain an ignored private folder, e.g. out/tumiart-stage4-p4/, with:
- CHECKPOINT.md: target scope, current milestone, exact base/head/remote PR SHA, current branch, worktree path, git status, changed files, tests actually passed/failed/skipped, next action and honest blockers.
- At least one test log and deterministic fixture evidence per material stage.
- Device screenshot manifest (APK SHA, Android API, dp/fontScale/theme/locale, filename and image SHA), plus the current native versus V3.2 visual difference assessment.
- Any provider/API/schema assumptions, asset provenance, and pending owner visual decisions.

Suggested internal milestones **within one PR**: (1) design-context/native theme-policy seam + tests, (2) PDP presentation + tests, (3) real Android captures and accessibility/commerce review, (4) final independent diff + draft PR. No extra PR for each small typography adjustment.

If Codex usage limit interrupts the task, the next assistant should **first inspect checkpoint + actual git status + code + logs**, determine completed versus untested changes, and resume without staging unreviewed work or overwriting history.

## Review and delivery

Open **one protected draft PR**, with only scoped code, tests and public-safe project docs, once local validation is ready. No merge. Report exact commits, changed paths, CI/workflow links once available, behavior proven by tests, visual captures, unresolved differences, and cross-brand/regression proof. Owner and reviewer will assess PDP design **and** correct commerce; source formatting/green CI alone do not establish native visual acceptance.

Proceed to P5 only after P4 native/product acceptance and appropriate PR/merged-main CI. P5+low-risk cross-screen presentation harmonization is a **later separate combined PR**, contingent on Home-v2 source/fallback and external rights; P6 auth/checkout only if evidence warrants it.
