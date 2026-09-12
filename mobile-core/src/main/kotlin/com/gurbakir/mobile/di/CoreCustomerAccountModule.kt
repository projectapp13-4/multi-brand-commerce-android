package com.gurbakir.mobile.di

import com.gurbakir.account.CustomerAccountApolloClientFactory
import com.gurbakir.account.CustomerAccountGateway
import com.gurbakir.account.CustomerAddressApolloClientFactory
import com.gurbakir.account.CustomerAddressGateway
import com.gurbakir.account.CustomerOrderApolloClientFactory
import com.gurbakir.account.CustomerOrderGateway
import com.gurbakir.account.CustomerProfileApolloClientFactory
import com.gurbakir.account.CustomerProfileGateway
import com.gurbakir.account.CustomerSessionResolver
import com.gurbakir.account.UnconfiguredCustomerAccountGateway
import com.gurbakir.account.UnconfiguredCustomerAddressGateway
import com.gurbakir.account.UnconfiguredCustomerOrderGateway
import com.gurbakir.account.UnconfiguredCustomerProfileGateway
import com.gurbakir.account.oauth.AndroidCustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountAuthorizationCoordinator
import com.gurbakir.account.oauth.CustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.CustomerAccountLogoutClient
import com.gurbakir.account.oauth.CustomerAccountTokenClient
import com.gurbakir.account.oauth.ShopifyCustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.ShopifyCustomerAccountTokenClient
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountDiscoveryClient
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountLogoutClient
import com.gurbakir.account.oauth.UnconfiguredCustomerAccountTokenClient
import com.gurbakir.account.session.CustomerAccountSessionCoordinator
import com.gurbakir.account.session.CustomerSessionStore
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.CustomerAccountCapability
import com.gurbakir.foundation.config.CustomerAccountConfiguration
import com.gurbakir.mobile.account.AccountController
import com.gurbakir.mobile.account.DefaultAccountController
import com.gurbakir.mobile.accountdeletion.AccountDeletionController
import com.gurbakir.mobile.accountdeletion.DefaultAccountDeletionController
import com.gurbakir.mobile.address.AddressController
import com.gurbakir.mobile.address.DefaultAddressController
import com.gurbakir.mobile.order.DefaultOrderController
import com.gurbakir.mobile.order.OrderController
import com.gurbakir.mobile.profile.DefaultProfileController
import com.gurbakir.mobile.profile.ProfileController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CoreCustomerAccountModule {
    @Provides
    @Singleton
    fun provideCustomerAccountDiscoveryClient(configuration: AppConfiguration): CustomerAccountDiscoveryClient = if (
        configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true &&
        configuration.storefront.validationIssues().isEmpty()
    ) {
        ShopifyCustomerAccountDiscoveryClient(
            shopDomain = configuration.storefront.domain,
            expectedIssuer = requireNotNull(configuration.enabledCustomerAccount()).issuer
        )
    } else {
        UnconfiguredCustomerAccountDiscoveryClient()
    }

    @Provides
    @Singleton
    fun provideCustomerAccountAuthorizationCoordinator(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>
    ): CustomerAccountAuthorizationCoordinator = CustomerAccountAuthorizationCoordinator(
        configuration.applicationComposition.capabilities.customerAccount,
        discoveryClient::get
    )

    @Provides
    @Singleton
    fun provideCustomerAccountTokenClient(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>
    ): CustomerAccountTokenClient = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true) {
        ShopifyCustomerAccountTokenClient(
            requireNotNull(configuration.enabledCustomerAccount()),
            discoveryClient.get()
        )
    } else {
        UnconfiguredCustomerAccountTokenClient()
    }

    @Provides
    @Singleton
    fun provideCustomerAccountLogoutClient(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>
    ): CustomerAccountLogoutClient = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() ==
        true
    ) {
        AndroidCustomerAccountLogoutClient(
            requireNotNull(configuration.enabledCustomerAccount()),
            discoveryClient.get()
        )
    } else {
        UnconfiguredCustomerAccountLogoutClient()
    }

    @Provides
    @Singleton
    fun provideCustomerAccountSessionCoordinator(
        configuration: AppConfiguration,
        tokenClient: Provider<CustomerAccountTokenClient>,
        logoutClient: Provider<CustomerAccountLogoutClient>,
        sessionStore: Provider<CustomerSessionStore>
    ): CustomerAccountSessionCoordinator = CustomerAccountSessionCoordinator(
        capability = configuration.applicationComposition.capabilities.customerAccount,
        tokenClient = tokenClient::get,
        sessionStore = sessionStore::get,
        logoutClient = logoutClient::get
    )

    @Provides
    @Singleton
    fun provideCustomerAccountGateway(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>,
        sessionCoordinator: CustomerAccountSessionCoordinator
    ): CustomerAccountGateway = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true) {
        CustomerAccountApolloClientFactory.createGateway(
            discoveryClient.get(),
            CustomerSessionResolver { sessionCoordinator.restore() }
        )
    } else {
        UnconfiguredCustomerAccountGateway()
    }

    @Provides
    @Singleton
    fun provideCustomerProfileGateway(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>,
        sessionCoordinator: CustomerAccountSessionCoordinator
    ): CustomerProfileGateway = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true) {
        CustomerProfileApolloClientFactory.createGateway(
            discoveryClient.get(),
            CustomerSessionResolver { sessionCoordinator.restore() }
        )
    } else {
        UnconfiguredCustomerProfileGateway()
    }

    @Provides
    @Singleton
    fun provideCustomerAddressGateway(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>,
        sessionCoordinator: CustomerAccountSessionCoordinator
    ): CustomerAddressGateway = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true) {
        CustomerAddressApolloClientFactory.createGateway(
            discoveryClient.get(),
            CustomerSessionResolver { sessionCoordinator.restore() }
        )
    } else {
        UnconfiguredCustomerAddressGateway()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object CustomerOrderModule {
    @Provides
    @Singleton
    fun provideCustomerOrderGateway(
        configuration: AppConfiguration,
        discoveryClient: Provider<CustomerAccountDiscoveryClient>,
        sessionCoordinator: CustomerAccountSessionCoordinator
    ): CustomerOrderGateway = if (configuration.enabledCustomerAccount()?.validationIssues()?.isEmpty() == true) {
        CustomerOrderApolloClientFactory.createGateway(
            discoveryClient.get(),
            CustomerSessionResolver { sessionCoordinator.restore() }
        )
    } else {
        UnconfiguredCustomerOrderGateway()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProductionAccountModule {
    @Binds
    @Singleton
    abstract fun bindAccountController(implementation: DefaultAccountController): AccountController

    @Binds
    @Singleton
    abstract fun bindAccountDeletionController(
        implementation: DefaultAccountDeletionController
    ): AccountDeletionController

    @Binds
    @Singleton
    abstract fun bindProfileController(implementation: DefaultProfileController): ProfileController

    @Binds
    @Singleton
    abstract fun bindAddressController(implementation: DefaultAddressController): AddressController

    @Binds
    @Singleton
    abstract fun bindOrderController(implementation: DefaultOrderController): OrderController
}

private fun AppConfiguration.enabledCustomerAccount(): CustomerAccountConfiguration? =
    (applicationComposition.capabilities.customerAccount as? CustomerAccountCapability.Enabled)?.configuration
