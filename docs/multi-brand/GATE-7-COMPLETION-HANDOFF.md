# Multi-Brand Gate 7 — Bounded Shopify-Driven Home Content Completion Handoff

**Status:** SOURCE IMPLEMENTED / CONFIGURED ACCEPTANCE COMPLETE / FINAL CANDIDATE GATES PENDING

**Evidence date:** 2026-09-14

**Immutable execution base:** `f0e7d007628ae9afd520e30d85cbd4204ed064f7`

**Last code checkpoint before this handoff:** `297d67a14f1a2f0212cffa64ed3ef01d5ab59de9`

**Branch:** `codex/gate-7-bounded-home-content`

This is the pre-merge implementation and evidence record for Multi-Brand Gate 7. The documentation-bearing commit is intentionally not embedded in this file because a file cannot contain the hash of the commit that contains its own final bytes. This handoff does not claim final exact-HEAD local validation, pull-request CI, owner merge, merged-main verification, post-merge CI, production readiness, Gate 8, a second merchant application or P3-16.

## 1. Outcome

Gürbakır Home now consumes a bounded, application-selected Shopify metaobject root while Android retains rendering, actions, capability behavior, refresh, expiry, recovery and security authority. The implementation:

- supports exactly one collection-grid and one featured-product section in merchant order;
- distinguishes explicit empty from missing, draft, unresolved and contradictory publication states using `declared_section_count`, stored reference values and resolved nodes;
- keeps Product and Collection actions typed and creates them only from current resolved resources;
- persists remote editorial identity, titles, ordering and typed GIDs only;
- keeps establishment independently durable from an evictable snapshot and forbids packaged resurrection after establishment;
- serializes request acceptance, session authority and persistence so obsolete requests cannot overwrite newer visible or stored state;
- provides an accessible native Refresh action in every Home state;
- retains the original editorial deadline through LKG hydration and rechecks expiry on resume;
- keeps Legal/Support reachable from Home whenever Customer Account is disabled;
- keeps the synthetic application remote-disabled, credential-free, Firebase-free and without an effective INTERNET permission.

No Room, DataStore, protected-store, OAuth, Firebase, application-ID, signing, market/language, font, arbitrary-layout or production-release migration was introduced.

## 2. Commit sequence

| Commit | Purpose |
|---|---|
| `dad1205` | Reconcile false current Gate 6 lifecycle wording without rewriting historical handoffs |
| `168d44a` | Expose the existing native Legal/Support card when Customer Account is disabled |
| `38a4834` | Add exact bounded Home operations, neutral provider observations, gateway methods and mapper tests |
| `2b7db4f` | Normalize the durable Gate 7 plan formatting |
| `c223a2d` | Add validator, strict persistence, establishment ownership, clock and acceptance coordinator |
| `7c76640` | Atomically cut configuration, repository, ViewModel, DI, UI and tests to the combined Home contract |
| `1b01f4e` | Add structural, package and active API 23 conformance enforcement |
| `297d67a` | Make the reusable mobile-core instrumentation lane compatible with API 23 |

The final implementation diff through `297d67a` changes 57 files with 4,232 insertions and 1,065 deletions. The handoff and its factual plan-status update are the only intended changes after that checkpoint before exact-candidate validation.

## 3. Contract and implementation

The checked-in `HomeContentMetaobject` operation reads the application-selected `mobile_home` root, strict schema/count fields, stored section-reference JSON, resolved nodes and bounded overflow sentinels. The checked-in `HomeResources` operation rehydrates at most seven unique typed resource IDs without persisting current money, availability or media.

The Storefront mapper preserves the existing Collection eligibility contract: a Collection requires a product even when direct media exists, and a present but rejected direct image cannot fall back to product media. Product money/media/availability failures omit only the current featured card after a valid editorial document is accepted. GraphQL errors remain whole-operation failures.

`HomeContentValidator` rejects malformed field types/values, unsupported versions, contradictory counts, unresolved section entries, overflow, repeated GIDs/families, same-kind identity conflicts, invalid titles and invalid typed resource references. Identical handles across the two different section types are valid.

`AndroidHomeContentStore` uses one private `home_content_v1` preference file with strict bounded JSON records for establishment and snapshot. Snapshot cleanup cannot erase establishment. Marker corruption becomes ownership-unknown and fails conservatively. Store work is main-safe through the injected Home-content IO dispatcher.

`HomeContentAcceptanceCoordinator` is singleton-scoped and serializes begin/accept/promotion. Its in-memory accepted state remains authoritative after an unconfirmed write, including intentional empty. The implementation deliberately makes no durability promise after an unconfirmed write followed by process death.

The combined Home ViewModel owns one request and one expiry timer. Manual refresh is distinct from automatic retryability, disables overlap, retains unexpired content while refreshing, hides expired merchant cards and never renews editorial TTL through resource hydration or failure.

## 4. Phase A actual-client evidence

Both exact Storefront operations were accepted by Shopify's bundled Storefront `2026-07` validator and compiled by Apollo generation. That establishes operation/schema compatibility, not publication behavior by itself.

Using the project-owned nonproduction shop and the generated mobile client, sanitized observations distinguished:

- active ordered root with two fully resolved children;
- active explicit-empty root;
- draft root, observed as unavailable rather than empty;
- active nonempty root whose draft child produced a nonempty stored declaration with zero resolved children;
- restored child/root visibility and original order;
- nonexistent selector;
- two different section types using the same `primary` handle.

Definitions and `mobile_home/primary` were inspected before mutation. Recoverable prior state was captured outside Git in the access-controlled operator record. Temporary probes were returned to DRAFT. The authorized primary root and its two children were left ACTIVE with the original collection-grid then featured-product order. No raw payload, token or typed resource ID is recorded here.

## 5. Test-driven regression evidence before the handoff

Focused JVM suites passed for Storefront mapping, document validation, strict persistence, coordinator ordering, repository state transitions, ViewModel expiry/refresh behavior and Compose rendering. The implemented fixtures cover explicit-empty versus unresolved child, cross-type handle acceptance, same-kind conflicts, corruption ownership, partition/version transitions, unconfirmed empty promotion, delayed A/B acceptance, bad Product money beside a valid Collection, Collection eligibility/media no-bypass behavior, original LKG deadlines, expiry equality/skew/overflow and offline recovery without fabricated commerce.

Physical-device focused instrumentation passed on a connected Android 13 device:

- `HomeScreenTest`: 5 tests, 0 failures;
- `AndroidHomeContentStoreTest`: 2 tests, 0 failures;
- synthetic Account-disabled Legal/Support navigation: passed without initializing Customer Account discovery/token/logout/secure-store providers.

The API 23 managed-device lane executed the real `HomeContentApi23ConformanceTest`: 2 tests, 0 failures. It exercised the Android store/codec plus intentional-empty and nonrenderable renderer branches offline. The same combined command completed with 122 mobile-core tests and no failures. The synthetic task was up-to-date in that run and therefore still requires a fresh exact-candidate execution.

Two unrelated instrumentation tests were correctly scoped to platform capabilities discovered by the new reusable API 23 lane: PixelCopy screenshot proof now requires API 26, and one search test whose driver relies on API 24 focus/IME behavior now requires API 24. Gate 7 production code was not weakened to accommodate either test.

Structural/package proof before this handoff:

- repository portability self-test: 63 fixtures passed;
- repository portability live validation: 46 checks passed;
- synthetic package self-test: 47 fixtures passed;
- synthetic debug/release package inspection: 63 checks passed;
- exact combined mobile-core/synthetic API 23 command: successful in 7 minutes 27 seconds, subject to the synthetic up-to-date limitation above.

These are pre-handoff results and do not replace the required exact documentation-bearing candidate matrix.

## 6. Configured Gürbakır device acceptance

The development-debug APK assembled successfully, installed over the existing nonproduction package without clearing data and launched on a connected Samsung SM-A225F running Android 13.

Configured acceptance passed:

- the Home root loaded as REMOTE with two sections in collection-grid then featured-product order and an exact 86,400,000 millisecond editorial interval;
- the top-bar Refresh action was exposed through accessibility semantics and accepted a fresh snapshot;
- publishing the reverse section order while Home remained alive, then using native Refresh, changed both accepted/persisted order without recreating Home;
- restoring the original merchant order and refreshing restored the original accepted/persisted order;
- publishing explicit empty and refreshing produced the native empty/Categories state and persisted an intentional-empty snapshot;
- restoring the two sections and refreshing returned the content without recreating Home;
- temporarily pointing the featured section at an already-existing DRAFT product kept both editorial section references, retained the Collection grid and omitted only the unavailable featured card; it did not become intentional empty;
- the original featured Product reference was restored and a final refresh accepted the original two-section state;
- Collection navigation opened the currently resolved Collection destination;
- Product navigation opened the currently resolved Product destination.

The real process-boundary procedure also passed. With a fresh REMOTE snapshot present, only the root was changed to DRAFT. `adb shell am force-stop` terminated the process without clearing data. Relaunch produced LKG with the same accepted/expires timestamps, two current resource-backed sections and typed actions. Restoring the root ACTIVE and invoking native Refresh returned the source to REMOTE and installed a new exact 24-hour deadline.

Account-disabled Legal/Support normal-path behavior was executed in the synthetic application. Gürbakır keeps Customer Account enabled, so its correct configured behavior is to omit the duplicate Home card and retain Legal/Support through Account. Loading/error/empty/LKG/expired placement is additionally covered by shared Compose tests.

No order, payment, customer, inventory, price, market, publication-channel or reference-service mutation occurred. The temporary root ordering, empty state and featured reference were all restored; the primary root and children remain ACTIVE.

## 7. Failures and corrections retained as evidence

- The first documentation-bearing candidate failed `spotlessCheck detekt lint` after 6 minutes 24 seconds because Android Lint required an explicit decision about two raw `SharedPreferences.Editor.commit()` calls. The KTX helper discards the boolean durability result required by this contract, so the two exact sites now carry a narrowly documented `UseKtx` suppression. Focused mobile-core formatting, lint and unit tests then completed successfully in 5 minutes 37 seconds. The original full-gate failure remains a FAIL and all final gates must run again on the corrected candidate.
- The historical planning baseline had one uncached Storefront timeout in an existing one-second Gate 6 test. Its isolated rerun and subsequent normal suite passed. It remains baseline flake evidence and is not relabeled as a fresh unconditional suite PASS.
- The first Gate 7 API 23 managed-device definition used an unavailable `aosp-atd` image. It was corrected to the repository-supported `aosp` 64-bit image.
- The initial API 23 run exposed one Account list item that was not composed, an API 24 preference cleanup call, API 26 PixelCopy usage and API 24 search-driver behavior. Tests were corrected or accurately SDK-scoped; repeated attempts to change production focus behavior were reverted after evidence showed the issue was test-driver/platform-specific.
- The first physical conformance compilation inferred a non-Unit expression-body return. The test body was made explicitly Unit and then passed 2/2.
- During local credential-assisted execution, a controlled public Storefront client token appeared once in local tool output. It is absent from Git, test reports, screenshots, operator evidence and this handoff. Because public Storefront tokens are extractable by design but still controlled project configuration, consumer-safe rotation must be assessed and completed before Gate 7 closure; the value must not be copied into any issue, PR, CI log or future report.

## 8. Honest pending evidence

The following are not yet claimed for the documentation-bearing candidate:

- final `spotlessCheck detekt lint`;
- final complete JVM suite and Apollo generation;
- final reusable/app/synthetic debug/release and Android-test assembly matrix;
- final API 30 managed-device lane;
- fresh final combined API 23 lane, including actual synthetic execution;
- final portability, public-readiness and synthetic package checks on a clean worktree;
- final redacted history/tree Gitleaks scans and dependency-integrity checks;
- whole-candidate code review and focused security/privacy review;
- protected pull request, resolved conversations or GitHub's three required exact-SHA checks;
- owner-authorized merge, merged-main ancestry/tree verification or post-merge CI.

Therefore the correct current state is:

**SOURCE IMPLEMENTED / CONFIGURED ACCEPTANCE COMPLETE / FINAL CANDIDATE GATES PENDING**

## 9. Rollback

Source rollback is a normal protected-PR revert of the Gate 7 commits. The prior build resumes packaged Home and ignores `home_content_v1`; no database, protected-store, OAuth, application-ID, signing or Keystore migration exists.

Preference records can remain because older builds do not read them. A later Gate 7 build rejects incompatible storage/content versions while retaining known establishment for the same partition. Switching store or selector creates another partition.

External rollback uses the captured operator pre-state. Missing/DRAFT is not deliberate withdrawal and may retain fresh LKG until expiry; deliberate withdrawal uses an explicit count-zero root plus Refresh. Git rollback does not modify Shopify state or erase device records.

After final exact-HEAD validation, review/security, the three protected GitHub checks, owner-authorized merge, merged-main verification and canonical post-merge CI succeed, Gate 7 may be called technically closed. Only then should a separate narrow documentation reconciliation update current indexes. Gate 8 does not begin automatically.
