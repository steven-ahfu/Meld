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
import com.metrolist.soundcloud.models.SoundCloudPlaylistDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SoundCloudPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId = savedStateHandle.get<String>("playlistId")?.removePrefix("soundcloud:")
    private val playlistJson = savedStateHandle.get<String>("playlistJson")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val _playlist = MutableStateFlow<SoundCloudPlaylistDto?>(decodePlaylist())
    val playlist = _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow(_playlist.value == null)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val tracks = MutableStateFlow(_playlist.value?.tracks.orEmpty())

    init {
        if (_playlist.value == null) {
            loadPlaylist()
        }
    }

    fun retry() {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        val id = playlistId
        if (id.isNullOrBlank()) {
            _error.value = "Missing SoundCloud playlist id"
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            SoundCloudTokenManager.ensureAuthenticated()
            SoundCloud.playlist(id)
                .onSuccess { dto ->
                    _playlist.value = dto
                    tracks.value = dto.tracks
                    _isLoading.value = false
                }
                .onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load SoundCloud playlist"
                    _isLoading.value = false
                }
        }
    }

    private fun decodePlaylist(): SoundCloudPlaylistDto? {
        val raw = playlistJson ?: return null
        return runCatching {
            json.decodeFromString(SoundCloudPlaylistDto.serializer(), raw)
        }.getOrNull()
    }
}
