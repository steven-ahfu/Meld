package com.metrolist.music.providers.soundcloud

import com.metrolist.soundcloud.auth.SoundCloudAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SoundCloudLoginCoordinator {
    private val _callbackUrl = MutableStateFlow<String?>(null)
    val callbackUrl: StateFlow<String?> = _callbackUrl
    private var pkceSession: SoundCloudAuth.PkceSession? = null

    @Synchronized
    fun ensurePkceSession(
        clientId: String,
        redirectUri: String,
    ): SoundCloudAuth.PkceSession {
        return pkceSession ?: SoundCloudAuth.newPkceSession(
            clientId = clientId,
            redirectUri = redirectUri,
        ).also { pkceSession = it }
    }

    @Synchronized
    fun currentPkceSession(): SoundCloudAuth.PkceSession? = pkceSession

    fun onCallback(callbackUrl: String) {
        _callbackUrl.value = callbackUrl
    }

    fun clearCallback(callbackUrl: String? = null) {
        if (callbackUrl == null || _callbackUrl.value == callbackUrl) {
            _callbackUrl.value = null
        }
    }

    @Synchronized
    fun clearSession() {
        pkceSession = null
    }
}
