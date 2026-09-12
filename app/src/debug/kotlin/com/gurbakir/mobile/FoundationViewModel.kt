package com.gurbakir.mobile

import androidx.lifecycle.ViewModel
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.foundation.config.ConfigurationIssue
import com.gurbakir.foundation.config.EnvironmentId
import com.gurbakir.foundation.logging.LogEvent
import com.gurbakir.foundation.logging.ProjectLogger
import com.gurbakir.foundation.logging.SafeAttribute
import com.gurbakir.mobile.navigation.IntegrationId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class FoundationViewModel
@Inject
constructor(configuration: AppConfiguration, logger: ProjectLogger) :
    ViewModel() {
    private val _state = MutableStateFlow(FoundationUiState.from(configuration, BuildConfig.FIREBASE_CONFIGURED))
    val state: StateFlow<FoundationUiState> = _state.asStateFlow()

    init {
        logger.info(
            event = LogEvent.of("foundation.started"),
            attributes =
                mapOf(
                    SafeAttribute.ENVIRONMENT to configuration.environment.name.lowercase(),
                    SafeAttribute.RESULT to
                        if (configuration.validationIssues.isEmpty()) "configured" else "incomplete"
                )
        )
    }
}

data class FoundationUiState(
    val brandDisplayName: String,
    val environment: EnvironmentId,
    val configurationIssues: Set<ConfigurationIssue>,
    val integrations: List<IntegrationStatus>
) {
    companion object {
        fun from(configuration: AppConfiguration, firebaseConfigured: Boolean): FoundationUiState = FoundationUiState(
            brandDisplayName = configuration.brand.displayName,
            environment = configuration.environment,
            configurationIssues = configuration.validationIssues,
            integrations =
                listOf(
                    IntegrationStatus(
                        id = IntegrationId.STOREFRONT,
                        ready = configuration.storefront.validationIssues().isEmpty()
                    ),
                    IntegrationStatus(
                        id = IntegrationId.CUSTOMER_ACCOUNT,
                        ready = (
                            configuration.applicationComposition.capabilities.customerAccount as?
                                com.gurbakir.foundation.config.CustomerAccountCapability.Enabled
                            )
                            ?.configuration?.validationIssues()?.isEmpty() == true
                    ),
                    IntegrationStatus(
                        id = IntegrationId.CHECKOUT,
                        ready = configuration.storefront.validationIssues().isEmpty()
                    ),
                    IntegrationStatus(
                        id = IntegrationId.FIREBASE,
                        ready = firebaseConfigured
                    )
                )
        )
    }
}

data class IntegrationStatus(val id: IntegrationId, val ready: Boolean)
