/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.soundcloud

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.SoundCloudQueue
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.utils.toSongItem
import com.metrolist.music.viewmodels.SoundCloudPlaylistViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SoundCloudPlaylistScreen(
    navController: NavController,
    viewModel: SoundCloudPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current

    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (isLoading && playlist == null) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (error != null) {
                item(key = "error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                playlist?.let { playlistItem ->
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(playlistItem.artworkUrl ?: playlistItem.user?.avatarUrl)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = playlistItem.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            playlistItem.user?.let { user ->
                                Text(
                                    text = user.fullName?.takeIf(String::isNotBlank) ?: user.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            playlistItem.description?.takeIf(String::isNotBlank)?.let { description ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    if (tracks.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            SoundCloudQueue(
                                                tracks = tracks,
                                                title = playlistItem.title,
                                                startIndex = 0,
                                                preloadItem = tracks.firstOrNull()?.toSongItem()?.toMediaMetadata(),
                                            )
                                        )
                                    }
                                },
                                enabled = tracks.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.play))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = playlistItem.trackCount?.let { "$it tracks" } ?: "${tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        val songItem = remember(track) { track.toSongItem() }
                        YouTubeListItem(
                            item = songItem,
                            isActive = mediaMetadata?.id == songItem.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (songItem.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                SoundCloudQueue(
                                                    tracks = tracks,
                                                    title = playlistItem.title,
                                                    startIndex = index,
                                                    preloadItem = songItem.toMediaMetadata(),
                                                )
                                            )
                                        }
                                    },
                                ),
                        )
                    }

                    if (tracks.isEmpty()) {
                        item {
                            EmptyPlaceholder(
                                icon = R.drawable.search,
                                text = stringResource(R.string.no_results_found),
                            )
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = {
                Text(
                    text = playlist?.title ?: "SoundCloud Playlist",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
    }
}
