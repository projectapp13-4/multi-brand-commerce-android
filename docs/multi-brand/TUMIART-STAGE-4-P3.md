# TumiArt Stage 4 P3 / PR-C — native catalog

Status: native implementation prepared for protected draft review, 2026-10-16. API 23 configured provider acceptance remains blocked below. No merge or native visual acceptance is claimed.

## Authority and scope

The owner selected [Gallery V3.2](TUMIART-STAGE-4-VISUAL-DESIGN-V3-2.md) as the final static target. Earlier candidates and pre-acceptance handoffs are historical. P2 merged through PR #48 as `358d78f4815fa41072a37238a5240a893004717e`; merged-main run `38929060046` passed all four jobs. These are baseline facts, not P3 validation.

P3 starts from that freshly fetched `origin/main` in an isolated worktree, on `codex/tumiart-stage4-p3`. It implements only Collection Discovery and Collection Product Listing. P2 tokens/fonts, Home-v2, PDP, identities, account, cart, checkout and provider configuration contracts remain outside this change.

## Implementation

- `CatalogPresentation.Standard` remains the default. TumiArt selects an application-owned Gallery policy and localized editorial resources. Shared code contains no brand-name branch. Search, Home and Wishlist tiles retain their existing presentation.
- Discovery adds raw menu depth and nearest accepted ancestor identity. Existing preorder, identity/destination deduplication and conflicts, filtered-action handling, descendant traversal and the 24-action / 64-node / depth-3 bounds remain authoritative.
- Gallery grouping selects eligible roots only: the configured featured root, up to four other covers, accessible child/remaining actions and the configured all-products root. Every projected action appears exactly once. Missing roots produce no fabricated link or promoted artist headline.
- Merchant menu labels remain visible labels. The existing mapper carries collection-versus-product media provenance and a bounded plain-text collection description. The query adds only `description(truncateAt: 280)`; the gateway, schema and media trust policy remain in use.
- Collection covers fill their frame. Product-image fallbacks and product artwork use `Fit`. The existing small shape token softens 4:5 media. No reference image, logo, font, dependency or theme-token changes are introduced.
- Listing retains its ViewModel, typed navigation, live price/range and availability, server sort/filter, pagination and error recovery. Gallery adds two normal phone columns, one at large text, complete titles, warm mats, a 48dp sort control and an independent 48dp Wishlist action below the image. Artist metadata is not fabricated.

## Provider evidence

Read-only Storefront confirmation on 2026-10-16 used `tum-koleksiyonlar` against `tumiart.com`, API `2026-07`. Six eligible root Collection actions remained in order: `ustalar`, `tumi-art-originals`, `modern-forms`, `zen-botanicals`, `cocuk-odasi-posterleri`, `tum-urunler`. Six artist Collection children remained under Ustalar. This independently confirms Storefront visibility; the brief's Admin observations do not replace it.

Ustalar, Originals and Modern Forms expose collection images. Zen and Çocuk currently use first-product media. These remain fitted rather than substituting the static prototype's lifestyle photographs. No Shopify mutation, account action, cart mutation, order or payment was performed.

The changed operation passed Shopify toolkit schema validation with resolved version `2026-07`, in addition to Apollo generation against the pinned schema. Controlled client configuration remains ignored and absent from this PR and its logs.

## Verification and evidence

| Check | Result and boundary |
| --- | --- |
| Broad local format / detekt / Android Lint / registered JVM lane | PASS: 1,047 passed cases, five existing Storefront skips, zero failures/errors. All registered application unit variants included. |
| Debug/release builds | PASS: shared core release and TumiArt debug/release/test APKs; other application debug builds. A final Windows G1 native JVM crash during R8 was recovered by a release run with process-only Serial GC and one worker. No tracked build setting changed. |
| Final TumiArt Android suite, API 30 and API 23 | No failures: 18 runner-reported cases per target; 16 methods exercised (11 catalog, three P2 font, two launch). The runner reports the one assumed/skipped live-capture method twice. The unconfigured-only launch branch was not exercised with configured inputs. The portrait/landscape edge test ran locally with `captureCatalog=true`. |
| Legacy catalog Android suite | 9/9 on API 30 and API 23 at approximately 391dp. At the API 23 device's physical 320dp, one unchanged test expects the second one-column tile to be simultaneously visible. The unchanged adaptive grid and test assumption were inspected; no layout or assertion was changed to mask it. |
| GraphQL / projection / package contracts | PASS: pinned 2026-07 schema, onboarding registry and lane enrollment, TumiArt package validator (27 checks) and validator self-tests (nine fixtures); Trial debug (12 checks), Synthetic debug (31 checks), public-readiness self-tests (20 fixtures) and portability self-tests (71 fixtures, including a positive Standard-default gallery opt-in and a negative cross-brand-default mutation). |
| Configured API 30 read-only native capture | PASS: 12 menu actions / six roots; 20 first-page, 20 next-page and 20 newest-sort products; ten decoded images, zero failures. |
| Configured API 23 live discovery | BLOCKED: `CONNECTION`; token-free HTTPS HEAD to the same project origin independently failed with `SSLHandshakeException / CertificateException / CertPathValidatorException`. No TLS/trust workaround was applied. Fixture UI success is not live provider proof. |
| Fresh independent code review | No confirmed actionable source finding; acceptance and runtime limits remain separate. |
| PR-head CI | Not yet published at this source checkpoint. Read the PR's checks and final handoff for the subsequent exact-head result. |

The final native main-source digest is `72b055526dad025486c4496672d7cda3250a0cdf332deb45056feaa9afb955e3` over canonical LF source/resource/query bytes and unchanged binary fonts (356 files). The ignored manifest records individual hashes; APK hashes and installed readback accompany final local evidence. Test-only capture-harness changes do not change this digest.

Evidence inventory:

- `out/tumiart-p3/verify-local-2.log`: broad successful lane; `verify-final-captures.log`: subsequent successful affected checks/builds; `verify-final-labels.log`: the interrupted G1 run; `verify-release-serial-gc.log`: successful final release retry.
- `tumiart-api30-final.log`, `tumiart-api23-final.log`, `legacy-api30.log`, `legacy-api23-391dp.log`: device results. The narrow legacy failure remains in `legacy-api23-final.log`.
- `fixtures-api30-final/` and `fixtures-api23-final/`: 11 offline state/geometry/interaction captures each, including owned portrait/landscape edge patterns and missing media.
- `live-api30-final/`: 30 Turkish native screenshots: light/dark × normal (~390dp)/1.3× (~390dp)/2× (320dp), discovery top/covers/continuation and listing top/art. These render actual repository data in an isolated native screen host with an explicitly empty test Wishlist; they do not assert a customer's live saved state or full-app navigation.
- `native/emulator-5556-final-app-*.png`: full-app English-system-locale captures of real discovery/listing in light/dark and at 320dp/2×. The large-font primary-tab ellipsis is retained as evidence.
- `live-gallery-api30-repaired.log`: successful configured capture; `live-gallery-api23-repaired.log`: explicit TLS-chain failure. Initial capture-harness failures are retained, not counted as passes. UI rendering was moved outside the coroutine and callbacks made class-owned before the successful API 30 rerun.

Compare these native frames with the separately retained accepted V3.2 captures. The native small shape token is 8dp; current merchant image/label choices and price ranges remain authoritative. The isolated 320dp screen host occupies part of a wider physical window; its right-side strip is not a product layout defect. Full-app frames establish window/inset behavior. Offline fixture frames establish state/geometry, not system-bar acceptance. Source/API checks and screenshot inspection do not substitute for owner acceptance.

Local evidence lives under ignored `out/tumiart-p3/` in this worktree. It is not available from public Git or CI. The accepted reference remains in the separately retained private V3.2 bundle; no merchant-image licensing grant is inferred from reference images or runtime readback.

## Remaining boundaries

- Independent native technical and visual review is required. Static V3.2 acceptance does not accept these Android screens.
- Merchant copy, media, price ranges and loaded-result counts differ from the static design. The catalog summary has no artist field; it is omitted.
- At 320dp/2×, the selected full-app primary-tab label is visibly ellipsized. This existing shared navigation issue has a captured, scoped follow-up: adapt primary-navigation label/height policy and verify all five destinations and TalkBack across brands. P3 does not refactor that navigation.
- API 23 live Storefront acceptance is blocked by the observed platform/provider TLS-chain validation failure; transport and trust configuration were not changed. Investigate certificate-chain compatibility separately without bypassing trust.
- TalkBack speech, physical-device acceptance, authenticated journeys, production asset rights, signing, Play and production readiness are not established.
- The configured app's Home returned a configuration-unavailable state during navigation. Discovery and collection listing loaded real Storefront data. Home remains a separate P5 concern.

## 2026-10-17 P3 visual refinement (follow-up to owner review)

The owner liked the general Gallery direction but requested smaller, calmer, consistently left-aligned intro/description typography, tidier spacing and smaller secondary collection covers while retaining two columns at ordinary phone font scales. This is a *presentation refinement*, not a revision of the accepted static V3.2 architecture or Shopify data ownership.

- The Gallery-only discovery hero uses local 24sp/30sp editorial type rather than the shared 28sp/34sp hero token; body uses 13sp/19sp, kicker 11sp/16sp, and intro vertical rhythm 6dp. System font scaling is preserved. P2 typography tokens, other brands and unrelated surfaces remain untouched.
- Featured heading uses 18sp/24sp, and the real merchant description may occupy up to four lines with explicit ellipsis rather than silently truncating. Root cover titles use 14sp/20sp. The normal-scale secondary cover maximum width decreases from 152dp to 136dp, retaining 4:5 artwork imagery, two columns, direct collection action and large-text adaptive one column.
- New Compose instrumentation tests verify the exact intro text style, text-end visibility including fontScale 2x, 136dp cover bound and existing full menu/commerce behavior. The live-capture fixture now verifies one cover at a time in 320dp/2x single-column mode; the 2-column capture still verifies both. Strict capture readiness records which media is not rendered and saves failure evidence, instead of mistaking offscreen lazy items for failed network images. Legitimate limited-palette artwork is not rejected merely for using fewer than eight colors.
- Local Gradle checks on the refined APK: spotlessness/detekt, core and TumiArt unit tests, TumiArt lint, app/test APK packaging: SUCCESS (292 actions, 36 executed, 256 up-to-date on the final application build). The final *separate* test-harness rebuild succeeded (159 actions, six executed). Fresh API 30 and API 23 catalog runs both reported `OK (12 tests)`; the configured API 30 read-only live-capture run reported `OK (1 test)`. Live readback: 12 menu actions / six root actions, 20 first-page, 20 next-page and 20 newest products, ten decoded images and zero failures. The screenshot capture explicitly checks six light/dark / normal-1.3x-2x combinations; 34 image evidence files exist locally. The existing API 23 live TLS limitation remains.
- Actual TumiArt Admin inventory on the review date had 16 collections, but `tum-koleksiyonlar` included only 12 collection destinations (six roots and six artist children); the four Admin collections omitted from this *merchant menu* are **En Çok Satanlar, Yeni Eklenenler, Minik Düşler and Van Gogh Koleksiyonu**. All 12 selected menu actions are retained by P3; the app must not silently invent additional routes or treat `tum-urunler` as a universal collection-directory API. Adding those four to Keşfet requires a separate owner-approved navigation/content decision; the store was not mutated.
- The 320dp/2x full-app primary-navigation label ellipsis and API 23 live TLS certificate-path issue are unchanged and remain separately owned follow-ups. V3.2 static acceptance does not automatically approve these new native captures.

Private local refinement evidence: ignored `out/tumiart-p3/refinement-2026-10-17/`, including test logs and `native-final/p3-gallery-live/`. Do **not** publish research/merchant screenshots to public Git or app assets. The PR remains draft until the updated exact-head CI succeeds and the owner separately accepts the real-screen layout.
