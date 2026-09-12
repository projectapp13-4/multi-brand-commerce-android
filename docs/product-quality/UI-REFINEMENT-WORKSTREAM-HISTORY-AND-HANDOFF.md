# Gürbakır Android — UI/Product Refinement Workstream History, Decisions, Evidence, and Handoff

**Document status:** Master historical narrative + current workstream handoff
**Last updated:** 2026-08-30
**Primary audience:** Project owner, future ChatGPT/Codex sessions, maintainers reviewing UI/product decisions
**Scope:** UI/product-quality history from the completed Phase 3 app through the forensic reconstruction and the later Home → Shop → Find → Saved/Listem → Account refinement work
**Not a release document:** This file does not declare production readiness, P3-16 completion, security hardening, store publication readiness, or whole-product visual completion.

---

## 0. Why this document exists

Gürbakır is already a functioning native Android Shopify commerce application. The difficult part of the recent work was not building missing commerce primitives; it was understanding why an already functional app still did not feel sufficiently deliberate, customer-facing, and visually resolved, then improving it without destabilizing the technical baseline.

The project has now gone through two distinct UI/product-quality eras:

1. a large **Product Quality** workstream that produced substantial real engineering and UI improvements but overclaimed whole-product visual completion; and
2. a later, smaller-batch **UI refinement** workstream that deliberately changed the method: inspect the actual merchant and references, make one bounded screen decision at a time, render on the real Samsung, and keep owner judgment separate from automated PASS results.

This document preserves that story so a future AI session does not have to reconstruct it from thousands of lines of conversation, and—more importantly—does not repeat the same mistakes.

### 0.1 Evidence language used here

Not every statement in this document has the same evidence strength. The following labels should be understood conceptually even when not repeated on every bullet:

- **Project/Git verified:** supported by tracked project documentation, forensic Git reconstruction, or source-history evidence.
- **Tracked forensic conclusion:** taken from the completed Product Quality forensic audit/change ledger.
- **Codex-reported + device-verified:** reported by the implementation agent after actual Samsung use/build/test work.
- **Owner-verified:** the project owner personally tested or explicitly accepted the behavior/result in the UI workstream.
- **Workstream research finding:** observed during merchant/reference research; re-check if the merchant site, external apps, or Shopify setup later changes.
- **Not independently re-inspected here:** this documentation pass does not have the final Windows repository mounted, so the final refinement/docs commits and clean-worktree state are recorded from the owner/Codex Git outputs rather than re-diffed line by line in this document.

The goal is continuity, not false certainty. If a future session finds current source that contradicts this handoff, current source and reproducible evidence win.

### 0.2 Documentation authority

This file is the **master narrative and current handoff** for the UI/Product Refinement workstream. It is the first document a future AI or maintainer should read to understand what happened, what was accepted, what was not, and how to continue.

Two companion evidence documents remain intentionally alongside it:

- `FORENSIC-UI-PRODUCT-QUALITY-AUDIT.md` — forensic evaluation of the earlier Product Quality work and its claims;
- `FORENSIC-UI-CHANGE-LEDGER.md` — source/Git provenance for the earlier Product Quality changes.

Earlier Product Quality checkpoint plans, issue matrices, acceptance notes, evidence packages, and the raw exported UI conversation were historical process artifacts. They were deliberately removed from the active documentation surface after repository-reference validation. They must not override current source, owner decisions, reproducible device evidence, this master document, or the two retained forensic companions.

The documentation consolidation was committed separately so that UI/source history and documentation-history cleanup remain distinguishable in Git.

### 0.3 Privacy and repository hygiene

This document intentionally does **not** contain:

- the owner's test email address;
- OAuth codes, access/refresh/ID tokens, PKCE material, customer IDs, private Shopify/Admin credentials, cart secret parameters, or signing secrets;
- private customer profile/address/order data;
- device serial numbers;
- unrelated personal-app data.

The Account work used an owner-authorized test/session path, but credentials/PII are not part of durable UI documentation.

---

# 1. Product baseline before the UI work

## 1.1 This is not a prototype

The production direction is a native Android clean rewrite. An older Flutter/Riverpod/GraphQL proof-of-concept existed, but it is not the current production application and should not be revived as the UI baseline.

Established architecture includes:

- Kotlin;
- Jetpack Compose;
- single-activity Android architecture;
- typed Navigation Compose routes;
- UDF / ViewModel + `StateFlow`;
- Hilt;
- Apollo Kotlin;
- Room;
- DataStore;
- Android Keystore-backed protected state;
- Shopify Storefront API;
- Shopify Customer Account API;
- Checkout Kit;
- Firebase-related infrastructure.

The architectural direction is documented in the ADR/preparation/Phase 2/Phase 3 corpus. In particular, the app intentionally keeps Storefront and Customer Account concerns separate, uses hosted OAuth rather than app-owned passwords, and keeps protected session material outside ordinary UI state.

## 1.2 Phase 3 already implemented the functional commerce app

Before Product Quality/UI work began, Phase 3 had already implemented the main customer journeys, including:

- Home/discovery;
- Categories/Collections;
- product listing;
- Search and local history;
- Product Detail;
- variants;
- media;
- device-local Wishlist;
- real Storefront cart lifecycle and ownership handling;
- Checkout Kit entry/return handling;
- Shopify Customer Account hosted OAuth/session handling;
- Account;
- Profile;
- Addresses;
- Orders;
- Legal/Support;
- account-deletion request/local-cleanup boundary;
- update-policy behavior;
- localization;
- deep links;
- persistence/state infrastructure.

The later UI efforts must **not** take credit for implementing those capabilities. Their purpose was presentation, interaction, hierarchy, state communication, adaptive behavior, and product quality on top of an existing functional baseline.

## 1.3 Important Phase 3 account/data contracts

Several UI decisions are constrained by deliberate product/security architecture and must not be casually redesigned away:

- **Signed-out Account is a valid Account root state.** It is not a separate native password screen.
- **Customer Account uses Shopify's hosted passwordless OAuth flow.** The app must not invent native email/password registration/recovery semantics.
- **Session/token material is protected.** Access/refresh/ID tokens, PKCE material, raw provider diagnostics, and customer IDs do not belong in customer-facing UI/logs/docs.
- **Profile is intentionally narrow.** The approved production model uses supported customer fields rather than inventing additional PII collection.
- **Addresses and Orders are private session-scoped customer data.** Existing project contracts deliberately avoid treating them as ordinary device-local caches.
- **Wishlist and Search history are device-local/account-independent.** Logout/session expiry does not automatically own or erase them.
- **Account deletion is request-only at the mobile boundary.** The app may direct a signed-in customer to owned merchant resources and perform explicit local cleanup, but it must not claim remote deletion/acknowledgement that the mobile app cannot prove.
- **Legal/Support is public.** It must remain reachable from signed-out states according to the existing route contract.

These are not visual preferences. They are established product/data/security boundaries that future UI work must respect.

## 1.4 P3-16 remains separate

P3-16/release-security work is not part of this UI workstream. The UI process must not silently turn into release readiness, production publication, signing/security hardening, analytics policy, or a new stabilization program.

At the point reconstructed here, P3-16 remains intentionally separate/not started as a UI consequence. Do not use visual acceptance as evidence of release readiness.

---

# 2. Git/source provenance: the UI work was on the correct app

A later forensic investigation was necessary because the disappointing visual result created a reasonable question: was the UI work perhaps done on the wrong source baseline, a stale branch, or a reimplementation?

The answer was **no**.

## 2.1 Key source anchors

| Role | Commit | Meaning |
|---|---|---|
| Last identified Phase 3 production-changing integration | `f338dedfd72e2088c87c85c30826dbb27de3873b` | Completed functional Phase 3 application baseline. |
| P3-15 acceptance/docs descendant | `29bc1049d2eb34787a9f9fd54dfc3a1e49d3dccc` | Records acceptance of the existing Phase 3 app; not a second implementation. |
| Later docs-only Phase 3/P3-16 state | `9832ae4c81679d5a07d22afd4f66652c9f821af5` | Clean source-history state from which Product Quality proceeded. |
| Final Product Quality production-source commit | `0996b9bb054aa837f0404ff01e70a7bab6ec6093` | Final tracked production UI/source changes from the old PQ workstream. |
| CP-06 completion / pre-refinement anchor | `e6a836cd951a54880b269ca6baa2dd7e3cc3b4e3` | Direct child of `0996b9b`; test/evidence/docs only, no production-source change. Later root-screen refinement began from this HEAD. |
| Accepted root-screen UI refinement | `505b04f7897f92a2d7e51c5d4d221cf5fb1b5f41` | Commits the owner-reviewed Home/Shop/Find/Saved/Listem/Account-root changes, root-navigation fix, required resources/localization, and focused tests. |
| Product-quality documentation consolidation | `58223b2fc75c04c9e412eb7db360d8c2c5384f06` | Keeps the master handoff + two forensic companions and removes superseded Product Quality checkpoint/evidence clutter. Final reported worktree is clean. |

Forensic ancestry verified the direct continuation:

```text
f338ded -> 29bc104 -> 9832ae4 -> Product Quality production commits -> 0996b9b -> e6a836c -> 505b04f -> 58223b2
```

The old UI work therefore did **not** suffer from stale-source migration, APK copying, a wrong application, or a disconnected rewrite.

## 2.2 Old Product Quality production-changing commits

The forensic change ledger identifies these main production-changing commits:

| Commit | Main contribution |
|---|---|
| `0ab3d29` | Shell/navigation/UI foundations, insets/IME contract, semantic theme/tokens, shared scaffold/state foundations. |
| `cfc7141` | Home loading/recovery presentation and Categories density/composition. |
| `a7c2304` | Collection cards, shared price treatment, Product Detail hierarchy/purchase bar/fullscreen media. |
| `a41c2ed` | Search, Account, Profile/Address interaction/IME behavior; debug evidence harness. |
| `40ebd2f` | Wishlist/Cart/checkout-state presentation and ownership disclosure. |
| `0996b9b` | Adaptive widths, responsive Product layout, large-font behavior and selected navigation/accessibility semantics. |

The defined implementation path from `9832ae4` to `0996b9b` changed 64 files with thousands of insertions. This was real production work—not merely tests and docs.

## 2.3 Artifact anchors before the new refinement work

| Artifact | Size | SHA-256 | Meaning |
|---|---:|---|---|
| Phase 3 development APK | 20,176,991 bytes | `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E` | Documented Phase 3/P3-15 development artifact. |
| Final old Product Quality APK | 21,126,882 bytes | `F221B5C692889AFFA039B3770E5BC943048F3CB89C85A1216F1B3BAAE2E7D4E1` | Final old PQ production-source artifact; documented Samsung install/pull hash match. |

These hashes are useful provenance anchors, not visual-quality proof.

## 2.4 Final consolidation commits for the later refinement work

After owner review of the root-screen work, the previously uncommitted refinement state was audited before staging. The accepted code/test/resource set was verified as 23 files, built successfully, and committed without the documentation-history files.

- `505b04f7897f92a2d7e51c5d4d221cf5fb1b5f41` — `feat(ui): refine primary screens and root navigation`
  - Home cleanup;
  - Home → Shop primary-root navigation correction;
  - Shop/Categories cleanup;
  - Find/Search cleanup;
  - Saved/Listem functional + merchant-alignment work;
  - Account-root refinement;
  - required tests, vector resources, and EN/TR localization.

Documentation was then consolidated separately:

- `58223b2fc75c04c9e412eb7db360d8c2c5384f06` — `docs(ui): consolidate product-quality history and handoff`
  - retained exactly three active `docs/product-quality` documents;
  - removed 8 superseded top-level Product Quality docs, 17 checkpoint docs, and 177 historical evidence artifacts;
  - deleted the untracked raw `PREVIOUS-UI-CONVERSATION.md`;
  - repository reference checks found no blocking links/dependencies;
  - final reported `git status --short` was clean.

This two-commit structure is intentional: production/UI changes and documentation-history consolidation remain independently reviewable.

---

# 3. What the original Product Quality work was trying to solve

The owner did not begin Product Quality because the app could not navigate or transact. The problem was that the real APK could still feel:

- unfinished;
- crowded;
- confusing;
- generic;
- engineering-oriented;
- insufficiently polished for a customer-facing commerce product.

The master principle was explicit:

> Functional completion and green tests do not prove visual or product-experience completion.

The intended success condition was a real development APK that, at ordinary phone size/default font, felt coherent, deliberate, easy to scan, customer-oriented, and professionally composed without destabilizing existing commerce/account functionality.

The physical rendered application was supposed to be the visual truth. Tests were intended to protect behavior and interaction—not certify aesthetics.

---

# 4. What the old Product Quality work genuinely improved

The forensic audit should not be read as “the old effort was useless.” It produced significant durable improvements that the later refinement work intentionally preserved.

## 4.1 Shell and navigation

Meaningful improvements included:

- icon + label primary navigation;
- clearer selected state;
- adaptive bottom bar / navigation rail behavior;
- primary versus secondary destination hierarchy;
- proper Up/Back ownership;
- shared scaffold/inset rules;
- IME-aware layout ownership;
- root placement for update/special overlays.

## 4.2 Shared visual/system foundations

The old work introduced or strengthened:

- semantic light/dark color mapping;
- typography roles;
- shared commerce state panels/skeletons;
- shared price presentation;
- reusable Product Card;
- bounded content widths;
- responsive Product layouts;
- selected large-text/accessibility semantics;
- focus/keyboard resilience.

These are valuable structural improvements even when they did not fully solve art direction.

## 4.3 Specific screen gains

### Categories

Categories became materially denser and easier to scan. This was one of the strongest old PQ wins.

### Collection/Product Cards

Collection moved to reusable product-card behavior, improving consistency and reducing duplicated card implementation.

### Product Detail

Product Detail received stronger price/purchase hierarchy, a persistent purchase action region, improved media/fullscreen behavior, and responsive treatment. The forensic audit considered Product one of the more substantially redesigned common surfaces.

### Search

Search moved toward one clearer interaction model, removing redundant visible submit/back affordances and improving IME/focus/history behavior.

### Wishlist/Cart

Loading/empty/error/populated states became more coherent; Cart special-state feedback and ownership communication improved.

### Account

The old PQ pass already moved Account somewhat toward task priority, but it did not go far enough. This became important later.

### Adaptive/accessibility

The old work built unusually strong bounded validation around widths, text scaling, keyboard/focus, semantics, dark mode, and selected adaptive states. This infrastructure should remain a regression guardrail.

---

# 5. Why the old Product Quality effort still missed the owner's goal

The forensic audit's most important conclusion was not that the code was wrong. It was that the **definition of success drifted**.

## 5.1 Broad product concern became a finite issue matrix

The original concern was open-ended and perceptual: “Does this real app feel like a finished customer product?”

Execution gradually transformed that into a closable matrix of named findings/checkpoints. Those findings were valuable, but once the finite set became the success definition, passing the matrix could happen without solving the full visual/product problem.

## 5.2 Architecture/system work came before sufficient art direction

A strong shell, tokens, scaffolds, shared state components, responsive widths, and accessibility behavior were built before a convincing brand/product visual direction had been established from real references.

That produced a technically coherent system that could still look generic or default-Compose/Material.

## 5.3 The physical Samsung became more of a gate than a design instrument

Device work was real and useful. It caught layout/focus/state defects. But much of it followed predefined capture/test matrices.

The missing loop was more open-ended:

```text
render -> look critically -> decide what feels weak -> redesign -> render again
```

Instead, the device too often answered “did the required state pass?” rather than “does this screen actually look good?”

## 5.4 Tests/evidence gained too much authority over visual judgment

The old process accumulated substantial automated and physical evidence. That was good engineering, but “103/103” or a hash match cannot answer:

- whether typography feels intentional;
- whether density is commercially effective;
- whether imagery has the right priority;
- whether a screen feels cheap/default;
- whether customer language is natural;
- whether the brand has confidence.

The final acceptance language became too strong relative to what the visual evidence actually established.

## 5.5 Reference APK usage was weaker than the durable record implied

The reference APK was always intended to be clean-room inspiration/evidence, not a donor. However, the durable old PQ history did not prove sufficiently concrete screen-by-screen visual use of it.

The forensic audit concluded that describing it as the “primary external behavior/design reference” overstated what could be demonstrated.

The owner later clarified an important distinction:

**Clean-room restrictions forbid copying proprietary expression; they do not forbid learning from and deliberately adapting generic hierarchy, density, spacing, component composition, navigation, and interaction patterns.**

The old process was too conservative about this distinction.

## 5.6 Customer-facing copy remained too technical

Several account/support/special-state screens still surfaced implementation vocabulary and internal system mechanics. Technical truthfulness is important, but technical transparency is not the same as good customer language.

This became a major focus in the later Account pass.

## 5.7 Whole-product acceptance exceeded the evidence

The old final status was defensible only against the bounded internal PQ contract. It was not defensible as proof that the original owner-observed visual problem had been fully solved.

The more accurate conclusion was—and remains historically important:

> The app became more coherent, usable, adaptive, and well-tested, but whole-product visual redesign/polish was only partial.

---

# 6. Weak areas identified by the forensic audit

The forensic audit's screen-level findings are important because they explain why the later refinement work did not simply “trust the old accepted UI.”

| Surface | Forensic status after old PQ | Important note |
|---|---|---|
| Shell/navigation | Strong improvement | Durable and worth preserving. |
| Home | Improved structurally, still visually generic/utility-heavy | Became the first new refinement anchor. |
| Categories | Material improvement | Later received additional cleanup. |
| Collection | Moderate improvement | Shared cards helped. |
| Product Detail | Stronger redesign | One of old PQ's better results. |
| Fullscreen media | Improved | Useful durable work. |
| Search | Moderate interaction improvement | Later refined again. |
| Wishlist | Better state presentation | Later exposed a real stale-state/root-nav bug. |
| Cart | Better state/ownership communication | Not part of the later root-screen batch yet. |
| Account | More task-first but still technical/text-heavy | Later received dedicated research + redesign. |
| Profile | Small/limited visual change | Technical/safety copy still weak. |
| Address | Form IME/focus improved; list polish not established | Candidate future refinement. |
| Orders | Little meaningful compact-state redesign | Candidate future refinement. |
| Legal/Support | Essentially still engineering/documentation-oriented | One of the clearest remaining weak surfaces. |
| Deletion/update | Mostly structural/special-state polish | Full final visual quality not established. |

The forensic audit explicitly classified the app as **functionally stable within the documented development boundary**, **partially visually redesigned**, **not proven visually polished across the whole product**, and **not release-ready**.

---

# 7. Closing the old Goal and starting a different UI workstream

The owner explicitly closed the old Product Quality Goal. The new UI effort was **not** a CP-07 or a reopening of the old framework.

This matters for future AI behavior:

- do not recreate the old checkpoint bureaucracy;
- do not turn visual work into a new giant audit matrix unless the owner asks;
- do not treat the forensic documents as an instruction to continue old Goal mechanics;
- preserve the old technical gains, but use a different design process.

The first task of the new conversation was a **read-only reconstruction/understanding pass**. It was deliberately forbidden from editing code, opening a new Goal, using limited design quota, or declaring acceptance. Its purpose was to ensure the agent understood the app lineage, inherited functionality, forensic conclusions, reference boundaries, and current mission before touching UI.

---

# 8. New UI method: small, owner-reviewable batches

The intended new loop was initially:

```text
current render
-> visual critique
-> real references
-> design decision
-> implementation
-> real Samsung render
-> self-critique
-> iterate
-> owner review
-> focused regression confirmation
```

The important shift was from **system/checkpoint-first** to **screen/product-decision-first**.

## 8.1 Core operating rules

- Work on one bounded screen/root at a time.
- Existing behavior is a constraint, not something to rewrite for convenience.
- Prefer local/reversible UI changes.
- Do not silently expand into domain/network/persistence/account/cart/checkout/Firebase work unless a real user-journey bug requires a focused correction.
- Build incrementally on the constrained Windows machine; avoid unnecessary clean builds/broad parallel tests.
- Render on the physical Samsung.
- Tests are regression guardrails, not visual judges.
- Do not commit experimental UI work until the owner decides how to consolidate it.
- Stop after the requested batch and wait for owner review.

## 8.2 The practical objective changed during Home

The new workstream originally hoped to establish a significantly stronger visual/art direction beginning with Home.

After several Home attempts, the owner concluded that the model was still not achieving the desired level of “beautification.” Rather than spending unlimited cycles pretending otherwise, the objective was reframed more practically:

> **cleanup/refinement rather than claiming a new art direction**

The later root screens therefore focused on:

- less clutter;
- better scanability;
- improved spacing/density;
- clearer hierarchy;
- calmer presentation;
- less unfinished/default-Compose feeling;
- less engineering-oriented customer copy;
- preserving real functionality.

This is **not** a declaration that minimalism is always correct. It is an honest scope correction after the Home experiments.

---

# 9. Reference strategy was upgraded substantially

A major process correction was to stop treating “reference” as a ceremonial word.

## 9.1 Reference APK: three evidence layers

The same successful commerce reference APK could be used through three distinct layers:

1. **Prepared project evidence**
   Existing maps/reports covering feature groups, destinations, GraphQL operations, endpoints, storage records, and screen/journey structure.

2. **Static APK/package/resources/files**
   Clean-room static analysis for structure/resources/behavior clues. This is analysis only; not an implementation donor.

3. **Live installed reference app on the Samsung**
   When accessible, navigate and observe actual hierarchy, interaction, density, imagery, task priority, and state behavior.

The intended reasoning chain became:

```text
observation
-> why it works
-> compare with Gürbakır data/contracts/Android conventions
-> independently adapt the generic principle
-> inspect Gürbakır render on Samsung
```

## 9.2 Clean-room boundary

Allowed learning includes generic:

- hierarchy;
- spacing rhythm;
- density;
- task priority;
- media composition;
- navigation composition;
- interaction/state patterns;
- purchase-action placement;
- customer-flow sequencing.

Do **not** copy:

- proprietary branding;
- assets;
- distinctive exact layouts;
- copy/text;
- icons as proprietary expression;
- decompiled implementation;
- private backend behavior.

## 9.3 User-provided visual observations from the reference app

The live/screenshotted reference (“third-party reference”) demonstrated several generic patterns that materially changed how the project evaluated Gürbakır:

- compact centered brand header with search/cart actions;
- imagery-first Home composition;
- campaign/section hierarchy;
- two-column collection/category grids with labels outside media;
- horizontal product rails with clear image/title/price/action hierarchy;
- compact root Categories structure rather than repeated headings;
- clean guest Account/list structures;
- persistent primary bottom navigation.

The important lesson was not “copy third-party reference.” It was that a successful commerce app often uses screen area, imagery, and hierarchy more deliberately than the current Gürbakır implementation did.

## 9.4 Derimod as a second independent live reference

The owner installed Derimod on the physical phone and authorized safe use as a visual/product benchmark. Later, a signed-in Derimod session was also available.

Allowed interactions included safe navigation, wishlist/favorite behavior, switching tabs, relaunching, and inspecting Account. Purchases, unrelated account edits, backend interception, credential extraction, or other risky activity were excluded.

Derimod was useful for generic observations such as:

- header density;
- media priority;
- section rhythm;
- product/category pacing;
- familiar heart/favorites behavior;
- signed-in Account grouping/greeting/purchases/support/profile-address settings/sign-out.

Its promotional density and exact expression were explicitly **not** treated as something Gürbakır should copy.

## 9.5 Merchant truth became a first-class reference

The strongest process correction came later:

> A merchant app should not be designed without inspecting the merchant.

Relevant evidence sources became:

- `gurbakir.com` in the existing browser session;
- connected Shopify plugin/Admin data, read-only where relevant;
- current app/domain contracts;
- live/static/prepared reference APK evidence;
- Derimod;
- physical Gürbakır render.

This introduced a stronger authority order for merchant-facing decisions:

1. **Gürbakır merchant/product truth**
2. **actual Android capability and existing contracts**
3. **successful mobile reference patterns**
4. **Android usability/conventions**
5. **craft/design heuristics**

Reference apps are not allowed to override what the merchant actually calls or offers.

---

# 10. Brand/product understanding correction

Early Home work leaned too quickly into an abstract “premium/editorial copper” direction. Later research showed that this was too generic and incomplete.

Workstream research on the public merchant experience indicated that Gür Bakır's product value is communicated through concrete craft/product properties such as:

- copper thickness/material quantity;
- weight;
- form;
- hammered workmanship;
- handle/form details;
- intended use;
- maintenance;
- polishing;
- tinning/re-tinning;
- long-term care/use;
- workshop/professional-kitchen context.

The merchant also has a real care/tinning capability. This is more specific than merely saying “premium copperware.”

### 10.1 Safe Phase 3 brand defaults were not a final brand system

The earlier Phase 3 design baseline—deep green, copper, ivory, system sans, Material icons, text wordmark—was intentionally safe/reversible because final approved logo/font/editorial assets were not established in the implementation packet.

That should not be retroactively interpreted as a final creative brand kit.

### 10.2 Public logo/brand evidence versus app approval

The public site displayed a graphical logo/wordmark during the workstream, but provenance/release approval for directly shipping public-site assets in the app was not established by this UI process.

Therefore future work must distinguish:

- “this exists publicly on the merchant site” from
- “this asset is approved and correctly sourced for the native app.”

### 10.3 Multi-brand architecture constraint

The project is intended to become a reusable Shopify-mobile foundation for other brands. Shared commerce architecture/modules/configuration should therefore remain brand-neutral.

Gürbakır-specific visual decisions belong in brand-owned configuration/assets/components, not as hardcoded “copper aesthetics” inside shared commerce mechanics.

This is a major future-product constraint.

---

# 11. Prompting/process correction for AI-assisted work

The project also changed how it prompts coding agents.

Early UI prompts became very long and repetitive. The later method followed the current OpenAI-style principle of giving the model:

- the task/outcome;
- relevant product/domain context;
- hard constraints/authorization boundary;
- success/stop condition;

while avoiding repeating the same rule five times or prescribing every micro-step.

A useful working template became:

```text
Task
-> desired outcome
-> relevant evidence/context
-> hard boundaries
-> real-device success condition
-> stop
```

The purpose is not token minimalism for its own sake. It is to reduce checklist behavior and give the reasoning model enough room to make actual product decisions.

The Account Stage 1 process added another important prompting principle:

> A research/plan artifact is primarily for the agent's own later implementation readiness, not a presentation to impress the owner.

The plan should therefore resolve hierarchy/scope/contracts and record genuine uncertainties, not pad itself with recommendations merely to look complete.

---

# 12. New workstream chronology — Home

Home was chosen as the first visual anchor because it exposes the app's product identity, merchandising rhythm, category discovery, imagery, typography, and use of space.

## 12.1 First Home direction: “editorial copper collection showcase”

The first serious direction tried to create a more distinctive hierarchy:

- replace a generic Home heading with a discovery-oriented copper message;
- make the first collection a larger 4:3 gateway;
- place remaining collections in a compact grid;
- make the featured product more compact;
- flatten surfaces;
- reduce oversized typography;
- keep help/legal quiet.

This was a genuine move away from “change some spacing” and toward a compositional idea.

However, the result still felt model-generic and relied on invented/abstract merchant copy such as “Discover copperware.” It did not demonstrate enough understanding of the real merchant.

## 12.2 Merchant research correction

After direct merchant/site research, invented discovery copy was removed and the direction became more evidence-conservative.

A second version used:

- equal-priority collection cards;
- uncropped 3:4 media;
- compact featured product;
- quieter help/policy treatment.

This was safer but also too generic. It solved some awkwardness without creating a convincing visual identity.

## 12.3 Live reference correction and final Home attempt

After the reference APK became available as a live visual benchmark, Home moved again toward:

- a two-column square-image collection grid;
- labels outside images;
- four live collection items;
- a full-width image-led featured product;
- demotion of zero/weak price presentation where appropriate;
- reduced opening whitespace;
- removal of redundant Help/Policies content from Home when Account already owned that task.

## 12.4 Owner verdict on Home

This distinction is critical and must not be lost:

> **Home was accepted for now as a cleanup/simplification, not as a successful beautification or successful new art direction.**

The owner explicitly judged that the model still had not achieved the desired “beautiful/redesigned” result. The right decision was to stop spending cycles pretending otherwise and continue with a more practical refinement objective.

Future AI must **not** cite Home as proof that the new visual direction was fully solved.

---

# 13. Root navigation bug discovered after Home

A real functional issue appeared during the UI work:

- Home `View all` opened Shop/Categories;
- the Shop root became selected;
- tapping Home bottom navigation could fail to return correctly;
- Android Back could return to Home because Shop had been pushed as a child destination rather than entered as a root.

## 13.1 Root cause

Home `View all` used a plain navigation call to `CategoriesRoute`, while the bottom navigation treated Categories/Shop as a primary root destination.

That created disagreement between:

- visible root selection;
- actual back stack;
- root navigation semantics.

## 13.2 Fix

The existing shared primary-root navigation contract was reused (`navigatePrimary(...)`) rather than creating a new workaround.

Samsung verification covered Home → View all → Shop → Home, direct roots, Search/Find → Saved → Home, and Back behavior.

This incident established a later rule: **when a UI task reveals a functional journey bug, do not hide behind “UI-only scope.” Fix the smallest real root cause before calling the screen complete.**

---

# 14. New workstream chronology — Shop

After Home, the process moved through root screens one at a time with explicit owner review.

The Shop/Categories root cleanup addressed a screen that still felt more verbose/tall than necessary.

## 14.1 Changes

Reported changes included:

- removal of redundant `Categories` / `Explore` heading duplication;
- conversion of tall 3:4 caption cards into square cropped tiles;
- labels moved outside imagery;
- regular centered labels with stable two-line geometry;
- a restrained two-column grid;
- special handling so the fifth live collection did not appear as an accidental full-width mismatch.

Primary implementation was localized to `CategoriesScreen.kt`.

## 14.2 Device iteration

The first Samsung render exposed the fifth-item width inconsistency and label-weight concerns; those were corrected before stopping.

## 14.3 Owner verdict

The owner described the result essentially as **“at least orderly.”**

That is the correct acceptance level. It is an accepted cleanup, not evidence that the project suddenly achieved the original high-level beautification ambition.

---

# 15. New workstream chronology — Find

Find/Search was then cleaned up using a deliberately leaner prompt.

## 15.1 Problems identified

The current screen had too much UI before the actual results:

- oversized search field;
- permanently visible helper text;
- overly prominent history controls;
- excess vertical space before useful content.

## 15.2 Changes

Reported changes included:

- a compact ~56dp search field;
- direct clear action;
- minimum-length guidance shown only when relevant;
- denser history/settings treatment;
- quieter result-count hierarchy;
- existing catalog/product cards preserved rather than unnecessarily redesigned again.

Files reported for this pass included:

- `SearchScreen.kt`;
- `ic_search_clear.xml`;
- Turkish/English strings.

## 15.3 Verification

Samsung checks included:

- initial/history state;
- keyboard/IME;
- one-character guidance;
- clear action;
- a safe real search (`cezve`) returning products;
- results with IME on/off;
- root switching and Back.

The live reference Search surface was blocked by its own offline/service state during that pass, so prepared/static evidence was used rather than bypassing the gate.

## 15.4 Acceptance

Find was accepted at the current cleanup/refinement level.

---

# 16. New workstream chronology — Saved/Wishlist/Listem

Saved became the most instructive part of the new process because it exposed failures in **both functional verification and merchant/product reasoning**.

## 16.1 First Saved cleanup

The initial UI cleanup reported:

- smaller/centered empty state;
- quieter device-only disclosure;
- responsive two-column populated product grid;
- clearer image/title/price hierarchy;
- full-width error/unresolved state;
- secondary `Clear all` treatment.

The reference pattern supported concise empty states and image-led saved-product presentation. The live reference Wishlist was blocked offline during that specific pass, so prepared/static evidence was used.

## 16.2 The first “complete” claim was wrong

The implementation report admitted that a retained Saved instance still required recreation to reflect saves made from Find, but still presented the screen as effectively complete.

The owner then found the real customer-facing failures:

1. save a product;
2. tap Saved;
3. the app could open/show Shop instead of Saved or Saved could become unreachable until restart;
4. after relaunch, saved items did not reliably appear as expected;
5. `Clear all` did work.

This was a serious process correction:

> A visually cleaned screen is not complete if its real customer journey is broken.

## 16.3 Functional root causes

After focused diagnosis, the reported root causes were:

### A. Stale retained Saved state

`WishlistViewModel` loaded once rather than continuously observing Room wishlist membership changes. A retained root ViewModel could therefore remain stale after saving from another destination.

### B. Wrong root navigation from Saved Browse

Saved's Browse action directly stacked Shop/Categories beneath/around the Saved root instead of using the shared primary-root navigation contract. Visible root selection and restored navigation state could disagree.

These causes match the owner's observed symptoms.

## 16.4 Functional fix

Reported functional changes were focused in:

- `ProductionApp.kt`;
- `WishlistDestination.kt`;
- `WishlistViewModel.kt`;
- `WishlistViewModelTest.kt`.

The fix:

- observes wishlist membership continuously and reloads/resolves products when membership changes;
- routes Browse through the existing primary-root navigation contract;
- adds a regression test for immediate updates without ViewModel recreation.

### Owner verification

The owner personally tested the functional fix and confirmed that it worked. This is one of the strongest acceptance points in the current workstream.

The relevant expected behavior is now:

- Save updates the list immediately;
- Saved/Listem root opens correctly;
- no app recreation is required to see newly saved items;
- root switching does not lose/stale the list;
- relaunch preserves the intended device-local list;
- remove-one persists;
- Clear all persists;
- re-save after Clear all works;
- Back/root behavior remains correct.

## 16.5 Usage-limit interruption and safe continuation lesson

The Saved fix was interrupted once by the coding-agent usage limit after partial edits. The worktree showed several modified files with an incomplete run.

The correct continuation strategy was **not** to reset or blindly continue. The next prompt required the agent to:

- inspect `git status` and the full diff;
- understand what the interrupted run had already changed;
- preserve unrelated accepted UI work;
- verify whether the partial Saved code was coherent;
- continue from the safest correct point.

This should become a general rule for AI-assisted dirty-worktree development.

## 16.6 Merchant parity discovery: Gür Bakır calls this task `Listem`

A merchant-site screenshot and later research changed the product discussion significantly.

The Gür Bakır storefront exposed the equivalent customer task as **`Listem`**. During the research pass, the agent reported that the web implementation stores the list in browser `localStorage` under:

```text
gurbakir_listem_v1
```

No Customer Account/cross-device synchronization was proven. Android likewise remains device-local, but through Room rather than browser storage.

The first agent conclusion was too conservative: it argued that using merchant terminology such as `Listem` could imply nonexistent web/app synchronization.

That reasoning was explicitly corrected.

### Key product lesson

**Shared terminology does not itself promise shared storage or cross-device synchronization.**

If web and native app both represent the same customer task—“my saved product list”—they may legitimately use the same merchant-facing concept even if each client currently persists it locally.

A synchronization promise would require explicit wording/behavior such as “available on all devices,” not merely using the merchant's list name.

## 16.7 Merchant-alignment correction

The agent was asked to re-evaluate the product decision in this order:

1. merchant truth;
2. actual Android capability;
3. reference/Derimod mobile patterns;
4. Android usability.

It then reported:

- Gür Bakır web consistently uses `Listem`;
- web persistence is browser-local;
- Android persistence is Room/device-local;
- no Customer Account linkage was proven;
- Shopify/Admin integration-inventory access was denied during the specific check, and this limitation was reported rather than guessed around;
- the web glyph was list-like, not the initially assumed bookmark;
- Derimod supported the familiar mobile heart/favorites pattern;
- reference evidence also supported a dedicated wishlist root/local retention pattern.

## 16.8 Final current product decision for Saved

The reported merchant-aligned result became:

- **Turkish:** `Listem`
- **English:** `My list`
- heart icon retained;
- device-local nature disclosed clearly;
- product count surfaced;
- empty/remove/clear copy aligned to the merchant task;
- no invented cross-channel synchronization;
- no new server-side wishlist feature.

Merchant-alignment correction touched only Saved-local presentation/resources, reported as:

- `WishlistScreen.kt`;
- Turkish strings;
- English strings.

The already owner-verified functional navigation/persistence fix was intentionally left unchanged.

## 16.9 Saved artifact evidence

After merchant alignment, the reported development APK was:

- size: **21,128,360 bytes**;
- SHA-256: `DB3BED0033E1902F531A1A2F6261B989E30A9CBB75880236D8B1955ED800B8DC`.

The app was installed and inspected in empty and populated states on the Samsung.

## 16.10 Saved's lasting process lessons

Saved produced several durable rules:

- Do not accept a screen because the Compose layout is cleaner if the real journey is broken.
- A `ViewModel`/navigation/persistence issue discovered through UI review is legitimate scope for a focused fix.
- Merchant semantics must be checked before choosing customer terminology.
- Technical storage differences do not automatically imply different customer concepts.
- If Shopify/plugin evidence is unavailable/denied, record the gap; do not fabricate a backend explanation.
- The working app's truth must beat a generic pattern from Derimod/reference apps.

---

# 17. New workstream chronology — Account

Account was treated differently because it is not merely a menu. It sits on top of authentication, session lifecycle, private customer data, hosted browser flow, deletion/privacy boundaries, and several protected child destinations.

This was explicitly recognized as one of the most important screens in the UI workstream.

## 17.1 Why a normal “cleanup Account” prompt was insufficient

A quick first Account prompt was rejected as too superficial. Before changing hierarchy, the agent needed to understand which current elements were:

- real customer capabilities;
- required security/session states;
- inherited Phase 3 technical artifacts;
- merely poor customer-facing presentation;
- missing merchant/reference evidence.

The repository documentation confirmed that Account uses hosted passwordless Customer Account OAuth and that session/token/provider internals should not leak into customer UI.

## 17.2 Account Stage 1: research and implementation planning only

Stage 1 deliberately forbade UI/source changes. Its goal was to prepare the agent itself for later implementation rather than produce a decorative owner-facing report.

Sources inspected/considered included:

- current Account source and states;
- relevant Phase 3 Account/Profile/Address/Order/Legal/deletion contracts;
- Gürbakır hosted customer-account experience;
- Shopify/plugin/Admin evidence where safely available/read-only;
- prepared/static reference APK evidence;
- live reference app where accessible;
- Derimod;
- Samsung behavior.

### Stage 1 findings

The research confirmed:

- auth is **Shopify-hosted passwordless**, not a native password model;
- the current root already has typed routes for Account/Orders/Profile/Addresses/Legal-Support/deletion;
- Profile is constrained to supported customer fields;
- private Orders/Addresses live within session/privacy boundaries;
- `Listem`, search history, and cart are not simply “Account-owned data”;
- signed-out Account is a first-class state;
- raw provider/token/server mechanics should not be steady-state customer content.

The central UI diagnosis was that **customer tasks such as Orders/Profile/Addresses were competing with or sitting behind session verification, memory-storage, Shopify/cart mechanics, market/device-data, and other implementation language.**

The safest useful generic reference pattern was:

> compact identity + scannable grouped task rows

Legacy reference password/signup behavior was explicitly rejected because it does not match Gürbakır's current Customer Account contract.

### Planned hierarchy

The Stage 2 plan settled on roughly:

**Signed out**

1. Account/Hesap title;
2. progress/error only when relevant;
3. compact Sign in surface;
4. Help and policies;
5. collapsed Data on this device.

**Signed in**

1. compact identity header;
2. `Your account` group: Orders / Profile / Addresses;
3. `Support and privacy`: Help / Device data / Account deletion request;
4. visible but lower-emphasis Sign out.

Additional decisions:

- provider/token/server/cart details leave the steady root UI;
- `Refresh session` is not a permanent customer action; retry appears only for a retryable retained-session problem;
- routes, controllers, session behavior, GraphQL/domain logic, hosted auth and child-screen implementations are preserved;
- existing focus/Back/session-expiry/privacy contracts remain intact.

## 17.3 Account Stage 2 implementation

Reported implementation areas:

- `AccountScreen.kt`;
- `AccountMenuRow.kt`;
- `AccountDeletionEntryButton.kt`;
- Turkish/English Account strings;
- Account-local vector icons;
- `AccountScreenTest.kt`.

Reported presentation changes:

- customer-facing copy rewritten;
- signed-in identity compacted;
- Orders/Profile/Addresses presented as grouped rows;
- Support/privacy and device-data information moved to a lower hierarchy;
- Sign out made directly visible but not dominant;
- signed-out/restoring/hosted-handoff/logging-out/recoverable-error branches separated coherently;
- 56dp task rows and appropriate outline-style icons;
- no rewrite of Account controller/session/domain/GraphQL/child routes.

The important product principle was **not** “hide all technical information.” Instead:

- remove raw implementation detail from steady customer hierarchy;
- keep truthful states;
- rewrite/demote/disclose progressively where appropriate;
- retain security/privacy-critical information when it genuinely matters.

## 17.4 Stage 2 validation

The agent reported:

- focused production/instrumentation compilation passed;
- `AccountViewModelTest` passed;
- `AccountControllerTest` passed;
- Samsung SM-A225F: 19 focused Account/shell/navigation tests with no failures/skips;
- signed-out, authenticated fixture, restoring, hosted handoff, logout progress, recoverable error, retained identity, 200% font states checked;
- Help → Back and Home → Account → Back manually verified.

The Stage 2 development APK was reported as:

- size: **21,116,626 bytes**;
- SHA-256: `4DD4A4CAF9ED66100B22363C3925859AE8E6B6C551EE18CA2005DBCA575E5CE7`;
- package: `com.gurbakir.mobile.dev.debug`;
- installed APK hash matched the local APK.

## 17.5 Important gap discovered after Stage 2: fixture validation was not enough

Although the fixture-based authenticated UI coverage was strong, the Stage 1/2 agent had not actually completed the available real signed-in Gürbakır and Derimod Account inspection before finalizing product decisions.

This mattered because the owner had already authorized normal sign-in/session use.

The project deliberately did **not** restart Account research from scratch. To conserve quota and avoid redundant work, a focused “close the missed signed-in evidence” pass was used.

## 17.6 Real authenticated Gürbakır verification

On the Samsung, the existing Gürbakır Customer Account session restored when Sign in was used. This is consistent with the project's real Shopify Customer Account/session implementation rather than a fake password UI.

The agent verified the real signed-in Account root and safely opened/returned from:

- Orders;
- Profile;
- Addresses.

No customer data was edited.

The agent reported that the real identity/session data rendered correctly and the basic Back flows worked.

## 17.7 Real signed-in Derimod comparison

The already signed-in Derimod Account experience showed generic patterns including:

- greeting/identity;
- purchases/orders prominence;
- support access;
- combined profile/address settings;
- visible sign-out.

These observations reinforced the implemented Gürbakır hierarchy rather than exposing a material design mistake. Derimod's promotional density was explicitly judged inappropriate to copy.

## 17.8 Final Account decision

The live evidence did **not** justify another Account code change.

Therefore:

- no Account redesign was reopened;
- no unnecessary rebuild/tests were run;
- the current Account root design was retained;
- Gürbakır was left signed in on the real app after verification.

This is an important example of efficient evidence use: **if live evidence confirms the current decision, do not change code just to prove work happened.**

## 17.9 Account scope boundary remains important

The new workstream refined the **Account root**. It did **not** silently redesign every child screen.

Profile, Address List/Form, Orders, Legal/Support, deletion, and update-special surfaces still have their own historical strengths/weaknesses and should be treated as separate future batches if the owner chooses to continue.

---

# 18. Physical-device methodology and incidents

## 18.1 Primary device

The main visual/interaction device is:

- Samsung A22 / SM-A225F;
- Android 13 / API 33;
- 720 × 1600;
- approximately 300 dpi.

Emulator use is not practical on the current host because of virtualization limitations. The project therefore depends heavily on real-device iteration.

## 18.2 The device is the visual judge, not merely a runner

For UI work, the required question is not only:

> Did the state render and the test pass?

It is also:

> Does this screen actually look deliberate at normal phone size?

This distinction remains mandatory for future UI batches.

## 18.3 Connection interruptions

Samsung/ADB connectivity was intermittent during parts of the workstream. One interruption coincided with Saved work and contributed to concern that the coding agent had lost context or rushed product research.

Future agents should:

- distinguish device disconnect from app/product failure;
- avoid making visual claims without a successful render/inspection;
- leave a safe worktree state if quota/device access ends unexpectedly.

## 18.4 Accidental unrelated-app launch incident

During device automation, the agent accidentally opened Spotify and Messages while trying to switch apps.

This was explicitly rejected as outside the authorized task.

The process rule was corrected to:

> **Use verified package IDs/components for app switching; never locate target apps by uncertain launcher/Recents coordinate guessing.**

If an unrelated personal app opens accidentally:

- do not inspect/interact with its content;
- leave immediately;
- return to the authorized target.

Verified package IDs reported later include:

- Gürbakır development app: `com.gurbakir.mobile.dev.debug`;
- Derimod: `com.akinon.derimod`.

The reference app should likewise be targeted only after its actual package identity is verified.

## 18.5 Temporary captures

The final Account signed-in verification reported temporary captures remaining only under ignored `build/` output because deletion was blocked by execution policy. They were not treated as tracked project changes.

---

# 19. Testing philosophy after the forensic correction

The project has substantial automated test infrastructure and should keep it.

But the roles are now separated:

## Tests can establish

- navigation contract regressions;
- state/persistence regressions;
- basic interaction behavior;
- semantics/tags;
- focus/IME behavior;
- compilation/build health;
- bounded large-font/adaptive regressions.

## Tests cannot establish

- good visual hierarchy;
- strong merchant fit;
- pleasing image composition;
- brand confidence;
- whether copy feels natural;
- whether a screen looks generic/default;
- whole-product polish.

When usage quota is low, broad reassurance testing may be skipped in favor of:

- essential live customer-flow verification;
- focused tests directly related to changed code;
- safe stopping without half-finished edits;
- owner-side follow-up testing.

This is not permission to skip critical correctness checks. It is a prioritization rule.

---

# 20. Current acceptance/status matrix after the new root-screen work

The following is the most useful compact state for future continuation.

| Area | Current status | Acceptance meaning |
|---|---|---|
| Phase 3 functional baseline | Preserved | Do not rebuild/reinvent. |
| Old Product Quality foundations | Preserve selectively | Shell/navigation/insets/theme/shared states/adaptive/accessibility gains remain valuable. |
| Home | **Accepted for now as orderly cleanup** | Explicitly **not** a successful beautification/new art-direction proof. |
| Root navigation bug | Fixed and Samsung-verified | Primary-root navigation contract reused. |
| Shop/Categories root | Accepted cleanup | More orderly/scanable; not claimed as major art-direction redesign. |
| Find/Search root | Accepted cleanup | Compact search/history/result hierarchy; real search/IME checked. |
| Saved functional flow | **Owner-verified fixed** | Immediate updates, correct root, persistence/relaunch/remove/clear behavior accepted. |
| Saved merchant alignment | Current decision: `Listem` / `My list` | Device-local truth preserved; no sync promise; heart retained; copy/count refined. |
| Account root Stage 1 research | Complete | Real contracts/merchant/reference constraints understood before implementation. |
| Account root Stage 2 | Complete | Customer-task-first root implemented; domain/auth/routes preserved. |
| Account real signed-in evidence | Complete | Real Gürbakır session + Orders/Profile/Addresses Back flows checked; signed-in Derimod compared. |
| Profile | Not newly redesigned in this workstream | Old forensic weaknesses may remain. |
| Address List/Form | Not newly redesigned as a dedicated batch | Old form focus/IME work remains; list visual quality was not established by old forensic audit. |
| Orders | Not newly redesigned as a dedicated batch | Old audit found little meaningful normal-state visual redesign. |
| Legal/Support | Still a high-priority weak candidate | Old audit found it notably engineering/documentation-oriented. |
| Deletion/update special states | Not newly redesigned as dedicated batches | Existing safety contracts must be preserved. |
| Whole-product visual polish | **Not established** | Do not use completed root cleanup as a global “UI done” claim. |
| Release readiness/P3-16 | **No / separate** | UI work does not satisfy release/security/commercial gates. |

---

# 21. Current repository/worktree model

The later refinement work is no longer an uncommitted experiment. It was explicitly audited, staged, committed, and then followed by a separate documentation-consolidation commit.

Final reported state for this handoff:

- branch: `main`;
- pre-refinement anchor: `e6a836cd951a54880b269ca6baa2dd7e3cc3b4e3`;
- accepted UI/source commit: `505b04f7897f92a2d7e51c5d4d221cf5fb1b5f41` (`feat(ui): refine primary screens and root navigation`);
- documentation-consolidation commit / current reported HEAD: `58223b2fc75c04c9e412eb7db360d8c2c5384f06` (`docs(ui): consolidate product-quality history and handoff`);
- final reported `git status --short`: **clean**;
- active `docs/product-quality` surface: exactly this master document plus the forensic audit and forensic change ledger.

A future session should still inspect `git status` and HEAD before editing, because the repository may have advanced since this handoff. But it should no longer assume that the Home/Shop/Find/Saved/Account work is a dirty worktree that needs consolidation.

Do not reset, rewrite, or revert the accepted commits merely because older conversation/history artifacts describe an earlier uncommitted state.

---

# 22. Reported APK timeline during the new refinement work

Intermediate artifacts are included only where the workstream recorded useful provenance. Some earlier reports retained only a short SHA prefix; do not fabricate full hashes.

| Stage | Reported size | Reported SHA evidence | Notes |
|---|---:|---|---|
| Navigation fix after Home | 21,127,458 bytes | prefix `64E18179…` | Primary-root bug correction. |
| Shop cleanup | 21,127,458 bytes | prefix `527302…` | Categories/Shop root cleanup. |
| Find cleanup | 21,128,256 bytes | prefix `91D756…` | Search/Find cleanup. |
| First Saved cleanup | 21,127,964 bytes | prefix `2E147…` | Later found functionally incomplete. |
| Saved functional fix | 21,127,964 bytes | `0B7C834EA787E32A98E21C1EFDC46DCF98C83E73009D36C7013ED41464E405B1` | Functional fix report; owner later confirmed behavior. |
| Saved merchant alignment | 21,128,360 bytes | `DB3BED0033E1902F531A1A2F6261B989E30A9CBB75880236D8B1955ED800B8DC` | `Listem` / `My list` correction. |
| Account Stage 2 | 21,116,626 bytes | `4DD4A4CAF9ED66100B22363C3925859AE8E6B6C551EE18CA2005DBCA575E5CE7` | Installed APK hash matched local; no rebuild required after final live signed-in evidence. |

The final real signed-in Account comparison did not change code, so the last reported Account APK remained the relevant installed artifact.

---

# 23. File-level summary of the committed later root-screen work

The final pre-commit audit staged and committed 23 accepted code/test/resource files. This section summarizes that committed scope; use commit `505b04f...` for exact diff-level review.

## Home

- `HomeMedia.kt`
- `HomeScreen.kt`

## Navigation/root behavior

- `ProductionApp.kt`

## Shop

- `CategoriesScreen.kt`

## Find

- `SearchScreen.kt`
- `ic_search_clear.xml`

## Saved/Listem

- `WishlistDestination.kt`
- `WishlistScreen.kt`
- `WishlistViewModel.kt`
- `WishlistViewModelTest.kt`

## Account root

- `AccountScreen.kt`
- `AccountMenuRow.kt`
- `AccountDeletionEntryButton.kt`
- `AccountScreenTest.kt`
- `ic_account_address.xml`
- `ic_account_cart.xml`
- `ic_account_delete.xml`
- `ic_account_device.xml`
- `ic_account_help.xml`
- `ic_account_orders.xml`
- `ic_expand_more.xml`

## Shared localization

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Earlier Home and Saved iterations remain relevant only as process history; commit `505b04f...` is the accepted source anchor for the final root-screen batch.

---

# 24. Do-not-regress rules for future AI sessions

This section is intentionally direct. These are the highest-value continuity constraints.

## 24.1 Product/source boundaries

Do not:

- rebuild the app from scratch;
- revive the old Flutter POC;
- treat old Product Quality as if it implemented the Phase 3 commerce domain;
- reopen the old Product Quality Goal or invent CP-07;
- start P3-16 as part of UI cleanup;
- rewrite Storefront/Customer Account/cart/checkout architecture for visual convenience;
- overwrite/reset accepted source history or a future dirty worktree without first understanding current HEAD/status.

## 24.2 Visual/process boundaries

Do not:

- claim “tests green = design good”;
- claim whole-product polish because root screens were cleaned up;
- treat Material defaults as a finished visual identity;
- design a merchant screen without inspecting the merchant when relevant evidence is available;
- mention reference apps without actually using the available evidence;
- copy proprietary reference branding/assets/text/exact layouts/code;
- refuse to adapt generic reference patterns merely because of clean-room constraints;
- spread one experimental screen direction globally before owner review;
- stop at the first technically correct render when a design iteration was requested;
- use the physical device only as a PASS gate;
- hide a real functional journey bug behind “UI-only scope.”

## 24.3 Merchant/product truth boundaries

Do not:

- invent campaign slogans, brand claims, care claims, sync behavior, account behavior, or customer capabilities without evidence;
- assume website data/storage implementation automatically equals mobile backend behavior;
- assume separate local persistence means the customer concept must have a different name;
- imply cross-device sync for `Listem` unless it is actually implemented/proven;
- hardcode Gürbakır/copper-specific aesthetics into shared multi-brand commerce architecture.

## 24.4 Account-specific boundaries

Do not:

- add native password/login/recovery UI;
- expose OAuth/session tokens or raw provider/server mechanics in customer UI;
- invent extra customer profile fields;
- treat Orders/Addresses as casual device-local data;
- claim remote account deletion from a local clear/browser return;
- remove required truthful security/privacy states merely because they are technical;
- redesign all Account child screens in the same batch unless explicitly authorized.

## 24.5 Device/privacy boundaries

Do not:

- open unrelated personal apps intentionally;
- guess launcher/Recents coordinates when package-targeted switching is possible;
- inspect Messages, Spotify, contacts, photos, notifications, files, or unrelated personal content;
- use private account data as documentation evidence;
- perform purchases or destructive merchant/customer mutations merely to make a UI acceptance matrix green.

---

# 25. What future UI work should probably examine next

No next screen is automatically authorized by this document. The owner should choose the next batch.

However, if the objective remains to finish the areas the forensic audit identified as visually weak, the strongest candidates are:

1. **Profile**
   Preserve the narrow supported Customer Account field contract; focus on customer language, form hierarchy, save/error presentation, and removal of unnecessary implementation vocabulary.

2. **Address List** (and then form only if needed)
   The form has meaningful old IME/focus work, but the final Address List visual quality was not established. Preserve the Turkey/PII/session contracts.

3. **Orders List/Detail**
   The old forensic audit found little meaningful visual redesign. Preserve read-only/private/session behavior and real fulfillment/tracking truth.

4. **Legal/Support**
   Historically one of the clearest examples of engineering/documentation-oriented customer content. This should probably receive merchant-language and information-architecture research before implementation.

5. **Cart/special states**
   Old PQ did meaningful state work; revisit only if owner/device review still finds visual/product problems.

6. **Product Detail/Collection**
   Old PQ already invested more here. Do not churn them without a specific owner-observed problem or stronger visual direction.

The safest continuation model is still:

```text
research current merchant/task truth
-> inspect current implementation
-> inspect relevant live/prepared references
-> make a bounded plan
-> implement one screen/batch
-> real Samsung inspection
-> owner review
-> stop
```

---

# 26. How to handle merchant/Shopify evidence in future UI tasks

The project now has connected Shopify tooling/Admin context in addition to the public website.

For a merchant-facing screen, ask:

1. What customer task does this native screen correspond to on the merchant side?
2. What does Gür Bakır call it?
3. Is the website behavior theme-local/browser-local, Customer Account-linked, Shopify-native, or app/plugin-backed?
4. What does the Android app actually support today?
5. Would matching web terminology create a truthful consistent concept, or falsely promise a capability such as sync?
6. Which mobile reference patterns help present the real capability better?

Use Shopify/Admin/plugin read-only where it can resolve the question. If permission/access is denied, record that limitation instead of inventing the answer.

Do not mutate merchant products, collections, theme/apps/settings, customers, orders, or other store data during UI research unless a separately authorized task truly requires it.

---

# 27. How to use external/mobile references correctly

For every important reference-driven decision, future work should be able to explain:

```text
What did we observe?
Why is that useful?
Does Gürbakır have the same customer task/data constraint?
What generic principle are we adapting?
What are we deliberately NOT copying?
How did the independent Gürbakır render perform on the Samsung?
```

If an agent cannot answer those questions, “we used the reference” is probably ceremonial rather than real.

When a live reference app is unavailable due to an offline/service gate, do not bypass it merely to satisfy a prompt. Use prepared/static evidence and state the limitation.

---

# 28. Lessons from specific mistakes in the new refinement work

The later work was better methodologically, but it still produced useful failures.

## 28.1 Home: brand/site research came too late

The model initially generated a generic editorial copper concept and invented discovery language before fully inspecting merchant truth.

**Lesson:** merchant understanding must precede brand-facing composition, not merely validate it afterward.

## 28.2 Saved: visual cleanup was reported complete despite a broken real flow

The retained-state problem was even mentioned in the report, but the screen was still framed as complete.

**Lesson:** if the screen's defining customer action requires recreation/relaunch to work, the task is not complete.

## 28.3 Saved: same name was incorrectly treated as a sync promise

The initial merchant-parity conclusion confused technical persistence boundaries with customer semantics.

**Lesson:** distinguish product concept, storage mechanism, and explicit customer promise.

## 28.4 Account: fixture coverage initially substituted for available real signed-in evidence

Deterministic fixtures were useful and safe, but the agent had an owner-authorized real session available and still finalized decisions without inspecting it.

**Lesson:** fixture coverage is not a substitute for available real product evidence when the task is product design.

## 28.5 Device automation: unrelated apps were opened

**Lesson:** personal physical devices require explicit package-targeted interaction discipline.

## 28.6 Usage limits can distort process

The Saved task was interrupted while files were being edited. Later Account verification also needed a shortened, priority-driven prompt because quota was nearly exhausted.

**Lesson:** when quota is low, prioritize a safe completed slice over broad reassurance work; record exact stopping state; do not leave ambiguous half-implementation if avoidable.

---

# 29. Recommended acceptance language going forward

Use precise status language. Examples:

### Good

- “Functionally verified on the Samsung.”
- “Owner-verified Saved persistence fix.”
- “Accepted cleanup/refinement.”
- “Real signed-in Account behavior inspected; no correction needed.”
- “Merchant alignment completed without sync claim.”
- “Whole-product polish remains unestablished.”

### Avoid

- “UI/UX complete” when only a bounded screen batch was checked.
- “Visually polished” based on tests.
- “Reference-driven” without concrete observation/decision evidence.
- “Shopify-backed” when only browser-local/theme/plugin behavior was seen.
- “Account deleted” when only local cleanup/request-resource behavior exists.
- “Synchronized list” when clients store locally and no sync is proven.

Honest acceptance language is part of product quality.

---

# 30. Suggested continuation protocol for a future AI session

A future session receiving this file should do the following before any new UI implementation:

1. Read this document plus the forensic audit/change ledger.
2. Inspect actual current `git status` and HEAD; compare against the recorded clean anchor `58223b2...`.
3. Confirm that the accepted UI commit `505b04f...` remains in ancestry and note any work added afterward.
4. Do not automatically revert/rewrite the accepted commits or any newer work.
5. Ask/resolve which next screen batch is authorized.
6. Inspect current source and real merchant counterpart for that task.
7. Use relevant prepared/static/live reference evidence—not every tool indiscriminately.
8. Keep the prompt/outcome lean and screen-specific.
9. Implement a bounded batch.
10. Inspect the real Samsung render/flow.
11. Use focused regression tests for changed behavior.
12. Report limitations honestly and stop for owner review.

If current source contradicts this document, preserve the evidence and explain the discrepancy instead of guessing which version is “supposed” to exist.

---

# Appendix A — Source documents that anchor this history

The strongest durable project sources include:

- `docs/product-quality/FORENSIC-UI-PRODUCT-QUALITY-AUDIT.md`
- `docs/product-quality/FORENSIC-UI-CHANGE-LEDGER.md`
- `docs/phase3/P3-00-CRITICAL-USER-FLOWS.md`
- `docs/phase3/P3-00-DATA-OWNERSHIP-AND-PERSISTENCE.md`
- `docs/phase3/P3-00-DESIGN-SYSTEM-AND-UX.md`
- `docs/phase3/P3-00-INFORMATION-ARCHITECTURE-AND-SCREEN-INVENTORY.md`
- `docs/phase3/P3-09-HANDOFF.md`
- `docs/phase3/P3-10-HANDOFF.md`
- `docs/phase3/P3-11-HANDOFF.md`
- `docs/phase3/P3-12-HANDOFF.md`
- `docs/phase3/P3-13-HANDOFF.md`
- `docs/phase3/P3-15-HANDOFF.md`
- `docs/phase3/PHASE-3-PRODUCT-DECISIONS.md`
- `docs/preparation/ARCHITECTURE-DIRECTION.md`
- `docs/phase2/CUSTOMER-ACCOUNT-OAUTH-PROOF.md`
- `docs/phase2/CONFIGURATION-AND-SECRETS.md`

The new root-screen refinement chronology after the forensic audit was additionally reconstructed from the owner/Codex workstream conversation and implementation/device reports. That work was subsequently audited and committed as `505b04f...`; documentation consolidation was committed separately as `58223b2...`. Future sessions should use those commits, current source, and reproducible device evidence rather than the removed raw conversation export.

---

# Appendix B — Old Product Quality forensic verdict, preserved verbatim in meaning

The forensic reconstruction's defensible high-level result was:

- correct Phase 3 source lineage;
- meaningful UI/UX/adaptive improvements;
- strong bounded engineering validation;
- no proof of a stale/reimplemented app;
- partial visual redesign;
- whole-product visual polish not established;
- several customer surfaces still generic/text-heavy/engineering-oriented;
- prior unqualified UI/UX acceptance stronger than the original owner goal justified;
- release readiness not established.

Future work should never erase this nuance.

---

# Appendix C — Current merchant/reference observations worth re-checking if time passes

These were useful during the 2026-08 UI workstream but are external and may change:

### Gür Bakır public merchant experience

- `Listem` is the merchant-facing wishlist/list terminology observed on the storefront.
- The list was reported to use browser `localStorage` key `gurbakir_listem_v1` at the time of inspection.
- Cross-channel/Customer Account synchronization was not proven.
- Public merchant communication emphasized concrete coppercraft/product/care characteristics rather than generic “premium” language.
- Public graphical logo/wordmark existed, but app asset provenance/approval was not established by this workstream.

### Reference APK

- Strong prepared evidence exists for commerce/account route breadth and storage/operation structure.
- Live/static use is allowed for generic product/design learning under clean-room rules.
- Do not assume a feature observed there maps directly to Shopify/Gürbakır backend semantics.

### Derimod

- Useful live benchmark for familiar mobile-commerce hierarchy, wishlist/favorites behavior, and signed-in Account task grouping.
- Not a design donor and not evidence of Gürbakır merchant requirements.

---

# Appendix D — Compact “current truth” for a future model

If a future model reads only one section, use this:

> Gürbakır is a functioning native Android Shopify app. Phase 3 implemented the commerce/account domain before the UI work. The old Product Quality work was on the correct source lineage and added valuable shell/navigation/state/adaptive/accessibility UI improvements, but its whole-product visual acceptance was too strong. A forensic audit closed that ambiguity. The later UI workstream therefore moved to small real-device batches and merchant/reference research. Home was cleaned up but explicitly did not achieve the hoped-for beautification. Shop and Find were accepted as orderly refinements. Saved exposed real stale Room/root-navigation bugs; those were fixed and personally verified by the owner, then merchant terminology was aligned to `Listem`/`My list` without inventing web/app synchronization. Account was researched before redesign because of Shopify hosted passwordless OAuth/session/privacy contracts; its root was made customer-task-first, then checked against a real restored Gürbakır Customer Account session and signed-in Derimod, with no further correction needed. The accepted root-screen work is committed at `505b04f...`; documentation consolidation is committed at `58223b2...`; the final reported worktree is clean. `docs/product-quality` intentionally contains only this master handoff plus the forensic audit and change ledger. Do not reopen old PQ checkpoints, do not claim whole-app polish, do not invent merchant/backend behavior, and continue one explicitly authorized screen batch at a time with the physical Samsung as the visual judge.

---

# Appendix E — Final project-management interpretation

The UI story should not be summarized as either “the first UI attempt failed” or “the UI is now finished.” Both are inaccurate.

A more useful interpretation is:

1. **Phase 3 succeeded at building the product functionality.**
2. **Old Product Quality succeeded at strengthening UI architecture, navigation, states, adaptive behavior, and several important screens.**
3. **Old Product Quality failed to prove the owner's broader visual-transformation goal because process/evidence closure overtook product judgment.**
4. **Forensic reconstruction correctly separated those truths instead of discarding the useful work.**
5. **The new workstream improved methodology:** real merchant truth, real references, smaller batches, physical-device judgment, leaner prompts, honest acceptance language.
6. **The new workstream still demonstrated the limits of AI visual design:** Home remained only an orderly cleanup rather than the desired art-direction breakthrough.
7. **The strongest later successes were product/flow correctness rather than decoration:** root navigation correctness, Saved real-time/persistence behavior, merchant-aligned `Listem`, and Account hierarchy grounded in real Shopify Customer Account constraints.
8. **The project should continue from evidence and owner-observed problems, not from a desire to make every screen “look changed.”**

That is the state future work should inherit.
