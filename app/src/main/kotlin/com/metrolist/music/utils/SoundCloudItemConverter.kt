/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.soundcloud.models.SoundCloudLikeOriginDto
import com.metrolist.soundcloud.models.SoundCloudPlaylistDto
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import com.metrolist.soundcloud.models.SoundCloudUserDto

const val SOUNDCLOUD_ID_PREFIX = "soundcloud:"

fun String.isSoundCloudId(): Boolean = startsWith(SOUNDCLOUD_ID_PREFIX)

fun String.stripSoundCloudPrefix(): String = removePrefix(SOUNDCLOUD_ID_PREFIX)

fun SoundCloudLikeOriginDto.toTrackDtoOrNull(): SoundCloudTrackDto? {
    val originId = id ?: return null
    val originTitle = title?.takeIf(String::isNotBlank) ?: return null
    if (kind != "track") return null
    return SoundCloudTrackDto(
        id = originId,
        title = originTitle,
        duration = duration,
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        streamable = streamable ?: false,
        userFavorite = userFavorite,
        user = user,
    )
}

fun SoundCloudTrackDto.toSongItem(): SongItem = SongItem(
    id = "$SOUNDCLOUD_ID_PREFIX$id",
    title = title,
    artists = listOfNotNull(
        user?.let {
            Artist(
                name = it.fullName?.takeIf(String::isNotBlank) ?: it.username,
                id = "$SOUNDCLOUD_ID_PREFIX${it.id}",
            )
        }
    ),
    album = user?.let {
        Album(
            name = it.fullName?.takeIf(String::isNotBlank) ?: it.username,
            id = "$SOUNDCLOUD_ID_PREFIX${it.id}",
        )
    },
    duration = duration?.div(1000)?.toInt(),
    thumbnail = artworkUrl ?: user?.avatarUrl.orEmpty(),
    explicit = false,
)

fun SoundCloudUserDto.toArtistItem(): ArtistItem = ArtistItem(
    id = "$SOUNDCLOUD_ID_PREFIX$id",
    title = fullName?.takeIf(String::isNotBlank) ?: username,
    thumbnail = avatarUrl,
    shuffleEndpoint = null,
    radioEndpoint = null,
)

fun SoundCloudPlaylistDto.toPlaylistItem(): PlaylistItem = PlaylistItem(
    id = "$SOUNDCLOUD_ID_PREFIX$id",
    title = title,
    author = user?.let {
        Artist(
            name = it.fullName?.takeIf(String::isNotBlank) ?: it.username,
            id = "$SOUNDCLOUD_ID_PREFIX${it.id}",
        )
    },
    songCountText = trackCount?.toString(),
    thumbnail = artworkUrl ?: user?.avatarUrl.orEmpty(),
    playEndpoint = null,
    shuffleEndpoint = null,
    radioEndpoint = null,
)

fun SoundCloudTrackDto.toMediaItem(streamUrl: String): MediaItem =
    toSongItem().toSoundCloudMediaItem(streamUrl)

fun SongItem.toSoundCloudMediaItem(streamUrl: String): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(streamUrl)
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnail.takeIf(String::isNotBlank)?.toUri())
            .setAlbumTitle(album?.name)
            .setAlbumArtist(artists.firstOrNull()?.name)
            .setDisplayTitle(title)
            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()
