# Multi-Brand Gate 6 — Shopify Navigation Discovery Migration

**Status:** Authorized implementation plan
**Review date:** 2026-09-12
**Original planning base:** `9969250e369acc52718f8733672a2cccc5da4be5`
**Immutable implementation base:** `9969250e369acc52718f8733672a2cccc5da4be5`

This document records the approved Gate 6 implementation contract. It is not, by itself, evidence that an implementation, test, live proof, review, CI run, merge, or post-merge verification passed. Actual evidence belongs in the completion handoff and immutable CI/review records.

## A. Provenance and execution checkpoint

The original planning session reported a clean `main` at the immutable base, passing baseline portability/package validators and focused JVM tests, and a read-only Storefront capability probe. GitHub `main` and successful baseline workflow run `34580946815` were independently checked on 2026-09-12 at the same SHA. Those observations remain dated planning evidence.

Implementation is isolated on `codex/multibrand-gate-6-shopify-navigation-discovery`. No Shopify, Firebase, Play, signing, production, customer, cart, checkout, payment, order, or Menu mutation is authorized by this plan.

For the development/staging cutover candidate, `main-menu` was selected under delegated reversible configuration authority after the planning probe established a bounded, readable shape with eligible Collections and first-product media. The value remains only in ignored local configuration. Tracked defaults remain blank and fail closed. The selection does not authorize a Shopify Menu edit and must be proven through the actual Storefront client and assembled application.

## B. Scope

Gate 6 transfers Categories discovery membership, presentation order, Collection list, and labels from compiled Android data to a bounded projection of one application-selected Shopify Menu.

The native product contract is deliberately narrower than Shopify navigation:

- consume at most three supported levels;
- flatten eligible Collections in depth-first pre-order;
- display no remote headings, accordions, URLs, route strings, or arbitrary components;
- place an eligible Collection parent before eligible descendants;
- continue through valid descendants of unsupported, unavailable, filtered, or malformed parents;
- route only through the existing typed `CollectionRoute(handle)`.

Home remains the independent compiled five-handle P3-01 contract. Gate 7 Home content, Gate 8 Admin provisioning, Gate 9 cleanup, P3-16, real-brand onboarding, runtime merchant switching, persistence, and new navigation targets remain out of scope.

## C. Ownership

The application module selects `shopify.catalogMenuHandle` and exposes the trimmed value through `BuildConfig.CATALOG_MENU_HANDLE`. `:storefront` validates the request and provider response and creates a provider-neutral bounded tree. `:mobile-core` revalidates injected tree shape, performs the flat product projection, owns failure presentation, and renders Categories. Synthetic uses `synthetic-catalog-menu` with its unconfigured gateway and no effective `INTERNET` permission.

Catalog no longer consumes `StorefrontHomeGateway` or `HomeCollectionSummary`. Reusing `HomeImageFields` and `HomeMedia` is shared transport/presentation reuse, not Home gateway ownership.

## D. Storefront contract

The implementation remains pinned to the checked-in Storefront `2026-07` schema. Menu access needs `unauthenticated_read_content`; Collection and Product fields need `unauthenticated_read_product_listings`. HTTP success with GraphQL errors remains a failure under the existing executor. `menu = null` remains distinct from transport or GraphQL failure.

```graphql
query CatalogDiscoveryMenu($handle: String!) {
  menu(handle: $handle) {
    id
    handle
    items {
      ...CatalogDiscoveryLevel1
    }
  }
}

fragment CatalogDiscoveryItemFields on MenuItem {
  id
  title
  type
  tags
  resource {
    __typename
    ... on Collection {
      id
      handle
      title
      image {
        ...HomeImageFields
      }
      products(first: 1) {
        nodes {
          featuredImage {
            ...HomeImageFields
          }
        }
      }
    }
  }
}

fragment CatalogDiscoveryLevel1 on MenuItem {
  ...CatalogDiscoveryItemFields
  items {
    ...CatalogDiscoveryLevel2
  }
}

fragment CatalogDiscoveryLevel2 on MenuItem {
  ...CatalogDiscoveryItemFields
  items {
    ...CatalogDiscoveryLevel3
  }
}

fragment CatalogDiscoveryLevel3 on MenuItem {
  ...CatalogDiscoveryItemFields
  items {
    id
  }
}
```

The final child ID is an overflow sentinel, not a fourth supported content level. A nonempty sentinel rejects the whole result. `MenuItem.tags` is queried only to retain whether filtering exists. `MenuItem.url`, `resourceId`, Menu title, `itemsCount`, arbitrary HTML, and remote route data are excluded. Required `Image.url` remains available through `HomeImageFields`.

## E. Public contracts

```kotlin
data class CatalogConfiguration(val menuHandle: String)

data class CatalogDiscoveryRequest(val menuHandle: String) {
    fun isValid(): Boolean =
        menuHandle.matches(Regex("^[a-z0-9][a-z0-9-]{0,254}$"))
}

object CatalogDiscoveryBounds {
    const val MAX_DEPTH = 3
    const val MAX_NODES = 64
}

data class CatalogDiscoveryMenu(
    val id: String,
    val handle: String,
    val items: List<CatalogDiscoveryNode>
)

data class CatalogDiscoveryNode(
    val id: String,
    val title: String,
    val hasCollectionFilters: Boolean,
    val target: CatalogDiscoveryTarget,
    val children: List<CatalogDiscoveryNode>
)

sealed interface CatalogDiscoveryTarget {
    data class Collection(
        val collection: CatalogDiscoveryCollection
    ) : CatalogDiscoveryTarget
    data object UnavailableCollection : CatalogDiscoveryTarget
    data object Unsupported : CatalogDiscoveryTarget
    data object Malformed : CatalogDiscoveryTarget
}

data class CatalogDiscoveryCollection(
    val id: String,
    val handle: String,
    val sourceTitle: String,
    val media: StorefrontMedia
)

data class CatalogCategoryItem(
    val stableId: String,
    val title: String,
    val collection: CatalogDiscoveryCollection
)
```

`StorefrontCatalogGateway` adds `loadCatalogDiscovery(CatalogDiscoveryRequest)` while retaining Collection-page loading unchanged. No default interface method may hide missing implementers.

## F. Validation and bounds

- Menu and Collection handles use `^[a-z0-9][a-z0-9-]{0,254}$`.
- Menu, MenuItem, and Collection IDs are opaque, nonblank, at most 255 characters, have no surrounding whitespace, and contain no ISO control characters.
- The returned Menu handle exactly matches the request.
- Every returned node at levels one through three counts before filtering or de-duplication. Node 64 is accepted; node 65 fails the whole result.
- Core independently rejects injected trees deeper than three or larger than 64 nodes.
- Exactly 24 unique accepted actions are allowed; the 25th fails the whole result. No bound is handled by truncation.
- Actionable MenuItem titles are trimmed, nonblank, at most 255 characters, and contain no ISO control characters.
- Cancellation propagates and never becomes a service/configuration result.

These are query-shape, mapped-tree, and rendered-output bounds. Apollo decodes the response before projection; no transport byte/allocation bound is claimed.

## G. Provider mapping and media

An actionable Collection requires `MenuItem.type == COLLECTION`, a Collection resource, valid Collection identity/handle, at least one returned product, and media accepted by `StorefrontMediaPolicy`.

- Collection type with null resource, no products, or no acceptable media is unavailable.
- Collection type with a non-Collection resource, or a known contradictory type with a Collection resource, is malformed.
- Unknown enum values and ordinary known non-Collection types are unsupported.
- Nonempty `tags`, including blank tag values, makes the Collection action unsupported and records a configuration partial problem. Gate 6 never widens it to the full Collection.
- Descendants remain independently eligible.

Media prefers a present Collection image. First-product featured media is used only when the Collection image field is absent. A rejected present Collection image is not bypassed by product fallback. Existing null-only alt-text fallback remains unchanged.

## H. Projection and identity

Traversal is depth-first pre-order in Shopify sibling order.

1. A repeated MenuItem ID keeps the first occurrence's action, skips the later occurrence's own action, records a configuration partial problem, and still visits later children. Invalid MenuItem IDs cannot act.
2. For repeated eligible destinations under distinct valid item IDs, the first accepted Collection ID or handle wins without an error. Earlier ineligible occurrences do not reserve the destination.
3. The same Collection ID with different handles, or the same handle with different Collection IDs, records a configuration partial problem; the first accepted action remains.

The accepted MenuItem ID is the UI key, the MenuItem title is displayed, and the validated Collection handle is the click target.

## I. Failure contract

| Condition | Categories result |
|---|---|
| Invalid selector, missing Menu, invalid root, or bound violation | Non-retryable configuration error |
| Transport failure | Connection error with existing retryability |
| GraphQL/service/access error | Non-retryable service error |
| Existing empty Menu or only ordinary unsupported/unavailable nodes | Empty |
| Eligible plus ordinary unsupported/unavailable nodes | Content without false partial failure |
| Malformed, filtered, repeated item ID, or identity conflict with content | Content plus non-retryable configuration partial |
| Same destination under distinct IDs | First action only, no error |
| Configuration partial problem with no accepted action | Non-retryable configuration error |

Categories retain current refresh/latest-request behavior. No persistent cache, LKG, packaged Categories hierarchy, or automatic refresh cadence is introduced.

## J. UI and localization

Categories retain the two-column grid, centered odd final item, touch targets, media component, and existing Collection journey. Category labels become merchant-default MenuItem titles. Local chrome/errors remain Android-localized; remote labels may remain Turkish under English chrome and are not pseudo-localized.

The truthful empty copy is:

- Turkish: `Şu anda gösterilecek kategori yok.`
- English: `There are no categories to show right now.`

Only obsolete app `category_label_*` resources are removed. Home resources and handles remain unchanged.

## K. Implementation sequence

1. Freeze the execution base and create an isolated worktree.
2. Add the finite Menu operation, neutral models, gateway method, mapper, fail-closed unconfigured adapter, and real-operation MockWebServer tests. Establish behavioral RED before GREEN.
3. Replace compiled Categories configuration with the selector; implement bounded core projection; update UI, app/synthetic composition, resources, repository/ViewModel/Compose tests; remove Catalog's Home gateway dependency.
4. Add field-aware structural enforcement and adversarial fixtures for static hierarchy, selector bypass, remote URL routing, missing tags/sentinel, Image URL control, Home preservation, and synthetic isolation.
5. Add a dedicated opt-in owned Menu proof separate from unrelated product-filter proof.
6. Run configured Gürbakır Categories/listing/back/root acceptance using the selected ignored configuration.

Commits are scoped as:

- `feat(storefront): add bounded catalog menu discovery`
- `refactor(multibrand): source Categories from Shopify Menu`
- `test(multibrand): enforce Storefront-owned catalog discovery`

## L. Verification

Required evidence includes Apollo generation/document inspection, focused and full JVM suites, Android-test compilation and API 30/API 23 instrumentation when available, app/core/synthetic assembly, repository portability and package validators, Gitleaks, whole-candidate review, focused security/privacy review, fresh selected-client proof, and matching configured-app acceptance.

Ordinary CI remains credential-free. `NOT RUN` is not a passing substitute. Missing selected-client/configured-app evidence blocks cutover. Missing local managed-device infrastructure must be reported and satisfied by exact-candidate CI before merge readiness.

## M. Documentation and closure

`docs/multi-brand/GATE-6-COMPLETION-HANDOFF.md` is a historical pre-merge evidence record. If selected-client or configured-app evidence is incomplete, it must state `CUTOVER ACCEPTANCE PENDING / NOT READY TO MERGE`. If another mandatory evidence gate is incomplete, it must identify that gate and still state `NOT READY TO MERGE`. Exact PR-HEAD CI, owner-reviewed merge, merged-main ancestry/tree verification, and successful post-merge main CI are required before Gate 6 is technically closed.

Live architecture/status reconciliation is a later narrow post-merge task. Historical Gate 1–5 and Phase 3 handoffs are not rewritten. Gate 7 does not start automatically.

## N. Rollback

Rollback is a normal reviewed revert of the Gate 6 PR. It restores compiled Catalog configuration and old category resources. There is no persistence, user-data migration, schema replacement, dependency upgrade, external Shopify mutation, Firebase/provider change, signing change, or Android permission addition. Source rollback is not an instant rollback of an installed artifact.

## O. Deferred work

Home content/metaobjects/LKG, Admin Menu provisioning, broad cleanup, second-brand onboarding, runtime switching, `@inContext`, new Menu route types, tag-filter routing, Collection listing redesign, Firebase redesign, release/signing/Play, persistence migrations, account-deletion operations, and P3-16 remain explicitly deferred.

## P. Closure checklist

- [ ] Deterministic exact-candidate checks are green.
- [ ] Selected Menu decision and fresh actual-client proof are recorded.
- [ ] Matching configured-app Turkish/English Categories and Collection journey are accepted.
- [ ] API 30 and API 23 lanes are green on the exact candidate.
- [ ] Gitleaks, whole-candidate review, and focused security/privacy review are complete.
- [ ] Final PR HEAD CI is green.
- [ ] Owner merge and merged-main verification are complete.
- [ ] Successful post-merge `main` CI is recorded.

Until every technical-closure item above is satisfied, the plan and implementation must not claim Gate 6 technically closed. Once they are satisfied, **Multi-Brand Gate 6 is technically CLOSED**.

The separate narrow live architecture/status reconciliation follows technical closure. Multi-Brand Gate 7 planning begins only after that reconciliation is complete.
