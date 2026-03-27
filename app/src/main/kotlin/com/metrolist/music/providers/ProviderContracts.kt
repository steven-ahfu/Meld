package com.metrolist.music.providers

import kotlinx.coroutines.flow.StateFlow

data class ProviderSessionState(
    val isEnabled: Boolean,
    val isLoggedIn: Boolean,
    val userDisplayName: String? = null,
    val avatarUrl: String? = null,
    val lastError: String? = null,
)

interface MusicProviderSessionStore {
    val provider: ProviderId
    val sessionState: StateFlow<ProviderSessionState>

    suspend fun isAuthenticated(): Boolean

    suspend fun clear()
}
