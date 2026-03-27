/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.music.constants.EnableSoundCloudKey
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.OnlineProvider
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.SpotifyAccessTokenKey
import com.metrolist.music.constants.UseSoundCloudSearchKey
import com.metrolist.music.constants.UseSpotifySearchKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.playback.SpotifyYouTubeMapper
import com.metrolist.music.utils.SoundCloudTokenManager
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.toAlbumItem
import com.metrolist.music.utils.toArtistItem as toSoundCloudArtistItem
import com.metrolist.music.utils.toArtistItem as toSpotifyArtistItem
import com.metrolist.music.utils.toPlaylistItem as toSoundCloudPlaylistItem
import com.metrolist.music.utils.toPlaylistItem as toSpotifyPlaylistItem
import com.metrolist.music.utils.toSongItem as toSoundCloudSongItem
import com.metrolist.music.utils.toSongItem as toSpotifySongItem
import com.metrolist.soundcloud.SoundCloud
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import com.metrolist.spotify.Spotify
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase,
) : ViewModel() {

    val spotifyYouTubeMapper = SpotifyYouTubeMapper(database)
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (_: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }

    /** Unified filter: null = summary view, or generic key like "songs", "albums", etc. */
    val activeFilter = MutableStateFlow<String?>(null)

    /** All providers that are enabled and authenticated for search. */
    val enabledProviders = MutableStateFlow<Set<OnlineProvider>>(setOf(OnlineProvider.YOUTUBE_MUSIC))

    /** Merged summary from all enabled providers. */
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        private set

    /** Merged filtered results. Keys are generic filter names: "songs", "albums", etc. */
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    // --- Per-provider internal state ---
    private val providerSummaries = mutableMapOf<OnlineProvider, SearchSummaryPage>()
    private val providerViewStates = mutableMapOf<String, ItemsPage>() // "YOUTUBE_MUSIC:songs" -> ItemsPage
    private val soundCloudTracksByItemId = mutableMapOf<String, SoundCloudTrackDto>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val providers = resolveEnabledProviders()
            enabledProviders.value = providers

            // Launch each provider's lifecycle in parallel
            for (provider in providers) {
                launch { initProvider(provider) }
            }
        }
    }

    private suspend fun resolveEnabledProviders(): Set<OnlineProvider> {
        val prefs = context.dataStore.data.first()
        val providers = mutableSetOf(OnlineProvider.YOUTUBE_MUSIC)

        val spotifyEnabled = prefs[EnableSpotifyKey] ?: false
        val useSpotify = prefs[UseSpotifySearchKey] ?: false
        val hasSpotifyToken = (prefs[SpotifyAccessTokenKey] ?: "").isNotEmpty()
        if (spotifyEnabled && useSpotify && hasSpotifyToken) {
            providers.add(OnlineProvider.SPOTIFY)
        }

        val soundCloudEnabled = prefs[EnableSoundCloudKey] ?: false
        val useSoundCloud = prefs[UseSoundCloudSearchKey] ?: false
        val hasSoundCloudToken = (prefs[SoundCloudAccessTokenKey] ?: "").isNotEmpty()
        if (soundCloudEnabled && useSoundCloud && hasSoundCloudToken) {
            providers.add(OnlineProvider.SOUNDCLOUD)
        }

        return providers
    }

    private suspend fun initProvider(provider: OnlineProvider) {
        // Load summary
        when (provider) {
            OnlineProvider.YOUTUBE_MUSIC -> loadYouTubeSummary()
            OnlineProvider.SPOTIFY -> loadSpotifySummary()
            OnlineProvider.SOUNDCLOUD -> loadSoundCloudSummary()
        }
        mergeSummaries()

        // Collect filter changes
        activeFilter.collect { filterKey ->
            if (filterKey != null) {
                when (provider) {
                    OnlineProvider.YOUTUBE_MUSIC -> loadYouTubeFiltered(filterKey)
                    OnlineProvider.SPOTIFY -> loadSpotifyFiltered(filterKey)
                    OnlineProvider.SOUNDCLOUD -> loadSoundCloudFiltered(filterKey)
                }
                mergeViewState(filterKey)
            }
        }
    }

    // ── Summary loaders ──────────────────────────────────────────────────

    private suspend fun loadYouTubeSummary() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        YouTube.searchSummary(query)
            .onSuccess { page ->
                val filtered = page
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                synchronized(providerSummaries) {
                    providerSummaries[OnlineProvider.YOUTUBE_MUSIC] = filtered
                }
            }
            .onFailure(::reportException)
    }

    private suspend fun loadSpotifySummary() {
        if (!SpotifyTokenManager.ensureAuthenticated()) {
            Timber.w("SearchVM: Spotify auth failed, skipping Spotify results")
            return
        }

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val result = Spotify.search(
            query = query,
            types = listOf("track", "album", "artist", "playlist"),
            limit = 10,
        ).getOrElse { firstError ->
            Timber.w(firstError, "SearchVM: Full Spotify search failed, retrying without playlists")
            Spotify.search(
                query = query,
                types = listOf("track", "album", "artist"),
                limit = 10,
            ).getOrElse { secondError ->
                Timber.e(secondError, "SearchVM: Spotify search failed completely")
                reportException(secondError)
                return
            }
        }

        val summaries = mutableListOf<SearchSummary>()

        result.tracks?.items?.filter { it.id.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { tracks ->
                val items: List<YTItem> = tracks
                    .filter { !hideExplicit || !it.explicit }
                    .map { it.toSpotifySongItem() }
                if (items.isNotEmpty()) summaries.add(SearchSummary(title = "Songs", items = items))
            }
        result.albums?.items?.filter { it.id.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { albums ->
                val items: List<YTItem> = albums.map { it.toAlbumItem() }
                if (items.isNotEmpty()) summaries.add(SearchSummary(title = "Albums", items = items))
            }
        result.artists?.items?.filter { it.id.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { artists ->
                val items: List<YTItem> = artists.map { it.toSpotifyArtistItem() }
                if (items.isNotEmpty()) summaries.add(SearchSummary(title = "Artists", items = items))
            }
        result.playlists?.items?.filter { it.id.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { playlists ->
                val items: List<YTItem> = playlists.map { it.toSpotifyPlaylistItem() }
                if (items.isNotEmpty()) summaries.add(SearchSummary(title = "Playlists", items = items))
            }

        synchronized(providerSummaries) {
            providerSummaries[OnlineProvider.SPOTIFY] = SearchSummaryPage(summaries = summaries)
        }
    }

    private suspend fun loadSoundCloudSummary() {
        if (!SoundCloudTokenManager.ensureAuthenticated()) {
            Timber.w("SearchVM: SoundCloud auth failed, skipping SoundCloud results")
            return
        }
        SoundCloud.searchSummary(query)
            .onSuccess { result ->
                val summaries = mutableListOf<SearchSummary>()

                if (result.tracks.isNotEmpty()) {
                    summaries.add(
                        SearchSummary(
                            title = "Songs",
                            items = result.tracks.map { track ->
                                track.toSoundCloudSongItem().also { soundCloudTracksByItemId[it.id] = track }
                            }
                        )
                    )
                }
                if (result.users.isNotEmpty()) {
                    summaries.add(SearchSummary(title = "Artists", items = result.users.map { it.toSoundCloudArtistItem() }))
                }
                if (result.playlists.isNotEmpty()) {
                    summaries.add(SearchSummary(title = "Playlists", items = result.playlists.map { it.toSoundCloudPlaylistItem() }))
                }

                synchronized(providerSummaries) {
                    providerSummaries[OnlineProvider.SOUNDCLOUD] = SearchSummaryPage(summaries = summaries)
                }
            }
            .onFailure {
                Timber.e(it, "SearchVM: SoundCloud summary search failed")
                reportException(it)
            }
    }

    // ── Filter mapping ───────────────────────────────────────────────────

    private fun mapToYouTubeFilter(filterKey: String): YouTube.SearchFilter? = when (filterKey) {
        "songs" -> YouTube.SearchFilter.FILTER_SONG
        "videos" -> YouTube.SearchFilter.FILTER_VIDEO
        "albums" -> YouTube.SearchFilter.FILTER_ALBUM
        "artists" -> YouTube.SearchFilter.FILTER_ARTIST
        "playlists" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
        "featured_playlists" -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
        "podcasts" -> YouTube.SearchFilter.FILTER_PODCAST
        else -> null
    }

    private fun mapToSpotifyFilter(filterKey: String): String? = when (filterKey) {
        "songs" -> "track"
        "albums" -> "album"
        "artists" -> "artist"
        "playlists" -> "playlist"
        else -> null
    }

    private fun mapToSoundCloudFilter(filterKey: String): String? = when (filterKey) {
        "songs" -> "track"
        "artists" -> "artist"
        "playlists" -> "playlist"
        else -> null
    }

    // ── Filtered loaders ─────────────────────────────────────────────────

    private suspend fun loadYouTubeFiltered(filterKey: String) {
        val ytFilter = mapToYouTubeFilter(filterKey) ?: return
        val pvKey = "YOUTUBE_MUSIC:$filterKey"
        synchronized(providerViewStates) { if (providerViewStates.containsKey(pvKey)) return }

        YouTube.search(query, ytFilter)
            .onSuccess { result ->
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val page = ItemsPage(
                    items = result.items
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts),
                    continuation = result.continuation,
                )
                synchronized(providerViewStates) { providerViewStates[pvKey] = page }
            }
            .onFailure(::reportException)
    }

    private suspend fun loadSpotifyFiltered(filterKey: String) {
        val spFilter = mapToSpotifyFilter(filterKey) ?: return
        val pvKey = "SPOTIFY:$filterKey"
        synchronized(providerViewStates) { if (providerViewStates.containsKey(pvKey)) return }

        if (!SpotifyTokenManager.ensureAuthenticated()) return

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val offset = 0
        val limit = 20

        Spotify.search(
            query = query,
            types = listOf(spFilter),
            limit = limit,
            offset = offset,
        ).onSuccess { result ->
            val items: List<YTItem> = when (spFilter) {
                "track" -> result.tracks?.items
                    ?.filter { !hideExplicit || !it.explicit }
                    ?.map { it.toSpotifySongItem() }
                    ?: emptyList()

                "album" -> result.albums?.items?.map { it.toAlbumItem() } ?: emptyList()
                "artist" -> result.artists?.items?.map { it.toSpotifyArtistItem() } ?: emptyList()
                "playlist" -> result.playlists?.items?.map { it.toSpotifyPlaylistItem() } ?: emptyList()
                else -> emptyList()
            }

            val hasMore = when (spFilter) {
                "track" -> (result.tracks?.items?.size ?: 0) >= limit
                "album" -> (result.albums?.items?.size ?: 0) >= limit
                "artist" -> (result.artists?.items?.size ?: 0) >= limit
                "playlist" -> (result.playlists?.items?.size ?: 0) >= limit
                else -> false
            }

            synchronized(providerViewStates) {
                providerViewStates[pvKey] = ItemsPage(
                    items = items.distinctBy { it.id },
                    continuation = if (hasMore) "spotify:$spFilter:${offset + limit}" else null,
                )
            }
        }.onFailure {
            Timber.e(it, "SearchVM: Spotify filtered search failed for type=$spFilter")
            reportException(it)
        }
    }

    private suspend fun loadSoundCloudFiltered(filterKey: String) {
        if (!SoundCloudTokenManager.ensureAuthenticated()) return
        val scFilter = mapToSoundCloudFilter(filterKey) ?: return
        val pvKey = "SOUNDCLOUD:$filterKey"
        synchronized(providerViewStates) { if (providerViewStates.containsKey(pvKey)) return }

        val page = when (scFilter) {
            "track" -> SoundCloud.searchTracks(query)
                .map {
                    ItemsPage(
                        items = it.collection.map { track ->
                            track.toSoundCloudSongItem().also { item -> soundCloudTracksByItemId[item.id] = track }
                        }.distinctBy { item -> item.id },
                        continuation = it.nextHref?.let { nextHref -> "soundcloud:track:$nextHref" },
                    )
                }

            "artist" -> SoundCloud.searchUsers(query)
                .map {
                    ItemsPage(
                        items = it.collection.map { user -> user.toSoundCloudArtistItem() }.distinctBy { item -> item.id },
                        continuation = it.nextHref?.let { nextHref -> "soundcloud:artist:$nextHref" },
                    )
                }

            "playlist" -> SoundCloud.searchPlaylists(query)
                .map {
                    ItemsPage(
                        items = it.collection.map { playlist -> playlist.toSoundCloudPlaylistItem() }.distinctBy { item -> item.id },
                        continuation = it.nextHref?.let { nextHref -> "soundcloud:playlist:$nextHref" },
                    )
                }

            else -> Result.failure(IllegalArgumentException("Unsupported SoundCloud filter: $scFilter"))
        }

        page.onSuccess {
            synchronized(providerViewStates) { providerViewStates[pvKey] = it }
        }.onFailure {
            Timber.e(it, "SearchVM: SoundCloud filtered search failed for type=$scFilter")
            reportException(it)
        }
    }

    // ── Merge functions ──────────────────────────────────────────────────

    private fun mergeSummaries() {
        val allSummaries: List<SearchSummaryPage>
        synchronized(providerSummaries) {
            allSummaries = providerSummaries.values.toList()
        }
        if (allSummaries.isEmpty()) return

        val typeOrder = listOf("Songs", "Albums", "Artists", "Playlists")
        val merged = mutableListOf<SearchSummary>()

        for (type in typeOrder) {
            val items = allSummaries.flatMap { page ->
                page.summaries.filter { it.title == type }.flatMap { it.items }
            }
            if (items.isNotEmpty()) {
                merged.add(SearchSummary(title = type, items = items.distinctBy { it.id }))
            }
        }

        // Add any types not in typeOrder (e.g., YouTube-specific like "Videos")
        val allTypes = allSummaries.flatMap { page -> page.summaries.map { it.title } }.distinct()
        for (type in allTypes.filter { it !in typeOrder }) {
            val items = allSummaries.flatMap { page ->
                page.summaries.filter { it.title == type }.flatMap { it.items }
            }
            if (items.isNotEmpty()) {
                merged.add(SearchSummary(title = type, items = items.distinctBy { it.id }))
            }
        }

        summaryPage = SearchSummaryPage(summaries = merged)
    }

    private fun mergeViewState(filterKey: String) {
        val providers = enabledProviders.value
        val allItems = mutableListOf<YTItem>()
        var hasAnyContinuation = false
        var anyLoaded = false

        synchronized(providerViewStates) {
            for (provider in providers) {
                val pvKey = "${provider.name}:$filterKey"
                providerViewStates[pvKey]?.let { page ->
                    anyLoaded = true
                    allItems.addAll(page.items)
                    if (page.continuation != null) hasAnyContinuation = true
                }
            }
        }

        if (anyLoaded) {
            viewStateMap[filterKey] = ItemsPage(
                items = allItems.distinctBy { it.id },
                continuation = if (hasAnyContinuation) "multi:$filterKey" else null,
            )
        }
    }

    // ── Load more ────────────────────────────────────────────────────────

    fun loadMore() {
        val filterKey = activeFilter.value ?: return
        val providers = enabledProviders.value

        for (provider in providers) {
            when (provider) {
                OnlineProvider.YOUTUBE_MUSIC -> loadMoreYouTube(filterKey)
                OnlineProvider.SPOTIFY -> loadMoreSpotify(filterKey)
                OnlineProvider.SOUNDCLOUD -> loadMoreSoundCloud(filterKey)
            }
        }
    }

    private fun loadMoreYouTube(filterKey: String) {
        val pvKey = "YOUTUBE_MUSIC:$filterKey"
        val viewState = synchronized(providerViewStates) { providerViewStates[pvKey] } ?: return
        val continuation = viewState.continuation ?: return

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.searchContinuation(continuation).onSuccess { searchResult ->
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                synchronized(providerViewStates) {
                    providerViewStates[pvKey] = ItemsPage(
                        items = (viewState.items + newItems).distinctBy { it.id },
                        continuation = searchResult.continuation,
                    )
                }
                mergeViewState(filterKey)
            }
        }
    }

    private fun loadMoreSpotify(filterKey: String) {
        val spFilter = mapToSpotifyFilter(filterKey) ?: return
        val pvKey = "SPOTIFY:$filterKey"
        val viewState = synchronized(providerViewStates) { providerViewStates[pvKey] } ?: return
        val continuation = viewState.continuation ?: return
        val parts = continuation.split(":")
        if (parts.size != 3) return
        val offset = parts[2].toIntOrNull() ?: return
        val limit = 20

        viewModelScope.launch(Dispatchers.IO) {
            if (!SpotifyTokenManager.ensureAuthenticated()) return@launch
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)

            Spotify.search(
                query = query,
                types = listOf(spFilter),
                limit = limit,
                offset = offset,
            ).onSuccess { result ->
                val newItems: List<YTItem> = when (spFilter) {
                    "track" -> result.tracks?.items
                        ?.filter { !hideExplicit || !it.explicit }
                        ?.map { it.toSpotifySongItem() }
                        ?: emptyList()

                    "album" -> result.albums?.items?.map { it.toAlbumItem() } ?: emptyList()
                    "artist" -> result.artists?.items?.map { it.toSpotifyArtistItem() } ?: emptyList()
                    "playlist" -> result.playlists?.items?.map { it.toSpotifyPlaylistItem() } ?: emptyList()
                    else -> emptyList()
                }

                val hasMore = when (spFilter) {
                    "track" -> (result.tracks?.items?.size ?: 0) >= limit
                    "album" -> (result.albums?.items?.size ?: 0) >= limit
                    "artist" -> (result.artists?.items?.size ?: 0) >= limit
                    "playlist" -> (result.playlists?.items?.size ?: 0) >= limit
                    else -> false
                }

                synchronized(providerViewStates) {
                    providerViewStates[pvKey] = ItemsPage(
                        items = (viewState.items + newItems).distinctBy { it.id },
                        continuation = if (hasMore) "spotify:$spFilter:${offset + limit}" else null,
                    )
                }
                mergeViewState(filterKey)
            }.onFailure {
                Timber.e(it, "SearchVM: Spotify loadMore failed")
                reportException(it)
            }
        }
    }

    private fun loadMoreSoundCloud(filterKey: String) {
        val scFilter = mapToSoundCloudFilter(filterKey) ?: return
        val pvKey = "SOUNDCLOUD:$filterKey"
        val viewState = synchronized(providerViewStates) { providerViewStates[pvKey] } ?: return
        val continuation = viewState.continuation ?: return
        val nextHref = continuation.substringAfter("soundcloud:$scFilter:", "")
        if (nextHref.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val page = when (scFilter) {
                "track" -> SoundCloud.searchTracks(query, continuation = nextHref)
                    .map {
                        ItemsPage(
                            items = (viewState.items + it.collection.map { track ->
                                track.toSoundCloudSongItem().also { item -> soundCloudTracksByItemId[item.id] = track }
                            }).distinctBy { item -> item.id },
                            continuation = it.nextHref?.let { next -> "soundcloud:track:$next" },
                        )
                    }

                "artist" -> SoundCloud.searchUsers(query, continuation = nextHref)
                    .map {
                        ItemsPage(
                            items = (viewState.items + it.collection.map { user -> user.toSoundCloudArtistItem() }).distinctBy { item -> item.id },
                            continuation = it.nextHref?.let { next -> "soundcloud:artist:$next" },
                        )
                    }

                "playlist" -> SoundCloud.searchPlaylists(query, continuation = nextHref)
                    .map {
                        ItemsPage(
                            items = (viewState.items + it.collection.map { playlist -> playlist.toSoundCloudPlaylistItem() }).distinctBy { item -> item.id },
                            continuation = it.nextHref?.let { next -> "soundcloud:playlist:$next" },
                        )
                    }

                else -> Result.failure(IllegalArgumentException("Unsupported SoundCloud filter: $scFilter"))
            }

            page.onSuccess {
                synchronized(providerViewStates) { providerViewStates[pvKey] = it }
                mergeViewState(filterKey)
            }.onFailure {
                Timber.e(it, "SearchVM: SoundCloud loadMore failed")
                reportException(it)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    fun soundCloudTrack(itemId: String): SoundCloudTrackDto? = soundCloudTracksByItemId[itemId]

    fun currentSoundCloudTracks(activeFilterKey: String?): List<SoundCloudTrackDto> {
        return if (activeFilterKey == "songs") {
            viewStateMap[activeFilterKey]?.items
                ?.mapNotNull { item -> soundCloudTracksByItemId[item.id] }
                .orEmpty()
        } else {
            summaryPage?.summaries
                ?.firstOrNull { it.title == "Songs" }
                ?.items
                ?.mapNotNull { item -> soundCloudTracksByItemId[item.id] }
                .orEmpty()
        }
    }
}
