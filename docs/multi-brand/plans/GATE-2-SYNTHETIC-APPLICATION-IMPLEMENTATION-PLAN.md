# Gate 2 Synthetic Application Implementation Plan

> **Status:** APPROVED FOR EXECUTION
>
> **Owner approval:** 2026-09-08, in the continuing Gate 2 planning and execution conversation.
>
> **Execution rule:** Use this plan with `superpowers:using-git-worktrees`, `superpowers:executing-plans`, test-driven development, and evidence-before-completion verification. Gate 2 stops before Gate 3.

**Goal:** Prove that the Gate 1 shared Android implementation can be composed, built, packaged, and run from a genuinely separate non-production Android application without changing Gürbakır identity, migration, persistence, Firebase, product, or release behavior.

**Logical Gradle project:** `:synthetic`

**Physical repository directory:** `apps/synthetic`

**Planning SHA:** `a6f3b3ffbfedd0e188b49c31936b9dce80ce0628`

**Execution-base SHA:** `a6f3b3ffbfedd0e188b49c31936b9dce80ce0628`

**Execution branch:** `codex/multibrand-gate-2-synthetic`

**Resolved execution worktree:** `<historical-gate-2-worktree>`

**Physical-device baseline:** INFINIX Infinix X6817, Android 12, API 31, build `SP1A.210812.016`. Device serial is intentionally omitted from the durable record.

---

# 1. Verified Planning Baseline

- A fresh execution-start fetch confirmed `origin/main` still equalled the planning SHA.
- The branch and worktree were absent locally and remotely before creation.
- The worktree was created directly at the execution SHA, was clean, and inherited zero commits.
- The repository initially contained seven included subprojects plus the Gradle root project.
- The initial shared/application JVM suite passed: 195 actionable tasks, 89 executed, 106 from cache.
- The initial repository portability validator passed all 25 checks.
- Existing compiler annotation warnings and the Gradle 10 deprecation summary were observed before Gate 2 changes and are baseline conditions, not Gate 2 regressions.
- Gate 1 is complete; no second application existed at the execution base.
- P3-16 is not started and remains outside Gate 2.

# 2. Authority and Source Map

Apply authority in this order:

1. `AGENTS.md` and `docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md`.
2. `docs/README.md` and the current multi-brand documentation map.
3. `docs/multi-brand/GATE-1-COMPLETION-HANDOFF.md`.
4. ADR-0004 and the current documents under `docs/architecture/`.
5. `docs/architecture/GURBAKIR-LEGACY-IDENTITIES.md`.
6. Current source/build configuration for implemented facts.
7. Current official Gradle, Android, Hilt, Room, Kotlin, and dependency-tooling contracts.

Current source outranks stale generated Graphify paths. Historical research remains provenance and is not blanket-rewritten.

# 3. Gate 2 Scope Decision

Gate 2 adds exactly one second Android application edge:

```text
logical project    :synthetic
physical directory apps/synthetic
role               non-production synthetic conformance application
```

In scope:

- Separate app identity, package, manifest, resources, icon, theme, and Android sandbox.
- Existing `MobileCoreApp` composition through app-owned configuration and Hilt bindings.
- Synthetic Home/Catalog/address/deep-link/tracking/legal configuration.
- Firebase-free update-policy composition through `UpdatePolicyRefreshResult.LocalDefaults`.
- Separate Room filename and Search/Wishlist partitions.
- Process-memory cart and Customer Account stores.
- JVM, package, managed-device, physical-device, portability, security, and CI evidence.
- Durable completion documentation and final PR-head CI.

Out of scope:

- A real merchant or production application.
- Brand flavors or runtime merchant switching.
- Capability-driven navigation or optional Search/Wishlist/Customer Account.
- Domain/media generalization, durable protected-store identities, or other Gate 3 inputs.
- Runtime custom fonts, generic providers/plugins, Shopify Navigation, or Home generalization.
- Credentials, production signing, Play, real orders/payments/customers, or P3-16.
- Gate 3 implementation.

# 4. Current Composition and API Map

Gate 2 uses these existing public seams without changing them:

- `MobileCoreApp` for shared Compose/navigation implementation.
- `AppConfiguration` and `BrandConfiguration` from `:foundation`.
- `HomeConfiguration`, `CatalogConfiguration`, `AddressTerritoryPolicy`, `MobileDeepLinkConfiguration`, and `TrackingUrlPolicy` from `:mobile-core`.
- `LocalCommerceDatabase`, `SearchHistoryPartition`, and `WishlistPartition` from `:mobile-core`.
- `CartSessionStore` from `:storefront` and `CustomerSessionStore` from `:account`.
- `CurrentAppVersionCode` and `UpdatePolicyRemoteGateway` from `:mobile-core`.
- `DeletionPageSource` and `DeletionPageLaunchResult` from `:mobile-core`.

The fixed Home/Categories/Search/Wishlist/Account navigation graph remains unchanged. `enabledFeatures` is configuration data but is not a runtime capability switch. Storefront media policy still has later Gürbakır-specific debt; the synthetic app prevents network access rather than generalizing it in Gate 2.

# 5. Synthetic Application Design

## 5.1 Build and Android identity

```text
namespace                 com.example.gate2synthetic
release application ID    com.example.gate2synthetic
debug application ID      com.example.gate2synthetic.debug
application class         com.example.gate2synthetic.Gate2SyntheticApplication
launcher activity         com.example.gate2synthetic.MainActivity
app label                 Gate 2 Synthetic
resource ownership marker Synthetic Lab
version code              1
version name              0.1.0-gate2
variants                  debug and release only
compile / target / min    36 / 36 / 23
Java                      17
AppAuth scheme            shop.0.gate2synthetic
redirect URI              shop.0.gate2synthetic://oauth/callback
```

Release is minified, resource-shrunk, unsigned, unregistered, and non-production.

## 5.2 Fail-closed service configuration

```text
environment                 DEVELOPMENT
storefront domain           storefront.gate2.invalid
Storefront API version      2026-07
Storefront public token     empty
Customer Account client ID  empty
issuer                      https://accounts.gate2.invalid
authorization endpoint      https://accounts.gate2.invalid/oauth/authorize
token endpoint              https://accounts.gate2.invalid/oauth/token
logout endpoint             https://accounts.gate2.invalid/oauth/logout
GraphQL endpoint            https://accounts.gate2.invalid/graphql
scopes                      openid, email, customer-account-api:full
Firebase                    false
analytics / crashlytics     false / false
```

The exact deliberate validation issues are `STOREFRONT_PUBLIC_TOKEN` and `CUSTOMER_ACCOUNT_CLIENT_ID`; existing unconfigured gateways must be selected.

## 5.3 Brand contract

```text
key                   gate2-synthetic
display name          Gate 2 Synthetic
default locale        en-CA
supported locales     { en-CA }
analytics namespace   gate2_synthetic
enabledFeatures       emptySet()
app icon              ic_gate2_synthetic_launcher
privacy               https://legal.gate2.invalid/privacy
terms                 https://legal.gate2.invalid/terms
support               https://legal.gate2.invalid/support
font resource         null
shape dp               4 / 16 / 28
spacing dp             6 / 12 / 20 / 32
motion ms              100 / 240 / 420
```

Complete color contract:

| Token | Light | Dark |
|---|---:|---:|
| primary | `0xFF4F378B` | `0xFFD0BCFF` |
| onPrimary | `0xFFFFFFFF` | `0xFF381E72` |
| primaryContainer | `0xFFEADDFF` | `0xFF4F378B` |
| onPrimaryContainer | `0xFF21005D` | `0xFFEADDFF` |
| secondary | `0xFF625B71` | `0xFFCCC2DC` |
| onSecondary | `0xFFFFFFFF` | `0xFF332D41` |
| secondaryContainer | `0xFFE8DEF8` | `0xFF4A4458` |
| onSecondaryContainer | `0xFF1D192B` | `0xFFE8DEF8` |
| tertiary | `0xFF7D5260` | `0xFFEFB8C8` |
| onTertiary | `0xFFFFFFFF` | `0xFF492532` |
| tertiaryContainer | `0xFFFFD8E4` | `0xFF633B48` |
| onTertiaryContainer | `0xFF31111D` | `0xFFFFD8E4` |
| background | `0xFFFFF7FF` | `0xFF141218` |
| onBackground | `0xFF1D1B20` | `0xFFE6E0E9` |
| surface | `0xFFFFF7FF` | `0xFF141218` |
| onSurface | `0xFF1D1B20` | `0xFFE6E0E9` |
| surfaceVariant | `0xFFE7E0EC` | `0xFF49454F` |
| onSurfaceVariant | `0xFF49454F` | `0xFFCAC4D0` |
| surfaceContainerLow | `0xFFF7F2FA` | `0xFF1D1B20` |
| surfaceContainer | `0xFFF3EDF7` | `0xFF211F26` |
| surfaceContainerHigh | `0xFFECE6F0` | `0xFF2B2930` |
| outline | `0xFF79747E` | `0xFF938F99` |
| outlineVariant | `0xFFCAC4D0` | `0xFF49454F` |
| error | `0xFFB3261E` | `0xFFF2B8B5` |
| onError | `0xFFFFFFFF` | `0xFF601410` |
| errorContainer | `0xFFF9DEDC` | `0xFF8C1D18` |
| onErrorContainer | `0xFF410E0B` | `0xFFF9DEDC` |
| inverseSurface | `0xFF322F35` | `0xFFE6E0E9` |
| inverseOnSurface | `0xFFF5EFF7` | `0xFF322F35` |
| inversePrimary | `0xFFD0BCFF` | `0xFF6750A4` |
| scrim | `0xFF000000` | `0xFF000000` |

Complete typography contract:

| Style | Size / line height | Weight |
|---|---|---|
| headlineLarge | 34 / 42 sp | SEMIBOLD |
| headlineMedium | 30 / 38 sp | SEMIBOLD |
| headlineSmall | 26 / 34 sp | MEDIUM |
| titleLarge | 20 / 28 sp | SEMIBOLD |
| titleMedium | 17 / 24 sp | MEDIUM |
| titleSmall | 15 / 22 sp | MEDIUM |
| bodyLarge | 18 / 26 sp | REGULAR |
| bodyMedium | 15 / 22 sp | REGULAR |
| bodySmall | 13 / 18 sp | REGULAR |
| labelLarge | 15 / 20 sp | SEMIBOLD |
| labelMedium | 13 / 18 sp | MEDIUM |
| labelSmall | 12 / 16 sp | MEDIUM |

Tests must assert the complete literal value object. Runtime tests must additionally prove representative MaterialTheme and composition-local values. No custom-font behavior is claimed.

## 5.4 Market, fixtures, and policies

```text
market / currency       ZZ / XTS
user-switchable         false
Search partition        DEVELOPMENT / ZZ
Wishlist partition      DEVELOPMENT / ZZ
database                gate2-synthetic-local.db
postal regex            ^AB-[0-9]{4}$
tracking host           tracking.gate2.invalid

Home range              SYNTHETIC_HOME_RANGE / Synthetic Picks / item limit 3
Home source A           SYNTHETIC_HOME_ALPHA / synthetic-alpha / Synthetic Alpha
Home source B           SYNTHETIC_HOME_BETA / synthetic-beta / Synthetic Beta
featured                SYNTHETIC_HOME_FEATURED / synthetic-featured-product / Synthetic Feature
Catalog A               SYNTHETIC_CATALOG_ALPHA / synthetic-alpha / Synthetic Alpha
Catalog B               SYNTHETIC_CATALOG_BETA / synthetic-beta / Synthetic Beta
```

Deep-link bases:

```text
https://links.gate2.invalid/collections
https://links.gate2.invalid/apps/mobile/products
https://links.gate2.invalid/apps/mobile/orders
```

Manifest filters use the matching trailing-slash prefixes and `android:autoVerify="false"`.

## 5.5 App-owned composition

- A plain `@HiltAndroidApp` Application; no Firebase or custom Coil loader.
- An `@AndroidEntryPoint` single Activity with edge-to-edge Compose content.
- `Gate2SyntheticApp` calls `MobileCoreApp` directly.
- The legal/support surface is synthetic and never launches external content.
- Deletion-page launch always returns `REJECTED`.
- App-side Hilt modules provide all configuration, persistence, local-data, deletion, version, and update-policy bindings needed by the shared graph.

# 6. New Shared Seams and Variance Rule

Expected new shared runtime APIs: **none**.

Synthetic-only imports, Hilt providers, manifest/resource fixes, test synchronization, package parsing, and bounded R8 rules are ordinary corrections.

Stop for owner review before changing architecture when concrete compiler/Hilt/Gradle/resource/R8/runtime evidence shows that an existing public seam cannot express the composition. Capture the exact failure, explain why existing APIs fail, propose the smallest seam, classify its gate, and identify Gürbakır regression risk. Do not create abstractions merely to silence a compiler error.

# 7. Exact Gradle Contract

`settings.gradle.kts` must include eight flat subprojects and then map the synthetic directory:

```kotlin
include(
    ":app",
    ":synthetic",
    ":mobile-core",
    ":foundation",
    ":storefront",
    ":account",
    ":checkout",
    ":firebase"
)

project(":synthetic").projectDir = file("apps/synthetic")
```

Invariant: eight included subprojects excluding root, nine Gradle project objects including root, and no `:apps` project.

The synthetic build applies Android application, Compose compiler, KSP, Hilt, and detekt plugins. It does not apply Kotlin serialization, Room, or Google Services plugins.

Direct project dependencies are `projects.mobileCore`, `projects.foundation`, `projects.storefront`, `projects.account`, and `projects.checkout`. `projects.app` and `projects.firebase` are forbidden.

No dependency version or catalog entry is added. Dependency locks are generated, not handwritten. The synthetic lock path is `apps/synthetic/gradle.lockfile`. Unexpected existing lock, settings lock, catalog, or verification-metadata changes require exact resolution evidence.

Exact task prefix: `:synthetic:*`. APKs are discovered through `apps/synthetic/build/outputs/apk/**/output-metadata.json` rather than filename assumptions.

# 8. Resource and Manifest Conformance

Both `values/strings.xml` and `values-en/strings.xml` must own:

```xml
<string name="app_name">Gate 2 Synthetic</string>
<string name="home_title">Synthetic Lab</string>
<string name="home_product_range_title">Synthetic Picks</string>
```

Tests must prove `home_title` in packaged `default` and `en` configurations and at runtime under `en-CA`, emulator English, and non-English fallback. This is a bounded resource-ownership proof, not a localization architecture.

The merged manifest must contain the synthetic Application, Activity, icon, label, theme, inert deep links, exact AppAuth scheme, restrictive backup/cleartext settings, no Firebase component/metadata, and no INTERNET permission. Transitive INTERNET declarations are explicitly removed.

# 9. Persistence Isolation

- Build a fresh schema-2 `LocalCommerceDatabase` named `gate2-synthetic-local.db` without attaching a migration.
- Inject exact `DEVELOPMENT / ZZ` Search and Wishlist partitions.
- Bind singleton Mutex-protected process-memory cart and customer session stores.
- Exercise read/write/clear and verify no Gürbakır database, SharedPreferences file, or Android Keystore alias is created in the synthetic sandbox.
- Do not claim whole-APK absence of dormant Gürbakır persistence constants from dependent library bytecode.
- Do not modify Gürbakır database, migration, SharedPreferences, or Keystore identities.
- Durable reusable protected-store identities and restore/upgrade behavior remain Gate 3+.

Overall persistence classification: **PARTIALLY PROVEN IN GATE 2**.

# 10. Portability Validator Changes

Refactor module inventory to records with `LogicalPath`, `Directory`, and `Role`.

The validator must:

- Model both application modules and the physical mapping.
- Expand hierarchical include paths and reject implicit/explicit `:apps`.
- Require exactly the eight approved included subprojects.
- Derive build and lock paths from physical directories.
- Reject shared/provider-to-app, app-to-app, synthetic-to-Firebase, and mobile-core-to-Firebase dependencies in all supported Gradle syntaxes.
- Preserve every current Gate 1/Gürbakır check.
- Add Firebase-absence and bidirectional contamination checks without rejecting legitimate shared `com.gurbakir...` package imports.
- Decode XML values before checking them.
- Add `-SelfTest` with pure temporary fixtures covering valid and invalid topology, mapping, dependency syntax, Firebase, and Kotlin/Java/XML contamination cases.

# 11. CI Changes

Add:

```text
:synthetic:testDebugUnitTest
:synthetic:assembleDebug
:synthetic:assembleRelease
:synthetic:assembleDebugAndroidTest
:synthetic:ciApi30DebugAndroidTest
```

Run the package validator after packaging. Run portability self-tests and normal clean-worktree validation. Retain all existing core/Gürbakır lanes. Fresh CI for the final documentation-bearing PR HEAD is mandatory.

# 12. Baseline and Final Validation Commands

Execution began only after fetch, exact SHA comparison, branch absence checks, clean worktree creation, exact HEAD equality, and zero inherited commits.

Final validation must include, serially:

1. Synthetic task discovery.
2. Lock generation and diff review.
3. `spotlessApply`, `spotlessCheck`, detekt, and lint.
4. Portability validator self-tests and repository checks.
5. All shared/Gürbakır/synthetic JVM tests.
6. Core, Gürbakır, synthetic debug/release/androidTest packaging.
7. APK/manifest/resource/dex/signing inspection.
8. API 30 GMD for core, Gürbakır, and synthetic.
9. Supplemental API 31 physical-device installation, flows, instrumentation, and focused diagnostics.
10. Git history/diff/cleanliness checks against the execution base.
11. Fresh final PR CI.

Record actual commands, outputs, failures, corrections, and artifact hashes in the completion handoff.

# 13. Gürbakır Compatibility Matrix

Gate 2 must preserve:

- All application IDs and environment flavors.
- OAuth callback scheme and App Links.
- `gurbakir-local.db`, Room schema 2, and migration 1→2.
- Customer and cart SharedPreferences/Keystore names.
- Optional four-file non-production Firebase configuration behavior.
- Fixed navigation and current resources.
- `:app -> :mobile-core` composition and all existing validation lanes.
- P3-13 remote-deletion uncertainty and P3-16 not-started status.

Existing tests, package inspection, validator checks, and scoped diffs provide the evidence.

# 14. Gate 2 Conformance Matrix

Target only after evidence:

| Dimension | Classification |
|---|---|
| Separate application edge, Gradle topology, app identity, packaged resource/manifest ownership | PROVEN IN GATE 2 |
| Design-token value/wiring | PROVEN IN GATE 2 |
| Runtime custom font | DEFERRED BEYOND GATE 2 |
| Market/locale, Home, Catalog, address, tracking, deep links | PARTIALLY PROVEN IN GATE 2 |
| Domain/media and reusable persistence inputs | DEFERRED TO GATE 3 |
| Search, Wishlist, Customer Account | PARTIALLY PROVEN IN GATE 2; optionality DEFERRED TO GATE 4 |
| Firebase absence | PROVEN IN GATE 2 |
| Generic provider architecture | DEFERRED TO GATE 5 |
| Persistence | PARTIALLY PROVEN IN GATE 2 |
| Unsigned non-production release and managed-device launch | PROVEN IN GATE 2 |
| External registrations | NOT APPLICABLE TO SYNTHETIC |
| Production readiness | DEFERRED BEYOND GATE 2 |

Before execution evidence, every build/runtime row is `NOT RUN`.

# 15. Test Ownership

- Synthetic JVM configuration tests own exact values, fail-closed issues, policies, fixtures, and tokens.
- Store JVM tests own real process-memory read/write/clear and concurrency behavior.
- Synthetic instrumentation owns Hilt launch, Home ownership marker, fixed navigation, locale/resource behavior, database/partition/store composition, and deep-link routing.
- `Test-RepositoryPortability.ps1` owns structural topology/dependency/contamination boundaries.
- `Test-Gate2SyntheticPackage.ps1` owns built APK identity, manifest, resource, Firebase, permission, signing, and artifact discovery.
- Existing tests retain Gürbakır and shared behavior ownership.

Production behavior is implemented test-first. Configuration-only scaffolding and documentation use direct deterministic validation.

# 16. Dependency and Security Review

- Add no external dependency/version.
- Keep synthetic independent of app/Firebase.
- Use `.invalid` hosts, empty credentials, disabled telemetry, and no INTERNET permission.
- Keep all real tokens, credentials, signing data, and private identifiers out of source/logs/docs.
- Use restrictive backup/data-transfer rules.
- Keep release unsigned.
- Inspect the final merged package, not just source.
- Treat a newly required dependency or shared runtime API as an architectural variance.
- Never contact real merchant/reference services or mutate real customer/order/payment data.

# 17. Commit Strategy

1. `docs(multi-brand): approve Gate 2 execution plan`
2. `feat(multi-brand): add synthetic application composition`
3. `test(multi-brand): enforce synthetic application boundaries`
4. `docs(multi-brand): record Gate 2 evidence`

Inspect the exact staged manifest before every commit. Preserve unrelated work. Do not amend, squash, force-push, merge, or delete branches.

# 18. Documentation and Handoff

`docs/multi-brand/GATE-2-COMPLETION-HANDOFF.md` is a required engineering record, not a command list. It must preserve:

- What was implemented and why.
- Actual findings, decisions, corrections, deviations, defects, and review resolutions.
- Confirmed/refined/disproved planning assumptions.
- Rejected alternatives with technical reasons.
- Local, managed-device, physical-device, package, Git, PR, and CI evidence.
- Gürbakır compatibility evidence.
- Partial Gate 2 proofs and Gate 3+ deferrals.
- Exact final repository/architecture state.

Update current documentation maps and status documents after evidence. Preserve historical records. The final documentation-bearing PR HEAD must receive fresh CI.

# 19. Explicit Non-goals

No real merchant, production flavor, runtime brand switch, capability redesign, domain/media generalization, durable protected-store redesign, custom font, provider framework, Firebase integration, production signing/Play, real external mutation, P3-16, merge, or Gate 3 work.

# 20. Completion Criteria

Gate 2 is complete only when:

- The exact eight-subproject topology and `:synthetic -> apps/synthetic` mapping pass.
- Debug, unsigned minified release, and androidTest packages build.
- APK/manifest/resource/Firebase/permission/signing checks pass.
- Hilt launch and bounded flows pass on API 30 GMD.
- Supplemental API 31 physical evidence is recorded honestly.
- Synthetic DB, partitions, ephemeral stores, and runtime write isolation pass.
- Gürbakır identities, migration, functionality, packages, and test lanes remain intact.
- Static, JVM, validator, package, managed-device, dependency, and secret checks pass.
- Lock/verification changes are fully explained.
- Final documentation accurately records implementation history and boundaries.
- The worktree is clean and history derives from the execution base.
- Fresh GitHub PR CI passes for final PR HEAD.
- Gate 3 and P3-16 remain not started.

A build alone is insufficient.

# 21. Final Consistency Review

Final values:

```text
logical project       :synthetic
physical directory    apps/synthetic
accessor              projects.synthetic
included subprojects  8 excluding root
total Gradle projects 9 including root
task prefix           :synthetic:
lock                  apps/synthetic/gradle.lockfile
APK root              apps/synthetic/build/outputs/apk
release ID            com.example.gate2synthetic
debug ID              com.example.gate2synthetic.debug
OAuth scheme          shop.0.gate2synthetic
database              gate2-synthetic-local.db
resource marker       Synthetic Lab in default and en
persistence           PARTIALLY PROVEN IN GATE 2
```

Executable configuration must not contain `:apps:synthetic`, `projects.apps.synthetic`, or a `:apps:synthetic:*` task. Those spellings may appear only in historical explanation or validator negative fixtures.

# 22. Same-conversation Execution Continuation

The owner approved this plan in the same conversation. Execution therefore continues without another general permission prompt. Ordinary implementation corrections stay within this plan. An architectural variance or point-of-action action identified by owner policy requires review. After implementation, validation, PR, fresh CI, and the final handoff, stop before Gate 3.
