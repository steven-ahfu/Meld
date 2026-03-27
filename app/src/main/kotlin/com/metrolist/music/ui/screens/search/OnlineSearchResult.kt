/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.playback.queues.SoundCloudQueue
import com.metrolist.music.playback.queues.SpotifyQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.isSoundCloudId
import com.metrolist.music.utils.isSpotifyId
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.stripSpotifyPrefix
import com.metrolist.music.utils.toSpotifyTrackStub
import com.metrolist.music.viewmodels.OnlineSearchViewModel
import com.metrolist.music.constants.OnlineProvider
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var isSearchFocused by remember { mutableStateOf(false) }
    val enabledProviders by viewModel.enabledProviders.collectAsState()

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

    // Extract query from navigation arguments
    val encodedQuery = navController.currentBackStackEntry?.arguments?.getString("query") ?: ""
    val decodedQuery = remember(encodedQuery) {
        try {
            URLDecoder.decode(encodedQuery, "UTF-8")
        } catch (e: Exception) {
            encodedQuery
        }
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }


    val onSearch: (String) -> Unit = remember {
        { searchQuery ->
            if (searchQuery.isNotEmpty()) {
                isSearchFocused = false
                focusManager.clearFocus()

                navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}") {
                    popUpTo("search/${URLEncoder.encode(decodedQuery, "UTF-8")}") {
                        inclusive = true
                    }

                    if (!pauseSearchHistory) {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                insert(SearchHistory(query = searchQuery))
                            }
                        }
                    }
                }
            }
        }
    }

    // Update query when decodedQuery changes
    LaunchedEffect(decodedQuery) {
        query = TextFieldValue(decodedQuery, TextRange(decodedQuery.length))
    }

    val activeFilterValue by viewModel.activeFilter.collectAsState()
    val searchSummary = viewModel.summaryPage

    val itemsPage: ItemsPage? by remember(activeFilterValue) {
        derivedStateOf {
            activeFilterValue?.let { viewModel.viewStateMap[it] }
        }
    }

    val hasActiveFilter = activeFilterValue != null

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val isSoundCloudItem = item.id.isSoundCloudId()
        val longClick = {
            if (!isSoundCloudItem) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    when (item) {
                        is SongItem ->
                            YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )

                        is AlbumItem ->
                            YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )

                        is ArtistItem ->
                            YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss,
                            )

                        is PlaylistItem ->
                            YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )

                        is PodcastItem ->
                            YouTubePlaylistMenu(
                                playlist = item.asPlaylistItem(),
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )

                        is EpisodeItem ->
                            YouTubeSongMenu(
                                song = item.asSongItem(),
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                    }
                }
            }
        }
        YouTubeListItem(
            item = item,
            isActive =
            when (item) {
                is SongItem -> mediaMetadata?.id == item.id
                is AlbumItem -> mediaMetadata?.album?.id == item.id
                is EpisodeItem -> mediaMetadata?.id == item.id
                else -> false
            },
            isPlaying = isPlaying,
            trailingContent = {
                if (!isSoundCloudItem) {
                    IconButton(
                        onClick = longClick,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier =
            Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else if (item.id.isSoundCloudId()) {
                                    val tracks = viewModel.currentSoundCloudTracks(activeFilterValue)
                                    val selectedTrack = viewModel.soundCloudTrack(item.id)
                                    val selectedIndex = selectedTrack?.let { track ->
                                        tracks.indexOfFirst { it.id == track.id }
                                    } ?: -1
                                    if (tracks.isNotEmpty() && selectedIndex >= 0) {
                                        playerConnection.playQueue(
                                            SoundCloudQueue(
                                                tracks = tracks,
                                                title = item.title,
                                                startIndex = selectedIndex,
                                                preloadItem = item.toMediaMetadata(),
                                            )
                                        )
                                    }
                                } else if (item.id.isSpotifyId()) {
                                    // Spotify search result: create SpotifyQueue
                                    val spotifyTrack = item.toSpotifyTrackStub()
                                    if (spotifyTrack != null) {
                                        playerConnection.playQueue(
                                            SpotifyQueue(
                                                initialTrack = spotifyTrack,
                                                mapper = viewModel.spotifyYouTubeMapper,
                                            )
                                        )
                                    }
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = item.id),
                                            item.toMediaMetadata()
                                        )
                                    )
                                }
                            }

                            is AlbumItem -> {
                                if (item.id.isSoundCloudId()) {
                                    return@combinedClickable
                                } else if (item.id.isSpotifyId()) {
                                    coroutineScope.launch {
                                        val searchQuery = "${item.title} ${item.artists?.firstOrNull()?.name.orEmpty()}"
                                        val ytResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                                        val ytAlbum = ytResult?.items?.firstOrNull { it is AlbumItem }
                                        if (ytAlbum != null) {
                                            navController.navigate("album/${ytAlbum.id}")
                                        }
                                    }
                                } else {
                                    navController.navigate("album/${item.id}")
                                }
                            }
                            is ArtistItem -> {
                                if (item.id.isSoundCloudId()) {
                                    navController.navigate("soundcloud_artist/${item.id.removePrefix("soundcloud:")}")
                                } else if (item.id.isSpotifyId()) {
                                    coroutineScope.launch {
                                        val ytResult = YouTube.search(item.title, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                                        val ytArtist = ytResult?.items?.firstOrNull { it is ArtistItem }
                                        if (ytArtist != null) {
                                            navController.navigate("artist/${ytArtist.id}")
                                        }
                                    }
                                } else {
                                    navController.navigate("artist/${item.id}")
                                }
                            }
                            is PlaylistItem -> {
                                if (item.id.isSoundCloudId()) {
                                    navController.navigate("soundcloud_playlist/${item.id.removePrefix("soundcloud:")}")
                                } else if (item.id.isSpotifyId()) {
                                    navController.navigate("spotify_playlist/${item.id.stripSpotifyPrefix()}")
                                } else {
                                    navController.navigate("online_playlist/${item.id}")
                                }
                            }
                            is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                            is EpisodeItem -> {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = item.id),
                                            item.toMediaMetadata()
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onLongClick = longClick,
                )
                .animateItem(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        // Google-style SearchBar with Material 3 design
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = { navController.navigateUp() }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = TextFieldValue("")
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch(query.text)
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (pureBlack)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = if (pureBlack)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        isSearchFocused = true
                    }
                }
        )

        // Main content area below search bar
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                run {
                    val hasYouTube = OnlineProvider.YOUTUBE_MUSIC in enabledProviders
                    val chips = mutableListOf(
                        null to stringResource(R.string.filter_all),
                        "songs" to stringResource(R.string.filter_songs),
                    )
                    if (hasYouTube) chips.add("videos" to stringResource(R.string.filter_videos))
                    chips.add("albums" to stringResource(R.string.filter_albums))
                    chips.add("artists" to stringResource(R.string.filter_artists))
                    chips.add("playlists" to stringResource(R.string.filter_playlists))
                    if (hasYouTube) {
                        chips.add("featured_playlists" to stringResource(R.string.filter_featured_playlists))
                        chips.add("podcasts" to stringResource(R.string.filter_podcasts))
                    }

                    ChipsRow(
                        chips = chips,
                        currentValue = activeFilterValue,
                        onValueUpdate = { newFilter ->
                            viewModel.activeFilter.value = newFilter
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!hasActiveFilter) {
                    searchSummary?.summaries?.forEach { summary ->
                        item {
                            NavigationTitle(summary.title)
                        }

                        items(
                            items = summary.items,
                            key = { "${summary.title}/${it.id}/${summary.items.indexOf(it)}" },
                            itemContent = ytItemContent,
                        )
                    }

                    if (searchSummary?.summaries?.isEmpty() == true) {
                        item {
                            EmptyPlaceholder(
                                icon = R.drawable.search,
                                text = stringResource(R.string.no_results_found),
                            )
                        }
                    }
                } else {
                    items(
                        items = itemsPage?.items.orEmpty().distinctBy { it.id },
                        key = { "filtered_${it.id}" },
                        itemContent = ytItemContent,
                    )

                    if (itemsPage?.continuation != null) {
                        item(key = "loading") {
                            ShimmerHost {
                                repeat(3) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    if (itemsPage?.items?.isEmpty() == true) {
                        item {
                            EmptyPlaceholder(
                                icon = R.drawable.search,
                                text = stringResource(R.string.no_results_found),
                            )
                        }
                    }
                }

                if (!hasActiveFilter && searchSummary == null || hasActiveFilter && itemsPage == null) {
                    item {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight))
                }
            }
        }
            if (isSearchFocused) {
                OnlineSearchScreen(
                    query = query.text,
                    onQueryChange = { query = it },
                    navController = navController,
                    onSearch = onSearch,
                    onDismiss = {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    pureBlack = pureBlack
                )
            }
            HideOnScrollFAB(
                lazyListState = lazyListState,
                icon = R.drawable.mic,
                onClick = { navController.navigate("recognition") }
            )
        }
    }
}
