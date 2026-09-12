# Gate 0 Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the durable, repository-local multi-brand architecture source of truth and evidence hierarchy without changing production/runtime behavior.

**Architecture:** Gate 0 is documentation-only. It records the accepted architecture—one monorepo, one Android application module per brand, existing `:app` retained as the Gürbakır application shell during migration, and future shared `:mobile-core`—while preserving the current Gürbakır-first implementation and all historical Phase documentation. Canonical current architecture, ADR rationale, research evidence, implementation plans, current-source facts, and historical records must be clearly distinguishable.

**Tech Stack:** Markdown, Git, repository-local documentation conventions, PowerShell validation, current GitHub source evidence.

**Spec:** Accepted `MULTI-BRAND WHITE-LABEL NORMATIVE ARCHITECTURE DESIGN`. The design evidence is materialized at `docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md` and this implementation plan is preserved at `docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md`.

## Global Constraints

- Gate 0 changes documentation only.
- Do not modify Kotlin, Java, XML, Gradle, GraphQL, scripts, resources, schemas, CI workflows, configuration files, or external systems.
- Do not create `:mobile-core` or any additional application module in Gate 0.
- Do not change current application IDs, namespaces, Firebase configuration, OAuth configuration, App Links, Room schemas, persistence names, Keystore aliases, runtime feature behavior, navigation, or dependency composition.
- Gürbakır is the first real implementation/validation brand; it is not the reusable architecture.
- `main` is intended to become the canonical multi-brand source of truth.
- Brands are not long-lived Git branches and are not repository forks.
- The normative target is one monorepo with one Android application module per brand and shared `:mobile-core`.
- Existing `:app` intentionally remains the Gürbakır application shell during migration.
- Shared modules must never depend on concrete brand application modules.
- Brand application modules own application/distribution identity and brand-specific composition.
- Shopify may own bounded merchant/catalog/merchandising content; Android retains native behavior, security policy, finite renderers, application identity, and executable code.
- Migration-sensitive Gürbakır identifiers must not be renamed merely to make names aesthetically generic.
- Historical Phase 1/2/3 and Product Quality documents remain historical evidence and must not be rewritten to suggest that the repository was always multi-brand.
- Current source remains authoritative for **implemented behavior**. `docs/architecture/*` defines the **accepted target architecture** after Gate 0.
- Production application ID, production signing identity, production Firebase, production OAuth callback, and verified production App Links remain unresolved P3-16 inputs. Do not invent them.
- Do not put Storefront tokens, OAuth/customer tokens, Admin credentials, Firebase service credentials, signing material, private keys, or other secrets into documentation.
- Stop after Gate 0 documentation validation. Do not begin Gate 1 source/module extraction.

---

## Planning Evidence Snapshot

This plan was prepared against:

```text
Repository: private historical repository
Branch: main
HEAD: 75f4b57054e9ddd5dd1dcc209a293407ce5086d2
Date: 2026-09-01
```

The executor must not blindly require that SHA if `main` has legitimately advanced. At execution time:

1. Fetch current `origin/main`.
2. Record its actual SHA.
3. If it differs from `75f4b57054e9ddd5dd1dcc209a293407ce5086d2`, reread every mutable source in the Evidence Reverification Matrix.
4. Use newer current-source facts where they differ.
5. Do not silently carry stale identifiers into new canonical documents.

### Evidence Reverification Matrix

| Concern | Current source/evidence to reread before editing |
|---|---|
| Repository modules | `settings.gradle.kts` |
| Current build/application IDs/Firebase package expectations | `app/build.gradle.kts` |
| Android App Links/application identity | `app/src/main/AndroidManifest.xml` |
| Current documentation authority | `AGENTS.md`, `docs/README.md`, `README.md` |
| Existing ADR conventions | `docs/decisions/ADR-0001-NATIVE-ANDROID-KOTLIN-COMPOSE.md`, ADR-0002, ADR-0003 |
| Historical source/provenance rules | `docs/preparation/SOURCE-OF-TRUTH-AND-PROVENANCE.md` |
| Current release/production unknowns | `docs/phase3/P3-16-HANDOFF.md` |
| Room physical database | `app/src/main/kotlin/com/gurbakir/mobile/di/SearchModule.kt` |
| Current Room schema version | `app/src/main/kotlin/com/gurbakir/mobile/search/LocalCommerceDatabase.kt` |
| Room exported schemas | `app/schemas/com.gurbakir.mobile.search.LocalCommerceDatabase/1.json`, `2.json` |
| Explicit Room migration | `app/src/main/kotlin/com/gurbakir/mobile/wishlist/WishlistRepository.kt` |
| Customer Account secure persistence | `account/src/main/kotlin/com/gurbakir/account/session/AndroidKeystoreCustomerSessionStore.kt` |
| Cart secure persistence | `storefront/src/main/kotlin/com/gurbakir/storefront/AndroidKeystoreCartSessionStore.kt` |
| OAuth callback construction | `scripts/Provision-CustomerAccountDiscovery.ps1`, `app/build.gradle.kts` |
| Existing multi-brand historical statements | `AGENTS.md`, `docs/preparation/ARCHITECTURE-DIRECTION.md`, `docs/product-quality/UI-REFINEMENT-WORKSTREAM-HISTORY-AND-HANDOFF.md` |
| Earlier multi-brand research | `docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md` |
| Accepted architecture basis | `docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md` |

---

# Target Gate 0 File Structure

Gate 0 should result in:

```text
docs/
├── decisions/
│   └── ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md
│
├── architecture/
│   ├── MULTI-BRAND-ARCHITECTURE.md
│   ├── BRAND-BOUNDARIES.md
│   ├── GURBAKIR-LEGACY-IDENTITIES.md
│   └── BRAND-ONBOARDING.md
│
└── multi-brand/
    ├── README.md
    ├── research/
    │   ├── MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md
    │   └── MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md
    └── plans/
        └── GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md
```

Also modify:

```text
AGENTS.md
docs/README.md
README.md
```

No other path is permitted to change in Gate 0.

`README.md` requires only a small Gate 0 clarification: it is a primary repository entry point and currently describes the Gürbakır application. It must accurately say that current source remains Gürbakır-first while an accepted multi-brand target architecture now governs future migration. Its current module list must remain a description of **existing** modules; it must not pretend `:mobile-core` already exists.

---

# Documentation Authority Model to Establish

## Current implemented facts

Use current source/configuration first. A target architecture document must never be cited as proof that `:mobile-core` already exists before Gate 1 creates it.

## Canonical target architecture

Use:

```text
docs/architecture/*
```

These define how the repository is intended to evolve and the invariants future implementation must preserve.

## Architectural decision history

Use:

```text
docs/decisions/*
```

ADR-0004 explains why the multi-brand architecture was selected. ADR-0001 through ADR-0003 remain accepted for their respective scopes.

## Research/evidence

Use:

```text
docs/multi-brand/research/*
```

These preserve investigation/rationale. They are evidence, not the concise operating specification.

## Migration plans

Use:

```text
docs/multi-brand/plans/*
```

Plans tell executors how to perform an approved gate. A plan does not override canonical architecture.

## Historical product/implementation records

Existing `docs/preparation/`, `docs/phase2/`, `docs/phase3/`, `docs/product-quality/`, and `docs/testing/` retained their scoped meanings at this checkpoint. The private forensic corpus was later consolidated for the public repository without backporting newer decisions into this historical plan.

---

# Task 0: Reverify Execution Baseline and Protect Existing Work

**Files:** read only; create/modify none.

- [ ] Fetch current `main` and record execution base:

```bash
git fetch origin main
git rev-parse origin/main
git branch --show-current
git rev-parse HEAD
git status --short
```

- [ ] Verify clean execution boundary:

```bash
git status --porcelain=v1 --untracked-files=normal
```

If unrelated user work exists, do not reset/stash/clean/overwrite it; use an isolated worktree/execution method.

- [ ] Reread every mutable source in the Evidence Reverification Matrix and confirm current modules, app IDs, namespace, Firebase package expectations, manifest App Links, Room filename/version/schema chain, secure persistence names, OAuth callback pattern, P3-16 unknowns and docs authority.

- [ ] If `origin/main` differs from planning SHA, record material Gate 0 differences and use newer source facts.

- [ ] Do not edit prohibited paths such as Gradle/source/config/scripts/workflows/resources/schemas.

**Expected outcome:** Gate 0 starts from current evidence without destroying user work.

---

# Task 1: Confirm the Repository-Local Evidence and Plan Corpus

The bootstrap branch already contains:

```text
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md
docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md
```

These were materialized before Codex execution so no chat attachments are required.

- [ ] Read all three completely.
- [ ] Confirm each research file clearly states its evidence/authority role and that the forensic brand-flavor recommendation remains visibly historical/superseded rather than silently rewritten.
- [ ] Confirm no transient ChatGPT citation syntax remains:

```bash
git grep -n -E '(filecite|cite|url)|utm_source=chatgpt\.com' -- docs/multi-brand || true
```

Expected: no output.

- [ ] Do not rewrite the evidence corpus merely for style. Correct only factual/current-reference issues discovered by Task 0 or broken repository-relative references required for Gate 0.

**Expected outcome:** future agents can recover the research, accepted design reasoning and Gate 0 execution plan from Git alone.

---

# Task 2: Record ADR-0004 as the Accepted Multi-Brand Decision

**Create:** `docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md`

Read ADR-0001 through ADR-0003 for repository style.

Required opening shape:

```markdown
# ADR-0004: Multi-Brand Android Application-Module Architecture

- Status: Accepted
- Date: 2026-09-01
- Decision owners: Project owner and multi-brand architecture reassessment
- Related decisions: ADR-0001, ADR-0002, ADR-0003
```

The ADR must include:

## Context

- Gürbakır first real implementation/validation brand;
- current source therefore legitimately Gürbakır-first in several areas;
- historical condition is provenance, not reusable architecture;
- future brands may differ materially in UI/navigation/services/capabilities/market/Firebase/Customer Account/integrations;
- repository forks, long-lived brand branches, shared `if (brand)` logic and arbitrary remote native UI are unacceptable;
- current `:app` mixes Android application assembly with reusable features.

## Decision

State unambiguously:

```text
one monorepo
one Android application module per brand
shared :mobile-core
existing :app remains Gürbakır application shell during migration
environment flavors stay inside each real brand app
no brand flavor dimension as normative brand boundary
no runtime merchant switching
shared code never depends on concrete brand applications
```

Gate 0 does not create `:mobile-core`.

## Alternative: one app + brand flavor

Record it as technically valid but not selected because application package/signing/Firebase/OAuth/App Links/resources/provider dependencies align naturally with an application module; global brand × environment × build-type growth is less attractive; source sets are not arbitrary Kotlin override/plugin mechanisms; module dependency direction gives stronger isolation.

Do not describe Android product flavors as broken.

## Rejected alternatives

Explicitly reject:

```text
separate repository per brand
long-lived per-brand branches
runtime multi-merchant APK
```

with repository-specific reasons.

## `:mobile-core` rationale

Current `:app` owns both Android application identity/assembly and reusable product UI/orchestration. `:mobile-core` separates those responsibilities; it is not modularization for aesthetics.

## Identity/persistence preservation

State:

- current Gürbakır installed/external/persistence identities are migration-sensitive;
- Room filename/schema chain continues;
- Customer Account/cart secure names/aliases remain migration-sensitive;
- `com.gurbakir.*` internal naming is not automatically a migration target;
- production identity/signing remains unresolved/external.

Link `../architecture/GURBAKIR-LEGACY-IDENTITIES.md`.

## Consequences/trade-offs

Benefits: strong brand isolation; coherent Android identity ownership; clearer Firebase/OAuth/App Link/resource boundaries; future compiled brand behavior owner; machine-enforceable dependency direction.

Costs: larger first migration than a flavor; `:app` must shed reusable implementation; common app Gradle logic may later justify convention build logic; some `BuildConfig` dependencies need explicit inputs.

## Reversibility/deferred

Architecture remains reversible before multiple production brands depend on it; returning to a flavor model is possible only if later evidence proves the selected boundary operationally worse.

Defer production Gürbakır package/signing, first real second brand, future app-ID convention, final mobile content schema, loyalty/custom-backend providers, physical exclusion of Account and build convention plugins.

## Evidence links

Link:

```text
../multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md
../multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md
../architecture/MULTI-BRAND-ARCHITECTURE.md
../architecture/BRAND-BOUNDARIES.md
../architecture/GURBAKIR-LEGACY-IDENTITIES.md
```

Review diff and use a dedicated documentation commit if useful.

---

# Task 3: Establish Canonical Multi-Brand Architecture and Brand Boundaries

**Create:**

```text
docs/architecture/MULTI-BRAND-ARCHITECTURE.md
docs/architecture/BRAND-BOUNDARIES.md
```

## `MULTI-BRAND-ARCHITECTURE.md`

Opening must make status explicit:

```markdown
# Multi-Brand Architecture

Status: **Canonical target architecture**

This document defines the repository's accepted multi-brand target architecture.
Current source remains authoritative for what is implemented today.
```

Immediately state Gate 0 is docs-only and `:mobile-core`/additional brand apps do not exist yet.

Record historical context: Gürbakır first validation brand; current repo has Gürbakır history; `main` is canonical shared development line; brands are not Git branches.

Include target graph:

```text
:app (Gürbakır shell)
   └──> :mobile-core
           ├──> :foundation
           ├──> :storefront
           ├──> :account
           └──> :checkout

:apps:<future-brand>
   └──> :mobile-core

brand app -> provider adapter such as :firebase when required
shared modules -X-> concrete brand app modules
```

Record build selection: application module selects brand; environment may remain product flavor inside brand app; build type remains Android concern; no runtime merchant switch; no normative brand flavor.

Ownership table must cover applicationId/signing/name/icons/splash/fonts/manifest/App Links/OAuth/Firebase/Storefront config/common Compose/navigation/design tokens/commerce-security/Shopify content/native renderers/legacy persistence.

Dependency invariants:

```text
Shared modules MUST NOT depend on :app or any :apps:<brand>.
Brand applications MAY depend on shared modules.
Shared code MUST NOT branch on concrete brand names.
Brand-specific compiled behavior stays on brand side of a narrow shared contract when a real requirement justifies it.
```

Define variation classes: tokens, packaged resources, Shopify merchant content, finite variants, capability/topology choices, compiled brand-specific UI/behavior, shared commerce/security.

Define Shopify/native boundary and explicitly prohibit arbitrary remote native classes/routes/components/executable behavior.

Persistence principle: separate application IDs already provide app-data isolation; do not add brandId everywhere; Gürbakır persistence identities remain exact unless a separately designed migration proves change safe.

Only forward-reference Gate 1; do not include a Gate 1 task plan.

## `BRAND-BOUNDARIES.md`

Define six ownership categories:

1. brand Android application module;
2. shared `:mobile-core`;
3. shared integration/domain module;
4. Shopify merchant content;
5. external service/configuration boundary;
6. migration-sensitive legacy identity.

Use current examples: app IDs/icons/App Links/Customer Account callback/Home/Catalog merchant hierarchy/Storefront gateway/Account/Checkout/Firebase/Room identity.

Anti-contamination rules:

```text
no shared if-brand switches
no shared module -> concrete app dependency
no merchant domain literals in shared runtime except approved fixtures/configuration
no production Product/Collection handles in shared merchant configuration
no arbitrary remotely supplied native class/route/component identifiers
```

Do not use generic word blacklists as the primary architecture enforcement.

Add future decision test:

```text
Is this Android distribution identity?
Is it reusable native product behavior?
Is it merchant-editable Shopify content?
Is it an external-provider boundary?
Is it migration-sensitive existing state?
Is it genuinely brand-specific compiled behavior?
```

---

# Task 4: Create the Gürbakır Legacy Identity Register

**Create:** `docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md`

Opening:

```markdown
# Gürbakır Legacy Identities

Status: **Migration-safety register**

This file records existing Gürbakır identifiers that later multi-brand work must preserve, migrate explicitly, or keep classified as unresolved. It contains no credentials or private signing material.
```

Reverify every value from current source at execution time.

Unless current source changed, record:

## Current non-production application IDs

```text
developmentDebug   = com.gurbakir.mobile.dev.debug
developmentRelease = com.gurbakir.mobile.dev
stagingDebug       = com.gurbakir.mobile.staging.debug
stagingRelease     = com.gurbakir.mobile.staging
```

Classification: preserve exact for existing Gürbakır non-production identities unless explicit external/application migration is separately approved.

Also record fail-closed default:

```text
com.gurbakir.mobile.unconfigured
```

as build safety/default, not production identity.

## Internal namespace/package naming

Unless source changes:

```text
app        = com.gurbakir.mobile
foundation = com.gurbakir.foundation
storefront = com.gurbakir.storefront
account    = com.gurbakir.account
checkout   = com.gurbakir.checkout
firebase   = com.gurbakir.firebase
root project = gurbakir-android
```

Classify as historical internal naming: do not rename merely for aesthetics.

## Room

Unless source changes:

```text
database filename = gurbakir-local.db
database class = com.gurbakir.mobile.search.LocalCommerceDatabase
schema version = 2
exported schemas = 1.json, 2.json
migration = WISHLIST_MIGRATION_1_2 / Migration(1, 2)
```

`gurbakir-local.db` and migration/schema continuity must remain exact for existing Gürbakır installs.

## Customer Account secure persistence

Unless source changes:

```text
SharedPreferences:
gurbakir_secure_customer_session_<normalizedEnvironmentId>

Keystore:
gurbakir.customer.session.<normalizedEnvironmentId>.v1
```

Preserve exact unless explicit secure-storage migration is designed and proven.

## Cart secure persistence

```text
SharedPreferences:
gurbakir_secure_cart_<normalizedEnvironmentId>

Keystore:
gurbakir.cart.<normalizedEnvironmentId>.v1
```

Same migration rule.

## Firebase package registration

Record exact current package contract but no JSON contents/project credentials:

```text
com.gurbakir.mobile.dev.debug
com.gurbakir.mobile.dev
com.gurbakir.mobile.staging.debug
com.gurbakir.mobile.staging
```

Current validation expects one exact client per package when configured; development debug/release share one dev project; staging debug/release share one staging project; development/staging projects remain distinct under current validation.

## OAuth callback

Tracked construction:

```text
shop.<shopId>.gurbakir://oauth/callback
```

Tracked fail-closed scheme:

```text
shop.unconfigured.gurbakir
```

Do not invent a concrete shop ID. Concrete registration is ignored/external and migration-sensitive.

## App Links

Unless manifest changed:

```text
https://gurbakir.com/collections/
https://gurbakir.com/apps/mobile/products/
https://gurbakir.com/apps/mobile/orders/
```

Record current `android:autoVerify="false"`; do not claim verified production App Links.

## Production/release identity

Record explicitly as unresolved/not represented/external:

```text
production applicationId
production signing
production Firebase
production Customer Account callback
verified production App Links
Play ownership/listing
```

Reference `docs/phase3/P3-16-HANDOFF.md` with correct relative path from the register.

Use a provenance table for concrete identifiers and manually verify no secrets/tokens/private signing data are included.

---

# Task 5: Define Future Brand Onboarding Contract

**Create:** `docs/architecture/BRAND-ONBOARDING.md`

Opening must say it describes the accepted model **after** required migration gates; `:mobile-core` and additional brand apps do not exist at Gate 0 completion.

Every future brand:

- stays in same monorepo;
- gets its own Android application module;
- does not get permanent branch/repo fork;
- selects brand by building app module;
- owns distribution identity;
- depends inward on shared modules;
- is never imported by shared modules.

Future onboarding record should resolve, without secrets:

```text
applicationId(s)
environments
app display name
icons/splash/fonts/assets
Storefront config
Customer Account enabled/disabled and callback registration
App Links/domain ownership
Firebase enabled/disabled and registrations
market/locale policy
capability set
primary navigation
legal/support destinations
approved media/domain policies
brand-specific compiled integration requirements
release/signing ownership when production is entered
```

Packaged app identity resources belong to application module.

Merchant catalog/navigation/merchandising should use Shopify-owned bounded structures when contracts exist. Do not hardcode production handles into shared Kotlin and do not define final metaobject schema here.

Separate application IDs provide application-data isolation. A new brand must not trigger a Gürbakır persistence migration.

Future conformance expectation: materially different identity/resources/font/market/navigation/capabilities/Account/Wishlist/Firebase/content/domain, implemented in a later approved gate.

Prohibit permanent branch, repo fork, runtime store switch, shared if-brand logic, accidental copying of Gürbakır persistence/Firebase/OAuth/App Link identities, committing secrets and unbounded remote UI/code.

Cross-link canonical docs and ADR-0004.

---

# Task 6: Establish Repository Documentation Authority and Entry Points

**Create:** `docs/multi-brand/README.md`

**Modify:**

```text
AGENTS.md
docs/README.md
README.md
```

## `docs/multi-brand/README.md`

Navigation/authority document, not duplicate architecture. Include Purpose, Authority model, Canonical architecture, Decision history, Research/evidence, Implementation plans, Historical-state relationship and Current migration status.

Authority:

```text
implemented behavior -> current source/config
accepted target -> docs/architecture/*
why -> ADR-0004
investigation/history -> docs/multi-brand/research/*
gate execution -> docs/multi-brand/plans/*
historical product checkpoints -> existing scoped Phase/preparation/product-quality records
```

Current status after Gate 0 must mean:

```text
architecture accepted
canonical docs established
runtime/module migration not started
current source still Gürbakır-first
```

## `AGENTS.md`

Preserve all existing native Android/security/provenance/approval rules and add durable multi-brand rules:

- Gürbakır first validation brand, not architecture;
- accepted decision points to ADR-0004 and canonical architecture;
- target one monorepo + one app module per brand + shared `:mobile-core`;
- current `:app` remains Gürbakır shell during migration;
- brands are not long-lived branches;
- shared modules never depend on concrete brand applications;
- migration-sensitive Gürbakır identities are not renamed for aesthetics;
- current source = implemented fact; `docs/architecture` = accepted target; historical records retain checkpoint authority; research does not override canonical architecture.

## `docs/README.md`

Update status date to Gate 0 execution date. State architecture accepted, Gate 0 docs-only, source still Gürbakır-first, P3-16 unchanged.

Make reading order domain-aware and add documentation-area rows for `docs/architecture`, `docs/multi-brand`, `docs/decisions`.

Explicitly state new architecture does not retroactively invalidate historically accurate Gürbakır-first Phase documents.

## root `README.md`

Minimal transition clarification near current introduction/status:

```text
Current implementation is Gürbakır-first because Gürbakır was the first real validation brand. The accepted target architecture is a multi-brand monorepo with one Android application module per brand and shared :mobile-core. Gate 0 establishes that architecture in documentation; runtime/module migration has not yet occurred.
```

Link canonical architecture, ADR-0004 and docs authority map.

Keep current module inventory factual: only currently existing `app`, `foundation`, `storefront`, `account`, `checkout`, `firebase`. Do not list `mobile-core` as implemented. Keep existing bootstrap Gradle commands unchanged.

---

# Task 7: Validate Gate 0 as Documentation-Only

Allowed final changed paths:

```text
AGENTS.md
README.md
docs/README.md
docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md
docs/architecture/MULTI-BRAND-ARCHITECTURE.md
docs/architecture/BRAND-BOUNDARIES.md
docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md
docs/architecture/BRAND-ONBOARDING.md
docs/multi-brand/README.md
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md
docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md
docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md
```

The two research files and plan already exist on the Gate 0 bootstrap branch before Codex execution; they are part of the same final Gate 0 allowlist.

## Changed-path validation

Against recorded Gate 0 base SHA:

```bash
git diff --name-status <GATE0_BASE_SHA>..HEAD
```

Expected: only allowed documentation paths.

Also:

```bash
git diff --name-only <GATE0_BASE_SHA>..HEAD -- \
  '*.kt' '*.kts' '*.java' '*.xml' '*.graphql' '*.graphqls' \
  '.github/**' 'scripts/**' 'config/**' 'gradle/**'
```

Expected: no output.

## Local Markdown link validation

From repo root, run:

```powershell
$files = @(
  'AGENTS.md',
  'README.md',
  'docs/README.md',
  'docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md',
  'docs/architecture/MULTI-BRAND-ARCHITECTURE.md',
  'docs/architecture/BRAND-BOUNDARIES.md',
  'docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md',
  'docs/architecture/BRAND-ONBOARDING.md',
  'docs/multi-brand/README.md',
  'docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-FORENSIC-RESEARCH.md',
  'docs/multi-brand/research/MULTI-BRAND-WHITE-LABEL-NORMATIVE-DESIGN-EVIDENCE.md',
  'docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md'
)

$errors = @()
foreach ($file in $files) {
    $directory = Split-Path -Parent $file
    if ([string]::IsNullOrWhiteSpace($directory)) { $directory = '.' }
    $content = Get-Content -LiteralPath $file -Raw
    $matches = [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')
    foreach ($match in $matches) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(https?://|mailto:|#)') { continue }
        $pathOnly = ($target -split '#', 2)[0]
        if ([string]::IsNullOrWhiteSpace($pathOnly)) { continue }
        $resolved = Join-Path $directory $pathOnly
        if (-not (Test-Path -LiteralPath $resolved)) { $errors += "$file -> $target" }
    }
}
if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host $_ }
    throw 'Broken local Markdown links detected.'
}
Write-Host 'PASS: all Gate 0 local Markdown file links resolve.'
```

Expected PASS.

## Canonical-reference checks

```bash
git grep -n "ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md" -- \
  AGENTS.md README.md docs/README.md docs/architecture docs/multi-brand

git grep -n "MULTI-BRAND-ARCHITECTURE.md" -- \
  AGENTS.md README.md docs/README.md docs/architecture docs/multi-brand
```

## Invariant checks

```bash
git grep -n -E "Gürbakır.*first|first.*Gürbakır" -- \
  AGENTS.md docs/architecture docs/multi-brand README.md

git grep -n -E "long-lived.*branch|Git branch" -- \
  AGENTS.md docs/architecture docs/multi-brand

git grep -n "mobile-core" -- \
  AGENTS.md README.md docs/architecture docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md

git grep -n -E "must not depend|MUST NOT depend" -- \
  docs/architecture docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md
```

## Historical-doc preservation

```bash
git diff --name-only <GATE0_BASE_SHA>..HEAD -- \
  docs/preparation \
  docs/phase2 \
  docs/phase3 \
  docs/product-quality \
  the private forensic corpus \
  docs/testing
```

Expected: no output.

## Legacy-source cross-checks

Use `git grep`/direct source reads to cross-check:

- current `com.gurbakir.mobile.dev/staging` IDs and unconfigured default against `app/build.gradle.kts`;
- `gurbakir-local.db` against `SearchModule.kt`;
- Customer Account preference/alias patterns against `AndroidKeystoreCustomerSessionStore.kt`;
- cart preference/alias patterns against `AndroidKeystoreCartSessionStore.kt`;
- callback pattern against `Provision-CustomerAccountDiscovery.ps1`;
- `gurbakir.com` routes against manifest.

If shell interpolation makes `$shopId` grep awkward, read the exact source line rather than inventing a value.

## Placeholder scan

```bash
git grep -n -E '\b(T[B]D|T[O]DO|F[I]XME|PLACEHOLD[E]R)\b' -- \
  AGENTS.md README.md docs/README.md \
  docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md \
  docs/architecture docs/multi-brand/README.md \
  docs/multi-brand/plans/GATE-0-DOCUMENTATION-IMPLEMENTATION-PLAN.md \
  || true
```

Expected no output. Explicit evidence states such as `UNRESOLVED / EXTERNAL`, `NOT REPRESENTED`, and `DEFERRED / YAGNI` are valid.

## Transient chat syntax scan

```bash
git grep -n -E '(filecite|cite|url)|utm_source=chatgpt\.com' -- \
  docs/architecture docs/multi-brand \
  docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md \
  || true
```

Expected no output.

## Repository portability

After intended Gate 0 commits and clean worktree:

```powershell
pwsh ./scripts/Test-RepositoryPortability.ps1 -RequireCleanWorktree
```

Expected repository portability validation PASS.

Do not run Android builds merely for ceremony when final diff is documentation-only. If CI automatically runs them after push, allow normal CI but do not make them Gate 0 functional proof.

## Final diff/status

```bash
git diff --stat <GATE0_BASE_SHA>..HEAD
git diff --name-status <GATE0_BASE_SHA>..HEAD
git log --oneline <GATE0_BASE_SHA>..HEAD
git status --short
```

Expected only approved docs and clean worktree after commits.

## No-history semantic review

Read in order:

```text
AGENTS.md
docs/README.md
docs/architecture/MULTI-BRAND-ARCHITECTURE.md
docs/decisions/ADR-0004-MULTI-BRAND-APPLICATION-MODULE-ARCHITECTURE.md
docs/architecture/BRAND-BOUNDARIES.md
docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md
docs/architecture/BRAND-ONBOARDING.md
docs/multi-brand/README.md
```

From repository alone, answers must be unambiguous:

```text
Why is current source Gürbakır-specific?
Is Gürbakır the architecture?
What does main represent going forward?
Are brands Git branches?
What selects a brand?
Does :mobile-core exist yet?
Who owns application identity?
Can shared code depend on :app?
What belongs in Shopify?
Which Gürbakır names must not be casually renamed?
Are historical Phase documents still valid?
Where is research?
Where are gate plans?
What production identities remain unresolved?
```

---

# Recommended Commit Boundaries

For the Codex execution portion of Gate 0, independently reviewable commits may be:

```text
1. docs(architecture): record multi-brand application-module decision
2. docs(architecture): define multi-brand ownership boundaries
3. docs(architecture): register gurbakir legacy identities
4. docs(architecture): define brand onboarding contract
5. docs: establish multi-brand architecture authority
```

The bootstrap branch already has repository-local research/design/plan commits. Do not rewrite or squash them merely for convenience unless the owner explicitly chooses a different branch-history strategy.

No Gate 0 commit may contain runtime/source changes.

---

# Exact Legacy Facts at Planning Time

Reverify at execution time before copying into canonical register.

## Android application IDs

```text
developmentDebug   = com.gurbakir.mobile.dev.debug
developmentRelease = com.gurbakir.mobile.dev
stagingDebug       = com.gurbakir.mobile.staging.debug
stagingRelease     = com.gurbakir.mobile.staging

fail-closed defaultConfig applicationId = com.gurbakir.mobile.unconfigured
```

## Namespaces/root

```text
app        = com.gurbakir.mobile
foundation = com.gurbakir.foundation
storefront = com.gurbakir.storefront
account    = com.gurbakir.account
checkout   = com.gurbakir.checkout
firebase   = com.gurbakir.firebase
root project = gurbakir-android
```

## Room

```text
gurbakir-local.db
LocalCommerceDatabase
schema version 2
exported 1.json and 2.json
WISHLIST_MIGRATION_1_2 / Migration(1,2)
```

## Customer Account secure persistence

```text
gurbakir_secure_customer_session_<normalizedEnvironmentId>
gurbakir.customer.session.<normalizedEnvironmentId>.v1
```

## Cart secure persistence

```text
gurbakir_secure_cart_<normalizedEnvironmentId>
gurbakir.cart.<normalizedEnvironmentId>.v1
```

## Firebase packages

```text
com.gurbakir.mobile.dev.debug
com.gurbakir.mobile.dev
com.gurbakir.mobile.staging.debug
com.gurbakir.mobile.staging
```

Do not record ignored JSON contents/project credentials.

## Customer Account callback

```text
shop.<shopId>.gurbakir://oauth/callback
shop.unconfigured.gurbakir   # fail-closed build scheme
```

Concrete shop ID is not represented in tracked main and must not be invented.

## Current App Link routes

```text
https://gurbakir.com/collections/
https://gurbakir.com/apps/mobile/products/
https://gurbakir.com/apps/mobile/orders/
```

Current manifest uses `android:autoVerify="false"`; do not imply verified production association.

## Production/release

```text
production applicationId: UNRESOLVED / NOT REPRESENTED
production signing: UNRESOLVED / NOT REPRESENTED
production Firebase: UNRESOLVED / EXTERNAL
production Customer Account callback: UNRESOLVED / EXTERNAL
verified production App Links: UNRESOLVED / EXTERNAL
Play ownership/listing: UNRESOLVED / EXTERNAL
```

P3-16 remains governing release-entry boundary.

---

# Gate 0 Completion Criteria

Gate 0 is complete only when:

- ADR-0004 exists and is Accepted;
- ADR records context, alternatives, selected app-module architecture, rejection of brand flavor as normative boundary, rejection of forks/long-lived branches, `:mobile-core` rationale, dependency direction, identity preservation, consequences, reversibility and deferred decisions;
- canonical `MULTI-BRAND-ARCHITECTURE.md` exists;
- `BRAND-BOUNDARIES.md` exists;
- `GURBAKIR-LEGACY-IDENTITIES.md` accurately records migration-sensitive facts without secrets/invented production values;
- `BRAND-ONBOARDING.md` defines future ownership while stating target modules are not yet implemented;
- `docs/multi-brand/README.md` distinguishes canonical docs/ADRs/research/plans/history;
- research and accepted design evidence remain under `docs/multi-brand/research/`;
- this plan remains under `docs/multi-brand/plans/`;
- `AGENTS.md` contains durable multi-brand invariants;
- `docs/README.md` exposes authority hierarchy;
- root `README.md` distinguishes current Gürbakır-first source from target architecture and does not claim `:mobile-core` already exists;
- historical Phase/Product Quality/testing/reference docs are unchanged;
- no Kotlin/Gradle/XML/GraphQL/config/script/CI/resource/schema file changed;
- no external system mutated;
- local Markdown links resolve;
- transient ChatGPT citation syntax absent;
- canonical docs have no unfinished placeholders;
- repository portability validation passes;
- diff contains only explicitly allowed documentation paths;
- final worktree clean after intended commits;
- Gate 1 has not begun.

---

# Handoff to Gate 0 Executor

1. Reverify current `main`, `origin/main`, branch and worktree first; protect existing user work.
2. Read the forensic evidence, normative design evidence and this Gate 0 plan from the repository before editing.
3. Execute only Gate 0 canonical documentation/authority tasks.
4. Make no runtime/source behavior changes.
5. Validate all documentation references, changed-path allowlist, source cross-checks, historical-doc preservation and portability checks described above.
6. Report exact execution-base SHA, files changed, commits, validations and final HEAD/status; distinguish observed checks from assumptions.
7. Stop before Gate 1. Do not create `:mobile-core`, synthetic brand, capability changes, Shopify content migration, Firebase refactor or persistence migrations.
