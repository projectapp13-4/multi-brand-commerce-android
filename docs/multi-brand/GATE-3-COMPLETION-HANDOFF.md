# Gate 3 Identity / Domain / Market / Persistence Completion Handoff

> **Implementation status:** COMPLETE IN SOURCE at last implementation checkpoint `ee476adb8204c81cb1c24420180297f6fe7ad0ea`.
>
> **Closure status:** OPEN. This pre-merge handoff is part of the candidate documentation-bearing PR HEAD. Fresh CI/final review on that HEAD, owner merge, and post-merge `main` ancestry/CI verification are subsequent events and remain pending here.
>
> **Scope stop:** Gate 4, Gate 5, a real second merchant, production onboarding, and P3-16 are not started or authorized by this handoff.
>
> **Observed through:** 2026-09-09 pre-documentation closeout.

## Executive outcome

Gate 3 implemented the approved fixed-input boundary without reopening the accepted application-module architecture. `AppConfiguration` now owns focused localization, market, and protected-persistence inputs; application composition owns the fixed persisted Search-normalization and territory-input policies; and the existing Storefront domain is the sole merchant input to a reusable fail-closed media policy.

Gürbakır's migration-sensitive state remains exact: application IDs, OAuth/App Links, Firebase composition, `gurbakir-local.db`, Room version/schema/migration chain, `DEVELOPMENT/TR` and `STAGING/TR` partitions, `tr-TR` Search normalization, SharedPreferences names, Android Keystore aliases, encrypted payload keys, and wire formats are unchanged. Gate 3 deliberately does change observable formatting in the documented locale cases by replacing three inconsistent rules with one application-owned foreground locale.

The non-production synthetic application remains Firebase-free, credential-free and without INTERNET permission. Its process-memory cart/customer stores were replaced with separate Android Keystore-backed encrypted stores using synthetic-only identities. It still does not represent a real merchant or production-ready application.

## Authority, execution base, and repository line

| Item | Gate 3 value |
|---|---|
| Approved plan | [Gate 3 implementation plan](plans/GATE-3-IDENTITY-DOMAIN-MARKET-PERSISTENCE-IMPLEMENTATION-PLAN.md) |
| Refreshed execution base | `417cd18165561a6bfa555060dc02ccf37e3ea743` (`origin/main`) |
| Audited Gate 2 branch head | `30bdbebee1aa26c9ef825ed22a76eb63b4899212` |
| Branch | `codex/multibrand-gate-3-identity-domain-market-persistence` |
| Pull request | #15 (private historical archive) |
| Last implementation checkpoint before this documentation | `ee476adb8204c81cb1c24420180297f6fe7ad0ea` |
| Merge state at documentation closeout | NOT MERGED; owner merge is a later event |

At execution start a fresh fetch resolved `origin/main` to `417cd181…`. That commit contained the accepted Gate 2 history; comparison with the audited Gate 2 head found no conflicting ownership, identity, migration, security or gate-boundary change. The worktree was created as a clean sibling from refreshed `origin/main`. A later fresh remote check found `main` unchanged while implementation continued.

The active original checkout was not rewritten. There was no amend, squash, force push, merge, branch deletion, destructive reset or history rewrite.

## Planning-evidence reconciliation

The plan's final correction pass withdrew an earlier unsubstantiated portability claim of `17/17`:

- the stale active-checkout script at `a6f3b3f…`, SHA-256 `35D82A66A3FE60D17428FA726EFDC6D7FC67D1AFFBB72E7E430F70650662E01B`, did not support `-SelfTest` and failed before fixtures;
- no retained command/output established a separate 17-fixture run, so that claim is `NOT RUN / NOT SUBSTANTIATED`;
- the final Gate 2 script in the audited worktree, SHA-256 `BC5D338965D10731DBCD4AC4FC816E18622F83F6B657345983B698CD8BEC96A3`, was freshly run during planning and passed 19 fixtures;
- the Gate 2 synthetic-package parser self-test passed 6 fixtures.

Those results proved only the then-existing validators. The implemented Gate 3 validators have their own fresh results below.

## Final ownership and data flow

### Focused configuration

`AppConfiguration` now contains:

- `LocalizationPolicy(defaultLocaleTag, supportedLocaleTags)`;
- `MarketConfiguration(id, countryCode, currencyCode)`;
- `ProtectedPersistenceConfiguration(cart, customerSession)`, each containing an exact preference name and Keystore alias.

`BrandConfiguration` no longer owns locale metadata. `HomeConfiguration` no longer owns market metadata. Shared stores neither derive nor normalize environment-specific identities. `BuildConfigurationSource.current` is one immutable app configuration used by both Hilt and `GurbakirApplication`; unknown environment values fail closed.

The final fixed inputs are:

| Input | Gürbakır | Synthetic |
|---|---|---|
| Localization | default `tr`; ordered supported `tr`, `en` | default/supported `en-CA` |
| Market | `TR / TR / TRY` | `ZZ / ZZ / XTS` |
| Persisted Search normalization | `tr-TR` | `en-CA` |
| Postal input mode | `NUMERIC` | `TEXT` |
| Room file | `gurbakir-local.db` | `gate2-synthetic-local.db` |
| Cart preferences | `gurbakir_secure_cart_development` / `gurbakir_secure_cart_staging` | `gate2_synthetic_secure_cart_development` |
| Cart alias | `gurbakir.cart.development.v1` / `gurbakir.cart.staging.v1` | `gate2.synthetic.cart.development.v1` |
| Customer preferences | `gurbakir_secure_customer_session_development` / `gurbakir_secure_customer_session_staging` | `gate2_synthetic_secure_customer_session_development` |
| Customer alias | `gurbakir.customer.session.development.v1` / `gurbakir.customer.session.staging.v1` | `gate2.synthetic.customer.session.development.v1` |
| Merchant media host | existing Storefront domain, currently `gurbakir.com` | `storefront.gate2.invalid` |

Locale, market, territory, currency, environment and persisted Search normalization remain separate concepts. Shopify-returned currency codes remain authoritative; fixed market metadata does not convert or rewrite returned money.

### Foreground locale and Gürbakır compatibility

The pure resolver processes the requested device-locale list in order. It preserves the full canonical requested tag for an exact supported match or a supported-language match, continues to later requested locales when unsupported, and otherwise falls back to the configured default. Debug pseudolocales `en-XA` and `ar-XB` are preserved only when explicitly enabled. The resolved default is appended as a deduplicated fallback. API 23 applies the first locale with `Configuration.setLocale`; API 24+ applies the list with `Configuration.setLocales`; both return a localized context through `createConfigurationContext`.

Gürbakır `MainActivity`, debug evidence activities and synthetic `MainActivity` wrap their base context. The `Application` is not wrapped, `Locale.setDefault` is not used by production code, and there is no language picker, persisted locale selection, AppCompat/`LocaleManager`, or runtime market engine.

The accepted pre/post matrix is:

| Requested locale | Pre-Gate-3 Home | Pre-Gate-3 Orders | Pre-Gate-3 Legal/support | Gate 3 effective locale for all three | Classification |
|---|---|---|---|---|---|
| `tr-TR` | forced `tr-TR` | Activity `tr-TR` | process default, normally `tr-TR` | `tr-TR` | existing behavior preserved |
| `en-US` | generic English | Activity `en-US` | process default, normally `en-US` | `en-US` | deliberate Home region-preservation correction |
| `en-GB` | generic English | Activity `en-GB` | process default, normally `en-GB` | `en-GB` | deliberate Home region-preservation correction |
| unsupported `fr-FR` | forced `tr-TR` | Activity `fr-FR` | process default, normally `fr-FR` | configured default `tr` | deliberate Orders/Legal fallback correction; Home stays Turkish-language |
| debug `en-XA` | generic English | Activity `en-XA` | process default, normally `en-XA` | `en-XA` | deliberate unified pseudolocale behavior |
| debug `ar-XB` | forced `tr-TR` | Activity `ar-XB` | process default, normally `ar-XB` | `ar-XB` | deliberate unified pseudolocale/RTL behavior |

Home money, customer-order date/money and Gürbakır legal/support date formatting now use the first effective Activity locale. Tests compare output with the platform formatter under that locale instead of pinning ICU punctuation, spacing or glyphs. This is explicitly not a zero-observable-change claim.

Search normalization remains fixed at `tr-TR` for every Gürbakır foreground locale, including English, unsupported locales and pseudolocales. NFKC, trim, whitespace collapse and lowercase behavior are preserved, so existing dotted/dotless-I keys remain addressable. Search and Wishlist partitions are derived from `AppConfiguration.environment.name` plus `AppConfiguration.market.id`, retaining exactly `DEVELOPMENT/TR`, `STAGING/TR` and synthetic `DEVELOPMENT/ZZ`.

`AddressTerritoryPolicy` now requires an explicit `NUMERIC` or `TEXT` postal input mode. Gürbakır stays numeric, synthetic is text, and the market country must match the supported territory. Generic E.164 validation is unchanged; Gürbakır copy now presents `+90` as an example in the unspaced format accepted by that validator, not as an exclusive requirement.

## Storefront media security

The former global policy became immutable `StorefrontMediaPolicy(merchantDomain)`. It receives only the domain, never a token or full configuration. Missing/invalid merchant configuration creates a deny-all policy without crashing startup and does not independently authorize the Shopify provider host.

Allowed origins remain exactly:

- the validated app Storefront host under the exact `/cdn/shop/` path boundary; and
- exact `cdn.shopify.com`, whose provider-owned path/query stays opaque after URL-safety checks.

The policy rejects non-HTTPS, non-443 ports, user information, fragments, ambiguous/non-canonical hosts, trailing dots, wildcards, IP literals, controls/backslashes, lookalike hosts, merchant paths outside the boundary, dot segments, layered or single encoded traversal/slashes, and malformed input. Every media-producing Home, Catalog/pagination, Search, Product and Cart mapper receives the policy explicitly; there is no static/default bypass.

`StorefrontMediaClientFactory` creates a credential-free client whose policy interceptor is first among application interceptors. Automatic HTTP and SSL redirects are disabled. The interceptor permits only GET, validates before the first downstream call, manually handles only 301/302/303/307/308, resolves relative locations, validates before each next call, closes intermediate bodies, limits redirects to five, detects loops, and rejects missing/malformed locations, unsupported 3xx, downgrade or disallowed targets. Cross-host redirects remove authorization, proxy authorization, cookies and the Storefront token header. Failures use generic URL/token-free `IOException` messages.

`MobileCoreImageLoaderFactory` composes this client for both applications through `SingletonImageLoader.Factory`. Tests prove equivalent policy behavior for Hilt gateway and application image-loader composition; no referential identity claim is made.

## Protected persistence and Room compatibility

`AndroidKeystoreCartSessionStore(context, identity)` and `AndroidKeystoreCustomerSessionStore(context, identity)` consume exact app-owned identities. They retain AES/GCM parameters, random IVs, preference payload keys, cart v2 writes, cart v1/v2 reads and v1 quarantine, ownership codes, customer-session v2 format, and `clear()` without deleting the Keystore key.

Wrong-type and partial-record recovery is now fail-closed. Both preference values are read inside guarded recovery; both absent means empty, while one missing half, wrong type, malformed Base64, unsupported/trailing data, cipher/key/authentication failure or corrupt ciphertext clears both payload fields and returns `null`. No payload, token, identity, URL, ciphertext/plaintext or raw exception is logged.

Independent frozen test-only Gate 2 codecs own literal identities, versions, ownership codes and byte encodings without importing production constants/codecs. Instrumentation covers frozen writer to Gate 3 reader, Gate 3 writer to frozen reader, pinned plaintext vectors, exact development/staging identities, v1 quarantine, ownership cases and all declared corruption paths.

Room did not change. These schema files remain byte-identical to the execution base:

| Schema | SHA-256 |
|---|---|
| `LocalCommerceDatabase/1.json` | `867B64F0CD0CB96BD8FFB0C2C3F07E20E8AC0B0195AAB5E3906E9D43480B3D4B` |
| `LocalCommerceDatabase/2.json` | `61DDCED2A6556F34091780B60483CC6DAEC423DD6DDEF62C4C290851897230F6` |
| `SearchHistoryDatabase/1.json` | `DFDFA4DC38200901E22BEAA89E8CB745F25AFBB15EDB011EE182B6C6F0090627` |
| `SearchHistoryDatabase/2.json` | `61DDCED2A6556F34091780B60483CC6DAEC423DD6DDEF62C4C290851897230F6` |

`gurbakir-local.db`, `gate2-synthetic-local.db`, Room version 2, entities, DAOs, identity hashes, `WISHLIST_MIGRATION_1_2`, table keys and destructive-migration policy remain unchanged. No Room migration was added because none is required.

## Synthetic conformance

Synthetic now binds the production encrypted store implementations with its exact separate identities. Tests write fake cart/customer state, recreate store instances, read it, clear it, and verify absence. Instrumentation/package validation confirms the synthetic preference files and aliases, `gate2-synthetic-local.db`, and the absence of exact Gürbakır preference names, aliases, database and Application identity in the synthetic UID/package.

The effective manifest retains `allowBackup="false"`, `fullBackupContent="false"`, no INTERNET permission, no Firebase registration/implementation, and API 31 cloud/device-transfer exclusions for shared preferences, databases and files. The validator binds decoded rules to the resource ID referenced by the effective manifest rather than trusting a fixed filename. These checks prove package controls, not universal OEM behavior or cross-device Keystore portability.

Synthetic shared UI now resolves through its `en-CA` default on unsupported-language devices and preserves a supported requested English region such as `en-US`. It remains non-production and does not prove live media fetching.

## Implementation findings, failures, and corrections

Failed attempts are retained here and are not counted as PASS.

| Finding/failure | Cause | Correction/evidence |
|---|---|---|
| Postal input mode initially reverted after address state refresh | Reconstructed UI state omitted the injected finite mode | `e917567` retained the mode through state transitions and added focused tests. |
| Initial media path parser accepted layered encoding | One decoding pass did not expose every encoded traversal/slash case | `f7995af` rejects layered ambiguity; focused policy tables pass. |
| First dependency-lock result omitted a Firebase release membership | The final requested configuration set had not resolved that already-pinned transitive | `3e1e125` regenerated locks through the full task set and restored `kotlin-stdlib-common:2.3.20`; no new library/version was added. |
| Preliminary CodeRabbit review posted five actionable findings | Phone example disagreed with E.164 syntax; one Search test did not vary process locale; backup fixtures did not discriminate transfer scope/domain; the market regex missed a spaced literal; raw strings bypassed two guards | `a015f4a` corrected all five, added adversarial fixtures, and resolved every thread. |
| Synthetic recreation did not prove a separate process boundary | Recreating store instances in one process is weaker than process restart | `f17d498` added gated seed/verify entry points; supplemental API 31 seed, force-stop and separately invoked verify passed. |
| Package backup validation was not bound to the manifest-selected XML | A correct fixed resource could mask a manifest reference to another file | `43c8f19` resolves the exact compiled resource ID and includes mismatch/ambiguity fixtures. |
| CI run #76 passed the 754-task build then failed package self-tests | A fixture assumed LF-only text; Linux input used a different final-newline shape | `a9777f0` handles LF/CRLF explicitly; both newline forms remain discriminating. |
| Adversarial self-review found the compatibility matrix test still reused one process locale | An earlier review reply overstated what was committed | `2fcc384` actually varies/restores `Locale.getDefault()` per matrix row and reconstructs the fixed Search policy in each row. |
| CI run #78 API 30 reached tests and failed one pseudolocale assertion | App debug generated pseudolocale resources, but the shared test host and synthetic debug APK did not | `8385ff4` enables debug pseudolocale generation for those two modules. AAPT2 confirms `en-rXA` and `ar-rXB` in both relevant APKs. |
| CI run #78 API 23 never executed tests | The legacy 32-bit API 23 AOSP emulator booted/adbd started but remained `offline` to the host until setup timeout | `8385ff4` retains Pixel 2/API 23/AOSP and selects its 64-bit image with `require64Bit=true`; fresh CI is the runtime authority. |
| CI run #79 API 23 remained offline with the x86_64 image | Architecture was no longer an explanation; AGP 9.2.1's launcher adds `-delay-adb`, and the legacy guest never became host-visible before the setup timeout | `cf25532` installs an API-23-job-only wrapper that removes exactly `-delay-adb` and delegates to the same official emulator. AGP still gates on `sys.boot_completed`, PackageManager, snapshot and test readiness. |
| CI run #80 reached API 23 instrumentation and found one failing resource test | The transport wrapper worked, but the synthetic test helper unconditionally referenced API 24's `android.os.LocaleList` on API 23 | Seven ordinary tests passed, one failed with `NoClassDefFoundError`, and two argument-gated process-proof entry points were skipped as designed. `ee476ad` uses `Configuration.setLocale` below API 24 and `setLocales` on API 24+. |
| Three later CodeRabbit observations were non-actionable | They proposed a cosmetic local variable rename, unrelated helper extraction, and deriving independent identity assertions from the configuration under test | Each was verified and rejected with rationale; no correctness/security issue remained and independent literals were intentionally retained. |

No finding required a Room migration, identity change, weakened media control, new dependency/version/repository/plugin, runtime switch, Gate 4/5 architecture or P3-16 work.

## Fresh local validation

The full application evidence below was run at `8385ff4`; `cf25532` changes only the API 23 CI emulator wrapper/workflow, and `ee476ad` changes only the API 23-compatible synthetic resource-test helper. The two later checkpoints received the targeted checks noted below:

| Validation | Result |
|---|---|
| `spotlessCheck detekt lint` | PASS; 327 tasks, 11m29s |
| Full foundation/account/checkout/storefront/firebase/mobile-core/app/synthetic JVM suites | PASS; 361 tests, 0 failures/errors, 2 conditional Storefront skips |
| `:mobile-core` debug/release, Gürbakır development/staging debug/release, synthetic debug/release and synthetic AndroidTest builds | PASS; combined full test/build invocation 739 tasks, 7m32s |
| Repository portability self-tests | PASS; 28/28 fixtures |
| Repository portability full mode with `-RequireCleanWorktree` | PASS; 43/43 checks, including the clean-worktree assertion |
| Synthetic package parser self-tests | PASS; 14/14 fixtures |
| Synthetic debug/release package validation | PASS; 53/53 checks |
| Room schema comparison | PASS; all four tracked JSON bytes and SHA-256 values unchanged from `417cd181…` |
| Local Gitleaks scan | PASS; no finding at the reviewed code-only state |
| Documentation reconciliation | PASS; root `spotlessCheck` (6 tasks), strict UTF-8 decoding for all nine changed documents, `git diff --check`, and focused relative-link validation |
| API 23 wrapper/workflow correction | Root `spotlessCheck` PASS (6 tasks) and `git diff --check` PASS; CI #80 then proved host attachment, installation and actual API 23 instrumentation execution |
| API 23 resource-test helper correction | Root `spotlessCheck` plus `:synthetic:assembleDebugAndroidTest` PASS; 162 tasks, 2m13s. Fresh CI on `ee476ad` is required for API 23 runtime proof |

The two Storefront JVM skips are existing conditional owned-configuration tests. They are not converted into live Storefront proof.

### Supplemental API 31 device evidence

On the dedicated Android 12/API 31 INFINIX X6817 test device, the synthetic debug/test packages ran with the device requested locale `tr-TR` and resolved the effective app locale to `en-CA`. Hilt composition returned the expected application state. A gated fake-data seed returned success; after force-stop, a separately invoked verification restored the exact encrypted cart and customer session and found only synthetic protected identities. Clear verification passed. Two no-argument process-class assumptions were deliberately skipped because those entry points require instrumentation arguments. A bounded 1,448-line log window contained no Android Runtime fatal entry. Only synthetic app/test data was cleared afterward.

This is supplemental process-stop/recreation evidence. It is not an OEM backup/transfer, device reboot, real credential, live merchant, or production proof.

## Preliminary PR CI before this documentation commit

| Run | Code checkpoint | Result and disposition |
|---|---|---|
| #75 (private historical archive) | `08c3fba…` | FAIL: exposed missing Firebase release lock membership; corrected in `3e1e125`. |
| #76 (private historical archive) | after lock correction | FAIL after the full 754-task package build passed: Linux package self-test exposed the newline assumption; corrected in `a9777f0`. |
| #77 (private historical archive) | `a9777f0…` | CANCELED as superseded; it is not PASS evidence. |
| #78 (private historical archive) | `2fcc384…` | FAIL: validate passed; API 30 executed 101 tests and found one pseudolocale packaging failure; API 23 executed no tests because the emulator stayed offline. Corrected in `8385ff4`. |
| #79 (private historical archive) | `8385ff471844489954c890508a829abc75ecb123` | FAIL: validate and all five API 30 module targets passed; API 23 again executed no test because the x86_64 guest stayed offline. Corrected in `cf25532`. |
| #80 (private historical archive) | `cf2553227b5b0f10efa03eb35f556782ffd98431` | FAIL: validate passed and API 23 finally executed instrumentation; seven ordinary tests passed, one resource-fallback test failed because its helper referenced API 24 `LocaleList`, and two argument-gated proof entry points were skipped. API 30 was canceled by the corrective push. Corrected in `ee476ad`. |
| #81 (private historical archive) | `ee476adb8204c81cb1c24420180297f6fe7ad0ea` | PASS: validate passed; API 30 discovered 148 tests across all five module targets with 146 passes, two argument-gated skips and zero failures; API 23 discovered ten synthetic tests with eight passes, the same two gated skips and zero failures. |

Only a completed fresh run on the exact checkpoint is eligible code-only CI evidence. The documentation commit created after this table must receive its own entirely fresh CI; no preliminary run can close Gate 3 because it necessarily predates this handoff.

## Review and security evidence before this documentation commit

CodeRabbit's initial full review covered the base-to-implementation diff and produced five actionable findings; all were independently verified, corrected in `a015f4a`, replied to, and resolved. Its outside-diff manifest-binding finding was corrected in `43c8f19`. Later incremental review found no actionable defect in the process-persistence, cross-platform validator or locale-test corrections. At pre-documentation closeout the PR contains five review threads and zero unresolved threads.

An adversarial internal review additionally caught the process-locale test overclaim and the missing generated pseudolocale resources before this handoff. Both received transparent commits and fresh tests/CI rather than being waived.

The focused immutable security-diff scan covered 98 changed production/build/workflow/script paths at checkpoint `c20a296` and sealed snapshot `codex-security-snapshot/v1:sha256:5d0b79103009f762726bc771bd4f54881da02b6147eeeea0915f0e6203226f90`; it reported zero findings across six security surfaces. Later narrow review/CI corrections were separately inspected. Secret scanning remained clean. This evidence supports the pre-documentation code state but does not replace final review/security checks on the documentation-bearing HEAD.

## Dependencies and locks

No version, repository, Gradle plugin, catalog entry or verification-metadata coordinate was added. Existing dependencies were rearranged to the owning modules:

- direct OkHttp and Coil network-fetcher dependencies were removed from `:app` after loader construction moved behind `:mobile-core`/`:storefront`;
- already-cataloged `coil-compose` was added directly to `:synthetic`;
- existing OkHttp remains in `:storefront`; existing Coil/OkHttp loader support remains in `:mobile-core`.

Locks were generated through the final build/test task set, never hand-edited. App lock coordinate keys are unchanged. Synthetic gained 145 module-local coordinate keys and mobile-core gained 20 configuration-visible keys; every added coordinate already existed in the execution-base repository lock universe. The version catalog, repository/settings declarations and dependency-verification metadata are unchanged.

## Compatibility and conformance result

These classifications describe the implemented source plus the pre-documentation evidence above. They become final Gate 3 closure claims only after the stable documentation-bearing HEAD passes fresh CI/final review and the owner merge/post-merge checks succeed.

| Concern | Classification | Evidence boundary |
|---|---|---|
| App-owned media policy used by mapping/loading | **PROVEN IN GATE 3** | Exact input, every mapper path, client composition and pre-connect/manual-redirect chain tests |
| Gürbakır protected-store identity/wire compatibility | **PROVEN IN GATE 3** | Exact literals plus independent forward/backward frozen fixtures and corruption recovery |
| No Gürbakır Room migration/partition drift | **PROVEN IN GATE 3** | Byte-identical schemas, exact filenames/version/migration/partitions and persistence tests |
| Distinct synthetic encrypted persistence | **PROVEN IN GATE 3** | Separate identities, recreation/clear/process-stop evidence and no Gürbakır state |
| Foreground locale/resource/formatting consistency | **PROVEN IN GATE 3** | Region preservation, default fallback, pseudolocales and every compatibility-matrix row |
| Zero observable Gürbakır formatting change | **NOT APPLICABLE** | Gate 3 intentionally corrects the documented inconsistent cases |
| Fixed app market/locale variation | **PROVEN IN GATE 3** | `TR/TR/TRY` versus `ZZ/ZZ/XTS`; `tr,en` versus `en-CA` |
| Gürbakır Search-key compatibility | **PROVEN IN GATE 3** | Exact `tr-TR` normalization under every foreground/process locale row |
| Territory-specific postal input | **PROVEN IN GATE 3** | Numeric/text policy, retained state and UI keyboard semantics |
| Process-death behavior | **PARTIALLY PROVEN IN GATE 3** | CI recreation plus physical force-stop/independent verify; not reboot/OS-kill proof |
| Live Shopify media-host completeness | **PARTIALLY PROVEN IN GATE 3** | Narrow policy and fixtures; live smoke was not run |
| General international market/localization/address support | **PARTIALLY PROVEN IN GATE 3** | Fixed inputs only, not a global/runtime engine |
| OEM backup/device-transfer behavior | **PARTIALLY PROVEN IN GATE 3** | Effective manifest/decoded package rules; universal OEM behavior unclaimable |
| Capability-driven navigation | **DEFERRED TO GATE 4** | Fixed shared graph unchanged |
| Generic Firebase/provider isolation | **DEFERRED TO GATE 5** | Existing Gürbakır provider composition unchanged |
| Storefront `@inContext`, FX, runtime market/language switching | **DEFERRED BEYOND GATE 3** | No product/provider expansion |
| Production registration/signing/Play/release/P3-16 | **DEFERRED BEYOND GATE 3** | Explicitly not started |
| Real credentials/production registration for synthetic | **NOT APPLICABLE** | Synthetic remains isolated/non-production |

## Explicit partial proofs and non-proofs

- Optional owned Storefront/media smoke: **NOT RUN**; no approved live non-production configuration was used for this execution.
- Two-APK replacement exercise: **NOT RUN**; no installed user application/data was touched. Independent frozen codecs provide the compatibility proof used by Gate 3.
- Full local API 23/API 30 managed-device lanes: **NOT RUN locally** because the required images were not installed; CI is the canonical device authority.
- Physical API 31 evidence is supplemental and does not replace API 23/API 30 CI.
- No live Storefront, Customer Account, Checkout Kit, Firebase, payment, remote deletion, production signing, Play, release or OEM-transfer behavior is claimed.
- P3-13 remote deletion acknowledgement/SLA/retention execution remains externally unverified. P3-16 remains not started.

## Actual commit history before documentation

The approved six-role structure expanded transparently because review and CI found correctable defects. No commit was amended or hidden:

1. `d30b7b2` — `docs(multi-brand): approve Gate 3 implementation plan`
2. `4177e9d` — `refactor(config): separate locale market and persistence inputs`
3. `61874cb` — `fix(storefront): enforce app-owned media origins before connect`
4. `9fd4d30` — `refactor(persistence): inject protected store identities`
5. `e917567` — `fix(address): retain injected postal input mode`
6. `f7995af` — `fix(storefront): reject layered media path encoding`
7. `c20a296` — `test(multibrand): prove Gate 3 conformance and CI`
8. `08c3fba` — `build(deps): reconcile Gate 3 instrumentation locks`
9. `3e1e125` — `fix(build): restore Firebase release lock membership`
10. `a015f4a` — `fix(validation): resolve preliminary review findings`
11. `f17d498` — `test(synthetic): prove process-restart persistence`
12. `43c8f19` — `fix(validation): bind backup rules to manifest reference`
13. `a9777f0` — `fix(validation): make synthetic fixtures cross-platform`
14. `2fcc384` — `test(localization): vary process locale in compatibility matrix`
15. `8385ff4` — `fix(testing): package pseudolocales for locale CI`
16. `cf25532` — `fix(ci): keep API 23 managed-device adb visible`
17. `ee476ad` — `fix(testing): support API 23 locale resource helper`
18. documentation commit containing this handoff/current-state reconciliation — `docs(multi-brand): record Gate 3 implementation handoff`

The extra commits are evidence-bearing corrections within the approved architecture, not Gate 4/5/P3-16 expansion.

## Non-recursive final PR, merge, and closeout lifecycle

This handoff records only facts knowable before its own commit: implementation history, decisions, corrections, local evidence, preliminary CI/review, partial proofs and non-proofs, PR number, and the last implementation checkpoint.

After this handoff/current-state documentation is committed and pushed:

1. that documentation-bearing commit becomes the candidate final PR HEAD;
2. the exact HEAD must receive fresh GitHub Actions CI and final review;
3. an actionable failure requires a normal corrective commit that also reconciles this handoff, followed by fresh CI/review on the new HEAD;
4. once one stable final HEAD passes, no repository commit is created merely to write its SHA, run number or review result into this file;
5. final-head/check/review evidence is recorded in immutable PR/GitHub closeout metadata and the same-conversation execution closeout;
6. the owner may then merge;
7. merge SHA, ancestry and post-merge `main` CI are likewise recorded in PR/GitHub and conversation closeout, never through a direct-to-main documentation-only commit;
8. Gate 3 becomes CLOSED only after owner merge and post-merge verification succeed.

At this document's closeout, final documentation-bearing PR CI/review, owner merge and post-merge `main` verification are all **PENDING**. The handoff must not be read as claiming otherwise.

## Rollback and recovery

Before merge, rollback is PR closure or branch abandonment; `main` remains untouched. After an owner merge, use a normal revert or corrective commit, never a destructive reset/history rewrite. Do not create a direct-to-main documentation commit solely to record merge or CI identifiers.

Because Gürbakır database names, Room schemas/migrations, partitions, Search normalization, protected identities and wire formats remain exact, the Gate 2 reader is the rollback compatibility target. Synthetic-only preference files/aliases may remain inert or be cleared only from its disposable sandbox.

## Final handoff rule

The next authorized action is to validate the documentation-bearing PR HEAD, complete final review, and stop for owner merge. Do not start Gate 4, Gate 5, a real brand, production onboarding or P3-16. Gate 3 remains open until the owner-controlled merge and post-merge `main` checks recorded outside this document are complete.
