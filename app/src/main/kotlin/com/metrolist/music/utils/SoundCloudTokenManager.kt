package com.metrolist.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.SoundCloudAvatarUrlKey
import com.metrolist.music.constants.SoundCloudClientIdKey
import com.metrolist.music.constants.SoundCloudRefreshTokenKey
import com.metrolist.music.constants.SoundCloudTokenExpiryKey
import com.metrolist.music.constants.SoundCloudUserIdKey
import com.metrolist.music.constants.SoundCloudUsernameKey
import com.metrolist.soundcloud.SoundCloud
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object SoundCloudTokenManager {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var clientId: String
    private val refreshMutex = Mutex()

    private val _needsReLogin = MutableStateFlow(false)
    val needsReLogin: StateFlow<Boolean> = _needsReLogin.asStateFlow()

    fun init(
        dataStore: DataStore<Preferences>,
        clientId: String,
    ) {
        this.dataStore = dataStore
        this.clientId = clientId
    }

    suspend fun ensureAuthenticated(): Boolean {
        if (!::dataStore.isInitialized) return false
        val settings = dataStore.data.first()
        val accessToken = settings[SoundCloudAccessTokenKey].orEmpty()
        val expiry = settings[SoundCloudTokenExpiryKey] ?: 0L

        if (accessToken.isBlank()) {
            return false
        }

        if (System.currentTimeMillis() < expiry || expiry == 0L) {
            SoundCloud.accessToken = accessToken
            SoundCloud.refreshToken = settings[SoundCloudRefreshTokenKey]
            return true
        }

        return refreshMutex.withLock {
            val freshSettings = dataStore.data.first()
            val freshToken = freshSettings[SoundCloudAccessTokenKey].orEmpty()
            val freshExpiry = freshSettings[SoundCloudTokenExpiryKey] ?: 0L
            if (freshToken.isNotBlank() && (freshExpiry == 0L || System.currentTimeMillis() < freshExpiry)) {
                SoundCloud.accessToken = freshToken
                SoundCloud.refreshToken = freshSettings[SoundCloudRefreshTokenKey]
                return@withLock true
            }

            val refreshToken = freshSettings[SoundCloudRefreshTokenKey].orEmpty()
            if (refreshToken.isBlank() || clientId.isBlank()) {
                // No refresh token (browser-based auth) — require re-login
                _needsReLogin.value = true
                return@withLock false
            }

            try {
                val token = SoundCloud.refreshAccessToken(
                    clientId = clientId,
                    clientSecret = "", // Not available with browser-based auth
                    token = refreshToken,
                )
                val expiresAt = token.expiresInSeconds?.let {
                    System.currentTimeMillis() + (it * 1000L) - 30_000L
                } ?: 0L
                SoundCloud.accessToken = token.accessToken
                SoundCloud.refreshToken = token.refreshToken ?: refreshToken
                dataStore.edit { prefs ->
                    prefs[SoundCloudAccessTokenKey] = token.accessToken
                    prefs[SoundCloudRefreshTokenKey] = token.refreshToken ?: refreshToken
                    prefs[SoundCloudTokenExpiryKey] = expiresAt
                }
                _needsReLogin.value = false
                true
            } catch (error: Exception) {
                Timber.e(error, "SoundCloudTokenManager refresh failed")
                dataStore.edit { prefs ->
                    prefs.remove(SoundCloudAccessTokenKey)
                    prefs.remove(SoundCloudRefreshTokenKey)
                    prefs.remove(SoundCloudTokenExpiryKey)
                }
                SoundCloud.accessToken = null
                SoundCloud.refreshToken = null
                _needsReLogin.value = true
                false
            }
        }
    }

    /**
     * Try using BuildConfig credentials directly (client_secret as access token).
     * If the API responds successfully, persist them and consider the user logged in.
     */
    suspend fun tryBuildConfigCredentials(
        clientId: String,
        clientSecret: String,
    ): Boolean {
        if (!::dataStore.isInitialized) return false

        SoundCloud.accessToken = clientSecret
        SoundCloud.clientId = clientId
        val user = try {
            SoundCloud.me().getOrNull()
        } catch (e: Exception) {
            Timber.w(e, "BuildConfig SoundCloud credentials failed API check")
            SoundCloud.accessToken = null
            return false
        }

        if (user == null) {
            Timber.w("BuildConfig SoundCloud credentials returned no user")
            SoundCloud.accessToken = null
            return false
        }

        Timber.d("BuildConfig SoundCloud credentials valid, persisting (user: %s)", user.username)
        this.clientId = clientId
        SoundCloud.userId = user.id.toString()
        dataStore.edit { prefs ->
            prefs[SoundCloudClientIdKey] = clientId
            prefs[SoundCloudAccessTokenKey] = clientSecret
            prefs[SoundCloudTokenExpiryKey] = 0L // no expiry known
            prefs[SoundCloudUserIdKey] = user.id.toString()
            prefs[SoundCloudUsernameKey] = user.username
            user.avatarUrl?.let { prefs[SoundCloudAvatarUrlKey] = it }
        }
        _needsReLogin.value = false
        return true
    }

    fun clearReLoginFlag() {
        _needsReLogin.value = false
    }
}
