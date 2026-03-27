/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.SoundCloudQueue
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.utils.isSoundCloudId
import com.metrolist.music.utils.stripSoundCloudPrefix
import com.metrolist.music.viewmodels.SoundCloudLibraryViewModel
import com.metrolist.music.extensions.togglePlayPause

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SoundCloudLibraryScreen(
    navController: NavController,
    viewModel: SoundCloudLibraryViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val profile by viewModel.profile.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val repostedTracks by viewModel.repostedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val followings by viewModel.followings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val likedTracksTitle = stringResource(R.string.soundcloud_liked_tracks)
    val repostedTracksTitle = stringResource(R.string.soundcloud_reposted_tracks)

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            error != null -> {
                EmptyPlaceholder(
                    icon = R.drawable.cloud,
                    text = error ?: stringResource(R.string.error_unknown),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            likedTracks.isEmpty() && repostedTracks.isEmpty() && playlists.isEmpty() && followings.isEmpty() -> {
                EmptyPlaceholder(
                    icon = R.drawable.cloud,
                    text = stringResource(R.string.soundcloud_library_empty),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                        NavigationTitle(profile?.fullName ?: profile?.username ?: stringResource(R.string.soundcloud_library_title))
                    }

                    if (likedTracks.isNotEmpty()) {
                        item(key = "liked_header", contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(stringResource(R.string.soundcloud_liked_tracks))
                        }

                        itemsIndexed(
                            items = likedTracks,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> CONTENT_TYPE_SONG },
                        ) { index, song ->
                            YouTubeListItem(
                                item = song,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                SoundCloudQueue(
                                                    tracks = likedTracks.mapNotNull { viewModel.songToTrack(it.id) },
                                                    title = likedTracksTitle,
                                                    startIndex = index,
                                                    preloadItem = song.toMediaMetadata(),
                                                )
                                            )
                                        }
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    if (repostedTracks.isNotEmpty()) {
                        item(key = "reposted_header", contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(stringResource(R.string.soundcloud_reposted_tracks))
                        }

                        itemsIndexed(
                            items = repostedTracks,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> CONTENT_TYPE_SONG },
                        ) { index, song ->
                            YouTubeListItem(
                                item = song,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                SoundCloudQueue(
                                                    tracks = repostedTracks.mapNotNull { viewModel.songToTrack(it.id) },
                                                    title = repostedTracksTitle,
                                                    startIndex = index,
                                                    preloadItem = song.toMediaMetadata(),
                                                )
                                            )
                                        }
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item(key = "playlists_header", contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(stringResource(R.string.filter_playlists))
                        }

                        itemsIndexed(playlists, key = { _, item -> item.id }) { _, playlist ->
                            YouTubeListItem(
                                item = playlist,
                                isActive = false,
                                isPlaying = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("soundcloud_playlist/${playlist.id.stripSoundCloudPrefix()}")
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    if (followings.isNotEmpty()) {
                        item(key = "following_header", contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(stringResource(R.string.soundcloud_following))
                        }

                        itemsIndexed(followings, key = { _, item -> item.id }) { _, artist ->
                            YouTubeListItem(
                                item = artist,
                                isActive = mediaMetadata?.artists?.any { it.id == artist.id } == true,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (artist.id.isSoundCloudId()) {
                                            navController.navigate("soundcloud_artist/${artist.id.stripSoundCloudPrefix()}")
                                        }
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    item(key = "footer_spacer") {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}
