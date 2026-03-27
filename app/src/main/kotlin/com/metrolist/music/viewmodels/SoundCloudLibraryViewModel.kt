/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.utils.toTrackDtoOrNull
import com.metrolist.music.utils.toArtistItem
import com.metrolist.music.utils.toPlaylistItem
import com.metrolist.music.utils.toSongItem
import com.metrolist.music.utils.SoundCloudTokenManager
import com.metrolist.soundcloud.SoundCloud
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import com.metrolist.soundcloud.models.SoundCloudUserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundCloudLibraryViewModel @Inject constructor() : ViewModel() {
    private val _profile = MutableStateFlow<SoundCloudUserDto?>(null)
    val profile = _profile.asStateFlow()

    private val _likedTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    private val _repostedTracks = MutableStateFlow<List<SongItem>>(emptyList())
    val repostedTracks = _repostedTracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _followings = MutableStateFlow<List<ArtistItem>>(emptyList())
    val followings = _followings.asStateFlow()
    private val likedTrackMap = mutableMapOf<String, SoundCloudTrackDto>()
    private val repostedTrackMap = mutableMapOf<String, SoundCloudTrackDto>()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            runCatching {
                SoundCloudTokenManager.ensureAuthenticated()
                val me = SoundCloud.me().getOrThrow()
                val likesPage = SoundCloud.likes().getOrThrow()
                val streamPage = SoundCloud.stream().getOrThrow()
                val selfPlaylistsPage = SoundCloud.selfPlaylists().getOrThrow()
                val likedPlaylistsPage = SoundCloud.playlists().getOrThrow()
                val followingsPage = SoundCloud.followings().getOrThrow()

                _profile.value = me
                _likedTracks.value = likesPage.collection.mapNotNull { like ->
                    like.track?.toSongItem() ?: like.origin?.toTrackDtoOrNull()?.toSongItem()
                }.distinctBy { it.id }.also { songs ->
                    likedTrackMap.clear()
                    likesPage.collection.forEach { like ->
                        val track = like.track ?: like.origin?.toTrackDtoOrNull()
                        val song = track?.toSongItem()
                        if (track != null && song != null) {
                            likedTrackMap[song.id] = track
                        }
                    }
                }
                _repostedTracks.value = streamPage.collection.mapNotNull { activity ->
                    activity.origin?.toTrackDtoOrNull()?.toSongItem()
                        ?: activity.track?.toSongItem()
                }.distinctBy { it.id }.also {
                    repostedTrackMap.clear()
                    streamPage.collection.forEach { activity ->
                        val track = activity.origin?.toTrackDtoOrNull() ?: activity.track
                        val song = track?.toSongItem()
                        if (track != null && song != null) {
                            repostedTrackMap[song.id] = track
                        }
                    }
                }

                _playlists.value = (likedPlaylistsPage.collection + selfPlaylistsPage.collection)
                    .distinctBy { it.id }
                    .map { it.toPlaylistItem() }

                _followings.value = followingsPage.collection
                    .distinctBy { it.id }
                    .map { it.toArtistItem() }
            }.onFailure {
                _error.value = it.message ?: "Failed to load SoundCloud library"
            }

            _isLoading.value = false
        }
    }

    fun songToTrack(songId: String): SoundCloudTrackDto? = likedTrackMap[songId] ?: repostedTrackMap[songId]
}
