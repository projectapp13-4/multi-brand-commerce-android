# TumiArt Stage 4 P5 — Native Home

Implementation candidate from merged main `1114ad7c3e2ea89bed18e4124e2a879fc6dfec00`.
P4 PR #50 merged at that commit; exact merged-main run
[39039166266](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/39039166266)
passed all four jobs. Earlier P4 draft records remain historical.

## Scope and ownership

TumiArt selects `HomeScreenPresentation.Gallery`; Standard remains the default.
The application selects five packaged fallback collection handles and one artwork
handle. The existing repository still obtains collection/product identities,
images, price and availability from Storefront. No commercial value or merchant
image is packaged. The opt-in renders complete collection names, intact artwork,
an explicit artwork action and a localized catalog minimum-price label. The
selected variant's current price and purchase eligibility remain PDP-owned.

Short section headings use the existing editorial title role. Artwork names and
prices reuse the existing functional product roles. Image loading/error/retry
reuses the native artwork image owner. Partial resource feedback is polite and
preserves the available content. There is no new data, persistence or playback
state machine. Featured preview size is bounded by the actual Home viewport
after scaffold insets; separate heading/art/details lazy items keep the entire
artwork visible in landscape and controls reachable with enlarged text.

TumiArt selects adaptive navigation captions after native evidence reproduced a
truncated selected label at 320dp/2x. Wrapping five narrow labels caused broken
words and uneven alignment. On large-text bottom navigation, the full selected
destination name occupies a caption above five native icons. Every icon retains
its full accessible name. Ordinary text and other native suite layouts keep the
existing labels. This changes presentation only:
all five destinations, capabilities, typed routes, ordering, identities, actions
and accessible names remain application-owned. Standard navigation preserves its
existing selected-label policy. No font-scale clamp is introduced.

## Verified fallback and provider boundary

Read-only Storefront queries on 2026-10-17 reverified these published resources
and decoded six media responses before configuration changed:

| Resource | Handle | Current media source |
| --- | --- | --- |
| Collection | `ustalar` | Collection image |
| Collection | `tumi-art-originals` | Collection image |
| Collection | `modern-forms` | Collection image |
| Collection | `zen-botanicals` | First-product image |
| Collection | `cocuk-odasi-posterleri` | First-product image |
| Featured artwork | `henri-matisse-bitkiler-1952` | Product image |

The featured identity was `gid://shopify/Product/9939567280373`, available at the
observation. Every future load uses provider data; that observation is not a
permanent availability or price promise. Collection media provenance is not
encoded in the existing Home summary, so Gallery conservatively uses Fit.

Fresh Storefront readback still returned no selected `mobile_home_v2/primary`
root. The private preflight also observed no corresponding Admin definition or
root. P5 performed no Admin provisioning. Merchant authorization and validated
provider setup are required separately before hero imagery, print/story sections
or merchant editorial text can appear. The existing bounded Image/Video/Collection/
Product families and their validators remain authoritative. Testing fixtures are
explicitly project-owned, never invented production editorial content.

## Recovery contract

| Situation | Preserved behavior and evidence |
| --- | --- |
| First installation, missing or invalid root | Verified packaged references hydrate current Storefront resources; no fallback editorial snapshot is persisted |
| Partial resources or unavailable artwork | Usable collections remain; unavailable or image-less featured products are excluded; Gallery explains partial content |
| Empty resources | Unavailable state, distinct from intentional merchant editorial empty |
| Manual refresh, missing root | Failure is reported; the ViewModel retains previously visible content |
| Fresh LKG on restart | Original editorial deadline is retained; current resources and money must hydrate again |
| Expired established LKG | Fails closed; packaged fallback cannot resurrect expired authority |
| Offline first open | Connection failure, then online initial-load/restart recovery |
| Offline LKG hydration | No persisted commercial values are replayed; online hydration restores current data |

The selected Shopify root and shared refresh semantics deliberately remain
unchanged. Consequently, manual refresh cannot successfully reload packaged
content while that selected root is missing. Initial loading/restart can use the
fallback. Provider provisioning is a separate requirement; P5 does not claim
successful remote editorial acceptance or successful missing-root manual refresh.

## Validation and acceptance

The fresh targeted baseline passed 47 tests. Configuration RED/GREEN then pinned
the verified fallback; nine TumiArt recovery tests exercise the actual application
configuration with deterministic gateways/stores. The complete shared-core,
Storefront and TumiArt JVM suites reported 758 tests: 753 executed and passed,
with five optional Storefront integration cases skipped. Native tests reproduced
collection-name truncation and the absent minimum-price qualifier, then passed
with Gallery. A separate state test reproduced absent partial-resource feedback,
then passed across loading, slow, error, empty, unavailable and partial states.

At implementation checkpoint `22721e7`, the registered JVM lane reported
1,068 cases: 1,063 executed and passed,
with five optional Storefront integration cases skipped. Shared Home/navigation
instrumentation passed 42 cases on API23. The TumiArt API23 suite passed its 29
executed cases, with nine optional evidence cases skipped. Separate API23 native
captures passed at the real physical 360dp viewport with observed 1x system text,
Turkish/English resources and light/dark themes. A requested 2x legacy-system
setting still read back as 1x in the activity and is not counted as actual 2x
evidence; the separately identified 2x component regressions passed. Virtual-size
legacy screenshot attempts had incompatible pixel coordinates and are excluded from accepted evidence.

Independent review identified a real large-text primary-to-secondary composition
reset. A native regression reproduced Home state/scroll loss on Back; the adaptive
shell now retains one NavHost composition owner and changes only its navigation
chrome. The regression passes for collection/artwork Back, tab state restoration
and secondary-first startup. A further native regression reproduced Home state
and scroll loss when the text-size policy changed. The opt-in now keeps the same
content owner across regular/enlarged text and adaptive layouts; only navigation
chrome changes. The 1x-to-2x-to-1x regression passes. Standard keeps its existing
scaffold path.

Final native matrix, configured runtime checks and exact-head CI are delivery
checks recorded in the draft PR and ignored checkpoint, bound to the recorded
runtime source and APK hashes. The API30 matrix uses actual 320/360/393dp and
landscape system viewports at 1x/1.3x/2x text. It retains initial frames and verifies
complete owned-art edge markers after scrolling above system bars. Component
regression overrides are identified separately from actual-system captures.
Draft/integration and owner native visual acceptance remain distinct. Neither emulator had an enabled spoken
accessibility service. Physical TalkBack, the fresh API23 live Home request
failure and historical TLS-chain boundary, merchant provisioning and
production/release readiness are not closed by fixture tests. No trust bypass, merchant/customer/cart/order/payment write,
merge or P6/P7 work is part of P5.

Private source/APK digests, native screenshots, merchant readback and continuation
instructions remain in ignored `out/tumiart-stage4-p5/CHECKPOINT.md` and adjacent
evidence. Merchant media must remain outside public Git.
