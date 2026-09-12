package com.example.gate2synthetic

import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.accountdeletion.DeletionPageSource
import com.gurbakir.mobile.address.AddressTerritoryPolicy
import com.gurbakir.mobile.catalog.CatalogConfiguration
import com.gurbakir.mobile.home.HomeConfiguration
import com.gurbakir.mobile.search.LocalCommerceDatabase
import com.gurbakir.mobile.search.SearchHistoryPartition
import com.gurbakir.mobile.update.CurrentAppVersionCode
import com.gurbakir.mobile.update.UpdatePolicyRemoteGateway
import com.gurbakir.mobile.wishlist.WishlistPartition
import com.gurbakir.storefront.CartSessionStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Runtime evidence surface owned only by the non-production Gate 2 conformance application. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface Gate2SyntheticConfigurationEntryPoint {
    fun appConfiguration(): AppConfiguration

    fun homeConfiguration(): HomeConfiguration

    fun catalogConfiguration(): CatalogConfiguration

    fun addressPolicy(): AddressTerritoryPolicy

    fun searchPartition(): SearchHistoryPartition

    fun wishlistPartition(): WishlistPartition

    fun deletionPageSource(): DeletionPageSource

    fun currentVersionCode(): CurrentAppVersionCode

    fun updatePolicyGateway(): UpdatePolicyRemoteGateway
}

/** Runtime persistence evidence surface owned only by the Gate 2 conformance application. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface Gate2SyntheticPersistenceEntryPoint {
    fun database(): LocalCommerceDatabase

    fun cartSessionStore(): CartSessionStore

    fun customerSessionStore(): CustomerSessionStore
}
