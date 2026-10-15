# TumiArt Stage 4 P0/P1 development scaffold

Date: 2026-10-15 (Europe/Istanbul). Scope: the owner-authorized first Stage 4
implementation slice, development only. No provider writes, production signing,
publication, customer mutation, orders, payments or self-merge are authorized here.

## P0 baseline and authority

- Base: `ed3926ca0e9197525d14d94635969d6742855df7`, freshly fetched `origin/main`.
  PR #46's custom Account-origin correction is merged. The older Stage 3
  blueprint's pending-integration wording is historical and superseded by this
  observation. [Merged-main run 137](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/38560472363)
  passed validate, instrumentation, minimum-sdk-instrumentation and native-16k.
- Primary checkout remains clean on local main `d6d61381`. Existing SDK24,
  remediation and Stage 3 worktrees are preserved. This change uses its own
  managed worktree and branch `codex/tumiart-stage4-p0-p1`.
- Current AGENTS, owner boundaries, documentation map, Brand Onboarding,
  Brand Boundaries, ADR-0004 and current source govern this slice.
- The owner's `CODEX_TUMIART_STAGE4_P0_P1_FIRST_TASK_2026-10-15.md` is the
  implementation specification. The dated design decision and retained final
  visual handoff are static design references for P2–P5. The separately named
  Stage 4 implementation plan and design handoff were supplied during this run
  and read completely before integration. Their P0/P1 scope agrees with this
  implementation; full typography is P2, final galleries P3/P4, the four-section
  Home/fallback solution P5, and authenticated commerce/Account acceptance P6.

| Concern | Existing source ownership | TumiArt P1 decision |
|---|---|---|
| Shell, manifest, Hilt, resources | Each application owns its composition | `:tumiart` maps explicitly to `apps/tumiart`; no shared brand branches |
| Configuration | Registry, generated projection, scoped ignored public-client input | Development only, TR/TRY, tr/en, Turkish durable Search normalization |
| Identity | App IDs, Room name, preferences, Keystore aliases are application-owned | Debug `com.tumiart.mobile.development.debug`; unsigned release `com.tumiart.mobile.development` is provisional and development-only |
| Account | Shared OAuth/session mechanics; app-owned mobile callback | Exact `shop.80183034101.tumiart://oauth/callback`, trusted `hesabim.tumiart.com`; lifecycle acceptance deferred to P6 |
| Menu/Home | App-selected bounded Shopify sources | `tum-koleksiyonlar`; `mobile_home_v2/primary`, schema 2/pilot-media-v2; no provisioning |
| Providers | Shared core is Firebase-neutral; Trial opts into Firebase | TumiArt has no Firebase dependency/plugin/config; local-default update gateway |
| Persistence | Shared implementations consume app-owned names | Independent `tumiart-development` identities; no existing migration change |
| Legal/support | Application-owned exact destinations | Only read-back TumiArt contact/privacy/membership pages may launch; other roles stay unavailable; no deletion-request claim |

Live read-only baseline discovery still identifies shop `80183034101`, exact
Shopify issuer, and the independently bound custom Account origin. Provisioning
pins remain `2026-07`; discovered runtime API version is separate evidence.
Ignored owner TumiArt development inputs (client properties and provider binding)
exist in the primary checkout. They were not copied into this P1 worktree:
config/local/tumiart/development.properties and the provider binding are
absent here. This P1 candidate was built and tested **unconfigured**, proving
fail-closed startup and package isolation only. A future configured development
smoke must compare/validate these inputs without exposing or tracking values
and remain brand-scoped; verified live Storefront, OAuth and commerce acceptance
is deliberately outside the tested P1 evidence and belongs to later slices.
No foreign-brand fallback is permitted.

| Asset | Reference availability | Approved APK use in this slice |
|---|---|---|
| Final PNG/HTML and website artwork | Retained static reference with asset manifest | No packaging rights inferred; excluded |
| Merchant logo/wordmark | Reference PNG exists | Excluded pending provenance/rights acceptance |
| Newsreader/Karla font binaries | Design direction only | Not bundled; system sans for scaffold |
| Launcher/splash | Newly authored project vector and platform theme | Safe replaceable placeholder, explicitly not final brand art |

The retained four-screen PNG was visually inspected. SHA-256 values:

- PNG: `4bcb9943be060d6a1c1f50e0a955d30ef2a5accaca4be6c336abdb4a14a25f41`
- HTML: `2600dc4083844ac6803dd7cba15ae4de1f3610436d8a9d5dc9f76d6b878dab01`
- Asset manifest: `6eb248d78a8d8d2f76aee952f2d5bc2ef0a738ae03eee8a21653063a7449085d`

The planned later-slice visual matrix below is recorded for continuity, not
claimed executed by P1. Every row crosses Light/Dark, 1×/2× text, narrow/standard
phones, portrait/landscape artwork, and successful/empty/error/offline states.

| Screen | Acceptance focus | Owning slice |
|---|---|---|
| Home | Short native hero/CTA, balanced collection access, one artwork, story; no stale commerce | P2/P5 |
| Collection discovery | Split Ustalar card, four 4:5 cards, all-artworks access, owned Menu actions | P2/P3 |
| Collection detail | Compact heading, two-column Fit media, live price/sort/pagination | P2/P3 |
| Artwork detail | Complete Fit artwork, zoom, real variant price/availability, no primary tabs or obstructed CTA | P2/P4 |

All later screens additionally require 48dp targets, Turkish long text,
TalkBack/semantics, system/IME insets, reduced-motion and lifecycle checks.
TumiArt P1 deliberately keeps the shared API-23 floor with an actual registered
API-23 app lane; no Play/Automatic Protection release requirement is inferred.

## Implementation plan and review focus

1. Add failing registry/enrollment assertions, enroll the module/application and
   all four CI lanes, generate projections through the supported generator.
2. Add application-owned configuration, shell/Hilt, resources and legal handling;
   test identity, exact callback, complete/fail-closed configuration, trusted
   origins, protected stores and provisional legal behavior.
3. Add launch/manifest instrumentation and a package validator proving Firebase
   absence and brand isolation in debug and minified release packages.
4. Run full offline onboarding, registry task resolution, focused and affected
   JVM tests, builds, format/static/lint, package/secret/isolation checks; execute
   device checks when available and exact-head CI through a protected PR.

Review especially: missing/partial local input; valid HTTPS endpoints belonging
to another merchant; callback sibling-host/path matches; minified package
provider contamination; existing-brand changes concealed by generated hashes.

P1 deliberately does not select a featured artwork or package a gallery. Empty
fallback selectors fail closed through existing shared validation; Categories is
the real merchant discovery entry. A missing Home root is a later content input,
not permission to create provider content in this task.

## Verification ledger — local candidate evidence, prior to PR

The initial enrollment negative test rejected UNKNOWN_APPLICATION: tumiart;
the supported registration and projections then passed registry checks. The
historical Gate 8 Apply fixture originally restored a hard-coded subset of
projections; its fix now enumerates registered applications/profiles and
verifies restoration. These are historical investigation facts, not a claim
that every mutable test was rerun in the final review.

Latest independent PR-preparation review (2026-10-15; exact uncommitted branch
codex/tumiart-stage4-p0-p1, base ed3926ca0e9197525d14d94635969d6742855df7):

- :tumiart:lintDevelopmentDebug: PASS after adding direct AppAuth
  dependency, removing the API 24-only launcher fillType, and refreshing
  the TumiArt Gradle dependency locks.
- Full spotlessCheck detekt lint: PASS (400 tasks), on Microsoft JDK 17.
- :tumiart:assembleDevelopmentDebug and
  :tumiart:assembleDevelopmentDebugAndroidTest: PASS with current P1 changes.
- :tumiart:assembleDevelopmentRelease: PASS (246 tasks), current R8-minified
  unsigned development-only artifact, reassembled after AppAuth/lock changes.
- :tumiart:testDevelopmentDebugUnitTest: PASS (9 tests, no errors/failures;
  7 configuration tests and 2 legal/support tests).
- scripts/Test-TumiArtPackage.ps1 -SelfTest: PASS (9 fixtures).
- scripts/Test-TumiArtPackage.ps1 -Variant All: PASS (27 checks) against
  current debug and minified release APKs. Assertions cover exact identity,
  manifest routes and backup flags, no Firebase DEX/provider, no Gürbakır/
  Trial persistence identity in DEX, R8 map, recognized secret patterns and
  development-signing boundaries.
- Onboarding suites Registry (121/121), Configuration (19/19),
  Enrollment (13/13) and OperatorReadOnly (90/90): PASS on current tree.
  The historical mutating -Suite All fixture was **not** repeated during
  final read-only review; it remains a required PR CI validation.
- scripts/Test-RepositoryPortability.ps1 -SelfTest: PASS (69 fixtures).
- Get-RegisteredGradleTasks.ps1 -Lane unit/assemble/api30/api23
  -ValidateOnly: PASS; expanded lists contain TumiArt in every lane.
- Android **API 23** emulator (independent x86_64 test AVD on D:): actual
  TumiArt instrumentation OK (2 tests) / INSTRUMENTATION_CODE: -1,
  verifying unconfigured fail-closed launch and owned OAuth/HTTPS manifest
  boundaries. This is local runtime evidence, not GitHub managed-device CI.
- git diff --check: PASS; main checkout remains clean; initial worktree
  changes retained. CI secret scan and PR exact-head checks are **not yet run**.

Evidence is in ignored out/tumiart-p1/:
review-tumiart-jvm-after-lock-stdout.log,
review-tumiart-assemble-release-stdout.log,
review-tumiart-package-all-stdout.log,
control5-full-static-stdout.log,
control6-api23-instrumentation-stdout.log,
CONTROL-6-API23-VERIFICATION-CHECKPOINT.md, and
CONTROL-7-INDEPENDENT-PRE-PR-REVIEW-2026-10-15.md.

### Residual boundaries and PR gate

- The app remains **unconfigured** in this worktree. Successful tests do not
  demonstrate live TumiArt Storefront, authenticated OAuth lifecycle, checkout,
  real catalog/Home editorial content, final visual fidelity, or production
  legal/release readiness. These are intentionally later acceptance slices.
- apps/tumiart/gradle.lockfile was newly generated with --write-locks for
  compile, AndroidTest, unit-test and release paths. It must be tracked in
  the PR along with the direct AppAuth dependency; unrelated brand versions
  were not intentionally upgraded.
- Previous Temurin 17.0.20.1 Windows JVM crash has no established root cause.
  Microsoft JDK 17.0.19 was successful locally; GitHub Linux Temurin CI still
  requires exact-head verification.
- The pre-PR independent review found no verified source-level P1 blocker, but
  neither that review nor the successful local APK/device tests are substitute
  evidence for all PR-head GitHub Actions checks, merge protection, or
  post-merge verification. The PR must remain **unmerged** for owner review.
