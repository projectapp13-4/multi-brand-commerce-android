# Gate 3 — Identity / Domain / Market / Persistence Inputs Implementation Plan

**Status:** Approved / execution authority
**Owner approval:** 2026-09-09, in the same ChatGPT Work conversation

## 1. Summary and boundary

Gate 3 removes remaining concrete merchant, market, locale, media-origin, and
protected-storage assumptions from shared implementation while preserving
Gürbakır's migration-sensitive identities and persisted data.

The implementation will:

- Separate localization and market inputs from BrandConfiguration.
- Move fixed market ownership from Home into AppConfiguration.
- Derive Search/Wishlist partitions from the application market.
- Make persisted Search normalization an explicit stable app-owned policy.
- Make StorefrontMediaPolicy a configurable fail-closed instance derived from
  the existing Storefront domain.
- Inject exact SharedPreferences and Android Keystore identities into reusable
  cart and customer-session stores.
- Replace synthetic memory session stores with distinctly named encrypted
  stores.
- Unify foreground Activity resources and formatting under a deterministic
  locale policy, including explicit tested Gürbakır formatting corrections.
- Preserve Room databases, schemas, migrations, application IDs, OAuth/App
  Links, Firebase composition, and external registrations.

Gate 3 does not create a real second merchant, runtime
merchant/market/language switching, generalized onboarding,
capability-driven navigation, or provider architecture. Gate 4, Gate 5, and
P3-16 remain outside this gate.

## 2. Planning evidence and authority

### Git and source baseline verified on 2026-09-09

| State | SHA / observation |
|---|---|
| Planning checkout main | a6f3b3ffbfedd0e188b49c31936b9dce80ce0628 |
| Planning checkout local origin/main | a6f3b3ffbfedd0e188b49c31936b9dce80ce0628 |
| Remote main observed during planning | 417cd18165561a6bfa555060dc02ccf37e3ea743 |
| Audited Gate 2 worktree/branch | 30bdbebee1aa26c9ef825ed22a76eb63b4899212 |

At execution start origin was fetched. Refreshed origin/main was
417cd18165561a6bfa555060dc02ccf37e3ea743, contained Gate 2 head
30bdbebee1aa26c9ef825ed22a76eb63b4899212, and both commits resolved to
tree 9cc73c16acf2b0e84e3f28fc8980491a457f5768. No implementation drift
required reconciliation.

### Validator evidence correction

The prior 17/17 portability statement is withdrawn:

- The stale planning-checkout script at a6f3b3f, SHA-256
  35D82A66A3FE60D17428FA726EFDC6D7FC67D1AFFBB72E7E430F70650662E01B,
  does not support -SelfTest; that parameter fails before any fixture.
- No retained command or stdout establishes a different 17-fixture script.
  The earlier claim is NOT RUN / NOT SUBSTANTIATED.
- Planning freshly ran Test-RepositoryPortability.ps1 -SelfTest at Gate 2
  head 30bdbeb. Script SHA-256 was
  BC5D338965D10731DBCD4AC4FC816E18622F83F6B657345983B698CD8BEC96A3;
  observed result was PASS, 19 fixtures.
- Planning freshly ran Test-Gate2SyntheticPackage.ps1 -SelfTest there;
  observed result was PASS, 6 fixtures.
- The refreshed execution baseline reproduced PASS, 19 fixtures and PASS,
  6 fixtures.

These planning/baseline self-tests prove only the existing validators, not
Gate 3 implementation or repository conformance.

### Authority order

1. Current source/configuration at refreshed origin/main.
2. docs/OWNER-AUTHORITY-AND-APPROVAL-BOUNDARIES.md.
3. Canonical multi-brand architecture, boundaries, onboarding,
   legacy identities, and ADR-0004.
4. Gate 2 completion evidence and its approved plan.
5. Phase 2/3 records for provenance, security, and compatibility.
6. Normative research only as design history.
7. Current primary Android, OkHttp, Shopify, backup, and Keystore contracts.

## 3. Gate 3 problem inventory

| Finding | Classification | Decision |
|---|---|---|
| Separate app IDs, manifests, resources, links, sandboxes | Correct Gate 2 behavior | Preserve |
| Gürbakır brand values, assets, legal links, Home/Catalog handles | Legitimate app inputs | Preserve |
| com.gurbakir namespaces and gate2 synthetic names | Historical/internal names | No cosmetic cleanup |
| StorefrontConfiguration.domain | Correct app/environment input | Sole merchant media-domain source |
| Static media policy contains gurbakir.com | Shared merchant leakage | Replace with app-derived instance |
| cdn.shopify.com | Provider constant | Retain exact host |
| Network-interceptor media validation | Runs after connection; redirect gap | First application interceptor |
| Catalog pagination uses generic HTTPS parsing | Media-policy bypass | Route through policy |
| Locale metadata in BrandConfiguration | Misplaced, unused | Focused app localization policy |
| Home, Orders, Legal use three locale rules | Runtime inconsistency | One effective foreground locale |
| Market metadata in HomeConfiguration | App input trapped in feature | Move to AppConfiguration |
| userSwitchable=false | Unimplemented runtime semantics | Remove |
| Search/Wishlist hardcode TR separately | Correct value, duplicate ownership | Derive from app market |
| Search normalization hardcodes tr-TR | Shared persistence assumption | Inject policy; preserve exact keys |
| Address territory/postal validation injected | Correct generic behavior | Preserve |
| Postal keyboard always numeric | Shared territory/UI assumption | Explicit finite mode |
| Phone copy says +90 required | Conflicts with generic E.164 | Make +90 an example |
| gurbakir-local.db, schema 2, migration 1 to 2 | Migration-sensitive | Preserve byte-for-byte |
| gate2-synthetic-local.db | Correct synthetic identity | Preserve |
| Secure stores derive Gürbakır names/aliases | Shared brand leakage | Inject exact app-owned identities |
| Synthetic sessions are in memory | Valid Gate 2, insufficient Gate 3 | Use distinct encrypted stores |
| Firebase/provider composition | Gate 5 | Unchanged |
| Fixed feature graph/navigation | Gate 4 | Unchanged |

## 4. Target contracts and ownership

### Foundation configuration

~~~kotlin
data class LocalizationPolicy(
    val defaultLocaleTag: String,
    val supportedLocaleTags: List<String>,
)

data class MarketConfiguration(
    val id: String,
    val countryCode: String,
    val currencyCode: String,
)

data class ProtectedStoreIdentity(
    val preferencesName: String,
    val keyAlias: String,
)

data class ProtectedPersistenceConfiguration(
    val cart: ProtectedStoreIdentity,
    val customerSession: ProtectedStoreIdentity,
)
~~~

AppConfiguration gains localization, market, and protectedPersistence.
BrandConfiguration loses defaultLocaleTag and supportedLocaleTags. Brand
retains visual identity, assets, legal links, feature metadata, and analytics
namespace. HomeConfiguration loses market and keeps merchandising inputs.

Validation rules:

- Locale tags use lowercase language and optional uppercase region.
- Supported locales are ordered, non-empty, duplicate-free, and include the
  default.
- Market IDs match [A-Z][A-Z0-9_-]{1,31}.
- Country codes are two uppercase letters; currency codes are three.
- Preference names match [a-z][a-z0-9_]{1,79}.
- Keystore aliases match [a-z][a-z0-9.-]{1,127}.
- Cart/customer preference names and aliases are mutually distinct.
- Shared stores never normalize or derive protected identities.
- Validation errors do not echo domains, identities, URLs, tokens, or
  payloads.

No database configuration framework, arbitrary identity registry, giant
brand object, or speculative multi-market engine is introduced.

### Exact application inputs

| Input | Gürbakır | Synthetic |
|---|---|---|
| Localization | default tr; supported tr, en in order | default/supported en-CA |
| Market | TR / TR / TRY | ZZ / ZZ / XTS |
| Search normalization | tr-TR | en-CA |
| Postal input mode | NUMERIC | TEXT |
| Room database | gurbakir-local.db | gate2-synthetic-local.db |
| Cart prefs | gurbakir_secure_cart_development or _staging | gate2_synthetic_secure_cart_development |
| Cart alias | gurbakir.cart.development.v1 or .staging.v1 | gate2.synthetic.cart.development.v1 |
| Customer prefs | gurbakir_secure_customer_session_development or _staging | gate2_synthetic_secure_customer_session_development |
| Customer alias | gurbakir.customer.session.development.v1 or .staging.v1 | gate2.synthetic.customer.session.development.v1 |
| Merchant media host | Existing validated Storefront domain, configured as gurbakir.com | storefront.gate2.invalid |

The tracked empty Storefront-domain default yields deny-all media behavior. It
must not crash startup or allow provider media independently.

BuildConfigurationSource.create() becomes one immutable
BuildConfigurationSource.current used by Hilt and GurbakirApplication.
Unknown ENVIRONMENT_ID values fail closed. Development/staging protected
identities use exhaustive literal mappings, never concatenation. Synthetic
retains its static configuration source. Tests prove equivalent policy
behavior across gateway and loader composition, not object referential
identity.

### Feature-specific contracts

- Add SearchHistoryNormalizationPolicy(localeTag) in mobile-core. Keep NFKC,
  trim, whitespace normalization, and lowercase; use the injected locale.
- Add PostalCodeInputMode.NUMERIC and TEXT to AddressTerritoryPolicy with no
  implicit mode.
- Turkish phone copy becomes: Telefon numarasını ülke koduyla girin
  (ör. +90 5xx xxx xx xx).
- English phone copy becomes: Enter the phone number with its country code
  (for example, +90 5xx xxx xx xx).
- Preserve generic E.164 validation and existing address semantics.

No external API, GraphQL operation, Room schema, deep-link, or OAuth callback
changes.

## 5. Locale, market, territory, and formatting

### Locale resolution

Preserve region-sensitive formatting when a requested locale belongs to a
supported language. A configured language-only en supports English resources
without converting en-US or en-GB to generic en.

Implement a pure resolver plus Context.withAppLocale(policy,
allowPseudoLocales):

1. Read requested device locales in order.
2. Preserve en-XA or ar-XB only when allowPseudoLocales is true; otherwise
   skip them before language matching.
3. Preserve a full canonical requested tag for an exact supported tag.
4. Preserve a full canonical requested tag when its language matches a
   supported locale's language.
5. Continue after unsupported requested locales.
6. Fall back to configured default when no request is supported.
7. Deduplicate and append default as final fallback.
8. API 23 applies the first result with Configuration.setLocale.
9. API 24+ applies the list with Configuration.setLocales.
10. Return createConfigurationContext.

Apply in attachBaseContext for Gürbakır MainActivity, debug
Stage3EvidenceActivity and Stage4EvidenceActivity, and synthetic MainActivity.
Do not wrap Application, call Locale.setDefault, add AppCompat/LocaleManager,
add a picker, or persist selection.

Synthetic UI intentionally changes from Turkish fallback on a Turkish device
to English using en-CA default. A supported en-US request retains en-US;
en-CA is fallback for unsupported languages.

### Gürbakır compatibility matrix

The pre-Gate-3 Legal column assumes process default matched requested locale.
Otherwise it was process-state-dependent, which Gate 3 removes.

| Requested | Pre Home money | Pre Orders | Pre Legal/support | Gate 3 effective | Classification |
|---|---|---|---|---|---|
| tr-TR | forced tr-TR | Activity tr-TR | normally tr-TR | tr-TR | Preserved |
| en-US | generic en | Activity en-US | normally en-US | en-US | Deliberate Home region correction |
| en-GB | generic en | Activity en-GB | normally en-GB | en-GB | Deliberate Home region correction |
| fr-FR | forced tr-TR | Activity fr-FR | normally fr-FR | default tr | Deliberate Orders/Legal fallback correction |
| debug en-XA | generic en | Activity en-XA | normally en-XA | en-XA | Deliberate unified pseudo behavior |
| debug ar-XB | forced tr-TR | Activity ar-XB | normally ar-XB | ar-XB | Deliberate unified pseudo behavior |

This is not a zero-observable-change claim. Every affected row is an explicit
acceptance scenario.

Expose first effective locale from LocalConfiguration and use it for Home
money, order date/money, and Gürbakır legal/support dates. Remove Home's
generic-English/Turkish mapping and Legal's Locale.getDefault dependency.
Tests compare output with platform formatting under resolved locale rather
than hardcoding ICU-sensitive punctuation/glyphs.

Shopify-returned currency codes remain authoritative. Market currency metadata
does not rewrite or convert returned money.

Search and Wishlist use environment.name and market.id. Gürbakır stays
DEVELOPMENT/TR and STAGING/TR; synthetic stays DEVELOPMENT/ZZ.
AddressTerritoryPolicy.supportedTerritoryCode equals market.countryCode.
Market, locale, territory, currency, environment, and Search normalization
remain separate. Gürbakır Search normalization stays exactly tr-TR under
every foreground locale, including English, unsupported locales, and
pseudolocales.

No Storefront in-context directive, currency conversion, market picker,
language picker, or runtime market engine is added.

## 6. Storefront domain and media security

Replace the global object with immutable StorefrontMediaPolicy constructed
from only the merchant-domain string, never token or full configuration.

Allowed origins:

- Exact validated Storefront host, only under exact /cdn/shop/ boundary.
- Exact cdn.shopify.com, with provider path/query opaque after safety checks.

This is egress policy, not tenant-ownership proof. Reject non-HTTPS,
non-default ports except explicit 443, userinfo, fragments,
uppercase/non-ASCII host ambiguity, trailing dots, wildcards, IP literals,
controls, backslashes, lookalike hosts, merchant paths outside /cdn/shop/,
dot segments, encoded traversal, encoded slash/backslash, boundary confusion,
and malformed input. Keep accepted queries. Missing/invalid merchant config is
deny-all.

Every media-producing mapper/gateway receives policy explicitly with no
global/default fallback, including catalog pagination.

Add StorefrontMediaClientFactory in storefront and
MobileCoreImageLoaderFactory in mobile-core. Both apps implement
SingletonImageLoader.Factory from their configuration.

The media client:

- Has policy interceptor first among application interceptors.
- Disables HTTP and SSL automatic redirects.
- Permits GET only.
- Has no cookies, authenticator, proxy authenticator, Storefront token, or
  customer credential.
- Validates initial URL before first proceed.
- Manually follows only 301, 302, 303, 307, and 308.
- Resolves relative Location, validates before each next proceed, and allows
  at most five hops.
- Rejects loops, missing/malformed Location, unsupported 3xx, downgrade, and
  disallowed targets.
- Closes intermediate response bodies.
- Strips Authorization, Proxy-Authorization, Cookie, and
  X-Shopify-Storefront-Access-Token on cross-host redirect.
- Throws generic URL-free/token-free IOException messages.

## 7. Protected persistence and migration safety

Constructors become:

~~~kotlin
AndroidKeystoreCartSessionStore(context, identity)
AndroidKeystoreCustomerSessionStore(context, identity)
~~~

They do not accept/normalize environment strings and contain no Gürbakır
preference or alias defaults. Hilt reads each named configuration field
directly rather than binding two unqualified identities.

Preserve AES/GCM format/key parameters, cart v2 writes, cart v1/v2 reads and
v1 quarantine, ownership codes, customer v2 wire format, random IV, payload
keys, and clear without deleting Keystore key.

Read both SharedPreferences values inside guarded recovery. Both absent means
empty. One missing, wrong type, malformed Base64, unsupported/trailing data,
key/cipher/authentication failure means corruption: clear both fields and
return null without logging values, tokens, identities, plaintext,
ciphertext, URLs, or raw exceptions.

Do not change gurbakir-local.db, gate2-synthetic-local.db, Room version 2,
entities/DAOs, schema JSON or identity hashes, WISHLIST_MIGRATION_1_2,
Search/Wishlist keys, or destructive policy. Add no Room abstraction or
migration.

Synthetic uses production encrypted cart/customer stores with exact synthetic
identities. Tests write, recreate/read, clear, assert exact synthetic
preference/alias use, and assert no Gürbakır preference, alias, or database
creation. Synthetic keeps no INTERNET permission, Firebase, or usable
credential and retains gate2-synthetic-local.db.

Preserve allowBackup=false, fullBackupContent=false, and API 31 cloud/device
transfer exclusions. Package tests parse effective manifest/decoded XML.
This is not universal OEM transfer or cross-device Keystore proof.

## 8. Compatibility and tests

Implementation uses test-first vertical slices.

Configuration/composition tests cover all new validation, invalid/duplicate
values and collisions, exact Gürbakır/synthetic values, unknown environment,
Home without market, named Hilt identities, and territory/market agreement.

Locale/format tests cover requested order, exact and same-language match,
region preservation, later-list match, fallback, dedupe, invalid policy,
en-CA, debug pseudolocales, release pseudo rejection, API 23 and API 24+
context branches, launched Activity context, an unoverridden shared string,
RTL, every Gürbakır matrix row, effective tags, and platform formatter
equality. Search tests prove exact tr-TR dotted/dotless-I output under every
foreground locale.

Market/Search/address tests preserve DEVELOPMENT/TR, STAGING/TR,
DEVELOPMENT/ZZ, byte-identical Gürbakır keys, en-CA synthetic behavior,
numeric/text postal semantics, generic E.164, and returned currency authority.

Media policy tables cover all accepted origins and rejected scheme, port,
userinfo, fragment, host confusion, trailing dot, wildcard, IP, canonical
ambiguity, merchant path, encoded traversal/slash, and query cases. Every
media producer has a discriminating test.

Network tests prove initial rejection reaches neither later interceptors nor
DNS/connect events; rejected redirect targets proceed downstream once and
close the body; accepted redirect codes, relative resolution, query retention,
cross-host header removal, loops, malformed/missing Location, and hop limit.
Production settings disable automatic redirects and put policy first. Add no
HTTP test dependency.

Frozen test-only Gate 2 secure-store fixtures own literal names, aliases,
versions, ownership codes, and codecs without production constants. Pin
literal cart v1, cart v2, and customer v2 plaintext vectors. Instrumentation
proves frozen writer to production reader and reverse, exact identities, v1
quarantine/ownership, and fail-closed clearing for missing half, wrong type,
bad Base64/ciphertext/key, unsupported version, and trailing bytes.

This proves identity/wire compatibility, not old UI execution. A two-APK
replacement exercise is supplemental and only on a disposable emulator with
matching signature, no uninstall/data clear, and genuine baseline-side store
probe. Never use installed user data.

Room evidence compares every schema JSON byte/SHA with execution base,
confirms unchanged version/entities/DAOs/migration/filenames/destructive
policy, and runs existing migration and persistence/composition tests. Add no
migration merely to prove none is needed.

## 9. Validators, CI, dependencies, and locks

Extend Test-RepositoryPortability.ps1 for exact shared production
merchant-domain, protected-identity, and concrete-market leakage over
Kotlin/Java and decoded qualified resources. Negative fixtures cover merchant
domains, Gürbakır identity defaults, market fallbacks, policy default/bypass,
plain/entity-encoded resources, and computed forms. Do not blanket-ban
Gürbakır, Turkish resources, namespaces, app values, fixtures, or history.

Extend Test-Gate2SyntheticPackage.ps1 for exact synthetic identity, no
INTERNET/Firebase, effective backup rules, English/default resources, exact
synthetic protected identities and Gürbakır identity absence from DEX, and no
Gürbakır DB/application-class identity. Runtime Hilt, Keystore readability,
redirect ordering, and process survival remain test responsibilities.

Add account/storefront Pixel 2 API 30 aosp-atd devices. The canonical
60-minute API 30 job runs:

~~~powershell
.\gradlew.bat :account:ciApi30DebugAndroidTest :storefront:ciApi30DebugAndroidTest :mobile-core:ciApi30DebugAndroidTest :app:ciApi30DevelopmentDebugAndroidTest :synthetic:ciApi30DebugAndroidTest "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"
~~~

Add a separate 45-minute synthetic Pixel 2 API 23 aosp job:

~~~powershell
.\gradlew.bat :synthetic:ciApi23DebugAndroidTest "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"
~~~

API 31 physical evidence is supplemental: non-production artifacts only,
synthetic seed, force-stop, separate verify, clear only synthetic data, inspect
locale/restoration/isolation/fatal logs, and report PASS, FAIL, or NOT RUN. It
never substitutes for API 23/30 CI.

Before documentation-bearing candidate HEAD run spotlessCheck, detekt, lint;
all module/app JVM suites; development/staging debug/release app builds;
mobile-core debug/release; synthetic debug/release/test APKs; validator
self-tests/full modes; API 23/30 lanes; dependency verification and secret
scan; optional approved read-only owned Storefront/media smoke, otherwise
NOT RUN.

Use existing dependencies. Keep app coil-compose. Remove direct app OkHttp
and Coil network fetcher after loader construction moves to mobile-core. Add
already-cataloged coil-compose to synthetic. Keep OkHttp in storefront and
Coil/OkHttp loader in mobile-core. Add no version, repository, plugin, catalog
entry, or new verification coordinate.

Generate locks through final tasks with --write-locks, never hand-edit.
Expected lock changes are app/synthetic configuration membership only. An
already-pinned transitive exposed by a new configuration is ordinary; any new
library/version/repository/plugin or unexplained checksum requires owner
review.

## 10. Non-recursive execution, documentation, and review lifecycle

1. Refresh Git, prove Gate 2 ancestry/tree, inspect drift, record base.
2. Create sibling branch/worktree
   codex/multibrand-gate-3-identity-domain-market-persistence.
3. Commit this approved plan as execution authority.
4. Implement configuration/locale/market/territory, media, persistence,
   synthetic, and validation slices with focused tests.
5. Run full local validation and reconcile locks.
6. Push and open a draft Gate 3 PR to obtain its permanent number.
7. Use preliminary PR CI/review to expose defects; correct transparently and
   rerun relevant local evidence.
8. Once implementation and then-knowable evidence are stable, write
   completion/current-state docs with actual history/decisions,
   defects/corrections, local validation, compatibility/security evidence,
   known preliminary CI/review findings, partial/non-proofs, PR number, last
   implementation checkpoint, and final-head/merge still pending.
9. Commit documentation; it becomes candidate final PR HEAD.
10. Push and require fresh CI plus final review on that HEAD.
11. If actionable defects arise, correct implementation/docs, reconcile
    handoff, commit transparently, and require fresh CI/review again.
12. Once a stable docs-bearing HEAD passes, create no commit just to write its
    SHA, run number, or review into the handoff.
13. Record final pre-merge SHA/checks/review in PR/GitHub metadata and this
    conversation.
14. Owner merges.
15. Verify merged-main ancestry and post-merge main CI.
16. Record merge SHA/post-merge result in PR/GitHub and this conversation,
    never through direct-to-main documentation.
17. Gate 3 closes only after owner merge and post-merge verification.
18. Stop before Gate 4.

Repository docs record only facts knowable before their own final commit.
Later CI/merge facts remain attached to immutable commit/PR events.

Commit sequence:

1. docs(multi-brand): approve Gate 3 implementation plan
2. refactor(config): separate locale market and persistence inputs
3. fix(storefront): enforce app-owned media origins before connect
4. refactor(persistence): inject protected store identities
5. test(multibrand): prove Gate 3 conformance and CI
6. docs(multi-brand): record Gate 3 implementation handoff

Final review corrections use descriptive fix or docs commits with required
handoff reconciliation. Do not amend, squash, force-push, merge, or delete
branches unless separately requested.

## 11. Completion handoff/current-state documentation

Write docs/multi-brand/GATE-3-COMPLETION-HANDOFF.md only after implementation
and all pre-documentation evidence are known. It records:

- Objective/boundary and planning/execution base.
- Final ownership/data flow and actual changes/reasons.
- Confirmed/refined/disproved assumptions and rejected alternatives.
- Defects/corrections known before its commit and plan deviations.
- Exact local commands/results and known preliminary CI/review.
- Domain/media, locale/market/territory, persistence/migration, Gürbakır,
  synthetic, and physical evidence.
- Dependency/lock changes, partial/non-proofs, rollback/recovery.
- PR number and last implementation checkpoint.
- The rule that final HEAD SHA/checks live in PR closeout metadata.
- Final PR CI/review, owner merge, and post-merge verification as pending.

It must not claim Gate 3 closed. Current-state references change only where
source facts changed and distinguish source completion from process closure.
Append a dated correction to docs/phase3/P3-01-HANDOFF.md explaining that
historical network-interceptor wording is superseded because network
interceptors execute after connection. Preserve the original history. Do not
rewrite Gate 0/1/2, add an ADR, or create post-merge documentation solely for
future facts.

## 12. Expected conformance result

These are closure-state classifications. The handoff records pre-final
evidence; they become final claims only after stable final HEAD CI/review and
post-merge verification.

| Concern | Classification | Meaning |
|---|---|---|
| App-owned media policy | PROVEN IN GATE 3 | Domain, all mappers, composition, pre-connect/redirect tests |
| Gürbakır protected identity/wire | PROVEN IN GATE 3 | Exact identities and independent forward/back fixtures |
| No Room migration/partition drift | PROVEN IN GATE 3 | Schema bytes, migration, DB/partition assertions |
| Synthetic encrypted persistence | PROVEN IN GATE 3 | Separate real stores, recreation/clear, no Gürbakır state |
| Foreground locale/format consistency | PROVEN IN GATE 3 | Region, fallback, pseudo, matrix |
| Zero observable formatting change | NOT APPLICABLE | Inconsistencies intentionally corrected |
| Fixed market/locale variation | PROVEN IN GATE 3 | TR/TR/TRY vs ZZ/ZZ/XTS; tr/en vs en-CA |
| Gürbakır Search compatibility | PROVEN IN GATE 3 | tr-TR exact under all foreground locales |
| Postal input variation | PROVEN IN GATE 3 | Numeric vs text semantics |
| Process death | PARTIALLY PROVEN IN GATE 3 | Physical if available; CI recreation separate |
| Live media-host completeness | PARTIALLY PROVEN IN GATE 3 | Narrow policy/optional smoke, no permanent guarantee |
| General international support | PARTIALLY PROVEN IN GATE 3 | Fixed inputs, not global engine |
| OEM transfer | PARTIALLY PROVEN IN GATE 3 | Package rules, not universal behavior |
| Capability/navigation | DEFERRED TO GATE 4 | No optional topology |
| Generic provider isolation | DEFERRED TO GATE 5 | Firebase composition unchanged |
| In-context, FX, runtime market | DEFERRED BEYOND GATE 3 | Separate decision |
| Navigation/Home/onboarding | DEFERRED BEYOND GATE 3 | No provisioning expansion |
| Signing/Play/release/P3-16 | DEFERRED BEYOND GATE 3 | Not started |
| Real synthetic credentials | NOT APPLICABLE | Synthetic remains isolated |

## 13. Execution-time unknowns

| Unknown | Evidence | Success | Ordinary correction | Owner threshold |
|---|---|---|---|---|
| Remote main | Fetch/ancestry/tree/diff | Gate 2, no conflict | Adapt wiring/tests | Ownership/migration/identity/gate conflict |
| Locks | Final resolution | Existing coordinates/explained membership | Reconcile pinned scopes | New dependency/version/repo/plugin |
| Image hosts | Fixtures/optional response | Merchant or cdn.shopify.com | Exact documented provider with controls | Arbitrary allowlist/weaker security |
| API 23/30 | GMD logs | Required lanes pass | Test/config/timeout fix | Platform/min-SDK architecture |
| API 31 | Fresh ADB | Supplemental pass | NOT RUN | Never weaken CI |
| OEM transfer | Manifest/XML | Packaged rules | Preserve exclusions | Universal claim prohibited |
| Owned Storefront config | Ignore/existence/redacted smoke | Optional pass | NOT RUN | Never expose secrets |

## 14. Completion and rollback

Gate 3 is CLOSED only when base compatibility, all contracts, locale matrix,
exact Search/protected/wire compatibility, unchanged Room evidence, pre-hop
media enforcement, all locale/market/territory/synthetic/validator/build/API
23/API 30 lanes, dependency boundary, reconciled handoff, fresh final-HEAD
CI/review, owner merge, post-merge ancestry/CI, and external closeout evidence
succeed, with no Gate 4, Gate 5, or P3-16 work begun.

Before merge, rollback is PR closure, branch abandonment, or normal commit
reversion. After merge use normal revert/corrective commit, never destructive
reset/history rewrite. Never create direct-to-main documentation only to
record merge/CI identifiers.

Because Gürbakır database, names, aliases, normalization, and wire formats do
not change, Gate 2 readers remain rollback-compatible. Synthetic-only files
and aliases may remain inert or be cleared only in its disposable sandbox.
Any required Room migration, identity rename, destructive fallback, or
unrecoverable state transformation invalidates this authority and returns to
owner review.

## 15. Final adversarial review

The approved plan was checked for non-recursive evidence; no future fact in a
predecessor commit; one locale resolver/format/test contract; en-US/en-GB
preservation; fr-FR fallback and debug pseudolocale corrections; exact
foreground-independent tr-TR Search normalization; exact protected
identity/wire compatibility; no Room migration; fail-closed media/redirect
handling; unchanged Gate 4/Gate 5/P3-16 boundaries; discriminating tests;
factual validator counts; and agreement among execution, documentation,
conformance, completion, and rollback.

Only the bounded Section 13 execution states were unknown at approval. Gate 4,
Gate 5, and P3-16 are not authorized by this plan.
