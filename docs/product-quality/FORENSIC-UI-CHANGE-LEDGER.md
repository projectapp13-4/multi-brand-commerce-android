# Forensic UI Change Ledger

Date: 2026-08-28

Audit boundary: Git/source reconstruction from the completed Phase 3 application through the final
Product Quality production source. This ledger does not modify or accept the application. It records
what changed, what was inherited, what was visible, and what is not proven.

Historical checkpoint documents and render bundles described below were removed from the active documentation tree during consolidation; their original tracked form remains recoverable from Git history through `e6a836c`.

## 1. Evidence notation

The following labels are used deliberately:

- **VERIFIED — GIT**: reproduced from commit objects, trees, ancestry checks, or diffs in this
  repository during the forensic audit.
- **VERIFIED — RENDER**: inspected in a tracked PNG/XML evidence pair or a tracked before/after PNG.
- **DOCUMENTED CLAIM**: recorded by a checkpoint validation/handoff, but the forensic audit did not
  rerun the command or device action.
- **INFERENCE**: a conclusion that follows from verified evidence but is not itself directly logged.
- **NOT PROVEN**: the available durable evidence cannot establish the claim.

The ledger never treats a test PASS as proof of visual polish and never treats a screenshot as proof
of a remote commerce mutation.

## 2. Independently verified source anchors

| Role | Commit | Git fact | Product meaning |
|---|---|---|---|
| Integrated Phase 3 application | `f338dedfd72e2088c87c85c30826dbb27de3873b` | **VERIFIED — GIT**; subject `feat(app): harden integrated production boundary`; parent `94a...` | Last production-changing Phase 3 checkpoint identified by both Git and the Phase 3 handoff. |
| P3-15 acceptance record | `29bc1049d2eb34787a9f9fd54dfc3a1e49d3dccc` | **VERIFIED — GIT**; direct descendant of `f338ded`; its diff from `f338ded` is Phase 3 documentation only | It records acceptance of the already-built Phase 3 app. It is not a second implementation of that app. |
| P3-16 handoff normalization | `9832ae4c81679d5a07d22afd4f66652c9f821af5` | **VERIFIED — GIT**; descendant of `29bc104`; the intervening commits are documentation-only | Latest known documentation HEAD named by the original Product Quality request and the clean source-history starting point used here. |
| Final Product Quality production source | `0996b9bb054aa837f0404ff01e70a7bab6ec6093` | **VERIFIED — GIT**; subject `feat(product-quality): complete CP-05 adaptive acceptance` | Contains the final production UI/source changes. |
| CP-06 completion | `e6a836cd951a54880b269ca6baa2dd7e3cc3b4e3` | **VERIFIED — GIT**; direct child of `0996b9b`; changes two Android tests and Product Quality evidence/documents, not production source | Final repository HEAD before this audit. CP-06 did not alter production application bytes. |

The audit ran ancestry checks equivalent to:

```text
f338ded -> 29bc104 -> 9832ae4 -> 0996b9b -> e6a836c
```

Every `git merge-base --is-ancestor` check in that chain returned success. There are no merge commits
between `29bc104` and `e6a836c`. Distances observed from Git were two commits from `29bc104` to
`9832ae4`, fourteen from `9832ae4` to `0996b9b`, and one from `0996b9b` to `e6a836c`.

**Lineage conclusion — VERIFIED — GIT:** the final UI source is a direct source-history continuation
of the latest completed Phase 3 app. No APK-to-APK migration, reimplementation on a stale branch, or
binary transfer occurred.

## 3. APK anchors

| Artifact | Source/provenance | Size | SHA-256 | Evidence status |
|---|---|---:|---|---|
| Phase 3 development APK | `app-development-debug.apk`; P3-15 handoff identifies production source `f338ded` and acceptance commit `29bc104` | 20,176,991 bytes | `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E` | Hash/size are a **DOCUMENTED CLAIM** in `docs/phase3/P3-15-HANDOFF.md`; source ancestry is **VERIFIED — GIT**. |
| Final Product Quality development APK | `app/build/outputs/apk/development/debug/app-development-debug.apk`; production source `0996b9b` | 21,126,882 bytes | `F221B5C692889AFFA039B3770E5BC943048F3CB89C85A1216F1B3BAAE2E7D4E1` | Build/install/pulled-device hash match is a **DOCUMENTED CLAIM** in CP-05/CP-06 validation; production source identity is **VERIFIED — GIT**. |

The build directory is not a durable Git object. This audit therefore does not independently claim
that a currently present binary was rebuilt or rehashed; it records the tracked validation claim and
independently proves its stated source commit.

## 4. Change-volume boundary

For the defined implementation path set
`app/src/main`, `app/src/debug`, `foundation/src/main`, `app/build.gradle.kts`, and
`gradle/libs.versions.toml`, Git reports from `9832ae4` to `0996b9b`:

- **64 files changed**;
- **3,674 insertions**;
- **934 deletions**.

That path set includes production source/resources, two debug-only evidence activities, test-tag
constants embedded in main source, and dependency/build configuration. It excludes unit tests,
instrumentation tests, screenshots, and Product Quality documents.

Across the entire repository from `9832ae4` to `e6a836c`, Git reports 300 files changed, 12,028
insertions, and 989 deletions. Of those, 202 changed files and 4,146 insertions are under
`docs/product-quality`. This volume is evidence of extensive implementation and audit work; it is not
itself evidence of visual quality.

At the audited checkpoint, tracked render evidence comprised 94 PNGs and 67 UI-hierarchy XML files:

| Checkpoint | PNG | XML | Meaning |
|---|---:|---:|---|
| CP-01 | 15 | 0 | Phase 3 visual baseline, including EN/TR and IME states |
| CP-03 | 12 | 0 | Six before and six after shell/foundation captures |
| CP-04 | 36 | 36 | Four staged implementation/evidence sets, including one rejected duplicate-state inspection |
| CP-05 | 15 | 15 | Width/font/dark/short-window/adaptive acceptance |
| CP-06 | 16 | 16 | Final compact-device journey and installed-home smoke |

## 5. Chronological commit ledger

| Commit | Checkpoint | Classification | Production effect |
|---|---|---|---|
| `5971da8` | CP-01 | evidence/documentation only | Captured the first baseline and issue matrix; no UI source change. |
| `5fb204e` | CP-01 | documentation/handoff only | First CP-01 handoff; no UI source change. |
| `c1b9c2e` | CP-01 | process/Goal documentation only | Paused at the skill-activation boundary; no UI source change. |
| `e45f344` | CP-01 correction | skill-audit/documentation only | Corrected the external mobile UI/UX skill installation/discovery record; no UI source change. |
| `1f07557` | CP-01 correction | evidence/documentation only | Reconciled 16 findings after skill-guided review; no UI source change. |
| `fccd488` | CP-01 correction | handoff only | Closed the corrected baseline; no UI source change. |
| `40a3909` | pre-CP-02 | tooling/process evidence | Proved physical-device development readiness; no intended product UI change. |
| `04a3551` | CP-02 | design/IA contract only | Locked navigation, inset, component, state, and adaptive decisions; no production UI change. |
| `0ab3d29` | CP-03 | **production UI/foundation** | App shell, adaptive navigation, inset/IME contract, design tokens, shared scaffold/state foundations. |
| `cfc7141` | CP-04 stage 1 | **production UI/state** | Home slow-loading/recovery and Categories density/composition. |
| `a7c2304` | CP-04 stage 2 | **production UI/commerce** | Collection cards, price block, Product hierarchy/purchase bar, fullscreen media. |
| `a41c2ed` | CP-04 stage 3 | **production UI/interaction** plus debug evidence | Search, Account, Profile/Address IME behavior; credential-free evidence activity. |
| `40ebd2f` | CP-04 stage 4 | **production UI/state** plus debug evidence | Wishlist/Cart/checkout states, ownership disclosure, adaptive cart state evidence. |
| `0996b9b` | CP-05 | **production adaptive/accessibility** | Bounded widths, responsive Product layout, large-font cart/product controls, navigation semantics. Final production-source commit. |
| `e6a836c` | CP-06 | test/evidence/documentation only | Tightened Search and navigation-label assertions; no production UI/source change. |

The six-checkpoint framework itself was present in the original master request. The later four-stage
split inside CP-04 and the redistribution of named surface work across CP-04/CP-05 were later
execution refinements.

## 6. Functionality inherited from Phase 3

The following table prevents Product Quality work from taking credit for pre-existing application
capabilities. Baseline existence was checked in the `9832ae4` tree. The typed route file already
contained the listed routes and was not redesigned by Product Quality.

| Capability already present at Phase 3 baseline | Baseline source evidence | Product Quality contribution on top |
|---|---|---|
| Single-activity production app and typed navigation | `ProductionApp.kt`, `ProductionRoutes.kt` | Replaced root visual shell/nav presentation; preserved route contracts and destination behavior. |
| Home/discovery data and sections | `home/HomeScreen.kt`, `HomeViewModel.kt` | Changed loading timing/presentation, layout density, skeletons, max width, and shared scaffold—not discovery data integration. |
| Categories and collection browsing | `catalog/CategoriesScreen.kt`, `CollectionScreen.kt` | Changed tile density, card composition, state spacing, and hierarchy—not Storefront querying. |
| Product detail, variants, media, add-to-cart | `product/ProductDetailScreen.kt`, `ProductCartAction.kt` | Reordered and recomposed the UI, added persistent purchase bar and fullscreen edge handling—not variant/cart domain behavior. |
| Search and recent-history logic | `search/SearchScreen.kt`, `SearchActions.kt`, search ViewModel/repository files | Removed redundant visible submit, added progressive disclosure and IME/focus polish; retained debounce/data behavior. |
| Device wishlist persistence and product resolution | wishlist feature and repositories | Reworked states/cards/actions; did not create persistence. |
| Cart lifecycle, ownership, mutations, and checkout entry | cart/checkout feature code | Reworked cards, state panels, ownership explanation, feedback actions, and large-font controls; did not create Checkout Kit or cart rules. |
| Customer Account OAuth/session | account/auth modules and Account screen | Reprioritized sign-in/customer tasks and disclosure; did not implement OAuth/PKCE. |
| Profile editing | profile feature | Applied shared shell/max width and IME evidence; the technical form contract and mutation behavior were inherited. |
| Address list/form and validation | address feature | Applied shared shell/max width and focused-item relocation; CRUD/validation rules were inherited. |
| Orders list/detail and tracking policy | `order/OrderScreens.kt` plus order controllers | Applied shared shell/insets/max width only; order behavior and content model were inherited. |
| Legal/support links and safe browser return | legal feature | Applied shared shell/insets/max width only; page inventory, security policy, and verbose source metadata were inherited. |
| Account-deletion request/local cleanup | account-deletion feature | Applied shared shell/insets/max width only; deletion boundary/behavior were inherited. |
| Update-policy behavior | update feature | Made the banner accept an external modifier for shell placement; policy behavior was inherited. |
| Localization | EN/TR resources and locale handling | Added/changed UI strings for new components and compact navigation labels; did not create the localization system. |
| Persistence/state infrastructure | repositories, DataStore/Keystore-backed abstractions, ViewModels | Shared visual states and one loading timer were added; core persistence and state ownership remained Phase 3 work. |
| Deep links and route recovery | typed route/deep-link handling | Shell tests exercised them; Product Quality did not implement the contracts. |

Earlier final Product Quality documents list all of these surfaces under “accepted product qualities.”
That is a validation statement, not proof that Product Quality implemented the underlying functions.
Reading the list as an implementation achievement would blur inherited Phase 3 work with UI changes.

## 7. Exhaustive meaningful production change ledger

### 7.1 Shell, navigation, insets, and theme

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-03 `0ab3d29`; CP-05 refinement `0996b9b` | `ProductionApp.kt`, adaptive-nav dependency metadata | Root `Scaffold` supplied padding to nested screen scaffolds. Five root items had labels but empty `icon = {}` slots. Primary screens also exposed their own textual Back affordance. | `NavigationSuiteScaffold` selects bottom bar or rail; five icon+label roots have selected/unselected icons; navigation is absent on secondary destinations; update-policy content is overlaid without changing routes. | PQ-001, PQ-005, PQ-006; clearer persistent roots and adaptive ownership. | **High, normal-state.** Every root screen looks and navigates differently. | Navigation/layout + visible UI |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | new `AppNavigationItem.kt`; eleven drawable vectors; EN/TR strings | Text-only Home/Categories/Search/Wishlist/Account labels; weak selected recognition and large-text risk. | 24dp Material-style icons, always-visible compact labels (`Home/Shop/Find/Saved/You` in EN), selected variants, 48dp minimum targets, and full accessibility descriptions separate from visible labels. | PQ-005 and large-font/localization acceptance. | **High at normal size** for icons/selected state; compact-label semantics matter under localization/accessibility. | Visual + interaction + accessibility |
| CP-03 `0ab3d29` | new `DestinationScaffold.kt`; most destination screens | Each destination owned a different `Scaffold`, top bar, safe insets, and textual Back implementation. Padding could be applied at multiple levels. | One primary/secondary hierarchy contract; secondary destinations require Up; standardized icon button/top-bar alignment; safe-drawing owner; `consumeWindowInsets(...).imePadding()` helper; shared content spacing. CP-05 adds centered bounded content helper. | PQ-001, PQ-006, PQ-009; prevent double insets/IME clipping and inconsistent hierarchy. | **Moderate normal-state** through consistent bars and Back; inset effect is more visible near system bars/IME. | Structural + navigation + inset |
| CP-03 `0ab3d29` | `AndroidManifest.xml` | No explicit soft-input mode. | Launcher activity declares `android:windowSoftInputMode="adjustResize"`. | PQ-009. | **Special-state only** when keyboard opens. | Behavior/inset |
| CP-03 `0ab3d29` | `MainActivity.kt` | `enableEdgeToEdge()` only. | Android 10+ navigation-bar contrast enforcement disabled so theme/system-bar treatment is explicit. | Edge-to-edge/system-bar legibility. | **Low/special-state**, dependent on OS/navigation mode. | Platform visual/inset |
| CP-03 `0ab3d29` | `foundation/config/BrandConfiguration.kt`, `foundation/ui/CommerceTheme.kt`, `brand/GurbakirBrand.kt` | Partial color/shape/spacing tokens; no explicit mapped typography roles; sparse color scheme. | Full semantic light/dark color slots, twelve text roles and weights, spacing/shapes/motion tokens mapped into Material 3 theme. Font resource remains `null` and therefore uses system sans. | PQ-012; consistent components and large-text behavior. | **Moderate but diffuse.** Surface colors/type weights change; no distinctive custom type/brand system. | Design system + visual |
| CP-03 `0ab3d29` | new `CommerceStateComponents.kt` | Screens composed their own text/button/progress empty/error/loading blocks. | Reusable tonal `CommerceStatePanel` with heading/body/icon/actions, 48dp targets, 160dp minimum height; non-announced static `CommerceSkeleton`. | PQ-011 and state consistency. | **High in empty/error/loading states; none when states are absent.** | Visual component + state + accessibility |
| CP-04 stage 2 `a7c2304` | new `PriceBlock.kt` | Screens formatted price/availability independently; product hierarchy could separate price from purchase action. | Shared localized current/range/compare-at/availability block with card/detail/purchase emphasis; server zero remains neutral and no “free” claim is invented. | PQ-014 and commerce consistency. | **Moderate normal-state.** | Visual hierarchy + truthful content |

### 7.2 Home and Categories

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-04 stage 1 `cfc7141` | `HomeViewModel.kt`, new `HomeLoadingClock.kt` | Initial requests could remain visually pending for many minutes; no bounded recovery transition. | Injectable eight-second slow-loading threshold, cancellation on completion/retry, retained-content slow state, and explicit recovery transition while the lower network timeout remains authoritative. | PQ-002; make prolonged loading observable without inventing completion. | **Special-state only**, after slow network/loading. | Behavior/state handling |
| CP-04 stage 1 `cfc7141`; CP-05 `0996b9b` | `HomeScreen.kt`, new `HomeStateComponents.kt` | Large skeleton-dominated launch; individual sections could emit duplicate recovery treatments; layout used generic screen composition. | Shared shell/skeleton/state panel, a single whole-page recovery panel for simultaneous slow sections, retained-content handling, max 1200dp content width, and adaptive product-range columns: one below 400dp, two at 400+, three at 456+, four at 640+. | PQ-002, PQ-011, PQ-015. | **Moderate normal-state** in loaded composition; **high special-state** for slow/error. | Visual + state + adaptive |
| CP-04 stage 1 `cfc7141` | `CatalogConfiguration.kt`, `CategoriesScreen.kt`, catalog tags and strings | `GridCells.Adaptive(240.dp)` plus 3:4 media produced one very large card at 384dp; first category label could fall below the viewport. | 140dp adaptive tile minimum, 3:4 `Fit`, two-line localized title, two-column compact layout, bounded max width, and skeleton/state treatment. | PQ-003; scan density and label visibility. | **Substantial normal-state visual/UX change.** | Visual + layout + discovery UX |
| CP-04 stage 1 correction | Home sources/evidence | First implementation rendered duplicate slow-recovery panels when both Home feeds were slow. | Rejected inspection capture retained under `stage-1/inspection`; final code presents one recovery panel. | Real rendered iteration, not a final feature. | Rejected temporary state. | Temporary experiment/correction |

### 7.3 Collection, reusable Product Card, and price presentation

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-04 stage 2 `a7c2304` | `CollectionScreen.kt`, new `ProductCard.kt`, `WishlistButton.kt`, catalog tags | Collection owned an inline card: 3:4 image, variable title, price, and full-text wishlist action; heights and scanning rhythm varied. | Shared 3:4 `Fit` card, separate click surface and 48dp overlay wishlist icon, maximum three title lines, consistent price then availability order, stable spacing and semantics. | PQ-007; balanced grid and reusable commerce hierarchy. | **Moderate-to-substantial normal-state change.** | Visual + interaction + component system |
| CP-04 stage 2 `a7c2304` | `PriceBlock.kt`, strings | Currency/zero/compare-at treatment was screen-specific. | Locale-aware shared price range/current/compare-at and availability presentation; actual `0.00` remains visible and neutral. | PQ-014 and approved no-invented-claim policy. | **Small-to-moderate normal-state polish.** | Content presentation |

### 7.4 Product Detail and fullscreen media

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-04 stage 2 `a7c2304` | `ProductDetailScreen.kt`, new `ProductPresentation.kt` | Long product title occupied the centered app bar; large gallery and a full-width “Enlarge image” action preceded price/availability/options; purchase action could be below the fold. | Generic destination title; body order becomes product title, price/availability, condensed square gallery, options/content; gallery image opens viewer directly; controls/count are overlaid. | PQ-004; make product/purchase facts visible before secondary media. | **Substantial normal-state hierarchy and interaction change.** | Visual + UX |
| CP-04 stage 2 `a7c2304`; CP-05 `0996b9b` | `ProductCartAction.kt`, `ProductDetailScreen.kt` | Add-to-cart lived in scrolling body with feedback. | Surface-backed purchase bar measured by `Scaffold`, with price/availability plus Add to Cart, navigation-bar inset ownership, and feedback. It stacks below 340dp or at font scale 1.5+. | PQ-004, PQ-009, PQ-015. | **High normal-state**; stacking is special configuration. | Visual + interaction + adaptive/inset |
| CP-05 `0996b9b` | `ProductDetailScreen.kt` | Single-column product composition at all widths. | At effective width 840dp+, media and task content use a bounded two-pane composition; compact remains single column. | PQ-015 and later owner reference-pattern clarification. | **Special configuration**; no change on default 384dp phone. | Adaptive layout |
| CP-04 stage 2 `a7c2304` | `ProductDetailScreen.kt`, `MainActivity.kt` | Cream/gray semi-immersive dialog with text controls and large system-bar regions. | Black edge-to-edge `Dialog`, `decorFitsSystemWindows=false`, safe-drawing controls, icon navigation, counter, controlled system-bar appearance, Back handling and opener-focus restoration. | PQ-013. | **Substantial when media viewer is opened; none in ordinary browsing.** | Visual + interaction + inset/accessibility |

### 7.5 Search

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-03 `0ab3d29` | `SearchScreen.kt`, `SearchActions.kt` | Search was a primary bottom-nav destination but displayed textual Back; nested safe-inset handling. | Primary destination with no visual Up; shared shell and inset consumption. The now-unused visual Back callback was removed from the UI action contract. | PQ-001, PQ-006. | **Moderate normal-state.** | Navigation/layout |
| CP-04 stage 3 `a41c2ed` | `SearchScreen.kt`, tags, strings | Query changes already led to results while an explicit full-width Search button remained; minimum/privacy helper copy and history toggle competed with the main task. IME Search did not explicitly clear focus/hide keyboard. | Search field is the sole visible query action; IME Search submits, clears focus, and hides keyboard. Too-short state stays in field support. History remains visible but its enable/privacy controls move into a labeled expandable card with expanded/collapsed semantics. | PQ-008, PQ-009; one interaction model and progressive disclosure. | **Moderate normal-state and meaningful IME behavior change.** | UX + visual hierarchy + IME/accessibility |
| CP-06 `e6a836c` | `SearchScreenTest.kt` only | Existing test did not assert the post-IME focus state strongly enough. | Test assertion strengthened; application source unchanged. | Final regression evidence. | No customer-visible effect. | Test-only |

### 7.6 Account, Profile, Address, Orders, Legal/Support, Deletion, Update

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-03 `0ab3d29`; CP-04 stage 3 `a41c2ed`; CP-05 `0996b9b` | `AccountScreen.kt`, new `AccountMenuRow.kt` | App bar and body repeated “Account”; hosted OAuth, session, local-data, and technical copy appeared with customer tasks in a long, weak hierarchy. | One destination title; status context; signed-out card leads with sign-in; signed-in summary and Orders/Profile/Addresses task rows lead; session and device-data controls move into disclosures; max 720dp width. | PQ-010; task-first hierarchy and progressive disclosure. | **Moderate-to-substantial normal-state improvement.** Still text-heavy when disclosures open. | Visual hierarchy + UX |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `ProfileScreen.kt` | Screen-specific top bar/padding; unbounded form width. Existing technical scope/unsaved-state copy was already present. | Shared secondary scaffold, icon Up, single inset contract, max 600dp form width. No substantive rewrite of the form composition or engineering-oriented explanatory copy. | PQ-001/PQ-006/PQ-009/PQ-015, not a full profile redesign. | **Small normal-state polish; mostly structural/adaptive.** | Layout/inset/adaptive |
| CP-03 `0ab3d29`; CP-04 stage 3 `a41c2ed`; CP-05 `0996b9b` | `AddressFormScreen.kt` | Fields were composed in one column item; keyboard focus movement did not have per-field lazy-list relocation. | Shared secondary scaffold/max 600dp; every field is a keyed lazy item; focus triggers `scrollToItem`; Next moves focus; IME correction keeps City/Postal fields visible. | PQ-009, PQ-016. | **Mostly behavioral/IME; small normal-state visual change.** | IME/state + layout |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `AddressListScreen.kt` | Screen-specific top bar/padding and full-width content. | Shared secondary scaffold/insets, standard icon Up, max 720dp. CRUD cards/copy/behavior otherwise inherited. | Shell consistency and adaptive width. | **Small normal-state polish.** | Structural/adaptive |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `OrderScreens.kt` | Screen-specific list/detail scaffolds and full-width content. | Shared secondary scaffold/insets, standard Up, max 720dp for list/detail. Order composition/copy/domain behavior otherwise inherited. | Shell consistency and adaptive width. | **Effectively no meaningful compact normal-state redesign beyond chrome.** | Structural/adaptive |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `LegalSupportScreen.kt` | Existing long legal/support introduction, source/adoption metadata, and button list in screen-specific scaffold. | Shared secondary scaffold/insets and max 720dp. The verbose “App integration baseline,” source dates, secure-tab explanations, and long actions remain. | Shell consistency only. | **Effectively no meaningful compact content redesign.** | Structural/adaptive |
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `AccountDeletionScreen.kt` | Existing deletion/local-cleanup workflow in screen-specific scaffold. | Shared secondary scaffold/insets and max 720dp. Core hierarchy, warnings, and actions otherwise inherited. | Shell consistency. | **Small structural polish; visual significance NOT PROVEN by a final direct screenshot.** | Structural/adaptive |
| CP-03 `0ab3d29` | `UpdatePolicyBanner.kt`, `ProductionApp.kt` | Banner composable owned its placement assumptions. | Destination accepts a caller modifier and is placed by the root shell overlay. Policy logic/copy unchanged. | Avoid shell/inset collision. | **Special-state only; final update-state visual result NOT PROVEN.** | Structural/inset |

### 7.7 Wishlist

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-03 `0ab3d29` | `WishlistScreen.kt`, `WishlistActions.kt`, `WishlistDestination.kt` | Primary destination showed textual Back and screen-specific scaffold. | Primary shared scaffold without Up; simplified action wiring and standard insets. | PQ-001/PQ-006. | **Moderate navigation chrome change.** | Navigation/layout |
| CP-04 stage 4 `40ebd2f` | `WishlistScreen.kt`, `WishlistButton.kt`, strings | Empty/storage/loading states were sparse text/progress/button layouts; device-only explanation led every state; failed entries were plain column blocks; clear confirmation reused device-only explanation. | Shared centered state panels with icon/body/action; device-only explanation moves into populated state; dedicated loading, storage, and partial-error treatments; tonal failed-entry surface; clearer clear-confirmation message; icon-based reusable wishlist control. | PQ-011, PQ-007/PQ-016. | **Substantial in empty/error states; moderate in populated cards.** | Visual state + UX |

### 7.8 Cart and checkout feedback

| CP / commit | Files | Before | Final | Reason / finding | Customer visibility | Primary class |
|---|---|---|---|---|---|---|
| CP-03 `0ab3d29`; CP-05 `0996b9b` | `CartScreen.kt` | Child screen-specific centered bar/padding. | Shared secondary scaffold, icon Up, single inset contract, max 720dp. | Shell consistency. | **Small-to-moderate normal-state.** | Navigation/layout |
| CP-04 stage 4 `40ebd2f` | `CartContent.kt`, new `CartFailurePresentation.kt`, new `CartOwnershipNotice.kt`, strings | Loading/error/empty/expired/restricted states were inconsistent text/button blocks; customer-associated ownership boundary had no dedicated customer-facing panel. | Shared state panels with accurate primary/secondary recovery; error action varies by retryability; separate inline failure; restricted/detach-pending copy; customer-associated ownership notice; retained active cart content. | PQ-011/PQ-016 and safe cart truthfulness. | **Substantial in non-happy states; ownership notice is special state.** | Visual state + behavior presentation |
| CP-04 stage 4 `40ebd2f` | `CartCheckoutSection.kt` | Checkout feedback was colored text and only completed state supplied Continue. | State panel titles/actions map completion, cancellation, cleanup, blocked link, and retryable/non-retryable failure to Continue/Retry/Refresh where valid. | Clear recovery after safe checkout return. | **Special-state only.** | State/UX |
| CP-05 `0996b9b` | `CartLineCard.kt` | Quantity controls remained one row regardless of font scale. | At font scale 1.5+ quantity label and full-width decrement/increment row stack; ordinary font remains horizontal. | PQ-015 large-text/touch target. | **Special configuration only.** | Adaptive/accessibility |

## 8. Screen-by-screen visual reconstruction

This table joins Git facts to inspected renders. “Before” refers to the Phase 3/CP-01 render, not a
mock. “Final” refers to tracked final or latest accepted evidence. Missing final coverage is stated.

| Surface | Before this UI effort | Concrete final state | Visual significance | Durable visual evidence / gap |
|---|---|---|---|---|
| App shell / primary navigation | Plain text-only five-item bottom navigation; weak selected differentiation; several roots also showed Back. | Icon+short-label adaptive navigation, selected icon variants, primary/secondary hierarchy, rail at wider overrides. | **Substantial shell usability change; moderate visual redesign.** It is still Material-default and brand-light. | CP-01 `03`, `08`, `11`, `12`, `14`, `15`; CP-03 before/after; CP-05 width captures; CP-06 Home/Categories/Search/Wishlist/Account. |
| Home | Launch dominated by large skeleton blocks; long pending state could persist; loaded layout was functional but generic. | Loaded wordmark/product-range/featured composition; bounded multi-column layout; shared skeleton and one slow-recovery panel. | **Moderate visible improvement**, stronger state behavior. Still large headings, system typography, text-only wordmark, and limited brand expression. | CP-01 `01`, `02`, `14`; CP-04 stage 1; CP-05 widths; CP-06 `01`, `14`, `16`. |
| Categories | At 384dp one giant 240dp-min tile; category label could be below fold. | Two-column 140dp-min grid with visible two-line labels and consistent 3:4 images. | **Substantial normal-state usability/density improvement.** | CP-01 `03`, `04`; CP-03 before/after; CP-04 stage 1; CP-06 `02`. |
| Collection | Two-column product cards with variable text/action heights and full-text save action. | Balanced shared cards, overlay wishlist heart, fixed title height, price/availability order. | **Moderate visible improvement.** | CP-01 `05`; CP-03 `03`; CP-04 stage 2; CP-06 `03`. |
| Product Detail | Product title crowded app bar; gallery and “Enlarge image” preceded price/purchase; add-to-cart could be below fold. | Generic app-bar title, task-first body, overlaid gallery controls, price/availability early, persistent purchase bar; two-pane at expanded width. | **Substantial hierarchy/interaction improvement.** Compact composition remains dense and typographically oversized/utilitarian rather than highly polished. | CP-01 `06`; CP-04 stage 2 `04`; CP-05 expanded `11`; CP-06 `04`. |
| Fullscreen media | Light/gray dialog with text controls and non-explicit edge treatment. | Black edge-to-edge viewer with safe icon controls, count, Back and focus restoration. | **Moderate-to-substantial special-state redesign.** | CP-01 `07`; CP-04 stage 2 `05`; CP-06 `05`. |
| Search | Root Back, text field plus redundant full-width Search button; history/privacy/toggle competed with search. | No root Up; field/IME are the one search action; history settings are disclosed; keyboard hides after action. | **Moderate visible/UX improvement.** Overall surface remains visually plain. | CP-01 `08`–`10`; CP-03 before/after; CP-04 stage 3 `02`/`03`; CP-06 `06`/`07`. |
| Wishlist | Sparse empty text and browse button, root Back, inconsistent state blocks. | Centered tonal empty/loading/error panels, icon and clear recovery; populated shared product cards and confirmation. | **Substantial in empty/error states; moderate in populated state.** | CP-01 `11`; CP-04 stage 4 `01`, `03`–`05`, `09`; CP-06 `08`. |
| Cart | Sparse empty text/button; non-happy states were technically truthful but visually inconsistent. | Shared empty/error/restricted panels, clearer recovery hierarchy, ownership notice, improved checkout feedback, font-responsive quantity controls. | **Substantial state design; moderate ordinary active-cart polish.** Customer-associated fixture remains text-heavy and visually rudimentary. | CP-01 `13`; CP-04 stage 4 `02`, `06`–`08`, `10`; CP-05 large-text Cart; CP-06 `09`. |
| Account | Duplicate Account title; technical OAuth/session/local-data copy competed with sign-in/tasks. | One title; sign-in or customer tasks first; session/local-data details in disclosures; grouped menu rows. | **Moderate-to-substantial hierarchy improvement**, but verbose technical content remains when expanded. | CP-01 `12`, `15`; CP-04 stage 3 `05`, `06`; CP-06 `10`, `11`, `15`. |
| Profile | Existing plain form and detailed Shopify/memory/server explanations. | Same form/content under standardized shell, max width, and validated IME/focus behavior. | **Mostly behavioral/adaptive; effectively no major compact visual redesign.** Engineering-oriented copy remains. | CP-04 stage 3 `07`–`09`; CP-05 expanded/large-font. No Phase 3 screenshot pair. |
| Address form/list | Existing CRUD form/list with verbose constraints; baseline authenticated screens were not captured. | Standardized shell/width; per-field lazy items keep focused fields visible with IME. | **Mostly behavioral/IME; small normal-state polish.** Address-list final visual quality is **NOT PROVEN**. | CP-04 stage 3 Address form captures; no direct final Address-list screenshot. |
| Orders list/detail | Existing read-only Shopify order flow and detailed state copy. | Standard shell/insets/max width only. | **Effectively no meaningful normal compact-state visual redesign.** | Automated route/state coverage is documented. Final order-screen render is **NOT PROVEN**. |
| Legal/support | Existing long technical/legal index and source/adoption metadata. | Standard shell/insets/max width; underlying long copy/buttons remain. | **Effectively no meaningful content redesign; visibly still engineering/documentation-oriented.** | CP-06 `12`/`13`; no matched Phase 3 screenshot, but source diff proves content was not materially redesigned. |
| Account deletion/local cleanup | Existing separation of remote request and local cleanup. | Standard shell/insets/max width only. | **Small structural polish.** | Local-data disclosure is captured; full deletion surface final visual quality is **NOT PROVEN**. |
| Update-policy UI | Existing policy/banner behavior. | Banner can be positioned by root shell. | **Special-state structural change.** | No final update-notice screenshot; visual quality is **NOT PROVEN**. |

## 9. Adaptive and accessibility-only changes

These changes should not be counted as a normal 384dp/default-font redesign even though they are
useful quality work.

| Change | Files/commit | When visible | Evidence |
|---|---|---|---|
| Navigation bar-to-rail adaptation | `ProductionApp.kt`, `AppNavigationItem.kt`; CP-03/CP-05 | Medium/expanded effective widths | CP-05 600/1440 overrides; device was still the same Samsung, not a native tablet. |
| Bounded content widths | Home 1200dp; Account/Address/Order/Legal/Cart/Wishlist 600–720dp; CP-05 | Wide windows | CP-05 expanded Home/Profile/Product. |
| Product two-pane layout | `ProductDetailScreen.kt`; CP-05 | >=840dp | CP-05 1440dp render and threshold unit test. |
| Home responsive columns | `HomeScreen.kt`; CP-05 | 320/384/456/600/expanded widths | CP-05 width captures. The 320dp capture was a loading/skeleton state, not loaded-content proof. |
| Product purchase-bar stacking | `ProductCartAction.kt`; CP-05 | <340dp or font >=1.5 | Focused tests and large-text evidence. |
| Cart quantity-control stacking | `CartLineCard.kt`; CP-05 | font >=1.5 | Focused test and large-text Cart capture. |
| Full navigation semantics separate from short labels | `AppNavigationItem.kt`; CP-05/CP-06 | Accessibility tree; visible label stays short | Focused Compose assertions; CP-06 label assertion strengthened. Real TalkBack traversal remained externally blocked. |
| Dark semantic palette | brand/theme files; CP-03 | Dark mode | CP-05 dark Home visual inspection. Quantitative contrast remained partial. |
| Focus visibility with IME | shared scaffold plus Search/Profile/Address-specific behavior | Keyboard/input states | Search/Profile/Address PNG/XML and focused tests. |

CP-05 width checks were performed through safe display-size/density overrides on the connected
Samsung. They demonstrate layout response at those effective widths. They are not evidence of OEM,
hinge, cutout, tablet posture, or a second physical form factor. Gesture navigation was not run;
TalkBack, Accessibility Scanner, and API-34 automatic accessibility checks were not available.

## 10. Test-only, evidence-only, temporary, and process work

### Test-only

- CP-03 added token, scaffold, navigation, inset, and physical-device shell tests.
- CP-04 added focused Home/Categories, Collection/Product/media, Search/Account/Profile/Address, and
  Wishlist/Cart state tests.
- CP-05 added `AdaptiveLayoutPolicyTest` and strengthened affected screen tests. One exact
  floating-point geometry assertion was relaxed to a one-pixel center-alignment tolerance after the
  render showed the buttons correctly aligned; no production behavior changed.
- CP-06 changed only `SearchScreenTest.kt` and `FoundationComponentsTest.kt` in application-related
  files. This strengthened keyboard-focus and navigation-label semantics evidence; it did not change
  the app.

Checkpoint documents claim the following validated sets. They were not rerun by this forensic audit:

| Boundary | Documented result | Important qualification |
|---|---|---|
| CP-03 | focused 6/6 and full 89/89 device | Later IME correction on Samsung used targeted evidence; the unchanged full set was not repeatedly rerun. |
| CP-04 stage 1 | Home JVM 4/4; app JVM 135/135; device 13/13, then corrected 9/9 | The 9/9 correction replaced the duplicate slow-state visual defect. |
| CP-04 stage 2 | focused 12/12 | Product/media/collection scope only. |
| CP-04 stage 3 | 21/21 before Address source correction; Address 8/8 after | Full 21 was not rerun after the Address-only correction. |
| CP-04 stage 4 | focused 12/12; app JVM 135/135 | State/fixture scope only. |
| CP-05 | adaptive JVM 3/3; affected device 30/30; later 9 plus corrected 1/1; app JVM 138/138 | Historic 89-test device suite deliberately not repeated. External accessibility limits remained. |
| CP-06 | six-module JVM 252 total, 250 PASS/2 opt-in skips; device 103/103 | First device run was 102/103 due to a test semantics expectation; test-only correction followed by clean 103/103. |

### Debug-only evidence harnesses

- `app/src/debug/kotlin/com/gurbakir/mobile/Stage3EvidenceActivity.kt` and its debug manifest entry
  render production Account/Profile/Address composables with deterministic local state.
- `app/src/debug/kotlin/com/gurbakir/mobile/Stage4EvidenceActivity.kt` and its debug manifest entry
  render production Wishlist/Cart special states.
- These activities do not enter the release source set and the normal launcher remains
  `MainActivity`. They enabled credential-free screenshots without remote mutation.
- They remain in the final debug source. That is a tooling/evidence contribution, not a customer
  feature or a proof of live authenticated behavior.

### Rejected or superseded evidence

- CP-04 stage 1 retained `inspection/01-en-home-slow-duplicate.*`, which exposed two recovery panels.
  The source was corrected and the inspection image is explicitly not accepted final evidence.
- CP-04 stage 3 went through rejected Address IME iterations before accepted City and Next captures.
  The rejected intermediate files were not retained as final evidence.
- CP-05 kept four rejected/mislabeled pairs under ignored build inspection output rather than
  promoting them into tracked evidence.
- CP-06 excluded inconclusive keyboard probes and removed one accidental out-of-scope capture.

### Documentation/process only

- CP-01/CP-02 skill audits, issue matrix, IA contract, screenshot plans, handoffs, Goal-state updates,
  process/device checks, hashes, and pause/resume records are documentation/process work.
- Repeated usage-limit, power, device-connectivity, and owner-review pauses produced many handoffs.
  They protected coherent state but did not directly change the rendered product.
- Formatting/Detekt refactors, including the named Product predicate introduced to satisfy a
  `ComplexCondition` finding, were intended to be behavior-neutral.

## 11. File-by-file implementation appendix

This appendix accounts for every file in the 64-file implementation path-set diff. Related icon
resources are grouped only when their effect is identical.

| File(s) | Status | Classification and exact role |
|---|---|---|
| `app/build.gradle.kts`; `gradle/libs.versions.toml` | modified | Added/registered Material adaptive navigation dependency; tooling/configuration enabling the shell. |
| `app/src/debug/AndroidManifest.xml` | modified | Registers two debug-only evidence activities. |
| `Stage3EvidenceActivity.kt` | added | Credential-free Account/Profile/Address production-composable evidence harness. |
| `Stage4EvidenceActivity.kt` | added | Credential-free Wishlist/Cart special-state production-composable evidence harness. |
| `app/src/main/AndroidManifest.xml` | modified | Explicit `adjustResize` for launcher activity. |
| `MainActivity.kt` | modified | Disables Android 10+ forced nav-bar contrast while retaining edge-to-edge. |
| `ProductionApp.kt` | modified | Adaptive root shell, root/secondary navigation ownership, icon nav, overlay placement, width-aware behavior. |
| `AccountMenuRow.kt` | added | Reusable full-width account task row. |
| `AccountScreen.kt` | modified | Single title, sign-in/customer-task priority, status and session/local-data disclosures, bounded width. |
| `AccountDeletionScreen.kt` | modified | Shared secondary scaffold/insets and bounded width only. |
| `AddressFormScreen.kt` | modified | Shared scaffold/width, per-field lazy items, focus-driven scrolling for IME. |
| `AddressListScreen.kt` | modified | Shared secondary scaffold/insets and bounded width. |
| `GurbakirBrand.kt` | modified | Full light/dark semantic colors, typography roles, motion; still system font. |
| `CartCheckoutSection.kt` | modified | State-specific checkout feedback panel/action mapping. |
| `CartContent.kt` | modified | Shared cart state panels, ownership state, error/recovery hierarchy. |
| `CartFailurePresentation.kt` | added | Maps cart failure categories to customer-facing message resources/retry presentation. |
| `CartLineCard.kt` | modified | Large-font quantity control stacking and reusable buttons. |
| `CartOwnershipNotice.kt` | added | Customer-associated cart and protected-content notice composition. |
| `CartScreen.kt` | modified | Shared secondary scaffold/insets and bounded content. |
| `CartTestTags.kt` | modified | Tags for new cart visual states/actions; test support in main source. |
| `CatalogConfiguration.kt` | modified | Category tile metadata/labels and 140dp compact density configuration. |
| `CatalogTestTags.kt` | modified | Tags for new card/price/category evidence. |
| `CategoriesScreen.kt` | modified | Dense labeled tile grid, shared shell/state/skeleton, bounded width. |
| `CollectionScreen.kt` | modified | Removes inline product-card implementation and delegates to shared card. |
| `ProductCard.kt` | added | Reusable product media/title/price/availability/wishlist card. |
| `HomeLoadingClock.kt` | added | Injectable slow-loading timing abstraction. |
| `HomeScreen.kt` | modified | Shared state/skeleton, bounded/adaptive discovery layout, duplicate-state correction. |
| `HomeStateComponents.kt` | added | Home-specific state/skeleton composition using shared foundations. |
| `HomeViewModel.kt` | modified | Eight-second slow state, cancellation, retained-content transition. |
| `LegalSupportScreen.kt` | modified | Shared shell/insets/bounded width; content structure retained. |
| `OrderScreens.kt` | modified | Shared shell/insets/bounded width; order presentation otherwise retained. |
| `ProductCartAction.kt` | modified | Persistent measured purchase bar and compact/large-font stacking. |
| `ProductDetailScreen.kt` | modified | Task-first order, new gallery/viewer, two-pane expanded mode, purchase-bar integration. |
| `ProductDetailTestTags.kt` | modified | Tags for purchase bar/price/availability and media behavior. |
| `ProductPresentation.kt` | added | Named product UI presentation helpers/predicates. |
| `ProfileScreen.kt` | modified | Shared shell/insets/bounded width; form/copy retained. |
| `SearchActions.kt` | modified | Removes obsolete visual Back action. |
| `SearchScreen.kt` | modified | One search model, IME clear/hide, history progressive disclosure, shared shell. |
| `SearchTestTags.kt` | modified | Tags for search settings/state evidence. |
| `AppNavigationItem.kt` | added | Icon+label nav item, selection, touch target, semantics. |
| `CommerceStateComponents.kt` | added | Shared state panel and skeleton. |
| `DestinationScaffold.kt` | added | Primary/secondary app-bar, Up, safeDrawing, inset/IME, spacing and max-width helpers. |
| `PriceBlock.kt` | added | Shared price/range/compare-at/availability hierarchy. |
| `UpdatePolicyBanner.kt` | modified | Accepts caller modifier for root placement; behavior unchanged. |
| `WishlistActions.kt`; `WishlistDestination.kt` | modified | Removes obsolete root Back wiring. |
| `WishlistButton.kt` | modified | Reusable 48dp icon/semantics action. |
| `WishlistScreen.kt` | modified | Shared state panels, clearer empty/error/loading/populated treatment, bounded width. |
| `ic_arrow_back.xml` | added | Standard icon Up affordance. |
| ten `ic_nav_*` selected/unselected XML files | added | Five primary navigation icons and selected variants. |
| `values-en/strings.xml`; `values/strings.xml` | modified | EN/TR compact nav labels, new state/action/account/search/product/cart copy, accessibility text. Existing verbose Profile/Legal/Order copy largely retained. |
| `foundation/config/BrandConfiguration.kt` | modified | Expands configuration types for semantic colors, typography, font weights, and motion. |
| `foundation/ui/CommerceTheme.kt` | modified | Maps expanded brand configuration into Material 3 light/dark color schemes and typography. |

## 12. Ledger conclusions

1. **VERIFIED — GIT:** the UI work was applied to the correct Phase 3 lineage.
2. **VERIFIED — GIT/RENDER:** there are real, customer-visible changes, especially in navigation,
   Categories density, Product hierarchy/media, Search task clarity, Account hierarchy, and shared
   Wishlist/Cart states.
3. A significant part of the work is structural, state-handling, adaptive, accessibility, testing,
   or evidence production rather than normal-size visual redesign.
4. Profile, Address list, Orders, Legal/support, deletion, and update-policy surfaces did not all
   receive a meaningful content-level visual redesign. Several lack direct final screen evidence.
5. CP-06 did not change production UI. It validated and documented the CP-05 production source.
6. All commerce/auth/navigation/data-flow functions listed in Section 6 were already present in
   Phase 3; this effort changed their presentation and selected interaction details only.
