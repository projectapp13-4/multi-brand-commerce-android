# Multi-Brand Gate 7 — Bounded Shopify-Driven Home Content

Status: **Phase A contract frozen; source implementation and configured acceptance complete; final candidate gates pending**

Approved date: 2026-09-13

Planning base: `f0e7d007628ae9afd520e30d85cbd4204ed064f7`

Implementation branch: `codex/gate-7-bounded-home-content`

## Purpose and evidence boundary

Gate 7 moves only merchant-owned Home editorial selection, order, remote titles and typed Product/Collection references out of the APK. It retains merchant-owned Storefront-readable metaobjects, an application-owned root selector, a finite native renderer for collection-grid and featured-product sections, typed navigation, editorial-only last-known-good persistence, packaged first-install fallback, and an offline/Firebase-free synthetic application.

Planning observations and the public baseline are historical evidence. Checked-in Apollo compilation, local tests, actual Storefront observations, configured-device acceptance, exact-candidate CI and post-merge evidence are separate proof classes. No PASS may be inferred from a planned command or a differently scoped baseline.

During the additive contract slice, Shopify's bundled Storefront schema validator accepted the root operation as artifact `g7-home-content-root-r01` revision 5 and the batch operation as `g7-home-resources-r06` revision 2 when each was supplied with the shared image fragment. The repository's Apollo generator also compiled both checked-in operations. Those results prove operation/schema compatibility only.

On 2026-09-13, the ignored configuration was supplied from the project-controlled historical workspace without printing or tracking credentials. The actual generated Apollo mobile client then distinguished active ordered content, explicit empty, a draft root, an active nonempty root whose child was draft, restored child visibility and a nonexistent selector. Three opt-in proof executions completed successfully; the final proof also read the published `mobile_home/primary` with two typed child entries that both use handle `primary`. Temporary probes were returned to DRAFT. Sanitized evidence is retained outside Git in the access-controlled operator record; raw payloads and tokens are excluded. This satisfies the Phase A observability gate and authorizes Phase B under this plan.

## Scope

Android retains:

- the two supported native section families;
- layout, accessibility, loading, error, expiry and refresh behavior;
- capability and primary-navigation topology, Wishlist behavior and Legal/Support reachability;
- media-origin enforcement and all executable behavior;
- store/environment/root selection, cache ownership and recovery;
- packaged first-install fallback.

Merchant data may select only ordered section entries, remote titles and typed Product/Collection references. It cannot select routes, URLs, components, classes, scripts, providers, application identity, permissions or Firebase behavior. `Image.url` is required commerce media data and remains subject to `StorefrontMediaPolicy`.

The following remain outside Gate 7: Gate 8 provisioning, a second real merchant, custom-font work, `@inContext`, runtime market/language switching, arbitrary page builders, Firebase redesign, signing, public release and P3-16.

## Phase A — contract and observability freeze

Before external mutation:

1. Inspect existing definitions named `mobile_home`, `mobile_home_collection_grid` and `mobile_home_featured_product`, plus `mobile_home/primary`.
2. Determine whether another consumer exists. Reuse only an exactly compatible authorized contract; never overwrite or weaken an unrelated definition.
3. Capture recoverable prior state in an ignored, access-controlled operator record.
4. Create definitions first, then child values, then roots, and finally retrieve them through the actual mobile Storefront client.
5. Use temporary nonproduction probe handles for publication-state cases.
6. Record sanitized classifications, field types, order and timing without tokens or raw payloads.

Required observations are active ordered nonempty, active explicit empty, draft root, active nonempty root referencing a draft child, restored active child/root and a nonexistent selector. If declared count, stored value, resolved references and connection completeness cannot discriminate these cases, Phase A fails and Phase B remains blocked.

External setup is limited to the approved project-owned development/staging shop. Definitions precede values, children are active before their root is activated, and this operator order is not represented as a server-atomic publication transaction.

## Shopify schema

All definitions are merchant-owned types, require Storefront `PUBLIC_READ`, merchant read/write administration and the publishable capability. The Admin API rejected an explicit merchant Admin-access input during creation; omitting it produced the platform-owned `PUBLIC_READ_WRITE` readback. The root mixed-reference validation required the two concrete child-definition GIDs rather than definition-type strings, plus `list.max = 2`; this provider representation is captured in operator evidence while the application contract remains limited to the two named types.

| Definition | Field | Contract |
|---|---|---|
| `mobile_home` | `schema_version` | Required `number_integer`; client accepts exactly `1` |
|  | `declared_section_count` | Required `number_integer`; provider bounds `0..2` |
|  | `sections` | Optional `list.mixed_reference`; exactly the two supported definitions; maximum `2` |
| `mobile_home_collection_grid` | `title` | Required `single_line_text_field`; 1–80 Unicode code points |
|  | `collections` | Required `list.collection_reference`; provider bounds `1..6` |
| `mobile_home_featured_product` | `title` | Required `single_line_text_field`; 1–80 Unicode code points |
|  | `product` | Required `product_reference` |

Root selector:

- type `mobile_home`;
- application property `shopify.homeContentRootHandle`;
- tracked blank default selects `HomeRemoteSource.Disabled`;
- ignored development/staging value is `primary`;
- invalid nonblank selectors issue zero Storefront requests.

`declared_section_count = 0` is intentional empty only when `sections` is absent or stores `[]` and resolved nodes are empty. Counts one or two require a stored JSON GID list of exactly that count and exact ordered fully resolved section-node correspondence. A nonzero declaration with zero or partial section resolution is invalid and is never promoted.

Collection resource references may resolve partially: typed declared IDs remain editorial references but cannot produce actions without current typed resolution. A valid Product GID with a null reference remains an unresolved editorial reference rather than becoming intentional empty.

## Checked-in Storefront operations

The exact root operation is [`HomeContentMetaobject.graphql`](../../../storefront/src/main/graphql/com/gurbakir/storefront/HomeContentMetaobject.graphql). It selects root identity and `updatedAt`, schema/count field type and value, `sections.type`, stored `sections.value`, three-node overflow sentinel, typed child fields, seven-node collection overflow sentinel, Product/Collection identity and current commerce fields.

The exact LKG hydration operation is [`HomeResources.graphql`](../../../storefront/src/main/graphql/com/gurbakir/storefront/HomeResources.graphql). It accepts at most seven unique typed IDs and returns position-preserving nullable nodes with Product/Collection identity, current handle, eligibility, money and media inputs.

Both operations target checked-in Storefront API `2026-07`. Metaobjects use token-based access with `unauthenticated_read_metaobjects`. No `@inContext`, universal 1,000-point ceiling or pre-decode HTTP byte cap is claimed.

## Storefront boundary

`StorefrontHomeGateway` retains the two legacy handle methods for packaged fallback and adds:

```kotlin
suspend fun loadHomeDocument(
    selector: HomeDocumentSelector,
): StorefrontResult<HomeDocumentObservation?>

suspend fun loadHomeResources(
    keys: List<HomeResourceKey>,
): StorefrontResult<HomeResourceBatch>
```

`:storefront` owns Apollo/generated types, raw field observations, runtime types, typed provider resources, current handles, money-parse outcomes and safe-media outcomes. Raw stored IDs are validation inputs, never action authority.

The batch accepts no more than seven unique typed GIDs. Mapping verifies response count, input/output position, ID, runtime type, duplicates/conflicts and null nodes. GraphQL errors retain the existing whole-operation failure boundary.

For Collections, current eligibility continues to require at least one product even when a direct image exists. Product-image fallback is used only when the direct image is absent. A present but rejected direct image cannot bypass media policy through fallback.

## Native/domain contract

```kotlin
sealed interface HomeRemoteSource {
    data object Disabled : HomeRemoteSource

    data class ShopifyMetaobject(
        val selector: HomeDocumentSelector,
        val supportedContentVersion: Int = 1,
        val ttlMillis: Long = 86_400_000L,
    ) : HomeRemoteSource
}

data class HomeConfiguration(
    val remoteSource: HomeRemoteSource,
    val packagedFallback: HomePackagedFallback,
)
```

`RemoteHomeSnapshot` persists only root/section identity, remote titles, order, section families and typed GIDs. It never persists money, availability, images, Android resource IDs, packaged text variants or executable destinations. Current commerce summaries and typed actions exist only in the in-memory presentation after current typed resource resolution.

Editorial state distinguishes `IntentionalEmpty` from `NonEmpty`, even when a nonempty document has no renderable current resources. Resource status distinguishes complete, partial, none-renderable and hydration-failed outcomes. Source distinguishes remote, LKG and packaged.

## Validation matrix

| Observation | Classification/action |
|---|---|
| Remote disabled | Packaged only; no ownership read or Storefront request |
| Invalid nonblank selector | Configuration failure; zero request; no promotion |
| Root absent/draft | Unavailable root; never intentional empty; no promotion |
| Transport/GraphQL error | Whole-operation failure |
| Missing/wrong schema version or unsupported version | Invalid/unsupported; no promotion |
| Missing/malformed declared count | Invalid; no promotion |
| Count `0`, sections absent/`[]`, resolved empty | Intentional empty; promote |
| Count `0` with any declared/resolved entry | Invalid; no promotion |
| Count `1..2` with absent/null/malformed stored list | Invalid; no promotion |
| Declared count differs from stored list or resolved nodes | Unresolved/contradictory; no promotion |
| Wrong GID/list type, overflow sentinel or null connection | Invalid; no promotion |
| Wrong section runtime type/unknown family | Invalid; no promotion |
| Repeated section GID or repeated family | Invalid; no promotion |
| Same `(type, handle)` mapped to conflicting GIDs | Invalid; no promotion |
| Same handle across the two distinct section types | Valid |
| Invalid title | Invalid section/document; no promotion |
| Malformed/wrong/duplicate/overflow Collection list | Invalid; no promotion |
| Declared Collection currently unresolved | Retain typed editorial GID; omit current card/action |
| Valid Product GID currently unresolved | Retain typed editorial GID; omit current card/action |
| Resolved unavailable Product, bad money or missing/rejected media | Promote editorial document; omit Product card; bounded resource status |
| Resolved Collection has no products | Omit card even with direct image |
| Direct Collection image present but rejected | Omit card; no fallback |
| Direct image absent | First-product image may be used if policy accepts it |
| Batch identity/type/duplicate/conflict error | Whole hydration failure; no fabricated commerce |
| Remote action/route/component URL | Not queried or modeled |
| `Image.url` | Required and filtered by media policy |

## Persistence and ownership

Storage uses the private `home_content_v1` `SharedPreferences` file, naturally scoped to each application package. The exact partition contains application ID, environment ID, Storefront domain, root type and root handle.

Two strict JSON records use fixed keys:

- establishment marker, maximum 4 KiB: storage version, exact partition, first-established timestamp and last accepted content version;
- snapshot, maximum 64 KiB: storage version, exact partition, accepted/expires timestamps and remote-only snapshot.

The codec rejects unknown fields/types, invalid partitions/timestamps, null IDs, packaged variants and incompatible versions. Snapshot corruption cleanup never clears establishment. A corrupt/undecodable marker becomes `OwnershipUnknown`, is not cleared, and forbids packaged fallback. Content/API incompatibility discards only the snapshot for the same partition; another store or selector is a new partition.

Marker and snapshot replace in one `Editor.commit()` behind one mutex. A false result is `UNCONFIRMED`, not proof that nothing reached disk. Blocking calls execute through an injected `@HomeContentIo CoroutineDispatcher` and the store is main-safe.

`HomeContentAcceptanceCoordinator` is singleton-scoped and owns the opaque current request token, session-authoritative accepted snapshot and the mutex covering begin/accept/promotion. B beginning before A accepts prevents A from mutating memory or disk. A newly accepted state, including empty, remains session-authoritative after an unconfirmed write so an older disk snapshot cannot return. No durability promise is made after an unconfirmed write followed by process death.

## Fallback, LKG and expiry

- First valid remote content or explicit empty establishes ownership and atomically promotes its editorial snapshot.
- Before establishment, remote failure may use the packaged handle fallback. If its Storefront reads also fail, native unavailable recovery is shown.
- After establishment, remote failure may hydrate a fresh LKG snapshot through `HomeResources`; packaged resurrection is forbidden.
- LKG hydration failure fabricates no current commerce and never renews editorial TTL.
- An establishment marker without a usable snapshot remains established and unavailable until remote recovery.
- Manual refresh keeps unexpired accepted content/empty visible with `refreshing=true`; overlapping refresh is disabled.
- At `now == expiresAt`, the snapshot is expired. Merchant cards are hidden and exactly one refresh begins.
- Failed expiry refresh ends in established-unavailable without a worker loop or packaged fallback.
- Each accepted remote snapshot, including empty, cancels/replaces the one live expiry timer. Hydration, failed refresh and retry do not extend it.
- Resume/re-entry rechecks the original deadline. Completion after expiry cannot publish content as fresh.
- Clock rollback up to five minutes clamps comparison to `acceptedAt`. Larger rollback, overflow or invalid exact TTL makes the snapshot unusable while preserving establishment.

No worker, alarm, service or generic background-refresh framework is added.

## UI, accessibility and navigation

The runtime cutover uses one combined Home load state so editorial order is atomic. A localized native top-bar Refresh button precedes Cart and remains visible in every state; it is disabled only while a request is active. Error-panel retry uses the same manual-refresh path. Manual refresh is independent of automatic retryability.

Intentional empty retains the native Categories action. Nonempty-but-unrenderable uses a distinct native recovery message. Refresh before expiry retains accepted content; expiry refresh hides expired merchant cards.

Legal/Support remains native and outside all content-state branches. When Customer Account is disabled, the existing `LegalSupportHomeCard` appears in healthy, empty, loading, error, LKG, expired and nonrenderable states. When Account is enabled, its existing Legal/Support destination remains the single entry point.

Remote Collection navigation uses the currently resolved handle. Remote Product navigation uses the currently resolved Product GID. Re-resolution of a cached GID to a changed current handle is valid. Wishlist remains capability-controlled.

Remote titles use the shop’s default Storefront language. Native copy remains localized Android content. The system-sans/null-font baseline is unchanged.

## Synthetic conformance

Synthetic production composition uses `HomeRemoteSource.Disabled` plus its independent packaged values. It remains credential-free, Firebase-free and without merged INTERNET permission. Neutral contract strings such as `mobile_home` and `primary` are not brand contamination.

Offline instrumentation may construct local remote snapshots/fake gateway results to execute the real store, codec, coordinator, state machine and renderer. It may not enable production remote access. Add `:mobile-core:ciApi23DebugAndroidTest` and retain `:synthetic:ciApi23DebugAndroidTest` so the minimum-SDK lane executes both active shared behavior and the disabled composition.

## MB-01 prerequisite

`HomeActions.openLegalSupport` is nullable. Production supplies it only when Customer Account is disabled, and `HomeScreen` appends the existing card outside content branching. Account-enabled composition omits the card. JVM/assembly evidence proves only compilation; normal-path API 30 instrumentation must execute before MB-01 is accepted.

Custom-font implementation remains deferred. Current app/test configurations select `fontFamilyResourceName = null`; Gate 7 deliberately retains system sans.

## Compilable implementation sequence

1. Reconcile only false live Gate 6 wording in the three current architecture documents.
2. Implement and instrument MB-01 as a separate prerequisite slice.
3. Add Storefront observation types, gateway methods, exact operations/mappers, fakes and this contract alongside legacy consumers.
4. Obtain the actual-client Phase A observations. Stop if empty/unresolved publication states are not discriminated.
5. Add native validator, partition, strict codec, store, clock and coordinator alongside legacy consumers.
6. Perform one atomic configuration/repository/ViewModel/DI/UI/test cutover after real batch mapping and all fakes compile.
7. Add structural/package enforcement plus active-path API 23 instrumentation.
8. Execute configured-app publication, refresh, navigation, LKG and real process-restart acceptance.
9. Commit the factual pre-merge handoff before final exact-HEAD local/review/security/CI gates.
10. Use the protected merge-commit flow; verify merged-main ancestry/tree and post-merge CI before technical closure. Reconcile live indexes separately without beginning Gate 8.

Every committed boundary must compile. Missing-symbol compiler failures are bootstrap evidence, not behavioral RED tests. Any correction creates a new candidate and repeats affected proof.

## Required proof

The regression matrix must discriminate:

- explicit empty versus nonempty root with unavailable child;
- cross-type identical handles versus same-kind conflicts;
- structural validity versus current resource renderability;
- bad Product money without discarding valid collection editorial content;
- collection product eligibility and rejected-direct-image no-bypass behavior;
- established corrupt snapshot across two reads and process recreation;
- accepted empty plus unconfirmed write without in-session resurrection;
- delayed A/B completion with B retained both visibly and on subsequent persisted read;
- accessible refresh from healthy and intentional-empty Home;
- original LKG deadline after resource hydration and resume-after-expiry;
- main-safe serialized IO;
- active store/codec/state/renderer behavior on API 23;
- synthetic disabled/no-network/Firebase-free packaging;
- force-stop/relaunch without clearing app data;
- typed route/action authority and query-aware URL exclusion;
- exact final documentation-bearing candidate identity;
- debug execution separately from release packaging;
- secret and dependency integrity.

The final local matrix includes formatting, detekt, lint, the complete JVM suite, Apollo generation, reusable/app/synthetic debug/release and Android-test assembly, API 30, combined mobile-core/synthetic API 23, portability/public-readiness/package validators, redacted Gitleaks scans, and final Git identity/status checks. A failed command remains recorded even if a later targeted retry succeeds.

## Configured-app acceptance and process restart

Configured acceptance covers healthy reorder followed by native Refresh, intentional-empty restoration via Refresh, nonrenderable/partial classifications, current typed Collection/Product navigation, Legal/Support in all required states and LKG/fallback classifications. It uses no order, payment, customer mutation or reference service.

Process proof:

1. Load an active probe root in `com.gurbakir.mobile.dev.debug` and observe source `REMOTE`.
2. Change only that probe root to DRAFT.
3. Force-stop the package without clearing data.
4. Relaunch `com.gurbakir.mobile.MainActivity`.
5. Observe source `LKG`, the original deadline, current resource hydration and typed actions.
6. Restore the root active, invoke native Refresh and observe source `REMOTE`.

Recreating a store object or Activity is not process-restart proof.

## Security and privacy

All decoded counts, strings, lists, cache records, operations and batches are bounded. Persisted public editorial metadata still uses private app storage, is excluded from backup, partitioned and never logged raw. Tokens must never enter source, tests, screenshots, evidence or handoffs.

Marker corruption fails conservatively. Newer content versions fail closed for executable authority. Partial external publication is expected and classified. Synthetic cannot contact a live merchant. Gate 7 adds no new dependency.

## Rollback

Source rollback is a protected-PR revert of Gate 7 commits. Older builds ignore `home_content_v1`; no Room, OAuth, protected-store, application-ID or Keystore migration exists. External rollback restores the captured definition/entry state or makes temporary probes DRAFT without altering unrelated consumers.

A missing/DRAFT root does not mean intentional withdrawal and may retain fresh LKG until expiry. Deliberate withdrawal publishes an explicit count-zero root and relies on refresh; immediate offline withdrawal is not guaranteed. Git revert does not remove Shopify data or device preference records.

## Closure criteria and blockers

Phase A is frozen only when both operations compile and the actual client discriminates ordered nonempty, explicit empty, draft root, draft child and restoration without leaking payloads/tokens. Failure stops Phase B.

Implementation completion additionally requires G7-R01 through G7-R08 regressions, executed MB-01 normal-path instrumentation, active API 23 proof, configured refresh/navigation/empty restoration and real process restart.

Merge readiness requires the handoff commit first, then the complete exact-HEAD local matrix, review/security with no unresolved finding, resolved PR conversations and all three strict GitHub checks on that SHA. Technical closure requires protected merge, merged-main tree/ancestry and canonical post-merge CI. Current documentation is reconciled only afterward in a narrow follow-up.

Phase A, MB-01 normal-path instrumentation, active API 23 execution and configured-app acceptance/process restart are complete and are recorded in the Gate 7 completion handoff. Current true blockers are the final exact-head local matrix, review/security, protected-PR checks, owner-authorized merge and post-merge proof. A controlled public Storefront token appeared in local tool output during execution; it is absent from Git and durable evidence, but rotation must be assessed and completed without breaking another authorized consumer before Gate 7 closure.

Fonts, `@inContext`, generic provisioning, a second real brand and P3-16 are not Gate 7 blockers.
