/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.metrolist.music.constants.EnableSoundCloudKey
import com.metrolist.music.R
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.UseSoundCloudLibraryKey
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.screens.library.local.LocalFilesScreen
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@Composable
fun LibraryScreen(navController: NavController) {
    val soundCloudEnabled by rememberPreference(EnableSoundCloudKey, false)
    val useSoundCloudLibrary by rememberPreference(UseSoundCloudLibraryKey, false)
    val soundCloudAccessToken by rememberPreference(SoundCloudAccessTokenKey, "")
    val showSoundCloudLibrary = soundCloudEnabled && useSoundCloudLibrary && soundCloudAccessToken.isNotBlank()

    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips =
                listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts),
                    LibraryFilter.LOCAL_FILES to stringResource(R.string.filter_local_files),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType =
                        if (filterType == it) {
                            LibraryFilter.LIBRARY
                        } else {
                            it
                        }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showSoundCloudLibrary) {
            SoundCloudLibraryScreen(navController)
        } else {
            when (filterType) {
                LibraryFilter.LIBRARY -> LibraryMixScreen(navController, filterContent)
                LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
                LibraryFilter.SONGS -> LibrarySongsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.PODCASTS -> LibraryPodcastsScreen(
                    navController,
                    filterContent)

                LibraryFilter.LOCAL_FILES -> LocalFilesScreen(
                    navController,
                    filterContent)
            }
        }
    }
}
