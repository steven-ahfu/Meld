/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.utils.SoundCloudTokenManager
import com.metrolist.soundcloud.SoundCloud
import com.metrolist.soundcloud.models.SoundCloudCollectionPage
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import com.metrolist.soundcloud.models.SoundCloudUserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SoundCloudArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId = savedStateHandle.get<String>("artistId")?.removePrefix("soundcloud:")
    private val artistJson = savedStateHandle.get<String>("artistJson")
    private val artistName = savedStateHandle.get<String>("artistName")
    private val artistUsername = savedStateHandle.get<String>("artistUsername")
    private val artistAvatarUrl = savedStateHandle.get<String>("artistAvatarUrl")
    private val artistDescription = savedStateHandle.get<String>("artistDescription")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val _artist = MutableStateFlow(decodeArtist())
    val artist = _artist.asStateFlow()

    private val _tracks = MutableStateFlow<List<SoundCloudTrackDto>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var continuation: String? = null
    private var resolvedUserId: String? = decodeArtist()?.id?.toString() ?: artistId

    init {
        loadArtist()
    }

    fun retry() {
        loadArtist()
    }

    fun loadMore() {
        val userId = resolvedUserId ?: return
        val next = continuation ?: return

        _isLoadingMore.value = true
        viewModelScope.launch(Dispatchers.IO) {
            SoundCloud.userTracks(userId, next)
                .onSuccess { page ->
                    appendTracks(page)
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load more SoundCloud tracks"
                }
            _isLoadingMore.value = false
        }
    }

    private fun loadArtist() {
        _isLoading.value = true
        _error.value = null
        _hasMore.value = false
        continuation = null

        viewModelScope.launch(Dispatchers.IO) {
            SoundCloudTokenManager.ensureAuthenticated()
            val loadedArtist = _artist.value ?: resolveArtist() ?: fallbackArtist()
            if (loadedArtist == null) {
                _error.value = "Unable to resolve SoundCloud artist"
                _isLoading.value = false
                return@launch
            }

            _artist.value = loadedArtist
            resolvedUserId = loadedArtist.id.toString()

            SoundCloud.userTracks(loadedArtist.id.toString())
                .onSuccess { page ->
                    appendTracks(page)
                    _isLoading.value = false
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load SoundCloud artist"
                    _isLoading.value = false
                }
        }
    }

    private suspend fun resolveArtist(): SoundCloudUserDto? {
        val id = artistId ?: return null
        return SoundCloud.user(id).getOrNull()
    }

    private fun fallbackArtist(): SoundCloudUserDto? {
        val id = artistId?.toLongOrNull() ?: return null
        val username = artistUsername ?: artistName ?: return null
        return SoundCloudUserDto(
            id = id,
            username = username,
            fullName = artistName,
            avatarUrl = artistAvatarUrl,
            description = artistDescription,
        )
    }

    private fun appendTracks(page: SoundCloudCollectionPage<SoundCloudTrackDto>) {
        _tracks.value = (_tracks.value + page.collection).distinctBy { it.id }
        continuation = page.nextHref
        _hasMore.value = continuation != null
    }

    private fun decodeArtist(): SoundCloudUserDto? {
        val raw = artistJson ?: return null
        return runCatching {
            json.decodeFromString(SoundCloudUserDto.serializer(), raw)
        }.getOrNull()
    }
}
