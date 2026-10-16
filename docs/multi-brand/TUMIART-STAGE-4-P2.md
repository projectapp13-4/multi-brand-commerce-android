# TumiArt Stage 4 P2 — theme and typography

Date: 2026-10-15; local review reconciliation: 2026-10-16.
Status: focused P2 implemented locally; pre-PR refinement recorded below.
This is the focused visual foundation, not the collection, gallery, Home,
authenticated commerce or production-release acceptance slices.

## Baseline and plan

Fresh `origin/main`: `f0760a9bca441013c429b4e822a89dc62db38fbd`.
[PR #47](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/47)
merged P1 head `21c0e124f06792e702b0b8859e5cb9c7069d53c8`;
[merged-main run](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/38723028758)
passed validate, instrumentation, minimum-sdk-instrumentation and native-16k.
Those are baseline results, not P2 CI results. P1's older pending-merge wording
remains historical. This work uses separate branch `codex/tumiart-stage4-p2`.
The owner requests an uncommitted local handoff: no commit, push or PR.

1. Inspect current ownership, complete Stage 4 plan/design documents and exact
   static visual reference; retain the selected Contemporary Art Gallery direction.
2. Pin the provisional-palette difference with a failing JVM test; implement
   default-preserving font roles and complete color mapping.
3. Verify exact font sources/licenses/weights/Turkish cmap, then package only
   licensed static fonts in the TumiArt application.
4. Exercise real Compose typography, long Turkish text, controls, errors,
   insets and light/dark/font-scale cases without provider credentials.
5. Run affected and registered regressions, builds/static checks and source/
   package isolation; leave exact evidence and remaining limits for review.

## Authority and reference

Current repository architecture and security rules outrank design references.
The complete `TUMIART_ASAMA4_UYGULAMA_PLANI_V1_2026-10-15.md`, especially P2/PR-B,
`TUMIART_STAGE4_DESIGN_DECISION_V1_2026-10-14.md` and
`TUMIART_STAGE4_DESIGN_HANDOFF_V1_2026-10-14.md` were inspected.
The four-screen rendered reference and its HTML/CSS were actually inspected;
the asset manifest and reference-only assets were inventoried. SHA-256:

| Reference | SHA-256 |
|---|---|
| Final PNG | `4bcb9943be060d6a1c1f50e0a955d30ef2a5accaca4be6c336abdb4a14a25f41` |
| Final HTML | `2600dc4083844ac6803dd7cba15ae4de1f3610436d8a9d5dc9f76d6b878dab01` |
| ASSET-MANIFEST.csv | `6eb248d78a8d8d2f76aee952f2d5bc2ef0a738ae03eee8a21653063a7449085d` |

HTML's tiny captions, 40/46px controls, serif product headings and hero gradient
are not copied mechanically: functional commerce remains Karla, touch targets
remain at least 48dp, text scales normally, and no gradient or unlicensed art is
introduced. P2 does not claim to implement those four product screens.

## Ownership and compatibility

`BrandFontFamilies` contains explicit app-supplied functional/editorial Compose
families. All Material typography remains functional. Short editorial copy
opts into `LocalBrandEditorialTypography.hero/title`; it is not inferred from
the brand key, a string resource name, or every Material headline.
`MobileCoreApp` forwards an optional family binding to the shared theme.
`fontFamilyResourceName` remains compatibility metadata, not a font resolver.

TumiArt supplies Karla 400/500/600 and Newsreader 16pt 400 from its own `res/font`.
The unconfigured setup and configured shared app both receive the same binding.
Editorial hero/title are 28/34sp and 20/28sp; functional body is 14–16sp,
product/title-small 14/20sp, buttons 14/20sp, labels/support 12–13sp. Existing
shared components keep their functional semantic choices; later feature slices
must review their own final screen hierarchy.

The original theme aliased fixed colors and several surface roles. Sixteen
defaulted color fields permit explicit Material roles while preserving every
old alias for callers that omit them. Existing Gürbakır, Trial and Synthetic
configuration/source stays unchanged. Frozen theme tests cover every previous
Material color mapping, sizes, line heights and system-sans defaults; separate
tests cover overriding the new roles and keeping commerce headings functional.

TumiArt's light background/surface/text are FAF7F2/FFFDFA/302A26; primary is
536A5D with E4EBE1 selected containers. Stone E8DDD0 is a neutral container,
slate 586F87 is tertiary. Decorative taupe is deliberately not assigned a text
role. Dark background/surface/text are 201F1E/292725/F1EBE2 with B3CEB9 primary.
Both sets explicitly define all supported Material roles, containers, on-colors,
outlines, fixed roles, inverse roles, scrim and error colors. Fixed roles stay
identical across themes. `outlineVariant` is decorative, not an active-control
boundary or small-text color. Images receive no tint/filter changes.

Corners become 8/10/12dp. Existing spacing 4/8/16/24dp and motion 150/220/300ms
remain. No navigation, provider, persistence, registry, workflow, dependency or
lockfile change is required. Font binaries and copyright notices remain app-owned;
[provenance](TUMIART-FONT-PROVENANCE.md), ASSET-LICENSES and third-party notices
record their independent OFL grant. Font notices are readable offline through
setup and Legal/Support and remain referenced through release shrinking.

The non-exported debug-only `TumiArtThemeProbeActivity`, Compose previews and
offline specimen exercise the actual app theme without selecting artwork,
fabricating prices or requiring Shopify. They are absent from release and not
part of navigation. `TumiArtThemeTest` verifies hashes/glyphs/resolved faces,
unclipped text, touch size, local interactions and readable licenses.

## Local evidence and limits — checkpoint of 15 October 2026

Local P2 source implementation and validation continued after Codex's usage limit.
A subsequent independent review verified the exact worktree and on-disk evidence.
**No P2 commit, push or PR has occurred.** This is *not* a release- or
production-acceptance claim. At that checkpoint, the handoff requested owner
visual signoff; the later continuation below reconciles that wording.

Verified current-source local evidence (15 October 2026):

- Codex conservative-JVM final Gradle run: BUILD SUCCESSFUL, 922 task outcomes,
  including all registered 12 unit tasks, spotlessApply/spotlessCheck, detekt,
  lint, TumiArt debug/test/minified development-release APKs, legacy app debug
  builds and mobile-core androidTest assembly. Earlier failures in preserved
  logs (lint resource configuration, device fixture clipping and JVM native
  crash) preceded this successful run and were not silently discarded.
- Unit test XML summary: 1,048 total, 1,043 passed, 5 provider-dependent
  skipped, **zero failures and errors** across 12 registered tasks.
- TumiArt package validation: PASS 27/27 checks. The final debug and minified
  release packages contain all four licensed TTF resources and both OFL
  notices, verified by hash after R8 resource shrinking.
- Actual API23 final application instrumentation: OK (5 tests).
- Exact theme specimen matrix: 12/12 PASS — API23 and API30, light/dark,
  system fontScale 1.0/1.3/2.0. Each log reports OK (1 test) and
  INSTRUMENTATION_CODE -1. Screen captures and companion metadata in
  out/tumiart-p2/screenshots/ are debug-only Android runtime evidence, not
  screenshots of the planned four commerce destinations.
- Source-manifest SHA-256: 24/24 enumerated source files matched on independent
  readback. Digest 896954bd049ab37b8c0765f4c40900715c76cc637fd0aab58e87d80cd59121e4.
- Onboarding Registry: PASS 121/121; projections match registry; all four
  registered task lanes validate. Full mutating onboarding All was not rerun
  in this P2 workspace.
- Contrast audit in out/tumiart-p2/contrast.csv records the evaluated
  text/container combinations against 4.5:1 minimum. Kotlin theme regression
  tests cover legacy aliases, typography defaults and TumiArt color roles.
- An additional actual unconfigured API23 app startup was observed and its
  screenshot saved as screenshots/actual-tumiart-unconfigured-api23-light.png.
  This is a plain development setup screen; it is *not* the gallery Home UI.
- A final independent focused foundation/TumiArt JVM recheck is logged
  separately in out/tumiart-p2/assistant-review-focused-jvm-retry-stdout.log;
  exit 0 and BUILD SUCCESSFUL in 34s. Its focused test tasks were
  UP-TO-DATE; it is an incremental consistency check, not a fresh full
  unit test execution.

The source visuals follow the approved Contemporary Art Gallery *color/type
direction*: warm ivory, stone, sage, slate, Newsreader short editorial heading,
Karla functional labels, complete Light/Dark scheme. A mockup screenshot is not
an implementation screenshot. Four final storefront screens — Home,
collections, collection detail and product/artwork detail — are **not
implemented in P2** and remain P3/P4/P5 scope. The P2 theme specimen is a
development-only, non-exported test surface, not the intended customer screen.

Known qualified detail: packaged Karla does not contain Turkish lira U+20BA;
Android platform glyph fallback displayed it in real-device evidence. Every
selected Turkish letter is bundled; exact lira fallback face is not independently
identified. No price/commerce formatting change was introduced.

Font rights are limited to the pinned OFL 1.1 font binaries; the visual
reference's artwork, images, merchant logo, prices and real products are not
packaged or newly licensed by this PR-B preparation. No live TumiArt
Storefront/Customer Account, checkout or payment test is claimed. No production
signing/release work was performed.

At that checkpoint, the recorded next integration steps were owner visual
inspection, scoped corrections, diff review and later GitHub CI. They did not
authorize Git publishing. See the current continuation below.
Evidence and exact checkpoint map are in ignored out/tumiart-p2/HANDOFF.md.

## Local pre-PR review reconciliation — 16 October 2026

The Contemporary Art Gallery direction, palette, two fonts and four-screen
composition are already selected. The debug specimen is a diagnostic, not a
new design proposal requiring approval. Actual final Android commerce-screen
visual acceptance remains P3–P7 work. The current instruction is local only:
**no staging, commit, push, PR, merge or branch-protection change**.

Subsequent ChatGPT Step 3 verification extended only the Android test with
actual Karla Medium/SemiBold ink comparison. Its selected 24-path snapshot is
`242a2803d8698bf139567ea711310a18008b4e6d43f0fee6196eafc0bc324632`.
It added scoped API30 runtime regression: Synthetic 2 passes, Gürbakır 15
passes and Trial 1 pass. TumiArt theme tests on both APIs had 3 passes and
1 conditional pixel skip; explicit API23 setup pixel capture passed. Its JVM
run succeeded with 3 executed and 240 up-to-date task outcomes; this was not
a clean rerun of every test. The original matrix and package receipts retain
their original source/artifact identities.

The final local design assessment retained the production palette, fonts,
tokens and shared seam. Only the debug specimen and its instrumentation were
refined: a native toggleable checkbox row exposes its role and checked state;
primary/outline/field shapes explicitly consume the existing small corner;
an outlined reset action exercises enabled/disabled and secondary states.
The checkbox defect was reproduced by an Android assertion before fixing it.
Production source/resources, other brands, dependencies, locks, providers,
identities, CI registration and Home contracts were unchanged by this review.

New local verification passed: root Spotless/Detekt, TumiArt development-debug
lint, focused JVM tasks and debug/test assembly (317 task outcomes: 23 executed,
294 up-to-date; foundation tests cached, TumiArt JVM tests executed). Both APIs
passed all six TumiArt application tests with capture enabled. Normal theme
mode on each API passed three tests with one explicit pixel assumption skip;
API30 also exercised drawing-disabled ATD compatibility. Eight new Light/Dark
matrix cells at 1x and 2x have 16 captures with exact metadata; the original
1.3x evidence remains associated with the original specimen. Package checks
passed 27/27; font/notice bytes, public-readiness 23/23, portability 46/46 and
the redacted diff secret scan passed. The selected 24-path source digest is
`0416c3aa8f34591fb6e301be089bed3894e2700b65a7473989d654e647e576d5`;
it is an uncommitted source snapshot, not a Git commit or whole-diff hash.

Shape consumption is explicit: Material's default full button corner and
extra-small field corner do not automatically become the three brand shape
tokens. Future screen owners should use the appropriate theme shape, as the
existing shared state primary already does. No new generic control primitive
or global Material override was justified.

The complete new evidence and A–J handoff are under ignored
`out/tumiart-p2/TUMIART_P2_PRE_PR_FINAL_REFINEMENT_HANDOFF.md` and
`out/tumiart-p2/pre-pr-2026-10-16/`. They separate new runs from historical
evidence, include current manifests and screenshots, and assess Home (P5),
discovery/detail (P3) and artwork detail (P4). Proposed responsive stacking or
one-column accessibility layouts remain proposals for those stages; the
original HTML/PNG and selected design documents were not changed.

No TalkBack service was installed on the checked emulators. Semantics and
interaction evidence is available; spoken service navigation is still
unobserved. Final-screen IME, gallery, live prices, OAuth/cart/checkout,
performance and release acceptance retain their separate later boundaries.
There is no P2 GitHub CI result while the work remains unpublished.

## Subsequent selected visual target — V3.2 (2026-10-16)

After the 16 October local P2 review and separate design iterations, the user accepted
[Gallery V3.2](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md) as the static four-screen
visual implementation reference. This later design choice supersedes the 14 October
v1 PNG/HTML **only for the intended four-screen composition**, not for font/palette
rationale, native accessibility, provider behavior, artifact/asset licensing or P2 scope.
The local reference bundle is excluded by gitignore; it is not a public PR artifact.
No P3/P4/P5 Compose screen was built by P2 and no P2 PR-head/merged-main CI has run.
The original source-manifest/test snapshots remain unchanged and historically accurate.
