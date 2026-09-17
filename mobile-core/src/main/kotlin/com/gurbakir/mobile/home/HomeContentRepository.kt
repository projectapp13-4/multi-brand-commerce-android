@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
) // The bounded state machine keeps acceptance, fallback, and resource truth in one audited owner.

package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeMediaObservation
import com.gurbakir.storefront.HomeMoneyObservation
import com.gurbakir.storefront.HomeResourceBatch
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.StorefrontFailure
import com.gurbakir.storefront.StorefrontHomeGateway
import com.gurbakir.storefront.StorefrontHomeResource
import com.gurbakir.storefront.StorefrontResult
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface HomeContentRepository {
    suspend fun load(trigger: HomeLoadTrigger): HomeLoadResult
}

class DefaultHomeContentRepository
@Inject
constructor(
    private val gateway: StorefrontHomeGateway,
    private val configuration: HomeConfiguration,
    private val validator: HomeContentValidator,
    private val store: HomeContentStore,
    private val coordinator: HomeContentAcceptanceCoordinator,
    private val clock: HomeEditorialClock,
    private val partition: HomeContentPartition
) : HomeContentRepository {
    override suspend fun load(trigger: HomeLoadTrigger): HomeLoadResult {
        val remote = configuration.remoteSource
        if (remote is HomeRemoteSource.Disabled) return loadPackagedFallback()
        remote as HomeRemoteSource.ShopifyMetaobject
        if (!remote.selector.isValid() || !remote.contractId.accepts(remote.selector)) {
            return configurationFailure()
        }

        val token = coordinator.begin()
        val now = clock.nowMillis()
        val storedRead = store.read(partition, remote.contractId.contentVersion, now)
        val rootResult = gateway.loadHomeDocument(remote.selector)

        if (rootResult is StorefrontResult.Success && rootResult.value != null) {
            val observation = requireNotNull(rootResult.value)
            val validation = validator.validate(remote.selector, observation, remote.contractId.contentVersion)
            if (validation is HomeDocumentValidation.Accepted) {
                return acceptRemote(token, validation.snapshot, observation, storedRead, now)
            }
            return resolveFailure(
                token,
                trigger,
                HomeLoadFailure(HomeLoadFailureCategory.SERVICE, retryable = false),
                remote,
                now
            )
        }

        val failure = when (rootResult) {
            is StorefrontResult.Failure -> rootResult.error.toHomeLoadFailure()
            is StorefrontResult.Success -> HomeLoadFailure(HomeLoadFailureCategory.SERVICE, retryable = false)
        }
        return resolveFailure(token, trigger, failure, remote, now)
    }

    private suspend fun resolveFailure(
        token: HomeRequestToken,
        trigger: HomeLoadTrigger,
        failure: HomeLoadFailure,
        source: HomeRemoteSource.ShopifyMetaobject,
        now: Long
    ): HomeLoadResult = when (
        val authority = coordinator.resolveFailure(
            token,
            partition,
            source.contractId.contentVersion,
            now
        )
    ) {
        is HomeFailureAuthority.Current -> fallbackAfterFailure(
            trigger,
            failure,
            authority.sessionSnapshot,
            authority.storedRead,
            source,
            now
        )

        HomeFailureAuthority.Superseded -> HomeLoadResult.Superseded
    }

    private suspend fun acceptRemote(
        token: HomeRequestToken,
        snapshot: RemoteHomeSnapshot,
        observation: HomeDocumentObservation,
        storedRead: HomeStoreRead,
        now: Long
    ): HomeLoadResult {
        val deadline = HomeEditorialClockPolicy.deadline(now, HOME_EDITORIAL_TTL_MILLIS)
            ?: return when (
                coordinator.resolveFailure(
                    token,
                    partition,
                    snapshot.contentVersion,
                    now
                )
            ) {
                is HomeFailureAuthority.Current ->
                    HomeLoadResult.Failed(HomeLoadFailure(HomeLoadFailureCategory.CONFIGURATION, false))

                HomeFailureAuthority.Superseded -> HomeLoadResult.Superseded
            }
        val stored = HomeStoredSnapshot(partition, now, deadline, snapshot)
        val existingMarker = (storedRead as? HomeStoreRead.Established)?.marker
        val marker = HomeEstablishmentRecord(
            partition = partition,
            firstEstablishedAtMillis = existingMarker?.firstEstablishedAtMillis ?: now,
            lastAcceptedContentVersion = snapshot.contentVersion
        )
        val presentation = render(
            snapshot,
            observation.resolvedResources(),
            HomeContentSource.REMOTE,
            deadline
        )
        return when (val acceptance = coordinator.accept(token, marker, stored)) {
            is HomeAcceptance.Accepted -> HomeLoadResult.Accepted(
                presentation,
                when (acceptance.persistence) {
                    HomeStoreWrite.CONFIRMED -> HomePersistenceStatus.CONFIRMED
                    HomeStoreWrite.UNCONFIRMED -> HomePersistenceStatus.UNCONFIRMED
                }
            )

            HomeAcceptance.Superseded -> HomeLoadResult.Superseded
        }
    }

    private suspend fun fallbackAfterFailure(
        trigger: HomeLoadTrigger,
        failure: HomeLoadFailure,
        sessionSnapshot: HomeStoredSnapshot?,
        storedRead: HomeStoreRead,
        source: HomeRemoteSource.ShopifyMetaobject,
        now: Long
    ): HomeLoadResult {
        if (trigger != HomeLoadTrigger.INITIAL) return HomeLoadResult.Failed(failure)
        val candidate = sessionSnapshot ?: (storedRead as? HomeStoreRead.Established)?.snapshot
        if (
            candidate != null &&
            candidate.snapshot.contentVersion == source.contractId.contentVersion &&
            HomeEditorialClockPolicy.freshness(candidate.acceptedAtMillis, candidate.expiresAtMillis, now) ==
            HomeEditorialFreshness.FRESH
        ) {
            return hydrateLkg(candidate, failure)
        }
        return when (storedRead) {
            HomeStoreRead.NeverEstablished -> loadPackagedFallback()
            is HomeStoreRead.Established, HomeStoreRead.OwnershipUnknown -> HomeLoadResult.Failed(failure)
        }
    }

    private suspend fun hydrateLkg(stored: HomeStoredSnapshot, rootFailure: HomeLoadFailure): HomeLoadResult {
        val keys = stored.snapshot.resourceKeys()
        if (keys.isEmpty()) {
            return HomeLoadResult.Accepted(
                render(stored.snapshot, emptyMap(), HomeContentSource.LKG, stored.expiresAtMillis),
                HomePersistenceStatus.NOT_APPLICABLE
            )
        }
        return when (val result = gateway.loadHomeResources(keys)) {
            is StorefrontResult.Failure -> HomeLoadResult.Failed(result.error.toHomeLoadFailure())

            is StorefrontResult.Success -> {
                val resources = result.value.strictResources(keys)
                    ?: return HomeLoadResult.Failed(rootFailure)
                HomeLoadResult.Accepted(
                    render(stored.snapshot, resources, HomeContentSource.LKG, stored.expiresAtMillis),
                    HomePersistenceStatus.NOT_APPLICABLE
                )
            }
        }
    }

    private suspend fun loadPackagedFallback(): HomeLoadResult = coroutineScope {
        val fallback = configuration.packagedFallback
        val configuredCollections = fallback.productRange.sources.take(fallback.productRange.itemLimit)
        val collectionDeferred = configuredCollections.map { source ->
            async { source to gateway.loadHomeCollection(source.handle) }
        }
        val productDeferred = async { gateway.loadHomeProduct(fallback.featuredProduct.handle) }
        val collectionOutcomes = collectionDeferred.awaitAll()
        val productOutcome = productDeferred.await()
        val sections = mutableListOf<HomeRenderedSection>()
        val collectionItems = collectionOutcomes.mapNotNull { (source, result) ->
            (result as? StorefrontResult.Success)?.value?.let { summary ->
                HomeCollectionItem(source.stableId, HomeText.Packaged(source.labelResourceId), summary)
            }
        }
        if (collectionItems.isNotEmpty()) {
            sections += HomeRenderedSection.CollectionGrid(
                fallback.productRange.stableId,
                HomeText.Packaged(fallback.productRange.titleResourceId),
                collectionItems
            )
        }
        val product = (productOutcome as? StorefrontResult.Success)?.value
        var renderedProduct = false
        if (product != null && product.availableForSale && product.media != null) {
            renderedProduct = true
            sections += HomeRenderedSection.FeaturedProduct(
                fallback.featuredProduct.stableId,
                HomeText.Packaged(fallback.featuredProduct.titleResourceId),
                HomeFeaturedItem(product)
            )
        }
        if (sections.isNotEmpty()) {
            HomeLoadResult.Accepted(
                HomePresentation(
                    HomeEditorialState.Packaged,
                    sections,
                    HomeContentSource.PACKAGED,
                    if (collectionItems.size + (if (renderedProduct) 1 else 0) == configuredCollections.size + 1) {
                        HomeResourceStatus.COMPLETE
                    } else {
                        HomeResourceStatus.PARTIAL
                    },
                    editorialExpiresAtMillis = null
                ),
                HomePersistenceStatus.NOT_APPLICABLE
            )
        } else {
            val failures = collectionOutcomes.mapNotNull { (_, result) ->
                (result as? StorefrontResult.Failure)?.error
            } + listOfNotNull((productOutcome as? StorefrontResult.Failure)?.error)
            if (failures.isEmpty()) {
                HomeLoadResult.Accepted(
                    HomePresentation(
                        HomeEditorialState.Packaged,
                        emptyList(),
                        HomeContentSource.PACKAGED,
                        HomeResourceStatus.NONE_RENDERABLE,
                        null
                    ),
                    HomePersistenceStatus.NOT_APPLICABLE
                )
            } else {
                HomeLoadResult.Failed(failures.toHomeLoadFailure())
            }
        }
    }

    private fun render(
        snapshot: RemoteHomeSnapshot,
        resolved: Map<HomeResourceKey, StorefrontHomeResource>,
        source: HomeContentSource,
        expiresAtMillis: Long
    ): HomePresentation {
        if (snapshot.sections.isEmpty()) {
            return HomePresentation(
                HomeEditorialState.IntentionalEmpty,
                emptyList(),
                source,
                HomeResourceStatus.COMPLETE,
                expiresAtMillis
            )
        }
        var expectedCards = 0
        var renderedCards = 0
        val rendered = snapshot.sections.mapNotNull { section ->
            when (section) {
                is RemoteHomeSection.CollectionGrid -> {
                    expectedCards += section.collections.size
                    val items = section.collections.mapNotNull { key ->
                        val resource = resolved[key] as? StorefrontHomeResource.Collection ?: return@mapNotNull null
                        if (!resource.hasProducts) return@mapNotNull null
                        val media = (resource.media as? HomeMediaObservation.Accepted)?.media ?: return@mapNotNull null
                        renderedCards += 1
                        HomeCollectionItem(
                            resource.key.gid,
                            HomeText.Remote(resource.title),
                            com.gurbakir.storefront.HomeCollectionSummary(
                                resource.key.gid,
                                resource.handle,
                                resource.title,
                                media
                            )
                        )
                    }
                    items.takeIf { it.isNotEmpty() }?.let {
                        HomeRenderedSection.CollectionGrid(section.sectionGid, HomeText.Remote(section.title), it)
                    }
                }

                is RemoteHomeSection.FeaturedProduct -> {
                    expectedCards += 1
                    val resource = resolved[section.product] as? StorefrontHomeResource.Product
                    val media = (resource?.media as? HomeMediaObservation.Accepted)?.media
                    val money = (resource?.money as? HomeMoneyObservation.Accepted)?.money
                    if (resource == null || !resource.availableForSale || media == null || money == null) {
                        null
                    } else {
                        renderedCards += 1
                        HomeRenderedSection.FeaturedProduct(
                            section.sectionGid,
                            HomeText.Remote(section.title),
                            HomeFeaturedItem(
                                com.gurbakir.storefront.HomeProductSummary(
                                    resource.key.gid,
                                    resource.handle,
                                    resource.title,
                                    resource.availableForSale,
                                    media,
                                    money
                                )
                            )
                        )
                    }
                }
            }
        }
        val status = when {
            renderedCards == 0 -> HomeResourceStatus.NONE_RENDERABLE
            renderedCards == expectedCards -> HomeResourceStatus.COMPLETE
            else -> HomeResourceStatus.PARTIAL
        }
        return HomePresentation(
            HomeEditorialState.NonEmpty(snapshot.sections),
            rendered,
            source,
            status,
            expiresAtMillis
        )
    }

    private fun HomeDocumentObservation.resolvedResources(): Map<HomeResourceKey, StorefrontHomeResource> =
        sections?.references?.nodes.orEmpty().flatMap { node ->
            val section = node.section
            section?.collections?.references?.nodes.orEmpty().mapNotNull { it.resource } +
                listOfNotNull(section?.product?.reference?.resource)
        }.associateBy { it.key }

    private fun RemoteHomeSnapshot.resourceKeys(): List<HomeResourceKey> = sections.flatMap { section ->
        when (section) {
            is RemoteHomeSection.CollectionGrid -> section.collections
            is RemoteHomeSection.FeaturedProduct -> listOf(section.product)
        }
    }.distinct()

    private fun HomeResourceBatch.strictResources(
        requested: List<HomeResourceKey>
    ): Map<HomeResourceKey, StorefrontHomeResource>? {
        if (resolutions.size != requested.size) return null
        val mapped = linkedMapOf<HomeResourceKey, StorefrontHomeResource>()
        resolutions.forEachIndexed { index, resolution ->
            if (resolution.requested != requested[index]) return null
            val resource = resolution.resource ?: return null
            if (resource.key != resolution.requested || mapped.put(resource.key, resource) != null) return null
            if (
                (resource.key.kind == HomeResourceKind.COLLECTION && resource !is StorefrontHomeResource.Collection) ||
                (resource.key.kind == HomeResourceKind.PRODUCT && resource !is StorefrontHomeResource.Product)
            ) {
                return null
            }
        }
        return mapped
    }

    private fun com.gurbakir.storefront.HomeDocumentSelector.isValid(): Boolean =
        type == "mobile_home" && handle.length in 1..255 && handle.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))

    private fun configurationFailure(): HomeLoadResult.Failed = HomeLoadResult.Failed(
        HomeLoadFailure(HomeLoadFailureCategory.CONFIGURATION, retryable = false)
    )
}

data class HomeLoadFailure(val category: HomeLoadFailureCategory, val retryable: Boolean)

enum class HomeLoadFailureCategory {
    CONNECTION,
    CONFIGURATION,
    SERVICE
}

private fun StorefrontFailure.toHomeLoadFailure(): HomeLoadFailure = listOf(this).toHomeLoadFailure()

private fun List<StorefrontFailure>.toHomeLoadFailure(): HomeLoadFailure {
    val category = when {
        any { it is StorefrontFailure.Configuration } -> HomeLoadFailureCategory.CONFIGURATION
        all { it is StorefrontFailure.Transport } -> HomeLoadFailureCategory.CONNECTION
        else -> HomeLoadFailureCategory.SERVICE
    }
    return HomeLoadFailure(
        category,
        isNotEmpty() && all { it is StorefrontFailure.Transport && it.retryable }
    )
}
