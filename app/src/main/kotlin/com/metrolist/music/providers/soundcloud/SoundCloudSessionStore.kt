package com.metrolist.music.providers.soundcloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.EnableSoundCloudKey
import com.metrolist.music.constants.OnlineProvider
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.SoundCloudAvatarUrlKey
import com.metrolist.music.constants.SoundCloudRefreshTokenKey
import com.metrolist.music.constants.SoundCloudTokenExpiryKey
import com.metrolist.music.constants.SoundCloudUserIdKey
import com.metrolist.music.constants.SoundCloudUsernameKey
import com.metrolist.music.providers.MusicProviderSessionStore
import com.metrolist.music.providers.ProviderId
import com.metrolist.music.providers.ProviderSessionState
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class SoundCloudSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : MusicProviderSessionStore {
    override val provider: ProviderId = OnlineProvider.SOUNDCLOUD
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sessionState = MutableStateFlow(
        ProviderSessionState(
            isEnabled = false,
            isLoggedIn = false,
        )
    )

    override val sessionState: StateFlow<ProviderSessionState> = _sessionState

    init {
        scope.launch {
            context.dataStore.data.collectLatest { prefs ->
                _sessionState.value = ProviderSessionState(
                    isEnabled = prefs[EnableSoundCloudKey] ?: false,
                    isLoggedIn = !prefs[SoundCloudAccessTokenKey].isNullOrBlank(),
                    userDisplayName = prefs[SoundCloudUsernameKey],
                    avatarUrl = prefs[SoundCloudAvatarUrlKey],
                )
            }
        }
    }

    override suspend fun isAuthenticated(): Boolean =
        sessionState.value.isLoggedIn

    override suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(SoundCloudAccessTokenKey)
            prefs.remove(SoundCloudRefreshTokenKey)
            prefs.remove(SoundCloudTokenExpiryKey)
            prefs.remove(SoundCloudUserIdKey)
            prefs.remove(SoundCloudUsernameKey)
            prefs.remove(SoundCloudAvatarUrlKey)
            prefs[EnableSoundCloudKey] = false
        }
    }
}
