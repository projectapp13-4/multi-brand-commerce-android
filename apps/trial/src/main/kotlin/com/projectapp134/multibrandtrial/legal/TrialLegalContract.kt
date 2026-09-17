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
    SETUP_REQUIRED
}

data class TrialLegalEntry(val role: TrialLegalRole, val state: TrialLegalState, val intendedUri: String)

data class TrialLegalContract(val entries: List<TrialLegalEntry>) {
    init {
        require(entries.map(TrialLegalEntry::role).toSet() == TrialLegalRole.entries.toSet())
        require(entries.size == TrialLegalRole.entries.size)
        require(entries.all { it.state == TrialLegalState.SETUP_REQUIRED })
        require(entries.map(TrialLegalEntry::intendedUri).toSet().size == entries.size)
    }

    companion object {
        fun setupRequired(origin: String, paths: Map<TrialLegalRole, String>): TrialLegalContract {
            val parsedOrigin = URI(origin)
            require(parsedOrigin.scheme == "https" && !parsedOrigin.host.isNullOrBlank())
            require(parsedOrigin.path.isNullOrEmpty() && parsedOrigin.query == null && parsedOrigin.fragment == null)
            require(paths.keys == TrialLegalRole.entries.toSet())
            require(paths.values.all { it.startsWith("/setup-required/") && "//" !in it })
            return TrialLegalContract(
                TrialLegalRole.entries.map { role ->
                    TrialLegalEntry(
                        role = role,
                        state = TrialLegalState.SETUP_REQUIRED,
                        intendedUri = origin + paths.getValue(role)
                    )
                }
            )
        }
    }
}

data class TrialDeletionContract(
    val state: TrialLegalState,
    val privacy: TrialLegalEntry,
    val request: TrialLegalEntry
) {
    init {
        require(state == TrialLegalState.SETUP_REQUIRED)
        require(privacy.role == TrialLegalRole.PRIVACY)
        require(request.role == TrialLegalRole.SUPPORT)
    }

    companion object {
        fun setupRequired(legal: TrialLegalContract): TrialDeletionContract {
            val entriesByRole = legal.entries.associateBy(TrialLegalEntry::role)
            return TrialDeletionContract(
                state = TrialLegalState.SETUP_REQUIRED,
                privacy = entriesByRole.getValue(TrialLegalRole.PRIVACY),
                request = entriesByRole.getValue(TrialLegalRole.SUPPORT)
            )
        }
    }
}
