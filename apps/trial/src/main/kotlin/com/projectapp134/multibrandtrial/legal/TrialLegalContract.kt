package com.projectapp134.multibrandtrial.legal

import java.net.URI

enum class TrialLegalRole {
    SUPPORT,
    PRIVACY,
    TERMS,
    SHIPPING,
    RETURNS,
    LEGAL_NOTICE
}

enum class TrialLegalState {
    SETUP_REQUIRED,
    DEVELOPMENT_VERIFIED
}

data class TrialLegalEntry(val role: TrialLegalRole, val state: TrialLegalState, val intendedUri: String)

data class TrialLegalContract(val entries: List<TrialLegalEntry>) {
    init {
        require(entries.map(TrialLegalEntry::role).toSet() == TrialLegalRole.entries.toSet())
        require(entries.size == TrialLegalRole.entries.size)
        require(entries.map(TrialLegalEntry::state).toSet().size == 1)
        require(entries.map(TrialLegalEntry::intendedUri).toSet().size == entries.size)
        entries.forEach { entry -> requireValidPageUri(entry.intendedUri) }
    }

    companion object {
        fun setupRequired(origin: String, paths: Map<TrialLegalRole, String>): TrialLegalContract = create(
            origin = origin,
            paths = paths,
            state = TrialLegalState.SETUP_REQUIRED,
            requiredPrefix = "/setup-required/"
        )

        fun developmentVerified(origin: String, paths: Map<TrialLegalRole, String>): TrialLegalContract = create(
            origin = origin,
            paths = paths,
            state = TrialLegalState.DEVELOPMENT_VERIFIED,
            requiredPrefix = "/pages/"
        )

        private fun create(
            origin: String,
            paths: Map<TrialLegalRole, String>,
            state: TrialLegalState,
            requiredPrefix: String
        ): TrialLegalContract {
            val parsedOrigin = URI(origin)
            require(
                parsedOrigin.scheme == "https" &&
                    !parsedOrigin.host.isNullOrBlank() &&
                    parsedOrigin.userInfo == null &&
                    parsedOrigin.port == -1
            )
            require(parsedOrigin.path.isNullOrEmpty() && parsedOrigin.query == null && parsedOrigin.fragment == null)
            require(paths.keys == TrialLegalRole.entries.toSet())
            require(
                paths.values.all { path ->
                    path.startsWith(requiredPrefix) &&
                        "//" !in path &&
                        URI(path).normalize().path == path &&
                        URI(path).query == null &&
                        URI(path).fragment == null
                }
            )
            return TrialLegalContract(
                TrialLegalRole.entries.map { role ->
                    TrialLegalEntry(role = role, state = state, intendedUri = origin + paths.getValue(role))
                }
            )
        }

        private fun requireValidPageUri(rawUri: String) {
            val uri = URI(rawUri)
            require(
                uri.scheme == "https" &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.port == -1 &&
                    uri.query == null &&
                    uri.fragment == null &&
                    uri.path.startsWith('/') &&
                    uri.normalize().path == uri.path
            )
        }
    }
}

data class TrialDeletionContract(val state: TrialLegalState, val privacy: TrialLegalEntry) {
    init {
        require(privacy.role == TrialLegalRole.PRIVACY)
        require(privacy.state == state)
    }

    companion object {
        fun setupRequired(legal: TrialLegalContract): TrialDeletionContract {
            require(legal.entries.all { it.state == TrialLegalState.SETUP_REQUIRED })
            return create(legal, TrialLegalState.SETUP_REQUIRED)
        }

        fun developmentVerified(legal: TrialLegalContract): TrialDeletionContract {
            require(legal.entries.all { it.state == TrialLegalState.DEVELOPMENT_VERIFIED })
            return create(legal, TrialLegalState.DEVELOPMENT_VERIFIED)
        }

        private fun create(legal: TrialLegalContract, state: TrialLegalState): TrialDeletionContract {
            val entriesByRole = legal.entries.associateBy(TrialLegalEntry::role)
            return TrialDeletionContract(
                state = state,
                privacy = entriesByRole.getValue(TrialLegalRole.PRIVACY)
            )
        }
    }
}
